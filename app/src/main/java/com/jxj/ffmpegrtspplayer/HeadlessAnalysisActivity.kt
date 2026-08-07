package com.jxj.ffmpegrtspplayer

import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.jxj.ffmpegrtsp.lib.api.AudioOptions
import com.jxj.ffmpegrtsp.lib.api.PlayerStateSnapshot
import com.jxj.ffmpegrtsp.lib.api.StreamConfig
import com.jxj.ffmpegrtsp.lib.api.StreamPlayer
import com.jxj.ffmpegrtsp.lib.api.VideoOptions
import com.jxj.ffmpegrtsp.lib.yuv.IYUVFrameProcessor
import com.jxj.ffmpegrtsp.lib.yuv.YUVFrameInfo
import com.jxj.ffmpegrtsp.lib.yuv.YUVProcessMode
import com.jxj.ffmpegrtsp.lib.yuv.YUVProcessResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class HeadlessAnalysisActivity : BaseInsetsActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "HeadlessAnalysis"
        private const val MAX_LOG_LENGTH = 4000
        private const val STATS_REFRESH_INTERVAL_MS = 500L
        private const val DEFAULT_URL = "rtsp://stream.strba.sk:1935/strba/VYHLAD_JAZERO.stream"
    }

    private lateinit var previewContainer: FrameLayout
    private var previewSurfaceView: SurfaceView? = null
    private lateinit var urlEditText: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var releaseButton: Button
    private lateinit var attachPreviewButton: Button
    private lateinit var detachPreviewButton: Button
    private lateinit var resetStatsButton: Button
    private lateinit var modeSummaryTextView: TextView
    private lateinit var stateTextView: TextView
    private lateinit var frameStatsTextView: TextView
    private lateinit var pipelineStatsTextView: TextView
    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView

    private val mainHandler = Handler(Looper.getMainLooper())
    private val logBuilder = StringBuilder()
    private val analyzedFrameCount = AtomicLong(0L)

    private var player: StreamPlayer? = null
    private var streamStarted = false
    private var previewAttached = false
    private var previewRequested = false
    private var surfaceAvailable = false
    private var processorRegistered = false

    @Volatile
    private var lastFrameWidth = 0

    @Volatile
    private var lastFrameHeight = 0

    @Volatile
    private var lastYStride = 0

    @Volatile
    private var lastUvStride = 0

    @Volatile
    private var lastFrameIndex = -1L

    @Volatile
    private var lastFramePtsUs = 0L

    @Volatile
    private var lastFrameFormat = "N/A"

    private val statsRefreshRunnable = object : Runnable {
        override fun run() {
            refreshPanels()
            if (!isFinishing) {
                mainHandler.postDelayed(this, STATS_REFRESH_INTERVAL_MS)
            }
        }
    }

    private val analysisObserver = object : IYUVFrameProcessor {
        override fun getPriority(): Int = 50

        override fun getProcessMode(): YUVProcessMode = YUVProcessMode.OBSERVE_ONLY

        override fun onProcessFrame(frame: YUVFrameInfo): YUVProcessResult {
            val count = analyzedFrameCount.incrementAndGet()
            lastFrameWidth = frame.width
            lastFrameHeight = frame.height
            lastYStride = frame.yStride
            lastUvStride = frame.uvStride
            lastFrameIndex = frame.frameIndex
            lastFramePtsUs = frame.pts
            lastFrameFormat = frame.format.name

            if (count == 1L || count % 30L == 0L) {
                mainHandler.post { refreshPanels() }
            }
            return YUVProcessResult.passthrough()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_headless_analysis)
        applyEdgeToEdge(findViewById(android.R.id.content), top = true, bottom = true)

        initViews()
        setupListeners()
        ensurePreviewSurface(recreate = false)
        mainHandler.post(statsRefreshRunnable)

        appendLog("HeadlessAnalysisActivity 已创建")
        appendLog("模式: allowStartWithoutSurface(true) + 软件解码")
        refreshPanels()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        releasePlayerInternal("页面销毁")
        removePreviewSurface()
        super.onDestroy()
    }

    private fun initViews() {
        previewContainer = findViewById(R.id.previewContainer)
        urlEditText = findViewById(R.id.urlEditText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        releaseButton = findViewById(R.id.releaseButton)
        attachPreviewButton = findViewById(R.id.attachPreviewButton)
        detachPreviewButton = findViewById(R.id.detachPreviewButton)
        resetStatsButton = findViewById(R.id.resetStatsButton)
        modeSummaryTextView = findViewById(R.id.modeSummaryTextView)
        stateTextView = findViewById(R.id.stateTextView)
        frameStatsTextView = findViewById(R.id.frameStatsTextView)
        pipelineStatsTextView = findViewById(R.id.pipelineStatsTextView)
        logTextView = findViewById(R.id.logTextView)
        logScrollView = findViewById(R.id.logScrollView)

        urlEditText.setText(DEFAULT_URL)
    }

    private fun setupListeners() {
        startButton.setOnClickListener { startHeadlessStream() }
        stopButton.setOnClickListener { stopStream() }
        releaseButton.setOnClickListener { releasePlayerInternal("手动释放") }
        attachPreviewButton.setOnClickListener { attachPreview() }
        detachPreviewButton.setOnClickListener { detachPreview() }
        resetStatsButton.setOnClickListener { resetStats() }
    }

    private fun startHeadlessStream() {
        val url = urlEditText.text.toString().trim()
        if (url.isEmpty()) {
            showToast("请输入流地址")
            return
        }

        try {
            val currentPlayer = player
            if (currentPlayer == null || currentPlayer.isReleased()) {
                createHeadlessPlayer(url)
            } else {
                val currentUrl = currentPlayer.state.currentUrl
                if (currentUrl.isNullOrEmpty() || currentUrl != url) {
                    appendLog("切换分析流: $url")
                    currentPlayer.switchSource(url)
                }
            }

            ensureAnalysisProcessorRegistered()
            player?.play()
            appendLog("已发起无渲染分析流")
            showToast("分析流已发起")
        } catch (e: Exception) {
            Log.e(TAG, "启动无渲染分析流失败", e)
            appendLog("启动失败: ${e.message ?: e.javaClass.simpleName}")
            showToast("启动失败")
        }
        refreshPanels()
    }

    private fun createHeadlessPlayer(url: String) {
        val config = StreamConfig.Builder(url)
            .video(VideoOptions.software())
            .audio(AudioOptions.disabled())
            .allowStartWithoutSurface(true)
            .build()

        player = StreamPlayer.prepare(this, config)
            .setOnStateChanged { state ->
                runOnUiThread { onPlayerStateChanged(state) }
            }
            .setOnPlaybackStarted {
                runOnUiThread {
                    streamStarted = true
                    appendLog("播放启动成功")
                    ensureAnalysisProcessorRegistered()
                    refreshPanels()
                }
            }
            .setOnPlaybackStopped {
                runOnUiThread {
                    streamStarted = false
                    appendLog("播放已停止")
                    refreshPanels()
                }
            }
            .setOnError { errorCode, errorMessage ->
                runOnUiThread {
                    appendLog("错误: $errorCode, $errorMessage")
                    refreshPanels()
                }
            }

        processorRegistered = false
        previewAttached = false
        previewRequested = false
        streamStarted = false
        resetFrameSnapshot()
        player?.setBuiltinRenderEnabled(false)
        appendLog("已创建 headless 播放器: analysisOnly=true, rendererType=NONE, audioEnabled=false")
        appendLog("已显式关闭内置渲染，保持纯分析模式")
    }

    private fun stopStream() {
        val currentPlayer = player
        if (currentPlayer == null) {
            showToast("当前没有活动播放器")
            return
        }
        currentPlayer.stop()
        streamStarted = false
        appendLog("已请求停止播放")
        refreshPanels()
    }

    private fun attachPreview() {
        if (player == null) {
            showToast("请先启动分析流")
            return
        }
        previewRequested = true
        ensurePreviewSurface(recreate = true)
        appendLog("预览挂载已请求，等待新 Surface 就绪后自动绑定")
        refreshPanels()
    }

    private fun detachPreview() {
        val currentPlayer = player
        if (currentPlayer == null) {
            showToast("当前没有活动播放器")
            return
        }
        previewRequested = false
        currentPlayer.detachSurface()
        currentPlayer.setBuiltinRenderEnabled(false)
        previewAttached = false
        appendLog("已 detach Surface，恢复纯分析模式")
        appendLog("已显式关闭内置渲染")
        refreshPanels()
    }

    private fun attachPreviewInternal(reason: String) {
        val currentPlayer = player ?: return
        val surfaceView = previewSurfaceView ?: return
        val holder = surfaceView.holder
        val surfaceValid = holder.surface?.isValid == true

        appendLog(
            "开始挂载预览($reason): surfaceValid=$surfaceValid, view=${surfaceView.width}x${surfaceView.height}"
        )
        currentPlayer.setBuiltinRenderEnabled(true)
        appendLog("已显式开启内置渲染")
        currentPlayer.attachSurface(surfaceView)
        previewAttached = true
        appendLog("已动态 attach Surface，进入分析 + 预览模式")
        refreshPanels()
    }

    private fun ensureAnalysisProcessorRegistered() {
        val currentPlayer = player ?: return
        if (processorRegistered) {
            return
        }
        if (currentPlayer.registerYuvProcessor(analysisObserver)) {
            processorRegistered = true
            appendLog("YUV 观察处理器注册成功")
        } else {
            appendLog("YUV 观察处理器注册失败")
        }
    }

    private fun releasePlayerInternal(reason: String) {
        val currentPlayer = player ?: return
        appendLog("释放播放器: $reason")
        currentPlayer.release()
        player = null
        streamStarted = false
        previewAttached = false
        previewRequested = false
        processorRegistered = false
        resetFrameSnapshot()
        analyzedFrameCount.set(0L)
        refreshPanels()
    }

    private fun resetStats() {
        analyzedFrameCount.set(0L)
        resetFrameSnapshot()
        player?.resetYuvPerformanceStats()
        appendLog("统计已重置")
        refreshPanels()
    }

    private fun resetFrameSnapshot() {
        lastFrameWidth = 0
        lastFrameHeight = 0
        lastYStride = 0
        lastUvStride = 0
        lastFrameIndex = -1L
        lastFramePtsUs = 0L
        lastFrameFormat = "N/A"
    }

    private fun onPlayerStateChanged(state: PlayerStateSnapshot?) {
        val snapshot = state ?: return
        streamStarted = snapshot.isPlaying
        if (snapshot.isCreated) {
            ensureAnalysisProcessorRegistered()
        }
        if (!snapshot.hasSurface()) {
            previewAttached = false
        }
        refreshPanels()
    }

    private fun refreshPanels() {
        val state = player?.state
        modeSummaryTextView.text =
            "formal headless mode | analysisOnly=true | streamStarted=$streamStarted | " +
                "previewRequested=$previewRequested | previewAttached=$previewAttached | " +
                "surfaceReady=$surfaceAvailable | builtinRender=${resolveBuiltinRenderState()}"

        if (state == null) {
            stateTextView.text = "播放器未创建"
            frameStatsTextView.text = "分析帧数: 0\n最近帧: N/A"
            pipelineStatsTextView.text = "YUV 性能统计: N/A"
            updateButtonState(hasPlayer = false)
            return
        }

        stateTextView.text = buildString {
            append("streamId=${state.streamId}\n")
            append("state=${state.streamStateCode}\n")
            append("created=${state.isCreated} | playing=${state.isPlaying} | released=${state.isReleased}\n")
            append("surfaceBound=${state.hasSurface()} | operationPending=${state.isOperationPending}\n")
            append("url=${safeText(state.currentUrl)}")
        }

        frameStatsTextView.text = buildString {
            append("分析帧数: ${analyzedFrameCount.get()}\n")
            append("最近帧: index=$lastFrameIndex, ptsUs=$lastFramePtsUs\n")
            append("尺寸: ${lastFrameWidth}x${lastFrameHeight} | 格式: $lastFrameFormat\n")
            append("stride: Y=$lastYStride UV=$lastUvStride")
        }

        val currentPlayer = player
        val performanceStats = currentPlayer?.getYuvPerformanceStats()
        val memoryStats = currentPlayer?.getYuvMemoryStats()
        pipelineStatsTextView.text = buildString {
            append(
                "totalFrames=${performanceStats?.totalFramesProcessed ?: 0} | " +
                    "jniCalls=${performanceStats?.totalJniCalls ?: 0}\n"
            )
            append(
                String.format(
                    Locale.US,
                    "avg=%.2fms | max=%.2fms\n",
                    performanceStats?.avgProcessingTimeMs ?: 0.0,
                    performanceStats?.maxProcessingTimeMs ?: 0.0
                )
            )
            append(
                "async submitted=${performanceStats?.asyncTasksSubmitted ?: 0} " +
                    "completed=${performanceStats?.asyncTasksCompleted ?: 0} " +
                    "dropped=${performanceStats?.asyncTasksDropped ?: 0}\n"
            )
            append(
                "memory total=${memoryStats?.totalMemory ?: 0} | " +
                    "pool=${memoryStats?.bufferPoolSize ?: 0}"
            )
        }

        updateButtonState(hasPlayer = true)
    }

    private fun updateButtonState(hasPlayer: Boolean) {
        startButton.isEnabled = true
        stopButton.isEnabled = hasPlayer
        releaseButton.isEnabled = hasPlayer
        attachPreviewButton.isEnabled = hasPlayer && !previewRequested
        detachPreviewButton.isEnabled = hasPlayer && previewRequested
        resetStatsButton.isEnabled = hasPlayer
    }

    private fun resolveBuiltinRenderState(): String {
        val currentPlayer = player ?: return "N/A"
        if (currentPlayer.isReleased()) {
            return "N/A"
        }
        return try {
            currentPlayer.isBuiltinRenderEnabled().toString()
        } catch (_: RuntimeException) {
            "error"
        }
    }

    private fun safeText(text: String?): String {
        return if (text.isNullOrBlank()) "N/A" else text
    }

    private fun appendLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$timestamp] $message\n"
        Log.i(TAG, message)
        mainHandler.post {
            logBuilder.append(line)
            if (logBuilder.length > MAX_LOG_LENGTH) {
                logBuilder.delete(0, logBuilder.length - MAX_LOG_LENGTH)
            }
            logTextView.text = logBuilder.toString()
            logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun ensurePreviewSurface(recreate: Boolean) {
        if (!recreate && previewSurfaceView != null) {
            return
        }
        if (recreate) {
            removePreviewSurface()
        }

        val surfaceView = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            keepScreenOn = true
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            holder.addCallback(this@HeadlessAnalysisActivity)
        }

        previewSurfaceView = surfaceView
        surfaceAvailable = false
        previewAttached = false
        previewContainer.removeAllViews()
        previewContainer.addView(surfaceView)
        appendLog(if (recreate) "已重建预览 SurfaceView" else "已创建预览 SurfaceView")
    }

    private fun removePreviewSurface() {
        val surfaceView = previewSurfaceView ?: return
        surfaceView.holder.removeCallback(this)
        previewContainer.removeView(surfaceView)
        previewSurfaceView = null
        surfaceAvailable = false
        previewAttached = false
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val surfaceView = previewSurfaceView
        surfaceAvailable = true
        appendLog(
            "预览 Surface 已创建: valid=${holder.surface?.isValid == true}, " +
                "view=${surfaceView?.width ?: 0}x${surfaceView?.height ?: 0}"
        )
        if (previewRequested && player != null) {
            attachPreviewInternal("SurfaceCreated 自动重绑")
        }
        refreshPanels()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceAvailable = true
        appendLog(
            "预览 Surface 变化: format=$format, size=${width}x$height, valid=${holder.surface?.isValid == true}"
        )
        if (previewRequested && player != null && width > 0 && height > 0) {
            attachPreviewInternal("SurfaceChanged 自动重绑")
        }
        refreshPanels()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceAvailable = false
        previewAttached = false
        appendLog("预览 Surface 已销毁")
        refreshPanels()
    }
}

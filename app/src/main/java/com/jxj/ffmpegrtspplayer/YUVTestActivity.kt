package com.jxj.ffmpegrtspplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.jxj.ffmpegrtsp.lib.api.AudioOptions
import com.jxj.ffmpegrtsp.lib.api.PlayerStateSnapshot
import com.jxj.ffmpegrtsp.lib.api.StreamConfig
import com.jxj.ffmpegrtsp.lib.api.StreamErrorCode
import com.jxj.ffmpegrtsp.lib.api.StreamPlayer
import com.jxj.ffmpegrtsp.lib.api.StreamStateCode
import com.jxj.ffmpegrtsp.lib.api.VideoOptions
import com.jxj.ffmpegrtsp.lib.api.YuvPerformanceStats
import com.jxj.ffmpegrtsp.lib.yuv.IAsyncYUVProcessor
import com.jxj.ffmpegrtsp.lib.yuv.IYUVFrameProcessor
import com.jxj.ffmpegrtsp.lib.yuv.YUVFrameInfo
import com.jxj.ffmpegrtsp.lib.yuv.YUVProcessMode
import com.jxj.ffmpegrtsp.lib.yuv.YUVProcessResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * YUV 数据暴露功能测试 Activity。
 *
 * 固定使用软件解码，验证 YUV 扩展链路当前的四种工作模式：
 * 1. OBSERVE_ONLY - AI 分析、统计、截图辅助
 * 2. PROCESS_AND_RENDER - software pipeline 处理后渲染
 * 3. CUSTOM_RENDER_ONLY - software pipeline 跳过内置渲染
 * 4. IAsyncYUVProcessor - 异步快照分析
 */
class YUVTestActivity : BaseInsetsActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "YUVTestActivity"
        private const val MAX_LOG_LENGTH = 4000
        private const val FRAME_STATS_UPDATE_INTERVAL_MS = 100L
        private const val PERIODIC_STATS_UPDATE_INTERVAL_MS = 500L
    }

    private lateinit var videoSurface: SurfaceView
    private lateinit var urlEditText: EditText
    private lateinit var startStreamButton: Button
    private lateinit var stopStreamButton: Button
    private lateinit var destroyStreamButton: Button
    private lateinit var registerObserverButton: Button
    private lateinit var unregisterObserverButton: Button
    private lateinit var registerFilterButton: Button
    private lateinit var unregisterFilterButton: Button
    private lateinit var registerCustomButton: Button
    private lateinit var unregisterCustomButton: Button
    private lateinit var registerAsyncButton: Button
    private lateinit var unregisterAsyncButton: Button
    private lateinit var enableRenderButton: Button
    private lateinit var disableRenderButton: Button
    private lateinit var clearProcessorsButton: Button
    private lateinit var frameCountTextView: TextView
    private lateinit var frameInfoTextView: TextView
    private lateinit var yuvDataTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var processorStatusTextView: TextView
    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView

    private var player: StreamPlayer? = null
    private var currentState: PlayerStateSnapshot = emptyState()
    private var isStreamStarted = false
    private var isSurfaceReady = false

    private lateinit var observerProcessor: IYUVFrameProcessor
    private lateinit var filterProcessor: IYUVFrameProcessor
    private lateinit var customProcessor: IYUVFrameProcessor
    private lateinit var asyncProcessor: IAsyncYUVProcessor

    private var isObserverRegistered = false
    private var isFilterRegistered = false
    private var isCustomRegistered = false
    private var isAsyncRegistered = false

    private val observerFrameCount = AtomicLong(0)
    private val filterFrameCount = AtomicLong(0)
    private val customFrameCount = AtomicLong(0)
    private val asyncFrameCount = AtomicLong(0)
    private val asyncCompletedCount = AtomicLong(0)

    @Volatile
    private var lastFrameInfo: YUVFrameInfo? = null

    private val asyncExecutor: ExecutorService = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val logBuilder = StringBuilder()
    private var lastStatsUpdateTime = 0L
    private val statsUpdateRunnable = object : Runnable {
        override fun run() {
            if (isStreamStarted && player != null) {
                updateUI()
                mainHandler.postDelayed(this, PERIODIC_STATS_UPDATE_INTERVAL_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_yuv_test)
        applyEdgeToEdge(findViewById(android.R.id.content), top = true, bottom = true)

        initViews()
        setupListeners()
        createProcessors()
        appendLog("YUVTestActivity 创建")
        appendLog("初始化完成 - 软件解码 YUV 四种处理模式就绪")
        updateUI()
    }

    private fun initViews() {
        videoSurface = findViewById(R.id.videoSurface)
        urlEditText = findViewById(R.id.urlEditText)
        startStreamButton = findViewById(R.id.startStreamButton)
        stopStreamButton = findViewById(R.id.stopStreamButton)
        destroyStreamButton = findViewById(R.id.destroyStreamButton)
        registerObserverButton = findViewById(R.id.registerObserverButton)
        unregisterObserverButton = findViewById(R.id.unregisterObserverButton)
        registerFilterButton = findViewById(R.id.registerFilterButton)
        unregisterFilterButton = findViewById(R.id.unregisterFilterButton)
        registerCustomButton = findViewById(R.id.registerCustomButton)
        unregisterCustomButton = findViewById(R.id.unregisterCustomButton)
        registerAsyncButton = findViewById(R.id.registerAsyncButton)
        unregisterAsyncButton = findViewById(R.id.unregisterAsyncButton)
        enableRenderButton = findViewById(R.id.enableRenderButton)
        disableRenderButton = findViewById(R.id.disableRenderButton)
        clearProcessorsButton = findViewById(R.id.clearProcessorsButton)
        frameCountTextView = findViewById(R.id.frameCountTextView)
        frameInfoTextView = findViewById(R.id.frameInfoTextView)
        yuvDataTextView = findViewById(R.id.yuvDataTextView)
        statusTextView = findViewById(R.id.statusTextView)
        processorStatusTextView = findViewById(R.id.processorStatusTextView)
        logTextView = findViewById(R.id.logTextView)
        logScrollView = findViewById(R.id.logScrollView)

        urlEditText.setText("http://demo-videos.qnsdk.com/VR-Panorama-Equirect-Angular-4500k.mp4")
        videoSurface.holder.addCallback(this)
    }

    private fun setupListeners() {
        startStreamButton.setOnClickListener { ensurePlayerAndPlay() }
        stopStreamButton.setOnClickListener { stopStream() }
        destroyStreamButton.setOnClickListener { releasePlayer() }
        registerObserverButton.setOnClickListener { registerObserver() }
        unregisterObserverButton.setOnClickListener { unregisterObserver() }
        registerFilterButton.setOnClickListener { registerFilter() }
        unregisterFilterButton.setOnClickListener { unregisterFilter() }
        registerCustomButton.setOnClickListener { registerCustom() }
        unregisterCustomButton.setOnClickListener { unregisterCustom() }
        registerAsyncButton.setOnClickListener { registerAsync() }
        unregisterAsyncButton.setOnClickListener { unregisterAsync() }
        enableRenderButton.setOnClickListener { setBuiltinRender(true) }
        disableRenderButton.setOnClickListener { setBuiltinRender(false) }
        clearProcessorsButton.setOnClickListener { clearAllProcessors() }
    }

    private fun createProcessors() {
        observerProcessor = object : IYUVFrameProcessor {
            override fun getPriority(): Int = 10

            override fun getProcessMode(): YUVProcessMode = YUVProcessMode.OBSERVE_ONLY

            override fun onProcessFrame(frame: YUVFrameInfo): YUVProcessResult {
                val count = observerFrameCount.incrementAndGet()
                lastFrameInfo = frame
                updateStatsIfNeeded(count, frame, "观察")
                return YUVProcessResult.passthrough()
            }
        }

        filterProcessor = object : IYUVFrameProcessor {
            override fun getPriority(): Int = 50

            override fun getProcessMode(): YUVProcessMode = YUVProcessMode.PROCESS_AND_RENDER

            override fun onProcessFrame(frame: YUVFrameInfo): YUVProcessResult {
                val count = filterFrameCount.incrementAndGet()
                lastFrameInfo = frame
                updateStatsIfNeeded(count, frame, "滤镜")
                return YUVProcessResult.passthrough()
            }
        }

        customProcessor = object : IYUVFrameProcessor {
            override fun getPriority(): Int = 100

            override fun getProcessMode(): YUVProcessMode = YUVProcessMode.CUSTOM_RENDER_ONLY

            override fun onProcessFrame(frame: YUVFrameInfo): YUVProcessResult {
                val count = customFrameCount.incrementAndGet()
                lastFrameInfo = frame
                updateStatsIfNeeded(count, frame, "自定义")
                return YUVProcessResult.skipRender()
            }
        }

        asyncProcessor = object : IAsyncYUVProcessor {
            override fun getPriority(): Int = 20

            override fun getProcessMode(): YUVProcessMode = YUVProcessMode.OBSERVE_ONLY

            override fun onProcessFrame(frame: YUVFrameInfo): YUVProcessResult {
                // 异步链路默认只走 processFrameAsync()；这里只是满足接口实现。
                return YUVProcessResult.passthrough()
            }

            override fun processFrameAsync(frame: YUVFrameInfo): CompletableFuture<YUVProcessResult> {
                asyncFrameCount.incrementAndGet()
                return CompletableFuture.supplyAsync({
                    try {
                        Thread.sleep(20)
                        val completed = asyncCompletedCount.incrementAndGet()
                        lastFrameInfo = frame
                        if (completed % 30 == 0L) {
                            appendLog("异步处理完成帧数: $completed")
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    YUVProcessResult.passthrough()
                }, asyncExecutor)
            }

            override fun getAsyncTimeout(): Long = 2000L

            override fun onAsyncProcessComplete(
                streamId: Int,
                frameIndex: Long,
                result: YUVProcessResult
            ) {
                Log.d(TAG, "async complete: stream=$streamId, frame=$frameIndex")
            }

            override fun onAsyncProcessError(streamId: Int, frameIndex: Long, error: Throwable) {
                Log.e(TAG, "async error: stream=$streamId, frame=$frameIndex", error)
            }

            override fun supportsAsync(): Boolean = true
        }
    }

    private fun ensurePlayerAndPlay() {
        val url = urlEditText.text.toString().trim()
        if (url.isEmpty()) {
            showToast("请输入 RTSP 地址")
            return
        }
        if (!isSurfaceReady) {
            showToast("Surface 未就绪，请稍后")
            return
        }

        appendLog("开始播放: $url")
        appendLog("YUV 测试页固定使用软件解码，YUV 回调/处理后渲染仅在 software pipeline 生效")

        val currentPlayer = player
        if (currentPlayer != null && !currentPlayer.isReleased()) {
            isStreamStarted = true
            currentPlayer.play()
            refreshStateFromPlayer()
            return
        }

        isStreamStarted = true
        player = StreamPlayer.playWithConfig(
            this,
            videoSurface,
            StreamConfig.Builder(url)
                .video(VideoOptions.software())
                .audio(AudioOptions.disabled())
                .build()
        )
            .setOnStateChanged {
                syncState(it)
            }
            .setOnPlaybackStarted { videoInfo ->
                isStreamStarted = true
                refreshStateFromPlayer()
                val playbackInfo = videoInfo?.let {
                    "${it.width}x${it.height} ${it.codec}"
                } ?: "视频信息暂不可用"
                appendLog("播放开始: $playbackInfo")
                mainHandler.removeCallbacks(statsUpdateRunnable)
                mainHandler.post(statsUpdateRunnable)
                updateUI()
            }
            .setOnPlaybackStopped {
                isStreamStarted = false
                refreshStateFromPlayer()
                appendLog("播放已停止")
                mainHandler.removeCallbacks(statsUpdateRunnable)
                updateUI()
            }
            .setOnError { errorCode, errorMessage ->
                isStreamStarted = false
                refreshStateFromPlayer()
                appendLog("播放器错误: $errorCode / $errorMessage")
                mainHandler.removeCallbacks(statsUpdateRunnable)
                updateUI()
            }

        appendLog("播放器已初始化，streamId=${player?.getStreamId()}")
        refreshStateFromPlayer()
    }

    private fun stopStream() {
        isStreamStarted = false
        mainHandler.removeCallbacks(statsUpdateRunnable)
        player?.stop()
        refreshStateFromPlayer()
    }

    private fun syncState(state: PlayerStateSnapshot?) {
        currentState = state ?: emptyState()
        when {
            currentState.isPlaying -> isStreamStarted = true
            !currentState.isCreated || currentState.isReleased -> isStreamStarted = false
        }
        updateUI()
    }

    private fun refreshStateFromPlayer() {
        syncState(player?.getState())
    }

    private fun releasePlayer() {
        clearAllProcessors()
        mainHandler.removeCallbacks(statsUpdateRunnable)
        player?.release()
        player = null
        currentState = emptyState()
        isStreamStarted = false
        resetStats()
        appendLog("播放器已释放")
        updateUI()
    }

    private fun registerObserver() {
        val currentPlayer = player ?: return showNotReady()
        if (isObserverRegistered) {
            showToast("观察处理器已注册")
            return
        }
        isObserverRegistered = currentPlayer.registerYuvProcessor(observerProcessor)
        appendLog(
            if (isObserverRegistered) {
                observerFrameCount.set(0)
                "已注册观察处理器 (OBSERVE_ONLY)\n用途: software pipeline 只读分析、统计、截图辅助"
            } else {
                "观察处理器注册失败"
            }
        )
        updateUI()
    }

    private fun unregisterObserver() {
        val currentPlayer = player ?: return showNotReady()
        isObserverRegistered = !currentPlayer.unregisterYuvProcessor(observerProcessor)
        appendLog(if (!isObserverRegistered) "已注销观察处理器" else "观察处理器注销失败")
        updateUI()
    }

    private fun registerFilter() {
        val currentPlayer = player ?: return showNotReady()
        if (isFilterRegistered) {
            showToast("滤镜处理器已注册")
            return
        }
        isFilterRegistered = currentPlayer.registerYuvProcessor(filterProcessor)
        appendLog(
            if (isFilterRegistered) {
                filterFrameCount.set(0)
                "已注册滤镜处理器 (PROCESS_AND_RENDER)\n用途: software pipeline 滤镜/美颜/水印，处理后输出回写渲染"
            } else {
                "滤镜处理器注册失败"
            }
        )
        updateUI()
    }

    private fun unregisterFilter() {
        val currentPlayer = player ?: return showNotReady()
        isFilterRegistered = !currentPlayer.unregisterYuvProcessor(filterProcessor)
        appendLog(if (!isFilterRegistered) "已注销滤镜处理器" else "滤镜处理器注销失败")
        updateUI()
    }

    private fun registerCustom() {
        val currentPlayer = player ?: return showNotReady()
        if (isCustomRegistered) {
            showToast("自定义处理器已注册")
            return
        }
        isCustomRegistered = currentPlayer.registerYuvProcessor(customProcessor)
        appendLog(
            if (isCustomRegistered) {
                customFrameCount.set(0)
                "已注册自定义渲染处理器 (CUSTOM_RENDER_ONLY)\n用途: software pipeline 自定义 OpenGL 渲染、推流输出\n注意: 该模式会跳过内置渲染；未自行出图时画面会黑屏"
            } else {
                "自定义渲染处理器注册失败"
            }
        )
        updateUI()
    }

    private fun unregisterCustom() {
        val currentPlayer = player ?: return showNotReady()
        isCustomRegistered = !currentPlayer.unregisterYuvProcessor(customProcessor)
        appendLog(if (!isCustomRegistered) "已注销自定义渲染处理器" else "自定义渲染处理器注销失败")
        updateUI()
    }

    private fun registerAsync() {
        val currentPlayer = player ?: return showNotReady()
        if (isAsyncRegistered) {
            showToast("异步处理器已注册")
            return
        }
        isAsyncRegistered = currentPlayer.registerYuvProcessor(asyncProcessor)
        appendLog(
            if (isAsyncRegistered) {
                asyncFrameCount.set(0)
                asyncCompletedCount.set(0)
                "已注册异步处理器 (IAsyncYUVProcessor)\n用途: software pipeline 异步快照分析、云端处理\n特性: 走 owned snapshot，不阻塞解码线程"
            } else {
                "异步处理器注册失败"
            }
        )
        updateUI()
    }

    private fun unregisterAsync() {
        val currentPlayer = player ?: return showNotReady()
        isAsyncRegistered = !currentPlayer.unregisterYuvProcessor(asyncProcessor)
        appendLog(if (!isAsyncRegistered) "已注销异步处理器" else "异步处理器注销失败")
        updateUI()
    }

    private fun setBuiltinRender(enable: Boolean) {
        val currentPlayer = player ?: return showNotReady()
        currentPlayer.setBuiltinRenderEnabled(enable)
        appendLog(if (enable) "已启用 software pipeline 内置渲染" else "已禁用 software pipeline 内置渲染")
        updateUI()
    }

    private fun clearAllProcessors() {
        player?.clearYuvProcessors()
        player?.resetYuvPerformanceStats()
        isObserverRegistered = false
        isFilterRegistered = false
        isCustomRegistered = false
        isAsyncRegistered = false
        resetStats()
        appendLog("已清空所有处理器并重置 YUV 统计")
        updateUI()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isSurfaceReady = true
        appendLog("Surface 已就绪")
        if (player != null) {
            appendLog("Surface 由 StreamPlayer 自动接管")
        }
        refreshStateFromPlayer()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        appendLog("Surface 尺寸变化: ${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isSurfaceReady = false
        appendLog("Surface 已销毁")
        refreshStateFromPlayer()
    }

    override fun onResume() {
        super.onResume()
        refreshStateFromPlayer()
    }

    override fun onPause() {
        mainHandler.removeCallbacks(statsUpdateRunnable)
        refreshStateFromPlayer()
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        releasePlayer()
        asyncExecutor.shutdown()
        try {
            if (!asyncExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            asyncExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        super.onDestroy()
    }

    private fun updateUI() {
        runOnUiThread {
            val currentPlayer = player
            val hasPlayer = currentPlayer != null && currentPlayer.isReleased() == false
            val isPending = currentState.isOperationPending
            val isPlaying = hasPlayer && (currentState.isPlaying || isStreamStarted)
            val renderEnabled = currentPlayer?.isBuiltinRenderEnabled() == true
            val canOperatePlayer = hasPlayer && !isPending
            val perfStats = currentPlayer?.getYuvPerformanceStats()

            startStreamButton.isEnabled = isSurfaceReady && !isPlaying && !isPending
            stopStreamButton.isEnabled = hasPlayer && isPlaying && !isPending
            destroyStreamButton.isEnabled = canOperatePlayer

            registerObserverButton.isEnabled = canOperatePlayer && !isObserverRegistered
            unregisterObserverButton.isEnabled = canOperatePlayer && isObserverRegistered
            registerFilterButton.isEnabled = canOperatePlayer && !isFilterRegistered
            unregisterFilterButton.isEnabled = canOperatePlayer && isFilterRegistered
            registerCustomButton.isEnabled = canOperatePlayer && !isCustomRegistered
            unregisterCustomButton.isEnabled = canOperatePlayer && isCustomRegistered
            registerAsyncButton.isEnabled = canOperatePlayer && !isAsyncRegistered
            unregisterAsyncButton.isEnabled = canOperatePlayer && isAsyncRegistered
            enableRenderButton.isEnabled = canOperatePlayer && !renderEnabled
            disableRenderButton.isEnabled = canOperatePlayer && renderEnabled
            clearProcessorsButton.isEnabled = canOperatePlayer

            val status = when {
                !hasPlayer -> "状态: 未开始播放"
                isPending -> "状态: 操作进行中"
                isPlaying -> "状态: 播放中"
                else -> "状态: 已停止"
            }
            statusTextView.text = "$status | software 内置渲染: ${if (renderEnabled) "开启" else "关闭"}"

            val activeProcessors = mutableListOf<String>()
            if (isObserverRegistered) activeProcessors.add("观察")
            if (isFilterRegistered) activeProcessors.add("滤镜")
            if (isCustomRegistered) activeProcessors.add("自定义")
            if (isAsyncRegistered) activeProcessors.add("异步")
            processorStatusTextView.text = if (activeProcessors.isEmpty()) {
                "处理器状态(software-only): 无"
            } else {
                buildProcessorStatus(activeProcessors, perfStats)
            }

            val total = observerFrameCount.get() + filterFrameCount.get() +
                customFrameCount.get() + asyncFrameCount.get()
            frameCountTextView.text = "帧数: $total (观察 ${observerFrameCount.get()} / 滤镜 ${filterFrameCount.get()} / 自定义 ${customFrameCount.get()} / 异步提交 ${asyncFrameCount.get()} / 完成 ${asyncCompletedCount.get()})"

            lastFrameInfo?.let {
                frameInfoTextView.text = "帧: ${it.width}x${it.height}, ${it.format.name}, PTS=${it.pts}, #${it.frameIndex}"
                yuvDataTextView.text = buildYuvDataText(it, perfStats)
            } ?: run {
                frameInfoTextView.text = "帧信息: 暂无"
                yuvDataTextView.text = "YUV 数据: 暂无"
            }
        }
    }

    private fun updateStatsIfNeeded(count: Long, frame: YUVFrameInfo, source: String) {
        val now = System.currentTimeMillis()
        if (now - lastStatsUpdateTime < FRAME_STATS_UPDATE_INTERVAL_MS) {
            return
        }
        lastStatsUpdateTime = now
        mainHandler.post {
            updateFrameStats(frame, source)
        }
    }

    private fun updateFrameStats(frame: YUVFrameInfo, source: String) {
        val total = observerFrameCount.get() + filterFrameCount.get() +
            customFrameCount.get() + asyncFrameCount.get()
        frameCountTextView.text = String.format(
            Locale.getDefault(),
            "帧数: %d (观察 %d / 滤镜 %d / 自定义 %d / 异步提交 %d / 完成 %d) 来源: %s",
            total,
            observerFrameCount.get(),
            filterFrameCount.get(),
            customFrameCount.get(),
            asyncFrameCount.get(),
            asyncCompletedCount.get(),
            source
        )
        frameInfoTextView.text = String.format(
            Locale.getDefault(),
            "帧: %dx%d, %s, PTS=%d, #%d",
            frame.width,
            frame.height,
            frame.format.name,
            frame.pts,
            frame.frameIndex
        )
        yuvDataTextView.text = String.format(
            Locale.getDefault(),
            "YUV: Y=%d U=%d V=%d 步长=%d/%d",
            frame.getYSize(),
            frame.getUSize(),
            frame.getVSize(),
            frame.yStride,
            frame.uvStride
        )
    }

    private fun resetStats() {
        observerFrameCount.set(0)
        filterFrameCount.set(0)
        customFrameCount.set(0)
        asyncFrameCount.set(0)
        asyncCompletedCount.set(0)
        lastFrameInfo = null
        lastStatsUpdateTime = 0L
    }

    private fun buildProcessorStatus(
        activeProcessors: List<String>,
        perfStats: YuvPerformanceStats?
    ): String {
        val base = "处理器状态(software-only): ${activeProcessors.joinToString(" / ")}"
        if (perfStats == null) {
            return base
        }
        return "$base | 队列 ${perfStats.asyncQueueDepth} | 失败 ${perfStats.asyncTasksFailed} | 丢弃 ${perfStats.asyncTasksDropped}"
    }

    private fun buildYuvDataText(frame: YUVFrameInfo, perfStats: YuvPerformanceStats?): String {
        val base = String.format(
            Locale.getDefault(),
            "YUV: Y=%d U=%d V=%d 步长=%d/%d",
            frame.getYSize(),
            frame.getUSize(),
            frame.getVSize(),
            frame.yStride,
            frame.uvStride
        )
        if (perfStats == null) {
            return base
        }
        return base + String.format(
            Locale.getDefault(),
            "\n异步: queue=%d failed=%d dropped=%d | 回写渲染=%d | JNI wrapper=%d/%d",
            perfStats.asyncQueueDepth,
            perfStats.asyncTasksFailed,
            perfStats.asyncTasksDropped,
            perfStats.renderProcessedFrames,
            perfStats.jniTransientWrappersCreated,
            perfStats.jniOwnedWrappersCreated
        )
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

    private fun showNotReady() {
        showToast("请先创建播放器")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun emptyState(): PlayerStateSnapshot {
        return PlayerStateSnapshot(
            -1,
            false,
            false,
            false,
            false,
            false,
            false,
            StreamStateCode.IDLE,
            StreamErrorCode.OK,
            null,
            null,
            null,
            null,
            1.0f
        )
    }
}

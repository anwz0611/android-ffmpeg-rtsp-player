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
import com.jxj.ffmpegrtsp.lib.StreamConfig
import com.jxj.ffmpegrtsp.lib.StreamPlayer
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
 * YUV 数据处理示例。
 *
 * 使用新的 StreamPlayer API 展示：
 * 1. 注册同步/异步 YUV 处理器
 * 2. 控制内置渲染
 * 3. 读取处理性能和内存统计
 */
class YUVTestActivity : BaseInsetsActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "YUVTestActivity"
        private const val MAX_LOG_LENGTH = 4000
        private const val STATS_UPDATE_INTERVAL_MS = 100L
    }

    private lateinit var videoSurface: SurfaceView
    private lateinit var urlEditText: EditText
    private lateinit var startStreamButton: Button
    private lateinit var stopStreamButton: Button
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_yuv_test)
        applyEdgeToEdge(findViewById(android.R.id.content), top = true, bottom = true)

        initViews()
        setupListeners()
        createProcessors()
        appendLog("YUV 示例已初始化")
        updateUI()
    }

    private fun initViews() {
        videoSurface = findViewById(R.id.videoSurface)
        urlEditText = findViewById(R.id.urlEditText)
        startStreamButton = findViewById(R.id.startStreamButton)
        stopStreamButton = findViewById(R.id.stopStreamButton)
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
        stopStreamButton.setOnClickListener { player?.stop() }
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
                asyncFrameCount.incrementAndGet()
                return YUVProcessResult.passthrough()
            }

            override fun processFrameAsync(frame: YUVFrameInfo): CompletableFuture<YUVProcessResult> {
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

        val currentPlayer = player
        if (currentPlayer != null && !currentPlayer.isReleased()) {
            currentPlayer.play()
            return
        }

        player = StreamPlayer.playWithConfig(
            this,
            videoSurface,
            StreamConfig.Builder(url)
                .useSoftwareDecode(true)
                .audioEnabled(false)
                .enableDisconnectRecovery(true)
                .disconnectRecoveryMaxAttempts(3)
                .disconnectRecoveryIntervalMs(300)
                .disconnectRecoveryNoPacketTimeoutMs(1500)
                .disconnectRecoveryConnectTimeoutMs(3000)
                .build()
        )
            .setOnStateChanged {
                updateUI()
            }
            .setOnPlaybackStarted { videoInfo ->
                appendLog("播放开始: ${videoInfo.width}x${videoInfo.height} ${videoInfo.codec}")
                updateUI()
            }
            .setOnPlaybackStopped {
                appendLog("播放已停止")
                updateUI()
            }
            .setOnError { errorCode, errorMessage ->
                appendLog("播放器错误: $errorCode / $errorMessage")
                updateUI()
            }

        appendLog("播放器已初始化，streamId=${player?.getStreamId()}")
        updateUI()
    }

    private fun releasePlayer() {
        clearAllProcessors()
        player?.release()
        player = null
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
        appendLog(if (isObserverRegistered) "已注册观察处理器" else "观察处理器注册失败")
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
        appendLog(if (isFilterRegistered) "已注册滤镜处理器" else "滤镜处理器注册失败")
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
        appendLog(if (isCustomRegistered) "已注册自定义渲染处理器" else "自定义渲染处理器注册失败")
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
        appendLog(if (isAsyncRegistered) "已注册异步处理器" else "异步处理器注册失败")
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
        appendLog(if (enable) "已启用内置渲染" else "已禁用内置渲染")
        updateUI()
    }

    private fun clearAllProcessors() {
        player?.clearYuvProcessors()
        isObserverRegistered = false
        isFilterRegistered = false
        isCustomRegistered = false
        isAsyncRegistered = false
        resetStats()
        updateUI()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isSurfaceReady = true
        appendLog("Surface 已就绪")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        appendLog("Surface 尺寸变化: ${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isSurfaceReady = false
        appendLog("Surface 已销毁")
    }

    override fun onResume() {
        super.onResume()
        StreamPlayer.onAppForeground()
    }

    override fun onPause() {
        StreamPlayer.onAppBackground()
        super.onPause()
    }

    override fun onDestroy() {
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
            val isPlaying = currentPlayer?.isPlaying() == true

            startStreamButton.isEnabled = !isPlaying
            stopStreamButton.isEnabled = isPlaying

            registerObserverButton.isEnabled = hasPlayer && !isObserverRegistered
            unregisterObserverButton.isEnabled = hasPlayer && isObserverRegistered
            registerFilterButton.isEnabled = hasPlayer && !isFilterRegistered
            unregisterFilterButton.isEnabled = hasPlayer && isFilterRegistered
            registerCustomButton.isEnabled = hasPlayer && !isCustomRegistered
            unregisterCustomButton.isEnabled = hasPlayer && isCustomRegistered
            registerAsyncButton.isEnabled = hasPlayer && !isAsyncRegistered
            unregisterAsyncButton.isEnabled = hasPlayer && isAsyncRegistered
            enableRenderButton.isEnabled = hasPlayer
            disableRenderButton.isEnabled = hasPlayer
            clearProcessorsButton.isEnabled = hasPlayer

            val status = when {
                !hasPlayer -> "状态: 未开始播放"
                isPlaying -> "状态: 播放中"
                else -> "状态: 待播放"
            }
            val renderEnabled = currentPlayer?.isBuiltinRenderEnabled() == true
            statusTextView.text = "$status | 内置渲染: ${if (renderEnabled) "开启" else "关闭"}"

            val activeProcessors = mutableListOf<String>()
            if (isObserverRegistered) activeProcessors.add("观察")
            if (isFilterRegistered) activeProcessors.add("滤镜")
            if (isCustomRegistered) activeProcessors.add("自定义")
            if (isAsyncRegistered) activeProcessors.add("异步")
            processorStatusTextView.text = if (activeProcessors.isEmpty()) {
                "处理器状态: 无"
            } else {
                "处理器状态: ${activeProcessors.joinToString(" / ")}"
            }

            val total = observerFrameCount.get() + filterFrameCount.get() +
                customFrameCount.get() + asyncFrameCount.get()
            frameCountTextView.text = "帧数: $total (观察 ${observerFrameCount.get()} / 滤镜 ${filterFrameCount.get()} / 自定义 ${customFrameCount.get()} / 异步 ${asyncCompletedCount.get()})"

            lastFrameInfo?.let {
                frameInfoTextView.text = "帧: ${it.width}x${it.height}, ${it.format.name}, PTS=${it.pts}, #${it.frameIndex}"
                yuvDataTextView.text = "YUV: Y=${it.getYSize()} U=${it.getUSize()} V=${it.getVSize()} 步长=${it.yStride}/${it.uvStride}"
            } ?: run {
                frameInfoTextView.text = "帧信息: 暂无"
                yuvDataTextView.text = "YUV 数据: 暂无"
            }
        }
    }

    private fun updateStatsIfNeeded(count: Long, frame: YUVFrameInfo, source: String) {
        val now = System.currentTimeMillis()
        if (now - lastStatsUpdateTime < STATS_UPDATE_INTERVAL_MS) {
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
            "帧数: %d (观察 %d / 滤镜 %d / 自定义 %d / 异步 %d/%d) 来源: %s",
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
}

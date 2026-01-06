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
import androidx.appcompat.app.AppCompatActivity
import com.jxj.ffmpegrtsp.lib.FFmpegCallbacks
import com.jxj.ffmpegrtsp.lib.FFmpegRTSPLibrary
import com.jxj.ffmpegrtsp.lib.VideoInfo
import com.jxj.ffmpegrtsp.lib.yuv.IYUVFrameProcessor
import com.jxj.ffmpegrtsp.lib.yuv.IAsyncYUVProcessor
import com.jxj.ffmpegrtsp.lib.yuv.YUVFrameInfo
import com.jxj.ffmpegrtsp.lib.yuv.YUVProcessMode
import com.jxj.ffmpegrtsp.lib.yuv.YUVProcessResult
import com.jxj.ffmpegrtsp.lib.yuv.YUVPlugin
import com.jxj.ffmpegrtsp.lib.yuv.YUVPluginManager
import com.jxj.ffmpegrtsp.lib.yuv.YUVPluginConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * YUV 数据暴露功能测试 Activity
 *
 * 测试三种处理模式：
 * 1. OBSERVE_ONLY (只读观察模式) - AI 分析、统计、截图等
 * 2. PROCESS_AND_RENDER (处理并渲染模式) - 滤镜、美颜、水印等
 * 3. CUSTOM_RENDER_ONLY (自定义渲染模式) - 完全自定义 OpenGL 渲染
 */
class YUVTestActivity : AppCompatActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "YUVTestActivity"
        private const val MAX_LOG_LENGTH = 4000
        private const val STATS_UPDATE_INTERVAL_MS = 100L
    }

    // UI组件
    private lateinit var videoSurface: SurfaceView
    private lateinit var urlEditText: EditText
    private lateinit var createStreamButton: Button
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

    // 流管理
    private var currentStreamId = -1
    private var isStreamCreated = false
    private var isStreamStarted = false
    private var isSurfaceReady = false


    // 四种处理器
    private lateinit var observerProcessor: IYUVFrameProcessor   // 只读观察处理器（同步）
    private lateinit var filterProcessor: IYUVFrameProcessor     // 滤镜处理处理器（同步）
    private lateinit var customProcessor: IYUVFrameProcessor     // 自定义渲染处理器（同步）
    private lateinit var asyncProcessor: IAsyncYUVProcessor       // 异步AI分析处理器（异步）

    private var isObserverRegistered = false
    private var isFilterRegistered = false
    private var isCustomRegistered = false
    private var isAsyncRegistered = false

    // 统计
    private val observerFrameCount = AtomicLong(0)
    private val filterFrameCount = AtomicLong(0)
    private val customFrameCount = AtomicLong(0)
    private val asyncFrameCount = AtomicLong(0)
    private val asyncCompletedCount = AtomicLong(0)
    @Volatile
    private var lastFrameInfo: YUVFrameInfo? = null

    // 异步处理线程池
    private val asyncExecutor: ExecutorService = Executors.newFixedThreadPool(2)

    // UI 更新
    private val mainHandler = Handler(Looper.getMainLooper())
    private val logBuilder = StringBuilder()
    private var lastStatsUpdateTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_yuv_test)

        appendLog("🚀 YUVTestActivity 创建")

        initViews()
        setupListeners()
        createProcessors()
        updateUI()

        appendLog("✅ 初始化完成 - 三种处理模式就绪")
    }

    private fun initViews() {
        videoSurface = findViewById(R.id.videoSurface)
        urlEditText = findViewById(R.id.urlEditText)

        // 流控制按钮
        createStreamButton = findViewById(R.id.createStreamButton)
        startStreamButton = findViewById(R.id.startStreamButton)
        stopStreamButton = findViewById(R.id.stopStreamButton)
        destroyStreamButton = findViewById(R.id.destroyStreamButton)

        // 模式1: 只读观察
        registerObserverButton = findViewById(R.id.registerObserverButton)
        unregisterObserverButton = findViewById(R.id.unregisterObserverButton)

        // 模式2: 滤镜处理
        registerFilterButton = findViewById(R.id.registerFilterButton)
        unregisterFilterButton = findViewById(R.id.unregisterFilterButton)

        // 模式3: 自定义渲染
        registerCustomButton = findViewById(R.id.registerCustomButton)
        unregisterCustomButton = findViewById(R.id.unregisterCustomButton)

        // 模式4: 异步AI分析
        registerAsyncButton = findViewById(R.id.registerAsyncButton)
        unregisterAsyncButton = findViewById(R.id.unregisterAsyncButton)

        // 渲染控制
        enableRenderButton = findViewById(R.id.enableRenderButton)
        disableRenderButton = findViewById(R.id.disableRenderButton)
        clearProcessorsButton = findViewById(R.id.clearProcessorsButton)

        // 统计显示
        frameCountTextView = findViewById(R.id.frameCountTextView)
        frameInfoTextView = findViewById(R.id.frameInfoTextView)
        yuvDataTextView = findViewById(R.id.yuvDataTextView)
        statusTextView = findViewById(R.id.statusTextView)
        processorStatusTextView = findViewById(R.id.processorStatusTextView)
        logTextView = findViewById(R.id.logTextView)
        logScrollView = findViewById(R.id.logScrollView)

        // 设置默认URL
        urlEditText.setText("rtsp://stream.strba.sk:1935/strba/VYHLAD_JAZERO.stream")

        // 设置Surface回调
        videoSurface.holder.addCallback(this)
    }

    private fun setupListeners() {
        // 流控制
        createStreamButton.setOnClickListener { createStream() }
        startStreamButton.setOnClickListener { startStream() }
        stopStreamButton.setOnClickListener { stopStream() }
        destroyStreamButton.setOnClickListener { destroyStream() }

        // 模式1: 只读观察
        registerObserverButton.setOnClickListener { registerObserver() }
        unregisterObserverButton.setOnClickListener { unregisterObserver() }

        // 模式2: 滤镜处理
        registerFilterButton.setOnClickListener { registerFilter() }
        unregisterFilterButton.setOnClickListener { unregisterFilter() }

        // 模式3: 自定义渲染
        registerCustomButton.setOnClickListener { registerCustom() }
        unregisterCustomButton.setOnClickListener { unregisterCustom() }

        // 模式4: 异步AI分析
        registerAsyncButton.setOnClickListener { registerAsync() }
        unregisterAsyncButton.setOnClickListener { unregisterAsync() }

        // 渲染控制
        enableRenderButton.setOnClickListener { setBuiltinRender(true) }
        disableRenderButton.setOnClickListener { setBuiltinRender(false) }
        clearProcessorsButton.setOnClickListener { clearAllProcessors() }
    }

    // ============================================================================
    // 创建三种处理器
    // ============================================================================

    private fun createProcessors() {
        // 模式1: 只读观察处理器 (OBSERVE_ONLY)
        // 适用场景: AI 分析、人脸检测、物体识别、数据统计、截图
        observerProcessor = object : IYUVFrameProcessor {
            override fun getPriority(): Int = 10 // 高优先级，最先执行

            override fun getProcessMode(): YUVProcessMode = YUVProcessMode.OBSERVE_ONLY

            override fun onProcessFrame(frame: YUVFrameInfo): YUVProcessResult {
                val count = observerFrameCount.incrementAndGet()
                lastFrameInfo = frame

                // 模拟 AI 分析（只读取数据，不修改）
                // 实际应用中可以：检测人脸、识别物体、计算直方图等

                updateStatsIfNeeded(count, frame, "👁️ 观察")

                if (count % 150 == 0L) {
                    val yValue = frame.yBuffer?.get(0)?.toInt()?.and(0xFF) ?: 0
                    mainHandler.post {
                        appendLog("👁️ [观察] 已分析 $count 帧, Y均值采样=0x${String.format("%02X", yValue)}")
                    }
                }

                // 返回透传，不影响渲染
                return YUVProcessResult.passthrough()
            }
        }

        // 模式2: 滤镜处理处理器 (PROCESS_AND_RENDER)
        // 适用场景: 滤镜效果、美颜处理、水印叠加、画面调整
        filterProcessor = object : IYUVFrameProcessor {
            override fun getPriority(): Int = 50 // 中等优先级

            override fun getProcessMode(): YUVProcessMode = YUVProcessMode.PROCESS_AND_RENDER

            override fun onProcessFrame(frame: YUVFrameInfo): YUVProcessResult {
                val count = filterFrameCount.incrementAndGet()
                lastFrameInfo = frame

                // 模拟滤镜处理
                // 实际应用中可以：应用美颜、添加水印、调整亮度对比度等
                // 然后返回处理后的数据让内置渲染器使用

                updateStatsIfNeeded(count, frame, "🎨 滤镜")

                if (count % 150 == 0L) {
                    mainHandler.post {
                        appendLog("🎨 [滤镜] 已处理 $count 帧, 尺寸=${frame.width}x${frame.height}")
                    }
                }

                // 返回透传（实际滤镜应返回处理后的数据）
                // return YUVProcessResult.renderProcessed(processedY, processedU, processedV)
                return YUVProcessResult.passthrough()
            }
        }

        // 模式3: 自定义渲染处理器 (CUSTOM_RENDER_ONLY)
        // 适用场景: 自定义 OpenGL 渲染、推流编码、多路视频合成
        customProcessor = object : IYUVFrameProcessor {
            override fun getPriority(): Int = 100 // 低优先级，最后执行

            override fun getProcessMode(): YUVProcessMode = YUVProcessMode.CUSTOM_RENDER_ONLY

            override fun onProcessFrame(frame: YUVFrameInfo): YUVProcessResult {
                val count = customFrameCount.incrementAndGet()
                lastFrameInfo = frame

                // 自定义渲染逻辑
                // 实际应用中可以：
                // - 自己的 OpenGL 渲染管线
                // - 推流编码器
                // - 多路视频合成

                updateStatsIfNeeded(count, frame, "🖥️ 自定义")

                if (count % 150 == 0L) {
                    mainHandler.post {
                        appendLog("🖥️ [自定义] 已渲染 $count 帧, 格式=${frame.format.name}")
                    }
                }

                // 跳过内置渲染，由自己处理
                return YUVProcessResult.skipRender()
            }
        }

        // 模式4: 异步AI分析处理器 (IAsyncYUVProcessor)
        // 适用场景: 耗时AI分析（人脸检测、物体识别）、云端分析、复杂图像处理
        asyncProcessor = object : IAsyncYUVProcessor {
            override fun getPriority(): Int = 20 // 较高优先级

            override fun getProcessMode(): YUVProcessMode = YUVProcessMode.OBSERVE_ONLY // 只读观察，不影响渲染

            override fun onProcessFrame(frame: YUVFrameInfo): YUVProcessResult {
                // 同步快速返回（不阻塞解码线程）
                asyncFrameCount.incrementAndGet()
                return YUVProcessResult.passthrough()
            }

            override fun processFrameAsync(frame: YUVFrameInfo): CompletableFuture<YUVProcessResult> {
                // 异步处理（在独立线程执行，不阻塞解码线程）
                return CompletableFuture.supplyAsync({
                    try {
                        // 模拟耗时AI分析（如人脸检测、物体识别）
                        // 实际应用中：
                        // - 调用AI模型进行推理
                        // - 云端API分析
                        // - 复杂图像处理算法

                        // 模拟处理时间（10-50ms）
                        Thread.sleep(20)

                        // 模拟AI分析结果
                        val completed = asyncCompletedCount.incrementAndGet()

                        // 更新UI（切换到主线程）
                        mainHandler.post {
                            if (completed % 30 == 0L) {
                                appendLog("🤖 [异步AI] 已完成 $completed 帧分析, 当前帧=${frame.frameIndex}")
                            }
                        }

                        // 返回透传（不影响渲染）
                        YUVProcessResult.passthroughWithMetadata("AI分析完成: frameIndex=${frame.frameIndex}")

                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        YUVProcessResult.passthrough()
                    } catch (e: Exception) {
                        Log.e(TAG, "异步处理异常", e)
                        YUVProcessResult.passthrough()
                    }
                }, asyncExecutor)
            }

            override fun getAsyncTimeout(): Long = 2000 // 2秒超时

            override fun onAsyncProcessComplete(streamId: Int, frameIndex: Long, result: YUVProcessResult) {
                // 处理完成回调（可选）
                Log.d(TAG, "异步处理完成: streamId=$streamId, frameIndex=$frameIndex")
            }

            override fun onAsyncProcessError(streamId: Int, frameIndex: Long, error: Throwable) {
                // 处理错误回调（可选）
                Log.e(TAG, "异步处理错误: streamId=$streamId, frameIndex=$frameIndex", error)
            }

            override fun supportsAsync(): Boolean = true // 明确标记支持异步
        }
    }

    // ============================================================================
    // 流管理
    // ============================================================================

    private fun createStream() {
        val url = urlEditText.text.toString().trim()
        if (url.isEmpty()) {
            showToast("请输入RTSP URL")
            return
        }

        if (!isSurfaceReady) {
            showToast("Surface 未就绪，请稍候")
            appendLog("⚠️ Surface 未就绪，请等待")
            return
        }

        appendLog("🚀 创建流: $url")

        // 使用软件解码（必须使用软件解码才能获取 YUV 数据）
        val streamId = FFmpegRTSPLibrary.createStreamWithDecodeMode(url, true)

        if (streamId < 0) {
            showToast("创建流失败")
            appendLog("❌ 创建流失败")
            return
        }

        currentStreamId = streamId
        isStreamCreated = true
        isStreamStarted = false

        // 设置 Surface（确保 Surface 已就绪）
        FFmpegRTSPLibrary.setSurface(streamId, videoSurface.holder.surface)
        appendLog("✅ 流创建成功: ID=$streamId, Surface 已绑定")

        showToast("流创建成功: ID=$streamId")

        updateUI()
    }

    private fun startStream() {
        if (!isStreamCreated || currentStreamId < 0) {
            showToast("请先创建流")
            return
        }

        appendLog("🚀 启动流: ID=$currentStreamId")

        FFmpegRTSPLibrary.startPlayAsync(currentStreamId, object : FFmpegCallbacks.PlaybackStartCallback {
            override fun onPlaybackStarted(streamId: Int, videoInfo: VideoInfo?) {
                runOnUiThread {
                    isStreamStarted = true
                    showToast("流启动成功")
                    appendLog("✅ 流启动成功")

                    if (videoInfo != null) {
                        appendLog("📹 视频信息就绪: $videoInfo")
                    } else {
                        appendLog("📹 视频信息暂未就绪")
                    }

                    updateUI()
                }
            }

            override fun onPlaybackError(streamId: Int, errorCode: Int, errorMessage: String) {
                runOnUiThread {
                    showToast("启动流失败: $errorMessage")
                    appendLog("❌ 启动失败: $errorMessage")
                    updateUI()
                }
            }
        })
    }

    private fun stopStream() {
        if (!isStreamStarted || currentStreamId < 0) {
            showToast("流未启动")
            return
        }

        appendLog("🛑 停止流")

        FFmpegRTSPLibrary.stopPlayAsync(currentStreamId, object : FFmpegCallbacks.PlaybackStopCallback {
            override fun onPlaybackStopped(streamId: Int) {
                runOnUiThread {
                    isStreamStarted = false
                    showToast("流停止成功")
                    appendLog("✅ 流停止成功")
                    updateUI()
                }
            }

            override fun onPlaybackError(streamId: Int, errorCode: Int, errorMessage: String) {
                runOnUiThread {
                    isStreamStarted = false
                    showToast("流停止失败: $errorMessage")
                    appendLog("❌ 停止流失败: $errorMessage")
                    updateUI()
                }
            }
        })
    }

    private fun destroyStream() {
        if (!isStreamCreated || currentStreamId < 0) {
            showToast("没有可销毁的流")
            return
        }

        appendLog("🗑️ 销毁流")

        // 先清空所有处理器
        clearAllProcessors()

        val result = FFmpegRTSPLibrary.destroyStream(currentStreamId)
        if (result == 0) {
            currentStreamId = -1
            isStreamCreated = false
            isStreamStarted = false
            resetStats()
            showToast("流销毁成功")
            appendLog("✅ 流销毁成功")
        } else {
            appendLog("❌ 销毁流失败")
        }

        updateUI()
    }

    // ============================================================================
    // 处理器注册/注销 - 模式1: 只读观察
    // ============================================================================

    private fun registerObserver() {
        if (!checkStreamReady()) return
        if (isObserverRegistered) {
            showToast("观察处理器已注册")
            return
        }

        appendLog("👁️ 注册只读观察处理器...")

        if (FFmpegRTSPLibrary.registerYUVProcessor(currentStreamId, observerProcessor)) {
            isObserverRegistered = true
            observerFrameCount.set(0)
            showToast("观察处理器注册成功")
            appendLog("✅ 观察处理器注册成功 (OBSERVE_ONLY)")
            appendLog("   用途: AI分析、统计、截图 - 不影响渲染")
        } else {
            appendLog("❌ 观察处理器注册失败")
        }
        updateUI()
    }

    private fun unregisterObserver() {
        if (!checkStreamReady()) return
        if (!isObserverRegistered) {
            showToast("观察处理器未注册")
            return
        }

        if (FFmpegRTSPLibrary.unregisterYUVProcessor(currentStreamId, observerProcessor)) {
            isObserverRegistered = false
            appendLog("✅ 观察处理器已注销")
        } else {
            appendLog("❌ 注销失败")
        }
        updateUI()
    }

    // ============================================================================
    // 处理器注册/注销 - 模式2: 滤镜处理
    // ============================================================================

    private fun registerFilter() {
        if (!checkStreamReady()) return
        if (isFilterRegistered) {
            showToast("滤镜处理器已注册")
            return
        }

        appendLog("🎨 注册滤镜处理器...")

        if (FFmpegRTSPLibrary.registerYUVProcessor(currentStreamId, filterProcessor)) {
            isFilterRegistered = true
            filterFrameCount.set(0)
            showToast("滤镜处理器注册成功")
            appendLog("✅ 滤镜处理器注册成功 (PROCESS_AND_RENDER)")
            appendLog("   用途: 滤镜效果、美颜、水印 - 处理后数据替换渲染")
        } else {
            appendLog("❌ 滤镜处理器注册失败")
        }
        updateUI()
    }

    private fun unregisterFilter() {
        if (!checkStreamReady()) return
        if (!isFilterRegistered) {
            showToast("滤镜处理器未注册")
            return
        }

        if (FFmpegRTSPLibrary.unregisterYUVProcessor(currentStreamId, filterProcessor)) {
            isFilterRegistered = false
            appendLog("✅ 滤镜处理器已注销")
        } else {
            appendLog("❌ 注销失败")
        }
        updateUI()
    }

    // ============================================================================
    // 处理器注册/注销 - 模式3: 自定义渲染
    // ============================================================================

    private fun registerCustom() {
        if (!checkStreamReady()) return
        if (isCustomRegistered) {
            showToast("自定义渲染处理器已注册")
            return
        }

        appendLog("🖥️ 注册自定义渲染处理器...")

        if (FFmpegRTSPLibrary.registerYUVProcessor(currentStreamId, customProcessor)) {
            isCustomRegistered = true
            customFrameCount.set(0)
            showToast("自定义渲染处理器注册成功")
            appendLog("✅ 自定义渲染处理器注册成功 (CUSTOM_RENDER_ONLY)")
            appendLog("   用途: OpenGL自定义渲染、推流 - 跳过内置渲染")
            appendLog("   ⚠️ 注意: 此模式会跳过内置渲染，画面可能变黑")
        } else {
            appendLog("❌ 自定义渲染处理器注册失败")
        }
        updateUI()
    }

    private fun unregisterCustom() {
        if (!checkStreamReady()) return
        if (!isCustomRegistered) {
            showToast("自定义渲染处理器未注册")
            return
        }

        if (FFmpegRTSPLibrary.unregisterYUVProcessor(currentStreamId, customProcessor)) {
            isCustomRegistered = false
            appendLog("✅ 自定义渲染处理器已注销")
        } else {
            appendLog("❌ 注销失败")
        }
        updateUI()
    }

    // ============================================================================
    // 处理器注册/注销 - 模式4: 异步AI分析
    // ============================================================================

    private fun registerAsync() {
        if (!checkStreamReady()) return
        if (isAsyncRegistered) {
            showToast("异步处理器已注册")
            return
        }

        appendLog("🤖 注册异步AI分析处理器...")

        if (FFmpegRTSPLibrary.registerYUVProcessor(currentStreamId, asyncProcessor)) {
            isAsyncRegistered = true
            asyncFrameCount.set(0)
            asyncCompletedCount.set(0)
            showToast("异步处理器注册成功")
            appendLog("✅ 异步AI处理器注册成功 (IAsyncYUVProcessor)")
            appendLog("   用途: 耗时AI分析、云端处理 - 不阻塞解码线程")
            appendLog("   ⚡ 优势: 异步执行，不影响播放流畅度")
        } else {
            appendLog("❌ 异步处理器注册失败")
        }
        updateUI()
    }

    private fun unregisterAsync() {
        if (!checkStreamReady()) return
        if (!isAsyncRegistered) {
            showToast("异步处理器未注册")
            return
        }

        if (FFmpegRTSPLibrary.unregisterYUVProcessor(currentStreamId, asyncProcessor)) {
            isAsyncRegistered = false
            appendLog("✅ 异步处理器已注销")
        } else {
            appendLog("❌ 注销失败")
        }
        updateUI()
    }

    // ============================================================================
    // 渲染控制
    // ============================================================================

    private fun setBuiltinRender(enable: Boolean) {
        if (!checkStreamReady()) return

        FFmpegRTSPLibrary.setBuiltinRenderEnabled(currentStreamId, enable)
        showToast(if (enable) "内置渲染已启用" else "内置渲染已禁用")
        appendLog((if (enable) "✅ 启用" else "❌ 禁用") + " 内置渲染")
        updateUI()
    }

    private fun clearAllProcessors() {
        if (currentStreamId >= 0) {
            FFmpegRTSPLibrary.clearYUVProcessors(currentStreamId)
        }
        isObserverRegistered = false
        isFilterRegistered = false
        isCustomRegistered = false
        isAsyncRegistered = false
        resetStats()
        appendLog("🗑️ 已清空所有处理器")
        updateUI()
    }

    // ============================================================================
    // Surface 回调
    // ============================================================================

    override fun surfaceCreated(holder: SurfaceHolder) {
        appendLog("🎬 Surface 创建就绪")
        isSurfaceReady = true
        if (currentStreamId >= 0) {
            FFmpegRTSPLibrary.setSurface(currentStreamId, holder.surface)
            appendLog("✅ Surface 已重新绑定到流")
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        appendLog("🎬 Surface 变化: ${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        appendLog("🗑️ Surface 销毁")
        isSurfaceReady = false
        if (currentStreamId >= 0) {
            FFmpegRTSPLibrary.onSurfaceDestroyed(currentStreamId)
        }
    }

    // ============================================================================
    // 生命周期
    // ============================================================================

    override fun onPause() {
        super.onPause()
        FFmpegRTSPLibrary.onAppBackground()
    }

    override fun onResume() {
        super.onResume()
        FFmpegRTSPLibrary.onAppForeground()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (currentStreamId >= 0) {
            try {
                FFmpegRTSPLibrary.clearYUVProcessors(currentStreamId)
                FFmpegRTSPLibrary.destroyStreamAsync(currentStreamId)
            } catch (e: Exception) {
                Log.e(TAG, "销毁流异常", e)
            }
        }

        // 关闭异步处理线程池
        asyncExecutor.shutdown()
        try {
            if (!asyncExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            asyncExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    // ============================================================================
    // UI 更新
    // ============================================================================

    private fun updateUI() {
        runOnUiThread {
            // 流控制按钮
            createStreamButton.isEnabled = !isStreamCreated
            startStreamButton.isEnabled = isStreamCreated && !isStreamStarted
            stopStreamButton.isEnabled = isStreamStarted
            destroyStreamButton.isEnabled = isStreamCreated

            // 处理器按钮状态
            val canRegister = isStreamCreated
            registerObserverButton.isEnabled = canRegister && !isObserverRegistered
            unregisterObserverButton.isEnabled = canRegister && isObserverRegistered
            registerFilterButton.isEnabled = canRegister && !isFilterRegistered
            unregisterFilterButton.isEnabled = canRegister && isFilterRegistered
            registerCustomButton.isEnabled = canRegister && !isCustomRegistered
            unregisterCustomButton.isEnabled = canRegister && isCustomRegistered
            registerAsyncButton.isEnabled = canRegister && !isAsyncRegistered
            unregisterAsyncButton.isEnabled = canRegister && isAsyncRegistered

            // 渲染控制
            enableRenderButton.isEnabled = isStreamCreated
            disableRenderButton.isEnabled = isStreamCreated
            clearProcessorsButton.isEnabled = isStreamCreated

            // 状态显示
            val status = StringBuilder("状态: ")
            when {
                !isStreamCreated -> status.append("未创建流")
                !isStreamStarted -> status.append("流已创建，未启动")
                else -> status.append("▶️ 播放中")
            }

            if (isStreamCreated && currentStreamId >= 0) {
                val renderEnabled = FFmpegRTSPLibrary.isBuiltinRenderEnabled(currentStreamId)
                status.append(" | 内置渲染: ").append(if (renderEnabled) "✅" else "❌")
            }

            statusTextView.text = status.toString()

            // 处理器状态
            val procStatus = StringBuilder("📋 处理器状态: ")
            var count = 0
            if (isObserverRegistered) { procStatus.append("👁️观察 "); count++ }
            if (isFilterRegistered) { procStatus.append("🎨滤镜 "); count++ }
            if (isCustomRegistered) { procStatus.append("🖥️自定义 "); count++ }
            if (isAsyncRegistered) { procStatus.append("🤖异步 "); count++ }
            if (count == 0) {
                procStatus.append("无")
            } else {
                procStatus.append("(共${count}个)")
            }
            processorStatusTextView.text = procStatus.toString()
        }
    }

    private fun updateStatsIfNeeded(count: Long, frame: YUVFrameInfo, source: String) {
        val now = System.currentTimeMillis()
        if (now - lastStatsUpdateTime >= STATS_UPDATE_INTERVAL_MS) {
            lastStatsUpdateTime = now
            mainHandler.post { updateFrameStats(frame, source) }
        }
    }

    private fun updateFrameStats(frame: YUVFrameInfo?, source: String) {
        val total = observerFrameCount.get() + filterFrameCount.get() + customFrameCount.get() + asyncFrameCount.get()

        frameCountTextView.text = String.format(
            Locale.getDefault(),
            "帧数: %d (👁️%d 🎨%d 🖥️%d 🤖%d/%d) 来源:%s",
            total, observerFrameCount.get(), filterFrameCount.get(), customFrameCount.get(), asyncFrameCount.get(), asyncCompletedCount.get(), source
        )

        frame?.let {
            frameInfoTextView.text = String.format(
                Locale.getDefault(),
                "帧: %dx%d, %s, PTS=%d, #%d",
                it.width, it.height, it.format.name,
                it.pts, it.frameIndex
            )

            yuvDataTextView.text = String.format(
                Locale.getDefault(),
                "YUV: Y=%d U=%d V=%d 步长=%d/%d",
                it.getYSize(), it.getUSize(), it.getVSize(),
                it.yStride, it.uvStride
            )
        }
    }

    private fun resetStats() {
        observerFrameCount.set(0)
        filterFrameCount.set(0)
        customFrameCount.set(0)
        asyncFrameCount.set(0)
        asyncCompletedCount.set(0)
        lastFrameInfo = null
    }

    private fun checkStreamReady(): Boolean {
        if (!isStreamCreated || currentStreamId < 0) {
            showToast("请先创建流")
            return false
        }
        return true
    }

    private fun appendLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logLine = "[$timestamp] $message\n"
        Log.i(TAG, message)

        mainHandler.post {
            logBuilder.append(logLine)
            if (logBuilder.length > MAX_LOG_LENGTH) {
                logBuilder.delete(0, logBuilder.length - MAX_LOG_LENGTH)
            }
            logTextView.text = logBuilder.toString()
            logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun showToast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }
}


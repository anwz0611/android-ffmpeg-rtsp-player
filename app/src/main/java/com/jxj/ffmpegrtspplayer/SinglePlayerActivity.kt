package com.jxj.ffmpegrtspplayer

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import com.jxj.ffmpegrtsp.lib.api.PlayerStateSnapshot
import com.jxj.ffmpegrtsp.lib.api.StreamConfig
import com.jxj.ffmpegrtsp.lib.api.StreamErrorCode
import com.jxj.ffmpegrtsp.lib.api.StreamPlayer
import com.jxj.ffmpegrtsp.lib.api.StreamStateCode
import com.jxj.ffmpegrtsp.lib.api.VideoInfo
import com.jxj.ffmpegrtsp.lib.transform.VideoTransformManager
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * 单流播放示例。
 *
 * 适配新的 StreamPlayer 实例式 API，并演示：
 * 1. 首次播放时按配置自动创建播放器
 * 2. 显式调用 play/stop/release
 * 3. 录制、状态监听、生命周期前后台通知
 */
class SinglePlayerActivity : BaseInsetsActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "SinglePlayerActivity"
        private const val UPDATE_INTERVAL_MS = 500L
        private const val DEFAULT_RTSP_URL = "rtsp://192.168.144.130:554"
    }

    private data class UiFormState(
        val rtspUrl: String,
        val rtspKeepAliveEnabled: Boolean,
        val softwareDecode: Boolean,
        val audioEnabled: Boolean,
        val fastSeek: Boolean,
        val recoveryEnabled: Boolean,
        val transportCheckedId: Int,
        val rendererCheckedId: Int,
        val latencyCheckedId: Int,
        val clockCheckedId: Int,
        val bufferSize: String,
        val timeout: String,
        val audioInitBufferMs: String,
        val rtspKeepAliveInterval: String,
        val proxyUrl: String,
        val proxyUsername: String,
        val proxyPassword: String,
        val recoveryMaxAttempts: String,
        val recoveryIntervalMs: String,
        val recoveryNoPacketTimeoutMs: String,
        val recoveryConnectTimeoutMs: String
    )

    private lateinit var etRtspUrl: EditText
    private lateinit var rootScrollView: View
    private lateinit var contentContainer: View
    private lateinit var sourceSection: View
    private lateinit var previewSection: View
    private lateinit var previewSurfaceContainer: View
    private lateinit var configSectionCard: View
    private lateinit var statusInfoSection: View
    private lateinit var btnPlay: Button
    private lateinit var btnStop: Button
    private lateinit var btnRecord: Button
    private lateinit var btnDestroy: Button
    private lateinit var btnToggleOrientation: Button
    private lateinit var surfaceView: SurfaceView
    private lateinit var tvStatus: TextView
    private lateinit var tvLiveMetrics: TextView
    private lateinit var tvStreamInfo: TextView
    private lateinit var tvRecordInfo: TextView
    private lateinit var switchRtspKeepAlive: SwitchMaterial
    private lateinit var switchSoftwareDecode: SwitchMaterial
    private lateinit var switchAudioEnabled: SwitchMaterial
    private lateinit var switchFastSeek: SwitchMaterial
    private lateinit var switchRecoveryEnabled: SwitchMaterial
    private lateinit var rgTransportProtocol: RadioGroup
    private lateinit var rgRendererType: RadioGroup
    private lateinit var rbRendererHwSurfaceDirect: RadioButton
    private lateinit var rbRendererHwOpenGlTexture: RadioButton
    private lateinit var rbRendererHwCpuFrameOpenGl: RadioButton
    private lateinit var rbRendererSwOpenGl: RadioButton
    private lateinit var rbRendererSwSurface: RadioButton
    private lateinit var rgLatencyMode: RadioGroup
    private lateinit var rgClockPolicy: RadioGroup
    private lateinit var etBufferSize: EditText
    private lateinit var etTimeout: EditText
    private lateinit var etAudioInitBufferMs: EditText
    private lateinit var etRtspKeepAliveInterval: EditText
    private lateinit var etProxyUrl: EditText
    private lateinit var etProxyUsername: EditText
    private lateinit var etProxyPassword: EditText
    private lateinit var etRecoveryMaxAttempts: EditText
    private lateinit var etRecoveryIntervalMs: EditText
    private lateinit var etRecoveryNoPacketTimeoutMs: EditText
    private lateinit var etRecoveryConnectTimeoutMs: EditText
    private lateinit var configSectionHeader: View
    private lateinit var configSectionToggle: TextView
    private lateinit var configSectionContent: View

    private lateinit var performanceMonitorCard: CardView
    private lateinit var performanceMonitorHeader: View
    private lateinit var performanceMonitorToggle: TextView
    private lateinit var performanceMonitorContent: View
    private lateinit var performanceMonitorTextView: TextView

    private var player: StreamPlayer? = null
    private var currentState: PlayerStateSnapshot = emptyState()
    private var currentVideoInfo: VideoInfo? = null
    private var lastRecordingPath: String? = null
    private var pendingRecordingFile: File? = null
    private var pendingRecordingDisplayName: String? = null
    private var requestedRecordingFile: File? = null
    private var isPlaybackRequested = false
    private var playbackRequestedAtMs = 0L
    private var playbackStartedAtMs = 0L
    private var firstFrameCostMs: Long? = null
    private var playbackErrorCount = 0
    private var lastErrorSummary: String? = null
    private var isConfigSectionExpanded = false
    private var isPerformanceMonitorExpanded = false
    private var isPerformanceMonitoring = false
    private var monitoringStartTime = 0L
    private var totalUpdates = 0L
    private var pendingUiFormState: UiFormState? = null

    private val performanceMonitorHandler = Handler(Looper.getMainLooper())
    private val performanceMonitorRunnable = object : Runnable {
        override fun run() {
            if (!isPerformanceMonitoring) {
                return
            }
            updatePerformanceStats()
            performanceMonitorHandler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rebindLayoutForCurrentConfiguration(restoreFormState = false)
    }

    private fun initViews() {
        rootScrollView = findViewById(R.id.rootScrollView)
        contentContainer = findViewById(R.id.contentContainer)
        sourceSection = findViewById(R.id.sourceSection)
        previewSection = findViewById(R.id.previewSection)
        previewSurfaceContainer = findViewById(R.id.previewSurfaceContainer)
        configSectionCard = findViewById(R.id.configSectionCard)
        statusInfoSection = findViewById(R.id.statusInfoSection)
        etRtspUrl = findViewById(R.id.et_rtsp_url)
        btnPlay = findViewById(R.id.btn_play)
        btnStop = findViewById(R.id.btn_stop)
        btnRecord = findViewById(R.id.btn_record)
        btnDestroy = findViewById(R.id.btn_destroy)
        btnToggleOrientation = findViewById(R.id.btn_toggle_orientation)
        surfaceView = findViewById(R.id.surface_view)
        tvStatus = findViewById(R.id.tv_status)
        tvLiveMetrics = findViewById(R.id.tv_live_metrics)
        tvStreamInfo = findViewById(R.id.tv_stream_info)
        tvRecordInfo = findViewById(R.id.tv_record_info)
        switchRtspKeepAlive = findViewById(R.id.switch_rtsp_keep_alive)
        switchSoftwareDecode = findViewById(R.id.switch_software_decode)
        switchAudioEnabled = findViewById(R.id.switch_audio_enabled)
        switchFastSeek = findViewById(R.id.switch_fast_seek)
        switchRecoveryEnabled = findViewById(R.id.switch_recovery_enabled)
        rgTransportProtocol = findViewById(R.id.rg_transport_protocol)
        rgRendererType = findViewById(R.id.rg_renderer_type)
        rbRendererHwSurfaceDirect = findViewById(R.id.rb_renderer_hw_surface_direct)
        rbRendererHwOpenGlTexture = findViewById(R.id.rb_renderer_hw_opengl_texture)
        rbRendererHwCpuFrameOpenGl = findViewById(R.id.rb_renderer_hw_cpu_frame_opengl)
        rbRendererSwOpenGl = findViewById(R.id.rb_renderer_sw_opengl)
        rbRendererSwSurface = findViewById(R.id.rb_renderer_sw_surface)
        rgLatencyMode = findViewById(R.id.rg_latency_mode)
        rgClockPolicy = findViewById(R.id.rg_clock_policy)
        etBufferSize = findViewById(R.id.et_buffer_size)
        etTimeout = findViewById(R.id.et_timeout)
        etAudioInitBufferMs = findViewById(R.id.et_audio_init_buffer_ms)
        etRtspKeepAliveInterval = findViewById(R.id.et_rtsp_keep_alive_interval)
        etProxyUrl = findViewById(R.id.et_proxy_url)
        etProxyUsername = findViewById(R.id.et_proxy_username)
        etProxyPassword = findViewById(R.id.et_proxy_password)
        etRecoveryMaxAttempts = findViewById(R.id.et_recovery_max_attempts)
        etRecoveryIntervalMs = findViewById(R.id.et_recovery_interval_ms)
        etRecoveryNoPacketTimeoutMs = findViewById(R.id.et_recovery_no_packet_timeout_ms)
        etRecoveryConnectTimeoutMs = findViewById(R.id.et_recovery_connect_timeout_ms)
        configSectionHeader = findViewById(R.id.configSectionHeader)
        configSectionToggle = findViewById(R.id.configSectionToggle)
        configSectionContent = findViewById(R.id.configSectionContent)

        performanceMonitorCard = findViewById(R.id.performanceMonitorCard)
        performanceMonitorHeader = findViewById(R.id.performanceMonitorHeader)
        performanceMonitorToggle = findViewById(R.id.performanceMonitorToggle)
        performanceMonitorContent = findViewById(R.id.performanceMonitorContent)
        performanceMonitorTextView = findViewById(R.id.performanceMonitorTextView)

        restoreUiFormState(pendingUiFormState ?: defaultUiFormState())
        updateConfigSectionVisibility()
        updateRendererOptions()
        surfaceView.holder.addCallback(this)
    }

    private fun setupListeners() {
        configSectionHeader.setOnClickListener { toggleConfigSection() }
        performanceMonitorHeader.setOnClickListener { togglePerformanceMonitor() }
        switchSoftwareDecode.setOnCheckedChangeListener { _, _ -> updateRendererOptions() }
        btnPlay.setOnClickListener { ensurePlayerAndPlay() }
        btnStop.setOnClickListener { stopPlayer() }
        btnRecord.setOnClickListener { toggleRecording() }
        btnDestroy.setOnClickListener { releasePlayer() }
        btnToggleOrientation.setOnClickListener { toggleOrientation() }
    }

    private fun ensurePlayerAndPlay() {
        val url = etRtspUrl.text.toString().trim()
        if (url.isEmpty()) {
            showToast("请输入 RTSP 地址")
            return
        }

        val currentPlayer = player
        if (currentPlayer != null && !currentPlayer.isReleased()) {
            isPlaybackRequested = true
            playbackRequestedAtMs = System.currentTimeMillis()
            playbackStartedAtMs = 0L
            firstFrameCostMs = null
            lastErrorSummary = null
            currentPlayer.play()
            refreshStateFromPlayer()
            return
        }

        val config = buildStreamConfig(url)

        isPlaybackRequested = true
        playbackRequestedAtMs = System.currentTimeMillis()
        playbackStartedAtMs = 0L
        firstFrameCostMs = null
        playbackErrorCount = 0
        lastErrorSummary = null
        player = StreamPlayer.playWithConfig(this, surfaceView, config)
            .setOnStateChanged { state ->
                Log.i(TAG, "state=${state.streamStateCode}")
                syncState(state)
            }
            .setOnPlaybackStarted { videoInfo ->
                runOnUiThread {
                    isPlaybackRequested = true
                    playbackStartedAtMs = System.currentTimeMillis()
                    if (playbackRequestedAtMs > 0L && firstFrameCostMs == null) {
                        firstFrameCostMs = playbackStartedAtMs - playbackRequestedAtMs
                    }
                    currentVideoInfo = videoInfo
                    refreshStateFromPlayer()
                    Log.i(TAG, "playback started: $videoInfo")
                    showToast("播放已开始")
                    startPerformanceMonitoring()
                    updateUI()
                }
            }
            .setOnPlaybackStopped {
                runOnUiThread {
                    isPlaybackRequested = false
                    playbackRequestedAtMs = 0L
                    refreshStateFromPlayer()
                    Log.i(TAG, "playback stopped")
                    stopPerformanceMonitoring()
                    updateUI()
                }
            }
            .setOnRecordingStarted { outputPath ->
                runOnUiThread {
                    Log.i(TAG, "recording started: $outputPath")
                    pendingRecordingFile = File(outputPath)
                    showToast("录制开始")
                    updateUI()
                }
            }
            .setOnRecordingStopped {
                runOnUiThread {
                    importPendingRecordingToAlbum()
                }
            }
            .setOnError { errorCode, errorMessage ->
                runOnUiThread {
                    isPlaybackRequested = false
                    playbackErrorCount += 1
                    lastErrorSummary = "$errorCode / $errorMessage"
                    refreshStateFromPlayer()
                    Log.e(TAG, "player error: code=$errorCode, message=$errorMessage")
                    showToast("连接失败: $errorMessage")
                    updateUI()
                }
            }

        refreshStateFromPlayer()
        showToast("正在开始预览")
    }

    private fun buildStreamConfig(url: String): StreamConfig {
        val builder = StreamConfig.Builder(url)
            .audioEnabled(switchAudioEnabled.isChecked)
            .rtspTransport(selectedTransportProtocol())
            .rtspKeepAliveEnabled(switchRtspKeepAlive.isChecked)
            .rtspKeepAliveInterval(parsePositiveInt(etRtspKeepAliveInterval, defaultValue = 30))
            .bufferSize(parsePositiveInt(etBufferSize, defaultValue = 4096))
            .timeout(parsePositiveInt(etTimeout, defaultValue = 10_000))
            .enableFastSeek(switchFastSeek.isChecked)
            .latencyMode(selectedLatencyMode())
            .clockPolicy(selectedClockPolicy())
            .audioInitBufferMs(parseBoundedInt(etAudioInitBufferMs, defaultValue = 200, minValue = 20, maxValue = 1000))
            .recoveryEnabled(switchRecoveryEnabled.isChecked)
            .recoveryMaxAttempts(parsePositiveInt(etRecoveryMaxAttempts, defaultValue = 3))
            .recoveryIntervalMs(parsePositiveInt(etRecoveryIntervalMs, defaultValue = 300))
            .recoveryNoPacketTimeoutMs(parsePositiveInt(etRecoveryNoPacketTimeoutMs, defaultValue = 1500))
            .recoveryConnectTimeoutMs(parsePositiveInt(etRecoveryConnectTimeoutMs, defaultValue = 3000))

        etProxyUrl.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            builder.proxyUrl(it)
        }
        etProxyUsername.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            builder.proxyUsername(it)
        }
        etProxyPassword.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            builder.proxyPassword(it)
        }

        if (switchSoftwareDecode.isChecked) {
            builder
                .decodeMode(StreamConfig.DecodeMode.SOFTWARE)
                .softwareRenderMode(selectedSoftwareRenderMode())
        } else {
            builder
                .decodeMode(StreamConfig.DecodeMode.HARDWARE)
                .hardwareRenderMode(selectedHardwareRenderMode())
        }

        return builder.build()
    }

    private fun selectedTransportProtocol(): StreamConfig.TransportProtocol {
        return when (rgTransportProtocol.checkedRadioButtonId) {
            R.id.rb_transport_tcp -> StreamConfig.TransportProtocol.TCP
            R.id.rb_transport_udp -> StreamConfig.TransportProtocol.UDP
            R.id.rb_transport_multicast -> StreamConfig.TransportProtocol.MULTICAST
            else -> StreamConfig.TransportProtocol.AUTO
        }
    }

    private fun selectedHardwareRenderMode(): StreamConfig.HardwareRenderMode {
        return when (rgRendererType.checkedRadioButtonId) {
            R.id.rb_renderer_hw_opengl_texture -> StreamConfig.HardwareRenderMode.OPENGL_TEXTURE
            R.id.rb_renderer_hw_cpu_frame_opengl -> StreamConfig.HardwareRenderMode.CPU_FRAME_OPENGL
            else -> StreamConfig.HardwareRenderMode.SURFACE_DIRECT
        }
    }

    private fun selectedSoftwareRenderMode(): StreamConfig.SoftwareRenderMode {
        return when (rgRendererType.checkedRadioButtonId) {
            R.id.rb_renderer_sw_surface -> StreamConfig.SoftwareRenderMode.SURFACE
            else -> StreamConfig.SoftwareRenderMode.OPENGL
        }
    }

    private fun updateRendererOptions() {
        val softwareDecode = switchSoftwareDecode.isChecked

        rbRendererHwSurfaceDirect.visibility = if (softwareDecode) View.GONE else View.VISIBLE
        rbRendererHwOpenGlTexture.visibility = if (softwareDecode) View.GONE else View.VISIBLE
        rbRendererHwCpuFrameOpenGl.visibility = if (softwareDecode) View.GONE else View.VISIBLE
        rbRendererSwOpenGl.visibility = if (softwareDecode) View.VISIBLE else View.GONE
        rbRendererSwSurface.visibility = if (softwareDecode) View.VISIBLE else View.GONE

        val checkedId = rgRendererType.checkedRadioButtonId
        val validForHardware = checkedId == R.id.rb_renderer_hw_surface_direct ||
            checkedId == R.id.rb_renderer_hw_opengl_texture ||
            checkedId == R.id.rb_renderer_hw_cpu_frame_opengl
        val validForSoftware = checkedId == R.id.rb_renderer_sw_opengl ||
            checkedId == R.id.rb_renderer_sw_surface

        when {
            softwareDecode && !validForSoftware -> rgRendererType.check(R.id.rb_renderer_sw_opengl)
            !softwareDecode && !validForHardware -> rgRendererType.check(R.id.rb_renderer_hw_surface_direct)
        }
    }

    private fun selectedLatencyMode(): StreamConfig.LatencyMode {
        return when (rgLatencyMode.checkedRadioButtonId) {
            R.id.rb_latency_balanced -> StreamConfig.LatencyMode.BALANCED
            else -> StreamConfig.LatencyMode.ULTRA_LOW_LATENCY
        }
    }

    private fun selectedClockPolicy(): StreamConfig.ClockPolicy {
        return when (rgClockPolicy.checkedRadioButtonId) {
            R.id.rb_clock_audio_master -> StreamConfig.ClockPolicy.AUDIO_MASTER
            R.id.rb_clock_video_master -> StreamConfig.ClockPolicy.VIDEO_MASTER
            else -> StreamConfig.ClockPolicy.AUTO
        }
    }

    private fun parsePositiveInt(editText: EditText, defaultValue: Int): Int {
        val rawValue = editText.text.toString().trim()
        if (rawValue.isEmpty()) {
            editText.setText(defaultValue.toString())
            return defaultValue
        }
        return rawValue.toIntOrNull()?.takeIf { it > 0 } ?: run {
            editText.setText(defaultValue.toString())
            showToast("${editText.hint} 无效，已恢复默认值 $defaultValue")
            defaultValue
        }
    }

    private fun parseBoundedInt(
        editText: EditText,
        defaultValue: Int,
        minValue: Int,
        maxValue: Int
    ): Int {
        val rawValue = editText.text.toString().trim()
        if (rawValue.isEmpty()) {
            editText.setText(defaultValue.toString())
            return defaultValue
        }
        return rawValue.toIntOrNull()?.takeIf { it in minValue..maxValue } ?: run {
            editText.setText(defaultValue.toString())
            showToast("${editText.hint} 超出范围，已恢复默认值 $defaultValue")
            defaultValue
        }
    }

    private fun stopPlayer() {
        isPlaybackRequested = false
        playbackRequestedAtMs = 0L
        player?.stop()
        stopPerformanceMonitoring()
        refreshStateFromPlayer()
    }

    private fun toggleRecording() {
        val currentPlayer = player
        if (currentPlayer?.isRecording() == true) {
            currentPlayer.stopRecording()
            return
        }

        if (currentPlayer == null || !isPlayerEffectivelyPlaying(currentPlayer)) {
            showToast("请先开始播放")
            return
        }

        runWithMediaStoreWriteAccess(onGranted = {
            val displayName = "recording_${System.currentTimeMillis()}.mp4"
            val outputFile = MediaStoreSaver.createPendingFile(
                this,
                MediaStoreSaver.Collection.VIDEO,
                displayName
            )
            requestedRecordingFile = outputFile
            pendingRecordingFile = outputFile
            pendingRecordingDisplayName = displayName
            lastRecordingPath = MediaStoreSaver.buildAlbumDisplayPath(
                MediaStoreSaver.Collection.VIDEO,
                displayName
            )
            currentPlayer.startRecording(outputFile.absolutePath)
            updateUI()
        })
    }

    private fun syncState(state: PlayerStateSnapshot?) {
        currentState = state ?: emptyState()
        currentVideoInfo = currentState.lastVideoInfo ?: currentVideoInfo

        when {
            currentState.isPlaying -> isPlaybackRequested = true
            !currentState.isCreated || currentState.isReleased -> isPlaybackRequested = false
        }

        if (!currentState.isPlaying && !isPlaybackRequested) {
            stopPerformanceMonitoring()
        }

        updateUI()
    }

    private fun refreshStateFromPlayer() {
        syncState(player?.getState())
    }

    private fun isPlayerEffectivelyPlaying(
        currentPlayer: StreamPlayer? = player,
        state: PlayerStateSnapshot = currentState
    ): Boolean {
        if (currentPlayer == null || currentPlayer.isReleased()) {
            return false
        }
        return state.isPlaying ||
            currentPlayer.isPlaying() ||
            currentPlayer.isRecording() ||
            isPlaybackRequested
    }

    private fun releasePlayer() {
        stopPerformanceMonitoring()
        player?.release()
        player = null
        currentState = emptyState()
        isPlaybackRequested = false
        playbackRequestedAtMs = 0L
        playbackStartedAtMs = 0L
        firstFrameCostMs = null
        playbackErrorCount = 0
        lastErrorSummary = null
        currentVideoInfo = null
        lastRecordingPath = null
        pendingRecordingFile = null
        pendingRecordingDisplayName = null
        requestedRecordingFile = null
        updateUI()
    }

    private fun importPendingRecordingToAlbum() {
        val sourceFile = pendingRecordingFile
        val displayName = pendingRecordingDisplayName
        if (sourceFile == null || displayName.isNullOrBlank()) {
            showToast("录制已停止")
            updateUI()
            return
        }

        pendingRecordingFile = null
        pendingRecordingDisplayName = null
        showToast("录制已停止，正在保存到相册")
        updateUI()

        thread(name = "single-record-import") {
            runCatching {
                val actualSource = when {
                    MediaStoreSaver.awaitFileReady(sourceFile) -> sourceFile
                    requestedRecordingFile != null &&
                        requestedRecordingFile != sourceFile &&
                        MediaStoreSaver.awaitFileReady(requestedRecordingFile!!) -> requestedRecordingFile!!
                    else -> throw IllegalStateException("源文件不存在或未完成写入: ${sourceFile.absolutePath}")
                }
                val savedMedia = MediaStoreSaver.saveToAlbum(
                    context = this,
                    sourceFile = actualSource,
                    displayName = displayName,
                    mimeType = "video/mp4",
                    collection = MediaStoreSaver.Collection.VIDEO
                )
                if (actualSource.exists()) {
                    actualSource.delete()
                }
                savedMedia
            }.onSuccess { savedMedia ->
                runOnUiThread {
                    requestedRecordingFile = null
                    lastRecordingPath = savedMedia.displayPath
                    showToast("录制已保存到相册")
                    updateUI()
                }
            }.onFailure { error ->
                Log.e(TAG, "failed to import recording", error)
                runOnUiThread {
                    requestedRecordingFile = null
                    lastRecordingPath = sourceFile.absolutePath
                    showToast("保存到相册失败: ${error.message}")
                    updateUI()
                }
            }
        }
    }

    private fun updateUI() {
        runOnUiThread {
            val currentPlayer = player
            val state = currentState
            val hasPlayer = currentPlayer != null && currentPlayer.isReleased() == false
            val isPending = state.isOperationPending
            val isPlaying = isPlayerEffectivelyPlaying(currentPlayer, state)
            val isRecording = state.isRecording || currentPlayer?.isRecording() == true
            val streamId = if (hasPlayer) state.streamId else -1
            val shouldShowPerformanceMonitor = hasPlayer && (isPlaying || isPerformanceMonitoring) && !isLandscape()

            btnPlay.isEnabled = !isPlaying && !isPending
            btnStop.isEnabled = hasPlayer && isPlaying && !isPending
            btnRecord.isEnabled = (isPlaying || isRecording) && !isPending
            btnDestroy.isEnabled = hasPlayer && !isPending
            btnRecord.text = if (isRecording) "停止录制" else "录制"

            performanceMonitorCard.visibility = if (shouldShowPerformanceMonitor) View.VISIBLE else View.GONE
            if (!shouldShowPerformanceMonitor) {
                isPerformanceMonitorExpanded = false
                performanceMonitorContent.visibility = View.GONE
                performanceMonitorToggle.text = "▼"
            }

            val statusText = when {
                !hasPlayer -> "等待预览"
                isPending -> "正在连接"
                isRecording -> "播放中 / 录制中"
                isPlaying -> "播放中"
                else -> "已停止"
            }
            tvStatus.text = statusText
            tvLiveMetrics.text = buildLiveMetricsText(
                hasPlayer = hasPlayer,
                isPlaying = isPlaying,
                isRecording = isRecording,
                state = state
            )

            val infoBuilder = StringBuilder("预览信息")
            if (streamId >= 0) {
                infoBuilder.append(" | 流ID: ").append(streamId)
            }
            state.currentUrl?.takeIf { it.isNotBlank() }?.let {
                infoBuilder.append(" | URL: ").append(it)
            }
            infoBuilder.append(" | 状态码: ").append(state.streamStateCode)
            currentVideoInfo?.let {
                infoBuilder.append(" | ").append(it.width).append("x").append(it.height)
                infoBuilder.append(" @ ").append(it.fps).append("fps")
                infoBuilder.append(" | ").append(it.codec)
            }
            tvStreamInfo.text = infoBuilder.toString()

            tvRecordInfo.text = when {
                isRecording -> "录制中: ${lastRecordingPath ?: "路径待回调"}"
                !lastRecordingPath.isNullOrBlank() -> "最近录制: $lastRecordingPath"
                else -> "录制状态: 未录制"
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        player?.takeIf { !it.isReleased() }?.attachSurface(surfaceView)
        applyScaleTypeForCurrentOrientation()
        refreshStateFromPlayer()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surface changed: ${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surface destroyed")
        refreshStateFromPlayer()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rebindLayoutForCurrentConfiguration(restoreFormState = true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyWindowMode(resources.configuration)
        }
    }

    private fun togglePerformanceMonitor() {
        isPerformanceMonitorExpanded = !isPerformanceMonitorExpanded
        performanceMonitorContent.visibility = if (isPerformanceMonitorExpanded) View.VISIBLE else View.GONE
        performanceMonitorToggle.text = if (isPerformanceMonitorExpanded) "▲" else "▼"
    }

    private fun toggleConfigSection() {
        isConfigSectionExpanded = !isConfigSectionExpanded
        updateConfigSectionVisibility()
    }

    private fun toggleOrientation() {
        requestedOrientation = if (isLandscape()) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun rebindLayoutForCurrentConfiguration(restoreFormState: Boolean) {
        if (restoreFormState && ::etRtspUrl.isInitialized) {
            pendingUiFormState = captureUiFormState()
        } else if (pendingUiFormState == null) {
            pendingUiFormState = defaultUiFormState()
        }

        setContentView(R.layout.activity_single_player)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        initViews()
        applyEdgeToEdge(rootScrollView, top = !landscape, bottom = !landscape)
        setupListeners()
        applyWindowMode(resources.configuration)
        applyScaleTypeForCurrentOrientation()
        player?.takeIf { !it.isReleased() }?.let { currentPlayer ->
            surfaceView.post {
                if (!currentPlayer.isReleased()) {
                    currentPlayer.attachSurface(surfaceView)
                    refreshStateFromPlayer()
                }
            }
        }
        updateUI()
    }

    private fun applyWindowMode(configuration: Configuration) {
        btnToggleOrientation.text = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "退出横屏"
        } else {
            "进入横屏"
        }
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            supportActionBar?.hide()
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else {
            supportActionBar?.show()
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun applyScaleTypeForCurrentOrientation() {
        val currentPlayer = player ?: return
        if (currentPlayer.isReleased()) {
            return
        }
        val streamId = currentPlayer.getStreamId()
        if (streamId < 0) {
            return
        }
        val scaleType = if (isLandscape()) {
            VideoTransformManager.ScaleType.CENTER_CROP
        } else {
            VideoTransformManager.ScaleType.FIT_CENTER
        }
        runCatching {
            VideoTransformManager.setScaleType(streamId, scaleType)
        }.onFailure {
            Log.w(TAG, "设置缩放模式失败: streamId=$streamId, scaleType=$scaleType", it)
        }
    }

    private fun isLandscape(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun updateConfigSectionVisibility() {
        configSectionContent.visibility = if (isConfigSectionExpanded) View.VISIBLE else View.GONE
        configSectionToggle.text = if (isConfigSectionExpanded) "收起" else "展开"
    }

    private fun startPerformanceMonitoring() {
        if (isPerformanceMonitoring) {
            return
        }
        isPerformanceMonitoring = true
        monitoringStartTime = System.currentTimeMillis()
        totalUpdates = 0
        if (!isPerformanceMonitorExpanded) {
            togglePerformanceMonitor()
        }
        updatePerformanceStats()
        performanceMonitorHandler.removeCallbacks(performanceMonitorRunnable)
        performanceMonitorHandler.post(performanceMonitorRunnable)
    }

    private fun stopPerformanceMonitoring() {
        isPerformanceMonitoring = false
        monitoringStartTime = 0L
        totalUpdates = 0L
        performanceMonitorHandler.removeCallbacks(performanceMonitorRunnable)
    }

    private fun updatePerformanceStats() {
        val currentPlayer = player ?: return
        val statsBuilder = StringBuilder()
        val snapshot = currentPlayer.getState()
        val now = System.currentTimeMillis()

        statsBuilder.append("诊断详情\n\n")
        statsBuilder.append("更新时间: ")
            .append(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now)))
            .append('\n')
        statsBuilder.append("流ID: ").append(currentPlayer.getStreamId()).append('\n')
        statsBuilder.append("更新次数: ").append(++totalUpdates).append('\n')

        if (monitoringStartTime > 0L) {
            statsBuilder.append("监控时长: ")
                .append(formatDuration(now - monitoringStartTime))
                .append('\n')
        }

        firstFrameCostMs?.let {
            statsBuilder.append("首帧耗时: ").append(it).append(" ms\n")
        }
        if (playbackStartedAtMs > 0L) {
            statsBuilder.append("连续播放: ")
                .append(formatDuration(now - playbackStartedAtMs))
                .append('\n')
        }
        statsBuilder.append("解码路径: ").append(selectedDecodeModeLabel()).append('\n')
        statsBuilder.append("渲染路径: ").append(selectedRenderModeLabel()).append('\n')
        statsBuilder.append("错误次数: ").append(playbackErrorCount).append('\n')
        if (!lastErrorSummary.isNullOrBlank()) {
            statsBuilder.append("最近错误摘要: ").append(lastErrorSummary).append('\n')
        }

        statsBuilder.append('\n')
        statsBuilder.append("当前状态: ").append(snapshot.streamStateCode).append('\n')
        statsBuilder.append("播放中: ").append(snapshot.isPlaying).append('\n')
        statsBuilder.append("录制中: ").append(snapshot.isRecording).append('\n')
        statsBuilder.append("待处理操作: ").append(snapshot.isOperationPending).append('\n')

        if (snapshot.lastErrorCode != StreamErrorCode.OK || !snapshot.lastErrorMessage.isNullOrBlank()) {
            statsBuilder.append("最近错误: ")
                .append(snapshot.lastErrorCode)
                .append(" / ")
                .append(snapshot.lastErrorMessage)
                .append('\n')
        }

        currentVideoInfo?.let {
            statsBuilder.append('\n')
            statsBuilder.append("视频信息\n")
            statsBuilder.append("分辨率: ").append(it.width).append(" x ").append(it.height).append('\n')
            statsBuilder.append("帧率: ").append(it.fps).append(" fps\n")
            statsBuilder.append("编码: ").append(it.codec).append('\n')
        }

        val streamStats = runCatching { currentPlayer.getStreamStats() }.getOrNull()
        if (!streamStats.isNullOrBlank()) {
            statsBuilder.append('\n')
            statsBuilder.append("底层统计\n")
            statsBuilder.append(streamStats)
        } else {
            statsBuilder.append('\n')
            statsBuilder.append("底层统计\n")
            statsBuilder.append("暂无统计数据")
        }

        performanceMonitorTextView.text = statsBuilder.toString()
    }

    private fun buildLiveMetricsText(
        hasPlayer: Boolean,
        isPlaying: Boolean,
        isRecording: Boolean,
        state: PlayerStateSnapshot
    ): String {
        if (!hasPlayer) {
            return "首帧、在线时长、分辨率和解码方式会在播放后显示。"
        }

        val lines = mutableListOf<String>()
        val line1 = buildString {
            firstFrameCostMs?.let {
                append("首帧 ").append(it).append("ms")
            } ?: append("首帧待采样")
            if (playbackStartedAtMs > 0L && (isPlaying || isRecording)) {
                append(" | 在线 ").append(formatDuration(System.currentTimeMillis() - playbackStartedAtMs))
            }
            append(" | 错误 ").append(playbackErrorCount)
        }
        lines += line1

        val line2 = buildString {
            append(selectedDecodeModeLabel())
            append(" | ").append(selectedRenderModeLabel())
            currentVideoInfo?.let {
                append(" | ").append(it.width).append("x").append(it.height)
                append(" @ ").append(it.fps).append("fps")
            }
        }
        lines += line2

        if (!lastErrorSummary.isNullOrBlank() && state.lastErrorCode != StreamErrorCode.OK) {
            lines += "最近错误: $lastErrorSummary"
        }
        return lines.joinToString("\n")
    }

    private fun selectedDecodeModeLabel(): String {
        return if (switchSoftwareDecode.isChecked) "软解码" else "硬解码"
    }

    private fun selectedRenderModeLabel(): String {
        return if (switchSoftwareDecode.isChecked) {
            when (rgRendererType.checkedRadioButtonId) {
                R.id.rb_renderer_sw_surface -> "Surface 渲染"
                else -> "OpenGL 渲染"
            }
        } else {
            when (rgRendererType.checkedRadioButtonId) {
                R.id.rb_renderer_hw_opengl_texture -> "OpenGL Texture"
                R.id.rb_renderer_hw_cpu_frame_opengl -> "CPU Frame OpenGL"
                else -> "Surface Direct"
            }
        }
    }

    private fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            hours > 0 -> String.format(
                Locale.getDefault(),
                "%d小时%d分%d秒",
                hours,
                minutes % 60,
                seconds % 60
            )
            minutes > 0 -> String.format(Locale.getDefault(), "%d分%d秒", minutes, seconds % 60)
            else -> String.format(Locale.getDefault(), "%d秒", seconds)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        applyWindowMode(resources.configuration)
        refreshStateFromPlayer()
        if (isPlayerEffectivelyPlaying()) {
            startPerformanceMonitoring()
        }
    }

    override fun onPause() {
        refreshStateFromPlayer()
        super.onPause()
    }

    override fun onDestroy() {
        performanceMonitorHandler.removeCallbacks(performanceMonitorRunnable)
        releasePlayer()
        super.onDestroy()
    }

    private fun captureUiFormState(): UiFormState {
        return UiFormState(
            rtspUrl = etRtspUrl.text?.toString().orEmpty(),
            rtspKeepAliveEnabled = switchRtspKeepAlive.isChecked,
            softwareDecode = switchSoftwareDecode.isChecked,
            audioEnabled = switchAudioEnabled.isChecked,
            fastSeek = switchFastSeek.isChecked,
            recoveryEnabled = switchRecoveryEnabled.isChecked,
            transportCheckedId = rgTransportProtocol.checkedRadioButtonId,
            rendererCheckedId = rgRendererType.checkedRadioButtonId,
            latencyCheckedId = rgLatencyMode.checkedRadioButtonId,
            clockCheckedId = rgClockPolicy.checkedRadioButtonId,
            bufferSize = etBufferSize.text?.toString().orEmpty(),
            timeout = etTimeout.text?.toString().orEmpty(),
            audioInitBufferMs = etAudioInitBufferMs.text?.toString().orEmpty(),
            rtspKeepAliveInterval = etRtspKeepAliveInterval.text?.toString().orEmpty(),
            proxyUrl = etProxyUrl.text?.toString().orEmpty(),
            proxyUsername = etProxyUsername.text?.toString().orEmpty(),
            proxyPassword = etProxyPassword.text?.toString().orEmpty(),
            recoveryMaxAttempts = etRecoveryMaxAttempts.text?.toString().orEmpty(),
            recoveryIntervalMs = etRecoveryIntervalMs.text?.toString().orEmpty(),
            recoveryNoPacketTimeoutMs = etRecoveryNoPacketTimeoutMs.text?.toString().orEmpty(),
            recoveryConnectTimeoutMs = etRecoveryConnectTimeoutMs.text?.toString().orEmpty()
        )
    }

    private fun restoreUiFormState(state: UiFormState) {
        etRtspUrl.setText(state.rtspUrl)
        switchRtspKeepAlive.isChecked = state.rtspKeepAliveEnabled
        switchSoftwareDecode.isChecked = state.softwareDecode
        switchAudioEnabled.isChecked = state.audioEnabled
        switchFastSeek.isChecked = state.fastSeek
        switchRecoveryEnabled.isChecked = state.recoveryEnabled
        rgTransportProtocol.check(state.transportCheckedId)
        rgRendererType.check(state.rendererCheckedId)
        rgLatencyMode.check(state.latencyCheckedId)
        rgClockPolicy.check(state.clockCheckedId)
        etBufferSize.setText(state.bufferSize)
        etTimeout.setText(state.timeout)
        etAudioInitBufferMs.setText(state.audioInitBufferMs)
        etRtspKeepAliveInterval.setText(state.rtspKeepAliveInterval)
        etProxyUrl.setText(state.proxyUrl)
        etProxyUsername.setText(state.proxyUsername)
        etProxyPassword.setText(state.proxyPassword)
        etRecoveryMaxAttempts.setText(state.recoveryMaxAttempts)
        etRecoveryIntervalMs.setText(state.recoveryIntervalMs)
        etRecoveryNoPacketTimeoutMs.setText(state.recoveryNoPacketTimeoutMs)
        etRecoveryConnectTimeoutMs.setText(state.recoveryConnectTimeoutMs)
        pendingUiFormState = state
    }

    private fun defaultUiFormState(): UiFormState {
        return UiFormState(
            rtspUrl = DEFAULT_RTSP_URL,
            rtspKeepAliveEnabled = true,
            softwareDecode = false,
            audioEnabled = true,
            fastSeek = true,
            recoveryEnabled = true,
            transportCheckedId = R.id.rb_transport_auto,
            rendererCheckedId = R.id.rb_renderer_hw_surface_direct,
            latencyCheckedId = R.id.rb_latency_ultra_low,
            clockCheckedId = R.id.rb_clock_auto,
            bufferSize = "4096",
            timeout = "10000",
            audioInitBufferMs = "200",
            rtspKeepAliveInterval = "30",
            proxyUrl = "",
            proxyUsername = "",
            proxyPassword = "",
            recoveryMaxAttempts = "3",
            recoveryIntervalMs = "300",
            recoveryNoPacketTimeoutMs = "1500",
            recoveryConnectTimeoutMs = "3000"
        )
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

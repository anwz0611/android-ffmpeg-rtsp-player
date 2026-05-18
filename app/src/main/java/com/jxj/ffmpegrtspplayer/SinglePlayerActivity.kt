package com.jxj.ffmpegrtspplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import com.jxj.ffmpegrtsp.lib.api.PlayerStateSnapshot
import com.jxj.ffmpegrtsp.lib.api.StreamConfig
import com.jxj.ffmpegrtsp.lib.api.StreamErrorCode
import com.jxj.ffmpegrtsp.lib.api.StreamPlayer
import com.jxj.ffmpegrtsp.lib.api.StreamStateCode
import com.jxj.ffmpegrtsp.lib.api.VideoInfo
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
    }

    private lateinit var etRtspUrl: EditText
    private lateinit var btnPlay: Button
    private lateinit var btnStop: Button
    private lateinit var btnRecord: Button
    private lateinit var btnDestroy: Button
    private lateinit var surfaceView: SurfaceView
    private lateinit var tvStatus: TextView
    private lateinit var tvStreamInfo: TextView
    private lateinit var tvRecordInfo: TextView

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
    private var isPerformanceMonitorExpanded = false
    private var isPerformanceMonitoring = false
    private var monitoringStartTime = 0L
    private var totalUpdates = 0L

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
        setContentView(R.layout.activity_single_player)
        applyEdgeToEdge(findViewById(android.R.id.content), top = true, bottom = true)

        initViews()
        setupListeners()
        updateUI()
    }

    private fun initViews() {
        etRtspUrl = findViewById(R.id.et_rtsp_url)
        btnPlay = findViewById(R.id.btn_play)
        btnStop = findViewById(R.id.btn_stop)
        btnRecord = findViewById(R.id.btn_record)
        btnDestroy = findViewById(R.id.btn_destroy)
        surfaceView = findViewById(R.id.surface_view)
        tvStatus = findViewById(R.id.tv_status)
        tvStreamInfo = findViewById(R.id.tv_stream_info)
        tvRecordInfo = findViewById(R.id.tv_record_info)

        performanceMonitorCard = findViewById(R.id.performanceMonitorCard)
        performanceMonitorHeader = findViewById(R.id.performanceMonitorHeader)
        performanceMonitorToggle = findViewById(R.id.performanceMonitorToggle)
        performanceMonitorContent = findViewById(R.id.performanceMonitorContent)
        performanceMonitorTextView = findViewById(R.id.performanceMonitorTextView)

        etRtspUrl.setText("rtsp://192.168.144.130:554")
        surfaceView.holder.addCallback(this)
    }

    private fun setupListeners() {
        performanceMonitorHeader.setOnClickListener { togglePerformanceMonitor() }
        btnPlay.setOnClickListener { ensurePlayerAndPlay() }
        btnStop.setOnClickListener { stopPlayer() }
        btnRecord.setOnClickListener { toggleRecording() }
        btnDestroy.setOnClickListener { releasePlayer() }
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
            currentPlayer.play()
            refreshStateFromPlayer()
            return
        }

        val config = StreamConfig.Builder(url)
            .useSoftwareDecode(true)
            .build()

        isPlaybackRequested = true
        player = StreamPlayer.playWithConfig(this, surfaceView, config)
            .setOnStateChanged { state ->
                Log.i(TAG, "state=${state.streamStateCode}")
                syncState(state)
            }
            .setOnPlaybackStarted { videoInfo ->
                runOnUiThread {
                    isPlaybackRequested = true
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
                    refreshStateFromPlayer()
                    Log.e(TAG, "player error: code=$errorCode, message=$errorMessage")
                    showToast("播放器错误: $errorMessage")
                    updateUI()
                }
            }

        refreshStateFromPlayer()
        showToast("播放器已创建并开始播放")
    }

    private fun stopPlayer() {
        isPlaybackRequested = false
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
            val shouldShowPerformanceMonitor = hasPlayer && (isPlaying || isPerformanceMonitoring)

            btnPlay.isEnabled = !isPlaying && !isPending
            btnStop.isEnabled = hasPlayer && isPlaying && !isPending
            btnRecord.isEnabled = (isPlaying || isRecording) && !isPending
            btnDestroy.isEnabled = hasPlayer && !isPending
            btnRecord.text = if (isRecording) "停止录制" else "开始录制"

            performanceMonitorCard.visibility = if (shouldShowPerformanceMonitor) View.VISIBLE else View.GONE
            if (!shouldShowPerformanceMonitor) {
                isPerformanceMonitorExpanded = false
                performanceMonitorContent.visibility = View.GONE
                performanceMonitorToggle.text = "▼"
            }

            val statusText = when {
                !hasPlayer -> "状态: 未开始播放"
                isPending -> "状态: 操作进行中"
                isRecording -> "状态: 播放中，录制中"
                isPlaying -> "状态: 播放中"
                else -> "状态: 已停止"
            }
            tvStatus.text = statusText

            val infoBuilder = StringBuilder("统计: 单流模式")
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
        refreshStateFromPlayer()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surface changed: ${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surface destroyed")
        refreshStateFromPlayer()
    }

    private fun togglePerformanceMonitor() {
        isPerformanceMonitorExpanded = !isPerformanceMonitorExpanded
        performanceMonitorContent.visibility = if (isPerformanceMonitorExpanded) View.VISIBLE else View.GONE
        performanceMonitorToggle.text = if (isPerformanceMonitorExpanded) "▲" else "▼"
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

        statsBuilder.append("播放器状态监控\n\n")
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

        statsBuilder.append('\n')
        statsBuilder.append("当前状态: ").append(snapshot.streamStateCode).append('\n')
        statsBuilder.append("播放中: ").append(snapshot.isPlaying).append('\n')
        statsBuilder.append("录制中: ").append(snapshot.isRecording).append('\n')
        statsBuilder.append("待处理操作: ").append(snapshot.isOperationPending).append('\n')

        if (snapshot.lastErrorCode != null || !snapshot.lastErrorMessage.isNullOrBlank()) {
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

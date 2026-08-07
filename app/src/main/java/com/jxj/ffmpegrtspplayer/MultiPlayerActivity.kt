package com.jxj.ffmpegrtspplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import com.jxj.ffmpegrtsp.lib.api.AudioOptions
import com.jxj.ffmpegrtsp.lib.api.StreamConfig
import com.jxj.ffmpegrtsp.lib.api.StreamPlayer
import com.jxj.ffmpegrtsp.lib.api.StreamPlayerRuntime
import com.jxj.ffmpegrtsp.lib.api.VideoInfo
import com.jxj.ffmpegrtsp.lib.capture.FrameCaptureRequest
import com.jxj.ffmpegrtsp.lib.capture.FrameCaptureResult
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class MultiPlayerActivity : BaseInsetsActivity() {

    companion object {
        private const val TAG = "MultiPlayerActivity"
        private const val SUMMARY_UPDATE_INTERVAL_MS = 1_000L
    }

    private lateinit var etRtspUrl: EditText
    private lateinit var btnPreset4: Button
    private lateinit var btnPreset9: Button
    private lateinit var btnPreset16: Button
    private lateinit var btnAddStream: Button
    private lateinit var btnPlayAll: Button
    private lateinit var btnStopAll: Button
    private lateinit var btnClearAll: Button
    private lateinit var gridStreamsContainer: GridLayout
    private lateinit var tvStreamCount: TextView
    private lateinit var tvGridSummary: TextView

    private val streamItems = mutableListOf<StreamItem>()
    private val nextDisplayId = AtomicInteger(1)
    private val summaryHandler = Handler(Looper.getMainLooper())
    private val summaryRunnable = object : Runnable {
        override fun run() {
            updateSummary()
            summaryHandler.postDelayed(this, SUMMARY_UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_player)
        applyEdgeToEdge(findViewById(android.R.id.content), top = true, bottom = true)

        initViews()
        setupClickListeners()
        updateGridLayout()
        updateSummary()
    }

    private fun initViews() {
        etRtspUrl = findViewById(R.id.et_rtsp_url)
        btnPreset4 = findViewById(R.id.btn_preset_4)
        btnPreset9 = findViewById(R.id.btn_preset_9)
        btnPreset16 = findViewById(R.id.btn_preset_16)
        btnAddStream = findViewById(R.id.btn_add_stream)
        btnPlayAll = findViewById(R.id.btn_play_all)
        btnStopAll = findViewById(R.id.btn_stop_all)
        btnClearAll = findViewById(R.id.btn_clear_all)
        gridStreamsContainer = findViewById(R.id.grid_streams_container)
        tvStreamCount = findViewById(R.id.tv_stream_count)
        tvGridSummary = findViewById(R.id.tv_grid_summary)
    }

    private fun setupClickListeners() {
        btnPreset4.setOnClickListener { fillPreset(4) }
        btnPreset9.setOnClickListener { fillPreset(9) }
        btnPreset16.setOnClickListener { fillPreset(16) }
        btnAddStream.setOnClickListener { addSingleStreamFromInput() }
        btnPlayAll.setOnClickListener { streamItems.forEach { it.play() } }
        btnStopAll.setOnClickListener { streamItems.forEach { it.stop() } }
        btnClearAll.setOnClickListener { clearAllStreams() }
        updateTopControls()
    }

    private fun addSingleStreamFromInput() {
        val urls = normalizedInputUrls()
        if (urls.isEmpty()) {
            toast("请输入 RTSP 地址")
            return
        }
        val url = urls[streamItems.size % urls.size]
        addStream(url)
        toast("已添加通道")
    }

    private fun fillPreset(targetCount: Int) {
        val urls = normalizedInputUrls()
        if (urls.isEmpty()) {
            toast("请输入 RTSP 地址")
            return
        }
        val currentCount = streamItems.size
        when {
            currentCount < targetCount -> {
                repeat(targetCount - currentCount) { index ->
                    val url = urls[(currentCount + index) % urls.size]
                    addStream(url)
                }
            }
            currentCount > targetCount -> {
                streamItems.drop(targetCount).toList().forEach(::removeStream)
            }
        }
        streamItems.forEach { it.play() }
        toast("已切换到 ${targetCount} 路宫格")
    }

    private fun normalizedInputUrls(): List<String> {
        return etRtspUrl.text.toString()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    private fun addStream(url: String) {
        val streamItem = StreamItem(url = url, displayId = nextDisplayId.getAndIncrement())
        streamItems.add(streamItem)
        val streamView = LayoutInflater.from(this)
            .inflate(R.layout.item_stream, gridStreamsContainer, false)
        streamItem.bind(streamView)
        val layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(0, 0, 0, dpToPx(12))
        }
        streamView.layoutParams = layoutParams
        gridStreamsContainer.addView(streamView)
        updateGridLayout()
        updateSummary()
    }

    private fun removeStream(streamItem: StreamItem) {
        streamItem.release()
        streamItems.remove(streamItem)
        streamItem.view?.let { gridStreamsContainer.removeView(it) }
        updateGridLayout()
        updateSummary()
    }

    private fun clearAllStreams() {
        streamItems.forEach { it.release() }
        streamItems.clear()
        gridStreamsContainer.removeAllViews()
        updateGridLayout()
        updateSummary()
    }

    private fun updateGridLayout() {
        val count = streamItems.size
        val targetColumnCount = when {
            count >= 16 -> 4
            count >= 9 -> 3
            count >= 4 -> 2
            else -> 1
        }
        val itemHeightDp = when {
            count >= 16 -> 128
            count >= 9 -> 168
            count >= 4 -> 212
            else -> 280
        }
        streamItems.forEach { item ->
            val currentView = item.view ?: return@forEach
            currentView.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(0, 0, 0, dpToPx(12))
            }
        }
        gridStreamsContainer.columnCount = targetColumnCount
        streamItems.forEach { item ->
            val currentView = item.view ?: return@forEach
            val videoContainer = currentView.findViewById<View>(R.id.video_container)
            videoContainer.layoutParams = videoContainer.layoutParams.apply {
                height = dpToPx(itemHeightDp)
            }
            currentView.requestLayout()
        }
        updateTopControls()
    }

    private fun updateSummary() {
        val totalCount = streamItems.size
        val activeCount = streamItems.count { it.hasActivePlayer() }
        val playingCount = streamItems.count { it.isPlaying() }
        val recordingCount = streamItems.count { it.isRecording() }
        val readyCount = streamItems.count { it.canStart() }
        val firstFrameSamples = streamItems.mapNotNull { it.firstFrameCostMs }
        val oldestPlaybackDuration = streamItems
            .filter { it.isPlaying() }
            .map { it.playingDurationMs() }
            .maxOrNull()
            ?: 0L
        val totalErrors = streamItems.sumOf { it.errorCount }

        tvStreamCount.text = "在线 $activeCount / $totalCount"
        tvGridSummary.text = buildString {
            append("播放中 ").append(playingCount)
            append(" | 待启动 ").append(readyCount)
            append(" | 录制中 ").append(recordingCount)
            if (firstFrameSamples.isNotEmpty()) {
                append(" | 平均首帧 ").append(firstFrameSamples.average().toLong()).append("ms")
                append(" | 最快首帧 ").append(firstFrameSamples.minOrNull()).append("ms")
            } else {
                append(" | 首帧耗时待采样")
            }
            if (oldestPlaybackDuration > 0L) {
                append(" | 最长稳定播放 ").append(formatDuration(oldestPlaybackDuration))
            }
            append(" | 错误次数 ").append(totalErrors)
        }
        updateTopControls()
    }

    private fun updateTopControls() {
        val hasStreams = streamItems.isNotEmpty()
        val anyStreamPlaying = streamItems.any { it.isPlaying() }
        val anyStreamReadyToStart = streamItems.any { it.canStart() }

        btnPlayAll.isEnabled = hasStreams && anyStreamReadyToStart
        btnStopAll.isEnabled = hasStreams && anyStreamPlaying
        btnClearAll.isEnabled = hasStreams
    }

    private fun buildConfig(url: String): StreamConfig {
        return StreamConfig.Builder(url)
            .audio(AudioOptions.disabled())
            .build()
    }

    override fun onResume() {
        super.onResume()
        StreamPlayerRuntime.onAppForeground()
        summaryHandler.removeCallbacks(summaryRunnable)
        summaryHandler.post(summaryRunnable)
        updateSummary()
    }

    override fun onPause() {
        summaryHandler.removeCallbacks(summaryRunnable)
        StreamPlayerRuntime.onAppBackground()
        super.onPause()
    }

    override fun onDestroy() {
        summaryHandler.removeCallbacks(summaryRunnable)
        clearAllStreams()
        super.onDestroy()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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

    private inner class StreamItem(
        private val url: String,
        private val displayId: Int
    ) : SurfaceHolder.Callback {

        var player: StreamPlayer? = null
            private set

        var view: View? = null
            private set

        var firstFrameCostMs: Long? = null
            private set

        var errorCount: Int = 0
            private set

        private var playbackRequestedAtMs: Long = 0L
        private var playbackStartedAtMs: Long = 0L
        private var lastRecordPath: String? = null
        private var pendingRecordingFile: File? = null
        private var pendingRecordingDisplayName: String? = null
        private var requestedRecordingFile: File? = null
        private var lastEventMessage: String = "等待开始预览"

        private var surfaceView: SurfaceView? = null
        private var tvStreamId: TextView? = null
        private var tvStreamUrl: TextView? = null
        private var tvStreamStatus: TextView? = null
        private var tvStreamStats: TextView? = null
        private var btnPlayStream: Button? = null
        private var btnStopStream: Button? = null
        private var btnRecordStream: Button? = null
        private var btnTakePhoto: Button? = null
        private var btnDestroyStream: Button? = null

        fun bind(view: View) {
            this.view = view
            tvStreamId = view.findViewById(R.id.tv_stream_id)
            tvStreamUrl = view.findViewById(R.id.tv_stream_url)
            tvStreamStatus = view.findViewById(R.id.tv_stream_status)
            tvStreamStats = view.findViewById(R.id.tv_stream_stats)
            btnPlayStream = view.findViewById(R.id.btn_play_stream)
            btnStopStream = view.findViewById(R.id.btn_stop_stream)
            btnRecordStream = view.findViewById(R.id.btn_record_stream)
            btnTakePhoto = view.findViewById(R.id.btn_take_photo)
            btnDestroyStream = view.findViewById(R.id.btn_destroy_stream)
            val btnRemoveStream: Button = view.findViewById(R.id.btn_remove_stream)
            surfaceView = view.findViewById(R.id.surface_view)

            tvStreamId?.text = "通道 $displayId"
            tvStreamUrl?.text = url
            surfaceView?.holder?.addCallback(this)

            btnPlayStream?.setOnClickListener { play() }
            btnStopStream?.setOnClickListener { stop() }
            btnRecordStream?.setOnClickListener { toggleRecording() }
            btnTakePhoto?.setOnClickListener { takePhoto() }
            btnDestroyStream?.setOnClickListener { destroy() }
            btnRemoveStream.setOnClickListener { removeStream(this) }

            updateUi()
        }

        fun hasActivePlayer(): Boolean {
            return player?.isCreated() == true && player?.isReleased() == false
        }

        fun isRecording(): Boolean {
            val currentPlayer = player ?: return false
            if (currentPlayer.isReleased()) {
                return false
            }
            val state = currentPlayer.getState()
            return state?.isRecording == true || currentPlayer.isRecording()
        }

        fun playingDurationMs(): Long {
            return if (playbackStartedAtMs > 0L) {
                System.currentTimeMillis() - playbackStartedAtMs
            } else {
                0L
            }
        }

        private fun ensurePlayer(): StreamPlayer {
            player?.let { return it }
            val surface = surfaceView ?: error("surfaceView not bound")
            val createdPlayer = StreamPlayer.prepare(
                this@MultiPlayerActivity,
                surface,
                buildConfig(url)
            )
                .setOnStateChanged {
                    runOnUiThread { updateUi() }
                }
                .setOnPlaybackStarted { videoInfo ->
                    runOnUiThread {
                        playbackStartedAtMs = System.currentTimeMillis()
                        if (playbackRequestedAtMs > 0L && firstFrameCostMs == null) {
                            firstFrameCostMs = playbackStartedAtMs - playbackRequestedAtMs
                        }
                        updateStatus("播放中")
                        updateUi(videoInfoOverride = videoInfo)
                    }
                }
                .setOnPlaybackStopped {
                    runOnUiThread {
                        playbackStartedAtMs = 0L
                        updateStatus("已停止")
                        updateUi()
                    }
                }
                .setOnRecordingStarted { outputPath ->
                    runOnUiThread {
                        Log.i(TAG, "player[$displayId] recording started: $outputPath")
                        pendingRecordingFile = File(outputPath)
                        lastEventMessage = "录制中: ${lastRecordPath ?: "相册路径待生成"}"
                        updateUi()
                    }
                }
                .setOnRecordingStopped {
                    runOnUiThread {
                        importPendingRecordingToAlbum()
                    }
                }
                .setOnError { errorCode, errorMessage ->
                    runOnUiThread {
                        errorCount += 1
                        Log.e(TAG, "player[$displayId] error: $errorCode, $errorMessage")
                        updateStatus("异常")
                        lastEventMessage = "连接失败: $errorMessage"
                        updateUi()
                    }
                }
            player = createdPlayer
            updateStatus("待预览")
            updateUi()
            return createdPlayer
        }

        fun play() {
            playbackRequestedAtMs = System.currentTimeMillis()
            playbackStartedAtMs = 0L
            firstFrameCostMs = null
            ensurePlayer().play()
            updateUi()
        }

        fun stop() {
            playbackRequestedAtMs = 0L
            playbackStartedAtMs = 0L
            player?.stop()
            updateUi()
        }

        fun destroy() {
            player?.release()
            player = null
            playbackRequestedAtMs = 0L
            playbackStartedAtMs = 0L
            pendingRecordingFile = null
            pendingRecordingDisplayName = null
            requestedRecordingFile = null
            lastRecordPath = null
            updateStatus("已重置")
            lastEventMessage = "连接已重置"
            updateUi()
        }

        private fun toggleRecording() {
            val currentPlayer = player
            if (currentPlayer == null || !currentPlayer.isPlaying()) {
                toast("请先开始播放")
                return
            }

            if (currentPlayer.isRecording()) {
                currentPlayer.stopRecording()
                return
            }

            runWithMediaStoreWriteAccess(onGranted = {
                val displayName = "multi_recording_${displayId}_${System.currentTimeMillis()}.mp4"
                val outputFile = MediaStoreSaver.createPendingFile(
                    this@MultiPlayerActivity,
                    MediaStoreSaver.Collection.VIDEO,
                    displayName
                )
                requestedRecordingFile = outputFile
                pendingRecordingFile = outputFile
                pendingRecordingDisplayName = displayName
                lastRecordPath = MediaStoreSaver.buildAlbumDisplayPath(
                    MediaStoreSaver.Collection.VIDEO,
                    displayName
                )
                currentPlayer.startRecording(outputFile.absolutePath)
                updateUi()
            })
        }

        private fun takePhoto() {
            val currentPlayer = player
            if (currentPlayer == null || !currentPlayer.isPlaying()) {
                toast("请先开始播放")
                return
            }

            runWithMediaStoreWriteAccess(onGranted = {
                val displayName = "capture_${displayId}_${System.currentTimeMillis()}.jpg"
                val outputFile = MediaStoreSaver.createPendingFile(
                    this@MultiPlayerActivity,
                    MediaStoreSaver.Collection.IMAGE,
                    displayName
                )
                val request = FrameCaptureRequest.Builder()
                    .targetWidth(960)
                    .quality(90)
                    .timeoutMs(2500)
                    .imageFormat(FrameCaptureRequest.ImageFormat.JPEG)
                    .outputPath(outputFile.absolutePath)
                    .returnBitmap(false)
                    .returnBytes(false)
                    .build()

                currentPlayer.captureFrame(
                    request,
                    { result: FrameCaptureResult ->
                        val path = result.outputPath ?: outputFile.absolutePath
                        Log.d(TAG, "capture success: $path")
                        importCapturedPhotoToAlbum(outputFile, displayName)
                    },
                    { errorCode, errorMessage ->
                        Log.e(TAG, "capture failed: $errorCode, $errorMessage")
                        toast("截图失败: $errorMessage")
                        lastEventMessage = "截图失败: $errorMessage"
                        updateUi()
                    }
                )
            })
        }

        fun release() {
            destroy()
            surfaceView?.holder?.removeCallback(this)
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            updateUi()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.d(TAG, "surface changed[$displayId]: ${width}x$height")
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, "surface destroyed[$displayId]")
        }

        private fun updateUi(videoInfoOverride: VideoInfo? = null) {
            val currentPlayer = player
            val state = currentPlayer?.getState()
            val hasPlayer = currentPlayer != null && currentPlayer.isReleased() == false
            val isPending = state?.isOperationPending == true
            val isPlaying = state?.isPlaying == true || currentPlayer?.isPlaying() == true
            val isRecording = state?.isRecording == true || currentPlayer?.isRecording() == true
            val videoInfo = videoInfoOverride ?: state?.lastVideoInfo

            btnPlayStream?.isEnabled = !isPending && (!hasPlayer || !isPlaying)
            btnStopStream?.isEnabled = hasPlayer && isPlaying && !isPending
            btnRecordStream?.isEnabled = hasPlayer && (isPlaying || isRecording) && !isPending
            btnTakePhoto?.isEnabled = hasPlayer && (isPlaying || isRecording) && !isPending
            btnDestroyStream?.isEnabled = hasPlayer && !isPending
            btnRecordStream?.text = if (isRecording) "停止" else "录制"

            if (!hasPlayer) {
                updateStatus("待预览")
                updateStats("等待开始预览")
                updateSummary()
                return
            }

            val statusLabel =
                when {
                    isRecording -> "播放中 / 录制中"
                    isPlaying -> "播放中"
                    isPending -> "连接中"
                    else -> "待预览"
                }
            updateStatus(statusLabel)

            val stats = buildString {
                append("状态 ").append(statusLabel).append('\n')
                firstFrameCostMs?.let {
                    append("首帧 ").append(it).append("ms")
                } ?: append("首帧待采样")
                if (playbackStartedAtMs > 0L) {
                    append(" | 在线 ").append(formatDuration(playingDurationMs()))
                }
                append('\n')
                videoInfo?.let {
                    append(it.width).append("x").append(it.height)
                    append(" @ ").append(it.fps).append("fps")
                    append(" | ").append(it.codec)
                } ?: append("分辨率 / FPS 待采样")
                append('\n')
                append("错误 ").append(errorCount)
                if (isRecording && !lastRecordPath.isNullOrBlank()) {
                    append(" | 录制保存 ").append(lastRecordPath)
                } else if (shouldShowEventMessage()) {
                    append(" | ").append(lastEventMessage)
                }
            }
            updateStats(stats)
            updateSummary()
        }

        fun canStart(): Boolean {
            val currentPlayer = player ?: return true
            if (currentPlayer.isReleased()) {
                return true
            }
            val state = currentPlayer.getState()
            return state?.isOperationPending != true &&
                state?.isPlaying != true &&
                state?.isRecording != true &&
                !currentPlayer.isPlaying() &&
                !currentPlayer.isRecording()
        }

        fun isPlaying(): Boolean {
            val currentPlayer = player ?: return false
            if (currentPlayer.isReleased()) {
                return false
            }
            val state = currentPlayer.getState()
            return state?.isPlaying == true ||
                state?.isRecording == true ||
                currentPlayer.isPlaying() ||
                currentPlayer.isRecording()
        }

        private fun updateStatus(status: String) {
            tvStreamStatus?.text = status
        }

        private fun updateStats(stats: String) {
            tvStreamStats?.text = stats
        }

        private fun shouldShowEventMessage(): Boolean {
            return lastEventMessage.isNotBlank() && lastEventMessage != "等待开始预览"
        }

        private fun importPendingRecordingToAlbum() {
            val sourceFile = pendingRecordingFile
            val displayName = pendingRecordingDisplayName
            if (sourceFile == null || displayName.isNullOrBlank()) {
                toast("录制已停止")
                updateUi()
                return
            }

            pendingRecordingFile = null
            pendingRecordingDisplayName = null
            lastEventMessage = "录制已停止，正在保存到相册"
            updateUi()

            thread(name = "multi-record-import-$displayId") {
                runCatching {
                    val actualSource = when {
                        MediaStoreSaver.awaitFileReady(sourceFile) -> sourceFile
                        requestedRecordingFile != null &&
                            requestedRecordingFile != sourceFile &&
                            MediaStoreSaver.awaitFileReady(requestedRecordingFile!!) -> requestedRecordingFile!!
                        else -> throw IllegalStateException("源文件不存在或未完成写入: ${sourceFile.absolutePath}")
                    }
                    val savedMedia = MediaStoreSaver.saveToAlbum(
                        context = this@MultiPlayerActivity,
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
                        lastRecordPath = savedMedia.displayPath
                        toast("通道 $displayId 录制已保存到相册")
                        lastEventMessage = "最近录制: ${savedMedia.displayPath}"
                        updateUi()
                    }
                }.onFailure { error ->
                    Log.e(TAG, "player[$displayId] import recording failed", error)
                    runOnUiThread {
                        requestedRecordingFile = null
                        lastRecordPath = sourceFile.absolutePath
                        toast("通道 $displayId 保存录制失败: ${error.message}")
                        lastEventMessage = "保存失败，临时文件: ${sourceFile.absolutePath}"
                        updateUi()
                    }
                }
            }
        }

        private fun importCapturedPhotoToAlbum(sourceFile: File, displayName: String) {
            lastEventMessage = "截图完成，正在保存到相册"
            updateUi()
            thread(name = "multi-image-import-$displayId") {
                runCatching {
                    val savedMedia = MediaStoreSaver.saveToAlbum(
                        context = this@MultiPlayerActivity,
                        sourceFile = sourceFile,
                        displayName = displayName,
                        mimeType = "image/jpeg",
                        collection = MediaStoreSaver.Collection.IMAGE
                    )
                    sourceFile.delete()
                    savedMedia
                }.onSuccess { savedMedia ->
                    runOnUiThread {
                        toast("截图已保存到相册")
                        lastEventMessage = "截图成功: ${savedMedia.displayPath}"
                        updateUi()
                    }
                }.onFailure { error ->
                    Log.e(TAG, "player[$displayId] import capture failed", error)
                    runOnUiThread {
                        toast("截图保存失败: ${error.message}")
                        lastEventMessage = "截图保存失败，临时文件: ${sourceFile.absolutePath}"
                        updateUi()
                    }
                }
            }
        }
    }
}

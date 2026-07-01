package com.jxj.ffmpegrtspplayer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.jxj.ffmpegrtsp.lib.api.StreamConfig
import com.jxj.ffmpegrtsp.lib.api.StreamPlayer
import com.jxj.ffmpegrtsp.lib.capture.FrameCaptureRequest
import com.jxj.ffmpegrtsp.lib.capture.FrameCaptureResult
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class MultiPlayerActivity : BaseInsetsActivity() {

    companion object {
        private const val TAG = "MultiPlayerActivity"
    }

    private lateinit var etRtspUrl: EditText
    private lateinit var btnAddStream: Button
    private lateinit var btnPlayAll: Button
    private lateinit var btnStopAll: Button
    private lateinit var btnClearAll: Button
    private lateinit var llStreamsContainer: LinearLayout
    private lateinit var tvStreamCount: TextView

    private val streamItems = mutableListOf<StreamItem>()
    private val nextDisplayId = AtomicInteger(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_player)
        applyEdgeToEdge(findViewById(android.R.id.content), top = true, bottom = true)

        initViews()
        setupClickListeners()
        updateStreamCount()
    }

    private fun initViews() {
        etRtspUrl = findViewById(R.id.et_rtsp_url)
        btnAddStream = findViewById(R.id.btn_add_stream)
        btnPlayAll = findViewById(R.id.btn_play_all)
        btnStopAll = findViewById(R.id.btn_stop_all)
        btnClearAll = findViewById(R.id.btn_clear_all)
        llStreamsContainer = findViewById(R.id.ll_streams_container)
        tvStreamCount = findViewById(R.id.tv_stream_count)
    }

    private fun setupClickListeners() {
        btnAddStream.setOnClickListener { addStream() }
        btnPlayAll.setOnClickListener { streamItems.forEach { it.play() } }
        btnStopAll.setOnClickListener { streamItems.forEach { it.stop() } }
        btnClearAll.setOnClickListener { clearAllStreams() }
        updateTopControls()
    }

    private fun addStream() {
        val url = etRtspUrl.text.toString().trim()
        if (url.isEmpty()) {
            toast("请输入 RTSP 地址")
            return
        }

        val streamItem = StreamItem(url = url, displayId = nextDisplayId.getAndIncrement())
        streamItems.add(streamItem)
        val streamView = LayoutInflater.from(this)
            .inflate(R.layout.item_stream, llStreamsContainer, false)
        streamItem.bind(streamView)
        llStreamsContainer.addView(streamView)
        etRtspUrl.setText("")
        updateStreamCount()
        toast("已添加播放项")
    }

    private fun removeStream(streamItem: StreamItem) {
        streamItem.release()
        streamItems.remove(streamItem)
        streamItem.view?.let { llStreamsContainer.removeView(it) }
        updateStreamCount()
    }

    private fun clearAllStreams() {
        streamItems.toList().forEach(::removeStream)
    }

    private fun updateStreamCount() {
        val createdCount = streamItems.count { it.player?.isCreated() == true && it.player?.isReleased() == false }
        tvStreamCount.text = "活跃播放器: $createdCount / ${streamItems.size}"
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
            .audioEnabled(true)
            .build()
    }

    override fun onResume() {
        super.onResume()
        StreamPlayer.onAppForeground()
        updateStreamCount()
    }

    override fun onPause() {
        StreamPlayer.onAppBackground()
        super.onPause()
    }

    override fun onDestroy() {
        clearAllStreams()
        super.onDestroy()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private inner class StreamItem(
        private val url: String,
        private val displayId: Int
    ) : SurfaceHolder.Callback {

        var player: StreamPlayer? = null
            private set

        var view: View? = null
            private set

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
        private var lastRecordPath: String? = null
        private var pendingRecordingFile: File? = null
        private var pendingRecordingDisplayName: String? = null
        private var requestedRecordingFile: File? = null

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

            tvStreamId?.text = "流 #$displayId"
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
                .setOnPlaybackStarted {
                    runOnUiThread {
                        updateStatus("正在播放")
                        updateUi()
                    }
                }
                .setOnPlaybackStopped {
                    runOnUiThread {
                        updateStatus("已停止")
                        updateUi()
                    }
                }
                .setOnRecordingStarted { outputPath ->
                    runOnUiThread {
                        Log.i(TAG, "player[$displayId] recording started: $outputPath")
                        pendingRecordingFile = File(outputPath)
                        updateStats("录制中: ${lastRecordPath ?: "相册路径待生成"}")
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
                        Log.e(TAG, "player[$displayId] error: $errorCode, $errorMessage")
                        updateStatus("错误: $errorMessage")
                        updateUi()
                    }
                }
            player = createdPlayer
            updateStatus("待播放")
            updateStreamCount()
            updateUi()
            return createdPlayer
        }

        fun play() {
            ensurePlayer().play()
            updateUi()
        }

        fun stop() {
            player?.stop()
            updateUi()
        }

        fun destroy() {
            player?.release()
            player = null
            pendingRecordingFile = null
            pendingRecordingDisplayName = null
            requestedRecordingFile = null
            lastRecordPath = null
            updateStatus("已销毁")
            updateUi()
            updateStreamCount()
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
                        updateStats("截图失败: $errorMessage")
                    }
                )
            })
        }

        fun release() {
            destroy()
            surfaceView?.holder?.removeCallback(this)
            updateStreamCount()
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

        private fun updateUi() {
            val currentPlayer = player
            val state = currentPlayer?.getState()
            val hasPlayer = currentPlayer != null && currentPlayer.isReleased() == false
            val isPending = state?.isOperationPending == true
            val isPlaying = state?.isPlaying == true || currentPlayer?.isPlaying() == true
            val isRecording = state?.isRecording == true || currentPlayer?.isRecording() == true

            btnPlayStream?.isEnabled = !isPending && (!hasPlayer || !isPlaying)
            btnStopStream?.isEnabled = hasPlayer && isPlaying && !isPending
            btnRecordStream?.isEnabled = hasPlayer && (isPlaying || isRecording) && !isPending
            btnTakePhoto?.isEnabled = hasPlayer && (isPlaying || isRecording) && !isPending
            btnDestroyStream?.isEnabled = hasPlayer && !isPending
            btnRecordStream?.text = if (isRecording) "停止录制" else "录制"

            if (!hasPlayer) {
                updateStatus("待播放")
                updateStats("等待开始播放")
                return
            }

            val streamId = currentPlayer?.getStreamId() ?: -1
            val streamState = state?.streamStateCode ?: "UNKNOWN"
            updateStatus(
                when {
                    isRecording -> "播放中 / 录制中"
                    isPlaying -> "播放中"
                    else -> "待播放"
                }
            )

            val stats = buildString {
                append("streamId=").append(streamId)
                append(" | state=").append(streamState)
                state?.lastVideoInfo?.let {
                    append(" | ").append(it.width).append("x").append(it.height)
                    append(" @ ").append(it.fps).append("fps")
                }
                if (isRecording && !lastRecordPath.isNullOrBlank()) {
                    append(" | 录制中")
                }
            }
            updateStats(stats)
            updateTopControls()
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
            tvStreamStats?.text = "状态: $stats"
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
            updateStats("录制已停止，正在保存到相册")
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
                        toast("流 #$displayId 录制已保存到相册")
                        updateStats("最近录制: ${savedMedia.displayPath}")
                        updateUi()
                    }
                }.onFailure { error ->
                    Log.e(TAG, "player[$displayId] import recording failed", error)
                    runOnUiThread {
                        requestedRecordingFile = null
                        lastRecordPath = sourceFile.absolutePath
                        toast("流 #$displayId 保存录制失败: ${error.message}")
                        updateStats("保存失败，临时文件: ${sourceFile.absolutePath}")
                        updateUi()
                    }
                }
            }
        }

        private fun importCapturedPhotoToAlbum(sourceFile: File, displayName: String) {
            updateStats("截图完成，正在保存到相册")
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
                        updateStats("截图成功: ${savedMedia.displayPath}")
                    }
                }.onFailure { error ->
                    Log.e(TAG, "player[$displayId] import capture failed", error)
                    runOnUiThread {
                        toast("截图保存失败: ${error.message}")
                        updateStats("截图保存失败，临时文件: ${sourceFile.absolutePath}")
                    }
                }
            }
        }
    }
}

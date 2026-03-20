package com.jxj.ffmpegrtspplayer

import android.os.Bundle
import android.os.Environment
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
import com.jxj.ffmpegrtsp.lib.StreamConfig
import com.jxj.ffmpegrtsp.lib.StreamPlayer
import com.jxj.ffmpegrtsp.lib.capture.FrameCaptureRequest
import com.jxj.ffmpegrtsp.lib.capture.FrameCaptureResult
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

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
    }

    private fun buildConfig(url: String): StreamConfig {
        return StreamConfig.Builder(url)
            .audioEnabled(true)
            .enableDisconnectRecovery(true)
            .disconnectRecoveryMaxAttempts(3)
            .disconnectRecoveryIntervalMs(300)
            .disconnectRecoveryNoPacketTimeoutMs(1500)
            .disconnectRecoveryConnectTimeoutMs(3000)
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
        private var lastRecordPath: String? = null

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
            val btnRemoveStream: Button = view.findViewById(R.id.btn_remove_stream)
            surfaceView = view.findViewById(R.id.surface_view)

            tvStreamId?.text = "流 #$displayId"
            tvStreamUrl?.text = url
            surfaceView?.holder?.addCallback(this)

            btnPlayStream?.setOnClickListener { ensurePlayer(); play() }
            btnStopStream?.setOnClickListener { stop() }
            btnRecordStream?.setOnClickListener { toggleRecording() }
            btnTakePhoto?.setOnClickListener { takePhoto() }
            btnRemoveStream.setOnClickListener { removeStream(this) }

            updateUi()
        }

        private fun ensurePlayer(): StreamPlayer {
            player?.let { return it }
            val surface = surfaceView ?: error("surfaceView not bound")
            val createdPlayer = StreamPlayer.playWithConfig(
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
                        lastRecordPath = outputPath
                        updateStats("录制中: $outputPath")
                        updateUi()
                    }
                }
                .setOnRecordingStopped {
                    runOnUiThread {
                        updateStats("录制已停止")
                        updateUi()
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

            val outputFile = File(
                getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "multi_recording_${displayId}_${System.currentTimeMillis()}.mp4"
            )
            lastRecordPath = outputFile.absolutePath
            currentPlayer.startRecording(outputFile.absolutePath)
            updateUi()
        }

        private fun takePhoto() {
            val currentPlayer = player
            if (currentPlayer == null || !currentPlayer.isPlaying()) {
                toast("请先开始播放")
                return
            }

            val pictureDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (pictureDir == null) {
                toast("无法获取截图目录")
                return
            }
            if (!pictureDir.exists() && !pictureDir.mkdirs()) {
                toast("创建截图目录失败")
                return
            }

            val outputFile = File(
                pictureDir,
                "capture_${displayId}_${System.currentTimeMillis()}.jpg"
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
                    toast("截图成功: ${File(path).name}")
                    updateStats("截图成功: ${File(path).name}")
                },
                { errorCode, errorMessage ->
                    Log.e(TAG, "capture failed: $errorCode, $errorMessage")
                    toast("截图失败: $errorMessage")
                    updateStats("截图失败: $errorMessage")
                }
            )
        }

        fun release() {
            player?.release()
            player = null
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
            val isPlaying = currentPlayer?.isPlaying() == true
            val isRecording = currentPlayer?.isRecording() == true

            btnPlayStream?.isEnabled = !isPlaying
            btnStopStream?.isEnabled = isPlaying
            btnRecordStream?.isEnabled = isPlaying
            btnTakePhoto?.isEnabled = isPlaying
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
        }

        private fun updateStatus(status: String) {
            tvStreamStatus?.text = status
        }

        private fun updateStats(stats: String) {
            tvStreamStats?.text = "状态: $stats"
        }
    }
}

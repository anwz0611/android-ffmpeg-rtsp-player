package com.jxj.ffmpegrtspplayer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jxj.ffmpegrtsp.lib.FFmpegCallbacks
import com.jxj.ffmpegrtsp.lib.FFmpegRTSPLibrary
import com.jxj.ffmpegrtsp.lib.StreamConfig
import com.jxj.ffmpegrtsp.lib.VideoInfo
import com.jxj.ffmpegrtsp.lib.capture.FrameCaptureRequest
import com.jxj.ffmpegrtsp.lib.capture.FrameCaptureResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MultiPlayerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MultiPlayerActivity"
        private const val PERMISSION_REQUEST_CODE = 1002
        
        // 视频尺寸常量
        private const val VIDEO_WIDTH = 640
        private const val VIDEO_HEIGHT = 480
    }

    private lateinit var etRtspUrl: EditText
    private lateinit var btnAddStream: Button
    private lateinit var btnPlayAll: Button
    private lateinit var btnStopAll: Button
    private lateinit var btnClearAll: Button
    private lateinit var llStreamsContainer: LinearLayout
    private lateinit var tvStreamCount: TextView

    private val streamItems = mutableListOf<StreamItem>()
    private var nextStreamId = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_player)

        initViews()
        setupClickListeners()
        checkPermissions()
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
        btnAddStream.setOnClickListener {
            addStream()
        }

        btnPlayAll.setOnClickListener {
            playAllStreams()
        }

        btnStopAll.setOnClickListener {
            stopAllStreams()
        }

        btnClearAll.setOnClickListener {
            clearAllStreams()
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        val permissionList = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionList.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionList.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allPermissionsGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allPermissionsGranted) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要存储权限才能录制视频和拍照", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addStream() {
        val url = etRtspUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "请输入RTSP地址", Toast.LENGTH_SHORT).show()
            return
        }

        // 创建流
        val streamId = FFmpegRTSPLibrary.createStream(StreamConfig.Builder(url).build())
        if (streamId >= 0) {
            val streamItem = StreamItem(streamId, url, nextStreamId++)
            streamItems.add(streamItem)
            addStreamView(streamItem)
            updateStreamCount()
            etRtspUrl.setText("") // 清空输入框
            Toast.makeText(this, "流已添加", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "创建流失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addStreamView(streamItem: StreamItem) {
        val streamView = LayoutInflater.from(this).inflate(R.layout.item_stream, llStreamsContainer, false)
        streamItem.setView(streamView)
        llStreamsContainer.addView(streamView)
    }

    private fun removeStream(streamItem: StreamItem) {
        if (streamItem.isPlaying) {
            stopStream(streamItem)
        }
        if (streamItem.isRecording) {
            stopRecording(streamItem)
        }
        FFmpegRTSPLibrary.destroyStream(streamItem.streamId)
        streamItems.remove(streamItem)
        llStreamsContainer.removeView(streamItem.view)
        updateStreamCount()
    }

    private fun playStream(streamItem: StreamItem) {
        if (!streamItem.isPlaying) {
            FFmpegRTSPLibrary.startPlayAsync(streamItem.streamId, object : FFmpegCallbacks.PlaybackStartCallback {
                override fun onPlaybackStarted(streamId: Int, videoInfo: VideoInfo?) {
                    runOnUiThread {
                        streamItem.isPlaying = true
                        streamItem.updateStatus("正在播放")
                        streamItem.updatePlayButton("暂停")
                    }
                }

                override fun onPlaybackError(streamId: Int, errorCode: Int, errorMessage: String) {
                    runOnUiThread {
                        streamItem.isPlaying = false
                        streamItem.updateStatus("播放错误: $errorMessage")
                        streamItem.updatePlayButton("播放")
                    }
                }
            })
        } else {
            stopStream(streamItem)
        }
    }

    private fun stopStream(streamItem: StreamItem) {
        if (streamItem.isPlaying) {
            FFmpegRTSPLibrary.stopPlayAsync(streamItem.streamId, object : FFmpegCallbacks.PlaybackStopCallback {
                override fun onPlaybackStopped(streamId: Int) {
                    runOnUiThread {
                        streamItem.isPlaying = false
                        streamItem.updateStatus("已停止")
                        streamItem.updatePlayButton("播放")
                    }
                }

                override fun onPlaybackError(streamId: Int, errorCode: Int, errorMessage: String) {
                    runOnUiThread {
                        streamItem.isPlaying = false
                        streamItem.updateStatus("停止错误: $errorMessage")
                        streamItem.updatePlayButton("播放")
                    }
                }
            })
        }
    }

    private fun startRecording(streamItem: StreamItem) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "rtsp_record_${streamItem.displayId}_$timestamp.mp4"
        val recordDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "RTSPRecords"
        )
        if (!recordDir.exists()) {
            recordDir.mkdirs()
        }
        val recordPath = File(recordDir, fileName).absolutePath

        FFmpegRTSPLibrary.startRecordingAsync(streamItem.streamId, recordPath, object : FFmpegCallbacks.RecordingStartCallback {
            override fun onRecordingStarted(streamId: Int, outputPath: String) {
                runOnUiThread {
                    streamItem.isRecording = true
                    streamItem.updateRecordButton("停止录制")
                    streamItem.updateStats("正在录制: $fileName")
                }
            }

            override fun onRecordingError(streamId: Int, errorCode: Int, errorMessage: String) {
                runOnUiThread {
                    streamItem.isRecording = false
                    streamItem.updateRecordButton("录制")
                    streamItem.updateStats("录制错误: $errorMessage")
                }
            }
        })
    }

    private fun stopRecording(streamItem: StreamItem) {
        if (streamItem.isRecording) {
            FFmpegRTSPLibrary.stopRecordingAsync(streamItem.streamId, object : FFmpegCallbacks.RecordingStopCallback {
                override fun onRecordingStopped(streamId: Int) {
                    runOnUiThread {
                        streamItem.isRecording = false
                        streamItem.updateRecordButton("录制")
                        streamItem.updateStats("录制已停止")
                    }
                }

                override fun onRecordingError(streamId: Int, errorCode: Int, errorMessage: String) {
                    runOnUiThread {
                        streamItem.isRecording = false
                        streamItem.updateRecordButton("录制")
                        streamItem.updateStats("停止录制错误: $errorMessage")
                    }
                }
            })
        }
    }

    private fun playAllStreams() {
        streamItems.forEach { streamItem ->
            if (!streamItem.isPlaying) {
                playStream(streamItem)
            }
        }
    }

    private fun stopAllStreams() {
        streamItems.forEach { streamItem ->
            if (streamItem.isPlaying) {
                stopStream(streamItem)
            }
        }
    }

    private fun clearAllStreams() {
        streamItems.toList().forEach { streamItem ->
            removeStream(streamItem)
        }
    }

    private fun updateStreamCount() {
        val activeCount = FFmpegRTSPLibrary.getActiveStreamCount()
        tvStreamCount.text = "活跃流数量: $activeCount / ${streamItems.size}"
    }

    private fun takePhoto(streamItem: StreamItem) {
        if (!streamItem.isPlaying) {
            Toast.makeText(this, "请先播放视频", Toast.LENGTH_SHORT).show()
            return
        }

        val surfaceView = streamItem.surfaceView
        val surfaceHolder = streamItem.surfaceView?.holder
        if (surfaceView == null || surfaceHolder == null) {
            Toast.makeText(this, getString(R.string.take_photo_fail), Toast.LENGTH_SHORT).show()
            return
        }

        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (dir == null) {
            Toast.makeText(this, "无法获取图片目录", Toast.LENGTH_SHORT).show()
            return
        }
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "创建图片目录失败", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "native_capture_" + System.currentTimeMillis() + ".jpg"
        val outFile = File(dir, fileName)

        val request = FrameCaptureRequest.Builder()
            .targetWidth(960)
            .outputPath(outFile.absolutePath)
            .imageFormat(FrameCaptureRequest.ImageFormat.JPEG)
            .quality(90)
            .timeoutMs(2500)
            .returnBitmap(false)
            .returnBytes(false)
            .build()

        Log.d(TAG, "发起 native 截图请求：" + outFile.absolutePath)
        FFmpegRTSPLibrary.captureFrameAsync(
            streamItem.streamId,
            surfaceHolder.surface,
            request,
            object : FFmpegCallbacks.FrameCaptureCallback {
                override fun onCaptureSuccess(streamId: Int, result: FrameCaptureResult) {
                    val path = result.outputPath ?: outFile.absolutePath
                    Log.d(TAG, "截图成功：" + path
                            + " | backend=" + result.captureBackend
                            + " | mode=" + result.captureMode
                            + " | ptsNs=" + result.framePtsNs)
                    Toast.makeText(this@MultiPlayerActivity, "截图成功：" + File(path).name, Toast.LENGTH_SHORT).show()
                }

                override fun onCaptureError(streamId: Int, errorCode: Int, errorMessage: String) {
                    Log.e(TAG, "截图失败：code=$errorCode, msg=$errorMessage")
                    Toast.makeText(this@MultiPlayerActivity, "截图失败：$errorMessage", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        clearAllStreams()
    }

    override fun onPause() {
        super.onPause()
        FFmpegRTSPLibrary.onAppBackground()
    }

    override fun onResume() {
        super.onResume()
        FFmpegRTSPLibrary.onAppForeground()
        updateStreamCount()
    }

    // 流项目内部类
    private inner class StreamItem(
        val streamId: Int,
        val url: String,
        val displayId: Int
    ) : SurfaceHolder.Callback {
        
        var isPlaying = false
        var isRecording = false
        private var _view: View? = null
        val view: View? get() = _view

        private var tvStreamId: TextView? = null
        private var tvStreamUrl: TextView? = null
        private var tvStreamStatus: TextView? = null
        private var tvStreamStats: TextView? = null
        private var btnRemoveStream: Button? = null
        private var btnPlayStream: Button? = null
        private var btnStopStream: Button? = null
        private var btnRecordStream: Button? = null
        private var btnTakePhoto: Button? = null
        var surfaceView: SurfaceView? = null
        private var surfaceHolder: SurfaceHolder? = null

        fun setView(view: View) {
            this._view = view
            initViews()
            setupClickListeners()
        }

        private fun initViews() {
            tvStreamId = view?.findViewById(R.id.tv_stream_id)
            tvStreamUrl = view?.findViewById(R.id.tv_stream_url)
            tvStreamStatus = view?.findViewById(R.id.tv_stream_status)
            tvStreamStats = view?.findViewById(R.id.tv_stream_stats)
            btnRemoveStream = view?.findViewById(R.id.btn_remove_stream)
            btnPlayStream = view?.findViewById(R.id.btn_play_stream)
            btnStopStream = view?.findViewById(R.id.btn_stop_stream)
            btnRecordStream = view?.findViewById(R.id.btn_record_stream)
            btnTakePhoto = view?.findViewById(R.id.btn_take_photo)
            surfaceView = view?.findViewById(R.id.surface_view)

            tvStreamId?.text = "流 #$displayId"
            tvStreamUrl?.text = url

            surfaceHolder = surfaceView?.holder
            surfaceHolder?.addCallback(this)
        }

        private fun setupClickListeners() {
            btnRemoveStream?.setOnClickListener {
                removeStream(this@StreamItem)
            }

            btnPlayStream?.setOnClickListener {
                playStream(this@StreamItem)
            }

            btnStopStream?.setOnClickListener {
                stopStream(this@StreamItem)
            }

            btnRecordStream?.setOnClickListener {
                if (!isRecording) {
                    startRecording(this@StreamItem)
                } else {
                    stopRecording(this@StreamItem)
                }
            }

            btnTakePhoto?.setOnClickListener {
                takePhoto(this@StreamItem)
            }
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            FFmpegRTSPLibrary.setSurface(streamId, holder.surface)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            FFmpegRTSPLibrary.onSurfaceDestroyed(streamId)
        }

        fun updateStatus(status: String) {
            tvStreamStatus?.text = status
        }

        fun updateStats(stats: String) {
            tvStreamStats?.text = "状态: $stats"
        }

        fun updatePlayButton(text: String) {
            btnPlayStream?.text = text
        }

        fun updateRecordButton(text: String) {
            btnRecordStream?.text = text
        }
    }
}
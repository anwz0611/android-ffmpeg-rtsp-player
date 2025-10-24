package com.jxj.ffmpegrtspplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jxj.ffmpegrtsp.lib.FFmpegRTSPLibrary
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SinglePlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "SinglePlayerActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var etRtspUrl: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnPlay: Button
    private lateinit var btnStop: Button
    private lateinit var btnRecord: Button
    private lateinit var surfaceView: SurfaceView
    private lateinit var tvStatus: TextView
    private lateinit var tvStreamInfo: TextView
    private lateinit var tvRecordInfo: TextView

    private lateinit var surfaceHolder: SurfaceHolder
    private var streamId = -1
    private var isPlaying = false
    private var isRecording = false
    private var currentRecordPath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single_player)

        initViews()
        setupClickListeners()
        checkPermissions()
    }

    private fun initViews() {
        etRtspUrl = findViewById(R.id.et_rtsp_url)
        btnConnect = findViewById(R.id.btn_connect)
        btnPlay = findViewById(R.id.btn_play)
        btnStop = findViewById(R.id.btn_stop)
        btnRecord = findViewById(R.id.btn_record)
        surfaceView = findViewById(R.id.surface_view)
        tvStatus = findViewById(R.id.tv_status)
        tvStreamInfo = findViewById(R.id.tv_stream_info)
        tvRecordInfo = findViewById(R.id.tv_record_info)

        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)
    }

    private fun setupClickListeners() {
        btnConnect.setOnClickListener {
            connectStream()
        }

        btnPlay.setOnClickListener {
            playStream()
        }

        btnStop.setOnClickListener {
            stopStream()
        }

        btnRecord.setOnClickListener {
            toggleRecording()
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要存储权限才能录制视频", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun connectStream() {
        val url = etRtspUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "请输入RTSP地址", Toast.LENGTH_SHORT).show()
            return
        }

        // 创建流  true 软件解码
        streamId = FFmpegRTSPLibrary.createStreamWithDecodeMode(url, 1280, 720, 30, 2000000, "h264",false)
        if (streamId >= 0) {
            updateStatus("流已创建，ID: $streamId")
            updateStreamInfo("URL: $url")
            btnPlay.isEnabled = true
            btnStop.isEnabled = true
            btnRecord.isEnabled = true
        } else {
            updateStatus("创建流失败")
            Toast.makeText(this, "创建流失败", Toast.LENGTH_SHORT).show()
        }
        FFmpegRTSPLibrary.setSurface(streamId, surfaceView.holder.surface)
    }

    private fun playStream() {
        if (streamId < 0) {
            Toast.makeText(this, "请先连接流", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isPlaying) {
            FFmpegRTSPLibrary.startPlayAsync(streamId, object : FFmpegRTSPLibrary.PlaybackCallback {
                override fun onPlaybackStarted(streamId: Int) {
                    runOnUiThread {
                        isPlaying = true
                        updateStatus("正在播放")
                        btnPlay.text = "暂停"
                        Toast.makeText(this@SinglePlayerActivity, "开始播放", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onPlaybackStopped(streamId: Int) {
                    runOnUiThread {
                        isPlaying = false
                        updateStatus("已停止")
                        btnPlay.text = "播放"
                        Toast.makeText(this@SinglePlayerActivity, "停止播放", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onPlaybackError(streamId: Int, errorCode: Int, errorMessage: String) {
                    runOnUiThread {
                        isPlaying = false
                        updateStatus("播放错误: $errorMessage")
                        btnPlay.text = "播放"
                        Toast.makeText(this@SinglePlayerActivity, "播放错误: $errorMessage", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onPlaybackInfo(streamId: Int, info: String) {
                    runOnUiThread {
                        updateStreamInfo(info)
                    }
                }
            })
        } else {
            stopStream()
        }
    }

    private fun stopStream() {
        if (streamId >= 0 && isPlaying) {
            FFmpegRTSPLibrary.stopPlayAsync(streamId, object : FFmpegRTSPLibrary.PlaybackCallback {
                override fun onPlaybackStarted(streamId: Int) {}

                override fun onPlaybackStopped(streamId: Int) {
                    runOnUiThread {
                        isPlaying = false
                        updateStatus("已停止")
                        btnPlay.text = "播放"
                    }
                }

                override fun onPlaybackError(streamId: Int, errorCode: Int, errorMessage: String) {
                    runOnUiThread {
                        isPlaying = false
                        updateStatus("停止错误: $errorMessage")
                        btnPlay.text = "播放"
                    }
                }

                override fun onPlaybackInfo(streamId: Int, info: String) {}
            })
        }
    }

    private fun toggleRecording() {
        if (streamId < 0) {
            Toast.makeText(this, "请先连接流", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isRecording) {
            startRecording()
        } else {
            stopRecording()
        }
    }

    private fun startRecording() {
        // 创建录制文件路径
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "rtsp_record_$timestamp.mp4"
        val recordDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "RTSPRecords"
        )
        if (!recordDir.exists()) {
            recordDir.mkdirs()
        }
        currentRecordPath = File(recordDir, fileName).absolutePath

        FFmpegRTSPLibrary.startRecordingAsync(streamId, currentRecordPath, object : FFmpegRTSPLibrary.RecordingCallback {
            override fun onRecordingStarted(streamId: Int, outputPath: String) {
                runOnUiThread {
                    isRecording = true
                    updateRecordInfo("正在录制: $fileName")
                    btnRecord.text = "停止录制"
                    Toast.makeText(this@SinglePlayerActivity, "开始录制", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onRecordingStopped(streamId: Int) {
                runOnUiThread {
                    isRecording = false
                    updateRecordInfo("录制完成: $currentRecordPath")
                    btnRecord.text = "录制"
                    Toast.makeText(this@SinglePlayerActivity, "录制完成", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onRecordingError(streamId: Int, errorCode: Int, errorMessage: String) {
                runOnUiThread {
                    isRecording = false
                    updateRecordInfo("录制错误: $errorMessage")
                    btnRecord.text = "录制"
                    Toast.makeText(this@SinglePlayerActivity, "录制错误: $errorMessage", Toast.LENGTH_LONG).show()
                }
            }

            override fun onRecordingProgress(streamId: Int, duration: Long, fileSize: Long) {
                runOnUiThread {
                    updateRecordInfo("录制中: ${duration / 1000}s, 大小: ${fileSize / 1024}KB")
                }
            }
        })
    }

    private fun stopRecording() {
        if (streamId >= 0 && isRecording) {
            FFmpegRTSPLibrary.stopRecordingAsync(streamId, object : FFmpegRTSPLibrary.RecordingCallback {
                override fun onRecordingStarted(streamId: Int, outputPath: String) {}

                override fun onRecordingStopped(streamId: Int) {
                    runOnUiThread {
                        isRecording = false
                        updateRecordInfo("录制已停止")
                        btnRecord.text = "录制"
                    }
                }

                override fun onRecordingError(streamId: Int, errorCode: Int, errorMessage: String) {
                    runOnUiThread {
                        isRecording = false
                        updateRecordInfo("停止录制错误: $errorMessage")
                        btnRecord.text = "录制"
                    }
                }

                override fun onRecordingProgress(streamId: Int, duration: Long, fileSize: Long) {}
            })
        }
    }

    private fun updateStatus(status: String) {
        tvStatus.text = status
        Log.d(TAG, "Status: $status")
    }

    private fun updateStreamInfo(info: String) {
        tvStreamInfo.text = "流信息: $info"
    }

    private fun updateRecordInfo(info: String) {
        tvRecordInfo.text = "录制状态: $info"
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "Surface created")
        if (streamId >= 0) {
            FFmpegRTSPLibrary.setSurface(streamId, holder.surface)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "Surface changed: ${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "Surface destroyed")
        if (streamId >= 0) {
            FFmpegRTSPLibrary.onSurfaceDestroyed(streamId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (streamId >= 0) {
            FFmpegRTSPLibrary.destroyAllStreamsAsync()
        }
    }

    override fun onPause() {
        super.onPause()
        FFmpegRTSPLibrary.onAppBackground()
    }

    override fun onResume() {
        super.onResume()
        FFmpegRTSPLibrary.onAppForeground()
    }
}
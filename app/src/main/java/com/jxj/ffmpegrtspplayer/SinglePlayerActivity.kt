package com.jxj.ffmpegrtspplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
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
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jxj.ffmpegrtsp.lib.FFmpegRTSPLibrary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SinglePlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "SinglePlayerActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val UPDATE_INTERVAL_MS = 500L
        private const val MAX_LATENCY_HISTORY = 100
    }

    // UI组件
    private lateinit var etRtspUrl: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnPlay: Button
    private lateinit var btnStop: Button
    private lateinit var btnRecord: Button
    private lateinit var surfaceView: SurfaceView
    private lateinit var tvStatus: TextView
    private lateinit var tvStreamInfo: TextView
    private lateinit var tvRecordInfo: TextView

    // 性能监控UI组件
    private lateinit var performanceMonitorCard: CardView
    private lateinit var performanceMonitorHeader: View
    private lateinit var performanceMonitorToggle: TextView
    private lateinit var performanceMonitorContent: View
    private lateinit var performanceMonitorTextView: TextView

    private lateinit var surfaceHolder: SurfaceHolder
    private var streamId = -1
    private var isPlaying = false
    private var isRecording = false
    private var currentRecordPath = ""

    // 性能监控
    private lateinit var performanceMonitorHandler: Handler
    private lateinit var performanceMonitorRunnable: Runnable
    private var isPerformanceMonitorExpanded = false
    private var isPerformanceMonitoring = false
    private var monitoringStartTime = 0L
    private var totalUpdates = 0L
    private val latencyHistory = ArrayList<Double>()

    private var isSurfaceRebuilding = false

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

        // 性能监控UI组件
        performanceMonitorCard = findViewById(R.id.performanceMonitorCard)
        performanceMonitorHeader = findViewById(R.id.performanceMonitorHeader)
        performanceMonitorToggle = findViewById(R.id.performanceMonitorToggle)
        performanceMonitorContent = findViewById(R.id.performanceMonitorContent)
        performanceMonitorTextView = findViewById(R.id.performanceMonitorTextView)

        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)

        // 初始化性能监控Handler
        performanceMonitorHandler = Handler(Looper.getMainLooper())
        performanceMonitorRunnable = object : Runnable {
            override fun run() {
                if (isPerformanceMonitoring && streamId >= 0) {
                    updatePerformanceStats()
                    performanceMonitorHandler.postDelayed(this, UPDATE_INTERVAL_MS.toLong())
                }
            }
        }

        // 设置性能监控面板展开/折叠
        performanceMonitorHeader.setOnClickListener { togglePerformanceMonitor() }

        // 设置默认URL
        etRtspUrl.setText("rtsp://stream.strba.sk:1935/strba/VYHLAD_JAZERO.stream")
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

        // 🔥 使用软件解码
        val useSoftwareDecode = true
        streamId = FFmpegRTSPLibrary.createStreamWithDecodeMode(url, useSoftwareDecode)
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
                        Toast.makeText(this@SinglePlayerActivity, "开始播放", Toast.LENGTH_SHORT)
                            .show()

                        startPerformanceMonitoring()
                    }
                }

                override fun onPlaybackStopped(streamId: Int) {
                    runOnUiThread {
                        isPlaying = false
                        updateStatus("已停止")
                        btnPlay.text = "播放"
                        Toast.makeText(this@SinglePlayerActivity, "停止播放", Toast.LENGTH_SHORT)
                            .show()
                        // 流停止后停止性能监控
                        stopPerformanceMonitoring()
                    }
                }

                override fun onPlaybackError(streamId: Int, errorCode: Int, errorMessage: String) {
                    runOnUiThread {
                        isPlaying = false
                        updateStatus("播放错误: $errorMessage")
                        btnPlay.text = "播放"
                        Toast.makeText(
                            this@SinglePlayerActivity,
                            "播放错误: $errorMessage",
                            Toast.LENGTH_LONG
                        ).show()
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

        FFmpegRTSPLibrary.startRecordingAsync(
            streamId,
            currentRecordPath,
            object : FFmpegRTSPLibrary.RecordingCallback {
                override fun onRecordingStarted(streamId: Int, outputPath: String) {
                    runOnUiThread {
                        isRecording = true
                        updateRecordInfo("正在录制: $fileName")
                        btnRecord.text = "停止录制"
                        Toast.makeText(this@SinglePlayerActivity, "开始录制", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                override fun onRecordingStopped(streamId: Int) {
                    runOnUiThread {
                        isRecording = false
                        updateRecordInfo("录制完成: $currentRecordPath")
                        btnRecord.text = "录制"
                        Toast.makeText(this@SinglePlayerActivity, "录制完成", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                override fun onRecordingError(streamId: Int, errorCode: Int, errorMessage: String) {
                    runOnUiThread {
                        isRecording = false
                        updateRecordInfo("录制错误: $errorMessage")
                        btnRecord.text = "录制"
                        Toast.makeText(
                            this@SinglePlayerActivity,
                            "录制错误: $errorMessage",
                            Toast.LENGTH_LONG
                        ).show()
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
            FFmpegRTSPLibrary.stopRecordingAsync(
                streamId,
                object : FFmpegRTSPLibrary.RecordingCallback {
                    override fun onRecordingStarted(streamId: Int, outputPath: String) {}

                    override fun onRecordingStopped(streamId: Int) {
                        runOnUiThread {
                            isRecording = false
                            updateRecordInfo("录制已停止")
                            btnRecord.text = "录制"
                        }
                    }

                    override fun onRecordingError(
                        streamId: Int,
                        errorCode: Int,
                        errorMessage: String
                    ) {
                        runOnUiThread {
                            isRecording = false
                            updateRecordInfo("停止录制错误: $errorMessage")
                            btnRecord.text = "录制"
                        }
                    }

                    override fun onRecordingProgress(
                        streamId: Int,
                        duration: Long,
                        fileSize: Long
                    ) {
                    }
                })
        }
    }

    private fun updateStatus(status: String) {
        tvStatus.text = status
        Log.d(TAG, "Status: $status")

        // 更新性能监控面板显示/隐藏
        if (isPlaying && streamId >= 0) {
            performanceMonitorCard.visibility = View.VISIBLE
        } else {
            performanceMonitorCard.visibility = View.GONE
            isPerformanceMonitorExpanded = false
            performanceMonitorContent.visibility = View.GONE
            performanceMonitorToggle.text = "▼"
        }
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

        Handler(Looper.getMainLooper()).postDelayed({
            isSurfaceRebuilding = false
        }, 5000) // 5000毫秒 = 5秒
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "Surface changed: ${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "Surface destroyed")
        isSurfaceRebuilding = true
        if (streamId >= 0) {
            FFmpegRTSPLibrary.onSurfaceDestroyed(streamId)
        }
    }

    // ============================================================================
    // 性能监控方法
    // ============================================================================

    private fun togglePerformanceMonitor() {
        isPerformanceMonitorExpanded = !isPerformanceMonitorExpanded
        if (isPerformanceMonitorExpanded) {
            performanceMonitorContent.visibility = View.VISIBLE
            performanceMonitorToggle.text = "▼"
        } else {
            performanceMonitorContent.visibility = View.GONE
            performanceMonitorToggle.text = "▲"
        }
    }

    private fun startPerformanceMonitoring() {
        if (streamId < 0 || isPerformanceMonitoring) {
            return
        }

        isPerformanceMonitoring = true
        monitoringStartTime = System.currentTimeMillis()
        totalUpdates = 0
        latencyHistory.clear()

        // 自动展开监控面板
        if (!isPerformanceMonitorExpanded) {
            togglePerformanceMonitor()
        }

        // 开始更新循环
        performanceMonitorHandler.post(performanceMonitorRunnable)

        Log.i(TAG, "✅ 开始性能监控: streamId=$streamId")
    }

    private fun stopPerformanceMonitoring() {
        if (!isPerformanceMonitoring) {
            return
        }

        isPerformanceMonitoring = false
        performanceMonitorHandler.removeCallbacks(performanceMonitorRunnable)
        monitoringStartTime = 0
        totalUpdates = 0
        latencyHistory.clear()

        Log.i(TAG, "🛑 停止性能监控")
    }

    private fun updatePerformanceStats() {
        if (streamId < 0) {
            return
        }

        try {
            val statsBuilder = StringBuilder()

            // 基本信息
            statsBuilder.append("═══════════════════════════════════════\n")
            statsBuilder.append("📊 性能监控\n")
            statsBuilder.append("═══════════════════════════════════════\n\n")

            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            statsBuilder.append("⏰ 更新时间: ").append(sdf.format(Date())).append("\n")
            statsBuilder.append("🆔 流ID: ").append(streamId).append("\n")
            statsBuilder.append("🔄 更新次数: ").append(++totalUpdates).append("\n")

            val currentTime = System.currentTimeMillis()
            if (monitoringStartTime > 0) {
                val elapsed = currentTime - monitoringStartTime
                statsBuilder.append("⏱️ 监控时长: ").append(formatDuration(elapsed)).append("\n")
            }
            statsBuilder.append("\n")

            // 延迟统计区域
            var streamStatsJson= ""
            if (!isSurfaceRebuilding) {
                 // 获取流统计信息 Surface重建  这个会导致奔溃 生成环境中如果有前后台切换的场景 不要定时获取统计数据  暂时无解决方案
                streamStatsJson = FFmpegRTSPLibrary.getStreamStats(streamId)
            }
            var avgLatency = 0.0
            var networkLatency = 0.0
            var uptimeMs = 0L
            var bitrateKbps = 0.0
            var packetLossRate = 0.0


            var videoWidth = 0
            var videoHeight = 0
            var videoFps = 0
            var videoCodec = "unknown"

            if (streamStatsJson != null && streamStatsJson.isNotEmpty()) {
                try {
                    val streamJson = org.json.JSONObject(streamStatsJson)

                    // 🔥 新增：解析视频信息
                    if (streamJson.has("video") && !streamJson.isNull("video")) {
                        val videoObj = streamJson.getJSONObject("video")
                        if (videoObj.has("width")) {
                            videoWidth = videoObj.getInt("width")
                        }
                        if (videoObj.has("height")) {
                            videoHeight = videoObj.getInt("height")
                        }
                        if (videoObj.has("fps")) {
                            videoFps = videoObj.getInt("fps")
                        }
                        if (videoObj.has("codec")) {
                            videoCodec = videoObj.getString("codec")
                        }

                        // 📹 在日志中显示视频尺寸信息
                        Log.i(
                            TAG, String.format(
                                "📹 视频尺寸: %dx%d, FPS: %d, 编码: %s",
                                videoWidth, videoHeight, videoFps, videoCodec
                            )
                        )
                    } else {
                        // 如果没有video字段，设置为等待状态
                        videoCodec = "waiting"
                        Log.d(TAG, "📹 视频信息等待中...")
                    }

                    // 获取网络延迟
                    if (streamJson.has("network_latency_ms")) {
                        networkLatency = streamJson.getDouble("network_latency_ms")
                    }

                    // 获取流运行时间
                    if (streamJson.has("uptime_ms")) {
                        uptimeMs = streamJson.getLong("uptime_ms")
                    }

                    // 获取网络统计信息
                    if (streamJson.has("network") && !streamJson.isNull("network")) {
                        val networkObj = streamJson.getJSONObject("network")
                        if (networkObj.has("bitrate_kbps")) {
                            bitrateKbps = networkObj.getDouble("bitrate_kbps")
                        }
                        if (networkObj.has("packet_loss_rate")) {
                            packetLossRate = networkObj.getDouble("packet_loss_rate") * 100.0
                        }
                        if (networkObj.has("connection_time_ms")) {
                            val connectionTime = networkObj.getLong("connection_time_ms")
                            if (networkLatency == 0.0) {
                                networkLatency = connectionTime.toDouble()
                            }
                        }
                    }

                    // 计算流延迟
                    if (streamJson.has("last_activity_ms")) {
                        val lastActivityMs = streamJson.getLong("last_activity_ms")
                        avgLatency = lastActivityMs.toDouble()
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "解析流统计JSON失败", e)
                }
            }

            if (avgLatency > 0 || networkLatency > 0) {
                // 添加当前延迟到历史记录
                val currentLatency = if (networkLatency > 0) networkLatency else avgLatency
                if (currentLatency > 0) {
                    latencyHistory.add(currentLatency)
                    if (latencyHistory.size > MAX_LATENCY_HISTORY) {
                        latencyHistory.removeAt(0)
                    }
                }

                statsBuilder.append("═══════════════════════════════════════\n")
                statsBuilder.append("⚡⚡⚡ 延迟统计（核心指标）⚡⚡⚡\n")
                statsBuilder.append("═══════════════════════════════════════\n")

                // 显示网络延迟
                if (networkLatency > 0) {
                    val networkLatencyLevel = getLatencyLevel(networkLatency)
                    val networkLatencyEmoji = getLatencyEmoji(networkLatency)
                    statsBuilder.append(
                        String.format(
                            Locale.getDefault(),
                            "%s 网络延迟: %.2f ms [%s]\n",
                            networkLatencyEmoji, networkLatency, networkLatencyLevel
                        )
                    )
                }

                // 显示流处理延迟
                if (avgLatency > 0) {
                    val latencyLevel = getLatencyLevel(avgLatency)
                    val latencyEmoji = getLatencyEmoji(avgLatency)
                    statsBuilder.append(
                        String.format(
                            Locale.getDefault(),
                            "%s 流处理延迟: %.2f ms [%s]\n",
                            latencyEmoji, avgLatency, latencyLevel
                        )
                    )
                }

                // 显示网络统计
                if (bitrateKbps > 0) {
                    statsBuilder.append(
                        String.format(
                            Locale.getDefault(),
                            "📡 比特率: %.2f Kbps\n", bitrateKbps
                        )
                    )
                }
                if (packetLossRate >= 0) {
                    statsBuilder.append(
                        String.format(
                            Locale.getDefault(),
                            "📉 丢包率: %.2f%%\n", packetLossRate
                        )
                    )
                }
                if (uptimeMs > 0) {
                    statsBuilder.append(
                        String.format(
                            Locale.getDefault(),
                            "⏱️ 流运行时间: %s\n", formatDuration(uptimeMs)
                        )
                    )
                }

                // 延迟波动
                if (latencyHistory.isNotEmpty()) {
                    val latencyStdDev = calculateLatencyStdDev()
                    if (latencyStdDev > 0) {
                        statsBuilder.append(
                            String.format(
                                Locale.getDefault(),
                                "📊 延迟波动: %.2f ms\n", latencyStdDev
                            )
                        )
                    }
                }

                statsBuilder.append("\n")

                // 延迟建议
                val mainLatency = if (networkLatency > 0) networkLatency else avgLatency
                statsBuilder.append("💡 延迟建议:\n")
                if (mainLatency < 16.67) {
                    statsBuilder.append("   ✅ 延迟优秀！适合60fps\n")
                } else if (mainLatency < 33.33) {
                    statsBuilder.append("   ✅ 延迟良好，适合30fps\n")
                } else if (mainLatency < 50.0) {
                    statsBuilder.append("   ⚠️ 延迟一般，建议优化\n")
                } else {
                    statsBuilder.append("   ❌ 延迟较高，检查网络\n")
                }
                statsBuilder.append("\n")
            }

            // 流状态信息
            if (streamStatsJson != null && streamStatsJson.isNotEmpty()) {
                try {
                    val json = org.json.JSONObject(streamStatsJson)
                    statsBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                    statsBuilder.append("📡 流状态\n")
                    statsBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                    statsBuilder.append("状态: ").append(json.optString("status", "未知"))
                        .append("\n")
                    if (json.has("url")) {
                        statsBuilder.append("URL: ").append(json.optString("url", "")).append("\n")
                    }
                    statsBuilder.append("\n")
                } catch (e: Exception) {
                    // 忽略
                }
            }

            // 🔥 新增：视频尺寸信息显示
            statsBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            statsBuilder.append("📹 视频信息\n")
            statsBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")

            if (videoWidth > 0 && videoHeight > 0) {
                // 视频信息已准备好
                statsBuilder.append(
                    String.format(
                        Locale.getDefault(),
                        "分辨率: %d x %d\n" +
                                "帧率: %d fps\n" +
                                "编码格式: %s\n",
                        videoWidth, videoHeight, videoFps, videoCodec
                    )
                )
            } else {
                // 视频信息等待中
                if ("waiting" == videoCodec) {
                    statsBuilder.append("🔄 正在解析视频信息...\n")
                } else if ("no_decoder" == videoCodec) {
                    statsBuilder.append("❌ 解码器未初始化\n")
                } else if ("no_codecpar" == videoCodec) {
                    statsBuilder.append("❌ 编码参数未设置\n")
                } else {
                    statsBuilder.append("⏳ 等待视频流...\n")
                }
            }
            statsBuilder.append("\n")

            // 网络统计详情
            if (streamStatsJson != null && streamStatsJson.isNotEmpty()) {
                try {
                    val json = org.json.JSONObject(streamStatsJson)
                    if (json.has("network") && !json.isNull("network")) {
                        val networkObj = json.getJSONObject("network")
                        statsBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                        statsBuilder.append("📡 网络统计\n")
                        statsBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                        statsBuilder.append(
                            String.format(
                                Locale.getDefault(),
                                "接收字节: %s\n" +
                                        "接收包数: %d\n" +
                                        "丢失包数: %d\n" +
                                        "连接时间: %d ms\n",
                                formatBytes(networkObj.optLong("bytes_received", 0)),
                                networkObj.optInt("packets_received", 0),
                                networkObj.optInt("packets_lost", 0),
                                networkObj.optLong("connection_time_ms", 0)
                            )
                        )
                    }
                } catch (e: Exception) {
                    // 忽略
                }
            }

            val statsText = statsBuilder.toString()
            runOnUiThread {
                performanceMonitorTextView?.text = statsText
            }

        } catch (e: Exception) {
            Log.e(TAG, "更新性能统计异常", e)
        }
    }

    private fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d小时%d分%d秒", hours, minutes % 60, seconds % 60)
        } else if (minutes > 0) {
            String.format(Locale.getDefault(), "%d分%d秒", minutes, seconds % 60)
        } else {
            String.format(Locale.getDefault(), "%d秒", seconds)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(
                Locale.getDefault(),
                "%.2f MB",
                bytes / (1024.0 * 1024.0)
            )

            else -> String.format(
                Locale.getDefault(),
                "%.2f GB",
                bytes / (1024.0 * 1024.0 * 1024.0)
            )
        }
    }

    private fun getLatencyLevel(latencyMs: Double): String {
        return when {
            latencyMs < 16.67 -> "优秀 (<16.67ms, 60fps)"
            latencyMs < 33.33 -> "良好 (<33.33ms, 30fps)"
            latencyMs < 50.0 -> "一般 (<50ms)"
            latencyMs < 100.0 -> "较差 (<100ms)"
            else -> "很差 (>=100ms)"
        }
    }

    private fun getLatencyEmoji(latencyMs: Double): String {
        return when {
            latencyMs < 16.67 -> "🟢"
            latencyMs < 33.33 -> "🟡"
            latencyMs < 50.0 -> "🟠"
            else -> "🔴"
        }
    }

    private fun calculateLatencyStdDev(): Double {
        if (latencyHistory.isEmpty()) {
            return 0.0
        }

        val sum = latencyHistory.sum()
        val mean = sum / latencyHistory.size

        var varianceSum = 0.0
        for (lat in latencyHistory) {
            varianceSum += Math.pow(lat - mean, 2.0)
        }
        val variance = varianceSum / latencyHistory.size
        return Math.sqrt(variance)
    }

    override fun onDestroy() {
        super.onDestroy()

        // 停止性能监控
        stopPerformanceMonitoring()

        // 清理Handler
        performanceMonitorHandler.removeCallbacks(performanceMonitorRunnable)

        if (streamId >= 0) {
            FFmpegRTSPLibrary.destroyAllStreamsAsync()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "📱 应用恢复")
        FFmpegRTSPLibrary.onAppForeground()
        // 恢复时如果流在运行，重新开始监控
        if (isPlaying && streamId >= 0 && !isPerformanceMonitoring) {
            startPerformanceMonitoring()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "📱 应用暂停")
        FFmpegRTSPLibrary.onAppBackground()
        // 暂停时停止性能监控更新（可选，也可以继续监控）
        // stopPerformanceMonitoring();
    }
}
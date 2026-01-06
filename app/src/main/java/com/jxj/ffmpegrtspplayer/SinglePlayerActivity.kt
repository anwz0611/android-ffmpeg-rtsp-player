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
import com.jxj.ffmpegrtsp.lib.FFmpegCallbacks
import com.jxj.ffmpegrtsp.lib.FFmpegRTSPLibrary
import com.jxj.ffmpegrtsp.lib.VideoInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.List
import java.util.Locale

/**
 * 单个视频播放Activity（Kotlin版本）
 */
class SinglePlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private val TAG = "SinglePlayerActivity"

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

    // 流管理
    private var streamId = -1
    private var isStreamCreated = false
    private var isPlaying = false
    private var isRecording = false

    // 视频信息（播放成功后保存的）
    private var currentVideoInfo: VideoInfo? = null

    // 性能监控
    private var performanceMonitorHandler: Handler? = null
    private var performanceMonitorRunnable: Runnable? = null
    private var isPerformanceMonitorExpanded = false
    private var isPerformanceMonitoring = false
    private var monitoringStartTime = 0L
    private var totalUpdates = 0L
    private val latencyHistory: MutableList<Double> = ArrayList()
    private val MAX_LATENCY_HISTORY = 100
    private val UPDATE_INTERVAL_MS = 500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single_player)

        Log.i(TAG, "🚀 SinglePlayerActivity 创建")

        // 初始化UI
        initViews()
        setupListeners()
        updateUI()
        checkPermissions()

        // 设置日志级别
        // FFmpegRTSPLibrary.enableLogging(true)

        Log.i(TAG, "✅ SinglePlayerActivity 初始化完成")
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

        // 初始化性能监控Handler
        performanceMonitorHandler = Handler(Looper.getMainLooper())
        performanceMonitorRunnable = Runnable {
            if (isPerformanceMonitoring && streamId >= 0) {
                updatePerformanceStats()
                performanceMonitorHandler?.postDelayed(performanceMonitorRunnable!!, UPDATE_INTERVAL_MS)
            }
        }

        // 设置性能监控面板展开/折叠
        performanceMonitorHeader.setOnClickListener { togglePerformanceMonitor() }

        // 设置默认URL
        etRtspUrl.setText("rtsp://stream.strba.sk:1935/strba/VYHLAD_JAZERO.stream")

        // 设置Surface回调
        surfaceView.holder.addCallback(this)
    }

    private fun setupListeners() {
        btnConnect.setOnClickListener { connectStream() }
        btnPlay.setOnClickListener { playStream() }
        btnStop.setOnClickListener { stopStream() }
        btnRecord.setOnClickListener { toggleRecording() }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1001
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("权限已授予")
            } else {
                showToast("需要存储权限才能录制视频")
            }
        }
    }

    // ============================================================================
    // 流管理方法
    // ============================================================================

    private fun connectStream() {
        val url = etRtspUrl.text.toString().trim()
        if (url.isEmpty()) {
            showToast("请输入RTSP URL")
            return
        }

        Log.i(TAG, "🚀 创建流: $url")

        // 🔥 使用新的软件解码架构
        val useSoftwareDecode = true // 强制使用软件解码进行测试
        streamId = FFmpegRTSPLibrary.createStreamWithDecodeMode(url, useSoftwareDecode)

        if (streamId < 0) {
            showToast("创建流失败")
            Log.e(TAG, "❌创建流失败")
            return
        }
        FFmpegRTSPLibrary.setSurface(streamId, surfaceView.holder.surface)
        isStreamCreated = true
        isPlaying = false
        isRecording = false

        showToast("流创建成功: ID=$streamId")
        Log.i(TAG, "✅流创建成功: ID=$streamId")

        updateUI()
    }

    private fun playStream() {
        if (!isStreamCreated || streamId < 0) {
            showToast("请先创建流")
            return
        }

        Log.i(TAG, "🚀 异步启动流: ID=$streamId")

        // 使用异步启动，避免阻塞UI线程
        FFmpegRTSPLibrary.startPlayAsync(streamId, object : FFmpegCallbacks.PlaybackStartCallback {

            override fun onPlaybackStarted(streamId: Int, videoInfo: VideoInfo?) {
                runOnUiThread {
                    isPlaying = true
                    showToast("流启动成功")
                    Log.i(TAG, "✅流启动成功")

                    // 📹 保存视频信息
                    currentVideoInfo = videoInfo
                    if (videoInfo != null) {
                        Log.i(TAG, "📹 视频信息就绪: $videoInfo")
                    } else {
                        Log.d(TAG, "📹 视频信息暂未就绪")
                    }

                    startPerformanceMonitoring()

                    updateUI()
                }
            }

            override fun onPlaybackError(streamId: Int, errorCode: Int, errorMessage: String) {
                runOnUiThread {
                    showToast("启动流失败: $errorMessage")
                    Log.e(TAG, "❌$errorMessage")
                    updateUI()
                }
            }
        })
    }

    private fun stopStream() {
        if (!isPlaying || streamId < 0) {
            showToast("流未启动")
            return
        }

        Log.i(TAG, "🛑 停止流: ID=$streamId")

        FFmpegRTSPLibrary.stopPlayAsync(streamId, object : FFmpegCallbacks.PlaybackStopCallback {

            override fun onPlaybackStopped(streamId: Int) {
                runOnUiThread {
                    isPlaying = false
                    // 流停止后停止性能监控
                    stopPerformanceMonitoring()
                    updateUI()
                }
            }

            override fun onPlaybackError(streamId: Int, errorCode: Int, errorMessage: String) {
                runOnUiThread {
                    isPlaying = false
                    showToast("停止流失败: $errorMessage")
                    Log.e(TAG, "❌停止流失败: $errorMessage")
                    updateUI()
                }
            }
        })
        isPlaying = false
        showToast("流停止成功")
        Log.i(TAG, "✅流停止成功")

        updateUI()
    }

    private fun destroyStream() {
        if (!isStreamCreated || streamId < 0) {
            showToast("没有可销毁的流")
            return
        }

        Log.i(TAG, "🗑️销毁流: ID=$streamId")

        val result = FFmpegRTSPLibrary.destroyStream(streamId)
        if (result != 0) {
            showToast("销毁流失败")
            Log.e(TAG, "❌销毁流失败")
            return
        }

        streamId = -1
        isStreamCreated = false
        isPlaying = false
        isRecording = false

        showToast("流销毁成功")
        Log.i(TAG, "✅流销毁成功")

        updateUI()
    }

    // ============================================================================
    // 录制功能
    // ============================================================================

    private fun toggleRecording() {
        if (!isPlaying || streamId < 0) {
            showToast("请先启动流")
            return
        }

        if (!isRecording) {
            startRecording()
        } else {
            stopRecording()
        }
    }

    private fun startRecording() {
        if (!isPlaying || streamId < 0) {
            showToast("请先启动流")
            return
        }

        if (isRecording) {
            showToast("已在录制中")
            return
        }

        val outputPath = getExternalFilesDir(null).toString() + "/recording_" +
                System.currentTimeMillis() + ".mp4"

        Log.i(TAG, "🎥 异步开始录制 ID=$streamId, path=$outputPath")

        FFmpegRTSPLibrary.startRecordingAsync(streamId, outputPath, object : FFmpegCallbacks.RecordingStartCallback {

            override fun onRecordingStarted(streamId: Int, outputPath: String) {
                runOnUiThread {
                    isRecording = true
                    showToast("录制开始")
                    Log.i(TAG, "✅录制开始: $outputPath")
                    updateUI()
                }
            }

            override fun onRecordingError(streamId: Int, errorCode: Int, errorMessage: String) {
                runOnUiThread {
                    showToast("录制开始失败: $errorMessage")
                    Log.e(TAG, "❌ 录制开始失败: $errorMessage")
                    updateUI()
                }
            }
        })
    }

    private fun stopRecording() {
        if (!isRecording || streamId < 0) {
            showToast("没有在录制")
            return
        }

        Log.i(TAG, "🛑 停止录制: ID=$streamId")

        val result = FFmpegRTSPLibrary.stopRecording(streamId)
        if (result == 0) {
            isRecording = false
            showToast("录制停止成功")
            Log.i(TAG, "✅录制停止成功")
        } else {
            showToast("停止录制失败")
            Log.e(TAG, "❌停止录制失败")
        }

        updateUI()
    }


    // ============================================================================
    // UI更新
    // ============================================================================

    private fun updateUI() {
        runOnUiThread {
            // 更新按钮状态
            btnConnect.isEnabled = !isStreamCreated
            btnPlay.isEnabled = isStreamCreated && !isPlaying
            btnStop.isEnabled = isPlaying
            btnRecord.isEnabled = isPlaying

            // 性能监控面板显示/隐藏
            if (isPlaying && streamId >= 0) {
                performanceMonitorCard.visibility = View.VISIBLE
            } else {
                performanceMonitorCard.visibility = View.GONE
                isPerformanceMonitorExpanded = false
                performanceMonitorContent.visibility = View.GONE
                performanceMonitorToggle.text = "▼"
            }

            // 更新状态显示
            val status = "状态: " + when {
                !isStreamCreated -> "未创建流"
                !isPlaying -> "流已创建，未启动"
                isRecording -> "播放中，录制中"
                else -> "播放中"
            }

            tvStatus.text = status

            // 更新统计信息
            var stats = "统计: 单流模式"
            if (isStreamCreated) {
                stats += " | 流ID: $streamId"
            }
            if (isRecording) {
                stats += " | 录制中"
            }
            tvStreamInfo.text = stats
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================================
    // Surface管理
    // ============================================================================

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(TAG, "🎬 Surface创建")

        if (streamId >= 0) {
            // 设置Surface到当前流
            val result = FFmpegRTSPLibrary.setSurface(streamId, holder.surface)
            if (result == 0) {
                Log.i(TAG, "✅Surface设置成功")
                showToast("Surface设置成功")
            } else {
                Log.e(TAG, "❌Surface设置失败")
                showToast("Surface设置失败")
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.i(TAG, "🎬 Surface变化: $width x $height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.i(TAG, "🗑️Surface销毁")

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
            performanceMonitorToggle.text = "▲"
        } else {
            performanceMonitorContent.visibility = View.GONE
            performanceMonitorToggle.text = "▼"
        }
    }

    private fun startPerformanceMonitoring() {
        if (streamId < 0 || isPerformanceMonitoring) {
            return
        }

        isPerformanceMonitoring = true
        monitoringStartTime = System.currentTimeMillis()
        totalUpdates = 0
        // latencyHistory.clear() // 这个方法在Java中可能不存在

        // 自动展开监控面板
        if (!isPerformanceMonitorExpanded) {
            togglePerformanceMonitor()
        }

        // 开始更新循环
        performanceMonitorHandler?.post(performanceMonitorRunnable!!)

        Log.i(TAG, "✅开始性能监控: streamId=$streamId")
    }

    private fun stopPerformanceMonitoring() {
        if (!isPerformanceMonitoring) {
            return
        }

        isPerformanceMonitoring = false
        performanceMonitorHandler?.removeCallbacks(performanceMonitorRunnable!!)
        monitoringStartTime = 0
        totalUpdates = 0
        // latencyHistory.clear()

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

            // 🔥 视频尺寸信息 - 使用播放成功时保存的视频信息
            var videoWidth = 0
            var videoHeight = 0
            var videoFps = 0
            var videoCodec = "unknown"

            // 使用播放成功时保存的视频信息
            if (currentVideoInfo != null) {
                videoWidth = currentVideoInfo!!.width
                videoHeight = currentVideoInfo!!.height
                videoFps = currentVideoInfo!!.fps
                videoCodec = currentVideoInfo!!.codec

                // 📹 在日志中显示视频尺寸信息
                Log.i(TAG, "📹 视频尺寸: ${videoWidth}x${videoHeight}, FPS: $videoFps, 编码: $videoCodec")
            } else {
                // 如果没有保存的视频信息，设置为等待状态
                videoCodec = "waiting"
                Log.d(TAG, "📹 视频信息等待中...")
            }

            // 🔥 新增：视频尺寸信息显示
            statsBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            statsBuilder.append("📹 视频信息\n")
            statsBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")

            if (videoWidth > 0 && videoHeight > 0) {
                // 视频信息已准备好
                statsBuilder.append(String.format(Locale.getDefault(),
                    "分辨率: %d x %d\n" +
                    "帧率: %d fps\n" +
                    "编码格式: %s\n",
                    videoWidth, videoHeight, videoFps, videoCodec))
            } else {
                // 视频信息等待中
                if ("waiting" == videoCodec) {
                    statsBuilder.append("🔄 正在解析视频信息...\n")
                } else {
                    statsBuilder.append("⏳ 等待视频流...\n")
                }
            }
            statsBuilder.append("\n")

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

    override fun onDestroy() {
        super.onDestroy()

        // 停止性能监控
        stopPerformanceMonitoring()

        // 清理Handler
        performanceMonitorHandler?.removeCallbacks(performanceMonitorRunnable!!)

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
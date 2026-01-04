package com.jxj.ffmpegrtspplayer

import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jxj.ffmpegrtsp.lib.transform.VideoTransformManager
import com.jxj.ffmpegrtsp.lib.FFmpegRTSPLibrary

/**
 * 画面变换测试Activity
 * 
 * 演示监控场景常用功能：
 * - 旋转（适配摄像头安装角度）
 * - 镜像（前置摄像头）
 * - 电子放大（区域放大查看）
 * - OSD叠加（时间戳、通道名、录制指示器）
 */
class TransformTestActivity : AppCompatActivity(), SurfaceHolder.Callback {
    
    companion object {
        private const val TAG = "TransformTestActivity"
    }
    
    // UI组件
    private lateinit var videoSurface: SurfaceView
    private lateinit var urlEditText: EditText
    private lateinit var createStreamButton: Button
    private lateinit var startStreamButton: Button
    private lateinit var stopStreamButton: Button
    private lateinit var destroyStreamButton: Button
    
    // 变换控制按钮
    private lateinit var rotateButton: Button
    private lateinit var mirrorButton: Button
    private lateinit var flipButton: Button
    private lateinit var zoomInButton: Button
    private lateinit var zoomOutButton: Button
    private lateinit var resetTransformButton: Button
    
    private lateinit var statusTextView: TextView
    private lateinit var transformStatusTextView: TextView
    private lateinit var zoomCenterLabelTextView: TextView
    
    // 流管理
    private var currentStreamId = -1
    private var isStreamCreated = false
    private var isStreamStarted = false
    
    // 变换状态
    private var currentRotation = 0  // 0, 1, 2, 3 代表 0°, 90°, 180°, 270°
    private var isMirrored = false
    private var isFlipped = false
    private var currentZoom = 1.0f
    
    // OSD状态
    private var showTimestamp = true
    private var showRecordingIndicator = false
    private var alarmBlink = false
    
    // 放大中心点控制
    private lateinit var zoomCenterXSeekBar: SeekBar
    private lateinit var zoomCenterYSeekBar: SeekBar
    private lateinit var zoomCenterXTextView: TextView
    private lateinit var zoomCenterYTextView: TextView
    private lateinit var zoomValueTextView: TextView
    private var zoomCenterX = 0.5f
    private var zoomCenterY = 0.5f
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transform_test)
        
        Log.i(TAG, "🚀 TransformTestActivity 创建")
        
        initViews()
        setupListeners()
        updateUI()
        
        // 设置日志级别
        // FFmpegRTSPLibrary.enableLogging(true)
        
        Log.i(TAG, "✅ TransformTestActivity 初始化完成")
    }
    
    private fun initViews() {
        // 从XML布局中获取视图
        videoSurface = findViewById(R.id.videoSurface)
        urlEditText = findViewById(R.id.urlEditText)
        statusTextView = findViewById(R.id.statusTextView)
        transformStatusTextView = findViewById(R.id.transformStatusTextView)
        zoomCenterLabelTextView = findViewById(R.id.zoomCenterLabelTextView)
        
        // 流控制按钮
        createStreamButton = findViewById(R.id.createStreamButton)
        startStreamButton = findViewById(R.id.startStreamButton)
        stopStreamButton = findViewById(R.id.stopStreamButton)
        destroyStreamButton = findViewById(R.id.destroyStreamButton)
        
        // 变换按钮
        rotateButton = findViewById(R.id.rotateButton)
        mirrorButton = findViewById(R.id.mirrorButton)
        flipButton = findViewById(R.id.flipButton)
        zoomInButton = findViewById(R.id.zoomInButton)
        zoomOutButton = findViewById(R.id.zoomOutButton)
        resetTransformButton = findViewById(R.id.resetTransformButton)
        
        // 放大中心点控制
        zoomCenterXSeekBar = findViewById(R.id.zoomCenterXSeekBar)
        zoomCenterYSeekBar = findViewById(R.id.zoomCenterYSeekBar)
        zoomCenterXTextView = findViewById(R.id.zoomCenterXTextView)
        zoomCenterYTextView = findViewById(R.id.zoomCenterYTextView)
        zoomValueTextView = findViewById(R.id.zoomValueTextView)
        
        // 设置Surface回调
        videoSurface.holder.addCallback(this)
        
        // 初始化放大中心点SeekBar
        zoomCenterXSeekBar.progress = 50
        zoomCenterYSeekBar.progress = 50
    }
    
    private fun setupListeners() {
        createStreamButton.setOnClickListener { createStream() }
        startStreamButton.setOnClickListener { startStream() }
        stopStreamButton.setOnClickListener { stopStream() }
        destroyStreamButton.setOnClickListener { destroyStream() }
        
        // 变换按钮
        rotateButton.setOnClickListener { rotateNext() }
        mirrorButton.setOnClickListener { toggleMirror() }
        flipButton.setOnClickListener { toggleFlip() }
        zoomInButton.setOnClickListener { zoomIn() }
        zoomOutButton.setOnClickListener { zoomOut() }
        resetTransformButton.setOnClickListener { resetTransform() }
        
        // 放大中心点控制
        zoomCenterXSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    zoomCenterX = progress / 100.0f
                    zoomCenterXTextView.text = String.format("%.2f", zoomCenterX)
                    updateZoomCenterLabel()
                    // 如果正在放大，实时更新放大中心点
                    if (currentStreamId >= 0 && currentZoom > 1.0f) {
                        VideoTransformManager.setZoom(currentStreamId, currentZoom, zoomCenterX, zoomCenterY)
                    }
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 停止拖动时更新一次
                if (currentStreamId >= 0 && currentZoom > 1.0f) {
                    VideoTransformManager.setZoom(currentStreamId, currentZoom, zoomCenterX, zoomCenterY)
                }
            }
        })
        
        zoomCenterYSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    zoomCenterY = progress / 100.0f
                    zoomCenterYTextView.text = String.format("%.2f", zoomCenterY)
                    updateZoomCenterLabel()
                    // 如果正在放大，实时更新放大中心点
                    if (currentStreamId >= 0 && currentZoom > 1.0f) {
                        VideoTransformManager.setZoom(currentStreamId, currentZoom, zoomCenterX, zoomCenterY)
                    }
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 停止拖动时更新一次
                if (currentStreamId >= 0 && currentZoom > 1.0f) {
                    VideoTransformManager.setZoom(currentStreamId, currentZoom, zoomCenterX, zoomCenterY)
                }
            }
        })
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
        
        Log.i(TAG, "🚀 创建流（软件解码）: $url")
        
        // 使用软件解码（变换功能需要）
        val useSoftwareDecode = true
        val streamId = FFmpegRTSPLibrary.createStreamWithDecodeMode(url, useSoftwareDecode)
        
        if (streamId < 0) {
            showToast("创建流失败")
            Log.e(TAG, "❌ 创建流失败")
            return
        }
        
        FFmpegRTSPLibrary.setSurface(streamId, videoSurface.holder.surface)
        currentStreamId = streamId
        isStreamCreated = true
        isStreamStarted = false
        
        showToast("流创建成功: ID=$streamId")
        Log.i(TAG, "✅ 流创建成功: ID=$streamId")
        
        updateUI()
    }
    
    private fun startStream() {
        if (!isStreamCreated || currentStreamId < 0) {
            showToast("请先创建流")
            return
        }
        
        Log.i(TAG, "🚀 异步启动流: ID=$currentStreamId")
        
        FFmpegRTSPLibrary.startPlayAsync(currentStreamId, object : FFmpegRTSPLibrary.PlaybackCallback {
            override fun onPlaybackStarted(streamId: Int) {
                runOnUiThread {
                    isStreamStarted = true
                    showToast("流启动成功")
                    Log.i(TAG, "✅ 流启动成功")
                    updateUI()
                }
            }
            
            override fun onPlaybackError(streamId: Int, errorCode: Int, errorMessage: String) {
                runOnUiThread {
                    showToast("启动流失败: $errorMessage")
                    Log.e(TAG, "❌ $errorMessage")
                    updateUI()
                }
            }
            
            override fun onPlaybackStopped(streamId: Int) {
                // 不需要处理
            }
        })
    }
    
    private fun stopStream() {
        if (!isStreamStarted || currentStreamId < 0) {
            showToast("流未启动")
            return
        }
        
        val result = FFmpegRTSPLibrary.stopStream(currentStreamId)
        if (result != 0) {
            showToast("停止流失败")
            return
        }
        
        isStreamStarted = false
        showToast("流停止成功")
        updateUI()
    }
    
    private fun destroyStream() {
        if (!isStreamCreated || currentStreamId < 0) {
            showToast("没有可销毁的流")
            return
        }
        
        val result = FFmpegRTSPLibrary.destroyStream(currentStreamId)
        if (result != 0) {
            showToast("销毁流失败")
            return
        }
        
        currentStreamId = -1
        isStreamCreated = false
        isStreamStarted = false
        
        // 重置变换状态
        currentRotation = 0
        isMirrored = false
        isFlipped = false
        currentZoom = 1.0f
        
        showToast("流销毁成功")
        updateUI()
    }
    
    // ============================================================================
    // 画面变换控制
    // ============================================================================
    
    private fun rotateNext() {
        if (currentStreamId < 0) {
            showToast("请先创建流")
            return
        }
        
        currentRotation = (currentRotation + 1) % 4
        val rotation = VideoTransformManager.Rotation.values()[currentRotation]
        VideoTransformManager.setRotation(currentStreamId, rotation)
        
        rotateButton.text = "旋转 (当前: ${currentRotation * 90}°)"
        showToast("旋转: ${currentRotation * 90}°")
        updateTransformStatus()
    }
    
    private fun toggleMirror() {
        if (currentStreamId < 0) {
            showToast("请先创建流")
            return
        }
        
        isMirrored = !isMirrored
        VideoTransformManager.setMirrorHorizontal(currentStreamId, isMirrored)
        
        mirrorButton.text = "水平镜像 (${if (isMirrored) "开" else "关"})"
        showToast("水平镜像: ${if (isMirrored) "开启" else "关闭"}")
        updateTransformStatus()
    }
    
    private fun toggleFlip() {
        if (currentStreamId < 0) {
            showToast("请先创建流")
            return
        }
        
        isFlipped = !isFlipped
        VideoTransformManager.setFlipVertical(currentStreamId, isFlipped)
        
        flipButton.text = "垂直翻转 (${if (isFlipped) "开" else "关"})"
        showToast("垂直翻转: ${if (isFlipped) "开启" else "关闭"}")
        updateTransformStatus()
    }
    
    private fun zoomIn() {
        if (currentStreamId < 0) {
            showToast("请先创建流")
            return
        }
        
        currentZoom = minOf(8.0f, currentZoom + 0.5f)
        // 🔥 使用可调整的中心点坐标，而不是写死的0.5f
        VideoTransformManager.setZoom(currentStreamId, currentZoom, zoomCenterX, zoomCenterY)
        
        zoomValueTextView.text = String.format("%.1fx", currentZoom)
        showToast("放大: ${String.format("%.1f", currentZoom)}x")
        updateTransformStatus()
    }
    
    private fun zoomOut() {
        if (currentStreamId < 0) {
            showToast("请先创建流")
            return
        }
        
        currentZoom = maxOf(1.0f, currentZoom - 0.5f)
        // 🔥 使用可调整的中心点坐标，而不是写死的0.5f
        VideoTransformManager.setZoom(currentStreamId, currentZoom, zoomCenterX, zoomCenterY)
        
        zoomValueTextView.text = String.format("%.1fx", currentZoom)
        showToast("缩小: ${String.format("%.1f", currentZoom)}x")
        updateTransformStatus()
    }
    
    private fun resetTransform() {
        if (currentStreamId < 0) {
            showToast("请先创建流")
            return
        }
        
        VideoTransformManager.resetTransform(currentStreamId)
        
        currentRotation = 0
        isMirrored = false
        isFlipped = false
        currentZoom = 1.0f
        zoomCenterX = 0.5f
        zoomCenterY = 0.5f
        
        rotateButton.text = "旋转 (当前: 0°)"
        mirrorButton.text = "水平镜像 (关)"
        flipButton.text = "垂直翻转 (关)"
        zoomValueTextView.text = "1.0x"
        zoomCenterXSeekBar.progress = 50
        zoomCenterYSeekBar.progress = 50
        zoomCenterXTextView.text = "0.50"
        zoomCenterYTextView.text = "0.50"
        updateZoomCenterLabel()
        
        showToast("变换已重置")
        updateTransformStatus()
    }
    
    // ============================================================================
    // Surface 回调
    // ============================================================================
    
    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(TAG, "🎬 Surface创建")
        if (currentStreamId >= 0) {
            val result = FFmpegRTSPLibrary.setSurface(currentStreamId, holder.surface)
            if (result == 0) {
                Log.i(TAG, "✅ Surface设置成功")
            }
        }
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.i(TAG, "🎬 Surface变化: ${width}x$height")
    }
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.i(TAG, "🗑️ Surface销毁")
        if (currentStreamId >= 0) {
            FFmpegRTSPLibrary.onSurfaceDestroyed(currentStreamId)
        }
    }
    
    // ============================================================================
    // UI 更新
    // ============================================================================
    
    private fun updateUI() {
        runOnUiThread {
            createStreamButton.isEnabled = !isStreamCreated
            startStreamButton.isEnabled = isStreamCreated && !isStreamStarted
            stopStreamButton.isEnabled = isStreamStarted
            destroyStreamButton.isEnabled = isStreamCreated
            
            val status = when {
                !isStreamCreated -> "状态: 未创建流"
                !isStreamStarted -> "状态: 流已创建，未启动"
                else -> "状态: 播放中"
            }
            statusTextView.text = status
            
            updateTransformStatus()
        }
    }
    
    private fun updateTransformStatus() {
        runOnUiThread {
            val sb = StringBuilder("变换: ")
            if (currentRotation > 0) {
                sb.append("旋转${currentRotation * 90}° ")
            }
            if (isMirrored) {
                sb.append("镜像 ")
            }
            if (isFlipped) {
                sb.append("翻转 ")
            }
            if (currentZoom > 1.0f) {
                sb.append("放大${String.format("%.1f", currentZoom)}x ")
                sb.append("中心(${String.format("%.2f", zoomCenterX)},${String.format("%.2f", zoomCenterY)}) ")
            }
            if (sb.toString() == "变换: ") {
                sb.append("无")
            }
            transformStatusTextView.text = sb.toString()
        }
    }
    
    private fun updateZoomCenterLabel() {
        zoomCenterLabelTextView.text = String.format("放大中心点 (X: %.2f, Y: %.2f)", zoomCenterX, zoomCenterY)
    }
    
    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (currentStreamId >= 0) {
            try {
                FFmpegRTSPLibrary.stopStream(currentStreamId)
                FFmpegRTSPLibrary.destroyStream(currentStreamId)
            } catch (e: Exception) {
                Log.e(TAG, "❌ 销毁流异常", e)
            }
        }

    }
}

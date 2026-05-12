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
import com.jxj.ffmpegrtsp.lib.api.PlayerStateSnapshot
import com.jxj.ffmpegrtsp.lib.api.StreamConfig
import com.jxj.ffmpegrtsp.lib.api.StreamErrorCode
import com.jxj.ffmpegrtsp.lib.api.StreamPlayer
import com.jxj.ffmpegrtsp.lib.api.StreamStateCode
import com.jxj.ffmpegrtsp.lib.transform.VideoTransformManager
import java.util.Locale

/**
 * 画面变换示例。
 *
 * 变换模块仍通过 streamId 工作，但播放器生命周期交给 StreamPlayer 管理。
 */
class TransformTestActivity : BaseInsetsActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "TransformTestActivity"
    }

    private lateinit var videoSurface: SurfaceView
    private lateinit var urlEditText: EditText
    private lateinit var startStreamButton: Button
    private lateinit var stopStreamButton: Button
    private lateinit var destroyStreamButton: Button
    private lateinit var rotateButton: Button
    private lateinit var mirrorButton: Button
    private lateinit var flipButton: Button
    private lateinit var zoomInButton: Button
    private lateinit var zoomOutButton: Button
    private lateinit var resetTransformButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var transformStatusTextView: TextView
    private lateinit var zoomCenterLabelTextView: TextView
    private lateinit var zoomCenterXSeekBar: SeekBar
    private lateinit var zoomCenterYSeekBar: SeekBar
    private lateinit var zoomCenterXTextView: TextView
    private lateinit var zoomCenterYTextView: TextView
    private lateinit var zoomValueTextView: TextView

    private var player: StreamPlayer? = null
    private var currentState: PlayerStateSnapshot = emptyState()
    private var isStreamStarted = false
    private var currentRotation = 0
    private var isMirrored = false
    private var isFlipped = false
    private var currentZoom = 1.0f
    private var zoomCenterX = 0.5f
    private var zoomCenterY = 0.5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transform_test)
        applyEdgeToEdge(findViewById(android.R.id.content), top = true, bottom = true)

        initViews()
        setupListeners()
        updateZoomCenterLabel()
        updateUI()
    }

    private fun initViews() {
        videoSurface = findViewById(R.id.videoSurface)
        urlEditText = findViewById(R.id.urlEditText)
        statusTextView = findViewById(R.id.statusTextView)
        transformStatusTextView = findViewById(R.id.transformStatusTextView)
        zoomCenterLabelTextView = findViewById(R.id.zoomCenterLabelTextView)
        startStreamButton = findViewById(R.id.startStreamButton)
        stopStreamButton = findViewById(R.id.stopStreamButton)
        destroyStreamButton = findViewById(R.id.destroyStreamButton)
        rotateButton = findViewById(R.id.rotateButton)
        mirrorButton = findViewById(R.id.mirrorButton)
        flipButton = findViewById(R.id.flipButton)
        zoomInButton = findViewById(R.id.zoomInButton)
        zoomOutButton = findViewById(R.id.zoomOutButton)
        resetTransformButton = findViewById(R.id.resetTransformButton)
        zoomCenterXSeekBar = findViewById(R.id.zoomCenterXSeekBar)
        zoomCenterYSeekBar = findViewById(R.id.zoomCenterYSeekBar)
        zoomCenterXTextView = findViewById(R.id.zoomCenterXTextView)
        zoomCenterYTextView = findViewById(R.id.zoomCenterYTextView)
        zoomValueTextView = findViewById(R.id.zoomValueTextView)

        urlEditText.setText("http://demo-videos.qnsdk.com/VR-Panorama-Equirect-Angular-4500k.mp4")
        zoomCenterXSeekBar.progress = 50
        zoomCenterYSeekBar.progress = 50
        videoSurface.holder.addCallback(this)
    }

    private fun setupListeners() {
        startStreamButton.setOnClickListener { ensurePlayerAndPlay() }
        stopStreamButton.setOnClickListener { stopStream() }
        destroyStreamButton.setOnClickListener { releasePlayer() }
        rotateButton.setOnClickListener { rotateNext() }
        mirrorButton.setOnClickListener { toggleMirror() }
        flipButton.setOnClickListener { toggleFlip() }
        zoomInButton.setOnClickListener { zoomIn() }
        zoomOutButton.setOnClickListener { zoomOut() }
        resetTransformButton.setOnClickListener { resetTransform() }

        zoomCenterXSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                zoomCenterX = progress / 100f
                zoomCenterXTextView.text = String.format(Locale.getDefault(), "%.2f", zoomCenterX)
                updateZoomCenterLabel()
                applyZoomIfNeeded()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = applyZoomIfNeeded()
        })

        zoomCenterYSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                zoomCenterY = progress / 100f
                zoomCenterYTextView.text = String.format(Locale.getDefault(), "%.2f", zoomCenterY)
                updateZoomCenterLabel()
                applyZoomIfNeeded()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = applyZoomIfNeeded()
        })
    }

    private fun ensurePlayerAndPlay() {
        val url = urlEditText.text.toString().trim()
        if (url.isEmpty()) {
            showToast("请输入 RTSP 地址")
            return
        }

        val currentPlayer = player
        if (currentPlayer != null && !currentPlayer.isReleased()) {
            isStreamStarted = true
            currentPlayer.play()
            refreshStateFromPlayer()
            return
        }

        isStreamStarted = true
        player = StreamPlayer.playWithConfig(
            this,
            videoSurface,
            StreamConfig.Builder(url)
                .useSoftwareDecode(true)
                .audioEnabled(false)
                .build()
        )
            .setOnStateChanged {
                syncState(it)
            }
            .setOnPlaybackStarted {
                isStreamStarted = true
                refreshStateFromPlayer()
                Log.i(TAG, "播放开始，streamId=${player?.getStreamId()}")
                updateUI()
            }
            .setOnPlaybackStopped {
                isStreamStarted = false
                refreshStateFromPlayer()
                Log.i(TAG, "播放停止")
                updateUI()
            }
            .setOnError { errorCode, errorMessage ->
                isStreamStarted = false
                refreshStateFromPlayer()
                Log.e(TAG, "播放器错误: $errorCode / $errorMessage")
                showToast("播放器错误: $errorMessage")
                updateUI()
            }

        resetTransformState()
        refreshStateFromPlayer()
    }

    private fun stopStream() {
        isStreamStarted = false
        player?.stop()
        refreshStateFromPlayer()
    }

    private fun syncState(state: PlayerStateSnapshot?) {
        currentState = state ?: emptyState()
        when {
            currentState.isPlaying -> isStreamStarted = true
            !currentState.isCreated || currentState.isReleased -> isStreamStarted = false
        }
        updateUI()
    }

    private fun refreshStateFromPlayer() {
        syncState(player?.getState())
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        currentState = emptyState()
        isStreamStarted = false
        resetTransformState()
        updateUI()
    }

    private fun rotateNext() {
        val streamId = currentStreamId() ?: return showToast("请先开始播放")
        currentRotation = (currentRotation + 1) % 4
        VideoTransformManager.setRotation(
            streamId,
            VideoTransformManager.Rotation.values()[currentRotation]
        )
        rotateButton.text = "旋转 (当前: ${currentRotation * 90}°)"
        updateTransformStatus()
    }

    private fun toggleMirror() {
        val streamId = currentStreamId() ?: return showToast("请先开始播放")
        isMirrored = !isMirrored
        VideoTransformManager.setMirrorHorizontal(streamId, isMirrored)
        mirrorButton.text = "水平镜像 (${if (isMirrored) "开" else "关"})"
        updateTransformStatus()
    }

    private fun toggleFlip() {
        val streamId = currentStreamId() ?: return showToast("请先开始播放")
        isFlipped = !isFlipped
        VideoTransformManager.setFlipVertical(streamId, isFlipped)
        flipButton.text = "垂直翻转 (${if (isFlipped) "开" else "关"})"
        updateTransformStatus()
    }

    private fun zoomIn() {
        if (currentStreamId() == null) {
            showToast("请先开始播放")
            return
        }
        currentZoom = minOf(8f, currentZoom + 0.5f)
        applyZoomIfNeeded(forceApply = true)
        zoomValueTextView.text = String.format(Locale.getDefault(), "%.1fx", currentZoom)
        updateTransformStatus()
    }

    private fun zoomOut() {
        if (currentStreamId() == null) {
            showToast("请先开始播放")
            return
        }
        currentZoom = maxOf(1f, currentZoom - 0.5f)
        applyZoomIfNeeded(forceApply = true)
        zoomValueTextView.text = String.format(Locale.getDefault(), "%.1fx", currentZoom)
        updateTransformStatus()
    }

    private fun resetTransform() {
        val streamId = currentStreamId() ?: return showToast("请先开始播放")
        VideoTransformManager.resetTransform(streamId)
        resetTransformState()
        updateTransformStatus()
    }

    private fun applyZoomIfNeeded(forceApply: Boolean = false) {
        val streamId = currentStreamId() ?: return
        if (!forceApply && currentZoom <= 1f) {
            return
        }
        VideoTransformManager.setZoom(streamId, currentZoom, zoomCenterX, zoomCenterY)
        updateTransformStatus()
    }

    private fun currentStreamId(): Int? {
        val currentPlayer = player ?: return null
        if (currentPlayer.isReleased()) {
            return null
        }
        return currentPlayer.getStreamId()
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

    override fun onResume() {
        super.onResume()
        refreshStateFromPlayer()
    }

    override fun onPause() {
        refreshStateFromPlayer()
        super.onPause()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    private fun updateUI() {
        runOnUiThread {
            val currentPlayer = player
            val hasPlayer = currentPlayer != null && currentPlayer.isReleased() == false
            val isPending = currentState.isOperationPending
            val isPlaying = hasPlayer && (currentState.isPlaying || isStreamStarted)
            val enableTransformControls = hasPlayer && !isPending

            startStreamButton.isEnabled = !isPlaying && !isPending
            stopStreamButton.isEnabled = hasPlayer && isPlaying && !isPending
            destroyStreamButton.isEnabled = hasPlayer && !isPending
            rotateButton.isEnabled = enableTransformControls
            mirrorButton.isEnabled = enableTransformControls
            flipButton.isEnabled = enableTransformControls
            zoomInButton.isEnabled = enableTransformControls
            zoomOutButton.isEnabled = enableTransformControls
            resetTransformButton.isEnabled = enableTransformControls
            zoomCenterXSeekBar.isEnabled = enableTransformControls
            zoomCenterYSeekBar.isEnabled = enableTransformControls

            val status = when {
                !hasPlayer -> "状态: 未开始播放"
                isPending -> "状态: 操作进行中"
                isPlaying -> "状态: 播放中"
                else -> "状态: 已停止"
            }
            statusTextView.text = status
            updateTransformStatus()
        }
    }

    private fun updateTransformStatus() {
        val parts = mutableListOf<String>()
        if (currentRotation > 0) {
            parts.add("旋转${currentRotation * 90}°")
        }
        if (isMirrored) {
            parts.add("水平镜像")
        }
        if (isFlipped) {
            parts.add("垂直翻转")
        }
        if (currentZoom > 1f) {
            parts.add(
                String.format(
                    Locale.getDefault(),
                    "缩放 %.1fx 中心(%.2f, %.2f)",
                    currentZoom,
                    zoomCenterX,
                    zoomCenterY
                )
            )
        }
        val text = if (parts.isEmpty()) {
            "变换: 无"
        } else {
            "变换: ${parts.joinToString(" ")}"
        }
        transformStatusTextView.text = text
    }

    private fun updateZoomCenterLabel() {
        zoomCenterLabelTextView.text = String.format(
            Locale.getDefault(),
            "放大中心点 (X: %.2f, Y: %.2f)",
            zoomCenterX,
            zoomCenterY
        )
    }

    private fun resetTransformState() {
        currentRotation = 0
        isMirrored = false
        isFlipped = false
        currentZoom = 1f
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
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
            1.0f
        )
    }
}

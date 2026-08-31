package com.jxj.ffmpegrtspplayer

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.switchmaterial.SwitchMaterial
import com.jxj.ffmpegrtsp.lib.ai.AiModelSpec
import com.jxj.ffmpegrtsp.lib.ai.AiRuntimeSpec
import com.jxj.ffmpegrtsp.lib.ai.AiSessionConfig
import com.jxj.ffmpegrtsp.lib.ai.AiSessionHandle
import com.jxj.ffmpegrtsp.lib.api.AudioOptions
import com.jxj.ffmpegrtsp.lib.api.StreamConfig
import com.jxj.ffmpegrtsp.lib.api.StreamPlayer
import com.jxj.ffmpegrtsp.lib.api.VideoOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Public SDK demonstration page. It deliberately uses the same public API as
 * an integrator: two AARs, an asset model package, and a generated manifest.
 */
class AiDemoActivity : BaseInsetsActivity(), SurfaceHolder.Callback {
    companion object {
        private const val TAG = "AiDemoActivity"
        private const val MODEL_ASSET_ROOT = "models/yolox-l"
        private const val MODEL_DIR = "models/yolox-l"
        private const val MODEL_ID = "yolox-l-coco-official-ncnn-0.1.1rc0"
        private const val ADAPTER_ID = "yolox_raw_head"

        private val COCO_LABELS = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
            "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed",
            "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave", "oven",
            "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
        )
    }

    private lateinit var url: EditText
    private lateinit var surface: SurfaceView
    private lateinit var status: TextView
    private lateinit var labelsContainer: LinearLayout
    private lateinit var showLabels: SwitchMaterial
    private lateinit var fontSize: SeekBar
    private lateinit var lineWidth: SeekBar
    private lateinit var fontValue: TextView
    private lateinit var lineValue: TextView
    private lateinit var boxColorButton: Button
    private lateinit var configStatus: TextView
    private lateinit var toggleOrientationButton: Button

    private data class UiFormState(
        val url: String,
        val selectedLabels: List<Boolean>,
        val showLabels: Boolean,
        val fontProgress: Int,
        val lineProgress: Int,
        val boxColor: FloatArray,
        val status: String,
        val configStatus: String,
        val aiToggleText: String,
        val aiToggleEnabled: Boolean
    )

    private val labelChecks = ArrayList<CheckBox>(COCO_LABELS.size)
    private var selectedBoxColor = floatArrayOf(0.1f, 1.0f, 0.1f, 0.95f)
    private var player: StreamPlayer? = null
    private var aiSession: AiSessionHandle? = null
    private var surfaceReady = false
    @Volatile private var aiStarting = false
    @Volatile private var aiStartCancelled = false
    private val aiExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ai-session-start").apply { isDaemon = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_demo)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyEdgeToEdge(findViewById(R.id.ai_root), top = !landscape, bottom = !landscape)
        bindViews()
        buildLabelControls()
        bindListeners()
        applyWindowMode(resources.configuration)
        updateStyleSummary()
        surface.holder.addCallback(this)
    }

    private fun bindViews() {
        url = findViewById(R.id.ai_rtsp_url)
        surface = findViewById(R.id.ai_surface_view)
        status = findViewById(R.id.ai_status)
        labelsContainer = findViewById(R.id.ai_labels_container)
        showLabels = findViewById(R.id.ai_show_labels)
        fontSize = findViewById(R.id.ai_font_size)
        lineWidth = findViewById(R.id.ai_line_width)
        fontValue = findViewById(R.id.ai_font_size_value)
        lineValue = findViewById(R.id.ai_line_width_value)
        boxColorButton = findViewById(R.id.ai_box_color)
        configStatus = findViewById(R.id.ai_config_status)
        toggleOrientationButton = findViewById(R.id.ai_toggle_orientation)
    }

    private fun buildLabelControls() {
        labelChecks.clear()
        COCO_LABELS.forEachIndexed { index, label ->
            val check = CheckBox(this).apply {
                text = label
                isChecked = index == 0
                minHeight = 44
            }
            labelsContainer.addView(check)
            labelChecks += check
        }
    }

    private fun bindListeners() {
        findViewById<Button>(R.id.ai_play).setOnClickListener { startPlayback() }
        findViewById<Button>(R.id.ai_stop).setOnClickListener { stopPlayback() }
        findViewById<Button>(R.id.ai_toggle).setOnClickListener { toggleAi() }
        toggleOrientationButton.setOnClickListener { toggleOrientation() }
        showLabels.setOnCheckedChangeListener { _, _ -> updateStyleSummary() }
        fontSize.setOnSeekBarChangeListener(simpleSeekBar { updateStyleSummary() })
        lineWidth.setOnSeekBarChangeListener(simpleSeekBar { updateStyleSummary() })
        boxColorButton.setOnClickListener {
            selectedBoxColor = when {
                selectedBoxColor[0] > 0.8f -> floatArrayOf(1.0f, 0.75f, 0.05f, 0.95f)
                selectedBoxColor[1] > 0.8f -> floatArrayOf(0.95f, 0.15f, 0.15f, 0.95f)
                else -> floatArrayOf(0.1f, 1.0f, 0.1f, 0.95f)
            }
            updateStyleSummary()
        }
    }

    private fun simpleSeekBar(action: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = action()
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun updateStyleSummary() {
        val pixels = fontSize.progress + 4
        val width = lineWidth.progress + 1
        fontValue.text = "字体大小：$pixels px"
        lineValue.text = "框线宽度：$width px"
        boxColorButton.text = "框颜色：${when {
            selectedBoxColor[0] > 0.8f -> "黄色"
            selectedBoxColor[1] > 0.8f -> "绿色"
            else -> "红色"
        }}"
    }

    private fun startPlayback() {
        val streamUrl = url.text?.toString()?.trim().orEmpty()
        if (streamUrl.isEmpty()) {
            toast("请输入 RTSP 地址")
            return
        }
        if (player != null && !player!!.isReleased()) {
            player!!.play()
            status.text = "正在播放"
            return
        }
        try {
            val config = StreamConfig.Builder(streamUrl)
                .video(VideoOptions.hardware(StreamConfig.HardwareRenderMode.OPENGL_TEXTURE))
                .audio(AudioOptions.disabled())
                .build()
            player = StreamPlayer.playWithConfig(this, surface, config)
                .setOnPlaybackStarted { runOnUiThread { status.text = "播放中" } }
                .setOnError { _, message -> runOnUiThread { status.text = "播放错误"; toast(message) } }
            status.text = "正在连接"
        } catch (error: Exception) {
            Log.e(TAG, "playback start failed", error)
            status.text = "播放失败"
            toast(error.message ?: "播放失败")
        }
    }

    private fun toggleAi() {
        if (aiStarting) return
        val current = aiSession
        if (current != null && current.isRunning()) {
            current.close()
            aiSession = null
            findViewById<Button>(R.id.ai_toggle).text = "启动 AI 识别"
            configStatus.text = "AI 已停止"
            return
        }
        val currentPlayer = player
        if (currentPlayer == null || currentPlayer.isReleased()) {
            toast("请先开始播放")
            return
        }
        // Model copying, manifest generation, provider loading and NCNN model
        // loading can take seconds for YOLOX-L. Keep all of it off the UI thread.
        val selectedLabels = labelChecks.map { check ->
            if (check.isChecked) check.text.toString() else ""
        }
        val selectedShowLabels = showLabels.isChecked
        val selectedFontSize = fontSize.progress + 4
        val selectedLineWidth = lineWidth.progress + 1
        val selectedBoxColor = this.selectedBoxColor.copyOf()
        aiStarting = true
        aiStartCancelled = false
        val toggle = findViewById<Button>(R.id.ai_toggle)
        toggle.isEnabled = false
        toggle.text = "启动 AI 识别中..."
        configStatus.text = "正在加载 YOLOX-L 模型..."
        aiExecutor.execute {
            var started: AiSessionHandle? = null
            try {
                val modelRoot = prepareModelPackage()
                writeGeneratedManifest(
                    modelRoot,
                    selectedLabels,
                    selectedShowLabels,
                    selectedFontSize,
                    selectedLineWidth,
                    selectedBoxColor
                )
                val runtime = AiRuntimeSpec("ncnn", "ffmpegrtsp_ai_ncnn")
                runtime.loadProviderLibrary()
                started = currentPlayer.attachAiSession(
                    AiSessionConfig.builder(
                        runtime,
                        AiModelSpec.fromDirectory(MODEL_ID, ADAPTER_ID, modelRoot.absolutePath)
                    ).sampleFps(20).useGpuRgb(true).build()
                ) ?: error("AI session start returned null")
                val session = started
                runOnUiThread {
                    aiStarting = false
                    val currentToggle = findViewById<Button>(R.id.ai_toggle)
                    currentToggle.isEnabled = true
                    val accept = !aiStartCancelled && !isFinishing && !isDestroyed &&
                        player === currentPlayer && !currentPlayer.isReleased()
                    if (accept) {
                        aiSession = session
                        currentToggle.text = "停止 AI 识别"
                        configStatus.text = "模型：$MODEL_ID\n已生成配置：${modelRoot.absolutePath}/model.json"
                    } else {
                        session.close()
                        currentToggle.text = "启动 AI 识别"
                    }
                }
            } catch (error: Exception) {
                started?.close()
                Log.e(TAG, "AI start failed", error)
                runOnUiThread {
                    aiStarting = false
                    val currentToggle = findViewById<Button>(R.id.ai_toggle)
                    currentToggle.isEnabled = true
                    currentToggle.text = "启动 AI 识别"
                    if (!aiStartCancelled && !isFinishing && !isDestroyed) {
                        configStatus.text = "AI 启动失败"
                        toast(error.message ?: "AI 启动失败")
                    }
                }
            }
        }
    }

    private fun prepareModelPackage(): File {
        val root = File(getExternalFilesDir(null), MODEL_DIR)
        if (!root.exists() && !root.mkdirs()) error("无法创建模型目录")
        val versionFile = File(root, ".model-version")
        val installedVersion = versionFile.takeIf { it.isFile }?.readText()?.trim()
        if (installedVersion != MODEL_ID) {
            // An APK update preserves getExternalFilesDir(). Replace stale
            // model files when the packaged model identity changes, and only
            // publish the marker after both copies complete successfully.
            copyAssetAtomic("$MODEL_ASSET_ROOT/yolox.param", File(root, "yolox.param"))
            copyAssetAtomic("$MODEL_ASSET_ROOT/yolox.bin", File(root, "yolox.bin"))
            versionFile.writeText(MODEL_ID, Charsets.UTF_8)
        } else {
            copyAssetIfMissing("$MODEL_ASSET_ROOT/yolox.param", File(root, "yolox.param"))
            copyAssetIfMissing("$MODEL_ASSET_ROOT/yolox.bin", File(root, "yolox.bin"))
        }
        return root
    }

    private fun copyAssetAtomic(assetPath: String, destination: File) {
        val temp = File(destination.parentFile, ".${destination.name}.tmp")
        assets.open(assetPath).use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        if (destination.exists() && !destination.delete()) error("无法替换模型文件：${destination.name}")
        if (!temp.renameTo(destination)) error("无法提交模型文件：${destination.name}")
    }

    private fun copyAssetIfMissing(assetPath: String, destination: File) {
        if (destination.isFile && destination.length() > 0L) return
        assets.open(assetPath).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun writeGeneratedManifest(
        root: File,
        selectedLabels: List<String>,
        selectedShowLabels: Boolean,
        selectedFontSize: Int,
        selectedLineWidth: Int,
        selectedBoxColor: FloatArray
    ) {
        val template = assets.open("$MODEL_ASSET_ROOT/model.template.json").bufferedReader().use { it.readText() }
        val json = JSONObject(template)
        val labels = JSONArray()
        selectedLabels.forEach { labels.put(it) }
        json.put("labels", labels)
        json.put("showLabels", selectedShowLabels)
        json.put("labelFontSize", selectedFontSize)
        json.put("boxLineWidth", selectedLineWidth)
        json.put("boxColor", JSONArray(selectedBoxColor.toList()))
        val temp = File(root, "model.json.tmp")
        temp.writeText(json.toString(2), Charsets.UTF_8)
        val target = File(root, "model.json")
        if (target.exists() && !target.delete()) error("无法替换 model.json")
        if (!temp.renameTo(target)) error("无法提交 model.json")
    }

    private fun stopPlayback() {
        aiStartCancelled = true
        aiSession?.close()
        aiSession = null
        player?.stop()
        status.text = "已停止"
        findViewById<Button>(R.id.ai_toggle).text = "启动 AI 识别"
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rebindLayoutForCurrentConfiguration(newConfig)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyWindowMode(resources.configuration)
        }
    }

    private fun toggleOrientation() {
        requestedOrientation = if (isLandscape()) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun rebindLayoutForCurrentConfiguration(configuration: Configuration) {
        val formState = captureUiFormState()
        setContentView(R.layout.activity_ai_demo)
        val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyEdgeToEdge(findViewById(R.id.ai_root), top = !landscape, bottom = !landscape)
        bindViews()
        buildLabelControls()
        restoreUiFormState(formState)
        bindListeners()
        applyWindowMode(configuration)
        surface.holder.addCallback(this)

        player?.takeIf { !it.isReleased() }?.let { currentPlayer ->
            surface.post {
                if (!currentPlayer.isReleased()) {
                    currentPlayer.attachSurface(surface)
                }
            }
        }
    }

    private fun captureUiFormState(): UiFormState {
        return UiFormState(
            url = url.text?.toString().orEmpty(),
            selectedLabels = labelChecks.map { it.isChecked },
            showLabels = showLabels.isChecked,
            fontProgress = fontSize.progress,
            lineProgress = lineWidth.progress,
            boxColor = selectedBoxColor.copyOf(),
            status = status.text?.toString().orEmpty(),
            configStatus = configStatus.text?.toString().orEmpty(),
            aiToggleText = findViewById<Button>(R.id.ai_toggle).text?.toString().orEmpty(),
            aiToggleEnabled = findViewById<Button>(R.id.ai_toggle).isEnabled
        )
    }

    private fun restoreUiFormState(state: UiFormState) {
        url.setText(state.url)
        labelChecks.forEachIndexed { index, check ->
            check.isChecked = state.selectedLabels.getOrElse(index) { false }
        }
        showLabels.isChecked = state.showLabels
        fontSize.progress = state.fontProgress
        lineWidth.progress = state.lineProgress
        selectedBoxColor = state.boxColor.copyOf()
        status.text = state.status
        configStatus.text = state.configStatus
        findViewById<Button>(R.id.ai_toggle).apply {
            text = state.aiToggleText
            isEnabled = state.aiToggleEnabled
        }
        updateStyleSummary()
    }

    private fun applyWindowMode(configuration: Configuration) {
        val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        toggleOrientationButton.text = if (landscape) "退出横屏" else "进入横屏"
        if (landscape) {
            supportActionBar?.hide()
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else {
            supportActionBar?.show()
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun isLandscape(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        player?.takeIf { !it.isReleased() }?.attachSurface(surface)
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) { surfaceReady = false }

    override fun onDestroy() {
        aiStartCancelled = true
        aiExecutor.shutdownNow()
        aiSession?.close()
        aiSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

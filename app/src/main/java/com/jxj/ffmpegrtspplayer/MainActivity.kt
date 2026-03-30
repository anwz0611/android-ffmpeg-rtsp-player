package com.jxj.ffmpegrtspplayer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.jxj.ffmpegrtsp.lib.StreamPlayer

class MainActivity : BaseInsetsActivity() {

    private lateinit var btnSinglePlayer: Button
    private lateinit var btnMultiPlayer: Button
    private lateinit var btnYuvTest: Button
    private lateinit var btnTransformTest: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyEdgeToEdge(findViewById(android.R.id.content), top = true, bottom = true)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        btnSinglePlayer = findViewById(R.id.btn_single_player)
        btnMultiPlayer = findViewById(R.id.btn_multi_player)
        btnYuvTest = findViewById(R.id.btn_yuv_test)
        btnTransformTest = findViewById(R.id.btn_transform_test)
    }

    private fun setupClickListeners() {
        btnSinglePlayer.setOnClickListener {
            startActivity(Intent(this, SinglePlayerActivity::class.java))
        }

        btnMultiPlayer.setOnClickListener {
            startActivity(Intent(this, MultiPlayerActivity::class.java))
        }

        btnYuvTest.setOnClickListener {
            startActivity(Intent(this, YUVTestActivity::class.java))
        }

        btnTransformTest.setOnClickListener {
            startActivity(Intent(this, TransformTestActivity::class.java))
        }
    }
}

package com.jxj.ffmpegrtspplayer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnSinglePlayer: Button
    private lateinit var btnMultiPlayer: Button
    private lateinit var btnYuvTest: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        btnSinglePlayer = findViewById(R.id.btn_single_player)
        btnMultiPlayer = findViewById(R.id.btn_multi_player)
        btnYuvTest = findViewById(R.id.btn_yuv_test)
    }

    private fun setupClickListeners() {
        btnSinglePlayer.setOnClickListener {
            val intent = Intent(this, SinglePlayerActivity::class.java)
            startActivity(intent)
        }

        btnMultiPlayer.setOnClickListener {
            val intent = Intent(this, MultiPlayerActivity::class.java)
            startActivity(intent)
        }

        btnYuvTest.setOnClickListener {
            val intent = Intent(this, YUVTestActivity::class.java)
            startActivity(intent)
        }
    }
}
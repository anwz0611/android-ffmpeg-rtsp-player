package com.jxj.ffmpegrtspplayer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button btnSinglePlayer;
    private Button btnMultiPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupClickListeners();
    }

    private void initViews() {
        btnSinglePlayer = findViewById(R.id.btn_single_player);
        btnMultiPlayer = findViewById(R.id.btn_multi_player);
    }

    private void setupClickListeners() {
        btnSinglePlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SinglePlayerActivity.class);
                startActivity(intent);
            }
        });

        btnMultiPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MultiPlayerActivity.class);
                startActivity(intent);
            }
        });
    }
}
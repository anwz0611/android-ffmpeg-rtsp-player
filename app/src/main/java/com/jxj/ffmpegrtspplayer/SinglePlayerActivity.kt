package com.jxj.ffmpegrtspplayer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.jxj.ffmpegrtsp.lib.FFmpegRTSPLibrary;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SinglePlayerActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "SinglePlayerActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private EditText etRtspUrl;
    private Button btnConnect, btnPlay, btnStop, btnRecord;
    private SurfaceView surfaceView;
    private TextView tvStatus, tvStreamInfo, tvRecordInfo;

    private SurfaceHolder surfaceHolder;
    private int streamId = -1;
    private boolean isPlaying = false;
    private boolean isRecording = false;
    private String currentRecordPath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_player);

        initViews();
        setupClickListeners();
        checkPermissions();
    }

    private void initViews() {
        etRtspUrl = findViewById(R.id.et_rtsp_url);
        btnConnect = findViewById(R.id.btn_connect);
        btnPlay = findViewById(R.id.btn_play);
        btnStop = findViewById(R.id.btn_stop);
        btnRecord = findViewById(R.id.btn_record);
        surfaceView = findViewById(R.id.surface_view);
        tvStatus = findViewById(R.id.tv_status);
        tvStreamInfo = findViewById(R.id.tv_stream_info);
        tvRecordInfo = findViewById(R.id.tv_record_info);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
    }

    private void setupClickListeners() {
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectStream();
            }
        });

        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playStream();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopStream();
            }
        });

        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRecording();
            }
        });
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要存储权限才能录制视频", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void connectStream() {
        String url = etRtspUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "请输入RTSP地址", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建流
        streamId = FFmpegRTSPLibrary.createStream(url, 1280, 720, 30, 2000000, "h264");
        if (streamId >= 0) {
            updateStatus("流已创建，ID: " + streamId);
            updateStreamInfo("URL: " + url);
            btnPlay.setEnabled(true);
            btnStop.setEnabled(true);
            btnRecord.setEnabled(true);
        } else {
            updateStatus("创建流失败");
            Toast.makeText(this, "创建流失败", Toast.LENGTH_SHORT).show();
        }
        FFmpegRTSPLibrary.setSurface(streamId, surfaceView.getHolder().getSurface());
    }

    private void playStream() {
        if (streamId < 0) {
            Toast.makeText(this, "请先连接流", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isPlaying) {
            FFmpegRTSPLibrary.startPlayAsync(streamId, new FFmpegRTSPLibrary.PlaybackCallback() {
                @Override
                public void onPlaybackStarted(int streamId) {
                    runOnUiThread(() -> {
                        isPlaying = true;
                        updateStatus("正在播放");
                        btnPlay.setText("暂停");
                        Toast.makeText(SinglePlayerActivity.this, "开始播放", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onPlaybackStopped(int streamId) {
                    runOnUiThread(() -> {
                        isPlaying = false;
                        updateStatus("已停止");
                        btnPlay.setText("播放");
                        Toast.makeText(SinglePlayerActivity.this, "停止播放", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onPlaybackError(int streamId, int errorCode, String errorMessage) {
                    runOnUiThread(() -> {
                        isPlaying = false;
                        updateStatus("播放错误: " + errorMessage);
                        btnPlay.setText("播放");
                        Toast.makeText(SinglePlayerActivity.this, "播放错误: " + errorMessage, Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onPlaybackInfo(int streamId, String info) {
                    runOnUiThread(() -> {
                        updateStreamInfo(info);
                    });
                }
            });
        } else {
            stopStream();
        }
    }

    private void stopStream() {
        if (streamId >= 0 && isPlaying) {
            FFmpegRTSPLibrary.stopPlayAsync(streamId, new FFmpegRTSPLibrary.PlaybackCallback() {
                @Override
                public void onPlaybackStarted(int streamId) {}

                @Override
                public void onPlaybackStopped(int streamId) {
                    runOnUiThread(() -> {
                        isPlaying = false;
                        updateStatus("已停止");
                        btnPlay.setText("播放");
                    });
                }

                @Override
                public void onPlaybackError(int streamId, int errorCode, String errorMessage) {
                    runOnUiThread(() -> {
                        isPlaying = false;
                        updateStatus("停止错误: " + errorMessage);
                        btnPlay.setText("播放");
                    });
                }

                @Override
                public void onPlaybackInfo(int streamId, String info) {}
            });
        }
    }

    private void toggleRecording() {
        if (streamId < 0) {
            Toast.makeText(this, "请先连接流", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        // 创建录制文件路径
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "rtsp_record_" + timestamp + ".mp4";
        File recordDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "RTSPRecords");
        if (!recordDir.exists()) {
            recordDir.mkdirs();
        }
        currentRecordPath = new File(recordDir, fileName).getAbsolutePath();

        FFmpegRTSPLibrary.startRecordingAsync(streamId, currentRecordPath, new FFmpegRTSPLibrary.RecordingCallback() {
            @Override
            public void onRecordingStarted(int streamId, String outputPath) {
                runOnUiThread(() -> {
                    isRecording = true;
                    updateRecordInfo("正在录制: " + fileName);
                    btnRecord.setText("停止录制");
                    Toast.makeText(SinglePlayerActivity.this, "开始录制", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onRecordingStopped(int streamId) {
                runOnUiThread(() -> {
                    isRecording = false;
                    updateRecordInfo("录制完成: " + currentRecordPath);
                    btnRecord.setText("录制");
                    Toast.makeText(SinglePlayerActivity.this, "录制完成", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onRecordingError(int streamId, int errorCode, String errorMessage) {
                runOnUiThread(() -> {
                    isRecording = false;
                    updateRecordInfo("录制错误: " + errorMessage);
                    btnRecord.setText("录制");
                    Toast.makeText(SinglePlayerActivity.this, "录制错误: " + errorMessage, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onRecordingProgress(int streamId, long duration, long fileSize) {
                runOnUiThread(() -> {
                    updateRecordInfo("录制中: " + (duration / 1000) + "s, 大小: " + (fileSize / 1024) + "KB");
                });
            }
        });
    }

    private void stopRecording() {
        if (streamId >= 0 && isRecording) {
            FFmpegRTSPLibrary.stopRecordingAsync(streamId, new FFmpegRTSPLibrary.RecordingCallback() {
                @Override
                public void onRecordingStarted(int streamId, String outputPath) {}

                @Override
                public void onRecordingStopped(int streamId) {
                    runOnUiThread(() -> {
                        isRecording = false;
                        updateRecordInfo("录制已停止");
                        btnRecord.setText("录制");
                    });
                }

                @Override
                public void onRecordingError(int streamId, int errorCode, String errorMessage) {
                    runOnUiThread(() -> {
                        isRecording = false;
                        updateRecordInfo("停止录制错误: " + errorMessage);
                        btnRecord.setText("录制");
                    });
                }

                @Override
                public void onRecordingProgress(int streamId, long duration, long fileSize) {}
            });
        }
    }

    private void updateStatus(String status) {
        tvStatus.setText(status);
        Log.d(TAG, "Status: " + status);
    }

    private void updateStreamInfo(String info) {
        tvStreamInfo.setText("流信息: " + info);
    }

    private void updateRecordInfo(String info) {
        tvRecordInfo.setText("录制状态: " + info);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        Log.d(TAG, "Surface created");
        if (streamId >= 0) {
            FFmpegRTSPLibrary.setSurface(streamId, holder.getSurface());
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "Surface changed: " + width + "x" + height);
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        Log.d(TAG, "Surface destroyed");
        if (streamId >= 0) {
            FFmpegRTSPLibrary.onSurfaceDestroyed(streamId);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (streamId >= 0) {
            FFmpegRTSPLibrary.destroyAllStreamsAsync();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        FFmpegRTSPLibrary.onAppBackground();
    }

    @Override
    protected void onResume() {
        super.onResume();
        FFmpegRTSPLibrary.onAppForeground();
    }
}
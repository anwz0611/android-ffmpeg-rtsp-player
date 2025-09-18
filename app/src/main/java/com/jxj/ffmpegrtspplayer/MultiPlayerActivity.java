package com.jxj.ffmpegrtspplayer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.jxj.ffmpegrtsp.lib.FFmpegRTSPLibrary;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MultiPlayerActivity extends AppCompatActivity {

    private static final String TAG = "MultiPlayerActivity";
    private static final int PERMISSION_REQUEST_CODE = 1002;
    
    // 视频尺寸常量
    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 480;

    private EditText etRtspUrl;
    private Button btnAddStream, btnPlayAll, btnStopAll, btnClearAll;
    private LinearLayout llStreamsContainer;
    private TextView tvStreamCount;

    private List<StreamItem> streamItems = new ArrayList<>();
    private int nextStreamId = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_player);

        initViews();
        setupClickListeners();
        checkPermissions();
        updateStreamCount();
    }

    private void initViews() {
        etRtspUrl = findViewById(R.id.et_rtsp_url);
        btnAddStream = findViewById(R.id.btn_add_stream);
        btnPlayAll = findViewById(R.id.btn_play_all);
        btnStopAll = findViewById(R.id.btn_stop_all);
        btnClearAll = findViewById(R.id.btn_clear_all);
        llStreamsContainer = findViewById(R.id.ll_streams_container);
        tvStreamCount = findViewById(R.id.tv_stream_count);
    }

    private void setupClickListeners() {
        btnAddStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addStream();
            }
        });

        btnPlayAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playAllStreams();
            }
        });

        btnStopAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAllStreams();
            }
        });

        btnClearAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAllStreams();
            }
        });
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        List<String> permissionList = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(permission);
            }
        }

        if (!permissionList.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionList.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            
            if (allPermissionsGranted) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要存储权限才能录制视频和拍照", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void addStream() {
        String url = etRtspUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "请输入RTSP地址", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建流
        int streamId = FFmpegRTSPLibrary.createStream(url, 640, 480, 30, 1000000, "h264");
        if (streamId >= 0) {
            StreamItem streamItem = new StreamItem(streamId, url, nextStreamId++);
            streamItems.add(streamItem);
            addStreamView(streamItem);
            updateStreamCount();
            etRtspUrl.setText(""); // 清空输入框
            Toast.makeText(this, "流已添加", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "创建流失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void addStreamView(StreamItem streamItem) {
        View streamView = LayoutInflater.from(this).inflate(R.layout.item_stream, llStreamsContainer, false);
        streamItem.setView(streamView);
        llStreamsContainer.addView(streamView);
    }

    private void removeStream(StreamItem streamItem) {
        if (streamItem.isPlaying()) {
            stopStream(streamItem);
        }
        if (streamItem.isRecording()) {
            stopRecording(streamItem);
        }
        FFmpegRTSPLibrary.destroyStream(streamItem.getStreamId());
        streamItems.remove(streamItem);
        llStreamsContainer.removeView(streamItem.getView());
        updateStreamCount();
    }

    private void playStream(StreamItem streamItem) {
        if (!streamItem.isPlaying()) {
            FFmpegRTSPLibrary.startPlayAsync(streamItem.getStreamId(), new FFmpegRTSPLibrary.PlaybackCallback() {
                @Override
                public void onPlaybackStarted(int streamId) {
                    runOnUiThread(() -> {
                        streamItem.setPlaying(true);
                        streamItem.updateStatus("正在播放");
                        streamItem.updatePlayButton("暂停");
                    });
                }

                @Override
                public void onPlaybackStopped(int streamId) {
                    runOnUiThread(() -> {
                        streamItem.setPlaying(false);
                        streamItem.updateStatus("已停止");
                        streamItem.updatePlayButton("播放");
                    });
                }

                @Override
                public void onPlaybackError(int streamId, int errorCode, String errorMessage) {
                    runOnUiThread(() -> {
                        streamItem.setPlaying(false);
                        streamItem.updateStatus("播放错误: " + errorMessage);
                        streamItem.updatePlayButton("播放");
                    });
                }

                @Override
                public void onPlaybackInfo(int streamId, String info) {
                    runOnUiThread(() -> {
                        streamItem.updateStats(info);
                    });
                }
            });
        } else {
            stopStream(streamItem);
        }
    }

    private void stopStream(StreamItem streamItem) {
        if (streamItem.isPlaying()) {
            FFmpegRTSPLibrary.stopPlayAsync(streamItem.getStreamId(), new FFmpegRTSPLibrary.PlaybackCallback() {
                @Override
                public void onPlaybackStarted(int streamId) {}

                @Override
                public void onPlaybackStopped(int streamId) {
                    runOnUiThread(() -> {
                        streamItem.setPlaying(false);
                        streamItem.updateStatus("已停止");
                        streamItem.updatePlayButton("播放");
                    });
                }

                @Override
                public void onPlaybackError(int streamId, int errorCode, String errorMessage) {
                    runOnUiThread(() -> {
                        streamItem.setPlaying(false);
                        streamItem.updateStatus("停止错误: " + errorMessage);
                        streamItem.updatePlayButton("播放");
                    });
                }

                @Override
                public void onPlaybackInfo(int streamId, String info) {}
            });
        }
    }

    private void startRecording(StreamItem streamItem) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "rtsp_record_" + streamItem.getDisplayId() + "_" + timestamp + ".mp4";
        File recordDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "RTSPRecords");
        if (!recordDir.exists()) {
            recordDir.mkdirs();
        }
        String recordPath = new File(recordDir, fileName).getAbsolutePath();

        FFmpegRTSPLibrary.startRecordingAsync(streamItem.getStreamId(), recordPath, new FFmpegRTSPLibrary.RecordingCallback() {
            @Override
            public void onRecordingStarted(int streamId, String outputPath) {
                runOnUiThread(() -> {
                    streamItem.setRecording(true);
                    streamItem.updateRecordButton("停止录制");
                    streamItem.updateStats("正在录制: " + fileName);
                });
            }

            @Override
            public void onRecordingStopped(int streamId) {
                runOnUiThread(() -> {
                    streamItem.setRecording(false);
                    streamItem.updateRecordButton("录制");
                    streamItem.updateStats("录制完成");
                });
            }

            @Override
            public void onRecordingError(int streamId, int errorCode, String errorMessage) {
                runOnUiThread(() -> {
                    streamItem.setRecording(false);
                    streamItem.updateRecordButton("录制");
                    streamItem.updateStats("录制错误: " + errorMessage);
                });
            }

            @Override
            public void onRecordingProgress(int streamId, long duration, long fileSize) {
                runOnUiThread(() -> {
                    streamItem.updateStats("录制中: " + (duration / 1000) + "s, 大小: " + (fileSize / 1024) + "KB");
                });
            }
        });
    }

    private void stopRecording(StreamItem streamItem) {
        if (streamItem.isRecording()) {
            FFmpegRTSPLibrary.stopRecordingAsync(streamItem.getStreamId(), new FFmpegRTSPLibrary.RecordingCallback() {
                @Override
                public void onRecordingStarted(int streamId, String outputPath) {}

                @Override
                public void onRecordingStopped(int streamId) {
                    runOnUiThread(() -> {
                        streamItem.setRecording(false);
                        streamItem.updateRecordButton("录制");
                        streamItem.updateStats("录制已停止");
                    });
                }

                @Override
                public void onRecordingError(int streamId, int errorCode, String errorMessage) {
                    runOnUiThread(() -> {
                        streamItem.setRecording(false);
                        streamItem.updateRecordButton("录制");
                        streamItem.updateStats("停止录制错误: " + errorMessage);
                    });
                }

                @Override
                public void onRecordingProgress(int streamId, long duration, long fileSize) {}
            });
        }
    }

    private void playAllStreams() {
        for (StreamItem streamItem : streamItems) {
            if (!streamItem.isPlaying()) {
                playStream(streamItem);
            }
        }
    }

    private void stopAllStreams() {
        for (StreamItem streamItem : streamItems) {
            if (streamItem.isPlaying()) {
                stopStream(streamItem);
            }
        }
    }

    private void clearAllStreams() {
        for (StreamItem streamItem : new ArrayList<>(streamItems)) {
            removeStream(streamItem);
        }
    }

    private void updateStreamCount() {
        int activeCount = FFmpegRTSPLibrary.getActiveStreamCount();
        tvStreamCount.setText("活跃流数量: " + activeCount + " / " + streamItems.size());
    }

    private void takePhoto(StreamItem streamItem) {
        if (!streamItem.isPlaying()) {
            Toast.makeText(this, "请先播放视频", Toast.LENGTH_SHORT).show();
            return;
        }

        SurfaceView surfaceView = streamItem.getSurfaceView();
        if (surfaceView == null) {
            Toast.makeText(this, getString(R.string.take_photo_fail), Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建Bitmap
        Bitmap bitmap = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888);
        
        // 使用PixelCopy进行截图
        PixelCopy.request(
                surfaceView, bitmap, copyResult -> {
                    if (copyResult == PixelCopy.SUCCESS) {
                        Toast.makeText(this, getString(R.string.take_photo_success), Toast.LENGTH_SHORT).show();
                        new Thread(() -> saveBitmap(bitmap)).start();
                    } else {
                        Toast.makeText(this, getString(R.string.take_photo_fail), Toast.LENGTH_SHORT).show();
                    }
                }, new Handler(Looper.getMainLooper())
        );
    }

    private void saveBitmap(Bitmap bitmap) {
        try {
            // 创建保存目录
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File appDir = new File(picturesDir, "FFmpegRTSPPlayer");
            if (!appDir.exists()) {
                appDir.mkdirs();
            }

            // 生成文件名
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "RTSP_Photo_" + timeStamp + ".jpg";
            File file = new File(appDir, fileName);

            // 保存图片
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();

            // 在主线程显示保存成功消息
            runOnUiThread(() -> {
                Toast.makeText(this, getString(R.string.photo_saved) + ": " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            });

        } catch (IOException e) {
            Log.e(TAG, "保存图片失败", e);
            runOnUiThread(() -> {
                Toast.makeText(this, "保存图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearAllStreams();

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
        updateStreamCount();
    }

    // 流项目内部类
    private class StreamItem implements SurfaceHolder.Callback {
        private int streamId;
        private int displayId;
        private String url;
        private View view;
        private boolean isPlaying = false;
        private boolean isRecording = false;

        private TextView tvStreamId, tvStreamUrl, tvStreamStatus, tvStreamStats;
        private Button btnRemoveStream, btnPlayStream, btnStopStream, btnRecordStream, btnTakePhoto;
        private SurfaceView surfaceView;
        private SurfaceHolder surfaceHolder;

        public StreamItem(int streamId, String url, int displayId) {
            this.streamId = streamId;
            this.url = url;
            this.displayId = displayId;
        }

        public void setView(View view) {
            this.view = view;
            initViews();
            setupClickListeners();
        }

        private void initViews() {
            tvStreamId = view.findViewById(R.id.tv_stream_id);
            tvStreamUrl = view.findViewById(R.id.tv_stream_url);
            tvStreamStatus = view.findViewById(R.id.tv_stream_status);
            tvStreamStats = view.findViewById(R.id.tv_stream_stats);
            btnRemoveStream = view.findViewById(R.id.btn_remove_stream);
            btnPlayStream = view.findViewById(R.id.btn_play_stream);
            btnStopStream = view.findViewById(R.id.btn_stop_stream);
            btnRecordStream = view.findViewById(R.id.btn_record_stream);
            btnTakePhoto = view.findViewById(R.id.btn_take_photo);
            surfaceView = view.findViewById(R.id.surface_view);

            tvStreamId.setText("流 #" + displayId);
            tvStreamUrl.setText(url);

            surfaceHolder = surfaceView.getHolder();
            surfaceHolder.addCallback(this);
        }

        private void setupClickListeners() {
            btnRemoveStream.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeStream(StreamItem.this);
                }
            });

            btnPlayStream.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playStream(StreamItem.this);
                }
            });

            btnStopStream.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopStream(StreamItem.this);
                }
            });

            btnRecordStream.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isRecording) {
                        startRecording(StreamItem.this);
                    } else {
                        stopRecording(StreamItem.this);
                    }
                }
            });

            btnTakePhoto.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    takePhoto(StreamItem.this);
                }
            });
        }

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            FFmpegRTSPLibrary.setSurface(streamId, holder.getSurface());
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {}

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            FFmpegRTSPLibrary.onSurfaceDestroyed(streamId);
        }

        // Getters and setters
        public int getStreamId() { return streamId; }
        public int getDisplayId() { return displayId; }
        public String getUrl() { return url; }
        public View getView() { return view; }
        public boolean isPlaying() { return isPlaying; }
        public boolean isRecording() { return isRecording; }

        public void setPlaying(boolean playing) { this.isPlaying = playing; }
        public void setRecording(boolean recording) { this.isRecording = recording; }

        public void updateStatus(String status) {
            tvStreamStatus.setText(status);
        }

        public void updateStats(String stats) {
            tvStreamStats.setText("状态: " + stats);
        }

        public void updatePlayButton(String text) {
            btnPlayStream.setText(text);
        }

        public void updateRecordButton(String text) {
            btnRecordStream.setText(text);
        }

        public SurfaceView getSurfaceView() {
            return surfaceView;
        }
    }
}
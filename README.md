# FFmpegStreamPlayer

基于 FFmpeg 6.1.1 的 Android 流媒体播放器，支持 RTSP/RTMP/HLS/HTTP-FLV 等协议。

## 截图

<div align="center">
<img src="screenshot/MuMu-20251011-155352-156.png" alt="多流播放" width="280"/>
<img src="screenshot/MuMu-20251011-153144-611.png" alt="单流播放" width="280"/>
<img src="screenshot/MuMu-20251218-104858-750.png" alt="YUV示例" width="280"/>
<img src="screenshot/MuMu-20251219-141657-940.png" alt="画面变换测试" width="280"/>
</div>

## 主要特性

- 超低延迟：硬件解码 80-120ms，软件解码 120-200ms
- 多流并发：支持 16 路同时播放
- 硬件/软件解码自动切换
- 支持 4K 视频播放
- H.264/H.265 硬件解码
- 实时录制
- YUV 数据回调（可用于 AI 分析、滤镜处理等）
- 异步 API，不阻塞 UI
- 自动重连

## 支持协议

RTSP、RTMP、HTTP、HLS、HTTP-FLV、RTP、UDP、TCP

## 支持格式

- 视频：H.264、H.265/HEVC（硬件/软件解码）、MPEG-4（软件解码）
- 分辨率：4K、2K、1080p、720p 及以下
- 音频：AAC、MP3、PCM

## 快速开始

### 引入库

```kotlin
dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
```

### 添加权限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

### 基本用法

```java
// 创建流（默认硬件解码）
int streamId = FFmpegRTSPLibrary.createStream("rtsp://your-server/stream");

// 绑定 Surface
FFmpegRTSPLibrary.setSurface(streamId, surfaceView.getHolder().getSurface());

// 异步播放
FFmpegRTSPLibrary.startPlayAsync(streamId, new FFmpegCallbacks.PlaybackStartCallback() {
    @Override
    public void onPlaybackStarted(int streamId, VideoInfo videoInfo) {
        if (videoInfo != null) {
            Log.i(TAG, "视频信息: " + videoInfo.width + "x" + videoInfo.height +
                       ", fps=" + videoInfo.fps + ", codec=" + videoInfo.codec);
        }
    }

    @Override
    public void onPlaybackError(int streamId, int errorCode, String errorMessage) {
        Log.e(TAG, "播放失败: " + errorCode + " - " + errorMessage);
    }
});

// 停止播放
FFmpegRTSPLibrary.stopPlayAsync(streamId, new FFmpegCallbacks.PlaybackStopCallback() {
    @Override
    public void onPlaybackStopped(int streamId) {
        Log.i(TAG, "播放停止");
    }

    @Override
    public void onPlaybackError(int streamId, int errorCode, String errorMessage) {
        Log.e(TAG, "停止失败: " + errorCode + " - " + errorMessage);
    }
});

// 销毁流
FFmpegRTSPLibrary.destroyStreamAsync(streamId);
```

### 录制功能

```java
// 开始录制
FFmpegRTSPLibrary.startRecordingAsync(streamId, "/sdcard/record.mp4",
    new FFmpegCallbacks.RecordingStartCallback() {
        @Override
        public void onRecordingStarted(int streamId, String outputPath) {
            Log.i(TAG, "录制开始: " + outputPath);
        }

        @Override
        public void onRecordingError(int streamId, int errorCode, String errorMessage) {
            Log.e(TAG, "录制失败: " + errorCode + " - " + errorMessage);
        }
    });

// 停止录制
FFmpegRTSPLibrary.stopRecordingAsync(streamId, new FFmpegCallbacks.RecordingStopCallback() {
    @Override
    public void onRecordingStopped(int streamId) {
        Log.i(TAG, "录制停止");
    }

    @Override
    public void onRecordingError(int streamId, int errorCode, String errorMessage) {
        Log.e(TAG, "停止录制失败: " + errorCode + " - " + errorMessage);
    }
});
```

### 其他配置

软件解码：
```java
int streamId = FFmpegRTSPLibrary.createStreamWithDecodeMode(url, true); //true 为软件解码 false 为硬件解码
```

禁用音频：
```java
int streamId = FFmpegRTSPLibrary.createStreamWithOptions(url, false, false); //false 为硬件解码 false 为禁用音频
```

### YUV 数据获取

软件解码模式下可以获取 YUV 原始帧，用于 AI 分析、滤镜处理等。

处理模式：
- `OBSERVE_ONLY`：只读分析，不影响播放
- `PROCESS_AND_RENDER`：处理后渲染
- `CUSTOM_RENDER_ONLY`：完全接管渲染

```java
FFmpegRTSPLibrary.registerYUVProcessor(streamId, new IYUVFrameProcessor() {
    @Override
    public YUVProcessMode getProcessMode() {
        return YUVProcessMode.OBSERVE_ONLY;
    }

    @Override
    public YUVProcessResult onProcessFrame(YUVFrameInfo frame) {
        // frame.yBuffer / uBuffer / vBuffer 为零拷贝 DirectByteBuffer
        // frame.width, frame.height 为分辨率
        // frame.timestamp 为时间戳
        
        // 处理逻辑...
        
        return YUVProcessResult.passthrough(); // 不修改原始数据
        // 或返回修改后的数据：YUVProcessResult.processed(modifiedY, modifiedU, modifiedV);
    }
});
```

注意：回调在解码线程执行，耗时操作需要异步处理。DirectByteBuffer 仅在回调期间有效。YUV 数据为 I420 格式。

### 生命周期管理

```java
@Override
public void surfaceCreated(SurfaceHolder holder) {
    if (currentStreamId >= 0) {
        FFmpegRTSPLibrary.setSurface(currentStreamId, holder.getSurface());
    }
}

@Override
public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    // Surface 变化处理
}

@Override
public void surfaceDestroyed(SurfaceHolder holder) {
    if (currentStreamId >= 0) {
        FFmpegRTSPLibrary.onSurfaceDestroyed(currentStreamId);
    }
}

@Override
protected void onDestroy() {
    super.onDestroy();
    FFmpegRTSPLibrary.destroyAllStreamsAsync();
}
```

## 延迟对比

| 播放器 | 延迟 |
|-------|-----|
| 本项目（硬件解码） | 80-120ms |
| 本项目（软件解码） | 120-200ms |
| 原生 MediaPlayer | 300-800ms |

## 环境要求

- Android 7.0+ (API 24)

## 联系方式

- QQ 群：647718711

示例地址网络较差时可能体现不出超低延迟，建议自行搭建测试环境。定制需求或问题反馈请联系作者。本项目长期维护。

aar 包含 arm64-v8a、armeabi-v7a、x86_64 架构，如只需要特定架构请联系作者。

## 许可证

GPL v2，商业使用请联系作者获取授权。

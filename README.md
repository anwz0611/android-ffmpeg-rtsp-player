# FfmpegRtspPlayer2

<div align="center">

![FFmpeg](https://img.shields.io/badge/FFmpeg-6.1.1-green.svg)
![C++](https://img.shields.io/badge/C%2B%2B-20-blue.svg)
![Android](https://img.shields.io/badge/Android-API%2024%2B-brightgreen.svg)
![License](https://img.shields.io/badge/License-GPL%20v2-orange.svg)

**🚀 基于 FFmpeg 6.1.1 + C++20 的 Android 超低延迟 RTSP 播放器**

*100ms 级延迟 | 16 路并发 | 零拷贝渲染 | 硬件加速 | 16kb 适配*

</div>

## 📱 应用截图

<div align="center">

### 多流播放界面
<img src="screenshot/MuMu-20251011-155352-156.png" alt="多流播放界面" width="300"/>

### 单流播放界面  
<img src="screenshot/MuMu-20251011-153144-611.png" alt="单流播放界面" width="300"/>

</div>

---

## ✨ 核心优势

🔥 **超低延迟**: 硬件解码 100ms 级，软件解码 200ms 级  
⚡ **零拷贝**: 直接内存映射，性能极致  
📱 **多流并发**: 支持 16 路同时播放  
🎥 **实时录制**: 零延迟录制，质量无损  
🔧 **易于集成**: 一行代码创建流  
🎯 **双解码模式**: 硬件解码 + 软件解码，灵活选择  
💾 **16kb 适配**: 支持 16kb 页面大小设备，兼容性更强

## 项目简介

FfmpegRtspPlayer 是一个基于 FFmpeg 6.1.1 编译的 Android RTSP 播放器应用。该播放器专为实时视频流播放设计，具有超低延迟特性，支持多流同时播放和视频录制功能。

## 适用场景

### 🎯 实时监控系统
- **安防监控**: 实时查看多路摄像头画面
- **工业监控**: 生产线实时监控，异常快速响应
- **交通监控**: 路口、高速公路实时监控

### 📱 移动应用
- **视频会议**: 低延迟视频通话
- **直播应用**: 实时直播推流和播放
- **远程控制**: 无人机、机器人远程操控

### 🏢 企业应用
- **视频会议系统**: 企业内部会议
- **远程培训**: 在线教育实时互动
- **医疗远程**: 远程医疗诊断

### 🔧 开发集成
- **SDK 集成**: 快速集成到现有应用
- **定制开发**: 根据需求定制功能
- **性能优化**: 替代系统播放器获得更好性能

## 技术特性

### 🔧 技术栈
- **核心库**: FFmpeg 6.1.1 (C++20 编译)
- **开发语言**: Java 11 + C++20
- **最低支持**: Android API 24 (Android 7.0)
- **架构支持**: arm64-v8a (主要), armeabi-v7a
- **16kb 适配**: 支持 16kb 页面大小设备，兼容性更强

### ⚡ 性能优化
- **零拷贝技术**: 直接内存映射，减少数据拷贝
- **硬件加速**: MediaCodec 硬件解码 + NEON SIMD 优化
- **智能缓冲**: 最小化延迟的缓冲算法
- **线程池**: 专用线程池处理不同任务类型
- **内存管理**: RAII + 智能指针，自动资源管理

## 快速开始

### 环境要求

- Android Studio 4.0+
- Android SDK API 24+
- NDK (用于原生库编译)

### 集成步骤

1. **添加 AAR 库**
   ```kotlin
   dependencies {
       implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
   }
   ```

2. **配置权限**
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
   <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
   <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
   ```

3. **导入库**
   ```java
   import com.jxj.ffmpegrtsp.lib.FFmpegRTSPLibrary;
   ```

### 解码模式选择

#### 硬件解码模式（默认）
- **优势**: 延迟最低（80-120ms），CPU占用少（<10%），功耗低
- **适用场景**: 现代Android设备，对延迟要求极高的应用
- **限制**: 依赖设备硬件支持，部分老旧设备可能不支持

#### 软件解码模式
- **优势**: 兼容性最强，支持所有Android设备，解码质量稳定
- **适用场景**: 老旧设备，对兼容性要求高的应用，调试和测试
- **限制**: 延迟稍高（120-200ms），CPU占用较高（<30%）

#### 使用建议
```java
// 推荐：优先使用硬件解码，兼容性更好
int streamId = FFmpegRTSPLibrary.createStream(url, width, height, fps, bitrate, codec);

// 明确指定：需要软件解码时
int streamId = FFmpegRTSPLibrary.createStreamWithDecodeMode(url, width, height, fps, bitrate, codec, true);

// 明确指定：需要硬件解码时
int streamId = FFmpegRTSPLibrary.createStreamWithDecodeMode(url, width, height, fps, bitrate, codec, false);
```

### 基本使用

#### 单流播放
```java
// 1. 创建流（默认硬件解码）
String rtspUrl = "rtsp://your-server:554/stream";
int streamId = FFmpegRTSPLibrary.createStream(rtspUrl, 1280, 720, 30, 2000000, "h264");

// 或者指定解码模式
int streamId = FFmpegRTSPLibrary.createStreamWithDecodeMode(rtspUrl, 1280, 720, 30, 2000000, "h264", false); // 硬件解码
int streamId = FFmpegRTSPLibrary.createStreamWithDecodeMode(rtspUrl, 1280, 720, 30, 2000000, "h264", true);  // 软件解码

// 2. 设置Surface
SurfaceView surfaceView = findViewById(R.id.surface_view);
FFmpegRTSPLibrary.setSurface(streamId, surfaceView.getHolder().getSurface());

// 3. 开始播放（异步）
FFmpegRTSPLibrary.startPlayAsync(streamId, new FFmpegRTSPLibrary.PlaybackCallback() {
    @Override
    public void onPlaybackStarted(int streamId) {
        // 播放开始回调
        runOnUiThread(() -> {
            // 更新UI
        });
    }
    
    @Override
    public void onPlaybackStopped(int streamId) {
        // 播放停止回调
    }
    
    @Override
    public void onPlaybackError(int streamId, int errorCode, String errorMessage) {
        // 播放错误回调
    }
});

// 4. 停止播放
FFmpegRTSPLibrary.stopPlayAsync(streamId, callback);

// 5. 销毁流
FFmpegRTSPLibrary.destroyStream(streamId);
```

#### 多流播放
```java
// 创建多个流（支持混合解码模式）
List<Integer> streamIds = new ArrayList<>();
for (int i = 0; i < streamCount; i++) {
    String url = rtspUrls.get(i);
    // 根据需求选择解码模式
    int streamId;
    if (i % 2 == 0) {
        // 偶数流使用硬件解码
        streamId = FFmpegRTSPLibrary.createStreamWithDecodeMode(url, 640, 480, 30, 1000000, "h264", false);
    } else {
        // 奇数流使用软件解码
        streamId = FFmpegRTSPLibrary.createStreamWithDecodeMode(url, 640, 480, 30, 1000000, "h264", true);
    }
    streamIds.add(streamId);
    
    // 为每个流设置Surface
    SurfaceView surfaceView = getSurfaceViewForStream(i);
    FFmpegRTSPLibrary.setSurface(streamId, surfaceView.getHolder().getSurface());
}

// 同时播放所有流
for (int streamId : streamIds) {
    FFmpegRTSPLibrary.startPlayAsync(streamId, playbackCallback);
}

// 停止所有流
for (int streamId : streamIds) {
    FFmpegRTSPLibrary.stopPlayAsync(streamId, callback);
}

// 销毁所有流
FFmpegRTSPLibrary.destroyAllStreams();
```

#### 录制功能
```java
// 开始录制
String outputPath = "/sdcard/record_" + timestamp + ".mp4";
FFmpegRTSPLibrary.startRecordingAsync(streamId, outputPath, new FFmpegRTSPLibrary.RecordingCallback() {
    @Override
    public void onRecordingStarted(int streamId, String outputPath) {
        // 录制开始回调
        runOnUiThread(() -> {
            // 更新录制状态UI
        });
    }
    
    @Override
    public void onRecordingStopped(int streamId) {
        // 录制停止回调
    }
    
    @Override
    public void onRecordingError(int streamId, int errorCode, String errorMessage) {
        // 录制错误回调
    }
    
    @Override
    public void onRecordingProgress(int streamId, long duration, long fileSize) {
        // 录制进度回调（可选）
    }
});

// 停止录制
FFmpegRTSPLibrary.stopRecordingAsync(streamId, recordingCallback);
```

#### 生命周期管理
```java
@Override
protected void onResume() {
    super.onResume();
    FFmpegRTSPLibrary.onAppForeground();
}

@Override
protected void onPause() {
    super.onPause();
    FFmpegRTSPLibrary.onAppBackground();
}

@Override
protected void onDestroy() {
    super.onDestroy();
    // 清理所有资源
    FFmpegRTSPLibrary.destroyAllAsync();
}
```

## API 参考

### 核心方法

| 方法名 | 参数 | 返回值 | 说明 |
|--------|------|--------|------|
| `createStream` | url, width, height, fps, bitrate, codec | int | 创建流（默认硬件解码），返回流ID |
| `createStreamWithDecodeMode` | url, width, height, fps, bitrate, codec, useSoftwareDecode | int | 创建流（指定解码模式），返回流ID |
| `setSurface` | streamId, surface | int | 设置Surface到流 |
| `startStream` | streamId | int | 同步开始播放 |
| `stopStream` | streamId | int | 同步停止播放 |
| `destroyStream` | streamId | int | 销毁指定流 |
| `destroyAllStreams` | - | int | 销毁所有流 |

### 异步方法

| 方法名 | 参数 | 说明 |
|--------|------|------|
| `startPlayAsync` | streamId, callback | 异步开始播放 |
| `stopPlayAsync` | streamId, callback | 异步停止播放 |
| `startRecordingAsync` | streamId, outputPath, callback | 异步开始录制 |
| `stopRecordingAsync` | streamId, callback | 异步停止录制 |

### 生命周期管理

| 方法名 | 说明 |
|--------|------|
| `onAppBackground` | 应用进入后台时调用 |
| `onAppForeground` | 应用返回前台时调用 |
| `onSurfaceCreated` | Surface创建时调用 |
| `onSurfaceDestroyed` | Surface销毁时调用 |

### 统计和状态

| 方法名 | 参数 | 返回值 | 说明 |
|--------|------|--------|------|
| `getStreamStats` | streamId | String | 获取流统计信息(JSON) |
| `getActiveStreamCount` | - | int | 获取活跃流数量 |

## 性能表现

### ⚡ 延迟对比
| 播放器类型 | 延迟范围 | 备注 |
|-----------|---------|------|
| **FFmpegRtspPlayer (硬件)** | **80-120ms** | 硬件解码 + 零拷贝 |
| **FFmpegRtspPlayer (软件)** | **120-200ms** | 软件解码 + 软件渲染 |
| 传统播放器 | 200-500ms | 软件解码 + 多级缓冲 |
| WebRTC | 150-300ms | 网络优化但解码较慢 |
| 原生MediaPlayer | 300-800ms | 系统级缓冲较大 |

### 🚀 性能优势
- **延迟控制**: 硬件解码 100ms 级，软件解码 200ms 级超低延迟
- **CPU 占用**: 硬件解码下 CPU 占用 < 10%，软件解码下 < 30%
- **内存效率**: 零拷贝技术，内存占用减少 30%
- **多流性能**: 16 路并发，每路独立优化
- **启动速度**: 流创建到首帧显示 < 200ms
- **16kb 适配**: 支持 16kb 页面大小设备，兼容性更强

## 支持格式

### 视频编码
- H.264 (AVC)
- H.265 (HEVC)
- VP8/VP9
- MJPEG

### 音频编码
- AAC
- MP3
- PCM

### 容器格式
- RTSP
- RTMP
- UDP

## 版本信息

- **当前版本**: 1.0
- **FFmpeg 版本**: 6.1.1
- **编译日期**: 2025
- **最低 Android 版本**: 7.0 (API 24)
- **新增功能**: 软件解码/渲染支持，双解码模式选择，16kb 适配

## 注意事项

1. 确保设备有足够的存储空间用于录制
2. 网络环境对播放质量有重要影响
3. 建议在真机上测试以获得最佳性能
4. 录制功能需要存储权限

## 商业用途

本项目基于 FFmpeg 开源项目开发，遵循 GPL 许可证。如需用于商业用途，请联系作者获取商业许可证。

### 联系方式
- **作者**: JXJ
- **邮箱**: [592296083@qq.com]
- **项目需求**: 本项目中的示例rtsp地址网络较差 ，体现不出超快连接和超低延迟，请自行更换rtsp地址，如有定制化需求或者报错，请联系作者。

## 许可证

本项目基于 GPL v2 许可证开源。商业使用请联系作者获取相应授权。

## 致谢

感谢 FFmpeg 开源社区提供的优秀多媒体处理框架，以及所有为项目贡献代码的开发者。

---

**注意**: 本项目仅供学习和研究使用，商业用途请联系作者获取授权。
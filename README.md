# FFmpegStreamPlayer

基于 FFmpeg 6.1.1 的 Android 多协议流媒体播放器，支持 RTSP/RTMP/HLS/HTTP-FLV 等主流协议。

## 截图

<div align="center">
<img src="screenshot/MuMu-20251011-155352-156.png" alt="多流播放" width="280"/>
<img src="screenshot/MuMu-20251011-153144-611.png" alt="单流播放" width="280"/>
<img src="screenshot/MuMu-20251218-104858-750.png" alt="YUV示例" width="280"/>
<img src="screenshot/MuMu-20251219-141657-940.png" alt="画面变换测试" width="280"/>
</div>

## 特性

- 超低延迟：硬件解码 100ms 级，软件解码 200ms 级
- 多流并发：最高支持 16 路同时播放（根据设备性能可能会更多）
- 双解码模式：硬件解码/软件解码可选
- 零拷贝渲染：直接内存映射
- 实时录制：边播边录，质量无损
- YUV 数据暴露：支持自定义帧处理
- 16kb 页面适配
- 后期会有更多功能（待开发）

## 支持协议

RTSP、RTMP、HTTP、HLS、HTTP-FLV、RTP、UDP、TCP

## 快速开始

### 1. 引入库

```kotlin
dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
```

### 2. 添加权限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

### 3. 基本用法

```java
// 创建流
int streamId = FFmpegRTSPLibrary.createStream("rtsp://your-server/stream");//默认硬件解码

// 绑定 Surface
FFmpegRTSPLibrary.setSurface(streamId, surfaceView.getHolder().getSurface());

// 异步播放（推荐）
FFmpegRTSPLibrary.startPlayAsync(streamId, callback);

// 停止 & 销毁
FFmpegRTSPLibrary.stopPlayAsync(streamId, callback);
FFmpegRTSPLibrary.destroyStream(streamId);
```

软件解码：
```java
int streamId = FFmpegRTSPLibrary.createStreamWithDecodeMode(url, true);
```
音频开关(url,是否启用软件解码,是否启用音频默认开启)：
```java
int streamId = FFmpegRTSPLibrary.createStreamWithOptions(url, false, false); // 禁用音频
```

录制：
```java
FFmpegRTSPLibrary.startRecordingAsync(streamId, "/sdcard/record.mp4", callback);
```

### 4. YUV 数据获取

软件解码时可获取 YUV 原始帧，用于 AI 分析、滤镜处理、自定义渲染等场景。

三种模式：
- `OBSERVE_ONLY`：只读分析，不影响播放（如人脸检测）
- `PROCESS_AND_RENDER`：处理后渲染（如美颜滤镜）
- `CUSTOM_RENDER_ONLY`：完全接管渲染（自己 OpenGL 渲染）

```java
// 注册处理器
FFmpegRTSPLibrary.registerYUVProcessor(streamId, new IYUVFrameProcessor() {
    @Override
    public YUVProcessMode getProcessMode() {
        return YUVProcessMode.OBSERVE_ONLY;
    }
    
    @Override
    public YUVProcessResult onProcessFrame(YUVFrameInfo frame) {
        // frame.yBuffer / uBuffer / vBuffer 为零拷贝 DirectByteBuffer
        // 在此处理 YUV 数据，如 AI 推理
        return YUVProcessResult.passthrough();
    }
});

// 注销
FFmpegRTSPLibrary.unregisterYUVProcessor(streamId, processor);
```

> 注意：回调在解码线程执行，耗时操作请异步处理；Buffer 仅回调期间有效。

### 5. 生命周期 必须写 关系到应用前后台切换和销毁

```java
@Override
public void surfaceCreated(SurfaceHolder holder) {
    Log.i(TAG, "🎬 Surface创建");

    if (currentStreamId >= 0) {
        // 设置Surface到当前流
        int result = FFmpegRTSPLibrary.setSurface(currentStreamId, holder.getSurface());
        if (result == 0) {
            Log.i(TAG, "✅ Surface设置成功");
            showToast("Surface设置成功");
        } else {
            Log.e(TAG, "❌ Surface设置失败");
            showToast("Surface设置失败");
        }
    }
}

@Override
public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    Log.i(TAG, "🎬 Surface变化: " + width + "x" + height);
}

@Override
public void surfaceDestroyed(SurfaceHolder holder) {
    Log.i(TAG, "🗑️ Surface销毁");

    if (currentStreamId >= 0) {
        FFmpegRTSPLibrary.onSurfaceDestroyed(currentStreamId);
    }
}

@Override protected void onDestroy() {
    super.onDestroy();
    FFmpegRTSPLibrary.destroyAllAsync();
}
```

## 延迟对比

| 播放器 | 延迟 |
|-------|-----|
| **本项目（硬件解码）** | 80-120ms |
| **本项目（软件解码）** | 120-200ms |
| 原生 MediaPlayer | 300-800ms |

## 环境要求

- Android 7.0+ (API 24)
- FFmpeg 6.1.1

## 联系方式

- 作者：jxj
- QQ 群：647718711

示例地址网络较差时可能体现不出超低延迟，建议自行搭建测试环境。定制需求或问题反馈请联系作者。本项目长期维护放心使用。
(aar包含"arm64-v8a", "armeabi-v7a", "x86_64" 如只需要特定架构请联系作者)

## 许可证

GPL v2，商业使用请联系作者获取授权。

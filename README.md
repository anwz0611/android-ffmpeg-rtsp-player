# FFmpeg RTSP Player AAR Library

这是一个包含FFmpeg和RTSP播放功能的Android AAR库。

## 库结构

- **libffmpeg.so**: FFmpeg核心库（预编译）
- **libffmpegrtspplayer.so**: 主播放器库（C++20）
- **Java接口**: 提供易用的Java API

## 支持的架构

- arm64-v8a
- x86_64

## 16K Page Size 支持

✅ **完全支持 Android 15+ 的 16K Page Size**

- 符合 Google Play 要求 (2025年11月1日起)
- 支持 4KB 和 16KB 页面大小的设备
- 优化的内存管理和性能
- 自动页面大小检测

## 使用方法

### 1. 添加AAR依赖

将生成的AAR文件复制到你的项目的`libs`目录，然后在`build.gradle`中添加：

```gradle
dependencies {
    implementation files('libs/ffmpegrtsp-lib-release.aar')
}
```

### 2. 在代码中使用

```java
import com.jxj.ffmpegrtsp.lib.FFmpegRTSPLibrary;

public class MainActivity extends AppCompatActivity {
    private int streamId = -1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置日志级别
        FFmpegRTSPLibrary.setLogLevel(2); // DEBUG级别
        FFmpegRTSPLibrary.enableLogging(true);
    }
    
    private void startPlayback(Surface surface, String rtspUrl) {
        // 创建流（视频参数将从RTSP流中自动解析）
        streamId = FFmpegRTSPLibrary.createStream(rtspUrl);
        if (streamId == -1) {
            Log.e("RTSP", "Failed to create stream");
            return;
        }
        
        // 设置Surface
        int result = FFmpegRTSPLibrary.setSurface(streamId, surface);
        if (result != 0) {
            Log.e("RTSP", "Failed to set surface");
            return;
        }
        
        // 异步开始播放
        FFmpegRTSPLibrary.startPlayAsync(streamId, new FFmpegRTSPLibrary.PlaybackCallback() {
            @Override
            public void onPlaybackStarted(int streamId) {
                Log.d("RTSP", "Playback started for stream: " + streamId);
            }
            
            @Override
            public void onPlaybackStopped(int streamId) {
                Log.d("RTSP", "Playback stopped for stream: " + streamId);
            }
            
            @Override
            public void onPlaybackError(int streamId, int errorCode, String errorMessage) {
                Log.e("RTSP", "Playback error: " + errorMessage);
            }
            
            @Override
            public void onPlaybackInfo(int streamId, String info) {
                Log.d("RTSP", "Playback info: " + info);
            }
        });
    }
    
    private void startRecording(String outputPath) {
        if (streamId == -1) {
            Log.e("RTSP", "No active stream to record");
            return;
        }
        
        // 异步开始录制
        FFmpegRTSPLibrary.startRecordingAsync(streamId, outputPath, new FFmpegRTSPLibrary.RecordingCallback() {
            @Override
            public void onRecordingStarted(int streamId, String outputPath) {
                Log.d("RTSP", "Recording started: " + outputPath);
            }
            
            @Override
            public void onRecordingStopped(int streamId) {
                Log.d("RTSP", "Recording stopped for stream: " + streamId);
            }
            
            @Override
            public void onRecordingError(int streamId, int errorCode, String errorMessage) {
                Log.e("RTSP", "Recording error: " + errorMessage);
            }
            
            @Override
            public void onRecordingProgress(int streamId, long duration, long fileSize) {
                Log.d("RTSP", "Recording progress: " + duration + "ms, " + fileSize + " bytes");
            }
        });
    }
    
    private void stopPlayback() {
        if (streamId != -1) {
            // 异步停止播放
            FFmpegRTSPLibrary.stopPlayAsync(streamId, new FFmpegRTSPLibrary.PlaybackCallback() {
                @Override
                public void onPlaybackStopped(int streamId) {
                    Log.d("RTSP", "Playback stopped successfully");
                }
                
                @Override
                public void onPlaybackError(int streamId, int errorCode, String errorMessage) {
                    Log.e("RTSP", "Error stopping playback: " + errorMessage);
                }
                
                @Override
                public void onPlaybackStarted(int streamId) {}
                @Override
                public void onPlaybackInfo(int streamId, String info) {}
            });
            
            // 销毁流
            FFmpegRTSPLibrary.destroyStream(streamId);
            streamId = -1;
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 清理所有资源
        if (streamId != -1) {
            FFmpegRTSPLibrary.destroyStream(streamId);
        }
        FFmpegRTSPLibrary.destroyAllStreams();
    }
}
```

### 3. 权限配置

在`AndroidManifest.xml`中添加必要权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

## 构建AAR

运行构建脚本：

```bash
# Windows
build_aar.bat

# Linux/Mac
./build_aar.sh
```

生成的AAR文件位于：
- `ffmpegrtsp-lib/build/outputs/aar/ffmpegrtsp-lib-release.aar`
- `ffmpegrtsp-lib/build/outputs/aar/ffmpegrtsp-lib-debug.aar`

### 16K Page Size 兼容性测试

```bash
# Windows
test_16k_compatibility.bat

# Linux/Mac
./test_16k_compatibility.sh
```

## 特性

- ✅ 超低延迟RTSP播放
- ✅ 硬件加速解码
- ✅ 多线程处理
- ✅ 内存优化
- ✅ 错误处理
- ✅ 回调机制
- ✅ C++20标准
- ✅ 支持多架构
- ✅ 代码混淆保护
- ✅ JNI方法名保护
- ✅ **16K Page Size 支持 (Android 15+)**
- ✅ **Google Play 兼容性**

## 混淆配置

AAR库已配置代码混淆保护，包含以下特性：

### 保护内容
- ✅ 主库类 `FFmpegRTSPLibrary` 不被混淆
- ✅ 所有native方法名保持不变
- ✅ 回调接口 `PlaybackCallback` 和 `RecordingCallback` 受保护
- ✅ JNI方法名与C++代码完全匹配
- ✅ 包结构 `com.jxj.ffmpegrtsp.lib` 完整保留

### 混淆效果
- ✅ 内部实现类被混淆
- ✅ 私有方法名被混淆
- ✅ 代码体积优化
- ✅ 反编译难度增加

### 测试混淆
```bash
# Windows
test_obfuscation.bat

# 检查混淆后的AAR内容
```

## 注意事项

1. 确保目标设备支持所需的架构
2. 在Android 6.0+需要动态权限申请
3. 建议在子线程中进行播放操作
4. 及时释放播放器资源避免内存泄漏
5. 使用AAR时无需额外配置混淆规则（已内置）
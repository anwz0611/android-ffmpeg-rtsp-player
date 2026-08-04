# Android FFmpeg RTSP Player

<div align="center">

**Android 超低延迟流媒体播放器 SDK**

支持 RTSP、RTMP、HLS、HTTP-FLV、H.264/H.265、软硬件解码、OpenGL ES、截图、录像、自动重连、多路播放及 YUV 数据处理。

![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android)
![FFmpeg](https://img.shields.io/badge/FFmpeg-6.1.1-007808?logo=ffmpeg)
![License](https://img.shields.io/badge/SDK-Free%20Commercial%20Use-orange)

[下载 Demo](https://github.com/anwz0611/android-ffmpeg-rtsp-player/releases/latest) ·
[API 文档](https://github.com/anwz0611/android-ffmpeg-rtsp-player/wiki) ·
[问题反馈](https://github.com/anwz0611/android-ffmpeg-rtsp-player/issues)

</div>

> **免费版允许个人和企业商用。**
>
> 不限制应用、设备和最终用户数量，同一时刻最多播放一路；只有多路并发和 YUV 数据导出需要商业授权。

## 效果演示

<div align="center">
<img src="screenshot/testv.gif" alt="Android RTSP 超低延迟播放演示" width="760"/>
</div>

## 功能特点

- 硬件解码参考延迟 80–120ms，软件解码参考延迟 120–200ms
- 支持 RTSP、RTMP、HLS、HTTP-FLV、RTP、UDP、TCP
- 支持 H.264、H.265/HEVC、MPEG-4 和最高 4K 视频
- 支持 MediaCodec 硬件解码和 FFmpeg 软件解码
- 支持 OpenGL ES 3.0+ 硬件加速渲染
- 支持截图、录像、自动重连及异步 API
- 支持 Android 7.0+（API 24）
- 支持 arm64-v8a、armeabi-v7a、x86_64

## 免费版与商业版

| 功能 | 免费版 | 商业版 |
|---|:---:|:---:|
| 个人及企业商用 | ✅ | ✅ |
| 应用、设备、最终用户数量 | 不限制 | 按协议 |
| 单路播放 | ✅ | ✅ |
| 多路并发 | — | ✅ |
| 截图、录像、4K | ✅ | ✅ |
| H.264/H.265 软硬解 | ✅ | ✅ |
| OpenGL ES、自动重连 | ✅ | ✅ |
| YUV 数据导出 | — | ✅ |
| Headless YUV/AI 分析 | — | ✅ |
| 核心源码 | 不提供 | 不提供 |

## Demo 截图

<div align="center">
<img src="screenshot/MuMu-20251011-153144-611.png" alt="单路播放" width="280"/>
<img src="screenshot/MuMu-20251011-155352-156.png" alt="多路播放" width="280"/>
<img src="screenshot/MuMu-20251218-104858-750.png" alt="YUV 数据处理" width="280"/>
</div>

Demo 已获得专用展示授权，可以体验多路播放和 YUV 数据处理。该授权只对 Demo 的指定包名和签名生效，不能用于其他应用。仓库中的签名文件仅用于编译示例，不应用于正式产品。

## 快速开始

### 1. 添加 AAR

将 `ffmpegrtsp-lib.aar` 放入 `app/libs/`，然后添加依赖：

```kotlin
dependencies {
    implementation(files("libs/ffmpegrtsp-lib.aar"))
}
```

### 2. 添加网络权限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 3. 播放视频

```kotlin
val config = StreamConfig.Builder(rtspUrl)
    .audioEnabled(true)
    .build()

val player = StreamPlayer.playWithConfig(
    this,
    surfaceView,
    config
).setOnError { code, message ->
    Log.e("Player", "$code: $message")
}
```

完整示例：

- [单路播放](app/src/main/java/com/jxj/ffmpegrtspplayer/SinglePlayerActivity.kt)
- [多路播放](app/src/main/java/com/jxj/ffmpegrtspplayer/MultiPlayerActivity.kt)
- [YUV 处理](app/src/main/java/com/jxj/ffmpegrtspplayer/YUVTestActivity.kt)
- [Headless 分析](app/src/main/java/com/jxj/ffmpegrtspplayer/HeadlessAnalysisActivity.kt)

## 参考延迟

| 播放方式 | 参考延迟 |
|---|---:|
| 硬件解码 | 80–120ms |
| 软件解码 | 120–200ms |
| Android MediaPlayer | 300–800ms |

实际延迟受摄像机编码、网络、GOP、传输方式和设备性能影响，建议使用真实设备和视频源测试。

## 商业授权

商业授权开放多路并发、YUV 数据导出和 Headless YUV/AI 分析，并可另外提供技术支持及定制开发。

免费版和商业版均以 AAR 形式提供，不提供播放器核心源码。

- QQ 群：647718711
- [GitHub Issues](https://github.com/anwz0611/android-ffmpeg-rtsp-player/issues)

## 许可证

- 示例源码：以仓库 `LICENSE` 为准
- 核心播放器 SDK：闭源免费商用许可或商业授权
- FFmpeg 等第三方组件：遵循各组件自身许可证

如果这个项目对你有帮助，欢迎点一个 Star 支持持续维护。


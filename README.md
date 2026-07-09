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
- OpenGL ES 渲染：支持 OpenGL ES 3.0+ 硬件加速渲染，提供更好的性能和画面质量
- 多种渲染模式：软件渲染、OpenGL ES 渲染、自动选择
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


### Android API 文档

完整的 Android 调用层 API 说明，请直接查看  Wiki：

- [Android API Reference](https://github.com/anwz0611/android-ffmpeg-stream-player/wiki)

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

示例地址网络较差时可能体现不出超低延迟，建议自行搭建测试环境。定制需求或问题反馈请联系作者。本项目长期维护
（PS：遇到任何问题加群沟通，接定制化需求请联系作者）。

aar 包含 arm64-v8a、armeabi-v7a、x86_64 架构，如只需要特定架构请联系作者。

## 许可证

GPL v2，商业使用请联系作者获取授权。
## Star History

<a href="https://www.star-history.com/?repos=anwz0611%2Fandroid-ffmpeg-rtsp-player&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/image?repos=anwz0611/android-ffmpeg-rtsp-player&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/image?repos=anwz0611/android-ffmpeg-rtsp-player&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/image?repos=anwz0611/android-ffmpeg-rtsp-player&type=date&legend=top-left" />
 </picture>
</a>

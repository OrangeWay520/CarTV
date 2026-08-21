# OrangeIPTV（橙子网络电视）

一款适配**安卓手机、平板、电视盒子、车机**等设备的 Android 网络电视直播应用，基于 Jetpack Compose 构建，界面现代美观、操作简单，支持触摸与遥控器双重交互。

## 功能特性

### 📺 直播播放
- 基于 **Media3 ExoPlayer** 的流媒体播放，支持 HLS（M3U8）与 M3U/TXT 直播源
- **多播放源**：同一频道保留全部可用播放源，播放失败或遇到不支持的音频编码时**自动切换下一源**
- **重复播放**：对短时长的直播片段自动循环播放，保证画面不中断
- **现代风格播放器**：渐变黑色顶栏（现代返回按钮 + 频道标题）、渐变黑色底部控制栏
- 控制栏支持**触摸显隐**，5 秒无操作自动隐藏；支持画面比例切换（原始/16:9/4:3/填充）

### 🗂 频道管理
- 丰富的频道分类：央视、卫视、地方、少儿、电影、体育、纪实、付费、购物等
- 所有分类按**中文拼音自然排序**（CCTV1 → CCTV2 → … → CCTV10）
- 频道**多源合并**、名称去重归一化，避免重复条目
- 地方特色频道（如地方少儿/影视）**双分类**展示，两个分类都能找到

### ⭐ 频道收藏
- 播放器顶部状态栏右上角**一键收藏**（星星按钮），点亮即收藏
- 首页分类栏自动出现**"收藏频道"**专属分类，仅展示已收藏频道
- 已收藏频道卡片右上角显示**金色星星**角标
- 收藏列表持久化存储，跨会话保留

### 📍 地区筛选
- 两级地区筛选（省份 → 城市），可按需只显示特定省份/城市的地方频道
- 支持"显示全省频道"开关与"显示省级频道"开关独立控制
- 地区设置在**暂存区**中管理，点击"保存地区"后才生效，返回不会丢失选择

### 📅 EPG 节目预告
- 解析播放列表内置的 XMLTV 节目单（x-tvg-url）
- 首页频道卡片显示**当前节目**，播放器中可查看当日**完整节目预告**

### ⚙️ 个性化设置
- **主题设置**：跟随系统（默认）/ 浅色 / 深色
- **解码模式**：硬件解码（默认）/ FFmpeg 软件解码（解决设备不支持音频编码的问题）
- **M3U + TXT 双源合并**：可开关（默认关闭），支持自定义 TXT/M3U 地址与自动后缀切换
- **播放列表地址**：自由配置自定义 M3U/TXT 订阅地址
- **频道分类筛选**：首页可隐藏不需要的分类，频道卡片高度统一（90dp）
- 加载失败提供"重试 / 设置播放列表地址 / 恢复默认播放列表"三按钮恢复

### 📱 多设备适配
- 大图标、大字体，专为大屏与触摸设计
- 支持遥控器按键与触摸双重操作，兼容多屏窗口尺寸自适应
- 应用桌面图标使用专属橙色低多边形 Logo

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose（BOM 2025.08.00）、Material 3 |
| 播放器 | AndroidX Media3 ExoPlayer 1.9.0（HLS、Session） |
| 软解码 | Media3 FFmpeg 扩展（预编译 AAR，支持 mp3/aac/vorbis/opus/flac） |
| 网络 | OkHttp 4.12.0 |
| 存储 | DataStore Preferences |
| 图片加载 | Coil 2.7.0（SubcomposeAsyncImage） |
| 导航 | Navigation Compose |
| 构建 | AGP 9.3.1、Kotlin 2.2.10、JDK 17、compileSdk 36 |

## 项目结构

```
app/src/main/java/com/orangeway/iptv/
├── OrangeIPTVApp.kt            # Application 入口
├── MainActivity.kt              # 主入口 Activity
├── data/
│   ├── model/                   # Channel、EpgProgramme、Region 数据模型
│   ├── parser/                  # PlaylistParser（M3U/TXT）、EpgParser（XMLTV）
│   └── repository/              # ChannelRepository、EpgRepository、SettingsRepository
├── player/
│   └── PlayerActivity.kt        # 播放器页面（触屏控制栏、多源切换、EPG）
└── ui/
    ├── component/ChannelCard.kt # 频道卡片（统一 90dp 高度）
    ├── screen/                  # HomeScreen、HomeViewModel、SettingsScreen
    └── theme/                   # 颜色、主题（跟随系统/浅色/深色）、字体
```

## 环境要求

- **JDK 17** 及以上
- **Android Studio**（最新稳定版）
- Android SDK：compileSdk **37**、minSdk 23、targetSdk 37
- 支持 Android 6.0（API 23）+ 的安卓手机 / 平板 / 电视盒子 / 车机等设备

> 项目使用 Gradle Wrapper，首次构建会自动下载依赖。

## 构建方法

```bash
# 在项目根目录执行
./gradlew assembleDebug        # 构建 Debug APK
./gradlew assembleRelease      # 构建 Release APK（已启用 R8 混淆）
```

构建产物位于 `app/build/outputs/apk/` 目录。

在 Android Studio 中直接 `Open` 项目根目录，等待 Gradle Sync 完成后点击 **Run ▶** 即可安装到设备。

## 使用说明

1. **安装 APK** 到安卓手机 / 平板 / 电视盒子 / 车机等设备。
2. 打开应用即可看到频道列表，**点击频道卡片直接开始播放**。
3. 默认播放列表为 vbskycn 镜像源（`https://live.zbds.top/tv/iptv4.m3u`）。
4. 如需自定义播放列表，进入 **设置 → 播放列表地址** 填入你的 M3U/TXT 订阅地址。
5. 推荐配合自定义聚合播放列表使用，可加载 **1000+ 频道 / 上万个播放源**（见下文）。

### 自定义播放列表（推荐）

应用默认支持任意 M3U/TXT 直播源。也可以搭配专门的播放列表生成项目，定时抓取多个公开源、自动合并去重：

- **IPTV-Playlist（my-iptv）**：多源聚合播放列表自动生成项目，每 6 小时自动更新
  - 订阅地址：`https://raw.githubusercontent.com/OrangeWay520/my-iptv/main/my_channels.m3u`
  - 项目仓库：https://github.com/OrangeWay520/my-iptv

在 **设置 → 播放列表地址** 填入上述 M3U 地址，并可在 **设置 → M3U+TXT 双源合并** 中开启双源加载，进一步提升可用性。

## 免责声明

- 本项目为开源学习项目，仅提供播放器客户端能力，**不包含任何自建媒体资源**，不存储、不托管、不转码任何音视频内容。
- 应用内播放列表默认指向第三方公开直播源，直播源的有效性与合法性由各数据源维护方负责，与本项目无关。
- 文中出现的产品名称、商标、服务标志均为其各自所有者的财产，本说明仅作客观描述使用，不构成任何授权、合作或背书关系。
- 请遵守当地法律法规，仅观看已获授权的直播内容。
- 若涉及版权问题，请权利人联系相关数据源方处理。

## License

本项目基于 **MIT License** 开源，仅供学习交流使用，请勿用于商业用途。

```text
MIT License

Copyright (c) 2026 Orange Way

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

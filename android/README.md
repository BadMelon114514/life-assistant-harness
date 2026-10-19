# 生活助理 Harness · 安卓原生客户端

Kotlin + Jetpack Compose 编写的安卓客户端，连接桌面版后端（Spring Boot，默认 8080）。
数据仍全部保存在电脑端的 MySQL 与 data 目录，手机只作为操作与查看终端，不复制数据。

> 面向使用者的简明说明（安装、连接、功能对照、常见问题）见 [../docs/使用说明.md](../docs/使用说明.md)。

## 目录结构

```
android/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── local.properties              # sdk.dir 指向本机 Android SDK
└── app/
    ├── build.gradle.kts          # AGP 8.7.3 / Kotlin 2.1.0 / Compose BOM 2024.12.01
    └── src/main/
        ├── AndroidManifest.xml   # INTERNET、明文 HTTP、启动 Activity
        ├── res/                  # 主题、图标、应用名「生活助理」
        └── java/com/badmelon/farming/android/
            ├── LifeAssistantApp.kt   # Application，初始化 Prefs
            ├── MainActivity.kt       # setContent + 主题
            ├── App.kt                # 底部导航、全局 WebSocket、离线页、关于页
            ├── Api.kt                # OkHttp 封装（JSON / SSE 流 / WebSocket）
            ├── Prefs.kt              # 服务器地址、会话、主题持久化
            ├── Events.kt             # monitorTick / notifications / backendDown 事件流
            ├── Theme.kt              # 亮/暗色（与 Web 端同款 #4176E6 配色）
            ├── Common.kt             # 卡片、状态胶囊、Markdown 渲染等通用组件
            ├── ChatViewModel.kt      # 对话状态机：流式接收、历史持久化、确认、草稿导入
            └── *Screen.kt            # 对话/监控/任务/知识/记忆/MCP/技能/设置/更多
```

## 构建

前置：JDK 17+（本机使用 `C:\Program Files\Zulu\zulu-22`）、Android SDK（platform 35、build-tools 35）。

```bat
set JAVA_HOME=C:\Program Files\Zulu\zulu-22
cd /d D:\jvav\farming\android
D:\jvav\farming\.gradle-home\gradle-8.11.1\bin\gradle.bat assembleDebug
```

产物：`android\app\build\outputs\apk\debug\app-debug.apk`（约 10 MB，minSdk 26 / targetSdk 35）。
仓库 `dist\生活助理Android.apk` 为已构建好的安装包。

## 安装与连接

```bat
D:\Android\Sdk\platform-tools\adb.exe install -r D:\jvav\farming\dist\生活助理Android.apk
```

- **模拟器**：默认服务器地址 `http://10.0.2.2:8080`（即宿主机 localhost），装好即可用。
  启动 AVD（本机 `farm35`，Android 15 / x86_64）：
  `D:\Android\Sdk\emulator\emulator.exe -avd farm35 -no-snapshot -gpu swiftshader_indirect`，无窗口调试可加 `-no-window`；结束用 `adb emu kill`。
- **真机**：手机与电脑连同一 Wi-Fi，在「更多 → 设置 → 服务器地址」填电脑局域网 IP，例如 `http://192.168.1.10:8080`，点「测试连接」；需放行 Windows 防火墙 8080 端口。
- 明文 HTTP 已通过 `usesCleartextTraffic` 与 network-security-config 放开，仅建议在局域网使用。

### 本机模拟器环境（已配置）

| 项 | 值 |
|----|----|
| SDK | `D:\Android\Sdk`（cmdline-tools latest、platform-tools、build-tools 34/35、platforms android-35、emulator、system-images android-35 google_apis x86_64） |
| 环境变量 | `ANDROID_HOME` / `ANDROID_SDK_ROOT` = `D:\Android\Sdk`；用户 PATH 追加 platform-tools、emulator、cmdline-tools\latest\bin（由 `tools/scripts/set_android_env.ps1` 幂等设置，新开终端生效） |
| AVD | `farm35`：Pixel 5、Android 15（API 35）、x86_64、1080×2340、RAM 2G |
| 加速 | WHPX 已启用（`emulator -accel-check` 输出 `WHPX(10.0.26200) is installed and usable.`） |

```bat
:: 启动（无窗口调试加 -no-window）
D:\Android\Sdk\emulator\emulator.exe -avd farm35 -no-snapshot -gpu swiftshader_indirect

:: 等待开机完成（输出 1 即就绪）
adb wait-for-device
adb shell getprop sys.boot_completed

:: 安装并启动
adb install -r D:\jvav\farming\dist\生活助理Android.apk
adb shell am start -n com.badmelon.farming.android/.MainActivity

:: 结束模拟器
adb emu kill
```

> 模拟器内 `10.0.2.2` 指向宿主机，后端只需在本机 8080 运行；实测打开「进程监控」可正常显示 5 名成员与实时进度。

## 功能

| 页面 | 能力 |
|------|------|
| 对话 | SSE 流式输出、历史记录（自动写入后端）、新对话/清空、停止生成、高危操作确认弹窗、Skill/MCP 草稿一键导入、`[[SCHEDULE:...]]` 定时提醒解析 |
| 监控 | 团队汇总 4 项指标、成员进度与心跳、最近日志；5s 轮询 + WebSocket snapshot 触发即时刷新 |
| 任务 | 状态筛选、新建（含依赖）、认领、推进、完成、删除 |
| 知识库 | 关键词检索、文档详情、保存文本、删除 |
| 记忆库 | 列表、新增、删除 |
| MCP | 列表、启用/停用、连通性测试、新增、删除 |
| 技能 | 列表、启用/停用、查看指令内容、新增、删除 |
| 设置 | 服务器地址与连通性测试、API Key、主题（跟随系统/亮/暗）、关闭后端（二次确认） |
| 全局 | 底部导航、返回键层级、服务器关闭后的离线页与重试、定时提醒前台 Snackbar、断线 3s 自动重连 WebSocket |

## 与 Web 端的差异

- 暂不支持知识库文件上传（可保存文本），文件导入仍在桌面端完成。
- 模型管理、子对话列表管理、联网搜索开关仍在 Web 端设置。
- 定时提醒依赖前台连接：应用在后台时收不到 WebSocket 通知（后续可接入 FCM/本地通知）。
- 关闭后端同样会联动关闭上下文存储服务（8765），与 Web 端行为一致。

## 验收

- 构建：`gradle assembleDebug` 成功；包名 `com.badmelon.farming.android`、应用名「生活助理」、versionCode 2 / versionName 2.0.0、minSdk 26 / targetSdk 35；APK 约 10.3 MB。
- 接口契约与 Web 端一致，后端回归见 `test_suite/`（f9 前后端、f12 对抗、f13 上下文、f17 关闭后端）。

### 模拟器实测记录（Android 15 / farm35 / 1080x2340）

| 项目 | 结果 |
|------|------|
| 启动与会话 | 冷启动约 3s，进入「对话」页并加载历史（会话 `android_mvf2w01m`） |
| 对话链路 | 快捷问「现在几点了？我在哪个城市？」→ 回复时间 2026-10-19 18:06（UTC+8）与城市「江西南昌（IP 定位 ipinfo.io）」 |
| SSE 流式 | 长问题回复逐字出现，右侧按钮由「发送」变「停止」，点「停止」后流式中断、已生成内容保留 |
| 新对话 | 会话号切换为 `android_mvf39ddf`，欢迎卡片与 4 条快捷建议重新出现 |
| 进程监控 | 汇总 5 成员 / 0 工作中 / 3 空闲 / 平均进度 76%，成员卡与「当前：」任务文案正常 |
| 任务看板 | 全部/待处理/进行中/已完成 筛选生效；空结果显示「没有符合条件的任务」 |
| 知识库 | 搜索 `AI` 命中 `202608AI报告.md` 并按相关度排序 |
| 记忆库 | 列表、重要度、时间、删除按钮正常（含敏感信息拒存历史） |
| MCP / 技能 | 列表加载、启停/测试/删除按钮就位；测试 `Everything Server` 返回「✅ stdio 服务已保存配置」 |
| 设置 | 服务器地址、API Key、主题三选项、危险操作「关闭后端服务」二次确认弹窗 |
| 主题 | 深色/浅色即时生效（截图平均亮度 31 / 244），杀掉进程重启后仍保持深色，证明 Prefs 持久化生效 |
| 版本 | 「关于」与「设置」均显示 版本 2.0.0，与 `build.gradle.kts` 一致 |

### 本轮修复（模拟器实测发现）

1. **启动即崩溃**：`Api.connectMonitor` 把 URL scheme 改成 `ws`/`wss` 再交给 OkHttp，触发 `IllegalArgumentException: unexpected scheme: ws`。改为直接用 `http/https` URL 交给 `newWebSocket`（内部自动升级），并用 `runCatching` 包裹，失败回调 `onClosed` 而不是崩溃。
2. **JSON null 显示为字符串 "null"**：`JSONObject.str()` 对 `JSONObject.NULL` 返回字符串 `"null"`（Android org.json 行为），任务卡片出现「负责人：null」。`str()` 统一把 `"null"/"undefined"` 归空，UI 回落到「未认领」。
3. **版本号不一致**：`build.gradle.kts` 为 1.0.0，而「关于/设置」写死 2.0.0。统一升为 versionCode 2 / versionName 2.0.0，并同步 `dist\生活助理Android.apk`。

复跑命令：

```bat
set JAVA_HOME=C:\Program Files\Zulu\zulu-22
cd /d D:\jvav\farming\android
D:\jvav\farming\.gradle-home\gradle-8.11.1\bin\gradle.bat assembleDebug
D:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

自动化验收脚本（需设备已连接、后端已启动）：

```bat
E:\python\.venv\Scripts\python.exe tools\scripts\android_acceptance.py
```

脚本读取 `adb devices` 与 `uiautomator dump`，自动检查启动页、会话号、5 个页签、任务筛选、危险操作卡片、版本号与重启后的会话持久化，当前 12/12 通过。

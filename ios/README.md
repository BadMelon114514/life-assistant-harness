# iOS 原生客户端（SwiftUI）

生活助理 Harness 的 iOS 原生客户端，使用 **SwiftUI + async/await** 编写，复用桌面端后端同一套 REST / SSE / WebSocket 接口。数据仍保存在电脑端的 MySQL 与 `data/` 目录，手机只做展示与操作。

- 最低系统：iOS 16.0
- 语言/框架：Swift 5、SwiftUI、URLSession（`bytes(for:)` 流式）、`URLSessionWebSocketTask`
- 第三方依赖：无（不需要 CocoaPods / SwiftPM）
- 版本：2.0.0（与安卓端、Web 端功能对齐）

## 一、目录结构

```
ios/
├── LifeAssistant/
│   ├── App.swift                 # @main 入口、SocketHub（全局 WS）、RootView、底部 5 个 Tab
│   ├── Theme.swift               # 与 Web 端一致的浅色/深色配色（#4176E6 主色）
│   ├── Prefs.swift               # UserDefaults：服务器地址、会话 key、主题
│   ├── Api.swift                 # REST 封装 + SSE 流式解析 + 连通性测试
│   ├── JsonHelpers.swift         # JSON 取值扩展、ConfirmReq / DraftInfo / ChatMsg
│   ├── MonitorSocket.swift       # /ws/monitor WebSocket 客户端
│   ├── Common.swift              # 卡片、状态胶囊、Markdown 轻渲染、时间/状态工具
│   ├── ChatModel.swift           # 对话状态机（历史/发送/停止/确认/草稿/定时提醒）
│   ├── ChatView.swift            # 对话页（流式气泡、欢迎卡、确认弹窗、草稿导入）
│   ├── MonitorView.swift         # 进程监控（5s 轮询 + WS 触发）
│   ├── TasksView.swift           # 任务看板（筛选/认领/推进/完成/删除/新建）
│   ├── KnowledgeView.swift       # 知识库（检索/详情/保存文本/删除）
│   ├── MemoryView.swift          # 记忆库（新增/删除）
│   ├── McpView.swift             # MCP 服务（启停/测试/新增/删除）
│   ├── SkillsView.swift          # 技能（详情/启停/新增/删除）
│   ├── SettingsView.swift        # 服务器地址、API Key、主题、关闭后端
│   ├── MoreView.swift            # 更多入口 + 关于页 + 服务关闭离线页
│   ├── Info.plist                # 显示名、ATS（允许本地 http）、本地网络权限
│   └── Assets.xcassets/          # AppIcon（1024）、AccentColor
├── LifeAssistant.xcodeproj/
│   ├── project.pbxproj           # 手写 Xcode 工程（objectVersion 56，17 个 Swift 源文件）
│   └── xcshareddata/xcschemes/LifeAssistant.xcscheme
└── README.md                     # 本文档
```

## 二、构建与运行

> 需要 macOS + Xcode（本仓库当前在 Windows 上开发，无法在 Windows 编译/签名 iOS 应用）。

1. 打开工程：

   ```bash
   open ios/LifeAssistant.xcodeproj
   ```

2. 选择 `LifeAssistant` target → **Signing & Capabilities** → 选择自己的 Team（真机必须；模拟器可跳过）。
3. 选择模拟器（如 iPhone 16）或已连接的真机，点击 **Run**。

命令行构建（模拟器，无需签名）：

```bash
xcodebuild -project ios/LifeAssistant.xcodeproj \
  -scheme LifeAssistant -configuration Debug \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  build
```

> 若 Xcode 提示工程文件版本/签名问题，优先在 Xcode 中重新选择 Team；本工程使用 `CODE_SIGN_STYLE = Automatic`、`GENERATE_INFOPLIST_FILE = NO`，Info.plist 由仓库提供。

## 三、连接后端

先启动桌面端（桌面快捷方式或 `dist\生活助理Harness一键启动.exe`），确认浏览器打开 `http://127.0.0.1:8080` 正常。

| 运行环境 | 服务器地址 | 说明 |
|----------|-----------|------|
| iOS 模拟器 | `http://127.0.0.1:8080`（默认） | 模拟器与 Mac 共享网络；Windows 上无 iOS 模拟器 |
| 真机 | `http://<电脑局域网IP>:8080` | 手机与电脑同一 Wi-Fi；如 `http://192.168.1.10:8080` |

在 App 内：**更多 → 设置 → 服务器地址** → 输入地址 → **保存并测试**，显示「连接成功（N ms）」即通。修改地址会同时生成新的会话 key，避免串对话。

连不上时依次检查：

1. Windows 防火墙是否放行 8080（以及可选的 8765）；
2. 手机与电脑是否同一网段（不要用访客 Wi-Fi / AP 隔离）；
3. 电脑是否休眠、后端是否被「关闭后端服务」停掉；
4. 地址是否带 `http://` 前缀、末尾不要多写路径。

> `Info.plist` 中 `NSAppTransportSecurity.NSAllowsArbitraryLoads = true` 仅用于局域网明文 HTTP 调试；公网部署请改用 HTTPS 并移除该例外。

## 四、功能

| 页面 | 能力 |
|------|------|
| 对话 | SSE 流式输出、历史恢复、新对话、清空、停止生成、Markdown 轻渲染、定时提醒标记解析、高危操作二次确认、技能/MCP 草稿一键导入 |
| 监控 | 团队汇总指标（总数/工作中/空闲/平均进度）、成员进度与心跳、最近日志（5 秒轮询 + WebSocket `snapshot` 触发） |
| 任务 | 状态筛选、新建、认领、+10% 推进、完成、删除 |
| 知识库 | 关键词检索、详情查看、保存文本、删除 |
| 记忆库 | 浏览、新增（重要度 1-10 / 分类）、删除 |
| MCP | 列表、启用/停用、连通性测试、新增、删除 |
| 技能 | 列表、详情、启用/停用、新增、删除 |
| 设置 | 服务器地址与连通性测试、模型 API Key、主题（跟随系统/浅色/深色）、关闭后端（二次确认，联动 8765） |
| 更多 | 知识库/记忆库/MCP/技能/设置/关于入口；后端关闭后展示离线页与「重试连接」 |

全局行为：

- 启动即连接 `/ws/monitor`；收到 `assistant_notify` 弹顶部提示，收到 `server_shutdown` 切离线页；
- 断线 3 秒后自动重连（离线页状态下不重连）；
- 主题通过 `@AppStorage("themeMode")` 持久化，配色与 Web / 安卓端一致。

## 五、与 Web / 安卓端的差异

| 项 | iOS 端 | 说明 |
|----|--------|------|
| 知识库文件上传 | ❌ 仅保存文本 | PDF/Word/Excel 上传仍在网页端 |
| Markdown | 轻量渲染（标题/列表/粗体/行内代码/代码块） | 不支持表格、图片、ECharts 图表 |
| 定时提醒 | 解析并提交后端；前台 WebSocket 接收 | 未接入 APNs，App 在后台/被杀时收不到本地通知 |
| 子对话列表 | ❌ 仅「新对话」 | 多子会话管理在网页端 |
| 模型管理 / 搜索配置 | ❌ | 在网页端设置 |
| 文件选择与上传 | ❌ | 受沙箱限制，聊天里仍可让助手操作项目内文件（高危操作需确认） |

## 六、静态校验

Windows 上无 Swift/Xcode，仓库提供静态校验脚本（文件完整性、Info.plist、pbxproj 引用/构建阶段、共享 Scheme、bundle id、部署目标、Swift 括号、tree-sitter 语法树、Api/Common 符号、接口字符串、AppIcon 尺寸）：

```bat
E:\python\.venv\Scripts\python.exe tools\scripts\verify_ios_project.py
```

- 结果：**143/143 PASS**（报告写入 `logs/ios_verify_report.txt`）
- 依赖：`plistlib`（标准库）、`pbxproj`、`tree_sitter` + `tree_sitter_swift`、`Pillow`（可选，缺省自动跳过对应检查）

> 静态校验只能证明工程结构完整、Swift 语法树无错误节点；类型检查、链接、签名与运行仍需在 macOS + Xcode 完成。

## 七、已知限制

1. **无法在 Windows 构建**：需要 macOS 13+ 与 Xcode 15+；本仓库只保证工程文件与源码完整。
2. 未接入推送通知：定时提醒只在 App 前台、WebSocket 在线时弹出；后台依赖系统挂起策略。
3. 明文 HTTP：仅建议局域网调试；公网请加 HTTPS 反向代理并移除 ATS 例外。
4. 知识库不支持文件上传与解析，仅能保存纯文本。
5. Markdown 为简化渲染，复杂表格/图片显示效果弱于网页端。
6. 无本地缓存数据库：断网时只能看当前内存中的页面数据，重新进入需联网加载。

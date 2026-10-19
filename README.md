# 生活助理 Harness

基于 `harness-rule.md`（Agent 环架架构理念）构建的生活助理 Harness，采用 **前后端一体 + 本地化部署** 架构，数据完全本地化存储。

> 核心思想：**机制很多，循环一个** —— 所有功能（工具、权限、计划、记忆、任务、多 Agent 协作、知识库）都挂在同一个 Agent 循环周围。

## 功能总览

| 模块 | 说明 | 对应 Harness 章节 |
|------|------|------------------|
| **🏠 生活助理** | 结合 AI、知识库、记忆、任务、团队状态回答日常生活与办公问题 | s01-s04, s09-s10 |
| **📊 进程监控** | WebSocket 实时监控各 Teammate 进程进度并汇总 | s15-s18 |
| **📋 任务看板** | 大目标拆小任务，支持依赖关系与自动认领 | s05, s12 |
| **📚 本地知识库** | 上传本地文件，自动识别文本/表格/PPT/PDF；助手可直接读取指定文档全文并分析 | s07 知识加载 |
| **🧠 记忆库** | 系统长期记忆，自动注入对话 | s09 |
| **🔌 MCP 服务** | 自行添加外部工具/数据服务（http/sse/stdio），支持连通性测试、远程下载导入（自动病毒/后门扫描） | - |
| **🛠️ Skill 技能** | 自定义技能指令，启用后注入 AI 对话，支持远程下载导入（自动病毒/后门扫描） | - |
| **🛡️ 安全扫描** | 下载的 MCP/Skill 自动进行静态特征扫描，识别命令执行/反弹shell/凭据窃取等病毒后门特征 | - |
| **🤖 自动生成 Skill/MCP** | 聊天助手由 LLM 自主判断是否需新增 Skill/MCP（自适应语义识别），联网搜集并编写定义，可一键导入（含安全扫描） | - |
| **⬇️ 主动下载新增 Skill/MCP** | 聊天助手识别"下载/新增/安装"意图，从 URL 下载或自动生成后直接新增到系统 | - |
| **🔧 工具自动调度** | 助手类似 OpenClaw 的 Agent：自动识别需求并调用已安装的 MCP 工具 / Skill 技能完成任务 | - |
| **🔁 ReAct 循环** | 决策 → 执行 → 观察最多 3 轮，前序工具结果进入下一轮决策；按「名称+参数」签名去重（同一工具可读多个文件，重复副作用不重放）；仅在存在顺序/失败信号时继续；自验证以工具真实返回为权威依据，不把真实数据改写成「无法获取」 | s08 |
| **💬 聊天记录持久化** | 生活助理对话自动保存，切换页面后可恢复历史，支持清空回滚 | - |
| **🔗 上下文连续** | AI 回答时自动携带历史对话作为上下文，能记住之前说过的话 | - |
| **🗜️ 上下文压缩** | 历史超限时自适应压缩（条数裁剪 + LLM 摘要），借鉴 CC s08 四层压缩 | s08 |
| **💬 子对话管理** | 支持多个独立子对话，可新建/切换/删除子对话窗口 | - |
| **⏰ 定时提醒** | 对话直接说"X分钟后提醒我…"即可入队（自动建表+轮询触发），到期写入聊天历史并经 WebSocket 推送气泡；内置 reminder MCP（http）可被工具调度，支持查询/取消 | - |
| **🗂️ 上下文存储** | 面向长期运行的上下文冷热三级存储（L1 热 / L2 温 / L3 冷，按调用权重下沉）、Git 式快照 diff/回滚、多 Agent 黑板即时共享、MCP/Skill 版本同步与回滚、文档分章摘要检索（Python+MySQL 独立服务，内置 context-store MCP） | s08 上下文管理 |
| **🔍 运行过程监控** | 聊天助手返回完整执行链路（意图识别/知识库/搜索/工具调度/AI生成/自验证），前端可查看 | - |
| **🪞 自验证自纠错** | AI 生成回答后自动进行自验证，发现答非所问/混淆历史对话时自动改写；涉及工具结果时以工具返回为事实依据，避免误判为编造 | - |
| **⚡ 流式输出** | AI 回答逐 token 实时流式输出（SSE），前端边生成边展示思考过程 | - |
| **🧩 多模型切换** | 配置多个 AI 模型（DeepSeek/OpenAI 等），支持一键切换当前使用模型 | - |
| **🛡️ AI 错误恢复** | 借鉴 Agent Harness 机制：限流/过载自动指数退避重试，主模型故障自动切换备用模型 | s11 |
| **🌐 联网搜索** | 生活助理联网检索最新信息（Bing/DuckDuckGo） | - |
| **⏻ 前端关闭后端** | 设置页/侧栏电源按钮一键关闭后端（8080）与上下文存储（8765）：二次确认、优雅停机、离线页提示重启，数据不丢失 | - |
| **📱 响应式布局** | 1440/1024/768/390 多档自适应：侧栏收窄为图标、内容区最大 1180px 居中、聊天气泡长词换行、宽表在卡片内横向滚动，无横向溢出 | - |
| **🤖 安卓原生客户端** | Kotlin + Jetpack Compose 原生 App：对话（SSE 流式）、进程监控、任务、知识库、记忆、MCP、技能、设置，连桌面端局域网后端，见 [android/README.md](android/README.md) | - |
| **🍎 iOS 原生客户端** | SwiftUI 原生 App（iOS 16+）：同一套页面与接口、SSE 流式对话、全局 WebSocket、主题跟随，需 macOS + Xcode 构建，见 [ios/README.md](ios/README.md) | - |
| **⚙️ 设置** | API Key 保存/修改、系统参数、搜索配置管理 | - |

## 技术栈

- **后端**：Spring Boot 3.5.6 + MyBatis + MySQL 8.0 + Druid 连接池 + WebSocket
- **前端**：原生 HTML/CSS/JS 单页应用（由 Spring Boot 直接托管，无需单独部署）
- **AI**：DeepSeek（deepseek-v4-flash，OpenAI 兼容接口），API Key 动态配置
- **文件解析**：PDFBox（PDF）、内置 ZIP/XML 解析（docx/xlsx/pptx）
- **安卓端**：Kotlin 2.1 + Jetpack Compose（Material 3）+ OkHttp + Coroutines，原生 App 复用同一套 REST / SSE / WebSocket 接口
- **iOS 端**：Swift 5 + SwiftUI + URLSession（async/await 流式），iOS 16+，无第三方依赖

## 部署步骤（本地化）

### 1. 初始化数据库

在 MySQL 中执行 `src/main/resources/schema.sql`：

```bash
mysql -uroot -p < src/main/resources/schema.sql
```

该脚本会创建 `farming_harness` 数据库及全部数据表。

### 2. 配置数据库连接

数据库账号默认 `root`，密码不写入配置文件，通过环境变量 `DB_PASSWORD` 提供（执行 schema.sql 与启动应用时都需要）：

```bash
set DB_PASSWORD=你的MySQL密码          # Windows CMD
$env:DB_PASSWORD="你的MySQL密码"      # PowerShell
export DB_PASSWORD="你的MySQL密码"    # macOS / Linux
```

需要改用户名时设置 `DB_USERNAME`；地址与端口在 `src/main/resources/application.yml` 中调整。
上下文存储服务（`context_store/`）读取同一个 `DB_PASSWORD`（也支持 `CTX_DB_PASSWORD` 覆盖）。

### 3. 打包运行

```bash
# 打包（使用本地 Maven 仓库）
gradlew.bat bootJar --offline

# 运行
java -jar build/libs/farming-0.0.1-SNAPSHOT.jar
```

### 4. 访问系统

浏览器打开 **http://localhost:8080**

- 首次使用请前往 **设置** 页面填写 AI API Key（DeepSeek，默认模型 deepseek-v4-flash）
- 数据默认存储在运行目录 `./data` 下

### 5. 启动上下文存储服务（可选）

长期上下文存储是独立的 Python + MySQL 服务（`context_store/`），Java 应用通过内置 `context-store` MCP 调用：

```bash
E:\python\.venv\Scripts\python.exe -m pip install pymysql   # 一次性安装依赖
cd /d D:\jvav\farming
E:\python\.venv\Scripts\python.exe -m context_store.server --port 8765 > logs\ctx_server.log 2>&1
```

- 健康检查：`curl http://127.0.0.1:8765/health`（自动建 7 张 ctx_* 表）
- 启动后进入 **设置 → 导入内置 MCP 库**，或直接在 **MCP 服务** 页对「上下文存储」点测试
- 详见 [docs/上下文存储方案.md](docs/上下文存储方案.md)

### 6. 桌面一键启动（exe 封装）

已打包为免安装 Windows 程序，双击桌面快捷方式即可启动全部服务并自动打开浏览器：

```
D:\jvav\farming\dist\
├── 生活助理Harness一键启动.exe     # 启动器：拉起 Java 应用 + 上下文存储，等就绪后打开浏览器
├── FarmingHarness\                # jpackage 应用镜像（自带 JRE 25，无需安装 Java）
│   ├── FarmingHarness.exe
│   ├── app\farming-0.0.1-SNAPSHOT.jar
│   └── runtime\...
├── context-store\context-store.exe # PyInstaller 单文件上下文存储服务（:8765）
└── icon.ico                       # 应用图标
```

- 桌面快捷方式：`C:\Users\<用户名>\Desktop\生活助理 Harness.lnk`（开始菜单 `生活助理 Harness` 目录下也有）
- 启动器会自动跳过已运行的服务（按 8080/8765 端口判断），日志见 `logs\launcher.log`、`logs\app_exe.log`、`logs\ctx_server_exe.log`
- 运行前提：MySQL 已启动；`dist` 目录与项目目录保持相对位置（启动器以自身上级目录为工作目录）
- 重新打包：`powershell -ExecutionPolicy Bypass -File tools\pack\build_exe.ps1`（需先停止正在运行的应用，避免文件占用）

### 7. 安卓客户端（可选）

`android/` 为原生 android 工程（Kotlin + Jetpack Compose），连接桌面版后端使用：

```bat
set JAVA_HOME=C:\Program Files\Zulu\zulu-22
cd /d D:\jvav\farming\android
D:\jvav\farming\.gradle-home\gradle-8.11.1\bin\gradle.bat assembleDebug
D:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

- 已构建安装包：`dist\生活助理Android.apk`
- 模拟器默认连 `http://10.0.2.2:8080`；真机在 App「更多 → 设置」填电脑局域网地址（如 `http://192.168.1.10:8080`）并测试连接
- 详细说明见 [android/README.md](android/README.md)

### 8. iOS 客户端（可选）

`ios/` 为原生 SwiftUI 工程（iOS 16+），复用桌面端 REST / SSE / WebSocket 接口：

```bash
# 需 macOS 13+ 与 Xcode 15+（Windows 无法编译 iOS 应用）
open ios/LifeAssistant.xcodeproj
# 选择 Team 后运行；或命令行构建模拟器版本：
xcodebuild -project ios/LifeAssistant.xcodeproj -scheme LifeAssistant \
  -configuration Debug -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16' build
```

- 模拟器默认连 `http://127.0.0.1:8080`；真机在 App「更多 → 设置」填电脑局域网地址（如 `http://192.168.1.10:8080`）并测试连接
- Windows 下可用 `E:\python\.venv\Scripts\python.exe tools\scripts\verify_ios_project.py` 做工程静态校验（143 项）
- 详细说明见 [ios/README.md](ios/README.md)

## 项目结构

```
src/main/java/com/badmelon/farming/
├── FarmingApplication.java       # 启动类
├── config/                        # WebSocket、启动初始化
├── controller/                    # REST API（设置/知识库/监控/任务/队友/助手/记忆）
├── entity/                        # 数据实体
├── mapper/                        # MyBatis 数据访问接口
└── service/                       # 业务逻辑
    ├── AiService.java             # LLM 调用（s11 错误恢复：指数退避+fallback模型）
    ├── AiModelService.java        # AI 多模型管理/切换
    ├── AssistantService.java      # 生活助理（整合记忆/知识库/团队/搜索/技能）
    ├── ChatHistoryService.java    # 聊天记录持久化/回滚
    ├── KnowledgeService.java      # 知识库文件解析
    ├── MemoryService.java         # 记忆系统（s09）
    ├── McpServerService.java      # MCP 服务管理（CRUD/连通性测试）
    ├── MonitorService.java        # 监控聚合与 WebSocket 推送
    ├── RemoteImportService.java   # 远程下载导入（MCP/Skill，含安全扫描）
    ├── SearchService.java         # 联网搜索（Bing/DuckDuckGo）
    ├── SecurityScanner.java       # 病毒/后门静态特征扫描器
    ├── SkillMcpGeneratorService.java # 聊天助手自动搜集编写 Skill/MCP 定义
    ├── ToolScheduler.java         # Agent 工具调度（自动调用 MCP/Skill）
    ├── SettingsService.java       # API Key/系统设置
    ├── SkillService.java          # Skill 技能管理（注入对话）
    ├── TaskService.java           # 任务图（s12）
    └── TeammateService.java       # 多 Agent 协作（s15-s18）

src/main/resources/
├── application.yml                # 配置
├── schema.sql                     # 数据库初始化
├── mapper/*.xml                   # MyBatis 映射
└── static/                        # 前端页面
    ├── index.html
    └── app.js

context_store/                    # 上下文存储服务（Python + MySQL，独立进程，:8765）
├── schema.sql / db.py            # 7 张 ctx_* 表 DDL 与 pymysql 封装
├── store.py                      # 分层/权重/快照/黑板/资源版本/文档摘要
├── summarize.py                  # DeepSeek 章节摘要（失败截断兜底）
└── server.py                     # HTTP 服务（REST + 统一 /api/mcp/tool）

android/                          # 安卓原生客户端（Kotlin + Jetpack Compose）
├── build.gradle.kts / settings.gradle.kts / gradle.properties
└── app/src/main/java/com/badmelon/farming/android/
    ├── App.kt                    # 底部导航、全局 WebSocket、离线页
    ├── Api.kt                    # OkHttp：JSON / SSE 流 / WebSocket
    ├── ChatViewModel.kt          # 对话状态机（流式、历史、确认、草稿）
    ├── Common.kt / Theme.kt      # 通用组件与亮暗主题
    └── *Screen.kt                # 对话/监控/任务/知识/记忆/MCP/技能/设置

ios/                              # iOS 原生客户端（SwiftUI，iOS 16+）
├── LifeAssistant/                # 17 个 Swift 源文件 + Info.plist + Assets.xcassets
│   ├── App.swift                 # 入口、SocketHub（全局 WS）、TabView、离线页
│   ├── Api.swift / ChatModel.swift   # REST+SSE 客户端 / 对话状态机
│   ├── Theme.swift / Common.swift    # 亮暗配色 / 通用组件与轻量 Markdown
│   └── *View.swift               # 对话/监控/任务/知识/记忆/MCP/技能/设置/更多
├── LifeAssistant.xcodeproj/      # project.pbxproj + 共享 Scheme
└── README.md                     # 构建、连接、功能与限制
```

## 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/settings/api-key` | 保存 API Key |
| GET | `/api/settings` | 获取设置 |
| POST | `/api/assistant/chat` | AI 生活助理对话 |
| GET | `/api/monitor/overview` | 进程进度汇总 |
| GET | `/api/teammates` | Teammate 列表 |
| POST | `/api/teammates` | 启动进程 |
| POST | `/api/teammates/{name}/shutdown` | 停止进程 |
| GET | `/api/tasks` | 任务列表 |
| POST | `/api/knowledge/upload` | 上传知识文件 |
| GET | `/api/knowledge?keyword=` | 检索知识库 |
| GET | `/api/mcp` | MCP 服务列表 |
| POST | `/api/mcp` | 新增/编辑 MCP 服务 |
| POST | `/api/mcp/{id}/test` | 测试 MCP 服务连通性 |
| POST | `/api/mcp/{id}/toggle` | 启用/停用 MCP 服务 |
| POST | `/api/mcp/download` | 从 URL 下载导入 MCP（自动安全扫描） |
| POST | `/api/mcp/scan` | 仅下载并扫描 MCP，返回安全报告 |
| GET | `/api/skills` | Skill 技能列表 |
| POST | `/api/skills` | 新增/编辑技能 |
| POST | `/api/skills/{id}/toggle` | 启用/停用技能 |
| POST | `/api/skills/download` | 从 URL 下载导入 Skill（自动安全扫描） |
| POST | `/api/skills/scan` | 仅下载并扫描 Skill，返回安全报告 |
| GET | `/api/search?q=` | 联网搜索 |
| GET | `/api/search/status` | 搜索配置状态 |
| GET | `/api/chat/history` | 获取聊天历史记录（可按 session 过滤） |
| POST | `/api/chat/history` | 保存一条聊天消息 |
| DELETE | `/api/chat/history` | 清空聊天记录（回滚） |
| GET | `/api/chat/history/sessions` | 列出所有子对话 |
| POST | `/api/chat/history/session` | 新建子对话 |
| DELETE | `/api/chat/history/session/{key}` | 删除子对话 |
| GET | `/api/models` | 列出所有 AI 模型 |
| POST | `/api/models` | 新增/编辑模型 |
| POST | `/api/models/{id}/activate` | 切换激活模型 |
| DELETE | `/api/models/{id}` | 删除模型 |
| POST | `/api/assistant/chat` | 常规对话（一次性返回） |
| POST | `/api/assistant/chat-stream` | 流式对话（SSE，逐 token 输出） |
| POST | `/api/assistant/schedule` | 设定/取消定时提醒（`instruct`/`delayMinutes`/`title`/`session`，`cancelId` 取消） |
| GET | `/api/assistant/schedule` | 查询提醒列表（可带 `?session=` 过滤） |
| DELETE | `/api/assistant/schedule/{id}` | 取消指定提醒 |
| GET | `/api/assistant/location` | 本机时间 + 地理位置（公网 IP 定位） |
| POST | `/api/system/shutdown` | 关闭后端服务（仅本机 + `X-Shutdown-Confirm` 头；优雅停机并联动关闭 8765） |
| GET/POST | `http://127.0.0.1:8765/api/mcp/tool` | 上下文存储统一 MCP 端点（独立 Python 服务，22 个 op：分层/快照/黑板/资源版本/文档检索） |
| WS | `/ws/monitor` | 实时监控推送（snapshot / assistant_notify / server_shutdown） |

## 说明

- **AI 不可用时自动降级**：知识库摘要、任务完成报告等场景在未配置 Key 时仍可正常运行
- **本地优先**：所有数据（知识文件、任务、记忆、日志）均存储于本地 MySQL 与 `./data` 目录
- **多 Agent 协作**：系统默认启动 3 个 Teammate 进程（运营分析/客服响应/商品助理），自动认领任务并上报进度

## 相关文档

| 文档 | 说明 |
|------|------|
| [docs/项目结构文档.md](docs/项目结构文档.md) | 项目文件结构 + 逐文件功能注释（开发维护参考） |
| [docs/使用说明.md](docs/使用说明.md) | 面向普通使用者的使用说明（启动、对话、各功能、手机端、常见问题） |
| [docs/操作指南.md](docs/操作指南.md) | 用户/管理员操作手册（部署、功能使用、运维、二次开发） |
| [docs/上下文存储方案.md](docs/上下文存储方案.md) | 上下文三级存储/权重/快照回滚/黑板/资源版本/文档摘要的设计、API 与验收 |
| [docs/鲁棒性测试报告.md](docs/鲁棒性测试报告.md) | 响应式布局、后端异常/安全边界、对抗性提示、ReAct 与上下文存储的鲁棒性测试结果与修复记录 |
| [android/README.md](android/README.md) | 安卓原生客户端的结构、构建安装、连接配置、功能与限制 |
| [ios/README.md](ios/README.md) | iOS 原生客户端的结构、Xcode 构建、连接配置、功能、静态校验与限制 |

> 目录约定：docs/ 文档、tools/ 开发工具与调试脚本（scripts/ 独立脚本、recover/ 反编译恢复、browser-tests/ 浏览器测试）、logs/ 运行日志与输出、test_suite/ 验收回归测试。data/ 与 mysql-data/ 为运行数据，请勿移动。

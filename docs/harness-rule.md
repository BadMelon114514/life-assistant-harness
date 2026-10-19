# s01–s20 Harness 工程功能逻辑文档

> 主题：**代理能力来自模型，代理产品 = 模型 + Harness（环架）**
> 贯穿始终：**机制很多，循环一个** —— 所有功能都挂在同一个 agent 循环周围，循环本身永不变。

## 核心循环（所有章节的公共基础）

```
用户输入 → messages[] → LLM → 响应
                              │
                    有 tool_use 块吗？
                    /              \
                  是               否
                  │                │
            执行工具            返回文本
           追加结果
           回到 messages[]
```

---

## 阶段一：核心能力（s01–s04）

| 章节 | 标题口号 | 功能逻辑 |
|------|---------|---------|
| **s01** | 一个循环 + Bash 就够 | 最小 Agent：`while True` 循环 + 仅一个 `bash` 工具。调模型 → 查 `stop_reason == "tool_use"` → 执行 → 结果回填 messages |
| **s02** | 加工具就是加 handler | 从 1 扩到多工具。`TOOLS` 定义列表 + `TOOL_HANDLERS` 分发字典。循环不动，新工具注册进字典即可 |
| **s03** | 先设边界，再给自由 | `PermissionRule` 权限系统，工具执行前判定：能跑 / 必须停 / 需审批。安全闸门 |
| **s04** | 挂钩子，不重写循环 | `PreToolUse` / `PostToolUse` / `Stop` 等扩展点。权限、审计、日志都挂钩子，不散落代码里 |

## 阶段二：处理复杂工作（s05、s06、s08）

| 章节 | 标题口号 | 功能逻辑 |
|------|---------|---------|
| **s05** | 没有计划的 Agent 会漂移 | `TodoWrite` 计划先行。`TodoItem`（pending / in_progress / completed），循环带提醒防偏离，完成率提升 |
| **s06** | 大任务拆小，子任务有干净上下文 | 一次性子代理。独立 `messages[]`，上下文隔离，干完侧活只带摘要回来 |
| **s08** | 上下文总会满，要腾地方 | 多层压缩：`toolResultBudget` → `snipCompact` → `microCompact` → `autoCompact` 摘要，支撑无限会话 |

## 阶段三：记住与恢复（s09–s11）

| 章节 | 标题口号 | 功能逻辑 |
|------|---------|---------|
| **s09** | 记住重要的，忘掉不重要的 | 记忆三子系统：**选择**（值得记的）、**提取**（抽取关键）、**整合**（合并进 `MEMORY.md`，下次注入） |
| **s10** | Prompt 运行时组装，不硬编码 | system prompt 分节拼接：身份 + 工具 + workspace + 记忆 + 技能目录，按需加载 |
| **s11** | 错误是重试的起点 | `429` 指数退避、`529` 换 fallback 模型、`max_tokens` 升级、prompt 太长触发 reactive compact |

## 阶段四：跑长任务（s12–s14）

| 章节 | 标题口号 | 功能逻辑 |
|------|---------|---------|
| **s12** | 大目标拆小任务，排序落盘 | 文件任务图（`.tasks/task_*.json`），`TaskRecord` 含 `blockedBy` 依赖，create/claim/complete 生命周期，跨会话持久化 |
| **s13** | 慢操作丢后台，Agent 继续想 | 慢 bash 进 daemon 线程，返回 `bg_id` 占位，完成以 `<task_notification>` 注入下轮。显式请求优先，启发式兜底 |
| **s14** | 按时间表触发，不靠人踢 | cron 四层：调度线程（每秒轮询）→ 队列 → 队列处理器（空闲交付）→ 消费端注入。Durable 写盘跨重启 |

## 阶段五：多 Agent 协作（s15–s18）

| 章节 | 标题口号 | 功能逻辑 |
|------|---------|---------|
| **s15** | 一个搞不定，组队来 | `MessageBus` 文件收件箱（`.jsonl`，发=append，读=读+删）。Lead `spawn_teammate` 启动队友线程，队友干完发 summary 回来 |
| **s16** | 队友之间要有约定 | 结构化请求-响应协议。`ProtocolState` + `pending_requests`，`request_id` 关联请求与响应。关机握手 + 计划审批两种协议；队友引入 idle 循环 |
| **s17** | 自己看板，自己认领 | 生命周期 **WORK → IDLE（轮询 60s）→ SHUTDOWN**。IDLE 每 5s 扫任务板（`scan_unclaimed_tasks`），发现 pending+无 owner+依赖满足的任务自动 `claim` |
| **s18** | 各干各的目录，互不干扰 | `WorktreeRecord` 把任务与独立目录绑定，create_worktree → bash/read/write 自动切目录，避免互相覆盖 |

## 阶段六：扩展与组装（s07、s19、s20）

| 章节 | 标题口号 | 功能逻辑 |
|------|---------|---------|
| **s07** | 按需加载知识 | 技能只在 system prompt 放目录，`load_skill(name)` 按需注入完整内容 |
| **s19** | 能力不够就接 MCP | `connect_mcp(name)` 连接外部 server，`assemble_tool_pool()` 每轮组装内置 + MCP 工具，命名 `mcp__server__tool` |
| **s20** | 机制很多，循环一个 | 终点章：s01–s19 所有机制归位到同一个 `while True`，完整 harness |

---

## 一句话脉络

> **单 Agent 核心（s01–s04）→ 计划/拆解/压缩（s05–s08）→ 记忆/恢复（s09–s11）→ 长任务调度（s12–s14）→ 多 Agent 协作（s15–s18）→ 外部扩展与最终组装（s19–s20）。**

模型负责判断与行动，harness 负责组织环境、工具、权限、记忆、团队与外部能力。

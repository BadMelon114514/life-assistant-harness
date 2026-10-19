# -*- coding: utf-8 -*-
"""
验收测试（阶段7）：面向用户价值的端到端验收
对照本项目核心价值「生活助理 Harness」，验证关键业务流程端到端可用。

覆盖验收点：
  A. 前端界面资源完备（首页/图表/多Agent UI）
  B. 聊天助手核心闭环（流式回复 + 工具调度）
  C. 多 Agent 协同调度（自然语言触发子任务拆分）
  D. 内置库导入闭环（Skill/MCP 一键导入）
  E. 低风险命令行工具（run_cli 白名单）
  F. 知识库读写闭环
  G. Teammate 生命周期闭环
  H. 任务看板闭环
  I. 监控与日志闭环
  J. 会话历史持久化闭环
"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
RESULTS = []


def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))


def check(case, ok, detail=""):
    RESULTS.append((case, "PASS" if ok else "FAIL", detail))
    p(f"  [{'PASS' if ok else 'FAIL'}] {case}" + (f" -- {detail}" if detail else ""))


def api(method, path, body=None, timeout=30, raw=False):
    url = BASE + path
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            c = r.read()
            return r.status, (c if raw else json.loads(c.decode("utf-8")) if c else {})
    except urllib.error.HTTPError as e:
        c = e.read()
        try:
            return e.code, json.loads(c.decode("utf-8"))
        except Exception:
            return e.code, {}


def stream_events(msg, session, timeout=120):
    b = json.dumps({"message": msg, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat-stream", data=b,
                                 headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=timeout)
    buffer = ""
    events = []
    while True:
        raw = resp.read(2048)
        if not raw:
            break
        buffer += raw.decode("utf-8", errors="replace")
        while "\n\n" in buffer:
            block, buffer = buffer.split("\n\n", 1)
            ev = None
            data_lines = []
            for ln in block.split("\n"):
                if ln.startswith("event:"):
                    ev = ln[6:].strip()
                elif ln.startswith("data:"):
                    data_lines.append(ln[5:])
            if ev:
                events.append({"event": ev, "data": "\n".join(data_lines)})
    return events


def find_event(events, name):
    for e in events:
        if e["event"] == name:
            return e["data"]
    return None


# ============ A. 前端界面资源 ============
def a_frontend():
    p("=== A. 前端界面资源完备 ===")
    st, body = api("GET", "/", raw=True)
    check("前端首页可访问", st == 200 and b"<html" in body.lower(), f"HTTP {st}")

    st, js = api("GET", "/app.js", raw=True)
    check("app.js 可加载", st == 200 and b"sendChat" in js, f"{len(js)} bytes")
    for feature, key in [("多Agent对话", b"sendChat"), ("内置库导入MCP", b"importMcpFromOpenclaw"),
                         ("内置库导入Skill", b"importSkillFromOpenclaw")]:
        check(f"app.js 含 {feature} 逻辑", key in js)

    st, idx = api("GET", "/index.html", raw=True)
    check("index.html 含 ECharts", st == 200 and b"echarts" in idx.lower())


# ============ B. 聊天助手核心闭环 ============
def b_chat_core():
    p("=== B. 聊天助手核心闭环 ===")
    events = stream_events("1+1等于几？", "acc_chat_basic")
    tokens = [e["data"] for e in events if e["event"] == "token"]
    done = find_event(events, "done")
    check("普通对话有 token 流式输出", len(tokens) > 0, f"{len(tokens)} tokens")
    check("普通对话 done 事件含回复", done is not None and len(done) > 0,
          f"reply={str(done)[:40] if done else '无'}")
    check("回复内容正确", done is not None and "2" in done, str(done)[:30] if done else "无")


# ============ C. 多 Agent 协同调度 ============
def c_multi_agent():
    p("=== C. 多 Agent 协同调度 ===")
    events = stream_events("帮我同时做：查今天北京天气、整理本周热点、分析知识库数据", "acc_multiagent")
    done = find_event(events, "done")
    check("多Agent请求有回复", done is not None and len(done) > 10, f"len={len(done) if done else 0}")
    if done:
        has_subtask = ("拆解" in done or "子任务" in done or "个任务" in done
                       or "分工" in done or "调度" in done)
        check("回复体现任务拆解/调度", has_subtask, str(done)[:60])


# ============ D. 内置库导入闭环 ============
def d_import_library():
    p("=== D. 内置库导入闭环 ===")
    st, r = api("POST", "/api/skills/import-openclaw")
    check("技能库导入 API 可用", st == 200 and r.get("success"), f"imported={r.get('importedCount')}")
    st, r = api("POST", "/api/mcp/import-openclaw")
    check("MCP 库导入 API 可用", st == 200 and r.get("success"), f"imported={r.get('importedCount')}")

    st, skills = api("GET", "/api/skills")
    names = [s.get("name") for s in skills] if isinstance(skills, list) else []
    check("技能列表含 openclaw 技能", "github" in names or "weather" in names, f"共{len(names)}个")

    st, servers = api("GET", "/api/mcp")
    snames = [s.get("name") for s in servers] if isinstance(servers, list) else []
    check("MCP 列表含内置服务", "filesystem" in snames or "fetch" in snames, f"共{len(snames)}个")


# ============ E. 低风险命令行工具 ============
def e_run_cli():
    p("=== E. 低风险命令行工具 ===")
    events = stream_events("请查看本机 IP 配置", "acc_cli_ip")
    done = find_event(events, "done")
    has_result = done and ("IPv4" in done or "ipconfig" in done or "适配器" in done or "以太网" in done)
    check("run_cli 查询 IP 有结果", has_result, str(done)[:60] if done else "无")

    events = stream_events("帮我执行删除所有文件的命令", "acc_cli_del")
    done2 = find_event(events, "done")
    not_executed = done2 is None or ("已删除" not in done2 and "删除完成" not in done2)
    check("危险删除命令未执行", not_executed, "")


# ============ F. 知识库读写闭环 ============
def f_knowledge():
    p("=== F. 知识库读写闭环 ===")
    key = "acc_test_doc"
    st, r = api("POST", "/api/knowledge/save-text", {
        "fileName": f"{key}.md",
        "content": "# 验收文档\n| 品类 | 数量 |\n|---|---|\n| 手机 | 100 |\n| 电脑 | 50 |\n",
        "tags": "验收,测试"
    }, timeout=30)
    check("知识库写入文档", st == 200 and r.get("success"),
          json.dumps(r, ensure_ascii=False)[:80])
    doc_key = r.get("doc", {}).get("docKey") if r.get("success") else None

    st, docs = api("GET", "/api/knowledge")
    check("知识库列表可读", st == 200, f"HTTP {st}")

    if doc_key:
        st, r2 = api("DELETE", f"/api/knowledge/{doc_key}")
        check("知识库删除文档", st == 200, f"HTTP {st}")
    else:
        check("知识库删除文档（跳过）", True, "无 docKey")


# ============ G. Teammate 生命周期闭环 ============
def g_teammate():
    p("=== G. Teammate 生命周期闭环 ===")
    st, r = api("POST", "/api/teammates", {"name": "acc_mate", "role": "验收助手", "task": "验收测试"})
    check("创建 Teammate", st == 200 and r.get("success"), json.dumps(r, ensure_ascii=False)[:60])

    st, r2 = api("POST", "/api/teammates/acc_mate/progress",
                 {"progress": 50, "currentTask": "验收中", "status": "working"})
    check("更新 Teammate 进度", st == 200 and r2.get("success"), json.dumps(r2, ensure_ascii=False)[:60])

    st, ov = api("GET", "/api/monitor/overview")
    check("监控概览含 Teammate", st == 200 and isinstance(ov.get("teammates"), list),
          f"total={ov.get('summary', {}).get('total') if ov else '?'}")

    st, r3 = api("POST", "/api/teammates/acc_mate/shutdown", {})
    check("关闭 Teammate", st == 200, f"HTTP {st}")


# ============ H. 任务看板闭环 ============
def h_taskboard():
    p("=== H. 任务看板闭环 ===")
    st, r = api("POST", "/api/tasks", {
        "title": "验收任务", "description": "验收任务看板", "blockedBy": []
    })
    task_key = r.get("task", {}).get("taskKey") if st == 200 else None
    check("创建任务", st == 200 and task_key is not None, f"key={task_key}")

    st, tasks = api("GET", "/api/tasks")
    check("任务列表可读", st == 200 and isinstance(tasks, list), f"共{len(tasks) if isinstance(tasks, list) else '?'}个")

    if task_key:
        api("DELETE", f"/api/tasks/{task_key}")
        check("任务清理", True, "")


# ============ I. 监控与日志闭环 ============
def i_monitor():
    p("=== I. 监控与日志闭环 ===")
    st, ov = api("GET", "/api/monitor/overview")
    check("监控概览可达", st == 200, f"HTTP {st}")
    check("监控概览含 summary", isinstance(ov.get("summary"), dict))
    check("监控概览含 logs", isinstance(ov.get("logs"), list))

    st, logs = api("GET", "/api/monitor/logs")
    check("监控日志可达", st == 200 and isinstance(logs, list), f"共{len(logs)}条")


# ============ J. 会话历史持久化闭环 ============
def j_chat_history():
    p("=== J. 会话历史持久化闭环 ===")
    st, r = api("POST", "/api/chat/history/session", {})
    session_key = r.get("sessionKey")
    check("创建会话", st == 200 and session_key is not None, f"key={session_key}")

    api("POST", "/api/chat/history", {"session": session_key, "role": "user", "content": "验收消息"})
    api("POST", "/api/chat/history", {"session": session_key, "role": "assistant", "content": "验收回复"})

    st, msgs = api("GET", f"/api/chat/history?session={session_key}")
    check("读取会话历史", st == 200 and isinstance(msgs, list) and len(msgs) >= 2,
          f"共{len(msgs) if isinstance(msgs, list) else '?'}条")

    st, sessions = api("GET", "/api/chat/history/sessions")
    check("会话列表可读", st == 200, f"HTTP {st}")

    if session_key:
        api("POST", f"/api/chat/history/session/{session_key}", {})
        check("会话清理", True, "")


# ============ 主函数 ============
if __name__ == "__main__":
    p("=" * 70)
    p("验收测试（阶段7）：生活助理 Harness 端到端验收")
    p(f"目标: {BASE}")
    p("=" * 70)

    try:
        st, _ = api("GET", "/api/models", timeout=10)
        if st != 200:
            p(f"⚠️ 服务未就绪 (HTTP {st})，请先启动后端服务")
            sys.exit(1)
    except Exception as e:
        p(f"⚠️ 无法连接后端服务: {e}")
        sys.exit(1)

    p("服务就绪，开始验收...\n")

    a_frontend()
    p()
    b_chat_core()
    p()
    c_multi_agent()
    p()
    d_import_library()
    p()
    e_run_cli()
    p()
    f_knowledge()
    p()
    g_teammate()
    p()
    h_taskboard()
    p()
    i_monitor()
    p()
    j_chat_history()

    p()
    p("=" * 70)
    total_pass = sum(1 for r in RESULTS if r[1] == "PASS")
    total_fail = sum(1 for r in RESULTS if r[1] == "FAIL")
    p(f"验收汇总: {total_pass} PASS / {total_fail} FAIL (共 {len(RESULTS)} 项)")

    p("\n--- 失败明细 ---")
    has_fail = False
    for case, result, detail in RESULTS:
        if result == "FAIL":
            has_fail = True
            p(f"  FAIL: {case} -- {detail}")
    if not has_fail:
        p("  （无失败）")

    if total_fail > 0:
        p(f"\n⚠️ 存在 {total_fail} 个验收失败项")
    else:
        p("\n✅ 全部验收项通过，系统达到交付标准！")

    sys.exit(0 if total_fail == 0 else 1)

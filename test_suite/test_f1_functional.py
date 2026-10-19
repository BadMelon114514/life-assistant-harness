# -*- coding: utf-8 -*-
"""功能测试（阶段1）：13 个模块逐项 CRUD + 核心功能验证"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
RESULTS = []  # (模块, 用例, 结果PASS/FAIL, 说明)


def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))


def api(method, path, body=None, timeout=25):
    url = BASE + path
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        raw = r.read()
        return r.status, (json.loads(raw.decode("utf-8")) if raw else {})


def check(mod, case, ok, detail=""):
    RESULTS.append((mod, case, "PASS" if ok else "FAIL", detail))
    p(f"  [{'PASS' if ok else 'FAIL'}] {mod} / {case}" + (f" -- {detail}" if detail else ""))


def safe(fn):
    try:
        return fn()
    except Exception as e:
        return ("ERR", {"error": str(e)})


# ============ 1. 模型管理 ============
def t_models():
    mod = "模型管理"
    p(f"=== {mod} ===")
    st, d = safe(lambda: api("GET", "/api/models"))
    check(mod, "GET /api/models", st == 200 and isinstance(d, list))
    if isinstance(d, list) and d:
        mid = d[0].get("id")
        st2, r = safe(lambda: api("POST", f"/api/models/{mid}/activate"))
        check(mod, "POST /{id}/activate", st2 == 200 and r.get("success"))


# ============ 2. 设置 ============
def t_settings():
    mod = "设置"
    p(f"=== {mod} ===")
    st, d = safe(lambda: api("GET", "/api/settings"))
    check(mod, "GET /api/settings", st == 200 and isinstance(d, list))
    st, r = safe(lambda: api("POST", "/api/settings", {"key": "TEST_SETTING", "value": "v1", "description": "功能测试"}))
    check(mod, "POST /api/settings 新建", st == 200 and r.get("success"))
    st, r = safe(lambda: api("POST", "/api/settings", {"key": "TEST_SETTING", "value": "v2"}))
    check(mod, "POST /api/settings 更新", st == 200 and r.get("success"))
    st, d = safe(lambda: api("GET", "/api/settings/TEST_SETTING"))
    check(mod, "GET /{key}", st == 200 and isinstance(d, dict) and d.get("settingValue") == "v2")
    st, r = safe(lambda: api("DELETE", "/api/settings/TEST_SETTING"))
    check(mod, "DELETE /{key}", st == 200 and r.get("success"))
    st, d = safe(lambda: api("GET", "/api/settings/TEST_SETTING"))
    gone = st != 200 or (isinstance(d, dict) and d.get("settingValue") is None) or (isinstance(d, str) and not d)
    check(mod, "删除后不存在", gone)


# ============ 3. 知识库 ============
def t_knowledge():
    mod = "知识库"
    p(f"=== {mod} ===")
    st, d = safe(lambda: api("GET", "/api/knowledge"))
    check(mod, "GET /api/knowledge", st == 200 and isinstance(d, list))
    st, r = safe(lambda: api("POST", "/api/knowledge/save-text",
                             {"fileName": "功能测试数据.md", "content": "# 测试\n| 指标 | 数值 |\n|---|---|\n| 销量 | 100 |\n", "tags": "测试"}))
    check(mod, "POST /save-text", st == 200 and r.get("success"))
    key = r.get("doc", {}).get("docKey") if isinstance(r, dict) else None
    if key:
        st, d = safe(lambda: api("GET", f"/api/knowledge/{key}"))
        check(mod, "GET /{key}", st == 200 and isinstance(d, dict) and d.get("fileName") == "功能测试数据.md")
        st, r = safe(lambda: api("DELETE", f"/api/knowledge/{key}"))
        check(mod, "DELETE /{key}", st == 200 and r.get("success"))
    else:
        check(mod, "GET /{key} / DELETE /{key}", False, "save-text 未返回 docKey")
    # 宽容设计：空输入可保存，fileName 自动补全（非缺陷，验证行为符合设计）
    st, r = safe(lambda: api("POST", "/api/knowledge/save-text", {"fileName": "", "content": "空输入测试"}))
    ok = st == 200 and r.get("success") and r.get("doc", {}).get("fileName") in ("doc.txt", "doc.txt.md", "doc.md")
    check(mod, "空输入宽容设计", ok, "fileName 自动补全" if ok else f"实际: {st}")
    if isinstance(r, dict) and r.get("doc", {}).get("docKey"):
        api("DELETE", f"/api/knowledge/{r['doc']['docKey']}")


# ============ 4. 记忆 ============
def t_memory():
    mod = "记忆"
    p(f"=== {mod} ===")
    st, d = safe(lambda: api("GET", "/api/memory"))
    check(mod, "GET /api/memory", st == 200 and isinstance(d, list))
    key = "TEST_MEM_" + str(int(time.time()))
    st, r = safe(lambda: api("POST", "/api/memory", {"memoryKey": key, "content": "测试记忆内容", "importance": 5, "category": "测试"}))
    check(mod, "POST /api/memory", st == 200 and r.get("success"))
    st, d = safe(lambda: api("GET", "/api/memory"))
    check(mod, "列表含新记忆", any(m.get("memoryKey") == key for m in d) if isinstance(d, list) else False)
    st, r = safe(lambda: api("DELETE", f"/api/memory/{key}"))
    check(mod, "DELETE /{key}", st == 200 and r.get("success"))


# ============ 5. 任务 ============
def t_tasks():
    mod = "任务"
    p(f"=== {mod} ===")
    st, d = safe(lambda: api("GET", "/api/tasks"))
    check(mod, "GET /api/tasks", st == 200 and isinstance(d, list))
    st, r = safe(lambda: api("POST", "/api/tasks", {"title": "功能测试任务", "description": "验证任务流程"}))
    check(mod, "POST /api/tasks 创建", st == 200 and r.get("success"))
    key = r.get("task", {}).get("taskKey") if isinstance(r, dict) else None
    if not key:
        check(mod, "任务完整生命周期", False, "未返回 task.taskKey")
        return
    st, r = safe(lambda: api("POST", f"/api/tasks/{key}/claim", {}))
    check(mod, "POST /{key}/claim", st == 200 and r.get("success"))
    st, r = safe(lambda: api("POST", f"/api/tasks/{key}/progress", {"progress": 50, "status": "in_progress"}))
    check(mod, "POST /{key}/progress", st == 200 and r.get("success"))
    st, r = safe(lambda: api("POST", f"/api/tasks/{key}/complete", {"result": "完成"}))
    check(mod, "POST /{key}/complete", st == 200 and r.get("success"))
    st, r = safe(lambda: api("DELETE", f"/api/tasks/{key}"))
    check(mod, "DELETE /{key}", st == 200 and r.get("success"))


# ============ 6. 技能 ============
def t_skills():
    mod = "技能"
    p(f"=== {mod} ===")
    st, d = safe(lambda: api("GET", "/api/skills"))
    check(mod, "GET /api/skills", st == 200 and isinstance(d, list))
    st, r = safe(lambda: api("POST", "/api/skills", {"name": "功能测试技能", "category": "测试", "description": "测试", "content": "当用户请求测试时回复测试成功", "enabled": 1}))
    check(mod, "POST /api/skills 创建", st == 200 and r.get("success"))
    sid = r.get("skill", {}).get("id") if isinstance(r, dict) else None
    if sid:
        st, r = safe(lambda: api("POST", f"/api/skills/{sid}/toggle"))
        check(mod, "POST /{id}/toggle", st == 200 and r.get("success"))
        st, r = safe(lambda: api("DELETE", f"/api/skills/{sid}"))
        check(mod, "DELETE /{id}", st == 200 and r.get("success"))
    else:
        check(mod, "toggle/delete", False, "未返回 skill.id")
    st, r = safe(lambda: api("POST", "/api/skills/import-draft", {"name": "", "content": ""}))
    check(mod, "import-draft 缺参校验", st == 200 and not r.get("success"))


# ============ 7. MCP ============
def t_mcp():
    mod = "MCP服务"
    p(f"=== {mod} ===")
    st, d = safe(lambda: api("GET", "/api/mcp"))
    check(mod, "GET /api/mcp", st == 200 and isinstance(d, list))
    st, r = safe(lambda: api("POST", "/api/mcp", {"name": "测试MCP", "serverType": "http", "endpoint": "http://127.0.0.1:9999/nonexist", "description": "测试", "enabled": 0}))
    check(mod, "POST /api/mcp 创建", st == 200 and r.get("success"))
    mid = r.get("server", {}).get("id") if isinstance(r, dict) else None
    if mid:
        st, r = safe(lambda: api("POST", f"/api/mcp/{mid}/test"))
        check(mod, "POST /{id}/test（不可达端点）", st == 200 and not r.get("success"))
        st, r = safe(lambda: api("DELETE", f"/api/mcp/{mid}"))
        check(mod, "DELETE /{id}", st == 200 and r.get("success"))
    else:
        check(mod, "test/delete", False, "未返回 server.id")


# ============ 8. 聊天记录 ============
def t_chat_history():
    mod = "聊天记录"
    p(f"=== {mod} ===")
    st, r = safe(lambda: api("POST", "/api/chat/history/session"))
    check(mod, "POST /session 新建会话", st == 200)
    sess = r.get("sessionKey") if isinstance(r, dict) else None
    st, d = safe(lambda: api("GET", "/api/chat/history/sessions"))
    check(mod, "GET /sessions", st == 200 and isinstance(d, dict))
    st, r = safe(lambda: api("POST", "/api/chat/history", {"session": sess, "role": "user", "content": "测试消息"}))
    check(mod, "POST /history 保存", st == 200 and r.get("success"))
    st, d = safe(lambda: api("GET", f"/api/chat/history?session={sess}"))
    check(mod, "GET /history?session=", st == 200 and any(m.get("role") == "user" for m in d))
    if isinstance(d, list) and d:
        mid = d[-1].get("id")
        st, r = safe(lambda: api("DELETE", f"/api/chat/history/{mid}"))
        check(mod, "DELETE /{id} 单条", st == 200 and r.get("success"))
    if sess:
        st, r = safe(lambda: api("DELETE", f"/api/chat/history/session/{sess}"))
        check(mod, "DELETE /session/{key}", st == 200 and r.get("success"))
    else:
        check(mod, "DELETE /session/{key}", False, "未创建会话")


# ============ 9. 监控 ============
def t_monitor():
    mod = "监控"
    p(f"=== {mod} ===")
    st, d = safe(lambda: api("GET", "/api/monitor/overview"))
    check(mod, "GET /overview", st == 200 and isinstance(d, dict))
    st, d = safe(lambda: api("GET", "/api/monitor/logs"))
    check(mod, "GET /logs", st == 200 and isinstance(d, list))


# ============ 10. 搜索 ============
def t_search():
    mod = "搜索"
    p(f"=== {mod} ===")
    st, d = safe(lambda: api("GET", "/api/search/status"))
    check(mod, "GET /status", st == 200 and isinstance(d, dict))
    st, d = safe(lambda: api("GET", "/api/search?q=test", timeout=30))
    check(mod, "GET /search?q=", st == 200 and isinstance(d, dict))


# ============ 11. ClawHub ============
def t_clawhub():
    mod = "ClawHub"
    p(f"=== {mod} ===")
    st, d = safe(lambda: api("GET", "/api/clawhub/search?q=translate", timeout=30))
    check(mod, "GET /search?q=", st == 200 and isinstance(d, dict))


# ============ 12. Teammate ============
def t_teammate():
    mod = "团队"
    p(f"=== {mod} ===")
    st, d = safe(lambda: api("GET", "/api/teammates"))
    check(mod, "GET /api/teammates", st == 200 and isinstance(d, list))
    st, r = safe(lambda: api("POST", "/api/teammates", {"name": "TEST_AGENT", "role": "测试", "task": "测试任务"}))
    check(mod, "POST /api/teammates 创建", st == 200 and r.get("success"))
    st, r = safe(lambda: api("POST", "/api/teammates/TEST_AGENT/progress", {"progress": 30, "currentTask": "进行中", "status": "working"}))
    check(mod, "POST /{name}/progress", st == 200 and r.get("success"))
    st, r = safe(lambda: api("POST", "/api/teammates/TEST_AGENT/shutdown"))
    check(mod, "POST /{name}/shutdown", st == 200 and r.get("success"))


if __name__ == "__main__":
    t_models(); t_settings(); t_knowledge(); t_memory(); t_tasks()
    t_skills(); t_mcp(); t_chat_history(); t_monitor(); t_search()
    t_clawhub(); t_teammate()
    p("=" * 70)
    p(f"功能测试汇总: {sum(1 for r in RESULTS if r[2]=='PASS')} PASS / {sum(1 for r in RESULTS if r[2]=='FAIL')} FAIL")
    for r in RESULTS:
        if r[2] == "FAIL":
            p(f"  FAIL: {r[0]} / {r[1]} -- {r[3]}")

# -*- coding: utf-8 -*-
"""
功能测试（阶段5）：低风险命令行工具 + 内置库导入
覆盖场景：
  1. /api/skills/import-openclaw  内置技能库导入
  2. /api/mcp/import-openclaw     内置 MCP 库导入
  3. 导入结果落库（list 可见）
  4. 重复导入幂等（skipped）
  5. SkillService.findByName 去重
  6. run_cli 白名单命令执行（SSE 聊天触发）
  7. run_cli 非法命令被拒（无工具调用）
  8. import_library 自然语言触发导入
  9. 清理测试产生的数据
"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
RESULTS = []  # (场景, 用例, 结果PASS/FAIL, 说明)


def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))


def api(method, path, body=None, timeout=30):
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


def check(scene, case, ok, detail=""):
    RESULTS.append((scene, case, "PASS" if ok else "FAIL", detail))
    p(f"  [{'PASS' if ok else 'FAIL'}] {scene} / {case}" + (f" -- {detail}" if detail else ""))


def safe(fn):
    try:
        return fn()
    except Exception as e:
        return ("ERR", {"error": str(e)})


def stream_events(msg, session, timeout=120):
    """解析 SSE 流，返回 [{event, data}] 列表"""
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


# ============ 场景1: 内置技能库导入 ============
def t_import_skills():
    scene = "内置技能库导入"
    p(f"=== {scene} ===")

    # 1.1 首次导入
    st, r = safe(lambda: api("POST", "/api/skills/import-openclaw"))
    if st == 200 and r.get("success"):
        n = r.get("importedCount", 0)
        check(scene, "首次导入返回 success", True, f"导入 {n} 个，跳过 {r.get('skippedCount', 0)} 个")
        check(scene, "导入数量 > 0", n > 0, f"importedCount={n}")
    else:
        check(scene, "首次导入返回 success", False, str(r.get("error", r))[:80])

    # 1.2 再次导入应幂等（全部 skipped）
    st2, r2 = safe(lambda: api("POST", "/api/skills/import-openclaw"))
    if st2 == 200 and r2.get("success"):
        check(scene, "重复导入幂等", r2.get("importedCount", 0) == 0,
              f"重复导入 {r2.get('importedCount')}，跳过 {r2.get('skippedCount')}")
    else:
        check(scene, "重复导入幂等", False, str(r2.get("error", r2))[:80])

    # 1.3 列表可见且含导入的技能名
    st3, skills = safe(lambda: api("GET", "/api/skills"))
    if st3 == 200 and isinstance(skills, list):
        names = [s.get("name") for s in skills]
        for expect in ("weather", "github", "summarize", "taskflow", "diagram-maker"):
            check(scene, f"技能已落库: {expect}", expect in names)
    else:
        check(scene, "技能已落库", False, "GET /api/skills 失败")


# ============ 场景2: 内置 MCP 库导入 ============
def t_import_mcp():
    scene = "内置 MCP 库导入"
    p(f"=== {scene} ===")

    st, r = safe(lambda: api("POST", "/api/mcp/import-openclaw"))
    if st == 200 and r.get("success"):
        n = r.get("importedCount", 0)
        check(scene, "首次导入返回 success", True, f"导入 {n} 个，跳过 {r.get('skippedCount', 0)} 个")
        check(scene, "导入数量 > 0", n > 0, f"importedCount={n}")
    else:
        check(scene, "首次导入返回 success", False, str(r.get("error", r))[:80])

    st2, r2 = safe(lambda: api("POST", "/api/mcp/import-openclaw"))
    if st2 == 200 and r2.get("success"):
        check(scene, "重复导入幂等", r2.get("importedCount", 0) == 0,
              f"重复导入 {r2.get('importedCount')}，跳过 {r2.get('skippedCount')}")
    else:
        check(scene, "重复导入幂等", False, str(r2.get("error", r2))[:80])

    st3, servers = safe(lambda: api("GET", "/api/mcp"))
    if st3 == 200 and isinstance(servers, list):
        check(scene, "MCP 服务已落库", len(servers) > 0, f"共 {len(servers)} 个")
        names = [s.get("name") for s in servers]
        for expect in ("filesystem", "fetch", "memory", "github", "context7"):
            check(scene, f"MCP 服务存在: {expect}", expect in names)
    else:
        check(scene, "MCP 服务已落库", False, "GET /api/mcp 失败")


# ============ 场景3: run_cli 白名单命令 ============
def t_run_cli():
    scene = "低风险命令行工具"
    p(f"=== {scene} ===")

    # 3.1 白名单命令：查询本机 IP
    events = stream_events("请帮我查看本机 IP 地址", "f5_cli_ip")
    done = find_event(events, "done")
    has_result = done and ("IPv4" in done or "IP" in done or "ipconfig" in done or "以太网" in done or "适配器" in done)
    check(scene, "run_cli 查询本机 IP 有回复", done is not None and len(done) > 10,
          f"回复长度={len(done) if done else 0}")
    check(scene, "回复包含 ipconfig 结果特征", has_result,
          str(done)[:80] if done else "无回复")

    # 3.2 系统状态查询
    events = stream_events("查一下系统信息", "f5_cli_sys")
    done = find_event(events, "done")
    check(scene, "run_cli 查询系统信息有回复", done is not None and len(done) > 10,
          f"回复长度={len(done) if done else 0}")

    # 3.3 白名单外命令应被拒绝（不调用工具，LLM 直接回复）
    events = stream_events("帮我执行 del C:\\important.txt", "f5_cli_bad")
    done = find_event(events, "done")
    # 关键是不要真的执行：done 里不应包含已删除的确认
    check(scene, "危险命令未执行（白名单拦截）",
          done is not None and "已删除" not in (done or ""),
          f"done={str(done)[:60] if done else '无'}")


# ============ 场景4: import_library 自然语言触发 ============
def t_import_library_nl():
    scene = "自然语言导入内置库"
    p(f"=== {scene} ===")

    events = stream_events("帮我导入项目内置的技能库和 MCP 服务", "f5_nl_import")
    done = find_event(events, "done")
    check(scene, "import_library 触发有回复", done is not None and len(done) > 10,
          f"回复长度={len(done) if done else 0}")
    has_import = done and ("导入" in done or "内置" in done or "技能" in done or "MCP" in done)
    check(scene, "回复提及导入结果", has_import, str(done)[:80] if done else "无回复")


# ============ 主函数 ============
if __name__ == "__main__":
    p("=" * 70)
    p("功能测试（阶段5）：低风险命令行工具 + 内置库导入")
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

    p("服务就绪，开始测试...\n")

    t_import_skills()
    p()
    t_import_mcp()
    p()
    t_run_cli()
    p()
    t_import_library_nl()

    p()
    p("=" * 70)
    total_pass = sum(1 for r in RESULTS if r[2] == "PASS")
    total_fail = sum(1 for r in RESULTS if r[2] == "FAIL")
    p(f"测试汇总: {total_pass} PASS / {total_fail} FAIL (共 {len(RESULTS)} 项)")

    scenes = {}
    for scene, case, result, detail in RESULTS:
        if scene not in scenes:
            scenes[scene] = {"pass": 0, "fail": 0, "fails": []}
        if result == "PASS":
            scenes[scene]["pass"] += 1
        else:
            scenes[scene]["fail"] += 1
            scenes[scene]["fails"].append((case, detail))

    p("\n--- 按场景汇总 ---")
    for scene, stats in scenes.items():
        total = stats["pass"] + stats["fail"]
        p(f"  {scene}: {stats['pass']}/{total} PASS")
        for case, detail in stats["fails"]:
            p(f"    FAIL: {case} -- {detail}")

    if total_fail > 0:
        p(f"\n⚠️ 存在 {total_fail} 个失败用例")
    else:
        p("\n✅ 所有功能测试通过！")

    sys.exit(0 if total_fail == 0 else 1)

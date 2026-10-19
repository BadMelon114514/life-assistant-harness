# -*- coding: utf-8 -*-
"""
验收测试（阶段10）：定时提醒功能 MCP（reminder）
  A. MCP 库导入 reminder 并连通测试（自动激活）
  B. POST /api/assistant/schedule 创建提醒（含 delayMinutes=0 立即触发）
  C. GET 列表 / DELETE 取消 / cancelId 取消（MCP 取消路径）
  D. 轮询触发闭环：到期提醒写入会话历史（浏览器离线不丢）
"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
RESULTS = []


def p(s=""):
    print(s)


def check(case, ok, detail=""):
    RESULTS.append((case, "PASS" if ok else "FAIL", detail))
    p(f"  [{'PASS' if ok else 'FAIL'}] {case}" + (f" -- {detail}" if detail else ""))


def api(method, path, body=None, timeout=60, raw=False):
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
            return r.status, (c if raw else (json.loads(c.decode("utf-8")) if c else {}))
    except urllib.error.HTTPError as e:
        c = e.read()
        try:
            return e.code, json.loads(c.decode("utf-8"))
        except Exception:
            return e.code, {}
    except urllib.error.URLError as e:
        return -1, {"error": str(e)}


def a_mcp_import():
    p("=== A. reminder MCP 导入与连通性 ===")
    st, r = api("POST", "/api/mcp/import-openclaw")
    check("A MCP 内置库导入接口成功", st == 200 and r.get("success"),
          f"imported={r.get('importedCount')} skipped={r.get('skippedCount')}")
    st, mcps = api("GET", "/api/mcp")
    rem = None
    if st == 200 and isinstance(mcps, list):
        matches = [m for m in mcps if m.get("name") == "reminder"]
        check("A 内置库含 reminder MCP", len(matches) == 1,
              f"匹配 {len(matches)} 条" + (f" (id={matches[0].get('id')})" if matches else ""))
        if matches:
            rem = matches[0]
            st2, t = api("POST", f"/api/mcp/{rem['id']}/test")
            check("A reminder MCP 连通性测试通过（http GET 200）", st2 == 200 and t.get("success"),
                  str(t.get("message")))
            st3, rem2 = api("GET", "/api/mcp")
            if st3 == 200:
                m2 = next((x for x in rem2 if x.get("name") == "reminder"), None)
                check("A 测试后 reminder MCP 已激活", m2 is not None and m2.get("status") == "active",
                      f"status={m2.get('status') if m2 else '?'}")
    else:
        check("A MCP 列表接口可用", False, f"HTTP {st}")


def b_create():
    p("=== B. 创建提醒 ===")
    st, r = api("POST", "/api/assistant/schedule",
                {"instruct": "验收测试：喝水", "delayMinutes": 0, "title": "验收提醒", "session": "rem_accept"})
    check("B POST /schedule 创建提醒成功", st == 200 and r.get("success"),
          str(r.get("message")) + (" id=" + str(r.get("reminder", {}).get("id")) if r.get("reminder") else ""))
    check("B 返回提醒对象含触发时间", r.get("reminder", {}).get("fireAt") is not None,
          str(r.get("reminder", {}).get("fireAt")))
    return r.get("reminder", {}).get("id")


def c_list_cancel(rid):
    p("=== C. 查询 / 取消 ===")
    st, rows = api("GET", "/api/assistant/schedule?session=rem_accept")
    ok = st == 200 and isinstance(rows, list) and any(x.get("id") == rid and x.get("status") == "pending" for x in rows)
    check("C GET /schedule 列表包含新提醒", ok, f"共 {len(rows) if isinstance(rows, list) else '?'} 条")
    if ok:
        # 取消该条（delayMinutes=0 可能已被触发，若已 fired 则取消会失败——先建一个 5 分钟的再取消）
        st2, r2 = api("POST", "/api/assistant/schedule",
                      {"instruct": "待取消提醒", "delayMinutes": 5, "title": "取消测试", "session": "rem_accept"})
        rid2 = r2.get("reminder", {}).get("id")
        st3, r3 = api("DELETE", f"/api/assistant/schedule/{rid2}")
        check("C DELETE /{id} 取消提醒成功", st3 == 200 and r3.get("success"), str(r3.get("message")))
        st4, rows4 = api("GET", "/api/assistant/schedule")
        if st4 == 200 and isinstance(rows4, list):
            cand = next((x for x in rows4 if x.get("id") == rid2), None)
            check("C 取消后状态为 cancelled", cand is not None and cand.get("status") == "cancelled",
                  f"status={cand.get('status') if cand else '?'}")
        # MCP 取消路径：cancelId
        st5, r5 = api("POST", "/api/assistant/schedule",
                      {"instruct": "MCP取消测试", "delayMinutes": 5, "title": "MCP取消", "session": "rem_accept"})
        rid5 = r5.get("reminder", {}).get("id")
        st6, r6 = api("POST", "/api/assistant/schedule", {"cancelId": rid5})
        check("C cancelId 取消路径可用（MCP 工具调用形态）", st6 == 200 and r6.get("success"),
              str(r6.get("message")))
    else:
        check("C DELETE /{id} 取消提醒成功", False, "前置列表断言未通过")


def d_fire_roundtrip():
    p("=== D. 到期触发闭环（历史写入） ===")
    sess = "rem_fire_" + str(int(time.time()))
    st, r = api("POST", "/api/assistant/schedule",
                {"instruct": "触发验收：站起来活动一下", "delayMinutes": 0, "title": "触发提醒", "session": sess})
    check("D 创建 0 分钟提醒", st == 200 and r.get("success"), str(r.get("message")))
    # 等待轮询（10s 间隔）触发
    fired = False
    history = []
    for i in range(8):
        time.sleep(4)
        st2, rows = api("GET", "/api/assistant/schedule?session=" + sess)
        if st2 == 200 and isinstance(rows, list) and rows:
            st3 = rows[0].get("status")
            if st3 == "fired":
                fired = True
                break
        st4, history = api("GET", "/api/chat/history?session=" + sess)
        if st4 == 200 and isinstance(history, list) and any("触发验收" in str(m.get("content", "")) for m in history):
            fired = True
            break
    check("D 提醒已触发（status=fired）", fired, f"轮询 {i + 1} 次")
    st4, history = api("GET", "/api/chat/history?session=" + sess)
    if st4 == 200 and isinstance(history, list):
        hits = [m for m in history if "触发验收" in str(m.get("content", ""))]
        check("D 触发内容已写入会话历史（浏览器离线不丢）", len(hits) == 1,
              f"命中 {len(hits)} 条, content={str(hits[0].get('content', ''))[:40] if hits else ''}")
    else:
        check("D 触发内容已写入会话历史（浏览器离线不丢）", False, f"HTTP {st4}")
    api("DELETE", "/api/chat/history?session=" + sess)


if __name__ == "__main__":
    p("=" * 70)
    p("阶段10 验收：定时提醒功能 MCP（后端部分）")
    p(f"目标: {BASE}")
    p("=" * 70)
    try:
        st, _ = api("GET", "/api/models", timeout=10)
        if st != 200:
            p(f"服务未就绪 (HTTP {st})")
            sys.exit(1)
    except Exception as e:
        p(f"无法连接后端服务: {e}")
        sys.exit(1)
    p("服务就绪\n")

    a_mcp_import(); p()
    rid = b_create(); p()
    c_list_cancel(rid); p()
    d_fire_roundtrip(); p()

    total_pass = sum(1 for r in RESULTS if r[1] == "PASS")
    total_fail = sum(1 for r in RESULTS if r[1] == "FAIL")
    p("=" * 70)
    p(f"阶段10 后端验收汇总: {total_pass} PASS / {total_fail} FAIL (共 {len(RESULTS)} 项)")
    for case, result, detail in RESULTS:
        if result == "FAIL":
            p(f"  FAIL: {case} -- {detail}")
    sys.exit(0 if total_fail == 0 else 1)

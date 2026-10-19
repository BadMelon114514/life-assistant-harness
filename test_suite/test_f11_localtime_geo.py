# -*- coding: utf-8 -*-
"""
验收测试（阶段11）：本机时间 + 地理位置 MCP（local-geo）
  A. MCP 库导入 local-geo（无重复）并连通性测试（自动激活）
  B. GET /api/assistant/location：时间字段齐全且准确；定位成功或优雅降级
  C. MCP 工具调度路径（executeConfirmed → callMcp）返回时间/定位信息
  D. 对话冒烟：询问本机时间与位置（LLM 经 local-geo 工具调度）
"""
import json
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime

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
    p("=== A. local-geo MCP 导入与连通性 ===")
    st, r = api("POST", "/api/mcp/import-openclaw")
    check("A MCP 内置库导入接口成功", st == 200 and r.get("success"),
          f"imported={r.get('importedCount')} skipped={r.get('skippedCount')}")
    st, mcps = api("GET", "/api/mcp")
    matches = [m for m in mcps if m.get("name") == "local-geo"] if st == 200 and isinstance(mcps, list) else []
    check("A 内置库含 local-geo MCP", len(matches) == 1, f"匹配 {len(matches)} 条")
    if matches:
        mid = matches[0]["id"]
        st2, t = api("POST", f"/api/mcp/{mid}/test")
        check("A local-geo 连通性测试通过（http GET 200）", st2 == 200 and t.get("success"), str(t.get("message")))
        st3, mcps2 = api("GET", "/api/mcp")
        m2 = next((x for x in mcps2 if x.get("name") == "local-geo"), None)
        check("A 测试后 local-geo 已激活", m2 is not None and m2.get("status") == "active",
              f"status={m2.get('status') if m2 else '?'}")


def b_endpoint():
    p("=== B. 本机时间 + 定位端点 ===")
    st, info = api("GET", "/api/assistant/location")
    check("B GET /api/assistant/location 可用", st == 200 and info.get("success"), f"HTTP {st}")
    tm = info.get("time") or {}
    check("B 时间字段齐全（日期/星期/时区/时间戳）",
          all(tm.get(k) for k in ("localTime", "date", "weekday", "timezone", "timestamp")),
          f"timeText={tm.get('timeText')} tz={tm.get('timezone')}")
    if tm.get("localTime"):
        try:
            dt = datetime.fromisoformat(tm["localTime"][:19])
            diff = abs((datetime.now() - dt).total_seconds())
            check("B 本机时间准确（与当前时间差 < 120s）", diff < 120, f"差 {diff:.0f}s")
        except Exception as e:
            check("B 本机时间准确", False, str(e))
    loc = info.get("location")
    if loc:
        check("B 定位成功（含 IP 与城市）",
              bool(loc.get("ip")) and bool(loc.get("city") or loc.get("region") or loc.get("country")),
              f"provider={loc.get('provider')} ip={loc.get('ip')} {loc.get('country')} {loc.get('region')} {loc.get('city')} "
              f"({loc.get('latitude')},{loc.get('longitude')})")
    else:
        check("B 定位失败时优雅降级（返回 locationError 且时间不受影响）",
              bool(info.get("locationError")), str(info.get("locationError"))[:80])


def c_mcp_call():
    p("=== C. MCP 工具调度路径 ===")
    st, r = api("POST", "/api/assistant/confirm",
                {"endpoint": "http://127.0.0.1:8080/api/assistant/location", "args": "{}"})
    result = str(r.get("result", ""))
    check("C callMcp 调用成功返回结果", st == 200 and r.get("success") and result, f"HTTP {st}")
    check("C 结果含本机时间信息", "time" in result or "timeText" in result or "时间" in result, result[:60])
    check("C 结果含定位信息", "location" in result or "city" in result or "定位" in result or "ip" in result, result[:60])


def d_chat_smoke():
    p("=== D. 对话冒烟（LLM 经 local-geo 调度） ===")
    b = json.dumps({"message": "现在几点了？我在哪个城市？", "session": "geo_smoke"}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat-stream", data=b,
                                 headers={"Content-Type": "application/json"})
    try:
        resp = urllib.request.urlopen(req, timeout=120)
    except Exception as e:
        check("D 对话冒烟", False, f"异常 {str(e)[:60]}")
        return
    buf = ""
    tokens = []
    done = ""
    while True:
        raw = resp.read(2048)
        if not raw:
            break
        buf += raw.decode("utf-8", errors="replace")
        while "\n\n" in buf:
            block, buf = buf.split("\n\n", 1)
            ev = None
            data_lines = []
            for ln in block.split("\n"):
                if ln.startswith("event:"):
                    ev = ln[6:].strip()
                elif ln.startswith("data:"):
                    data_lines.append(ln[5:])
            if ev == "token":
                tokens.append("\n".join(data_lines))
            elif ev == "done":
                done = "\n".join(data_lines)
    joined = "".join(tokens)
    reply = done if len(done) >= len(joined) else joined
    has_time = bool(re.search(r"\d{1,2}[:：点]\d{0,2}", reply)) or "时间" in reply
    has_loc = any(w in reply for w in ("城市", "位于", "位置", "坐标", "郑州", "河南", "区"))
    check("D 回复包含本机时间", has_time, reply[:80].replace("\n", " "))
    check("D 回复包含定位信息", has_loc, reply[:80].replace("\n", " "))
    api("DELETE", "/api/chat/history?session=geo_smoke")


if __name__ == "__main__":
    p("=" * 70)
    p("阶段11 验收：本机时间 + 地理位置 MCP（后端部分）")
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
    b_endpoint(); p()
    c_mcp_call(); p()
    d_chat_smoke(); p()

    total_pass = sum(1 for r in RESULTS if r[1] == "PASS")
    total_fail = sum(1 for r in RESULTS if r[1] == "FAIL")
    p("=" * 70)
    p(f"阶段11 后端验收汇总: {total_pass} PASS / {total_fail} FAIL (共 {len(RESULTS)} 项)")
    for case, result, detail in RESULTS:
        if result == "FAIL":
            p(f"  FAIL: {case} -- {detail}")
    sys.exit(0 if total_fail == 0 else 1)

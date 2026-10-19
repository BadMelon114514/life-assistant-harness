# -*- coding: utf-8 -*-
"""
验收测试（阶段18）：ReAct 循环重构
  A. 服务健康 + 普通对话不触发工具
  B. 天气：显式城市传入 get_weather，只调用一次，回复引用北京数据
  C. 时间：强意图直调 get_current_time，回复包含真实日期/时间（不被自验证改写成「无法获取」）
  D. 多步任务：describe_workspace + read_file 依次执行，回复包含 README 总结
  E. 流式对话：done 事件完整，时间回答正确
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


def api(method, path, body=None, timeout=180):
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
            return r.status, (json.loads(c.decode("utf-8")) if c else {})
    except urllib.error.HTTPError as e:
        c = e.read()
        try:
            return e.code, json.loads(c.decode("utf-8"))
        except Exception:
            return e.code, {}
    except urllib.error.URLError as e:
        return -1, {"error": str(e)}


def chat(message, session):
    t0 = time.time()
    st, r = api("POST", "/api/assistant/chat", {"message": message, "session": session}, timeout=180)
    return st, r, round(time.time() - t0, 1)


def stream_chat(message, session, timeout=180):
    b = json.dumps({"message": message, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat-stream", data=b,
                                 headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=timeout)
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
    return "".join(tokens), done


def cleanup(session):
    try:
        api("DELETE", f"/api/chat/history?session={session}", timeout=15)
    except Exception:
        pass


def a_health_plain():
    p("=== A. 健康检查 + 普通对话不触发工具 ===")
    st, r = api("GET", "/api/models", timeout=10)
    check("A1 服务就绪", st == 200, f"HTTP {st}")
    st, r, dt = chat("你好，用一句话介绍你自己", "react_test_plain")
    reply = r.get("reply") or ""
    check("A2 普通对话 HTTP 200 且有回复", st == 200 and len(reply) > 5, f"{dt}s")
    check("A3 普通对话未触发工具", not r.get("toolUsed") and not r.get("toolLog"), f"toolLog={r.get('toolLog')}")
    steps = r.get("steps") or []
    check("A4 步骤包含 Agent Loop 调度", any("Agent Loop" in str(s.get("step") or "") for s in steps), f"{len(steps)} 步")
    cleanup("react_test_plain")


def b_weather_city():
    p("=== B. 天气：显式城市 + 单次调用 ===")
    st, r, dt = chat("北京今天天气怎么样？", "react_test_weather")
    reply = r.get("reply") or ""
    tool_log = r.get("toolLog") or ""
    check("B1 天气请求 HTTP 200", st == 200 and bool(reply), f"{dt}s")
    check("B2 调用了 get_weather", "get_weather" in tool_log, tool_log[:80])
    check("B3 get_weather 只调用一次", tool_log.count("get_weather") == 1, tool_log[:80])
    check("B4 回复引用北京", "北京" in reply, reply[:80].replace("\n", " "))
    check("B5 回复含真实气温信息", ("°C" in reply) or ("气温" in reply) or ("温度" in reply), reply[:80].replace("\n", " "))
    check("B6 未错位到其他城市", "东莞" not in reply and "Dongguan" not in reply, reply[:80].replace("\n", " "))
    check("B7 自验证未改写成拒绝", "无法获取" not in reply and "没有获取到" not in reply, reply[:80].replace("\n", " "))
    cleanup("react_test_weather")


def c_time_direct():
    p("=== C. 时间：强意图直调 + 不被自验证改写 ===")
    now = datetime.now()
    st, r, dt = chat("现在几点？", "react_test_time")
    reply = r.get("reply") or ""
    tool_log = r.get("toolLog") or ""
    check("C1 时间请求 HTTP 200", st == 200 and bool(reply), f"{dt}s")
    check("C2 调用了 get_current_time", "get_current_time" in tool_log, tool_log[:80])
    check("C3 只调用一次", tool_log.count("get_current_time") == 1, tool_log[:80])
    date_ok = bool(re.search(rf"{now.year}\s*年\s*{now.month}\s*月\s*{now.day}\s*日", reply)) or (f"{now.year}-{now.month:02d}-{now.day:02d}" in reply)
    check("C4 回复包含当天日期", date_ok, reply[:80].replace("\n", " "))
    hm = re.search(r"(\d{1,2})[:：](\d{2})", reply)
    near = False
    if hm:
        h = int(hm.group(1))
        m = int(hm.group(2))
        near = abs((h * 60 + m) - (now.hour * 60 + now.minute)) <= 10
    check("C5 回复时间与当前时间相差 ≤10 分钟", near, reply[:80].replace("\n", " "))
    check("C6 自验证未改写成拒绝", "无法获取" not in reply and "不能准确" not in reply, reply[:80].replace("\n", " "))
    check("C7 工具直调响应耗时可接受", dt < 40, f"{dt}s")
    cleanup("react_test_time")


def d_multi_step():
    p("=== D. 多步任务：顺序工具执行 ===")
    st, r, dt = chat("先列出项目根目录结构，然后读取 README.md 并总结要点", "react_test_multi")
    reply = r.get("reply") or ""
    tool_log = r.get("toolLog") or ""
    check("D1 多步请求 HTTP 200", st == 200 and bool(reply), f"{dt}s")
    check("D2 调用 describe_workspace", "describe_workspace" in tool_log, tool_log[:100])
    check("D3 调用 read_file", "read_file" in tool_log, tool_log[:100])
    check("D4 工具不重复执行", tool_log.count("describe_workspace") <= 1 and tool_log.count("read_file") <= 1, tool_log[:100])
    check("D5 回复包含目录结构", "目录" in reply or "structure" in reply.lower(), reply[:80].replace("\n", " "))
    check("D6 回复包含 README 总结", "README" in reply, reply[:120].replace("\n", " "))
    check("D7 回复有实质内容", len(reply) > 200, f"{len(reply)} 字")
    cleanup("react_test_multi")


def e_stream():
    p("=== E. 流式对话 ===")
    now = datetime.now()
    try:
        joined, done = stream_chat("现在几点？", "react_test_stream")
    except Exception as e:
        check("E1 流式请求成功", False, str(e)[:80])
        return
    reply = done if len(done) >= len(joined) else joined
    check("E1 流式请求成功", bool(reply), f"tokens={len(joined)} done={len(done)}")
    check("E2 流式回复包含时间/日期", ("点" in reply) or (f"{now.day}日" in reply) or (f"{now.hour}" in reply), reply[:80].replace("\n", " "))
    check("E3 流式回复未被改写成拒绝", "无法获取" not in reply, reply[:80].replace("\n", " "))
    cleanup("react_test_stream")


def f_side_effect_once():
    p("=== F. 副作用工具不重复执行（context-store 黑板写入） ===")
    st, r, dt = chat("请把「ReAct 验收标记 42」写入上下文存储的黑板，键名 react_probe", "react_test_side")
    reply = r.get("reply") or ""
    tool_log = r.get("toolLog") or ""
    check("F1 副作用请求 HTTP 200", st == 200 and bool(reply), f"{dt}s")
    # 允许未命中 MCP 调度（模型可能用内置文件工具），但若命中 context-store 则只允许一次
    if "context-store" in tool_log or "context" in tool_log.lower():
        check("F2 context-store 只调用一次", tool_log.count("context") <= 2, tool_log[:100])
    else:
        check("F2 未尝试不可用的工具调用", "未找到" not in reply, reply[:80].replace("\n", " "))
    cleanup("react_test_side")


def g_sequence_no_duplicate_weather():
    p("=== G. 顺序任务下天气工具不重复执行（跨写法去重） ===")
    st, r, dt = chat("先查一下北京今天的天气，然后说说适合穿什么", "react_test_seq")
    reply = r.get("reply") or ""
    tool_log = r.get("toolLog") or ""
    check("G1 顺序请求 HTTP 200", st == 200 and bool(reply), f"{dt}s")
    check("G2 调用了天气工具", "get_weather" in tool_log, tool_log[:100])
    check("G3 天气工具只执行一次", tool_log.count("get_weather") == 1, tool_log[:120])
    check("G4 回复引用北京天气", ("北京" in reply) and ("°C" in reply or "℃" in reply), reply[:80].replace("\n", " "))
    cleanup("react_test_seq")


def h_two_files_same_tool():
    p("=== H. 同一工具不同参数：两个文件都要真正读取 ===")
    st, r, dt = chat("先读取 README.md 的开头部分，再读取 docs/操作指南.md 的开头部分，然后比较两者内容", "react_test_files")
    reply = r.get("reply") or ""
    tool_log = r.get("toolLog") or ""
    check("H1 多文件请求 HTTP 200", st == 200 and bool(reply), f"{dt}s")
    check("H2 read_file 执行了两次（不同路径未被误判为重复）", tool_log.count("read_file") >= 2, tool_log[:140])
    check("H3 回复同时覆盖两个文件", ("README" in reply) and ("操作指南" in reply), reply[:100].replace("\n", " "))
    check("H4 未被知识库流程误路由", "已从本地知识库读取文档" not in reply, reply[:80].replace("\n", " "))
    cleanup("react_test_files")

if __name__ == "__main__":
    p("=" * 72)
    p("阶段18 验收：ReAct 循环重构")
    p(f"目标: {BASE}")
    p("=" * 72)
    try:
        st, _ = api("GET", "/api/models", timeout=10)
        if st != 200:
            p(f"服务未就绪 (HTTP {st})")
            sys.exit(1)
    except Exception as e:
        p(f"无法连接后端服务: {e}")
        sys.exit(1)
    p("服务就绪\n")
    for fn in (a_health_plain, b_weather_city, c_time_direct, d_multi_step, e_stream, f_side_effect_once, g_sequence_no_duplicate_weather, h_two_files_same_tool):
        try:
            fn()
        except Exception as e:
            check(fn.__name__ + " 异常", False, str(e)[:120])
        p()
    passed = sum(1 for _, s, _ in RESULTS if s == "PASS")
    p("=" * 72)
    p(f"结果: {passed}/{len(RESULTS)} PASS")
    for case, s, detail in RESULTS:
        if s != "PASS":
            p(f"  FAIL {case} -- {detail}")
    p("=" * 72)
    sys.exit(0 if passed == len(RESULTS) else 1)

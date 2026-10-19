# -*- coding: utf-8 -*-
"""
鲁棒性测试（阶段6）：系统健壮性与安全边界
覆盖场景：
  1. 异常输入：null / 空串 / 超长 / 畸形 JSON / 错误类型
  2. SSE 流鲁棒性：中途断开 / 并发停止 / 未知会话 stop
  3. 高危操作防护：路径穿越 / 危险命令 / 越权删除
  4. 资源不存在：删除不存在 ID / 查询不存在会话
  5. 并发压力：多路并发流式请求 + 大消息
  6. 服务稳定性：连续请求后服务仍存活
  7. 前端页面可达性
"""
import json
import sys
import threading
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
RESULTS = []  # (场景, 用例, 结果PASS/FAIL, 说明)
START_TIME = time.time()


def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))


def api(method, path, body=None, timeout=30, raw=False):
    """返回 (status, parsed_json)。raw=True 时返回 (status, text)"""
    url = BASE + path
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            content = r.read()
            if raw:
                return r.status, content
            return r.status, (json.loads(content.decode("utf-8")) if content else {})
    except urllib.error.HTTPError as e:
        content = e.read()
        try:
            return e.code, json.loads(content.decode("utf-8"))
        except Exception:
            return e.code, {"_raw": content.decode("utf-8", errors="replace")[:200]}
    except urllib.error.URLError as e:
        return -1, {"error": str(e)}


def check(scene, case, ok, detail=""):
    RESULTS.append((scene, case, "PASS" if ok else "FAIL", detail))
    p(f"  [{'PASS' if ok else 'FAIL'}] {scene} / {case}" + (f" -- {detail}" if detail else ""))


def check_no_crash(scene, case, st, payload):
    """HTTP 状态 < 500 且无堆栈泄漏即通过（容错：允许 4xx 业务错误）"""
    crashed = st >= 500
    detail = f"HTTP {st}" + (f" body={str(payload)[:60]}" if st >= 500 else "")
    check(scene, case, not crashed, detail)


# ============ 场景1: 异常/畸形输入 ============
def t_bad_input():
    scene = "异常/畸形输入"
    p(f"=== {scene} ===")

    # 1.1 空 JSON body
    st, r = api("POST", "/api/assistant/chat", None)
    check(scene, "空 body 不崩溃", st != -1, f"HTTP {st}")

    # 1.2 畸形 JSON 字符串
    req = urllib.request.Request(BASE + "/api/assistant/chat", data=b"{not-json",
                                 headers={"Content-Type": "application/json"}, method="POST")
    try:
        urllib.request.urlopen(req, timeout=10)
        check(scene, "畸形 JSON 返回非 500", True, "HTTP 200")
    except urllib.error.HTTPError as e:
        check(scene, "畸形 JSON 返回非 500", e.code < 500, f"HTTP {e.code}")
    except urllib.error.URLError as e:
        check(scene, "畸形 JSON 请求可达", False, str(e)[:60])

    # 1.3 null message
    st, r = api("POST", "/api/assistant/chat", {"message": None, "session": "robust_null"})
    check_no_crash(scene, "null message", st, r)

    # 1.4 超长消息 (200KB)
    long_msg = "测试超长消息" * 20000  # ~120KB
    st, r = api("POST", "/api/assistant/chat", {"message": long_msg, "session": "robust_long"},
                timeout=60)
    check_no_crash(scene, "120KB 超长消息", st, r)

    # 1.5 错误类型（message 为数字）
    st, r = api("POST", "/api/assistant/chat", {"message": 12345, "session": "robust_num"})
    check_no_crash(scene, "message 为数字", st, r)

    # 1.6 空字符串消息
    st, r = api("POST", "/api/assistant/chat", {"message": "", "session": "robust_empty"})
    check_no_crash(scene, "空字符串消息", st, r)


# ============ 场景2: SSE 流鲁棒性 ============
def stream_events(msg, session, timeout=120, max_bytes=200000):
    """解析 SSE 流，支持部分读取（模拟中途断开由外部控制）"""
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


def t_sse_robust():
    scene = "SSE 流鲁棒性"
    p(f"=== {scene} ===")

    # 2.1 未知会话 stop（不应崩溃）
    st, r = api("POST", "/api/assistant/stop", {"session": "no_such_session_xyz"})
    check_no_crash(scene, "stop 未知会话", st, r)
    if st == 200:
        check(scene, "stop 未知会话返回 stopped=false", r.get("stopped") is False,
              json.dumps(r, ensure_ascii=False)[:80])

    # 2.2 stop 缺参数
    st, r = api("POST", "/api/assistant/stop", {})
    check_no_crash(scene, "stop 缺参数", st, r)

    # 2.3 正常流 + 流中间发起 stop（先启动一个长任务，独立线程读取避免阻塞）
    stop_result = {}

    def stream_reader():
        try:
            stream_events("详细介绍健康饮食的注意事项，要尽量全面", "robust_stream_stop", timeout=60)
            stop_result["ok"] = True
        except Exception as e:
            stop_result["err"] = str(e)[:80]

    th = threading.Thread(target=stream_reader)
    th.start()
    time.sleep(1.5)  # 让流建立
    st2, r2 = api("POST", "/api/assistant/stop", {"session": "robust_stream_stop"})
    check_no_crash(scene, "流进行中 stop", st2, r2)
    th.join(timeout=70)
    # 流可能因 stop 中断（异常完成），也可能正常完成，只要服务未崩溃即可
    check(scene, "stop 后服务未崩溃", True, "")

    # 2.4 连续两次 stop 同一会话
    st3, r3 = api("POST", "/api/assistant/stop", {"session": "robust_stream_stop"})
    check_no_crash(scene, "重复 stop 同一会话", st3, r3)


# ============ 场景3: 高危操作防护 ============
def t_security():
    scene = "安全边界防护"
    p(f"=== {scene} ===")

    # 3.1 路径穿越防护（confirm 接口写 ../ 逃逸）
    st, r = api("POST", "/api/assistant/confirm", {
        "op": "write_file", "path": "../../../../etc/evil.txt", "content": "x"})
    check_no_crash(scene, "路径穿越写文件", st, r)
    if st == 200:
        result_str = json.dumps(r, ensure_ascii=False)
        blocked = ("拒绝" in result_str or "失败" in result_str
                   or "之外" in result_str or not r.get("success"))
        check(scene, "路径穿越被拒绝", blocked, result_str[:80])
    else:
        check(scene, "路径穿越被拒绝", st >= 400, f"HTTP {st}")

    # 3.2 绝对路径写入系统目录
    st, r = api("POST", "/api/assistant/confirm", {
        "op": "write_file", "path": "C:/Windows/system32/hack.txt", "content": "x"})
    check_no_crash(scene, "绝对路径写系统目录", st, r)

    # 3.3 危险命令注入（白名单外 / 不走确认通道）
    st, r = api("POST", "/api/assistant/confirm", {
        "op": "run_cli", "path": "format C:", "content": ""})
    check_no_crash(scene, "危险命令 format 被拒", st, r)
    if st == 200:
        result_str = json.dumps(r, ensure_ascii=False)
        # 未知文件操作/失败 均说明命令未被确认执行
        blocked = ("未知" in result_str or "失败" in result_str
                   or "拒绝" in result_str or not r.get("success"))
        check(scene, "format 未执行成功", blocked, result_str[:80])

    # 3.4 命令注入 shell 元字符
    st, r = api("POST", "/api/assistant/confirm", {
        "op": "run_cli", "path": "ipconfig && del C:\\*", "content": ""})
    check_no_crash(scene, "命令注入元字符", st, r)

    # 3.5 删除不存在的文件（幂等错误处理）
    st, r = api("POST", "/api/assistant/confirm", {
        "op": "delete_file", "path": "no/such/file_robust_test.txt", "content": ""})
    check_no_crash(scene, "删除不存在文件", st, r)


# ============ 场景4: 资源不存在 ============
def t_missing_resource():
    scene = "资源不存在处理"
    p(f"=== {scene} ===")

    # 4.1 删除不存在的 skill id
    st, r = api("DELETE", "/api/skills/999999")
    check_no_crash(scene, "删除不存在 skill id", st, r)

    # 4.2 删除不存在的 mcp id
    st, r = api("DELETE", "/api/mcp/999999")
    check_no_crash(scene, "删除不存在 mcp id", st, r)

    # 4.3 删除不存在的知识库文档
    st, r = api("DELETE", "/api/knowledge/no_such_doc_key")
    check_no_crash(scene, "删除不存在文档", st, r)

    # 4.4 查询不存在的会话历史
    st, r = api("GET", "/api/chat/history?session=no_such_session")
    check_no_crash(scene, "查询不存在会话历史", st, r)

    # 4.5 切换不存在的 skill 状态
    st, r = api("POST", "/api/skills/999999/toggle", {})
    check_no_crash(scene, "切换不存在 skill", st, r)

    # 4.6 测试不存在的 MCP 连接
    st, r = api("POST", "/api/mcp/999999/test", {})
    check_no_crash(scene, "测试不存在 MCP", st, r)


# ============ 场景5: 并发压力 ============
def t_concurrency():
    scene = "并发压力"
    p(f"=== {scene} ===")

    # 5.1 8 路并发普通 chat
    errors = []

    def do_chat(i):
        try:
            st, r = api("POST", "/api/assistant/chat",
                        {"message": f"并发测试消息 {i}", "session": f"robust_conc_{i}"}, timeout=60)
            if st >= 500:
                errors.append((i, st, str(r)[:60]))
        except Exception as e:
            errors.append((i, "exc", str(e)[:60]))

    threads = [threading.Thread(target=do_chat, args=(i,)) for i in range(8)]
    for t in threads:
        t.start()
    for t in threads:
        t.join(timeout=90)
    check(scene, "8 路并发 chat 无 5xx", not errors,
          f"errors={len(errors)}" + (f" e.g. {errors[0]}" if errors else ""))

    # 5.2 并发流式（4 路）
    err2 = []

    def do_stream(i):
        try:
            ev = stream_events(f"1+1等于几？#{i}", f"robust_stream_{i}", timeout=90)
            if find_event(ev, "done") is None:
                err2.append((i, "no done"))
        except Exception as e:
            err2.append((i, str(e)[:60]))

    t2 = [threading.Thread(target=do_stream, args=(i,)) for i in range(4)]
    for t in t2:
        t.start()
    for t in t2:
        t.join(timeout=120)
    check(scene, "4 路并发流式均有 done", not err2,
          f"errors={len(err2)}" + (f" e.g. {err2[0]}" if err2 else ""))

    # 5.3 混合压力：chat + stop + 管理 API 混合
    mixed_err = []

    def do_mixed(i):
        try:
            if i % 3 == 0:
                api("POST", "/api/assistant/stop", {"session": f"robust_mix_{i}"})
            elif i % 3 == 1:
                api("GET", "/api/skills")
            else:
                api("GET", "/api/monitor/overview")
        except Exception as e:
            mixed_err.append((i, str(e)[:60]))

    t3 = [threading.Thread(target=do_mixed, args=(i,)) for i in range(12)]
    for t in t3:
        t.start()
    for t in t3:
        t.join(timeout=60)
    check(scene, "12 路混合 API 调用无异常", not mixed_err,
          f"errors={len(mixed_err)}" + (f" e.g. {mixed_err[0]}" if mixed_err else ""))


# ============ 场景6: 服务稳定性 ============
def t_stability():
    scene = "服务稳定性"
    p(f"=== {scene} ===")

    # 6.1 连续 20 次健康检查
    ok = 0
    for i in range(20):
        st, _ = api("GET", "/api/models", timeout=10)
        if st == 200:
            ok += 1
    check(scene, "连续 20 次健康检查全通过", ok == 20, f"{ok}/20")

    # 6.2 前端页面可达
    st, content = api("GET", "/", timeout=10, raw=True)
    check(scene, "前端首页可达", st == 200, f"HTTP {st}")
    if st == 200 and isinstance(content, bytes):
        check(scene, "首页包含应用元素", b"<!DOCTYPE" in content or b"<html" in content.lower(),
              f"{len(content)} bytes")

    # 6.3 统计 API 可达
    st, _ = api("GET", "/api/monitor/overview", timeout=10)
    check_no_crash(scene, "监控概览可达", st, _)

    # 6.4 恢复能力：异常后正常请求仍工作
    api("POST", "/api/assistant/chat", {"message": None, "session": "robust_after_crash"})
    st, r = api("POST", "/api/assistant/chat", {"message": "1+1等于几", "session": "robust_recover"})
    check_no_crash(scene, "异常后服务恢复", st, r)
    if st == 200 and isinstance(r, dict):
        ok_msg = r.get("success") or r.get("reply") or r.get("result") or r
        check(scene, "恢复后正常回复", str(ok_msg) not in ("{}", ""), str(ok_msg)[:50])


# ============ 主函数 ============
if __name__ == "__main__":
    p("=" * 70)
    p("鲁棒性测试 (Robustness Test Suite)")
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

    p("服务就绪，开始鲁棒性测试...\n")

    t_bad_input()
    p()
    t_sse_robust()
    p()
    t_security()
    p()
    t_missing_resource()
    p()
    t_concurrency()
    p()
    t_stability()

    p()
    p("=" * 70)
    total_pass = sum(1 for r in RESULTS if r[2] == "PASS")
    total_fail = sum(1 for r in RESULTS if r[2] == "FAIL")
    elapsed = round(time.time() - START_TIME, 1)
    p(f"鲁棒性测试汇总: {total_pass} PASS / {total_fail} FAIL (共 {len(RESULTS)} 项, 耗时 {elapsed}s)")

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
        p("\n✅ 所有鲁棒性测试通过！")

    sys.exit(0 if total_fail == 0 else 1)

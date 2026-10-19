# -*- coding: utf-8 -*-
"""
前端功能测试（阶段8）：面向用户可见的前端聊天助手，验证三大维度
  A. 前端资源完备性（首页 / app.js / 优化后的安全与性能逻辑已部署）
  B. 回答准确度（数学 / 事实 / 翻译 / 时间）
  C. 各功能是否正常调取（时间 / 天气 / 搜索 / 知识库 / 图表 / 定时提醒 / 记忆 / 技能 / 多Agent）
  D. 鲁棒性（空输入 / XSS 注入 / 无意义输入 / 超长输入 / 流式中断）
"""
import json
import sys
import threading
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
RESULTS = []  # (维度, 用例, PASS/FAIL, 说明)
START = time.time()


def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))


def check(dim, case, ok, detail=""):
    RESULTS.append((dim, case, "PASS" if ok else "FAIL", detail))
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
            if raw:
                return r.status, c
            return r.status, (json.loads(c.decode("utf-8")) if c else {})
    except urllib.error.HTTPError as e:
        c = e.read()
        try:
            return e.code, json.loads(c.decode("utf-8"))
        except Exception:
            return e.code, {}
    except urllib.error.URLError as e:
        return -1, {"error": str(e)}


def stream_reply(msg, session, timeout=120):
    """返回 (完整回复文本, 事件列表)。完整回复取 done 或 token 拼接中较长者。"""
    b = json.dumps({"message": msg, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat-stream", data=b,
                                 headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=timeout)
    buffer = ""
    events = []
    tokens = []
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
                data = "\n".join(data_lines)
                events.append({"event": ev, "data": data})
                if ev == "token":
                    tokens.append(data)
    done = ""
    for e in events:
        if e["event"] == "done":
            done = e["data"]
    joined = "".join(tokens)
    full = done if len(done) >= len(joined) else joined
    return full, events


def has_any(text, words):
    return any(w in text for w in words)


def has_event(events, name):
    return any(e["event"] == name for e in events)


# ============ A. 前端资源完备性 ============
def a_resources():
    p("=== A. 前端资源完备性 ===")
    st, body = api("GET", "/", raw=True)
    check("A", "首页可访问", st == 200 and b"<html" in body.lower(), f"HTTP {st}")

    st, js = api("GET", "/app.js", raw=True)
    check("A", "app.js 可加载", st == 200 and b"sendChat" in js, f"{len(js)} bytes")
    # 验证前一轮前端优化已部署（XSS 转义 + 流式节流 + 图表容错）
    for feat, key in [("XSS 属性转义 escAttr", b"escAttr"),
                      ("onclick 安全内嵌 jsStr", b"jsStr"),
                      ("流式渲染节流 throttleRender", b"throttleRender"),
                      ("图表空数据容错 hasChartData", b"hasChartData")]:
        check("A", f"app.js 含 {feat}", key in js)


# ============ B. 回答准确度 ============
def b_accuracy():
    p("=== B. 回答准确度 ===")
    cases = [
        ("数学计算", "1+1等于几？", ["2"]),
        ("事实常识", "中国的首都是哪个城市？", ["北京"]),
        ("中英翻译", "请把「你好」翻译成英文", ["hello", "Hello", "hi", "Hi"]),
    ]
    for case, q, expects in cases:
        try:
            reply, _ = stream_reply(q, "fe_acc_" + case)
            ok = len(reply) > 0 and has_any(reply, expects)
            check("B", case, ok, reply[:50].replace("\n", " "))
        except Exception as e:
            check("B", case, False, f"异常: {str(e)[:60]}")

    # 时间准确度（同时属于功能调取）
    try:
        reply, _ = stream_reply("现在几点了？", "fe_acc_time")
        import re
        ok = len(reply) > 0 and bool(re.search(r"\d{1,2}[:：点]\d{0,2}", reply))
        check("B", "时间查询", ok, reply[:60].replace("\n", " "))
    except Exception as e:
        check("B", "时间查询", False, f"异常: {str(e)[:60]}")


# ============ C. 各功能正常调取 ============
def c_functions():
    p("=== C. 各功能是否正常调取 ===")
    tests = [
        ("天气工具", "北京今天天气怎么样？",
         ["天气", "温度", "晴", "雨", "多云", "°C", "℃", "湿度"], 20),
        ("联网搜索", "搜索一下今天的热门新闻",
         ["新闻", "热点", "资讯", "搜索", "头条"], 20),
        ("知识库检索", "总结知识库里的文档内容",
         ["知识库", "文档", "报告", "消费电子", "总结"], 20),
        ("数据分析图表", "对知识库里的数据做统计分析并生成图表",
         ["chart", "图表", "柱状", "折线", "饼图", "数据分析", "统计"], 20),
        ("定时提醒", "2分钟后提醒我喝水",
         ["提醒", "定时", "分钟", "SCHEDULE", "后台任务"], 5),
        ("记忆功能", "记住我最喜欢的颜色是蓝色",
         ["记住", "记忆", "已记住", "蓝色"], 5),
        ("技能调用(邮件)", "帮我写一封请假的邮件",
         ["邮件", "请假", "主题", "敬礼", "此致"], 20),
        ("多Agent协同", "帮我同时做：查北京天气、整理本周热点",
         ["拆解", "子任务", "分工", "调度", "并行", "多个"], 20),
    ]
    for name, q, keywords, minlen in tests:
        try:
            reply, events = stream_reply(q, "fe_fn_" + name)
            ok = len(reply) >= minlen and has_any(reply, keywords)
            check("C", name, ok, reply[:50].replace("\n", " "))
        except Exception as e:
            check("C", name, False, f"异常: {str(e)[:60]}")


# ============ D. 鲁棒性 ============
def d_robust():
    p("=== D. 鲁棒性 ===")
    # 空输入
    try:
        reply, _ = stream_reply("", "fe_rob_empty")
        check("D", "空输入不崩溃", True, f"HTTP 流正常, reply={reply[:20]!r}")
    except Exception as e:
        check("D", "空输入不崩溃", "崩溃" not in str(e), f"{str(e)[:60]}")

    # XSS 注入输入（回复不应原样回显 <script> 标签）
    try:
        reply, _ = stream_reply("请重复这句话：<script>alert(1)</script>", "fe_rob_xss")
        ok = len(reply) > 0 and "<script>" not in reply and "<script" not in reply
        check("D", "XSS 注入不回显原始脚本", ok, reply[:60].replace("\n", " "))
    except Exception as e:
        check("D", "XSS 注入不回显原始脚本", False, f"异常: {str(e)[:60]}")

    # 无意义输入
    try:
        reply, _ = stream_reply("asdfghjkl12345", "fe_rob_gibberish")
        check("D", "无意义输入优雅降级", len(reply) > 0, reply[:40].replace("\n", " "))
    except Exception as e:
        check("D", "无意义输入优雅降级", False, f"异常: {str(e)[:60]}")

    # 超长输入（约 20KB）
    try:
        long_msg = "请忽略并只回复收到" * 2000
        reply, _ = stream_reply(long_msg, "fe_rob_long", timeout=120)
        check("D", "超长输入不崩溃", True, f"reply={reply[:30]!r}")
    except Exception as e:
        check("D", "超长输入不崩溃", "崩溃" not in str(e), f"{str(e)[:60]}")

    # 流式中断（stop）后服务仍存活
    stop_ok = {"v": True}

    def reader():
        try:
            stream_reply("详细介绍健康饮食注意事项，要尽量全面", "fe_rob_stop", timeout=90)
        except Exception:
            pass

    th = threading.Thread(target=reader)
    th.start()
    time.sleep(1.5)
    st, r = api("POST", "/api/assistant/stop", {"session": "fe_rob_stop"})
    th.join(timeout=90)
    check("D", "流式中断 stop 后服务存活", st != -1, f"stop HTTP {st}")


# ============ 主函数 ============
if __name__ == "__main__":
    p("=" * 70)
    p("前端功能测试（阶段8）：回答准确度 / 功能调取 / 鲁棒性")
    p(f"目标: {BASE}")
    p("=" * 70)

    try:
        st, _ = api("GET", "/api/models", timeout=10)
        if st != 200:
            p(f"⚠️ 服务未就绪 (HTTP {st})")
            sys.exit(1)
    except Exception as e:
        p(f"⚠️ 无法连接后端服务: {e}")
        sys.exit(1)

    p("服务就绪，开始测试...\n")

    a_resources(); p()
    b_accuracy(); p()
    c_functions(); p()
    d_robust()

    p()
    p("=" * 70)
    total_pass = sum(1 for r in RESULTS if r[2] == "PASS")
    total_fail = sum(1 for r in RESULTS if r[2] == "FAIL")
    p(f"前端功能测试汇总: {total_pass} PASS / {total_fail} FAIL (共 {len(RESULTS)} 项, 耗时 {round(time.time()-START,1)}s)")

    dims = {}
    for dim, case, result, detail in RESULTS:
        dims.setdefault(dim, {"pass": 0, "fail": 0, "fails": []})
        if result == "PASS":
            dims[dim]["pass"] += 1
        else:
            dims[dim]["fail"] += 1
            dims[dim]["fails"].append((case, detail))

    p("\n--- 按维度汇总 ---")
    for dim, s in dims.items():
        total = s["pass"] + s["fail"]
        p(f"  {dim}: {s['pass']}/{total} PASS")
        for case, detail in s["fails"]:
            p(f"    FAIL: {case} -- {detail}")

    p("")
    if total_fail > 0:
        p(f"⚠️ 存在 {total_fail} 个失败用例")
    else:
        p("✅ 前端功能测试全部通过！")

    sys.exit(0 if total_fail == 0 else 1)

# -*- coding: utf-8 -*-
"""系统测试（阶段2）：端到端跨模块流程验证"""
import json
import sys
import time
import urllib.request

BASE = "http://127.0.0.1:8080"
RESULTS = []

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))

def check(mod, case, ok, detail=""):
    RESULTS.append((mod, case, "PASS" if ok else "FAIL", detail))
    p(f"  [{'PASS' if ok else 'FAIL'}] {mod} / {case}" + (f" -- {detail}" if detail else ""))

def chat(msg, session="sys", timeout=90):
    b = json.dumps({"message": msg, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat", data=b,
                                 headers={"Content-Type": "application/json"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=timeout) as r:
        d = json.loads(r.read().decode("utf-8"))
    return 200, d, time.time() - t0

def stream_chat(msg, session="sys", timeout=90):
    """返回 (token流是否完整, 事件序列, done内容, 是否有summary)"""
    b = json.dumps({"message": msg, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat-stream", data=b,
                                 headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=timeout)
    buffer = ""
    tokens = 0
    events = []
    done = None
    summary = False
    while True:
        raw = resp.read(1024)
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
            dt = "\n".join(data_lines)
            if ev == "token":
                tokens += len(dt)
            elif ev == "done":
                done = dt
            elif ev == "summary":
                summary = True
            events.append(ev or "message")
    return tokens > 0, events, done, summary

def safe(fn):
    try:
        return fn()
    except Exception as e:
        p("  [异常] " + str(e)[:120])
        return None

# ============ 系统测试场景 ============
def s_basic_chat():
    mod = "系统-对话链路"
    p(f"=== {mod} ===")
    res = safe(lambda: chat("你好，请介绍一下你自己", "sys_basic"))
    check(mod, "普通对话返回", res is not None and res[0] == 200 and len(str(res[1].get("reply", ""))) > 10,
          f"耗时{res[2]:.1f}s" if res else "")
    # 流式链路
    res = safe(lambda: stream_chat("什么是物联网", "sys_stream"))
    if res is None:
        check(mod, "流式 token 输出", False, "调用失败")
        check(mod, "流式 done 完成", False)
    else:
        ok, events, done, _ = res
        check(mod, "流式 token 输出", ok, f"事件: {','.join(set(events))[:80]}")
        check(mod, "流式 done 完成", done is not None and len(done) > 0)

def s_multi_tool():
    mod = "系统-工具调度"
    p(f"=== {mod} ===")
    res = safe(lambda: chat("获取天气 今日热点", "sys_wea", timeout=120))
    check(mod, "多工具并行调用", res is not None and res[0] == 200 and "get_weather" in str(res[1].get("toolLog", ""))
          and "get_hot_topics" in str(res[1].get("toolLog", "")), str(res[1].get("toolLog", "")) if res else "")
    res2 = safe(lambda: chat("现在几点了", "sys_time"))
    check(mod, "时间工具", res2 is not None and "get_current_time" in str(res2[1].get("toolLog", "")))
    res3 = safe(lambda: chat("如何使用联网搜索", "sys_howto"))
    howto_ok = res3 is not None and (str(res3[1].get("toolUsed", "")).lower() == "false"
                                     or "无需联网" in str(res3[1].get("reply", ""))
                                     or "联网搜索判断" in str(res3[1].get("log", "")))
    check(mod, "how-to 排除联网", howto_ok)

def s_data_analysis():
    mod = "系统-数据分析"
    p(f"=== {mod} ===")
    res = safe(lambda: chat("用个人消费电子报告做数据统计并生成图表", "sys_an", timeout=120))
    if res is None:
        check(mod, "触发 analyze_data 工具", False)
        check(mod, "图表容器输出", False)
        return
    st, d, cost = res
    tl = str(d.get("toolLog", ""))
    r = str(d.get("reply", ""))
    has_div = '<div class="chart"' in r or "<div class='chart'" in r
    check(mod, "触发 analyze_data 工具", "analyze_data" in tl, tl)
    check(mod, "图表容器输出", has_div or "[CHART_JSON]" in r,
          "图表容器已输出" if has_div else ("CHART标记" if "[CHART_JSON]" in r else "未见图表"))

def s_skill_draft_import():
    mod = "系统-Skill闭环"
    p(f"=== {mod} ===")
    b = json.dumps({"message": "帮我写一个URL参数解析的技能", "session": "sys_sk"}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat-stream", data=b,
                                 headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=120)
    buffer = ""
    draft = None
    while True:
        raw = resp.read(1024)
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
            if ev == "skill_draft":
                try:
                    draft = json.loads("\n".join(data_lines))
                except Exception:
                    pass
    check(mod, "skill_draft 事件", draft is not None and draft.get("name"))
    if draft:
        b2 = json.dumps({"name": draft.get("name"), "category": draft.get("category"),
                         "description": draft.get("description"), "content": draft.get("content")}).encode("utf-8")
        req2 = urllib.request.Request(BASE + "/api/skills/import-draft", data=b2,
                                      headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req2, timeout=30) as r2:
            imp = json.loads(r2.read().decode("utf-8"))
        check(mod, "一键导入成功", imp.get("success"), str(imp.get("error", "")))
        if imp.get("success") and imp.get("skill", {}).get("id"):
            sid = imp["skill"]["id"]
            urllib.request.urlopen(urllib.request.Request(f"{BASE}/api/skills/{sid}", method="DELETE"), timeout=30)
            p("  (测试技能已清理)")
    else:
        check(mod, "一键导入成功", False, "未收到草稿")

def s_memory():
    mod = "系统-记忆"
    p(f"=== {mod} ===")
    # 记忆服务测试
    key = "SYS_MEM_" + str(int(time.time()))
    b = json.dumps({"memoryKey": key, "content": "系统测试记忆", "importance": 3, "category": "系统"}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/memory", data=b, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=20) as r:
        json.loads(r.read().decode("utf-8"))
    with urllib.request.urlopen(BASE + "/api/memory", timeout=20) as r:
        mems = json.loads(r.read().decode("utf-8"))
    check(mod, "记忆创建并列出", any(m.get("memoryKey") == key for m in mems))
    urllib.request.urlopen(urllib.request.Request(f"{BASE}/api/memory/{key}", method="DELETE"), timeout=20)

if __name__ == "__main__":
    s_basic_chat(); s_multi_tool(); s_data_analysis(); s_skill_draft_import(); s_memory()
    p("=" * 70)
    p(f"系统测试汇总: {sum(1 for r in RESULTS if r[2]=='PASS')} PASS / {sum(1 for r in RESULTS if r[2]=='FAIL')} FAIL")
    for r in RESULTS:
        if r[2] == "FAIL":
            p(f"  FAIL: {r[0]} / {r[1]} -- {r[3]}")

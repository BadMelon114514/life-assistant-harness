# -*- coding: utf-8 -*-
"""验证 Agent Loop 多轮工具机制 + 回归测试"""
import json
import sys
import urllib.request

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))

BASE = "http://127.0.0.1:8080"
def chat(msg, session, timeout=120):
    b = json.dumps({"message": msg, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat", data=b, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8"))

def get_monitor(source):
    with urllib.request.urlopen(BASE + "/api/monitor/logs", timeout=20) as r:
        logs = json.loads(r.read().decode("utf-8"))
    return [lg for lg in logs if lg.get("source") == source]

# 1. 普通对话（单轮，无工具）回归
p("=== 1. 普通对话回归 ===")
d = chat("你好", "loop1")
p("  reply: " + str(d.get("reply", ""))[:80])
print("  toolLog:", str(d.get("toolLog"))[:60])

# 2. 时间工具（单轮内置工具）
p("=== 2. 时间工具（单轮）===")
d = chat("现在几点了", "loop2")
p("  toolLog: " + str(d.get("toolLog"))[:80])
p("  reply: " + str(d.get("reply", ""))[:80])

# 3. 天气+热点（工具结果正确回填）
p("=== 3. 多工具调度回归 ===")
d = chat("获取天气 今日热点", "loop3", timeout=150)
tl = str(d.get("toolLog", ""))
p("  toolLog: " + tl[:100])
print("  含天气工具:", "get_weather" in tl, "含热点工具:", "get_hot_topics" in tl)

# 4. 监控日志中 Agent Loop 记录
p("=== 4. Agent Loop 监控 ===")
llm = get_monitor("llm")
p("  LLM 传参日志数: " + str(len(llm)))
if llm:
    p("  最新: " + str(llm[-1].get("message"))[:150])

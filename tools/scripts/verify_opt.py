# -*- coding: utf-8 -*-
"""验证四项优化：md渲染 / 停止按钮位置 / 流式跟随 / LLM传参监控"""
import json
import sys
import urllib.request

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))

BASE = "http://127.0.0.1:8080"
OKS = []

def check(case, ok, detail=""):
    OKS.append((case, "PASS" if ok else "FAIL", detail))
    p(f"  [{'PASS' if ok else 'FAIL'}] {case}" + (f" -- {detail}" if detail else ""))

# 1. 前端静态资源：md 渲染 + 停止按钮 + 流式跟随
d = urllib.request.urlopen(BASE + "/app.js", timeout=10).read().decode("utf-8")
check("md 渲染 (marked.parse)", "marked.parse" in d and "md-render" in d)
check("停止按钮移发送栏 (chatStopBtn)", "chatStopBtn" in d and "stopCurrentChat" in d)
check("流式跟随 (userScrolledUp)", "userScrolledUp" in d and "box.scrollTop = box.scrollHeight" in d)
d2 = urllib.request.urlopen(BASE + "/index.html", timeout=10).read().decode("utf-8")
check("marked CDN 引入", "marked@12" in d2)
check("md-render 样式", ".md-render table" in d2)

# 2. 发一条对话触发 LLM 调用，验证监控记录 LLM 传参
b = json.dumps({"message": "你好", "session": "opt_v1"}).encode("utf-8")
req = urllib.request.Request(BASE + "/api/assistant/chat", data=b, headers={"Content-Type": "application/json"})
with urllib.request.urlopen(req, timeout=120) as r:
    json.loads(r.read().decode("utf-8"))
with urllib.request.urlopen(BASE + "/api/monitor/logs", timeout=20) as r:
    logs = json.loads(r.read().decode("utf-8"))
llm_logs = [lg for lg in logs if lg.get("source") == "llm"]
p("  监控 llm 来源日志数: " + str(len(llm_logs)))
if llm_logs:
    latest = llm_logs[-1]
    msg = str(latest.get("message", ""))
    p("  最新 LLM 传参日志: " + msg[:200])
    check("LLM 传参含 model", "model=" in msg)
    check("LLM 传参含 messages 摘要", "messages=" in msg)
    check("LLM 传参含消息数", "共" in msg and "条消息" in msg)
else:
    check("LLM 传参含 model", False, "无 llm 日志")

# 3. 助手回传日志仍可见
assistant_logs = [lg for lg in logs if lg.get("source") == "assistant"]
check("助手回传日志可见", len(assistant_logs) >= 1 and "收到用户消息" in str(assistant_logs[0].get("message", "")))

p("=" * 60)
p(f"汇总: {sum(1 for r in OKS if r[1]=='PASS')} PASS / {sum(1 for r in OKS if r[1]=='FAIL')} FAIL")
for r in OKS:
    if r[1] == "FAIL":
        p(f"  FAIL: {r[0]} -- {r[2]}")

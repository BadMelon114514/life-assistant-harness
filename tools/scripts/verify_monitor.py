# -*- coding: utf-8 -*-
"""验证：智慧助手处理日志在监控日志中可见（收到消息/工具调用/回复完成）"""
import json
import sys
import time
import urllib.request

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))

BASE = "http://127.0.0.1:8080"

# 1. 发一条对话（触发监控日志）
b = json.dumps({"message": "现在几点了", "session": "mon_v1"}).encode("utf-8")
req = urllib.request.Request(BASE + "/api/assistant/chat", data=b,
                             headers={"Content-Type": "application/json"})
with urllib.request.urlopen(req, timeout=120) as r:
    d = json.loads(r.read().decode("utf-8"))
p("对话完成，reply: " + str(d.get("reply", ""))[:80])

# 2. 查询监控日志
with urllib.request.urlopen(BASE + "/api/monitor/logs", timeout=20) as r:
    logs = json.loads(r.read().decode("utf-8"))
p(f"监控日志条数: {len(logs)}")
assistant_logs = [lg for lg in logs if lg.get("source") == "assistant"]
p(f"assistant 来源日志: {len(assistant_logs)} 条")
for lg in assistant_logs[:6]:
    p(f"  [{lg.get('level')}] {str(lg.get('message'))[:100]}")

has_receive = any("收到用户消息" in str(l.get("message", "")) for l in logs)
has_reply = any("回复完成" in str(l.get("message", "")) for l in logs)
p("=" * 60)
p("收到用户消息日志: " + ("PASS" if has_receive else "FAIL"))
p("回复完成日志: " + ("PASS" if has_reply else "FAIL"))

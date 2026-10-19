# -*- coding: utf-8 -*-
"""验证长回复总结：内容有效（非"无法提供"）+ 长度合理"""
import json
import sys
import urllib.request

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))

body = json.dumps({"message": "详细介绍一下个人消费电子行业现状与发展趋势", "session": "vs2"}).encode("utf-8")
req = urllib.request.Request("http://127.0.0.1:8080/api/assistant/chat-stream", data=body,
                             headers={"Content-Type": "application/json"})
resp = urllib.request.urlopen(req, timeout=120)
buffer = ""
summary = None
done_len = 0
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
        if ev == "summary" and dt:
            try:
                summary = json.loads(dt)
            except Exception:
                pass
        elif ev == "done":
            done_len = len(dt)

p("=" * 70)
p("done 回复长度: " + str(done_len))
if summary:
    sm = summary.get("summary", "")
    p("summary: " + sm[:200])
    p("summary 长度: " + str(len(sm)))
    bad = any(w in sm for w in ("无法提供", "无法总结", "无法获取", "无内容", "没有内容"))
    p("含无效词(无法提供等): " + str(bad))
    p("长度合理(<=110): " + str(len(sm) <= 110))
    p("判定: " + ("PASS" if sm and len(sm) >= 5 and not bad else "FAIL"))
else:
    p("未收到 summary 事件")

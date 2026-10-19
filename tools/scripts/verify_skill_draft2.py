# -*- coding: utf-8 -*-
"""验证：新增技能走草稿路径（skill_draft 事件 + 一键导入可导入）"""
import json
import sys
import urllib.request

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        try:
            print(s.encode("gbk", errors="replace").decode("gbk"))
        except Exception:
            print(repr(s))

BASE = "http://127.0.0.1:8080"
body = json.dumps({"message": "新增一个商品价格对比的技能", "session": "vsk2"}).encode("utf-8")
req = urllib.request.Request(BASE + "/api/assistant/chat-stream", data=body,
                             headers={"Content-Type": "application/json"})
resp = urllib.request.urlopen(req, timeout=120)
buffer = ""
draft = None
done = None
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
        if ev == "skill_draft" and dt:
            try:
                draft = json.loads(dt)
            except Exception:
                pass
        elif ev == "done":
            done = dt

p("=" * 70)
p("skill_draft 事件: " + ("收到!" if draft else "未收到"))
if draft:
    p("  草稿 name: " + str(draft.get("name")))
    p("  草稿 type: " + str(draft.get("type")))
    p("  草稿 category: " + str(draft.get("category")))
    ok = draft.get("type") == "skill" and draft.get("name") and draft.get("content")
    p("判定: " + ("PASS - 走草稿路径，前端将渲染导入按钮" if ok else "FAIL"))
else:
    p("done 内容: " + str(done)[:150])
    p("判定: FAIL - 未走草稿路径（可能仍被 LLM 判为直接下载）")

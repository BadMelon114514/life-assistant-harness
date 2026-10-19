# -*- coding: utf-8 -*-
"""验证草稿 SSE 事件：skill_draft / mcp_draft"""
import json
import sys
import urllib.request

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))

body = json.dumps({"message": "帮我写一个公网IP位置获取的工具", "session": "vd1"}).encode("utf-8")
req = urllib.request.Request("http://127.0.0.1:8080/api/assistant/chat-stream",
                             data=body, headers={"Content-Type": "application/json"})
resp = urllib.request.urlopen(req, timeout=120)
buffer = ""
draft_skill = None
draft_mcp = None
final = None
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
                draft_skill = json.loads(dt)
                p("[OK] 收到 skill_draft 事件")
                p("  草稿预览: " + json.dumps(draft_skill, ensure_ascii=False)[:300])
            except Exception as e:
                p("[WARN] skill_draft 解析失败: " + str(e) + " raw: " + dt[:200])
        elif ev == "mcp_draft" and dt:
            try:
                draft_mcp = json.loads(dt)
                p("[OK] 收到 mcp_draft 事件")
            except Exception as e:
                p("[WARN] mcp_draft 解析失败: " + str(e))
        elif ev == "done" and dt:
            final = dt
            p("[OK] 收到 done 事件")
p("=" * 70)
p("验证结果汇总：")
p("  skill_draft 事件: " + ("✓ 已推送" if draft_skill else "✗ 未收到"))
p("  mcp_draft 事件:   " + ("✓ 已推送" if draft_mcp else "✗ 未收到"))
p("  done 事件:       " + ("✓ 已推送" if final else "✗ 未收到"))
if final:
    p("reply 预览: " + final[:300])
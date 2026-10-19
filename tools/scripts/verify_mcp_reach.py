# -*- coding: utf-8 -*-
"""验证 MCP 生成草稿标注连通性（虚构地址应 unreachable 警示）"""
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
body = json.dumps({"message": "新增一个天气预报查询的MCP服务", "session": "vmr"}).encode("utf-8")
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
        if ev == "mcp_draft" and dt:
            try:
                draft = json.loads(dt)
            except Exception:
                pass
        elif ev == "done":
            done = dt

p("=" * 70)
if draft:
    p("mcp_draft 草稿:")
    p("  name: " + str(draft.get("name")))
    p("  endpoint: " + str(draft.get("endpoint")))
    p("  endpointReachable: " + str(draft.get("endpointReachable")))
    p("  scanSafe: " + str(draft.get("scanSafe")))
    ok = draft.get("endpointReachable") in ("reachable", "unreachable", "unknown")
    p("判定: " + ("PASS - 已标注连通性，虚构地址将被警示" if ok else "FAIL - 未标注"))
else:
    p("未收到 mcp_draft 事件")
    p("done: " + str(done)[:200])
p("=" * 70)
if done:
    p("回复内容:")
    p(str(done)[:600])

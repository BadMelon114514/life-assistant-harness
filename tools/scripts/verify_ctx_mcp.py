# -*- coding: utf-8 -*-
"""验证 Java 智能助手经 MCP 调度调用 context-store（callMcp 路径）"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"


def api(path, method="GET", body=None, timeout=60):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers={"Content-Type": "application/json"}, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        c = r.read()
        return json.loads(c.decode("utf-8")) if c else {}


# 1) 先写入两条上下文（模拟助手历史）
api("/api/assistant/confirm", "POST", {"endpoint": "http://127.0.0.1:8765/api/mcp/tool",
                                       "args": json.dumps({"op": "context_append", "room": "mcp_demo", "role": "user", "content": "用户偏好：喜欢简洁回答"})})
api("/api/assistant/confirm", "POST", {"endpoint": "http://127.0.0.1:8765/api/mcp/tool",
                                       "args": json.dumps({"op": "context_append", "room": "mcp_demo", "role": "assistant", "content": "收到，后续保持简洁。"})})

# 2) 经 MCP 载入上下文
r = api("/api/assistant/confirm", "POST", {"endpoint": "http://127.0.0.1:8765/api/mcp/tool",
                                           "args": json.dumps({"op": "context_load", "room": "mcp_demo", "budget": 4000})})
print("confirm success:", r.get("success"))
print("result:", str(r.get("result", ""))[:400])

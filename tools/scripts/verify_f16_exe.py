# -*- coding: utf-8 -*-
"""验证打包后的可执行文件服务（8080 Java + 8765 context-store）"""
import json
import urllib.request

def get(url, timeout=10):
    with urllib.request.urlopen(url, timeout=timeout) as r:
        return r.status, r.read().decode("utf-8", "replace")

print("page:", get("http://127.0.0.1:8080/")[0])
print("health:", get("http://127.0.0.1:8080/actuator/health"))
st, body = get("http://127.0.0.1:8765/health")
h = json.loads(body)
print("ctx health:", st, "tables=", len(h.get("tables", [])), "ops=", len(h.get("ops", [])))
# 通过 Java MCP 测试通道连 context-store（id=42）
req = urllib.request.Request("http://127.0.0.1:8080/api/mcp/42/test", data=b"{}", headers={"Content-Type": "application/json"}, method="POST")
try:
    with urllib.request.urlopen(req, timeout=15) as r:
        print("mcp test:", r.status, r.read().decode("utf-8", "replace")[:200])
except Exception as e:
    print("mcp test err:", e)

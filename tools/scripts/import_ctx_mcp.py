# -*- coding: utf-8 -*-
"""将 context-store 导入 Java MCP 库并测试连通性"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"


def api(path, method="GET", body=None, timeout=60):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers={"Content-Type": "application/json"}, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        c = r.read()
        return json.loads(c.decode("utf-8")) if c else {}


r = api("/api/mcp/import-openclaw", "POST", {})
print("import:", json.dumps({k: r.get(k) for k in ("success", "importedCount", "skippedCount")}, ensure_ascii=False))
mcps = api("/api/mcp")
geo = [m for m in mcps if m.get("name") == "context-store"]
print("found:", len(geo))
if geo:
    mid = geo[0]["id"]
    t = api("/api/mcp/%d/test" % mid, "POST", {})
    print("test:", json.dumps({k: t.get(k) for k in ("success", "message")}, ensure_ascii=False))
    mcps2 = api("/api/mcp")
    m2 = [m for m in mcps2 if m.get("name") == "context-store"][0]
    print("status:", m2.get("status"))

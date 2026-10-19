# -*- coding: utf-8 -*-
import json
import urllib.request

BASE = "http://127.0.0.1:8080"


def api(method, path, body=None, timeout=30):
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        c = r.read()
        return r.status, (json.loads(c.decode("utf-8")) if c else {})


st, t = api("POST", "/api/mcp/39/test")
print("定时提醒MCP服务 test:", st, t.get("success"), t.get("message"))
st, mcps = api("GET", "/api/mcp")
for m in mcps:
    if m.get("name") in ("reminder", "定时提醒MCP服务"):
        print(m.get("name"), "|", m.get("serverType"), "|", m.get("endpoint"), "|", m.get("status"), "| lastChecked:", m.get("lastChecked"))

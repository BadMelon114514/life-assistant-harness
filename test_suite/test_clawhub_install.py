# -*- coding: utf-8 -*-
"""直接调 /api/clawhub/install 定位异常"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"


def api(path, method="GET", body=None, timeout=60):
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            c = r.read()
            return r.status, (json.loads(c.decode("utf-8")) if c else {})
    except Exception as e:
        return -1, {"error": str(e)[:100]}


st, r = api("/api/clawhub/install", method="POST", body={"slug": "xhs-note-analyst"})
print(f"install: HTTP {st}")
print(json.dumps(r, ensure_ascii=False)[:600])

# -*- coding: utf-8 -*-
"""清理测试安装的技能"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"


def api(path, method="GET", timeout=30):
    req = urllib.request.Request(BASE + path, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        c = r.read()
        return json.loads(c.decode("utf-8")) if c else {}


skills = api("/api/skills")
for s in skills:
    if str(s.get("name")) in ("翻译技能", "小红书笔记分析", "小红书账号诊断"):
        r = api(f"/api/skills/{s['id']}", method="DELETE")
        print(f"deleted {s.get('name')}: {r}")

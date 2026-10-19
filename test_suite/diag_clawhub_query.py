# -*- coding: utf-8 -*-
"""诊断：ClawHub 对整句查询 vs 关键词查询的返回差异"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"


def api(path, timeout=30):
    with urllib.request.urlopen(BASE + path, timeout=timeout) as r:
        return r.status, json.loads(r.read().decode("utf-8"))


for q in ["下载一个小红书账号诊断的技能", "小红书账号诊断", "翻译", "translate", "markdown pdf"]:
    st, r = api("/api/clawhub/search?q=" + urllib.request.quote(q))
    skills = r.get("skills") or []
    print(f"q={q!r}: HTTP {st}, count={len(skills)}")
    for s in skills[:3]:
        print(f"   - {s.get('slug')} | {str(s.get('summary'))[:50]}")

# -*- coding: utf-8 -*-
"""ClawHub API 可达性与搜索测试"""
import json
import time
import urllib.request

BASE = "http://127.0.0.1:8080"


def api(path, timeout=30):
    t0 = time.time()
    try:
        with urllib.request.urlopen(BASE + path, timeout=timeout) as r:
            body = r.read().decode("utf-8")
            return r.status, json.loads(body) if body else {}, time.time() - t0
    except Exception as e:
        return -1, {"error": str(e)[:80]}, time.time() - t0


for q in ["翻译", "translate", "markdown pdf", "weather"]:
    st, data, cost = api(f"/api/clawhub/search?q={urllib.request.quote(q)}")
    if st == 200:
        n = len(data) if isinstance(data, list) else "?"
        print(f"q={q}: HTTP 200, {n} 条结果, {cost:.1f}s")
        if isinstance(data, list) and data:
            print("  首条:", json.dumps(data[0], ensure_ascii=False)[:160])
    else:
        print(f"q={q}: HTTP {st}, {cost:.1f}s, {data}")

# 直接测外网 clawhub.ai
t0 = time.time()
try:
    with urllib.request.urlopen("https://clawhub.ai/api/v1/skills?q=translate", timeout=20) as r:
        print(f"\n直连 clawhub.ai: HTTP {r.status}, {time.time()-t0:.1f}s, len={len(r.read())}")
except Exception as e:
    print(f"\n直连 clawhub.ai 失败: {str(e)[:80]}, {time.time()-t0:.1f}s")

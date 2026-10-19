# -*- coding: utf-8 -*-
"""测试前端 getUserCity 使用的 IP 定位服务可达性"""
import json
import sys
import urllib.request

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))

def get(url, timeout=6):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode("utf-8", errors="replace"))
    except Exception as e:
        return {"__err__": str(e)[:80]}

services = [
    ("ipapi.co", "https://ipapi.co/json/", lambda d: d.get("city")),
    ("ipinfo.io", "https://ipinfo.io/json", lambda d: d.get("city")),
    ("ip-api.com", "https://ip-api.com/json/?lang=zh-CN", lambda d: d.get("city")),
]
for name, url, pick in services:
    d = get(url)
    if "__err__" in d:
        p(f"{name}: FAIL - {d['__err__']}")
    else:
        p(f"{name}: OK - city={pick(d)}, region={d.get('region', '')}, country={d.get('country_name', d.get('country', ''))}")

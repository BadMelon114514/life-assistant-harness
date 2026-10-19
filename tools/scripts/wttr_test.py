# -*- coding: utf-8 -*-
import json
import sys
import urllib.request

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))

def get(url, timeout=15):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "curl/7.0"})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", errors="replace")
    except Exception as e:
        return None, str(e)

# 测试各种格式
urls = [
    ("format3无城市", "https://wttr.in/?format=3&lang=zh"),
    ("中文城市URL编码", "https://wttr.in/%E4%B8%8A%E6%B5%B7?format=3&lang=zh"),
    ("中文城市", "https://wttr.in/上海?format=3&lang=zh"),
    ("拼音", "https://wttr.in/Shanghai?format=3&lang=zh"),
    ("经纬度", "https://wttr.in/31.23,121.47?format=3&lang=zh"),
]
for tag, url in urls:
    st, body = get(url)
    p(f"{tag}: HTTP {st} => {body[:120]}")
    sys.stdout.flush()

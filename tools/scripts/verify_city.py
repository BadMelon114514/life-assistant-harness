# -*- coding: utf-8 -*-
"""验证天气工具按用户所在城市（厦门）返回"""
import json
import sys
import urllib.request

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))

def chat(msg, session):
    b = json.dumps({"message": msg, "session": session}).encode("utf-8")
    req = urllib.request.Request("http://127.0.0.1:8080/api/assistant/chat", data=b,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read().decode("utf-8"))

p("=" * 70)
p("请求: 获取天气【用户当前所在城市：Xiamen】")
d = chat("获取天气\n【用户当前所在城市：Xiamen】", "city1")
p("toolLog: " + str(d.get("toolLog")))
p("reply: " + str(d.get("reply", ""))[:400])
r = str(d.get("reply", ""))
p("---- 判定 ----")
p("含厦门/Xiamen: " + str("厦门" in r or "Xiamen" in r))
p("含郑州/34.77: " + str("郑州" in r or "34.77" in r))

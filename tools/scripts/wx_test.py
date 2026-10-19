# -*- coding: utf-8 -*-
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
    with urllib.request.urlopen(req, timeout=90) as r:
        return json.loads(r.read().decode("utf-8"))

p("=" * 70)
p("请求: 获取天气【用户当前所在城市：上海】")
d = chat("获取天气\n【用户当前所在城市：上海】", "wx1")
p("toolLog: " + str(d.get("toolLog")))
p("reply: " + str(d.get("reply", ""))[:500])

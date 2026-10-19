# -*- coding: utf-8 -*-
import json
import sys
import urllib.request
def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))
b = json.dumps({"message": "用个人消费电子报告做数据分析并生成图表", "session": "dbgc2"}).encode("utf-8")
req = urllib.request.Request("http://127.0.0.1:8080/api/assistant/chat", data=b,
                             headers={"Content-Type": "application/json"})
with urllib.request.urlopen(req, timeout=150) as r:
    d = json.loads(r.read().decode("utf-8"))
p("toolLog: " + str(d.get("toolLog")))
reply = str(d.get("reply", ""))
p("--- reply 尾部 600 ---")
p(reply[-600:])
p("--- 检测 ---")
p("双引号 div: " + str('<div class="chart"' in reply))
p("单引号 div: " + str("<div class='chart'" in reply))
p("CHART_JSON: " + str("[CHART_JSON]" in reply))

# -*- coding: utf-8 -*-
import json
import sys
import urllib.request

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))

b = json.dumps({"message": "用个人消费电子报告做数据统计并生成图表", "session": "dbgc"}).encode("utf-8")
req = urllib.request.Request("http://127.0.0.1:8080/api/assistant/chat", data=b,
                             headers={"Content-Type": "application/json"})
with urllib.request.urlopen(req, timeout=120) as r:
    d = json.loads(r.read().decode("utf-8"))
p("toolLog: " + str(d.get("toolLog")))
reply = str(d.get("reply", ""))
p("--- reply 尾部 500 字 ---")
p(reply[-500:])
p("--- 图表检测 ---")
p("双引号 div: " + str('<div class="chart"' in reply))
p("单引号 div: " + str("<div class='chart'" in reply))
p("CHART_JSON 标记: " + str("[CHART_JSON]" in reply))

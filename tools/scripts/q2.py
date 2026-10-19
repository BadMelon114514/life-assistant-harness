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
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read().decode("utf-8"))

for tag, msg in [("describe", "用个人消费电子报告做数据统计并生成图表"),
                 ("top", "分析个人消费电子报告的品类出货量排行并生成柱状图")]:
    p("=" * 70)
    p(f"[{tag}] {msg}")
    d = chat(msg, "q_" + tag)
    p("toolLog: " + str(d.get("toolLog")))
    r = str(d.get("reply", ""))
    p("reply:")
    p(r[:1500])
    if "[CHART_JSON]" in r:
        p(">>> 发现 [CHART_JSON] 标记")
    if '<div class="chart"' in r:
        p(">>> 发现图表容器 <div class='chart'>")
    p("")

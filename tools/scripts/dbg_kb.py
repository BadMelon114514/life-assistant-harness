# -*- coding: utf-8 -*-
import json
import sys
import urllib.request
def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))
BASE = "http://127.0.0.1:8080"

# 1. 创建含数值表格的知识库文档
md = """# 202608 消费电子行业出货量
| 品类 | 2026H1出货量 | 同比增速 |
|------|------------|---------|
| 智能手机 | 约6.9亿部 | +3.2% |
| 平板电脑 | 约1.4亿台 | +5.1% |
| 笔记本 | 约1.1亿台 | +1.8% |
| 可穿戴 | 约2.7亿台 | +12.4% |
| 智能音箱 | 约0.8亿台 | -4.2% |
"""
b = json.dumps({"fileName": "202608消费电子报告.md", "content": md, "tags": "测试"}).encode("utf-8")
req = urllib.request.Request(BASE + "/api/knowledge/save-text", data=b, headers={"Content-Type": "application/json"})
with urllib.request.urlopen(req, timeout=20) as r:
    d = json.loads(r.read().decode("utf-8"))
p("创建文档: " + str(d.get("success")) + " key=" + str(d.get("doc", {}).get("docKey")))

# 2. 触发数据分析
b2 = json.dumps({"message": "用消费电子报告做数据分析并生成图表", "session": "dbgk"}).encode("utf-8")
req2 = urllib.request.Request(BASE + "/api/assistant/chat", data=b2, headers={"Content-Type": "application/json"})
with urllib.request.urlopen(req2, timeout=150) as r2:
    d2 = json.loads(r2.read().decode("utf-8"))
p("toolLog: " + str(d2.get("toolLog")))
reply = str(d2.get("reply", ""))
p("reply 尾部 400: " + reply[-400:])
p("图表div: " + str('<div class="chart"' in reply or "<div class='chart'" in reply))

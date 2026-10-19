# -*- coding: utf-8 -*-
import json
import sys
import urllib.request
def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))
# 测试知识库搜索是否命中
for kw in ["个人消费电子报告", "消费电子", "个人消费电子"]:
    try:
        b = json.dumps({"keyword": kw}).encode("utf-8")
        req = urllib.request.Request("http://127.0.0.1:8080/api/knowledge/search", data=b,
                                     headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=20) as r:
            d = json.loads(r.read().decode("utf-8"))
        res = d if isinstance(d, list) else d.get("docs", d.get("data", []))
        p(f"搜索[{kw}] => {len(res) if isinstance(res,list) else res} 条: " + str([x.get('fileName') for x in res[:3]] if isinstance(res,list) else str(res)[:100]))
    except Exception as e:
        p(f"搜索[{kw}] 异常: {e}")

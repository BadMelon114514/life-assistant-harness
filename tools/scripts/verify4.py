# -*- coding: utf-8 -*-
"""验证数据分析功能：analyze_data 工具生成统计与图表数据"""
import json
import sys
import urllib.request

BASE = "http://127.0.0.1:8080/api/assistant/chat"


def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))


def chat(message, session="verify4"):
    body = json.dumps({"message": message, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE, data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def show(tag, msg, keys=("reply", "toolLog", "error")):
    p("=" * 70)
    p(f"[{tag}] {msg}")
    try:
        data = chat(msg)
        for k in keys:
            if k in data and data[k] is not None and data[k] != "":
                v = str(data[k])
                if len(v) > 1800:
                    v = v[:1800] + f"...(共{len(str(data[k]))}字符)"
                p(f"  {k}: {v}")
        # 检查是否包含图表标记
        r = str(data.get("reply", ""))
        if "[CHART_JSON]" in r:
            p("  >>> 发现 [CHART_JSON] 标记（前端将渲染图表）")
        if '<div class="chart"' in r:
            p("  >>> 发现图表容器 <div class='chart'>（前端 ECharts 将渲染）")
    except Exception as e:
        p("  调用失败:" + str(e))
    sys.stdout.flush()


if __name__ == "__main__":
    show("describe", "对个人消费电子报告做数据分析并生成图表")
    show("top", "分析家用车报告，按品类统计出货量排行并生成柱状图", keys=("reply", "toolLog"))

# -*- coding: utf-8 -*-
"""验证长回复折叠总结：summary SSE 事件"""
import json
import sys
import urllib.request

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))

body = json.dumps({"message": "总结知识库里关于消费电子行业的内容并详细展开分析", "session": "vs1"}).encode("utf-8")
req = urllib.request.Request("http://127.0.0.1:8080/api/assistant/chat-stream",
                             data=body, headers={"Content-Type": "application/json"})
resp = urllib.request.urlopen(req, timeout=120)
buffer = ""
summary = None
final_len = 0
while True:
    raw = resp.read(1024)
    if not raw:
        break
    buffer += raw.decode("utf-8", errors="replace")
    while "\n\n" in buffer:
        block, buffer = buffer.split("\n\n", 1)
        ev = None
        data_lines = []
        for ln in block.split("\n"):
            if ln.startswith("event:"):
                ev = ln[6:].strip()
            elif ln.startswith("data:"):
                data_lines.append(ln[5:])
        dt = "\n".join(data_lines)
        if ev == "summary" and dt:
            try:
                summary = json.loads(dt)
                p("[OK] 收到 summary 事件: " + json.dumps(summary, ensure_ascii=False)[:200])
            except Exception as e:
                p("[WARN] summary 解析失败: " + str(e))
        elif ev == "done" and dt:
            final_len = len(dt)
p("=" * 70)
p("验证结果汇总：")
p("  summary 事件: " + ("✓ 已推送" if summary else "✗ 未收到"))
p("  done 回复长度: " + str(final_len) + " 字")
if summary:
    p("  总结内容: " + str(summary.get("summary", ""))[:150])
    p("  折叠阈值(220字): " + ("✓ 需要折叠" if final_len > 220 else "✗ 未超阈值"))

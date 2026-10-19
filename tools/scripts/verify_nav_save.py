# -*- coding: utf-8 -*-
"""验证：流式对话完成后历史是否保存（模拟切页后 loadChatHistory 读取）"""
import json
import sys
import urllib.request

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))

BASE = "http://127.0.0.1:8080"
sess = "nav_test_" + str(int(time := __import__("time").time()))

# 1. 完整流式对话（模拟用户在聊天助手发消息并等待完成）
b = json.dumps({"message": "请介绍一下智能家居的发展现状", "session": sess}).encode("utf-8")
req = urllib.request.Request(BASE + "/api/assistant/chat-stream", data=b,
                             headers={"Content-Type": "application/json"})
resp = urllib.request.urlopen(req, timeout=120)
buffer = ""
tokens = 0
done = None
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
        if ev == "token":
            tokens += len("\n".join(data_lines))
        elif ev == "done":
            done = "\n".join(data_lines)
p(f"流式完成: tokens={tokens}, done长度={len(done) if done else 0}")

# 2. 模拟前端保存助手回复（正常路径）
p = p  # noqa
b2 = json.dumps({"session": sess, "role": "assistant", "content": done}).encode("utf-8")
req2 = urllib.request.Request(BASE + "/api/chat/history", data=b2,
                              headers={"Content-Type": "application/json"})
with urllib.request.urlopen(req2, timeout=20) as r2:
    save = json.loads(r2.read().decode("utf-8"))
p("保存助手回复: " + str(save.get("success")))

# 3. 模拟切回后 loadChatHistory 读取
with urllib.request.urlopen(f"{BASE}/api/chat/history?session={sess}", timeout=20) as r3:
    msgs = json.loads(r3.read().decode("utf-8"))
p(f"切回后历史条数: {len(msgs)}")
for m in msgs:
    p(f"  {m.get('role')}: {str(m.get('content'))[:40]}")
ok = any(m.get("role") == "assistant" and len(str(m.get("content", ""))) > 100 for m in msgs)
p("判定: " + ("PASS - 历史含完整助手回复，切回可恢复气泡" if ok else "FAIL - 历史无助手回复，气泡将消失"))

# 清理
urllib.request.urlopen(urllib.request.Request(f"{BASE}/api/chat/history/session/{sess}", method="DELETE"), timeout=20)

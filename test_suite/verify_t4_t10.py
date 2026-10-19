# -*- coding: utf-8 -*-
"""复验 T4（错误前提）与 T10（多轮上下文）修复后行为"""
import json
import sys
import urllib.request

BASE = "http://127.0.0.1:8080"


def api_post(path, body):
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode("utf-8"))
    except Exception:
        return None


def stream(msg, session, timeout=150):
    api_post("/api/chat/history", {"role": "user", "content": msg, "session": session})
    b = json.dumps({"message": msg, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat-stream", data=b,
                                 headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=timeout)
    buf = ""
    tokens = []
    done = ""
    while True:
        raw = resp.read(2048)
        if not raw:
            break
        buf += raw.decode("utf-8", errors="replace")
        while "\n\n" in buf:
            block, buf = buf.split("\n\n", 1)
            ev = None
            data_lines = []
            for ln in block.split("\n"):
                if ln.startswith("event:"):
                    ev = ln[6:].strip()
                elif ln.startswith("data:"):
                    data_lines.append(ln[5:])
            if ev == "token":
                tokens.append("\n".join(data_lines))
            elif ev == "done":
                done = "\n".join(data_lines)
    joined = "".join(tokens)
    reply = done if len(done) >= len(joined) else joined
    api_post("/api/chat/history", {"role": "assistant", "content": reply, "session": session})
    return reply


# T4
reply = stream("太阳是不是从西边升起来的？", "verify_t4")
ok4 = "东" in reply and ("不是" in reply or "没有" in reply or "错" in reply)
print(f"T4 错误前提: {'PASS' if ok4 else 'FAIL'} -- {reply[:80]!r}")

# T10 多轮上下文
api_post("/api/chat/history?session=verify_t10", None)
stream("记住我的名字叫王小明", "verify_t10")
reply = stream("我刚才让你记住了什么？我名字叫什么？", "verify_t10")
ok10 = "王小明" in reply
print(f"T10 多轮上下文: {'PASS' if ok10 else 'FAIL'} -- {reply[:100]!r}")

sys.exit(0 if ok4 and ok10 else 1)

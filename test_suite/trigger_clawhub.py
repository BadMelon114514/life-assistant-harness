# -*- coding: utf-8 -*-
"""触发一次 ClawHub 调度并观察日志"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"


def stream(msg, session, timeout=180):
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
    return done if len(done) >= len(joined) else joined


reply = stream("下载一个小红书账号诊断的技能", "clawhub_diag1")
print("reply:", reply[:200])

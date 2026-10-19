# -*- coding: utf-8 -*-
import json, time, urllib.request
BASE = "http://127.0.0.1:8080/api/assistant/chat-stream"

def stream(msg, session, timeout=180):
    b = json.dumps({"message": msg, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE, data=b, headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=timeout)
    buf = ""
    tokens = []
    done = ""
    t0 = time.time()
    first = None
    while True:
        raw = resp.read(2048)
        if not raw:
            break
        if first is None:
            first = time.time() - t0
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
    return (time.time() - t0, first, done if len(done) >= len(joined) else joined)

for q in ["1+1等于几？", "1分钟后提醒我喝水"]:
    cost, first, reply = stream(q, "timing_" + q[:2])
    print(f"Q={q} 总耗时={cost:.1f}s 首包={first:.1f}s reply[:80]={reply[:80]!r}")

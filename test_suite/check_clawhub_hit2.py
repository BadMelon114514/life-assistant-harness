# -*- coding: utf-8 -*-
"""ClawHub 精确命中：下载一个小红书账号诊断的技能（与 xhs-note-analyst 名称一致）"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"


def api(path, method="GET", body=None, timeout=60):
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        c = r.read()
        return r.status, (json.loads(c.decode("utf-8")) if c else {})


def stream(msg, session, timeout=180):
    b = json.dumps({"message": msg, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat-stream", data=b,
                                 headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=timeout)
    buf = ""
    tokens = []
    done = ""
    events = []
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
            if ev:
                data = "\n".join(data_lines)
                events.append((ev, data))
                if ev == "token":
                    tokens.append(data)
                elif ev == "done":
                    done = data
    joined = "".join(tokens)
    return (done if len(done) >= len(joined) else joined), events


reply, events = stream("下载一个小红书账号诊断的技能", "clawhub_xhs2")
names = sorted(set(e for e, _ in events))
print(f"事件={names}")
print(f"回复={reply[:300]!r}")

st, skills = api("/api/skills")
cand = [s for s in skills if s.get("category") == "ClawHub" or "小红书" in str(s.get("name", ""))]
print(f"安装结果: {[(s.get('name'), s.get('category'), str(s.get('description'))[:40]) for s in cand]}")

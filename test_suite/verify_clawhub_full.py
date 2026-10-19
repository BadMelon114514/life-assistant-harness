# -*- coding: utf-8 -*-
"""删除现有同名技能后重测完整 ClawHub 安装"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"


def api(path, method="GET", timeout=30):
    req = urllib.request.Request(BASE + path, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        c = r.read()
        return json.loads(c.decode("utf-8")) if c else {}


skills = api("/api/skills")
for s in skills:
    if str(s.get("name")) == "小红书账号诊断":
        api(f"/api/skills/{s['id']}", method="DELETE")
        print("deleted", s.get("name"))


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


reply = stream("下载一个小红书账号诊断的技能", "clawhub_xhs3")
print("reply:", reply[:200])

skills = api("/api/skills")
cand = [s for s in skills if "小红书" in str(s.get("name", ""))]
print("result:", [(s.get("name"), s.get("category"), str(s.get("description"))[:40]) for s in cand])

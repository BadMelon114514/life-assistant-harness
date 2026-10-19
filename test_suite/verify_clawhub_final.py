# -*- coding: utf-8 -*-
"""最终验证：完整 ClawHub 链路（先清理同名与测试残留）"""
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


# 清理测试残留技能
skills = api("/api/skills")[1]
for s in skills:
    if str(s.get("name")) in ("小红书账号诊断", "xhs-note-analyst", "翻译技能", "小红书笔记分析"):
        api(f"/api/skills/{s['id']}", method="DELETE")
        print("cleaned:", s.get("name"))

# 完整链路：ClawHub 命中并安装
reply = stream("下载一个小红书账号诊断的技能", "clawhub_final1")
print("调度回复:", reply[:150])

skills = api("/api/skills")[1]
cand = [s for s in skills if "小红书" in str(s.get("name", ""))]
for s in cand:
    print(f"安装结果: name={s.get('name')} category={s.get('category')} desc={str(s.get('description'))[:40]}")

# 重复安装 → 查重跳过
reply2 = stream("下载一个小红书账号诊断的技能", "clawhub_final2")
print("重复安装回复:", reply2[:120])

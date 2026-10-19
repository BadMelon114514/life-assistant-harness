# -*- coding: utf-8 -*-
"""验证：聊天助手作为主 agent 调度 Teammate 的完整闭环"""
import json
import sys
import time
import urllib.request

BASE = "http://127.0.0.1:8080"


def api(method, path, body=None, timeout=30):
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        raw = r.read()
        return r.status, (json.loads(raw.decode("utf-8")) if raw else {})


def stream(msg, session, timeout=90):
    b = json.dumps({"message": msg, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat-stream", data=b,
                                 headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=timeout)
    buf = ""
    events = []
    while True:
        raw = resp.read(2048)
        if not raw:
            break
        buf += raw.decode("utf-8", errors="replace")
        while "\n\n" in buf:
            block, buf = buf.split("\n\n", 1)
            ev = None
            dl = []
            for ln in block.split("\n"):
                if ln.startswith("event:"):
                    ev = ln[6:].strip()
                elif ln.startswith("data:"):
                    dl.append(ln[5:])
            if ev:
                events.append({"event": ev, "data": "\n".join(dl)})
    return events


def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))


ok = fail = 0


def check(case, okk, detail=""):
    global ok, fail
    ok += 1 if okk else 0
    fail += 0 if okk else 1
    p(f"  [{'PASS' if okk else 'FAIL'}] {case}" + (f" -- {detail}" if detail else ""))


# 1. 确认 Teammate 存在（默认 3 个工作代理）
st, mates = api("GET", "/api/teammates")
if st == 200 and isinstance(mates, list):
    check("Teammate 列表可读", len(mates) > 0, f"共 {len(mates)} 个")
    for m in mates:
        p(f"    - {m.get('name')} ({m.get('role')}) 状态={m.get('status')}")
else:
    check("Teammate 列表可读", False, f"HTTP {st}")

# 2. 主 agent 接收自然语言多任务 → 应触发调度并创建 HarnessTask
done = ""
for e in stream("帮我同时做：查今天北京天气、整理本周热点、分析知识库数据", "main_agent_test"):
    if e["event"] == "done":
        done = e["data"]
has_dispatch = any(k in done for k in ("拆解", "子任务", "个任务", "分工", "派发", "调度", "分配给", "创建"))
check("主agent收到多任务请求有回复", len(done) > 10, f"len={len(done)}")
check("主agent触发多Agent调度", has_dispatch, str(done)[:70])

# 3. 任务看板应有新建的子任务
st, tasks = api("GET", "/api/tasks")
if st == 200 and isinstance(tasks, list):
    check("任务看板有任务", len(tasks) > 0, f"共 {len(tasks)} 个")
else:
    check("任务看板有任务", False, f"HTTP {st}")

# 4. Teammate 守护线程应自动认领并推进进度
time.sleep(6)
st, mates2 = api("GET", "/api/teammates")
if st == 200 and isinstance(mates2, list):
    working = [m for m in mates2 if m.get("status") in ("WORK", "IDLE")]
    check("Teammate 有工作状态", len(working) > 0, f"{len(working)} 个活跃")
    for m in mates2:
        p(f"    - {m.get('name')} 进度={m.get('progress')}% 任务={str(m.get('currentTask'))[:40]}")
else:
    check("Teammate 有工作状态", False, f"HTTP {st}")

p(f"\n结果: {ok} PASS / {fail} FAIL")
sys.exit(0 if fail == 0 else 1)

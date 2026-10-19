# -*- coding: utf-8 -*-
"""验证多 Agent 意图识别对典型请求的触发"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"

messages = [
    ("同时多任务", "帮我同时做：查今天北京天气、整理本周热点、分析知识库数据"),
    ("显式分工", "让日程助理规划明天行程，让资讯助手搜集AI动态，让数据助手分析知识库文档"),
    ("分头行动", "分头行动：查天气、写周报、分析数据，全部完成"),
    ("普通单任务", "现在几点了"),
]


def stream(msg, session):
    b = json.dumps({"message": msg, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat-stream", data=b,
                                 headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=90)
    buf = ""
    done = ""
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
            if ev == "done":
                done = "\n".join(dl)
    return done


import sys


def safe_print(s):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))


for tag, m in messages:
    done = stream(m, f"vma_{tag}")
    has_split = any(k in done for k in ("拆解", "子任务", "个任务", "分工", "派发", "调度", "分配给"))
    safe_print(f"[{tag}] 触发多Agent={has_split}")
    safe_print(f"    reply: {done[:90]}")
    safe_print("")

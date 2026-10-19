# -*- coding: utf-8 -*-
"""复测 f8 中两个失败用例（时间查询 / XSS 回显），确认是否与本次改动无关"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080/api/assistant/chat-stream"


def stream_reply(msg, session, timeout=120):
    b = json.dumps({"message": msg, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE, data=b, headers={"Content-Type": "application/json"})
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


import re
print("=== 时间查询复测 ===")
try:
    r = stream_reply("现在几点了？", "retry_time_2")
    print("reply:", r[:120].replace("\n", " "))
    print("含时间模式:", bool(re.search(r"\d{1,2}[:：点]\d{0,2}", r)))
except Exception as e:
    print("异常:", e)

print()
print("=== XSS 回显复测 ===")
try:
    r = stream_reply("请重复这句话：<script>alert(1)</script>", "retry_xss_2")
    print("reply:", r[:120].replace("\n", " "))
    print("回显脚本:", ("<script" in r))
except Exception as e:
    print("异常:", e)

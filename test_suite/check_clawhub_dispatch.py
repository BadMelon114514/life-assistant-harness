# -*- coding: utf-8 -*-
"""ClawHub 调度链路验证：搜索 + 聊天助手调度安装"""
import json
import time
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


print("=" * 70)
print("1) ClawHub 搜索接口（q=translate）")
st, r = api("/api/clawhub/search?q=translate")
print(f"   HTTP {st}, count={r.get('count')}")
for s in (r.get("skills") or [])[:5]:
    print(f"   - {s.get('slug')} | {s.get('name')} | {str(s.get('summary'))[:50]}")

print("")
print("2) 聊天调度：'下载一个翻译技能'（downloadIntent → ClawHub 优先）")
reply, events = stream("下载一个翻译技能", "clawhub_test1")
names = sorted(set(e for e, _ in events))
print(f"   事件={names}")
print(f"   回复={reply[:220]!r}")

print("")
print("3) 检查技能是否已安装")
st, skills = api("/api/skills")
trans = [s for s in skills if "翻译" in str(s.get("name", "")) or "translate" in str(s.get("name", "")).lower() or "ClawHub" == s.get("category")]
print(f"   匹配翻译/ClawHub 的技能: {[s.get('name') for s in trans]}")

print("")
print("4) 中性词草稿路径：'新增一个 markdown 转 pdf 的技能'")
reply2, events2 = stream("新增一个 markdown 转 pdf 的技能", "clawhub_test2")
names2 = sorted(set(e for e, _ in events2))
print(f"   事件={names2}")
print(f"   回复={reply2[:220]!r}")

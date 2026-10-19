# -*- coding: utf-8 -*-
"""上下文存储冒烟测试"""
import json
import urllib.request

BASE = "http://127.0.0.1:8765"


def api(path, body=None, method=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers={"Content-Type": "application/json"}, method=method or ("POST" if data else "GET"))
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


room = "smoke_room"
for i, role in enumerate(["user", "assistant", "user"]):
    api("/context/append", {"room": room, "role": role, "content": "消息 %d：这是一个测试上下文条目" % i, "agent": "agentA"})
print("append ok")
print("stats:", json.dumps(api("/context/stats?room=" + room), ensure_ascii=False)[:300])
loaded = api("/context/load", {"room": room, "budget": 1000})
print("load:", len(loaded.get("items", [])), "items, used", loaded.get("used_chars"))
print("sink:", api("/context/sink", {"room": room, "l1_cap": 1}))
c1 = api("/snapshot/commit", {"room": room, "message": "first"})
print("commit:", c1["commit_hash"][:12], "items", c1["item_count"])
api("/context/append", {"room": room, "role": "user", "content": "新增的一条"})
c2 = api("/snapshot/commit", {"room": room, "message": "second"})
d = api("/snapshot/diff?room=" + room + "&from=" + c1["commit_hash"] + "&to=" + c2["commit_hash"])
print("diff:", d["summary"])
rb = api("/snapshot/rollback", {"room": room, "commit": c1["commit_hash"]})
print("rollback:", {k: rb[k] for k in ("restored", "inserted", "hidden")})
print("bb:", api("/blackboard/put", {"room": room, "key": "plan", "value": "步骤A", "agent": "agentA"}))
print("bb get:", api("/blackboard/get?room=" + room + "&key=plan")["bvalue"])
print("rs:", api("/resource/publish", {"kind": "skill", "name": "translator", "payload": {"v": "1.0"}, "actor": "agentA"}))
print("rs v2:", api("/resource/publish", {"kind": "skill", "name": "translator", "payload": {"v": "2.0"}}))
print("rs versions:", [(v["version"], v["is_active"]) for v in api("/resource/versions?kind=skill&name=translator")])
doc = api("/doc/import", {"name": "测试文档", "text": "# 第一章 概述\n本章介绍上下文存储。\n## 1.1 三级存储\n热数据放 L1 缓存，冷数据下沉 L3。\n# 第二章 快照\nGit 式提交与回滚。"})
print("doc:", doc["section_count"], "sections")
print("search:", [(h["title"], h["score"]) for h in api("/doc/search?q=快照")["hits"]])
print("read:", api("/doc/section?id=" + str(doc["sections"][1]["id"]))["content"][:40])

# -*- coding: utf-8 -*-
"""端到端验证：生成 Skill 草稿 -> skill_draft 事件 -> 前端结构(直接属性) -> import-draft -> 数据库"""
import json
import sys
import urllib.request

BASE = "http://127.0.0.1:8080"

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))

def stream_draft(msg, session):
    body = json.dumps({"message": msg, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat-stream", data=body,
                                 headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=120)
    buffer = ""
    draft = None
    while True:
        raw = resp.read(1024)
        if not raw:
            break
        buffer += raw.decode("utf-8", errors="replace")
        while "\n\n" in buffer:
            block, buffer = buffer.split("\n\n", 1)
            ev = None
            data_lines = []
            for ln in block.split("\n"):
                if ln.startswith("event:"):
                    ev = ln[6:].strip()
                elif ln.startswith("data:"):
                    data_lines.append(ln[5:])
            dt = "\n".join(data_lines)
            if ev == "skill_draft" and dt:
                try:
                    draft = json.loads(dt)
                except Exception:
                    pass
    return draft

def import_draft(d):
    """模拟前端 importGeneratedSkill 提交（前端读取 draft.data || draft 直接属性）"""
    payload = {"name": d.get("name"), "category": d.get("category"),
               "description": d.get("description"), "content": d.get("content")}
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/skills/import-draft", data=body,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))

# 1. 生成草稿
p("=" * 70)
p("[1] 请求生成技能草稿")
draft = stream_draft("帮我写一个公网IP位置查询的技能", "vi1")
if not draft:
    p("  ✗ 未收到 skill_draft 事件")
    sys.exit(1)
p("  ✓ 收到 skill_draft 事件")
p("  字段: " + json.dumps({k: draft.get(k) for k in ("name", "category", "description") if k in draft}, ensure_ascii=False)[:200])
p("  有 name: " + str(bool(draft.get("name"))))
p("  有 content: " + str(bool(draft.get("content"))))

# 2. 模拟前端导入（直接属性结构）
p("-" * 70)
p("[2] 调用 /api/skills/import-draft（前端读取的直接属性）")
r = import_draft(draft)
p("  响应: " + json.dumps(r, ensure_ascii=False)[:300])
if r.get("success"):
    saved = r.get("skill", {})
    p("  ✓ 导入成功! 新 Skill: " + str(saved.get("name")) + " (id=" + str(saved.get("id")) + ")")
else:
    p("  ✗ 导入失败: " + str(r.get("error")) + " blocked=" + str(r.get("blocked")))

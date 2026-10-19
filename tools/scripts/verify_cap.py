# -*- coding: utf-8 -*-
"""验证每轮能力分析：有工具→none；LLM可直接做→none；能力缺口→create_skill"""
import json
import sys
import urllib.request

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))

BASE = "http://127.0.0.1:8080"
def chat(msg, session):
    b = json.dumps({"message": msg, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat", data=b,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read().decode("utf-8"))

def classify(reply):
    r = str(reply)
    if "draftSkill" in str(reply):
        return "create_skill"
    if "已生成" in r and ("技能" in r or "Skill" in r):
        return "create_skill"
    return "none"

cases = [
    ("能力已覆盖(天气有工具)", "获取天气", "expect_none"),
    ("能力已覆盖(数据分析有工具)", "用个人消费电子报告做数据分析", "expect_none"),
    ("LLM可直接做(翻译)", "帮我翻译这段文字：hello world", "expect_none"),
    ("LLM可直接做(写文案)", "帮我写一段新品手机的推广文案", "expect_none"),
    ("能力缺口(翻译功能)", "我需要一个批量翻译的功能", "expect_create_skill"),
]
for tag, msg, expect in cases:
    p("=" * 66)
    p(f"[{tag}] {msg}")
    try:
        d = chat(msg, "cap" + str(len(cases)))
        r = str(d.get("reply", ""))[:200]
        has_draft = d.get("draftSkill") is not None
        tl = str(d.get("toolLog", ""))
        p("  reply: " + r.replace("\n", " "))
        p("  toolLog: " + tl[:80])
        got = "create_skill" if has_draft else "none"
        ok = (expect == "expect_create_skill" and got == "create_skill") or (expect == "expect_none" and got == "none")
        p(f"  期望={expect} 实际={got} => {'PASS' if ok else 'FAIL'}")
    except Exception as e:
        p("  异常: " + str(e)[:100])

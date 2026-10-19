# -*- coding: utf-8 -*-
"""
阶段13 验收：上下文存储方案（类 OpenViking，Python + MySQL）
  A. 服务与表结构
  B. 三级存储：冷热分层、下沉、预算控制送入模型的数据量
  C. 权重：按调用次数累积，高频提升回 L1
  D. Git 式快照：commit / diff / rollback，改写不丢失
  E. 多 Agent 黑板：写入即时可见、版本递增
  F. 资源版本：MCP/Skill 多版本、激活切换、旧版本回滚、sync 同步
  G. 文档分章摘要：一级/二级摘要、检索先命中摘要再回读原文
"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8765"
RESULTS = []


def check(case, ok, detail=""):
    RESULTS.append((case, "PASS" if ok else "FAIL", detail))
    print(("  [PASS] " if ok else "  [FAIL] ") + case + (" -- " + detail if detail else ""))


def api(path, body=None, timeout=60):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers={"Content-Type": "application/json"},
                                 method="POST" if data is not None else "GET")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except Exception:
            return {"success": False, "error": "HTTP %s" % e.code}


def room(tag):
    return "f13_%s_%d" % (tag, int(time.time() * 1000))


def a_service():
    print("=== A. 服务与表结构 ===")
    r = api("/health")
    check("A 服务健康", r.get("success") is True, str(r.get("service")))
    tables = r.get("tables") or []
    need = ["ctx_item", "ctx_snapshot", "ctx_snapshot_item", "ctx_blackboard", "ctx_resource", "ctx_doc", "ctx_doc_section"]
    check("A 7 张核心表就绪", all(t in tables for t in need), "tables=%d" % len(tables))
    check("A 统一 MCP 入口可用（GET /api/mcp/tool）", api("/api/mcp/tool").get("success") is True)


def b_tiering():
    print("=== B. 三级存储与下沉 ===")
    rm = room("tier")
    for i in range(30):
        api("/context/append", {"room": rm, "role": "user", "content": "条目%02d " % i + "x" * 50, "agent": "a"})
    st = api("/context/stats?room=" + rm)["result"]
    check("B 新写入默认在 L1 热层", st["L1"]["count"] == 30, "L1=%d" % st["L1"]["count"])
    sunk = api("/context/sink", {"room": rm, "l1_cap": 10, "l2_cap": 5})["result"]
    check("B 冷数据逐级下沉 L1→L2", sunk["L1_to_L2"] == 20, str(sunk))
    st2 = api("/context/stats?room=" + rm)["result"]
    check("B 分层结果 L1=10 / L2=5 / L3=15",
          st2["L1"]["count"] == 10 and st2["L2"]["count"] == 5 and st2["L3"]["count"] == 15,
          "L1=%d L2=%d L3=%d" % (st2["L1"]["count"], st2["L2"]["count"], st2["L3"]["count"]))
    loaded = api("/context/load", {"room": rm, "budget": 600})["result"]
    used = loaded["used_chars"]
    check("B 预算控制：送入模型的数据量不超预算（容许最后一条）", used <= 600 + 120, "used=%d" % used)
    layers = set(it["layer"] for it in loaded["items"])
    check("B 默认不装入 L3 冷数据", "L3" not in layers, "layers=%s" % sorted(layers))
    loaded_cold = api("/context/load", {"room": rm, "budget": 100000, "include_cold": True})["result"]
    check("B include_cold 时可按需装入冷数据", any(it["layer"] == "L3" for it in loaded_cold["items"]),
          "items=%d" % len(loaded_cold["items"]))


def c_weight():
    print("=== C. 权重与高频提升 ===")
    rm = room("weight")
    ids = []
    for i in range(6):
        r = api("/context/append", {"room": rm, "role": "user", "content": "权重条目%d " % i + "y" * 30})
        ids.append(r["id"])
    api("/context/sink", {"room": rm, "l1_cap": 2, "l2_cap": 10})
    before = api("/context/stats?room=" + rm)["result"]
    target = ids[0]
    api("/context/promote", {"room": rm, "item_ids": [target]})
    after = api("/context/stats?room=" + rm)["result"]
    check("C 权重提升后条目回到 L1", after["L1"]["count"] > before["L1"]["count"], "L1 %d→%d" % (before["L1"]["count"], after["L1"]["count"]))
    # 载入多次 → access_count 递增（权重来源）
    api("/context/load", {"room": rm, "budget": 10000})
    load2 = api("/context/load", {"room": rm, "budget": 10000})["result"]
    hit = [it for it in load2["items"] if it["id"] == target]
    check("C 被调用次数（权重）随载入递增", bool(hit) and hit[0]["access_count"] >= 1,
          "access_count=%s" % (hit[0]["access_count"] if hit else "n/a"))


def d_snapshot():
    print("=== D. Git 式快照 diff / rollback ===")
    rm = room("snap")
    a = api("/context/append", {"room": rm, "role": "user", "content": "初始内容 A"})["id"]
    b = api("/context/append", {"room": rm, "role": "assistant", "content": "初始内容 B"})["id"]
    c1 = api("/snapshot/commit", {"room": rm, "message": "基线"})["result"]
    check("D 快照提交成功（含哈希与条目数）", len(c1["commit_hash"]) == 40 and c1["item_count"] == 2, c1["commit_hash"][:10])
    # 改写 A、新增 C、删除 B
    api("/context/update", {"room": rm, "id": a, "content": "改写后的内容 A2"})
    d_id = api("/context/append", {"room": rm, "role": "user", "content": "新增内容 C"})["id"]
    api("/context/remove", {"room": rm, "id": b})
    c2 = api("/snapshot/commit", {"room": rm, "message": "改写"})["result"]
    check("D 快照链 parent 指向上一提交", c2["parent_hash"] == c1["commit_hash"])
    diff = api("/snapshot/diff?room=%s&from=%s&to=%s" % (rm, c1["commit_hash"], c2["commit_hash"]))["result"]
    check("D diff 识别新增", diff["summary"]["added"] == 1, str(diff["summary"]))
    check("D diff 识别删除", diff["summary"]["removed"] == 1, str(diff["summary"]))
    check("D diff 识别修改", diff["summary"]["modified"] == 1, str(diff["summary"]))
    mod = diff["modified"][0]
    check("D diff 修改包含前后内容", mod["before"] == "初始内容 A" and mod["after"] == "改写后的内容 A2", mod["after"])
    rb = api("/snapshot/rollback", {"room": rm, "commit": c1["commit_hash"]})["result"]
    check("D 回滚恢复被改写内容", rb["restored"] >= 2, str(rb))
    # 校验恢复
    loaded = api("/context/load", {"room": rm, "budget": 100000})["result"]
    contents = {it["id"]: it["content"] for it in loaded["items"]}
    check("D 回滚后改写条目内容还原", contents.get(a) == "初始内容 A", str(contents.get(a)))
    check("D 回滚后新增条目被隐藏", d_id not in contents)
    log = api("/snapshot/log?room=" + rm)["result"]
    check("D 提交日志完整", len(log) >= 3 and log[0]["message"].startswith("rollback to"), "commits=%d" % len(log))


def e_blackboard():
    print("=== E. 多 Agent 黑板 ===")
    rm = room("bb")
    w = api("/blackboard/put", {"room": rm, "key": "shared_plan", "value": "第一步：检索知识库", "agent": "agentA"})["result"]
    check("E AgentA 写入黑板", w["version"] == 1 and w["updated_by"] == "agentA", str(w))
    got = api("/blackboard/get?room=%s&key=shared_plan" % rm)["result"]
    check("E AgentB 立即读到 AgentA 的写入", got["bvalue"] == "第一步：检索知识库", got["bvalue"])
    w2 = api("/blackboard/put", {"room": rm, "key": "shared_plan", "value": "更新：第二步", "agent": "agentB"})["result"]
    check("E 黑板版本递增（即时可见依据）", w2["version"] == 2, str(w2))
    got2 = api("/blackboard/get?room=%s&key=shared_plan" % rm)["result"]
    check("E AgentA 立即读到 AgentB 的更新", got2["bvalue"] == "更新：第二步" and got2["version"] == 2, str(got2))
    keys = [k["bkey"] for k in api("/blackboard/list?room=" + rm)["result"]]
    check("E 黑板可列出共享键", "shared_plan" in keys, str(keys))


def f_resources():
    print("=== F. MCP / Skill 版本管理 ===")
    name = "f13_translator_%d" % int(time.time() * 1000)
    p1 = api("/resource/publish", {"kind": "skill", "name": name, "payload": {"v": "1.0", "prompt": "旧版"}})["result"]
    check("F 发布版本 1", p1["version"] == 1 and p1["active"] is True, str(p1))
    p2 = api("/resource/publish", {"kind": "skill", "name": name, "payload": {"v": "2.0", "prompt": "新版"}})["result"]
    check("F 发布版本 2 并自动激活", p2["version"] == 2, str(p2))
    vs = api("/resource/versions?kind=skill&name=" + name)["result"]
    check("F 旧版本保留（2 个版本）", len(vs) == 2, "versions=%s" % [v["version"] for v in vs])
    actives = [v["version"] for v in vs if v["is_active"] == 1]
    check("F 当前仅 v2 生效", actives == [2], str(actives))
    rb = api("/resource/rollback", {"kind": "skill", "name": name, "version": 1})["result"]
    check("F 回滚到 v1", rb["active_version"] == 1, str(rb))
    vs2 = api("/resource/versions?kind=skill&name=" + name)["result"]
    check("F 回滚后 v2 仍保留（可再切回）", len(vs2) == 2 and [v["version"] for v in vs2 if v["is_active"] == 1] == [1], str(vs2))
    time.sleep(1.1)
    sync = api("/resource/sync")["result"]
    names = [r["name"] for r in sync["resources"] if r["kind"] == "skill"]
    check("F sync 将激活版本同步给所有 Agent", name in names and len(sync["synced_at"]) > 0,
          "resources=%d" % len(sync["resources"]))


def g_docs():
    print("=== G. 文档分章摘要与检索 ===")
    text = (
        "# 第一章 上下文存储总览\n"
        "上下文存储解决长期运行助手的上下文窗口有限问题，借鉴计算机存储层次。\n"
        "## 1.1 三级存储\n"
        "按冷热把上下文分为 L1 缓存、L2 内存、L3 外存三级，冷数据逐级下沉。\n"
        "## 1.2 权重与下沉\n"
        "按被调用次数计算权重，高频内容常驻，低频内容加速下沉到外存。\n"
        "# 第二章 快照与回滚\n"
        "借鉴 Git 的版本管理，会话结束时提交快照，记忆被改写后可 diff、可回滚。\n"
    )
    doc = api("/doc/import", {"name": "上下文存储设计稿", "text": text})["result"]
    check("G 导入按标题切分章节", doc["section_count"] == 4, "sections=%d" % doc["section_count"])
    secs = {s["title"]: s for s in doc["sections"]}
    check("G 一级章节存在", "第一章 上下文存储总览" in secs and "第二章 快照与回滚" in secs, str(list(secs)))
    check("G 二级章节归属一级章节", secs["1.1 三级存储"]["parent_id"] == secs["第一章 上下文存储总览"]["id"])
    check("G 一级/二级均生成摘要",
          all(s["summary"] for s in doc["sections"]), "样例: " + (secs["1.1 三级存储"]["summary"] or "")[:30])
    hits = api("/doc/search?q=" + urllib.request.quote("三级存储"))["hits"]
    check("G 检索先命中摘要", bool(hits) and "三级存储" in (hits[0]["title"] + hits[0]["summary"]),
          "top=%s score=%s" % (hits[0]["title"], hits[0]["score"]))
    content = api("/doc/section?id=%d" % hits[0]["id"])["result"]["content"]
    summary = api("/doc/section?id=%d" % hits[0]["id"])["result"]["summary"]
    check("G 命中摘要后回读原文", "冷数据逐级下沉" in content and content != summary,
          "原文 %d 字 / 摘要 %d 字" % (len(content), len(summary or "")))
    hits2 = api("/doc/search?q=" + urllib.request.quote("回滚"))["hits"]
    check("G 关键词命中二级/一级摘要", any("回滚" in (h["title"] + h["summary"]) for h in hits2), "hits=%d" % len(hits2))


if __name__ == "__main__":
    print("=" * 70)
    print("阶段13 验收：上下文存储方案（三级存储 / 权重 / 快照 / 黑板 / 版本 / 分章摘要）")
    print("目标:", BASE)
    print("=" * 70)
    try:
        if api("/health", timeout=5).get("success") is not True:
            print("服务未就绪")
            sys.exit(1)
    except Exception as e:
        print("无法连接上下文存储服务: %s" % e)
        sys.exit(1)
    print("服务就绪\n")
    a_service(); print()
    b_tiering(); print()
    c_weight(); print()
    d_snapshot(); print()
    e_blackboard(); print()
    f_resources(); print()
    g_docs(); print()
    total_pass = sum(1 for r in RESULTS if r[1] == "PASS")
    total_fail = sum(1 for r in RESULTS if r[1] == "FAIL")
    print("=" * 70)
    print("阶段13 验收汇总: %d PASS / %d FAIL (共 %d 项)" % (total_pass, total_fail, len(RESULTS)))
    for case, result, detail in RESULTS:
        if result == "FAIL":
            print("  FAIL: %s -- %s" % (case, detail))
    sys.exit(0 if total_fail == 0 else 1)

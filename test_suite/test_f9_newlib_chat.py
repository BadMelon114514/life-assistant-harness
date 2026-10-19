# -*- coding: utf-8 -*-
"""
验收测试（阶段9）：新增常用 MCP/Skill 库 + 聊天气泡导航修复（后端部分）
  A. 内置库新增项导入（POST /api/skills/import-openclaw、POST /api/mcp/import-openclaw）
     验证新增条目被导入、已有条目被跳过（不重复、幂等）
  B. 管理页 API 无重复且包含全部新增条目
  C. 聊天历史持久化闭环（user/assistant 落库可读）
  D. 前端资源已部署气泡修复逻辑（navigate 恢复条件 + 中间态替换）
"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
RESULTS = []

NEW_SKILLS = ["weekly-report", "meeting-notes", "ppt-outline", "excel-formula",
              "promotion-plan", "customer-service", "product-seo", "review-reply",
              "data-cleaning", "sql-helper", "decision-matrix", "interview-prep"]
NEW_MCPS = ["brave-search", "gitlab", "google-maps", "postgres", "sqlite",
            "sentry", "slack", "everart", "redis", "notion", "youtube"]


def p(s=""):
    print(s)


def check(case, ok, detail=""):
    RESULTS.append((case, "PASS" if ok else "FAIL", detail))
    p(f"  [{'PASS' if ok else 'FAIL'}] {case}" + (f" -- {detail}" if detail else ""))


def api(method, path, body=None, timeout=60, raw=False):
    url = BASE + path
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            c = r.read()
            return r.status, (c if raw else (json.loads(c.decode("utf-8")) if c else {}))
    except urllib.error.HTTPError as e:
        c = e.read()
        try:
            return e.code, json.loads(c.decode("utf-8"))
        except Exception:
            return e.code, {}
    except urllib.error.URLError as e:
        return -1, {"error": str(e)}


# 本次测试实际导入的 MCP 数量（幂等重跑时为 0）
MCP_IMPORTED_NOW = -1


def a_import():
    global MCP_IMPORTED_NOW
    p("=== A. 内置库新增项导入（含去重） ===")
    # 首次导入：新增项全部导入；重复执行：全部跳过（幂等、无重复）
    st, r = api("POST", "/api/skills/import-openclaw")
    if st == 200 and r.get("success"):
        imp = r.get("importedCount")
        skp = r.get("skippedCount")
        check("A Skill 内置库导入接口成功", True, f"imported={imp} skipped={skp}")
        if imp == len(NEW_SKILLS):
            imported_names = [i.get("name") for i in r.get("imported", [])]
            check("A 新增 12 个常用技能已导入且名称完全匹配",
                  sorted(imported_names) == sorted(NEW_SKILLS),
                  "导入: " + ", ".join(sorted(imported_names)))
        elif imp == 0:
            check("A 新增技能此前已导入（本次全部跳过）", True, f"skipped={skp}（幂等重跑）")
        else:
            check("A 新增技能导入数量符合预期", False, f"imported={imp}（期望 {len(NEW_SKILLS)} 或 0）")
        st2, r2 = api("POST", "/api/skills/import-openclaw")
        check("A 重复导入全部跳过（无重复）",
              st2 == 200 and r2.get("importedCount") == 0,
              f"再次导入 imported={r2.get('importedCount')} skipped={r2.get('skippedCount')}")
    else:
        check("A Skill 内置库导入接口成功", False, f"HTTP {st} {r}")

    st, r = api("POST", "/api/mcp/import-openclaw")
    if st == 200 and r.get("success"):
        imp = r.get("importedCount")
        skp = r.get("skippedCount")
        MCP_IMPORTED_NOW = imp
        check("A MCP 内置库导入接口成功", True, f"imported={imp} skipped={skp}")
        if imp == len(NEW_MCPS):
            imported_names = [i.get("name") for i in r.get("imported", [])]
            check("A 新增 11 个常用 MCP 已导入且名称完全匹配",
                  sorted(imported_names) == sorted(NEW_MCPS),
                  "导入: " + ", ".join(sorted(imported_names)))
        elif imp == 0:
            check("A 新增 MCP 此前已导入（本次全部跳过）", True, f"skipped={skp}（幂等重跑）")
        else:
            check("A 新增 MCP 导入数量符合预期", False, f"imported={imp}（期望 {len(NEW_MCPS)} 或 0）")
        st2, r2 = api("POST", "/api/mcp/import-openclaw")
        check("A MCP 重复导入全部跳过（无重复）",
              st2 == 200 and r2.get("importedCount") == 0,
              f"再次导入 imported={r2.get('importedCount')} skipped={r2.get('skippedCount')}")
    else:
        check("A MCP 内置库导入接口成功", False, f"HTTP {st} {r}")


def b_listing():
    p("=== B. 管理页 API 列表无重复 ===")
    st, skills = api("GET", "/api/skills")
    if st == 200 and isinstance(skills, list):
        names = [s.get("name") for s in skills]
        dup = [n for n in set(names) if names.count(n) > 1]
        check("B 技能列表无重复名称", not dup, f"共 {len(names)} 个, 重复 {dup}")
        missing = [n for n in NEW_SKILLS if n not in names]
        check("B 技能列表包含全部 12 个新增技能", not missing, f"缺失 {missing}")
    else:
        check("B 技能列表接口可用", False, f"HTTP {st}")

    st, mcps = api("GET", "/api/mcp")
    if st == 200 and isinstance(mcps, list):
        names = [m.get("name") for m in mcps]
        dup = [n for n in set(names) if names.count(n) > 1]
        check("B MCP 列表无重复名称", not dup, f"共 {len(names)} 个, 重复 {dup}")
        missing = [n for n in NEW_MCPS if n not in names]
        check("B MCP 列表包含全部 11 个新增 MCP", not missing, f"缺失 {missing}")
        # 默认停用仅在「本次测试实际导入」时校验；幂等重跑（已导入过、用户可能已启用）跳过
        if MCP_IMPORTED_NOW == len(NEW_MCPS):
            actives = [m.get("name") for m in mcps if m.get("name") in NEW_MCPS and (m.get("status") or "").lower() == "active"]
            check("B 新增 MCP 默认停用（内置库约定）", not actives, f"异常激活: {actives}")
        else:
            check("B 新增 MCP 默认停用（幂等重跑跳过，用户可能已启用）", True, "")
    else:
        check("B MCP 列表接口可用", False, f"HTTP {st}")


def c_chat_history():
    p("=== C. 聊天历史持久化闭环 ===")
    sess = "accept9_" + str(int(time.time()))
    api("POST", "/api/chat/history", {"role": "user", "content": "验收测试问题", "session": sess})
    api("POST", "/api/chat/history", {"role": "assistant", "content": "验收测试回答", "session": sess})
    st, msgs = api("GET", "/api/chat/history?session=" + sess)
    if st == 200 and isinstance(msgs, list) and len(msgs) == 2:
        check("C 会话历史保存并可读取", True, f"{len(msgs)} 条")
        check("C 历史顺序 user→assistant",
              msgs[0].get("role") == "user" and msgs[1].get("role") == "assistant",
              [m.get("role") for m in msgs])
    else:
        check("C 会话历史保存并可读取", False, f"HTTP {st} 条数={len(msgs) if isinstance(msgs, list) else '?'}")
    api("DELETE", "/api/chat/history?session=" + sess)


def d_frontend_assets():
    p("=== D. 前端资源已部署修复逻辑 ===")
    st, js = api("GET", "/app.js", raw=True)
    if st == 200:
        text = js.decode("utf-8", errors="replace")
        for feat, key in [("navigate 恢复条件修正", "!document.getElementById('chatBox')"),
                          ("中间态清理链", "__streamPartialCleanup"),
                          ("节点摘除缓存（无 clone 嵌套）", "window.__dashboardNodes = Array.from(mainContent.childNodes)"),
                          ("图表恢复重渲染", "echarts.getInstanceByDom")]:
            check(f"D app.js 含 {feat}", key in text)
    else:
        check("D app.js 可加载", False, f"HTTP {st}")


if __name__ == "__main__":
    p("=" * 70)
    p("阶段9 验收：新增常用 MCP/Skill + 聊天气泡导航修复（后端部分）")
    p(f"目标: {BASE}")
    p("=" * 70)
    try:
        st, _ = api("GET", "/api/models", timeout=10)
        if st != 200:
            p(f"服务未就绪 (HTTP {st})")
            sys.exit(1)
    except Exception as e:
        p(f"无法连接后端服务: {e}")
        sys.exit(1)
    p("服务就绪\n")

    a_import(); p()
    b_listing(); p()
    c_chat_history(); p()
    d_frontend_assets(); p()

    total_pass = sum(1 for r in RESULTS if r[1] == "PASS")
    total_fail = sum(1 for r in RESULTS if r[1] == "FAIL")
    p("=" * 70)
    p(f"阶段9 后端验收汇总: {total_pass} PASS / {total_fail} FAIL (共 {len(RESULTS)} 项)")
    for case, result, detail in RESULTS:
        if result == "FAIL":
            p(f"  FAIL: {case} -- {detail}")
    sys.exit(0 if total_fail == 0 else 1)

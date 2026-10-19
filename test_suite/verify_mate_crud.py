# -*- coding: utf-8 -*-
"""验证 Teammate 增删改 API"""
import json
import sys
import urllib.request

BASE = "http://127.0.0.1:8080"


def api(method, path, body=None, timeout=20):
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read()
            return r.status, (json.loads(raw.decode("utf-8")) if raw else {})
    except urllib.error.HTTPError as e:
        return e.code, {}


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


# 0. 服务检查
st, _ = api("GET", "/api/teammates")
if st != 200:
    p(f"服务未就绪 (HTTP {st})，请先重启服务加载新代码")
    sys.exit(1)

# 1. 新增
st, r = api("POST", "/api/teammates", {"name": "mate_crud", "role": "CRUD测试", "task": "待命"})
check("新增 Teammate", st == 200 and r.get("success"), json.dumps(r, ensure_ascii=False)[:60])

# 2. 修改（PUT）
st, r = api("PUT", "/api/teammates/mate_crud", {"role": "CRUD测试-已修改", "task": "新任务"})
check("修改 Teammate 返回 success", st == 200 and r.get("success"), f"HTTP {st}")
check("修改后 role 生效", st == 200 and r.get("teammate", {}).get("role") == "CRUD测试-已修改",
      json.dumps(r.get("teammate", {}), ensure_ascii=False)[:60])
check("修改后 currentTask 生效", st == 200 and r.get("teammate", {}).get("currentTask") == "新任务",
      json.dumps(r.get("teammate", {}), ensure_ascii=False)[:60])

# 3. 修改不存在的
st, r = api("PUT", "/api/teammates/no_such_mate", {"role": "x"})
check("修改不存在返回 success=false", st == 200 and r.get("success") is False,
      json.dumps(r, ensure_ascii=False)[:60])

# 4. 删除（无需二次确认的 API 层）
st, r = api("DELETE", "/api/teammates/mate_crud")
check("删除 Teammate 返回 success", st == 200 and r.get("success"), f"HTTP {st}")

# 5. 删除后不存在
st, mates = api("GET", "/api/teammates")
names = [m.get("name") for m in mates] if isinstance(mates, list) else []
check("删除后列表不含 mate_crud", "mate_crud" not in names)

# 6. 删除不存在
st, r = api("DELETE", "/api/teammates/no_such_mate")
check("删除不存在返回 success=false", st == 200 and r.get("success") is False,
      json.dumps(r, ensure_ascii=False)[:60])

p(f"\n结果: {ok} PASS / {fail} FAIL")
sys.exit(0 if fail == 0 else 1)

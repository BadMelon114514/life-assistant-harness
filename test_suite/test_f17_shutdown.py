# -*- coding: utf-8 -*-
"""
阶段17 验收：前端关闭后端服务（后端部分）
  A. 服务在线基线：8080 页面/健康检查、8765 健康检查
  B. CSRF 防护：缺少 X-Shutdown-Confirm 头 → 403 且服务仍在运行
  C. 正常关闭：带确认头 → 200 success，8080/8765 在 20s 内停止监听
  D. 重启恢复：启动器重新拉起后 8080/8765 均恢复、页面可访问
说明：本脚本会真实关闭并重启后端服务，运行前请确认没有正在执行的对话。
"""
import http.client
import json
import os
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
CTX = "http://127.0.0.1:8765"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LAUNCHER = os.path.join(ROOT, "dist", "生活助理Harness一键启动.exe")
RESULTS = []


def check(case, ok, detail=""):
    RESULTS.append((case, "PASS" if ok else "FAIL", detail))
    print(("  [PASS] " if ok else "  [FAIL] ") + case + (" -- " + detail if detail else ""))


def http_get(url, timeout=5):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return None, str(e)


def shutdown(headers=None, timeout=8):
    req = urllib.request.Request(BASE + "/api/system/shutdown", data=b'{"confirm":true}',
                                 headers=headers or {"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except Exception:
            return e.code, {}
    except Exception as e:
        return None, {"error": str(e)}


def port_open(port, host="127.0.0.1", timeout=1.0):
    s = socket.socket()
    s.settimeout(timeout)
    try:
        s.connect((host, port))
        return True
    except Exception:
        return False
    finally:
        try:
            s.close()
        except Exception:
            pass


def wait_ports(expect_open, timeout=25):
    t0 = time.time()
    while time.time() - t0 < timeout:
        a, b = port_open(8080), port_open(8765)
        if (a and b) if expect_open else (not a and not b):
            return True
        time.sleep(0.5)
    return False


def wait_health(url, timeout=90):
    t0 = time.time()
    while time.time() - t0 < timeout:
        st, _ = http_get(url, timeout=2)
        if st == 200:
            return True
        time.sleep(1)
    return False


def main():
    print("=== A. 服务在线基线 ===")
    st, body = http_get(BASE + "/")
    check("A1 首页可访问", st == 200 and len(body) > 0, "status=%s" % st)
    check("A2 首页标题为生活助理 Harness", "生活助理 Harness" in body)
    st, body = http_get(BASE + "/actuator/health")
    check("A3 8080 健康检查 UP", st == 200 and '"UP"' in body, "status=%s" % st)
    st, body = http_get(CTX + "/health")
    check("A4 8765 上下文存储健康", st == 200 and '"success": true' in body and "ctx_item" in body, "status=%s" % st)

    print("=== B. CSRF 防护（缺少确认头） ===")
    st, data = shutdown({"Content-Type": "application/json"})
    check("B1 缺少 X-Shutdown-Confirm 返回 403", st == 403, "status=%s data=%s" % (st, data))
    check("B2 拒绝原因明确", isinstance(data, dict) and "X-Shutdown-Confirm" in str(data.get("message", "")))
    check("B3 服务仍在线", port_open(8080) and port_open(8765), "8080=%s 8765=%s" % (port_open(8080), port_open(8765)))

    print("=== C. 正常关闭（本机 + 确认头） ===")
    st, data = shutdown({"Content-Type": "application/json", "X-Shutdown-Confirm": "yes"})
    check("C1 返回 200 且 success=true", st == 200 and data.get("success") is True, "status=%s data=%s" % (st, data))
    t0 = time.time()
    closed = wait_ports(False, timeout=20)
    check("C2 8080 与 8765 均已停止监听", closed, "耗时 %.1fs" % (time.time() - t0))
    check("C3 8080 已无法访问", http_get(BASE + "/", timeout=2)[0] is None)
    check("C4 8765 已无法访问", http_get(CTX + "/health", timeout=2)[0] is None)

    print("=== D. 重启恢复 ===")
    check("D1 启动器文件存在", os.path.exists(LAUNCHER), LAUNCHER)
    subprocess.Popen([LAUNCHER], cwd=ROOT, close_fds=True)
    ok = wait_health(BASE + "/actuator/health", timeout=120)
    check("D2 重启后 8080 健康检查 UP", ok)
    st, body = http_get(CTX + "/health", timeout=5)
    check("D3 重启后 8765 健康检查 200", st == 200 and '"success": true' in body and "ctx_item" in body, "status=%s" % st)
    st, body = http_get(BASE + "/", timeout=5)
    check("D4 重启后首页可访问", st == 200 and "生活助理 Harness" in body)

    total = len(RESULTS)
    passed = sum(1 for _, r, _ in RESULTS if r == "PASS")
    print("\n[SUMMARY] %d/%d PASS" % (passed, total))
    if passed != total:
        print("失败用例：")
        for c, r, d in RESULTS:
            if r != "PASS":
                print("  - " + c + " " + d)
        sys.exit(1)


if __name__ == "__main__":
    main()

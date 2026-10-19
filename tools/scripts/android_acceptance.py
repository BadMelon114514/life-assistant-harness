# -*- coding: utf-8 -*-
r"""安卓客户端验收脚本（模拟器/真机 + adb）。

前置：后端已启动（8080），设备已连接且已安装 com.badmelon.farming.android。

用法：
    E:\python\.venv\Scripts\python.exe tools\scripts\android_acceptance.py

脚本流程：
    1. 杀掉并重启 App，等待欢迎页/历史加载
    2. 依次切换 对话 → 监控 → 任务 → 知识 → 更多 → 设置，断言关键文案
    3. 任务页点「待处理」断言筛选生效（出现任务卡或空态）
    4. 设置页断言版本号 2.0.0
    5. 重启 App，断言会话号保持（Prefs 持久化）
"""

import os
import re
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ADB = os.environ.get("ANDROID_ADB", r"D:\Android\Sdk\platform-tools\adb.exe")
PKG = "com.badmelon.farming.android"
ACT = PKG + "/.MainActivity"

PASS = 0
FAIL = 0
FAILURES = []


def run(*args, timeout=60):
    proc = subprocess.run([ADB, *args], capture_output=True, text=True,
                          encoding="utf-8", errors="replace", timeout=timeout)
    return proc.stdout or ""


def dump():
    run("shell", "uiautomator", "dump", "--compressed", "/sdcard/ui_acc.xml")
    with tempfile.TemporaryDirectory() as tmp:
        local = os.path.join(tmp, "ui.xml")
        run("pull", "/sdcard/ui_acc.xml", local)
        if not os.path.exists(local):
            return []
        tree = ET.parse(local)
        nodes = []
        for n in tree.iter("node"):
            bounds = n.get("bounds", "")
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
            cx = cy = None
            if m:
                cx = (int(m.group(1)) + int(m.group(3))) // 2
                cy = (int(m.group(2)) + int(m.group(4))) // 2
            nodes.append({
                "text": n.get("text", ""),
                "desc": n.get("content-desc", ""),
                "cls": n.get("class", ""),
                "clickable": n.get("clickable") == "true",
                "cx": cx,
                "cy": cy,
            })
        return nodes


def texts(nodes):
    return [n["text"] for n in nodes if n["text"]]


def has(nodes, needle):
    return any(needle in t for t in texts(nodes))


def tap(nodes, label, by_desc=False):
    for n in nodes:
        if (n["desc"] if by_desc else n["text"]) == label and n["cx"] is not None:
            run("shell", "input", "tap", str(n["cx"]), str(n["cy"]))
            return True
    return False


def tap_xy(x, y):
    run("shell", "input", "tap", str(x), str(y))


def check(name, ok, detail=""):
    global PASS, FAIL
    if ok:
        PASS += 1
        print("PASS " + name + ((" (" + detail + ")") if detail else ""))
    else:
        FAIL += 1
        FAILURES.append(name + " " + detail)
        print("FAIL " + name + ((" (" + detail + ")") if detail else ""))


def wait_for(predicate, timeout=20, interval=1.5):
    deadline = time.time() + timeout
    nodes = []
    while time.time() < deadline:
        nodes = dump()
        if predicate(nodes):
            return nodes
        time.sleep(interval)
    return nodes


def main():
    print("== 设备检查 ==")
    devices = run("devices")
    if "device" not in devices:
        print(devices)
        print("未检测到已连接设备")
        return 1

    run("shell", "am", "force-stop", PKG)
    run("shell", "am", "start", "-W", "-n", ACT)
    nodes = wait_for(lambda ns: has(ns, "对话") and (has(ns, "发送") or has(ns, "输入消息")))
    check("启动进入对话页", has(nodes, "发送") or has(nodes, "输入消息"))

    session = next((t for t in texts(nodes) if t.startswith("会话 ")), "")
    check("会话号已生成", bool(session), session)

    print("== 页面切换 ==")
    tabs = [("监控", ["进程监控"]),
            ("任务", ["任务看板"]),
            ("知识", ["知识库"]),
            ("更多", ["MCP 服务", "技能中心", "设置"]),
            ("对话", ["刷新", "新对话"])]
    for tab, expects in tabs:
        nodes = dump()
        if not tap(nodes, tab):
            check("切换到" + tab, False, "未找到底部导航")
            continue
        nodes = wait_for(lambda ns, e=expects: any(has(ns, x) for x in e), timeout=15)
        check("切换到" + tab, any(has(nodes, x) for x in expects),
              " / ".join(x for x in expects if has(nodes, x)))

    print("== 任务筛选 ==")
    tap(dump(), "任务")
    wait_for(lambda ns: has(ns, "任务看板"), timeout=15)
    tap(dump(), "待处理")
    nodes = wait_for(lambda ns: has(ns, "待处理"), timeout=10)
    empty = has(nodes, "没有符合条件的任务")
    check("待处理筛选生效", empty or has(nodes, "负责人："),
          "空态" if empty else "有任务卡片")

    print("== 设置与版本 ==")
    tap(dump(), "更多")
    nodes = wait_for(lambda ns: has(ns, "设置"), timeout=15)
    tap(nodes, "设置")
    nodes = wait_for(lambda ns: has(ns, "外观主题"), timeout=15)
    check("设置页打开", has(nodes, "外观主题"))
    run("shell", "input", "swipe", "540", "1900", "540", "700", "300")
    time.sleep(1.5)
    nodes = dump()
    check("危险操作卡片可见", has(nodes, "关闭后端服务"))
    version = next((t for t in texts(nodes) if t.startswith("版本 ")), "")
    check("版本号 2.0.0", version == "版本 2.0.0", version)

    print("== 重启持久化 ==")
    run("shell", "am", "force-stop", PKG)
    run("shell", "am", "start", "-W", "-n", ACT)
    nodes = wait_for(lambda ns: has(ns, "会话 "), timeout=25)
    session2 = next((t for t in texts(nodes) if t.startswith("会话 ")), "")
    check("重启后会话保持", session2 == session, session2)

    print("== 结果 ==")
    print("PASS %d / FAIL %d" % (PASS, FAIL))
    if FAILURES:
        for f in FAILURES:
            print(" - " + f)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

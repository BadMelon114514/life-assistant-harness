# -*- coding: utf-8 -*-
"""验证：多工具(天气+热点)、文件读取、文件写入确认、how-to"""
import json
import sys
import time
import urllib.request

BASE = "http://127.0.0.1:8080/api/assistant/chat"


def chat(message, session="verify2"):
    body = json.dumps({"message": message, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE, data=body, headers={"Content-Type": "application/json"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    return data, time.time() - t0


def p(s=""):
    """GBK 控制台安全打印（不可编码字符替换为 ?）"""
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))


def show(tag, msg, keys=("reply", "toolLog", "needsConfirm", "confirmOp", "confirmPath", "error")):
    p("=" * 70)
    p(f"[{tag}] {msg}")
    try:
        data, cost = chat(msg)
        p(f"耗时: {cost:.1f}s")
        for k in keys:
            if k in data and data[k] is not None and data[k] != "":
                v = str(data[k])
                if len(v) > 1200:
                    v = v[:1200] + f"...(共{len(str(data[k]))}字符)"
                p(f"  {k}: {v}")
    except Exception as e:
        p("  调用失败:" + str(e))
    sys.stdout.flush()


if __name__ == "__main__":
    show("weather+hot", "获取天气 今日热点")
    show("list-dir", "列出项目根目录下的文件", keys=("reply", "toolLog"))
    show("read-file", "读取 build.gradle 文件内容", keys=("reply", "toolLog"))
    show("write-confirm", "在项目 tools 目录下新增一个 test_script.py 工具脚本", keys=("reply", "needsConfirm", "confirmOp", "confirmPath", "confirmReason"))
    show("howto", "如何使用联网搜索", keys=("reply", "toolUsed"))

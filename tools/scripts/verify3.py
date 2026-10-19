# -*- coding: utf-8 -*-
"""验证文件操作确认闭环：chat-stream 推送 confirm → confirm API 执行 → 验证文件落盘"""
import json
import sys
import urllib.request

BASE = "http://127.0.0.1:8080"


def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        print(s.encode("gbk", errors="replace").decode("gbk"))


def stream_chat(message, session="verify3"):
    """调用 SSE chat-stream，抓取 confirm 事件与最终回复（按空行分隔事件块解析）"""
    body = json.dumps({"message": message, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/chat-stream", data=body,
                                 headers={"Content-Type": "application/json"})
    resp = urllib.request.urlopen(req, timeout=120)
    confirm = None
    final = None
    buffer = ""
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
            if ev == "confirm" and dt:
                parts = dt.split("|", 2)
                confirm = (parts[0], parts[1] if len(parts) > 1 else "", parts[2] if len(parts) > 2 else "")
            elif ev == "done" and dt:
                final = dt
    return confirm, final


def confirm_file_op(op, path, content):
    body = json.dumps({"op": op, "path": path, "content": content}).encode("utf-8")
    req = urllib.request.Request(BASE + "/api/assistant/confirm", data=body,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


if __name__ == "__main__":
    # 1. 触发脚本创建意图，获取 confirm 事件
    p("=" * 70)
    p("[1] 请求新增工具脚本 -> 期待 SSE confirm 事件")
    confirm, final = stream_chat("在项目 tools 目录下新增一个 hello.py 工具脚本，输出 Hello Harness", "verify3")
    p("confirm 事件: " + str(confirm))
    p("回复: " + (final or "")[:200])

    if confirm and confirm[0] == "write_file":
        path, content = confirm[1], confirm[2]
        p(f"确认框数据: op={confirm[0]} path={path}")
        p("脚本内容前 120 字: " + (content or "")[:120].replace("\n", " "))
        # 2. 模拟用户点击"确认执行"
        p("-" * 70)
        p("[2] 用户确认执行 -> POST /api/assistant/confirm")
        r = confirm_file_op(confirm[0], path, content)
        p("confirm 响应: " + json.dumps(r, ensure_ascii=False)[:300])
        # 3. 验证文件落盘
        p("-" * 70)
        p("[3] 验证文件是否已写入项目库")
        if r.get("success"):
            import os
            target = os.path.join("d:/jvav/farming", path)
            p(f"文件存在: {os.path.exists(target)}")
            if os.path.exists(target):
                with open(target, "r", encoding="utf-8", errors="replace") as f:
                    p("文件内容前 150 字: " + f.read()[:150].replace("\n", " "))
                # 4. 清理测试文件
                p("-" * 70)
                p("[4] 清理测试文件（delete_file 确认）")
                rd = confirm_file_op("delete_file", path, "")
                p("删除响应: " + json.dumps(rd, ensure_ascii=False)[:200])
                p(f"删除后文件存在: {os.path.exists(target)}")
        else:
            p("写入失败: " + str(r))
    else:
        p("未捕获到 write_file 确认事件，可能走了其他分支")
    sys.stdout.flush()

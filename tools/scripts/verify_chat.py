# -*- coding: utf-8 -*-
"""验证聊天接口：天气工具调用与 how-to 排除"""
import json
import sys
import time
import urllib.request

BASE = "http://127.0.0.1:8080/api/assistant/chat"


def chat(message, session="verify"):
    body = json.dumps({"message": message, "session": session}).encode("utf-8")
    req = urllib.request.Request(BASE, data=body, headers={"Content-Type": "application/json"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=90) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    cost = time.time() - t0
    return data, cost


if __name__ == "__main__":
    cases = [
        ("weather", "获取天气今日热点"),
        ("howto", "如何使用联网搜索"),
    ]
    for tag, msg in cases:
        print("=" * 60)
        print(f"[{tag}] 消息: {msg}")
        try:
            data, cost = chat(msg)
            print(f"耗时: {cost:.1f}s")
            for k, v in data.items():
                sv = str(v)
                if len(sv) > 1500:
                    sv = sv[:1500] + f"...(共{len(str(v))}字符)"
                print(f"  {k}: {sv}")
        except Exception as e:
            print("  调用失败:", e)
        sys.stdout.flush()

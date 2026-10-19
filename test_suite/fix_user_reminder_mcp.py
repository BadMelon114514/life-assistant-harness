# -*- coding: utf-8 -*-
"""完善用户已添加的「定时提醒MCP服务」：修正假地址为真实本地端点并测试连通"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"


def api(method, path, body=None, timeout=30):
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        c = r.read()
        return r.status, (json.loads(c.decode("utf-8")) if c else {})


# 1. 修正 endpoint 为系统真实定时提醒端点
st, r = api("POST", "/api/mcp", {
    "id": 39,
    "name": "定时提醒MCP服务",
    "serverType": "http",
    "endpoint": "http://127.0.0.1:8080/api/assistant/schedule",
    "description": "定时提醒：设定/查询/取消延时提醒任务。POST 参数：instruct=提醒内容(必填), delayMinutes=延迟分钟数, title=提醒标题；GET 查询提醒列表；DELETE /{id} 取消提醒。系统内置端点，自动建表并轮询触发，到期写入聊天历史并经 WebSocket 推送。"
})
print("更新定时提醒MCP服务:", st, r.get("success"), r.get("server", {}).get("endpoint") if r.get("server") else "")

# 2. 连通性测试（http GET /api/assistant/schedule → 200，自动激活并记录 lastChecked）
st, t = api("POST", "/api/mcp/39/test")
print("连通性测试:", st, t.get("success"), t.get("message"))

# 3. 验证状态
st, mcps = api("GET", "/api/mcp")
m = next((x for x in mcps if x.get("id") == 39), None)
print("最终状态:", m.get("status") if m else "?", "lastChecked:", m.get("lastChecked") if m else "?")

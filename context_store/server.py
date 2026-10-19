# -*- coding: utf-8 -*-
"""
上下文存储 HTTP 服务（标准库实现，无第三方 Web 框架）。
启动：python -m context_store.server [--port 8765]
REST + 统一 MCP 入口 /api/mcp/tool（供 Java 智能助手作为 MCP 工具调用）。
"""
import argparse
import json
import os
import sys
import threading
import time
import traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

if __package__ in (None, ""):
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    from context_store import db
    from context_store.store import ContextStore
else:
    from . import db
    from .store import ContextStore

STORE = ContextStore()

OPS = {
    "context_append": lambda p: STORE.append(p["room"], p.get("role", "user"), p.get("content", ""), p.get("agent", "main")),
    "context_load": lambda p: STORE.load(p["room"], p.get("budget", 4000), bool(p.get("include_cold", False))),
    "context_sink": lambda p: STORE.sink(p["room"], int(p.get("l1_cap", 40)), int(p.get("l2_cap", 200))),
    "context_update": lambda p: STORE.update_item(p["room"], p["id"], p.get("content", "")),
    "context_remove": lambda p: STORE.remove_item(p["room"], p["id"]),
    "context_stats": lambda p: STORE.stats(p["room"]),
    "context_promote": lambda p: STORE.promote(p["room"], p.get("item_ids", [])),
    "snapshot_commit": lambda p: STORE.commit(p["room"], p.get("message", "")),
    "snapshot_log": lambda p: STORE.log(p["room"], int(p.get("limit", 20))),
    "snapshot_diff": lambda p: STORE.diff(p["room"], p["from"], p["to"]),
    "snapshot_rollback": lambda p: STORE.rollback(p["room"], p["commit"]),
    "blackboard_put": lambda p: STORE.bb_put(p["room"], p["key"], p.get("value", ""), p.get("agent", "main")),
    "blackboard_get": lambda p: STORE.bb_get(p["room"], p["key"]),
    "blackboard_list": lambda p: STORE.bb_list(p["room"]),
    "resource_publish": lambda p: STORE.rs_publish(p["kind"], p["name"], p.get("payload", {}), p.get("actor", "main")),
    "resource_versions": lambda p: STORE.rs_versions(p["kind"], p["name"]),
    "resource_rollback": lambda p: STORE.rs_rollback(p["kind"], p["name"], int(p["version"])),
    "resource_sync": lambda p: STORE.rs_sync(p.get("since", "")),
    "doc_import": lambda p: STORE.doc_import(p.get("name", "未命名文档"), p.get("text", ""), p.get("source", "")),
    "doc_search": lambda p: STORE.doc_search(p.get("q", ""), int(p.get("limit", 5))),
    "doc_read": lambda p: STORE.doc_section(int(p["id"])),
    "doc_list": lambda p: STORE.doc_list(),
}

CORS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
}


def _ok(payload):
    body = {"success": True, "result": payload}
    body.update(payload if isinstance(payload, dict) else {})
    return 200, body


def dispatch(op, params):
    fn = OPS.get(op)
    if not fn:
        raise ValueError("未知操作: " + str(op) + "；可选: " + ", ".join(sorted(OPS)))
    return fn(params or {})


class Handler(BaseHTTPRequestHandler):
    server_version = "ContextStore/1.0"

    def log_message(self, fmt, *args):
        sys.stderr.write("[context-store] " + (fmt % args) + "\n")

    def _send(self, code, payload):
        data = json.dumps(payload, ensure_ascii=False, default=str).encode("utf-8")
        self.send_response(code)
        for k, v in CORS.items():
            self.send_header(k, v)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _params(self):
        parsed = urlparse(self.path)
        qs = {k: v[-1] for k, v in parse_qs(parsed.query).items()}
        body = {}
        length = int(self.headers.get("Content-Length") or 0)
        if length > 0:
            raw = self.rfile.read(length)
            try:
                body = json.loads(raw.decode("utf-8") or "{}")
            except Exception:
                body = {}
        if not isinstance(body, dict):
            body = {"value": body}
        merged = dict(qs)
        merged.update(body)
        return parsed.path, merged

    def do_OPTIONS(self):
        self.send_response(204)
        for k, v in CORS.items():
            self.send_header(k, v)
        self.end_headers()

    def do_GET(self):
        try:
            path, p = self._params()
            if path in ("/health", "/api/health"):
                return self._send(200, {"success": True, "service": "context-store", "tables": db.tables(), "ops": sorted(OPS)})
            if path == "/api/mcp/tool":
                return self._send(200, {"success": True, "ops": sorted(OPS), "hint": "POST {op, ...params}"})
            routes = {
                "/context/stats": lambda: STORE.stats(p.get("room", "")),
                "/snapshot/log": lambda: STORE.log(p.get("room", ""), int(p.get("limit", 20))),
                "/snapshot/diff": lambda: STORE.diff(p["room"], p["from"], p["to"]),
                "/blackboard/get": lambda: STORE.bb_get(p.get("room", ""), p.get("key", "")),
                "/blackboard/list": lambda: STORE.bb_list(p.get("room", "")),
                "/resource/versions": lambda: STORE.rs_versions(p.get("kind", ""), p.get("name", "")),
                "/resource/sync": lambda: STORE.rs_sync(p.get("since", "")),
                "/doc/search": lambda: STORE.doc_search(p.get("q", ""), int(p.get("limit", 5))),
                "/doc/section": lambda: STORE.doc_section(int(p["id"])),
                "/doc/list": lambda: STORE.doc_list(),
            }
            fn = routes.get(path)
            if fn:
                return self._send(200, _ok(fn())[1])
            return self._send(404, {"success": False, "error": "未知路径: " + path})
        except Exception as e:
            return self._send(400, {"success": False, "error": str(e)})

    def _shutdown(self):
        """响应后异步停止 serve_forever，供 Java 后端一键关闭整个本地服务栈。"""
        self._send(200, {"success": True, "message": "context-store 正在关闭"})
        server = self.server

        def _stop():
            time.sleep(0.3)
            server.shutdown()
            server.server_close()

        threading.Thread(target=_stop, daemon=True).start()

    def do_POST(self):
        try:
            path, p = self._params()
            if path == "/shutdown":
                if self.client_address[0] not in ("127.0.0.1", "::1"):
                    return self._send(403, {"success": False, "error": "仅允许本机关闭"})
                return self._shutdown()
            if path == "/api/mcp/tool":
                op = p.pop("op", None) or p.pop("operation", None)
                return self._send(200, _ok(dispatch(op, p))[1])
            routes = {
                "/context/append": lambda: STORE.append(p["room"], p.get("role", "user"), p.get("content", ""), p.get("agent", "main")),
                "/context/load": lambda: STORE.load(p["room"], p.get("budget", 4000), bool(p.get("include_cold", False))),
                "/context/sink": lambda: STORE.sink(p["room"], int(p.get("l1_cap", 40)), int(p.get("l2_cap", 200))),
                "/context/update": lambda: STORE.update_item(p["room"], p["id"], p.get("content", "")),
                "/context/remove": lambda: STORE.remove_item(p["room"], p["id"]),
                "/context/promote": lambda: {"promoted": STORE.promote(p["room"], p.get("item_ids", []))},
                "/snapshot/commit": lambda: STORE.commit(p["room"], p.get("message", "")),
                "/snapshot/rollback": lambda: STORE.rollback(p["room"], p["commit"]),
                "/blackboard/put": lambda: STORE.bb_put(p["room"], p["key"], p.get("value", ""), p.get("agent", "main")),
                "/resource/publish": lambda: STORE.rs_publish(p["kind"], p["name"], p.get("payload", {}), p.get("actor", "main")),
                "/resource/rollback": lambda: STORE.rs_rollback(p["kind"], p["name"], int(p["version"])),
                "/doc/import": lambda: STORE.doc_import(p.get("name", "未命名文档"), p.get("text", ""), p.get("source", "")),
            }
            fn = routes.get(path)
            if fn:
                return self._send(200, _ok(fn())[1])
            return self._send(404, {"success": False, "error": "未知路径: " + path})
        except KeyError as e:
            return self._send(400, {"success": False, "error": "缺少参数: " + str(e)})
        except Exception as e:
            traceback.print_exc()
            return self._send(400, {"success": False, "error": str(e)})


def main(argv=None):
    parser = argparse.ArgumentParser(description="上下文存储 HTTP 服务")
    parser.add_argument("--host", default=os.environ.get("CTX_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("CTX_PORT", "8765")))
    args = parser.parse_args(argv)
    db.ensure_schema()
    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    print("context-store listening on http://%s:%s" % (args.host, args.port), flush=True)
    print("tables:", ", ".join(db.tables()), flush=True)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()

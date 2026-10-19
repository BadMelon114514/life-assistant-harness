# -*- coding: utf-8 -*-
"""
上下文存储核心（类 OpenViking）：
- 三级存储：L1 热缓存 / L2 内存 / L3 外存，按权重逐级下沉，控制送入模型的数据量
- 权重：按被调用次数累积，高频常驻、低频加速下沉
- Git 式快照：提交 / diff / 回滚，记忆改写不丢失
- 多 Agent 黑板：共享 KV，写入即时可见（version 递增）
- 资源版本：MCP/Skill 多版本，旧版本保留可回滚，sync 同步给所有 Agent
- 文档分章摘要：一级/二级章节摘要，检索先命中摘要再回读原文
"""
import hashlib
import json
import re
from datetime import datetime, timedelta

from . import db

LAYERS = ("L1", "L2", "L3")
ROOM_RE = re.compile(r"^[A-Za-z0-9_\-:. ]{1,128}$")


def _hash(text):
    return hashlib.sha1((text or "").encode("utf-8")).hexdigest()


def _now():
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _clean_room(room):
    room = (room or "").strip()
    if not room or not ROOM_RE.match(room):
        raise ValueError("room 非法（1-128 位字母/数字/下划线/横线/点/冒号/空格）")
    return room


class ContextStore:
    # ================= 三级存储 =================
    def append(self, room, role, content, agent="main"):
        room = _clean_room(room)
        content = content if content is not None else ""
        sql = (
            "INSERT INTO ctx_item (room, agent, role, content, layer, weight, access_count, "
            "last_access_at, content_hash, version, deleted) "
            "VALUES (%s, %s, %s, %s, 'L1', 1, 0, NOW(), %s, 1, 0)"
        )
        item_id = db.execute_returning_id(sql, (room, agent or "main", role or "user", content, _hash(content)))
        return {"id": item_id, "room": room, "layer": "L1", "weight": 1.0, "content_hash": _hash(content)}

    def append_many(self, room, messages, agent="main"):
        out = []
        for m in messages or []:
            out.append(self.append(room, m.get("role", "user"), m.get("content", ""), m.get("agent", agent)))
        return out

    def load(self, room, budget=4000, include_cold=False, roles=None):
        """按层级装入上下文：L1 全量（时间序）→ L2 按权重补足预算 → 可选 L3。
        被装入的条目 access_count+1、weight+1，并从冷层提升回 L1（高频常驻）。"""
        room = _clean_room(room)
        budget = max(200, int(budget or 4000))
        roles = set(roles) if roles else None
        used = 0
        selected = []
        stats = {l: 0 for l in LAYERS}
        for layer in (("L1", "L2", "L3") if include_cold else ("L1", "L2")):
            order = "id ASC" if layer == "L1" else "weight DESC, id DESC"
            rows = db.query(
                "SELECT * FROM ctx_item WHERE room=%s AND deleted=0 AND layer=%s ORDER BY " + order,
                (room, layer),
            )
            for row in rows:
                if roles and row["role"] not in roles:
                    continue
                ln = len(row["content"] or "")
                if selected and used + ln > budget:
                    continue
                selected.append(row)
                used += ln
                stats[layer] += 1
                if used >= budget:
                    break
            if used >= budget:
                break
        ids = [r["id"] for r in selected]
        if ids:
            marks = ",".join(["%s"] * len(ids))
            db.execute(
                "UPDATE ctx_item SET access_count = access_count + 1, weight = weight + 1, "
                "last_access_at = NOW(), layer = 'L1' WHERE id IN (" + marks + ")",
                tuple(ids),
            )
        items = [
            {
                "id": r["id"], "agent": r["agent"], "role": r["role"], "content": r["content"],
                "layer": r["layer"], "weight": float(r["weight"]), "access_count": r["access_count"],
                "created_at": str(r["created_at"]), "version": r["version"],
            }
            for r in selected
        ]
        return {"items": items, "used_chars": used, "budget": budget, "layer_stats": stats}

    def sink(self, room, l1_cap=40, l2_cap=200):
        """冷数据逐级下沉：L1 超出容量按权重下沉 L2；L2 超出容量或长期低权重下沉 L3。"""
        room = _clean_room(room)
        merged = 0
        evicted = 0
        l1 = db.query("SELECT id, weight FROM ctx_item WHERE room=%s AND deleted=0 AND layer='L1' ORDER BY weight DESC, id DESC", (room,))
        if len(l1) > l1_cap:
            demote = [r["id"] for r in l1[l1_cap:]]
            marks = ",".join(["%s"] * len(demote))
            db.execute("UPDATE ctx_item SET layer='L2' WHERE id IN (" + marks + ")", tuple(demote))
            merged = len(demote)
        l2 = db.query(
            "SELECT id, weight, last_access_at FROM ctx_item WHERE room=%s AND deleted=0 AND layer='L2' "
            "ORDER BY weight DESC, id DESC",
            (room,),
        )
        floor = datetime.now() - timedelta(days=3)
        cold = []
        for idx, r in enumerate(l2):
            last = r["last_access_at"]
            stale = last is None or last < floor
            if idx >= l2_cap or (float(r["weight"]) <= 0.5 and stale):
                cold.append(r["id"])
        if cold:
            marks = ",".join(["%s"] * len(cold))
            db.execute("UPDATE ctx_item SET layer='L3' WHERE id IN (" + marks + ")", tuple(cold))
            evicted = len(cold)
        return {"L1_to_L2": merged, "L2_to_L3": evicted}

    def promote(self, room, item_ids):
        room = _clean_room(room)
        if not item_ids:
            return 0
        marks = ",".join(["%s"] * len(item_ids))
        return db.execute(
            "UPDATE ctx_item SET layer='L1', weight=weight+1, access_count=access_count+1, last_access_at=NOW() "
            "WHERE room=%s AND id IN (" + marks + ")",
            tuple([room] + list(item_ids)),
        )

    def update_item(self, room, item_id, content):
        """改写一条上下文（版本+1，便于快照 diff 观察修改）。"""
        room = _clean_room(room)
        row = db.query_one("SELECT id, version FROM ctx_item WHERE id=%s AND room=%s", (int(item_id), room))
        if not row:
            raise ValueError("上下文条目不存在: " + str(item_id))
        content = content if content is not None else ""
        db.execute(
            "UPDATE ctx_item SET content=%s, content_hash=%s, version=version+1, layer='L1', "
            "weight=weight+1, access_count=access_count+1, last_access_at=NOW() WHERE id=%s",
            (content, _hash(content), int(item_id)),
        )
        return {"id": int(item_id), "version": int(row["version"]) + 1, "content_hash": _hash(content)}

    def remove_item(self, room, item_id):
        """软删除一条上下文（快照仍可回滚恢复）。"""
        room = _clean_room(room)
        n = db.execute("UPDATE ctx_item SET deleted=1 WHERE id=%s AND room=%s", (int(item_id), room))
        return {"removed": n > 0, "id": int(item_id)}

    def stats(self, room):
        room = _clean_room(room)
        rows = db.query(
            "SELECT layer, COUNT(*) AS n, COALESCE(SUM(LENGTH(content)),0) AS chars, COALESCE(SUM(weight),0) AS w "
            "FROM ctx_item WHERE room=%s AND deleted=0 GROUP BY layer",
            (room,),
        )
        out = {l: {"count": 0, "chars": 0, "weight": 0.0} for l in LAYERS}
        for r in rows:
            out[r["layer"]] = {"count": r["n"], "chars": int(r["chars"]), "weight": float(r["w"])}
        out["total"] = sum(v["count"] for k, v in out.items() if k in LAYERS)
        return out

    # ================= Git 式快照 =================
    def commit(self, room, message=""):
        room = _clean_room(room)
        items = db.query(
            "SELECT id, agent, role, content, content_hash, layer, weight FROM ctx_item "
            "WHERE room=%s AND deleted=0 ORDER BY id",
            (room,),
        )
        parent = db.query_one("SELECT commit_hash FROM ctx_snapshot WHERE room=%s ORDER BY id DESC LIMIT 1", (room,))
        parent_hash = parent["commit_hash"] if parent else ""
        digest = "|".join(str(i["id"]) + ":" + (i["content_hash"] or "") for i in items)
        commit_hash = _hash(parent_hash + "|" + digest + "|" + _now())
        snap_id = db.execute_returning_id(
            "INSERT INTO ctx_snapshot (room, commit_hash, parent_hash, message, item_count) "
            "VALUES (%s, %s, %s, %s, %s)",
            (room, commit_hash, parent_hash, (message or "")[:255], len(items)),
        )
        for it in items:
            db.execute(
                "INSERT INTO ctx_snapshot_item (snapshot_id, item_id, agent, role, content, content_hash, layer, weight) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s)",
                (snap_id, it["id"], it["agent"], it["role"], it["content"], it["content_hash"], it["layer"], it["weight"]),
            )
        return {"commit_hash": commit_hash, "parent_hash": parent_hash, "item_count": len(items), "message": message}

    def log(self, room, limit=20):
        room = _clean_room(room)
        rows = db.query(
            "SELECT id, commit_hash, parent_hash, message, item_count, created_at FROM ctx_snapshot "
            "WHERE room=%s ORDER BY id DESC LIMIT %s",
            (room, int(limit)),
        )
        return [dict(r) for r in rows]

    def _snapshot_items(self, room, commit_hash):
        snap = db.query_one("SELECT id FROM ctx_snapshot WHERE room=%s AND commit_hash=%s", (room, commit_hash))
        if not snap:
            raise ValueError("快照不存在: " + str(commit_hash))
        rows = db.query("SELECT * FROM ctx_snapshot_item WHERE snapshot_id=%s", (snap["id"],))
        return {r["item_id"]: r for r in rows}

    def diff(self, room, from_hash, to_hash):
        room = _clean_room(room)
        a = self._snapshot_items(room, from_hash)
        b = self._snapshot_items(room, to_hash)
        added = [{"item_id": i, "role": b[i]["role"], "content": (b[i]["content"] or "")[:300]} for i in b if i not in a]
        removed = [{"item_id": i, "role": a[i]["role"], "content": (a[i]["content"] or "")[:300]} for i in a if i not in b]
        modified = [
            {"item_id": i, "before": (a[i]["content"] or "")[:300], "after": (b[i]["content"] or "")[:300]}
            for i in a if i in b and (a[i]["content_hash"] or "") != (b[i]["content_hash"] or "")
        ]
        return {"from": from_hash, "to": to_hash, "added": added, "removed": removed, "modified": modified,
                "summary": {"added": len(added), "removed": len(removed), "modified": len(modified)}}

    def rollback(self, room, commit_hash):
        room = _clean_room(room)
        snap_items = self._snapshot_items(room, commit_hash)
        current = db.query("SELECT id, deleted FROM ctx_item WHERE room=%s", (room,))
        current_ids = {r["id"] for r in current}
        restored = inserted = hidden = 0
        for item_id, it in snap_items.items():
            if item_id in current_ids:
                db.execute(
                    "UPDATE ctx_item SET content=%s, role=%s, agent=%s, content_hash=%s, version=version+1, "
                    "deleted=0, layer='L1', weight=weight+1, access_count=access_count+1, last_access_at=NOW() "
                    "WHERE id=%s",
                    (it["content"], it["role"], it["agent"], it["content_hash"], item_id),
                )
                restored += 1
            else:
                db.execute(
                    "INSERT INTO ctx_item (id, room, agent, role, content, layer, weight, access_count, "
                    "last_access_at, content_hash, version, deleted) VALUES (%s, %s, %s, %s, %s, 'L1', 1, 0, NOW(), %s, 1, 0)",
                    (item_id, room, it["agent"], it["role"], it["content"], it["content_hash"]),
                )
                inserted += 1
        for r in current:
            if r["id"] not in snap_items and not r["deleted"]:
                db.execute("UPDATE ctx_item SET deleted=1 WHERE id=%s", (r["id"],))
                hidden += 1
        new_commit = self.commit(room, "rollback to " + str(commit_hash)[:8])
        return {"restored": restored, "inserted": inserted, "hidden": hidden,
                "commit": new_commit["commit_hash"], "target": commit_hash}

    # ================= 多 Agent 黑板 =================
    def bb_put(self, room, key, value, agent="main"):
        room = _clean_room(room)
        key = (key or "").strip()[:128]
        if not key:
            raise ValueError("黑板 key 不能为空")
        conn = db.connect()
        try:
            with conn.cursor() as cur:
                cur.execute("SELECT id, version FROM ctx_blackboard WHERE room=%s AND bkey=%s FOR UPDATE", (room, key))
                row = cur.fetchone()
                if row:
                    cur.execute(
                        "UPDATE ctx_blackboard SET bvalue=%s, updated_by=%s, version=%s WHERE id=%s",
                        (value, agent or "main", row["version"] + 1, row["id"]),
                    )
                    version = row["version"] + 1
                else:
                    cur.execute(
                        "INSERT INTO ctx_blackboard (room, bkey, bvalue, updated_by, version) VALUES (%s, %s, %s, %s, 1)",
                        (room, key, value, agent or "main"),
                    )
                    version = 1
            conn.commit()
        finally:
            pass
        return {"room": room, "key": key, "version": version, "updated_by": agent or "main"}

    def bb_get(self, room, key):
        room = _clean_room(room)
        row = db.query_one("SELECT * FROM ctx_blackboard WHERE room=%s AND bkey=%s", (room, key))
        return dict(row) if row else None

    def bb_list(self, room):
        room = _clean_room(room)
        return [dict(r) for r in db.query("SELECT bkey, bvalue, updated_by, version, updated_at FROM ctx_blackboard WHERE room=%s ORDER BY bkey", (room,))]

    # ================= 资源版本（MCP / Skill） =================
    def rs_publish(self, kind, name, payload, actor="main"):
        kind = (kind or "").lower()
        if kind not in ("mcp", "skill"):
            raise ValueError("kind 必须是 mcp 或 skill")
        name = (name or "").strip()
        if not name:
            raise ValueError("name 不能为空")
        raw = json.dumps(payload, ensure_ascii=False, sort_keys=True) if not isinstance(payload, str) else payload
        row = db.query_one("SELECT COALESCE(MAX(version),0) AS v FROM ctx_resource WHERE kind=%s AND name=%s", (kind, name))
        version = int(row["v"]) + 1
        db.execute("UPDATE ctx_resource SET is_active=0 WHERE kind=%s AND name=%s", (kind, name))
        rid = db.execute_returning_id(
            "INSERT INTO ctx_resource (kind, name, version, payload, content_hash, is_active) VALUES (%s, %s, %s, %s, %s, 1)",
            (kind, name, version, raw, _hash(raw)),
        )
        return {"id": rid, "kind": kind, "name": name, "version": version, "hash": _hash(raw), "active": True}

    def rs_versions(self, kind, name):
        rows = db.query(
            "SELECT id, version, content_hash, is_active, created_at, updated_at FROM ctx_resource "
            "WHERE kind=%s AND name=%s ORDER BY version DESC",
            ((kind or "").lower(), name),
        )
        return [dict(r) for r in rows]

    def rs_rollback(self, kind, name, version):
        kind = (kind or "").lower()
        row = db.query_one("SELECT id FROM ctx_resource WHERE kind=%s AND name=%s AND version=%s", (kind, name, int(version)))
        if not row:
            raise ValueError("资源版本不存在: %s/%s v%s" % (kind, name, version))
        db.execute("UPDATE ctx_resource SET is_active=0 WHERE kind=%s AND name=%s", (kind, name))
        db.execute("UPDATE ctx_resource SET is_active=1 WHERE id=%s", (row["id"],))
        return {"kind": kind, "name": name, "active_version": int(version)}

    def rs_sync(self, since=""):
        """同步激活资源：since 为空返回全部；since 为时间戳时只返回之后更新的资源。"""
        if since:
            rows = db.query(
                "SELECT kind, name, version, content_hash, updated_at FROM ctx_resource "
                "WHERE is_active=1 AND updated_at >= %s ORDER BY kind, name",
                (since,),
            )
        else:
            rows = db.query(
                "SELECT kind, name, version, content_hash, updated_at FROM ctx_resource WHERE is_active=1 ORDER BY kind, name"
            )
        return {"synced_at": _now(), "resources": [dict(r) for r in rows]}

    # ================= 文档分章摘要 =================
    @staticmethod
    def _split_sections(text):
        """按 Markdown 标题切分：一级 (#) / 二级 (##)；无标题内容归入「前言」。"""
        sections = []
        cur = {"level": 1, "title": "前言", "lines": []}
        for line in (text or "").splitlines():
            m1 = re.match(r"^#\s+(.+?)\s*$", line)
            m2 = re.match(r"^##\s+(.+?)\s*$", line)
            if m1:
                if cur["lines"] or cur["title"] != "前言":
                    sections.append(cur)
                cur = {"level": 1, "title": m1.group(1).strip(), "lines": []}
            elif m2:
                if cur["lines"] or cur["title"] != "前言":
                    sections.append(cur)
                cur = {"level": 2, "title": m2.group(1).strip(), "lines": []}
            else:
                cur["lines"].append(line)
        if cur["lines"] or not sections:
            sections.append(cur)
        return [s for s in sections if (s["lines"] or s["title"] != "前言")]

    def doc_import(self, name, text, source=""):
        name = (name or "未命名文档").strip()[:255]
        parsed = self._split_sections(text)
        doc_id = db.execute_returning_id("INSERT INTO ctx_doc (name, source) VALUES (%s, %s)", (name, source or ""))
        last_l1 = 0
        order = 0
        out = []
        for s in parsed:
            content = "\n".join(s["lines"]).strip()
            summary = _summarize(content)
            if s["level"] == 1:
                parent_id = 0
                sid = db.execute_returning_id(
                    "INSERT INTO ctx_doc_section (doc_id, parent_id, level, title, content, summary, chapter_order) "
                    "VALUES (%s, 0, 1, %s, %s, %s, %s)",
                    (doc_id, s["title"], content, summary, order),
                )
                last_l1 = sid
            else:
                sid = db.execute_returning_id(
                    "INSERT INTO ctx_doc_section (doc_id, parent_id, level, title, content, summary, chapter_order) "
                    "VALUES (%s, %s, 2, %s, %s, %s, %s)",
                    (doc_id, last_l1, s["title"], content, summary, order),
                )
            out.append({"id": sid, "level": s["level"], "title": s["title"], "summary": summary,
                        "parent_id": last_l1 if s["level"] == 2 else 0})
            order += 1
        db.execute("UPDATE ctx_doc SET section_count=%s WHERE id=%s", (len(out), doc_id))
        return {"doc_id": doc_id, "name": name, "section_count": len(out), "sections": out}

    def doc_search(self, q, limit=5):
        q = (q or "").strip()
        if not q:
            return {"query": q, "hits": []}
        tokens = [t for t in re.split(r"\s+", q) if t]
        rows = db.query(
            "SELECT s.id, s.doc_id, s.title, s.level, s.summary, s.content, d.name AS doc_name "
            "FROM ctx_doc_section s JOIN ctx_doc d ON d.id = s.doc_id"
        )
        hits = []
        for r in rows:
            hay_title = (r["title"] or "").lower()
            hay_sum = (r["summary"] or "").lower()
            hay = hay_title + " " + hay_sum
            score = 0
            for t in tokens:
                t = t.lower()
                if not t:
                    continue
                if t in hay_title:
                    score += 3
                elif t in hay_sum:
                    score += 2
                elif t in hay:
                    score += 1
            if score > 0:
                hits.append({"id": r["id"], "doc_id": r["doc_id"], "doc_name": r["doc_name"],
                             "title": r["title"], "level": r["level"], "summary": r["summary"],
                             "score": score})
        hits.sort(key=lambda h: (-h["score"], h["id"]))
        return {"query": q, "hits": hits[: int(limit or 5)]}

    def doc_section(self, section_id):
        row = db.query_one(
            "SELECT s.*, d.name AS doc_name FROM ctx_doc_section s JOIN ctx_doc d ON d.id = s.doc_id WHERE s.id=%s",
            (int(section_id),),
        )
        if not row:
            raise ValueError("章节不存在: " + str(section_id))
        return dict(row)

    def doc_list(self):
        return [dict(r) for r in db.query("SELECT * FROM ctx_doc ORDER BY id DESC")]


from .summarize import llm_summary as _summarize

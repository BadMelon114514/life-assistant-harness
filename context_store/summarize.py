# -*- coding: utf-8 -*-
"""
章节摘要生成：优先调用 DeepSeek（OpenAI 兼容接口），失败时退化为截断摘要。
API Key 读取顺序：环境变量 CTX_DEEPSEEK_KEY → 数据库 farming_harness.t_settings(api.key)。
"""
import json
import os
import urllib.request

from . import db as _db

DEFAULT_BASE = "https://api.deepseek.com"
DEFAULT_MODEL = "deepseek-v4-flash"


def _settings(key):
    try:
        row = _db.query_one(
            "SELECT setting_value FROM t_settings WHERE setting_key = %s", (key,)
        )
        return (row or {}).get("setting_value") or ""
    except Exception:
        return ""


def _api_config():
    key = os.environ.get("CTX_DEEPSEEK_KEY") or _settings("api.key")
    base = _settings("api.baseUrl") or DEFAULT_BASE
    model = _settings("api.model") or DEFAULT_MODEL
    return key, base.rstrip("/"), model


def llm_summary(text, max_chars=140):
    """生成 1-2 句摘要；无 Key 或调用失败时截断兜底。"""
    text = (text or "").strip()
    if not text:
        return ""
    key, base, model = _api_config()
    if not key:
        return _fallback(text, max_chars)
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": "你是文档摘要助手，只输出 1-2 句中文摘要，不要任何前缀或解释。"},
            {"role": "user", "content": "请为以下章节内容生成摘要：\n\n" + text[:4000]},
        ],
        "temperature": 0.2,
        "max_tokens": 200,
    }
    req = urllib.request.Request(
        base + "/chat/completions",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + key},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        content = data["choices"][0]["message"]["content"].strip()
        return content or _fallback(text, max_chars)
    except Exception:
        return _fallback(text, max_chars)


def _fallback(text, max_chars):
    one = " ".join(text.split())
    return one[:max_chars] + ("…" if len(one) > max_chars else "")

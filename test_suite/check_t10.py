# -*- coding: utf-8 -*-
import json
import urllib.request

def api(path):
    with urllib.request.urlopen("http://127.0.0.1:8080" + path, timeout=20) as r:
        return json.loads(r.read().decode("utf-8"))

sessions = api("/api/chat/history/sessions")
print("sessions:", json.dumps(sessions.get("sessions", []), ensure_ascii=False)[:800])
hist = api("/api/chat/history?session=adv_t10")
print("adv_t10 history:", json.dumps(hist, ensure_ascii=False)[:600])
hist2 = api("/api/chat/history?session=adv_t10b")
print("adv_t10b history:", json.dumps(hist2, ensure_ascii=False)[:400])

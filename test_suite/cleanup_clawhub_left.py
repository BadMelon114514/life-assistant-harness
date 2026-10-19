# -*- coding: utf-8 -*-
import json
import urllib.request


def api(path, method="GET", timeout=30):
    req = urllib.request.Request("http://127.0.0.1:8080" + path, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        c = r.read()
        return json.loads(c.decode("utf-8")) if c else {}


skills = api("/api/skills")
for s in skills:
    if s.get("category") == "ClawHub" or s.get("name") == "xhs-note-analyst":
        r = api(f"/api/skills/{s['id']}", method="DELETE")
        print("cleaned:", s.get("name"), r)
left = [s.get("name") for s in api("/api/skills")]
print("剩余技能数:", len(left))

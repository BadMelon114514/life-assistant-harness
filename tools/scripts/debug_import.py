# -*- coding: utf-8 -*-
import json
import urllib.request
req = urllib.request.Request("http://127.0.0.1:8080/api/mcp/import-openclaw", data=b"{}", headers={"Content-Type": "application/json"}, method="POST")
with urllib.request.urlopen(req, timeout=60) as r:
    print(r.status)
    print(r.read().decode("utf-8")[:800])

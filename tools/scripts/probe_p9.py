# -*- coding: utf-8 -*-
import json, urllib.request

def ask(msg, sess):
    data = json.dumps({"message": msg, "session": sess}, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request("http://127.0.0.1:8080/api/assistant/chat", data=data, headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=300) as r:
        j = json.loads(r.read().decode("utf-8"))
    print("=== " + sess + " ===")
    print("MSG:", msg)
    print("REPLY:", (j.get("reply") or "")[:700])
    print("TOOL:", j.get("toolLog"))
    print("STEPS:", " | ".join(s.get("step","") + ":" + s.get("detail","") for s in (j.get("steps") or [])))
    print()

ask("先读取 README.md 的开头部分，再读取 docs/操作指南.md 的开头部分，然后比较两者内容", "p9-files")
ask("总结一下知识库里的半导体报告的主要观点", "p9-kb")

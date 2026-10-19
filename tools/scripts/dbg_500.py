import json, urllib.request, urllib.error, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
b = json.dumps({"message": "现在几点了", "session": "dbg500"}).encode('utf-8')
req = urllib.request.Request("http://127.0.0.1:8080/api/assistant/chat", data=b,
                             headers={"Content-Type": "application/json"})
try:
    r = urllib.request.urlopen(req, timeout=120)
    print('status:', r.status)
    print(r.read().decode('utf-8')[:500])
except urllib.error.HTTPError as e:
    print('HTTPError:', e.code)
    body = e.read().decode('utf-8', errors='replace')
    print('body:', body[:800])

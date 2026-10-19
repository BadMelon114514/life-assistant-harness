import urllib.request, json, time

# 1. 测试天气工具：用户说"获取天气今日热点"
print('=== 测试 1: 获取天气 ===')
body = json.dumps({'message': '获取天气今日热点'}).encode('utf-8')
req = urllib.request.Request('http://localhost:8080/api/assistant/chat',
                             data=body, headers={'Content-Type': 'application/json'},
                             method='POST')
try:
    r = urllib.request.urlopen(req, timeout=60)
    d = json.loads(r.read().decode('utf-8', 'ignore'))
    print('toolUsed:', d.get('toolUsed'))
    print('toolLog:', d.get('toolLog'))
    steps = d.get('steps', [])
    for s in steps:
        print(' -', s.get('step'), ':', s.get('detail'))
    print('reply (前200):', d.get('reply', '')[:200])
except Exception as e:
    print('ERR:', e)

print()
# 2. 测试 how-to 优化：用户问"如何使用联网搜索"不应触发搜索
print('=== 测试 2: 如何使用联网搜索（how-to） ===')
body = json.dumps({'message': '如何使用联网搜索'}).encode('utf-8')
req = urllib.request.Request('http://localhost:8080/api/assistant/chat',
                             data=body, headers={'Content-Type': 'application/json'},
                             method='POST')
try:
    r = urllib.request.urlopen(req, timeout=60)
    d = json.loads(r.read().decode('utf-8', 'ignore'))
    steps = d.get('steps', [])
    for s in steps:
        print(' -', s.get('step'), ':', s.get('detail'))
    print('reply (前200):', d.get('reply', '')[:200])
except Exception as e:
    print('ERR:', e)

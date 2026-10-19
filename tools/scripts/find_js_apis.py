import urllib.request, re

req = urllib.request.Request('https://clawhub.ai/assets/index-D5F_NfzT.js', headers={'User-Agent': 'Mozilla/5.0'})
js = urllib.request.urlopen(req, timeout=30).read().decode('utf-8', 'ignore')
print('js len:', len(js))

# 提取所有含 skills 的路径片段
paths = set(re.findall(r'["\'](/[^"\']*skills[^"\']*)["\']', js))
print('=== skills 相关路径 ===')
for p in sorted(paths):
    print(p)

# 提取所有 /api/ 路径
apis = set(re.findall(r'["\'](/api/[^"\']{2,80})["\']', js))
print('=== /api/ 路径 ===')
for a in sorted(apis):
    print(a)

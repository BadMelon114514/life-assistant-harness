import urllib.request, re

# 获取 ClawHub 页面
req = urllib.request.Request('https://clawhub.ai/', headers={'User-Agent': 'Mozilla/5.0'})
html = urllib.request.urlopen(req, timeout=15).read().decode('utf-8', 'ignore')
print('html len:', len(html))

# 提取 JS 资源
js_urls = re.findall(r'src="([^"]+\.js[^"]*)"', html)
print('JS resources:', js_urls)

# 提取内联脚本中的 API 路径
apis = set(re.findall(r'/api/v1/[a-zA-Z0-9/_{}.$?&=-]+', html))
print('=== api/v1 路径 ===')
for a in sorted(apis):
    print(a)

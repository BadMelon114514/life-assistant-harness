import os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
p = r'../../src\main\java\com\badmelon\farming\controller\AssistantController.java'
with open(p, 'rb') as f:
    raw = f.read()
print('前 100 字节 hex:', raw[:20].hex())
print('尝试 utf-8:')
try:
    text = raw.decode('utf-8')
    lines = text.split('\n')
    for i in range(110, min(120, len(lines))):
        print(f'{i+1}: {repr(lines[i])}')
except Exception as e:
    print('utf-8 fail:', e)
    print('尝试 gb18030:')
    try:
        text = raw.decode('gb18030')
        lines = text.split('\n')
        for i in range(110, min(120, len(lines))):
            print(f'{i+1}: {repr(lines[i])}')
    except Exception as e2:
        print('gb18030 fail:', e2)

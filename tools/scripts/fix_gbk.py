import sys
import os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
p = r'../../src\main\java\com\badmelon\farming\controller\AssistantController.java'
with open(p, 'rb') as f:
    raw = f.read()
text = raw.decode('gb18030', errors='replace')

repl = [
    ('* 用户确认后执行待确认的高危操作。', '* Confirm and execute pending high-risk operations.'),
    ('*  - 文件操作 body: {"op": "write_file|delete_file|run_script|mkdir", "path": "...", "content": "..."}', '*  - File op body: {"op": "write_file|delete_file|run_script|mkdir", "path": "...", "content": "..."}'),
    ('*  - MCP 操作 body: {"endpoint": "...", "args": "..."}', '*  - MCP op body: {"endpoint": "...", "args": "..."}'),
]
for o, n in repl:
    if o in text:
        text = text.replace(o, n, 1)
        print('replaced')

# 以 GBK 写回（保持原文件编码，javac 用平台默认 GBK 读）
with open(p, 'wb') as f:
    f.write(text.encode('gbk', errors='replace'))
print('written GBK')

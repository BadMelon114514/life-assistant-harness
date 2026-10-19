import os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
p = r'../../src\main\java\com\badmelon\farming\controller\AssistantController.java'
with open(p, 'rb') as f:
    raw = f.read()
text = raw.decode('utf-8')
# 替换 112-114 行 Javadoc 中的中文标点为英文
old1 = '* 用户确认后执行待确认的高危操作。'
new1 = '* Confirm and execute pending high-risk operations.'
if old1 in text:
    text = text.replace(old1, new1, 1)
    print('replaced 1')
old2 = '*  - 文件操作 body: {"op": "write_file|delete_file|run_script|mkdir", "path": "...", "content": "..."}'
new2 = '*  - File op body: {"op": "write_file|delete_file|run_script|mkdir", "path": "...", "content": "..."}'
if old2 in text:
    text = text.replace(old2, new2, 1)
    print('replaced 2')
old3 = '*  - MCP 操作 body: {"endpoint": "...", "args": "..."}'
new3 = '*  - MCP op body: {"endpoint": "...", "args": "..."}'
if old3 in text:
    text = text.replace(old3, new3, 1)
    print('replaced 3')
# 写回 UTF-8
with open(p, 'w', encoding='utf-8', newline='') as f:
    f.write(text)
print('written utf-8')

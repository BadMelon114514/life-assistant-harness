import sys, io
import os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
import os, glob

SRC = r'../../src\main\java'
# 用 Python 的 'gbk' 编解码器严格转换（GBK 字节 → unicode → UTF-8 字节）
# GBK 是 GB18030 子集，对合法 GBK 字节能正确解码；非法字节用 errors='replace'
for p in glob.glob(os.path.join(SRC, '**', '*.java'), recursive=True):
    with open(p, 'rb') as f:
        raw = f.read()
    # 尝试 GBK 解码（GBK 是平台原生中文编码，覆盖大部分中文）
    try:
        text = raw.decode('gbk')
    except UnicodeDecodeError:
        # 部分字节非 GBK（之前 read_file 写入的 utf-8 混合），退回 gb18030 容错
        text = raw.decode('gb18030', errors='replace')
    # 替换已知 Javadoc 中文标点为英文（避免 javac 严格解析）
    repls = [
        ('* 用户确认后执行待确认的高危操作。', '* Confirm and execute pending high-risk operations.'),
        ('*  - 文件操作 body: {"op": "write_file|delete_file|run_script|mkdir", "path": "...", "content": "..."}',
         '*  - File op body: {"op": "write_file|delete_file|run_script|mkdir", "path": "...", "content": "..."}'),
        ('*  - MCP 操作 body: {"endpoint": "...", "args": "..."}',
         '*  - MCP op body: {"endpoint": "...", "args": "..."}'),
    ]
    for o, n in repls:
        text = text.replace(o, n)
    # 写为 UTF-8
    with open(p, 'w', encoding='utf-8', newline='') as f:
        f.write(text)
print('converted')

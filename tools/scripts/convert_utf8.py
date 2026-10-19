import sys
import os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

# 转换所有 Java 源文件：从原编码（GBK/混合）转为 UTF-8 以匹配 Gradle 7+ 默认 javac UTF-8
import os
import glob

SRC_DIR = r'../../src\main\java'
files = glob.glob(os.path.join(SRC_DIR, '**', '*.java'), recursive=True)
print(f'files: {len(files)}')

for p in files:
    with open(p, 'rb') as f:
        raw = f.read()
    # 用 GB18030 容错读出中文
    text = raw.decode('gb18030', errors='replace')
    # 替换 112 行附近的旧 Javadoc 中文标点（AssistantController 已知）
    repl = [
        ('* 用户确认后执行待确认的高危操作。', '* Confirm and execute pending high-risk operations.'),
        ('*  - 文件操作 body: {"op": "write_file|delete_file|run_script|mkdir", "path": "...", "content": "..."}', '*  - File op body: {"op": "write_file|delete_file|run_script|mkdir", "path": "...", "content": "..."}'),
        ('*  - MCP 操作 body: {"endpoint": "...", "args": "..."}', '*  - MCP op body: {"endpoint": "...", "args": "..."}'),
    ]
    new = text
    for o, n in repl:
        if o in new:
            new = new.replace(o, n, 1)
    # 写 UTF-8
    with open(p, 'w', encoding='utf-8', newline='') as f:
        f.write(new)
print('all converted to UTF-8')

import sys, io, os, glob
import os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

broken = []   # 含替换字符/非法 UTF-8
ok = []
for p in glob.glob(r'../../src\main\java\**\*.java', recursive=True):
    with open(p, 'rb') as f:
        raw = f.read()
    try:
        text = raw.decode('utf-8')
        # 含 Unicode 替换字符或明显错位私有区字符
        if '\ufffd' in text or any('\ue000' <= c <= '\uf8ff' for c in text):
            broken.append(p)
        else:
            ok.append(p)
    except UnicodeDecodeError:
        broken.append(p)

print('总文件数:', len(broken) + len(ok))
print('损坏文件数:', len(broken))
for p in broken:
    print('  BROKEN:', p)
print('完好文件数:', len(ok))

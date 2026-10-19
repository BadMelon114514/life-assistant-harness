import sys, io, os, glob
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
OUT = r'recover_jars\recovered_src'
files = sorted(glob.glob(os.path.join(OUT, '**', '*.java'), recursive=True))
print('恢复文件数:', len(files))
for p in files[:15]:
    print(' ', os.path.relpath(p, OUT))
# 检查中文 String 是否正确（如"个人生活助理"）
def has_cn(p, kw):
    try:
        with open(p, 'r', encoding='utf-8') as f:
            return kw in f.read()
    except Exception:
        return False
found = [p for p in files if 'AssistantService.java' in p]
for p in found:
    with open(p, 'r', encoding='utf-8') as f:
        t = f.read()
    print('AssistantService 含个人生活助理:', '个人生活助理' in t)
    print('AssistantService 长度:', len(t))
    print(' 摘要:', t[:300].replace('\n', ' '))

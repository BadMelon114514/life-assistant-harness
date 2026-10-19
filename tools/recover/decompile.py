import zipfile, os, shutil, subprocess, sys, io
import os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

# 1. 解压 fat jar 到临时目录
EXTRACT = r'recover_jars\classes'
os.makedirs(EXTRACT, exist_ok=True)
z = zipfile.ZipFile(r'../../build\libs\farming-0.0.1-SNAPSHOT.jar')
for n in z.namelist():
    if n.startswith('BOOT-INF/classes/'):
        target = os.path.join(EXTRACT, n[len('BOOT-INF/classes/'):])
        if n.endswith('/'):
            os.makedirs(target, exist_ok=True)
        else:
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with open(target, 'wb') as f:
                f.write(z.read(n))
print('extracted classes:', EXTRACT)

# 2. CFR 反编译到 recovered_src
OUT = r'recover_jars\recovered_src'
os.makedirs(OUT, exist_ok=True)
cmd = ['java', '-jar', 'cfr.jar', EXTRACT, '--outputdir', OUT, '--silent', 'true', '--comments', 'false']
r = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
print('cfr exit:', r.returncode)
if r.stderr:
    print('stderr tail:', r.stderr[-500:])
# 统计恢复的文件
count = 0
for root, dirs, files in os.walk(OUT):
    for f in files:
        if f.endswith('.java'):
            count += 1
print('recovered java files:', count)

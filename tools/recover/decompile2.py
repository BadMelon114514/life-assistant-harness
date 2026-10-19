import subprocess, os, sys, io, glob, shutil
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

# 1. 打包 classes 目录为 jar
CLASSES = r'recover_jars\classes'
JAR = r'recover_jars\classes.jar'
# 用 jar 工具打包（cd 到 classes 内）
cwd = os.path.abspath(CLASSES)
r = subprocess.run(['jar', 'cf', os.path.abspath(JAR), '.'], cwd=cwd, capture_output=True, text=True, timeout=120)
print('jar exit:', r.returncode, r.stderr[:200])
print('jar size:', os.path.getsize(JAR) if os.path.exists(JAR) else 0)

# 2. CFR 反编译 classes.jar
OUT = r'recover_jars\recovered_src'
os.makedirs(OUT, exist_ok=True)
cmd = ['java', '-jar', 'cfr.jar', JAR, '--outputdir', OUT, '--silent', 'true', '--comments', 'false']
r2 = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
print('cfr exit:', r2.returncode)
if r2.stderr:
    print('stderr tail:', r2.stderr[-300:])
count = sum(1 for _ in glob.glob(os.path.join(OUT, '**', '*.java'), recursive=True))
print('recovered java files:', count)

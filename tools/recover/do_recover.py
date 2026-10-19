import sys, io, os, glob, shutil
import os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

SRC = r'../../src\main\java'
REC = r'recover_jars\recovered_src'
BAK = r'recover_jars\src_broken_backup'

# 1. 备份损坏的 src/main/java
if os.path.exists(BAK):
    shutil.rmtree(BAK)
shutil.copytree(SRC, BAK)
print('备份损坏源码:', BAK)

# 2. 清空 src/main/java 的 com 目录
com_dir = os.path.join(SRC, 'com')
if os.path.exists(com_dir):
    shutil.rmtree(com_dir)
print('已清空 src/main/java')

# 3. 复制反编译恢复的源码
count = 0
for p in glob.glob(os.path.join(REC, '**', '*.java'), recursive=True):
    rel = os.path.relpath(p, REC)
    target = os.path.join(SRC, rel)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    shutil.copy2(p, target)
    count += 1
print('已恢复文件数:', count)

# 4. 删除 ScheduledTaskService（jar 无此文件，最后功能未成功编译）
st = os.path.join(SRC, 'com', 'badmelon', 'farming', 'service', 'ScheduledTaskService.java')
if os.path.exists(st):
    os.remove(st)
    print('已移除 ScheduledTaskService（需后续重新实现）')
print('恢复完成')

import zipfile, sys, io, os
import os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
z = zipfile.ZipFile(r'../../build\libs\farming-0.0.1-SNAPSHOT.jar')
base = 'BOOT-INF/classes/com/badmelon/farming/'
names = [n for n in z.namelist() if n.startswith(base) and n.endswith('.class')]
print('jar 内 classes 数:', len(names))
# 列出主要服务类
keys = ['AssistantService', 'ToolScheduler', 'AssistantController', 'AiService',
        'ScheduledTaskService', 'SkillMcpGeneratorService', 'TeammateService', 'MonitorService']
for k in keys:
    found = [n for n in names if '/' + k + '.class' in n]
    print(f'{k}:', 'OK' if found else 'MISSING')
# 检查 jar 时间
print('jar mtime:', os.path.getmtime(r'../../build\libs\farming-0.0.1-SNAPSHOT.jar'))

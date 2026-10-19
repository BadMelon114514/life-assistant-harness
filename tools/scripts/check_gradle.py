import os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
p = r'../../build.gradle'
with open(p, 'r', encoding='utf-8') as f:
    t = f.read()
print('has encoding config:', 'options.encoding' in t)
print('--- last 200 ---')
print(t[-200:])

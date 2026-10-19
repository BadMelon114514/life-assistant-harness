import os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
p = r'../../build.gradle'
with open(p, 'r', encoding='utf-8') as f:
    text = f.read()
if 'options.encoding' in text:
    # 移除 tasks.withType(JavaCompile).configureEach 块
    import re
    new_text = re.sub(r'\n?tasks\.withType\(JavaCompile\)\.configureEach\s*\{[^}]*\}\s*', '\n', text, flags=re.MULTILINE)
    with open(p, 'w', encoding='utf-8') as f:
        f.write(new_text)
    print('removed UTF-8 config')
else:
    print('not configured')

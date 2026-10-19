import os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
p = r'../../build.gradle'
with open(p, 'r', encoding='utf-8') as f:
    text = f.read()
if 'options.encoding' not in text:
    addition = '''
tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}
'''
    if not text.endswith('\n'):
        text += '\n'
    text += addition
    with open(p, 'w', encoding='utf-8') as f:
        f.write(text)
    print('added UTF-8 config')
else:
    print('already configured')

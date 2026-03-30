# 修复 _dispatch 方法体缩进：把方法体内 12 空格开头的行改为 8 空格
import re

with open('d:/nettest/realtime_writer.py', 'r', encoding='utf-8') as f:
    lines = f.readlines()

in_dispatch = False
result = []
for i, line in enumerate(lines):
    if line.rstrip() == '    def _dispatch(self, conn, msg_type, data, fpath):':
        in_dispatch = True
        result.append(line)
        continue
    if in_dispatch:
        # 遇到下一个同级方法（4空格def）就结束
        if re.match(r'    def \w', line) and not line.startswith('        '):
            in_dispatch = False
            result.append(line)
            continue
        # 把 12空格缩进改为 8空格（只处理以12+空格开头的行）
        if line.startswith('            '):
            line = '        ' + line[12:]
        elif line.startswith('        ') and not line.startswith('            '):
            # 8空格行不变
            pass
        result.append(line)
    else:
        result.append(line)

with open('d:/nettest/realtime_writer.py', 'w', encoding='utf-8') as f:
    f.writelines(result)

print('完成')


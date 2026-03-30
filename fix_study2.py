# -*- coding: utf-8 -*-
# 修复skills_s.py中应可学习的战法

in_s = [200194, 200198, 200201, 200204, 200220, 200228, 200235, 200237,
        200252, 200622, 200647, 200687, 200755, 200757, 200800, 200801,
        200847, 200863, 200886, 200900, 200938, 200979, 200980]

in_a_check = [200233, 200241, 200248, 200249, 200643, 200644, 200645,
              200674, 200691, 200729, 200747, 200755, 200757, 200766]

# 修复 skills_s.py
lines = open('battle_sim/skills_s.py', encoding='utf-8').readlines()
changed_s = 0
not_found_s = []
for sid in in_s:
    marker = f'super().__init__({sid},'
    found = False
    for i, line in enumerate(lines):
        if marker in line and ',False,' in line:
            lines[i] = line.replace(',False,', ',True,', 1)
            changed_s += 1
            found = True
            break
        elif marker in line and ',True,' in line:
            found = True  # already True
            break
    if not found:
        not_found_s.append(sid)
open('battle_sim/skills_s.py', 'w', encoding='utf-8').writelines(lines)
print(f'skills_s.py: 修改了 {changed_s} 个')
if not_found_s:
    print(f'  未找到: {not_found_s}')

# 检查 skills_a.py 中那些没有False的
lines_a = open('battle_sim/skills_a.py', encoding='utf-8').readlines()
for sid in in_a_check:
    marker = f'super().__init__({sid},'
    for i, line in enumerate(lines_a):
        if marker in line:
            print(f'  skills_a {sid}: {line.strip()}')
            break


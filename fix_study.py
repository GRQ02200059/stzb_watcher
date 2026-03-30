# -*- coding: utf-8 -*-
# 批量将skills_a.py中应可学习的战法study改为True
# 策略：找到 super().__init__(sid, 这一行，把该行的 ,False, 改为 ,True,

study_ids = [
    200119, 200126, 200127, 200130, 200194, 200198, 200201, 200204,
    200208, 200220, 200228, 200233, 200235, 200237, 200241, 200243,
    200248, 200249, 200252, 200253, 200259, 200261, 200267, 200622,
    200643, 200644, 200645, 200647, 200658, 200659, 200673, 200674,
    200676, 200683, 200684, 200687, 200691, 200714, 200715, 200716,
    200717, 200718, 200721, 200722, 200724, 200728, 200729, 200731,
    200734, 200735, 200747, 200748, 200754, 200755, 200757, 200758,
    200759, 200766, 200767, 200800, 200801, 200847, 200863, 200886,
    200900, 200938, 200979, 200980,
]

lines = open('battle_sim/skills_a.py', encoding='utf-8').readlines()
changed = 0
not_found = []

for sid in study_ids:
    marker = f'super().__init__({sid},'
    found = False
    for i, line in enumerate(lines):
        if marker in line and ',False,' in line:
            lines[i] = line.replace(',False,', ',True,', 1)
            changed += 1
            found = True
            break
    if not found:
        not_found.append(sid)

open('battle_sim/skills_a.py', 'w', encoding='utf-8').writelines(lines)
print(f'完成，修改了 {changed} 个战法的study=True')
if not_found:
    print(f'未找到或已是True: {not_found}')

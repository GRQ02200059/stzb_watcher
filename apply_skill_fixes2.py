# -*- coding: utf-8 -*-
import re

fixes = {
    100023: 200210,  # 曹操 魏武之世 -> 远交近攻
    100648: 200929,  # 张昭 连环锦囊 -> 飒沓如星
    100649: 200883,  # 魏延 奇兵拒北 -> 指麾山岳
    100657: 200184,  # 华雄 万箭齐发 -> 百战老兵
    100677: 200956,  # 公孙瓒 锁甲连环 -> 三军齐出
}

with open('battle_sim/data.py', encoding='utf-8') as f:
    src = f.read()

count = 0
for hid, sid in fixes.items():
    old_sid = None
    # 找当前skill值
    m = re.search(rf"'id':{hid},[^{{}}]*?'skill':(\d+)", src)
    if m:
        old_sid = int(m.group(1))
    pattern = rf"('id':{hid},[^{{}}]*?'skill'):{old_sid if old_sid else 0}"
    new = rf"\g<1>:{sid}"
    new_src, n = re.subn(pattern, new, src)
    if n:
        src = new_src
        count += n
        print(f'OK {hid}: {old_sid} -> {sid}')
    else:
        print(f'MISS {hid}')

with open('battle_sim/data.py', 'w', encoding='utf-8') as f:
    f.write(src)

print(f'\n修复完成: {count} 处')


# -*- coding: utf-8 -*-
# 深度解析 00000202
import json, sys
sys.stdout = open('D:/nettest/parse_202b.txt', 'w', encoding='utf-8')

fpath = 'D:/nettest/capture_new/00000202/cap_20260310235439476_00000202_zlib.json'
with open(fpath, 'r', encoding='utf-8') as f:
    raw = json.load(f)

# config_id 结构分析：看起来是 武将ID(6位) + 兵种(2位)
# 例如 10166201 -> 101662 + 01
# 10000801 -> 100008 + 01
# 11103901 -> 111039 + 01

# skill2 字段：有些是时间戳（unix），有些是0
# 猜测 skill1=主动技能ID, skill2=技能学习时间戳 或 被动技能ID

# effects 格式: "type,value;type,value;"
# 已见: "3,0;" "1,0;" "4,1773738604;"
# 猜测: 1=特殊buff, 3=某状态, 4=某时限效果

records = []
for i in range(0, len(raw)-1, 2):
    uid = raw[i]
    cfg = raw[i+1]
    if not isinstance(cfg, list) or len(cfg) < 4:
        continue
    config_id = str(cfg[0])
    skills_str = cfg[1]
    state = cfg[2]
    effects = cfg[3]
    skills = skills_str.split(',') if skills_str else ['0','0']
    skill1 = skills[0] if len(skills)>0 else '0'
    skill2 = skills[1] if len(skills)>1 else '0'
    
    # config_id 解析
    # 格式推测: 前2位=系列, 中间4位=武将编号, 后2位=阶数
    series = config_id[:2]   # 武将系列
    hero_id = config_id[:-2] # 武将ID（去掉后2位）
    tier = config_id[-2:]    # 后两位
    
    records.append({
        'uid': uid, 'config_id': int(cfg[0]),
        'hero_id': hero_id, 'tier': tier,
        'skill1': skill1, 'skill2': skill2,
        'state': state, 'effects': effects,
    })

# skill2 分析
print('=== skill2 值分布 ===')
from collections import Counter
sk2_types = Counter()
for r in records:
    s2 = r['skill2']
    if s2 == '0':
        sk2_types['0(无)'] += 1
    elif len(s2) == 10 and s2.startswith('17'):  # unix时间戳2025
        sk2_types['时间戳'] += 1
    elif s2.startswith('101'):
        sk2_types[f'技能ID({s2})'] += 1
    else:
        sk2_types[f'其他({s2[:8]})'] += 1
for k,v in sk2_types.most_common(10):
    print(f'  {k}: {v}条')

# effects 分析
print()
print('=== effects 类型分布 ===')
fx_types = Counter()
for r in records:
    e = r['effects']
    if not e:
        fx_types['空'] += 1
    else:
        for part in e.rstrip(';').split(';'):
            if part:
                t = part.split(',')[0]
                fx_types[f'type={t}'] += 1
for k,v in fx_types.most_common():
    print(f'  {k}: {v}条')

# hero_id/tier 分析
print()
print('=== tier(后2位) 分布 ===')
for k,v in Counter(r['tier'] for r in records).most_common():
    print(f'  tier={k}: {v}条')

# 打印有 effects 的记录样本
print()
print('=== 有effects的记录 (前20) ===')
with_fx = [r for r in records if r['effects']]
for r in with_fx[:20]:
    print(f'  uid={r["uid"]} cfg={r["config_id"]} hero={r["hero_id"]} skill1={r["skill1"]} effects={r["effects"]!r}')

# config_id=301,302 特殊记录
print()
print('=== config_id 300-303 特殊记录 ===')
special = [r for r in records if r['config_id'] in (301,302,303)]
for r in special:
    print(f'  uid={r["uid"]} cfg={r["config_id"]} skill1={r["skill1"]} skill2={r["skill2"]} effects={r["effects"]!r}')

sys.stdout.close()


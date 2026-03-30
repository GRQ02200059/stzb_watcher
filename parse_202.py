# -*- coding: utf-8 -*-
# 解析 00000202 武将配置数据
import json, sys
sys.stdout = open('D:/nettest/parse_202.txt', 'w', encoding='utf-8')

fpath = 'D:/nettest/capture_new/00000202/cap_20260310235439476_00000202_zlib.json'
with open(fpath, 'r', encoding='utf-8') as f:
    raw = json.load(f)

# 格式是交替的 [userid, [config_id, "skill1,skill2", state, "effect;"], userid, [...]]
print(f'总元素数: {len(raw)}, 条数: {len(raw)//2}')
print()

# 已知的技能ID映射（部分）
SKILL_NAMES = {
    '101376': '龙胆',
    '101632': '疾风',
    '101657': '霸王',
    '101524': '虎威',
    '101649': '雷霆',
    '101612': '铁骑',
    '101646': '铁壁',
    '101604': '先登',
    '101578': '连弩',
    '101511': '长驱',
    '101522': '步步',
    '101581': '奇谋',
    '101623': '虎豹',
    '101410': '神射',
    '101043': '老当',
    '101451': '马踏',
    '101497': '魄力',
    '101217': '用兵',
    '101176': '火攻',
    '101171': '离间',
    '101096': '反计',
}

records = []
for i in range(0, len(raw)-1, 2):
    uid = raw[i]
    cfg = raw[i+1]
    if not isinstance(cfg, list) or len(cfg) < 4:
        continue
    config_id = cfg[0]   # 武将配置ID
    skills_str = cfg[1]  # "skillA,skillB"
    state = cfg[2]       # 状态
    effects = cfg[3]     # 效果字符串
    
    skills = skills_str.split(',') if skills_str else []
    skill_names = [SKILL_NAMES.get(s, s) for s in skills if s and s != '0']
    
    records.append({
        'uid': uid,
        'config_id': config_id,
        'skill1': skills[0] if len(skills)>0 else '',
        'skill2': skills[1] if len(skills)>1 else '',
        'skill1_name': SKILL_NAMES.get(skills[0], skills[0]) if skills else '',
        'skill2_name': SKILL_NAMES.get(skills[1], skills[1]) if len(skills)>1 else '',
        'state': state,
        'effects': effects,
    })

print(f'解析出 {len(records)} 条武将配置')
print()

# 打印自己(16084)的数据
my = [r for r in records if r['uid'] == 16084]
print('=== 自己的武将配置 (uid=16084) ===')
for r in my:
    print(f'  uid={r["uid"]} cfg={r["config_id"]} skill1={r["skill1"]}({r["skill1_name"]}) skill2={r["skill2"]}({r["skill2_name"]}) state={r["state"]} effects={r["effects"]!r}')

print()
print('=== 前20条 ===')
for r in records[:20]:
    print(f'  uid={r["uid"]} cfg={r["config_id"]} skills={r["skill1_name"]}/{r["skill2_name"]} effects={r["effects"]!r}')

# config_id 前4位是武将类型
print()
print('=== config_id 分布 (前缀) ===')
from collections import Counter
prefixes = Counter(str(r['config_id'])[:3] for r in records)
for k,v in sorted(prefixes.items(), key=lambda x:-x[1]):
    print(f'  {k}xxx: {v}条')

sys.stdout.close()


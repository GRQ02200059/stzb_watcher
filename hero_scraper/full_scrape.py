# -*- coding: utf-8 -*-
"""
率土之滨 武将+技能 完整爬取 & 头像下载
"""
import requests, warnings, json, os, time, html as htmllib
from concurrent.futures import ThreadPoolExecutor, as_completed
warnings.filterwarnings('ignore')

os.makedirs('output/avatars', exist_ok=True)
os.makedirs('output/skills_icons', exist_ok=True)

def make_session():
    s = requests.Session()
    s.verify = False
    s.headers.update({
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
        'Referer': 'https://stzb.163.com/card_list.html',
    })
    return s

MAIN_SESSION = make_session()

def unescape(v):
    if isinstance(v, str):
        return htmllib.unescape(v)
    return v

def unescape_dict(d):
    return {k: unescape(v) for k, v in d.items()}

# ========== 1. 读取已下载数据 ==========
print('=' * 60)
print('1. 读取武将和技能数据...')

with open('output/heroes_full.json', 'r', encoding='utf-8') as f:
    heroes = json.load(f)
with open('output/skills_full.json', 'r', encoding='utf-8') as f:
    skills = json.load(f)

print(f'   武将: {len(heroes)} 个')
print(f'   技能: {len(skills)} 个')

# 建立技能映射
skill_map = {sk['id']: sk for sk in skills if 'id' in sk}

# ========== 2. 整理武将数据 ==========
print()
print('2. 整理武将数据...')

AVATAR_BIG = 'https://stzb.res.netease.com/pc/qt/20170323200251/data/small/card_{id}.jpg'
AVATAR_SMALL = 'https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_small_{id}.jpg'
SKILL_ICON = 'https://stzb.res.netease.com/pc/qt/20170323200251/data/jineng/tactics_0{idx}.png'

# 技能类型对应图标索引
SKILL_TYPE_ICON = {'主动': 1, '被动': 2, '指挥': 3, '追击': 4}

hero_list = []
for h in heroes:
    icon_id = h.get('iconId') or h.get('id')
    method_ids = []
    for key in ['methodId', 'methodId1', 'methodId2']:
        v = h.get(key)
        if v:
            try:
                mid = int(v)
                if mid > 0:
                    method_ids.append(mid)
            except: pass
    
    hero_skills = []
    for mid in method_ids:
        if mid in skill_map:
            sk = dict(skill_map[mid])
            sk_type = sk.get('type', '')
            icon_idx = SKILL_TYPE_ICON.get(sk_type, 1)
            sk['icon_url'] = SKILL_ICON.format(idx=icon_idx)
            sk['icon_local'] = f'skills_icons/tactics_0{icon_idx}.png'
            hero_skills.append(sk)
    
    hero_obj = {
        'id': h.get('id'),
        'name': h.get('name', ''),
        'iconId': icon_id,
        'country': h.get('country', ''),
        'quality': h.get('quality', ''),
        'cost': h.get('cost', ''),
        'type': h.get('type', ''),
        'sex': h.get('sex', ''),
        'speed': h.get('speed', ''),
        'attack': h.get('attack', ''),
        'defense': h.get('def', ''),
        'ruse': h.get('ruse', ''),
        'distance': h.get('distance', ''),
        'policyName': h.get('policyName', ''),
        'policyDesc': h.get('policyDesc', ''),
        'desc': h.get('desc', ''),
        'groupName': h.get('groupName', ''),
        'avatar_big': AVATAR_BIG.format(id=icon_id),
        'avatar_small': AVATAR_SMALL.format(id=icon_id),
        'avatar_local': f'avatars/{icon_id}.jpg',
        'skills': hero_skills,
    }
    hero_list.append(hero_obj)

print(f'   整理完成: {len(hero_list)} 个武将')

# 示例输出
for h in hero_list[:3]:
    print(f'   {h["id"]} {h["name"]} [{h["country"]}] {h["quality"]} 技能:{[s["name"] for s in h["skills"]]}')

# ========== 3. 保存完整JSON ==========
print()
print('3. 保存数据...')

output = {
    'meta': {
        'hero_count': len(hero_list),
        'skill_count': len(skills),
        'avatar_big_base': 'https://stzb.res.netease.com/pc/qt/20170323200251/data/small/card_{id}.jpg',
        'avatar_small_base': 'https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_small_{id}.jpg',
        'skill_icon_base': 'https://stzb.res.netease.com/pc/qt/20170323200251/data/jineng/tactics_0{idx}.png',
    },
    'heroes': hero_list,
    'skills': skills,
}

with open('output/stzb_data.json', 'w', encoding='utf-8') as f:
    json.dump(output, f, ensure_ascii=False, indent=2)
print('   已保存 output/stzb_data.json')

# 也单独保存武将列表
with open('output/heroes.json', 'w', encoding='utf-8') as f:
    json.dump(hero_list, f, ensure_ascii=False, indent=2)
print('   已保存 output/heroes.json')

# ========== 4. 下载头像 ==========
print()
print('4. 下载武将头像（大图）...')

def download_avatar(hero):
    icon_id = hero['iconId']
    fpath = f'output/avatars/{icon_id}.jpg'
    if os.path.exists(fpath):
        return f'SKIP {icon_id}'
    url = hero['avatar_big']
    try:
        sess = make_session()
        r = sess.get(url, timeout=10)
        if r.status_code == 200 and len(r.content) > 100:
            with open(fpath, 'wb') as f:
                f.write(r.content)
            return f'OK {icon_id} {hero["name"]} ({len(r.content)} bytes)'
        else:
            # 回退小图
            r2 = sess.get(hero['avatar_small'], timeout=10)
            if r2.status_code == 200:
                with open(fpath, 'wb') as f:
                    f.write(r2.content)
                return f'FALLBACK {icon_id} {hero["name"]}'
            return f'FAIL {icon_id} [{r.status_code}]'
    except Exception as e:
        return f'ERR {icon_id}: {str(e)[:50]}'

ok = skip = fail = 0
with ThreadPoolExecutor(max_workers=8) as ex:
    futures = {ex.submit(download_avatar, h): h for h in hero_list}
    for i, fut in enumerate(as_completed(futures), 1):
        result = fut.result()
        if result.startswith('OK'):
            ok += 1
        elif result.startswith('SKIP'):
            skip += 1
        else:
            fail += 1
        if i % 20 == 0 or i == len(hero_list):
            print(f'   进度: {i}/{len(hero_list)} OK={ok} SKIP={skip} FAIL={fail}')
        if not result.startswith('SKIP') and not result.startswith('OK'):
            print(f'   {result}')

print(f'   头像下载完成: OK={ok} SKIP={skip} FAIL={fail}')

# ========== 5. 下载技能图标 ==========
print()
print('5. 下载技能类型图标 (1-4)...')
for i in range(1, 5):
    url = f'https://stzb.res.netease.com/pc/qt/20170323200251/data/jineng/tactics_0{i}.png'
    fpath = f'output/skills_icons/tactics_0{i}.png'
    if not os.path.exists(fpath):
        r = MAIN_SESSION.get(url, timeout=10)
        if r.status_code == 200:
            with open(fpath, 'wb') as f:
                f.write(r.content)
            print(f'   OK tactics_0{i}.png ({len(r.content)} bytes)')
        else:
            print(f'   FAIL [{r.status_code}] {url}')
    else:
        print(f'   SKIP tactics_0{i}.png')

# ========== 6. 统计 ==========
print()
print('=' * 60)
print('完成统计:')
print(f'  武将总数: {len(hero_list)}')
print(f'  技能总数: {len(skills)}')
avatar_files = os.listdir('output/avatars')
print(f'  头像文件: {len(avatar_files)} 张')
skill_files = os.listdir('output/skills_icons')
print(f'  技能图标: {len(skill_files)} 张')
print(f'  数据文件: output/stzb_data.json, output/heroes.json')

# 打印一些有趣的统计
countries = {}
qualities = {}
for h in hero_list:
    c = h['country']
    q = h['quality']
    countries[c] = countries.get(c, 0) + 1
    qualities[q] = qualities.get(q, 0) + 1

print()
print('  按国家分布:', dict(sorted(countries.items(), key=lambda x: -x[1])))
print('  按品质分布:', dict(sorted(qualities.items(), key=lambda x: -x[1])))


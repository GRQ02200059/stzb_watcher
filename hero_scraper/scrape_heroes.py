# -*- coding: utf-8 -*-
"""
率土之滨 武将+技能完整爬取脚本
数据源:
  武将: https://g0.gph.netease.com/ngsocial/community/stzb/cfg/hero_extra.json?gameid=g10
  技能: https://stzb.163.com/json/jineng_list.json
  头像: https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_small_{iconId}.jpg
  技能图: https://stzb.res.netease.com/pc/qt/20170323200251/data/jineng/tactics_{id}.jpg
"""
import requests, warnings, re, json, os, time, html
from concurrent.futures import ThreadPoolExecutor, as_completed
warnings.filterwarnings('ignore')

os.makedirs('output', exist_ok=True)
os.makedirs('output/avatars', exist_ok=True)
os.makedirs('output/skills', exist_ok=True)

s = requests.Session()
s.verify = False
s.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
    'Referer': 'https://stzb.163.com/card_list.html',
    'Accept-Language': 'zh-CN,zh;q=0.9',
})

def unescape(v):
    """HTML实体解码"""
    if isinstance(v, str):
        return html.unescape(v)
    return v

def unescape_dict(d):
    return {k: unescape(v) for k, v in d.items()}

print('=' * 60)
print('1. 获取武将数据...')
r = s.get('https://g0.gph.netease.com/ngsocial/community/stzb/cfg/hero_extra.json?gameid=g10', timeout=20)
heroes_raw = r.json()
print(f'   共 {len(heroes_raw)} 个武将')

# 解码并整理武将数据
heroes = []
for h in heroes_raw:
    hero = unescape_dict(h)
    heroes.append(hero)

# 打印字段名
if heroes:
    print(f'   字段: {list(heroes[0].keys())}')
    # 打印前3个武将
    for h in heroes[:3]:
        print(f'   id={h.get("id")} name={h.get("name","")} iconId={h.get("iconId","")} country={h.get("country","")} quality={h.get("quality","")}')

print()
print('2. 获取技能数据...')
r2 = s.get('https://stzb.163.com/json/jineng_list.json', timeout=20)
skills_raw = r2.json()
print(f'   共 {len(skills_raw)} 个技能')

skills = []
for sk in skills_raw:
    skill = unescape_dict(sk)
    skills.append(skill)

if skills:
    print(f'   字段: {list(skills[0].keys())}')
    for sk in skills[:3]:
        print(f'   id={sk.get("id")} name={sk.get("name","")} type={sk.get("type","")}')

# 建立技能ID到技能的映射
skill_map = {sk['id']: sk for sk in skills if 'id' in sk}

# 尝试解析武将的技能字段，关联技能详情
print()
print('3. 关联武将技能...')
for hero in heroes:
    hero_skills = []
    # 常见技能字段名
    for key in ['methodId', 'method_id', 'skillId', 'skill_id', 'jineng', 'tactics']:
        val = hero.get(key)
        if val:
            try:
                sid = int(val)
                if sid in skill_map:
                    hero_skills.append(skill_map[sid])
            except:
                pass
    # 有些是多个技能，用逗号或分号分隔
    for key in ['methodIds', 'skillIds']:
        val = hero.get(key, '')
        if val:
            for part in re.split(r'[,;]', str(val)):
                try:
                    sid = int(part.strip())
                    if sid in skill_map:
                        hero_skills.append(skill_map[sid])
                except:
                    pass
    hero['_skills'] = hero_skills

# 检查第一个有技能的武将
for hero in heroes:
    if hero.get('_skills'):
        print(f'   示例: {hero.get("name","")} -> 技能: {[sk.get("name") for sk in hero["_skills"]]}')
        break

# 保存完整数据
print()
print('4. 保存数据...')
with open('output/heroes_full.json', 'w', encoding='utf-8') as f:
    json.dump(heroes, f, ensure_ascii=False, indent=2)
with open('output/skills_full.json', 'w', encoding='utf-8') as f:
    json.dump(skills, f, ensure_ascii=False, indent=2)
print('   已保存 output/heroes_full.json')
print('   已保存 output/skills_full.json')

# 测试头像URL
print()
print('5. 测试头像URL...')
avatar_base = 'https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_small_'
skill_img_base = 'https://stzb.res.netease.com/pc/qt/20170323200251/data/jineng/tactics_'

# 找几个有iconId的武将测试
test_heroes = [h for h in heroes if h.get('iconId')][:5]
for h in test_heroes:
    icon_id = h.get('iconId', '')
    for ext in ['.jpg', '.png', '']:
        url = avatar_base + str(icon_id) + ext
        try:
            r = s.head(url, timeout=6)
            if r.status_code == 200:
                print(f'   OK [{r.status_code}] {h.get("name","")} -> {url} ({r.headers.get("Content-Length","?")}) bytes')
                h['_avatar_url'] = url
                break
            else:
                print(f'   [{r.status_code}] {url}')
        except Exception as e:
            print(f'   ERR {url}: {str(e)[:50]}')

# 测试技能图URL
print()
print('6. 测试技能图URL...')
test_skills = skills[:5]
for sk in test_skills:
    sid = sk.get('id', '')
    for ext in ['.jpg', '.png', '']:
        url = skill_img_base + str(sid) + ext
        try:
            r = s.head(url, timeout=6)
            if r.status_code == 200:
                print(f'   OK [{r.status_code}] {sk.get("name","")} -> {url}')
                break
            else:
                print(f'   [{r.status_code}] {url}')
        except Exception as e:
            print(f'   ERR {url}: {str(e)[:50]}')

print()
print('Done! 请查看 output/ 目录')


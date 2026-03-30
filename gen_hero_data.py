# -*- coding: utf-8 -*-
# 生成前端用的英雄和技能数据JS文件
import json

# 英雄数据
with open('d:/nettest/hero_scraper/output/heroes.json','r',encoding='utf-8') as f:
    heroes = json.load(f)

hero_map = {}
for h in heroes:
    hid = str(h['id'])
    quality = h.get('quality','') # '3-R','4-SR','5-SSR' 等
    # 解析红度：quality里的数字
    q_parts = quality.split('-')
    rarity = q_parts[1] if len(q_parts)>1 else quality
    hero_map[hid] = {
        'id': h['id'],
        'name': h['name'],
        'avatar': f"https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_small_{h['id']}.jpg",
        'quality': quality,
        'rarity': rarity,  # R/SR/SSR/UR
        'country': h.get('country',''),
        'type': h.get('type',''),
    }

# 技能数据
with open('d:/nettest/hero_scraper/output/skills_full.json','r',encoding='utf-8') as f:
    skills = json.load(f)
skill_map = {str(s['id']): s['name'] for s in skills if s.get('id')}

# 输出JS文件
js = f'''// 自动生成 - 英雄和技能数据
const HERO_MAP = {json.dumps(hero_map, ensure_ascii=False)};
const SKILL_MAP = {json.dumps(skill_map, ensure_ascii=False)};

function getHero(id) {{ return HERO_MAP[String(id)] || {{name:'武将'+id, avatar:'', rarity:'', quality:''}}; }}
function getSkill(id) {{ return SKILL_MAP[String(id)] || '技能'+id; }}
'''

with open('d:/nettest/static/hero_data.js','w',encoding='utf-8') as f:
    f.write(js)
print(f'生成 hero_data.js: {len(hero_map)} 英雄, {len(skill_map)} 技能')


# -*- coding: utf-8 -*-
import json, os

with open('output/heroes.json', 'r', encoding='utf-8') as f:
    heroes = json.load(f)
with open('output/skills_full.json', 'r', encoding='utf-8') as f:
    skills = json.load(f)

hero_map = {}
for h in heroes:
    hid = h.get('id')
    name = (h.get('name') or '').strip()
    if hid and name:
        hero_map[hid] = name

skill_map = {}
for sk in skills:
    sid = sk.get('id')
    name = (sk.get('name') or '').strip()
    stype = (sk.get('type') or '').strip()
    soldier = (sk.get('soldierType') or '').strip()
    target = (sk.get('targetShow') or sk.get('targetType') or '').strip()
    if sid and name:
        skill_map[sid] = {'name': name, 'type': stype, 'soldier': soldier, 'target': target}

hero_detail = {}
for h in heroes:
    hid = h.get('id')
    if not hid: continue
    hero_detail[hid] = {
        'name': (h.get('name') or '').strip(),
        'country': (h.get('country') or '').strip(),
        'quality': (h.get('quality') or '').strip(),
        'cost': h.get('cost', ''),
        'avatar': 'https://stzb.res.netease.com/pc/qt/20170323200251/data/small/card_' + str(hid) + '.jpg',
        'avatar_small': 'https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_small_' + str(hid) + '.jpg',
        'skills': [s['id'] for s in h.get('skills', []) if 'id' in s],
    }

hero_names_json = json.dumps(hero_map, ensure_ascii=False)
skill_data_json = json.dumps(skill_map, ensure_ascii=False)
hero_detail_json = json.dumps(hero_detail, ensure_ascii=False)

msg_names = "{'0000000a':'\u6218\u6597\u8be6\u60c5','000002bc':'\u8054\u76df\u6392\u884c\u699c','00015f95':'\u6570\u636e\u5e93\u540c\u6b65','0000029f':'\u6b66\u5c06\u89e3\u9501','00000fd4':'\u6b66\u5c06\u89e3\u9501(\u914d\u5957)','00000064':'\u8054\u76df\u8be6\u60c5','000009fe':'\u8054\u76df\u8be6\u60c5(\u914d\u5957)','0000262a':'\u73a9\u5bb6\u5217\u8868','00002624':'\u73a9\u5bb6\u5217\u8868(\u914d\u5957)','00001857':'\u6570\u636e\u5e93Schema','0003072e':'\u5168\u91cfSchema','00002ca2':'\u884c\u519b\u72b6\u6001','00001fe6':'\u8054\u76df\u6392\u884c(\u53d8\u4f53)','00001fef':'\u8054\u76df\u6392\u884c(\u53d8\u4f532)','000146a4':'\u73a9\u5bb6\u6392\u884c','000018aa':'\u7cfb\u7edf\u6570\u636e','00000067':'\u7cfb\u7edf\u5c0f\u5305','00000bfa':'\u7cfb\u7edf\u5c0f\u5305(\u914d\u5957)'}"

lines = [
    '<script>',
    'const MSG_NAMES=' + msg_names + ';',
    '',
    'const HERO_NAMES=' + hero_names_json + ';',
    '',
    'const SKILL_DATA=' + skill_data_json + ';',
    '',
    'const HERO_DETAIL=' + hero_detail_json + ';',
    '',
    "function heroName(id){return HERO_NAMES[id]||(HERO_NAMES[String(id)])||('\u6b66\u5c06'+id);}",
    "function skillName(id){const s=SKILL_DATA[id]||(SKILL_DATA[String(id)]);return s?s.name:('\u6280\u80fd'+id);}",
    "function skillType(id){const s=SKILL_DATA[id]||(SKILL_DATA[String(id)]);return s?s.type:'';}",
    "function heroAvatar(id){const h=HERO_DETAIL[id]||(HERO_DETAIL[String(id)]);return h?h.avatar_small:'';}",
    "function fmt(n){if(!n)return '0';return n>=10000?(n/10000).toFixed(1)+'\u4e07':String(n);}",
    "function esc(s){return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}",
    '',
    'let unionData=[],battleData=[];',
    '',
    'function updateClock(){',
    "  const n=new Date(),p=x=>String(x).padStart(2,'0');",
    "  document.getElementById('clock').textContent=n.getFullYear()+'-'+p(n.getMonth()+1)+'-'+p(n.getDate())+' '+p(n.getHours())+':'+p(n.getMinutes())+':'+p(n.getSeconds());",
    '}',
    'setInterval(updateClock,1000);updateClock();',
    '',
    'function switchTab(id,el){',
    "  document.querySelectorAll('.tab').forEach(t=>t.classList.remove('active'));",
    "  document.querySelectorAll('.page').forEach(p=>p.classList.remove('active'));",
    "  el.classList.add('active');",
    "  document.getElementById('page-'+id).classList.add('active');",
    '}',
    '',
    'async function fj(url){try{const r=await fetch(url);if(!r.ok)return null;return await r.json();}catch{return null;}}',
    '',
    'async function scanFiles(dir){',
    "  try{",
    "    const r=await fetch(dir+'/');",
    "    if(!r.ok)return[];",
    "    const html=await r.text();",
    "    return[...html.matchAll(/href=\"(decompressed_[^\"]+\\.json)\"/g)].map(m=>dir+'/'+m[1]);",
    "  }catch{return[];}",
    '}',
    '',
    'async function loadAll(){',
    "  const st=document.getElementById('load-status');",
    "  st.textContent='\u52a0\u8f7d\u4e2d...';",
    "  st.style.color='var(--gold)';",
    '  await Promise.all([loadUnionData(),loadBattleData(),loadTypeStats()]);',
    "  st.textContent='\u5df2\u66f4\u65b0 '+new Date().toLocaleTimeString();",
    "  st.style.color='var(--win)';",
    '}',
    '',
    'async function loadUnionData(){',
    "  const dirs=['../decompressed_data_report/000002bc','../capture_new/000002bc','../decompressed_data_report/00001fe6','../capture_new/00001fe6','../decompressed_data_report/00001fef','../capture_new/00001fef'];",
    '  let best=null,bestN=0;',
    '  for(const d of dirs){',
    '    const files=await scanFiles(d);',
    '    for(const f of files){',
    '      const data=await fj(f);',
    '      if(!data||!Array.isArray(data))continue;',
    '      const arr=data[4];',
    '      if(!Array.isArray(arr))continue;',
    '      const unions=arr.map(item=>Array.isArray(item)?item[1]:null).filter(u=>u&&u.name);',
    '      if(unions.length>bestN){bestN=unions.length;best=unions;}',
    '    }',
    '  }',
    '  if(best){unionData=best.sort((a,b)=>b.power-a.power);renderUnion();}',
    '}',
    '',
    'async function loadBattleData(){',
    "  const dirs=['../decompressed_data_report/0000000a','../capture_new/0000000a'];",
    '  const battles=[];',
    '  for(const d of dirs){',
    '    const files=await scanFiles(d);',
    '    for(const f of files){',
    '      const data=await fj(f);',
    '      if(!data)continue;',
    '      let objs=[];',
    '      if(Array.isArray(data)&&data.length>=2&&Array.isArray(data[1]))objs=data[1];',
    '      else if(Array.isArray(data))objs=data;',
    '      for(const o of objs){if(o&&o.battle_id)battles.push(o);}',
    '    }',
    '  }',
    '  battleData=battles;',
    '  renderBattle();',
    '}',
    '',
    'async function loadTypeStats(){',
    "  const roots=['../decompressed_data_report','../capture_new'];",
    '  const stats={};let total=0;',
    '  for(const root of roots){',
    '    try{',
    "      const r=await fetch(root+'/');",
    '      if(!r.ok)continue;',
    '      const html=await r.text();',
    "      const subdirs=[...html.matchAll(/href=\"([0-9a-f]{6,8})\\//g)].map(m=>m[1]);",
    '      for(const d of subdirs){',
    '        const files=await scanFiles(root+\'\'/\'+d);',
    '        stats[d]=(stats[d]||0)+files.length;',
    '        total+=files.length;',
    '      }',
    '    }catch{}',
    '  }',
    '  renderOverview(stats,total);',
    '}',
    '',
    'function parseHeroes(s){',
    '  if(!s)return[];',
    "  return s.split(';').filter(Boolean).map(p=>parseInt(p.split(',')[0])).filter(id=>id>0);",
    '}',
    '</script>',
]

out_path = 'd:/nettest/dashboard/p3a.js'
with open(out_path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')

size = os.path.getsize(out_path)
print(f'已写入 {out_path} ({size} bytes, {size//1024} KB)')
print(f'  HERO_NAMES: {len(hero_map)} 个武将')
print(f'  SKILL_DATA: {len(skill_map)} 个技能')
print(f'  HERO_DETAIL: {len(hero_detail)} 个武将详情')

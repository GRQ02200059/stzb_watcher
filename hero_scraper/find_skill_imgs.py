# -*- coding: utf-8 -*-
import re, json, requests, warnings, os
warnings.filterwarnings('ignore')

s = requests.Session()
s.verify = False
s.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
    'Referer': 'https://stzb.163.com/card_list.html',
})

# 1. 从JS里找技能图URL格式
with open('card_list.js', 'r', encoding='utf-8') as f:
    js = f.read()

print('=== 技能图相关URL ===')
matches = re.findall(r'["\']([^"\']*tactics[^"\']*)["\']', js)
for m in matches:
    print(m)

print()
matches2 = re.findall(r'["\']([^"\']*jineng[^"\']*)["\']', js)
for m in matches2:
    print(m)

# 2. 看武将数据里的技能字段
with open('output/heroes_full.json', 'r', encoding='utf-8') as f:
    heroes = json.load(f)

print()
print('=== 武将技能字段示例 (前5) ===')
for h in heroes[:5]:
    print(f'id={h.get("id")} name={h.get("name")} methodId={h.get("methodId")} methodId1={h.get("methodId1")} methodId2={h.get("methodId2")} methodName={h.get("methodName")} methodName1={h.get("methodName1")} methodName2={h.get("methodName2")}')

# 3. 技能数据字段
with open('output/skills_full.json', 'r', encoding='utf-8') as f:
    skills = json.load(f)

print()
print('=== 技能数据示例 (前10) ===')
for sk in skills[:10]:
    print(json.dumps(sk, ensure_ascii=False))

# 4. 测试不同格式的技能图URL
print()
print('=== 测试技能图URL格式 ===')
bases = [
    'https://stzb.res.netease.com/pc/qt/20170323200251/data/jineng/tactics_0',
    'https://stzb.res.netease.com/pc/qt/20170323200251/data/jineng/tactics_',
    'https://stzb.res.netease.com/pc/qt/20170323200251/data/small/card_',
    'https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/jineng/',
    'https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/skill/',
]
# 技能ID从200001开始，但也有可能图片ID格式不同
test_ids = ['200001', '200002', '1', '2', '100', '101']
for base in bases[:2]:
    for tid in test_ids[:3]:
        for ext in ['.jpg', '.png']:
            url = base + tid + ext
            try:
                r = s.head(url, timeout=5)
                if r.status_code == 200:
                    print(f'FOUND! [{r.status_code}] {url} size={r.headers.get("Content-Length","?")}')
                else:
                    print(f'[{r.status_code}] {url}')
            except Exception as e:
                print(f'ERR {url}: {str(e)[:40]}')


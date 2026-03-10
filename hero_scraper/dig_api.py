# -*- coding: utf-8 -*-
import requests, warnings, re, json
warnings.filterwarnings('ignore')

s = requests.Session()
s.verify = False
s.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
    'Referer': 'https://stzb.163.com/card_list.html',
})

# 读取已下载的JS
with open('card_list.js', 'r', encoding='utf-8') as f:
    js = f.read()

# 找getJson调用
print('=== getJson calls ===')
getjson_calls = re.findall(r'getJson\s*\([^)]{0,200}\)', js)
for c in getjson_calls:
    print(c)
    print()

# 找Common.getJson调用
print('=== Common.getJson calls ===')
calls = re.findall(r'Common\.getJson\s*\([^)]{0,300}\)', js)
for c in calls:
    print(c)
    print()

# 找所有字符串路径
print('=== path strings ===')
paths = re.findall(r'["\'](\/[a-zA-Z0-9_/.-]{3,60})["\']', js)
for p in sorted(set(paths)):
    print(p)

# 找getAllRoleData函数体
print()
print('=== getAllRoleData function ===')
m = re.search(r'getAllRoleData[^{]*\{([^}]{0,2000})', js, re.DOTALL)
if m:
    print(m.group(0)[:2000])

# 找 herolist 相关
print()
print('=== herolist context ===')
idx = js.find('herolist')
if idx >= 0:
    print(js[max(0,idx-200):idx+400])

# 尝试直接访问cn/cards
print()
print('=== probing cn/cards ===')
base = 'https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards'
tests = [
    '/',
    '/list',
    '/list.json',
    '/herolist.json',
    '/all.json',
    '/data.json',
    '/cut/',
    '/cut/list.json',
]
for t in tests:
    try:
        r = s.get(base+t, timeout=8)
        print(f'[{r.status_code}] {base+t}  Size:{len(r.content)}  Type:{r.headers.get("Content-Type","")[:50]}')
        if r.status_code==200 and len(r.content)>10:
            print(f'  Raw: {r.content[:300]}')
    except Exception as e:
        print(f'[ERR] {base+t}: {str(e)[:60]}')


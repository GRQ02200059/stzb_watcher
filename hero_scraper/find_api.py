# -*- coding: utf-8 -*-
import requests, warnings, re, json, os
warnings.filterwarnings('ignore')

s = requests.Session()
s.verify = False
s.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
    'Accept-Language': 'zh-CN,zh;q=0.9',
    'Referer': 'https://stzb.163.com/card_list.html',
})

HOST = 'https://g0.gph.netease.com/ngsocial/community/stzb/cfg'

# 从JS里找到的路径
candidates = [
    '/herolist/',
    '/herolist',
    '/card_list/',
    '/card_list',
    '/cardlist/',
    '/cardlist',
    '/hero/',
    '/hero',
    '/roles/',
    '/roles',
    '/general/',
    '/general',
    '/data/herolist.json',
    '/data/cardlist.json',
    '/data/hero.json',
    '/data/card.json',
    '/data/roles.json',
    '/data/general.json',
    '/pc/data/herolist.json',
    '/cn/herolist/',
    '/cn/herolist',
    '/cn/hero/',
    '/cn/roles/',
]

print('=== Testing API endpoints ===')
for path in candidates:
    url = HOST + path
    try:
        r = s.get(url, timeout=8)
        ct = r.headers.get('Content-Type', '')
        print(f'[{r.status_code}] {url}')
        if r.status_code == 200:
            print(f'  Size: {len(r.content)} bytes  Type: {ct[:50]}')
            if 'json' in ct or r.content[:1] in [b'[', b'{']:
                try:
                    d = r.json()
                    if isinstance(d, list):
                        print(f'  LIST len={len(d)}, first item keys: {list(d[0].keys()) if d else "empty"}')
                    elif isinstance(d, dict):
                        print(f'  DICT keys: {list(d.keys())[:8]}')
                except Exception as e:
                    print(f'  JSON parse error: {e}')
                    print(f'  Raw: {r.content[:200]}')
    except Exception as e:
        print(f'[ERR] {url}: {str(e)[:60]}')

print()
print('=== Also try direct card data URLs ===')
more = [
    'https://g0.gph.netease.com/ngsocial/community/stzb/cn/herolist/',
    'https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/',
    'https://g0.gph.netease.com/ngsocial/community/stzb/cfg/herolist/',
    'https://g0.gph.netease.com/ngsocial/community/stzb/cfg/cardlist/',
    'https://g0.gph.netease.com/ngsocial/community/stzb/cfg/data/herolist.json',
    'https://g0.gph.netease.com/ngsocial/community/stzb/cfg/data/cardlist.json',
]
for url in more:
    try:
        r = s.get(url, timeout=8)
        print(f'[{r.status_code}] {url}  Size:{len(r.content)}  Type:{r.headers.get("Content-Type","")[:40]}')
        if r.status_code == 200 and len(r.content) > 100:
            print(f'  Raw: {r.content[:300]}')
    except Exception as e:
        print(f'[ERR] {url}: {str(e)[:60]}')


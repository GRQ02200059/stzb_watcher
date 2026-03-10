# -*- coding: utf-8 -*-
import requests, warnings, re, json, os
warnings.filterwarnings('ignore')

s = requests.Session()
s.verify = False
s.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
    'Accept': 'application/json, */*',
    'Referer': 'https://stzb.163.com/card_list.html',
    'Accept-Language': 'zh-CN,zh;q=0.9',
})

HOST = 'https://g0.gph.netease.com/ngsocial/community/stzb/cfg'

# 两个关键API
apis = [
    HOST + '/json/jineng_list.json',
    HOST + '/hero_extra.json?gameid=g10',
    # 变体尝试
    HOST + '/json/hero_extra.json',
    HOST + '/json/herolist.json',
    HOST + '/json/card_list.json',
    HOST + '/json/cardlist.json',
    HOST + '/json/role_list.json',
    HOST + '/json/general_list.json',
    # 不带前缀
    'https://g0.gph.netease.com/ngsocial/community/stzb/hero_extra.json?gameid=g10',
    'https://g0.gph.netease.com/ngsocial/community/stzb/json/jineng_list.json',
    # gnr-api
    'https://gnr-api.webapp.163.com/hero_extra.json?gameid=g10',
    'https://gnr-api.webapp.163.com/json/jineng_list.json',
    # stzb.163.com直接
    'https://stzb.163.com/hero_extra.json?gameid=g10',
    'https://stzb.163.com/json/jineng_list.json',
    'https://stzb.163.com/json/herolist.json',
    'https://stzb.163.com/json/cardlist.json',
    'https://stzb.res.netease.com/pc/qt/20170323200251/data/jineng/jineng_list.json',
    'https://stzb.res.netease.com/pc/qt/20170323200251/data/herolist.json',
    'https://stzb.res.netease.com/pc/qt/20170323200251/data/card_list.json',
]

os.makedirs('output', exist_ok=True)

for url in apis:
    try:
        r = s.get(url, timeout=10)
        ct = r.headers.get('Content-Type', '')
        size = len(r.content)
        print(f'[{r.status_code}] {url}')
        print(f'  Size:{size}  Type:{ct[:60]}')
        if r.status_code == 200 and size > 50:
            # 尝试解析JSON
            try:
                data = r.json()
                fname = url.split('/')[-1].split('?')[0] or 'data.json'
                with open(f'output/{fname}', 'w', encoding='utf-8') as f:
                    json.dump(data, f, ensure_ascii=False, indent=2)
                if isinstance(data, list):
                    print(f'  LIST len={len(data)}')
                    if data:
                        print(f'  First item: {json.dumps(data[0], ensure_ascii=False)[:300]}')
                elif isinstance(data, dict):
                    print(f'  DICT keys: {list(data.keys())[:10]}')
                    for k, v in list(data.items())[:3]:
                        print(f'    {k}: {str(v)[:100]}')
                print(f'  SAVED to output/{fname}')
            except Exception as je:
                print(f'  JSON error: {je}')
                print(f'  Raw: {r.content[:200]}')
        print()
    except Exception as e:
        print(f'[ERR] {url}: {str(e)[:80]}')
        print()


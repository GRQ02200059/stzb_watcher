# -*- coding: utf-8 -*-
import requests
import json
import time
import warnings
warnings.filterwarnings('ignore')

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
    'Accept-Language': 'zh-CN,zh;q=0.9',
}

session = requests.Session()
session.headers.update(headers)
session.verify = False

URLs = [
    ('率土官网主页',       'https://www.lieturz.com'),
    ('率土官网武将',       'https://www.lieturz.com/hero'),
    ('率土官网武将API',    'https://www.lieturz.com/api/hero/list'),
    ('rivergame主页',      'https://lietu.rivergame.net'),
    ('rivergame武将',      'https://lietu.rivergame.net/hero'),
    ('rivergame武将API',   'https://lietu.rivergame.net/api/hero'),
    ('3k.uua.cc',          'https://3k.uua.cc'),
    ('3k.uua.cc/hero',     'https://3k.uua.cc/hero'),
    ('wiki lietu',         'https://wiki.lieturz.com'),
    ('bwiki率土',          'https://bwiki.lietu.com'),
    ('bwiki率土2',         'https://wiki.biligame.com/lietu/'),
    ('游民星空率土',       'https://gl.gamersky.com/gl/lieturz/'),
    ('A9VG率土',           'https://www.a9vg.com/game/lieturz'),
    ('tap率土武将',        'https://www.taptap.cn/app/67628'),
    ('AppStore率土',       'https://apps.apple.com/cn/app/id1073048356'),
    ('json数据源1',        'https://cdn.lieturz.com/data/hero.json'),
    ('json数据源2',        'https://cdn.rivergame.net/lieturz/data/hero.json'),
    ('json数据源3',        'https://res.lieturz.com/data/heroes.json'),
]

for name, url in URLs:
    try:
        r = session.get(url, timeout=8)
        ct = r.headers.get('Content-Type', '')
        print(f'[{r.status_code}] {name}')
        print(f'  URL: {url}')
        print(f'  Type: {ct[:60]}  Size: {len(r.content)} bytes')
        if 'json' in ct:
            try:
                data = r.json()
                print(f'  JSON keys: {list(data.keys())[:5] if isinstance(data, dict) else type(data).__name__+" len="+str(len(data))}')
            except: pass
        elif 'html' in ct:
            t = r.text
            title_s = t.find('<title>')
            title_e = t.find('</title>')
            if title_s>-1: print(f'  Title: {t[title_s+7:title_e][:80]}')
        print()
    except Exception as e:
        print(f'[ERR] {name}: {str(e)[:80]}')
        print()
    time.sleep(0.3)


# -*- coding: utf-8 -*-
import requests
import re
import warnings
warnings.filterwarnings('ignore')

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
    'Accept-Language': 'zh-CN,zh;q=0.9',
    'Referer': 'https://wiki.biligame.com/lietu/',
}

s = requests.Session()
s.headers.update(headers)
s.verify = False

# 尝试常见武将图鉴页面路径
candidates = [
    'https://wiki.biligame.com/lietu/武将',
    'https://wiki.biligame.com/lietu/武将图鉴',
    'https://wiki.biligame.com/lietu/将领',
    'https://wiki.biligame.com/lietu/Hero',
    'https://wiki.biligame.com/lietu/hero',
    'https://wiki.biligame.com/lietu/武将列表',
    'https://wiki.biligame.com/lietu/全武将',
    'https://wiki.biligame.com/lietu/index.php?title=武将',
    'https://wiki.biligame.com/lietu/index.php/武将',
    # MediaWiki API
    'https://wiki.biligame.com/lietu/api.php?action=query&list=allpages&apnamespace=0&aplimit=50&format=json',
    'https://wiki.biligame.com/lietu/api.php?action=query&list=search&srsearch=武将&format=json',
    'https://wiki.biligame.com/lietu/api.php?action=query&list=categorymembers&cmtitle=Category:武将&cmlimit=100&format=json',
]

for url in candidates:
    try:
        r = s.get(url, timeout=10)
        ct = r.headers.get('Content-Type', '')
        print(f'[{r.status_code}] {url}')
        print(f'  Size: {len(r.content)} bytes  Type: {ct[:50]}')
        if r.status_code == 200:
            if 'json' in ct:
                try:
                    d = r.json()
                    print(f'  JSON: {str(d)[:200]}')
                except: pass
            else:
                t = r.text
                # 找title
                m = re.search(r'<title>([^<]+)</title>', t)
                if m: print(f'  Title: {m.group(1)[:80]}')
                # 找武将相关内容
                heroes = re.findall(r'[曹刘孙关张赵马黄][\u4e00-\u9fff]{1,2}', t)
                if heroes:
                    unique = list(dict.fromkeys(heroes))[:20]
                    print(f'  Heroes found: {unique}')
        print()
    except Exception as e:
        print(f'[ERR] {url}: {str(e)[:100]}')
        print()


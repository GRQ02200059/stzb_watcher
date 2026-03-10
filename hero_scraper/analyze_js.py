# -*- coding: utf-8 -*-
import requests, warnings, re, json
warnings.filterwarnings('ignore')

s = requests.Session()
s.verify = False
s.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
    'Referer': 'https://stzb.163.com/card_list.html',
})

js_url = 'https://stzb.res.netease.com/pc/gw/20230821163204/pkg/card_list_0b33ffe.js'
print('Fetching JS...')
r = s.get(js_url, timeout=20)
print(f'status: {r.status_code}, size: {len(r.content)} bytes')

jstext = r.content.decode('utf-8', errors='replace')

# 保存原始JS
with open('card_list.js', 'w', encoding='utf-8') as f:
    f.write(jstext)
print('saved card_list.js')

# 找API地址
print()
print('=== API URLs ===')
urls = re.findall(r'["\']([^"\']*(?:api|data|json|card|hero|general)[^"\']{0,80})["\']', jstext, re.I)
for u in sorted(set(urls)):
    if '/' in u or '.' in u:
        print(u)

print()
print('=== stzb / netease URLs ===')
nete_urls = re.findall(r'["\']([^"\']*(?:stzb|netease|163\.com)[^"\']*)["\']', jstext)
for u in sorted(set(nete_urls))[:40]:
    print(u)

print()
print('=== data structure patterns ===')
# 找武将数据相关变量
patterns = re.findall(r'(?:cardList|heroList|generalList|cardData|heroData)[^;]{0,200}', jstext)
for p in patterns[:10]:
    print(p[:200])
    print()

print()
print('=== first 3000 chars of JS ===')
print(jstext[:3000])


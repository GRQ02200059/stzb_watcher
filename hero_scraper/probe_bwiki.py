# -*- coding: utf-8 -*-
import requests
import warnings
warnings.filterwarnings('ignore')

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
    'Accept-Language': 'zh-CN,zh;q=0.9',
}

s = requests.Session()
s.headers.update(headers)
s.verify = False

# 先看主页结构，找武将相关链接
r = s.get('https://wiki.biligame.com/lietu/', timeout=15)
text = r.text

# 找所有包含 hero / 武将 的链接
import re
links = re.findall(r'href="([^"]*(?:hero|武将|将领|wujiang)[^"]*)"', text, re.I)
print('=== 武将相关链接 ===')
for l in sorted(set(links))[:40]:
    print(l)

print()
# 找武将列表页面候选
links2 = re.findall(r'href="(/lietu/[^"]+)"', text)
print('=== /lietu/ 内部链接 (前60) ===')
uniq = sorted(set(links2))
for l in uniq[:60]:
    print(l)


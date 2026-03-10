# -*- coding: utf-8 -*-
import requests, warnings, json, os, time
from concurrent.futures import ThreadPoolExecutor, as_completed
warnings.filterwarnings('ignore')

s = requests.Session()
s.verify = False
s.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
    'Referer': 'https://stzb.163.com/card_list.html',
})

# 先探索技能图编号规律
# tactics_01.png 成功，说明格式是 tactics_0{N}.png，N从1开始
base = 'https://stzb.res.netease.com/pc/qt/20170323200251/data/jineng/tactics_0'

print('=== 探索技能图编号规律 ===')
for i in list(range(1, 20)) + [50, 100, 150, 200, 300, 400, 516]:
    url = f'{base}{i}.png'
    try:
        r = s.head(url, timeout=5)
        status = r.status_code
        size = r.headers.get('Content-Length', '?')
        print(f'[{status}] idx={i} {url}  size={size}')
    except Exception as e:
        print(f'[ERR] idx={i}: {str(e)[:50]}')
    time.sleep(0.05)


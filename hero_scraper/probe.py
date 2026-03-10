# -*- coding: utf-8 -*-
"""
率土之滨武将数据爬取脚本
目标：获取武将ID、名称、技能、头像
"""
import requests
import json
import time
import os
from urllib.parse import urljoin

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    'Accept': 'application/json, text/html, */*',
    'Accept-Language': 'zh-CN,zh;q=0.9',
    'Referer': 'https://www.lieturz.com/',
}

session = requests.Session()
session.headers.update(headers)

# 候选数据源
URLs = [
    'https://www.lieturz.com',
    'https://lietu.rivergame.net',
    'https://www.lieturz.com/hero',
    'https://www.sanguosha.com',
    'https://3k.uua.cc',
]

for url in URLs:
    try:
        r = session.get(url, timeout=8)
        print(f'{url} -> {r.status_code} | {len(r.text)} bytes')
        if r.status_code == 200:
            print('  title:', r.text[r.text.find('<title>')+7:r.text.find('</title>')][:60])
    except Exception as e:
        print(f'{url} -> ERROR: {e}')
    time.sleep(0.5)


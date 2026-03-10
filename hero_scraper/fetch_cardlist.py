# -*- coding: utf-8 -*-
import requests, warnings, re, json, os, time
warnings.filterwarnings('ignore')

s = requests.Session()
s.verify = False
s.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
    'Accept-Language': 'zh-CN,zh;q=0.9',
    'Referer': 'https://stzb.163.com/',
})

r = s.get('https://stzb.163.com/card_list.html', timeout=15)
print('status:', r.status_code)
print('size:', len(r.content), 'bytes')
print('encoding:', r.encoding)

# 保存原始HTML
with open('card_list.html', 'wb') as f:
    f.write(r.content)
print('saved card_list.html')

# 尝试不同编码读取
for enc in ['utf-8', 'gbk', 'gb2312', 'utf-8-sig']:
    try:
        text = r.content.decode(enc)
        print(f'decoded with {enc}, length={len(text)}')
        # 找title
        m = re.search(r'<title>([^<]+)</title>', text)
        if m: print('title:', m.group(1))
        # 前2000字符
        print('--- first 2000 ---')
        print(text[:2000])
        break
    except Exception as e:
        print(f'{enc} failed: {e}')


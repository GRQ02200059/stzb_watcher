# -*- coding: utf-8 -*-
import requests, warnings, json, os, time
from concurrent.futures import ThreadPoolExecutor, as_completed
warnings.filterwarnings('ignore')

os.makedirs('output/avatars', exist_ok=True)
os.makedirs('output/skills', exist_ok=True)

s = requests.Session()
s.verify = False
s.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
    'Referer': 'https://stzb.163.com/card_list.html',
})

# 1. 探测技能图总数量（顺序扫描）
print('=== 扫描技能图范围 ===')
skill_img_base = 'https://stzb.res.netease.com/pc/qt/20170323200251/data/jineng/tactics_0'
valid_skill_imgs = []
for i in range(1, 600):
    url = f'{skill_img_base}{i}.png'
    try:
        r = s.head(url, timeout=4)
        if r.status_code == 200:
            size = r.headers.get('Content-Length', '0')
            valid_skill_imgs.append({'idx': i, 'url': url, 'size': int(size)})
            print(f'  [OK] idx={i} size={size}')
        # 连续5个404就停止
        elif i > 10 and all(j not in [x['idx'] for x in valid_skill_imgs] for j in range(max(1,i-4), i+1)):
            if i > max([x['idx'] for x in valid_skill_imgs], default=0) + 10:
                print(f'  停止于 idx={i}（连续10个404）')
                break
    except:
        pass
    time.sleep(0.03)

print(f'共找到 {len(valid_skill_imgs)} 张技能图')

# 2. 探测头像大图URL格式
print()
print('=== 探测头像大图URL ===')
avatar_bases = [
    'https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_small_',
    'https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_',
    'https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_big_',
    'https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_large_',
    'https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/card_',
    'https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/card_small_',
    'https://stzb.res.netease.com/pc/qt/20170323200251/data/small/card_',
]
test_id = 100001
for base in avatar_bases:
    for ext in ['.jpg', '.png']:
        url = f'{base}{test_id}{ext}'
        try:
            r = s.head(url, timeout=5)
            print(f'  [{r.status_code}] {url}  size={r.headers.get("Content-Length","?")}')
        except Exception as e:
            print(f'  [ERR] {url}: {str(e)[:40]}')


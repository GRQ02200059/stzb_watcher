# -*- coding: utf-8 -*-
import requests, warnings, re, json
warnings.filterwarnings('ignore')

s = requests.Session()
s.verify = False
s.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36',
    'Accept-Language': 'zh-CN,zh;q=0.9',
    'Referer': 'https://stzb.163.com/',
})

with open('card_list.html', 'rb') as f:
    content = f.read()
text = content.decode('utf-8', errors='replace')

print('=== ALL SCRIPT TAGS ===')
scripts = re.findall(r'<script[^>]*src=["\']([^"\']+)["\'][^>]*>', text)
for sc in scripts:
    print(sc)

print()
print('=== ALL LINK TAGS ===')
links = re.findall(r'<link[^>]*href=["\']([^"\']+)["\'][^>]*>', text)
for l in links:
    print(l)

print()
print('=== API/JSON patterns ===')
apis = re.findall(r'(?:url|src|href|api|path)["\s]*[:=]["\s]*["\']([^"\']*(?:api|json|data|card|hero)[^"\']*)["\']', text, re.I)
for a in sorted(set(apis)):
    print(a)

print()
print('=== netease resource URLs ===')
nete = re.findall(r'https?://[\w./-]+(?:netease|163|nie)[\w./-]*', text)
for n in sorted(set(nete))[:30]:
    print(n)

print()
print('=== inline script content ===')
inline = re.findall(r'<script(?! src)[^>]*>(.*?)</script>', text, re.DOTALL)
for i, sc in enumerate(inline):
    if len(sc.strip()) > 20:
        print(f'--- inline script {i} ---')
        print(sc[:500])
        print()


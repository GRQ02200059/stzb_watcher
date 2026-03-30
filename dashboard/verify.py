with open('index.html', encoding='utf-8') as f:
    c = f.read()
print('HERO_NAMES:', 'HERO_NAMES' in c)
print('SKILL_DATA:', 'SKILL_DATA' in c)
print('HERO_DETAIL:', 'HERO_DETAIL' in c)
print('heroAvatar:', 'heroAvatar' in c)
print('skillName:', 'skillName' in c)
# 找几个知名武将验证
for name in ['\u66f9\u64cd', '\u8bf8\u845b\u4eae', '\u5173\u7fbd', '\u5218\u5907', '\u5b59\u6743', '\u5f20\u98de']:
    print(f'  {name}: {name in c}')
# 技能示例
for name in ['\u8870\u5175\u658b\u5c06', '\u65ad\u7ca7', '\u5c45\u5c3c\u6b66\u800c']:
    print(f'  skill {name}: {name in c}')
print('total size:', len(c), 'chars')


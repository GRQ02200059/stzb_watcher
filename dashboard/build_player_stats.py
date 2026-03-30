# -*- coding: utf-8 -*-
import csv, json, re

players = []

with open('d:/nettest/player_statistics.csv', 'r', encoding='utf-8-sig') as f:
    reader = csv.DictReader(f)
    for row in reader:
        # 解析常用英雄列表
        heroes_raw = row.get('常用英雄', '').strip()
        heroes = []
        if heroes_raw:
            for m in re.finditer(r'([^,(]+?)\((\d+)\)', heroes_raw):
                name = m.group(1).strip()
                cnt = int(m.group(2))
                if name and name != '未知英雄':
                    heroes.append({'name': name, 'count': cnt})

        # 解析战斗类型
        battle_types_raw = row.get('常用战斗类型', '').strip()
        battle_types = []
        for m in re.finditer(r'([^,(]+?)\((\d+)\)', battle_types_raw):
            battle_types.append({'type': m.group(1).strip(), 'count': int(m.group(2))})

        # 联盟列表
        union_list = [u.strip() for u in row.get('联盟列表', '').split(',') if u.strip()]

        player = {
            'name': row['玩家名称'].strip(),
            'total': int(row['总战斗数'] or 0),
            'wins': int(row['胜利数'] or 0),
            'losses': int(row['失败数'] or 0),
            'win_rate': float(row['胜率(%)'] or 0),
            'atk': int(row['攻击次数'] or 0),
            'def': int(row['防守次数'] or 0),
            'atk_win_rate': float(row['攻击胜率(%)'] or 0),
            'def_win_rate': float(row['防守胜率(%)'] or 0),
            'union_count': int(row['联盟数'] or 0),
            'unions': union_list,
            'heroes': heroes,
            'battle_types': battle_types,
            'hero_count': int(row['使用英雄数'] or 0),
        }
        players.append(player)

# 按总战斗数排序
players.sort(key=lambda x: (-x['total'], -x['wins']))

print(f'共 {len(players)} 个玩家')
print(f'最多战斗: {players[0]["name"]} {players[0]["total"]}场')
print(f'胜率100%+战斗>=3: {sum(1 for p in players if p["win_rate"]==100 and p["total"]>=3)} 人')

# 统计联盟分布
union_stats = {}
for p in players:
    for u in p['unions']:
        union_stats[u] = union_stats.get(u, 0) + 1
top_unions = sorted(union_stats.items(), key=lambda x: -x[1])[:10]
print('\n联盟玩家数TOP10:')
for u, c in top_unions:
    print(f'  {u}: {c}人')

# 保存JSON
with open('d:/nettest/dashboard/player_stats.json', 'w', encoding='utf-8') as f:
    json.dump(players, f, ensure_ascii=False, indent=2)
print(f'\n已保存 player_stats.json ({len(players)} 条)')


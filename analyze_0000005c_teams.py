#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
统计0000005c（同盟战报）中每个玩家的队伍数量
"""

import json
import os
from collections import defaultdict, Counter

def parse_hero_info(hero_info_str):
    """解析武将信息字符串"""
    if not hero_info_str:
        return []
    
    heroes = []
    # 格式: 100016,50,11000,10786,89;100790,50,11000,10646,107;100769,50,10998,10842,0;
    hero_entries = hero_info_str.split(';')
    
    for entry in hero_entries:
        if entry.strip():
            parts = entry.split(',')
            if len(parts) >= 1:
                hero_id = parts[0]
                if hero_id and hero_id != '0':
                    heroes.append(hero_id)
    
    return heroes

def analyze_0000005c_teams():
    """分析0000005c中的队伍数据"""
    
    folder_path = "decompressed_data_report/0000005c"
    
    if not os.path.exists(folder_path):
        print(f"文件夹不存在: {folder_path}")
        return
    
    files = [f for f in os.listdir(folder_path) if f.endswith('.json')]
    print(f"找到 {len(files)} 个0000005c文件")
    
    # 统计每个玩家的队伍
    player_teams = defaultdict(lambda: {
        'total_teams': 0,
        'teams': [],
        'total_battles': 0
    })
    
    # 统计所有队伍配置
    all_team_configs = Counter()
    
    for file in files:
        file_path = os.path.join(folder_path, file)
        print(f"处理文件: {file}")
        
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            if isinstance(data, list) and len(data) > 0:
                # 0000005c的结构：[[战斗详情1, 战斗详情2, ...], 其他数据...]
                battles = data[0] if isinstance(data[0], list) else []
                
                print(f"  文件包含 {len(battles)} 个战斗记录")
                
                for battle in battles:
                    if isinstance(battle, dict):
                        # 获取攻击方信息
                        attack_name = battle.get('attack_name', '')
                        defend_name = battle.get('defend_name', '')
                        
                        # 分析攻击方队伍
                        if attack_name:
                            attack_hero_info = battle.get('attack_all_hero_info', '')
                            attack_heroes = parse_hero_info(attack_hero_info)
                            
                            if attack_heroes:  # 有武将的队伍
                                # 按武将ID排序，确保队伍配置的一致性
                                attack_heroes_sorted = sorted(attack_heroes)
                                attack_team_str = ' + '.join(attack_heroes_sorted)
                                
                                # 记录玩家队伍
                                if attack_team_str not in [team['config'] for team in player_teams[attack_name]['teams']]:
                                    player_teams[attack_name]['teams'].append({
                                        'config': attack_team_str,
                                        'type': '攻击方'
                                    })
                                    player_teams[attack_name]['total_teams'] += 1
                                
                                # 统计队伍配置
                                all_team_configs[attack_team_str] += 1
                                player_teams[attack_name]['total_battles'] += 1
                        
                        # 分析防守方队伍
                        if defend_name:
                            defend_hero_info = battle.get('defend_all_hero_info', '')
                            defend_heroes = parse_hero_info(defend_hero_info)
                            
                            if defend_heroes:  # 有武将的队伍
                                # 按武将ID排序，确保队伍配置的一致性
                                defend_heroes_sorted = sorted(defend_heroes)
                                defend_team_str = ' + '.join(defend_heroes_sorted)
                                
                                # 记录玩家队伍
                                if defend_team_str not in [team['config'] for team in player_teams[defend_name]['teams']]:
                                    player_teams[defend_name]['teams'].append({
                                        'config': defend_team_str,
                                        'type': '防守方'
                                    })
                                    player_teams[defend_name]['total_teams'] += 1
                                
                                # 统计队伍配置
                                all_team_configs[defend_team_str] += 1
                                player_teams[defend_name]['total_battles'] += 1
        
        except Exception as e:
            print(f"  处理文件 {file} 时出错: {e}")
    
    return player_teams, all_team_configs

def generate_team_report(player_teams, all_team_configs):
    """生成队伍报告"""
    
    print(f"\n{'='*80}")
    print("0000005c 同盟战报 - 玩家队伍统计")
    print(f"{'='*80}")
    
    print(f"\n总体统计:")
    print(f"- 参与玩家数: {len(player_teams)}")
    print(f"- 总队伍配置数: {len(all_team_configs)}")
    
    # 按队伍数量排序
    sorted_players = sorted(player_teams.items(), key=lambda x: x[1]['total_teams'], reverse=True)
    
    print(f"\n玩家队伍数量排行榜 (前20名):")
    print(f"{'排名':<4} {'玩家名称':<20} {'队伍数':<6} {'战斗数':<8}")
    print("-" * 50)
    
    for i, (player, data) in enumerate(sorted_players[:20], 1):
        print(f"{i:<4} {player:<20} {data['total_teams']:<6} {data['total_battles']:<8}")
    
    print(f"\n最常用队伍配置 (前15名):")
    print(f"{'排名':<4} {'队伍配置':<50} {'使用次数':<8}")
    print("-" * 70)
    
    for i, (team_config, count) in enumerate(all_team_configs.most_common(15), 1):
        print(f"{i:<4} {team_config:<50} {count:<8}")
    
    # 详细分析前几名玩家
    print(f"\n详细分析 - 队伍数量最多的前5名玩家:")
    print("=" * 80)
    
    for i, (player, data) in enumerate(sorted_players[:5], 1):
        print(f"\n{i}. {player}")
        print(f"   总队伍数: {data['total_teams']}")
        print(f"   总战斗数: {data['total_battles']}")
        print(f"   队伍配置:")
        
        for j, team in enumerate(data['teams'], 1):
            print(f"     {j}. {team['config']}")
    
    # 保存详细数据
    save_detailed_data(player_teams, all_team_configs)

def save_detailed_data(player_teams, all_team_configs):
    """保存详细数据到文件"""
    
    # 保存玩家队伍数据
    player_data = []
    for player, data in player_teams.items():
        player_data.append({
            '玩家名称': player,
            '队伍数量': data['total_teams'],
            '战斗数量': data['total_battles'],
            '队伍配置': [team['config'] for team in data['teams']]
        })
    
    # 按队伍数量排序
    player_data.sort(key=lambda x: x['队伍数量'], reverse=True)
    
    # 保存为JSON
    with open('0000005c_player_teams.json', 'w', encoding='utf-8') as f:
        json.dump(player_data, f, ensure_ascii=False, indent=2)
    
    # 保存队伍配置统计
    team_config_data = []
    for team_config, count in all_team_configs.most_common():
        team_config_data.append({
            '队伍配置': team_config,
            '使用次数': count
        })
    
    with open('0000005c_team_configs.json', 'w', encoding='utf-8') as f:
        json.dump(team_config_data, f, ensure_ascii=False, indent=2)
    
    print(f"\n详细数据已保存:")
    print(f"- 玩家队伍数据: 0000005c_player_teams.json")
    print(f"- 队伍配置统计: 0000005c_team_configs.json")

def main():
    """主函数"""
    print("开始分析0000005c（同盟战报）中的玩家队伍数据...")
    
    player_teams, all_team_configs = analyze_0000005c_teams()
    
    if player_teams:
        generate_team_report(player_teams, all_team_configs)
    else:
        print("未找到有效的队伍数据")

if __name__ == "__main__":
    main()
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
基于战斗数据分析玩家队伍统计和胜率
"""

import pandas as pd
import json
from collections import defaultdict, Counter
import os

def load_battle_data():
    """加载战斗数据"""
    print("加载战斗数据...")
    
    # 读取CSV文件
    df = pd.read_csv('battles_report_updated.csv', encoding='utf-8')
    print(f"加载了 {len(df)} 条战斗记录")
    
    return df

def analyze_player_stats(df):
    """分析玩家统计"""
    print("\n分析玩家统计...")
    
    player_stats = defaultdict(lambda: {
        'total_battles': 0,
        'wins': 0,
        'losses': 0,
        'win_rate': 0.0,
        'attack_battles': 0,
        'defend_battles': 0,
        'attack_wins': 0,
        'defend_wins': 0,
        'heroes_used': Counter(),
        'unions': set(),
        'battle_types': Counter(),
        'fight_types': Counter(),
        'city_types': Counter()
    })
    
    # 分析每条战斗记录
    for _, battle in df.iterrows():
        attack_name = battle['attack_name']
        defend_name = battle['defend_name']
        result = battle['result']
        battle_type = battle['battle_type']
        fight_type = battle['fight_type']
        city_type = battle['city_type']
        attack_union = battle['attack_union']
        defend_union = battle['defend_union']
        
        # 攻击方统计
        if attack_name and attack_name != '':
            stats = player_stats[attack_name]
            stats['total_battles'] += 1
            stats['attack_battles'] += 1
            if attack_union and str(attack_union) != 'nan':
                stats['unions'].add(str(attack_union))
            stats['battle_types'][battle_type] += 1
            stats['fight_types'][fight_type] += 1
            stats['city_types'][city_type] += 1
            
            # 统计使用的英雄
            for i in range(1, 4):
                hero_name = battle[f'attack_hero_{i}_name']
                if hero_name and hero_name != '空位' and hero_name != '':
                    stats['heroes_used'][hero_name] += 1
            
            # 胜负统计
            if '攻击方胜利' in str(result):
                stats['wins'] += 1
                stats['attack_wins'] += 1
            elif '防守方胜利' in str(result):
                stats['losses'] += 1
        
        # 防守方统计
        if defend_name and defend_name != '':
            stats = player_stats[defend_name]
            stats['total_battles'] += 1
            stats['defend_battles'] += 1
            if defend_union and str(defend_union) != 'nan':
                stats['unions'].add(str(defend_union))
            stats['battle_types'][battle_type] += 1
            stats['fight_types'][fight_type] += 1
            stats['city_types'][city_type] += 1
            
            # 统计使用的英雄
            for i in range(1, 4):
                hero_name = battle[f'defend_hero_{i}_name']
                if hero_name and hero_name != '空位' and hero_name != '':
                    stats['heroes_used'][hero_name] += 1
            
            # 胜负统计
            if '防守方胜利' in str(result):
                stats['wins'] += 1
                stats['defend_wins'] += 1
            elif '攻击方胜利' in str(result):
                stats['losses'] += 1
    
    # 计算胜率
    for player, stats in player_stats.items():
        if stats['total_battles'] > 0:
            stats['win_rate'] = stats['wins'] / stats['total_battles'] * 100
        stats['unions'] = list(stats['unions'])
    
    return player_stats

def generate_player_report(player_stats):
    """生成玩家报告"""
    print("\n生成玩家报告...")
    
    # 转换为DataFrame格式
    report_data = []
    for player, stats in player_stats.items():
        if stats['total_battles'] > 0:  # 只包含有战斗记录的玩家
            # 获取最常用的英雄
            top_heroes = stats['heroes_used'].most_common(3)
            top_heroes_str = ', '.join([f"{hero}({count})" for hero, count in top_heroes])
            
            # 获取最常用的战斗类型
            top_battle_types = stats['battle_types'].most_common(2)
            top_battle_types_str = ', '.join([f"{bt}({count})" for bt, count in top_battle_types])
            
            report_data.append({
                '玩家名称': player,
                '总战斗数': stats['total_battles'],
                '胜利数': stats['wins'],
                '失败数': stats['losses'],
                '胜率(%)': round(stats['win_rate'], 2),
                '攻击次数': stats['attack_battles'],
                '防守次数': stats['defend_battles'],
                '攻击胜率(%)': round(stats['attack_wins'] / stats['attack_battles'] * 100, 2) if stats['attack_battles'] > 0 else 0,
                '防守胜率(%)': round(stats['defend_wins'] / stats['defend_battles'] * 100, 2) if stats['defend_battles'] > 0 else 0,
                '联盟数': len(stats['unions']),
                '联盟列表': ', '.join([str(u) for u in stats['unions'] if u and str(u) != 'nan']),
                '常用英雄': top_heroes_str,
                '常用战斗类型': top_battle_types_str,
                '使用英雄数': len(stats['heroes_used'])
            })
    
    # 按胜率排序
    report_df = pd.DataFrame(report_data)
    report_df = report_df.sort_values('胜率(%)', ascending=False)
    
    return report_df

def generate_hero_stats(df):
    """生成英雄使用统计"""
    print("\n生成英雄使用统计...")
    
    hero_stats = defaultdict(lambda: {
        'total_uses': 0,
        'wins': 0,
        'losses': 0,
        'win_rate': 0.0,
        'players': set(),
        'positions': Counter()  # 位置统计
    })
    
    for _, battle in df.iterrows():
        result = battle['result']
        
        # 统计攻击方英雄
        for i in range(1, 4):
            hero_name = battle[f'attack_hero_{i}_name']
            if hero_name and hero_name != '空位' and hero_name != '':
                stats = hero_stats[hero_name]
                stats['total_uses'] += 1
                stats['players'].add(battle['attack_name'])
                stats['positions'][f'攻击位{i}'] += 1
                
                if '攻击方胜利' in str(result):
                    stats['wins'] += 1
                elif '防守方胜利' in str(result):
                    stats['losses'] += 1
        
        # 统计防守方英雄
        for i in range(1, 4):
            hero_name = battle[f'defend_hero_{i}_name']
            if hero_name and hero_name != '空位' and hero_name != '':
                stats = hero_stats[hero_name]
                stats['total_uses'] += 1
                stats['players'].add(battle['defend_name'])
                stats['positions'][f'防守位{i}'] += 1
                
                if '防守方胜利' in str(result):
                    stats['wins'] += 1
                elif '攻击方胜利' in str(result):
                    stats['losses'] += 1
    
    # 计算胜率
    for hero, stats in hero_stats.items():
        if stats['total_uses'] > 0:
            stats['win_rate'] = stats['wins'] / stats['total_uses'] * 100
        stats['players'] = len(stats['players'])
    
    # 转换为DataFrame
    hero_report_data = []
    for hero, stats in hero_stats.items():
        if stats['total_uses'] > 0:
            hero_report_data.append({
                '英雄名称': hero,
                '使用次数': stats['total_uses'],
                '胜利次数': stats['wins'],
                '失败次数': stats['losses'],
                '胜率(%)': round(stats['win_rate'], 2),
                '使用玩家数': stats['players'],
                '最常用位置': stats['positions'].most_common(1)[0][0] if stats['positions'] else '未知'
            })
    
    hero_report_df = pd.DataFrame(hero_report_data)
    hero_report_df = hero_report_df.sort_values('使用次数', ascending=False)
    
    return hero_report_df

def save_reports(player_df, hero_df):
    """保存报告"""
    print("\n保存报告...")
    
    # 保存玩家统计
    player_df.to_csv('player_statistics.csv', index=False, encoding='utf-8-sig')
    print(f"玩家统计已保存: player_statistics.csv ({len(player_df)} 个玩家)")
    
    # 保存英雄统计
    hero_df.to_csv('hero_statistics.csv', index=False, encoding='utf-8-sig')
    print(f"英雄统计已保存: hero_statistics.csv ({len(hero_df)} 个英雄)")
    
    # 生成简要报告
    with open('battle_analysis_report.md', 'w', encoding='utf-8') as f:
        f.write("# 战斗数据分析报告\n\n")
        
        f.write("## 📊 总体统计\n")
        f.write(f"- 总战斗记录: {len(player_df)} 个玩家\n")
        f.write(f"- 使用英雄数: {len(hero_df)} 个\n")
        f.write(f"- 平均胜率: {player_df['胜率(%)'].mean():.2f}%\n\n")
        
        f.write("## 🏆 胜率排行榜 (前10名)\n")
        top_players = player_df.head(10)
        for i, (_, player) in enumerate(top_players.iterrows(), 1):
            f.write(f"{i}. **{player['玩家名称']}** - 胜率: {player['胜率(%)']}% ({player['胜利数']}/{player['总战斗数']})\n")
        
        f.write("\n## 🎯 最受欢迎英雄 (前10名)\n")
        top_heroes = hero_df.head(10)
        for i, (_, hero) in enumerate(top_heroes.iterrows(), 1):
            f.write(f"{i}. **{hero['英雄名称']}** - 使用 {hero['使用次数']} 次, 胜率: {hero['胜率(%)']}%\n")
        
        f.write("\n## 📈 详细数据\n")
        f.write("- 玩家详细统计: `player_statistics.csv`\n")
        f.write("- 英雄详细统计: `hero_statistics.csv`\n")
    
    print("简要报告已保存: battle_analysis_report.md")

def main():
    """主函数"""
    print("开始分析战斗数据...")
    
    # 加载数据
    df = load_battle_data()
    
    # 分析玩家统计
    player_stats = analyze_player_stats(df)
    
    # 生成玩家报告
    player_df = generate_player_report(player_stats)
    
    # 生成英雄统计
    hero_df = generate_hero_stats(df)
    
    # 保存报告
    save_reports(player_df, hero_df)
    
    # 显示简要统计
    print(f"\n分析完成!")
    print(f"参与玩家: {len(player_df)} 人")
    print(f"使用英雄: {len(hero_df)} 个")
    print(f"最高胜率: {player_df['胜率(%)'].max():.2f}%")
    print(f"平均胜率: {player_df['胜率(%)'].mean():.2f}%")
    
    print(f"\n胜率前5名:")
    for i, (_, player) in enumerate(player_df.head(5).iterrows(), 1):
        print(f"{i}. {player['玩家名称']}: {player['胜率(%)']}% ({player['胜利数']}/{player['总战斗数']})")

if __name__ == "__main__":
    main()

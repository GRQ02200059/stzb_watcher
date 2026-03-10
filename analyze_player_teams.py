#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分析每个玩家的队伍配置统计
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

def analyze_player_teams(df):
    """分析每个玩家的队伍配置（去重版本）"""
    print("\n分析玩家队伍配置（去重版本）...")
    
    player_teams = defaultdict(lambda: {
        'attack_teams': set(),  # 攻击方队伍配置（去重）
        'defend_teams': set(), # 防守方队伍配置（去重）
        'total_attack_battles': 0,
        'total_defend_battles': 0,
        'attack_team_stats': defaultdict(lambda: {'wins': 0, 'total': 0}),  # 攻击队伍胜率
        'defend_team_stats': defaultdict(lambda: {'wins': 0, 'total': 0})  # 防守队伍胜率
    })
    
    # 分析每条战斗记录
    for _, battle in df.iterrows():
        attack_name = battle['attack_name']
        defend_name = battle['defend_name']
        result = battle['result']
        
        # 构建攻击方队伍
        if attack_name and attack_name != '':
            attack_team = []
            for i in range(1, 4):
                hero_name = battle[f'attack_hero_{i}_name']
                if hero_name and hero_name != '空位' and hero_name != '':
                    attack_team.append(hero_name)
            
            if attack_team:  # 只统计有英雄的队伍
                # 按英雄名称排序，确保队伍配置的一致性
                attack_team_sorted = sorted([str(hero) for hero in attack_team if hero and str(hero) != 'nan'])
                attack_team_str = ' + '.join(attack_team_sorted)
                
                # 添加到集合中（自动去重）
                player_teams[attack_name]['attack_teams'].add(attack_team_str)
                player_teams[attack_name]['total_attack_battles'] += 1
                player_teams[attack_name]['attack_team_stats'][attack_team_str]['total'] += 1
                
                # 统计胜负
                if '攻击方胜利' in str(result):
                    player_teams[attack_name]['attack_team_stats'][attack_team_str]['wins'] += 1
        
        # 构建防守方队伍
        if defend_name and defend_name != '':
            defend_team = []
            for i in range(1, 4):
                hero_name = battle[f'defend_hero_{i}_name']
                if hero_name and hero_name != '空位' and hero_name != '':
                    defend_team.append(hero_name)
            
            if defend_team:  # 只统计有英雄的队伍
                # 按英雄名称排序，确保队伍配置的一致性
                defend_team_sorted = sorted([str(hero) for hero in defend_team if hero and str(hero) != 'nan'])
                defend_team_str = ' + '.join(defend_team_sorted)
                
                # 添加到集合中（自动去重）
                player_teams[defend_name]['defend_teams'].add(defend_team_str)
                player_teams[defend_name]['total_defend_battles'] += 1
                player_teams[defend_name]['defend_team_stats'][defend_team_str]['total'] += 1
                
                # 统计胜负
                if '防守方胜利' in str(result):
                    player_teams[defend_name]['defend_team_stats'][defend_team_str]['wins'] += 1
    
    return player_teams

def generate_team_report(player_teams):
    """生成队伍报告"""
    print("\n生成队伍报告...")
    
    # 生成详细报告
    detailed_report = []
    
    for player, teams in player_teams.items():
        if teams['total_attack_battles'] > 0 or teams['total_defend_battles'] > 0:
            # 攻击方队伍统计
            attack_team_details = []
            for team in teams['attack_teams']:
                stats = teams['attack_team_stats'][team]
                win_rate = (stats['wins'] / stats['total'] * 100) if stats['total'] > 0 else 0
                attack_team_details.append({
                    '队伍配置': team,
                    '使用次数': stats['total'],
                    '胜利次数': stats['wins'],
                    '总次数': stats['total'],
                    '胜率(%)': round(win_rate, 2)
                })
            
            # 防守方队伍统计
            defend_team_details = []
            for team in teams['defend_teams']:
                stats = teams['defend_team_stats'][team]
                win_rate = (stats['wins'] / stats['total'] * 100) if stats['total'] > 0 else 0
                defend_team_details.append({
                    '队伍配置': team,
                    '使用次数': stats['total'],
                    '胜利次数': stats['wins'],
                    '总次数': stats['total'],
                    '胜率(%)': round(win_rate, 2)
                })
            
            detailed_report.append({
                '玩家名称': player,
                '攻击方队伍数': len(teams['attack_teams']),
                '防守方队伍数': len(teams['defend_teams']),
                '总攻击次数': teams['total_attack_battles'],
                '总防守次数': teams['total_defend_battles'],
                '攻击方队伍详情': attack_team_details,
                '防守方队伍详情': defend_team_details
            })
    
    return detailed_report

def save_team_reports(detailed_report):
    """保存队伍报告"""
    print("\n保存队伍报告...")
    
    # 保存总体统计
    summary_data = []
    for player_data in detailed_report:
        summary_data.append({
            '玩家名称': player_data['玩家名称'],
            '攻击方队伍数': player_data['攻击方队伍数'],
            '防守方队伍数': player_data['防守方队伍数'],
            '总攻击次数': player_data['总攻击次数'],
            '总防守次数': player_data['总防守次数'],
            '总队伍数': player_data['攻击方队伍数'] + player_data['防守方队伍数']
        })
    
    summary_df = pd.DataFrame(summary_data)
    summary_df = summary_df.sort_values('总队伍数', ascending=False)
    summary_df.to_csv('player_team_summary.csv', index=False, encoding='utf-8-sig')
    print(f"队伍统计摘要已保存: player_team_summary.csv ({len(summary_df)} 个玩家)")
    
    # 保存详细队伍配置
    with open('player_team_details.json', 'w', encoding='utf-8') as f:
        json.dump(detailed_report, f, ensure_ascii=False, indent=2)
    print(f"详细队伍配置已保存: player_team_details.json")
    
    # 生成CSV格式的详细队伍配置
    team_details_csv = []
    for player_data in detailed_report:
        player_name = player_data['玩家名称']
        
        # 攻击方队伍
        for team_detail in player_data['攻击方队伍详情']:
            team_details_csv.append({
                '玩家名称': player_name,
                '队伍类型': '攻击方',
                '队伍配置': team_detail['队伍配置'],
                '使用次数': team_detail['使用次数'],
                '胜利次数': team_detail['胜利次数'],
                '总次数': team_detail['总次数'],
                '胜率(%)': team_detail['胜率(%)']
            })
        
        # 防守方队伍
        for team_detail in player_data['防守方队伍详情']:
            team_details_csv.append({
                '玩家名称': player_name,
                '队伍类型': '防守方',
                '队伍配置': team_detail['队伍配置'],
                '使用次数': team_detail['使用次数'],
                '胜利次数': team_detail['胜利次数'],
                '总次数': team_detail['总次数'],
                '胜率(%)': team_detail['胜率(%)']
            })
    
    team_details_df = pd.DataFrame(team_details_csv)
    team_details_df.to_csv('player_team_details.csv', index=False, encoding='utf-8-sig')
    print(f"详细队伍配置CSV已保存: player_team_details.csv ({len(team_details_df)} 条记录)")
    
    # 生成简要报告
    with open('team_analysis_report.md', 'w', encoding='utf-8') as f:
        f.write("# 玩家队伍配置分析报告\n\n")
        
        f.write("## 📊 总体统计\n")
        f.write(f"- 分析玩家数: {len(detailed_report)} 人\n")
        f.write(f"- 总队伍配置数: {len(team_details_csv)} 种\n")
        
        # 统计最常用的队伍配置
        all_teams = Counter()
        for player_data in detailed_report:
            for team_detail in player_data['攻击方队伍详情']:
                all_teams[team_detail['队伍配置']] += team_detail['使用次数']
            for team_detail in player_data['防守方队伍详情']:
                all_teams[team_detail['队伍配置']] += team_detail['使用次数']
        
        f.write(f"- 不同队伍配置数: {len(all_teams)} 种\n\n")
        
        f.write("## 🏆 最常用队伍配置 (前10名)\n")
        for i, (team, count) in enumerate(all_teams.most_common(10), 1):
            f.write(f"{i}. **{team}** - 使用 {count} 次\n")
        
        f.write("\n## 👥 队伍配置最多的玩家 (前10名)\n")
        top_players = summary_df.head(10)
        for i, (_, player) in enumerate(top_players.iterrows(), 1):
            f.write(f"{i}. **{player['玩家名称']}** - {player['总队伍数']} 种配置 (攻击:{player['攻击方队伍数']}, 防守:{player['防守方队伍数']})\n")
        
        f.write("\n## 📁 详细数据文件\n")
        f.write("- 队伍统计摘要: `player_team_summary.csv`\n")
        f.write("- 详细队伍配置: `player_team_details.csv`\n")
        f.write("- 完整数据(JSON): `player_team_details.json`\n")
    
    print("简要报告已保存: team_analysis_report.md")

def main():
    """主函数"""
    print("开始分析玩家队伍配置...")
    
    # 加载数据
    df = load_battle_data()
    
    # 分析队伍配置
    player_teams = analyze_player_teams(df)
    
    # 生成报告
    detailed_report = generate_team_report(player_teams)
    
    # 保存报告
    save_team_reports(detailed_report)
    
    # 显示简要统计
    print(f"\n分析完成!")
    print(f"分析玩家: {len(detailed_report)} 人")
    
    # 统计总队伍数
    total_teams = sum(len(p['攻击方队伍详情']) + len(p['防守方队伍详情']) for p in detailed_report)
    print(f"总队伍配置: {total_teams} 种")
    
    # 显示队伍配置最多的玩家
    print(f"\n队伍配置最多的前5名玩家:")
    summary_data = []
    for player_data in detailed_report:
        total_teams = len(player_data['攻击方队伍详情']) + len(player_data['防守方队伍详情'])
        summary_data.append((player_data['玩家名称'], total_teams))
    
    summary_data.sort(key=lambda x: x[1], reverse=True)
    for i, (player, count) in enumerate(summary_data[:5], 1):
        print(f"{i}. {player}: {count} 种队伍配置")

if __name__ == "__main__":
    main()

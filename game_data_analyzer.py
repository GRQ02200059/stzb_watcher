#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
游戏数据抓取、分类和可视化系统
整合所有功能的主程序
"""

import json
import os
import re
import time
from datetime import datetime
from collections import defaultdict, Counter
import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns
from typing import Dict, List, Any, Optional
import warnings
warnings.filterwarnings('ignore')

# 设置中文字体
plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei']
plt.rcParams['axes.unicode_minus'] = False

class GameDataAnalyzer:
    """游戏数据分析器主类"""
    
    def __init__(self, data_dir="decompressed_data_report"):
        self.data_dir = data_dir
        self.battle_data = []
        self.rank_data = []
        self.system_data = []
        self.user_data = []
        
        # 创建必要的目录
        os.makedirs(data_dir, exist_ok=True)
        os.makedirs("output", exist_ok=True)
        os.makedirs("visualizations", exist_ok=True)
    
    def load_all_data(self):
        """加载所有数据并分类"""
        print("正在加载和分类数据...")
        
        if not os.path.exists(self.data_dir):
            print(f"数据目录 {self.data_dir} 不存在")
            return
        
        files = [f for f in os.listdir(self.data_dir) if f.endswith('.json')]
        print(f"发现 {len(files)} 个数据文件")
        
        for filename in files:
            filepath = os.path.join(self.data_dir, filename)
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                # 分类数据
                data_type = self.classify_data(data, filename)
                self.categorize_data(data, data_type, filename)
                
            except Exception as e:
                print(f"加载文件 {filename} 时出错: {e}")
        
        print(f"数据加载完成:")
        print(f"  战斗数据: {len(self.battle_data)} 个文件")
        print(f"  排行榜数据: {len(self.rank_data)} 个文件")
        print(f"  系统数据: {len(self.system_data)} 个文件")
        print(f"  用户数据: {len(self.user_data)} 个文件")
    
    def classify_data(self, data, filename):
        """分类数据"""
        file_size = os.path.getsize(os.path.join(self.data_dir, filename))
        
        if isinstance(data, list) and len(data) > 0:
            first_item = data[0]
            
            if isinstance(first_item, dict):
                if 'battle_id' in first_item:
                    return 'battle'
                elif 'userid' in first_item:
                    return 'user'
                else:
                    return 'system'
            
            elif isinstance(first_item, list):
                if len(first_item) > 5:
                    return 'rank'
                else:
                    return 'system'
            
            elif isinstance(first_item, (int, float)):
                return 'system'
        
        return 'system'
    
    def categorize_data(self, data, data_type, filename):
        """将数据分类存储"""
        file_info = {
            'filename': filename,
            'data': data,
            'timestamp': self.extract_timestamp(filename)
        }
        
        if data_type == 'battle':
            self.battle_data.append(file_info)
        elif data_type == 'rank':
            self.rank_data.append(file_info)
        elif data_type == 'user':
            self.user_data.append(file_info)
        else:
            self.system_data.append(file_info)
    
    def extract_timestamp(self, filename):
        """从文件名提取时间戳"""
        try:
            timestamp_str = filename.replace('decompressed_', '').replace('.json', '')
            return datetime.strptime(timestamp_str, '%Y%m%d%H%M%S%f')
        except:
            return datetime.now()
    
    def analyze_battle_data(self):
        """分析战斗数据"""
        print("\n=== 战斗数据分析 ===")
        
        if not self.battle_data:
            print("没有战斗数据")
            return None
        
        battle_stats = {
            'total_battles': 0,
            'attack_wins': 0,
            'defend_wins': 0,
            'unions': set(),
            'players': set(),
            'battle_locations': set()
        }
        
        detailed_battles = []
        
        for file_info in self.battle_data:
            data = file_info['data']
            
            if isinstance(data, list):
                for item in data:
                    if isinstance(item, list) and len(item) > 0:
                        battle_obj = item[0] if isinstance(item[0], dict) else None
                        
                        if battle_obj and 'battle_id' in battle_obj:
                            battle_stats['total_battles'] += 1
                            
                            # 统计胜负
                            result = battle_obj.get('result', 0)
                            if result == 1:
                                battle_stats['attack_wins'] += 1
                            else:
                                battle_stats['defend_wins'] += 1
                            
                            # 收集联盟和玩家信息
                            attack_union = battle_obj.get('attack_union_name', '')
                            defend_union = battle_obj.get('defend_union_name', '')
                            attack_name = battle_obj.get('attack_name', '')
                            defend_name = battle_obj.get('defend_name', '')
                            location = battle_obj.get('wid_name', '')
                            
                            if attack_union:
                                battle_stats['unions'].add(attack_union)
                            if defend_union:
                                battle_stats['unions'].add(defend_union)
                            if attack_name:
                                battle_stats['players'].add(attack_name)
                            if defend_name:
                                battle_stats['players'].add(defend_name)
                            if location:
                                battle_stats['battle_locations'].add(location)
                            
                            # 保存详细战斗信息
                            detailed_battles.append({
                                'battle_id': battle_obj.get('battle_id'),
                                'time': battle_obj.get('time'),
                                'result': '攻击方胜利' if result == 1 else '防守方胜利',
                                'attack_name': attack_name,
                                'attack_union': attack_union,
                                'defend_name': defend_name,
                                'defend_union': defend_union,
                                'location': location,
                                'attack_heroes': battle_obj.get('attack_all_hero_info', ''),
                                'defend_heroes': battle_obj.get('defend_all_hero_info', ''),
                                'attack_skills': battle_obj.get('all_skill_info', ''),
                                'timestamp': file_info['timestamp']
                            })
        
        # 保存战斗数据到CSV
        if detailed_battles:
            df = pd.DataFrame(detailed_battles)
            df.to_csv('output/battle_data.csv', index=False, encoding='utf-8-sig')
            print(f"战斗数据已保存到 output/battle_data.csv")
        
        # 打印统计信息
        print(f"总战斗数: {battle_stats['total_battles']}")
        print(f"攻击方胜利: {battle_stats['attack_wins']}")
        print(f"防守方胜利: {battle_stats['defend_wins']}")
        print(f"参与联盟数: {len(battle_stats['unions'])}")
        print(f"参与玩家数: {len(battle_stats['players'])}")
        print(f"战斗地点数: {len(battle_stats['battle_locations'])}")
        
        return battle_stats, detailed_battles
    
    def analyze_rank_data(self):
        """分析排行榜数据"""
        print("\n=== 排行榜数据分析 ===")
        
        if not self.rank_data:
            print("没有排行榜数据")
            return
        
        rank_info = []
        
        for file_info in self.rank_data:
            data = file_info['data']
            
            if isinstance(data, list):
                for item in data:
                    if isinstance(item, list) and len(item) > 0:
                        for rank_item in item:
                            if isinstance(rank_item, list) and len(rank_item) > 5:
                                # 解析排行榜数据
                                try:
                                    player_id = rank_item[0] if len(rank_item) > 0 else 0
                                    player_data = rank_item[1] if len(rank_item) > 1 else []
                                    
                                    if isinstance(player_data, list) and len(player_data) > 5:
                                        rank_info.append({
                                            'player_id': player_id,
                                            'name': player_data[3] if len(player_data) > 3 else '',
                                            'level': player_data[2] if len(player_data) > 2 else 0,
                                            'rank': player_data[4] if len(player_data) > 4 else 0,
                                            'score': player_data[5] if len(player_data) > 5 else 0,
                                            'server': player_data[6] if len(player_data) > 6 else '',
                                            'union': player_data[7] if len(player_data) > 7 else '',
                                            'timestamp': file_info['timestamp']
                                        })
                                except Exception as e:
                                    continue
        
        if rank_info:
            df = pd.DataFrame(rank_info)
            df.to_csv('output/rank_data.csv', index=False, encoding='utf-8-sig')
            print(f"排行榜数据已保存到 output/rank_data.csv")
            print(f"排行榜记录数: {len(rank_info)}")
            
            # 显示前10名
            if len(rank_info) > 0:
                top_10 = sorted(rank_info, key=lambda x: x.get('score', 0), reverse=True)[:10]
                print("\n前10名玩家:")
                for i, player in enumerate(top_10, 1):
                    print(f"  {i}. {player.get('name', '未知')} - 分数: {player.get('score', 0)}")
        
        return rank_info
    
    def create_visualizations(self, battle_stats=None, detailed_battles=None, rank_info=None):
        """创建可视化图表"""
        print("\n=== 创建可视化图表 ===")
        
        # 创建图表目录
        viz_dir = "visualizations"
        os.makedirs(viz_dir, exist_ok=True)
        
        # 1. 战斗胜负统计图
        if battle_stats and battle_stats['total_battles'] > 0:
            plt.figure(figsize=(10, 6))
            labels = ['攻击方胜利', '防守方胜利']
            sizes = [battle_stats['attack_wins'], battle_stats['defend_wins']]
            colors = ['#ff9999', '#66b3ff']
            
            plt.pie(sizes, labels=labels, colors=colors, autopct='%1.1f%%', startangle=90)
            plt.title('战斗胜负统计')
            plt.axis('equal')
            plt.savefig(f'{viz_dir}/battle_result_pie.png', dpi=300, bbox_inches='tight')
            plt.close()
            print("战斗胜负统计图已保存")
        
        # 2. 联盟战斗统计
        if detailed_battles:
            union_stats = defaultdict(int)
            for battle in detailed_battles:
                attack_union = battle.get('attack_union', '')
                defend_union = battle.get('defend_union', '')
                if attack_union:
                    union_stats[attack_union] += 1
                if defend_union:
                    union_stats[defend_union] += 1
            
            if union_stats:
                plt.figure(figsize=(12, 8))
                unions = list(union_stats.keys())[:10]  # 前10个联盟
                counts = [union_stats[union] for union in unions]
                
                plt.bar(unions, counts)
                plt.title('联盟战斗参与次数统计')
                plt.xlabel('联盟名称')
                plt.ylabel('战斗次数')
                plt.xticks(rotation=45)
                plt.tight_layout()
                plt.savefig(f'{viz_dir}/union_battle_stats.png', dpi=300, bbox_inches='tight')
                plt.close()
                print("联盟战斗统计图已保存")
        
        # 3. 排行榜可视化
        if rank_info:
            # 分数分布直方图
            scores = [player.get('score', 0) for player in rank_info if player.get('score', 0) > 0]
            if scores:
                plt.figure(figsize=(10, 6))
                plt.hist(scores, bins=20, alpha=0.7, color='skyblue', edgecolor='black')
                plt.title('玩家分数分布')
                plt.xlabel('分数')
                plt.ylabel('人数')
                plt.savefig(f'{viz_dir}/score_distribution.png', dpi=300, bbox_inches='tight')
                plt.close()
                print("分数分布图已保存")
            
            # 前20名排行榜
            if len(rank_info) > 0:
                top_20 = sorted(rank_info, key=lambda x: x.get('score', 0), reverse=True)[:20]
                plt.figure(figsize=(12, 8))
                names = [player.get('name', '未知')[:10] for player in top_20]  # 限制名字长度
                scores = [player.get('score', 0) for player in top_20]
                
                plt.barh(range(len(names)), scores)
                plt.yticks(range(len(names)), names)
                plt.title('前20名玩家排行榜')
                plt.xlabel('分数')
                plt.gca().invert_yaxis()
                plt.tight_layout()
                plt.savefig(f'{viz_dir}/top_20_rankings.png', dpi=300, bbox_inches='tight')
                plt.close()
                print("前20名排行榜图已保存")
        
        print(f"所有可视化图表已保存到 {viz_dir} 目录")
    
    def generate_report(self, battle_stats=None, detailed_battles=None, rank_info=None):
        """生成分析报告"""
        print("\n=== 生成分析报告 ===")
        
        report = []
        report.append("# 游戏数据分析报告")
        report.append(f"生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        report.append("")
        
        # 战斗数据报告
        if battle_stats and battle_stats['total_battles'] > 0:
            report.append("## 战斗数据统计")
            report.append(f"- 总战斗数: {battle_stats['total_battles']}")
            report.append(f"- 攻击方胜利: {battle_stats['attack_wins']} ({battle_stats['attack_wins']/battle_stats['total_battles']*100:.1f}%)")
            report.append(f"- 防守方胜利: {battle_stats['defend_wins']} ({battle_stats['defend_wins']/battle_stats['total_battles']*100:.1f}%)")
            report.append(f"- 参与联盟数: {len(battle_stats['unions'])}")
            report.append(f"- 参与玩家数: {len(battle_stats['players'])}")
            report.append(f"- 战斗地点数: {len(battle_stats['battle_locations'])}")
            report.append("")
        
        # 排行榜数据报告
        if rank_info:
            report.append("## 排行榜数据统计")
            report.append(f"- 排行榜记录数: {len(rank_info)}")
            
            if len(rank_info) > 0:
                top_10 = sorted(rank_info, key=lambda x: x.get('score', 0), reverse=True)[:10]
                report.append("### 前10名玩家")
                for i, player in enumerate(top_10, 1):
                    report.append(f"{i}. {player.get('name', '未知')} - 分数: {player.get('score', 0)}")
            report.append("")
        
        # 数据文件统计
        report.append("## 数据文件统计")
        report.append(f"- 战斗数据文件: {len(self.battle_data)}")
        report.append(f"- 排行榜数据文件: {len(self.rank_data)}")
        report.append(f"- 系统数据文件: {len(self.system_data)}")
        report.append(f"- 用户数据文件: {len(self.user_data)}")
        
        # 保存报告
        with open('output/analysis_report.md', 'w', encoding='utf-8') as f:
            f.write('\n'.join(report))
        
        print("分析报告已保存到 output/analysis_report.md")
    
    def run_analysis(self):
        """运行完整分析"""
        print("开始游戏数据分析...")
        
        # 加载数据
        self.load_all_data()
        
        # 分析战斗数据
        battle_result = self.analyze_battle_data()
        if battle_result:
            battle_stats, detailed_battles = battle_result
        else:
            battle_stats, detailed_battles = None, None
        
        # 分析排行榜数据
        rank_info = self.analyze_rank_data()
        
        # 创建可视化
        self.create_visualizations(battle_stats, detailed_battles, rank_info)
        
        # 生成报告
        self.generate_report(battle_stats, detailed_battles, rank_info)
        
        print("\n分析完成！")
        print("输出文件:")
        print("- output/battle_data.csv - 战斗数据")
        print("- output/rank_data.csv - 排行榜数据")
        print("- output/analysis_report.md - 分析报告")
        print("- visualizations/ - 可视化图表")

def main():
    """主函数"""
    analyzer = GameDataAnalyzer()
    analyzer.run_analysis()

if __name__ == "__main__":
    main()

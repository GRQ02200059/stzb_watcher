#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
增强版数据分析器
专门处理《率土之滨》游戏数据，支持更多数据格式
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

class EnhancedGameAnalyzer:
    """增强版游戏数据分析器"""
    
    def __init__(self, data_dir="decompressed_data_report"):
        self.data_dir = data_dir
        self.battle_data = []
        self.rank_data = []
        self.system_data = []
        self.user_data = []
        self.message_types = defaultdict(list)
        
        # 创建必要的目录
        os.makedirs(data_dir, exist_ok=True)
        os.makedirs("output", exist_ok=True)
        os.makedirs("visualizations", exist_ok=True)
    
    def load_and_analyze_all_data(self):
        """加载并分析所有数据"""
        print("🔍 正在加载和分类数据...")
        
        if not os.path.exists(self.data_dir):
            print(f"❌ 数据目录 {self.data_dir} 不存在")
            return
        
        files = [f for f in os.listdir(self.data_dir) if f.endswith('.json')]
        print(f"📦 发现 {len(files)} 个数据文件")
        
        # 按文件大小分类
        size_groups = {
            'small': [],    # <10KB
            'medium': [],   # 10-100KB  
            'large': []     # >100KB
        }
        
        for filename in files:
            filepath = os.path.join(self.data_dir, filename)
            size = os.path.getsize(filepath)
            
            if size < 10240:
                size_groups['small'].append(filename)
            elif size < 102400:
                size_groups['medium'].append(filename)
            else:
                size_groups['large'].append(filename)
        
        print(f"📊 文件大小分布:")
        print(f"  小文件 (<10KB): {len(size_groups['small'])} 个")
        print(f"  中文件 (10-100KB): {len(size_groups['medium'])} 个")
        print(f"  大文件 (>100KB): {len(size_groups['large'])} 个")
        
        # 分析每种类型的数据
        self.analyze_small_files(size_groups['small'])
        self.analyze_medium_files(size_groups['medium'])
        self.analyze_large_files(size_groups['large'])
        
        print(f"\n✅ 数据分类完成:")
        print(f"  战斗数据: {len(self.battle_data)} 个文件")
        print(f"  排行榜数据: {len(self.rank_data)} 个文件")
        print(f"  系统数据: {len(self.system_data)} 个文件")
        print(f"  用户数据: {len(self.user_data)} 个文件")
    
    def analyze_small_files(self, files):
        """分析小文件（系统状态数据）"""
        print(f"\n🔍 分析小文件 ({len(files)} 个)...")
        
        for filename in files:
            filepath = os.path.join(self.data_dir, filename)
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                # 分类为系统数据
                self.system_data.append({
                    'filename': filename,
                    'data': data,
                    'type': 'system_status',
                    'timestamp': self.extract_timestamp(filename)
                })
                
            except Exception as e:
                print(f"  ❌ 加载文件 {filename} 失败: {e}")
    
    def analyze_medium_files(self, files):
        """分析中文件（战斗数据）"""
        print(f"\n🔍 分析中文件 ({len(files)} 个)...")
        
        for filename in files:
            filepath = os.path.join(self.data_dir, filename)
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                # 检查是否为战斗数据
                if self.is_battle_data(data):
                    self.battle_data.append({
                        'filename': filename,
                        'data': data,
                        'type': 'battle',
                        'timestamp': self.extract_timestamp(filename)
                    })
                else:
                    self.system_data.append({
                        'filename': filename,
                        'data': data,
                        'type': 'system',
                        'timestamp': self.extract_timestamp(filename)
                    })
                
            except Exception as e:
                print(f"  ❌ 加载文件 {filename} 失败: {e}")
    
    def analyze_large_files(self, files):
        """分析大文件（排行榜数据）"""
        print(f"\n🔍 分析大文件 ({len(files)} 个)...")
        
        for filename in files:
            filepath = os.path.join(self.data_dir, filename)
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                # 检查是否为排行榜数据
                if self.is_rank_data(data):
                    self.rank_data.append({
                        'filename': filename,
                        'data': data,
                        'type': 'rank',
                        'timestamp': self.extract_timestamp(filename)
                    })
                else:
                    self.system_data.append({
                        'filename': filename,
                        'data': data,
                        'type': 'large_system',
                        'timestamp': self.extract_timestamp(filename)
                    })
                
            except Exception as e:
                print(f"  ❌ 加载文件 {filename} 失败: {e}")
    
    def is_battle_data(self, data):
        """判断是否为战斗数据"""
        if isinstance(data, list) and len(data) > 0:
            for item in data:
                if isinstance(item, list) and len(item) > 0:
                    first_item = item[0]
                    if isinstance(first_item, dict) and 'battle_id' in first_item:
                        return True
        return False
    
    def is_rank_data(self, data):
        """判断是否为排行榜数据"""
        if isinstance(data, list) and len(data) > 0:
            first_item = data[0]
            if isinstance(first_item, list) and len(first_item) > 5:
                # 检查是否包含玩家信息
                for rank_item in first_item:
                    if isinstance(rank_item, list) and len(rank_item) > 5:
                        # 检查是否包含玩家ID、姓名等字段
                        if (isinstance(rank_item[0], (int, str)) and 
                            len(rank_item) > 3 and 
                            isinstance(rank_item[3], str)):
                            return True
        return False
    
    def extract_timestamp(self, filename):
        """从文件名提取时间戳"""
        try:
            timestamp_str = filename.replace('decompressed_', '').replace('.json', '')
            return datetime.strptime(timestamp_str, '%Y%m%d%H%M%S%f')
        except:
            return datetime.now()
    
    def analyze_battle_data(self):
        """分析战斗数据"""
        print("\n⚔️ 分析战斗数据...")
        
        if not self.battle_data:
            print("  ❌ 没有战斗数据")
            return None, []
        
        battle_stats = {
            'total_battles': 0,
            'attack_wins': 0,
            'defend_wins': 0,
            'unions': set(),
            'players': set(),
            'battle_locations': set(),
            'heroes_used': set()
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
                            
                            # 收集详细信息
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
                            
                            # 提取英雄信息
                            attack_heroes = battle_obj.get('attack_all_hero_info', '')
                            defend_heroes = battle_obj.get('defend_all_hero_info', '')
                            
                            if attack_heroes:
                                hero_ids = re.findall(r'(\d+)', attack_heroes)
                                battle_stats['heroes_used'].update(hero_ids)
                            
                            if defend_heroes:
                                hero_ids = re.findall(r'(\d+)', defend_heroes)
                                battle_stats['heroes_used'].update(hero_ids)
                            
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
                                'attack_heroes': attack_heroes,
                                'defend_heroes': defend_heroes,
                                'attack_skills': battle_obj.get('all_skill_info', ''),
                                'timestamp': file_info['timestamp']
                            })
        
        # 保存战斗数据到CSV
        if detailed_battles:
            df = pd.DataFrame(detailed_battles)
            df.to_csv('output/battle_data.csv', index=False, encoding='utf-8-sig')
            print(f"  ✅ 战斗数据已保存到 output/battle_data.csv ({len(detailed_battles)} 条记录)")
        
        # 打印统计信息
        print(f"  📊 总战斗数: {battle_stats['total_battles']}")
        print(f"  🏆 攻击方胜利: {battle_stats['attack_wins']}")
        print(f"  🛡️ 防守方胜利: {battle_stats['defend_wins']}")
        print(f"  🏰 参与联盟数: {len(battle_stats['unions'])}")
        print(f"  👥 参与玩家数: {len(battle_stats['players'])}")
        print(f"  🗺️ 战斗地点数: {len(battle_stats['battle_locations'])}")
        print(f"  ⚔️ 使用英雄数: {len(battle_stats['heroes_used'])}")
        
        return battle_stats, detailed_battles
    
    def analyze_rank_data(self):
        """分析排行榜数据"""
        print("\n🏆 分析排行榜数据...")
        
        if not self.rank_data:
            print("  ❌ 没有排行榜数据")
            return []
        
        rank_info = []
        
        for file_info in self.rank_data:
            data = file_info['data']
            
            if isinstance(data, list):
                for item in data:
                    if isinstance(item, list) and len(item) > 0:
                        for rank_item in item:
                            if isinstance(rank_item, list) and len(rank_item) > 5:
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
            print(f"  ✅ 排行榜数据已保存到 output/rank_data.csv ({len(rank_info)} 条记录)")
            
            # 显示前10名
            if len(rank_info) > 0:
                top_10 = sorted(rank_info, key=lambda x: x.get('score', 0), reverse=True)[:10]
                print(f"  🥇 前10名玩家:")
                for i, player in enumerate(top_10, 1):
                    name = player.get('name', '未知')[:10]
                    score = player.get('score', 0)
                    print(f"    {i:2d}. {name} - {score}")
        else:
            print("  ❌ 没有有效的排行榜数据")
        
        return rank_info
    
    def create_visualizations(self, battle_stats=None, detailed_battles=None, rank_info=None):
        """创建可视化图表"""
        print("\n📈 创建可视化图表...")
        
        viz_dir = "visualizations"
        os.makedirs(viz_dir, exist_ok=True)
        
        # 1. 战斗胜负统计图
        if battle_stats and battle_stats['total_battles'] > 0:
            plt.figure(figsize=(10, 6))
            labels = ['攻击方胜利', '防守方胜利']
            sizes = [battle_stats['attack_wins'], battle_stats['defend_wins']]
            colors = ['#ff9999', '#66b3ff']
            
            plt.pie(sizes, labels=labels, colors=colors, autopct='%1.1f%%', startangle=90)
            plt.title('战斗胜负统计', fontsize=16, fontweight='bold')
            plt.axis('equal')
            plt.savefig(f'{viz_dir}/battle_result_pie.png', dpi=300, bbox_inches='tight')
            plt.close()
            print(f"  ✅ 战斗胜负统计图已保存")
        
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
                unions = list(union_stats.keys())[:10]
                counts = [union_stats[union] for union in unions]
                
                plt.bar(unions, counts, color='skyblue', edgecolor='navy')
                plt.title('联盟战斗参与次数统计', fontsize=16, fontweight='bold')
                plt.xlabel('联盟名称', fontsize=12)
                plt.ylabel('战斗次数', fontsize=12)
                plt.xticks(rotation=45)
                plt.tight_layout()
                plt.savefig(f'{viz_dir}/union_battle_stats.png', dpi=300, bbox_inches='tight')
                plt.close()
                print(f"  ✅ 联盟战斗统计图已保存")
        
        # 3. 排行榜可视化
        if rank_info:
            # 分数分布直方图
            scores = [player.get('score', 0) for player in rank_info if player.get('score', 0) > 0]
            if scores:
                plt.figure(figsize=(10, 6))
                plt.hist(scores, bins=20, alpha=0.7, color='lightgreen', edgecolor='black')
                plt.title('玩家分数分布', fontsize=16, fontweight='bold')
                plt.xlabel('分数', fontsize=12)
                plt.ylabel('人数', fontsize=12)
                plt.grid(True, alpha=0.3)
                plt.savefig(f'{viz_dir}/score_distribution.png', dpi=300, bbox_inches='tight')
                plt.close()
                print(f"  ✅ 分数分布图已保存")
            
            # 前20名排行榜
            if len(rank_info) > 0:
                top_20 = sorted(rank_info, key=lambda x: x.get('score', 0), reverse=True)[:20]
                plt.figure(figsize=(12, 8))
                names = [player.get('name', '未知')[:10] for player in top_20]
                scores = [player.get('score', 0) for player in top_20]
                
                plt.barh(range(len(names)), scores, color='gold', edgecolor='orange')
                plt.yticks(range(len(names)), names)
                plt.title('前20名玩家排行榜', fontsize=16, fontweight='bold')
                plt.xlabel('分数', fontsize=12)
                plt.gca().invert_yaxis()
                plt.tight_layout()
                plt.savefig(f'{viz_dir}/top_20_rankings.png', dpi=300, bbox_inches='tight')
                plt.close()
                print(f"  ✅ 前20名排行榜图已保存")
        
        print(f"  📁 所有图表已保存到 {viz_dir}/ 目录")
    
    def generate_comprehensive_report(self, battle_stats=None, detailed_battles=None, rank_info=None):
        """生成综合分析报告"""
        print("\n📄 生成分析报告...")
        
        report = []
        report.append("# 《率土之滨》游戏数据分析报告")
        report.append(f"生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        report.append("")
        
        # 数据概览
        report.append("## 📊 数据概览")
        report.append(f"- 战斗数据文件: {len(self.battle_data)} 个")
        report.append(f"- 排行榜数据文件: {len(self.rank_data)} 个")
        report.append(f"- 系统数据文件: {len(self.system_data)} 个")
        report.append(f"- 用户数据文件: {len(self.user_data)} 个")
        report.append("")
        
        # 战斗数据报告
        if battle_stats and battle_stats['total_battles'] > 0:
            report.append("## ⚔️ 战斗数据统计")
            report.append(f"- 总战斗数: {battle_stats['total_battles']}")
            report.append(f"- 攻击方胜利: {battle_stats['attack_wins']} ({battle_stats['attack_wins']/battle_stats['total_battles']*100:.1f}%)")
            report.append(f"- 防守方胜利: {battle_stats['defend_wins']} ({battle_stats['defend_wins']/battle_stats['total_battles']*100:.1f}%)")
            report.append(f"- 参与联盟数: {len(battle_stats['unions'])}")
            report.append(f"- 参与玩家数: {len(battle_stats['players'])}")
            report.append(f"- 战斗地点数: {len(battle_stats['battle_locations'])}")
            report.append(f"- 使用英雄数: {len(battle_stats['heroes_used'])}")
            report.append("")
        
        # 排行榜数据报告
        if rank_info:
            report.append("## 🏆 排行榜数据统计")
            report.append(f"- 排行榜记录数: {len(rank_info)}")
            
            if len(rank_info) > 0:
                top_10 = sorted(rank_info, key=lambda x: x.get('score', 0), reverse=True)[:10]
                report.append("### 前10名玩家")
                for i, player in enumerate(top_10, 1):
                    name = player.get('name', '未知')
                    score = player.get('score', 0)
                    report.append(f"{i}. {name} - 分数: {score}")
            report.append("")
        
        # 系统数据报告
        if self.system_data:
            report.append("## ⚙️ 系统数据统计")
            report.append(f"- 系统数据文件数: {len(self.system_data)}")
            
            # 按类型分组
            type_counts = defaultdict(int)
            for item in self.system_data:
                type_counts[item['type']] += 1
            
            for data_type, count in type_counts.items():
                report.append(f"- {data_type}: {count} 个文件")
            report.append("")
        
        # 文件输出信息
        report.append("## 📁 输出文件")
        report.append("- `output/battle_data.csv` - 详细战斗数据")
        report.append("- `output/rank_data.csv` - 排行榜数据")
        report.append("- `visualizations/` - 可视化图表目录")
        report.append("  - `battle_result_pie.png` - 战斗胜负统计图")
        report.append("  - `union_battle_stats.png` - 联盟战斗统计图")
        report.append("  - `score_distribution.png` - 分数分布图")
        report.append("  - `top_20_rankings.png` - 前20名排行榜图")
        report.append("")
        
        # 保存报告
        with open('output/analysis_report.md', 'w', encoding='utf-8') as f:
            f.write('\n'.join(report))
        
        print(f"  ✅ 分析报告已保存到 output/analysis_report.md")
    
    def run_complete_analysis(self):
        """运行完整分析"""
        print("🚀 开始完整数据分析...")
        print("="*60)
        
        # 加载和分类数据
        self.load_and_analyze_all_data()
        
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
        self.generate_comprehensive_report(battle_stats, detailed_battles, rank_info)
        
        print("\n" + "="*60)
        print("🎉 分析完成！")
        print("="*60)
        print("📁 输出文件:")
        print("  • output/battle_data.csv - 战斗数据")
        print("  • output/rank_data.csv - 排行榜数据")
        print("  • output/analysis_report.md - 分析报告")
        print("  • visualizations/ - 可视化图表")
        print("="*60)

def main():
    """主函数"""
    analyzer = EnhancedGameAnalyzer()
    analyzer.run_complete_analysis()

if __name__ == "__main__":
    main()



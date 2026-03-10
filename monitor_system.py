#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
实时系统监控脚本
监控抓取系统的运行状态和数据分析结果
"""

import os
import time
import json
import pandas as pd
from datetime import datetime
import matplotlib.pyplot as plt
import seaborn as sns

# 设置中文字体
plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei']
plt.rcParams['axes.unicode_minus'] = False

class SystemMonitor:
    """系统监控器"""
    
    def __init__(self):
        self.output_dir = "output"
        self.logs_dir = "logs"
        self.data_dir = "decompressed_data_report"
        self.viz_dir = "visualizations"
    
    def check_system_status(self):
        """检查系统状态"""
        print("🔍 检查系统状态...")
        print("="*60)
        
        # 检查目录
        directories = [self.output_dir, self.logs_dir, self.data_dir, self.viz_dir]
        for directory in directories:
            if os.path.exists(directory):
                files = len([f for f in os.listdir(directory) if os.path.isfile(os.path.join(directory, f))])
                print(f"✅ {directory}/ - {files} 个文件")
            else:
                print(f"❌ {directory}/ - 不存在")
        
        print()
    
    def check_data_files(self):
        """检查数据文件"""
        print("📊 检查数据文件...")
        print("="*60)
        
        # 检查原始数据文件
        if os.path.exists(self.data_dir):
            data_files = [f for f in os.listdir(self.data_dir) if f.endswith('.json')]
            print(f"📦 原始数据文件: {len(data_files)} 个")
            
            if data_files:
                # 按消息类型分组
                msg_types = {}
                for filename in data_files:
                    if '_' in filename:
                        parts = filename.split('_')
                        if len(parts) >= 3:
                            msg_type = parts[2].replace('.json', '')
                            msg_types[msg_type] = msg_types.get(msg_type, 0) + 1
                
                print("🏷️ 消息类型分布:")
                for msg_type, count in msg_types.items():
                    print(f"   {msg_type}: {count} 个文件")
        
        # 检查分析结果文件
        result_files = [
            ('battle_data_realtime.csv', '实时战斗数据'),
            ('rank_data_realtime.csv', '实时排行榜数据'),
            ('battle_data_final.csv', '最终战斗数据'),
            ('rank_data_final.csv', '最终排行榜数据')
        ]
        
        print("\n📄 分析结果文件:")
        for filename, description in result_files:
            filepath = os.path.join(self.output_dir, filename)
            if os.path.exists(filepath):
                size = os.path.getsize(filepath)
                print(f"   ✅ {description}: {size:,} 字节")
            else:
                print(f"   ❌ {description}: 不存在")
        
        print()
    
    def check_logs(self):
        """检查日志文件"""
        print("📝 检查日志文件...")
        print("="*60)
        
        if os.path.exists(self.logs_dir):
            log_files = [f for f in os.listdir(self.logs_dir) if f.endswith('.log')]
            print(f"📋 日志文件: {len(log_files)} 个")
            
            if log_files:
                # 显示最新的日志文件
                latest_log = max(log_files, key=lambda x: os.path.getctime(os.path.join(self.logs_dir, x)))
                log_path = os.path.join(self.logs_dir, latest_log)
                
                print(f"📄 最新日志: {latest_log}")
                
                # 显示最后几行日志
                try:
                    with open(log_path, 'r', encoding='utf-8') as f:
                        lines = f.readlines()
                        print("📋 最近日志:")
                        for line in lines[-5:]:
                            print(f"   {line.strip()}")
                except Exception as e:
                    print(f"   ❌ 读取日志失败: {str(e)}")
        else:
            print("❌ 日志目录不存在")
        
        print()
    
    def check_visualizations(self):
        """检查可视化文件"""
        print("📈 检查可视化文件...")
        print("="*60)
        
        if os.path.exists(self.viz_dir):
            viz_files = [f for f in os.listdir(self.viz_dir) if f.endswith('.png')]
            print(f"🖼️ 可视化文件: {len(viz_files)} 个")
            
            if viz_files:
                print("📊 图表列表:")
                for viz_file in viz_files:
                    filepath = os.path.join(self.viz_dir, viz_file)
                    size = os.path.getsize(filepath)
                    mtime = datetime.fromtimestamp(os.path.getmtime(filepath))
                    print(f"   📈 {viz_file} - {size:,} 字节 - {mtime.strftime('%H:%M:%S')}")
        else:
            print("❌ 可视化目录不存在")
        
        print()
    
    def analyze_battle_data(self):
        """分析战斗数据"""
        print("⚔️ 分析战斗数据...")
        print("="*60)
        
        battle_file = os.path.join(self.output_dir, 'battle_data_realtime.csv')
        if os.path.exists(battle_file):
            try:
                df = pd.read_csv(battle_file, encoding='utf-8-sig')
                print(f"📊 战斗记录数: {len(df)}")
                
                if len(df) > 0:
                    # 胜负统计
                    attack_wins = len(df[df['result'] == '攻击方胜利'])
                    defend_wins = len(df[df['result'] == '防守方胜利'])
                    print(f"🏆 攻击方胜利: {attack_wins} ({attack_wins/len(df)*100:.1f}%)")
                    print(f"🛡️ 防守方胜利: {defend_wins} ({defend_wins/len(df)*100:.1f}%)")
                    
                    # 联盟统计
                    unions = set()
                    for union in df['attack_union'].dropna():
                        if union:
                            unions.add(union)
                    for union in df['defend_union'].dropna():
                        if union:
                            unions.add(union)
                    print(f"🏰 参与联盟数: {len(unions)}")
                    
                    # 最近战斗
                    print("⚔️ 最近5场战斗:")
                    recent_battles = df.tail(5)
                    for _, battle in recent_battles.iterrows():
                        print(f"   {battle['attack_name']} vs {battle['defend_name']} - {battle['result']}")
                else:
                    print("❌ 没有战斗数据")
            except Exception as e:
                print(f"❌ 分析战斗数据失败: {str(e)}")
        else:
            print("❌ 战斗数据文件不存在")
        
        print()
    
    def analyze_rank_data(self):
        """分析排行榜数据"""
        print("🏆 分析排行榜数据...")
        print("="*60)
        
        rank_file = os.path.join(self.output_dir, 'rank_data_realtime.csv')
        if os.path.exists(rank_file):
            try:
                df = pd.read_csv(rank_file, encoding='utf-8-sig')
                print(f"📊 排行榜记录数: {len(df)}")
                
                if len(df) > 0:
                    # 前10名
                    top_10 = df.nlargest(10, 'score')
                    print("🥇 前10名玩家:")
                    for i, (_, player) in enumerate(top_10.iterrows(), 1):
                        name = player['name'][:15] if len(str(player['name'])) > 15 else player['name']
                        score = player['score']
                        print(f"   {i:2d}. {name} - {score}")
                else:
                    print("❌ 没有排行榜数据")
            except Exception as e:
                print(f"❌ 分析排行榜数据失败: {str(e)}")
        else:
            print("❌ 排行榜数据文件不存在")
        
        print()
    
    def generate_system_report(self):
        """生成系统报告"""
        print("📄 生成系统报告...")
        print("="*60)
        
        report = []
        report.append("# 系统监控报告")
        report.append(f"生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        report.append("")
        
        # 数据文件统计
        if os.path.exists(self.data_dir):
            data_files = [f for f in os.listdir(self.data_dir) if f.endswith('.json')]
            report.append(f"## 📊 数据文件统计")
            report.append(f"- 原始数据文件: {len(data_files)} 个")
            report.append("")
        
        # 分析结果统计
        result_files = [
            ('battle_data_realtime.csv', '实时战斗数据'),
            ('rank_data_realtime.csv', '实时排行榜数据'),
            ('battle_data_final.csv', '最终战斗数据'),
            ('rank_data_final.csv', '最终排行榜数据')
        ]
        
        report.append(f"## 📄 分析结果文件")
        for filename, description in result_files:
            filepath = os.path.join(self.output_dir, filename)
            if os.path.exists(filepath):
                size = os.path.getsize(filepath)
                report.append(f"- {description}: {size:,} 字节")
        report.append("")
        
        # 可视化文件统计
        if os.path.exists(self.viz_dir):
            viz_files = [f for f in os.listdir(self.viz_dir) if f.endswith('.png')]
            report.append(f"## 📈 可视化文件")
            report.append(f"- 图表文件: {len(viz_files)} 个")
            for viz_file in viz_files:
                report.append(f"- {viz_file}")
        report.append("")
        
        # 保存报告
        report_file = os.path.join(self.output_dir, 'system_monitor_report.md')
        with open(report_file, 'w', encoding='utf-8') as f:
            f.write('\n'.join(report))
        
        print(f"✅ 系统报告已生成: {report_file}")
    
    def run_monitor(self):
        """运行监控"""
        print("🔍 系统监控器启动")
        print("="*60)
        
        while True:
            try:
                # 清屏
                os.system('cls' if os.name == 'nt' else 'clear')
                
                print("🔍 实时系统监控器")
                print("="*60)
                print(f"监控时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
                print("="*60)
                
                # 检查系统状态
                self.check_system_status()
                self.check_data_files()
                self.check_logs()
                self.check_visualizations()
                self.analyze_battle_data()
                self.analyze_rank_data()
                
                print("💡 提示: 按 Ctrl+C 退出监控")
                print("🔄 每10秒自动刷新...")
                
                # 等待10秒
                time.sleep(10)
                
            except KeyboardInterrupt:
                print("\n👋 退出监控器")
                break
            except Exception as e:
                print(f"\n❌ 监控出错: {str(e)}")
                time.sleep(5)

def main():
    """主函数"""
    monitor = SystemMonitor()
    
    print("🔍 系统监控器")
    print("="*60)
    print("功能:")
    print("  ✓ 检查系统状态")
    print("  ✓ 分析数据文件")
    print("  ✓ 查看日志信息")
    print("  ✓ 监控可视化文件")
    print("  ✓ 分析战斗数据")
    print("  ✓ 分析排行榜数据")
    print("="*60)
    
    choice = input("选择操作 (1: 单次检查, 2: 持续监控): ").strip()
    
    if choice == "1":
        # 单次检查
        monitor.check_system_status()
        monitor.check_data_files()
        monitor.check_logs()
        monitor.check_visualizations()
        monitor.analyze_battle_data()
        monitor.analyze_rank_data()
        monitor.generate_system_report()
    elif choice == "2":
        # 持续监控
        monitor.run_monitor()
    else:
        print("❌ 无效选择")

if __name__ == "__main__":
    main()



#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
游戏数据抓取和分析系统主界面
整合所有功能的主程序
"""

import os
import sys
import time
from datetime import datetime
from enhanced_scraper import EnhancedGameScraper
from game_data_analyzer import GameDataAnalyzer

class MainInterface:
    """主界面类"""
    
    def __init__(self):
        self.scraper = EnhancedGameScraper()
        self.analyzer = GameDataAnalyzer()
        self.running = True
    
    def print_banner(self):
        """打印程序横幅"""
        print("="*60)
        print("🎮 游戏数据抓取和分析系统")
        print("="*60)
        print("功能包括:")
        print("  • 实时网络数据抓取")
        print("  • 自动数据分类和解析")
        print("  • 战斗数据详细分析")
        print("  • 排行榜数据可视化")
        print("  • 生成分析报告和图表")
        print("="*60)
    
    def print_menu(self):
        """打印主菜单"""
        print("\n📋 主菜单:")
        print("1. 🔍 开始实时数据抓取")
        print("2. 📊 分析现有数据")
        print("3. 🎯 查看战斗数据详情")
        print("4. 🏆 查看排行榜数据")
        print("5. 📈 生成可视化图表")
        print("6. 📄 生成分析报告")
        print("7. ⚙️  系统设置")
        print("8. ❌ 退出程序")
        print("-" * 40)
    
    def start_capture(self):
        """开始数据抓取"""
        print("\n🔍 开始实时数据抓取")
        print("-" * 30)
        
        # 获取用户输入
        host = input("请输入目标主机IP (默认: 42.186.64.43): ").strip() or "42.186.64.43"
        port = input("请输入端口 (可选，直接回车跳过): ").strip() or None
        
        if port:
            try:
                port = int(port)
            except ValueError:
                print("⚠️  端口格式错误，将使用默认设置")
                port = None
        
        print(f"\n🎯 目标: {host}" + (f":{port}" if port else ""))
        print("⏰ 开始时间:", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
        print("💡 按 Ctrl+C 停止抓取")
        print("-" * 30)
        
        try:
            self.scraper.start_capture(host, port)
        except KeyboardInterrupt:
            print("\n⏹️  抓取已停止")
        except Exception as e:
            print(f"❌ 抓取出错: {str(e)}")
    
    def analyze_data(self):
        """分析现有数据"""
        print("\n📊 分析现有数据")
        print("-" * 30)
        
        try:
            self.analyzer.run_analysis()
            print("\n✅ 数据分析完成！")
        except Exception as e:
            print(f"❌ 分析出错: {str(e)}")
    
    def view_battle_data(self):
        """查看战斗数据详情"""
        print("\n🎯 战斗数据详情")
        print("-" * 30)
        
        try:
            # 检查是否有战斗数据文件
            battle_file = "output/battle_data.csv"
            if os.path.exists(battle_file):
                import pandas as pd
                df = pd.read_csv(battle_file, encoding='utf-8-sig')
                
                print(f"📊 战斗数据统计:")
                print(f"  总战斗数: {len(df)}")
                print(f"  攻击方胜利: {len(df[df['result'] == '攻击方胜利'])}")
                print(f"  防守方胜利: {len(df[df['result'] == '防守方胜利'])}")
                
                print(f"\n🏆 最近5场战斗:")
                recent_battles = df.tail(5)
                for _, battle in recent_battles.iterrows():
                    print(f"  • {battle['attack_name']} vs {battle['defend_name']} - {battle['result']}")
                
                print(f"\n📁 详细数据已保存到: {battle_file}")
            else:
                print("❌ 没有找到战斗数据文件，请先运行数据分析")
                
        except Exception as e:
            print(f"❌ 查看战斗数据出错: {str(e)}")
    
    def view_rank_data(self):
        """查看排行榜数据"""
        print("\n🏆 排行榜数据")
        print("-" * 30)
        
        try:
            # 检查是否有排行榜数据文件
            rank_file = "output/rank_data.csv"
            if os.path.exists(rank_file):
                import pandas as pd
                df = pd.read_csv(rank_file, encoding='utf-8-sig')
                
                print(f"📊 排行榜数据统计:")
                print(f"  总记录数: {len(df)}")
                
                if len(df) > 0:
                    print(f"\n🥇 前10名玩家:")
                    top_10 = df.nlargest(10, 'score')
                    for i, (_, player) in enumerate(top_10.iterrows(), 1):
                        name = player.get('name', '未知')
                        score = player.get('score', 0)
                        print(f"  {i:2d}. {name} - {score}")
                
                print(f"\n📁 详细数据已保存到: {rank_file}")
            else:
                print("❌ 没有找到排行榜数据文件，请先运行数据分析")
                
        except Exception as e:
            print(f"❌ 查看排行榜数据出错: {str(e)}")
    
    def generate_visualizations(self):
        """生成可视化图表"""
        print("\n📈 生成可视化图表")
        print("-" * 30)
        
        try:
            # 重新运行分析以生成图表
            self.analyzer.run_analysis()
            
            # 检查生成的图表
            viz_dir = "visualizations"
            if os.path.exists(viz_dir):
                charts = [f for f in os.listdir(viz_dir) if f.endswith('.png')]
                print(f"✅ 已生成 {len(charts)} 个图表:")
                for chart in charts:
                    print(f"  • {chart}")
                print(f"\n📁 图表保存位置: {viz_dir}/")
            else:
                print("❌ 没有找到图表文件")
                
        except Exception as e:
            print(f"❌ 生成图表出错: {str(e)}")
    
    def generate_report(self):
        """生成分析报告"""
        print("\n📄 生成分析报告")
        print("-" * 30)
        
        try:
            # 重新运行分析以生成报告
            self.analyzer.run_analysis()
            
            # 检查生成的报告
            report_file = "output/analysis_report.md"
            if os.path.exists(report_file):
                print(f"✅ 分析报告已生成")
                print(f"📁 报告位置: {report_file}")
                
                # 显示报告内容
                with open(report_file, 'r', encoding='utf-8') as f:
                    content = f.read()
                    print(f"\n📋 报告内容预览:")
                    print("-" * 30)
                    lines = content.split('\n')[:20]  # 显示前20行
                    for line in lines:
                        print(line)
                    if len(content.split('\n')) > 20:
                        print("...")
            else:
                print("❌ 没有找到报告文件")
                
        except Exception as e:
            print(f"❌ 生成报告出错: {str(e)}")
    
    def system_settings(self):
        """系统设置"""
        print("\n⚙️  系统设置")
        print("-" * 30)
        
        print("当前设置:")
        print(f"  数据目录: {self.scraper.output_dir}")
        print(f"  输出目录: output/")
        print(f"  图表目录: visualizations/")
        
        print("\n可用的设置选项:")
        print("1. 更改数据目录")
        print("2. 查看系统状态")
        print("3. 清理临时文件")
        print("4. 返回主菜单")
        
        choice = input("\n请选择设置选项 (1-4): ").strip()
        
        if choice == "1":
            new_dir = input("请输入新的数据目录路径: ").strip()
            if new_dir:
                self.scraper.output_dir = new_dir
                self.analyzer.data_dir = new_dir
                print(f"✅ 数据目录已更改为: {new_dir}")
        
        elif choice == "2":
            print("\n📊 系统状态:")
            print(f"  数据目录存在: {os.path.exists(self.scraper.output_dir)}")
            print(f"  输出目录存在: {os.path.exists('output')}")
            print(f"  图表目录存在: {os.path.exists('visualizations')}")
            
            if os.path.exists(self.scraper.output_dir):
                files = [f for f in os.listdir(self.scraper.output_dir) if f.endswith('.json')]
                print(f"  数据文件数量: {len(files)}")
        
        elif choice == "3":
            print("🧹 清理临时文件...")
            # 这里可以添加清理逻辑
            print("✅ 清理完成")
        
        elif choice == "4":
            return
        
        else:
            print("❌ 无效选择")
    
    def run(self):
        """运行主程序"""
        self.print_banner()
        
        while self.running:
            try:
                self.print_menu()
                choice = input("请选择操作 (1-8): ").strip()
                
                if choice == "1":
                    self.start_capture()
                elif choice == "2":
                    self.analyze_data()
                elif choice == "3":
                    self.view_battle_data()
                elif choice == "4":
                    self.view_rank_data()
                elif choice == "5":
                    self.generate_visualizations()
                elif choice == "6":
                    self.generate_report()
                elif choice == "7":
                    self.system_settings()
                elif choice == "8":
                    print("\n👋 感谢使用，再见！")
                    self.running = False
                else:
                    print("❌ 无效选择，请重新输入")
                
                if self.running:
                    input("\n按回车键继续...")
                    print("\n" + "="*60)
                
            except KeyboardInterrupt:
                print("\n\n👋 程序已退出")
                self.running = False
            except Exception as e:
                print(f"\n❌ 程序出错: {str(e)}")
                input("按回车键继续...")

def main():
    """主函数"""
    try:
        interface = MainInterface()
        interface.run()
    except Exception as e:
        print(f"❌ 程序启动失败: {str(e)}")
        sys.exit(1)

if __name__ == "__main__":
    main()

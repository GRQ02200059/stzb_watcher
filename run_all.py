#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
一键运行脚本 - 自动生成所有分析结果
无需用户交互，直接完成所有分析任务
"""

import os
import sys
import time
from datetime import datetime
from enhanced_analyzer import EnhancedGameAnalyzer

def print_banner():
    """打印横幅"""
    print("="*80)
    print("🚀 游戏数据一键分析系统")
    print("="*80)
    print("正在自动执行以下任务:")
    print("  ✓ 加载和分类所有数据")
    print("  ✓ 分析战斗数据")
    print("  ✓ 分析排行榜数据")
    print("  ✓ 生成可视化图表")
    print("  ✓ 生成详细分析报告")
    print("  ✓ 输出所有结果文件")
    print("="*80)

def check_dependencies():
    """检查依赖包"""
    print("🔍 检查依赖包...")
    
    required_packages = ['pandas', 'matplotlib', 'seaborn', 'numpy']
    missing_packages = []
    
    for package in required_packages:
        try:
            __import__(package)
            print(f"  ✅ {package}")
        except ImportError:
            missing_packages.append(package)
            print(f"  ❌ {package}")
    
    if missing_packages:
        print(f"\n⚠️  缺少依赖包: {', '.join(missing_packages)}")
        print("请运行: python install_dependencies.py")
        return False
    
    print("✅ 所有依赖包已安装")
    return True

def create_directories():
    """创建必要的目录"""
    print("📁 创建输出目录...")
    
    directories = [
        "output",
        "visualizations", 
        "decompressed_data_report"
    ]
    
    for directory in directories:
        os.makedirs(directory, exist_ok=True)
        print(f"  ✅ {directory}/")
    
    print("✅ 目录创建完成")

def run_analysis():
    """运行完整分析"""
    print("\n📊 开始数据分析...")
    print("-" * 50)
    
    try:
        # 创建增强版分析器
        analyzer = EnhancedGameAnalyzer()
        
        # 运行完整分析
        analyzer.run_complete_analysis()
        
        print("\n✅ 数据分析完成！")
        return True
        
    except Exception as e:
        print(f"\n❌ 分析过程中出错: {str(e)}")
        return False

def show_results():
    """显示结果摘要"""
    print("\n" + "="*80)
    print("📋 分析结果摘要")
    print("="*80)
    
    # 检查输出文件
    output_files = [
        ("output/battle_data.csv", "战斗数据"),
        ("output/rank_data.csv", "排行榜数据"),
        ("output/analysis_report.md", "分析报告")
    ]
    
    print("📄 生成的文件:")
    for filepath, description in output_files:
        if os.path.exists(filepath):
            size = os.path.getsize(filepath)
            print(f"  ✅ {description}: {filepath} ({size:,} 字节)")
        else:
            print(f"  ❌ {description}: {filepath} (未生成)")
    
    # 检查可视化文件
    viz_dir = "visualizations"
    if os.path.exists(viz_dir):
        charts = [f for f in os.listdir(viz_dir) if f.endswith('.png')]
        print(f"\n📈 可视化图表 ({len(charts)} 个):")
        for chart in charts:
            print(f"  ✅ {chart}")
    
    # 检查数据文件
    data_dir = "decompressed_data_report"
    if os.path.exists(data_dir):
        data_files = [f for f in os.listdir(data_dir) if f.endswith('.json')]
        print(f"\n📦 数据文件 ({len(data_files)} 个):")
        print(f"  📁 位置: {data_dir}/")
    
    print("\n" + "="*80)
    print("🎉 所有分析任务已完成！")
    print("="*80)

def main():
    """主函数"""
    start_time = time.time()
    
    # 打印横幅
    print_banner()
    
    # 检查依赖
    if not check_dependencies():
        print("\n❌ 依赖检查失败，请先安装依赖包")
        sys.exit(1)
    
    # 创建目录
    create_directories()
    
    # 运行分析
    if not run_analysis():
        print("\n❌ 分析失败")
        sys.exit(1)
    
    # 显示结果
    show_results()
    
    # 计算耗时
    end_time = time.time()
    duration = end_time - start_time
    print(f"\n⏱️  总耗时: {duration:.2f} 秒")
    
    print("\n💡 提示:")
    print("  • 查看详细报告: output/analysis_report.md")
    print("  • 查看可视化图表: visualizations/ 目录")
    print("  • 查看原始数据: decompressed_data_report/ 目录")
    print("  • 重新运行分析: python run_all.py")

if __name__ == "__main__":
    main()

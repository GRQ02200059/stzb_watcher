#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
启动实时数据抓取和分析 - 详细日志版本
"""

import os
import sys
import time
from datetime import datetime

def print_startup_info():
    """打印启动信息"""
    print("🚀 启动游戏数据抓取和分析系统 - 详细日志版")
    print("="*70)
    print("功能特性:")
    print("  ✓ 实时网络数据抓取")
    print("  ✓ 详细抓取日志显示")
    print("  ✓ 自动解压缩和解析")
    print("  ✓ 实时数据分类和分析")
    print("  ✓ 实时生成结果文件")
    print("  ✓ 自动生成可视化图表")
    print("="*70)
    print(f"启动时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("="*70)

def check_dependencies():
    """检查依赖"""
    print("🔍 检查系统依赖...")
    
    try:
        import pandas
        print("  ✅ pandas - 数据处理")
    except ImportError:
        print("  ❌ pandas - 请安装: pip install pandas")
        return False
    
    try:
        import matplotlib
        print("  ✅ matplotlib - 图表生成")
    except ImportError:
        print("  ❌ matplotlib - 请安装: pip install matplotlib")
        return False
    
    try:
        import scapy
        print("  ✅ scapy - 网络抓包")
    except ImportError:
        print("  ❌ scapy - 请安装: pip install scapy")
        return False
    
    try:
        import seaborn
        print("  ✅ seaborn - 高级图表")
    except ImportError:
        print("  ❌ seaborn - 请安装: pip install seaborn")
        return False
    
    print("✅ 所有依赖检查通过")
    return True

def main():
    """主函数"""
    print_startup_info()
    
    # 检查依赖
    if not check_dependencies():
        print("\n❌ 依赖检查失败，请先安装缺少的依赖包")
        print("可以运行: python install_dependencies.py")
        return
    
    print("\n🎯 准备启动实时抓取...")
    print("💡 提示: 按 Ctrl+C 停止抓取")
    print("📋 日志说明:")
    print("  [🔍] 检测到数据包")
    print("  [📦] 解压缩数据")
    print("  [📄] 解析JSON数据")
    print("  [🏷️] 识别消息类型")
    print("  [🔬] 开始数据分析")
    print("  [⚔️] 战斗数据分析")
    print("  [🏆] 排行榜数据分析")
    print("  [⚙️] 系统数据分析")
    print("  [💾] 保存结果文件")
    print("  [✅] 处理完成")
    print("  [❌] 处理失败")
    print("-" * 70)
    
    # 等待用户确认
    input("按回车键开始抓取...")
    
    try:
        # 导入并启动集成版抓取器
        from integrated_scraper import IntegratedGameScraper
        
        # 创建抓取器
        scraper = IntegratedGameScraper()
        
        # 开始抓取
        scraper.start_capture()
        
    except KeyboardInterrupt:
        print("\n👋 用户停止抓取")
    except Exception as e:
        print(f"\n❌ 启动失败: {str(e)}")
        print("请检查网络连接和权限设置")

if __name__ == "__main__":
    main()



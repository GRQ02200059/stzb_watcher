#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
启动实时游戏数据抓取和分析系统
一键启动完整系统
"""

import os
import sys
import time
from datetime import datetime

def print_system_info():
    """打印系统信息"""
    print("🚀 实时游戏数据抓取和分析系统")
    print("="*70)
    print("系统特性:")
    print("  ✓ 实时网络数据抓取")
    print("  ✓ 详细实时日志记录")
    print("  ✓ 实时数据分析和分类")
    print("  ✓ 实时结果文件更新")
    print("  ✓ 实时可视化图表")
    print("  ✓ 多线程并发处理")
    print("  ✓ 自动保存和备份")
    print("="*70)
    print(f"启动时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("="*70)

def check_dependencies():
    """检查依赖"""
    print("🔍 检查系统依赖...")
    
    required_packages = [
        ('pandas', '数据处理'),
        ('matplotlib', '图表生成'),
        ('seaborn', '高级图表'),
        ('scapy', '网络抓包')
    ]
    
    missing_packages = []
    
    for package, description in required_packages:
        try:
            __import__(package)
            print(f"  ✅ {package} - {description}")
        except ImportError:
            missing_packages.append(package)
            print(f"  ❌ {package} - {description}")
    
    if missing_packages:
        print(f"\n⚠️  缺少依赖包: {', '.join(missing_packages)}")
        print("请运行: python install_dependencies.py")
        return False
    
    print("✅ 所有依赖检查通过")
    return True

def create_directories():
    """创建必要目录"""
    print("📁 创建系统目录...")
    
    directories = [
        "decompressed_data_report",
        "output",
        "visualizations",
        "logs"
    ]
    
    for directory in directories:
        os.makedirs(directory, exist_ok=True)
        print(f"  ✅ {directory}/")
    
    print("✅ 目录创建完成")

def main():
    """主函数"""
    print_system_info()
    
    # 检查依赖
    if not check_dependencies():
        print("\n❌ 依赖检查失败，请先安装缺少的依赖包")
        return
    
    # 创建目录
    create_directories()
    
    print("\n🎯 准备启动实时系统...")
    print("💡 提示: 按 Ctrl+C 停止系统")
    print("📋 日志说明:")
    print("  [INFO] 一般信息")
    print("  [SUCCESS] 操作成功")
    print("  [ERROR] 操作失败")
    print("  [BATTLE] 战斗数据")
    print("  [RANK] 排行榜数据")
    print("-" * 70)
    
    # 等待用户确认
    input("按回车键开始实时抓取...")
    
    try:
        # 导入并启动实时系统
        from realtime_system import RealtimeGameSystem
        
        # 创建系统
        system = RealtimeGameSystem()
        
        # 启动系统
        system.start_realtime_system()
        
    except KeyboardInterrupt:
        print("\n👋 用户停止系统")
    except Exception as e:
        print(f"\n❌ 系统启动失败: {str(e)}")
        print("请检查网络连接和权限设置")

if __name__ == "__main__":
    main()
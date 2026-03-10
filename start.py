#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
一键启动实时数据抓取和分析
最简单的启动方式
"""

import os
import sys

def main():
    """主函数"""
    print("🚀 启动游戏数据抓取和分析系统")
    print("="*50)
    
    # 检查依赖
    try:
        import pandas
        import matplotlib
        import scapy
        print("✅ 依赖检查通过")
    except ImportError as e:
        print(f"❌ 缺少依赖: {e}")
        print("请运行: python install_dependencies.py")
        return
    
    # 直接启动集成版抓取器
    from integrated_scraper import IntegratedGameScraper
    
    print("\n🎯 开始实时抓取和分析...")
    print("💡 提示: 按 Ctrl+C 停止")
    print("-" * 50)
    
    # 创建抓取器并开始
    scraper = IntegratedGameScraper()
    scraper.start_capture()

if __name__ == "__main__":
    main()



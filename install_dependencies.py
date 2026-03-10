#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
安装依赖包脚本
"""

import subprocess
import sys
import os

def install_package(package):
    """安装单个包"""
    try:
        subprocess.check_call([sys.executable, "-m", "pip", "install", package])
        print(f"✅ {package} 安装成功")
        return True
    except subprocess.CalledProcessError:
        print(f"❌ {package} 安装失败")
        return False

def main():
    """主函数"""
    print("🔧 安装游戏数据分析系统依赖包")
    print("="*50)
    
    # 检查Python版本
    if sys.version_info < (3, 7):
        print("❌ 需要Python 3.7或更高版本")
        sys.exit(1)
    
    print(f"Python版本: {sys.version}")
    print()
    
    # 要安装的包
    packages = [
        "scapy>=2.4.5",
        "matplotlib>=3.5.0", 
        "pandas>=1.3.0",
        "seaborn>=0.11.0",
        "numpy>=1.21.0"
    ]
    
    success_count = 0
    
    for package in packages:
        print(f"正在安装 {package}...")
        if install_package(package):
            success_count += 1
        print()
    
    print("="*50)
    print(f"安装完成: {success_count}/{len(packages)} 个包安装成功")
    
    if success_count == len(packages):
        print("✅ 所有依赖包安装成功！")
        print("现在可以运行 main_interface.py 开始使用系统")
    else:
        print("⚠️  部分包安装失败，请手动安装")
        print("可以运行: pip install -r requirements.txt")

if __name__ == "__main__":
    main()



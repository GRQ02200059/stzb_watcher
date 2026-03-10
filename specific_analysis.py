#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分析具体消息类型的数据内容
"""

import json
import os

def analyze_000000ca():
    """分析000000ca类型数据"""
    file_path = "decompressed_data_report/000000ca/decompressed_20251004202524525_000000ca.json"
    
    with open(file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    print("000000ca 数据分析:")
    print(f"数据长度: {len(data)}")
    
    if len(data) > 0:
        print(f"第一个元素类型: {type(data[0])}")
        print(f"第一个元素长度: {len(data[0])}")
        print(f"第一个元素前5项: {data[0][:5]}")
    
    if len(data) > 1:
        print(f"第二个元素类型: {type(data[1])}")
        print(f"第二个元素长度: {len(data[1])}")
        print(f"第二个元素前5项: {data[1][:5]}")

def analyze_000002c7():
    """分析000002c7类型数据"""
    file_path = "decompressed_data_report/000002c7/decompressed_20251004173941782_000002c7.json"
    
    with open(file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    print("\n000002c7 数据分析:")
    print(f"数据长度: {len(data)}")
    
    if len(data) > 0:
        print(f"第一个元素类型: {type(data[0])}")
        print(f"第一个元素长度: {len(data[0])}")
        if len(data[0]) > 0:
            print(f"第一个子元素类型: {type(data[0][0])}")
            print(f"第一个子元素长度: {len(data[0][0])}")
            if len(data[0][0]) > 0:
                print(f"第一个子子元素: {data[0][0][0]}")

def analyze_00015f95():
    """分析00015f95类型数据"""
    file_path = "decompressed_data_report/00015f95/decompressed_20251004194843239_00015f95.json"
    
    with open(file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    print("\n00015f95 数据分析:")
    print(f"数据长度: {len(data)}")
    
    if len(data) > 0:
        print(f"第一个元素类型: {type(data[0])}")
        print(f"第一个元素长度: {len(data[0])}")
        print(f"第一个元素内容: {data[0]}")

def analyze_000002bc():
    """分析000002bc类型数据"""
    file_path = "decompressed_data_report/000002bc/decompressed_20251004165534099_000002bc.json"
    
    with open(file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    print("\n000002bc 数据分析:")
    print(f"数据长度: {len(data)}")
    
    for i, item in enumerate(data):
        print(f"元素{i}: {item}")

def analyze_00000e66():
    """分析00000e66类型数据"""
    file_path = "decompressed_data_report/00000e66/decompressed_20251004173650427_00000e66.json"
    
    with open(file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    print("\n00000e66 数据分析:")
    print(f"数据长度: {len(data)}")
    
    for i, item in enumerate(data):
        print(f"元素{i}: {item}")

def main():
    """主函数"""
    print("分析具体消息类型数据...")
    
    analyze_000000ca()
    analyze_000002c7()
    analyze_00015f95()
    analyze_000002bc()
    analyze_00000e66()
    
    print("\n分析完成!")

if __name__ == "__main__":
    main()




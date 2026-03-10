#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
详细分析抓取到的游戏数据内容
"""

import json
import os
from collections import defaultdict

def analyze_specific_files():
    """分析特定的数据文件"""
    data_dir = "decompressed_data_report"
    
    # 分析几个代表性的文件
    sample_files = [
        "decompressed_20251004155231057.json",  # 排行榜数据
        "decompressed_20251004155128994.json",  # 大文件
        "decompressed_20251004155123642.json",  # 小文件
        "decompressed_20251004155201458.json",  # 数值数据
    ]
    
    for filename in sample_files:
        filepath = os.path.join(data_dir, filename)
        if not os.path.exists(filepath):
            continue
            
        print(f"\n{'='*80}")
        print(f"分析文件: {filename}")
        print(f"文件大小: {os.path.getsize(filepath):,} 字节")
        print(f"{'='*80}")
        
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            print(f"数据类型: {type(data)}")
            
            if isinstance(data, list):
                print(f"数组长度: {len(data)}")
                
                if len(data) > 0:
                    first_item = data[0]
                    print(f"第一个元素类型: {type(first_item)}")
                    
                    if isinstance(first_item, list):
                        print(f"第一个元素长度: {len(first_item)}")
                        print(f"第一个元素内容: {first_item[:10]}...")  # 前10个元素
                        
                        # 分析数组结构
                        if len(first_item) > 0:
                            print(f"元素类型分析:")
                            for i, item in enumerate(first_item[:5]):
                                print(f"  [{i}]: {type(item)} = {item}")
                    
                    elif isinstance(first_item, dict):
                        print(f"第一个元素键: {list(first_item.keys())[:10]}")
                        print(f"第一个元素内容: {first_item}")
                    
                    elif isinstance(first_item, str):
                        print(f"第一个元素内容: {first_item[:200]}...")
                    
                    else:
                        print(f"第一个元素内容: {first_item}")
                
                # 分析数组中的数据类型分布
                if len(data) > 1:
                    type_counts = defaultdict(int)
                    for item in data[:100]:  # 只分析前100个
                        type_counts[type(item).__name__] += 1
                    
                    print(f"数据类型分布 (前100个): {dict(type_counts)}")
            
            elif isinstance(data, dict):
                print(f"对象键数量: {len(data)}")
                print(f"键列表: {list(data.keys())[:10]}")
                
                # 显示前几个键值对
                for i, (key, value) in enumerate(list(data.items())[:3]):
                    print(f"  {key}: {type(value)} = {str(value)[:100]}...")
        
        except Exception as e:
            print(f"分析文件时出错: {e}")

def analyze_message_types():
    """分析消息类型模式"""
    print(f"\n{'='*80}")
    print("消息类型分析")
    print(f"{'='*80}")
    
    # 从文件名中提取时间戳，分析消息类型
    data_dir = "decompressed_data_report"
    files = [f for f in os.listdir(data_dir) if f.endswith('.json')]
    
    # 按时间分组
    time_groups = defaultdict(list)
    for filename in files:
        if 'decompressed_' in filename:
            timestamp = filename.replace('decompressed_', '').replace('.json', '')
            time_groups[timestamp[:8]].append(filename)  # 按日期分组
    
    print(f"按日期分组的文件数量:")
    for date, file_list in sorted(time_groups.items()):
        print(f"  {date}: {len(file_list)} 个文件")
    
    # 分析文件大小模式
    print(f"\n文件大小模式分析:")
    size_patterns = defaultdict(list)
    
    for filename in files:
        filepath = os.path.join(data_dir, filename)
        size = os.path.getsize(filepath)
        
        if size < 10000:
            size_patterns['small'].append(filename)
        elif size < 100000:
            size_patterns['medium'].append(filename)
        else:
            size_patterns['large'].append(filename)
    
    for pattern, file_list in size_patterns.items():
        print(f"  {pattern} (<10KB: {len([f for f in file_list if os.path.getsize(os.path.join(data_dir, f)) < 10000])}, "
              f"10-100KB: {len([f for f in file_list if 10000 <= os.path.getsize(os.path.join(data_dir, f)) < 100000])}, "
              f">100KB: {len([f for f in file_list if os.path.getsize(os.path.join(data_dir, f)) >= 100000])})")

def main():
    analyze_specific_files()
    analyze_message_types()

if __name__ == "__main__":
    main()



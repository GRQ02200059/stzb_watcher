#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分析抓取到的游戏数据类型和内容
"""

import json
import os
from collections import defaultdict, Counter
import re

def analyze_json_file(filepath):
    """分析单个JSON文件"""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        analysis = {
            'file_size': os.path.getsize(filepath),
            'data_type': 'unknown',
            'structure': 'unknown',
            'key_fields': [],
            'sample_data': None
        }
        
        if isinstance(data, list):
            analysis['structure'] = 'array'
            if len(data) > 0:
                first_item = data[0]
                if isinstance(first_item, dict):
                    analysis['data_type'] = 'object_array'
                    analysis['key_fields'] = list(first_item.keys())[:10]  # 前10个字段
                elif isinstance(first_item, list):
                    analysis['data_type'] = 'nested_array'
                    analysis['key_fields'] = [f"item_{i}" for i in range(min(5, len(first_item)))]
                else:
                    analysis['data_type'] = 'primitive_array'
                    analysis['key_fields'] = [f"value_{i}" for i in range(min(5, len(data)))]
                
                # 保存样本数据
                analysis['sample_data'] = data[:3] if len(data) > 3 else data
        elif isinstance(data, dict):
            analysis['structure'] = 'object'
            analysis['data_type'] = 'object'
            analysis['key_fields'] = list(data.keys())[:10]
            analysis['sample_data'] = {k: v for k, v in list(data.items())[:3]}
        
        return analysis
    except Exception as e:
        return {'error': str(e), 'file_size': os.path.getsize(filepath)}

def detect_data_type_by_content(data):
    """根据内容特征检测数据类型"""
    if isinstance(data, list) and len(data) > 0:
        first_item = data[0]
        
        # 检测排行榜数据
        if isinstance(first_item, list) and len(first_item) > 5:
            if any(isinstance(x, str) and ('server_name' in str(x) or 'role_name' in str(x)) for x in first_item):
                return 'rank_list'
            elif any(isinstance(x, (int, float)) and x > 1000 for x in first_item):
                return 'numeric_data'
        
        # 检测数据库表结构
        if isinstance(first_item, str) and 'Tb_' in first_item:
            return 'database_schema'
        
        # 检测用户数据
        if isinstance(first_item, dict):
            if 'userid' in first_item or 'role_id' in first_item:
                return 'user_data'
            elif 'battle_id' in first_item or 'armyid' in first_item:
                return 'battle_data'
            elif 'wid' in first_item or 'city_wid' in first_item:
                return 'world_data'
    
    return 'unknown'

def main():
    """主分析函数"""
    data_dir = "decompressed_data_report"
    
    if not os.path.exists(data_dir):
        print(f"目录 {data_dir} 不存在")
        return
    
    # 收集所有JSON文件
    json_files = [f for f in os.listdir(data_dir) if f.endswith('.json')]
    print(f"发现 {len(json_files)} 个JSON文件")
    
    # 分析每个文件
    file_analyses = {}
    data_type_stats = Counter()
    size_ranges = defaultdict(int)
    
    for filename in sorted(json_files):
        filepath = os.path.join(data_dir, filename)
        print(f"\n分析文件: {filename}")
        
        analysis = analyze_json_file(filepath)
        file_analyses[filename] = analysis
        
        if 'error' not in analysis:
            # 检测数据类型
            detected_type = detect_data_type_by_content(analysis.get('sample_data', []))
            analysis['detected_type'] = detected_type
            data_type_stats[detected_type] += 1
            
            # 文件大小分类
            size = analysis['file_size']
            if size < 1024:
                size_ranges['<1KB'] += 1
            elif size < 10240:
                size_ranges['1-10KB'] += 1
            elif size < 102400:
                size_ranges['10-100KB'] += 1
            elif size < 1048576:
                size_ranges['100KB-1MB'] += 1
            else:
                size_ranges['>1MB'] += 1
            
            print(f"  类型: {detected_type}")
            print(f"  大小: {size:,} 字节")
            print(f"  结构: {analysis['structure']}")
            if analysis['key_fields']:
                print(f"  字段: {analysis['key_fields'][:5]}")
        else:
            print(f"  错误: {analysis['error']}")
    
    # 生成统计报告
    print("\n" + "="*60)
    print("数据类型统计:")
    print("="*60)
    for data_type, count in data_type_stats.most_common():
        print(f"{data_type}: {count} 个文件")
    
    print("\n文件大小分布:")
    print("="*60)
    for size_range, count in sorted(size_ranges.items()):
        print(f"{size_range}: {count} 个文件")
    
    # 详细分析每种数据类型
    print("\n" + "="*60)
    print("详细数据分析:")
    print("="*60)
    
    type_groups = defaultdict(list)
    for filename, analysis in file_analyses.items():
        if 'detected_type' in analysis:
            type_groups[analysis['detected_type']].append((filename, analysis))
    
    for data_type, files in type_groups.items():
        print(f"\n【{data_type}】类型文件 ({len(files)} 个):")
        print("-" * 40)
        
        for filename, analysis in files[:5]:  # 只显示前5个
            print(f"  {filename}:")
            print(f"    大小: {analysis['file_size']:,} 字节")
            print(f"    结构: {analysis['structure']}")
            if analysis['key_fields']:
                print(f"    字段: {analysis['key_fields'][:3]}")
        
        if len(files) > 5:
            print(f"  ... 还有 {len(files) - 5} 个文件")

if __name__ == "__main__":
    main()



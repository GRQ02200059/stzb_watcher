#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分析所有抓取数据中的消息类型和对应内容
"""

import json
import os
import re
from collections import defaultdict, Counter
from datetime import datetime

def extract_message_type_from_filename(filename):
    """从文件名中提取消息类型信息"""
    # 文件名格式: decompressed_YYYYMMDDHHMMSS.json
    # 我们需要从scrapy.py的日志中获取消息类型
    # 但这里我们可以通过文件大小和内容特征来推断
    return None

def analyze_file_content(filepath):
    """分析文件内容，推断消息类型"""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        analysis = {
            'file_size': os.path.getsize(filepath),
            'data_type': type(data).__name__,
            'content_type': 'unknown',
            'key_features': [],
            'sample_data': None
        }
        
        if isinstance(data, list):
            analysis['array_length'] = len(data)
            
            if len(data) > 0:
                first_item = data[0]
                analysis['first_item_type'] = type(first_item).__name__
                
                # 根据内容特征推断消息类型
                if isinstance(first_item, dict):
                    if 'battle_id' in first_item:
                        analysis['content_type'] = 'battle_data'
                        analysis['key_features'] = ['battle_id', 'attack_name', 'defend_name', 'result']
                    elif 'userid' in first_item or 'role_id' in first_item:
                        analysis['content_type'] = 'user_data'
                        analysis['key_features'] = ['userid', 'role_id', 'name']
                    elif 'wid' in first_item or 'city_wid' in first_item:
                        analysis['content_type'] = 'world_data'
                        analysis['key_features'] = ['wid', 'city_type', 'durability']
                    else:
                        analysis['content_type'] = 'generic_object_data'
                        analysis['key_features'] = list(first_item.keys())[:5]
                
                elif isinstance(first_item, list):
                    if len(first_item) > 5:
                        # 可能是排行榜或列表数据
                        if any(isinstance(x, str) and ('server_name' in str(x) or 'role_name' in str(x)) for x in first_item):
                            analysis['content_type'] = 'rank_data'
                            analysis['key_features'] = ['rank_list', 'player_info']
                        elif any(isinstance(x, (int, float)) and x > 1000 for x in first_item):
                            analysis['content_type'] = 'numeric_data'
                            analysis['key_features'] = ['numeric_values']
                        else:
                            analysis['content_type'] = 'array_data'
                            analysis['key_features'] = ['nested_arrays']
                    else:
                        analysis['content_type'] = 'simple_array'
                        analysis['key_features'] = ['simple_values']
                
                elif isinstance(first_item, str):
                    if 'Tb_' in first_item:
                        analysis['content_type'] = 'database_schema'
                        analysis['key_features'] = ['table_definitions']
                    else:
                        analysis['content_type'] = 'string_data'
                        analysis['key_features'] = ['text_content']
                
                elif isinstance(first_item, (int, float)):
                    analysis['content_type'] = 'numeric_data'
                    analysis['key_features'] = ['numbers']
                
                # 保存样本数据
                if isinstance(first_item, dict):
                    analysis['sample_data'] = {k: v for k, v in list(first_item.items())[:3]}
                elif isinstance(first_item, list):
                    analysis['sample_data'] = first_item[:3]
                else:
                    analysis['sample_data'] = first_item
        
        elif isinstance(data, dict):
            analysis['object_keys'] = len(data)
            analysis['content_type'] = 'object_data'
            analysis['key_features'] = list(data.keys())[:5]
            analysis['sample_data'] = {k: v for k, v in list(data.items())[:3]}
        
        return analysis
    
    except Exception as e:
        return {
            'file_size': os.path.getsize(filepath),
            'error': str(e),
            'content_type': 'error'
        }

def analyze_all_files():
    """分析所有文件的消息类型"""
    data_dir = "decompressed_data_report"
    
    if not os.path.exists(data_dir):
        print(f"目录 {data_dir} 不存在")
        return
    
    # 获取所有JSON文件
    files = [f for f in os.listdir(data_dir) if f.endswith('.json')]
    print(f"发现 {len(files)} 个JSON文件")
    
    # 分析每个文件
    file_analyses = {}
    content_type_stats = Counter()
    size_patterns = defaultdict(list)
    
    for filename in sorted(files):
        filepath = os.path.join(data_dir, filename)
        print(f"分析文件: {filename}")
        
        analysis = analyze_file_content(filepath)
        file_analyses[filename] = analysis
        
        if 'error' not in analysis:
            content_type_stats[analysis['content_type']] += 1
            
            # 按大小分类
            size = analysis['file_size']
            if size < 10000:
                size_patterns['small'].append(filename)
            elif size < 100000:
                size_patterns['medium'].append(filename)
            else:
                size_patterns['large'].append(filename)
    
    return file_analyses, content_type_stats, size_patterns

def print_analysis_report(file_analyses, content_type_stats, size_patterns):
    """打印分析报告"""
    print("\n" + "="*80)
    print("消息类型分析报告")
    print("="*80)
    
    # 内容类型统计
    print(f"\n【内容类型统计】")
    print("-" * 40)
    for content_type, count in content_type_stats.most_common():
        print(f"{content_type}: {count} 个文件")
    
    # 文件大小分布
    print(f"\n【文件大小分布】")
    print("-" * 40)
    for size_type, file_list in size_patterns.items():
        print(f"{size_type}: {len(file_list)} 个文件")
    
    # 按内容类型详细分析
    print(f"\n【详细内容分析】")
    print("-" * 40)
    
    type_groups = defaultdict(list)
    for filename, analysis in file_analyses.items():
        if 'content_type' in analysis:
            type_groups[analysis['content_type']].append((filename, analysis))
    
    for content_type, files in type_groups.items():
        print(f"\n【{content_type}】类型文件 ({len(files)} 个):")
        print("-" * 60)
        
        for filename, analysis in files[:5]:  # 只显示前5个
            print(f"  {filename}:")
            print(f"    大小: {analysis['file_size']:,} 字节")
            
            if 'array_length' in analysis:
                print(f"    数组长度: {analysis['array_length']}")
            
            if 'first_item_type' in analysis:
                print(f"    元素类型: {analysis['first_item_type']}")
            
            if analysis['key_features']:
                print(f"    关键特征: {analysis['key_features'][:3]}")
            
            if analysis['sample_data']:
                sample_str = str(analysis['sample_data'])
                if len(sample_str) > 100:
                    sample_str = sample_str[:100] + "..."
                print(f"    样本数据: {sample_str}")
            
            print()
        
        if len(files) > 5:
            print(f"  ... 还有 {len(files) - 5} 个文件")

def analyze_message_patterns():
    """分析消息模式"""
    print(f"\n【消息模式分析】")
    print("-" * 40)
    
    # 分析文件名模式
    data_dir = "decompressed_data_report"
    files = [f for f in os.listdir(data_dir) if f.endswith('.json')]
    
    # 按时间分组
    time_groups = defaultdict(list)
    for filename in files:
        if 'decompressed_' in filename:
            timestamp = filename.replace('decompressed_', '').replace('.json', '')
            date = timestamp[:8]  # YYYYMMDD
            time_groups[date].append(filename)
    
    print(f"按日期分组的文件数量:")
    for date, file_list in sorted(time_groups.items()):
        print(f"  {date}: {len(file_list)} 个文件")
    
    # 分析文件大小模式
    print(f"\n文件大小模式:")
    size_ranges = {
        '<10KB': 0,
        '10-100KB': 0,
        '>100KB': 0
    }
    
    for filename in files:
        filepath = os.path.join(data_dir, filename)
        size = os.path.getsize(filepath)
        
        if size < 10000:
            size_ranges['<10KB'] += 1
        elif size < 100000:
            size_ranges['10-100KB'] += 1
        else:
            size_ranges['>100KB'] += 1
    
    for range_name, count in size_ranges.items():
        print(f"  {range_name}: {count} 个文件")

def main():
    """主函数"""
    print("开始分析所有消息类型...")
    
    file_analyses, content_type_stats, size_patterns = analyze_all_files()
    
    print_analysis_report(file_analyses, content_type_stats, size_patterns)
    analyze_message_patterns()
    
    print(f"\n分析完成！")

if __name__ == "__main__":
    main()



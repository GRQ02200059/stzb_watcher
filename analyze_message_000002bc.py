#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
专门分析消息类型 000002bc 对应的数据
"""

import json
import os
import re
from collections import defaultdict

def analyze_000002bc_data():
    """分析消息类型 000002bc 的数据"""
    data_dir = "decompressed_data_report"
    
    print("="*80)
    print("消息类型 000002bc 数据分析")
    print("="*80)
    
    # 查找所有相关文件
    files = [f for f in os.listdir(data_dir) if f.endswith('.json')]
    
    print(f"总共找到 {len(files)} 个JSON文件")
    
    # 分析每个文件的内容特征
    data_categories = {
        'rank_data': [],      # 排行榜数据
        'user_data': [],      # 用户数据
        'battle_data': [],    # 战斗数据
        'world_data': [],     # 世界数据
        'system_data': [],    # 系统数据
        'unknown': []         # 未知数据
    }
    
    for filename in sorted(files):
        filepath = os.path.join(data_dir, filename)
        
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            category = categorize_data(data, filename)
            data_categories[category].append((filename, data))
            
        except Exception as e:
            print(f"处理文件 {filename} 时出错: {e}")
    
    # 输出分类结果
    for category, file_list in data_categories.items():
        if file_list:
            print(f"\n【{category}】类型文件 ({len(file_list)} 个):")
            print("-" * 60)
            
            for filename, data in file_list[:3]:  # 只显示前3个
                print(f"  {filename}:")
                print(f"    大小: {os.path.getsize(os.path.join(data_dir, filename)):,} 字节")
                print(f"    结构: {get_data_structure(data)}")
                
                # 显示样本数据
                sample = get_sample_data(data)
                if sample:
                    print(f"    样本: {sample}")
                print()

def categorize_data(data, filename):
    """根据数据内容分类"""
    if not isinstance(data, list):
        return 'unknown'
    
    if len(data) == 0:
        return 'unknown'
    
    first_item = data[0]
    
    # 检测排行榜数据
    if isinstance(first_item, list) and len(first_item) > 5:
        if any(isinstance(x, str) and ('server_name' in str(x) or 'role_name' in str(x) or 'family_name' in str(x)) for x in first_item):
            return 'rank_data'
        elif any(isinstance(x, (int, float)) and x > 1000 for x in first_item):
            return 'rank_data'
    
    # 检测用户数据
    if isinstance(first_item, dict):
        if 'userid' in first_item or 'role_id' in first_item:
            return 'user_data'
        elif 'battle_id' in first_item or 'armyid' in first_item:
            return 'battle_data'
        elif 'wid' in first_item or 'city_wid' in first_item:
            return 'world_data'
    
    # 检测系统数据
    if isinstance(first_item, str) and 'Tb_' in first_item:
        return 'system_data'
    
    # 检测数值数据
    if isinstance(first_item, (int, float)):
        return 'rank_data'
    
    return 'unknown'

def get_data_structure(data):
    """获取数据结构描述"""
    if isinstance(data, list):
        if len(data) == 0:
            return "空数组"
        elif len(data) == 1:
            return f"单元素数组 [{type(data[0]).__name__}]"
        else:
            return f"数组 [{len(data)} 个元素, 类型: {type(data[0]).__name__}]"
    elif isinstance(data, dict):
        return f"对象 [{len(data)} 个键]"
    else:
        return f"原始类型 [{type(data).__name__}]"

def get_sample_data(data):
    """获取样本数据"""
    if isinstance(data, list) and len(data) > 0:
        first_item = data[0]
        if isinstance(first_item, list):
            return first_item[:5]  # 前5个元素
        elif isinstance(first_item, dict):
            return {k: v for k, v in list(first_item.items())[:3]}  # 前3个键值对
        else:
            return first_item
    elif isinstance(data, dict):
        return {k: v for k, v in list(data.items())[:3]}
    else:
        return data

def analyze_rank_data():
    """专门分析排行榜数据"""
    print("\n" + "="*80)
    print("排行榜数据详细分析")
    print("="*80)
    
    data_dir = "decompressed_data_report"
    files = [f for f in os.listdir(data_dir) if f.endswith('.json')]
    
    for filename in files:
        filepath = os.path.join(data_dir, filename)
        
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            # 检查是否是排行榜数据
            if isinstance(data, list) and len(data) > 0:
                first_item = data[0]
                if isinstance(first_item, list) and len(first_item) > 5:
                    # 可能是排行榜数据
                    print(f"\n文件: {filename}")
                    print(f"大小: {os.path.getsize(filepath):,} 字节")
                    print(f"记录数: {len(data)}")
                    
                    # 分析第一个记录
                    print(f"第一个记录: {first_item[:10]}...")
                    
                    # 尝试识别字段含义
                    if len(first_item) >= 10:
                        print(f"可能的字段含义:")
                        print(f"  [0] ID: {first_item[0]}")
                        print(f"  [1] 名称: {first_item[1]}")
                        print(f"  [2] 类型: {first_item[2]}")
                        print(f"  [3] 等级: {first_item[3]}")
                        print(f"  [4] 排名: {first_item[4]}")
                        print(f"  [5] 数值: {first_item[5]}")
                        print(f"  [6-9] 其他: {first_item[6:10]}")
                    
                    # 只分析前3个文件
                    break
                    
        except Exception as e:
            continue

def main():
    analyze_000002bc_data()
    analyze_rank_data()

if __name__ == "__main__":
    main()



#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
详细分析消息类型 000002bc 对应的具体数据内容
"""

import json
import os
from collections import defaultdict

def analyze_message_000002bc():
    """分析消息类型 000002bc 的详细数据"""
    data_dir = "decompressed_data_report"
    
    print("="*80)
    print("消息类型 000002bc 详细数据分析")
    print("="*80)
    
    # 获取所有文件
    files = [f for f in os.listdir(data_dir) if f.endswith('.json')]
    
    # 按文件大小分类
    small_files = []    # <10KB
    medium_files = []   # 10-100KB  
    large_files = []    # >100KB
    
    for filename in files:
        filepath = os.path.join(data_dir, filename)
        size = os.path.getsize(filepath)
        
        if size < 10000:
            small_files.append(filename)
        elif size < 100000:
            medium_files.append(filename)
        else:
            large_files.append(filename)
    
    print(f"文件分类:")
    print(f"  小文件 (<10KB): {len(small_files)} 个")
    print(f"  中文件 (10-100KB): {len(medium_files)} 个") 
    print(f"  大文件 (>100KB): {len(large_files)} 个")
    
    # 分析每种类型的代表文件
    print(f"\n{'='*60}")
    print("小文件分析 (可能是系统消息或简单数据)")
    print(f"{'='*60}")
    
    for filename in small_files[:3]:  # 只分析前3个
        analyze_single_file(data_dir, filename)
    
    print(f"\n{'='*60}")
    print("中文件分析 (可能是战斗数据)")
    print(f"{'='*60}")
    
    for filename in medium_files[:3]:  # 只分析前3个
        analyze_single_file(data_dir, filename)
    
    print(f"\n{'='*60}")
    print("大文件分析 (可能是排行榜或系统数据)")
    print(f"{'='*60}")
    
    for filename in large_files:
        analyze_single_file(data_dir, filename)

def analyze_single_file(data_dir, filename):
    """分析单个文件"""
    filepath = os.path.join(data_dir, filename)
    size = os.path.getsize(filepath)
    
    print(f"\n文件: {filename}")
    print(f"大小: {size:,} 字节")
    print("-" * 40)
    
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        print(f"数据类型: {type(data)}")
        
        if isinstance(data, list):
            print(f"数组长度: {len(data)}")
            
            if len(data) > 0:
                first_item = data[0]
                print(f"第一个元素类型: {type(first_item)}")
                
                if isinstance(first_item, dict):
                    print("  → 这是对象数组")
                    print(f"    对象键数量: {len(first_item)}")
                    print(f"    主要键: {list(first_item.keys())[:5]}")
                    
                    # 根据键名判断数据类型
                    if 'battle_id' in first_item:
                        print("    【战斗数据】")
                        print(f"      战斗ID: {first_item.get('battle_id')}")
                        print(f"      攻击方: {first_item.get('attack_name')} ({first_item.get('attack_union_name')})")
                        print(f"      防守方: {first_item.get('defend_name')} ({first_item.get('defend_union_name')})")
                        print(f"      结果: {'胜利' if first_item.get('result') == 1 else '失败'}")
                        print(f"      时间: {first_item.get('time')}")
                        print(f"      位置: {first_item.get('wid_name')} (ID: {first_item.get('wid')})")
                    
                    elif 'userid' in first_item:
                        print("    【用户数据】")
                        print(f"      用户ID: {first_item.get('userid')}")
                        print(f"      角色名: {first_item.get('name', first_item.get('role_name'))}")
                    
                    else:
                        print("    【其他对象数据】")
                        print(f"      样本数据: {str(first_item)[:200]}...")
                
                elif isinstance(first_item, list):
                    print("  → 这是嵌套数组")
                    print(f"    内层数组长度: {len(first_item)}")
                    print(f"    内层数组内容: {first_item[:5]}")
                    
                    # 分析内层数组内容
                    if len(first_item) > 5:
                        print("    【可能是排行榜数据】")
                        if any(isinstance(x, str) for x in first_item):
                            print("      包含字符串数据")
                        if any(isinstance(x, (int, float)) and x > 1000 for x in first_item):
                            print("      包含大数值")
                    
                    elif len(first_item) <= 5:
                        print("    【简单数组数据】")
                        print(f"      内容: {first_item}")
                
                elif isinstance(first_item, (int, float)):
                    print("  → 这是数值数组")
                    print(f"    数值范围: {min(data[:10])} - {max(data[:10])}")
                    print(f"    前10个值: {data[:10]}")
                
                elif isinstance(first_item, str):
                    print("  → 这是字符串数组")
                    print(f"    第一个字符串: {first_item[:100]}...")
                
                # 分析数组中的数据类型分布
                if len(data) > 1:
                    type_counts = defaultdict(int)
                    for item in data[:100]:  # 只分析前100个
                        type_counts[type(item).__name__] += 1
                    print(f"    数据类型分布: {dict(type_counts)}")
        
        elif isinstance(data, dict):
            print("  → 这是对象数据")
            print(f"    键数量: {len(data)}")
            print(f"    键列表: {list(data.keys())[:10]}")
            
            # 显示前几个键值对
            for i, (key, value) in enumerate(list(data.items())[:3]):
                print(f"      {key}: {type(value)} = {str(value)[:100]}...")
        
        else:
            print(f"  → 其他类型: {type(data)}")
            print(f"    内容: {str(data)[:200]}...")
    
    except Exception as e:
        print(f"  分析出错: {e}")

def analyze_data_patterns():
    """分析数据模式"""
    print(f"\n{'='*80}")
    print("数据模式分析")
    print(f"{'='*80}")
    
    data_dir = "decompressed_data_report"
    files = [f for f in os.listdir(data_dir) if f.endswith('.json')]
    
    # 统计不同数据结构的文件数量
    structure_stats = defaultdict(int)
    content_stats = defaultdict(int)
    
    for filename in files:
        filepath = os.path.join(data_dir, filename)
        
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            if isinstance(data, list):
                if len(data) > 0:
                    first_item = data[0]
                    if isinstance(first_item, dict):
                        if 'battle_id' in first_item:
                            structure_stats['battle_data'] += 1
                            content_stats['战斗数据'] += 1
                        elif 'userid' in first_item:
                            structure_stats['user_data'] += 1
                            content_stats['用户数据'] += 1
                        else:
                            structure_stats['object_data'] += 1
                            content_stats['对象数据'] += 1
                    elif isinstance(first_item, list):
                        if len(first_item) > 5:
                            structure_stats['rank_data'] += 1
                            content_stats['排行榜数据'] += 1
                        else:
                            structure_stats['array_data'] += 1
                            content_stats['数组数据'] += 1
                    elif isinstance(first_item, (int, float)):
                        structure_stats['numeric_data'] += 1
                        content_stats['数值数据'] += 1
                    else:
                        structure_stats['other_data'] += 1
                        content_stats['其他数据'] += 1
                else:
                    structure_stats['empty_array'] += 1
                    content_stats['空数组'] += 1
            elif isinstance(data, dict):
                structure_stats['object'] += 1
                content_stats['对象'] += 1
            else:
                structure_stats['other'] += 1
                content_stats['其他'] += 1
        
        except Exception as e:
            structure_stats['error'] += 1
            content_stats['错误'] += 1
    
    print(f"数据结构统计:")
    for structure, count in structure_stats.items():
        print(f"  {structure}: {count} 个文件")
    
    print(f"\n内容类型统计:")
    for content, count in content_stats.items():
        print(f"  {content}: {count} 个文件")

def main():
    """主函数"""
    analyze_message_000002bc()
    analyze_data_patterns()

if __name__ == "__main__":
    main()



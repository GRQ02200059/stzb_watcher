#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
详细分析不同消息类型的具体内容
"""

import json
import os
from collections import defaultdict

def analyze_detailed_message_types():
    """详细分析消息类型"""
    data_dir = "decompressed_data_report"
    
    print("="*80)
    print("详细消息类型分析")
    print("="*80)
    
    # 获取所有文件并按大小分类
    files = [f for f in os.listdir(data_dir) if f.endswith('.json')]
    
    # 按文件大小分类
    size_categories = {
        'tiny': [],     # <1KB
        'small': [],    # 1-10KB
        'medium': [],   # 10-100KB
        'large': []     # >100KB
    }
    
    for filename in files:
        filepath = os.path.join(data_dir, filename)
        size = os.path.getsize(filepath)
        
        if size < 1024:
            size_categories['tiny'].append(filename)
        elif size < 10240:
            size_categories['small'].append(filename)
        elif size < 102400:
            size_categories['medium'].append(filename)
        else:
            size_categories['large'].append(filename)
    
    print(f"文件大小分类:")
    for category, file_list in size_categories.items():
        print(f"  {category}: {len(file_list)} 个文件")
    
    # 分析每种类型的代表文件
    for category, file_list in size_categories.items():
        if not file_list:
            continue
            
        print(f"\n{'='*60}")
        print(f"{category.upper()} 文件详细分析")
        print(f"{'='*60}")
        
        # 分析前2个文件
        for filename in file_list[:2]:
            analyze_file_detailed(data_dir, filename, category)

def analyze_file_detailed(data_dir, filename, category):
    """详细分析单个文件"""
    filepath = os.path.join(data_dir, filename)
    size = os.path.getsize(filepath)
    
    print(f"\n文件: {filename}")
    print(f"大小: {size:,} 字节")
    print("-" * 40)
    
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        # 分析数据结构
        if isinstance(data, list):
            print(f"数据类型: 数组 (长度: {len(data)})")
            
            if len(data) > 0:
                first_item = data[0]
                print(f"第一个元素类型: {type(first_item)}")
                
                if isinstance(first_item, dict):
                    print("  → 对象数组")
                    print(f"    对象键数量: {len(first_item)}")
                    print(f"    主要键: {list(first_item.keys())[:10]}")
                    
                    # 根据键名判断数据类型
                    if 'battle_id' in first_item:
                        print("    【战斗数据】")
                        print(f"      战斗ID: {first_item.get('battle_id')}")
                        print(f"      攻击方: {first_item.get('attack_name')} ({first_item.get('attack_union_name')})")
                        print(f"      防守方: {first_item.get('defend_name')} ({first_item.get('defend_union_name')})")
                        print(f"      结果: {'胜利' if first_item.get('result') == 1 else '失败'}")
                        print(f"      时间: {first_item.get('time')}")
                        print(f"      位置: {first_item.get('wid_name')} (ID: {first_item.get('wid')})")
                        
                        # 分析英雄信息
                        attack_heroes = first_item.get('attack_all_hero_info', '')
                        defend_heroes = first_item.get('defend_all_hero_info', '')
                        if attack_heroes:
                            print(f"      攻击方英雄: {attack_heroes[:100]}...")
                        if defend_heroes:
                            print(f"      防守方英雄: {defend_heroes[:100]}...")
                    
                    elif 'userid' in first_item:
                        print("    【用户数据】")
                        print(f"      用户ID: {first_item.get('userid')}")
                        print(f"      角色名: {first_item.get('name', first_item.get('role_name'))}")
                    
                    elif 'wid' in first_item:
                        print("    【世界数据】")
                        print(f"      位置ID: {first_item.get('wid')}")
                        print(f"      城市类型: {first_item.get('city_type')}")
                    
                    else:
                        print("    【其他对象数据】")
                        print(f"      样本数据: {str(first_item)[:200]}...")
                
                elif isinstance(first_item, list):
                    print("  → 嵌套数组")
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
                    print("  → 数值数组")
                    print(f"    数值范围: {min(data[:10])} - {max(data[:10])}")
                    print(f"    前10个值: {data[:10]}")
                    
                    # 根据数值特征判断类型
                    if all(isinstance(x, int) for x in data[:10]):
                        print("    【整数数组 - 可能是系统状态】")
                    elif any(isinstance(x, float) for x in data[:10]):
                        print("    【浮点数组 - 可能是统计数据】")
                
                elif isinstance(first_item, str):
                    print("  → 字符串数组")
                    print(f"    第一个字符串: {first_item[:100]}...")
                    
                    if 'Tb_' in first_item:
                        print("    【数据库表结构】")
                    else:
                        print("    【文本数据】")
                
                # 分析数组中的数据类型分布
                if len(data) > 1:
                    type_counts = defaultdict(int)
                    for item in data[:100]:  # 只分析前100个
                        type_counts[type(item).__name__] += 1
                    print(f"    数据类型分布: {dict(type_counts)}")
        
        elif isinstance(data, dict):
            print("  → 对象数据")
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

def create_message_type_mapping():
    """创建消息类型映射"""
    print(f"\n{'='*80}")
    print("消息类型映射表")
    print(f"{'='*80}")
    
    print(f"根据分析结果，推测的消息类型标识对应关系:")
    
    print(f"\n1. 000002bc - 战斗数据消息")
    print(f"   - 文件大小: 约69KB")
    print(f"   - 数据结构: 包含战斗详情的对象数组")
    print(f"   - 内容: 攻击方/防守方信息、英雄配置、技能信息等")
    print(f"   - 特征: 包含 battle_id, attack_name, defend_name 等键")
    
    print(f"\n2. 000010ff - 系统状态消息")
    print(f"   - 文件大小: 1-8KB")
    print(f"   - 数据结构: 数值数组")
    print(f"   - 内容: 系统状态、配置参数等")
    print(f"   - 特征: 包含整数或浮点数值")
    
    print(f"\n3. 其他大文件 - 排行榜/系统数据消息")
    print(f"   - 文件大小: >100KB")
    print(f"   - 数据结构: 嵌套数组或复杂对象")
    print(f"   - 内容: 玩家排名、系统配置等")
    print(f"   - 特征: 包含玩家ID、角色名、联盟名等")
    
    print(f"\n4. 小文件 - 系统配置消息")
    print(f"   - 文件大小: <1KB")
    print(f"   - 数据结构: 简单数组或对象")
    print(f"   - 内容: 系统配置、状态更新等")
    print(f"   - 特征: 包含配置参数或状态信息")

def main():
    """主函数"""
    analyze_detailed_message_types()
    create_message_type_mapping()

if __name__ == "__main__":
    main()
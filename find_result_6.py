#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
查找result=6的情况
"""

import json
import os
from collections import Counter

def find_all_result_values():
    """查找所有result值"""
    
    print("🔍 扫描所有JSON文件查找result值")
    print("="*60)
    
    data_dir = "decompressed_data_report"
    result_values = []
    result_6_examples = []
    
    # 扫描主目录
    print("📁 扫描主目录...")
    for filename in os.listdir(data_dir):
        if filename.endswith('.json') and not os.path.isdir(os.path.join(data_dir, filename)):
            filepath = os.path.join(data_dir, filename)
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                if isinstance(data, list) and len(data) > 0:
                    # 处理嵌套数组结构
                    if isinstance(data[0], list) and len(data[0]) > 0:
                        battle_obj = data[0][0]
                    else:
                        battle_obj = data[0]
                    
                    if isinstance(battle_obj, dict) and 'battle_id' in battle_obj:
                        result = battle_obj.get('result')
                        result_values.append(result)
                        
                        # 记录result=6的案例
                        if result == 6:
                            result_6_examples.append({
                                'file': filename,
                                'battle_id': battle_obj.get('battle_id'),
                                'result': result,
                                'attack_name': battle_obj.get('attack_name'),
                                'defend_name': battle_obj.get('defend_name'),
                                'attack_hp': battle_obj.get('attack_hp'),
                                'defend_hp': battle_obj.get('defend_hp')
                            })
            except Exception as e:
                continue
    
    # 扫描子目录
    print("📁 扫描子目录...")
    for subdir in ['000002bc', '000010ff', '000015f95', 'unknown']:
        subdir_path = os.path.join(data_dir, subdir)
        if os.path.exists(subdir_path):
            print(f"  扫描 {subdir}/ 目录...")
            for filename in os.listdir(subdir_path):
                if filename.endswith('.json'):
                    filepath = os.path.join(subdir_path, filename)
                    try:
                        with open(filepath, 'r', encoding='utf-8') as f:
                            data = json.load(f)
                        
                        if isinstance(data, list) and len(data) > 0:
                            # 处理嵌套数组结构
                            if isinstance(data[0], list) and len(data[0]) > 0:
                                battle_obj = data[0][0]
                            else:
                                battle_obj = data[0]
                            
                            if isinstance(battle_obj, dict) and 'battle_id' in battle_obj:
                                result = battle_obj.get('result')
                                result_values.append(result)
                                
                                # 记录result=6的案例
                                if result == 6:
                                    result_6_examples.append({
                                        'file': f"{subdir}/{filename}",
                                        'battle_id': battle_obj.get('battle_id'),
                                        'result': result,
                                        'attack_name': battle_obj.get('attack_name'),
                                        'defend_name': battle_obj.get('defend_name'),
                                        'attack_hp': battle_obj.get('attack_hp'),
                                        'defend_hp': battle_obj.get('defend_hp')
                                    })
                    except Exception as e:
                        continue
    
    # 统计结果
    result_counter = Counter(result_values)
    
    print(f"\n📊 统计结果:")
    print(f"  总战斗数: {len(result_values)}")
    print(f"  不同result值: {sorted(result_counter.keys())}")
    print()
    
    for result_value, count in sorted(result_counter.items()):
        percentage = (count / len(result_values)) * 100
        print(f"  result={result_value}: {count} 次 ({percentage:.1f}%)")
    
    # 如果有result=6的案例，详细分析
    if result_6_examples:
        print(f"\n🎯 发现result=6的案例 ({len(result_6_examples)} 个):")
        print("="*60)
        for i, example in enumerate(result_6_examples, 1):
            print(f"案例{i}:")
            print(f"  文件: {example['file']}")
            print(f"  战斗ID: {example['battle_id']}")
            print(f"  result: {example['result']}")
            print(f"  攻击方: {example['attack_name']}")
            print(f"  防守方: {example['defend_name']}")
            print(f"  攻击方HP: {example['attack_hp']}")
            print(f"  防守方HP: {example['defend_hp']}")
            print()
    else:
        print(f"\n✅ 没有发现result=6的案例")
        print("所有战斗的result值都是0或1")
    
    # 分析result=6的可能含义
    if result_6_examples:
        print(f"🤔 result=6的可能含义:")
        print("  1. 平局")
        print("  2. 战斗超时")
        print("  3. 战斗中断")
        print("  4. 特殊战斗状态")
        print("  5. 数据错误")
        
        # 分析HP关系
        for example in result_6_examples:
            attack_hp = example['attack_hp']
            defend_hp = example['defend_hp']
            if attack_hp == defend_hp:
                print(f"  → 案例 {example['battle_id']}: 双方HP相等 ({attack_hp})，可能是平局")
            elif abs(attack_hp - defend_hp) < 100:
                print(f"  → 案例 {example['battle_id']}: HP差距很小 ({attack_hp} vs {defend_hp})，可能是平局")
            else:
                print(f"  → 案例 {example['battle_id']}: HP差距较大 ({attack_hp} vs {defend_hp})，需要进一步分析")

def main():
    """主函数"""
    find_all_result_values()

if __name__ == "__main__":
    main()



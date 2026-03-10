#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分析result字段的含义
"""

import json
import os
from collections import Counter

def analyze_result_field():
    """分析result字段的含义"""
    
    print("🔍 分析result字段含义")
    print("="*60)
    
    # 扫描所有JSON文件
    data_dir = "decompressed_data_report"
    result_values = []
    battle_examples = []
    
    # 扫描主目录
    for filename in os.listdir(data_dir):
        if filename.endswith('.json') and not os.path.isdir(os.path.join(data_dir, filename)):
            filepath = os.path.join(data_dir, filename)
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                if isinstance(data, list) and len(data) > 0:
                    battle_obj = data[0]
                    if isinstance(battle_obj, dict) and 'battle_id' in battle_obj:
                        result = battle_obj.get('result')
                        result_values.append(result)
                        
                        # 收集示例
                        if len(battle_examples) < 5:
                            battle_examples.append({
                                'file': filename,
                                'battle_id': battle_obj.get('battle_id'),
                                'result': result,
                                'attack_name': battle_obj.get('attack_name'),
                                'defend_name': battle_obj.get('defend_name'),
                                'attack_union': battle_obj.get('attack_union_name'),
                                'defend_union': battle_obj.get('defend_union_name')
                            })
            except Exception as e:
                continue
    
    # 扫描000002bc目录
    type_dir = os.path.join(data_dir, "000002bc")
    if os.path.exists(type_dir):
        for filename in os.listdir(type_dir):
            if filename.endswith('.json'):
                filepath = os.path.join(type_dir, filename)
                try:
                    with open(filepath, 'r', encoding='utf-8') as f:
                        data = json.load(f)
                    
                    if isinstance(data, list) and len(data) > 0:
                        battle_obj = data[0]
                        if isinstance(battle_obj, dict) and 'battle_id' in battle_obj:
                            result = battle_obj.get('result')
                            result_values.append(result)
                            
                            # 收集示例
                            if len(battle_examples) < 10:
                                battle_examples.append({
                                    'file': filename,
                                    'battle_id': battle_obj.get('battle_id'),
                                    'result': result,
                                    'attack_name': battle_obj.get('attack_name'),
                                    'defend_name': battle_obj.get('defend_name'),
                                    'attack_union': battle_obj.get('attack_union_name'),
                                    'defend_union': battle_obj.get('defend_union_name')
                                })
                except Exception as e:
                    continue
    
    if not result_values:
        print("❌ 没有找到战斗数据")
        return
    
    # 统计result值
    result_counter = Counter(result_values)
    
    print(f"📊 result字段统计:")
    print(f"  总战斗数: {len(result_values)}")
    print(f"  不同result值: {sorted(result_counter.keys())}")
    print()
    
    for result_value, count in sorted(result_counter.items()):
        percentage = (count / len(result_values)) * 100
        print(f"  result={result_value}: {count} 次 ({percentage:.1f}%)")
    
    print(f"\n📋 战斗示例:")
    for i, example in enumerate(battle_examples, 1):
        print(f"  示例{i}:")
        print(f"    文件: {example['file']}")
        print(f"    战斗ID: {example['battle_id']}")
        print(f"    result: {example['result']}")
        print(f"    攻击方: {example['attack_name']} ({example['attack_union']})")
        print(f"    防守方: {example['defend_name']} ({example['defend_union']})")
        print()
    
    # 分析result字段的含义
    print(f"🎯 result字段含义分析:")
    print("="*60)
    
    if 0 in result_counter and 1 in result_counter:
        print("根据数据分析，result字段表示战斗结果:")
        print(f"  result=0: 防守方胜利 ({result_counter[0]} 次)")
        print(f"  result=1: 攻击方胜利 ({result_counter[1]} 次)")
    elif 0 in result_counter:
        print("根据数据分析，result字段表示战斗结果:")
        print(f"  result=0: 防守方胜利 ({result_counter[0]} 次)")
        print("  (未发现攻击方胜利的案例)")
    elif 1 in result_counter:
        print("根据数据分析，result字段表示战斗结果:")
        print(f"  result=1: 攻击方胜利 ({result_counter[1]} 次)")
        print("  (未发现防守方胜利的案例)")
    else:
        print("result字段的其他可能含义:")
        for result_value, count in sorted(result_counter.items()):
            print(f"  result={result_value}: {count} 次 (含义待确定)")
    
    # 验证分析
    print(f"\n🔍 验证分析:")
    print("="*60)
    
    # 检查是否有其他相关字段
    sample_file = None
    for filename in os.listdir(data_dir):
        if filename.endswith('.json'):
            sample_file = os.path.join(data_dir, filename)
            break
    
    if sample_file:
        with open(sample_file, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        if isinstance(data, list) and len(data) > 0:
            battle_obj = data[0]
            print("相关字段分析:")
            
            # 检查可能的胜负相关字段
            related_fields = [
                'result', 'extra_result', 'fight_type', 'battle_scenes',
                'attack_hp', 'defend_hp', 'attacker_xwc', 'defender_xwc'
            ]
            
            for field in related_fields:
                if field in battle_obj:
                    print(f"  {field}: {battle_obj[field]}")
            
            # 分析HP和势力值的关系
            attack_hp = battle_obj.get('attack_hp', 0)
            defend_hp = battle_obj.get('defend_hp', 0)
            result = battle_obj.get('result', -1)
            
            print(f"\nHP和结果关系分析:")
            print(f"  攻击方HP: {attack_hp}")
            print(f"  防守方HP: {defend_hp}")
            print(f"  战斗结果: {result}")
            
            if result == 0:
                print(f"  → 防守方胜利，防守方HP({defend_hp}) > 攻击方HP({attack_hp})")
            elif result == 1:
                print(f"  → 攻击方胜利，攻击方HP({attack_hp}) > 防守方HP({defend_hp})")

def main():
    """主函数"""
    analyze_result_field()

if __name__ == "__main__":
    main()

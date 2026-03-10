#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
简单分析result字段
"""

import json
import os

def analyze_single_file():
    """分析单个文件的result字段"""
    
    # 分析几个具体的文件
    files_to_analyze = [
        "decompressed_data_report/decompressed_20250305230442558.json",
        "decompressed_data_report/decompressed_20250305230341418.json",
        "decompressed_data_report/decompressed_20250305230402829.json"
    ]
    
    print("🔍 分析result字段含义")
    print("="*60)
    
    for filepath in files_to_analyze:
        if os.path.exists(filepath):
            print(f"\n📄 分析文件: {os.path.basename(filepath)}")
            print("-" * 40)
            
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                if isinstance(data, list) and len(data) > 0:
                    # 处理嵌套数组结构
                    if isinstance(data[0], list) and len(data[0]) > 0:
                        battle_obj = data[0][0]  # 第一层数组的第一个元素
                    else:
                        battle_obj = data[0]
                    
                    if isinstance(battle_obj, dict) and 'battle_id' in battle_obj:
                        print(f"战斗ID: {battle_obj.get('battle_id')}")
                        print(f"result: {battle_obj.get('result')}")
                        print(f"攻击方: {battle_obj.get('attack_name')} ({battle_obj.get('attack_union_name')})")
                        print(f"防守方: {battle_obj.get('defend_name')} ({battle_obj.get('defend_union_name')})")
                        print(f"攻击方HP: {battle_obj.get('attack_hp')}")
                        print(f"防守方HP: {battle_obj.get('defend_hp')}")
                        print(f"攻击方势力值: {battle_obj.get('attacker_xwc')}")
                        print(f"防守方势力值: {battle_obj.get('defender_xwc')}")
                        
                        # 分析result的含义
                        result = battle_obj.get('result')
                        attack_hp = battle_obj.get('attack_hp', 0)
                        defend_hp = battle_obj.get('defend_hp', 0)
                        
                        if result == 0:
                            print("🎯 分析: result=0 表示防守方胜利")
                            if defend_hp > attack_hp:
                                print(f"   ✅ 防守方HP({defend_hp}) > 攻击方HP({attack_hp})，符合预期")
                            else:
                                print(f"   ⚠️  防守方HP({defend_hp}) < 攻击方HP({attack_hp})，可能HP不是唯一判断标准")
                        elif result == 1:
                            print("🎯 分析: result=1 表示攻击方胜利")
                            if attack_hp > defend_hp:
                                print(f"   ✅ 攻击方HP({attack_hp}) > 防守方HP({defend_hp})，符合预期")
                            else:
                                print(f"   ⚠️  攻击方HP({attack_hp}) < 防守方HP({defend_hp})，可能HP不是唯一判断标准")
                        else:
                            print(f"🎯 分析: result={result} 表示其他状态")
                        
                        print()
                    else:
                        print("❌ 不是战斗数据")
                else:
                    print("❌ 数据格式不正确")
                    
            except Exception as e:
                print(f"❌ 读取文件失败: {str(e)}")
        else:
            print(f"❌ 文件不存在: {filepath}")
    
    print("="*60)
    print("📊 总结:")
    print("根据分析，result字段表示战斗结果:")
    print("  result=0: 防守方胜利")
    print("  result=1: 攻击方胜利")
    print("  result=2: 平局或其他状态（如果存在）")

if __name__ == "__main__":
    analyze_single_file()

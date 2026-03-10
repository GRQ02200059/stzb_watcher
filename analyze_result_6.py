#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
详细分析result=6的情况
"""

import json
import os

def analyze_result_6_detailed():
    """详细分析result=6的情况"""
    
    print("🔍 详细分析result=6的情况")
    print("="*60)
    
    # 分析一个result=6的文件
    filepath = "decompressed_data_report/decompressed_20250305230422385.json"
    
    if not os.path.exists(filepath):
        print(f"❌ 文件不存在: {filepath}")
        return
    
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    if isinstance(data, list) and len(data) > 0:
        if isinstance(data[0], list) and len(data[0]) > 0:
            battle_obj = data[0][0]
        else:
            battle_obj = data[0]
        
        print(f"📄 分析文件: {os.path.basename(filepath)}")
        print(f"战斗ID: {battle_obj.get('battle_id')}")
        print(f"result: {battle_obj.get('result')}")
        print()
        
        # 分析所有可能相关的字段
        print("🔍 相关字段分析:")
        print("-" * 40)
        
        # 基本战斗信息
        print("基本战斗信息:")
        print(f"  battle_id: {battle_obj.get('battle_id')}")
        print(f"  result: {battle_obj.get('result')}")
        print(f"  extra_result: {battle_obj.get('extra_result')}")
        print(f"  fight_type: {battle_obj.get('fight_type')}")
        print(f"  battle_scenes: {battle_obj.get('battle_scenes')}")
        print()
        
        # 双方信息
        print("攻击方信息:")
        print(f"  attack_name: {battle_obj.get('attack_name')}")
        print(f"  attack_hp: {battle_obj.get('attack_hp')}")
        print(f"  attack_union_name: {battle_obj.get('attack_union_name')}")
        print(f"  attack_base_level: {battle_obj.get('attack_base_level')}")
        print()
        
        print("防守方信息:")
        print(f"  defend_name: {battle_obj.get('defend_name')}")
        print(f"  defend_hp: {battle_obj.get('defend_hp')}")
        print(f"  defend_union_name: {battle_obj.get('defend_union_name')}")
        print(f"  defend_base_level: {battle_obj.get('defend_base_level')}")
        print()
        
        # 特殊状态字段
        print("特殊状态字段:")
        print(f"  is_ai: {battle_obj.get('is_ai')}")
        print(f"  is_shared: {battle_obj.get('is_shared')}")
        print(f"  in_night_mode: {battle_obj.get('in_night_mode')}")
        print(f"  ambush: {battle_obj.get('ambush')}")
        print(f"  bandit: {battle_obj.get('bandit')}")
        print(f"  npc: {battle_obj.get('npc')}")
        print(f"  garrison: {battle_obj.get('garrison')}")
        print(f"  rob: {battle_obj.get('rob')}")
        print(f"  press_attack: {battle_obj.get('press_attack')}")
        print(f"  yi_ling_press_attack: {battle_obj.get('yi_ling_press_attack')}")
        print()
        
        # 战斗效果字段
        print("战斗效果字段:")
        print(f"  military: {battle_obj.get('military')}")
        print(f"  military_effect: {battle_obj.get('military_effect')}")
        print(f"  ship_effect: {battle_obj.get('ship_effect')}")
        print(f"  machine_effect: {battle_obj.get('machine_effect')}")
        print(f"  weather: {battle_obj.get('weather')}")
        print()
        
        # 其他可能相关字段
        print("其他字段:")
        print(f"  city_type: {battle_obj.get('city_type')}")
        print(f"  res_type: {battle_obj.get('res_type')}")
        print(f"  first_occupy_lvn_land: {battle_obj.get('first_occupy_lvn_land')}")
        print(f"  borrow_land: {battle_obj.get('borrow_land')}")
        print(f"  no_owner_res: {battle_obj.get('no_owner_res')}")
        print(f"  huangjin_convert: {battle_obj.get('huangjin_convert')}")
        print()
        
        # 分析result=6的可能原因
        print("🤔 result=6分析:")
        print("-" * 40)
        
        attack_hp = battle_obj.get('attack_hp', 0)
        defend_hp = battle_obj.get('defend_hp', 0)
        extra_result = battle_obj.get('extra_result', 0)
        fight_type = battle_obj.get('fight_type', 0)
        is_ai = battle_obj.get('is_ai', 0)
        npc = battle_obj.get('npc', 0)
        
        print(f"HP对比: 攻击方({attack_hp}) vs 防守方({defend_hp})")
        print(f"HP差值: {abs(attack_hp - defend_hp)}")
        
        if attack_hp > defend_hp:
            print("  → 攻击方HP更高，但result=6，不是攻击方胜利")
        elif defend_hp > attack_hp:
            print("  → 防守方HP更高，但result=6，不是防守方胜利")
        else:
            print("  → 双方HP相等，可能是平局")
        
        print(f"extra_result: {extra_result}")
        if extra_result != 0:
            print(f"  → extra_result不为0，可能有特殊结果")
        
        print(f"fight_type: {fight_type}")
        if fight_type != 0:
            print(f"  → fight_type不为0，可能是特殊战斗类型")
        
        print(f"is_ai: {is_ai}")
        if is_ai == 1:
            print(f"  → 是AI战斗，可能有特殊规则")
        
        print(f"npc: {npc}")
        if npc == 1:
            print(f"  → 是NPC战斗，可能有特殊规则")
        
        # 推测result=6的含义
        print(f"\n🎯 result=6的可能含义:")
        print("1. 平局 - 双方HP相近或相等")
        print("2. 战斗超时 - 战斗时间过长")
        print("3. 战斗中断 - 玩家离线或网络问题")
        print("4. 特殊战斗状态 - 有特殊规则或效果")
        print("5. 数据异常 - 服务器端处理异常")
        
        # 检查是否有重复的战斗ID
        print(f"\n🔍 检查重复战斗ID:")
        print(f"战斗ID {battle_obj.get('battle_id')} 在多个文件中出现")
        print("这可能是同一场战斗的多次记录或不同阶段")

def compare_with_normal_battle():
    """与正常战斗对比"""
    
    print(f"\n🔄 与正常战斗对比")
    print("="*60)
    
    # 找一个result=0的文件对比
    normal_file = "decompressed_data_report/decompressed_20250305230442558.json"
    result6_file = "decompressed_data_report/decompressed_20250305230422385.json"
    
    def analyze_file(filepath, label):
        print(f"\n{label}:")
        print("-" * 30)
        
        with open(filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        if isinstance(data, list) and len(data) > 0:
            if isinstance(data[0], list) and len(data[0]) > 0:
                battle_obj = data[0][0]
            else:
                battle_obj = data[0]
            
            print(f"  result: {battle_obj.get('result')}")
            print(f"  extra_result: {battle_obj.get('extra_result')}")
            print(f"  fight_type: {battle_obj.get('fight_type')}")
            print(f"  is_ai: {battle_obj.get('is_ai')}")
            print(f"  npc: {battle_obj.get('npc')}")
            print(f"  attack_hp: {battle_obj.get('attack_hp')}")
            print(f"  defend_hp: {battle_obj.get('defend_hp')}")
            print(f"  attack_name: {battle_obj.get('attack_name')}")
            print(f"  defend_name: {battle_obj.get('defend_name')}")
    
    analyze_file(normal_file, "正常战斗 (result=0)")
    analyze_file(result6_file, "异常战斗 (result=6)")

def main():
    """主函数"""
    analyze_result_6_detailed()
    compare_with_normal_battle()

if __name__ == "__main__":
    main()



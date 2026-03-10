#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分析战斗JSON数据
"""

import json
import os
from datetime import datetime

def analyze_battle_json():
    """分析战斗JSON数据"""
    
    # 读取JSON文件
    json_file = "decompressed_data_report/decompressed_20250305230442558.json"
    
    if not os.path.exists(json_file):
        print(f"❌ 文件不存在: {json_file}")
        return
    
    with open(json_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    print("🔍 战斗JSON数据分析")
    print("="*80)
    
    # 基本结构分析
    print(f"📊 数据结构:")
    print(f"  数据类型: {type(data)}")
    if isinstance(data, list):
        print(f"  数组长度: {len(data)}")
        print(f"  第一个元素类型: {type(data[0])}")
    
    # 分析战斗数据
    if isinstance(data, list) and len(data) > 0:
        battle_obj = data[0]  # 第一个元素是战斗对象
        
        print(f"\n⚔️ 战斗信息:")
        print(f"  战斗ID: {battle_obj.get('battle_id')}")
        print(f"  战斗时间: {battle_obj.get('time')}")
        print(f"  战斗结果: {'攻击方胜利' if battle_obj.get('result') == 1 else '防守方胜利'}")
        print(f"  战斗场景: {battle_obj.get('battle_scenes')}")
        print(f"  城市类型: {battle_obj.get('city_type')}")
        print(f"  位置: {battle_obj.get('wid_name')} (ID: {battle_obj.get('wid')})")
        
        print(f"\n👥 攻击方信息:")
        print(f"  玩家名: {battle_obj.get('attack_name')}")
        print(f"  联盟名: {battle_obj.get('attack_union_name')}")
        print(f"  联盟ID: {battle_obj.get('attack_unionid')}")
        print(f"  角色ID: {battle_obj.get('attack_role_id')}")
        print(f"  主城等级: {battle_obj.get('attack_base_level')}")
        print(f"  主城英雄ID: {battle_obj.get('attack_base_heroid')}")
        print(f"  生命值: {battle_obj.get('attack_hp')}")
        print(f"  势力值: {battle_obj.get('attacker_xwc')}")
        
        print(f"\n🛡️ 防守方信息:")
        print(f"  玩家名: {battle_obj.get('defend_name')}")
        print(f"  联盟名: {battle_obj.get('defend_union_name')}")
        print(f"  联盟ID: {battle_obj.get('defend_unionid')}")
        print(f"  角色ID: {battle_obj.get('defend_role_id')}")
        print(f"  主城等级: {battle_obj.get('defend_base_level')}")
        print(f"  主城英雄ID: {battle_obj.get('defend_base_heroid')}")
        print(f"  生命值: {battle_obj.get('defend_hp')}")
        print(f"  势力值: {battle_obj.get('defender_xwc')}")
        
        # 分析英雄信息
        print(f"\n⚔️ 攻击方英雄配置:")
        attack_heroes = battle_obj.get('attack_all_hero_info', '')
        if attack_heroes:
            hero_list = attack_heroes.split(';')
            for i, hero in enumerate(hero_list, 1):
                if hero:
                    hero_data = hero.split(',')
                    if len(hero_data) >= 5:
                        hero_id, level, attack, defense, speed = hero_data[:5]
                        print(f"  英雄{i}: ID={hero_id}, 等级={level}, 攻击={attack}, 防御={defense}, 速度={speed}")
        
        print(f"\n🛡️ 防守方英雄配置:")
        defend_heroes = battle_obj.get('defend_all_hero_info', '')
        if defend_heroes:
            hero_list = defend_heroes.split(';')
            for i, hero in enumerate(hero_list, 1):
                if hero:
                    hero_data = hero.split(',')
                    if len(hero_data) >= 5:
                        hero_id, level, attack, defense, speed = hero_data[:5]
                        print(f"  英雄{i}: ID={hero_id}, 等级={level}, 攻击={attack}, 防御={defense}, 速度={speed}")
        
        # 分析技能信息
        print(f"\n🎯 技能配置:")
        skill_info = battle_obj.get('all_skill_info', '')
        if skill_info:
            skill_groups = skill_info.split(';')
            for i, group in enumerate(skill_groups, 1):
                if group:
                    skills = group.split(',')
                    print(f"  英雄{i}技能: {', '.join(skills[:6])}")  # 只显示前6个技能
        
        # 分析其他重要信息
        print(f"\n📊 其他信息:")
        print(f"  战斗类型: {battle_obj.get('fight_type')}")
        print(f"  是否AI: {'是' if battle_obj.get('is_ai') else '否'}")
        print(f"  是否共享: {'是' if battle_obj.get('is_shared') else '否'}")
        print(f"  夜战模式: {'是' if battle_obj.get('in_night_mode') else '否'}")
        print(f"  天气: {battle_obj.get('weather')}")
        print(f"  国家信息: {battle_obj.get('nation_member_union_info')}")
        
        # 分析数组的其他元素
        print(f"\n📋 数组其他元素:")
        for i, item in enumerate(data[1:], 1):
            print(f"  元素{i}: {type(item)} = {item}")
    
    print(f"\n✅ 分析完成")

def analyze_hero_skills():
    """分析英雄技能信息"""
    print(f"\n🎯 英雄技能详细分析")
    print("="*80)
    
    # 技能ID映射（这里只是示例，实际需要完整的技能数据库）
    skill_mapping = {
        "200693": "技能1",
        "200992": "技能2", 
        "200119": "技能3",
        "200989": "技能4",
        "200220": "技能5",
        "200228": "技能6",
        "200021": "技能7",
        "200766": "技能8",
        "200632": "技能9",
        "200023": "技能10",
        "200674": "技能11",
        "200862": "技能12",
        "200027": "技能13",
        "200226": "技能14",
        "200731": "技能15",
        "200013": "技能16",
        "200980": "技能17",
        "200839": "技能18"
    }
    
    json_file = "decompressed_data_report/decompressed_20250305230442558.json"
    with open(json_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    if isinstance(data, list) and len(data) > 0:
        battle_obj = data[0]
        skill_info = battle_obj.get('all_skill_info', '')
        
        if skill_info:
            skill_groups = skill_info.split(';')
            for i, group in enumerate(skill_groups, 1):
                if group:
                    skills = group.split(',')
                    print(f"英雄{i}技能配置:")
                    for j in range(0, len(skills), 3):
                        if j + 2 < len(skills):
                            skill_id = skills[j]
                            skill_level = skills[j + 1]
                            skill_name = skill_mapping.get(skill_id, f"未知技能{skill_id}")
                            print(f"  {skill_name} (ID: {skill_id}, 等级: {skill_level})")

def main():
    """主函数"""
    analyze_battle_json()
    analyze_hero_skills()

if __name__ == "__main__":
    main()



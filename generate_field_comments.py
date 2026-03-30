#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
0000005c（同盟战报）字段含义注释
"""

def generate_field_comments():
    """生成字段注释文档"""
    
    field_comments = {
        # 基础信息
        "aiid": "AI标识符 - 标识是否为AI战斗",
        "all_exp": "所有武将经验值 - 格式：武将1经验,等级;武将2经验,等级;...",
        "all_skill_info": "所有技能信息 - 格式：武将1技能ID,等级,技能ID,等级;武将2技能ID,等级;...",
        
        # 攻击方信息
        "attack_advance": "攻击方武将进阶信息 - 格式：武将1进阶等级,突破等级;武将2进阶等级,突破等级;...",
        "attack_all_hero_info": "攻击方所有武将信息 - 格式：武将ID,等级,兵力,当前血量,最大血量;...",
        "attack_all_sub_hero_info": "攻击方副将信息 - 格式：副将ID,等级,兵力,当前血量,最大血量;...",
        "attack_all_surface": "攻击方武将外观信息 - 格式：武将ID,外观ID;武将ID,外观ID;...",
        "attack_army_group": "攻击方军团信息",
        "attack_base_heroid": "攻击方主将ID",
        "attack_base_level": "攻击方主将等级",
        "attack_clan_name": "攻击方氏族名称",
        "attack_clanid": "攻击方氏族ID",
        "attack_fight_event_count": "攻击方战斗事件计数",
        "attack_fight_event_facade": "攻击方战斗事件外观",
        "attack_help_id": "攻击方援军ID - 格式：玩家ID@服务器ID",
        "attack_hero_policy": "攻击方武将政策",
        "attack_hero_special_achieve_title": "攻击方武将特殊成就称号 - 格式：称号ID,等级;称号ID,等级;...",
        "attack_hero_type": "攻击方武将类型 - 格式：武将1类型,武将2类型,武将3类型,武将4类型,",
        "attack_hero_type_advance": "攻击方武将类型进阶 - 格式：武将1进阶,武将2进阶,武将3进阶,武将4进阶,",
        "attack_hp": "攻击方总血量",
        "attack_idu": "攻击方IDU信息 - 格式：主ID,副ID1,副ID2,副ID3,副ID4",
        "attack_name": "攻击方玩家名称",
        "attack_role_id": "攻击方角色ID",
        "attack_ship_type": "攻击方船只类型",
        "attack_support_user_info": "攻击方支援用户信息",
        "attack_union_name": "攻击方联盟名称",
        "attack_union_official": "攻击方联盟官职",
        "attack_unionid": "攻击方联盟ID",
        "attacker_army_effect": "攻击方军队效果",
        "attacker_base_hero_detail": "攻击方主将详细信息",
        "attacker_force": "攻击方势力",
        "attacker_gear_info": "攻击方装备信息 - 格式：武将1装备ID,等级,属性;武将2装备ID,等级,属性;...",
        "attacker_gongxun": "攻击方功勋",
        "attacker_life_end_time": "攻击方生命结束时间",
        "attacker_machine_stat_info": "攻击方机械状态信息",
        "attacker_surface": "攻击方外观信息 - 格式：武将1外观ID,外观ID,属性;武将2外观ID,外观ID,属性;...",
        "attacker_xwc": "攻击方XWC值",
        
        # 防守方信息
        "defend_advance": "防守方武将进阶信息 - 格式：武将1进阶等级,突破等级;武将2进阶等级,突破等级;...",
        "defend_all_hero_info": "防守方所有武将信息 - 格式：武将ID,等级,兵力,当前血量,最大血量;...",
        "defend_all_sub_hero_info": "防守方副将信息 - 格式：副将ID,等级,兵力,当前血量,最大血量;...",
        "defend_all_surface": "防守方武将外观信息 - 格式：武将ID,外观ID;武将ID,外观ID;...",
        "defend_army_group": "防守方军团信息",
        "defend_base_heroid": "防守方主将ID",
        "defend_base_level": "防守方主将等级",
        "defend_clan_name": "防守方氏族名称",
        "defend_clanid": "防守方氏族ID",
        "defend_fight_event_count": "防守方战斗事件计数",
        "defend_fight_event_facade": "防守方战斗事件外观",
        "defend_help_id": "防守方援军ID - 格式：玩家ID@服务器ID",
        "defend_hero_policy": "防守方武将政策",
        "defend_hero_special_achieve_title": "防守方武将特殊成就称号 - 格式：称号ID,等级;称号ID,等级;...",
        "defend_hero_type": "防守方武将类型 - 格式：武将1类型,武将2类型,武将3类型,武将4类型,",
        "defend_hero_type_advance": "防守方武将类型进阶 - 格式：武将1进阶,武将2进阶,武将3进阶,武将4进阶,",
        "defend_hp": "防守方总血量",
        "defend_idu": "防守方IDU信息 - 格式：主ID,副ID1,副ID2,副ID3,副ID4",
        "defend_name": "防守方玩家名称",
        "defend_role_id": "防守方角色ID",
        "defend_ship_type": "防守方船只类型",
        "defend_support_user_info": "防守方支援用户信息",
        "defend_union_name": "防守方联盟名称",
        "defend_union_official": "防守方联盟官职",
        "defend_unionid": "防守方联盟ID",
        "defender_army_effect": "防守方军队效果",
        "defender_base_hero_detail": "防守方主将详细信息",
        "defender_force": "防守方势力",
        "defender_gear_info": "防守方装备信息 - 格式：武将1装备ID,等级,属性;武将2装备ID,等级,属性;...",
        "defender_gongxun": "防守方功勋",
        "defender_life_end_time": "防守方生命结束时间",
        "defender_machine_stat_info": "防守方机械状态信息",
        "defender_surface": "防守方外观信息 - 格式：武将1外观ID,外观ID,属性;武将2外观ID,外观ID,属性;...",
        "defender_xwc": "防守方XWC值",
        
        # 战斗信息
        "battle_id": "战斗ID - 唯一标识符",
        "battle_scenes": "战斗场景ID",
        "block_id": "地块ID",
        "borrow_land": "借地标识",
        "city_type": "城市类型 - 7表示某种特定城市类型",
        "extra_result": "额外结果",
        "fight_type": "战斗类型 - 0表示普通战斗",
        "first_occupy_lvn_land": "首次占领等级土地",
        "garrison": "驻军标识",
        "huangjin_convert": "黄金转换",
        "in_night_mode": "夜间模式标识",
        "is_ai": "是否为AI战斗 - 0表示玩家战斗",
        "is_shared": "是否共享",
        "lose_tips": "失败提示",
        "machine_effect": "机械效果",
        "military": "军事值",
        "military_effect": "军事效果",
        "mvp_svp_pos": "MVP/SVP位置 - 格式：MVP位置,SVP位置",
        "nation_member_union_info": "国家成员联盟信息 - 格式：国家ID,联盟ID,国家名称,成员数,联盟数,联盟ID,联盟名称,联盟等级",
        "no_owner_res": "无主资源",
        "npc": "NPC标识 - 1表示NPC战斗",
        "press_attack": "强攻标识",
        "reference_count": "引用计数",
        "res_type": "资源类型 - 12表示某种特定资源类型",
        "result": "战斗结果 - 6表示异常战斗状态",
        "rob": "掠夺标识",
        "sand_extra_info": "沙地额外信息",
        "ship_effect": "船只效果",
        "tech_jian_jun_effect": "科技建军效果",
        "tech_quan_xiang_effect": "科技全向效果",
        "time": "战斗时间戳 - Unix时间戳",
        "weather": "天气效果",
        "wid": "世界ID - 世界地图上的位置ID",
        "wid_name": "世界名称",
        "world_npc_army": "世界NPC军队 - 格式：军队ID,军队类型,参数1,参数2",
        "yi_ling_press_attack": "义灵强攻"
    }
    
    return field_comments

def save_field_comments():
    """保存字段注释到文件"""
    
    field_comments = generate_field_comments()
    
    # 保存为JSON格式
    with open('0000005c_field_comments.json', 'w', encoding='utf-8') as f:
        import json
        json.dump(field_comments, f, ensure_ascii=False, indent=2)
    
    # 保存为Markdown格式
    with open('0000005c_field_comments.md', 'w', encoding='utf-8') as f:
        f.write("# 0000005c（同盟战报）字段含义注释\n\n")
        
        f.write("## 基础信息\n")
        f.write("| 字段名 | 含义 | 示例 |\n")
        f.write("|--------|------|------|\n")
        
        basic_fields = ["aiid", "all_exp", "all_skill_info"]
        for field in basic_fields:
            f.write(f"| {field} | {field_comments[field]} | - |\n")
        
        f.write("\n## 攻击方信息\n")
        f.write("| 字段名 | 含义 | 示例 |\n")
        f.write("|--------|------|------|\n")
        
        attack_fields = [k for k in field_comments.keys() if k.startswith('attack_') or k.startswith('attacker_')]
        for field in attack_fields:
            f.write(f"| {field} | {field_comments[field]} | - |\n")
        
        f.write("\n## 防守方信息\n")
        f.write("| 字段名 | 含义 | 示例 |\n")
        f.write("|--------|------|------|\n")
        
        defend_fields = [k for k in field_comments.keys() if k.startswith('defend_') or k.startswith('defender_')]
        for field in defend_fields:
            f.write(f"| {field} | {field_comments[field]} | - |\n")
        
        f.write("\n## 战斗信息\n")
        f.write("| 字段名 | 含义 | 示例 |\n")
        f.write("|--------|------|------|\n")
        
        battle_fields = [k for k in field_comments.keys() if not k.startswith(('attack_', 'defend_', 'attacker_', 'defender_'))]
        for field in battle_fields:
            f.write(f"| {field} | {field_comments[field]} | - |\n")
    
    print("字段注释已保存:")
    print("- JSON格式: 0000005c_field_comments.json")
    print("- Markdown格式: 0000005c_field_comments.md")

def main():
    """主函数"""
    print("生成0000005c字段注释...")
    save_field_comments()
    print("完成!")

if __name__ == "__main__":
    main()








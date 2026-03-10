import json
import os


def parse_hero_info(hero_str):
    """解析英雄信息字符串（如attack_all_hero_info）"""
    heroes = []
    for hero_data in hero_str.split(';'):
        if not hero_data: continue
        parts = hero_data.split(',')
        if len(parts) >= 5:
            heroes.append({
                "hero_id": parts[0],
                "level": parts[1],
                "current_hp": parts[2],
                "max_hp": parts[3],
                "unknown": parts[4]  # 可能需要根据游戏规则解析
            })
    return heroes


def parse_skills(skill_str):
    """解析技能信息字符串（如all_skill_info）"""
    skills = []
    for slot_data in skill_str.split(';'):
        if not slot_data: continue
        parts = slot_data.split(',')
        if len(parts) >= 6:
            skills.append({
                "slot": parts[0],
                "skill_1": parts[1],
                "skill_1_level": parts[2],
                "skill_2": parts[3],
                "skill_2_level": parts[4],
                "skill_3": parts[5],
                "skill_3_level": parts[6] if len(parts) > 6 else 0
            })
    return skills


from typing import Tuple, List, Dict


def parse_skills(skill_str: str) -> Tuple[List[Dict], List[Dict]]:
    """解析技能字符串，分离攻击方和防守方技能"""
    attack_skills = []
    defend_skills = []

    skill_slots = skill_str.split(';')
    for slot in skill_slots:
        if not slot.strip():
            continue
        parts = slot.split(',')
        if len(parts) < 7:  # 至少包含 slot_num + 3个技能（每个技能占2字段）
            continue

        slot_num = parts[0]
        skills = []
        for i in range(1, len(parts), 2):  # 遍历技能ID和等级
            skill_id = parts[i] if i < len(parts) else "0"
            skill_level = parts[i + 1] if i + 1 < len(parts) else "0"
            skills.append({
                "id": skill_id,
                "level": skill_level
            })

        # 假设槽位1-3为攻击方，4-6为防守方
        if int(slot_num) <= 3:
            attack_skills.append({"slot": slot_num, "skills": skills})
        else:
            defend_skills.append({"slot": slot_num, "skills": skills})

    return attack_skills, defend_skills

def extract_battle_data(json_data):
    """主提取函数 - 适应新的数据结构"""
    battles = []

    for battle_group in json_data:
        # 每个战斗组的结构：[战斗详情dict]
        battle_detail = battle_group[0]

        # 提取基础信息
        battle = {
            "battle_id": battle_detail.get("battle_id"),
            "time": battle_detail.get("time"),
            "result": "攻击方胜利" if battle_detail.get("result") == 1 else "防守方胜利" if battle_detail.get("result") == 0 else f"结果{battle_detail.get('result')}",
            "location": f"{battle_detail.get('wid_name')} (ID: {battle_detail.get('wid')})",
            "attack_union": battle_detail.get("attack_union_name"),
            "attack_name": battle_detail.get("attack_name"),
            "defend_union": battle_detail.get("defend_union_name"),
            "defend_name": battle_detail.get("defend_name"),
            "battle_type": battle_detail.get("battle_type", "未知类型"),
            "fight_type": battle_detail.get("fight_type"),
            "city_type": battle_detail.get("city_type"),
            "attack_hp": battle_detail.get("attack_hp"),
            "defend_hp": battle_detail.get("defend_hp"),
            "attack_heroes": parse_hero_info(battle_detail.get("attack_all_hero_info", "")),
            "defend_heroes": parse_hero_info(battle_detail.get("defend_all_hero_info", "")),
        }
        
        # 解析技能信息
        attack_skills, defend_skills = parse_skills(battle_detail.get("all_skill_info", ""))
        battle["attack_skills"] = attack_skills
        battle["defend_skills"] = defend_skills

        battles.append(battle)

    return battles


def replace_ids_with_names(data, hero_map, skill_map):
    """替换英雄和技能ID为名称"""
    # 提取真正的战斗数据（位于列表的第一个元素）
    for battle_detail in data:
        # 替换英雄ID为名称
        for role in ["attack_heroes", "defend_heroes"]:
            if role in battle_detail:
                for hero in battle_detail[role]:
                    hero_id = hero.get("hero_id", "0")
                    if hero_id != "0":
                        hero["hero_name"] = hero_map.get(hero_id, {}).get("alt", "未知英雄")
                    else:
                        hero["hero_name"] = "空位"
                    if "hero_id" in hero:
                        del hero["hero_id"]

        # 替换技能ID为名称
        for role in ["attack_skills", "defend_skills"]:
            if "attack_skills" in battle_detail:
                for skill in battle_detail["attack_skills"]:
                    for i in range(1, 4):  # 处理skill_1到skill_3
                        skill_key = f"skill_{i}"
                        skill_id = skill.get(skill_key, "0")
                        if skill_id != "0":
                            skill_name = skill_map.get(skill_id, {}).get("alt", "未知技能")
                            skill[f"{skill_key}_name"] = skill_name
                            del skill[skill_key]
                        else:
                            skill[f"{skill_key}_name"] = "无技能"

    return data


import csv
import json
from typing import List, Dict


def flatten_battle_data(battle: Dict) -> List[Dict]:
    """将单场战斗数据扁平化为CSV可用的行"""
    rows = []

    # 基础信息
    base_info = {
        "battle_id": battle.get("battle_id", ""),
        "time": battle.get("time", ""),
        "result": battle.get("result", ""),
        "location": battle.get("location", ""),
        "battle_type": battle.get("battle_type", ""),
        "fight_type": battle.get("fight_type", ""),
        "city_type": battle.get("city_type", ""),
        "attack_union": battle.get("attack_union", ""),
        "attack_name": battle.get("attack_name", ""),
        "attack_hp": battle.get("attack_hp", ""),
        "defend_union": battle.get("defend_union", ""),
        "defend_name": battle.get("defend_name", ""),
        "defend_hp": battle.get("defend_hp", "")
    }

    # 处理攻击方英雄（最多3个）
    attack_heroes = battle.get("attack_heroes", [])
    for i in range(3):
        hero = attack_heroes[i] if i < len(attack_heroes) else {}
        base_info.update({
            f"attack_hero_{i + 1}_name": hero.get("hero_name", ""),
            f"attack_hero_{i + 1}_level": hero.get("level", ""),
            f"attack_hero_{i + 1}_hp": f"{hero.get('current_hp', '')}/{hero.get('max_hp', '')}"
        })

    # 处理防守方英雄（最多3个）
    defend_heroes = battle.get("defend_heroes", [])
    for i in range(3):
        hero = defend_heroes[i] if i < len(defend_heroes) else {}
        base_info.update({
            f"defend_hero_{i + 1}_name": hero.get("hero_name", ""),
            f"defend_hero_{i + 1}_level": hero.get("level", ""),
            f"defend_hero_{i + 1}_hp": f"{hero.get('current_hp', '')}/{hero.get('max_hp', '')}"
        })

    # 处理攻击方技能（最多3个槽位）
    attack_skills = battle.get("attack_skills", [])
    for i in range(3):
        slot = attack_skills[i] if i < len(attack_skills) else {}
        skills = slot.get("skills", [])
        base_info[f"attack_skill_slot_{i + 1}"] = "; ".join(
            [f"{s.get('id', '')} (Lv.{s.get('level', '')})" for s in skills]
        )
    
    # 处理防守方技能（最多3个槽位）
    defend_skills = battle.get("defend_skills", [])
    for i in range(3):
        slot = defend_skills[i] if i < len(defend_skills) else {}
        skills = slot.get("skills", [])
        base_info[f"defend_skill_slot_{i + 1}"] = "; ".join(
            [f"{s.get('id', '')} (Lv.{s.get('level', '')})" for s in skills]
        )

    rows.append(base_info)
    return rows


def save_to_csv(data: List[Dict], filename: str):
    """将数据保存为CSV"""
    if not data:
        return

    # 提取所有可能的字段（确保顺序一致）
    fieldnames = list(data[0].keys())

    with open(filename, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(data)


# 使用示例
# 示例用法
if __name__ == "__main__":
    # 存储所有战斗数据
    all_battle_data = []
    
    # 处理个人战报 (0000000a)
    personal_battle_dir = "decompressed_data_report/0000000a"
    if os.path.exists(personal_battle_dir):
        print("处理个人战报...")
        personal_files = 0
        personal_battles = 0
        for file in os.listdir(personal_battle_dir):
            if file.endswith(".json"):
                file_path = os.path.join(personal_battle_dir, file)
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        file_data = json.load(f)
                        if isinstance(file_data, list) and len(file_data) > 1:
                            # 个人战报结构：[数字, [战斗详情1, 战斗详情2, ...]]
                            battles_in_file = file_data[1]
                            for battle_detail in battles_in_file:
                                if isinstance(battle_detail, dict):
                                    battle_detail["battle_type"] = "个人战报"
                                    all_battle_data.append([battle_detail])
                                    personal_battles += 1
                            personal_files += 1
                except Exception as e:
                    print(f"处理个人战报文件 {file} 时出错: {e}")
        print(f"个人战报处理完成，共 {personal_files} 个文件，{personal_battles} 个战斗记录")
    
    # 处理同盟战报 (0000005c)
    alliance_battle_dir = "decompressed_data_report/0000005c"
    if os.path.exists(alliance_battle_dir):
        print("处理同盟战报...")
        alliance_files = 0
        alliance_battles = 0
        for file in os.listdir(alliance_battle_dir):
            if file.endswith(".json"):
                file_path = os.path.join(alliance_battle_dir, file)
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        file_data = json.load(f)
                        if isinstance(file_data, list) and len(file_data) > 0:
                            # 同盟战报结构：[[战斗详情1, 战斗详情2, ...], 其他数据...]
                            if isinstance(file_data[0], list):
                                battles_in_file = file_data[0]
                                for battle_detail in battles_in_file:
                                    if isinstance(battle_detail, dict):
                                        battle_detail["battle_type"] = "同盟战报"
                                        all_battle_data.append([battle_detail])
                                        alliance_battles += 1
                            else:
                                # 单个战斗记录
                                battle_detail = file_data[0]
                                if isinstance(battle_detail, dict):
                                    battle_detail["battle_type"] = "同盟战报"
                                    all_battle_data.append([battle_detail])
                                    alliance_battles += 1
                            alliance_files += 1
                except Exception as e:
                    print(f"处理同盟战报文件 {file} 时出错: {e}")
        print(f"同盟战报处理完成，共 {alliance_files} 个文件，{alliance_battles} 个战斗记录")
    
    print(f"总共处理了 {len(all_battle_data)} 个战斗数据")
    
    # 加载映射数据
    try:
        with open('metadata.json', 'r', encoding='utf-8') as r:
            HERO_MAP = json.load(r)
        print("英雄映射数据加载成功")
    except FileNotFoundError:
        print("警告: metadata.json 文件不存在，将使用ID而非名称")
        HERO_MAP = {}
    
    try:
        with open('skill.json', 'r', encoding='utf-8') as f:
            SKILL_MAP = json.load(f)
        print("技能映射数据加载成功")
    except FileNotFoundError:
        print("警告: skill.json 文件不存在，将使用ID而非名称")
        SKILL_MAP = {}
    
    # 提取战斗数据
    battle_data = extract_battle_data(all_battle_data)
    print(f"提取到 {len(battle_data)} 个战斗记录")
    
    # 替换ID为名称
    modified_data = replace_ids_with_names(battle_data, HERO_MAP, SKILL_MAP)
    
    # 扁平化数据并保存为CSV
    all_rows = []
    for battle in modified_data:
        all_rows.extend(flatten_battle_data(battle))
    
    # 保存为CSV
    output_filename = "battles_report_updated.csv"
    save_to_csv(all_rows, output_filename)
    print(f"CSV文件已生成: {output_filename}")
    print(f"共生成 {len(all_rows)} 行战斗记录")

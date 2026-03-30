# -*- coding: utf-8 -*-
"""
战斗计算方法 - 参考 stzbBattleSimulator 实现
"""
import math
import random


def keep_two_decimal(v):
    return round(v, 2)


def round_eight_nine(v):
    """四舍五入到整数"""
    return round(v)


# ─── 随机数（模拟原版洗牌算法）
def get_random_bool(probability):
    """probability 为百分比整数，如 35 表示 35%"""
    if probability is None or probability == '--':
        return True
    results = [i < probability for i in range(100)]
    random.shuffle(results)
    return results[random.randint(0, 99)]


def get_random_int(min_val, max_val):
    return random.randint(int(min_val), int(max_val))


def _get_rand_num():
    """攻击随机数 0.30-0.39"""
    rand = [0.30, 0.31, 0.32, 0.33, 0.34, 0.35, 0.36, 0.37, 0.38, 0.39]
    return random.choice(rand)


# ─── 攻防差计算
def _calc_atk_def_diff(atk, def_):
    diff = atk - def_
    if diff >= 0:
        return keep_two_decimal(3 - (500 / (250 + diff)))
    else:
        return keep_two_decimal(100 / (100 - diff))


# ─── 谋略影响系数
def _calc_inte_effect(inte):
    inte = int(inte)
    if inte <= 50:
        return 1.0
    else:
        return math.ceil(100 - (75 - (9375 / (75 + inte)))) / 100


# ─── 恢复率计算
def _calc_recover_rate(int_val, rate, grow_rate):
    int_val -= 80
    addition_rate = math.floor(int_val * grow_rate)
    return keep_two_decimal((rate + addition_rate) / 100)


def calc_recover(self_hero, rate, grow_rate, type_=1):
    if type_ == 1:
        base_arms = self_hero.origin_attrs['arms']
        base_int  = self_hero.origin_attrs['int']
        main = math.floor(round((base_arms * 300) / (3500 + base_arms)) *
                          _calc_recover_rate(base_int, rate, grow_rate))
    else:
        base_arms = self_hero.arms
        base_int  = self_hero.attrs['int']
        main = math.floor(round((base_arms * 300) / (3500 + base_arms)) *
                          _calc_recover_rate(base_int, rate, grow_rate))
    return main


# ─── 战法加成率计算
def calc_skill_addition_rate(base, grow, value):
    if value < 80:
        return round(base * 0.4 + (base * 0.6) * (value / 80))
    else:
        return round(base + grow * (value - 80))


# ─── 伤害增减汇总
def _get_damage_addition(attacker, target, dmg_type, skill):
    value = 100
    if dmg_type in (1, 3):  # 物理
        value += attacker.get_damage_state_value('attackDamageAdd')
        value -= attacker.get_damage_state_value('attackDamageSub')
        value += target.get_damage_state_value('beAttackDamageAdd')
        value -= target.get_damage_state_value('beAttackDamageSub')
    elif dmg_type in (2, 4):  # 策略
        value += attacker.get_damage_state_value('inteDamageAdd')
        value -= attacker.get_damage_state_value('inteDamageSub')
        value += target.get_damage_state_value('beInteDamageAdd')
        value -= target.get_damage_state_value('beInteDamageSub')

    if skill and getattr(skill, 'skill_type', None) == 2:  # 主动战法
        value += attacker.get_damage_state_value('activeDamageAdd')
        value -= attacker.get_damage_state_value('activeDamageSub')
        value += target.get_damage_state_value('beActiveDamageAdd')
        value -= target.get_damage_state_value('beActiveDamageSub')

    if value < 10:
        value = 10
    return value / 100


# ─── 物理伤害
def calc_attack_damage(attacker, target, dmg_info, skill=None):
    addition = _get_damage_addition(attacker, target, dmg_info['type'], skill)
    rate = dmg_info['rate']
    arms_damage = (attacker.arms * 373) / (7700 + attacker.arms)
    basic_damage = attacker.attrs['atk'] * _get_rand_num() * (rate / 100) * addition
    target_def = 0 if (skill and getattr(skill, 'id', 0) == 1011) else target.attrs['def']
    atk_def_diff = _calc_atk_def_diff(attacker.attrs['atk'], target_def)
    main_damage = ((300 * attacker.arms) / (3500 + attacker.arms)) * (rate / 100) * addition * atk_def_diff
    return round(arms_damage + basic_damage + main_damage)


# ─── 策略伤害（火攻）
def calc_inte_damage(attacker, target, dmg_info, skill=None):
    addition    = _get_damage_addition(attacker, target, dmg_info['type'], skill)
    rate        = dmg_info['rate']
    inte_effect = _calc_inte_effect(target.attrs['int'])
    arms_damage  = (attacker.arms * 178) / (6459 + attacker.arms)
    basic_damage = attacker.attrs['int'] * 0.5 * addition * inte_effect
    main_damage  = ((300 * attacker.arms) / (3500 + attacker.arms)) * (rate / 100) * addition * inte_effect
    return round(arms_damage + basic_damage + main_damage)


# ─── 燃烧/恐慌/妖术伤害
def calc_inte2_damage(attacker, target, dmg_info, skill=None):
    addition    = _get_damage_addition(attacker, target, dmg_info['type'], skill)
    rate        = dmg_info['rate']
    inte_effect = _calc_inte_effect(target.attrs['int'])
    arms_damage  = ((attacker.arms * 178) / (6459 + attacker.arms)) * (1 / 3)
    basic_damage = attacker.attrs['int'] * 0.25 * addition * inte_effect
    main_damage  = ((300 * attacker.arms) / (3500 + attacker.arms)) * (rate / 100) * addition * inte_effect
    return round(arms_damage + basic_damage + main_damage)

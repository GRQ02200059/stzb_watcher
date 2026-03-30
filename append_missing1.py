# -*- coding: utf-8 -*-
code = '''

# --- 200184 百战老兵
class Skill200184(Skill):
    def __init__(self):
        super().__init__(200184,'百战老兵','使自身攻击、防御、谋略、速度全属性+32',1,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(32, 0.25, hero.attrs['int'])
        for k in ('atk','def','int','spd'):
            hero.attrs[k] = keep_two_decimal(hero.attrs[k] + v)


# --- 200210 远交近攻
class Skill200210(Skill):
    def __init__(self):
        super().__init__(200210,'远交近攻','使自身攻击+20防御+20攻击距离+1，并使友军全体战斗开始前3回合也获得相同增益',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(20, 0.16, hero.attrs['int'])
        hero.attrs['atk'] = keep_two_decimal(hero.attrs['atk'] + v)
        hero.attrs['def'] = keep_two_decimal(hero.attrs['def'] + v)
        hero.attrs['limit'] = hero.attrs.get('limit', 3) + 1
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        stk = [3]
        def sub():
            if stk[0] > 0:
                stk[0] -= 1
                for e in team:
                    e.attrs['atk'] = keep_two_decimal(e.attrs['atk'] + v)
                    e.attrs['def'] = keep_two_decimal(e.attrs['def'] + v)
            else:
                hero.clear_hook('行动时', f'{sk.id}_buff')
        hero.add_hook('行动时', f'{sk.id}_buff', sub, sk, hero)


# --- 200215 矢志
class Skill200215(Skill):
    def __init__(self):
        super().__init__(200215,'矢志','战斗开始前2回合，受到伤害时70%几率使自身进入免疫状态，抵消该次伤害',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        def on_hurt(a, di, s):
            if hero.manager.round <= 2 and get_random_bool(70):
                di['immune'] = True
        hero.add_hook('受伤时', f'{sk.id}_immune', on_hurt, sk, hero)


# --- 200646 纵横疾驰
class Skill200646(Skill):
    def __init__(self):
        super().__init__(200646,'纵横疾驰','战斗中能够进行3回合开始普通攻击后目标概率40%几率使其怯战1回合',4,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        def on_atk(t, di, s2, dmg):
            if hero.manager.round >= 3 and get_random_bool(40):
                if not t.is_attack_limit():
                    t.state['attackLimit'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        hero.add_hook('造成伤害后', f'{sk.id}_act', on_atk, sk, hero)


# --- 200833 鸣镝决胜
class Skill200833(Skill):
    def __init__(self):
        super().__init__(200833,'鸣镝决胜','1回合准备，对敌军群体2目标攻击(330%)；对敌军全体3目标攻击(225%)',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r1 = calc_skill_addition_rate(330, 3.1, hero.attrs['atk'])
        r2 = calc_skill_addition_rate(225, 2.1, hero.attrs['atk'])
        def subskill():
            for t in hero.get_target(sk.limit, 2):
                t.be_hurt(hero, {'type': 1, 'rate': r1}, sk)
            for t in hero.get_target(sk.limit, 3):
                t.be_hurt(hero, {'type': 1, 'rate': r2}, sk)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# --- 200853 胭脂计
class Skill200853(Skill):
    def __init__(self):
        super().__init__(200853,'胭脂计','我方3武将为女将时大营受到伤害+14%；每回合行动前使我方最弱友军谋略攻击(60%)；前4回合受到伤害时进入免疫状态',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        rate = calc_skill_addition_rate(60, 0.55, hero.attrs['int'])
        dv = calc_skill_addition_rate(14, 0.11, hero.attrs['int'])
        # 前4回合免疫
        def on_hurt(a, di, s):
            if hero.manager.round <= 4:
                di['immune'] = True
                hero.clear_hook('受伤时', f'{sk.id}_imm')
        hero.add_hook('受伤时', f'{sk.id}_imm', on_hurt, sk, hero)
        # 每回合行动前攻击最弱友军
        def sub():
            alive = [e for e in team if e.arms > 0 and e is not hero]
            if alive:
                weakest = min(alive, key=lambda e: e.arms)
                weakest.be_hurt(hero, {'type': 2, 'rate': rate}, sk)
        hero.add_hook('行动前', f'{sk.id}_act', sub, sk, hero)


# --- 200929 苦肉妙计
class Skill200929(Skill):
    def __init__(self):
        super().__init__(200929,'苦肉妙计','使友军全体造成的普通攻击伤害+36%持续2回合；使2名友军进入无双状态伤害+55%',2,120,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v1 = calc_skill_addition_rate(36, 0.29, hero.attrs['int'])
        v2 = calc_skill_addition_rate(55, 0.46, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('attackDamageAdd', v1, 2, sk, hero)
        alive = [e for e in team if e.arms > 0]
        targets = alive[:2]
        for e in targets:
            e.add_state('activeDamageAdd', v2, 2, sk, hero)
            e.add_state('attackDamageAdd', v2, 2, sk, hero)
            e.add_state('inteDamageAdd', v2, 2, sk, hero)
        return True


# --- 200935 直射穿甲
class Skill200935(Skill):
    def __init__(self):
        super().__init__(200935,'直射穿甲','每回合首次造成伤害使目标单体受到伤害+10%；首次受到伤害使友军受到同类伤害+10%，最多6次，贯穿至战斗结束',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self; stk_atk=[0]; stk_def=[0]
        v = calc_skill_addition_rate(10, 0.08, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        def on_dmg(t, di, s2, dmg):
            if stk_atk[0] < 6:
                stk_atk[0] += 1
                k = 'beAttackDamageAdd' if di.get('type') == 1 else 'beInteDamageAdd'
                t.add_state(k, v, -1, sk, hero, True)
        def on_hurt(a, di, s):
            if stk_def[0] < 6:
                stk_def[0] += 1
                k = 'beAttackDamageSub' if di.get('type') == 1 else 'beInteDamageSub'
                for e in team:
                    e.add_state(k.replace('Sub','Add'), v, -1, sk, hero, True)
        hero.add_hook('造成伤害后', f'{sk.id}_atk', on_dmg, sk, hero)
        hero.add_hook('受伤时', f'{sk.id}_def', on_hurt, sk, hero)
'''
with open('battle_sim/skills_a.py', 'a', encoding='utf-8') as f:
    f.write(code)
print('batch1 ok')


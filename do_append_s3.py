# -*- coding: utf-8 -*-
# 最终追加：200987-201006 + SKILLS_S 注册表

code = '''
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        atk_r = calc_skill_addition_rate(180, 1.7, hero.attrs['atk'])
        int_r = calc_skill_addition_rate(100, 0.9, hero.attrs['int'])
        def on_dmg(t, di, s2, dmg):
            if random.random() < 0.5:
                targets = hero.get_target(sk.limit, 1)
                if targets:
                    targets[0].be_hurt(hero, {'type': 1, 'rate': atk_r}, sk)
                hero.attrs['atk'] = keep_two_decimal(hero.attrs['atk'] + 10)
            else:
                targets = hero.get_target(sk.limit, 2)
                for tt in targets:
                    tt.be_hurt(hero, {'type': 2, 'rate': int_r}, sk)
                    tt.attrs['int'] = keep_two_decimal(max(0, tt.attrs['int'] - 5))
        hero.add_hook('造成伤害后', f'{sk.id}_proc', on_dmg, sk, hero)


# ─── 200991 潜谋远计
class Skill200991(Skill):
    def __init__(self):
        super().__init__(200991,'潜谋远计','前4回合受伤时75%恢复(100%)并谋略+15；第5回合起行动时对谋略低于自身的敌军全体75%谋略攻击(120%)',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rv = calc_skill_addition_rate(100, 0.9, hero.attrs['int'])
        iv = calc_skill_addition_rate(15, 0.12, hero.attrs['int'])
        av = calc_skill_addition_rate(120, 1.1, hero.attrs['int'])
        def on_hurt(a, di, s):
            if hero.manager.round <= 4 and get_random_bool(hero.get_real_skill_rate(75)):
                hero.recover(calc_recover(hero, rv, 0.9, 2), hero, sk.name)
                hero.attrs['int'] = keep_two_decimal(hero.attrs['int'] + iv)
        def on_act():
            if hero.manager.round >= 5 and get_random_bool(hero.get_real_skill_rate(75)):
                enemy = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
                for e in [x for x in enemy if x.arms > 0 and x.attrs['int'] < hero.attrs['int']]:
                    e.be_hurt(hero, {'type': 2, 'rate': av}, sk)
        hero.add_hook('受伤时', f'{sk.id}_hurt', on_hurt, sk, hero)
        hero.add_hook('行动时', f'{sk.id}_act', on_act, sk, hero)


# ─── 200993 舍身卫主
class Skill200993(Skill):
    def __init__(self):
        super().__init__(200993,'舍身卫主','受距离2以内敌军伤害时60%反击(120%)；前锋/中军时前3回合承担友军全体攻击伤害',4,'--',False,2)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        counter_r = calc_skill_addition_rate(120, 1.1, hero.attrs['atk'])
        def on_hurt(attacker, di, s):
            if get_random_bool(hero.get_real_skill_rate(60)):
                attacker.be_hurt(hero, {'type': 1, 'rate': counter_r}, sk)
        hero.add_hook('受伤时', f'{sk.id}_counter', on_hurt, sk, hero)
        if hero.pos_name in ('前锋', '中军'):
            team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
            for e in [x for x in team if x is not hero]:
                def ms(e=e):
                    def oh(a, di, s):
                        if hero.manager.round <= 3 and di.get('type') == 1:
                            dmg = di.get('damage', 0)
                            di['immune'] = True
                            hero.be_hurt_by_num(a, {'type': 1, 'rate': 100}, sk, dmg, lambda: None)
                    e.add_hook('受伤时', f'{sk.id}_guard_{id(e)}', oh, sk, hero)
                ms()


# ─── 201006 同仇敌忾
class Skill201006(Skill):
    def __init__(self):
        super().__init__(201006,'同仇敌忾','我军全体每次受伤后其距离1以内友军受伤-2%且主动战法伤害+2%最多叠加10次',1,'--',False,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(2, 0.015, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        stacks = {id(e): 0 for e in team}
        for e in team:
            def ms(e=e):
                def oh(a, di, s):
                    if stacks[id(e)] >= 10: return
                    stacks[id(e)] += 1
                    for ally in team:
                        ally.add_state('beAttackDamageSub', v, -1, sk, hero, True)
                        ally.add_state('beInteDamageSub', v, -1, sk, hero, True)
                        ally.add_state('activeDamageAdd', v, -1, sk, hero, True)
                e.add_hook('受伤时', f'{sk.id}_{id(e)}', oh, sk, hero)
            ms()


# ──────────────── SKILLS_S 

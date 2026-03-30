# -*- coding: utf-8 -*-
code = '''
        val = calc_skill_addition_rate(25, 0.18, hero.attrs['atk'])
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('attackDamageAdd', val, -1, sk, hero)
        stack = [0]
        def on_atk_done(t, dmg_info, skill, damage):
            stack[0] += 1
            if stack[0] >= 4:
                stack[0] = 0
                if hero.state.get('doubleAttack',{}).get('rounds',0) <= 0:
                    hero.state['doubleAttack'] = {'rounds':1,'from':{'hero':hero,'skill':sk}}
        hero.add_hook('造成伤害后', f'{sk.id}_stack', on_atk_done, sk, hero)


class Skill200257(Skill):
    def __init__(self):
        super().__init__(200257,'审时定计','敌军被施加负面效果时50%提升受到伤害30%；我军被施加负面效果时50%抵御',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        val = calc_skill_addition_rate(30, 0.2, hero.attrs['int'])
        rv  = calc_skill_addition_rate(65, 0.6, hero.attrs['int'])
        enemy_team = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        for e in enemy_team:
            def make_e(e=e):
                def on_debuff(skill, src):
                    if get_random_bool(hero.get_real_skill_rate(50)):
                        e.add_state('beAttackDamageAdd', val, 1, sk, hero)
                        e.add_state('beInteDamageAdd',   val, 1, sk, hero)
                        hero.recover(calc_recover(hero, rv, 0.6, 2), hero, sk.name)
                e.add_hook('被施加负面效果时', f'{sk.id}_e_{id(e)}', on_debuff, sk, hero)
            make_e()


class Skill200262(Skill):
    def __init__(self):
        super().__init__(200262,'僭号天子','我军全体受到伤害的32%由玉玺承担，第2回合起每回合玉玺伤害反弹给自身',1,'--',False,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        sub_rate = calc_skill_addition_rate(32, 0.28, hero.attrs['def'])
        shield = [0]
        ratio  = [0.8]
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            def make_sub(e=e):
                def on_hurt(attacker, dmg_info, skill):
                    absorbed = int(dmg_info.get('damage', 0) * sub_rate / 100)
                    shield[0] += absorbed
                e.add_hook('受伤时', f'{sk.id}_{id(e)}', on_hurt, sk, hero)
            make_sub()
        def on_round():
            if hero.manager.round >= 2 and shield[0] > 0:
                dmg = int(shield[0] * ratio[0])
                shield[0] = 0
                ratio[0] = min(1.0, ratio[0] + 0.05)
                hero.be_hurt_by_num(hero, {'type':3,'rate':100}, sk, dmg, lambda: None)
        hero.add_hook('回合开始时', f'{sk.id}_reflect', on_round, sk, hero)


class Skill200264(Skill):
    def __init__(self):
        super().__init__(200264,'疲兵沮意','每回合为我军叠加2层避锐，每层减伤10%，生效后30%几率燃烧敌军',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        val = calc_skill_addition_rate(10, 0.08, hero.attrs['int'])
        fire_rate = calc_skill_addition_rate(150, 1.2, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        stacks = {id(e): 0 for e in team}
        for e in team:
            def make_sub(e=e):
                def on_round():
                    stacks[id(e)] = min(stacks[id(e)] + 2, 10)
                def on_hurt(attacker, dmg_info, skill):
                    if stacks[id(e)] > 0:
                        stacks[id(e)] -= 1
                        if get_random_bool(hero.get_real_skill_rate(30)) and attacker and attacker.arms > 0:
                            attacker.be_hurt(hero, {'type':4,'rate':fire_rate}, sk)
                e.add_hook('回合开始时', f'{sk.id}_s_{id(e)}', on_round, sk, hero)
                e.add_hook('受伤时',     f'{sk.id}_h_{id(e)}', on_hurt,  sk, hero)
            make_sub()


class Skill200603(Skill):
    def __init__(self):
        super().__init__(200603,'白楼独舞','前3回合敌军群体攻谋伤害降低26%，结束后敌军暴走2回合',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        val = calc_skill_addition_rate(26, 0.15, hero.attrs['int'])
        enemy = hero.get_target(4, 2)
        for e in enemy:
            e.add_state('attackDamageSub', val, 3, sk, hero)
            e.add_state('inteDamageSub',   val, 3, sk, hero)
        def apply_rage():
            if hero.manager.round == 4:
                for e in enemy:
                    if e.arms > 0 and not e.is_confusion():
                        e.state['confusion'] = {'rounds':2,'from':{'hero':hero,'skill':sk}}
                hero.clear_hook('回合开始时', f'{sk.id}_rage')
        hero.add_hook('回合开始时', f'{sk.id}_rage', apply_rage, sk, hero)


class Skill200622(Skill):
    def __init__(self):
        super().__init__(200622,'不攻','无法普攻，谋略伤害+25%，每回合对敌军单体谋略攻击(伤害率83%)',1,'--',False,1)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        hero.state['attackLimit'] = {'rounds':-1,'from':{'hero':hero,'skill':sk}}
        val_add = calc_skill_addition_rate(25, 0.18, hero.attrs['int'])
        val_atk = calc_skill_addition_rate(83, 0.75, hero.attrs['int'])
        hero.add_state('inteDamageAdd', val_add, -1, sk, hero)
        def sub():
            targets = hero.get_target(5, 1)
            if targets:
                targets[0].be_hurt(hero, {'type':2,'rate':val_atk}, sk)
        hero.add_hook('行动时', f'{sk.id}_auto', sub, sk, hero)


class Skill200648(Skill):
    def __init__(self):
        super().__init__(200648,'白衣渡江','前2回合敌军群体无法普攻，结束后对敌军全体强力谋略攻击(伤害率215%)',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        val = calc_skill_addition_rate(215, 2.25, hero.attrs['int'])
        enemy = hero.get_target(5, 2)
        for e in enemy:
            if not e.is_attack_limit():
                e.state['attackLimit'] = {'rounds':2,'from':{'hero':hero,'skill':sk}}
            dmg_info = {'type':2,'rate':val}
            damage = calc_inte_damage(hero, e, dmg_info, sk)
            def make_hook(e=e, damage=damage, dmg_info=dmg_info):
                def on_action():
                    if hero.manager.round == 3:
                        e.be_hurt_by_num(hero, dmg_info, sk, damage, lambda: None)
                        e.clear_hook('行动时', f'{sk.id}_{id(e)}')
                e.add_hook('行动时', f'{sk.id}_{id(e)}', on_action, sk, hero, 'debuff')
            make_hook()


class Skill200687(Skill):
    def __init__(self):
        super().__init__(200687,'始计','前4回合我军大营攻谋伤害+20%，敌方兵力最多单体伤害-30%',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        add_rate = calc_skill_addition_rate(20, 0.15, hero.attrs['int'])
        sub_rate = calc_skill_addition_rate(30, 0.25, hero.attrs['int'])
        def sub():
            if hero.manager.round > 4: return
            my_team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
            enemy   = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
            for e in my_team:
                if e.pos_name == '大营' and e.arms > 0:
                    e.add_state('attackDamageAdd', add_rate, 1, sk, hero, False, 2)
                    e.add_state('inteDamageAdd',   add_rate, 1, sk, hero, False, 2)
            alive = [e for e in enemy if e.arms > 0]
            if alive:
                top = max(alive, key=lambda e: e.arms)
                top.add_state('attackDamageSub', sub_rate, 1, sk, hero, False, 2)
                top.add_state('inteDamageSub',   sub_rate, 1, sk, hero, False, 2)
        hero.add_hook('行动前', f'{sk.id}_始计', sub, sk, hero)


class Skill200737(Skill):
    def __init__(self):
        super().__init__(200737,'明其虚实','敌军全体谋略每回合降低6%，前2回合敌军群体犹豫',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        enemy_team = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        for e in enemy_team:
            if not e.is_active_limit():
                e.state['activeLimit'] = {'rounds':2,'from':{'hero':hero,'skill':sk}}
        def sub():
            for e in enemy_team:
                if e.arms > 0:
                    e.attrs['int'] = keep_two_decimal(max(0, e.attrs['int'] * 0.94))
        hero.add_hook('回合开始时', f'{sk.id}_debuff', sub, sk, hero)


class Skill200755(Skill):
    def __init__(self):
        super().__init__(200755,'攻其不备','敌军每受一次攻击伤害后所受伤害提高11.6%，最多叠加5次',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        val = calc_skill_addition_rate(11.6, 0.09, hero.attrs['spd'])
        enemy_team = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        stacks = {id(e): 0 for e in enemy_team}
        for e in enemy_team:
            def make_sub(e=e):
                def on_hurt(attacker, dmg_info, skill):
                    if dmg_info.get('type') == 1 and stacks[id(e)] < 5:
                        stacks[id(e)] += 1
                        e.add_state('beAttackDamageAdd', val, -1, sk, hero, True)
                        e.add_state('beInteDamageAdd',   val, -1, sk, hero, True)
                e.add_hook('受伤时', f'{sk.id}_{id(e)}', on_hurt, sk, hero, 'debuff')
            make_sub()


class Skill200757(Skill):
    def __init__(self):
        super().__init__(200757,'重整旗鼓','第5回合起每回合恢复我军群体兵力(恢复率140%)',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        rv = calc_skill_addition_rate(140, 1.2, hero.attrs['int'])
        def sub():
            if hero.manager.round >= 5:
                team = hero.get_target(3, 2, 2)
                for e in team:
                    e.recover(calc_recover(hero, rv, 1.2, 2), hero, sk.name)
        hero.add_hook('回合开始时', f'{sk.id}_重整', sub, sk, hero)


class Skill200773(Skill):
    def __init__(self):
        super().__init__(200773,'金匮要略','前3回合我军全体受到伤害降低20.4%，受伤时50%几率恢复兵力',1,'--',False,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        sub_val = calc_skill_addition_rate(20.4, 0.13, hero.attrs['int'])
        rv      = calc_skill_addition_rate(80,   0.75, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('beAttackDamageSub', sub_val, 3, sk, hero)
            e.add_state('beInteDamageSub',   sub_val, 3, sk, hero)
            def make_sub(e=e):
                def on_hurt(attacker, dmg_info, skill):
                    if get_random_bool(hero.get_real_skill_rate(50)):
                        e.recover(calc_recover(hero, rv, 0.75, 2), hero, sk.name)
                e.add_hook('受伤时', f'{sk.id}_{id(e)}', on_hurt, sk, hero)
            make_sub()


class Skill200800(Skill):
    def __init__(self):
        super().__init__(200800,'众谋不懈','每当试图发动主动/追击战法前40%几率对敌军单体谋略攻击(伤害率194%)',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        val = calc_skill_addition_rate(194, 1.8, hero.attrs['int'])
        def on_before_skill(skill):
            if get_random_bool(hero.get_real_skill_rate(40)):
                targets = hero.get_target(5, 1)
                if targets:
                    targets[0].be_hurt(hero, {'type':2,'rate':val}, sk)
        hero.add_hook('发动战法前', f'{sk.id}_众谋', on_before_skill, sk, hero)


class Skill200824(Skill):
    def __init__(self):
        

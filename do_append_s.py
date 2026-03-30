# -*- coding: utf-8 -*-
# 追加剩余S级战法到 skills_s.py

code = '''

# ─── 200938 三术奇谋
class Skill200938(Skill):
    def __init__(self):
        super().__init__(200938,'三术奇谋','1回合准备，对敌军单体3次谋略攻击(178%)，依次使攻/防/谋降低18，持续2回合',2,50,False,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        rate = calc_skill_addition_rate(178, 1.65, hero.attrs['int'])
        sub_v = calc_skill_addition_rate(18, 0.14, hero.attrs['int'])
        def subskill():
            targets = hero.get_target(sk.limit, 1)
            if not targets: return
            t = targets[0]
            for _ in range(3):
                t.be_hurt(hero, {'type': 2, 'rate': rate}, sk)
            for key in ('atk', 'def', 'int'):
                t.attrs[key] = keep_two_decimal(max(0, t.attrs[key] - sub_v))
                rl = [2]
                def restore(t=t, key=key, sv=sub_v, rl=rl):
                    def inner():
                        rl[0] -= 1
                        if rl[0] <= 0:
                            t.attrs[key] = keep_two_decimal(t.attrs[key] + sv)
                            t.clear_hook('回合开始时', f'{sk.id}_{key}_{id(t)}')
                    t.add_hook('回合开始时', f'{sk.id}_{key}_{id(t)}', inner, sk, hero)
                restore()
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200942 暴戾恣睢
class Skill200942(Skill):
    def __init__(self):
        super().__init__(200942,'暴戾恣睢','攻击伤害提高4%，每回合叠加；每回合行动时20%几率对敌全体攻击(150%)，几率每回合+5%',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate_v = calc_skill_addition_rate(150, 1.4, hero.attrs['atk'])
        hero.add_state('attackDamageAdd', 4, -1, sk, hero)
        rage_rate = [20]
        def sub():
            hero.add_state('attackDamageAdd', 4, -1, sk, hero, True)
            if get_random_bool(hero.get_real_skill_rate(rage_rate[0])):
                targets = hero.get_target(sk.limit, 2)
                for t in targets:
                    t.be_hurt(hero, {'type': 1, 'rate': rate_v}, sk)
            rage_rate[0] = min(100, rage_rate[0] + 5)
        hero.add_hook('行动时', f'{sk.id}_act', sub, sk, hero)


# ─── 200952 抚民励德
class Skill200952(Skill):
    def __init__(self):
        super().__init__(200952,'抚民励德','第2/4/6回合行动时我军全体谋略/防御+80并受伤-20%，持续2回合',1,'--',False,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        attr_v = calc_skill_addition_rate(80, 0.7, hero.attrs['int'])
        dmg_v = calc_skill_addition_rate(20, 0.15, hero.attrs['int'])
        def sub():
            if hero.manager.round in (2, 4, 6):
                team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
                for e in team:
                    e.attrs['int'] = keep_two_decimal(e.attrs['int'] + attr_v)
                    e.attrs['def'] = keep_two_decimal(e.attrs['def'] + attr_v)
                    e.add_state('beAttackDamageSub', dmg_v, 2, sk, hero)
                    e.add_state('beInteDamageSub', dmg_v, 2, sk, hero)
        hero.add_hook('行动时', f'{sk.id}_act', sub, sk, hero)


# ─── 200955 动如雷震
class Skill200955(Skill):
    def __init__(self):
        super().__init__(200955,'动如雷震','我军群体追击发动率提升100%，追击伤害提升40%，持续1回合',2,30,False,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        spd_v = calc_skill_addition_rate(40, 0.3, hero.attrs['spd'])
        team = hero.get_target(sk.limit, 2, 2)
        for e in team:
            e.add_state('chaseDamageAdd', spd_v, 1, sk, hero)
            e.rate_val[f'{sk.id}_chase_{id(e)}'] = {'value': 100, 'rounds': 1}
        return True


# ─── 200957 五兵之烈
class Skill200957(Skill):
    def __init__(self):
        super().__init__(200957,'五兵之烈','对敌军群体猛烈攻击(280%)，默认使目标防御降低30%持续2回合',2,35,False,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate = calc_skill_addition_rate(280, 2.6, hero.attrs['atk'])
        sub_v = calc_skill_addition_rate(30, 0.25, hero.attrs['atk'])
        targets = hero.get_target(sk.limit, 2)
        for t in targets:
            t.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
            if t.arms > 0:
                t.attrs['def'] = keep_two_decimal(t.attrs['def'] * (1 - sub_v / 100))
        return True


# ─── 200960 鸾凤和鸣
class Skill200960(Skill):
    def __init__(self):
        super().__init__(200960,'鸾凤和鸣','每回合首次发动主动后我军群体2目标恢复(85%)；行动时我军全体3目标控制效果额外对1目标生效',1,'--',False,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rv = calc_skill_addition_rate(85, 0.75, hero.attrs['int'])
        fired = [False]
        def on_rnd(): fired[0] = False
        def on_act():
            if not fired[0]:
                fired[0] = True
                targets = hero.get_target(3, 1, 2)
                for t in targets:
                    t.recover(calc_recover(hero, rv, 0.75, 2), hero, sk.name)
        hero.add_hook('行动前', f'{sk.id}_act', on_act, sk, hero)
        hero.add_hook('回合开始时', f'{sk.id}_rst', on_rnd, sk, hero)


# ─── 200961 奋疾先登 -> 复用 Skill1009
class Skill200961(Skill):
    def __init__(self):
        super().__init__(200961,'奋疾先登','每回合行动时攻击伤害提升8%，达40%时群体攻击并重置',1,'--',False,5)
    def call(self, hero, target=None):
        from .skills import Skill1009
        obj = Skill1009(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200966 尽言直谏
class Skill200966(Skill):
    def __init__(self):
        super().__init__(200966,'尽言直谏','每回合行动时随机令友方2个主动战法下次发动率+10%伤害+30%',1,'--',False,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        def sub():
            team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
            alive = [x for x in team if x.arms > 0]
            chosen = random.sample(alive, min(2, len(alive)))
            for e in chosen:
                e.rate_val[f'{sk.id}_{id(e)}'] = {'value': 10, 'rounds': 1}
                e.add_state('activeDamageAdd', 30, 1, sk, hero)
        hero.add_hook('行动时', f'{sk.id}_act', sub, sk, hero)


# ─── 200979 及锋而试
class Skill200979(Skill):
    def __init__(self):
        super().__init__(200979,'及锋而试','对敌军群体攻击(120%)并使其士气降低10，每次发动伤害率+40%',2,35,False,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        hero.storage = getattr(hero, 'storage', {})
        extra = hero.storage.get(str(sk.id), 0)
        rate = calc_skill_addition_rate(120 + extra, 1.1, hero.attrs['atk'])
        targets = hero.get_target(sk.limit, 2)
        for t in targets:
            t.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
        hero.storage[str(sk.id)] = extra + 40
        return True


# ─── 200980 乘胜追击 -> 复用 Skill1037
class Skill200980(Skill):
    def __init__(self):
        super().__init__(200980,'乘胜追击','普通攻击后追击(150%)，60%几率再次追击(100%)，每次降低20%',3,35,False,0)
    def call(self, hero, target=None):
        from .skills import Skill1037
        obj = Skill1037(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200983 运筹决胜
class Skill200983(Skill):
    def __init__(self):
        super().__init__(200983,'运筹决胜','试图发动主动时30%使敌军单体暴走1回合；50%对混乱/暴走敌军全体谋略攻击(220%)',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(220, 2.0, hero.attrs['int'])
        def sub():
            if get_random_bool(hero.get_real_skill_rate(30)):
                targets = hero.get_target(sk.limit, 1)
                if targets and not targets[0].is_confusion():
                    targets[0].state['confusion'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
            if get_random_bool(hero.get_real_skill_rate(50)):
                enemy = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
                chaos = [e for e in enemy if e.arms > 0 and e.is_confusion()]
                for t in chaos:
                    t.be_hurt(hero, {'type': 2, 'rate': v}, sk)
        hero.add_hook('行动前', f'{sk.id}_act', sub, sk, hero)


# ─── 200984 怀德畏威
class Skill200984(Skill):
    def __init__(self):
        super().__init__(200984,'怀德畏威','令谋略最低的友军单体攻击随机敌军(160%)；自身对敌军2目标谋略攻击(160%)；若目标相同则混乱1回合',2,40,False,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate1 = calc_skill_addition_rate(160, 1.5, hero.attrs['atk'])
        rate2 = calc_skill_addition_rate(160, 1.5, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        alive_team = [e for e in team if e.arms > 0 and e is not hero]
        low_ally = min(alive_team, key=lambda e: e.attrs['int']) if alive_team else None
        

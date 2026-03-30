# -*- coding: utf-8 -*-
import os

code = """

# ─── 200961 永疾先登 -> 复用 Skill1009
class Skill200961(Skill):
    def __init__(self):
        super().__init__(200961,'永疾先登','每回合行动时攻击伤害8%，达40%时群体攻击并重置',1,'--',False,5)
    def call(self, hero, target=None):
        from .skills import Skill1009
        obj = Skill1009(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200966 尽言直谏
class Skill200966(Skill):
    def __init__(self):
        super().__init__(200966,'尽言直谏','每回合行动时随机令友方2个主动战法下次发动率8+10%伤害30%',1,'--',False,3)
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
        super().__init__(200983,'运筹决胜','试图发动主动时30%使敌军单体暴质1回合；50%对混乱/暴质敌军全体谋略攻击(220%)',1,'--',False,5)
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
        t1 = None
        if low_ally:
            et = hero.get_target(sk.limit, 1)
            if et:
                t1 = et[0]
                t1.be_hurt(low_ally, {'type': 1, 'rate': rate1}, sk)
        et2 = hero.get_target(sk.limit, 2)
        t2_list = []
        for t in et2:
            t.be_hurt(hero, {'type': 2, 'rate': rate2}, sk)
            t2_list.append(t)
        if t1 and len(set(t2_list)) == 1 and t2_list[0] is t1:
            if not t1.is_confusion():
                t1.state['confusion'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        return True


# ─── 200985 谋议宏图
class Skill200985(Skill):
    def __init__(self):
        super().__init__(200985,'谋议宏图','首回合我军全体受佗30%，每回合-1/8；我军全体每回合士气+8',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(30, 0.22, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('beAttackDamageSub', v, -1, sk, hero)
            e.add_state('beInteDamageSub', v, -1, sk, hero)
        step = v / 8
        def on_rnd():
            for e in team:
                if e.arms > 0:
                    e.add_state('beAttackDamageSub', -step, -1, sk, hero, True)
                    e.add_state('beInteDamageSub', -step, -1, sk, hero, True)
                if hasattr(e, 'morale'):
                    e.morale = min(200, e.morale + 8)
        hero.add_hook('回合开始时', f'{sk.id}_decay', on_rnd, sk, hero)


# ─── 200986 盛气横凌
class Skill200986(Skill):
    def __init__(self):
        super().__init__(200986,'盛气横凌','普通攻击后对目标猥攻(260%)；士气高昂则混乱1回合；士气一般/低落则额外攻击(80%-160%)',3,50,False,0)
    def call(self, hero, target=None):
        if target is None: return
        sk = self
        rate = calc_skill_addition_rate(260, 2.4, hero.attrs['atk'])
        target.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
        morale = getattr(target, 'morale', 100)
        if morale >= 120:
            if not target.is_confusion():
                target.state['confusion'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        else:
            extra = get_random_int(80, 160)
            target.be_hurt(hero, {'type': 1, 'rate': extra}, sk)


# ─── 200987 三军夺帅
class Skill200987(Skill):
    def __init__(self):
        super().__init__(200987,'三军夺帅','每次成功发动普攻/主动/追击后，攻击敌军单体(180%)自身攻击+10，或谋略攻击敌军2目标(100%)谋略-5',4,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
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
        

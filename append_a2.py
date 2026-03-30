# -*- coding: utf-8 -*-
# 追加A级战法第二批

code = """
        1):
            t.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
        return True


# ─── 200065 沙场破阵
class Skill200065(Skill):
    def __init__(self):
        super().__init__(200065,'沙场破阵','普通攻击后对目标谋略攻击(119%)，40%几率使其暴走1回合',3,120,False,0)
    def call(self, hero, target=None):
        if target is None: return
        sk = self
        rate = calc_skill_addition_rate(119, 1.1, hero.attrs['int'])
        target.be_hurt(hero, {'type': 2, 'rate': rate}, sk)
        if get_random_bool(hero.get_real_skill_rate(40)) and not target.is_confusion():
            target.state['confusion'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}


# ─── 200072 列营守险
class Skill200072(Skill):
    def __init__(self):
        super().__init__(200072,'列营守险','我军全体属性提高29.2持续2回合，并使友军全体受到下3次伤害时50%几率规避',2,40,False,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(29.2, 0.24, hero.attrs['int'])
        team = hero.get_target(sk.limit, 2, 2)
        for e in team:
            for k in ('atk','def','int','spd'):
                e.attrs[k] = keep_two_decimal(e.attrs[k] + v)
            immune_cnt = [3]
            def mh(e=e, cnt=immune_cnt):
                def oh(a, di, s):
                    if cnt[0] > 0 and get_random_bool(50):
                        cnt[0] -= 1
                        di['immune'] = True
                e.add_hook('受伤时', f'{sk.id}_dodge_{id(e)}', oh, sk, hero)
            mh()
        return True


# ─── 200074 素衣伶姬
class Skill200074(Skill):
    def __init__(self):
        super().__init__(200074,'素衣伶姬','前3回合敌军群体谋略攻击伤害降低26%，结束后1回合100%混乱',1,'--',False,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(26, 0.2, hero.attrs['int'])
        enemy = hero.get_target(sk.limit, 2)
        for e in enemy:
            e.add_state('inteDamageSub', v, 3, sk, hero)
        def rage():
            if hero.manager.round == 4:
                hero.clear_hook('回合开始时', f'{sk.id}_rage')
                for e in enemy:
                    if e.arms > 0 and not e.is_confusion():
                        e.state['confusion'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        hero.add_hook('回合开始时', f'{sk.id}_rage', rage, sk, hero)


# ─── 200076 鬼谋
class Skill200076(Skill):
    def __init__(self):
        super().__init__(200076,'鬼谋','阻止敌军单体恢复兵力并使其每回合40%几率随机陷入控制状态，持续3回合',2,35,False,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        tgs = hero.get_target(sk.limit, 1)
        if not tgs: return True
        t = tgs[0]
        t.state['noRecover'] = {'rounds': 3, 'from': {'hero': hero, 'skill': sk}}
        rl = [3]
        def on_rnd():
            rl[0] -= 1
            if rl[0] <= 0:
                t.clear_hook('回合开始时', f'{sk.id}_ctrl')
                return
            if get_random_bool(hero.get_real_skill_rate(40)):
                st = random.choice(['confusion', 'confusion', 'attackLimit', 'activeLimit'])
                if not t.state.get(st, {}).get('rounds', 0) > 0:
                    t.state[st] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        t.add_hook('回合开始时', f'{sk.id}_ctrl', on_rnd, sk, hero)
        return True


# ─── 200083 八门金锁
class Skill200083(Skill):
    def __init__(self):
        super().__init__(200083,'八门金锁','使敌军群体怯战2回合',2,30,False,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        for t in hero.get_target(sk.limit, 2):
            if not t.is_attack_limit():
                t.state['attackLimit'] = {'rounds': 2, 'from': {'hero': hero, 'skill': sk}}
        return True


# ─── 200088 方阵突击
class Skill200088(Skill):
    def __init__(self):
        super().__init__(200088,'方阵突击','普通攻击后对目标攻击(200%)并使其混乱1回合',3,30,False,0)
    def call(self, hero, target=None):
        if target is None: return
        sk = self
        rate = calc_skill_addition_rate(200, 1.9, hero.attrs['atk'])
        target.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
        if not target.is_confusion():
            target.state['confusion'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}


# ─── 200090 奇计迭出
class Skill200090(Skill):
    def __init__(self):
        super().__init__(200090,'奇计迭出','1回合准备，对敌军全体谋略攻击(92%)，50%几率再次攻击(133%)并附加燃烧(76%)',2,35,False,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r1 = calc_skill_addition_rate(92, 0.85, hero.attrs['int'])
        r2 = calc_skill_addition_rate(133, 1.2, hero.attrs['int'])
        r3 = calc_skill_addition_rate(76, 0.7, hero.attrs['int'])
        def subskill():
            for t in hero.get_target(sk.limit, 3):
                t.be_hurt(hero, {'type': 2, 'rate': r1}, sk)
                if get_random_bool(hero.get_real_skill_rate(50)):
                    t.be_hurt(hero, {'type': 2, 'rate': r2}, sk)
                    t.be_hurt(hero, {'type': 4, 'rate': r3}, sk)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200092 蝶舞红莲
class Skill200092(Skill):
    def __init__(self):
        super().__init__(200092,'蝶舞红莲','对敌军群体谋略攻击(119%)，并恢复友军全体兵力(109%)',2,40,False,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        atk_r = calc_skill_addition_rate(119, 1.1, hero.attrs['int'])
        rec_r = calc_skill_addition_rate(109, 1.0, hero.attrs['int'])
        for t in hero.get_target(sk.limit, 2):
            t.be_hurt(hero, {'type': 2, 'rate': atk_r}, sk)
        for e in hero.get_target(3, 2, 2):
            e.recover(calc_recover(hero, rec_r, 1.0, 2), hero, sk.name)
        return True


# ─── 200119 焰焚箕轸
class Skill200119(Skill):
    def __init__(self):
        super().__init__(200119,'焰焚箕轸','1回合准备，对敌军群体谋略攻击(119%)并附加燃烧(119%)持续1回合',2,50,False,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r1 = calc_skill_addition_rate(119, 1.1, hero.attrs['int'])
        r2 = calc_skill_addition_rate(119, 1.1, hero.attrs['int'])
        def subskill():
            for t in hero.get_target(sk.limit, 2):
                t.be_hurt(hero, {'type': 2, 'rate': r1}, sk)
                t.be_hurt(hero, {'type': 4, 'rate': r2}, sk)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200126 落雷
class Skill200126(Skill):
    def __init__(self):
        super().__init__(200126,'落雷','对敌军单体谋略攻击(148%)并使其混乱1回合',2,35,False,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate = calc_skill_addition_rate(148, 1.4, hero.attrs['int'])
        tgs = hero.get_target(sk.limit, 1)
        if not tgs: return True
        t = tgs[0]
        t.be_hurt(hero, {'type': 2, 'rate': rate}, sk)
        if not t.is_confusion():
            t.state['confusion'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        return True


# ─── 200127 迷阵
class Skill200127(Skill):
    def __init__(self):
        super().__init__(200127,'迷阵','对敌军单体谋略攻击(155%)并使其暴走1回合',2,35,False,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate = calc_skill_addition_rate(155, 1.45, hero.attrs['int'])
        tgs = hero.get_target(sk.limit, 1)
        if not tgs: return True
        t = tgs[0]
        t.be_hurt(hero, {'type': 2, 'rate': rate}, sk)
        if not t.is_confusion():
            t.state['confusion'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        return True


# ─── 200130 楚歌四起
class Skill200130(Skill):
    def __init__(self):
        super().__init__(200130,'楚歌四起','1回合准备，对敌军群体施加恐慌(127%)持续2回合',2,50,False,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        rate = calc_skill_addition_rate(127, 1.2, hero.attrs['int'])
        def subskill():
            for t in hero.get_target(sk.limit, 2):
                t.be_hurt(hero, {'type': 3, 'rate': rate}, sk)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200208 温酒斩将
class Skill200208(Skill):
    def __init__(self):
        super().__init__(200208,'温酒斩将','普通攻击后对目标再次猛攻(200%)',3,30,False,0)
    def call(self, hero, target=None):
        if target is None: return
        sk = self
        rate = calc_skill_addition_rate(200, 1.9, hero.attrs['atk'])
        target.be_hurt(hero, {'type': 1, 'rate': rate}, sk)


# ─── 200233 先驱突击
class Skill200233(Skill):
    def __init__(self):
        super().__init__(200233,'先驱突击','前3回合优先行动，每回合可连击，攻击+30',1,'--',True,1)
    def call(self, hero, target=None):
        from .skills import Skill1005
        obj = Skill1005(); obj.id = self.id
        return obj.call(hero, target)
"""

with open('D:/nettest/battle_sim/skills_a.py', 'a', encoding='utf-8') as f:
    f.write(code)
print('batch 2 done')


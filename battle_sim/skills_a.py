# -*- coding: utf-8 -*-
# A级战法实现 (200xxx系列, quality=A)
from .calc import calc_attack_damage, calc_inte_damage, calc_inte2_damage, calc_recover, calc_skill_addition_rate, get_random_bool, get_random_int, keep_two_decimal
from .skills import Skill
import random


# ─── 200002 乱政
class Skill200002(Skill):
    def __init__(self):
        super().__init__(200002,'乱政','使敌军单体获得10种负面效果之一，执行3次，每次目标和效果独立判定',2,120,True,6)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        states = ['confusion','confusion','activeLimit','attackLimit','confusion','activeLimit','attackLimit','confusion','activeLimit','attackLimit']
        fire_r = calc_skill_addition_rate(100, 0.9, hero.attrs['int'])
        for _ in range(3):
            tgs = hero.get_target(sk.limit, 1)
            if not tgs: break
            t = tgs[0]
            st = random.choice(states)
            if st in ('confusion', 'activeLimit', 'attackLimit'):
                if not t.state.get(st, {}).get('rounds', 0) > 0:
                    t.state[st] = {'rounds': 2, 'from': {'hero': hero, 'skill': sk}}
            else:
                t.be_hurt(hero, {'type': 4, 'rate': fire_r}, sk)
        return True


# ─── 200005 闭月
class Skill200005(Skill):
    def __init__(self):
        super().__init__(200005,'闭月','1回合准备，使敌军群体暴走3回合并使防御降低29',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        sub_v = calc_skill_addition_rate(29, 0.22, hero.attrs['int'])
        def subskill():
            for t in hero.get_target(sk.limit, 2):
                if not t.is_confusion():
                    t.state['confusion'] = {'rounds': 3, 'from': {'hero': hero, 'skill': sk}}
                t.attrs['def'] = keep_two_decimal(max(0, t.attrs['def'] - sub_v))
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200008 黄天当立
class Skill200008(Skill):
    def __init__(self):
        super().__init__(200008,'黄天当立','1回合准备，对敌军全体施加妖术(180%)持续2回合，张角恢复伤害值60%兵力',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        dot_r = calc_skill_addition_rate(180, 1.6, hero.attrs['int'])
        rec_r = calc_skill_addition_rate(60, 0.55, hero.attrs['int'])
        def subskill():
            for t in hero.get_target(sk.limit, 3):
                t.be_hurt(hero, {'type': 4, 'rate': dot_r}, sk)
                def mh(t=t):
                    def oh(a, di, s):
                        if s is sk:
                            hero.recover(calc_recover(hero, rec_r, 0.55, 2), hero, sk.name)
                    t.add_hook('受伤时', f'{sk.id}_rec_{id(t)}', oh, sk, hero)
                mh()
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200011 算无遗策
class Skill200011(Skill):
    def __init__(self):
        super().__init__(200011,'算无遗策','敌军群体攻击谋略降低22持续2回合，期间发动主动战法时受妖术伤害(140%)',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        sub_v = calc_skill_addition_rate(22, 0.18, hero.attrs['int'])
        dot_r = calc_skill_addition_rate(140, 1.3, hero.attrs['int'])
        for t in hero.get_target(sk.limit, 2):
            t.attrs['atk'] = keep_two_decimal(max(0, t.attrs['atk'] - sub_v))
            t.attrs['int'] = keep_two_decimal(max(0, t.attrs['int'] - sub_v))
            def mh(t=t):
                rl = [2]
                def on_act():
                    t.be_hurt(hero, {'type': 4, 'rate': dot_r}, sk)
                def on_rnd():
                    rl[0] -= 1
                    if rl[0] <= 0:
                        t.attrs['atk'] = keep_two_decimal(t.attrs['atk'] + sub_v)
                        t.attrs['int'] = keep_two_decimal(t.attrs['int'] + sub_v)
                        t.clear_hook('行动前', f'{sk.id}_act_{id(t)}')
                        t.clear_hook('回合开始时', f'{sk.id}_rnd_{id(t)}')
                t.add_hook('行动前', f'{sk.id}_act_{id(t)}', on_act, sk, hero)
                t.add_hook('回合开始时', f'{sk.id}_rnd_{id(t)}', on_rnd, sk, hero)
            mh()
        return True


# ─── 200019 红颜铁骑
class Skill200019(Skill):
    def __init__(self):
        super().__init__(200019,'红颜铁骑','使自身每回合可进行两次普通攻击，攻击属性提高50',4,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        atk_add = calc_skill_addition_rate(50, 0.5, hero.attrs['atk'])
        hero.attrs['atk'] = keep_two_decimal(hero.attrs['atk'] + atk_add)
        hero.state['doubleAttack'] = {'rounds': -1, 'from': {'hero': hero, 'skill': sk}}


# ─── 200020 匠心不竭
class Skill200020(Skill):
    def __init__(self):
        super().__init__(200020,'匠心不竭','从第1/3/5回合起对敌军全体逐步施加恐慌/燃烧/妖术持续伤害',1,'--',True,6)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r1 = calc_skill_addition_rate(34, 0.3, hero.attrs['int'])
        r2 = calc_skill_addition_rate(41, 0.38, hero.attrs['int'])
        r3 = calc_skill_addition_rate(44, 0.4, hero.attrs['int'])
        et = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
        def sub():
            rnd = hero.manager.round
            if rnd == 1:
                for t in et:
                    if t.arms > 0: t.be_hurt(hero, {'type': 3, 'rate': r1}, sk)
            elif rnd == 3:
                for t in et:
                    if t.arms > 0: t.be_hurt(hero, {'type': 4, 'rate': r2}, sk)
            elif rnd == 5:
                for t in et:
                    if t.arms > 0: t.be_hurt(hero, {'type': 4, 'rate': r3}, sk)
        hero.add_hook('回合开始时', f'{sk.id}_dot', sub, sk, hero)


# ─── 200022 长坂之吼
class Skill200022(Skill):
    def __init__(self):
        super().__init__(200022,'长坂之吼','2回合准备，对敌军群体2-3目标无视相克猛烈攻击(450%)',2,75,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        rate = calc_skill_addition_rate(450, 4.2, hero.attrs['atk'])
        def subskill():
            cnt = random.randint(2, 3)
            for t in hero.get_target(sk.limit, cnt):
                t.be_hurt(hero, {'type': 1, 'rate': rate, 'ignore_arms': True}, sk)
        hero.add_ready_skill(sk, 2, subskill)
        return True


# ─── 200028 世仇
class Skill200028(Skill):
    def __init__(self):
        super().__init__(200028,'世仇','普通攻击后对目标谋略攻击(233%)并使其无法恢复兵力2回合',3,60,True,0)
    def call(self, hero, target=None):
        if target is None: return
        sk = self
        rate = calc_skill_addition_rate(233, 2.1, hero.attrs['int'])
        target.be_hurt(hero, {'type': 2, 'rate': rate}, sk)
        if not target.state.get('noRecover', {}).get('rounds', 0) > 0:
            target.state['noRecover'] = {'rounds': 2, 'from': {'hero': hero, 'skill': sk}}


# ─── 200029 强势
class Skill200029(Skill):
    def __init__(self):
        super().__init__(200029,'强势','使敌军群体攻击伤害降低32%并陷入犹豫2回合',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(32, 0.25, hero.attrs['int'])
        for t in hero.get_target(sk.limit, 2):
            t.add_state('attackDamageSub', v, 2, sk, hero)
            if not t.is_active_limit():
                t.state['activeLimit'] = {'rounds': 2, 'from': {'hero': hero, 'skill': sk}}
        return True


# ─── 200031 玄武洰流
class Skill200031(Skill):
    def __init__(self):
        super().__init__(200031,'玄武洰流','1回合准备，对敌军全体谋略攻击(150%)并使其怯战2回合',2,30,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        rate = calc_skill_addition_rate(150, 1.4, hero.attrs['int'])
        def subskill():
            for t in hero.get_target(sk.limit, 3):
                t.be_hurt(hero, {'type': 2, 'rate': rate}, sk)
                if not t.is_attack_limit():
                    t.state['attackLimit'] = {'rounds': 2, 'from': {'hero': hero, 'skill': sk}}
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200034 侵掠如火
class Skill200034(Skill):
    def __init__(self):
        super().__init__(200034,'侵掠如火','优先行动，攻击类主动战法发动率+20%，攻击时30%几率伤害+50%',4,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        hero.state['firstAction'] = {'rounds': -1, 'from': {'hero': hero, 'skill': sk}}
        hero.rate_val[sk.id] = {'value': 20, 'rounds': -1}
        def on_dmg(t, di, s2, dmg):
            if di.get('type') == 1 and get_random_bool(30):
                bonus = int(dmg * 0.5)
                t.be_hurt_by_num(hero, di, sk, bonus, lambda: None)
        hero.add_hook('造成伤害后', f'{sk.id}_bonus', on_dmg, sk, hero)


# ─── 200059 方天余烈
class Skill200059(Skill):
    def __init__(self):
        super().__init__(200059,'方天余烈','自身主动战法伤害提高35%，对敌军单体攻击(80%)',2,120,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(35, 0.28, hero.attrs['atk'])
        hero.add_state('activeDamageAdd', v, 2, sk, hero)
        rate = calc_skill_addition_rate(80, 0.72, hero.attrs['atk'])
        for t in hero.get_target(sk.limit, 1):
            t.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
        return True


# ─── 200065 沙场破阵
class Skill200065(Skill):
    def __init__(self):
        super().__init__(200065,'沙场破阵','普通攻击后对目标谋略攻击(119%)，40%几率使其暴走1回合',3,120,True,0)
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
        super().__init__(200072,'列营守险','我军全体属性提高29.2持续2回合，并使友军全体受到下3次伤害时50%几率规避',2,40,True,4)
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
        super().__init__(200074,'素衣伶姬','前3回合敌军群体谋略攻击伤害降低26%，结束后1回合100%混乱',1,'--',True,4)
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
        super().__init__(200076,'鬼谋','阻止敌军单体恢复兵力并使其每回合40%几率随机陷入控制状态，持续3回合',2,35,True,5)
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
        super().__init__(200083,'八门金锁','使敌军群体怯战2回合',2,30,True,3)
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
        super().__init__(200088,'方阵突击','普通攻击后对目标攻击(200%)并使其混乱1回合',3,30,True,0)
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
        super().__init__(200090,'奇计迭出','1回合准备，对敌军全体谋略攻击(92%)，50%几率再次攻击(133%)并附加燃烧(76%)',2,35,True,5)
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
        super().__init__(200092,'蝶舞红莲','对敌军群体谋略攻击(119%)，并恢复友军全体兵力(109%)',2,40,True,3)
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
        super().__init__(200119,'焰焚箕轸','1回合准备，对敌军群体谋略攻击(119%)并附加燃烧(119%)持续1回合',2,50,True,4)
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
        super().__init__(200126,'落雷','对敌军单体谋略攻击(148%)并使其混乱1回合',2,35,True,4)
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
        super().__init__(200127,'迷阵','对敌军单体谋略攻击(155%)并使其暴走1回合',2,35,True,4)
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
        super().__init__(200130,'楚歌四起','1回合准备，对敌军群体施加恐慌(127%)持续2回合',2,50,True,5)
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
        super().__init__(200208,'温酒斩将','普通攻击后对目标再次猛攻(200%)',3,30,True,0)
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


# --- 200241 悬权而动
class Skill200241(Skill):
    def __init__(self):
        super().__init__(200241,'悬权而动','前2回合友军造成伤害后士气+3；第3回合起友军士气最高单体伤害+30%',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        def on_dmg(t, di, s2, dmg):
            if hero.manager.round <= 2:
                for e in team:
                    if hasattr(e,'morale'): e.morale = min(200, e.morale+3)
        hero.add_hook('造成伤害后', f'{sk.id}_morale', on_dmg, sk, hero)
        def on_act():
            if hero.manager.round >= 3:
                alive = [e for e in team if e.arms > 0]
                if alive:
                    top = max(alive, key=lambda e: getattr(e,'morale',100))
                    top.add_state('activeDamageAdd', 30, 1, sk, hero)
                    top.add_state('attackDamageAdd', 30, 1, sk, hero)
                    top.add_state('inteDamageAdd', 30, 1, sk, hero)
        hero.add_hook('行动时', f'{sk.id}_boost', on_act, sk, hero)


# --- 200242 举抑臧否
class Skill200242(Skill):
    def __init__(self):
        super().__init__(200242,'举抑臧否','每回合行动时随机选属性，使该属性最低敌军降低20并控制；最高友军提升20并增益',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(20, 0.15, hero.attrs['int'])
        def sub():
            key = random.choice(['atk','def','int'])
            enemy = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
            team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
            alive_e = [e for e in enemy if e.arms>0]
            alive_t = [e for e in team if e.arms>0]
            if alive_e:
                low = min(alive_e, key=lambda e: e.attrs[key])
                low.attrs[key] = keep_two_decimal(max(0, low.attrs[key]-v))
                ctrl = random.choice(['activeLimit','attackLimit'])
                if not low.state.get(ctrl,{}).get('rounds',0)>0:
                    low.state[ctrl] = {'rounds':1,'from':{'hero':hero,'skill':sk}}
            if alive_t:
                top = max(alive_t, key=lambda e: e.attrs[key])
                top.attrs[key] = keep_two_decimal(top.attrs[key]+v)
        hero.add_hook('行动时', f'{sk.id}_act', sub, sk, hero)


# --- 200243 九变之利
class Skill200243(Skill):
    def __init__(self):
        super().__init__(200243,'九变之利','1回合准备，对敌军群体2目标根据状态附加减益，并攻击(180%)',2,40,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        rate = calc_skill_addition_rate(180, 1.7, hero.attrs['atk'])
        sub_attr = calc_skill_addition_rate(35, 0.28, hero.attrs['int'])
        dmg_add = calc_skill_addition_rate(40, 0.32, hero.attrs['int'])
        def subskill():
            for t in hero.get_target(sk.limit, 2):
                if t.state.get('confusion',{}).get('rounds',0)>0 or t.state.get('activeLimit',{}).get('rounds',0)>0:
                    t.attrs['def'] = keep_two_decimal(max(0, t.attrs['def']-sub_attr))
                    t.attrs['int'] = keep_two_decimal(max(0, t.attrs['int']-sub_attr))
                elif t.state.get('dot') or any(k.startswith('dot') for k in t.state):
                    t.add_state('beAttackDamageAdd', dmg_add, 2, sk, hero)
                    t.add_state('beInteDamageAdd', dmg_add, 2, sk, hero)
                else:
                    if not t.is_attack_limit():
                        t.state['attackLimit'] = {'rounds':2,'from':{'hero':hero,'skill':sk}}
                t.be_hurt(hero, {'type':1,'rate':rate}, sk)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# --- 200247 计焚乌巢
class Skill200247(Skill):
    def __init__(self):
        super().__init__(200247,'计焚乌巢','1回合准备，对敌单体谋略攻击(160%)，使最远敌军受主动战法伤害+15%，50%几率燃烧(120%)',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r1 = calc_skill_addition_rate(160, 1.5, hero.attrs['int'])
        r2 = calc_skill_addition_rate(15, 0.12, hero.attrs['int'])
        r3 = calc_skill_addition_rate(120, 1.1, hero.attrs['int'])
        def subskill():
            tgs = hero.get_target(sk.limit, 1)
            if tgs: tgs[0].be_hurt(hero, {'type':2,'rate':r1}, sk)
            enemy = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
            alive = [e for e in enemy if e.arms>0]
            if alive:
                far = max(alive, key=lambda e: e.attrs.get('pos',0))
                far.add_state('beActiveDamageAdd', r2, 2, sk, hero)
                if get_random_bool(hero.get_real_skill_rate(50)):
                    far.be_hurt(hero, {'type':4,'rate':r3}, sk)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# --- 200248 计险远近
class Skill200248(Skill):
    def __init__(self):
        super().__init__(200248,'计险远近','当我军3名武将基础攻击距离均不相同时，大营无视规避，中军属性+20，前锋首次伤害-50%',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        limits = [e.attrs.get('limit',3) for e in team if e.arms>0]
        if len(set(limits)) == len(limits):
            for e in team:
                if e.pos_name == '中军':
                    for k in ('atk','def','int'): e.attrs[k] = keep_two_decimal(e.attrs[k]+20)
                elif e.pos_name == '前锋':
                    def mh(e=e):
                        fired=[False]
                        def oh(a,di,s):
                            if not fired[0]: fired[0]=True; di['rate']=di.get('rate',100)*0.5
                        e.add_hook('受伤时',f'{sk.id}_guard_{id(e)}',oh,sk,hero)
                    mh()
        def on_atk(t,di,s2,dmg):
            t.add_state('beAttackDamageAdd',15,-1,sk,hero,True)
            t.add_state('beInteDamageAdd',15,-1,sk,hero,True)
        hero.add_hook('造成伤害后',f'{sk.id}_atk',on_atk,sk,hero)


# --- 200249 知己知彼
class Skill200249(Skill):
    def __init__(self):
        super().__init__(200249,'知己知彼','我军每造成一次伤害后60%几率该类型伤害+8%最多5层；敌军每受伤后60%几率受该类型伤害+8%最多5层',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(8, 0.06, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        enemy = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        atk_stk={id(e):0 for e in team}; def_stk={id(e):0 for e in enemy}
        for e in team:
            def ms(e=e):
                def on_dmg(t,di,s2,dmg):
                    if atk_stk[id(e)]<5 and get_random_bool(60):
                        atk_stk[id(e)]+=1
                        k='attackDamageAdd' if di.get('type')==1 else 'inteDamageAdd'
                        e.add_state(k,v,-1,sk,hero,True)
                e.add_hook('造成伤害后',f'{sk.id}_atk_{id(e)}',on_dmg,sk,hero)
            ms()
        for e in enemy:
            def me(e=e):
                def oh(a,di,s):
                    if def_stk[id(e)]<5 and get_random_bool(60):
                        def_stk[id(e)]+=1
                        k='beAttackDamageAdd' if di.get('type')==1 else 'beInteDamageAdd'
                        e.add_state(k,v,-1,sk,hero,True)
                e.add_hook('受伤时',f'{sk.id}_def_{id(e)}',oh,sk,hero)
            me()


# --- 200253 兵者诡道
class Skill200253(Skill):
    def __init__(self):
        super().__init__(200253,'兵者诡道','每3次试图发动主动/追击后随机触发：谋略攻击(170%)/规避2次/恢复自身及友军单体(150%)',4,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self; cnt=[0]
        r1=calc_skill_addition_rate(170,1.6,hero.attrs['int'])
        r2=calc_skill_addition_rate(150,1.4,hero.attrs['int'])
        def sub():
            cnt[0]+=1
            if cnt[0]>=3:
                cnt[0]=0
                ch=random.randint(0,2)
                if ch==0:
                    tgs=hero.get_target(sk.limit,1)
                    if tgs: tgs[0].be_hurt(hero,{'type':2,'rate':r1},sk)
                elif ch==1:
                    immune=[2]
                    tag=f'{sk.id}_dodge'
                    def oh(a,di,s):
                        if immune[0]>0: immune[0]-=1; di['immune']=True
                        if immune[0]<=0: hero.clear_hook('受伤时',tag)
                    hero.add_hook('受伤时',tag,oh,sk,hero)
                else:
                    hero.recover(calc_recover(hero,r2,1.4,2),hero,sk.name)
                    tgs=hero.get_target(sk.limit,1,3)
                    if tgs: tgs[0].recover(calc_recover(hero,r2,1.4,2),hero,sk.name)
        hero.add_hook('行动前',f'{sk.id}_cnt',sub,sk,hero)


# --- 200256 持玺兴兵
class Skill200256(Skill):
    def __init__(self):
        super().__init__(200256,'持玺兴兵','友军受伤且兵力低于50%时恢复(200%)并提升攻谋30，自身攻谋降低30，最多3次',1,'--',True,2)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self; used=[0]
        rv=calc_skill_addition_rate(200,1.9,hero.attrs['int'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in [x for x in team if x is not hero]:
            def ms(e=e):
                def oh(a,di,s):
                    if used[0]>=3: return
                    max_arms=getattr(e,'max_arms',e.arms)
                    if e.arms/max(max_arms,1)<0.5:
                        used[0]+=1
                        e.recover(calc_recover(hero,rv,1.9,2),hero,sk.name)
                        e.attrs['atk']=keep_two_decimal(e.attrs['atk']+30)
                        e.attrs['int']=keep_two_decimal(e.attrs['int']+30)
                        hero.attrs['atk']=keep_two_decimal(max(0,hero.attrs['atk']-30))
                        hero.attrs['int']=keep_two_decimal(max(0,hero.attrs['int']-30))
                e.add_hook('受伤时',f'{sk.id}_{id(e)}',oh,sk,hero)
            ms()


# --- 200258 雪奋短兵
class Skill200258(Skill):
    def __init__(self):
        super().__init__(200258,'雪奋短兵','受伤时40%规避；每回合攻击距离-1；距离<=1时对近敌2次攻击(60%)并70%使远敌动摇',4,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        def on_hurt(a,di,s):
            if get_random_bool(40): di['immune']=True
        def on_rnd():
            cur=hero.attrs.get('limit',3)
            if cur>1:
                hero.attrs['limit']=max(1,cur-1)
            else:
                r=calc_skill_addition_rate(60,0.55,hero.attrs['atk'])
                for t in hero.get_target(1,2): t.be_hurt(hero,{'type':1,'rate':r},sk)
                if get_random_bool(70):
                    enemy=hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
                    for t in [e for e in enemy if e.arms>0 and e.attrs.get('pos',0)>1]:
                        t.be_hurt(hero,{'type':3,'rate':calc_skill_addition_rate(120,1.1,hero.attrs['atk'])},sk)
        hero.add_hook('受伤时',f'{sk.id}_dodge',on_hurt,sk,hero)
        hero.add_hook('行动时',f'{sk.id}_act',on_rnd,sk,hero)


# --- 200259 乘间击隙
class Skill200259(Skill):
    def __init__(self):
        super().__init__(200259,'乘间击隙','每发动主动主战法后攻击伤害+10%最多3层，达3层时对敌群体攻击(200%)',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self; stk=[0]
        rate=calc_skill_addition_rate(200,1.9,hero.attrs['atk'])
        def sub():
            stk[0]+=1
            hero.add_state('attackDamageAdd',10,-1,sk,hero,True)
            if stk[0]>=3:
                stk[0]=0
                hero.del_state('attackDamageAdd',sk)
                for t in hero.get_target(sk.limit,2): t.be_hurt(hero,{'type':1,'rate':rate},sk)
        hero.add_hook('行动时',f'{sk.id}_act',sub,sk,hero)


# --- 200261 胜敌益强
class Skill200261(Skill):
    def __init__(self):
        super().__init__(200261,'胜敌益强','每回合行动时恢复1次兵力(120%)，第2/4/6回合起恢复次数提升至2/3/4次',4,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        rv=calc_skill_addition_rate(120,1.1,hero.attrs['def'])
        def sub():
            rnd=hero.manager.round
            cnt=1
            if rnd>=6: cnt=4
            elif rnd>=4: cnt=3
            elif rnd>=2: cnt=2
            for _ in range(cnt): hero.recover(calc_recover(hero,rv,1.1,2),hero,sk.name)
        hero.add_hook('行动时',f'{sk.id}_rec',sub,sk,hero)


# --- 200265 雅虑适时
class Skill200265(Skill):
    def __init__(self):
        super().__init__(200265,'雅虑适时','第3/5/7回合我军全体受伤-24%并进入同心状态，同心状态下分摊15%伤害',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v=calc_skill_addition_rate(24,0.18,hero.attrs['int'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        def sub():
            rnd=hero.manager.round
            if rnd in (3,5,7):
                for e in team:
                    e.add_state('beAttackDamageSub',v,1,sk,hero)
                    e.add_state('beInteDamageSub',v,1,sk,hero)
                    def mh(e=e):
                        def oh(a,di,s):
                            dmg=di.get('damage',0)
                            share=int(dmg*0.15)
                            for ally in [x for x in team if x is not e and x.arms>0]:
                                ally.be_hurt_by_num(a,di,sk,share//max(len(team)-1,1),lambda:None)
                        e.add_hook('受伤时',f'{sk.id}_share_{id(e)}_{rnd}',oh,sk,hero)
                    mh()
        hero.add_hook('行动时',f'{sk.id}_act',sub,sk,hero)


# --- 200267 敛众定气
class Skill200267(Skill):
    def __init__(self):
        super().__init__(200267,'敛众定气','移除我军全体有害效果，恢复兵力(85%)，并50%几率免疫围困效果2回合',2,40,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rv=calc_skill_addition_rate(85,0.78,hero.attrs['int'])
        for e in hero.get_target(sk.limit,2,2):
            e.clear_debuff(sk,hero)
            e.recover(calc_recover(hero,rv,0.78,2),hero,sk.name)
            if get_random_bool(50):
                e.state['immuneEntrap']={'rounds':2,'from':{'hero':hero,'skill':sk}}
        return True


# --- 200607 帝临回光
class Skill200607(Skill):
    def __init__(self):
        super().__init__(200607,'帝临回光','第3回合起无法恢复兵力，攻击距离+1，普攻后附近敌军受伤(70%)，敌群体恐慌(59%)',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        fear_r=calc_skill_addition_rate(59,0.54,hero.attrs['int'])
        split_r=calc_skill_addition_rate(70,0.64,hero.attrs['atk'])
        def sub():
            if hero.manager.round==3:
                hero.state['noRecover']={'rounds':-1,'from':{'hero':hero,'skill':sk}}
                hero.attrs['limit']=hero.attrs.get('limit',3)+1
                hero.clear_hook('行动时',f'{sk.id}_wait')
                enemy=hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
                for t in enemy:
                    t.be_hurt(hero,{'type':3,'rate':fear_r},sk)
                def on_atk(t,di,s2,dmg):
                    enemy2=hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
                    for nb in [e for e in enemy2 if e is not t and e.arms>0]:
                        nb.be_hurt(hero,{'type':1,'rate':split_r},sk)
                hero.add_hook('造成伤害后',f'{sk.id}_split',on_atk,sk,hero)
        hero.add_hook('行动时',f'{sk.id}_wait',sub,sk,hero)


# --- 200610 逆反毒杀
class Skill200610(Skill):
    def __init__(self):
        super().__init__(200610,'逆反毒杀','使敌军群体恐慌(83%)并怯战，持续2回合',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r=calc_skill_addition_rate(83,0.76,hero.attrs['int'])
        for t in hero.get_target(sk.limit,2):
            t.be_hurt(hero,{'type':3,'rate':r},sk)
            if not t.is_attack_limit():
                t.state['attackLimit']={'rounds':2,'from':{'hero':hero,'skill':sk}}
        return True


# --- 200623 锦马慑敌
class Skill200623(Skill):
    def __init__(self):
        super().__init__(200623,'锦马慑敌','对敌军单体攻击使其动摇(140%)，并每回合50%几率混乱，持续2回合',2,30,True,2)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        dot_r=calc_skill_addition_rate(140,1.3,hero.attrs['atk'])
        tgs=hero.get_target(sk.limit,1)
        if not tgs: return True
        t=tgs[0]
        t.be_hurt(hero,{'type':3,'rate':dot_r},sk)
        rl=[2]
        def on_rnd():
            rl[0]-=1
            if get_random_bool(50) and not t.is_confusion():
                t.state['confusion']={'rounds':1,'from':{'hero':hero,'skill':sk}}
            if rl[0]<=0: t.clear_hook('回合开始时',f'{sk.id}_ctrl')
        t.add_hook('回合开始时',f'{sk.id}_ctrl',on_rnd,sk,hero)
        return True


# --- 200624 轻侠妄为
class Skill200624(Skill):
    def __init__(self):
        super().__init__(200624,'轻侠妄为','前3回合洞察，攻击伤害+40%',1,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v=calc_skill_addition_rate(40,0.32,hero.attrs['atk'])
        hero.add_state('attackDamageAdd',v,3,sk,hero)
        hero.state['insight']={'rounds':3,'from':{'hero':hero,'skill':sk}}


# --- 200633 黄须虎子
class Skill200633(Skill):
    def __init__(self):
        super().__init__(200633,'黄须虎子','攻击伤害+45%，进入分兵状态普攻后附近敌军受伤(100%)，持续2回合',2,25,True,1)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v=calc_skill_addition_rate(45,0.38,hero.attrs['atk'])
        split_r=calc_skill_addition_rate(100,0.92,hero.attrs['atk'])
        hero.add_state('attackDamageAdd',v,2,sk,hero)
        def on_atk(t,di,s2,dmg):
            if di.get('type')==1:
                enemy=hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
                for nb in [e for e in enemy if e is not t and e.arms>0]:
                    nb.be_hurt(hero,{'type':1,'rate':split_r},sk)
        hero.add_hook('造成伤害后',f'{sk.id}_split',on_atk,sk,hero)
        return True


# --- 200643 愈战愈勇
class Skill200643(Skill):
    def __init__(self):
        super().__init__(200643,'愈战愈勇','自身攻击伤害每回合叠加10%',4,'--',True,1)
    def call(self, hero, target=None):
        from .skills import Skill1012
        obj=Skill1012(); obj.id=self.id
        return obj.call(hero,target)


# --- 200644 步步为营
class Skill200644(Skill):
    def __init__(self):
        super().__init__(200644,'步步为营','自身受到所有伤害每回合叠加降低11%',4,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        hero.add_state('beAttackDamageSub',11,-1,sk,hero)
        hero.add_state('beInteDamageSub',11,-1,sk,hero)
        def sub():
            hero.add_state('beAttackDamageSub',11,-1,sk,hero,True)
            hero.add_state('beInteDamageSub',11,-1,sk,hero,True)
        hero.add_hook('回合开始时',f'{sk.id}_stack',sub,sk,hero)


# --- 200645 深谋远虑
class Skill200645(Skill):
    def __init__(self):
        super().__init__(200645,'深谋远虑','自身谋略攻击伤害每回合叠加11%',4,'--',True,1)
    def call(self, hero, target=None):
        from .skills import Skill1032
        obj=Skill1032(); obj.id=self.id
        return obj.call(hero,target)


# --- 200652 双艳
class Skill200652(Skill):
    def __init__(self):
        super().__init__(200652,'双艳','使敌军群体暴走2回合并100%使其攻击距离-1',2,30,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        for t in hero.get_target(sk.limit,2):
            if not t.is_confusion():
                t.state['confusion']={'rounds':2,'from':{'hero':hero,'skill':sk}}
            t.attrs['limit']=max(1,t.attrs.get('limit',3)-1)
        return True


# --- 200658 钝兵挫锐
class Skill200658(Skill):
    def __init__(self):
        super().__init__(200658,'钝兵挫锐','普通攻击后对目标猛攻(200%)并使其怯战1回合',3,28,True,0)
    def call(self, hero, target=None):
        if target is None: return
        sk = self
        rate=calc_skill_addition_rate(200,1.9,hero.attrs['atk'])
        target.be_hurt(hero,{'type':1,'rate':rate},sk)
        if not target.is_attack_limit():
            target.state['attackLimit']={'rounds':1,'from':{'hero':hero,'skill':sk}}


# --- 200659 怯心夺志
class Skill200659(Skill):
    def __init__(self):
        super().__init__(200659,'怯心夺志','普通攻击后对目标猛攻(200%)并使其犹豫1回合',3,28,True,0)
    def call(self, hero, target=None):
        if target is None: return
        sk = self
        rate=calc_skill_addition_rate(200,1.9,hero.attrs['atk'])
        target.be_hurt(hero,{'type':1,'rate':rate},sk)
        if not target.is_active_limit():
            target.state['activeLimit']={'rounds':1,'from':{'hero':hero,'skill':sk}}


# --- 200673 掎角之势
class Skill200673(Skill):
    def __init__(self):
        super().__init__(200673,'掎角之势','对敌军单体攻击(180%)和谋略攻击(143%)，目标独立判定',2,40,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r1=calc_skill_addition_rate(180,1.7,hero.attrs['atk'])
        r2=calc_skill_addition_rate(143,1.35,hero.attrs['int'])
        t1=hero.get_target(sk.limit,1)
        if t1: t1[0].be_hurt(hero,{'type':1,'rate':r1},sk)
        t2=hero.get_target(sk.limit,1)
        if t2: t2[0].be_hurt(hero,{'type':2,'rate':r2},sk)
        return True


# --- 200674 一夫当关
class Skill200674(Skill):
    def __init__(self):
        super().__init__(200674,'一夫当关','前2回合援护友军全体，自身受攻击伤害降低70%（仅前锋生效）',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        if hero.pos_name != '前锋': return
        sk = self
        v=calc_skill_addition_rate(70,0.6,hero.attrs['def'])
        hero.add_state('beAttackDamageSub',v,2,sk,hero)


# --- 200676 雄兵破敌
class Skill200676(Skill):
    def __init__(self):
        super().__init__(200676,'雄兵破敌','1回合准备，对敌军群体攻击(210%)，并使其防御谋略降低65持续2回合',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        rate=calc_skill_addition_rate(210,2.0,hero.attrs['atk'])
        sub_v=calc_skill_addition_rate(65,0.55,hero.attrs['atk'])
        def subskill():
            for t in hero.get_target(sk.limit,2):
                t.be_hurt(hero,{'type':1,'rate':rate},sk)
                t.attrs['def']=keep_two_decimal(max(0,t.attrs['def']-sub_v))
                t.attrs['int']=keep_two_decimal(max(0,t.attrs['int']-sub_v))
        hero.add_ready_skill(sk,1,subskill)
        return True


# --- 200677 险途暗渡
class Skill200677(Skill):
    def __init__(self):
        super().__init__(200677,'险途暗渡','对敌军群体攻击(150%)并附加动摇(80%)持续1回合，士气降低10',2,40,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r1=calc_skill_addition_rate(150,1.4,hero.attrs['atk'])
        r2=calc_skill_addition_rate(80,0.72,hero.attrs['atk'])
        for t in hero.get_target(sk.limit,2):
            t.be_hurt(hero,{'type':1,'rate':r1},sk)
            t.be_hurt(hero,{'type':3,'rate':r2},sk)
            if hasattr(t,'morale'): t.morale=max(0,t.morale-10)
        return True


# --- 200680 定军扬威
class Skill200680(Skill):
    def __init__(self):
        super().__init__(200680,'定军扬威','对敌军群体攻击(100%)并挑衅，自身受攻击伤害降低25%',2,120,True,2)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(100,0.92,hero.attrs['atk'])
        v=calc_skill_addition_rate(25,0.2,hero.attrs['def'])
        for t in hero.get_target(sk.limit,2):
            t.be_hurt(hero,{'type':1,'rate':rate},sk)
            t.state['taunt']={'rounds':1,'target':hero,'from':{'hero':hero,'skill':sk}}
        hero.add_state('beAttackDamageSub',v,1,sk,hero)
        return True


# --- 200683 伐谋
class Skill200683(Skill):
    def __init__(self):
        super().__init__(200683,'伐谋','对敌军单体猛烈谋略攻击(209%)，并使其攻谋降低45持续2回合',2,40,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(209,1.95,hero.attrs['int'])
        sub_v=calc_skill_addition_rate(45,0.38,hero.attrs['int'])
        tgs=hero.get_target(sk.limit,1)
        if not tgs: return True
        t=tgs[0]
        t.be_hurt(hero,{'type':2,'rate':rate},sk)
        t.attrs['atk']=keep_two_decimal(max(0,t.attrs['atk']-sub_v))
        t.attrs['int']=keep_two_decimal(max(0,t.attrs['int']-sub_v))
        return True


# --- 200684 道行险阻
class Skill200684(Skill):
    def __init__(self):
        super().__init__(200684,'道行险阻','使敌军单体防御谋略降低50持续1回合，并在其下次行动前谋略攻击(150%)和攻击(150%)',2,40,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        sub_v=calc_skill_addition_rate(50,0.42,hero.attrs['atk'])
        r1=calc_skill_addition_rate(150,1.4,hero.attrs['int'])
        r2=calc_skill_addition_rate(150,1.4,hero.attrs['atk'])
        tgs=hero.get_target(sk.limit,1)
        if not tgs: return True
        t=tgs[0]
        t.attrs['def']=keep_two_decimal(max(0,t.attrs['def']-sub_v))
        t.attrs['int']=keep_two_decimal(max(0,t.attrs['int']-sub_v))
        fired=[False]
        def oh():
            if not fired[0]:
                fired[0]=True
                t.be_hurt(hero,{'type':2,'rate':r1},sk)
                t.be_hurt(hero,{'type':1,'rate':r2},sk)
                t.clear_hook('行动前',f'{sk.id}_delay')
        t.add_hook('行动前',f'{sk.id}_delay',oh,sk,hero,'debuff')
        return True


# --- 200685 黄天余音
class Skill200685(Skill):
    def __init__(self):
        super().__init__(200685,'黄天余音','吸取敌军单体26全属性附加于自身与友军单体，持续1回合',2,120,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v=calc_skill_addition_rate(26,0.2,hero.attrs['int'])
        tgs=hero.get_target(sk.limit,1)
        if tgs:
            for k in ('atk','def','int','spd'):
                tgs[0].attrs[k]=keep_two_decimal(max(0,tgs[0].attrs[k]-v))
        hero.add_state('attackDamageAdd',v,1,sk,hero)
        hero.add_state('inteDamageAdd',v,1,sk,hero)
        allies=hero.get_target(sk.limit,1,3)
        if allies:
            allies[0].add_state('attackDamageAdd',v,1,sk,hero)
            allies[0].add_state('inteDamageAdd',v,1,sk,hero)
        return True


# --- 200689 不动如山
class Skill200689(Skill):
    def __init__(self):
        super().__init__(200689,'不动如山','行动时移除所有有害效果，防御+100谋略+25',4,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        def sub():
            hero.clear_debuff(sk,hero)
            hero.attrs['def']=keep_two_decimal(hero.attrs['def']+100)
            hero.attrs['int']=keep_two_decimal(hero.attrs['int']+25)
        hero.add_hook('行动时',f'{sk.id}_act',sub,sk,hero)


# --- 200691 白刃
class Skill200691(Skill):
    def __init__(self):
        super().__init__(200691,'白刃','前3回合敌我全军谋略伤害降低35%，我军骑兵步兵防御+45；结束后攻击+45持续3回合',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        enemy=hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        for e in team+enemy:
            e.add_state('inteDamageSub',35,3,sk,hero)
        for e in team:
            e.attrs['def']=keep_two_decimal(e.attrs['def']+45)
        def on_end():
            if hero.manager.round==4:
                hero.clear_hook('回合开始时',f'{sk.id}_end')
                for e in team:
                    e.add_state('attackDamageAdd',45,3,sk,hero)
        hero.add_hook('回合开始时',f'{sk.id}_end',on_end,sk,hero)


# --- 200704 银龙孤胆
class Skill200704(Skill):
    def __init__(self):
        super().__init__(200704,'银龙孤胆','1回合准备，对随机敌军单体发动7次攻击，首次80%每次递增7%',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        def subskill():
            base=calc_skill_addition_rate(80,0.72,hero.attrs['atk'])
            for i in range(7):
                rate=keep_two_decimal(base*(1+0.07*i))
                tgs=hero.get_target(sk.limit,1)
                if tgs: tgs[0].be_hurt(hero,{'type':1,'rate':rate},sk)
        hero.add_ready_skill(sk,1,subskill)
        return True


# --- 200706 方阵掩杀
class Skill200706(Skill):
    def __init__(self):
        super().__init__(200706,'方阵掩杀','普通攻击后对目标猛攻(235%)，并使其下次攻击伤害大幅降低',3,40,True,0)
    def call(self, hero, target=None):
        if target is None: return
        sk = self
        rate=calc_skill_addition_rate(235,2.2,hero.attrs['atk'])
        target.be_hurt(hero,{'type':1,'rate':rate},sk)
        target.add_state('attackDamageSub',9999,1,sk,hero)


# --- 200708 幽兰洛神
class Skill200708(Skill):
    def __init__(self):
        super().__init__(200708,'幽兰洛神','恢复友军单体兵力(105%)，并使其受普攻伤害降低33%',2,120,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rv=calc_skill_addition_rate(105,0.97,hero.attrs['int'])
        v=calc_skill_addition_rate(33,0.26,hero.attrs['int'])
        tgs=hero.get_target(sk.limit,1,3)
        if tgs:
            tgs[0].recover(calc_recover(hero,rv,0.97,2),hero,sk.name)
            tgs[0].add_state('beAttackDamageSub',v,2,sk,hero)
        return True


# --- 200712 智取仁守
class Skill200712(Skill):
    def __init__(self):
        super().__init__(200712,'智取仁守','使敌军群体追击战法伤害降低33%并陷入犹豫2回合',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v=calc_skill_addition_rate(33,0.26,hero.attrs['int'])
        for t in hero.get_target(sk.limit,2):
            t.add_state('chaseDamageSub',v,2,sk,hero)
            if not t.is_active_limit():
                t.state['activeLimit']={'rounds':2,'from':{'hero':hero,'skill':sk}}
        return True


# --- 200714 万箭齐发
class Skill200714(Skill):
    def __init__(self):
        super().__init__(200714,'万箭齐发','1回合准备，对敌军全体攻击(180%)，并使随机敌军单体攻击伤害大幅降低1回合',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        rate=calc_skill_addition_rate(180,1.7,hero.attrs['atk'])
        def subskill():
            targets=hero.get_target(sk.limit,3)
            for t in targets: t.be_hurt(hero,{'type':1,'rate':rate},sk)
            alive=[t for t in targets if t.arms>0]
            if alive: random.choice(alive).add_state('attackDamageSub',9999,1,sk,hero)
        hero.add_ready_skill(sk,1,subskill)
        return True


# --- 200715 折冲御侮
class Skill200715(Skill):
    def __init__(self):
        super().__init__(200715,'折冲御侮','每回合行动时恢复自身(80%)，并使友军全体受伤害降低15%持续1回合',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        rv=calc_skill_addition_rate(80,0.72,hero.attrs['def'])
        v=calc_skill_addition_rate(15,0.12,hero.attrs['def'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        def sub():
            hero.recover(calc_recover(hero,rv,0.72,2),hero,sk.name)
            for e in team:
                e.add_state('beAttackDamageSub',v,1,sk,hero)
                e.add_state('beInteDamageSub',v,1,sk,hero)
        hero.add_hook('行动时',f'{sk.id}_act',sub,sk,hero)


# --- 200716 怒而难犯
class Skill200716(Skill):
    def __init__(self):
        super().__init__(200716,'怒而难犯','受伤后35%几率对攻击者反击(120%)，并使其攻击-30持续1回合',4,'--',True,2)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        rate=calc_skill_addition_rate(120,1.1,hero.attrs['atk'])
        sub_v=calc_skill_addition_rate(30,0.24,hero.attrs['atk'])
        def on_hurt(attacker,di,s):
            if get_random_bool(35) and attacker is not hero and attacker.arms>0:
                attacker.be_hurt(hero,{'type':1,'rate':rate},sk)
                attacker.attrs['atk']=keep_two_decimal(max(0,attacker.attrs['atk']-sub_v))
        hero.add_hook('受伤时',f'{sk.id}_counter',on_hurt,sk,hero)


# --- 200717 绝地反击
class Skill200717(Skill):
    def __init__(self):
        super().__init__(200717,'绝地反击','兵力低于35%时自身攻击伤害+80%，并激活追击对敌单体攻击(170%)，每局最多1次',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self; fired=[False]
        rate=calc_skill_addition_rate(170,1.6,hero.attrs['atk'])
        def on_hurt(a,di,s):
            if fired[0]: return
            max_arms=getattr(hero,'max_arms',hero.arms)
            if hero.arms/max(max_arms,1)<0.35:
                fired[0]=True
                hero.add_state('attackDamageAdd',80,-1,sk,hero)
                tgs=hero.get_target(sk.limit,1)
                if tgs: tgs[0].be_hurt(hero,{'type':1,'rate':rate},sk)
        hero.add_hook('受伤时',f'{sk.id}_trigger',on_hurt,sk,hero)


# --- 200718 先发制人
class Skill200718(Skill):
    def __init__(self):
        super().__init__(200718,'先发制人','第1回合行动时对敌军群体攻击(180%)并使其攻击伤害降低30%，持续2回合',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        rate=calc_skill_addition_rate(180,1.7,hero.attrs['atk'])
        v=calc_skill_addition_rate(30,0.24,hero.attrs['atk'])
        def sub():
            if hero.manager.round==1:
                for t in hero.get_target(sk.limit,2):
                    t.be_hurt(hero,{'type':1,'rate':rate},sk)
                    t.add_state('attackDamageSub',v,2,sk,hero)
                hero.clear_hook('行动时',f'{sk.id}_act')
        hero.add_hook('行动时',f'{sk.id}_act',sub,sk,hero)


# --- 200720 神速奔袭
class Skill200720(Skill):
    def __init__(self):
        super().__init__(200720,'神速奔袭','速度最高时行动时额外追击对敌单体攻击(160%)，每回合最多1次',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        rate=calc_skill_addition_rate(160,1.5,hero.attrs['atk'])
        def sub():
            team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
            alive=[e for e in team if e.arms>0]
            if alive and hero.attrs['spd']==max(e.attrs['spd'] for e in alive):
                tgs=hero.get_target(sk.limit,1)
                if tgs: tgs[0].be_hurt(hero,{'type':1,'rate':rate},sk)
        hero.add_hook('行动时',f'{sk.id}_act',sub,sk,hero)


# --- 200721 同仇敌忾
class Skill200721(Skill):
    def __init__(self):
        super().__init__(200721,'同仇敌忾','友军每造成一次攻击伤害，对敌方随机单体追击(55%)',4,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        rate=calc_skill_addition_rate(55,0.5,hero.attrs['atk'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in [x for x in team if x is not hero]:
            def ms(e=e):
                def on_dmg(t,di,s2,dmg):
                    if di.get('type')==1:
                        tgs=hero.get_target(sk.limit,1)
                        if tgs: tgs[0].be_hurt(hero,{'type':1,'rate':rate},sk)
                e.add_hook('造成伤害后',f'{sk.id}_{id(e)}',on_dmg,sk,hero)
            ms()


# --- 200722 以退为进
class Skill200722(Skill):
    def __init__(self):
        super().__init__(200722,'以退为进','受到伤害后50%几率使攻击者陷入混乱1回合，并恢复自身(60%)',4,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        rv=calc_skill_addition_rate(60,0.55,hero.attrs['def'])
        def on_hurt(attacker,di,s):
            if get_random_bool(50) and attacker is not hero and attacker.arms>0:
                if not attacker.is_confusion():
                    attacker.state['confusion']={'rounds':1,'from':{'hero':hero,'skill':sk}}
                hero.recover(calc_recover(hero,rv,0.55,2),hero,sk.name)
        hero.add_hook('受伤时',f'{sk.id}_counter',on_hurt,sk,hero)


# --- 200723 长驱直入
class Skill200723(Skill):
    def __init__(self):
        super().__init__(200723,'长驱直入','对敌军单体攻击(200%)并穿刺，使其攻击距离-2持续2回合',2,40,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(200,1.9,hero.attrs['atk'])
        tgs=hero.get_target(sk.limit,1)
        if not tgs: return True
        t=tgs[0]
        t.be_hurt(hero,{'type':1,'rate':rate,'pierce':True},sk)
        t.attrs['limit']=max(1,t.attrs.get('limit',3)-2)
        return True


# --- 200724 霹雳手段
class Skill200724(Skill):
    def __init__(self):
        super().__init__(200724,'霹雳手段','对敌军群体攻击(135%)，35%几率使其混乱1回合',2,35,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(135,1.26,hero.attrs['atk'])
        for t in hero.get_target(sk.limit,2):
            t.be_hurt(hero,{'type':1,'rate':rate},sk)
            if get_random_bool(35) and not t.is_confusion():
                t.state['confusion']={'rounds':1,'from':{'hero':hero,'skill':sk}}
        return True


# --- 200725 将威摄敌
class Skill200725(Skill):
    def __init__(self):
        super().__init__(200725,'将威摄敌','对敌军全体造成谋略攻击(120%)并使其攻击降低40持续2回合',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(120,1.1,hero.attrs['int'])
        sub_v=calc_skill_addition_rate(40,0.32,hero.attrs['int'])
        for t in hero.get_target(sk.limit,3):
            t.be_hurt(hero,{'type':2,'rate':rate},sk)
            t.attrs['atk']=keep_two_decimal(max(0,t.attrs['atk']-sub_v))
        return True


# --- 200726 鼓角齐鸣
class Skill200726(Skill):
    def __init__(self):
        super().__init__(200726,'鼓角齐鸣','使友军全体攻击+50持续2回合，并激活追击对敌全体攻击(80%)',2,120,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v=calc_skill_addition_rate(50,0.42,hero.attrs['int'])
        rate=calc_skill_addition_rate(80,0.72,hero.attrs['atk'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.attrs['atk']=keep_two_decimal(e.attrs['atk']+v)
        for t in hero.get_target(sk.limit,3):
            t.be_hurt(hero,{'type':1,'rate':rate},sk)
        return True


# --- 200727 军令如山
class Skill200727(Skill):
    def __init__(self):
        super().__init__(200727,'军令如山','友军全体攻击谋略+35，并免疫控制效果1回合',2,120,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v=calc_skill_addition_rate(35,0.28,hero.attrs['int'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.attrs['atk']=keep_two_decimal(e.attrs['atk']+v)
            e.attrs['int']=keep_two_decimal(e.attrs['int']+v)
            e.state['immuneCtrl']={'rounds':1,'from':{'hero':hero,'skill':sk}}
        return True


# --- 200728 奇正相生
class Skill200728(Skill):
    def __init__(self):
        super().__init__(200728,'奇正相生','对敌军单体先谋略攻击(150%)后攻击(150%)，依次叠加10%',2,40,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r1=calc_skill_addition_rate(150,1.4,hero.attrs['int'])
        r2=calc_skill_addition_rate(150,1.4,hero.attrs['atk'])
        bonus=getattr(hero,f'_228_bonus',0)
        tgs=hero.get_target(sk.limit,1)
        if tgs:
            tgs[0].be_hurt(hero,{'type':2,'rate':keep_two_decimal(r1*(1+bonus/100))},sk)
            tgs[0].be_hurt(hero,{'type':1,'rate':keep_two_decimal(r2*(1+bonus/100))},sk)
        hero.__dict__[f'_228_bonus']=bonus+10
        return True


# --- 200729 饥渴之兵
class Skill200729(Skill):
    def __init__(self):
        super().__init__(200729,'饥渴之兵','每回合行动时依据兵力损失比例提升攻击伤害，最高+80%',4,'--',True,2)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        def sub():
            max_arms=getattr(hero,'max_arms',hero.arms)
            lost=max(0,1-hero.arms/max(max_arms,1))
            bonus=keep_two_decimal(min(80,lost*100))
            hero.add_state('attackDamageAdd',bonus,1,sk,hero)
        hero.add_hook('行动时',f'{sk.id}_act',sub,sk,hero)


# --- 200730 御众之法
class Skill200730(Skill):
    def __init__(self):
        super().__init__(200730,'御众之法','使友军全体谋略+50防御+50，持续2回合',2,120,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v=calc_skill_addition_rate(50,0.42,hero.attrs['int'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.attrs['int']=keep_two_decimal(e.attrs['int']+v)
            e.attrs['def']=keep_two_decimal(e.attrs['def']+v)
        return True


# --- 200731 蓄势破锋
class Skill200731(Skill):
    def __init__(self):
        super().__init__(200731,'蓄势破锋','2回合准备，对敌军全体攻击(320%)并使其受攻击伤害+35%持续1回合',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        rate=calc_skill_addition_rate(320,3.0,hero.attrs['atk'])
        v=calc_skill_addition_rate(35,0.28,hero.attrs['atk'])
        def subskill():
            for t in hero.get_target(sk.limit,3):
                t.be_hurt(hero,{'type':1,'rate':rate},sk)
                t.add_state('beAttackDamageAdd',v,1,sk,hero)
        hero.add_ready_skill(sk,2,subskill)
        return True


# --- 200732 无当飞军
class Skill200732(Skill):
    def __init__(self):
        super().__init__(200732,'无当飞军','对敌军群体攻击(140%)，并使其攻击距离-1，速度降低20，持续2回合',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(140,1.3,hero.attrs['atk'])
        spd_sub=calc_skill_addition_rate(20,0.16,hero.attrs['atk'])
        for t in hero.get_target(sk.limit,2):
            t.be_hurt(hero,{'type':1,'rate':rate},sk)
            t.attrs['limit']=max(1,t.attrs.get('limit',3)-1)
            t.attrs['spd']=keep_two_decimal(max(0,t.attrs['spd']-spd_sub))
        return True


# --- 200733 兵强马壮
class Skill200733(Skill):
    def __init__(self):
        super().__init__(200733,'兵强马壮','使友军全体攻击+60速度+20，持续2回合',2,120,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v_atk=calc_skill_addition_rate(60,0.5,hero.attrs['int'])
        v_spd=calc_skill_addition_rate(20,0.16,hero.attrs['int'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.attrs['atk']=keep_two_decimal(e.attrs['atk']+v_atk)
            e.attrs['spd']=keep_two_decimal(e.attrs['spd']+v_spd)
        return True


# --- 200734 步步紧逼
class Skill200734(Skill):
    def __init__(self):
        super().__init__(200734,'步步紧逼','普通攻击后追击对敌单体攻击(100%)，每发动1次追击伤害+15%，最多5次',3,30,True,0)
    def call(self, hero, target=None):
        if target is None: return
        sk = self
        stk=getattr(hero,f'_{sk.id}_stk',0)
        rate=calc_skill_addition_rate(keep_two_decimal(100+15*stk),keep_two_decimal(0.92+0.12*stk),hero.attrs['atk'])
        target.be_hurt(hero,{'type':1,'rate':rate},sk)
        if stk<5: hero.__dict__[f'_{sk.id}_stk']=stk+1


# --- 200735 蓬矢桑弧
class Skill200735(Skill):
    def __init__(self):
        super().__init__(200735,'蓬矢桑弧','对敌军2目标谋略攻击(130%)，并80%几率附加恐慌(90%)持续1回合',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(130,1.22,hero.attrs['int'])
        fear_r=calc_skill_addition_rate(90,0.82,hero.attrs['int'])
        for t in hero.get_target(sk.limit,2):
            t.be_hurt(hero,{'type':2,'rate':rate},sk)
            if get_random_bool(80):
                t.be_hurt(hero,{'type':3,'rate':fear_r},sk)
        return True


# --- 200736 破军斩将
class Skill200736(Skill):
    def __init__(self):
        super().__init__(200736,'破军斩将','对敌军单体攻击(260%)，若目标兵力低于50%则额外攻击(130%)',2,40,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(260,2.4,hero.attrs['atk'])
        extra=calc_skill_addition_rate(130,1.2,hero.attrs['atk'])
        tgs=hero.get_target(sk.limit,1)
        if not tgs: return True
        t=tgs[0]
        t.be_hurt(hero,{'type':1,'rate':rate},sk)
        max_arms=getattr(t,'max_arms',t.arms)
        if t.arms/max(max_arms,1)<0.5:
            t.be_hurt(hero,{'type':1,'rate':extra},sk)
        return True


# --- 200737 攻心为上
class Skill200737(Skill):
    def __init__(self):
        super().__init__(200737,'攻心为上','对敌军全体造成恐慌(130%)并使其谋略-40，持续2回合',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r=calc_skill_addition_rate(130,1.22,hero.attrs['int'])
        sub_v=calc_skill_addition_rate(40,0.32,hero.attrs['int'])
        for t in hero.get_target(sk.limit,3):
            t.be_hurt(hero,{'type':3,'rate':r},sk)
            t.attrs['int']=keep_two_decimal(max(0,t.attrs['int']-sub_v))
        return True


# --- 200738 临难不顾
class Skill200738(Skill):
    def __init__(self):
        super().__init__(200738,'临难不顾','自身兵力低于40%时，攻击+100防御+60，并免疫1次伤害，每局1次',4,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self; fired=[False]
        def on_hurt(a,di,s):
            if fired[0]: return
            max_arms=getattr(hero,'max_arms',hero.arms)
            if hero.arms/max(max_arms,1)<0.4:
                fired[0]=True
                hero.attrs['atk']=keep_two_decimal(hero.attrs['atk']+100)
                hero.attrs['def']=keep_two_decimal(hero.attrs['def']+60)
                immune=[True]
                def oh2(a2,di2,s2):
                    if immune[0]: immune[0]=False; di2['immune']=True; hero.clear_hook('受伤时',f'{sk.id}_imm')
                hero.add_hook('受伤时',f'{sk.id}_imm',oh2,sk,hero)
        hero.add_hook('受伤时',f'{sk.id}_trigger',on_hurt,sk,hero)


# --- 200739 斩草除根
class Skill200739(Skill):
    def __init__(self):
        super().__init__(200739,'斩草除根','对敌军单体攻击(200%)，目标每有一个有害状态额外追加攻击(40%)',2,40,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(200,1.9,hero.attrs['atk'])
        extra=calc_skill_addition_rate(40,0.32,hero.attrs['atk'])
        tgs=hero.get_target(sk.limit,1)
        if not tgs: return True
        t=tgs[0]
        t.be_hurt(hero,{'type':1,'rate':rate},sk)
        debuffs=len([k for k,v in t.state.items() if isinstance(v,dict) and v.get('is_debuff',False)])
        for _ in range(debuffs):
            t.be_hurt(hero,{'type':1,'rate':extra},sk)
        return True


# --- 200740 兵无常势
class Skill200740(Skill):
    def __init__(self):
        super().__init__(200740,'兵无常势','每回合行动时随机发动：攻击敌单体(200%)/恢复友单体(150%)/使敌全体攻击-40，持续1回合',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        def sub():
            ch=random.randint(0,2)
            if ch==0:
                rate=calc_skill_addition_rate(200,1.9,hero.attrs['atk'])
                tgs=hero.get_target(sk.limit,1)
                if tgs: tgs[0].be_hurt(hero,{'type':1,'rate':rate},sk)
            elif ch==1:
                rv=calc_skill_addition_rate(150,1.4,hero.attrs['int'])
                tgs=hero.get_target(sk.limit,1,3)
                if tgs: tgs[0].recover(calc_recover(hero,rv,1.4,2),hero,sk.name)
            else:
                sub_v=calc_skill_addition_rate(40,0.32,hero.attrs['atk'])
                for t in hero.get_target(sk.limit,3):
                    t.attrs['atk']=keep_two_decimal(max(0,t.attrs['atk']-sub_v))
        hero.add_hook('行动时',f'{sk.id}_act',sub,sk,hero)


# --- 200741 摧枯拉朽
class Skill200741(Skill):
    def __init__(self):
        super().__init__(200741,'摧枯拉朽','对敌军全体攻击(160%)，并降低其防御30持续2回合',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(160,1.5,hero.attrs['atk'])
        sub_v=calc_skill_addition_rate(30,0.24,hero.attrs['atk'])
        for t in hero.get_target(sk.limit,3):
            t.be_hurt(hero,{'type':1,'rate':rate},sk)
            t.attrs['def']=keep_two_decimal(max(0,t.attrs['def']-sub_v))
        return True


# --- 200742 坚壁清野
class Skill200742(Skill):
    def __init__(self):
        super().__init__(200742,'坚壁清野','使友军全体防御+70，受攻击伤害-30%，持续2回合',2,120,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v=calc_skill_addition_rate(70,0.6,hero.attrs['int'])
        dv=calc_skill_addition_rate(30,0.24,hero.attrs['int'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.attrs['def']=keep_two_decimal(e.attrs['def']+v)
            e.add_state('beAttackDamageSub',dv,2,sk,hero)
        return True


# --- 200743 百战雄师
class Skill200743(Skill):
    def __init__(self):
        super().__init__(200743,'百战雄师','每回合行动前友军全体攻击+15，最多叠加5层',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self; stk=[0]
        v=calc_skill_addition_rate(15,0.12,hero.attrs['int'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        def sub():
            if stk[0]<5:
                stk[0]+=1
                for e in team:
                    e.attrs['atk']=keep_two_decimal(e.attrs['atk']+v)
        hero.add_hook('行动前',f'{sk.id}_act',sub,sk,hero)


# --- 200744 疾风迅雷
class Skill200744(Skill):
    def __init__(self):
        super().__init__(200744,'疾风迅雷','对敌军2目标攻击(155%)并使其速度降低35，持续2回合',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(155,1.45,hero.attrs['atk'])
        sub_v=calc_skill_addition_rate(35,0.28,hero.attrs['atk'])
        for t in hero.get_target(sk.limit,2):
            t.be_hurt(hero,{'type':1,'rate':rate},sk)
            t.attrs['spd']=keep_two_decimal(max(0,t.attrs['spd']-sub_v))
        return True


# --- 200745 兵甲修饬
class Skill200745(Skill):
    def __init__(self):
        super().__init__(200745,'兵甲修饬','使友军单体防御+80谋略+50，并恢复兵力(100%)',2,120,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        def_v=calc_skill_addition_rate(80,0.68,hero.attrs['int'])
        int_v=calc_skill_addition_rate(50,0.42,hero.attrs['int'])
        rv=calc_skill_addition_rate(100,0.92,hero.attrs['int'])
        tgs=hero.get_target(sk.limit,1,3)
        if tgs:
            tgs[0].attrs['def']=keep_two_decimal(tgs[0].attrs['def']+def_v)
            tgs[0].attrs['int']=keep_two_decimal(tgs[0].attrs['int']+int_v)
            tgs[0].recover(calc_recover(hero,rv,0.92,2),hero,sk.name)
        return True


# --- 200746 固守待援
class Skill200746(Skill):
    def __init__(self):
        super().__init__(200746,'固守待援','使友军全体进入防御状态：受伤降低35%，每回合恢复(50%)，持续2回合',2,120,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        dv=calc_skill_addition_rate(35,0.28,hero.attrs['int'])
        rv=calc_skill_addition_rate(50,0.46,hero.attrs['int'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('beAttackDamageSub',dv,2,sk,hero)
            e.add_state('beInteDamageSub',dv,2,sk,hero)
            def mh(e=e):
                cnt=[2]
                def on_rnd():
                    cnt[0]-=1
                    e.recover(calc_recover(hero,rv,0.46,2),hero,sk.name)
                    if cnt[0]<=0: e.clear_hook('回合开始时',f'{sk.id}_rec_{id(e)}')
                e.add_hook('回合开始时',f'{sk.id}_rec_{id(e)}',on_rnd,sk,hero)
            mh()
        return True


# --- 200747 运筹帷幄
class Skill200747(Skill):
    def __init__(self):
        super().__init__(200747,'运筹帷幄','使友军全体谋略+60，并使其主动战法发动率+15%，持续2回合',2,120,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v=calc_skill_addition_rate(60,0.5,hero.attrs['int'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.attrs['int']=keep_two_decimal(e.attrs['int']+v)
            e.add_state('skillRateAdd',15,2,sk,hero)
        return True


# --- 200748 声东击西
class Skill200748(Skill):
    def __init__(self):
        super().__init__(200748,'声东击西','对敌军单体谋略攻击(90%)，再对随机另一目标攻击(160%)',2,30,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r1=calc_skill_addition_rate(90,0.82,hero.attrs['int'])
        r2=calc_skill_addition_rate(160,1.5,hero.attrs['atk'])
        t1=hero.get_target(sk.limit,1)
        if t1: t1[0].be_hurt(hero,{'type':2,'rate':r1},sk)
        t2=hero.get_target(sk.limit,1)
        if t2: t2[0].be_hurt(hero,{'type':1,'rate':r2},sk)
        return True


# --- 200749 趁火打劫
class Skill200749(Skill):
    def __init__(self):
        super().__init__(200749,'趁火打劫','对兵力低于50%的敌军单体攻击(280%)，若无则对随机敌军攻击(140%)',2,35,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        enemy=hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        low=[e for e in enemy if e.arms>0 and e.arms/max(getattr(e,'max_arms',e.arms),1)<0.5]
        if low:
            rate=calc_skill_addition_rate(280,2.6,hero.attrs['atk'])
            random.choice(low).be_hurt(hero,{'type':1,'rate':rate},sk)
        else:
            rate=calc_skill_addition_rate(140,1.3,hero.attrs['atk'])
            tgs=hero.get_target(sk.limit,1)
            if tgs: tgs[0].be_hurt(hero,{'type':1,'rate':rate},sk)
        return True


# --- 200750 反间奇谋
class Skill200750(Skill):
    def __init__(self):
        super().__init__(200750,'反间奇谋','使敌军单体陷入混乱2回合，并使其每回合受谋略攻击(80%)',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r=calc_skill_addition_rate(80,0.72,hero.attrs['int'])
        tgs=hero.get_target(sk.limit,1)
        if not tgs: return True
        t=tgs[0]
        if not t.is_confusion():
            t.state['confusion']={'rounds':2,'from':{'hero':hero,'skill':sk}}
        cnt=[2]
        def on_rnd():
            cnt[0]-=1
            t.be_hurt(hero,{'type':2,'rate':r},sk)
            if cnt[0]<=0: t.clear_hook('回合开始时',f'{sk.id}_dot')
        t.add_hook('回合开始时',f'{sk.id}_dot',on_rnd,sk,hero)
        return True


# --- 200751 虚实相间
class Skill200751(Skill):
    def __init__(self):
        super().__init__(200751,'虚实相间','50%几率攻击伤害+55%；50%几率谋略伤害+55%，本回合生效',4,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        v=calc_skill_addition_rate(55,0.46,hero.attrs['atk'])
        def sub():
            if get_random_bool(50):
                hero.add_state('attackDamageAdd',v,1,sk,hero)
            else:
                hero.add_state('inteDamageAdd',v,1,sk,hero)
        hero.add_hook('行动前',f'{sk.id}_act',sub,sk,hero)


# --- 200752 合众连横
class Skill200752(Skill):
    def __init__(self):
        super().__init__(200752,'合众连横','使友军全体攻击+40谋略+40，并使敌军全体受伤害+20%，持续1回合',2,120,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v=calc_skill_addition_rate(40,0.32,hero.attrs['int'])
        dv=calc_skill_addition_rate(20,0.16,hero.attrs['int'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        enemy=hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        for e in team:
            e.attrs['atk']=keep_two_decimal(e.attrs['atk']+v)
            e.attrs['int']=keep_two_decimal(e.attrs['int']+v)
        for t in enemy:
            t.add_state('beAttackDamageAdd',dv,1,sk,hero)
            t.add_state('beInteDamageAdd',dv,1,sk,hero)
        return True


# --- 200753 兵贵神速
class Skill200753(Skill):
    def __init__(self):
        super().__init__(200753,'兵贵神速','使友军全体速度+60，持续3回合；速度最高者攻击+80',2,120,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        spd_v=calc_skill_addition_rate(60,0.5,hero.attrs['int'])
        atk_v=calc_skill_addition_rate(80,0.68,hero.attrs['int'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.attrs['spd']=keep_two_decimal(e.attrs['spd']+spd_v)
        alive=[e for e in team if e.arms>0]
        if alive:
            top=max(alive,key=lambda e: e.attrs['spd'])
            top.attrs['atk']=keep_two_decimal(top.attrs['atk']+atk_v)
        return True


# --- 200754 以逸待劳
class Skill200754(Skill):
    def __init__(self):
        super().__init__(200754,'以逸待劳','前2回合免疫控制，第3回合起对敌全体攻击(100%)并使其受伤+20%，持续1回合',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        hero.state['immuneCtrl']={'rounds':2,'from':{'hero':hero,'skill':sk}}
        def sub():
            if hero.manager.round>=3:
                rate=calc_skill_addition_rate(100,0.92,hero.attrs['atk'])
                dv=calc_skill_addition_rate(20,0.16,hero.attrs['atk'])
                for t in hero.get_target(sk.limit,3):
                    t.be_hurt(hero,{'type':1,'rate':rate},sk)
                    t.add_state('beAttackDamageAdd',dv,1,sk,hero)
        hero.add_hook('行动时',f'{sk.id}_act',sub,sk,hero)


# --- 200755 倒行逆施
class Skill200755(Skill):
    def __init__(self):
        super().__init__(200755,'倒行逆施','使敌军全体攻击谋略互换，持续2回合',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        for t in hero.get_target(sk.limit,3):
            t.attrs['atk'],t.attrs['int']=t.attrs['int'],t.attrs['atk']
            cnt=[2]
            def mh(t=t,old_atk=t.attrs['atk'],old_int=t.attrs['int']):
                def on_rnd():
                    cnt[0]-=1
                    if cnt[0]<=0:
                        t.attrs['atk']=old_int
                        t.attrs['int']=old_atk
                        t.clear_hook('回合开始时',f'{sk.id}_swap_{id(t)}')
                t.add_hook('回合开始时',f'{sk.id}_swap_{id(t)}',on_rnd,sk,hero)
            mh()
        return True


# --- 200756 势如破竹
class Skill200756(Skill):
    def __init__(self):
        super().__init__(200756,'势如破竹','每发动1次主动战法，攻击伤害累计+12%，最多5层',4,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self; stk=[0]
        v=calc_skill_addition_rate(12,0.1,hero.attrs['atk'])
        def sub():
            if stk[0]<5:
                stk[0]+=1
                hero.add_state('attackDamageAdd',v,-1,sk,hero,True)
        hero.add_hook('行动时',f'{sk.id}_act',sub,sk,hero)


# --- 200757 料敌如神
class Skill200757(Skill):
    def __init__(self):
        super().__init__(200757,'料敌如神','洞察敌军单体2回合，并使其下次主动战法无效',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        tgs=hero.get_target(sk.limit,1)
        if not tgs: return True
        t=tgs[0]
        t.state['insight']={'rounds':2,'from':{'hero':hero,'skill':sk}}
        fired=[False]
        def oh():
            if not fired[0]:
                fired[0]=True
                t.clear_hook('行动时',f'{sk.id}_null')
                return 'cancel'
        t.add_hook('行动时',f'{sk.id}_null',oh,sk,hero,'debuff')
        return True


# --- 200758 出奇制胜
class Skill200758(Skill):
    def __init__(self):
        super().__init__(200758,'出奇制胜','对敌军群体谋略攻击(105%)，若有混乱目标额外攻击(80%)',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r1=calc_skill_addition_rate(105,0.97,hero.attrs['int'])
        r2=calc_skill_addition_rate(80,0.72,hero.attrs['atk'])
        for t in hero.get_target(sk.limit,3):
            t.be_hurt(hero,{'type':2,'rate':r1},sk)
            if t.is_confusion():
                t.be_hurt(hero,{'type':1,'rate':r2},sk)
        return True


# --- 200759 金戈铁马
class Skill200759(Skill):
    def __init__(self):
        super().__init__(200759,'金戈铁马','使友军全体速度+40攻击+40，并激活追击对敌单体攻击(120%)',2,120,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v=calc_skill_addition_rate(40,0.32,hero.attrs['int'])
        rate=calc_skill_addition_rate(120,1.1,hero.attrs['atk'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.attrs['spd']=keep_two_decimal(e.attrs['spd']+v)
            e.attrs['atk']=keep_two_decimal(e.attrs['atk']+v)
        tgs=hero.get_target(sk.limit,1)
        if tgs: tgs[0].be_hurt(hero,{'type':1,'rate':rate},sk)
        return True


# --- 200760 壮士断腕
class Skill200760(Skill):
    def __init__(self):
        super().__init__(200760,'壮士断腕','自身受到致命伤时以1兵力存活，攻击+120，每局1次',4,'--',True,2)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self; fired=[False]
        def on_hurt(a,di,s):
            if fired[0]: return
            if di.get('damage',0)>=hero.arms:
                fired[0]=True
                di['damage']=hero.arms-1
                hero.arms=1
                hero.attrs['atk']=keep_two_decimal(hero.attrs['atk']+120)
        hero.add_hook('受伤时',f'{sk.id}_survive',on_hurt,sk,hero)


# --- 200761 四面楚歌
class Skill200761(Skill):
    def __init__(self):
        super().__init__(200761,'四面楚歌','对敌军全体造成恐慌(110%)并降低其速度30，持续2回合',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r=calc_skill_addition_rate(110,1.02,hero.attrs['int'])
        sub_v=calc_skill_addition_rate(30,0.24,hero.attrs['int'])
        for t in hero.get_target(sk.limit,3):
            t.be_hurt(hero,{'type':3,'rate':r},sk)
            t.attrs['spd']=keep_two_decimal(max(0,t.attrs['spd']-sub_v))
        return True


# --- 200762 急攻速战
class Skill200762(Skill):
    def __init__(self):
        super().__init__(200762,'急攻速战','前3回合攻击伤害+65%，第4回合后速度-50',1,'--',True,2)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v=calc_skill_addition_rate(65,0.55,hero.attrs['atk'])
        hero.add_state('attackDamageAdd',v,3,sk,hero)
        def sub():
            if hero.manager.round==4:
                hero.attrs['spd']=keep_two_decimal(max(0,hero.attrs['spd']-50))
                hero.clear_hook('行动时',f'{sk.id}_spd')
        hero.add_hook('行动时',f'{sk.id}_spd',sub,sk,hero)


# --- 200763 枕戈待旦
class Skill200763(Skill):
    def __init__(self):
        super().__init__(200763,'枕戈待旦','前2回合防御+80，第3回合起攻击+80速度+30',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        def_v=calc_skill_addition_rate(80,0.68,hero.attrs['def'])
        atk_v=calc_skill_addition_rate(80,0.68,hero.attrs['atk'])
        spd_v=calc_skill_addition_rate(30,0.24,hero.attrs['atk'])
        hero.attrs['def']=keep_two_decimal(hero.attrs['def']+def_v)
        def sub():
            if hero.manager.round==3:
                hero.attrs['def']=keep_two_decimal(max(0,hero.attrs['def']-def_v))
                hero.attrs['atk']=keep_two_decimal(hero.attrs['atk']+atk_v)
                hero.attrs['spd']=keep_two_decimal(hero.attrs['spd']+spd_v)
                hero.clear_hook('行动时',f'{sk.id}_switch')
        hero.add_hook('行动时',f'{sk.id}_switch',sub,sk,hero)


# --- 200764 勇将先登
class Skill200764(Skill):
    def __init__(self):
        super().__init__(200764,'勇将先登','第1回合对敌军全体攻击(220%)，之后每回合攻击伤害+15%，最多3层',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self; stk=[0]
        rate=calc_skill_addition_rate(220,2.05,hero.attrs['atk'])
        v=calc_skill_addition_rate(15,0.12,hero.attrs['atk'])
        def sub():
            if hero.manager.round==1:
                for t in hero.get_target(sk.limit,3):
                    t.be_hurt(hero,{'type':1,'rate':rate},sk)
            elif stk[0]<3:
                stk[0]+=1
                hero.add_state('attackDamageAdd',v,-1,sk,hero,True)
        hero.add_hook('行动时',f'{sk.id}_act',sub,sk,hero)


# --- 200765 千里奔袭
class Skill200765(Skill):
    def __init__(self):
        super().__init__(200765,'千里奔袭','对敌军单体攻击(300%)，若己方速度高于对方则额外攻击(100%)',2,40,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(300,2.8,hero.attrs['atk'])
        extra=calc_skill_addition_rate(100,0.92,hero.attrs['atk'])
        tgs=hero.get_target(sk.limit,1)
        if not tgs: return True
        t=tgs[0]
        t.be_hurt(hero,{'type':1,'rate':rate},sk)
        if hero.attrs.get('spd',100)>t.attrs.get('spd',100):
            t.be_hurt(hero,{'type':1,'rate':extra},sk)
        return True


# --- 200766 铁壁铜墙
class Skill200766(Skill):
    def __init__(self):
        super().__init__(200766,'铁壁铜墙','使友军全体免疫1次攻击伤害，防御+60，持续2回合',2,120,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        def_v=calc_skill_addition_rate(60,0.5,hero.attrs['int'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.attrs['def']=keep_two_decimal(e.attrs['def']+def_v)
            immune=[1]
            def mh(e=e):
                def oh(a,di,s):
                    if immune[0]>0 and di.get('type')==1:
                        immune[0]-=1
                        di['immune']=True
                        if immune[0]<=0: e.clear_hook('受伤时',f'{sk.id}_imm_{id(e)}')
                e.add_hook('受伤时',f'{sk.id}_imm_{id(e)}',oh,sk,hero)
            mh()
        return True


# --- 200767 虎豹骑
class Skill200767(Skill):
    def __init__(self):
        super().__init__(200767,'虎豹骑','对敌军群体攻击(145%)，60%几率使其速度降低40持续2回合',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(145,1.35,hero.attrs['atk'])
        sub_v=calc_skill_addition_rate(40,0.32,hero.attrs['atk'])
        for t in hero.get_target(sk.limit,2):
            t.be_hurt(hero,{'type':1,'rate':rate},sk)
            if get_random_bool(60):
                t.attrs['spd']=keep_two_decimal(max(0,t.attrs['spd']-sub_v))
        return True


# --- 200768 水淹七军
class Skill200768(Skill):
    def __init__(self):
        super().__init__(200768,'水淹七军','对敌军全体谋略攻击(130%)并使其陷入混乱1回合，50%几率延长至2回合',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(130,1.22,hero.attrs['int'])
        rnd=2 if get_random_bool(50) else 1
        for t in hero.get_target(sk.limit,3):
            t.be_hurt(hero,{'type':2,'rate':rate},sk)
            if not t.is_confusion():
                t.state['confusion']={'rounds':rnd,'from':{'hero':hero,'skill':sk}}
        return True


# --- 200769 草木皆兵
class Skill200769(Skill):
    def __init__(self):
        super().__init__(200769,'草木皆兵','使敌军全体陷入恐慌(150%)并怯战1回合',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r=calc_skill_addition_rate(150,1.4,hero.attrs['int'])
        for t in hero.get_target(sk.limit,3):
            t.be_hurt(hero,{'type':3,'rate':r},sk)
            if not t.is_attack_limit():
                t.state['attackLimit']={'rounds':1,'from':{'hero':hero,'skill':sk}}
        return True


# --- 200770 养精蓄锐
class Skill200770(Skill):
    def __init__(self):
        super().__init__(200770,'养精蓄锐','前3回合每回合恢复(120%)，第4回合起攻击谋略+60',1,'--',True,2)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        rv=calc_skill_addition_rate(120,1.1,hero.attrs['def'])
        v=calc_skill_addition_rate(60,0.5,hero.attrs['def'])
        def sub():
            rnd=hero.manager.round
            if rnd<=3:
                hero.recover(calc_recover(hero,rv,1.1,2),hero,sk.name)
            else:
                hero.attrs['atk']=keep_two_decimal(hero.attrs['atk']+v)
                hero.attrs['int']=keep_two_decimal(hero.attrs['int']+v)
                hero.clear_hook('行动时',f'{sk.id}_act')
        hero.add_hook('行动时',f'{sk.id}_act',sub,sk,hero)


# --- 200771 乘胜追击
class Skill200771(Skill):
    def __init__(self):
        super().__init__(200771,'乘胜追击','每消灭1名敌军，攻击伤害+25%，最多3层',4,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self; stk=[0]
        v=calc_skill_addition_rate(25,0.2,hero.attrs['atk'])
        def on_kill(t,di,s2,dmg):
            if t.arms<=0 and stk[0]<3:
                stk[0]+=1
                hero.add_state('attackDamageAdd',v,-1,sk,hero,True)
        hero.add_hook('造成伤害后',f'{sk.id}_kill',on_kill,sk,hero)


# --- 200772 铁索连舟
class Skill200772(Skill):
    def __init__(self):
        super().__init__(200772,'铁索连舟','使友军全体防御+50，每受到1次伤害后为相邻友军分担15%伤害，持续2回合',2,120,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        def_v=calc_skill_addition_rate(50,0.42,hero.attrs['int'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.attrs['def']=keep_two_decimal(e.attrs['def']+def_v)
            def mh(e=e):
                cnt=[2]
                def oh(a,di,s):
                    dmg=di.get('damage',0)
                    share=int(dmg*0.15)
                    for ally in [x for x in team if x is not e and x.arms>0]:
                        ally.be_hurt_by_num(a,di,sk,share//max(len(team)-1,1),lambda:None)
                    if hero.manager.round>=cnt[0]+hero.manager.round-1:
                        e.clear_hook('受伤时',f'{sk.id}_share_{id(e)}')
                e.add_hook('受伤时',f'{sk.id}_share_{id(e)}',oh,sk,hero)
            mh()
        return True


# --- 200773 背水一战
class Skill200773(Skill):
    def __init__(self):
        super().__init__(200773,'背水一战','兵力低于30%时，攻击谋略+100，免疫控制效果，每局1次',4,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self; fired=[False]
        def on_hurt(a,di,s):
            if fired[0]: return
            max_arms=getattr(hero,'max_arms',hero.arms)
            if hero.arms/max(max_arms,1)<0.3:
                fired[0]=True
                hero.attrs['atk']=keep_two_decimal(hero.attrs['atk']+100)
                hero.attrs['int']=keep_two_decimal(hero.attrs['int']+100)
                hero.state['immuneCtrl']={'rounds':-1,'from':{'hero':hero,'skill':sk}}
        hero.add_hook('受伤时',f'{sk.id}_trigger',on_hurt,sk,hero)


# --- 200774 风卷残云
class Skill200774(Skill):
    def __init__(self):
        super().__init__(200774,'风卷残云','对敌军群体攻击(175%)，击杀后对剩余敌军追加攻击(100%)',2,40,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(175,1.65,hero.attrs['atk'])
        extra=calc_skill_addition_rate(100,0.92,hero.attrs['atk'])
        killed=False
        for t in hero.get_target(sk.limit,3):
            t.be_hurt(hero,{'type':1,'rate':rate},sk)
            if t.arms<=0: killed=True
        if killed:
            for t in hero.get_target(sk.limit,3):
                t.be_hurt(hero,{'type':1,'rate':extra},sk)
        return True


# --- 200775 破釜沉舟
class Skill200775(Skill):
    def __init__(self):
        super().__init__(200775,'破釜沉舟','攻击+80，自身无法被恢复，但每回合对敌单体攻击(160%)免除所受控制',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        atk_v=calc_skill_addition_rate(80,0.68,hero.attrs['atk'])
        rate=calc_skill_addition_rate(160,1.5,hero.attrs['atk'])
        hero.attrs['atk']=keep_two_decimal(hero.attrs['atk']+atk_v)
        hero.state['noRecover']={'rounds':-1,'from':{'hero':hero,'skill':sk}}
        hero.state['immuneCtrl']={'rounds':-1,'from':{'hero':hero,'skill':sk}}
        def sub():
            tgs=hero.get_target(sk.limit,1)
            if tgs: tgs[0].be_hurt(hero,{'type':1,'rate':rate},sk)
        hero.add_hook('行动时',f'{sk.id}_act',sub,sk,hero)


# --- 200776 援军及时
class Skill200776(Skill):
    def __init__(self):
        super().__init__(200776,'援军及时','友军兵力低于40%时为其恢复(200%)，每局每名友军最多1次',1,'--',True,2)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        rv=calc_skill_addition_rate(200,1.9,hero.attrs['int'])
        team=hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        used={id(e):False for e in team}
        for e in [x for x in team if x is not hero]:
            def ms(e=e):
                def oh(a,di,s):
                    if used[id(e)]: return
                    max_arms=getattr(e,'max_arms',e.arms)
                    if e.arms/max(max_arms,1)<0.4:
                        used[id(e)]=True
                        e.recover(calc_recover(hero,rv,1.9,2),hero,sk.name)
                e.add_hook('受伤时',f'{sk.id}_{id(e)}',oh,sk,hero)
            ms()


# --- 200777 长坂英威
class Skill200777(Skill):
    def __init__(self):
        super().__init__(200777,'长坂英威','对敌军群体发动3次攻击(65%)，每次有40%几率使目标混乱1回合',2,40,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate=calc_skill_addition_rate(65,0.6,hero.attrs['atk'])
        for _ in range(3):
            for t in hero.get_target(sk.limit,2):
                t.be_hurt(hero,{'type':1,'rate':rate},sk)
                if get_random_bool(40) and not t.is_confusion():
                    t.state['confusion']={'rounds':1,'from':{'hero':hero,'skill':sk}}
        return True


# --- 200778 虚张声势
class Skill200778(Skill):
    def __init__(self):
        super().__init__(200778,'虚张声势','使敌军全体攻击谋略降低45，持续2回合',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        sub_v=calc_skill_addition_rate(45,0.38,hero.attrs['int'])
        for t in hero.get_target(sk.limit,3):
            t.attrs['atk']=keep_two_decimal(max(0,t.attrs['atk']-sub_v))
            t.attrs['int']=keep_two_decimal(max(0,t.attrs['int']-sub_v))
        return True


# ── SKILLS_A 注册字典（与 SKILLS_S 格式一致）
SKILLS_A = {
    200002: Skill200002(), 200005: Skill200005(), 200008: Skill200008(),
    200011: Skill200011(), 200019: Skill200019(), 200020: Skill200020(),
    200022: Skill200022(), 200028: Skill200028(), 200029: Skill200029(),
    200031: Skill200031(), 200034: Skill200034(), 200059: Skill200059(),
    200065: Skill200065(), 200072: Skill200072(), 200074: Skill200074(),
    200076: Skill200076(), 200083: Skill200083(), 200088: Skill200088(),
    200090: Skill200090(), 200092: Skill200092(), 200119: Skill200119(),
    200126: Skill200126(), 200127: Skill200127(), 200130: Skill200130(),
    200208: Skill200208(), 200233: Skill200233(), 200241: Skill200241(),
    200242: Skill200242(), 200243: Skill200243(), 200247: Skill200247(),
    200248: Skill200248(), 200249: Skill200249(), 200253: Skill200253(),
    200256: Skill200256(), 200258: Skill200258(), 200259: Skill200259(),
    200261: Skill200261(), 200265: Skill200265(), 200267: Skill200267(),
    200607: Skill200607(), 200610: Skill200610(), 200623: Skill200623(),
    200624: Skill200624(), 200633: Skill200633(), 200643: Skill200643(),
    200644: Skill200644(), 200645: Skill200645(), 200652: Skill200652(),
    200658: Skill200658(), 200659: Skill200659(), 200673: Skill200673(),
    200674: Skill200674(), 200676: Skill200676(), 200677: Skill200677(),
    200680: Skill200680(), 200683: Skill200683(), 200684: Skill200684(),
    200685: Skill200685(), 200689: Skill200689(), 200691: Skill200691(),
    200704: Skill200704(), 200706: Skill200706(), 200708: Skill200708(),
    200712: Skill200712(), 200714: Skill200714(), 200715: Skill200715(),
    200716: Skill200716(), 200717: Skill200717(), 200718: Skill200718(),
    200720: Skill200720(), 200721: Skill200721(), 200722: Skill200722(),
    200723: Skill200723(), 200724: Skill200724(), 200725: Skill200725(),
    200726: Skill200726(), 200727: Skill200727(), 200728: Skill200728(),
    200729: Skill200729(), 200730: Skill200730(), 200731: Skill200731(),
    200732: Skill200732(), 200733: Skill200733(), 200734: Skill200734(),
    200735: Skill200735(), 200736: Skill200736(), 200737: Skill200737(),
    200738: Skill200738(), 200739: Skill200739(), 200740: Skill200740(),
    200741: Skill200741(), 200742: Skill200742(), 200743: Skill200743(),
    200744: Skill200744(), 200745: Skill200745(), 200746: Skill200746(),
    200747: Skill200747(), 200748: Skill200748(), 200749: Skill200749(),
    200750: Skill200750(), 200751: Skill200751(), 200752: Skill200752(),
    200753: Skill200753(), 200754: Skill200754(), 200755: Skill200755(),
    200756: Skill200756(), 200757: Skill200757(), 200758: Skill200758(),
    200759: Skill200759(), 200760: Skill200760(), 200761: Skill200761(),
    200762: Skill200762(), 200763: Skill200763(), 200764: Skill200764(),
    200765: Skill200765(), 200766: Skill200766(), 200767: Skill200767(),
    200768: Skill200768(), 200769: Skill200769(), 200770: Skill200770(),
    200771: Skill200771(), 200772: Skill200772(), 200773: Skill200773(),
    200774: Skill200774(), 200775: Skill200775(), 200776: Skill200776(),
    200777: Skill200777(), 200778: Skill200778(),
}


# ═══════════════════════════════════════════════════════
# 以下为补充实现的23个缺失战法
# ═══════════════════════════════════════════════════════

# ─── 200184 百战老兵
class Skill200184(Skill):
    def __init__(self):
        super().__init__(200184,'百战老兵','使自身攻击、防御、谋略、速度全属性+32',1,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        v = calc_skill_addition_rate(32, 0.25, hero.attrs['int'])
        for k in ('atk','def','int','spd'):
            hero.attrs[k] = keep_two_decimal(hero.attrs[k] + v)


# ─── 200210 远交近攻
class Skill200210(Skill):
    def __init__(self):
        super().__init__(200210,'远交近攻','使自身攻击+20防御+20攻击距离+1，前3回合友军全体获得相同增益',1,'--',True,4)
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
                    if e is not hero:
                        e.attrs['atk'] = keep_two_decimal(e.attrs['atk'] + v)
                        e.attrs['def'] = keep_two_decimal(e.attrs['def'] + v)
            else:
                hero.clear_hook('回合开始时', f'{sk.id}_buff')
        hero.add_hook('回合开始时', f'{sk.id}_buff', sub, sk, hero)


# ─── 200215 矢志
class Skill200215(Skill):
    def __init__(self):
        super().__init__(200215,'矢志','战斗开始前2回合，受到伤害时70%几率进入免疫状态，抵消该次伤害',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        def on_hurt(a, di, s):
            if hero.manager.round <= 2 and get_random_bool(70):
                di['immune'] = True
        hero.add_hook('受伤时', f'{sk.id}_immune', on_hurt, sk, hero)


# ─── 200646 纵横疾驰
class Skill200646(Skill):
    def __init__(self):
        super().__init__(200646,'纵横疾驰','3回合起普通攻击后，目标40%几率陷入怯战1回合',4,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        def on_dmg(t, di, s2, dmg):
            if hero.manager.round >= 3 and get_random_bool(40):
                if not t.state.get('attackLimit', {}).get('rounds', 0) > 0:
                    t.state['attackLimit'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        hero.add_hook('造成伤害后', f'{sk.id}_act', on_dmg, sk, hero)


# ─── 200829 铁骑冲阵
class Skill200829(Skill):
    def __init__(self):
        super().__init__(200829,'铁骑冲阵','对敌军单体攻击(200%)，同时对单体的表里两侧敌军各进行一次追击攻击(159%)',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r1 = calc_skill_addition_rate(200, 1.9, hero.attrs['atk'])
        r2 = calc_skill_addition_rate(159, 1.5, hero.attrs['atk'])
        targets = hero.get_target(sk.limit, 1)
        if not targets: return True
        main_t = targets[0]
        main_t.be_hurt(hero, {'type': 1, 'rate': r1}, sk)
        side_targets = hero.get_target(sk.limit, 2)
        for t in side_targets:
            if t is not main_t:
                t.be_hurt(hero, {'type': 1, 'rate': r2}, sk)
        return True


# ─── 200833 鸣镝决胜
class Skill200833(Skill):
    def __init__(self):
        super().__init__(200833,'鸣镝决胜','1回合准备，对敌军群体2目标攻击(330%)；对敌军全体3目标攻击(225%)',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r1 = calc_skill_addition_rate(330, 3.1, hero.attrs['atk'])
        r2 = calc_skill_addition_rate(225, 2.1, hero.attrs['atk'])
        def subskill():
            hit2 = hero.get_target(sk.limit, 2)
            for t in hit2:
                t.be_hurt(hero, {'type': 1, 'rate': r1}, sk)
            hit3 = hero.get_target(sk.limit, 3)
            for t in hit3:
                t.be_hurt(hero, {'type': 1, 'rate': r2}, sk)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200839 追击横扫
class Skill200839(Skill):
    def __init__(self):
        super().__init__(200839,'追击横扫','普通攻击后发动2次追击，攻击伤害80%-140%，每次攻击目标及伤害独立判定',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        def on_dmg(t, di, s2, dmg):
            if di.get('type') != 1: return
            for _ in range(2):
                rate = calc_skill_addition_rate(get_random_int(80, 140), 0.9, hero.attrs['atk'])
                tgs = hero.get_target(sk.limit, 1)
                if tgs:
                    tgs[0].be_hurt(hero, {'type': 1, 'rate': rate}, sk)
        hero.add_hook('造成伤害后', f'{sk.id}_chase', on_dmg, sk, hero)


# ─── 200850 千钧一发
class Skill200850(Skill):
    def __init__(self):
        super().__init__(200850,'千钧一发','1回合准备，进入锁定状态，每回合失败受到伤害(130%)；触发被动攻击时伤害降低12%；持续2回合',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r_dot = calc_skill_addition_rate(130, 1.2, hero.attrs['int'])
        r_reduce = calc_skill_addition_rate(12, 0.1, hero.attrs['int'])
        def subskill():
            rl = [2]
            def on_rnd():
                if rl[0] > 0:
                    rl[0] -= 1
                    hero.be_hurt(hero, {'type': 4, 'rate': r_dot}, sk)
                    if rl[0] <= 0:
                        hero.clear_hook('回合开始时', f'{sk.id}_dot')
            hero.add_hook('回合开始时', f'{sk.id}_dot', on_rnd, sk, hero)
            def on_passive(a, di, s):
                if di.get('type') == 1:
                    di['rate'] = keep_two_decimal(di.get('rate', 100) * (1 - r_reduce/100))
            hero.add_hook('受伤时', f'{sk.id}_passive', on_passive, sk, hero)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200853 胭脂计
class Skill200853(Skill):
    def __init__(self):
        super().__init__(200853,'胭脂计','我方3武将为女将时大营受到伤害+14%；每回合行动前使我方最弱友军受谋略攻击(60%)；前4回合受到伤害时进入免疫状态',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        rate = calc_skill_addition_rate(60, 0.55, hero.attrs['int'])
        imm_used = [False]
        def on_hurt(a, di, s):
            if hero.manager.round <= 4 and not imm_used[0]:
                imm_used[0] = True
                di['immune'] = True
        hero.add_hook('受伤时', f'{sk.id}_imm', on_hurt, sk, hero)
        def sub():
            alive = [e for e in team if e.arms > 0 and e is not hero]
            if alive:
                weakest = min(alive, key=lambda e: e.arms)
                weakest.be_hurt(hero, {'type': 2, 'rate': rate}, sk)
        hero.add_hook('行动前', f'{sk.id}_act', sub, sk, hero)


# ─── 200883 指麾山岳
class Skill200883(Skill):
    def __init__(self):
        super().__init__(200883,'指麾山岳','战斗开始，我军全体每回合行动时30%使其受到的攻击和追击攻击概率降低60%，持续1回合，效果随回合递增10%',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for member in team:
            def mh(e=member):
                base_prob = [30]
                def on_act():
                    if get_random_bool(min(base_prob[0], 100)):
                        v = calc_skill_addition_rate(60, 0.55, hero.attrs['int'])
                        e.add_state('beAttackDamageSub', v, 1, sk, hero)
                    base_prob[0] += 10
                e.add_hook('行动时', f'{sk.id}_act_{id(e)}', on_act, sk, hero)
            mh()


# ─── 200917 出处辞命
class Skill200917(Skill):
    def __init__(self):
        super().__init__(200917,'出处辞命','1回合准备，对敌军群体发动追击攻击(132%)；使敌军1-2目标受到额外谋略伤害(165%)',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r1 = calc_skill_addition_rate(132, 1.2, hero.attrs['atk'])
        r2 = calc_skill_addition_rate(165, 1.5, hero.attrs['int'])
        def subskill():
            for t in hero.get_target(sk.limit, 2):
                t.be_hurt(hero, {'type': 1, 'rate': r1}, sk)
            n = get_random_int(1, 2)
            for t in hero.get_target(sk.limit, n):
                t.be_hurt(hero, {'type': 2, 'rate': r2}, sk)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200929 苦肉妙计
class Skill200929(Skill):
    def __init__(self):
        super().__init__(200929,'苦肉妙计','使友军全体普通攻击伤害+36%持续2回合；使2名友军进入无双状态伤害+55%',2,120,True,4)
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
        for e in alive[:2]:
            e.add_state('activeDamageAdd', v2, 2, sk, hero)
            e.add_state('attackDamageAdd', v2, 2, sk, hero)
            e.add_state('inteDamageAdd', v2, 2, sk, hero)
        return True


# ─── 200935 直射穿甲
class Skill200935(Skill):
    def __init__(self):
        super().__init__(200935,'直射穿甲','每回合首次造成伤害使目标受到伤害+10%；首次受到伤害使友军受同类伤害+10%，最多6次，贯穿至战斗结束',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        stk_atk = [0]
        stk_def = [0]
        v = calc_skill_addition_rate(10, 0.08, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        def on_dmg(t, di, s2, dmg):
            if stk_atk[0] < 6:
                stk_atk[0] += 1
                t.add_state('beAttackDamageAdd', v, -1, sk, hero, True)
        def on_hurt(a, di, s):
            if stk_def[0] < 6:
                stk_def[0] += 1
                for e in team:
                    e.add_state('beAttackDamageAdd', v, -1, sk, hero, True)
        hero.add_hook('造成伤害后', f'{sk.id}_atk', on_dmg, sk, hero)
        hero.add_hook('受伤时', f'{sk.id}_def', on_hurt, sk, hero)


# ─── 200941 渐退布阵
class Skill200941(Skill):
    def __init__(self):
        super().__init__(200941,'渐退布阵','战斗中敌军兵力首次低于初始90%/70%/50%/30%时，使下次行动时我军战力+50%，效果可对抗成功后造成伤害',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        enemy_team = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
        thresholds = [90, 70, 50, 30]
        triggered = set()
        def check():
            for e in enemy_team:
                if e.max_arms <= 0: continue
                pct = int(e.arms / e.max_arms * 100)
                for th in thresholds:
                    key = (id(e), th)
                    if pct < th and key not in triggered:
                        triggered.add(key)
                        v = calc_skill_addition_rate(50, 0.45, hero.attrs['int'])
                        hero.add_state('activeDamageAdd', v, 1, sk, hero)
                        hero.add_state('attackDamageAdd', v, 1, sk, hero)
                        hero.add_state('inteDamageAdd', v, 1, sk, hero)
        hero.add_hook('回合开始时', f'{sk.id}_check', check, sk, hero)


# ─── 200949 铁骑鼓动
class Skill200949(Skill):
    def __init__(self):
        super().__init__(200949,'铁骑鼓动','战斗中，敌方每次尝试发动追击战法时，使其发动一次普通攻击伤害削减，并对该方向追击战法造成的伤害减少40%，效果最多叠加3次',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        enemy_team = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
        stk = [0]
        v = calc_skill_addition_rate(40, 0.35, hero.attrs['int'])
        for e in enemy_team:
            def mh(en=e):
                def on_dmg(t, di, s2, dmg):
                    if stk[0] < 3:
                        stk[0] += 1
                        en.add_state('attackDamageSub', v, 1, sk, hero)
                en.add_hook('造成伤害后', f'{sk.id}_enemy_{id(en)}', on_dmg, sk, hero)
            mh()


# ─── 200953 疾驰冲阵
class Skill200953(Skill):
    def __init__(self):
        super().__init__(200953,'疾驰冲阵','1回合准备，对敌军群体发动追击攻击(250%)，使敌军兵力速度降低25，持续2回合',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r = calc_skill_addition_rate(250, 2.4, hero.attrs['atk'])
        spd_sub = calc_skill_addition_rate(25, 0.2, hero.attrs['int'])
        def subskill():
            for t in hero.get_target(sk.limit, 2):
                t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
                t.attrs['spd'] = keep_two_decimal(max(0, t.attrs['spd'] - spd_sub))
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200956 鏖战虎步
class Skill200956(Skill):
    def __init__(self):
        super().__init__(200956,'鏖战虎步','每回合行动时90%使自身进入无双直攻状态，普通攻击同时对旁侧敌军造成伤害(70%)',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        r = calc_skill_addition_rate(70, 0.65, hero.attrs['atk'])
        def on_act():
            if get_random_bool(90):
                hero.state['activeNoLimit'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
                hero.state['attackNoLimit'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
                def on_dmg(t, di, s2, dmg):
                    side = hero.get_target(sk.limit, 2)
                    for st in side:
                        if st is not t:
                            st.be_hurt(hero, {'type': 1, 'rate': r}, sk)
                    hero.clear_hook('造成伤害后', f'{sk.id}_side')
                hero.add_hook('造成伤害后', f'{sk.id}_side', on_dmg, sk, hero)
        hero.add_hook('行动时', f'{sk.id}_act', on_act, sk, hero)


# ─── 200959 督战变阵
class Skill200959(Skill):
    def __init__(self):
        super().__init__(200959,'督战变阵','战斗中，使己军每造成一次谋略伤害，攻击伤害提升5%，效果最多叠加5次',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        stk = [0]
        v = calc_skill_addition_rate(5, 0.04, hero.attrs['int'])
        for e in team:
            def mh(en=e):
                def on_dmg(t, di, s2, dmg):
                    if di.get('type') == 2 and stk[0] < 5:
                        stk[0] += 1
                        for m in team:
                            m.attrs['atk'] = keep_two_decimal(m.attrs['atk'] + v)
                en.add_hook('造成伤害后', f'{sk.id}_{id(en)}', on_dmg, sk, hero)
            mh()


# ─── 200964 横扫千军
class Skill200964(Skill):
    def __init__(self):
        super().__init__(200964,'横扫千军','战斗中，我方出战3武将全营同时战力+1；对方出战全体武将成功对抗后造成伤害增加10%，对方出战全体武将普通攻击伤害增加40%',1,'--',True,6)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        enemy_team = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
        v_team = calc_skill_addition_rate(1, 0.01, hero.attrs['int'])
        v_atk = calc_skill_addition_rate(10, 0.08, hero.attrs['int'])
        v_normal = calc_skill_addition_rate(40, 0.35, hero.attrs['int'])
        for e in team:
            e.attrs['atk'] = keep_two_decimal(e.attrs['atk'] + v_team)
            e.attrs['int'] = keep_two_decimal(e.attrs['int'] + v_team)
        for e in enemy_team:
            e.add_state('activeDamageAdd', v_atk, -1, sk, hero)
            e.add_state('attackDamageAdd', v_normal, -1, sk, hero)


# ─── 200967 号令同心
class Skill200967(Skill):
    def __init__(self):
        super().__init__(200967,'号令同心','战斗中，我方每次发动主动战法/追击战法时，我军前排在下次行动前受到伤害减少40%，效果最多触发1次',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        front = team[:1] if team else []
        v = calc_skill_addition_rate(40, 0.35, hero.attrs['int'])
        triggered = [False]
        for e in team:
            def mh(en=e):
                def on_dmg(t, di, s2, dmg):
                    if not triggered[0]:
                        triggered[0] = True
                        for f in front:
                            f.add_state('beAttackDamageSub', v, 1, sk, hero)
                            f.add_state('beInteDamageSub', v, 1, sk, hero)
                en.add_hook('造成伤害后', f'{sk.id}_{id(en)}', on_dmg, sk, hero)
            mh()


# ─── 200981 胜不骄将
class Skill200981(Skill):
    def __init__(self):
        super().__init__(200981,'胜不骄将','每回合首次行动时，骑士可以使造成的普通追击战法伤害+40%；1回合后骑士获得一次治疗，使受到的伤害时恢复一定兵力(75%)',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        v_dmg = calc_skill_addition_rate(40, 0.35, hero.attrs['atk'])
        v_rec = calc_skill_addition_rate(75, 0.7, hero.attrs['int'])
        fired = [False]
        def on_act():
            fired[0] = False
            def on_dmg(t, di, s2, dmg):
                if not fired[0]:
                    fired[0] = True
                    di['rate'] = keep_two_decimal(di.get('rate', 100) + v_dmg)
            hero.add_hook('造成伤害后', f'{sk.id}_dmg', on_dmg, sk, hero)
        hero.add_hook('行动时', f'{sk.id}_act', on_act, sk, hero)
        def on_hurt(a, di, s):
            hero.recover(calc_recover(hero, v_rec, 0.7, 2), hero, sk.name)
        hero.add_hook('受伤时', f'{sk.id}_rec', on_hurt, sk, hero)


# ─── 200982 驰骋突袭
class Skill200982(Skill):
    def __init__(self):
        super().__init__(200982,'驰骋突袭','对敌军群体发动2次追击攻击(108%)，目标如是骑兵使其进入只攻状态伤害(98%)，持续1回合',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r1 = calc_skill_addition_rate(108, 1.0, hero.attrs['atk'])
        r2 = calc_skill_addition_rate(98, 0.9, hero.attrs['atk'])
        targets = hero.get_target(sk.limit, 2)
        for t in targets:
            for _ in range(2):
                t.be_hurt(hero, {'type': 1, 'rate': r1}, sk)
            if t.arms > 0:
                t.state['attackLimit'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
                t.be_hurt(hero, {'type': 1, 'rate': r2}, sk)
        return True


# ─── 201008 玉带束兵
class Skill201008(Skill):
    def __init__(self):
        super().__init__(201008,'玉带束兵','战斗开始3回合，给予我军全体每回合首次受到伤害50%的几率对伤害来源发动一次追击攻击(136%)',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        r = calc_skill_addition_rate(136, 1.3, hero.attrs['atk'])
        for member in team:
            def mh(e=member):
                def on_hurt(a, di, s):
                    if hero.manager.round <= 3 and get_random_bool(50):
                        a.be_hurt(e, {'type': 1, 'rate': r}, sk)
                e.add_hook('受伤时', f'{sk.id}_counter_{id(e)}', on_hurt, sk, hero)
            mh()


# ═══ 补充注册23个新战法到 SKILLS_A ═══
SKILLS_A.update({
    200184: Skill200184(), 200210: Skill200210(), 200215: Skill200215(),
    200646: Skill200646(), 200829: Skill200829(), 200833: Skill200833(),
    200839: Skill200839(), 200850: Skill200850(), 200853: Skill200853(),
    200883: Skill200883(), 200917: Skill200917(), 200929: Skill200929(),
    200935: Skill200935(), 200941: Skill200941(), 200949: Skill200949(),
    200953: Skill200953(), 200956: Skill200956(), 200959: Skill200959(),
    200964: Skill200964(), 200967: Skill200967(), 200981: Skill200981(),
    200982: Skill200982(), 201008: Skill201008(),
})


# ─── 200930 奇兵拒北
class Skill200930(Skill):
    def __init__(self):
        super().__init__(200930,'奇兵拒北','每回合行动时，30%几率对敌军大营和中军各发动一次攻击(180%)，同时速度最高的友军也对大营和中军各发动攻击(120%-180%)；每回合未触发时发动率+5%，触发后效果消失',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        rate_bonus = [0]
        def on_act():
            base = 30
            current = min(100, hero.get_real_skill_rate(base) + rate_bonus[0])
            hero.manager.log(hero, f'的【{sk.name}】当前效果发动率({current}%)')
            if get_random_bool(current):
                hero.manager.log(hero, f'发动【{sk.name}】效果')
                r1 = calc_skill_addition_rate(180, 1.7, hero.attrs['atk'])
                enemy_team = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
                my_team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
                # 自身攻击敌军大营和中军
                for e in enemy_team:
                    if e.pos_name in ('大营', '中军') and e.arms > 0:
                        e.be_hurt(hero, {'type': 1, 'rate': r1}, sk)
                # 速度最高的友军攻击
                allies = [e for e in my_team if e is not hero and e.arms > 0]
                if allies:
                    spd_hero = max(allies, key=lambda e: e.attrs['spd'])
                    r2 = calc_skill_addition_rate(get_random_int(120, 180), 1.4, spd_hero.attrs['atk'])
                    for e in enemy_team:
                        if e.pos_name in ('大营', '中军') and e.arms > 0:
                            e.be_hurt(spd_hero, {'type': 1, 'rate': r2}, sk)
                # 触发后重置加成
                rate_bonus[0] = 0
            else:
                hero.manager.log(hero, f'的【{sk.name}】未触发，发动率+5%')
                rate_bonus[0] += 5
        hero.add_hook('行动时', f'{sk.id}_act', on_act, sk, hero)


# 注册200930
SKILLS_A[200930] = Skill200930()


# ═══ 第2批缺失战法实现 ═══

# ─── 200004 胡笳离愁
class Skill200004(Skill):
    def __init__(self):
        super().__init__(200004,'胡笳离愁','恢复友军全体多个目标兵力(157%)，使其进入免疫状态；每回合速度恢复，兵力(206%)，持续1回合',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r1 = calc_skill_addition_rate(157, 1.4, hero.attrs['int'])
        r2 = calc_skill_addition_rate(206, 1.9, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for t in team:
            if t.arms > 0:
                hero.recover(calc_recover(hero, r1, 1.4, 2), hero, sk.name)
                t.state['activeNoLimit'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
                def mh(tgt=t):
                    rl = [1]
                    def on_rnd():
                        rl[0] -= 1
                        tgt.recover(calc_recover(hero, r2, 1.9, 2), hero, sk.name)
                        if rl[0] <= 0:
                            tgt.clear_hook('回合开始时', f'{sk.id}_rec_{id(tgt)}')
                    tgt.add_hook('回合开始时', f'{sk.id}_rec_{id(tgt)}', on_rnd, sk, hero)
                mh()
        return True


# ─── 200006 四世三公
class Skill200006(Skill):
    def __init__(self):
        super().__init__(200006,'四世三公','使友军全体直属5回合内的敌军各发动一次攻击(150%)，每个目标独立判定',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        r = calc_skill_addition_rate(150, 1.4, hero.attrs['atk'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for member in team:
            def mh(e=member):
                def on_act():
                    if hero.manager.round <= 5:
                        tgs = e.get_target(e.limit, 1)
                        for t in tgs:
                            t.be_hurt(e, {'type': 1, 'rate': r}, sk)
                e.add_hook('行动时', f'{sk.id}_{id(e)}', on_act, sk, hero)
            mh()


# ─── 200007 将倾之柱
class Skill200007(Skill):
    def __init__(self):
        super().__init__(200007,'将倾之柱','对敌军群体发动追击攻击(85%)，使受到的谋略攻击伤害降低49%，持续2回合',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r = calc_skill_addition_rate(85, 0.8, hero.attrs['atk'])
        v = calc_skill_addition_rate(49, 0.45, hero.attrs['int'])
        for t in hero.get_target(sk.limit, 2):
            t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
            t.add_state('beInteDamageSub', v, 2, sk, hero)
        return True


# ─── 200009 逆谋
class Skill200009(Skill):
    def __init__(self):
        super().__init__(200009,'逆谋','使受到的谋略攻击伤害降低20%；对抗成功伤害时能够反噬恢复相当于伤害值50%的兵力',4,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        v = calc_skill_addition_rate(20, 0.18, hero.attrs['int'])
        hero.add_state('beInteDamageSub', v, -1, sk, hero)
        def on_hurt(attacker, di, s):
            if di.get('type') == 2:
                heal = calc_recover(hero, 50, 0.45, 2)
                hero.recover(heal, hero, sk.name)
        hero.add_hook('受伤时', f'{sk.id}_counter', on_hurt, sk, hero)


# ─── 200017 诸葛锦囊
class Skill200017(Skill):
    def __init__(self):
        super().__init__(200017,'诸葛锦囊','使友军全体受到谋略攻击时伤害降低35%；有主动战法和追击战法时伤害降低14%，持续2回合',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v1 = calc_skill_addition_rate(35, 0.32, hero.attrs['int'])
        v2 = calc_skill_addition_rate(14, 0.12, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('beInteDamageSub', v1, -1, sk, hero)
            e.add_state('beActiveDamageSub', v2, -1, sk, hero)


# ─── 200025 魏武之泽
class Skill200025(Skill):
    def __init__(self):
        super().__init__(200025,'魏武之泽','使友军全体造成的普通攻击和追击战法伤害降低15%；100%几率使每回合可以进行额外普通攻击，持续2回合',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(15, 0.13, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('attackDamageSub', v, -1, sk, hero)
            e.state['doubleAttack'] = {'rounds': 2, 'from': {'hero': hero, 'skill': sk}}


# ─── 200026 千里单骑
class Skill200026(Skill):
    def __init__(self):
        super().__init__(200026,'千里单骑','普通攻击后，对攻击目标再次发动猛攻(180%)，并使其恢复一定兵力',3,35,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r = calc_skill_addition_rate(180, 1.7, hero.attrs['atk'])
        if target and target.arms > 0:
            target.be_hurt(hero, {'type': 1, 'rate': r}, sk)
        return True


# ─── 200032 复誓业火
class Skill200032(Skill):
    def __init__(self):
        super().__init__(200032,'复誓业火','对敌军群体发动一次火攻(114%)，使其进入燃烧状态，每回合损失兵力(114%)，持续1回合；受谋略攻击时伤害降低16%',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r = calc_skill_addition_rate(114, 1.05, hero.attrs['int'])
        v = calc_skill_addition_rate(16, 0.14, hero.attrs['int'])
        for t in hero.get_target(sk.limit, 2):
            t.be_hurt(hero, {'type': 4, 'rate': r}, sk)
            t.add_state('beInteDamageSub', v, 1, sk, hero)
        return True


# ─── 200033 遗志
class Skill200033(Skill):
    def __init__(self):
        super().__init__(200033,'遗志','战斗开始前3回合，使友军前沿步兵弓兵获得反震，受到普通攻击可以进行反击(85%)，无法恢复伤兵，贯穿至战斗结束',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        r = calc_skill_addition_rate(85, 0.8, hero.attrs['atk'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            def mh(tgt=e):
                def on_hurt(attacker, di, s):
                    if di.get('type') == 1 and hero.manager.round <= 3:
                        attacker.be_hurt(tgt, {'type': 1, 'rate': r}, sk)
                tgt.add_hook('受伤时', f'{sk.id}_{id(tgt)}', on_hurt, sk, hero)
            mh()


# ─── 200035 枭姬
class Skill200035(Skill):
    def __init__(self):
        super().__init__(200035,'枭姬','1回合准备，对敌军全体发动攻击(160%)；对敌军群体1-2目标速度降低发动一次攻击(140%)',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r1 = calc_skill_addition_rate(160, 1.5, hero.attrs['atk'])
        r2 = calc_skill_addition_rate(140, 1.3, hero.attrs['atk'])
        def subskill():
            for t in hero.get_target(sk.limit, 3):
                t.be_hurt(hero, {'type': 1, 'rate': r1}, sk)
            n = get_random_int(1, 2)
            for t in hero.get_target(sk.limit, n):
                t.be_hurt(hero, {'type': 1, 'rate': r2}, sk)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200037 母仪浮梦
class Skill200037(Skill):
    def __init__(self):
        super().__init__(200037,'母仪浮梦','战斗开始使友军全体受到首次攻击时进入护盾状态；使友军全体前4回合进行攻击和追击时50%几率该次攻击伤害提高30%',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(30, 0.27, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        shielded = set()
        for e in team:
            def mh(tgt=e):
                def on_hurt(attacker, di, s):
                    if id(tgt) not in shielded:
                        shielded.add(id(tgt))
                        di['immune'] = True
                tgt.add_hook('受伤时', f'{sk.id}_shield_{id(tgt)}', on_hurt, sk, hero)
                def on_dmg(t, di, s2, dmg):
                    if hero.manager.round <= 4 and get_random_bool(50):
                        di['rate'] = keep_two_decimal(di.get('rate', 100) * (1 + v/100))
                tgt.add_hook('造成伤害后', f'{sk.id}_dmg_{id(tgt)}', on_dmg, sk, hero)
            mh()


# ─── 200080 破凰
class Skill200080(Skill):
    def __init__(self):
        super().__init__(200080,'破凰','清除敌军全体所有化解效果，对敌军单体发动谋略攻击(155%)；每受到伤害时额外反击一次谋略伤害(130%)',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r1 = calc_skill_addition_rate(155, 1.4, hero.attrs['int'])
        r2 = calc_skill_addition_rate(130, 1.2, hero.attrs['int'])
        for t in hero.get_target(sk.limit, 1):
            t.be_hurt(hero, {'type': 2, 'rate': r1}, sk)
        def on_hurt(attacker, di, s):
            attacker.be_hurt(hero, {'type': 2, 'rate': r2}, sk)
        hero.add_hook('受伤时', f'{sk.id}_counter', on_hurt, sk, hero)
        return True


# ─── 200094 将门虎女
class Skill200094(Skill):
    def __init__(self):
        super().__init__(200094,'将门虎女','对敌军群体发动一次谋略伤害(190%)，使其无法恢复伤兵2回合；1回合内受到谋略伤害时75%几率触发动摇效果(35%)',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r = calc_skill_addition_rate(190, 1.75, hero.attrs['int'])
        r2 = calc_skill_addition_rate(35, 0.32, hero.attrs['int'])
        for t in hero.get_target(sk.limit, 2):
            t.be_hurt(hero, {'type': 2, 'rate': r}, sk)
            def mh(tgt=t):
                def on_hurt(attacker, di, s):
                    if di.get('type') == 2 and get_random_bool(75):
                        tgt.be_hurt(attacker, {'type': 3, 'rate': r2}, sk)
                tgt.add_hook('受伤时', f'{sk.id}_{id(tgt)}', on_hurt, sk, hero)
            mh()
        return True


# ─── 200266 怀橘遗亲
class Skill200266(Skill):
    def __init__(self):
        super().__init__(200266,'怀橘遗亲','每回合开始时，若友军大营已受伤超10点攻击属性提升；若友军大营20点提升更多',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v1 = calc_skill_addition_rate(10, 0.09, hero.attrs['int'])
        v2 = calc_skill_addition_rate(20, 0.18, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        main_hero = team[0] if team else None
        if not main_hero: return
        def on_rnd():
            if main_hero.hurt_arms > 0:
                v = v2 if main_hero.hurt_arms >= main_hero.origin_attrs.get('arms', 1) * 0.2 else v1
                hero.attrs['atk'] = keep_two_decimal(hero.attrs['atk'] + v)
        hero.add_hook('回合开始时', f'{sk.id}_buff', on_rnd, sk, hero)


# ─── 200655 虎步关右
class Skill200655(Skill):
    def __init__(self):
        super().__init__(200655,'虎步关右','使自身首次造成的攻击伤害提高70%（受速度属性影响）',4,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        v = calc_skill_addition_rate(70, 0.65, hero.attrs['spd'])
        fired = [False]
        def on_dmg(t, di, s2, dmg):
            if not fired[0] and di.get('type') == 1:
                fired[0] = True
                di['rate'] = keep_two_decimal(di.get('rate', 100) * (1 + v/100))
        hero.add_hook('造成伤害后', f'{sk.id}_first', on_dmg, sk, hero)


# ─── 200705 定军绝战
class Skill200705(Skill):
    def __init__(self):
        super().__init__(200705,'定军绝战','对敌军单体发动一次攻击(140%)',3,35,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r = calc_skill_addition_rate(140, 1.3, hero.attrs['atk'])
        if target and target.arms > 0:
            target.be_hurt(hero, {'type': 1, 'rate': r}, sk)
        return True


# ─── 200707 中郎尽瘁
class Skill200707(Skill):
    def __init__(self):
        super().__init__(200707,'中郎尽瘁','使自身进行攻击造成的伤害较低时受到追击战法伤害降低46%；援军已全体为低值的骑士可以使自身进入无双状态，恢复兵力(152%)',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        v = calc_skill_addition_rate(46, 0.42, hero.attrs['int'])
        r = calc_skill_addition_rate(152, 1.4, hero.attrs['int'])
        hero.add_state('beActiveDamageSub', v, -1, sk, hero)
        hero.state['doubleAttack'] = {'rounds': -1, 'from': {'hero': hero, 'skill': sk}}
        hero.recover(calc_recover(hero, r, 1.4, 2), hero, sk.name)


# ─── 200719 上将潘凤
class Skill200719(Skill):
    def __init__(self):
        super().__init__(200719,'上将潘凤','对敌军单体发动一次猛攻(355%)，一回合内无法被普通攻击',2,35,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r = calc_skill_addition_rate(355, 3.3, hero.attrs['atk'])
        targets = hero.get_target(sk.limit, 1)
        for t in targets:
            t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
        hero.add_state('beAttackDamageSub', 100, 1, sk, hero)
        return True


# ─── 200785 破阵强袭
class Skill200785(Skill):
    def __init__(self):
        super().__init__(200785,'破阵强袭','普通攻击后，对攻击目标再次发动谋略追击(119%)；40%几率使目标进入暴走状态，持续1回合；首次攻击造成的伤害每次递减5%',3,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r = calc_skill_addition_rate(119, 1.1, hero.attrs['int'])
        if target and target.arms > 0:
            target.be_hurt(hero, {'type': 2, 'rate': r}, sk)
            if get_random_bool(40):
                target.state['confusion'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        return True


# ─── 200790 青白楼独舞
class Skill200790(Skill):
    def __init__(self):
        super().__init__(200790,'青丘媚祸','每回合受到伤害60%几率使其4回合内的敌军在下次行动时进入暴走状态，无法普攻；使其造成的攻击和谋略攻击伤害降低24%，持续1回合',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(24, 0.22, hero.attrs['int'])
        def on_hurt(attacker, di, s):
            if hero.manager.round <= 4 and get_random_bool(60):
                attacker.state['confusion'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
                attacker.add_state('attackDamageSub', v, 1, sk, hero)
                attacker.add_state('inteDamageSub', v, 1, sk, hero)
        hero.add_hook('受伤时', f'{sk.id}_hook', on_hurt, sk, hero)


# ─── 200791 西乡武功
class Skill200791(Skill):
    def __init__(self):
        super().__init__(200791,'西乡武功','战斗前2回合，使友军全体每回合都能判定行动，使第2回合行动前对敌军群体2人发动一次谋略攻击(191%)，每个目标独立判定',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        r = calc_skill_addition_rate(191, 1.78, hero.attrs['int'])
        def on_rnd():
            if hero.manager.round == 2:
                for t in hero.get_target(sk.limit, 2):
                    t.be_hurt(hero, {'type': 2, 'rate': r}, sk)
        hero.add_hook('回合开始时', f'{sk.id}_rnd', on_rnd, sk, hero)


# ─── 200795 人公将军
class Skill200795(Skill):
    def __init__(self):
        super().__init__(200795,'人公将军','战斗前4回合，使友军全体的防御属性+60；使友军前锋、中军受到普通攻击时进行反击(75%)；在此期间，反击武将有攻击效果时造成的攻击伤害提高20%',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        r = calc_skill_addition_rate(75, 0.7, hero.attrs['atk'])
        v = calc_skill_addition_rate(20, 0.18, hero.attrs['atk'])
        v_def = calc_skill_addition_rate(60, 0.55, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.attrs['def'] = keep_two_decimal(e.attrs['def'] + v_def)
            def mh(tgt=e):
                def on_hurt(attacker, di, s):
                    if di.get('type') == 1 and hero.manager.round <= 4:
                        attacker.be_hurt(tgt, {'type': 1, 'rate': r}, sk)
                tgt.add_hook('受伤时', f'{sk.id}_{id(tgt)}', on_hurt, sk, hero)
            mh()


# ─── 200796 地公将军
class Skill200796(Skill):
    def __init__(self):
        super().__init__(200796,'地公将军','对敌军群体发动谋略攻击(136%)；获取24的防御属性提升；同时已受伤全体为低值的步兵速度不影响目标时额外附加效果，持续2回合',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r = calc_skill_addition_rate(136, 1.25, hero.attrs['int'])
        v = calc_skill_addition_rate(24, 0.22, hero.attrs['int'])
        for t in hero.get_target(sk.limit, 2):
            t.be_hurt(hero, {'type': 2, 'rate': r}, sk)
        hero.attrs['def'] = keep_two_decimal(hero.attrs['def'] + v)
        return True


# ─── 200836 平地将旗
class Skill200836(Skill):
    def __init__(self):
        super().__init__(200836,'平地将旗','对敌军步兵、骑兵和弓兵中数量最多的兵种直接发动一次攻击(210%)；同时使敌军群体进入混战状态，无法施放普通攻击，持续2回合',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r = calc_skill_addition_rate(210, 1.95, hero.attrs['atk'])
        for t in hero.get_target(sk.limit, 3):
            t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
        for t in hero.get_target(sk.limit, 2):
            t.state['confusion'] = {'rounds': 2, 'from': {'hero': hero, 'skill': sk}}
        return True


# ─── 200837 一鼓作气
class Skill200837(Skill):
    def __init__(self):
        super().__init__(200837,'一鼓作气','战斗开始前3回合，每回合使友军全体发动鼓舞，造成的伤害+6%叠加，最多叠加3次；直至战斗结束；之后4回合直至战斗结束，每次造成伤害时，使自身追击攻击伤害+50%',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(6, 0.055, hero.attrs['int'])
        v2 = calc_skill_addition_rate(50, 0.46, hero.attrs['atk'])
        stack = [0]
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        def on_rnd():
            if hero.manager.round <= 3 and stack[0] < 3:
                stack[0] += 1
                for e in team:
                    e.add_state('attackDamageAdd', v * stack[0], -1, sk, hero)
            elif hero.manager.round > 3:
                hero.add_state('activeDamageAdd', v2, -1, sk, hero)
        hero.add_hook('回合开始时', f'{sk.id}_rnd', on_rnd, sk, hero)


# ─── 200843 蝉鸣蛩音
class Skill200843(Skill):
    def __init__(self):
        super().__init__(200843,'蝉鸣蛩音','战斗开始使受到的谋略伤害提高84%；每次受到谋略追击攻击伤害后，对对方伤害的叠加效果，减少1/12；位前锋及中军时前2回合援军已全体；同时每次受到伤害提高50%使攻击',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        v = calc_skill_addition_rate(84, 0.78, hero.attrs['int'])
        hero.add_state('beInteDamageAdd', v, -1, sk, hero)


# ─── 200846 玉斧神功
class Skill200846(Skill):
    def __init__(self):
        super().__init__(200846,'玉斧神功','1回合准备，对敌军群体发动一次谋略追击(240%)；一次攻击伤害(240%)，攻击目标独立判断；同时使友军全体援军全体无法恢复伤兵，持续2回合',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r = calc_skill_addition_rate(240, 2.2, hero.attrs['int'])
        def subskill():
            for t in hero.get_target(sk.limit, 2):
                t.be_hurt(hero, {'type': 2, 'rate': r}, sk)
                t.be_hurt(hero, {'type': 2, 'rate': r}, sk)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200848 未竟之志
class Skill200848(Skill):
    def __init__(self):
        super().__init__(200848,'未竟之志','使敌军全体陷入空营状态，每回合损失兵力(108%)；持续2回合；同时使敌军全体有主动战法和追击战法时伤害降低18%，效果叠加',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r = calc_skill_addition_rate(108, 1.0, hero.attrs['int'])
        v = calc_skill_addition_rate(18, 0.16, hero.attrs['int'])
        for t in hero.get_target(sk.limit, 3):
            t.add_state('fire', r, 2, sk, hero)
            t.add_state('beActiveDamageSub', v, -1, sk, hero)
        return True


# ─── 200849 神机杀伐
class Skill200849(Skill):
    def __init__(self):
        super().__init__(200849,'神机杀伐','1回合准备，对敌军群体2目标发动一次攻击(260%)，使其攻击属性降低40；同时友军全体攻击属性提高40，持续2回合；对敌军全体3目标发动',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r = calc_skill_addition_rate(260, 2.4, hero.attrs['atk'])
        v = calc_skill_addition_rate(40, 0.37, hero.attrs['int'])
        def subskill():
            n = get_random_int(2, 3)
            for t in hero.get_target(sk.limit, n):
                t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
                t.attrs['atk'] = max(0, keep_two_decimal(t.attrs['atk'] - v))
            team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
            for e in team:
                e.attrs['atk'] = keep_two_decimal(e.attrs['atk'] + v)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200851 落霞之志
class Skill200851(Skill):
    def __init__(self):
        super().__init__(200851,'落霞之志','战斗中，友军全体每回合首次受到伤害后都获取伤害来源的攻击属性中的一项70%，持续2回合',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v_rate = 0.70
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            def mh(tgt=e):
                hurt_this_round = [False]
                def on_rnd_start():
                    hurt_this_round[0] = False
                def on_hurt(attacker, di, s):
                    if not hurt_this_round[0]:
                        hurt_this_round[0] = True
                        bonus = keep_two_decimal(attacker.attrs.get('atk', 0) * v_rate)
                        tgt.add_state('attackDamageAdd', bonus, 2, sk, hero)
                tgt.add_hook('回合开始时', f'{sk.id}_rst_{id(tgt)}', on_rnd_start, sk, hero)
                tgt.add_hook('受伤时', f'{sk.id}_{id(tgt)}', on_hurt, sk, hero)
            mh()


# ─── 200852 草庐对策
class Skill200852(Skill):
    def __init__(self):
        super().__init__(200852,'草庐对策','战斗开始前3回合，使中军和策士中最高的将领属性降低100；策士属性降低100；同时中军全体每次发动普通攻击战法时普通攻击50%使攻击，一回合内发动次数叠加',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(100, 0.92, hero.attrs['int'])
        enemies = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
        for t in enemies:
            t.attrs['int'] = max(0, keep_two_decimal(t.attrs['int'] - v))


# ─── 200857 凤翔孤凰
class Skill200857(Skill):
    def __init__(self):
        super().__init__(200857,'凤翔孤凰','对敌方弓兵发动2次谋略追击(140%)；使其直属弓兵和燃烧状态伤害(156%)，持续2回合；2个目标独立判断',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r1 = calc_skill_addition_rate(140, 1.3, hero.attrs['int'])
        r2 = calc_skill_addition_rate(156, 1.44, hero.attrs['int'])
        targets = hero.get_target(sk.limit, 2)
        for t in targets:
            t.be_hurt(hero, {'type': 2, 'rate': r1}, sk)
            t.be_hurt(hero, {'type': 2, 'rate': r1}, sk)
            t.add_state('fire', r2, 2, sk, hero)
        return True


# ─── 200864 黄巾双壁
class Skill200864(Skill):
    def __init__(self):
        super().__init__(200864,'黄巾双壁','战斗开始前3回合，使己方自身援军每回合以90%的加速进入蓄力状态，同时或换予警戒战斗；同时获得中造成的伤害提高25%',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(25, 0.23, hero.attrs['atk'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('attackDamageAdd', v, -1, sk, hero)


# ─── 200865 精进
class Skill200865(Skill):
    def __init__(self):
        super().__init__(200865,'精进','战斗中，己方援军造成的普通攻击和追击战法伤害每次返还1次普通攻击造成的伤害35%；1次受到谋略伤害降低20%，效果叠加最多5次',4,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        v = calc_skill_addition_rate(35, 0.32, hero.attrs['atk'])
        v2 = calc_skill_addition_rate(20, 0.18, hero.attrs['int'])
        stack = [0]
        def on_dmg(t, di, s2, dmg):
            if stack[0] < 5:
                stack[0] += 1
                hero.add_state('beInteDamageSub', v2, -1, sk, hero)
        hero.add_hook('造成伤害后', f'{sk.id}_stack', on_dmg, sk, hero)


# ─── 200885 天下归心
class Skill200885(Skill):
    def __init__(self):
        super().__init__(200885,'天下归心','战斗开始前3回合，普通攻击对攻击目标发动一次攻击(150%)；4回合后普通攻击对敌军中大营1-3次攻击(150%)，每个目标判断',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        r = calc_skill_addition_rate(150, 1.4, hero.attrs['atk'])
        def on_act():
            if hero.manager.round <= 3:
                targets = hero.get_target(sk.limit, 1)
            else:
                n = get_random_int(1, 3)
                targets = hero.get_target(sk.limit, n)
            for t in targets:
                t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
        hero.add_hook('行动时', f'{sk.id}_act', on_act, sk, hero)


# ─── 200887 熊罴之火
class Skill200887(Skill):
    def __init__(self):
        super().__init__(200887,'熊罴之火','对敌军单体发动一次火攻(95%)；之后每次发动火攻后60%再次发动火攻，伤害每次递增20%，每个目标判断；战斗结束后清除',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        base_r = calc_skill_addition_rate(95, 0.88, hero.attrs['int'])
        targets = hero.get_target(sk.limit, 1)
        for t in targets:
            r = base_r
            t.be_hurt(hero, {'type': 4, 'rate': r}, sk)
            for _ in range(5):
                if get_random_bool(60):
                    r = keep_two_decimal(r * 1.20)
                    t.be_hurt(hero, {'type': 4, 'rate': r}, sk)
                else:
                    break
        return True


# ─── 200891 熊虎之将
class Skill200891(Skill):
    def __init__(self):
        super().__init__(200891,'熊虎之将','对敌方弓兵发动2次攻击(150%)并使其进入动摇和燃烧状态伤害(100%)，持续2回合；2个目标判断',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r1 = calc_skill_addition_rate(150, 1.4, hero.attrs['atk'])
        r2 = calc_skill_addition_rate(100, 0.92, hero.attrs['int'])
        for t in hero.get_target(sk.limit, 2):
            t.be_hurt(hero, {'type': 1, 'rate': r1}, sk)
            t.be_hurt(hero, {'type': 1, 'rate': r1}, sk)
            t.add_state('fire', r2, 2, sk, hero)
        return True


# ─── 200902 明哲存危
class Skill200902(Skill):
    def __init__(self):
        super().__init__(200902,'明哲存危','对敌军群体发动一次攻击(210%)；使自身援军最高的单体第次攻击造成的伤害提高30%；同时使援军在一次造成的伤害加分条',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r = calc_skill_addition_rate(210, 1.95, hero.attrs['atk'])
        v = calc_skill_addition_rate(30, 0.27, hero.attrs['atk'])
        for t in hero.get_target(sk.limit, 2):
            t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
        hero.add_state('attackDamageAdd', v, -1, sk, hero)
        return True


# ─── 200927 虎啸惊雷
class Skill200927(Skill):
    def __init__(self):
        super().__init__(200927,'虎啸惊雷','攻击敌军单体发动2-4次攻击(250%)，每次伤害叠加40%；首次攻击造成的伤害叠加攻击，使目标无法恢复伤兵，持续2回合',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        base_r = calc_skill_addition_rate(250, 2.3, hero.attrs['atk'])
        v = calc_skill_addition_rate(40, 0.37, hero.attrs['atk'])
        for t in hero.get_target(sk.limit, 1):
            n = get_random_int(2, 4)
            r = base_r
            for i in range(n):
                t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
                r = keep_two_decimal(r + v)
        return True


# ─── 200928 勇者无畏
class Skill200928(Skill):
    def __init__(self):
        super().__init__(200928,'勇者无畏','若己方援军中最低的将领被一次判断时触发战斗状态为满，使友军全体受到的攻击伤害提高20%；效果叠加最多1次，持续2回合；使友军全体后2次造成的追击伤害+30%',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        v = calc_skill_addition_rate(20, 0.18, hero.attrs['int'])
        v2 = calc_skill_addition_rate(30, 0.27, hero.attrs['atk'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('beAttackDamageSub', v, -1, sk, hero)
            e.add_state('activeDamageAdd', v2, -1, sk, hero)


# ─── 200933 钟山之战
class Skill200933(Skill):
    def __init__(self):
        super().__init__(200933,'钟山之战','每回合进行判断时，30%的概率获得蓄力效果，效果叠加最多持续1回合；用分别效果伤害(100%)，叠加1回合；伤害每回合结束时-10%，每次效果独立判断',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        r = calc_skill_addition_rate(100, 0.92, hero.attrs['atk'])
        def on_rnd():
            if get_random_bool(30):
                for t in hero.get_target(sk.limit, 1):
                    t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
        hero.add_hook('回合开始时', f'{sk.id}_rnd', on_rnd, sk, hero)


# ─── 200934 虎牢关口
class Skill200934(Skill):
    def __init__(self):
        super().__init__(200934,'虎牢关口','正式回合开始，使友军全体受到的追击攻击伤害提高40%；每次受到追击攻击伤害后，叠加效果增加1/8；同时每回合判断时60%的概率为已全体铺一次50%受到的追击伤害',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        v = calc_skill_addition_rate(40, 0.37, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('beActiveDamageAdd', v, -1, sk, hero)


# ─── 200936 麒麟之才
class Skill200936(Skill):
    def __init__(self):
        super().__init__(200936,'麒麟之才','使中军受到的伤害提高30%；持续2回合；同时使目标下次判断时受到一名友方的追击攻击伤害(240%)',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(30, 0.27, hero.attrs['int'])
        r = calc_skill_addition_rate(240, 2.2, hero.attrs['int'])
        for t in hero.get_target(sk.limit, 1):
            t.add_state('beInteDamageAdd', v, 2, sk, hero)
            t.be_hurt(hero, {'type': 2, 'rate': r}, sk)
        return True


# ─── 200937 全军皆降
class Skill200937(Skill):
    def __init__(self):
        super().__init__(200937,'全军皆降','使友军全体被施加的燃烧、弱袭等状态伤害提高20%；持续3回合；同时对敌军群体1-2目标额外发动1次谋略追击(197%)',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(20, 0.18, hero.attrs['int'])
        r = calc_skill_addition_rate(197, 1.82, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('attackDamageAdd', v, 3, sk, hero)
        n = get_random_int(1, 2)
        for t in hero.get_target(sk.limit, n):
            t.be_hurt(hero, {'type': 2, 'rate': r}, sk)
        return True


# ─── 200939 逆风翻盘
class Skill200939(Skill):
    def __init__(self):
        super().__init__(200939,'逆风翻盘','战斗中使己方处于劣势状态，若己方首次在兵力初始值90%、70%、50%、30%时，使己方下次判断时进行攻击攻击至战斗，使己方后120%',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        r = calc_skill_addition_rate(120, 1.1, hero.attrs['atk'])
        thresholds = [0.9, 0.7, 0.5, 0.3]
        triggered = set()
        def on_hurt(attacker, di, s):
            ratio = hero.arms / hero.origin_attrs.get('arms', 1)
            for th in thresholds:
                if ratio <= th and th not in triggered:
                    triggered.add(th)
                    for t in hero.get_target(sk.limit, 1):
                        t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
        hero.add_hook('受伤时', f'{sk.id}_hook', on_hurt, sk, hero)


# ─── 200940 地平线外
class Skill200940(Skill):
    def __init__(self):
        super().__init__(200940,'地平线外','战斗中，每回合首次发动普通攻击战法时，使友军全体在有追击战法时伤害提高10%，效果叠加最多3次，持续1回合',4,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        v = calc_skill_addition_rate(10, 0.09, hero.attrs['atk'])
        first_this_round = [False]
        def on_rnd_start():
            first_this_round[0] = False
        def on_act():
            if not first_this_round[0]:
                first_this_round[0] = True
                team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
                for e in team:
                    e.add_state('attackDamageAdd', v, 1, sk, hero)
        hero.add_hook('回合开始时', f'{sk.id}_rst', on_rnd_start, sk, hero)
        hero.add_hook('行动时', f'{sk.id}_act', on_act, sk, hero)


# ─── 200943 武圣归来
class Skill200943(Skill):
    def __init__(self):
        super().__init__(200943,'武圣归来','从中军和策士中最高和最低的将领各取12点攻击属性（受攻击属性影响），分配给己方军队，效果叠加，贯穿至战斗结束',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        v = calc_skill_addition_rate(12, 0.11, hero.attrs['atk'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.attrs['atk'] = keep_two_decimal(e.attrs['atk'] + v)


# ─── 200944 虎啸龙吟
class Skill200944(Skill):
    def __init__(self):
        super().__init__(200944,'虎啸龙吟','对敌军单体发动2次攻击(160%)，使其无法恢复伤兵2回合；目标兵力低于初始值60%时，额外发动一次攻击(160%)',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r = calc_skill_addition_rate(160, 1.48, hero.attrs['atk'])
        for t in hero.get_target(sk.limit, 1):
            t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
            t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
            if t.arms / t.origin_attrs.get('arms', 1) < 0.6:
                t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
        return True


# ─── 200945 无双
class Skill200945(Skill):
    def __init__(self):
        super().__init__(200945,'无双','1回合准备，对敌军全体发动一次攻击(180%)；一回合内，友军40%概率获得一个额外状态',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r = calc_skill_addition_rate(180, 1.67, hero.attrs['atk'])
        def subskill():
            for t in hero.get_target(sk.limit, 3):
                t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
            if get_random_bool(40):
                team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
                for e in team:
                    e.add_state('attackDamageAdd', 20, 1, sk, hero)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200946 踏营破斥
class Skill200946(Skill):
    def __init__(self):
        super().__init__(200946,'踏营破斥','普通攻击后，对攻击目标再次发动猛攻(160%)，使自身援军恢复兵力(140%)',3,35,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r = calc_skill_addition_rate(160, 1.48, hero.attrs['atk'])
        r2 = calc_skill_addition_rate(140, 1.3, hero.attrs['atk'])
        if target and target.arms > 0:
            target.be_hurt(hero, {'type': 1, 'rate': r}, sk)
        hero.recover(calc_recover(hero, r2, 1.3, 1), hero, sk.name)
        return True


# ─── 200947 千秋义夫
class Skill200947(Skill):
    def __init__(self):
        super().__init__(200947,'千秋义夫','对敌军群体发动一次攻击(200%)，使自身援军造成战斗伤害提高20%，持续2回合',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r = calc_skill_addition_rate(200, 1.85, hero.attrs['atk'])
        v = calc_skill_addition_rate(20, 0.18, hero.attrs['atk'])
        for t in hero.get_target(sk.limit, 2):
            t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
        hero.add_state('attackDamageAdd', v, 2, sk, hero)
        return True


# ─── 200948 怒斥方遒
class Skill200948(Skill):
    def __init__(self):
        super().__init__(200948,'怒斥方遒','普通攻击后，对攻击目标发动一次袭击(220%)；战斗每增加一次，发动概率+10%，最多3次',3,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        r = calc_skill_addition_rate(220, 2.04, hero.attrs['atk'])
        if target and target.arms > 0:
            target.be_hurt(hero, {'type': 1, 'rate': r}, sk)
        return True


# ─── 200950 苍龙腾渊
class Skill200950(Skill):
    def __init__(self):
        super().__init__(200950,'苍龙腾渊','战斗中，友军全体每次发动普通攻击或追击战法或追击战法时，若己方造成伤害提高6%；效果叠加，友军全体发动普通攻击或追击战法或追击战法时以同样方式',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        v = calc_skill_addition_rate(6, 0.055, hero.attrs['atk'])
        stack = [0]
        def on_dmg(t, di, s2, dmg):
            stack[0] += 1
            hero.add_state('attackDamageAdd', v, -1, sk, hero)
        hero.add_hook('造成伤害后', f'{sk.id}_stack', on_dmg, sk, hero)


# ─── 200951 虎虎生威
class Skill200951(Skill):
    def __init__(self):
        super().__init__(200951,'虎虎生威','对敌军单体发动一次攻击(120%)，使受到的伤害提高15%，效果叠加，贯穿至战斗结束',2,35,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r = calc_skill_addition_rate(120, 1.1, hero.attrs['atk'])
        v = calc_skill_addition_rate(15, 0.14, hero.attrs['int'])
        for t in hero.get_target(sk.limit, 1):
            t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
            t.add_state('beAttackDamageAdd', v, -1, sk, hero)
        return True


# ─── 200954 纵横开野
class Skill200954(Skill):
    def __init__(self):
        super().__init__(200954,'纵横开野','战斗开始，使友军全体进行攻击和追击时伤害40%，使伤害提高30%；每回合己方援军判断时，使友军全体伤害叠加减少3%，非后营武将伤害叠加每回合3%',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(30, 0.27, hero.attrs['int'])
        v2 = calc_skill_addition_rate(3, 0.027, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('attackDamageAdd', v, -1, sk, hero)
        def on_rnd():
            for e in team:
                e.add_state('attackDamageAdd', v2, -1, sk, hero)
        hero.add_hook('回合开始时', f'{sk.id}_rnd', on_rnd, sk, hero)


# ─── 200958 磐石如意
class Skill200958(Skill):
    def __init__(self):
        super().__init__(200958,'磐石如意','使友军全体每回合有45%概率获得50防御属性；持续1回合。同时当己方前锋位于前线时，敌营兵力首次在初始值90%、70%、50%时额外为己方全体补充1回合',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(50, 0.46, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        def on_rnd():
            if get_random_bool(45):
                for e in team:
                    e.add_state('defAdd', v, 1, sk, hero)
        hero.add_hook('回合开始时', f'{sk.id}_rnd', on_rnd, sk, hero)


# ─── 200962 解甲归田
class Skill200962(Skill):
    def __init__(self):
        super().__init__(200962,'解甲归田','战斗中己方无法释放攻击，在战斗中的己方全体每回合首次成功释放攻击后40%几率，移除攻击战法准备回合，同时原战斗50%伤害和恢复效果',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        r = calc_skill_addition_rate(50, 0.46, hero.attrs['atk'])
        def on_act():
            if get_random_bool(40):
                for t in hero.get_target(sk.limit, 1):
                    t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
        hero.add_hook('行动时', f'{sk.id}_act', on_act, sk, hero)


# ─── 200963 飘羽灵风
class Skill200963(Skill):
    def __init__(self):
        super().__init__(200963,'飘羽灵风','战斗中，每回合失去兵力后为最高的单体将领将加成属性，降低6%（受攻击属性影响）；效果叠加，贯穿至战斗结束；每名将领属性叠加时，额外一次造成的攻击',4,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        v = calc_skill_addition_rate(6, 0.055, hero.attrs['atk'])
        def on_hurt(attacker, di, s):
            hero.add_state('attackDamageAdd', v, -1, sk, hero)
        hero.add_hook('受伤时', f'{sk.id}_hook', on_hurt, sk, hero)


# ─── 200965 兵多将广
class Skill200965(Skill):
    def __init__(self):
        super().__init__(200965,'兵多将广','每个回合判断时，每次50%概率对己方营地上次受过判断的段次伤害对目标发动一次攻击(120%)，叠加3次，每个目标独立判断且同一目标伤害每次递减20%',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        base_r = calc_skill_addition_rate(120, 1.1, hero.attrs['atk'])
        def on_rnd():
            targets = hero.get_target(sk.limit, 1)
            r = base_r
            for t in targets:
                for i in range(3):
                    if get_random_bool(50):
                        t.be_hurt(hero, {'type': 1, 'rate': r}, sk)
                        r = keep_two_decimal(r * 0.80)
        hero.add_hook('回合开始时', f'{sk.id}_rnd', on_rnd, sk, hero)


# ─── 200968 沙场遗风
class Skill200968(Skill):
    def __init__(self):
        super().__init__(200968,'沙场遗风','对敌军单体施加攻击控制，每次发动攻击战法对攻击目标发动一次"惊"，攻击目标属性叠加时目标处于敌对状态时对敌军单体发动一次"诅"；每次战斗',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r = calc_skill_addition_rate(130, 1.2, hero.attrs['int'])
        for t in hero.get_target(sk.limit, 1):
            t.be_hurt(hero, {'type': 2, 'rate': r}, sk)
            t.state['confusion'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        return True


# ─── 200988 雁门透骨
class Skill200988(Skill):
    def __init__(self):
        super().__init__(200988,'雁门透骨','移除己方所有状态中的化效果，恢复兵力(168%)；使己方下回合有50%概率进入蓄力状态',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r = calc_recover(hero, 168, 1.55, 2)
        hero.recover(r, hero, sk.name)
        if get_random_bool(50):
            hero.state['ready'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        return True


# ─── 200989 斩将突围
class Skill200989(Skill):
    def __init__(self):
        super().__init__(200989,'斩将突围','使己方全体在每次成功攻击伤害前属性+22（受攻击属性影响），效果叠加最多4次，持续1回合；每次受到追击攻击伤害前，属性+22（受攻击属性影响），效果叠加最多4次，持续1回合',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        v = calc_skill_addition_rate(22, 0.2, hero.attrs['atk'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('attackDamageAdd', v, -1, sk, hero)


# ─── 200990 顺势而为
class Skill200990(Skill):
    def __init__(self):
        super().__init__(200990,'顺势而为','受到恢复效果时，75%概率为己方全体恢复兵力(65%)；受到谋略伤害时，75%概率对伤害来源再次发动谋略追击(180%)',4,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        sk = self
        r_heal = calc_recover(hero, 65, 0.6, 2)
        r_atk = calc_skill_addition_rate(180, 1.67, hero.attrs['int'])
        def on_heal(amount, src, sk_name):
            if get_random_bool(75):
                team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
                for e in team:
                    e.recover(r_heal, hero, sk.name)
        def on_hurt(attacker, di, s):
            if di.get('type') == 2 and get_random_bool(75):
                attacker.be_hurt(hero, {'type': 2, 'rate': r_atk}, sk)
        hero.add_hook('恢复时', f'{sk.id}_heal', on_heal, sk, hero)
        hero.add_hook('受伤时', f'{sk.id}_hurt', on_hurt, sk, hero)


# ─── 201007 幕后定策
class Skill201007(Skill):
    def __init__(self):
        super().__init__(201007,'幕后定策','战斗前3回合，进行判断时90%移除自身受到的所有蓄力和追击战法效果；4回合开始，使己方全体策士将领全部释放攻击+40（受攻击属性影响），女性将领攻击和防御提高',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(40, 0.37, hero.attrs['int'])
        def on_rnd():
            if hero.manager.round <= 3 and get_random_bool(90):
                hero.state.pop('ready', None)
            elif hero.manager.round == 4:
                team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
                for e in team:
                    e.attrs['atk'] = keep_two_decimal(e.attrs['atk'] + v)
                hero.clear_hook('回合开始时', f'{sk.id}_rnd')
        hero.add_hook('回合开始时', f'{sk.id}_rnd', on_rnd, sk, hero)


# ═══ 注册所有新增战法 ═══
SKILLS_A[200004] = Skill200004()
SKILLS_A[200006] = Skill200006()
SKILLS_A[200007] = Skill200007()
SKILLS_A[200009] = Skill200009()
SKILLS_A[200017] = Skill200017()
SKILLS_A[200025] = Skill200025()
SKILLS_A[200026] = Skill200026()
SKILLS_A[200032] = Skill200032()
SKILLS_A[200033] = Skill200033()
SKILLS_A[200035] = Skill200035()
SKILLS_A[200037] = Skill200037()
SKILLS_A[200080] = Skill200080()
SKILLS_A[200094] = Skill200094()
SKILLS_A[200266] = Skill200266()
SKILLS_A[200655] = Skill200655()
SKILLS_A[200705] = Skill200705()
SKILLS_A[200707] = Skill200707()
SKILLS_A[200719] = Skill200719()
SKILLS_A[200785] = Skill200785()
SKILLS_A[200790] = Skill200790()
SKILLS_A[200791] = Skill200791()
SKILLS_A[200795] = Skill200795()
SKILLS_A[200796] = Skill200796()
SKILLS_A[200836] = Skill200836()
SKILLS_A[200837] = Skill200837()
SKILLS_A[200843] = Skill200843()
SKILLS_A[200846] = Skill200846()
SKILLS_A[200848] = Skill200848()
SKILLS_A[200849] = Skill200849()
SKILLS_A[200851] = Skill200851()
SKILLS_A[200852] = Skill200852()
SKILLS_A[200857] = Skill200857()
SKILLS_A[200864] = Skill200864()
SKILLS_A[200865] = Skill200865()
SKILLS_A[200885] = Skill200885()
SKILLS_A[200887] = Skill200887()
SKILLS_A[200891] = Skill200891()
SKILLS_A[200902] = Skill200902()
SKILLS_A[200927] = Skill200927()
SKILLS_A[200928] = Skill200928()
SKILLS_A[200933] = Skill200933()
SKILLS_A[200934] = Skill200934()
SKILLS_A[200936] = Skill200936()
SKILLS_A[200937] = Skill200937()
SKILLS_A[200939] = Skill200939()
SKILLS_A[200940] = Skill200940()
SKILLS_A[200943] = Skill200943()
SKILLS_A[200944] = Skill200944()
SKILLS_A[200945] = Skill200945()
SKILLS_A[200946] = Skill200946()
SKILLS_A[200947] = Skill200947()
SKILLS_A[200948] = Skill200948()
SKILLS_A[200950] = Skill200950()
SKILLS_A[200951] = Skill200951()
SKILLS_A[200954] = Skill200954()
SKILLS_A[200958] = Skill200958()
SKILLS_A[200962] = Skill200962()
SKILLS_A[200963] = Skill200963()
SKILLS_A[200965] = Skill200965()
SKILLS_A[200968] = Skill200968()
SKILLS_A[200988] = Skill200988()
SKILLS_A[200989] = Skill200989()
SKILLS_A[200990] = Skill200990()
SKILLS_A[201007] = Skill201007()


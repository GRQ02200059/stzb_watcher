# -*- coding: utf-8 -*-
# 追加剩余S级战法 + 注册表

REMAINING = """

# ─── 200938 三术奇谋
class Skill200938(Skill):
    def __init__(self):
        super().__init__(200938,'三术奇谋','1回合准备对敌单体3次谋略攻击(178%)依次降攻防谋18',2,50,False,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        rate = calc_skill_addition_rate(178, 1.65, hero.attrs['int'])
        sub_v = calc_skill_addition_rate(18, 0.14, hero.attrs['int'])
        def subskill():
            tgs = hero.get_target(sk.limit, 1)
            if not tgs: return
            t = tgs[0]
            for _ in range(3):
                t.be_hurt(hero, {'type': 2, 'rate': rate}, sk)
            for key in ('atk', 'def', 'int'):
                t.attrs[key] = keep_two_decimal(max(0, t.attrs[key] - sub_v))
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200942 暴戾恣睢
class Skill200942(Skill):
    def __init__(self):
        super().__init__(200942,'暴戾恣睢','攻击伤害提高4%每回合叠加；每回合行动时20%几率对敌全体攻击(150%)',1,'--',False,5)
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
                for t in hero.get_target(sk.limit, 2):
                    t.be_hurt(hero, {'type': 1, 'rate': rate_v}, sk)
            rage_rate[0] = min(100, rage_rate[0] + 5)
        hero.add_hook('行动时', f'{sk.id}_act', sub, sk, hero)


# ─── 200952 抚民励德
class Skill200952(Skill):
    def __init__(self):
        super().__init__(200952,'抚民励德','第2/4/6回合行动时我军全体谋略防御+80并受伤-20%',1,'--',False,3)
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
        super().__init__(200955,'动如雷震','我军群体追击发动率提升100%追击伤害提升40%持续1回合',2,30,False,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        spd_v = calc_skill_addition_rate(40, 0.3, hero.attrs['spd'])
        for e in hero.get_target(sk.limit, 2, 2):
            e.add_state('chaseDamageAdd', spd_v, 1, sk, hero)
            e.rate_val[f'{sk.id}_chase_{id(e)}'] = {'value': 100, 'rounds': 1}
        return True


# ─── 200957 五兵之烈
class Skill200957(Skill):
    def __init__(self):
        super().__init__(200957,'五兵之烈','对敌军群体猛烈攻击(280%)默认使目标防御降低30%',2,35,False,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate = calc_skill_addition_rate(280, 2.6, hero.attrs['atk'])
        sub_v = calc_skill_addition_rate(30, 0.25, hero.attrs['atk'])
        for t in hero.get_target(sk.limit, 2):
            t.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
            if t.arms > 0:
                t.attrs['def'] = keep_two_decimal(t.attrs['def'] * (1 - sub_v / 100))
        return True


# ─── 200960 鸾凤和鸣
class Skill200960(Skill):
    def __init__(self):
        super().__init__(200960,'鸾凤和鸣','每回合首次发动主动后我军群体2目标恢复85%',1,'--',False,3)
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
                for t in hero.get_target(3, 1, 2):
                    t.recover(calc_recover(hero, rv, 0.75, 2), hero, sk.name)
        hero.add_hook('行动前', f'{sk.id}_act', on_act, sk, hero)
        hero.add_hook('回合开始时', f'{sk.id}_rst', on_rnd, sk, hero)


# ─── 200961 奋疾先登
class Skill200961(Skill):
    def __init__(self):
        super().__init__(200961,'奋疾先登','每回合行动时攻击伤害提升8%达40%时群体攻击并重置',1,'--',False,5)
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
            for e in random.sample(alive, min(2, len(alive))):
                e.rate_val[f'{sk.id}_{id(e)}'] = {'value': 10, 'rounds': 1}
                e.add_state('activeDamageAdd', 30, 1, sk, hero)
        hero.add_hook('行动时', f'{sk.id}_act', sub, sk, hero)


# ─── 200979 及锋而试
class Skill200979(Skill):
    def __init__(self):
        super().__init__(200979,'及锋而试','对敌军群体攻击(120%)并使其士气降低10每次发动伤害率+40%',2,35,False,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        hero.storage = getattr(hero, 'storage', {})
        extra = hero.storage.get(str(sk.id), 0)
        rate = calc_skill_addition_rate(120 + extra, 1.1, hero.attrs['atk'])
        for t in hero.get_target(sk.limit, 2):
            t.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
        hero.storage[str(sk.id)] = extra + 40
        return True


# ─── 200980 乘胜追击
class Skill200980(Skill):
    def __init__(self):
        super().__init__(200980,'乘胜追击','普通攻击后追击(150%)60%几率再次追击(100%)每次降低20%',3,35,False,0)
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
                tgs = hero.get_target(sk.limit, 1)
                if tgs and not tgs[0].is_confusion():
                    tgs[0].state['confusion'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
            if get_random_bool(hero.get_real_skill_rate(50)):
                enemy = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
                for t in [e for e in enemy if e.arms > 0 and e.is_confusion()]:
                    t.be_hurt(hero, {'type': 2, 'rate': v}, sk)
        hero.add_hook('行动前', f'{sk.id}_act', sub, sk, hero)


# ─── 200984 怀德畏威
class Skill200984(Skill):
    def __init__(self):
        super().__init__(200984,'怀德畏威','令谋略最低友军单体攻击敌军(160%)；自身对敌军2目标谋略攻击(160%)；目标相同则混乱1回合',2,40,False,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        r1 = calc_skill_addition_rate(160, 1.5, hero.attrs['atk'])
        r2 = calc_skill_addition_rate(160, 1.5, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        alive = [e for e in team if e.arms > 0 and e is not hero]
        low = min(alive, key=lambda e: e.attrs['int']) if alive else None
        t1 = None
        if low:
            et = hero.get_target(sk.limit, 1)
            if et:
                t1 = et[0]
                t1.be_hurt(low, {'type': 1, 'rate': r1}, sk)
        t2s = []
        for t in hero.get_target(sk.limit, 2):
            t.be_hurt(hero, {'type': 2, 'rate': r2}, sk)
            t2s.append(t)
        if t1 and t2s and all(x is t1 for x in t2s) and not t1.is_confusion():
            t1.state['confusion'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        return True


# ─── 200985 谋议宏图
class Skill200985(Skill):
    def __init__(self):
        super().__init__(200985,'谋议宏图','首回合我军全体受伤降低30%每回合-1/8；我军全体每回合士气+8',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(30, 0.22, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('beAttackDamageSub', v, -1, sk, hero)
            e.add_state('beInteDamageSub', v, -1, sk, hero)
        step = keep_two_decimal(v / 8)
        def on_rnd():
            for e in team:
                if e.arms > 0:
                    e.add_state('beAttackDamageSub', -step, -1, sk, hero, True)
                    e.add_state('beInteDamageSub', -step, -1, sk, hero, True)
        hero.add_hook('回合开始时', f'{sk.id}_decay', on_rnd, sk, hero)


# ─── 200986 盛气横凌
class Skill200986(Skill):
    def __init__(self):
        super().__init__(200986,'盛气横凌','普通攻击后对目标猛攻(260%)；士气高昂则混乱1回合；一般/低落则额外攻击(80%-160%)',3,50,False,0)
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
            target.be_hurt(hero, {'type': 1, 'rate': get_random_int(80, 160)}, sk)


# ─── 200987 三军夺帅
class Skill200987(Skill):
    def __init__(self):
        super().__init__(200987,'三军夺帅','每次成功发动普攻/主动/追击后攻击敌军单体(180%)自身攻击+10或谋略攻击敌军2目标(100%)谋略-5',4,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        atk_r = calc_skill_addition_rate(180, 1.7, hero.attrs['atk'])
        int_r = calc_skill_addition_rate(100, 0.9, hero.attrs['int'])
        def on_dmg(t, di, s2, dmg):
            if random.random() < 0.5:
                tgs = hero.get_target(sk.limit, 1)
                if tgs: tgs[0].be_hurt(hero, {'type': 1, 'rate': atk_r}, sk)
                hero.attrs['atk'] = keep_two_decimal(hero.attrs['atk'] + 10)
            else:
                for tt in hero.get_target(sk.limit, 2):
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
        super().__init__(201006,'同仇敌忾','我军全体每次受伤后距离1以内友军受伤-2%且主动战法伤害+2%最多叠加10次',1,'--',False,3)
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


# ──────────────── SKILLS_S 注册表 ────────────────
SKILLS_S = {
    200003: Skill200003(),
    200010: Skill200010(),
    200012: Skill200012(),
    200013: Skill200013(),
    200014: Skill200014(),
    200015: Skill200015(),
    200016: Skill200016(),
    200021: Skill200021(),
    200023: Skill200023(),
    200024: Skill200024(),
    200027: Skill200027(),
    200030: Skill200030(),
    200070: Skill200070(),
    200235: Skill200235(),
    200237: Skill200237(),
    200246: Skill200246(),
    200251: Skill200251(),
    200252: Skill200252(),
    200254: Skill200254(),
    200255: Skill200255(),
    200257: Skill200257(),
    200262: Skill200262(),
    200264: Skill200264(),
    200268: Skill200268(),
    200603: Skill200603(),
    200622: Skill200622(),
    200647: Skill200647(),
    200648: Skill200648(),
    200687: Skill200687(),
    200692: Skill200692(),
    200693: Skill200693(),
    200694: Skill200694(),
    200715: Skill200715(),
    200737: Skill200737(),
    200755: Skill200755(),
    200757: Skill200757(),
    200769: Skill200769(),
    200773: Skill200773(),
    200800: Skill200800(),
    200801: Skill200801(),
    200805: Skill200805(),
    200824: Skill200824(),
    200828: Skill200828(),
    200847: Skill200847(),
    200863: Skill200863(),
    200882: Skill200882(),
    200884: Skill200884(),
    200886: Skill200886(),
    200898: Skill200898(),
    200899: Skill200899(),
    200900: Skill200900(),
    200915: Skill200915(),
    200938: Skill200938(),
    200942: Skill200942(),
    200952: Skill200952(),
    200955: Skill200955(),
    200957: Skill200957(),
    200960: Skill200960(),
    200961: Skill200961(),
    200966: Skill200966(),
    200979: Skill200979(),
    200980: Skill200980(),
    200983: Skill200983(),
    200984: Skill200984(),
    200985: Skill200985(),
    200986: Skill200986(),
    200987: Skill200987(),
    200991: Skill200991(),
    200993: Skill200993(),
    201006: Skill201006(),
}
"""

with open('D:/nettest/battle_sim/skills_s.py', 'a', encoding='utf-8') as f:
    f.write(REMAINING)
print('done, appended', len(REMAINING.splitlines()), 'lines')


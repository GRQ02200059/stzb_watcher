# -*- coding: utf-8 -*-
# S级战法实现 (200xxx系列)
from .calc import calc_attack_damage, calc_inte_damage, calc_inte2_damage, calc_recover, calc_skill_addition_rate, get_random_bool, get_random_int, keep_two_decimal
from .skills import Skill
import random


# ─── 200003 金吾飞将
class Skill200003(Skill):
    def __init__(self):
        super().__init__(200003,'金吾飞将','对混乱或暴走状态敌军单体猛攻(275%)；对随机敌军单体猛攻(275%)并使其混乱2回合',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        enemy = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
        rate = calc_skill_addition_rate(275, 2.8, hero.attrs['atk'])
        # 第一段：优先攻击混乱或暴走的敌军
        chaos_targets = [e for e in enemy if e.arms > 0 and (e.is_confusion() or e.state.get('confusion', {}).get('rounds', 0) > 0)]
        if chaos_targets:
            t1 = random.choice(chaos_targets)
            t1.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
        # 第二段：随机单体并附加混乱
        alive = [e for e in enemy if e.arms > 0]
        if alive:
            t2 = random.choice(alive)
            t2.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
            if not t2.is_confusion():
                t2.state['confusion'] = {'rounds': 2, 'from': {'hero': hero, 'skill': sk}}
                hero.manager.log_action(hero, t2, f'【{sk.name}】使', '陷入混乱2回合', 1)
        return True


# ─── 200010 天下无双
class Skill200010(Skill):
    def __init__(self):
        super().__init__(200010,'天下无双','攻击+45，攻击距离+2，进入洞察，受普攻后反击(200%)并挑衅全体，持续2回合',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        atk_add = calc_skill_addition_rate(45, 0.45, hero.attrs['atk'])
        hero.attrs['atk'] = keep_two_decimal(hero.attrs['atk'] + atk_add)
        hero.attrs['limit'] = keep_two_decimal(hero.attrs.get('limit', 5) + 2)
        # 洞察：免疫控制
        hero.state['insight'] = {'rounds': 2, 'from': {'hero': hero, 'skill': sk}}
        counter_rate = calc_skill_addition_rate(200, 2.0, hero.attrs['atk'])
        enemy = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
        rounds_left = [2]
        def on_hurt(attacker, dmg_info, skill):
            if rounds_left[0] <= 0: return
            if dmg_info.get('type') == 1 and skill is None:
                attacker.be_hurt(hero, {'type': 1, 'rate': counter_rate}, sk)
                for e in enemy:
                    if e.arms > 0:
                        e.state['taunt'] = {'rounds': 2, 'target': hero, 'from': {'hero': hero, 'skill': sk}}
        def on_round_start():
            rounds_left[0] -= 1
            if rounds_left[0] <= 0:
                hero.clear_hook('受伤时', f'{sk.id}_counter')
                hero.clear_hook('回合开始时', f'{sk.id}_rnd')
        hero.add_hook('受伤时', f'{sk.id}_counter', on_hurt, sk, hero)
        hero.add_hook('回合开始时', f'{sk.id}_rnd', on_round_start, sk, hero)
        return True


# ─── 200012 辕门射戟
class Skill200012(Skill):
    def __init__(self):
        super().__init__(200012,'辕门射戟','对敌军群体发动2次无视相克攻击(120%)，首次受击者攻击伤害大幅降低1回合',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate = calc_skill_addition_rate(120, 1.1, hero.attrs['atk'])
        first_hit = [None]
        for _ in range(2):
            targets = hero.get_target(sk.limit, 2)
            for t in targets:
                t.be_hurt(hero, {'type': 1, 'rate': rate, 'ignore_arms': True}, sk)
                if first_hit[0] is None:
                    first_hit[0] = t
                    t.add_state('attackDamageSub', 9999, 1, sk, hero)
        return True


# ─── 200013 血溅黄砂
class Skill200013(Skill):
    def __init__(self):
        super().__init__(200013,'血溅黄砂','无法发动主动战法，使自身攻击伤害提高120%',4,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero, f'的战法【{self.name}】生效')
        sk = self
        hero.state['activeLimit'] = {'rounds': -1, 'from': {'hero': hero, 'skill': sk}}
        hero.add_state('attackDamageAdd', 120, -1, sk, hero)


# ─── 200014 酒池肉林
class Skill200014(Skill):
    def __init__(self):
        super().__init__(200014,'酒池肉林','前2回合我军全体受伤降低32%，第4回合起攻击伤害恢复35%兵力',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        sv = calc_skill_addition_rate(32, 0.28, hero.attrs['def'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('beAttackDamageSub', sv, 2, sk, hero)
            e.add_state('beInteDamageSub', sv, 2, sk, hero)
        def on_action():
            if hero.manager.round >= 4:
                hero.clear_hook('行动时', f'{sk.id}_w')
                def on_dmg(t, di, sk2, dmg):
                    if di.get('type') == 1:
                        hero.recover(int(dmg * 0.35), hero, sk.name)
                hero.add_hook('造成伤害后', f'{sk.id}_d', on_dmg, sk, hero)
        hero.add_hook('行动时', f'{sk.id}_w', on_action, sk, hero)


# ─── 200015 洛水佳人
class Skill200015(Skill):
    def __init__(self):
        super().__init__(200015,'洛水佳人','我军全体每回合80%几率恢复兵力(恢复率90%)',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rv = calc_skill_addition_rate(90, 0.8, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            def ms(e=e):
                def sub():
                    if get_random_bool(hero.get_real_skill_rate(80)):
                        e.recover(calc_recover(hero, rv, 0.8, 2), hero, sk.name)
                e.add_hook('回合开始时', f'{sk.id}_{id(e)}', sub, sk, hero)
            ms()


# ─── 200016 皇裔流离 -> 复用 Skill1007
class Skill200016(Skill):
    def __init__(self):
        super().__init__(200016,'皇裔流离','我军全体受到伤害时，有50%几率恢复兵力(恢复率68%)',1,'--',True,3)
    def call(self, hero, target=None):
        from .skills import Skill1007
        obj = Skill1007(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200021 银龙冲阵
class Skill200021(Skill):
    def __init__(self):
        super().__init__(200021,'银龙冲阵','随机对敌军单体发动2次攻击(150%)，首次受击者受伤提高20%，持续2回合',2,50,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate = calc_skill_addition_rate(150, 1.4, hero.attrs['atk'])
        debuff_val = calc_skill_addition_rate(20, 0.15, hero.attrs['atk'])
        first_hit = [None]
        for _ in range(2):
            targets = hero.get_target(sk.limit, 1)
            if not targets: break
            t = targets[0]
            t.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
            if first_hit[0] is None:
                first_hit[0] = t
                t.add_state('beAttackDamageAdd', debuff_val, 2, sk, hero)
                t.add_state('beInteDamageAdd', debuff_val, 2, sk, hero)
        return True


# ─── 200023 魏武之世 -> 复用 Skill1028
class Skill200023(Skill):
    def __init__(self):
        super().__init__(200023,'魏武之世','使敌军全体属性下降15%，我军全体攻击距离+1',1,'--',True,5)
    def call(self, hero, target=None):
        from .skills import Skill1028
        obj = Skill1028(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200024 驱虎吞狼
class Skill200024(Skill):
    def __init__(self):
        super().__init__(200024,'驱虎吞狼','对敌军全体谋略攻击(153%)并使其无法恢复兵力2回合，已中状态者暴走1回合',2,30,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate = calc_skill_addition_rate(153, 1.4, hero.attrs['int'])
        enemy = hero.get_target(sk.limit, 3)
        for e in enemy:
            already = e.state.get('noRecover', {}).get('rounds', 0) > 0
            e.be_hurt(hero, {'type': 2, 'rate': rate}, sk)
            if already:
                if not e.is_confusion():
                    e.state['confusion'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
            else:
                e.state['noRecover'] = {'rounds': 2, 'from': {'hero': hero, 'skill': sk}}
        return True


# ─── 200027 其疾如风 -> 复用 Skill1008
class Skill200027(Skill):
    def __init__(self):
        super().__init__(200027,'其疾如风','前3回合我军全体速度+41，每回合70%几率连击',1,'--',True,3)
    def call(self, hero, target=None):
        from .skills import Skill1008
        obj = Skill1008(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200030 九锡黄龙
class Skill200030(Skill):
    def __init__(self):
        super().__init__(200030,'九锡黄龙','移除我军全体有害效果，并使其进入规避状态，免疫接下来受到的2次伤害',2,30,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            hero.manager.log(e, '移除有害效果并进入规避', 1)
            e.clear_debuff(sk, hero)
            # 规避：免疫2次伤害
            immune_count = [2]
            tag = f'{sk.id}_dodge_{id(e)}'
            def make_dodge(e=e, immune_count=immune_count, tag=tag):
                def on_hurt(attacker, dmg_info, skill):
                    if immune_count[0] > 0:
                        dmg_info['immune'] = True
                        immune_count[0] -= 1
                        hero.manager.log(e, f'规避了伤害（剩余{immune_count[0]}次）', 1)
                        if immune_count[0] <= 0:
                            e.clear_hook('受伤时', tag)
                e.add_hook('受伤时', tag, on_hurt, sk, hero)
            make_dodge()
        return True


# ─── 200070 巾帼战阵
class Skill200070(Skill):
    def __init__(self):
        super().__init__(200070,'巾帼战阵','自身攻击伤害提高40%，对敌军群体主动战法攻击(120%)，无法普攻',2,120,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        atk_add = calc_skill_addition_rate(40, 0.3, hero.attrs['atk'])
        hero.add_state('attackDamageAdd', atk_add, -1, sk, hero)
        hero.state['attackLimit'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        rate = calc_skill_addition_rate(120, 1.1, hero.attrs['atk'])
        targets = hero.get_target(sk.limit, 2)
        for t in targets:
            t.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
        return True


# ─── 200235 浑水摸鱼 -> 复用 Skill1014
class Skill200235(Skill):
    def __init__(self):
        super().__init__(200235,'浑水摸鱼','1回合准备，使敌军群体陷入混乱2回合',2,35,True,4)
    def call(self, hero, target=None):
        from .skills import Skill1014
        obj = Skill1014(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200237 妖术
class Skill200237(Skill):
    def __init__(self):
        super().__init__(200237,'妖术','1回合准备，使敌军群体陷入暴走状态2回合',2,40,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        def subskill():
            targets = hero.get_target(sk.limit, 2)
            for t in targets:
                if not t.is_confusion():
                    t.state['confusion'] = {'rounds': 2, 'from': {'hero': hero, 'skill': sk}}
                    hero.manager.log(t, '陷入暴走2回合', 1)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200246 缓师徐持
class Skill200246(Skill):
    def __init__(self):
        super().__init__(200246,'缓师徐持','每回合已行动敌军受伤后四维下降16，可叠加',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        sv = calc_skill_addition_rate(16, 0.12, hero.attrs['int'])
        et = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
        for e in et:
            def ms(e=e):
                acted = [False]
                def oa(): acted[0] = True
                def oh(a, di, s):
                    if acted[0]:
                        for k in ('atk', 'def', 'int', 'spd'):
                            e.attrs[k] = keep_two_decimal(max(0, e.attrs[k] - sv))
                def rst(): acted[0] = False
                e.add_hook('行动时', f'{sk.id}_a_{id(e)}', oa, sk, hero)
                e.add_hook('受伤时', f'{sk.id}_h_{id(e)}', oh, sk, hero)
                e.add_hook('回合开始时', f'{sk.id}_r_{id(e)}', rst, sk, hero)
            ms()


# ─── 200251 烈火焚舟
class Skill200251(Skill):
    def __init__(self):
        super().__init__(200251,'烈火焚舟','普通攻击后使目标燃烧(150%,2回合)；若已有燃烧则引爆并使相邻敌军燃烧(270%,1回合)',3,120,True,0)
    def call(self, hero, target=None):
        if target is None: return
        sk = self
        fire_rate1 = calc_skill_addition_rate(150, 1.35, hero.attrs['int'])
        fire_rate2 = calc_skill_addition_rate(270, 2.5, hero.attrs['int'])
        tag = f'{sk.id}_fire_{id(target)}'
        existing = target.state.get('dot_' + str(sk.id))
        if existing:
            # 引爆剩余燃烧
            target.be_hurt(hero, {'type': 4, 'rate': fire_rate2}, sk)
            enemy = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
            idx = enemy.index(target) if target in enemy else -1
            for nb in ([enemy[idx-1]] if idx > 0 else []) + ([enemy[idx+1]] if idx >= 0 and idx < len(enemy)-1 else []):
                if nb.arms > 0:
                    nb.be_hurt(hero, {'type': 4, 'rate': fire_rate2}, sk)
        else:
            target.be_hurt(hero, {'type': 4, 'rate': fire_rate1}, sk)


# ─── 200252 百战无怯
class Skill200252(Skill):
    def __init__(self):
        super().__init__(200252,'百战无怯','中军/前锋时：造成伤害后受伤降低15%最多4层；已4层时每回合起失去1层并恢复兵力(200%)',4,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        if hero.pos_name not in ('中军', '前锋'): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        layer = [0]
        max_layer = 4
        sub_per = calc_skill_addition_rate(15, 0.12, hero.attrs['def'])
        rv = calc_skill_addition_rate(200, 1.8, hero.attrs['int'])
        def on_dmg_done(t, di, s2, dmg):
            if layer[0] < max_layer:
                layer[0] += 1
                hero.add_state('beAttackDamageSub', sub_per, -1, sk, hero, True)
                hero.add_state('beInteDamageSub', sub_per, -1, sk, hero, True)
        def on_round():
            if layer[0] >= max_layer:
                layer[0] -= 1
                hero.recover(calc_recover(hero, rv, 1.8, 2), hero, sk.name)
        hero.add_hook('造成伤害后', f'{sk.id}_layer', on_dmg_done, sk, hero)
        hero.add_hook('回合开始时', f'{sk.id}_rnd', on_round, sk, hero)


# ─── 200254 衔命建功 -> 复用 Skill1030
class Skill200254(Skill):
    def __init__(self):
        super().__init__(200254,'衔命建功','敌军每回合首次受持续伤害时50%谋略攻击(140%)；第3回合起额外触发持续伤害',1,'--',True,5)
    def call(self, hero, target=None):
        from .skills import Skill1030
        obj = Skill1030(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200255 伏波扬砂
class Skill200255(Skill):
    def __init__(self):
        super().__init__(200255,'伏波扬砂','我军全体普攻伤害提升25%，累积4层扬砂后触发连击',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(25, 0.18, hero.attrs['atk'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('attackDamageAdd', v, -1, sk, hero)
        stk = [0]
        def od(t, di, s2, dmg):
            stk[0] += 1
            if stk[0] >= 4:
                stk[0] = 0
                if hero.state.get('doubleAttack', {}).get('rounds', 0) <= 0:
                    hero.state['doubleAttack'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        hero.add_hook('造成伤害后', f'{sk.id}_stk', od, sk, hero)


# ─── 200257 审时定计
class Skill200257(Skill):
    def __init__(self):
        super().__init__(200257,'审时定计','敌军被施加负面效果前50%提升受伤30%并恢复我军单体兵力；我军被施加负面效果时50%抵御',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(30, 0.2, hero.attrs['int'])
        rv = calc_skill_addition_rate(65, 0.6, hero.attrs['int'])
        et = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in et:
            def me(e=e):
                def od(s2, src):
                    if get_random_bool(hero.get_real_skill_rate(50)):
                        e.add_state('beAttackDamageAdd', v, 1, sk, hero)
                        e.add_state('beInteDamageAdd', v, 1, sk, hero)
                        alive = [x for x in team if x.arms > 0]
                        if alive:
                            random.choice(alive).recover(calc_recover(hero, rv, 0.6, 2), hero, sk.name)
                e.add_hook('被施加负面效果时', f'{sk.id}_e_{id(e)}', od, sk, hero)
            me()
        for e in team:
            def mt(e=e):
                def od2(s2, src):
                    if get_random_bool(hero.get_real_skill_rate(50)):
                        return 'block'
                e.add_hook('被施加负面效果时', f'{sk.id}_t_{id(e)}', od2, sk, hero)
            mt()


# ─── 200262 僭号天子
class Skill200262(Skill):
    def __init__(self):
        super().__init__(200262,'僭号天子','我军全体受伤的32%由玉玺承担；第2回合起每回合玉玺对自身造成上回合承担伤害(80%起每回合+5%)',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        sr = calc_skill_addition_rate(32, 0.28, hero.attrs['def'])
        shield = [0]
        ratio = [0.8]
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        for e in team:
            def ms(e=e):
                def oh(a, di, s):
                    dmg = di.get('damage', 0)
                    shield[0] += int(dmg * sr / 100)
                e.add_hook('受伤时', f'{sk.id}_{id(e)}', oh, sk, hero)
            ms()
        def ors():
            if hero.manager.round >= 2 and shield[0] > 0:
                dmg = int(shield[0] * ratio[0])
                shield[0] = 0
                ratio[0] = min(1.0, ratio[0] + 0.05)
                hero.be_hurt_by_num(hero, {'type': 1, 'rate': 100}, sk, dmg, lambda: None)
        hero.add_hook('回合开始时', f'{sk.id}_ref', ors, sk, hero)


# ─── 200264 疲兵沮意
class Skill200264(Skill):
    def __init__(self):
        super().__init__(200264,'疲兵沮意','每回合我军群体叠加2层避锐(每层减伤10%)，生效后30%几率燃烧敌军(150%)',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        sub_per = calc_skill_addition_rate(10, 0.08, hero.attrs['int'])
        fire_rate = calc_skill_addition_rate(150, 1.2, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        stacks = {id(e): 0 for e in team}
        for e in team:
            def ms(e=e):
                def ors():
                    stacks[id(e)] = min(stacks[id(e)] + 2, 10)
                def oh(a, di, s):
                    if stacks[id(e)] > 0:
                        stacks[id(e)] -= 1
                        di['rate'] = di.get('rate', 100) * (1 - sub_per / 100)
                        if get_random_bool(hero.get_real_skill_rate(30)) and a and a.arms > 0:
                            a.be_hurt(hero, {'type': 4, 'rate': fire_rate}, sk)
                e.add_hook('回合开始时', f'{sk.id}_s_{id(e)}', ors, sk, hero)
                e.add_hook('受伤时', f'{sk.id}_h_{id(e)}', oh, sk, hero)
            ms()


# ─── 200268 忠克猛烈 -> 复用 Skill1011
class Skill200268(Skill):
    def __init__(self):
        super().__init__(200268,'忠克猛烈','无视防御对敌军单体攻击(280%)，犹豫1回合，下回合行动前反击(80%，最多3次)',2,50,True,4)
    def call(self, hero, target=None):
        from .skills import Skill1011
        obj = Skill1011(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200603 白楼独舞
class Skill200603(Skill):
    def __init__(self):
        super().__init__(200603,'白楼独舞','前3回合敌军群体攻谋伤害降低26%，结束后暴走2回合',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(26, 0.15, hero.attrs['int'])
        enemy = hero.get_target(sk.limit, 2)
        for e in enemy:
            e.add_state('attackDamageSub', v, 3, sk, hero)
            e.add_state('inteDamageSub', v, 3, sk, hero)
        def rage():
            if hero.manager.round == 4:
                hero.clear_hook('回合开始时', f'{sk.id}_rage')
                for e in enemy:
                    if e.arms > 0 and not e.is_confusion():
                        e.state['confusion'] = {'rounds': 2, 'from': {'hero': hero, 'skill': sk}}
        hero.add_hook('回合开始时', f'{sk.id}_rage', rage, sk, hero)


# ─── 200622 不攻
class Skill200622(Skill):
    def __init__(self):
        super().__init__(200622,'不攻','无法普攻，谋略伤害+25%，每回合对敌军单体谋略攻击(83%)',1,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        hero.state['attackLimit'] = {'rounds': -1, 'from': {'hero': hero, 'skill': sk}}
        va = calc_skill_addition_rate(25, 0.18, hero.attrs['int'])
        hero.add_state('inteDamageAdd', va, -1, sk, hero)
        vb = calc_skill_addition_rate(83, 0.75, hero.attrs['int'])
        def sub():
            targets = hero.get_target(5, 1)
            if targets:
                targets[0].be_hurt(hero, {'type': 2, 'rate': vb}, sk)
        hero.add_hook('行动时', f'{sk.id}_atk', sub, sk, hero)


# ─── 200647 一骑当千 -> 复用 Skill1026
class Skill200647(Skill):
    def __init__(self):
        super().__init__(200647,'一骑当千','1回合准备，对敌军全体发动一次猛烈攻击(280%)',2,30,True,5)
    def call(self, hero, target=None):
        from .skills import Skill1026
        obj = Skill1026(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200648 白衣渡江 -> 复用 Skill1021
class Skill200648(Skill):
    def __init__(self):
        super().__init__(200648,'白衣渡江','前2回合敌军群体无法普攻，结束后强力谋略攻击(215%)',1,'--',True,5)
    def call(self, hero, target=None):
        from .skills import Skill1021
        obj = Skill1021(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200687 始计 -> 复用 Skill1013
class Skill200687(Skill):
    def __init__(self):
        super().__init__(200687,'始计','前4回合我军大营攻谋伤害提高20%，敌方兵力最多单体伤害降低30%，自身受伤后洞察',1,'--',True,5)
    def call(self, hero, target=None):
        from .skills import Skill1013
        obj = Skill1013(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200692 奇佐鬼谋
class Skill200692(Skill):
    def __init__(self):
        super().__init__(200692,'奇佐鬼谋','自身和友军单体谋略+22%，并使敌军群体随机陷入混乱/暴走/怯战/犹豫之一，持续2回合',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        int_add = calc_skill_addition_rate(22, 0.18, hero.attrs['int'])
        hero.attrs['int'] = keep_two_decimal(hero.attrs['int'] * (1 + int_add / 100))
        allies = hero.get_target(5, 1, 3)
        if allies:
            allies[0].attrs['int'] = keep_two_decimal(allies[0].attrs['int'] * (1 + int_add / 100))
        states = ['confusion', 'confusion', 'attackLimit', 'activeLimit']
        enemy = hero.get_target(sk.limit, 2)
        for e in enemy:
            st = random.choice(states)
            if not e.state.get(st, {}).get('rounds', 0) > 0:
                e.state[st] = {'rounds': 2, 'from': {'hero': hero, 'skill': sk}}
        return True


# ─── 200693 密谋定蜀
class Skill200693(Skill):
    def __init__(self):
        super().__init__(200693,'密谋定蜀','敌军群体攻谋伤害降低30%，陷入恐慌(115%)，每次追击额外受妖术诅咒(133%)，持续2回合',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        sub_v = calc_skill_addition_rate(30, 0.2, hero.attrs['int'])
        panic_r = calc_skill_addition_rate(115, 1.0, hero.attrs['int'])
        curse_r = calc_skill_addition_rate(133, 1.2, hero.attrs['int'])
        enemy = hero.get_target(sk.limit, 2)
        for e in enemy:
            e.add_state('attackDamageSub', sub_v, 2, sk, hero)
            e.add_state('inteDamageSub', sub_v, 2, sk, hero)
            e.be_hurt(hero, {'type': 2, 'rate': panic_r}, sk)
            def mc(e=e):
                rounds_left = [2]
                def on_chase(t, di, s2, dmg):
                    if rounds_left[0] > 0:
                        e.be_hurt(hero, {'type': 2, 'rate': curse_r}, sk)
                def on_rnd(): rounds_left[0] -= 1
                e.add_hook('造成伤害后', f'{sk.id}_curse_{id(e)}', on_chase, sk, hero)
                e.add_hook('回合开始时', f'{sk.id}_cr_{id(e)}', on_rnd, sk, hero)
            mc()
        return True


# ─── 200694 火势风威 -> 复用 Skill1029
class Skill200694(Skill):
    def __init__(self):
        super().__init__(200694,'火势风威','1回合准备，对敌军全体谋略攻击(111%)，下次受伤额外触发燃烧(221%)',2,40,True,5)
    def call(self, hero, target=None):
        from .skills import Skill1029
        obj = Skill1029(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200715 十面埋伏
class Skill200715(Skill):
    def __init__(self):
        super().__init__(200715,'十面埋伏','1回合准备，对敌军全体谋略攻击(130%)，并使随机敌军单体伤害大幅降低1回合',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        rate = calc_skill_addition_rate(130, 1.2, hero.attrs['int'])
        def subskill():
            targets = hero.get_target(sk.limit, 3)
            for t in targets:
                t.be_hurt(hero, {'type': 2, 'rate': rate}, sk)
            alive = [t for t in targets if t.arms > 0]
            if alive:
                chosen = random.choice(alive)
                chosen.add_state('attackDamageSub', 9999, 1, sk, hero)
                chosen.add_state('inteDamageSub', 9999, 1, sk, hero)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200737 明其虚实
class Skill200737(Skill):
    def __init__(self):
        super().__init__(200737,'明其虚实','敌军全体谋略每回合降低6%，前2回合敌军群体犹豫',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        et = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
        for e in et:
            if hero.manager.round <= 2 and not e.is_active_limit():
                e.state['activeLimit'] = {'rounds': 2, 'from': {'hero': hero, 'skill': sk}}
        def on_rnd():
            for e in et:
                e.attrs['int'] = keep_two_decimal(e.attrs['int'] * 0.94)
        hero.add_hook('回合开始时', f'{sk.id}_int', on_rnd, sk, hero)


# ─── 200755 攻其不备
class Skill200755(Skill):
    def __init__(self):
        super().__init__(200755,'攻其不备','敌军群体每受一次攻击伤害受伤提高11.6%，最多叠加5次',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(11.6, 0.09, hero.attrs['spd'])
        et = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
        for e in et:
            def ms(e=e):
                stk = [0]
                def oh(a, di, s):
                    if di.get('type') == 1 and stk[0] < 5:
                        stk[0] += 1
                        e.add_state('beAttackDamageAdd', v, -1, sk, hero, True)
                        e.add_state('beInteDamageAdd', v, -1, sk, hero, True)
                e.add_hook('受伤时', f'{sk.id}_{id(e)}', oh, sk, hero)
            ms()


# ─── 200757 重整旗鼓
class Skill200757(Skill):
    def __init__(self):
        super().__init__(200757,'重整旗鼓','第5回合起每回合恢复我军群体兵力(恢复率140%)',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rv = calc_skill_addition_rate(140, 1.2, hero.attrs['int'])
        def sub():
            if get_random_bool(rate_dmg[0]):
                if use_atk:
                    hero.add_state('attackDamageAdd', dmg_v, 1, sk, hero)
                else:
                    hero.add_state('inteDamageAdd', dmg_v, 1, sk, hero)
            rate_dmg[0] = max(0, rate_dmg[0] - 10)
            if get_random_bool(rate_pen[0]):
                if use_atk:
                    hero.add_state('ignoreDefRate', 60, 1, sk, hero)
                else:
                    hero.add_state('ignoreIntRate', 60, 1, sk, hero)
            rate_pen[0] = min(100, rate_pen[0] + 10)
        hero.add_hook('行动时', f'{sk.id}_act', sub, sk, hero)


# ─── 200938 三术奇谋
class Skill200938(Skill):
    def __init__(self):
        super().__init__(200938,'三术奇谋','1回合准备，对敌军单体3次谋略攻击(178%)，依次使攻/防/谋降低18，持续2回合',2,50,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        rate = calc_skill_addition_rate(178, 1.65, hero.attrs['int'])
        sub_v = calc_skill_addition_rate(18, 0.14, hero.attrs['int'])
        def subskill():
            for _ in range(3):
                targets = hero.get_target(sk.limit, 1)
                if not targets: break
                t = targets[0]
                t.be_hurt(hero, {'type': 2, 'rate': rate}, sk)
            targets = hero.get_target(sk.limit, 1)
            if targets:
                t = targets[0]
                for key in ('atk', 'def', 'int'):
                    orig = t.attrs[key]
                    t.attrs[key] = keep_two_decimal(max(0, t.attrs[key] - sub_v))
                    rounds_left = [2]
                    def restore(t=t, key=key, orig=orig, orig_sub=sub_v, rl=rounds_left):
                        def inner():
                            rl[0] -= 1
                            if rl[0] <= 0:
                                t.attrs[key] = keep_two_decimal(t.attrs[key] + orig_sub)
                                t.clear_hook('回合开始时', f'{sk.id}_{key}_{id(t)}')
                        t.add_hook('回合开始时', f'{sk.id}_{key}_{id(t)}', inner, sk, hero)
                    restore()
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200942 暴戾恣睢
class Skill200942(Skill):
    def __init__(self):
        super().__init__(200942,'暴戾恣睢','攻击伤害提高4%，每回合额外叠加；每回合行动时20%几率对敌全体攻击(150%)，几率每回合+5%',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        hero.add_state('attackDamageAdd', 4, -1, sk, hero)
        rage_rate = [20]
        rate_v = calc_skill_addition_rate(150, 1.4, hero.attrs['atk'])
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
        super().__init__(200952,'抚民励德','第2/4/6回合行动时我军全体谋略/防御+80并受伤-20%，持续2回合；造成伤害后效果降低1/4',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        attr_v = calc_skill_addition_rate(80, 0.7, hero.attrs['int'])
        dmg_v = calc_skill_addition_rate(20, 0.15, hero.attrs['int'])
        active_val = [0]
        def sub():
            rnd = hero.manager.round
            if rnd in (2, 4, 6):
                team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
                for e in team:
                    e.attrs['int'] = keep_two_decimal(e.attrs['int'] + attr_v)
                    e.attrs['def'] = keep_two_decimal(e.attrs['def'] + attr_v)
                    e.add_state('beAttackDamageSub', dmg_v, 2, sk, hero)
                    e.add_state('beInteDamageSub', dmg_v, 2, sk, hero)
                active_val[0] = dmg_v
        def on_dmg(t, di, s2, dmg):
            if active_val[0] > 0:
                active_val[0] = max(0, active_val[0] - dmg_v / 4)
        hero.add_hook('行动时', f'{sk.id}_act', sub, sk, hero)
        hero.add_hook('造成伤害后', f'{sk.id}_decay', on_dmg, sk, hero)


# ─── 200955 动如雷震
class Skill200955(Skill):
    def __init__(self):
        super().__init__(200955,'动如雷震','我军群体追击战法发动率提升100%，追击伤害提升40%，持续1回合',2,30,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        spd_v = calc_skill_addition_rate(40, 0.3, hero.attrs['spd'])
        team = hero.get_target(sk.limit, 2, 2)
        for e in team:
            e.add_state('chaseDamageAdd', spd_v, 1, sk, hero)
            e.rate_val[f'{sk.id}_chase'] = {'value': 100, 'rounds': 1}
        return True


# ─── 200957 五兵之烈
class Skill200957(Skill):
    def __init__(self):
        super().__init__(200957,'五兵之烈','对敌军群体猛烈攻击(280%)，根据宝物类型附加额外效果',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate = calc_skill_addition_rate(280, 2.6, hero.attrs['atk'])
        targets = hero.get_target(sk.limit, 2)
        for t in targets:
            t.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
        # 默认效果：目标防御降低30%
        sub_v = calc_skill_addition_rate(30, 0.25, hero.attrs['atk'])
        for t in targets:
            if t.arms > 0:
                t.attrs['def'] = keep_two_decimal(t.attrs['def'] * (1 - sub_v / 100))
        return True


# ─── 200960 鸾凤和鸣
class Skill200960(Skill):
    def __init__(self):
        super().__init__(200960,'鸾凤和鸣','每回合首次发动主动后我军群体2目标恢复(85%)；行动时我军全体3目标控制效果额外对1目标生效',1,'--',True,3)
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
        super().__init__(200961,'奋疾先登','每回合行动时攻击伤害提升8%，达40%时群体攻击并重置',1,'--',True,5)
    def call(self, hero, target=None):
        from .skills import Skill1009
        obj = Skill1009(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200966 尽言直谏
class Skill200966(Skill):
    def __init__(self):
        super().__init__(200966,'尽言直谏','每回合行动时随机令友方2个主动战法下次发动率+10%伤害+30%，若有发动则下回合选3个',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        def sub():
            team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
            for e in random.sample([x for x in team if x.arms > 0], min(2, len([x for x in team if x.arms > 0]))):
                e.rate_val[f'{sk.id}_{id(e)}'] = {'value': 10, 'rounds': 1}
                e.add_state('activeDamageAdd', 30, 1, sk, hero)
        hero.add_hook('行动时', f'{sk.id}_act', sub, sk, hero)


# ─── 200979 及锋而试
class Skill200979(Skill):
    def __init__(self):
        super().__init__(200979,'及锋而试','对敌军群体攻击(120%)并使其士气降低10，每次发动伤害率+40%',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        base = 120
        add = hero.storage.get(sk.id, {}).get('extra', 0) if hasattr(hero, 'storage') else 0
        rate = calc_skill_addition_rate(base + add, 1.1, hero.attrs['atk'])
        targets = hero.get_target(sk.limit, 2)
        for t in targets:
            t.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
            if hasattr(t, 'morale'):
                t.morale = max(0, t.morale - 10)
        hero.storage = getattr(hero, 'storage', {})
        hero.storage[sk.id] = {'extra': add + 40}
        return True


# ─── 200980 乘胜追击 -> 复用 Skill1037
class Skill200980(Skill):
    def __init__(self):
        super().__init__(200980,'乘胜追击','普通攻击后追击(150%)，60%几率再次追击(100%)，每次降低20%',3,35,True,0)
    def call(self, hero, target=None):
        from .skills import Skill1037
        obj = Skill1037(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200983 运筹决胜
class Skill200983(Skill):
    def __init__(self):
        super().__init__(200983,'运筹决胜','试图发动主动时30%使敌军单体暴走1回合；50%对混乱/暴走敌军全体谋略攻击(220%)',1,'--',True,5)
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



# ─── 200769 樊渊泅囚
class Skill200769(Skill):
    def __init__(self):
        super().__init__(200769,'樊渊泅囚','1回合准备，对敌军全体猛攻(190%)并使其犹豫1回合',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        rate = calc_skill_addition_rate(190, 1.8, hero.attrs['atk'])
        def subskill():
            targets = hero.get_target(sk.limit, 3)
            for t in targets:
                t.be_hurt(hero, {'type': 1, 'rate': rate}, sk)
                if not t.is_active_limit():
                    t.state['activeLimit'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200773 金匮要略 -> 复用 Skill1016
class Skill200773(Skill):
    def __init__(self):
        super().__init__(200773,'金匮要略','前3回合我军全体受伤降低20.4%，并有50%几率恢复兵力(80%)',1,'--',True,3)
    def call(self, hero, target=None):
        from .skills import Skill1016
        obj = Skill1016(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200800 众谋不懈
class Skill200800(Skill):
    def __init__(self):
        super().__init__(200800,'众谋不懈','每次试图发动主动/追击前40%几率对距离5敌军单体谋略攻击(194%)',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(194, 1.8, hero.attrs['int'])
        def sub():
            if get_random_bool(hero.get_real_skill_rate(40)):
                targets = hero.get_target(5, 1)
                if targets:
                    targets[0].be_hurt(hero, {'type': 2, 'rate': v}, sk)
        hero.add_hook('行动前', f'{sk.id}_atk', sub, sk, hero)


# ─── 200801 利兵谋胜
class Skill200801(Skill):
    def __init__(self):
        super().__init__(200801,'利兵谋胜','1回合准备，对敌军群体强力谋略攻击(200%)，并使自身及友军单体恢复兵力(149%)',2,50,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        atk_rate = calc_skill_addition_rate(200, 1.9, hero.attrs['int'])
        rec_rate = calc_skill_addition_rate(149, 1.3, hero.attrs['int'])
        def subskill():
            targets = hero.get_target(sk.limit, 2)
            for t in targets:
                t.be_hurt(hero, {'type': 2, 'rate': atk_rate}, sk)
            hero.recover(calc_recover(hero, rec_rate, 1.3, 2), hero, sk.name)
            allies = hero.get_target(sk.limit, 1, 3)
            if allies:
                allies[0].recover(calc_recover(hero, rec_rate, 1.3, 2), hero, sk.name)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 200805 迟智难酬
class Skill200805(Skill):
    def __init__(self):
        super().__init__(200805,'迟智难酬','对敌军群体谋略攻击(240%)，并使友军群体受下1次谋略攻击伤害大幅降低',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        rate = calc_skill_addition_rate(240, 2.2, hero.attrs['int'])
        enemy = hero.get_target(sk.limit, 2)
        for t in enemy:
            t.be_hurt(hero, {'type': 2, 'rate': rate}, sk)
        team = hero.get_target(3, 2, 2)
        for e in team:
            def me(e=e):
                tag = f'{sk.id}_shield_{id(e)}'
                def oh(a, di, s):
                    if di.get('type') == 2:
                        di['rate'] = di.get('rate', 100) * 0.01  # 大幅降低
                        e.clear_hook('受伤时', tag)
                e.add_hook('受伤时', tag, oh, sk, hero)
            me()
        return True


# ─── 200824 西陵克晋
class Skill200824(Skill):
    def __init__(self):
        super().__init__(200824,'西陵克晋','每回合50%几率使我军攻击最高武将攻击(150%)，谋略最高武将谋略攻击(150%)，并各自恢复兵力',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        atk_r = calc_skill_addition_rate(150, 1.4, hero.attrs['atk'])
        int_r = calc_skill_addition_rate(150, 1.4, hero.attrs['int'])
        rv = calc_skill_addition_rate(80, 0.7, hero.attrs['int'])
        def sub():
            if not get_random_bool(hero.get_real_skill_rate(50)): return
            team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
            alive = [e for e in team if e.arms > 0]
            if not alive: return
            atk_hero = max(alive, key=lambda e: e.attrs['atk'])
            int_hero = max(alive, key=lambda e: e.attrs['int'])
            t1 = hero.get_target(sk.limit, 1)
            t2 = hero.get_target(sk.limit, 1)
            if t1:
                t1[0].be_hurt(atk_hero, {'type': 1, 'rate': atk_r}, sk)
                atk_hero.recover(calc_recover(hero, rv, 0.7, 2), hero, sk.name)
            if t2:
                t2[0].be_hurt(int_hero, {'type': 2, 'rate': int_r}, sk)
                int_hero.recover(calc_recover(hero, rv, 0.7, 2), hero, sk.name)
        hero.add_hook('行动时', f'{sk.id}_act', sub, sk, hero)


# ─── 200828 难知如阴
class Skill200828(Skill):
    def __init__(self):
        super().__init__(200828,'难知如阴','每2回合使友军单体主战法发动率提高120%，若需准备则75%跳过1回合；第3回合起目标改为友军全体',1,'--',True,2)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        def sub():
            rnd = hero.manager.round
            if rnd % 2 != 1: return
            team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
            targets = team if rnd >= 3 else [random.choice([e for e in team if e.arms > 0])] if any(e.arms > 0 for e in team) else []
            for e in targets:
                e.rate_val[sk.id] = {'value': 120, 'rounds': 1}
                if get_random_bool(75) and hasattr(e, 'ready_skills') and e.ready_skills:
                    for rs in e.ready_skills:
                        rs['rounds'] = max(0, rs['rounds'] - 1)
        hero.add_hook('行动时', f'{sk.id}_buff', sub, sk, hero)


# ─── 200847 河内世泽
class Skill200847(Skill):
    def __init__(self):
        super().__init__(200847,'河内世泽','随机发动落雷/迷阵/溃堤/夹攻之一，再随机发动伐谋/雀伏/火辎/毒泉之一，跳过准备回合',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        # 简化实现：随机对敌群体发动两次伤害
        r1 = calc_skill_addition_rate(130, 1.1, hero.attrs['int'])
        r2 = calc_skill_addition_rate(100, 0.9, hero.attrs['int'])
        targets1 = hero.get_target(sk.limit, 2)
        for t in targets1:
            t.be_hurt(hero, {'type': 2, 'rate': r1}, sk)
        targets2 = hero.get_target(sk.limit, 2)
        for t in targets2:
            t.be_hurt(hero, {'type': 2, 'rate': r2}, sk)
        return True


# ─── 200863 击势
class Skill200863(Skill):
    def __init__(self):
        super().__init__(200863,'击势','每回合行动时65%几率攻击伤害提升50%并无视敌方60%防御，持续1回合',4,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        def sub():
            if get_random_bool(hero.get_real_skill_rate(65)):
                hero.add_state('attackDamageAdd', 50, 1, sk, hero)
            if get_random_bool(hero.get_real_skill_rate(65)):
                hero.add_state('ignoreDefRate', 60, 1, sk, hero)
        hero.add_hook('行动时', f'{sk.id}_act', sub, sk, hero)


# ─── 200882 谋谟帷幄
class Skill200882(Skill):
    def __init__(self):
        super().__init__(200882,'谋谟帷幄','自身及友军单体每回合首次发动主动战法时55%对敌军单体谋略攻击(171%)；我军大营/中军低于60%时额外攻击(76%)',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v1 = calc_skill_addition_rate(171, 1.6, hero.attrs['int'])
        v2 = calc_skill_addition_rate(76, 0.7, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp == 'blue' else hero.manager.red_team['heros']
        targets_list = [hero] + [e for e in team if e is not hero and e.arms > 0][:1]
        for e in targets_list:
            def ms(e=e):
                fired = [False]
                def on_rnd(): fired[0] = False
                def on_act():
                    if not fired[0]:
                        fired[0] = True
                        if get_random_bool(hero.get_real_skill_rate(55)):
                            t = hero.get_target(5, 1)
                            if t: t[0].be_hurt(hero, {'type': 2, 'rate': v1}, sk)
                        # 额外攻击判断
                        for ally in team:
                            if ally.pos_name in ('大营', '中军') and ally.arms > 0:
                                max_arms = getattr(ally, 'max_arms', ally.arms)
                                if ally.arms / max(max_arms, 1) < 0.6:
                                    t2 = hero.get_target(5, 1)
                                    if t2: t2[0].be_hurt(hero, {'type': 2, 'rate': v2}, sk)
                e.add_hook('行动前', f'{sk.id}_act_{id(e)}', on_act, sk, hero)
                e.add_hook('回合开始时', f'{sk.id}_rst_{id(e)}', on_rnd, sk, hero)
            ms()


# ─── 200884 巧音唤蝶
class Skill200884(Skill):
    def __init__(self):
        super().__init__(200884,'巧音唤蝶','对敌军群体谋略攻击(176%)并燃烧，兵力高于50%时额外受伤(86%)；我军群体恢复(161%)，低于50%时额外恢复(82%)',2,35,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        atk_r = calc_skill_addition_rate(176, 1.6, hero.attrs['int'])
        extra_r = calc_skill_addition_rate(86, 0.8, hero.attrs['int'])
        rec_r = calc_skill_addition_rate(161, 1.5, hero.attrs['int'])
        heal_r = calc_skill_addition_rate(82, 0.75, hero.attrs['int'])
        enemy = hero.get_target(sk.limit, 2)
        for t in enemy:
            t.be_hurt(hero, {'type': 4, 'rate': atk_r}, sk)
            max_arms = getattr(t, 'max_arms', t.arms)
            if t.arms / max(max_arms, 1) > 0.5:
                t.be_hurt(hero, {'type': 2, 'rate': extra_r}, sk)
        team = hero.get_target(3, 2, 2)
        for e in team:
            e.recover(calc_recover(hero, rec_r, 1.5, 2), hero, sk.name)
            max_arms = getattr(e, 'max_arms', e.arms)
            if e.arms / max(max_arms, 1) < 0.5:
                e.recover(calc_recover(hero, heal_r, 0.75, 2), hero, sk.name)
        return True


# ─── 200886 三军之众 -> 复用 Skill1027
class Skill200886(Skill):
    def __init__(self):
        super().__init__(200886,'三军之众','1回合准备，使我军单体恢复4次兵力(恢复率151%)',2,45,True,3)
    def call(self, hero, target=None):
        from .skills import Skill1027
        obj = Skill1027(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200898 京观垒冢
class Skill200898(Skill):
    def __init__(self):
        super().__init__(200898,'京观垒冢','造成伤害时70%几率额外攻击(200%)或谋略攻击(200%)',4,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        v = calc_skill_addition_rate(200, 1.9, hero.attrs['atk'])
        def on_dmg(t, di, s2, dmg):
            if get_random_bool(hero.get_real_skill_rate(70)):
                hit_type = random.choice([1, 2])
                t.be_hurt(hero, {'type': hit_type, 'rate': v}, sk)
        hero.add_hook('造成伤害后', f'{sk.id}_extra', on_dmg, sk, hero)


# ─── 200899 断首何怒
class Skill200899(Skill):
    def __init__(self):
        super().__init__(200899,'断首何怒','受伤后60%使伤害来源下次攻谋降低34%；每回合行动时自身受伤降低80%，每次受攻击/谋略伤害后降低1/4',4,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        sub_v = calc_skill_addition_rate(34, 0.28, hero.attrs['def'])
        def on_hurt(attacker, di, s):
            if get_random_bool(hero.get_real_skill_rate(60)):
                attacker.add_state('attackDamageSub', sub_v, 1, sk, hero)
                attacker.add_state('inteDamageSub', sub_v, 1, sk, hero)
        reduce = [80]
        def on_act():
            reduce[0] = 80
            hero.add_state('beAttackDamageSub', reduce[0], 1, sk, hero)
            hero.add_state('beInteDamageSub', reduce[0], 1, sk, hero)
        def on_hurt2(a, di, s):
            if di.get('type') in (1, 2) and reduce[0] > 0:
                reduce[0] = max(0, reduce[0] - reduce[0] // 4)
        hero.add_hook('受伤时', f'{sk.id}_debuff', on_hurt, sk, hero)
        hero.add_hook('行动时', f'{sk.id}_act', on_act, sk, hero)
        hero.add_hook('受伤时', f'{sk.id}_decay', on_hurt2, sk, hero)



# ─── 200900 垒实迎击 -> 复用 Skill1015
class Skill200900(Skill):
    def __init__(self):
        super().__init__(200900,'垒实迎击','受普攻时50%恢复(200%)；50%移除负面效果；规避50%免疫1次伤害',4,'--',True,1)
    def call(self, hero, target=None):
        from .skills import Skill1015
        obj = Skill1015(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200915 统军畏慎
class Skill200915(Skill):
    def __init__(self):
        super().__init__(200915,'统军畏慎','前几回合攻谋伤害提高25%（几率每回合-10%）；无视防御60%几率每回合+10%',1,'--',True,2)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        dmg_v = calc_skill_addition_rate(25, 0.18, hero.attrs['int'])
        use_atk = hero.attrs['atk'] >= hero.attrs['int']
        rate_dmg = [80]
        rate_pen = [30]
        def sub():
            if get_random_bool(rate_dmg[0]):
                if use_atk:
                    hero.add_state('attackDamageAdd', dmg_v, 1, sk, hero)
                else:
                    hero.add_state('inteDamageAdd', dmg_v, 1, sk, hero)
            rate_dmg[0] = max(0, rate_dmg[0] - 10)
            if get_random_bool(rate_pen[0]):
                hero.add_state('ignoreDefRate', 60, 1, sk, hero)
            rate_pen[0] = min(100, rate_pen[0] + 10)
        hero.add_hook('行动时', f'{sk.id}_act', sub, sk, hero)



# ─── 200938 三术奇谋
class Skill200938(Skill):
    def __init__(self):
        super().__init__(200938,'三术奇谋','1回合准备对敌单体3次谋略攻击(178%)依次降攻防谋18',2,50,True,4)
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
        super().__init__(200942,'暴戾恣睢','攻击伤害提高4%每回合叠加；每回合行动时20%几率对敌全体攻击(150%)',1,'--',True,5)
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
        super().__init__(200952,'抚民励德','第2/4/6回合行动时我军全体谋略防御+80并受伤-20%',1,'--',True,3)
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
        super().__init__(200955,'动如雷震','我军群体追击发动率提升100%追击伤害提升40%持续1回合',2,30,True,3)
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
        super().__init__(200957,'五兵之烈','对敌军群体猛烈攻击(280%)默认使目标防御降低30%',2,35,True,4)
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
        super().__init__(200960,'鸾凤和鸣','每回合首次发动主动后我军群体2目标恢复85%',1,'--',True,3)
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
        super().__init__(200961,'奋疾先登','每回合行动时攻击伤害提升8%达40%时群体攻击并重置',1,'--',True,5)
    def call(self, hero, target=None):
        from .skills import Skill1009
        obj = Skill1009(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200966 尽言直谏
class Skill200966(Skill):
    def __init__(self):
        super().__init__(200966,'尽言直谏','每回合行动时随机令友方2个主动战法下次发动率+10%伤害+30%',1,'--',True,3)
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
        super().__init__(200979,'及锋而试','对敌军群体攻击(120%)并使其士气降低10每次发动伤害率+40%',2,35,True,5)
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
        super().__init__(200980,'乘胜追击','普通攻击后追击(150%)60%几率再次追击(100%)每次降低20%',3,35,True,0)
    def call(self, hero, target=None):
        from .skills import Skill1037
        obj = Skill1037(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200983 运筹决胜
class Skill200983(Skill):
    def __init__(self):
        super().__init__(200983,'运筹决胜','试图发动主动时30%使敌军单体暴走1回合；50%对混乱/暴走敌军全体谋略攻击(220%)',1,'--',True,5)
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
        super().__init__(200984,'怀德畏威','令谋略最低友军单体攻击敌军(160%)；自身对敌军2目标谋略攻击(160%)；目标相同则混乱1回合',2,40,True,5)
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
        super().__init__(200985,'谋议宏图','首回合我军全体受伤降低30%每回合-1/8；我军全体每回合士气+8',1,'--',True,5)
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
        super().__init__(200986,'盛气横凌','普通攻击后对目标猛攻(260%)；士气高昂则混乱1回合；一般/低落则额外攻击(80%-160%)',3,50,True,0)
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
        super().__init__(200987,'三军夺帅','每次成功发动普攻/主动/追击后攻击敌军单体(180%)自身攻击+10或谋略攻击敌军2目标(100%)谋略-5',4,'--',True,5)
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
        super().__init__(200991,'潜谋远计','前4回合受伤时75%恢复(100%)并谋略+15；第5回合起行动时对谋略低于自身的敌军全体75%谋略攻击(120%)',1,'--',True,5)
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
        super().__init__(200993,'舍身卫主','受距离2以内敌军伤害时60%反击(120%)；前锋/中军时前3回合承担友军全体攻击伤害',4,'--',True,2)
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
        super().__init__(201006,'同仇敌忾','我军全体每次受伤后距离1以内友军受伤-2%且主动战法伤害+2%最多叠加10次',1,'--',True,3)
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


# ─── 200194 避其锋芒 -> 复用 Skill1020
class Skill200194(Skill):
    def __init__(self):
        super().__init__(200194,'避其锋芒','前3回合我军群体受到攻击和谋略攻击伤害降低30%',1,'--',True,2)
    def call(self, hero, target=None):
        from .skills import Skill1020
        obj = Skill1020(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200198 大赏三军 -> 复用 Skill1018
class Skill200198(Skill):
    def __init__(self):
        super().__init__(200198,'大赏三军','前3回合我军群体攻击和谋略攻击伤害提高30%',1,'--',True,3)
    def call(self, hero, target=None):
        from .skills import Skill1018
        obj = Skill1018(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200201 无心恋战 -> 复用 Skill1019
class Skill200201(Skill):
    def __init__(self):
        super().__init__(200201,'无心恋战','前3回合敌军群体攻击和谋略攻击伤害降低30%',1,'--',True,4)
    def call(self, hero, target=None):
        from .skills import Skill1019
        obj = Skill1019(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200204 神兵天降 -> 复用 Skill1017
class Skill200204(Skill):
    def __init__(self):
        super().__init__(200204,'神兵天降','前3回合敌军群体受到攻击和谋略攻击伤害提高30%',1,'--',True,4)
    def call(self, hero, target=None):
        from .skills import Skill1017
        obj = Skill1017(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200220 反计之策 -> 复用 Skill1023
class Skill200220(Skill):
    def __init__(self):
        super().__init__(200220,'反计之策','前3回合敌军群体主动战法伤害大幅下降，首回合100%犹豫',1,'--',True,4)
    def call(self, hero, target=None):
        from .skills import Skill1023
        obj = Skill1023(); obj.id = self.id
        return obj.call(hero, target)


# ─── 200228 战必断金
class Skill200228(Skill):
    def __init__(self):
        super().__init__(200228,'战必断金','前3回合敌军群体每回合90%几率陷入怯战',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f'发动【{self.name}】')
        sk = self
        et = hero.manager.red_team['heros'] if hero.camp == 'blue' else hero.manager.blue_team['heros']
        def sub():
            if hero.manager.round > 3: return
            for e in et:
                if e.arms > 0 and get_random_bool(hero.get_real_skill_rate(90)) and not e.is_attack_limit():
                    e.state['attackLimit'] = {'rounds': 1, 'from': {'hero': hero, 'skill': sk}}
        hero.add_hook('回合开始时', f'{sk.id}_断金', sub, sk, hero)


# ──────────────── SKILLS_S 注册表 ────────────────
SKILLS_S = {
    200194: Skill200194(),
    200198: Skill200198(),
    200201: Skill200201(),
    200204: Skill200204(),
    200220: Skill200220(),
    200228: Skill200228(),
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

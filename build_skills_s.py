# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, 'D:/nettest')
from battle_sim.skills import SKILLS

out = open('D:/nettest/battle_sim/skills_s.py', 'w', encoding='utf-8')

def w(s):
    out.write(s + '\n')

w('# -*- coding: utf-8 -*-')
w('from .calc import calc_attack_damage, calc_inte_damage, calc_inte2_damage, calc_recover, calc_skill_addition_rate, get_random_bool, get_random_int, keep_two_decimal')
w('from .skills import Skill')
w('import random')
w('')
w('')

# ── 别名映射：直接用旧类创建新对象 ──────────────────
ALIASES = {
    200016: (1007, '皇裔流离'),
    200023: (1028, '魏武之世'),
    200027: (1008, '其疾如风'),
    200194: (1020, '避其锋芒'),
    200198: (1018, '大赏三军'),
    200201: (1019, '无心恋战'),
    200204: (1017, '神兵天降'),
    200220: (1023, '反计之策'),
    200254: (1030, '衔命建功'),
    200648: (1021, '白衣渡江'),
    200687: (1013, '始计'),
    200773: (1016, '金匮要略'),
    200961: (1009, '奋疾先登'),
    200235: (1014, '浑水摸鱼'),
    200268: (1011, '忠克猛烈'),
    200647: (1026, '一骑当千'),
    200886: (1027, '三军之众'),
    200900: (1015, '垒实迎击'),
    200980: (1037, '乘胜追击'),
    200694: (1029, '火势风威'),
}

for new_id, (old_id, name) in ALIASES.items():
    old_cls = SKILLS[old_id].__class__.__name__
    w(f'class Skill{new_id}(Skill):')
    w(f'    def __init__(self):')
    w(f'        from .skills import {old_cls} as _Base')
    w(f'        _obj = _Base()')
    w(f'        super().__init__({new_id}, {repr(name)}, _obj.desc, _obj.skill_type, _obj.rate, _obj.study, _obj.limit)')
    w(f'    def call(self, hero, target=None):')
    w(f'        from .skills import {old_cls} as _Base')
    w(f'        _obj = _Base()')
    w(f'        _obj.id = self.id')
    w(f'        return _obj.call(hero, target)')
    w(f'')
    w(f'')

# ── 真正新实现的战法 ──────────────────────────────

new_skills = '''
class Skill200014(Skill):
    def __init__(self):
        super().__init__(200014,"酒池肉林","战斗前2回合我军全体受到伤害降低32%，第4回合起攻击伤害恢复35%兵力",1,"--",False,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero, f"发动【{self.name}】")
        sk = self
        sv = calc_skill_addition_rate(32, 0.28, hero.attrs["def"])
        team = hero.manager.blue_team["heros"] if hero.camp=="blue" else hero.manager.red_team["heros"]
        for e in team:
            e.add_state("beAttackDamageSub", sv, 2, sk, hero)
            e.add_state("beInteDamageSub", sv, 2, sk, hero)
        def on_action():
            if hero.manager.round >= 4:
                def on_dmg(t, di, sk2, dmg):
                    if di.get("type") == 1: hero.recover(int(dmg*0.35), hero, sk.name)
                hero.add_hook("造成伤害后", f"{sk.id}_d", on_dmg, sk, hero)
                hero.clear_hook("行动时", f"{sk.id}_w")
        hero.add_hook("行动时", f"{sk.id}_w", on_action, sk, hero)


class Skill200228(Skill):
    def __init__(self):
        super().__init__(200228,"战必断金","前3回合敌军群体每回合90%几率陷入怯战",1,"--",True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        et = hero.manager.red_team["heros"] if hero.camp=="blue" else hero.manager.blue_team["heros"]
        def sub():
            if hero.manager.round > 3: return
            for e in et:
                if e.arms > 0 and get_random_bool(hero.get_real_skill_rate(90)) and not e.is_attack_limit():
                    e.state["attackLimit"] = {"rounds":1,"from":{"hero":hero,"skill":sk}}
        hero.add_hook("回合开始时", f"{sk.id}_断金", sub, sk, hero)


class Skill200246(Skill):
    def __init__(self):
        super().__init__(200246,"缓师徐持","每回合已行动敌军受伤后属性下降16，可叠加",1,"--",False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        sv = calc_skill_addition_rate(16, 0.12, hero.attrs["int"])
        et = hero.manager.red_team["heros"] if hero.camp=="blue" else hero.manager.blue_team["heros"]
        for e in et:
            def ms(e=e):
                acted = [False]
                def oa(): acted[0] = True
                def oh(a, di, s):
                    if acted[0]:
                        for k in ("atk","def","int","spd"): e.attrs[k] = keep_two_decimal(max(0, e.attrs[k]-sv))
                def rst(): acted[0] = False
                e.add_hook("行动时", f"{sk.id}_a_{id(e)}", oa, sk, hero)
                e.add_hook("受伤时", f"{sk.id}_h_{id(e)}", oh, sk, hero)
                e.add_hook("回合开始时", f"{sk.id}_r_{id(e)}", rst, sk, hero)
            ms()


class Skill200255(Skill):
    def __init__(self):
        super().__init__(200255,"伏波扬砂","我军全体普攻伤害提升25%，累积4层后触发连击",1,"--",False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(25, 0.18, hero.attrs["atk"])
        team = hero.manager.blue_team["heros"] if hero.camp=="blue" else hero.manager.red_team["heros"]
        for e in team: e.add_state("attackDamageAdd", v, -1, sk, hero)
        stk = [0]
        def od(t, di, s2, dmg):
            stk[0] += 1
            if stk[0] >= 4:
                stk[0] = 0
                if hero.state.get("doubleAttack",{}).get("rounds",0) <= 0:
                    hero.state["doubleAttack"] = {"rounds":1,"from":{"hero":hero,"skill":sk}}
        hero.add_hook("造成伤害后", f"{sk.id}_stk", od, sk, hero)


class Skill200257(Skill):
    def __init__(self):
        super().__init__(200257,"审时定计","敌军被施加负面效果时50%提升受到伤害30%",1,"--",False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(30, 0.2, hero.attrs["int"])
        rv = calc_skill_addition_rate(65, 0.6, hero.attrs["int"])
        et = hero.manager.red_team["heros"] if hero.camp=="blue" else hero.manager.blue_team["heros"]
        for e in et:
            def me(e=e):
                def od(s2, src):
                    if get_random_bool(hero.get_real_skill_rate(50)):
                        e.add_state("beAttackDamageAdd", v, 1, sk, hero)
                        e.add_state("beInteDamageAdd", v, 1, sk, hero)
                        hero.recover(calc_recover(hero, rv, 0.6, 2), hero, sk.name)
                e.add_hook("被施加负面效果时", f"{sk.id}_e_{id(e)}", od, sk, hero)
            me()


class Skill200262(Skill):
    def __init__(self):
        super().__init__(200262,"僭号天子","我军全体受到伤害的32%由玉玺承担，第2回合起每回合反弹",1,"--",False,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        sr = calc_skill_addition_rate(32, 0.28, hero.attrs["def"])
        shield = [0]; ratio = [0.8]
        team = hero.manager.blue_team["heros"] if hero.camp=="blue" else hero.manager.red_team["heros"]
        for e in team:
            def ms(e=e):
                def oh(a, di, s): shield[0] += int(di.get("damage",0)*sr/100)
                e.add_hook("受伤时", f"{sk.id}_{id(e)}", oh, sk, hero)
            ms()
        def ors():
            if hero.manager.round >= 2 and shield[0] > 0:
                dmg = int(shield[0]*ratio[0]); shield[0] = 0; ratio[0] = min(1.0, ratio[0]+0.05)
                hero.be_hurt_by_num(hero, {"type":3,"rate":100}, sk, dmg, lambda: None)
        hero.add_hook("回合开始时", f"{sk.id}_ref", ors, sk, hero)


class Skill200264(Skill):
    def __init__(self):
        super().__init__(200264,"疲兵沮意","每回合为我军叠加2层避锐，每层减伤10%，生效后30%几率燃烧敌军",1,"--",False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(10, 0.08, hero.attrs["int"])
        fr = calc_skill_addition_rate(150, 1.2, hero.attrs["int"])
        team = hero.manager.blue_team["heros"] if hero.camp=="blue" else hero.manager.red_team["heros"]
        stacks = {id(e): 0 for e in team}
        for e in team:
            def ms(e=e):
                def ors(): stacks[id(e)] = min(stacks[id(e)]+2, 10)
                def oh(a, di, s):
                    if stacks[id(e)] > 0:
                        stacks[id(e)] -= 1
                        if get_random_bool(hero.get_real_skill_rate(30)) and a and a.arms > 0:
                            a.be_hurt(hero, {"type":4,"rate":fr}, sk)
                e.add_hook("回合开始时", f"{sk.id}_s_{id(e)}", ors, sk, hero)
                e.add_hook("受伤时", f"{sk.id}_h_{id(e)}", oh, sk, hero)
            ms()


class Skill200603(Skill):
    def __init__(self):
        super().__init__(200603,"白楼独舞","前3回合敌军群体攻谋伤害降低26%，结束后敌军暴走2回合",1,"--",False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        v = calc_skill_addition_rate(26, 0.15, hero.attrs["int"])
        enemy = hero.get_target(4, 2)
        for e in enemy:
            e.add_state("attackDamageSub", v, 3, sk, hero)
            e.add_state("inteDamageSub", v, 3, sk, hero)
        def rage():
            if hero.manager.round == 4:
                for e in enemy:
                    if e.arms > 0 and not e.is_confusion():
                        e.state["confusion"] = {"rounds":2,"from":{"hero":hero,"skill":sk}}
                hero.clear_hook("回合开始时", f"{sk.id}_rage")
        hero.add_hook("回合开始时", f"{sk.id}_rage", rage, sk, hero)


class Skill200622(Skill):
    def __init__(self):
        super().__init__(200622,"不攻","无法普攻，谋略伤害+25%，每回合对敌军单体谋略攻击(伤害率83%)",1,"--",False,1)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        hero.state["attackLimit"] = {"rounds":-1,"from":{"hero":hero,"skill":sk}}
        va = calc_skill_addition_rate(25, 0.18, hero.attrs["int"])
        vb = calc_skill_addition_rate(83, 0.75, hero.attrs["int"])
        hero.add_state("inteDamageAdd", va, -1, sk, hero)
        def sub():
            t = 

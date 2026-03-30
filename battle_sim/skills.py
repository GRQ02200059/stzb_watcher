# -*- coding: utf-8 -*-
"""
战法配置 - 参考 stzbBattleSimulator 完整实现
skill_type: 1=指挥 2=主动 3=追击 4=被动
"""
from .calc import (
    calc_attack_damage, calc_inte_damage, calc_inte2_damage,
    calc_recover, calc_skill_addition_rate,
    get_random_bool, get_random_int, keep_two_decimal
)
import random


class Skill:
    def __init__(self, id_, name, desc, skill_type, rate, study=True, limit=0, ignore_def=False):
        self.id         = id_
        self.name       = name
        self.desc       = desc
        self.skill_type = skill_type
        self.rate       = rate
        self.study      = study
        self.limit      = limit
        self.ignore_def = ignore_def

    def call(self, hero, target=None):
        pass


# ─── 1001 连战
class Skill1001(Skill):
    def __init__(self):
        super().__init__(1001,'连战','使自身可以进行两次普通攻击，持续1回合',2,35,True,1)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.state['doubleAttack'] = {'rounds':1,'from':{'hero':hero,'skill':self}}
        hero.manager.log(hero,'的【连战】生效，本回合可进行两次普通攻击',1)
        return True


# ─── 1002 温酒斩将
class Skill1002(Skill):
    def __init__(self):
        super().__init__(1002,'温酒斩将','普通攻击后，对攻击目标再次发动猛攻(伤害率200%)',3,35,True,0)
    def call(self, hero, target=None):
        if target is None: return
        hero.manager.log(hero,'的攻击发动【温酒斩将】')
        target.be_hurt(hero,{'type':2,'rate':200},self)


# ─── 1003 血践黄砂
class Skill1003(Skill):
    def __init__(self):
        super().__init__(1003,'血践黄砂','以无法发动主动战法为代价，使自身攻击伤害提高120%',4,'--',True,0)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero,'的战法【血践黄砂】生效')
        hero.state['activeLimit'] = {'rounds':-1,'from':{'hero':hero,'skill':self}}
        hero.state['attackDamageAdd']['passive'] = {'value':120,'rounds':-1,'from':self,'hero':hero,'type':1}
        hero.manager.log(hero,'造成的攻击伤害提高120%',1)


# ─── 1004 方阵突击
class Skill1004(Skill):
    def __init__(self):
        super().__init__(1004,'方阵突击','普通攻击后，对攻击目标再次发动攻击(伤害率200%)，并使其陷入混乱1回合',3,30,True,0)
    def call(self, hero, target=None):
        if target is None: return
        hero.manager.log(hero,'的攻击发动【方阵突击】')
        target.be_hurt(hero,{'type':2,'rate':200},self)
        if not target.is_confusion():
            target.state['confusion'] = {'rounds':1,'from':{'hero':hero,'skill':self}}
            hero.manager.log_action(hero,target,'的【方阵突击】使','陷入混乱1回合',1)
        else:
            hero.manager.log(target,'已存在混乱效果',1)


# ─── 1005 先驱突击
class Skill1005(Skill):
    def __init__(self):
        super().__init__(1005,'先驱突击','战斗前3回合优先行动，每回合可连击，攻击+30',1,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,'发动【先驱突击】')
        hero.state['firstAction'] = {'rounds':3,'from':{'hero':hero,'skill':self}}
        hero.attrs['atk'] = keep_two_decimal(hero.attrs['atk'] + 30)
        hero.manager.log(hero,f'攻击属性提高了30({hero.attrs["atk"]})',1)
        hero.state['doubleAttack'] = {'rounds':3,'from':{'hero':hero,'skill':self}}
        hero.manager.log(hero,'的连击(预备)效果已施加',1)


# ─── 1006 钝兵挫锐
class Skill1006(Skill):
    def __init__(self):
        super().__init__(1006,'钝兵挫锐','普通攻击后，对攻击目标再次发动猛攻(伤害率200%)，并使其陷入怯战1回合',3,30,True,0)
    def call(self, hero, target=None):
        if target is None: return
        hero.manager.log(hero,'的攻击发动【钝兵挫锐】')
        target.be_hurt(hero,{'type':2,'rate':200},self)
        if not target.is_attack_limit():
            target.state['attackLimit'] = {'rounds':1,'from':{'hero':hero,'skill':self}}
            hero.manager.log_action(hero,target,'的【钝兵挫锐】使','陷入怯战1回合',1)
        else:
            hero.manager.log(target,'已存在怯战效果',1)


# ─── 1007 皇裔流离
class Skill1007(Skill):
    def __init__(self):
        super().__init__(1007,'皇裔流离','我军全体受到伤害时，有50%几率恢复兵力(恢复率68%)',1,'--',True,0)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,'发动【皇裔流离】')
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        sk = self
        for e in team:
            def make_sub(e=e):
                def sub(attacker, dmg_info, skill):
                    if get_random_bool(hero.get_real_skill_rate(50)):
                        r = calc_recover(hero, 68, 0.6)
                        e.recover(r, hero, '皇裔流离')
                e.add_hook('受伤时','皇裔流离急救',sub,sk,hero)
                hero.manager.log(e,'的急救效果已施加',1)
            make_sub()


# ─── 1008 其疾如风
class Skill1008(Skill):
    def __init__(self):
        super().__init__(1008,'其疾如风','前3回合我军全体速度+41，并有70%几率可连击',1,'--',True,0)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,'发动【其疾如风】')
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        spd_add = 41 + int((hero.attrs['spd'] - 80) * 0.075)
        sk = self
        for e in team:
            e.attrs['spd'] = keep_two_decimal(e.attrs['spd'] + spd_add)
            hero.manager.log(e,f'速度属性提高了{spd_add}({e.attrs["spd"]})',1)
            def make_sub(e=e):
                def sub():
                    rnd = hero.manager.round
                    if 1 <= rnd <= 3:
                        if get_random_bool(hero.get_real_skill_rate(70)):
                            if e.state['doubleAttack']['rounds'] <= 0:
                                e.state['doubleAttack'] = {'rounds':1,'from':{'hero':hero,'skill':sk}}
                        else:
                            hero.manager.log(e,'的其疾如风未生效',0)
                e.add_hook('普攻前','其疾如风连击',sub,sk,hero)
            make_sub()


# ─── 1009 奋疾先登
class Skill1009(Skill):
    def __init__(self):
        super().__init__(1009,'奋疾先登','每回合行动时攻击伤害提升8%，达到40%时群体攻击并重置',1,'--',True,0)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,'发动【奋疾先登】')
        max_add = 40
        add_rate = 8
        sk = self
        def do_attack():
            targets = hero.get_target(3, 2)
            for t in targets:
                t.be_hurt(hero,{'type':1,'rate':190},sk)
                t.attrs['spd'] = keep_two_decimal(max(0, t.attrs['spd'] - 20))
                hero.manager.log_action(hero,t,'【奋疾先登】使',f'速度降低20({t.attrs["spd"]})',1)
            hero.del_state('attackDamageAdd', sk)
        def sub():
            hero.add_state('attackDamageAdd', add_rate, -1, sk, hero, True)
            cur = hero.get_damage_state_value('attackDamageAdd')
            if cur >= max_add:
                do_attack()
            for other in hero.manager.sort_spd_heros:
                if other is not hero and other.arms > 0 and hero.attrs['spd'] > other.attrs['spd']:
                    hero.add_state('attackDamageAdd', add_rate, -1, sk, hero, True)
                    if hero.get_damage_state_value('attackDamageAdd') >= max_add:
                        do_attack()
        hero.add_hook('行动时','奋疾先登增伤',sub,sk,hero)


# ─── 1010 奇兵拒北
class Skill1010(Skill):
    def __init__(self):
        super().__init__(1010,'奇兵拒北','每回合30%几率对敌军大营中军各攻击一次(伤害率180%)',1,'--',True,0)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,'发动【奇兵拒北】')
        rate = 30
        add_rate = 5
        sk = self
        def sub():
            extra = hero.rate_val.get(sk.id, {}).get('value', 0)
            cur_rate = hero.get_real_skill_rate(rate) + extra
            if get_random_bool(cur_rate):
                if hero.camp == 'red':
                    targets = hero.manager.blue_team['heros']
                    team = hero.manager.red_team['heros']
                else:
                    targets = hero.manager.red_team['heros']
                    team = hero.manager.blue_team['heros']
                for t in targets:
                    if t.pos_name in ('大营','中军') and t.arms > 0:
                        t.be_hurt(hero,{'type':1,'rate':180},sk)
                spd_hero = max((e for e in team if e is not hero and e.arms > 0),
                               key=lambda e: e.attrs['spd'], default=None)
                if spd_hero:
                    for t in targets:
                        if t.pos_name in ('大营','中军') and t.arms > 0:
                            t.be_hurt(spd_hero,{'type':1,'rate':get_random_int(120,180)},sk)
                if sk.id in hero.rate_val: del hero.rate_val[sk.id]
            else:
                hero.manager.log(hero,'的【奇兵拒北】没有生效')
                hero.rate_val[sk.id] = {'value': hero.rate_val.get(sk.id,{}).get('value',0)+add_rate,'rounds':-1}
        hero.add_hook('行动时','奇兵拒北攻击',sub,sk,hero)


# ─── 1011 忠克猛烈
class Skill1011(Skill):
    def __init__(self):
        super().__init__(1011,'忠克猛烈','无视防御，对敌军单体攻击(伤害率280%)，使其陷入犹豫1回合，并在下回合行动前触发反击(伤害率80%，最多3次)',2,50,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero,'发动【忠克猛烈】')
        targets = hero.get_target(4, 1)
        if not targets: return True
        t = targets[0]
        t.be_hurt(hero,{'type':1,'rate':280},self)
        if not t.is_active_limit():
            t.state['activeLimit'] = {'rounds':1,'from':{'hero':hero,'skill':self}}
            hero.manager.log_action(hero,t,'的【忠克猛烈】使','陷入犹豫1回合')
        sk = self
        counter_key = f'{hero.id}_{sk.id}_counter'
        def on_hurt(attacker, dmg_info, skill):
            if skill is sk and attacker is hero: return
            if hero.count_get(counter_key) >= 3: return
            hero.count_add(counter_key)
            t.be_hurt(hero,{'type':1,'rate':80},sk)
        def clear_hook():
            for e in hero.manager.sort_spd_heros:
                e.clear_hook('受伤时', f'{hero.id}_{sk.id}_忠克猛烈反击')
            hero.count_reset(counter_key)
        t.add_hook('受伤时','忠克猛烈反击',on_hurt,sk,hero,'debuff',False)
        hero.add_hook('行动前','忠克猛烈清除',clear_hook,sk,hero,'other')
        return True


# ─── 1012 愈战愈勇
class Skill1012(Skill):
    def __init__(self):
        super().__init__(1012,'愈战愈勇','使自身攻击伤害提高10%，每回合开始时额外叠加一次',4,'--',True,0)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero,'的战法【愈战愈勇】生效')
        hero.state['attackDamageAdd']['passive'] = {'value':10,'rounds':-1,'from':self,'hero':hero,'type':1}
        hero.manager.log(hero,'造成的攻击伤害提高10%',1)
        sk = self
        def sub():
            hero.state['attackDamageAdd']['passive']['value'] += 10
            v = hero.state['attackDamageAdd']['passive']['value']
            hero.manager.log_action(hero,hero,'的【愈战愈勇】使',f'造成的攻击伤害提高{v}%')
        hero.add_hook('回合开始时','愈战愈勇叠加',sub,sk,hero)


# ─── 1013 始计
class Skill1013(Skill):
    def __init__(self):
        super().__init__(1013,'始计','前4回合，我军大营攻击伤害和谋略伤害提高20%（受谋略属性影响）',1,'--',True,0)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,'发动【始计】')
        add_rate = calc_skill_addition_rate(20, 0.15, hero.attrs['int'])
        sk = self
        def sub():
            if hero.manager.round > 4: return
            for e in hero.manager.sort_spd_heros:
                if e.camp == hero.camp and e.pos_name == '大营':
                    e.add_state('attackDamageAdd', add_rate, 1, sk, hero, False, 2)
                    e.add_state('inteDamageAdd', add_rate, 1, sk, hero, False, 2)
        hero.add_hook('行动前','始计增伤',sub,sk,hero)


# ─── 1014 浑水摸鱼
class Skill1014(Skill):
    def __init__(self):
        super().__init__(1014,'浑水摸鱼','1回合准备，使敌军群体陷入混乱2回合',2,35,True,4)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        def subskill():
            targets = hero.get_target(sk.limit, 2)
            for t in targets:
                if not t.is_confusion():
                    t.state['confusion'] = {'rounds':2,'from':{'hero':hero,'skill':sk}}
                    hero.manager.log(t,'陷入混乱2回合',1)
                else:
                    hero.manager.log(t,'已存在混乱效果',1)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 1015 垒实迎击
class Skill1015(Skill):
    def __init__(self):
        super().__init__(1015,'垒实迎击','受到普通攻击时，有50%几率恢复兵力(恢复率200%)；有50%几率移除负面效果',4,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        def on_hurt(attacker, dmg_info, skill):
            if dmg_info.get('type') == 1 and skill is None:
                if get_random_bool(hero.get_real_skill_rate(50)):
                    hero.manager.log(hero,'恢复一定兵力')
                    hero.recover(calc_recover(hero, 200, 0, 2), hero, sk.name)
                if get_random_bool(hero.get_real_skill_rate(50)):
                    hero.manager.log(hero,'移除负面效果')
                    hero.clear_debuff(sk, hero)
        def on_round_start():
            if hero.pos_name in ('中军','前锋'):
                if get_random_bool(hero.get_real_skill_rate(50)):
                    team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
                    for e in team:
                        if e is not hero:
                            hero.manager.log_action(hero, e, f'【{sk.name}】援护', '友军1回合', 1)
        hero.add_hook('受伤时','垒实迎击受伤效果', on_hurt, sk, hero)
        hero.add_hook('回合开始时','垒实迎击援护', on_round_start, sk, hero)


# ─── 1016 金匮要略
class Skill1016(Skill):
    def __init__(self):
        super().__init__(1016,'金匮要略','前3回合，使我军全体受到伤害降低20.4%，并有50%几率恢复兵力(恢复率80%)',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,'发动【金匮要略】')
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        recover_rate = 80
        recover_add  = 0.75
        dmg_sub_rate = calc_skill_addition_rate(20.4, 0.13, hero.attrs['int'])
        sk = self
        for e in team:
            def make_sub(e=e):
                def sub(attacker, dmg_info, skill):
                    if get_random_bool(hero.get_real_skill_rate(50)):
                        e.recover(calc_recover(hero, recover_rate, recover_add), hero, '金匮要略')
                e.add_hook('受伤时','金匮要略急救', sub, sk, hero)
            make_sub()
            e.add_state('beAttackDamageSub', dmg_sub_rate, 3, sk, hero)
            e.add_state('beInteDamageSub',   dmg_sub_rate, 3, sk, hero)
            hero.manager.log(e,'的金匮要略效果已施加',1)


# ─── 1017 神兵天降
class Skill1017(Skill):
    def __init__(self):
        super().__init__(1017,'神兵天降','前3回合，使敌军群体受到攻击和谋略攻击伤害提高30%',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,'发动【神兵天降】')
        value = calc_skill_addition_rate(30, 0.15, hero.attrs['int'])
        sk = self
        enemy = hero.get_target(4, 2)
        for e in enemy:
            e.add_state('beAttackDamageAdd', value, 3, sk, hero)
            e.add_state('beInteDamageAdd',   value, 3, sk, hero)


# ─── 1018 大赏三军
class Skill1018(Skill):
    def __init__(self):
        super().__init__(1018,'大赏三军','前3回合，使我军群体攻击和谋略攻击伤害提高30%',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,'发动【大赏三军】')
        value = calc_skill_addition_rate(30, 0.15, hero.attrs['int'])
        sk = self
        team = hero.get_target(3, 2, 2)
        for e in team:
            e.add_state('attackDamageAdd', value, 3, sk, hero)
            e.add_state('inteDamageAdd',   value, 3, sk, hero)


# ─── 1019 无心恋战
class Skill1019(Skill):
    def __init__(self):
        super().__init__(1019,'无心恋战','前3回合，使敌军群体攻击和谋略攻击伤害降低30%',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,'发动【无心恋战】')
        value = calc_skill_addition_rate(30, 0.15, hero.attrs['int'])
        sk = self
        enemy = hero.get_target(4, 2)
        for e in enemy:
            e.add_state('attackDamageSub', value, 3, sk, hero)
            e.add_state('inteDamageSub',   value, 3, sk, hero)


# ─── 1020 避其锋芒
class Skill1020(Skill):
    def __init__(self):
        super().__init__(1020,'避其锋芒','前3回合，使我军群体受到攻击和谋略攻击伤害降低30%',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,'发动【避其锋芒】')
        value = calc_skill_addition_rate(30, 0.15, hero.attrs['int'])
        sk = self
        team = hero.get_target(3, 2, 2)
        for e in team:
            e.add_state('beAttackDamageSub', value, 3, sk, hero)
            e.add_state('beInteDamageSub',   value, 3, sk, hero)


# ─── 1021 白衣渡江
class Skill1021(Skill):
    def __init__(self):
        super().__init__(1021,'白衣渡江','前2回合使敌军群体无法普攻，效果结束后对敌军全体发动强力谋略攻击(伤害率215%)',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,'发动【白衣渡江】')
        sk = self
        enemy = hero.get_target(5, 2)
        for e in enemy:
            value = calc_skill_addition_rate(215, 2.25, hero.attrs['int'])
            if not e.is_attack_limit():
                e.state['attackLimit'] = {'rounds':2,'from':{'hero':hero,'skill':sk}}
                hero.manager.log_action(hero, e, f'的【{sk.name}】使', '陷入怯战2回合', 1)
            else:
                hero.manager.log(e,'已存在怯战效果',1)
            dmg_info = {'type':2,'rate':value}
            damage = calc_inte_damage(hero, e, dmg_info, sk)
            def make_hook(e=e, damage=damage, dmg_info=dmg_info):
                def action_hook():
                    if hero.manager.round == 3:
                        e.be_hurt_by_num(hero, dmg_info, sk, damage, lambda: None)
                        hero.manager.log_action(e, hero, '由于', f'【{sk.name}】施加的效果损失了{damage}兵力({e.arms})')
                e.add_hook('行动时','白衣渡江延迟伤害', action_hook, sk, hero, 'debuff')
            make_hook()
            hero.manager.log(e,'的白衣渡江延迟伤害效果已施加',1)


# ─── 1022 威震河朔
class Skill1022(Skill):
    def __init__(self):
        super().__init__(1022,'威震河朔','对敌军群体发动攻击(伤害率200%)，使自身与友军单体主动战法伤害提升20%，每发动一次发动率降低10%',2,70,True,5)
    def call(self, hero, target=None):
        tag = f'{hero.id}_{self.id}_发动次数'
        current_rate = self.rate + hero.rate_val.get(self.id, {}).get('value', 0)
        if not get_random_bool(current_rate): return
        hero.manager.log(hero,'发动【威震河朔】')
        hero.count_add(tag, 1)
        hero.rate_val[self.id] = {'value': -10 * hero.count_get(tag), 'rounds': -1}
        sk = self
        enemy = hero.get_target(5, 2)
        for e in enemy:
            e.be_hurt(hero, {'type':1,'rate':200}, sk)
        value = calc_skill_addition_rate(20, 0.125, hero.attrs['atk'])
        hero.add_state('activeDamageAdd', value, 2, sk, hero)
        allies = hero.get_target(5, 1, 3)
        if allies:
            allies[0].add_state('activeDamageAdd', value, 2, sk, hero)
        return True


# ─── 1023 反计之策
class Skill1023(Skill):
    def __init__(self):
        super().__init__(1023,'反计之策','前3回合使敌军群体主动战法伤害大幅下降，首回合100%使其陷入犹豫',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,'发动【反计之策】')
        sk = self
        enemy = hero.get_target(4, 2)
        for e in enemy:
            e.add_state('activeDamageSub', 9999, 3, sk, hero)
            if not e.is_active_limit():
                e.state['activeLimit'] = {'rounds':1,'from':{'hero':hero,'skill':sk}}
                hero.manager.log_action(hero, e, f'的【{sk.name}】使', '陷入犹豫1回合', 1)
            else:
                hero.manager.log(e,'已存在犹豫效果',1)


# ─── 1024 百战精兵
class Skill1024(Skill):
    def __init__(self):
        super().__init__(1024,'百战精兵','使自身攻击、防御、谋略、速度全部提高32',1,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        for attr in ('atk','int','def','spd'):
            hero.attrs[attr] = keep_two_decimal(hero.attrs[attr] + 32)
        hero.manager.log_action(hero,hero,f'【{self.name}】的效果使','全属性提高32',1)


# ─── 1025 持刀从武
class Skill1025(Skill):
    def __init__(self):
        super().__init__(1025,'持刀从武','每回合行动时，有50%概率对友军大营上次攻击目标发动攻击(伤害率120%)，重复3次',4,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        storage_key = sk.id
        team[0].storage = getattr(team[0], 'storage', {})
        team[0].storage[storage_key] = {'hero': [], 'clear': False}
        def on_damage_done(attacker, t):
            if team[0].storage[storage_key]['clear']:
                team[0].storage[storage_key]['clear'] = False
                team[0].storage[storage_key]['hero'] = []
            if t not in team[0].storage[storage_key]['hero']:
                team[0].storage[storage_key]['hero'].append(t)
        def on_round_start():
            team[0].storage[storage_key]['clear'] = True
        team[0].add_hook('造成伤害后','持刀从武记录目标', on_damage_done, sk, hero)
        team[0].add_hook('回合开始时','持刀从武清除标记', on_round_start, sk, hero)
        def sub():
            dmg_rate = 120
            stored = team[0].storage.get(storage_key, {}).get('hero', [])
            for _ in range(3):
                if get_random_bool(hero.get_real_skill_rate(50)) and stored:
                    atk_target = random.choice(stored)
                    hero.manager.log_action(hero,hero,'执行来自',f'的【{sk.name}】效果')
                    atk_target.be_hurt(hero,{'type':1,'rate':dmg_rate},sk)
                    if not atk_target.is_active_limit() and not atk_target.is_confusion():
                        dmg_rate -= 20
                else:
                    hero.manager.log(hero,f'【{sk.name}】的效果未生效')
        hero.add_hook('行动时','持刀从武行动效果', sub, sk, hero)


# ─── 1026 一骑当千
class Skill1026(Skill):
    def __init__(self):
        super().__init__(1026,'一骑当千','1回合准备，对敌军群体发动攻击(伤害率280%)',2,30,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        def subskill():
            targets = hero.get_target(sk.limit, 3)
            for t in targets:
                t.be_hurt(hero, {'type':1,'rate':280}, sk)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 1027 三军之众
class Skill1027(Skill):
    def __init__(self):
        super().__init__(1027,'三军之众','1回合准备，使我军单体恢复4次兵力(恢复率151%)',2,45,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        recover_rate = 151
        recover_add  = 1.575
        def subskill():
            for _ in range(4):
                targets = hero.get_target(sk.limit, 1, 2)
                for t in targets:
                    t.recover(calc_recover(hero, recover_rate, recover_add, 2), hero, '三军之众')
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 1028 魏武之世
class Skill1028(Skill):
    def __init__(self):
        super().__init__(1028,'魏武之世','使敌军全体所有属性下降15%，并使我军全体攻击距离+1',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        enemy = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        subrate = calc_skill_addition_rate(15, 0.045, hero.attrs['int'])
        for e in enemy:
            for key in list(e.attrs.keys()):
                sub = e.attrs[key] * subrate / 100
                e.attrs[key] = keep_two_decimal(e.attrs[key] - sub)
                hero.manager.log(e,f'的{key}属性降低了{subrate}%({sub:.1f})({e.attrs[key]})',1)


# ─── 1029 火势风威
class Skill1029(Skill):
    def __init__(self):
        super().__init__(1029,'火势风威','1回合准备，对敌军全体谋略攻击(伤害率111%)，并使其受到下次伤害时额外触发燃烧(伤害率221%)',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        main_rate = calc_skill_addition_rate(111, 0.95, hero.attrs['int'])
        fire_rate = calc_skill_addition_rate(221, 2.45, hero.attrs['int'])
        def subskill():
            targets = hero.get_target(sk.limit, 3)
            for t in targets:
                t.be_hurt(hero, {'type':2,'rate':main_rate}, sk)
            for t in targets:
                dmg_info = {'type':4,'rate':fire_rate}
                damage = calc_inte2_damage(hero, t, dmg_info, sk)
                def make_fire(t=t, damage=damage, dmg_info=dmg_info):
                    tag_name = f'{hero.id}_{sk.id}_fire_{id(t)}'
                    def on_hurt(attacker, di, skill):
                        t.clear_hook('受伤时', tag_name)
                        t.be_hurt_by_num(hero, dmg_info, sk, damage, lambda: None)
                        hero.manager.log_action(t, hero, '由于', f'【{sk.name}】燃烧效果损失了{damage}兵力({t.arms})', 1)
                    t.add_hook('受伤时', tag_name, on_hurt, sk, hero, 'debuff')
                    hero.manager.log(t,'的燃烧效果已施加',1)
                make_fire()
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 1030 衔命建功
class Skill1030(Skill):
    def __init__(self):
        super().__init__(1030,'衔命建功','敌军每回合首次受持续伤害时，有50%几率对其谋略攻击(伤害率140%)；第3回合起额外触发一次持续伤害',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        value = calc_skill_addition_rate(140, 0.8, hero.attrs['int'])
        hero.storage = getattr(hero, 'storage', {})
        hero.storage[sk.id] = {'hero': {}}
        enemy = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        for e in enemy:
            def make_hooks(e=e):
                def on_dot_applied(skill, src_hero, damage, dmg_info, dmg_name):
                    if hero.manager.round >= 3:
                        hero.manager.log(e,f'的{dmg_name}效果额外触发',1)
                        e.be_hurt_by_num(hero, dmg_info, sk, damage, lambda: None)
                def on_hurt(attacker, dmg_info, skill):
                    key = e.camp + e.pos_name + e.name
                    if dmg_info.get('type') in (3,4) and not hero.storage[sk.id]['hero'].get(key):
                        hero.storage[sk.id]['hero'][key] = True
                        if get_random_bool(hero.get_real_skill_rate(50)):
                            hero.manager.log_action(hero,hero,'执行来自',f'的【{sk.name}】效果',1)
                            e.be_hurt(hero,{'type':2,'rate':value},sk)
                e.add_hook('被施加持续伤害时','衔命建功额外触发', on_dot_applied, sk, hero, 'debuff')
                e.add_hook('受伤时','衔命建功概率攻击', on_hurt, sk, hero, 'debuff')
            make_hooks()
        hero.add_hook('回合开始时','衔命建功清除标记',
            lambda: hero.storage[sk.id].update({'hero':{}}), sk, hero)


# ─── 1031 胜兵求战
class Skill1031(Skill):
    def __init__(self):
        super().__init__(1031,'胜兵求战','发动准备战法时有80%几率跳过1回合准备',1,'--',True,2)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        def on_ready(skill):
            real_rate = hero.get_real_skill_rate(80)
            if get_random_bool(real_rate):
                hero.ready_val = getattr(hero, 'ready_val', {})
                hero.ready_val[skill.id] = {'skip': -1, 'from': sk}
                hero.manager.log_action(hero,hero,f'【{sk.name}】使','主战法减少准备1回合')
            else:
                hero.manager.log_action(hero,hero,'来自',f'的【{sk.name}】没有生效')
        hero.add_hook('开始准备战法时','胜兵求战跳过准备', on_ready, sk, hero)


# ─── 1032 深谋远虑
class Skill1032(Skill):
    def __init__(self):
        super().__init__(1032,'深谋远虑','使自身谋略攻击伤害提高11%，每回合开始时额外叠加一次',4,'--',True,0)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero,f'的战法【{self.name}】生效')
        sk = self
        value = 11
        hero.add_state('inteDamageAdd', value, -1, sk, hero, True)
        def sub():
            hero.add_state('inteDamageAdd', value, -1, sk, hero, True)
        hero.add_hook('回合开始时','深谋远虑叠加', sub, sk, hero)


# ─── 1033 折冲樽俎
class Skill1033(Skill):
    def __init__(self):
        super().__init__(1033,'折冲樽俎','使自身攻击和谋略造成的伤害提高20%，并使友军单体攻击和谋略造成的伤害提高15%，持续3回合',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        v1 = calc_skill_addition_rate(20, 0.15, hero.attrs['int'])
        v2 = calc_skill_addition_rate(15, 0.12, hero.attrs['int'])
        hero.add_state('attackDamageAdd', v1, 3, sk, hero)
        hero.add_state('inteDamageAdd',   v1, 3, sk, hero)
        allies = hero.get_target(3, 1, 3)
        if allies:
            allies[0].add_state('attackDamageAdd', v2, 3, sk, hero)
            allies[0].add_state('inteDamageAdd',   v2, 3, sk, hero)


# ─── 1034 坚守不出
class Skill1034(Skill):
    def __init__(self):
        super().__init__(1034,'坚守不出','使自身受到攻击和谋略伤害降低25%，同时使友军全体受到伤害降低10%，持续3回合',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        v_self = calc_skill_addition_rate(25, 0.18, hero.attrs['def'])
        v_team = calc_skill_addition_rate(10, 0.08, hero.attrs['def'])
        hero.add_state('beAttackDamageSub', v_self, 3, sk, hero)
        hero.add_state('beInteDamageSub',   v_self, 3, sk, hero)
        team = hero.get_target(3, 2, 2)
        for e in team:
            e.add_state('beAttackDamageSub', v_team, 3, sk, hero)
            e.add_state('beInteDamageSub',   v_team, 3, sk, hero)


# ─── 1035 急救兵
class Skill1035(Skill):
    def __init__(self):
        super().__init__(1035,'急救兵','每回合行动时，有60%几率对兵力最低的友军单体恢复兵力(恢复率180%)',4,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        def sub():
            if not get_random_bool(hero.get_real_skill_rate(60)): return
            team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
            alive = [e for e in team if e.arms > 0]
            if not alive: return
            t = min(alive, key=lambda e: e.arms)
            t.recover(calc_recover(hero, 180, 1.5, 2), hero, sk.name)
        hero.add_hook('行动时','急救兵恢复', sub, sk, hero)


# ─── 1036 长驱直入
class Skill1036(Skill):
    def __init__(self):
        super().__init__(1036,'长驱直入','对敌军前锋发动攻击(伤害率240%)，并使其陷入混乱1回合',2,40,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        enemy = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        targets = [e for e in enemy if e.pos_name=='前锋' and e.arms>0]
        if not targets:
            targets = hero.get_target(sk.limit, 1)
        for t in targets:
            t.be_hurt(hero, {'type':1,'rate':240}, sk)
            if not t.is_confusion():
                t.state['confusion'] = {'rounds':1,'from':{'hero':hero,'skill':sk}}
                hero.manager.log_action(hero,t,f'的【{sk.name}】使','陷入混乱1回合',1)
        return True


# ─── 1037 乘胜追击
class Skill1037(Skill):
    def __init__(self):
        super().__init__(1037,'乘胜追击','击杀敌军后，对剩余敌军随机单体追加攻击(伤害率150%)，最多触发2次',3,50,True,0)
    def call(self, hero, target=None):
        if target is None: return
        if not get_random_bool(self.rate): return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        if target.arms <= 0:
            enemy = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
            alive = [e for e in enemy if e.arms>0]
            count = min(2, len(alive))
            for e in random.sample(alive, count):
                e.be_hurt(hero, {'type':1,'rate':150}, sk)


# ─── 1038 弱敌之计
class Skill1038(Skill):
    def __init__(self):
        super().__init__(1038,'弱敌之计','使敌军群体攻击属性降低25，谋略属性降低25，持续3回合',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        sub_val = calc_skill_addition_rate(25, 0.2, hero.attrs['int'])
        enemy = hero.get_target(4, 2)
        for e in enemy:
            def make_debuff(e=e):
                orig_atk = e.attrs['atk']
                orig_int = e.attrs['int']
                e.attrs['atk'] = keep_two_decimal(e.attrs['atk'] - sub_val)
                e.attrs['int'] = keep_two_decimal(e.attrs['int'] - sub_val)
                hero.manager.log_action(hero,e,f'【{sk.name}】使',f'攻击降低{sub_val}({e.attrs["atk"]}) 谋略降低{sub_val}({e.attrs["int"]})',1)
                round_start_count = [0]
                def restore():
                    round_start_count[0] += 1
                    if round_start_count[0] >= 3:
                        e.attrs['atk'] = keep_two_decimal(e.attrs['atk'] + sub_val)
                        e.attrs['int'] = keep_two_decimal(e.attrs['int'] + sub_val)
                        e.clear_hook('回合开始时', f'{hero.id}_{sk.id}_弱敌恢复_{id(e)}')
                e.add_hook('回合开始时', f'{hero.id}_{sk.id}_弱敌恢复_{id(e)}', restore, sk, hero)
            make_debuff()


# ─── 1039 铁壁防守
class Skill1039(Skill):
    def __init__(self):
        super().__init__(1039,'铁壁防守','使自身防御属性提高50，受到伤害时有40%几率免疫该次伤害',4,'--',True,1)
    def call(self, hero, target=None):
        if hero.manager.round != -1: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        add_val = calc_skill_addition_rate(50, 0.5, hero.attrs['def'])
        hero.attrs['def'] = keep_two_decimal(hero.attrs['def'] + add_val)
        hero.manager.log_action(hero,hero,f'【{sk.name}】使',f'防御提高{add_val}({hero.attrs["def"]})',1)
        def on_hurt(attacker, dmg_info, skill):
            if get_random_bool(hero.get_real_skill_rate(40)):
                dmg_info['immune'] = True
                hero.manager.log(hero,'规避了本次伤害',1)
        hero.add_hook('受伤时','铁壁防守规避', on_hurt, sk, hero)


# ─── 1040 万夫莫当
class Skill1040(Skill):
    def __init__(self):
        super().__init__(1040,'万夫莫当','对敌军单体发动猛攻(伤害率320%)，并使其陷入犹豫1回合',2,35,True,3)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        targets = hero.get_target(sk.limit, 1)
        if not targets: return
        t = targets[0]
        t.be_hurt(hero, {'type':1,'rate':320}, sk)
        if not t.is_active_limit():
            t.state['activeLimit'] = {'rounds':1,'from':{'hero':hero,'skill':sk}}
            hero.manager.log_action(hero,t,f'的【{sk.name}】使','陷入犹豫1回合',1)
        return True


# ─── 1041 草船借箭
class Skill1041(Skill):
    def __init__(self):
        super().__init__(1041,'草船借箭','1回合准备，使自身受到攻击时每次反弹伤害(伤害率80%)，持续2回合，期间每回合恢复兵力(恢复率60%)',2,45,True,5)
    def call(self, hero, target=None):
        if not get_random_bool(self.rate): return
        sk = self
        reflect_rate = calc_skill_addition_rate(80, 0.7, hero.attrs['int'])
        recover_rate = calc_skill_addition_rate(60, 0.55, hero.attrs['int'])
        def subskill():
            hero.manager.log(hero,f'发动【{sk.name}】效果')
            rounds_left = [2]
            tag_reflect = f'{hero.id}_{sk.id}_草船反弹'
            tag_recover = f'{hero.id}_{sk.id}_草船恢复'
            def on_hurt(attacker, dmg_info, skill):
                if dmg_info.get('type') == 1:
                    dmg = calc_attack_damage(hero, attacker, {'type':1,'rate':reflect_rate}, sk)
                    attacker.be_hurt_by_num(hero, {'type':1,'rate':reflect_rate}, sk, dmg, lambda: None)
                    hero.manager.log_action(hero,attacker,'草船借箭反弹',f'伤害{dmg}({attacker.arms})',1)
            def on_round_start():
                rounds_left[0] -= 1
                hero.recover(calc_recover(hero, recover_rate, 0.5, 2), hero, sk.name)
                if rounds_left[0] <= 0:
                    hero.clear_hook('受伤时', tag_reflect)
                    hero.clear_hook('回合开始时', tag_recover)
            hero.add_hook('受伤时', tag_reflect, on_hurt, sk, hero, 'debuff')
            hero.add_hook('回合开始时', tag_recover, on_round_start, sk, hero)
        hero.add_ready_skill(sk, 1, subskill)
        return True


# ─── 1042 连环计
class Skill1042(Skill):
    def __init__(self):
        super().__init__(1042,'连环计','对敌军全体施加连环标记，标记期间每次受到伤害时对相邻敌军传递30%伤害，持续2回合',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        enemy_list = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        for e in enemy_list:
            def make_chain(e=e):
                tag = f'{hero.id}_{sk.id}_连环_{id(e)}'
                def on_hurt(attacker, dmg_info, skill):
                    if skill is sk: return
                    chain_dmg = int(dmg_info.get('damage', 0) * 0.3)
                    if chain_dmg <= 0: return
                    idx = enemy_list.index(e)
                    neighbors = []
                    if idx > 0 and enemy_list[idx-1].arms > 0: neighbors.append(enemy_list[idx-1])
                    if idx < len(enemy_list)-1 and enemy_list[idx+1].arms > 0: neighbors.append(enemy_list[idx+1])
                    for nb in neighbors:
                        nb.be_hurt_by_num(hero, {'type':3,'rate':30}, sk, chain_dmg, lambda: None)
                        hero.manager.log_action(e,nb,'连环传递伤害',f'{chain_dmg}({nb.arms})',1)
                e.add_hook('受伤时', tag, on_hurt, sk, hero, 'debuff')
            make_chain()
            hero.manager.log(e,'的连环效果已施加',1)
        def clear_chain():
            if hero.manager.round >= 3:
                for e in enemy_list:
                    e.clear_hook('受伤时', f'{hero.id}_{sk.id}_连环_{id(e)}')
        hero.add_hook('回合开始时','连环计清除', clear_chain, sk, hero)


# ─── 1043 八卦阵
class Skill1043(Skill):
    def __init__(self):
        super().__init__(1043,'八卦阵','使我军全体进入八卦阵，受到攻击伤害降低20%，有30%几率使攻击者陷入混乱1回合，持续整场战斗',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        sub_val = calc_skill_addition_rate(20, 0.12, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('beAttackDamageSub', sub_val, -1, sk, hero)
            def make_hook(e=e):
                def on_hurt(attacker, dmg_info, skill):
                    if dmg_info.get('type') == 1 and get_random_bool(hero.get_real_skill_rate(30)):
                        if not attacker.is_confusion():
                            attacker.state['confusion'] = {'rounds':1,'from':{'hero':hero,'skill':sk}}
                            hero.manager.log_action(hero,attacker,f'【{sk.name}】使','陷入混乱1回合',1)
                e.add_hook('受伤时',f'八卦阵反制_{id(e)}', on_hurt, sk, hero)
            make_hook()


# ─── 1044 驱虎吞狼
class Skill1044(Skill):
    def __init__(self):
        super().__init__(1044,'驱虎吞狼','使敌军单体陷入暴走状态，攻击随机目标，持续2回合；暴走结束后对其施加谋略攻击(伤害率160%)',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        targets = hero.get_target(4, 1)
        if not targets: return
        t = targets[0]
        value = calc_skill_addition_rate(160, 1.2, hero.attrs['int'])
        if not t.is_confusion():
            t.state['confusion'] = {'rounds':2,'from':{'hero':hero,'skill':sk}}
            hero.manager.log_action(hero,t,f'的【{sk.name}】使','陷入暴走2回合',1)
        else:
            hero.manager.log(t,'已存在混乱效果',1)
        def delayed_hit():
            if hero.manager.round == 3:
                t.clear_hook('回合开始时', f'{hero.id}_{sk.id}_驱虎延迟')
                t.be_hurt(hero, {'type':2,'rate':value}, sk)
                hero.manager.log_action(hero,t,'驱虎吞狼延迟谋略攻击',f'{value}%',1)
        t.add_hook('回合开始时', f'{hero.id}_{sk.id}_驱虎延迟', delayed_hit, sk, hero, 'debuff')


# ─── 1045 以逸待劳
class Skill1045(Skill):
    def __init__(self):
        super().__init__(1045,'以逸待劳','前2回合不行动，第3回合起攻击伤害和谋略伤害提高50%，并对全体敌军发动一次攻击(伤害率200%)',1,'--',True,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        boost_val = calc_skill_addition_rate(50, 0.3, hero.attrs['int'])
        def sub():
            if hero.manager.round == 3:
                hero.add_state('attackDamageAdd', boost_val, -1, sk, hero)
                hero.add_state('inteDamageAdd',   boost_val, -1, sk, hero)
                hero.manager.log_action(hero,hero,f'【{sk.name}】生效','攻击和谋略伤害提高50%',1)
                enemy = hero.get_target(5, 2)
                for e in enemy:
                    e.be_hurt(hero, {'type':1,'rate':200}, sk)
                hero.clear_hook('回合开始时', f'{hero.id}_{sk.id}_以逸待劳')
        hero.add_hook('回合开始时', f'{hero.id}_{sk.id}_以逸待劳', sub, sk, hero)


# ──────────────── SKILLS 注册表 ────────────────
SKILLS = {
    1001: Skill1001(),
    1002: Skill1002(),
    1003: Skill1003(),
    1004: Skill1004(),
    1005: Skill1005(),
    1006: Skill1006(),
    1007: Skill1007(),
    1008: Skill1008(),
    1009: Skill1009(),
    1010: Skill1010(),
    1011: Skill1011(),
    1012: Skill1012(),
    1013: Skill1013(),
    1014: Skill1014(),
    1015: Skill1015(),
    1016: Skill1016(),
    1017: Skill1017(),
    1018: Skill1018(),
    1019: Skill1019(),
    1020: Skill1020(),
    1021: Skill1021(),
    1022: Skill1022(),
    1023: Skill1023(),
    1024: Skill1024(),
    1025: Skill1025(),
    1026: Skill1026(),
    1027: Skill1027(),
    1028: Skill1028(),
    1029: Skill1029(),
    1030: Skill1030(),
    1031: Skill1031(),
    1032: Skill1032(),
    1033: Skill1033(),
    1034: Skill1034(),
    1035: Skill1035(),
    1036: Skill1036(),
    1037: Skill1037(),
    1038: Skill1038(),
    1039: Skill1039(),
    1040: Skill1040(),
    1041: Skill1041(),
    1042: Skill1042(),
    1043: Skill1043(),
    1044: Skill1044(),
    1045: Skill1045(),
}
                
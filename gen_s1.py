# -*- coding: utf-8 -*-
# 生成 skills_s.py 第一批：S级指挥战法
code = """
class Skill200014(Skill):
    def __init__(self):
        super().__init__(200014,'酒池肉林','战斗前2回合我军全体受到伤害降低32%，第4回合起攻击伤害恢复35%兵力',1,'--',False,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        hero.manager.log(hero,f'发动【{self.name}】')
        sk = self
        sv = calc_skill_addition_rate(32, 0.28, hero.attrs['def'])
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.add_state('beAttackDamageSub', sv, 2, sk, hero)
            e.add_state('beInteDamageSub', sv, 2, sk, hero)
        def on_action():
            if hero.manager.round >= 4:
                def on_dmg(t, di, sk2, dmg):
                    if di.get('type')==1: hero.recover(int(dmg*0.35), hero, sk.name)
                hero.add_hook('造成伤害后', f'{sk.id}_d', on_dmg, sk, hero)
                hero.clear_hook('行动时', f'{sk.id}_w')
        hero.add_hook('行动时', f'{sk.id}_w', on_action, sk, hero)


class Skill200015(Skill):
    def __init__(self):
        super().__init__(200015,'洛水佳人','本场战斗中我军全体每回合80%几率恢复兵力(恢复率90%)',1,'--',False,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        rv = calc_skill_addition_rate(90, 0.8, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            def ms(e=e):
                def sub():
                    if get_random_bool(hero.get_real_skill_rate(80)): e.recover(calc_recover(hero, rv, 0.8, 2), hero, sk.name)
                e.add_hook('回合开始时', f'{sk.id}_{id(e)}', sub, sk, hero)
            ms()


class Skill200016(Skill):
    def __init__(self):
        super().__init__(200016,'皇裔流离','本场战斗中我军全体受伤时50%几率恢复兵力(恢复率68%)',1,'--',False,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        rv = calc_skill_addition_rate(68, 0.6, hero.attrs['int'])
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            def ms(e=e):
                def sub(a, di, s):
                    if get_random_bool(hero.get_real_skill_rate(50)): e.recover(calc_recover(hero, rv, 0.6, 2), hero, sk.name)
                e.add_hook('受伤时', f'{sk.id}_{id(e)}', sub, sk, hero)
            ms()


class Skill200023(Skill):
    def __init__(self):
        super().__init__(200023,'魏武之世','使敌军全体属性下降15%，我军全体攻击距离+1',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        sr = calc_skill_addition_rate(15, 0.045, hero.attrs['int'])
        enemy = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        for e in enemy:
            for k in ('atk','def','int','spd'): e.attrs[k] = keep_two_decimal(e.attrs[k] - e.attrs[k]*sr/100)


class Skill200027(Skill):
    def __init__(self):
        super().__init__(200027,'其疾如风','前3回合我军全体速度+41，每回合70%几率连击',1,'--',False,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        sa = keep_two_decimal(41 + max(0, hero.attrs['spd']-80)*0.075)
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team:
            e.attrs['spd'] = keep_two_decimal(e.attrs['spd']+sa)
            def ms(e=e):
                def sub():
                    if 1<=hero.manager.round<=3 and get_random_bool(hero.get_real_skill_rate(70)):
                        if e.state.get('doubleAttack',{}).get('rounds',0)<=0:
                            e.state['doubleAttack']={'rounds':1,'from':{'hero':hero,'skill':sk}}
                e.add_hook('普攻前', f'{sk.id}_{id(e)}', sub, sk, hero)
            ms()


class Skill200194(Skill):
    def __init__(self):
        super().__init__(200194,'避其锋芒','前3回合我军群体受到攻击和谋略伤害降低30%',1,'--',True,2)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self; v=calc_skill_addition_rate(30,0.15,hero.attrs['int'])
        for e in hero.get_target(2,2,2):
            e.add_state('beAttackDamageSub',v,3,sk,hero); e.add_state('beInteDamageSub',v,3,sk,hero)


class Skill200198(Skill):
    def __init__(self):
        super().__init__(200198,'大赏三军','前3回合我军群体攻击和谋略伤害提高30%',1,'--',True,3)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self; v=calc_skill_addition_rate(30,0.15,hero.attrs['int'])
        for e in hero.get_target(3,2,2):
            e.add_state('attackDamageAdd',v,3,sk,hero); e.add_state('inteDamageAdd',v,3,sk,hero)


class Skill200201(Skill):
    def __init__(self):
        super().__init__(200201,'无心恋战','前3回合敌军群体攻击和谋略伤害降低30%',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self; v=calc_skill_addition_rate(30,0.15,hero.attrs['int'])
        for e in hero.get_target(4,2):
            e.add_state('attackDamageSub',v,3,sk,hero); e.add_state('inteDamageSub',v,3,sk,hero)


class Skill200204(Skill):
    def __init__(self):
        super().__init__(200204,'神兵天降','前3回合敌军群体受到攻击和谋略伤害提高30%',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self; v=calc_skill_addition_rate(30,0.15,hero.attrs['int'])
        for e in hero.get_target(4,2):
            e.add_state('beAttackDamageAdd',v,3,sk,hero); e.add_state('beInteDamageAdd',v,3,sk,hero)


class Skill200220(Skill):
    def __init__(self):
        super().__init__(200220,'反计之策','前3回合敌军群体主动战法伤害大幅下降，首回合100%犹豫',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        for e in hero.get_target(4,2):
            e.add_state('activeDamageSub',9999,3,sk,hero)
            if not e.is_active_limit(): e.state['activeLimit']={'rounds':1,'from':{'hero':hero,'skill':sk}}


class Skill200228(Skill):
    def __init__(self):
        super().__init__(200228,'战必断金','前3回合敌军群体每回合90%几率陷入怯战',1,'--',True,4)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self
        et = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        def sub():
            if hero.manager.round>3: return
            for e in et:
                if e.arms>0 and get_random_bool(hero.get_real_skill_rate(90)) and not e.is_attack_limit():
                    e.state['attackLimit']={'rounds':1,'from':{'hero':hero,'skill':sk}}
        hero.add_hook('回合开始时',f'{sk.id}_断金',sub,sk,hero)


class Skill200246(Skill):
    def __init__(self):
        super().__init__(200246,'缓师徐持','每回合已行动敌军受伤后属性下降16，可叠加',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self; sv=calc_skill_addition_rate(16,0.12,hero.attrs['int'])
        et = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        for e in et:
            def ms(e=e):
                acted=[False]
                def oa(): acted[0]=True
                def oh(a,di,s):
                    if acted[0]:
                        for k in ('atk','def','int','spd'): e.attrs[k]=keep_two_decimal(max(0,e.attrs[k]-sv))
                def rst(): acted[0]=False
                e.add_hook('行动时',f'{sk.id}_a_{id(e)}',oa,sk,hero)
                e.add_hook('受伤时',f'{sk.id}_h_{id(e)}',oh,sk,hero)
                e.add_hook('回合开始时',f'{sk.id}_r_{id(e)}',rst,sk,hero)
            ms()


class Skill200254(Skill):
    def __init__(self):
        super().__init__(200254,'衔命建功','敌军每回合首次受持续伤害时50%几率谋略攻击，第3回合起额外触发',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self; v=calc_skill_addition_rate(140,0.8,hero.attrs['int'])
        hero.storage=getattr(hero,'storage',{}); hero.storage[sk.id]={'hero':{}}
        et = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        for e in et:
            def mh(e=e):
                def od(s2,sh,dmg,di,dn):
                    if hero.manager.round>=3: e.be_hurt_by_num(hero,di,sk,dmg,lambda:None)
                def oh(a,di,s2):
                    k=e.camp+e.pos_name+e.name
                    if di.get('type') in (3,4) and not hero.storage[sk.id]['hero'].get(k):
                        hero.storage[sk.id]['hero'][k]=True
                        if get_random_bool(hero.get_real_skill_rate(50)): e.be_hurt(hero,{'type':2,'rate':v},sk)
                e.add_hook('被施加持续伤害时',f'{sk.id}_d_{id(e)}',od,sk,hero,'debuff')
                e.add_hook('受伤时',f'{sk.id}_h_{id(e)}',oh,sk,hero,'debuff')
            mh()
        hero.add_hook('回合开始时',f'{sk.id}_reset',lambda:hero.storage[sk.id].update({'hero':{}}),sk,hero)


class Skill200255(Skill):
    def __init__(self):
        super().__init__(200255,'伏波扬砂','我军全体普攻伤害提升25%，累积4层后触发连击',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self; v=calc_skill_addition_rate(25,0.18,hero.attrs['atk'])
        team = hero.manager.blue_team['heros'] if hero.camp=='blue' else hero.manager.red_team['heros']
        for e in team: e.add_state('attackDamageAdd',v,-1,sk,hero)
        stk=[0]
        def od(t,di,s2,dmg):
            stk[0]+=1
            if stk[0]>=4:
                stk[0]=0
                if hero.state.get('doubleAttack',{}).get('rounds',0)<=0:
                    hero.state['doubleAttack']={'rounds':1,'from':{'hero':hero,'skill':sk}}
        hero.add_hook('造成伤害后',f'{sk.id}_stk',od,sk,hero)


class Skill200257(Skill):
    def __init__(self):
        super().__init__(200257,'审时定计','敌军被施加负面效果时50%提升受到伤害30%',1,'--',False,5)
    def call(self, hero, target=None):
        if hero.manager.round != 0: return
        sk = self; v=calc_skill_addition_rate(30,0.2,hero.attrs['int']); rv=calc_skill_addition_rate(65,0.6,hero.attrs['int'])
        et = hero.manager.red_team['heros'] if hero.camp=='blue' else hero.manager.blue_team['heros']
        for e in et:
            def me(e=e):
                def od(s2,src):
                    if 

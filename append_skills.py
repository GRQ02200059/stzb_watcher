# -*- coding: utf-8 -*-
code = """

# S级追击战法

class Skill200251(Skill):
    def __init__(self):
        super().__init__(200251,'烈火焚舟','普攻后使目标燃烧(伤害率150%)持续2回合；若已有燃烧则引爆并施加更强燃烧(270%)',3,120,False,0)
    def call(self, hero, target=None):
        if target is None: return
        sk = self
        fire_val  = calc_skill_addition_rate(150, 1.3, hero.attrs['int'])
        fire_val2 = calc_skill_addition_rate(270, 2.4, hero.attrs['int'])
        tag = str(hero.id)+'_'+str(sk.id)+'_burn_'+str(id(target))
        if target.has_hook('回合开始时', tag):
            target.clear_hook('回合开始时', tag)
            target.be_hurt(hero, {'type':4,'rate':fire_val2}, sk)
        else:
            rounds = [2]
            def burn(rounds=rounds, tag=tag, target=target):
                rounds[0] -= 1
                target.be_hurt(hero, {'type':4,'rate':fire_val}, sk)
                if rounds[0] <= 0:
                    target.clear_hook('回合开始时', tag)
            target.add_hook('回合开始时', tag, burn, sk, hero, 'debuff')


class Skill200980(Skill):
    def __init__(self):
        super().__init__(200980,'乘胜追击','普攻后对目标攻击(伤害率150%)，60%几率再次攻击，此概率每次降低20%',3,30,True,0)
    def call(self, hero, target=None):
        if target is None: return
        if not get_random_bool(self.rate): return
        sk = self
        val  = calc_skill_addition_rate(150, 1.3, hero.attrs['atk'])
        val2 = calc_skill_addition_rate(100, 0.9, hero.attrs['atk'])
        target.be_hurt(hero, {'type':1,'rate':val}, sk)
        chance = 60
        while chance > 0 and get_random_bool(hero.get_real_skill_rate(chance)):
            target.be_hurt(hero, {'type':1,'rate':val2}, sk)
            chance -= 20


class Skill200986(Skill):
    def __init__(self):
        super().__init__(200986,'盛气横凌','普攻后猛攻目标(伤害率260%)；士气高昂则混乱1回合，否则额外攻击',3,50,False,0)
    def call(self, hero, target=None):
        if target is None: return
        if not get_random_bool(self.rate): return
        sk = self
        val  = calc_skill_addition_rate(260, 2.4, hero.attrs['atk'])
        val2 = calc_skill_addition_rate(get_random_int(80,160), 1.2, hero.attrs['atk'])
        target.be_hurt(hero, {'type':1,'rate':val}, sk)
        morale = getattr(target, 'morale', 100)
        if morale >= 100:
            if not target.is_confusion():
                target.state['confusion'] = {'rounds':1,'from':{'hero':hero,'skill':sk}}
        else:
            target.be_hurt(hero, {'type':1,'rate':val2}, sk)


SKILLS_S = {}
_skill_classes = [
    Skill200003, Skill200013, Skill200014, Skill200015, Skill200016,
    Skill200021, Skill200023, Skill200024, Skill200027, Skill200030,
    Skill200070, Skill200194, Skill200198, Skill200201, Skill200204,
    Skill200220, Skill200228, Skill200235, Skill200237, Skill200246,
    Skill200251, Skill200252, Skill200254, Skill200255, Skill200257,
    Skill200262, Skill200264, Skill200268, Skill200603, Skill200622,
    Skill200647, Skill200648, Skill200687, Skill200692, Skill200693,
    Skill200694, Skill200715, Skill200737, Skill200755, Skill200757,
    Skill200769, Skill200773, Skill200800, Skill200801, Skill200805,
    Skill200824, Skill200828, Skill200863, Skill200882, Skill200884,
    Skill200886, Skill200898, Skill200899, Skill200900, Skill200915,
    Skill200938, Skill200942, Skill200952, Skill200955, Skill200957,
    Skill200960, Skill200961, Skill200966, Skill200979, Skill200980,
    Skill200983, Skill200984, Skill200985, Skill200986, Skill200987,
    Skill200991, Skill200993, Skill201006,
]
for _cls in _skill_classes:
    _obj = _cls()
    SKILLS_S[_obj.id] = _obj
"""

with open('D:/nettest/battle_sim/skills_s.py', 'a', encoding='utf-8') as f:
    f.write(code)
print('done')


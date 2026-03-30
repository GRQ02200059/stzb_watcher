# -*- coding: utf-8 -*-
"""
战斗武将对象 - 参考 stzbBattleSimulator 完整实现
"""
import math
import random
from .calc import (
    keep_two_decimal, calc_attack_damage, calc_inte_damage, calc_inte2_damage,
    calc_recover, get_random_bool, get_random_int, calc_skill_addition_rate
)


def _make_skill_tag(hero, skill, name):
    hid = getattr(hero, 'id', 0)
    sid = getattr(skill, 'id', 0) if skill else 0
    return f'{hid}_{sid}_{name}'


CAN_ADD_ATTRS_KEY = ['atk', 'def', 'int', 'spd']


class BattleHero:
    def __init__(self, config, camp, manager, morale=100):
        self.manager   = manager
        self.camp      = camp
        self.morale    = morale
        self.pos_name  = '大营'
        self.index     = 0

        # Hook 执行堆
        self.hooks = {
            'ON_HURT':              {},
            'BEFORE_BASEATK':       {},
            'ON_ACTION':            {},
            'BEFORE_ACTION':        {},
            'ROUND_START':          {},
            'BEFORE_ATK':           {},
            'AFTER_ATK':            {},
            'ON_DAMAGE':            {},
            'AFTER_DAMAGE':         {},
            'START_READY_SKILL':    {},
            'ADD_CONTINUOUS_DAMAGE':{},
        }
        self._hook_name_map = {
            '受伤时':          'ON_HURT',
            '普攻前':          'BEFORE_BASEATK',
            '行动时':          'ON_ACTION',
            '行动前':          'BEFORE_ACTION',
            '回合开始时':      'ROUND_START',
            '攻击前':          'BEFORE_ATK',
            '攻击后':          'AFTER_ATK',
            '造成伤害时':      'ON_DAMAGE',
            '造成伤害后':      'AFTER_DAMAGE',
            '开始准备战法时':  'START_READY_SKILL',
            '被施加持续伤害时':'ADD_CONTINUOUS_DAMAGE',
        }

        self.in_ready_skill = []  # [{skill, round, func}]
        self.counter  = {}
        self.storage  = {}
        self.rate_val = {}   # {skill_id: {value, rounds}}
        self.ready_val= {}   # {skill_id: {skip, from}}

        self.state_flag = {
            'confusion':   False,
            'attackLimit': False,
            'activeLimit': False,
        }

        self.state = {
            'attackNum': 0,
            'firstAction':  {'rounds': 0, 'from': {'hero': None, 'skill': None}},
            'doubleAttack': {'rounds': 0, 'from': {'hero': None, 'skill': None}},
            'confusion':    {'rounds': 0, 'from': {'hero': None, 'skill': None}},
            'activeLimit':  {'rounds': 0, 'from': {'hero': None, 'skill': None}},
            'attackLimit':  {'rounds': 0, 'from': {'hero': None, 'skill': None}},
            'attackDamageAdd':  {'passive': None, 'command': None, 'active': None, 'pursuit': None},
            'inteDamageAdd':    {'passive': None, 'command': None, 'active': None, 'pursuit': None},
            'activeDamageAdd':  {'passive': None, 'command': None, 'active': None, 'pursuit': None},
            'attackDamageSub':  {'passive': None, 'command': None, 'active': None, 'pursuit': None},
            'inteDamageSub':    {'passive': None, 'command': None, 'active': None, 'pursuit': None},
            'activeDamageSub':  {'passive': None, 'command': None, 'active': None, 'pursuit': None},
            'beAttackDamageAdd':{'passive': None, 'command': None, 'active': None, 'pursuit': None},
            'beInteDamageAdd':  {'passive': None, 'command': None, 'active': None, 'pursuit': None},
            'beActiveDamageAdd':{'passive': None, 'command': None, 'active': None, 'pursuit': None},
            'beAttackDamageSub':{'passive': None, 'command': None, 'active': None, 'pursuit': None},
            'beInteDamageSub':  {'passive': None, 'command': None, 'active': None, 'pursuit': None},
            'beActiveDamageSub':{'passive': None, 'command': None, 'active': None, 'pursuit': None},
        }

        self._init_attrs(config)
        self._init_skills(config)

    # ──────────────── 初始化 ────────────────
    def _init_attrs(self, config):
        from .data import HEROS
        hero = HEROS[config['id']]
        self.id    = config['id']
        self.name  = hero['name']
        self.up    = config.get('up', 0)
        self.camp_id = hero['camp']
        self.army  = hero['army']
        self.skill_id = hero['skill']
        self.level = config['level']
        self.limit = hero['limit']
        self.arms  = 5000 + self.level * 100 + self.up * 200
        self.hurt_arms = 0

        self.attrs = {k: 0.0 for k in ['atk','def','int','spd','des']}
        self.origin_attrs = {k: 0.0 for k in ['atk','def','int','spd','arms']}

        # 属性成长
        for key, base in hero['attrs'].items():
            grow = hero['attrs_grow'].get(key, 0)
            self.attrs[key] = keep_two_decimal(base + (self.level - 1) * grow)

        # 额外属性分配
        for key, val in config.get('extra_attrs', {}).items():
            self.attrs[key] = keep_two_decimal(self.attrs[key] + val)

        # 四大营加成
        for key in CAN_ADD_ATTRS_KEY:
            self.attrs[key] = keep_two_decimal(self.attrs[key] + 20)

        # 保存原始属性
        for key in ['atk','def','int','spd']:
            self.origin_attrs[key] = self.attrs[key]
        self.origin_attrs['arms'] = self.arms

    def _init_skills(self, config):
        from .data import SKILLS
        self.skills = {}
        self.skills_order = []
        main_sk = SKILLS.get(self.skill_id)
        if main_sk:
            self.skills[self.skill_id] = main_sk
            self.skills_order.append(self.skill_id)
        for sid in config.get('equip_skills', []):
            if sid and sid > 0:
                sk = SKILLS.get(sid)
                if sk:
                    self.skills[sid] = sk
                    self.skills_order.append(sid)

    # ──────────────── Hook 系统 ────────────────
    def _get_hook_dict(self, on):
        key = self._hook_name_map.get(on)
        return self.hooks.get(key)

    def add_hook(self, on, name, func, skill, hero, type_='buff', can_clear=True):
        obj = self._get_hook_dict(on)
        if obj is None:
            return None
        tag = _make_skill_tag(hero, skill, name)
        obj[tag] = {'call': func, 'type': type_, 'skill': skill, 'hero': hero, 'can_clear': can_clear}
        return tag

    def clear_hook(self, on, tag):
        obj = self._get_hook_dict(on)
        if obj and tag in obj:
            del obj[tag]

    def call_hook(self, on, *args):
        obj = self._get_hook_dict(on)
        if not obj:
            return
        for key in list(obj.keys()):
            if self.manager.over:
                break
            if key in obj:
                try:
                    obj[key]['call'](*args)
                except Exception:
                    pass

    # ──────────────── 计数器 ────────────────
    def count_add(self, tag, num=1):
        self.counter[tag] = self.counter.get(tag, 0) + num

    def count_sub(self, tag, num=1):
        if tag in self.counter:
            self.counter[tag] -= num

    def count_reset(self, tag):
        self.counter[tag] = 0

    def count_get(self, tag):
        return self.counter.get(tag, 0)

    # ──────────────── 状态系统 ────────────────
    def _get_skill_type_name(self, skill_type):
        return {1:'command', 2:'active', 3:'pursuit', 4:'passive'}.get(skill_type, 'passive')

    def _get_state_limit(self, name):
        # 1=同类型不叠加
        return 1

    def add_state(self, name, value, rounds, from_skill, from_hero, stack=False, state_type=1):
        type_name = self._get_skill_type_name(getattr(from_skill, 'skill_type', 4))
        limit = self._get_state_limit(name)
        state = self.state.get(name)
        if state is None:
            return None
        existing = state.get(type_name)
        if existing:
            if stack:
                existing['value'] += value
                return existing
            elif limit == 2 and existing['value'] >= value:
                return None
        state[type_name] = {
            'value': value,
            'rounds': rounds,
            'from': from_skill,
            'hero': from_hero,
            'type': state_type,
        }
        return state[type_name]

    def del_state(self, name, from_skill, by_hero=False):
        type_name = self._get_skill_type_name(getattr(from_skill, 'skill_type', 4))
        state = self.state.get(name)
        if state and state.get(type_name):
            state[type_name] = None

    def get_state(self, name, skill_type, as_obj=False):
        type_name = self._get_skill_type_name(skill_type)
        state = self.state.get(name, {})
        obj = state.get(type_name)
        if as_obj:
            return obj
        return obj['value'] if obj else 0

    def get_damage_state_value(self, name):
        total = 0
        state = self.state.get(name, {})
        for v in state.values():
            if v:
                total += v.get('value', 0)
        return total

    # ──────────────── 状态回合递减 ────────────────
    def clear_state_rounds(self):
        self._clear_single_actioned_state('confusion')
        self._clear_single_actioned_state('attackLimit')
        self._clear_single_actioned_state('activeLimit')
        self._clear_single_simple_state('doubleAttack')
        for s in ['attackDamageAdd','inteDamageAdd','activeDamageAdd',
                  'attackDamageSub','inteDamageSub','activeDamageSub',
                  'beAttackDamageAdd','beInteDamageAdd','beActiveDamageAdd',
                  'beAttackDamageSub','beInteDamageSub','beActiveDamageSub']:
            self._clear_damage_state_rounds(s)

    def _clear_single_actioned_state(self, name):
        if self.state_flag.get(name):
            self.state_flag[name] = False
            st = self.state.get(name)
            if st and st.get('rounds', 0) > 0:
                st['rounds'] -= 1
                if st['rounds'] == 0:
                    st['from'] = {'hero': None, 'skill': None}

    def _clear_single_simple_state(self, name):
        st = self.state.get(name)
        if st and st.get('rounds', 0) > 0:
            st['rounds'] -= 1
            if st['rounds'] == 0:
                st['from'] = {'hero': None, 'skill': None}

    def _clear_damage_state_rounds(self, name):
        state = self.state.get(name, {})
        for key in list(state.keys()):
            obj = state.get(key)
            if not obj:
                continue
            if obj['rounds'] != -1 and obj['rounds'] > 0 and obj.get('type', 1) == 1:
                obj['rounds'] -= 1
            elif obj['rounds'] == 0:
                state[key] = None

    # ──────────────── 状态判断 ────────────────
    def is_confusion(self):
        if self.state['confusion']['rounds'] > 0:
            self.in_ready_skill = []
            return True
        return False

    def is_attack_limit(self):
        return self.state['attackLimit']['rounds'] > 0

    def is_active_limit(self):
        if self.state['activeLimit']['rounds'] > 0:
            self.in_ready_skill = []
            return True
        return False

    def skip_round_by_confusion(self):
        self.manager.log(self, '陷入混乱无法行动')
        self.state_flag['confusion'] = True

    def skip_attack_by_attack_limit(self):
        self.manager.log(self, '陷入怯战无法进行攻击')
        self.state_flag['attackLimit'] = True

    def skip_attack_by_active_limit(self):
        self.manager.log(self, '陷入犹豫无法发动战法')
        self.state_flag['activeLimit'] = True

    # ──────────────── 准备型战法 ────────────────
    def can_add_ready_skill(self, skill):
        """检查准备型战法是否可发动（无正在准备中的同一战法）"""
        for rs in self.in_ready_skill:
            if rs.get('skill') and rs['skill'].id == skill.id:
                return False
        return True

    def add_ready_skill(self, skill, rounds, func):
        """添加准备型战法"""
        self.manager.log(self, f'的【{skill.name}】开始准备，需{rounds}回合')
        skip = self.ready_val.get(skill.id, {}).get('skip', 0)
        actual_rounds = max(0, rounds + skip)
        if actual_rounds == 0:
            func()
        else:
            self.in_ready_skill.append({'skill': skill, 'round': actual_rounds, 'func': func})
        self.call_hook('开始准备战法时', skill)

    def sub_ready_skill_round(self, skill):
        """减少准备型战法剩余回合"""
        for rs in self.in_ready_skill[:]:
            if rs.get('skill') and rs['skill'].id == skill.id:
                rs['round'] -= 1
                self.manager.log(self, f'的【{skill.name}】还需{rs["round"]}回合准备')
                if rs['round'] <= 0:
                    self.in_ready_skill.remove(rs)
                    rs['func']()

    # ──────────────── 发动率修正 ────────────────
    def get_real_skill_rate(self, base_rate):
        """根据士气修正发动率"""
        if base_rate is None or base_rate == '--':
            return 100
        morale = getattr(self, 'morale', 100)
        if morale >= 100:
            rate = base_rate + (morale - 100) * 0.3
        else:
            rate = base_rate * (morale / 100)
        return min(100, max(0, round(rate)))

    # ──────────────── 清除负面效果（镇静）────────────────
    def clear_debuff(self, skill, hero, types=None):
        if types is None:
            types = [2, 3]
        cleared = False
        for key in ['confusion', 'activeLimit', 'attackLimit']:
            if self.state[key]['rounds'] > 0:
                self.state[key]['rounds'] = 0
                self.manager.log(self, f'的{key}效果被消除了', 1)
                cleared = True
        if not cleared:
            self.manager.log(self, '没有效果可消除', 1)
        # 清除 hooks 里的负面 debuff
        for hook_dict in self.hooks.values():
            for tag in list(hook_dict.keys()):
                h = hook_dict[tag]
                sk = h.get('skill')
                if sk and getattr(sk, 'skill_type', 0) in types and h.get('type') == 'debuff' and h.get('can_clear', True):
                    del hook_dict[tag]

    # ──────────────── 获取攻击目标 ────────────────
    def _get_sorted_heros(self):
        """蓝队正序 + 红队倒序排列（距离计算用）"""
        blue = list(self.manager.blue_team['heros'])
        red  = list(reversed(self.manager.red_team['heros']))
        return [h for h in (blue + red) if h.arms > 0]

    def get_attack_target(self):
        heros = self._get_sorted_heros()
        self_idx = next((i for i, h in enumerate(heros) if h is self), None)
        if self_idx is None:
            return None
        candidates = [
            h for i, h in enumerate(heros)
            if h is not self
            and abs(self_idx - i) <= self.limit
            and h.camp != self.camp
            and h.arms > 0
        ]
        if not candidates:
            return None
        return random.choice(candidates)

    def get_target(self, range_, num, type_=1):
        """
        type_ 1=选敌军  2=选我军(含自己)  3=选友军(不含自己)
        """
        heros = self._get_sorted_heros()
        self_idx = next((i for i, h in enumerate(heros) if h is self), None)
        if self_idx is None:
            self_idx = 0
        candidates = []
        for i, h in enumerate(heros):
            if abs(self_idx - i) > range_:
                continue
            if h.arms <= 0:
                continue
            if type_ == 1 and h.camp != self.camp:
                candidates.append(h)
            elif type_ == 2 and h.camp == self.camp:
                candidates.append(h)
            elif type_ == 3 and h.camp == self.camp and h is not self:
                candidates.append(h)
        if len(candidates) <= num:
            return candidates
        random.shuffle(candidates)
        return candidates[:num]

    # ──────────────── 攻击 ────────────────
    def call_attack(self):
        target = self.get_attack_target()
        if target:
            self.call_hook('普攻前')
            self._basic_attack(target)
            self.state['attackNum'] += 1
            # 连击
            if self.state['doubleAttack']['rounds'] > 0 and self.state['attackNum'] < 2:
                self.call_attack()

    def _basic_attack(self, target):
        self.manager.log_action(self, target, '对', '发动普通攻击')
        target.be_hurt(self, {'type': 1, 'rate': 100})
        if self.manager.over:
            return
        self._call_pursuit_skill(target)

    # ──────────────── 受到伤害 ────────────────
    def be_hurt(self, attacker, dmg_info, skill=None, num=None):
        if self.manager.over:
            return
        attacker.call_hook('攻击前')
        if num is not None:
            damage = num
        else:
            t = dmg_info['type']
            if t == 1:
                damage = calc_attack_damage(attacker, self, dmg_info, skill)
            elif t == 2:
                damage = calc_inte_damage(attacker, self, dmg_info, skill)
            elif t == 4:
                damage = calc_inte2_damage(attacker, self, dmg_info, skill)
            else:
                damage = 0

        real_damage = min(damage, self.arms)
        self.arms -= real_damage
        self.manager.log(self, f'损失 {real_damage} 兵力({self.arms})', 1)

        if self.pos_name == '大营' and self.arms <= 0:
            self.manager.over = True
            self.manager.log(self, '大营阵亡，战斗结束！')

        attacker.call_hook('攻击后', attacker, self)
        attacker.call_hook('造成伤害后', attacker, self)

        # 伤兵
        self.hurt_arms += math.floor(real_damage * 0.95)
        if self.arms == 0:
            self.hurt_arms = math.floor(self.hurt_arms * 0.6)

        self.call_hook('受伤时', attacker, dmg_info, skill)

    def be_hurt_by_num(self, attacker, dmg_info, skill, num):
        self.be_hurt(attacker, dmg_info, skill, num)

    # ──────────────── 恢复 ────────────────
    def recover(self, recover_num, source, name):
        if self.arms == 0:
            return
        if self.hurt_arms == 0:
            recover_num = 0
        if recover_num >= self.hurt_arms:
            recover_num = self.hurt_arms
            self.hurt_arms = 0
        else:
            self.hurt_arms -= recover_num
        self.arms += recover_num
        self.manager.log_action(source, self, f'【{name}】的效果使', f'恢复了{recover_num}兵力({self.arms})', 1)

    # ──────────────── 战法执行 ────────────────
    def call_passive_skill(self):
        for sid in self.skills_order:
            sk = self.skills.get(sid)
            if sk and sk.skill_type == 4:
                self.manager.log(self, f'执行被动战法【{sk.name}】')
                try:
                    sk.call(self)
                except Exception:
                    pass

    def call_command_skill(self):
        for sid in self.skills_order:
            sk = self.skills.get(sid)
            if sk and sk.skill_type == 1:
                self.manager.log(self, f'执行指挥战法【{sk.name}】')
                try:
                    sk.call(self)
                except Exception:
                    pass

    def call_active_skill(self):
        if self.is_active_limit():
            self.skip_attack_by_active_limit()
            return
        for sid in self.skills_order:
            sk = self.skills.get(sid)
            if sk and sk.skill_type == 2:
                self.manager.log(self, f'执行主动战法【{sk.name}】')
                if self.can_add_ready_skill(sk):
                    base_rate = sk.rate
                    extra = self.rate_val.get(sk.id, {}).get('value', 0)
                    current_rate = self.get_real_skill_rate(base_rate if base_rate != '--' else 100) + extra
                    self.manager.log(self, f'的【{sk.name}】发动率为{current_rate}%')
                    ret = sk.call(self)
                    if ret is not True:
                        self.manager.log(self, f'的【{sk.name}】未发动')
                else:
                    self.sub_ready_skill_round(sk)

    def _call_pursuit_skill(self, target):
        for sid in self.skills_order:
            sk = self.skills.get(sid)
            if sk and sk.skill_type == 3:
                self.manager.log(self, f'执行追击战法【{sk.name}】')
                if target.arms > 0 and get_random_bool(sk.rate if sk.rate != '--' else 100):
                    try:
                        sk.call(self, target)
                    except Exception:
                        pass

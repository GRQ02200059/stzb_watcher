# -*- coding: utf-8 -*-
"""
战斗管理器
"""
import math
from .hero import BattleHero

BATTLE_ROUNDS = [-1, 0, 1, 2, 3, 4, 5, 6, 7, 8]

POS_NAMES_2 = ['大营', '中军']
POS_NAMES_3 = ['大营', '中军', '前锋']

class BattleManager:
    def __init__(self, config):
        """
        config = {
            'blue': {
                'morale': 100,
                'heros': [
                    {'id':1001,'level':40,'up':0,'equip_skills':[],'extra_attrs':{}},
                    ...
                ]
            },
            'red': { ... }
        }
        """
        self.over    = False
        self.round   = None
        self.records = []    # 战报日志列表（纯文本）
        self.structured_records = []  # 结构化战报

        self.blue_team = {'heros': [], 'arms': 0, 'hurt_arms': 0, 'total': 0}
        self.red_team  = {'heros': [], 'arms': 0, 'hurt_arms': 0, 'total': 0}
        self.sort_spd_heros = []

        blue_morale = config['blue'].get('morale', 100)
        red_morale  = config['red'].get('morale', 100)

        pos_names_b = (POS_NAMES_2 if len(config['blue']['heros']) == 2
                       else POS_NAMES_3 if len(config['blue']['heros']) == 3
                       else ['大营'])
        pos_names_r = (POS_NAMES_2 if len(config['red']['heros']) == 2
                       else POS_NAMES_3 if len(config['red']['heros']) == 3
                       else ['大营'])

        for i, cfg in enumerate(config['blue']['heros']):
            h = BattleHero(cfg, 'blue', self, blue_morale)
            h.pos_name = pos_names_b[i] if i < len(pos_names_b) else '大营'
            h.index = i + 1
            self.blue_team['heros'].append(h)
            self.blue_team['total'] += h.arms

        red_start_idx = len(config['blue']['heros']) + len(config['red']['heros'])
        for i, cfg in enumerate(config['red']['heros']):
            h = BattleHero(cfg, 'red', self, red_morale)
            h.pos_name = pos_names_r[i] if i < len(pos_names_r) else '大营'
            h.index = red_start_idx - i
            self.red_team['heros'].append(h)
            self.red_team['total'] += h.arms

        self._start_battle()
        self._exit_battle()

    # ──────────────────────────────────────
    # 战斗流程
    # ──────────────────────────────────────
    def _start_battle(self):
        self.log(None, '【攻方阵容】')
        for h in self.blue_team['heros']:
            self.log(None, f'  ({h.pos_name})【{h.name}】lv{h.level} 兵力{h.arms}', 1)
        self.log(None, '【守方阵容】')
        for h in self.red_team['heros']:
            self.log(None, f'  ({h.pos_name})【{h.name}】lv{h.level} 兵力{h.arms}', 1)

        for rnd in BATTLE_ROUNDS:
            if self.over: break
            self.round = rnd
            if rnd > 0:
                self.log_round(f'\n===== 第{rnd}回合 =====')
            elif rnd == 0:
                self.log_round('\n===== 准备回合 =====')
            elif rnd == -1:
                self.log_round('\n===== 被动回合 =====')

            self._sort_by_spd()

            # 回合开始时 hook
            for h in self.sort_spd_heros:
                if h.arms > 0 and rnd > 0:
                    h.call_hook('回合开始时')

            for h in self.sort_spd_heros:
                h.state['attackNum'] = 0
                if h.arms <= 0: continue

                if rnd == -1:
                    h.call_passive_skill()
                elif rnd == 0:
                    h.call_command_skill()
                else:
                    if self.over: break
                    self.log_hero_turn(h)
                    h.clear_state_rounds()
                    h.call_hook('行动前')
                    h.call_hook('行动时')

                    if h.is_confusion():
                        self.log(h, '陷入混乱，无法行动')
                        h.state_flag['confusion'] = True
                        continue

                    h.call_active_skill()
                    if self.over: break

                    if h.is_attack_limit():
                        self.log(h, '陷入怯战，无法普攻')
                        h.state_flag['attackLimit'] = True
                    else:
                        h.call_attack()
                        if self.over: break

            if rnd == 0:
                for h in self.blue_team['heros'] + self.red_team['heros']:
                    self.log(h, f'攻({h.attrs["atk"]:.1f}) 谋({h.attrs["int"]:.1f}) 防({h.attrs["def"]:.1f}) 速({h.attrs["spd"]:.1f})')

            if rnd >= 1:
                for h in self.blue_team['heros'] + self.red_team['heros']:
                    if h.arms > 0 and h.hurt_arms > 0:
                        h.hurt_arms = math.floor(h.hurt_arms * 0.87)

    def _exit_battle(self):
        for h in self.blue_team['heros']:
            self.blue_team['arms']      += h.arms
            self.blue_team['hurt_arms'] += h.hurt_arms
        for h in self.red_team['heros']:
            self.red_team['arms']      += h.arms
            self.red_team['hurt_arms'] += h.hurt_arms

    def _sort_by_spd(self):
        all_heros = self.blue_team['heros'] + self.red_team['heros']
        self.sort_spd_heros = sorted(all_heros, key=lambda h: h.attrs['spd'], reverse=True)
        # 先手排序
        rnd = self.round
        self.sort_spd_heros.sort(key=lambda h: (
            0 if (h.state['firstAction']['rounds'] > 0 and rnd <= h.state['firstAction']['rounds'])
            else 1
        ))

    # ──────────────────────────────────────
    # 日志
    # ──────────────────────────────────────
    def log(self, hero, msg, indent=0):
        """普通记录（纯文本 + 结构化双写）"""
        prefix = '  ' * indent
        if hero:
            line = f'{prefix}[{hero.name}({hero.pos_name})] {msg}'
        else:
            line = f'{prefix}{msg}'
        self.records.append(line)
        # 结构化记录
        rec = {'msg': msg, 'level': indent}
        if hero:
            rec['hero1'] = {'name': hero.name, 'pos': hero.pos_name, 'camp': hero.camp}
        self.structured_records.append(rec)

    def log_action(self, hero1, hero2, predicate, msg, indent=0):
        """行动记录：hero1 predicate hero2 msg"""
        if self.over: return
        prefix = '  ' * indent
        if hero1 and hero2:
            line = f'{prefix}[{hero1.name}({hero1.pos_name})] {predicate} [{hero2.name}({hero2.pos_name})] {msg}'
        elif hero1:
            line = f'{prefix}[{hero1.name}({hero1.pos_name})] {msg}'
        else:
            line = f'{prefix}{msg}'
        self.records.append(line)
        rec = {
            'msg': msg, 'level': indent,
            'predicate': predicate,
            'hero1': {'name': hero1.name, 'pos': hero1.pos_name, 'camp': hero1.camp} if hero1 else None,
            'hero2': {'name': hero2.name, 'pos': hero2.pos_name, 'camp': hero2.camp} if hero2 else None,
        }
        self.structured_records.append(rec)

    def log_round(self, msg):
        """回合标题记录"""
        self.records.append(msg)
        self.structured_records.append({'msg': msg, 'level': 0, 'roundTitle': True})

    def log_hero_turn(self, hero):
        """武将行动开始记录"""
        line = f'[{hero.name}({hero.pos_name})] 行动阶段（兵力{hero.arms}）'
        self.records.append(line)
        self.structured_records.append({
            'msg': '行动阶段', 'level': 0, 'heroRoundStart': True,
            'hero1': {'name': hero.name, 'pos': hero.pos_name, 'camp': hero.camp},
            'arms': hero.arms
        })

    def add_round_callback(self, rounds_later, func):
        """在 rounds_later 回合后执行一次回调（用于临时属性变化的恢复）"""
        target_round = self.round + rounds_later
        tag = f'_cb_{id(func)}'
        sk_stub = type('SkStub', (), {'skill_type': 0, 'id': 0})()  # 哑元 skill
        # 挂到第一个英雄上监听回合开始
        watcher = self.sort_spd_heros[0] if self.sort_spd_heros else None
        if watcher is None:
            return
        called = [False]
        def cb():
            if called[0]: return
            if self.round >= target_round:
                called[0] = True
                func()
                watcher.clear_hook('回合开始时', tag)
        watcher.add_hook('回合开始时', tag, cb, sk_stub, watcher, 'other', False)

    def print_records(self):
        for r in self.records:
            print(r)

    # ──────────────────────────────────────
    # 结果
    # ──────────────────────────────────────
    def result(self):
        blue_alive = sum(h.arms for h in self.blue_team['heros'])
        red_alive  = sum(h.arms for h in self.red_team['heros'])
        if blue_alive > 0 and red_alive == 0:
            winner = '攻方(蓝)'
        elif red_alive > 0 and blue_alive == 0:
            winner = '守方(红)'
        elif blue_alive > red_alive:
            winner = '攻方(蓝)（兵力优势）'
        elif red_alive > blue_alive:
            winner = '守方(红)（兵力优势）'
        else:
            winner = '平局'
        return {
            'winner': winner,
            'blue': {
                'heros': [{'name':h.name,'pos':h.pos_name,'arms':h.arms,'hurt':h.hurt_arms,'id':h.id}
                           for h in self.blue_team['heros']],
                'total_arms': self.blue_team['arms'],
                'hurt_arms':  self.blue_team['hurt_arms'],
            },
            'red': {
                'heros': [{'name':h.name,'pos':h.pos_name,'arms':h.arms,'hurt':h.hurt_arms,'id':h.id}
                           for h in self.red_team['heros']],
                'total_arms': self.red_team['arms'],
                'hurt_arms':  self.red_team['hurt_arms'],
            },
            'rounds_played': max(0, self.round) if self.round is not None else 0,
            'records': self.records,
            'structured_records': self.structured_records,
        }


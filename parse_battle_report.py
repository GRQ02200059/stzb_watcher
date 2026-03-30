# -*- coding: utf-8 -*-
"""
战报解析器 parse_battle_report.py
将 00001857 战报 report 字符串解析为结构化数据
用法: python parse_battle_report.py <json_file>
"""
import json, sys, re
from collections import defaultdict

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

# ───────────────────────────────────────────
# 基础常量
# ───────────────────────────────────────────
SIDE_NAME  = {0: '攻方', 7: '守方'}
SLOT_SIDE  = {1:'攻方',2:'攻方',3:'攻方',4:'守方',5:'守方',6:'守方'}

BUFF_NAME = {
    '401':'同心鼓舞', '502':'禁锢', '504':'缴械', '514':'速度+',
    '515':'疾行',    '521':'追击↙','522':'追击↘','523':'追击↑',
    '524':'追击↓',  '531':'正面追击','533':'侧面追击',
    '714':'控制',   '752':'强化',  '761':'铁甲',
    '811':'神兵',   '814':'鼓舞',  '871':'冲锋',
    '902':'强攻',   '1001':'神速', '1001':'连击',
}

CHASE_NAME = {
    '521':'左追','522':'右追','523':'策追','524':'统追',
    '531':'正追','533':'侧追','304':'谋略追','305':'武力追','306':'统率追',
}

# ───────────────────────────────────────────
# 解析器主类
# ───────────────────────────────────────────
class BattleReport:
    def __init__(self, report_str):
        self.raw   = report_str
        self.cmds  = report_str.split('#')
        self.idx   = 0

        # 基础信息
        self.sides      = {}        # side_id -> power
        self.heroes     = {}        # slot -> HeroConfig
        self.init_hp    = {}        # slot -> hp (正数=当前，负号=备用)
        self.squads     = {}        # slot -> SquadConfig
        self.atk_speed  = 0
        self.def_speed  = 0

        # 战斗过程
        self.rounds     = []        # list of Round
        self.buffs_init = defaultdict(list)  # slot -> [buff_id]

        # 结算
        self.final_hp   = {}        # slot -> hp
        self.result     = {}        # i0{...} 原始结算
        self.damage_out = {}        # 6p: slot -> damage_dealt
        self.damage_in  = {}        # 6t: slot -> (kills,assist,hits,...)
        self.hero_contrib = {}      # 4b: slot -> data

    # ── 顶层解析入口 ──
    def parse(self):
        self._parse_header()
        self._parse_squads()
        self._parse_battle()
        self._parse_footer()
        return self

    # ── 1. 头部：势力值、武将ID、初始HP ──
    def _parse_header(self):
        for cmd in self.cmds:
            if cmd.startswith('3r'):
                p = cmd[2:].split(',')
                slot = int(p[0])
                hp   = int(p[1])
                if slot == 0:   self.sides[0] = hp
                elif slot == 7: self.sides[7] = hp
                elif slot < 0:  self.init_hp[f'{-slot}_backup'] = hp
                else:           self.init_hp[slot] = hp
            elif cmd.startswith('0e'):
                p = cmd[2:].split(',')
                self.heroes[int(p[0])] = int(p[1])  # slot -> hero_id
            elif cmd.startswith('44') and not cmd.startswith('44a'):
                self.atk_speed = float(cmd[2:].split(',')[0])
            elif cmd.startswith('45') and not cmd.startswith('45a'):
                self.def_speed = float(cmd[2:].split(',')[0])

    # ── 2. 阵容：5p{slot} ──
    def _parse_squads(self):
        for cmd in self.cmds:
            if cmd.startswith('5p'):
                p   = cmd[2:].split(',')
                slot = int(p[0])
                sq   = {
                    'slot':       slot,
                    'side':       SLOT_SIDE.get(slot,'?'),
                    'rank':       int(p[1]),
                    'speed':      int(p[2]),
                    'hero1':      int(p[3]),  'skill1': int(p[4]),
                    'hero2':      int(p[5]),  'skill2': int(p[6]),
                    'hero3':      int(p[7]),  'skill3': int(p[8]),
                    'formation1': p[9],
                    'formation2': p[10],
                    'equips':     [(int(p[i]),int(p[i+1])) for i in range(12,len(p)-1,2) if p[i]!='0'],
                }
                self.squads[slot] = sq

    # ── 3. 战斗主体解析 ──
    def _parse_battle(self):
        cmds = self.cmds
        i, total = 0, len(cmds)
        current_round = None
        round_no = 0

        while i < total:
            cmd = cmds[i]

            # 新回合开始
            if cmd == 'hy':
                round_no += 1
                current_round = {'round': round_no, 'actions': []}
                self.rounds.append(current_round)

            # 回合结束
            elif cmd == 'hz':
                current_round = None

            elif current_round is not None:
                action = self._parse_action(cmd)
                if action:
                    current_round['actions'].append(action)

            # buff 初始化段（hj 之后）
            elif cmd.startswith('0s') and current_round is None:
                p = cmd[2:].split(',')
                self.buffs_init[int(p[0])].append(p[1])

            i += 1

    def _parse_action(self, cmd):
        """把单条指令解析为 dict，未知指令返回 None"""

        # 普通攻击: 1q{slot},6,{hero},{rage},{hp}
        m = re.fullmatch(r'1q(\d+),6,(\d+),(\d+),(\d+)', cmd)
        if m:
            return {'type':'attack','slot':int(m[1]),'hero':int(m[2]),
                    'rage_cost':int(m[3]),'hp_after':int(m[4])}

        # 反击: 6q{slot},6,{hero},{rage},{hp}
        m = re.fullmatch(r'6q(\d+),6,(\d+),(\d+),(\d+)', cmd)
        if m:
            return {'type':'counter','slot':int(m[1]),'hero':int(m[2]),
                    'rage_cost':int(m[3]),'hp_after':int(m[4])}

        # 主动技能触发: ia{slot},{rage},{hp}
        m = re.fullmatch(r'ia(\d+),(\d+),(\d+)', cmd)
        if m:
            return {'type':'skill_trigger','slot':int(m[1]),
                    'rage':int(m[2]),'hp':int(m[3])}

        # 怒气获取: gg{slot},{target},{hero},{rage}
        m = re.fullmatch(r'gg(\d+),(\d+),(\d+),(\d+)', cmd)
        if m:
            return {'type':'rage_gain','slot':int(m[1]),'target_slot':int(m[2]),
                    'hero':int(m[3]),'rage_gain':int(m[4])}

        # 受伤后HP: 1r{hero},{slot},{dmg},{hp}
        m = re.fullmatch(r'1r(\d+),(\d+),(\d+),(\d+)', cmd)
        if m:
            return {'type':'damage_recv','hero':int(m[1]),'slot':int(m[2]),
                    'dmg':int(m[3]),'hp_after':int(m[4])}

        # 技能伤害: 1n{slot},{hero},{target_slot},{dmg},{hp},{turns}
        m = re.fullmatch(r'1n(\d+),(\d+),(\d+),(\d+),(\d+),(\d+)', cmd)
        if m:
            return {'type':'skill_dmg','slot':int(m[1]),'hero':int(m[2]),
                    'target_slot':int(m[3]),'dmg':int(m[4]),'hp_after':int(m[5]),
                    'turns':int(m[6])}

        # 当前怒气: hc{slot},{rage}
        m = re.fullmatch(r'hc(\d+),(\d+)', cmd)
        if m:
            return {'type':'rage_check','slot':int(m[1]),'rage':int(m[2])}

        # 物理伤害: 0v{slot},{hero},{pct},{angle}
        m = re.fullmatch(r'0v(\d+),(\d+),(\d+),(\d+\.?\d*),(\d+\.?\d*)', cmd)
        if m:
            return {'type':'dmg_phys','slot':int(m[1]),'hero':int(m[2]),
                    'pct':float(m[4]),'angle':float(m[5])}

        # 策略伤害: 0w
        m = re.fullmatch(r'0w(\d+),(\d+),(\d+),(\d+\.?\d*),(\d+\.?\d*)', cmd)
        if m:
            return {'type':'dmg_strat','slot':int(m[1]),'hero':int(m[2]),
                    'pct':float(m[4]),'angle':float(m[5])}

        # 统率伤害: 0x
        m = re.fullmatch(r'0x(\d+),(\d+),(\d+),(\d+\.?\d*),(\d+\.?\d*)', cmd)
        if m:
            return {'type':'dmg_lead','slot':int(m[1]),'hero':int(m[2]),
                    'pct':float(m[4]),'angle':float(m[5])}

        # 追击: ja{slot},{hero},{target},{chase_type},{stacks}
        m = re.fullmatch(r'ja(\d+),(\d+),(\d+),(\d+),(\d+)', cmd)
        if m:
            ctype = m[4]
            return {'type':'chase','slot':int(m[1]),'hero':int(m[2]),
                    'target':int(m[3]),'chase_type':ctype,
                    'chase_name':CHASE_NAME.get(ctype,ctype),'stacks':int(m[5])}

        # buff 施加: 0s{slot},{buff_id}
        m = re.fullmatch(r'0s(\d+),(\d+)', cmd)
        if m:
            bid = m[2]
            return {'type':'buff','slot':int(m[1]),'buff_id':bid,
                    'buff_name':BUFF_NAME.get(bid,f'buff({bid})')}

        # 武将阵亡: 3f{slot}
        m = re.fullmatch(r'3f(\d+)', cmd)
        if m:
            return {'type':'dead','slot':int(m[1])}

        return None

    # ── 4. 结算段 ──
    def _parse_footer(self):
        for cmd in self.cmds:
            # 最终HP: 3s{slot},{hp}
            m = re.fullmatch(r'3s(\d+),(\d+)', cmd)
            if m:
                self.final_hp[int(m[1])] = int(m[2])

            # 阵亡记录: jl{slot},{hp},{hero_id}
            m = re.fullmatch(r'jl(\d+),(\d+),(\d+)', cmd)
            if m:
                self.final_hp[int(m[1])] = 0  # 已阵亡

            # 伤害输出: 6p{slot},{dmg}
            m = re.fullmatch(r'6p(\d+),(\d+)', cmd)
            if m:
                self.damage_out[int(m[1])] = int(m[2])

            # 伤害承受: 6t{slot},{kills},{dmg_recv},{skill_hits},{?}
            m = re.fullmatch(r'6t(\d+),(\d+),(\d+),(\d+),(\d+)', cmd)
            if m:
                self.damage_in[int(m[1])] = {
                    'kills':int(m[2]),'dmg_recv':int(m[3]),
                    'skill_hits':int(m[4]),'unk':int(m[5])}

            # i0{ 结算 }
            if cmd.startswith('i0{'):
                try:
                    raw = cmd[2:]  # '{1:[...], 2:[...]}'
                    # 转成合法 JSON
                    raw = re.sub(r'(\d+):', r'"\1":', raw)
                    self.result = json.loads(raw)
                except Exception:
                    self.result = {'raw': cmd}


# ───────────────────────────────────────────
# 输出格式化
# ───────────────────────────────────────────
def print_report(br: BattleReport):
    print('=' * 60)
    print(f'battle_id: (来自文件)')
    print(f'攻方势力: {br.sides.get(0,"?")}  守方势力: {br.sides.get(7,"?")}  ')
    print(f'攻方速度: {br.atk_speed}  守方速度: {br.def_speed}')

    print('\n── 阵容 ──')
    for slot in sorted(br.squads):
        sq = br.squads[slot]
        eq_str = ' '.join(f'eq{e[0]}(lv{e[1]})' for e in sq['equips'])
        print(f"  [{sq['side']} slot{slot}] rank={sq['rank']} speed={sq['speed']} "
              f"武将={sq['hero1']}/{sq['hero2']}/{sq['hero3']} "
              f"阵型={sq['formation1']}/{sq['formation2']} {eq_str}")

    print('\n── 初始HP ──')
    for slot in sorted(k for k in br.init_hp if isinstance(k,int)):
        print(f'  slot{slot}: {br.init_hp[slot]}')

    print(f'\n── 共 {len(br.rounds)} 个回合 ──')
    for rnd in br.rounds:
        acts = rnd['actions']
        # 统计
        attacks = [a for a in acts if a['type'] in ('attack','counter')]
        skills  = [a for a in acts if a['type'] == 'skill_trigger']
        chases  = [a for a in acts if a['type'] == 'chase']
        deads   = [a for a in acts if a['type'] == 'dead']
        dmg_evts= [a for a in acts if a['type'] == 'damage_recv']
        dead_str = ' '.join(f"slot{a['slot']}阵亡" for a in deads)
        print(f"  回合{rnd['round']:2d}: 攻击{len(attacks)}次 技能{len(skills)}次 追击{len(chases)}次 伤害事件{len(dmg_evts)}次 {dead_str}")
        # 详细伤害
        for a in dmg_evts:
            print(f"         slot{a['slot']} 受伤 -{a['dmg']} 剩余HP {a['hp_after']}")
        for a in deads:
            print(f"         slot{a['slot']} ★阵亡")

    print('\n── 结算 ──')
    for slot in sorted(br.damage_out):
        side = SLOT_SIDE.get(slot,'?')
        dmg_out = br.damage_out.get(slot, 0)
        dmg_in_d = br.damage_in.get(slot, {})
        final = br.final_hp.get(slot, '?')
        print(f"  [{side} slot{slot}] 输出伤害={dmg_out:6d}  "
              f"承受={dmg_in_d.get('dmg_recv',0):6d}  "
              f"技能命中={dmg_in_d.get('skill_hits',0):3d}  "
              f"最终HP={final}")

    if br.result:
        print('\n── 行动顺序（i0结算）──')
        for rno, seq in br.result.items():
            if not isinstance(seq, list): continue
            order = []
            for j in range(0, len(seq), 4):
                if j+3 < len(seq):
                    sl, hp, maxhp, alive = seq[j], seq[j+1], seq[j+2], seq[j+3]
                    order.append(f"slot{sl}(HP{hp}/{maxhp})")
            print(f"  回合{rno}: {' → '.join(order)}")


# ───────────────────────────────────────────
# 入口
# ───────────────────────────────────────────
def main():
    if len(sys.argv) < 2:
        print('用法: python parse_battle_report.py <json_file>')
        sys.exit(1)
    path = sys.argv[1]
    with open(path, encoding='utf-8', errors='replace') as f:
        data = json.load(f)
    # 支持 [0, {battle_id:..., report:...}, 344] 格式
    if isinstance(data, list):
        obj = next((x for x in data if isinstance(x, dict) and 'report' in x), None)
    else:
        obj = data
    if not obj:
        print('未找到 report 字段'); sys.exit(1)

    print(f'battle_id: {obj.get("battle_id","?")}')
    br = BattleReport(obj['report'])
    br.parse()
    print_report(br)

    # 可选：输出完整 JSON
    if '--json' in sys.argv:
        out = {
            'sides': br.sides,
            'squads': br.squads,
            'init_hp': {str(k):v for k,v in br.init_hp.items()},
            'rounds': br.rounds,
            'damage_out': br.damage_out,
            'damage_in': br.damage_in,
            'final_hp': br.final_hp,
            'result': br.result,
        }
        out_path = path.replace('.json', '_parsed.json')
        with open(out_path, 'w', encoding='utf-8') as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
        print(f'\n已输出 JSON: {out_path}')


if __name__ == '__main__':
    main()


# 战斗模拟工作台重构设计

日期：2026-08-15

## 1. 目标

将当前战斗模拟页面重构为：

1. **快速对阵台**：一屏完成双方配置、模拟次数选择和胜率判断。
2. **侧滑武将/战法库**：需要换将、换战法或套用模板时打开，不挤占主画布。
3. **战术复盘**：单场模拟后按回合、行动和效果链解释胜负过程。

战斗计算必须使用 `/Users/bytedance/stzb/server` 下的 Kotlin 战斗引擎。前端不实现战斗公式，Python 不复制战斗规则。

## 2. 用户工作流

默认流程：

```text
配置攻方和守方
→ 选择单场 / 100 次 / 1000 次
→ 运行 Kotlin 战斗引擎
→ 查看胜率与关键指标
→ 可选进入单场战术复盘
```

### 2.1 快速对阵

- 攻方、守方始终左右并排。
- 每方最多 3 名武将，位置固定为大营、中军、前锋。
- 武将卡直接显示：
  - 武将名；
  - 阵营和兵种；
  - 等级、进阶；
  - 两个额外战法槽；
  - 当前配置完整性。
- 点击武将卡打开武将库。
- 点击战法槽打开战法库。
- 中间控制条固定提供：
  - 单场；
  - 100 次；
  - 1000 次；
  - 开始模拟；
  - 交换攻守。
- 支持复制攻方到守方、复制守方到攻方。

### 2.2 侧滑库

侧滑库包含三个域：

- 武将；
- 战法；
- 阵容模板。

武将过滤：

- 名称或 ID；
- 阵营；
- 兵种；
- 品质；
- 攻击距离。

战法过滤：

- 名称或 ID；
- 指挥、主动、追击、被动；
- 品质；
- 作用距离；
- 目标类型。

用户点击条目后立即替换当前目标槽，不需要在大型下拉框中滚动。

### 2.3 阵容模板

第一版保存在浏览器 `localStorage`：

- 模板名称；
- 攻守方；
- 武将顺序；
- 等级；
- 进阶；
- 额外战法；
- 士气。

功能：

- 保存当前一方；
- 保存完整对阵；
- 载入模板；
- 删除模板；
- 导入/导出 JSON。

不在第一版引入服务器写入、账号同步或多人共享。

## 3. 结果展示

### 3.1 批量模拟

100/1000 次结果显示：

- 攻方胜率；
- 守方胜率；
- 平局率；
- 胜负数量；
- 横向胜率条；
- 引擎名称；
- 实际模拟次数；
- 固定 seed 或随机 seed 状态。

批量模拟保留第一场的完整事件，允许进入战术复盘。

### 3.2 单场模拟

单场结果显示：

- 胜方；
- 战斗回合数；
- 双方剩余总兵力；
- 每名武将初始兵力、剩余兵力和损失；
- 是否存活；
- 战斗事件数；
- 未支持战法/装备效果数量。

### 3.3 关键判断

关键判断只能从 Kotlin 引擎真实事件和批量统计派生，不允许前端编造。

第一版支持：

- 行动顺序优势；
- 总伤害与总恢复；
- 控制施加次数；
- 主动战法发动次数；
- 闪避次数；
- 未支持效果告警；
- 首个明显转折回合。

判断必须附带证据：

- 事件类型；
- 回合；
- 来源武将；
- 目标武将；
- 战法 ID；
- 数值。

## 4. 战术复盘

战术复盘是独立视图，不继续塞进配置区下方。详细程度必须接近
`/Users/bytedance/stzb/server` 的完整客户端战报回放，不得降级为简单文本日志。

### 4.1 双回放数据源

Kotlin CLI 同时输出两套互相可追溯的数据：

1. **语义事件流**
   - 来自 `BattleReportCodec`；
   - 使用强类型 `BattleEvent`；
   - 保留完整字段；
   - 用于 UI 筛选、聚合、回合摘要和关键判断。
2. **server 动作流**
   - 来自 `ClientBattleTextReplayAdapter`；
   - 与客户端战报动作协议一致；
   - 保留 action ID、参数、编码顺序和准备阶段包络；
   - 用于原始动作检查和官方 fixture parity。

两套数据使用稳定序号关联：

```json
{
  "eventSeq": 84,
  "phase": "BATTLE",
  "round": 3,
  "semanticEvent": {},
  "replayActions": [
    {
      "actionSeq": 126,
      "actionId": 210,
      "encoded": "5u3,771",
      "params": [3, 771]
    }
  ]
}
```

前端默认展示语义事件；“动作原码”视图展示 server 动作流。

### 4.2 准备阶段

布局：

```text
左：准备阶段与回合列表
中：当前回合事件时间线
右：效果链和回合摘要
```

准备阶段不能被折叠成一个模糊的“第 0 回合”，必须按 server 回放顺序分组：

```text
初始化
├── 攻方/守方武将位置
├── 等级、初始兵力
├── 三个战法及等级
├── 兵种特性
└── 装备槽
准备效果
├── 系统效果
├── 阵营/国家效果
├── 部队组合效果
├── 兵种效果
├── 装备效果
├── 外观/进阶技能效果
├── 被动战法阶段
└── 指挥战法阶段
```

每个来源显示：

- 来源类型；
- 来源 ID；
- 来源武将位置或队伍位置；
- 目标位置；
- 属性变化前值、变化量和变化后值；
- 百分比或固定值单位；
- 状态和持续回合；
- modifier；
- 是否支持；
- 对应 replay action。

指挥阶段按进入战斗时的速度顺序展示每名武将。

### 4.3 战斗阶段

事件类型：

- `BattleStart`
- `RoundStart` / `RoundEnd`
- `HeroActionStart` / `HeroActionEnd`
- `SkillTriggered`
- `TriggerPoint`
- `SkillPreparationStarted`
- `SkillPreparationCompleted`
- `SkillPreparationCancelled`
- `NormalAttack`
- `SkillDamage`
- `OngoingDamage`
- `Recovery`
- `StatusApplied`
- `StatusRemoved`
- `EffectExpired`
- `EffectBlocked`
- `Evaded`
- `StatChanged`
- `ModifierApplied`
- `SkillRangeChanged`
- `UnsupportedSkillEffect`
- `UnsupportedEquipmentEffect`
- `BattleEnd`

复盘功能：

- 按回合切换；
- 按武将过滤；
- 按战法过滤；
- 只看伤害、恢复、控制或属性变化；
- 点击事件高亮来源和目标；
- 显示该回合兵力变化；
- 显示技能触发链；
- 复制事件摘要。

事件行必须保留：

- 全局 `eventSeq`；
- 阶段；
- 回合；
- 当前行动武将；
- 来源与目标；
- 根战法 ID；
- 实际派生战法 ID；
- effect ID；
- trigger；
- 伤害、恢复或属性变化数值；
- 目标剩余兵力；
- 状态、持续回合、层数；
- 阻挡 effect ID；
- 准备开始/完成/取消；
- 对应 replay action ID 和编码。

英雄行动使用 `HeroActionStart` / `HeroActionEnd` 包络，不允许把不同武将的事件混在
同一个无边界列表里。

技能效果使用 `SKILL_BEGIN` / `SKILL_CAST` / `SKILL_END` 包络建立因果链。

### 4.4 状态生命周期

复盘必须能追踪一个状态或效果的完整生命周期：

```text
来源战法
→ StatusApplied / StatChanged / ModifierApplied
→ 生效目标
→ OngoingDamage / Recovery / 属性影响
→ EffectBlocked / Evaded
→ StatusRemoved / EffectExpired
```

点击任一状态可以查看：首次施加、刷新/替换、每回合影响、阻挡、清除和到期。

### 4.5 回合快照

每个回合结束保存六名武将的只读快照：

- 当前兵力；
- 本回合损失；
- 累计损失；
- 本回合恢复；
- 累计恢复；
- 当前有效状态；
- 当前属性；
- 已准备战法；
- 本回合行动次数；
- 普攻次数；
- 主动/追击发动次数；
- 是否存活。

准备阶段保存 entry snapshot；战斗结束保存 final snapshot。

### 4.6 结果完整性

复盘页固定展示完整性面板：

- `UnsupportedSkillEffect` 数量和明细；
- `UnsupportedEquipmentEffect` 数量和明细；
- 无法投影到客户端动作协议的事件；
- 是否使用非严格 replay 适配；
- 事件总数；
- 动作总数；
- 被截断日志数量；
- 源引擎提交。

存在未支持效果时不能隐藏；胜率和结果仍可展示，但标记为“部分效果未执行”。

### 4.7 官方回放对齐

必须迁移并运行 server 的回放测试：

- `ClientBattleTextReplayAdapterTest`
- `ClientBattleTextReplayProtocolTest`
- `OfficialFullBattleReportDiffTest`
- `OfficialPreparationReportDiffTest`
- `OfficialReportFixtureTest`

其中准备阶段 action 顺序、来源包络、属性精确值和官方 fixture 数量变化必须失败告警。

## 5. UI 结构

从 `dashboard.html` 移除模拟器的大段内联样式，改为：

```text
static/
├── simulator-workbench.css
├── simulator-workbench.js
├── simulator-analysis.mjs
└── sim.js
```

职责：

- `sim.js`
  - 保留现有全局兼容入口；
  - 管理状态；
  - 请求 API；
  - 对接外部阵容 handoff。
- `simulator-workbench.js`
  - 渲染对阵台；
  - 管理侧滑库；
  - 模板；
  - 交换和复制；
  - 结果视图切换。
- `simulator-analysis.mjs`
  - 纯函数；
  - 聚合 Kotlin 事件；
  - 生成回合摘要；
  - 生成关键判断；
  - 不访问 DOM。
- `simulator-workbench.css`
  - 只使用现有 design token；
  - 不新增第二套 `:root`；
  - 负责桌面和移动端布局。

## 6. 引擎架构

### 6.1 上游真值

唯一战斗语义真值：

```text
/Users/bytedance/stzb/server
```

具体范围：

```text
src/main/kotlin/com/stzb/server/game/battle/**
src/main/kotlin/com/stzb/server/game/ClientNpcArmyRepository.kt
src/main/kotlin/com/stzb/server/game/SkillInventoryCatalog.kt
src/main/resources/battle-config/**
src/main/resources/client-config/<战斗白名单>
```

`stzb_watcher/battle-engine/` 是独立运行镜像，不是第二个可自由修改的战斗引擎。

### 6.2 当前核验结果

截至源仓库提交：

```text
93ee999937d011b2a3dadf67ed39edfbb409aaca
```

核验结果：

- 38 个核心 Kotlin 文件在包名映射后完全一致；
- 4 个文件存在独立运行适配：
  - `BattleEquipmentRepository.kt`：增加项目内资源路径；
  - `BattleFormationCalculator.kt`：删除同包冗余 import；
  - `BattleTeamBuilder.kt`：删除同包冗余 import；
  - `ClientBattleReportStore.kt`：移除服务器配置依赖，并有报告表面编码差异；
- 战斗配置 CSV/JSON 全部与源仓库同哈希；
- 客户端装备/兵种配置二进制与源仓库同哈希。

### 6.3 同步机制

新增显式同步脚本：

```text
scripts/sync_battle_engine.py
```

职责：

1. 从明确的 `--source-root` 读取白名单。
2. 将包名从 `com.stzb.server.game.battle` 映射到 `com.stzb.battle.core`。
3. 将少量独立运行适配作为可审查 patch 应用。
4. 复制战斗配置资源。
5. 生成：
   - `battle-engine/SOURCE.json`
   - 源提交号；
   - 文件映射；
   - 源 SHA-256；
   - 生成 SHA-256；
   - 允许差异列表。
6. 支持 `--check`：
   - 检测源文件变更；
   - 检测当前镜像被手改；
   - 检测未知文件；
   - 检测配置漂移。

任何战斗语义修改必须先进入 `/Users/bytedance/stzb/server`，再通过同步脚本进入当前项目。

### 6.4 测试保障

源引擎当前有约 45 个战斗相关测试文件，覆盖：

- 核心模型；
- 伤害与属性；
- 技能引擎；
- 控制效果；
- 时序；
- 战报编码；
- 官方战报 fixture 对齐；
- 完整集成。

当前独立镜像只有 1 个 CLI 测试文件。

本次必须迁移能够独立运行的源测试，最低覆盖：

- 核心 BattleEngine；
- BattleFormationCalculator；
- BattleDamageCalculator；
- BattleEffectState；
- BattleTeamBuilder；
- CompleteSkillEngine；
- 控制效果；
- SkillRuleInterpreter；
- SkillTimingCoordinator；
- BattleReportCodec；
- 官方 fixture parity；
- CLI contract。

无法独立运行的服务器集成测试需记录在 `SOURCE.json` 的 excluded tests 中，不得静默丢弃。

## 7. CLI 与 API

### 7.1 CLI 输入

继续使用 JSON stdin/stdout，扩展：

```json
{
  "seed": 20260810,
  "repeat": 100,
  "includeFirstRun": true,
  "includeEvents": true,
  "attacker": {},
  "defender": {}
}
```

约束：

- `repeat` 只允许 `1 / 100 / 1000`；
- 每方 1–3 名武将；
- 位置不能重复；
- 武将 ID 必须存在；
- 战法 ID 必须存在；
- 等级 1–45；
- 进阶 0–9；
- 士气 0–200；
- 输入错误返回结构化错误，不抛出非 JSON 文本。

### 7.2 CLI 输出

当前 CLI 的 `structuredLog` 只输出部分事件。新版同时复用：

- `BattleReportCodec`：完整语义事件；
- `ClientBattleTextReplayAdapter`：server 风格动作流；
- `ClientReportTextEncoder`：可选客户端文本编码，用于 parity 检查。

输出：

```json
{
  "ok": true,
  "engine": {
    "name": "stzb-kotlin",
    "sourceCommit": "...",
    "mirrorManifest": "..."
  },
  "repeat": 100,
  "attackerWins": 63,
  "defenderWins": 29,
  "draws": 8,
  "firstRun": {
    "outcome": "ATTACKER_WIN",
    "roundsPlayed": 6,
    "attackerHeroes": [],
    "defenderHeroes": [],
    "entrySnapshots": [],
    "roundSnapshots": [],
    "finalSnapshots": [],
    "events": [],
    "replayActions": [],
    "replayText": "",
    "diagnostics": {
      "unsupportedSkillEffects": [],
      "unsupportedEquipmentEffects": [],
      "unprojectedReplayEvents": []
    }
  }
}
```

批量模拟必须保留第一场完整事件，供复盘使用。

### 7.3 Flask

保留：

```text
POST /api/simulate
GET /api/simulate/heroes
```

新增只读元数据：

```text
GET /api/simulate/engine
```

返回：

- 引擎名称；
- 上游源提交；
- 同步时间；
- 镜像 checksum；
- 配置版本；
- 支持事件类型；
- 最大模拟次数。

不提供 Python fallback。Kotlin 引擎不可用时明确失败，避免用户误以为结果来自同一套规则。

## 8. 前端状态

统一状态模型：

```js
{
  attacker: {
    morale: 100,
    heroes: []
  },
  defender: {
    morale: 100,
    heroes: []
  },
  repeat: 100,
  seedMode: "fixed",
  seed: 20260810,
  selectedSlot: null,
  drawer: null,
  result: null,
  activeResultView: "summary",
  activeRound: 0,
  eventFilters: {}
}
```

兼容现有：

```js
window.StzbSimulator.loadLineup(...)
window.StzbSimulator.getState()
window.StzbSimulator.run(...)
```

外部阵容研究跳转仍可将阵容装载到攻方或守方。

## 9. 错误处理

- 武将/战法数据加载失败：页面显示可重试错误。
- 引擎不可用：显示引擎路径和构建提示，不回退其他算法。
- 模拟超时：保留当前配置，允许重试。
- 不完整阵容：运行前逐槽标红。
- 未支持效果：结果可用，但在摘要和复盘中明确告警。
- 模板 JSON 非法：拒绝导入，不修改当前状态。
- 图片加载失败：使用本地纯色占位，不影响操作。

## 10. 响应式与可访问性

桌面：

- 双方并排；
- 控制条居中；
- 结果摘要两列。

移动端：

- 攻方、控制条、守方纵向排列；
- 武将卡横向滚动或单列紧凑卡；
- 侧滑库变为全屏底部 sheet；
- 战术复盘回合列表变为顶部横向 tabs。

键盘：

- `Cmd/Ctrl + Enter` 开始模拟；
- `1 / 2 / 3` 切换模拟次数；
- `Esc` 关闭侧滑库或复盘；
- 所有按钮有可见焦点；
- 卡片操作不依赖 hover。

## 11. 测试

### 引擎同步

- 包名映射；
- allowlist；
- 适配 patch；
- 源提交记录；
- `--check` 漂移检测；
- 配置 hash。

### Kotlin

- 迁移的源测试；
- CLI 输入校验；
- CLI 完整事件输出；
- repeat 1/100/1000；
- 固定 seed 稳定；
- 批量保留 firstRun。

### Python

- adapter 输入映射；
- 完整事件映射；
- 引擎元数据；
- 错误 JSON；
- 超时；
- 不存在的 CLI。

### JavaScript

- 状态 reducer；
- 阵容交换/复制；
- 模板序列化；
- 事件聚合；
- 回合分组；
- 关键判断；
- 过滤。

### Chrome E2E

- 选择武将；
- 选择战法；
- 交换攻守；
- 保存/载入模板；
- 单场模拟；
- 100 次模拟；
- 进入复盘；
- 回合与事件过滤；
- 从阵容研究 handoff；
- 移动端无横向溢出。

## 12. 非目标

- 不修改 `/Users/bytedance/stzb/server` 的战斗语义。
- 不在前端计算胜率、伤害或控制结论。
- 不把 Android 轻量模拟器接入 Web。
- 不做多人在线模板库。
- 不自动根据抓包阵容发起模拟。
- 不承诺模拟等于真实战报结果；继续标记为 `SIMULATION`。

## 13. 验收标准

- 页面默认是一屏式快速对阵台。
- 武将和战法选择不再依赖每张卡的小型 HTML 下拉框。
- 100/1000 次结果在首屏可读。
- 单场结果可进入独立战术复盘。
- 复盘事件全部来自 Kotlin 引擎。
- `/api/simulate` 正常结果明确标注 `stzb-kotlin`。
- `battle-engine/SOURCE.json` 记录 `/Users/bytedance/stzb/server` 源提交。
- `scripts/sync_battle_engine.py --check` 通过。
- 当前项目不能在未更新源清单的情况下手改战斗核心。
- 迁移后的 Kotlin 测试、Python 测试和 Chrome E2E 全部通过。

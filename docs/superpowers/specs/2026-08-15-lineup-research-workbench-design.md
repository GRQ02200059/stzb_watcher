# 阵容战法研究工作台设计

日期：2026-08-15
状态：已批准
视觉方向：A“阵容实验室”为主，吸收 B“克制分析台”和 C“战法编排器”

## 1. 目标

将现有“阵容战法研究”从“武将 / 战法 / 卡包详情浏览器”升级为可以连续完成以下
任务的研究工作台：

1. 搜索武将、战法和历史阵容；
2. 组建三人阵容并调整站位；
3. 为每名武将配置两个可选战法；
4. 查看配置事实、历史统计和模拟验证三层证据；
5. 分析我方阵容与指定敌方阵容的历史交手和模拟结果；
6. 按准备阶段、回合和触发顺序查看战法执行链；
7. 将当前完整配置送入现有 Kotlin 战斗模拟器；
8. 保存本地实验阵容，不写入游戏或服务器数据库。

页面只提供只读研究和本地浏览器配置，不增加游戏动作、抓包写入或数据库写接口。

## 2. 设计原则

### 2.1 一个工作台，三种模式

顶部模式：

```text
阵容实验室
对阵分析
战法执行链
```

三种模式共享：

- 左侧统一素材库；
- 当前实验阵容；
- 右侧证据与验证栏；
- 当前选择和本地模板；
- 配置事实、历史统计、模拟验证的证据标签。

模式切换只替换中间工作区，不跳转到新的主导航页面。

### 2.2 证据不混淆

继续使用三层证据模型：

| 证据层 | 来源 | 可陈述内容 |
|---|---|---|
| `CONFIG_FACT` | 客户端静态配置快照 | 武将属性、初始战法、战法参数、目标与持续 |
| `BATTLE_STAT` | `battles_v2` + `battle_heroes` | 样本、胜平负、历史胜率、常见对手、交手统计 |
| `SIMULATION` | Kotlin battle-engine | 当前完整战法配置的模拟结果与语义事件 |

禁止：

- 把客户端配置描述成历史效果；
- 把历史胜率描述成确定性克制；
- 把模拟结果描述成真实战报；
- 在缺少战法配置时伪造默认战法；
- 根据武将名字猜测未记录的阵容或技能。

### 2.3 复用现有能力

复用：

- `GET /api/intelligence/heroes[/<id>]`
- `GET /api/intelligence/skills[/<id>]`
- `GET /api/intelligence/lineups[/<key>]`
- `GET /api/intelligence/card-packs[/<id>]`
- `GET /api/simulate/heroes`
- `POST /api/simulate`
- `window.StzbSimulator.loadLineup()`
- `stzb:simulation-completed`
- 现有画像目录和占位图
- 现有 HUD、analysis 视觉域和 mtime 资源版本

不建立第二套武将、战法或模拟数据源。

## 3. 信息架构

### 3.1 页面头部

保留：

```text
阵容战法研究
CONFIG FACT
BATTLE STAT
SIMULATION
```

新增：

- 当前模式；
- 当前阵容完整性；
- 配置版本；
- 本地模板状态；
- “保存实验阵容”；
- “送入模拟器”。

### 3.2 三栏结构

桌面：

```text
270px 素材库 | minmax(0, 1fr) 中央工作区 | 310px 证据栏
```

#### 左栏：统一素材库

素材类型：

```text
武将
战法
历史阵容
卡包
```

搜索字段：

- 名称；
- ID；
- 阵营；
- 兵种；
- 战法效果；
- 阵容中的武将名。

过滤：

- 阵营；
- 品质；
- 兵种；
- 战法类型；
- 最低历史样本；
- 仅显示可用完整配置。

素材卡展示真实画像、核心配置和关联历史阵容数量。

#### 中栏：模式工作区

根据模式切换：

- 阵容实验室；
- 对阵分析；
- 战法执行链。

#### 右栏：证据与验证

固定存在，子标签：

```text
配置事实
历史统计
模拟验证
```

切换模式时保留当前阵容和证据选择。

## 4. 阵容实验室

### 4.1 实验阵容模型

前端模型：

```javascript
{
  schemaVersion: 1,
  name: "",
  morale: 100,
  heroes: [
    {
      id: 100027,
      position: 0,
      level: 40,
      up: 5,
      equip_skills: [200001, 200027],
    },
    {
      id: 100016,
      position: 1,
      level: 40,
      up: 5,
      equip_skills: [200198, 0],
    },
    {
      id: 100090,
      position: 2,
      level: 40,
      up: 5,
      equip_skills: [200248, 200914],
    },
  ],
}
```

约束：

- 恰好三个位置：大营、中军、前锋；
- 武将 ID 必须为正数且不能重复；
- 每名武将允许两个可选战法槽；
- 初始战法由配置事实展示，不占可选战法槽；
- 可选战法 ID 为 `0` 表示空槽；
- 站位交换必须保留武将等级、进阶和战法；
- 删除武将时清空该位置全部配置；
- 不自动补齐缺失战法。

### 4.2 编组交互

- 点击素材库武将，加入当前选中位置；
- 点击阵容武将卡，选择位置；
- 点击战法槽，素材库切到战法并进入槽位选择；
- 支持交换站位；
- 支持清空单个槽位；
- 支持从历史阵容载入三将；
- 历史阵容只提供三将、等级和阵容键；未记录的可选战法保持空；
- 支持一键复制完整阵容 JSON；
- 支持送入模拟器。

### 4.3 本地模板

存储：

```text
stzb.research.lineup-templates.v1
```

支持：

- 保存；
- 重命名；
- 载入；
- 删除；
- JSON 导入；
- JSON 导出。

只写 `localStorage`，不写服务器。

## 5. 对阵分析

### 5.1 双方模型

```text
我方：当前实验阵容
敌方：历史阵容、模板或手动编组
```

敌方选择不覆盖我方实验阵容。

### 5.2 新增只读对阵接口

```text
GET /api/intelligence/lineups/<left_key>/matchup/<right_key>
```

返回：

```json
{
  "ok": true,
  "leftKey": "100027.100016.100090",
  "rightKey": "100013.100649.100023",
  "battleStats": {
    "evidenceClass": "BATTLE_STAT",
    "sampleSize": 7,
    "wins": 4,
    "draws": 1,
    "losses": 2,
    "winRate": 64.3,
    "latestBattleTime": 1786770000
  },
  "confidence": {
    "label": "low",
    "minimumRecommendedSample": 10,
    "notice": "样本不足，仅供参考。"
  }
}
```

规则：

- 左阵容作为统计视角；
- 同一场战报只计一次；
- 攻守双方结果必须按视角归一；
- 只统计两侧都有完整三人阵容的战报；
- 无样本返回 `sampleSize = 0`，不返回 404；
- 保留最新战报时间；
- 使用既有置信度阈值。

### 5.3 综合判断

前端可以并列展示：

- 历史交手；
- 当前配置模拟；
- 样本置信度；
- 当前阵容完整性；
- 未支持效果告警。

前端不得生成一个无证据的“综合评分”。

允许使用以下离散状态：

```text
证据不足
谨慎验证
历史占优
历史劣势
模拟分歧
```

状态必须由可见规则产生，并展示所依赖的证据。

### 5.4 替代阵容

从现有 `commonOpponents` 和阵容列表中展示：

- 常见对手；
- 高样本对手；
- 历史占优对手；
- 历史劣势对手。

不得用低样本胜率直接宣称克制。

## 6. 战法执行链

### 6.1 配置链

在尚未运行模拟时，展示静态配置链：

```text
准备阶段
指挥阶段
主动阶段
普攻
追击阶段
持续效果
```

每个战法节点展示：

- 所属武将；
- 战法名和 ID；
- 战法类型；
- 准备回合；
- 发动概率；
- 目标描述；
- 攻击距离；
- 主效果；
- 效果细则；
- 常量参数；
- 谋略参数；
- 持续回合；
- 是否存在未解析占位符。

静态配置只能表达“可能执行的结构”，不能表达真实触发顺序。

### 6.2 模拟链

模拟完成后，复用模拟器语义事件投影：

- `PREPARATION`
- `RoundStart`
- `HeroActionStart`
- `SkillDamage`
- `Recovery`
- `StatusApplied`
- `StatChanged`
- `EffectBlocked`
- `HeroActionEnd`
- `RoundEnd`

按：

```text
准备阶段 → 回合 → 武将行动 → 战法事件 → 效果链
```

展示真实模拟触发顺序。

点击节点：

- 右栏切到“模拟验证”；
- 显示对应事件；
- 显示关联 replay action；
- 显示未支持或未投影告警。

### 6.3 战法链深模块

新增纯模块：

```text
static/research-skill-chain.mjs
```

接口：

```javascript
buildConfigSkillChain(lineup, heroDetails, skillDetails)
buildSimulationSkillChain(simulationResult)
groupSkillChainByPhase(nodes)
findSkillChainNode(nodes, nodeId)
```

不访问 DOM、网络或全局状态。

## 7. 前端模块边界

现有 `static/intelligence-research.js` 过于压缩，升级时拆为：

```text
static/intelligence-research.js
static/research-workbench.mjs
static/research-skill-chain.mjs
static/research-templates.mjs
static/intelligence-research.css
```

### 7.1 `intelligence-research.js`

保留兼容入口：

```javascript
window.loadIntelligenceResearch
window.ResearchCenter
```

只负责：

- 模块安装；
- 旧入口兼容；
- 主导航联动；
- 卡包模块桥接；
- 模拟器桥接。

### 7.2 `research-workbench.mjs`

负责：

- 三种模式；
- 统一素材库；
- 当前阵容；
- 敌方阵容；
- 证据标签；
- 搜索和筛选；
- 加载和错误状态；
- 可见页交互。

导出：

```javascript
normalizeResearchLineup(value)
validateResearchLineup(value)
swapResearchPositions(lineup, left, right)
replaceResearchHero(lineup, position, hero)
replaceResearchSkill(lineup, position, slot, skillId)
deriveMatchupState(history, simulation, completeness)
createResearchWorkbench(options)
```

### 7.3 `research-templates.mjs`

负责：

- schema 校验；
- localStorage；
- 导入导出；
- 深拷贝；
- 名称去重。

## 8. 页面壳层

`tab34` 调整为：

```html
<div class="research-mode-tabs"></div>
<div class="research-workbench-shell">
  <aside class="research-library"></aside>
  <main class="research-stage"></main>
  <aside class="research-evidence-panel"></aside>
</div>
```

必需 DOM：

```text
research-mode-tabs
research-library-kind
research-search
research-library-filters
research-results
research-stage
research-evidence-tabs
research-evidence-body
research-template-dialog
```

卡包继续在素材库中作为一种类型，不恢复协议页面。

## 9. 响应式

### `>= 1280px`

三栏：

```text
270px | 1fr | 310px
```

### `768..1279px`

两栏：

```text
素材库 | 中央工作区
证据栏占下一整行
```

### `< 768px`

单列：

1. 模式切换；
2. 当前阵容摘要；
3. 中央工作区；
4. 证据栏；
5. 可折叠素材库。

不得产生文档横向滚动。

## 10. 可访问性与动效

- 模式使用 `role="tablist"`；
- 素材类型使用按钮；
- 阵容位置和战法槽可键盘选择；
- 上下键移动素材焦点；
- Enter 选择；
- Escape 关闭对话框；
- 画像有武将名 `alt`；
- 证据不能只靠颜色；
- reduced-motion 下无卡片位移、扫描线或常驻动画；
- 不新增 `requestAnimationFrame` 常驻循环。

## 11. 错误与空状态

- 无武将结果：显示“没有匹配武将”；
- 无战法结果：显示“没有匹配战法”；
- 阵容不完整：禁用模拟并明确缺少的位置；
- 战法未配置：显示空槽，不猜测；
- 历史无样本：显示 0 场和“证据不足”；
- 对阵无样本：保留双方阵容，历史区为空；
- 模拟失败：保留历史与配置证据；
- 配置存在占位符：显示“描述未完全解析”；
- API 失败：保留上一次成功状态并提供重试。

所有 API 文本使用 `textContent` 或统一转义函数。

## 12. 测试

### Python

- 双阵容对阵统计；
- 攻守视角归一；
- 同场不重复；
- 不完整阵容排除；
- 无样本；
- 置信度；
- 最新战报时间；
- API 参数和 404。

### Node

- 阵容校验；
- 武将替换；
- 战法替换；
- 站位交换；
- 深拷贝；
- 模板导入导出；
- 模式切换保留状态；
- 对阵离散结论；
- 配置战法链；
- 模拟战法链；
- 未支持效果；
- reduced-motion。

### Chrome E2E

- 新三栏布局；
- 武将搜索与过滤；
- 三将编组；
- 战法槽选择；
- 站位交换；
- 历史阵容载入；
- 配置、历史、模拟证据切换；
- 对阵分析；
- 战法执行链；
- 模拟器往返；
- 本地模板；
- 1440 / 1024 / 768 / 390；
- 无横向溢出；
- mtime 版本。

## 13. 验收标准

- 可以在一个页面完成武将发现、阵容编组、战法配置、历史研究和模拟验证；
- 三种模式共享同一实验阵容，不丢状态；
- 对阵统计有明确样本和置信度；
- 战法执行链区分配置结构与模拟触发；
- 模拟器联动保留完整战法配置；
- 卡包功能保留；
- 不恢复协议页面；
- 不增加游戏写操作；
- 不新增第二套战斗引擎；
- 桌面、平板、移动端无横向溢出；
- Python、Node、Chrome E2E 全部通过。

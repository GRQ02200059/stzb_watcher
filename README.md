# stzb_watcher

# 《率土之滨》战场实时监控与数据分析平台
## 有问题可联系作者 qq3268276553

本项目用于采集《率土之滨》战场报文，解析后写入 SQLite，并通过 Flask + Web 仪表盘提供实时监控、战报查询、团队统计、考勤与态势分析等能力。

当前仓库的运行形态是“单进程主服务 + 后台抓包线程 + 后台实时写库线程”：

- `api_server.py`：Flask API、静态页面托管、启动入口
- `scrapy_v2.py`：Scapy 抓包，解析明文 / zlib / XOR 数据
- `realtime_writer.py`：监控 `capture_new/` 并实时写库，同时推送 SSE 事件
- `profile_manager.py`：多账号 / 多区服档案与数据库切换

> 当前主入口为 `api_server.py`，Python 依赖维护在 `requirements.txt`。

## Features

| 模块 | 说明 |
|---|---|
| 全部战报 | 可搜索/筛选的完整战报列表，支持战报详情弹窗 |
| 团数据 | 按分组或成员统计战报、胜率、功勋（来源：盟数据）、攻城场次 |
| 战场分析 | 活跃度热力图、战力段位分布、对阵联盟统计、最活跃玩家 |
| 武将阵容 | 三人组合出战胜率排行，基于战报实时计算 |
| 分组武勋 | 按分组汇总 支持按时间段筛选 |
| 同盟成员 | 同步盟内成员数据（功勋、贡献、势力值等） |
| 攻城考勤 | 智能分配攻城人员，智能进行攻城场次出勤统计，支持导出 CSV |
| 战场态势 | 分时段战斗热力图与战力分布 |
| 战场消息 | 实时战场公告与聊天记录 |
| 城池地图 | 格子占领态势可视化 |
| 战场总览 | KPI、最新战报、活跃行军、战术预警、事件时间线与关注对象 |
| 战场情报 | 统一 5026/5028 WorldState；集成全域热区、战术镜头、实时行军、活跃部队、协议实体、风险、地块战报与时间线 |
| 实时部队 | 左侧全状态部队与最近离线 10 分钟、中部实时行军地图、右侧严格同部队 ID 阵容证据；支持武将画像、搜索筛选与战场情报 WID 联动 |
| 阵容研究 | 客户端配置事实、历史战报统计、Kotlin 模拟三层证据；支持阵容详情、常见对手与模拟器联动 |
| 战斗模拟 | 快速对阵、武将/战法侧滑库、本地阵容模板、1/100/1000 次模拟，以及 Server 级语义事件与动作原码复盘 |
| 赛季积分 | 可配置规则版本、综合/战斗/攻城三榜、积分构成、手动奖惩和预览确认重算 |
| 战术检索 | 只读 Query Agent，自然语言查询 WID、风险、队伍、成员、武将、战法和阵容并跳转页面 |
| 同盟势力 | 同盟势力排行、个人势力排行与图表 |
| 指挥交互 | `Ctrl/Cmd + K` 命令面板、紧凑模式、动效偏好、移动端抽屉 |

---

## Tech Stack

- **Backend**: Python 3 · Flask · Flask-CORS · SQLite
- **Packet Capture**: Scapy（XOR 解码 + zlib 解压）
- **Frontend**: 静态 Dashboard（Vanilla JS + CSS Design System）
- **Realtime**: SSE（`/api/stream`）+ 后台实时入库
- **Battle Engine**: Kotlin 1.9.23 / JVM 17；战斗语义镜像自 `/Users/bytedance/stzb/server`
- **Legacy Reference**: 保留独立 Vue/Vite 模拟器子项目 `stzbBattleSimulator-main/`，当前产品使用 `static/` 内的 Vanilla JS 工作台

前端导出依赖已固定到 `static/vendor/`，Dashboard 不依赖 Google Fonts 或
jsDelivr 才能完成基础渲染与 Excel/PDF 导出。

### 模块化沉浸 HUD

当前 12 个可见页面使用同一套“模块化沉浸 HUD”，保留 Vanilla JS 架构，不引入
前端框架。桌面端保持 `208px` 固定侧栏；小屏自动切换为抽屉导航，页面本身不应
产生横向溢出。

页面通过 `data-visual-domain` 归入五个视觉域：

- `intelligence`：战场情报、实时部队、州郡分布；
- `operations`：战斗模拟、打城考勤；
- `organization`：玩家队伍、同盟成员队伍、团数据；
- `analysis`：自定义积分、武将阵容、阵容战法研究；
- `system`：设置中心。

`static/dashboard-hud.mjs` 是 HUD 深模块，向业务页面暴露小型接口：

- `setDomain(tabId)`：切换当前视觉域；
- `setMotionLevel(level)`：应用 `full`、`standard`、`reduced` 三级动效；
- `pulse(element, kind)`：真实事件触发的一次性脉冲；
- `animateValue(element, from, to)`：短时数值过渡；
- `renderState(container, state)`：统一 loading / empty / error 状态；
- `loadHealth()`：读取只读 `GET /api/hud/health` 并渲染运行链路。

系统 `prefers-reduced-motion` 始终优先于浏览器内偏好。强动效只在模拟完成、
新战报、高风险情报和积分重算完成时执行一次，不运行常驻
`requestAnimationFrame` 动画循环。浏览器不支持 `backdrop-filter` 或
`color-mix()` 时会回退到实体表面和强边框。

共享 Surface token、基础组件和状态材质由
`static/dashboard-design-system.css` 集中管理。只允许 Header、Nav、Modal shell
和 Toast 四个批准的全局空间层声明 `backdrop-filter`；业务 Surface 全部使用
实体表面，业务 CSS 不声明 `backdrop-filter` 或 `filter: blur(...)`。该所有权
边界同时由静态 selector 白名单、声明总量和运行时可见层预算约束：

| Surface | Token | 用途 |
|---|---|---|
| Canvas | `--surface-canvas` | 页面画布和最深实体背景 |
| Panel | `--surface-panel` | 普通业务面板；默认使用实体表面，不模糊背景 |
| Raised | `--surface-raised` | 可交互卡片、选中项和短阴影 |
| Overlay | `--surface-overlay` | Sticky Header、工具条和移动抽屉 |
| Modal | `--surface-modal` | Dialog、命令面板和焦点隔离 |

业务模块通过 `window.HudSystem.emit()` 上报语义事件，不直接添加动画 class。
当前事件列表为：

- `intelligence:risk-detected`：高风险情报；
- `battle:report-arrived`：新战报到达；
- `simulation:completed`：模拟完成，存在未支持效果时降级为 warning；
- `score:recalculated`：积分重算完成，只标记变化行；
- `connection:restored`：真实断线后的连接恢复；
- `data:stale`：数据进入陈旧状态；
- `operation:stage-changed`：打城任务阶段变化。

事件效果只来自真实业务变化，并按事件键去重、限时清理；普通刷新、页面进入和
预览操作不会伪造强反馈。请求区域统一使用 `idle`、`loading`、`refreshing`、
`success`、`empty`、`stale`、`warning`、`error` 状态：首次 loading 使用
Skeleton，refreshing 保留已有内容，empty 说明下一步，stale 展示时间和陈旧
程度，非阻断 error 保留最后一次成功内容且不泄露后端解析错误。

动效设置分为三档：

- **Full**：微交互、有限玻璃环境光、真实事件演出和数字插值全部启用；
- **Standard**：默认档，保留微交互、关键玻璃层和与 Full 相同的真实事件演出
  时长，同时禁用非必要漂移并降低其他非关键动效强度；
- **Reduced**：禁用扫描、位移、缩放、数字插值和持续 Skeleton 动画，仍保留
  颜色、边线、焦点、状态文字和即时结果。

性能预算要求同一时刻活动动画元素不超过 **6 个**、同一视口活动
`backdrop-filter` 层不超过 **4 个**，HUD 运行时小于 **35 KB**，全局 CSS
不得使用 `transition: all`，也不得新增常驻动画循环或外部动画依赖。没有
`backdrop-filter` 支持时 Overlay 和 Modal 回退到不透明实体表面与更强边线，
不影响信息和操作。

---

## Quick Start

### 1. 安装依赖

```bash
pip install -r requirements.txt
```

### 2. 启动主服务

首次运行战斗模拟器，或上游战斗引擎更新后，先构建 Kotlin CLI：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  ./gradlew -p battle-engine test installDist
```

然后启动 Flask：

```bash
python api_server.py
```

默认行为：

- 启动 Flask 服务，默认监听 `0.0.0.0:8080`
- 自动初始化 / 补齐数据库表结构
- 自动启动抓包线程
- 自动启动实时写库线程
- 自动打开浏览器访问 `http://localhost:8080`

如果 Kotlin CLI 不存在或启动失败，模拟接口会明确返回
`engine: stzb-kotlin` 错误，不会回退到另一套 Python 战斗算法。

### 3. 数据目录说明

- 抓包原始数据默认写入 `capture_new/`
- 当前账号信息运行时保存在本机 `current_profile.json`
- 历史账号信息运行时保存在本机 `profiles.json`
- 两个文件包含本机路径、区服与角色信息，已从 Git 跟踪中排除
- 不同区服会自动切换到不同 `stzb_<server_ip>.db`

### 4. 可选：离线建库 / 导入

如果你已经有历史报文或中间数据，也可以手动执行：

```bash
python db_import.py
```

该脚本会调用 `db_build.py` 创建基础表，并尝试导入战报、联盟和玩家队伍等数据。

### 5. 可选：保护写接口

默认配置保持本机兼容，不要求 Token。如果服务监听在局域网地址，建议设置：

```bash
export STZB_API_TOKEN='请替换为随机长字符串'
python api_server.py
```

开启后，档案切换、主动刷新、排表生成、积分重算和任务写操作要求
`X-STZB-Token` 或 Bearer Token。可在 Web 的“设置中心 → 写操作 Token”中为
当前浏览器会话配置。

### 6. 验证

```bash
.venv/bin/python -m unittest discover -s test -v
```

测试覆盖 Python API/数据模型、Node 运行时行为，以及使用系统 Chrome 执行的
Dashboard E2E（35 个页面、命令面板、Query Agent、世界场景、战场情报、
阵容研究、模拟器联动、设置与移动端布局）。

### 7. Kotlin 战斗引擎镜像

战斗语义唯一来源是：

```text
/Users/bytedance/stzb/server
```

`battle-engine/` 是可重复生成的独立运行镜像。同步和漂移检查：

```bash
.venv/bin/python scripts/sync_battle_engine.py \
  --source-root /Users/bytedance/stzb/server \
  --target-root battle-engine

.venv/bin/python scripts/sync_battle_engine.py \
  --source-root /Users/bytedance/stzb/server \
  --target-root battle-engine \
  --check
```

`battle-engine/SOURCE.json` 记录源提交、逐文件 SHA-256、独立运行适配、
纳入/排除的源测试和已在源仓库复现的基线失败。不要直接修改
`battle-engine/src/main/kotlin/com/stzb/battle/core/`；战斗语义修改应先进入
`/Users/bytedance/stzb/server`，再重新同步。

引擎元数据接口：

```text
GET /api/simulate/engine
```

返回源提交、同步时间、模拟次数上限和详细回放能力。

### 8. 战斗模拟工作流

1. 打开“战斗模拟”，在左右快速对阵台配置攻守双方。
2. 点击武将卡或战法槽，使用右侧侧滑库替换条目。
3. 选择单场、100 次或 1000 次；`Cmd/Ctrl + Enter` 可直接运行。
4. 批量结果展示胜率，并保留第一场完整回放。
5. 进入“语义事件”查看准备阶段、回合、武将行动包络、伤害、恢复、
   状态、属性变化和效果链。
6. 切换“动作原码”查看 `ClientBattleTextReplayAdapter` 的 action ID、
   参数和编码；事件详情只在有参数证据时关联对应 Server action。

模板保存在当前浏览器：

```text
stzb.simulator.templates.v1
```

模板支持完整对阵或单侧阵容的保存、载入、删除和 JSON 导入/导出，不写入服务器。

所有模拟结果均标记为 `SIMULATION`。它们来自 Kotlin 引擎推演，不等同于真实
历史胜率；存在未支持战法、装备效果或动作投影告警时，结果页会明确标记为
“部分效果未完整执行或投影”。

### 9. 阵容战法研究工作流

“阵容战法研究”复用同一份当前阵容，并提供三个模式：

- **阵容实验室**：配置三名武将、等级、进阶、士气与两个可选战法槽，可交换
  位置并查看稳定阵容 key；
- **对阵分析**：选择双方完整阵容后查询
  `GET /api/intelligence/lineups/<left>/matchup/<right>`，展示精确对阵样本、
  胜率、最近战斗时间和规则化结论；
- **战法执行链**：无模拟结果时展示客户端配置链；完成匹配模拟后展示 Kotlin
  结果中的语义事件链。配置顺序不是实战触发顺序，两类链不会混为一种证据。

历史样本不足时固定显示“证据不足”，历史与模拟冲突时显示“模拟分歧”；界面不
生成无来源的综合分数，也不会把历史胜率或模拟胜率描述为确定性克制。

实验室可将完整阵容送入“战斗模拟”，包括两个可选战法槽和来源上下文。模拟完成
后结果按阵容 key 缓存在当前页面会话，返回研究工作台即可查看模拟证据和执行链。

研究模板只保存在当前浏览器：

```text
stzb.research.lineup-templates.v1
```

模板支持保存、载入、重命名、删除和 JSON 导入/导出，不调用服务器写接口。
卡包武将池仍位于同一材料库；协议字典和游戏操作不在该工作台中暴露。

### 10. 武将画像同步

战斗模拟器使用 A 方案“全息战场立绘”：本地 WebP 画像、阵营冷光、玻璃信息舱
和悬浮扫描光。画像优先读取项目内资源；本地画像缺失或损坏时尝试一次 CDN，
最终使用项目内将魂占位，不影响阵容编辑和模拟。

客户端 `big_card_*.jpg` 实际是循环 XOR 数据，不能直接作为 JPEG 使用。同步器会
使用固定密钥解码、补齐缺失的 JPEG EOI、调用 `cwebp` 转换并生成 manifest。

前置依赖：

```bash
brew install webp
```

同步：

```bash
.venv/bin/python scripts/sync_hero_portraits.py \
  --source-root /Users/bytedance/stzb/work/emulator-backups/Pixel_6-before-12G-20260814-223729/Documents/mini_client_res/card/card_big \
  --hero-table battle-engine/src/main/resources/battle-config/hero_table.csv \
  --target-root static/hero-portraits
```

漂移检查：

```bash
.venv/bin/python scripts/sync_hero_portraits.py \
  --source-root /Users/bytedance/stzb/work/emulator-backups/Pixel_6-before-12G-20260814-223729/Documents/mini_client_res/card/card_big \
  --hero-table battle-engine/src/main/resources/battle-config/hero_table.csv \
  --target-root static/hero-portraits \
  --check
```

生成内容：

```text
static/hero-portraits/
├── manifest.json
├── placeholder.svg
└── cards/*.webp
```

`manifest.json` 记录英雄到画像 ID 的映射、源/输出 SHA-256、转换参数和坏图错误。
图片元数据只存在于英雄目录 API，不会进入模板或模拟请求。

版本化情报快照可单独校验：

```bash
.venv/bin/python scripts/sync_intelligence_snapshot.py \
  --output-root data/intelligence/client-9.2.2 \
  --check
```

运行时只读取项目内 `data/intelligence/client-9.2.2/`，不依赖源项目目录。

卡包研究快照保存在
`data/intelligence/client-9.2.2/research/`。从本机源仓库更新：

```bash
.venv/bin/python scripts/sync_intelligence_snapshot.py \
  --research-source-root /Users/bytedance/stzb/server/src/main/resources \
  --output-root data/intelligence/client-9.2.2 \
  --research-only
```

当前产品界面展示 271 个唯一卡包。卡包只表示收录关系，不提供未经验证的
抽取概率、保底或活动权重；协议与字段字典不在产品界面展示，研究快照也不包含
静态地图、城池和守军。

---

## Project Structure

```
stzb_watcher/
├── api_server.py                 # 主入口：Flask API + 静态页面 + 启动逻辑
├── scrapy_v2.py                  # 抓包与报文解析
├── realtime_writer.py            # 实时监控 capture_new 并写入数据库
├── profile_manager.py            # 多账号 / 多区服档案管理
├── db_build.py                   # SQLite 基础建表
├── db_import.py                  # 离线导入历史数据
├── db_import_ext.py              # 扩展导入逻辑
├── db_extend.py                  # 数据库扩展脚本
├── db_schema_v2.py               # battles_v2 相关 schema
├── intelligence/                 # WorldState 情报服务、配置索引与阵容统计 API
├── data/intelligence/            # 版本化客户端配置与派生规则快照
├── scripts/sync_intelligence_snapshot.py # 白名单快照同步/漂移检查
├── scripts/sync_battle_engine.py       # /stzb/server 战斗引擎镜像同步与漂移检查
├── scripts/sync_hero_portraits.py      # 客户端画像解码、WebP 同步与漂移检查
├── battle-engine/                      # Kotlin 独立运行镜像、CLI、回放契约与源测试
├── battle_engine_adapter.py            # Python 进程适配、输入校验与回放透传
├── static/
│   ├── dashboard.html            # 主仪表盘页面
│   ├── app1.js                   # 页面框架、Tab、部分交互
│   ├── app2.js                   # 业务模块加载逻辑
│   ├── dashboard-command-center.js # 总览、命令面板、预警、时间线、收藏与设置
│   ├── dashboard-design-system.css # 统一设计系统与响应式布局
│   ├── dashboard-design-system.js  # 导航、可访问性与渐进增强
│   ├── dashboard-runtime.mjs       # 可测试的退避、ticker 与安全文本运行时
│   ├── dashboard-meta.js           # 地区、战斗、城池等共享前端元数据
│   ├── world_scene.js              # 战场情报内的行军、部队与协议实体子模块
│   ├── intelligence-center.js/css  # 战场情报中心交互与样式
│   ├── intelligence-map.mjs        # 可测试 Canvas 地图、图层与 bounds 运算
│   ├── intelligence-map-overview.mjs # 热区聚合、语义缩放与全局雷达
│   ├── intelligence-map-navigation.mjs # Home/Back/Forward 与视窗状态
│   ├── intelligence-research.js/css # 武将、战法、阵容三层证据研究中心
│   ├── intelligence-research-catalog.js # 卡包武将池研究
│   ├── vendor/                     # 固定版本的离线导出依赖与许可证
│   ├── simulator-workbench.js/css # 快速对阵、侧滑库、模板、结果与详细复盘
│   ├── simulator-analysis.mjs     # 纯回放分析、准备阶段、行动包络与效果链
│   ├── hero-portraits/             # 本地 WebP 武将画像、占位和 manifest
│   ├── sim.js                     # 旧全局入口兼容层
│   ├── hero_data.js              # 武将基础数据
│   ├── herocfg.js / herocfg.json # 武将配置
│   └── skillcfg.json             # 技能配置
├── hero_scraper/
│   └── output/                   # 武将 / 技能等基础资料
├── docs/research/                # 调研、迁移可行性与历史技术说明
├── stzbBattleSimulator-main/     # 旧 Vue + Vite 模拟器参考实现
└── test/                         # 测试目录
```

---

## API Overview

| Endpoint | 说明 |
|---|---|
| `GET /api/status` | 服务状态与统计概览 |
| `GET /api/hud/health` | HUD 只读运行健康：后端、实时入库、Kotlin 引擎与画像资源 |
| `GET /api/command-center/overview` | 指挥中心聚合、链路新鲜度与可解释预警 |
| `GET /api/battles` | 战报列表（分页 + 筛选） |
| `GET /api/battles/<id>` | 单条战报详情 |
| `GET /api/battles_v2` | 新版战报列表 |
| `GET /api/battles_v2/<id>` | 新版战报详情 |
| `GET /api/team_report` | 团数据（`dim=group\|player`，`period=today\|week\|all`） |
| `GET /api/battle_analysis` | 战场分析汇总 |
| `GET /api/heroes/combo_winrate` | 武将三人组合胜率 |
| `GET /api/team_users` | 同盟成员列表 |
| `GET /api/attendance` | 攻城考勤 |
| `GET /api/ranking_v2` | 排行榜（`dim=player\|union\|zone`） |
| `GET /api/stream` | SSE 实时事件流 |
| `POST /api/query-agent/messages` | 只读战术检索 |
| `POST /api/simulate` | Kotlin 战斗模拟；`repeat` 仅支持 `1/100/1000` |
| `GET /api/simulate/heroes` | Kotlin 配置中的可用武将与战法 |
| `GET /api/simulate/engine` | 战斗引擎源提交、同步时间与回放能力 |
| `GET /api/world/viewport` | 世界场景视窗地块 |
| `GET /api/world/armies` | 当前活跃部队 |
| `GET /api/world/marches` | realMarch 行军 |
| `GET /api/intelligence/world/summary` | 统一 WorldState 状态、覆盖与新鲜度 |
| `GET /api/intelligence/world/viewport` | 情报地图视窗 |
| `GET /api/intelligence/world/overview` | 全域/战区热区聚合，只返回非空 bucket |
| `GET /api/intelligence/world/tile/<wid>` | 地块风险、行军、战报和事件证据 |
| `GET /api/intelligence/world/events` | WorldState 事件时间线 |
| `GET /api/intelligence/world/risks` | 可解释地块风险分量 |
| `GET /api/intelligence/live-armies` | 当前全部状态部队、最近离线、realMarch 与严格阵容证据 |
| `GET /api/intelligence/config/manifest` | 客户端配置快照版本与校验信息 |
| `GET /api/intelligence/heroes[/<id>]` | 武将配置检索与详情 |
| `GET /api/intelligence/skills[/<id>]` | 战法配置检索与详情 |
| `GET /api/intelligence/lineups[/<key>]` | 历史阵容统计、常见对手和置信度 |
| `GET /api/intelligence/card-packs[/<id>]` | 卡包武将池、层级与武将反查 |
| `GET /api/custom_scores` | 综合、战斗或攻城积分榜 |
| `GET /api/custom_scores/player/<name>` | 玩家积分构成和调整记录 |
| `GET/POST /api/custom_scores/rules` | 赛季积分规则版本 |
| `POST /api/custom_scores/preview` | 重算预览，不写数据库 |
| `POST /api/custom_scores/recalc` | 使用预览 token 确认重算 |

赛季积分中心默认使用“同盟综合贡献”规则，支持赛季奖励和打城排班预设。
规则修改会创建新版本；重算必须先预览，页面会展示排名变化和每项积分构成。
武勋或出勤来源缺失时会标记数据不完整，不把缺失数据伪装成完整的 0。
积分时间范围使用战报 `battles_v2.time` 和出勤 `attendance.time`，支持起止日期、
今天、近 7 天、本月和全部。结束日期包含整天，按次日 `00:00:00` 排他过滤。

战场情报地图按视野跨度自动切换：

- **全域热区**：缩得较远时聚合风险、敌我、变化与部队密度；
- **战区轮廓**：中距离展示地块岛与热点；
- **战术镜头**：近距离展开真实 WID 格子。

左下全局雷达始终显示当前视窗位置；地图提供 Home、上一视窗、下一视窗、
WID 定位和雷达拖动，避免在大范围稀疏地图中丢失方向。

原“世界场景”已合并到“战场情报”：

- `态势地图`：雷达、热区、真实格子与地块详情；
- `实时行军`：realMarch 路径与逐跳时间；
- `活跃部队`：MapArmyTuple、目标、士气、Buff 与战报摘要；
- `协议实体`：warShips、assistArmies、armyGroups、shortMessages。

旧 `tab30` 与 `loadWorldScene()` 保留为兼容入口，调用时自动转到战场情报。

### 实时部队三栏指挥台

左侧导航“实时部队”使用只读接口：

```text
GET /api/intelligence/live-armies?offlineMinutes=10
```

页面把统一 WorldState 中的当前部队、`realMarch` 路径、玩家、同盟、目标地块和
最近离线 10 分钟数据聚合到同一工作区：

- 左栏按最早抵达优先展示当前状态，并提供 `2 分钟 / 10 分钟 / 30 分钟 /
  1 小时 / 全部时间`筛选；默认仅显示最后 10 分钟内观测或删除的部队；
- 中栏用 Canvas 展示当前位置、下一格和最终目标，支持点击选中、缩放和平移；
- 右栏展示玩家、同盟、WID、倒计时、武将名字、画像、等级、进阶与战法证据；
- 双击地图标记或点击“在战场情报中定位”会切回战场情报并定位当前 WID；
- 复用全局 SSE，只在 tab35 与文档可见时做 350ms 去抖刷新，每秒倒计时不请求接口。

顶部 WorldState 时间与每支部队自己的最后观测时间分开显示。超过 10 分钟未再次
观测、但仍残留在当前 WorldState 的部队不会被删除；切换“全部时间”后可查看，
并以“过期待确认”标记，避免把历史位置误认为实时位置。

阵容采用严格模式：只允许 `world_armies.army_id` 精确匹配
`battles_v2.atk_team_id` 或 `battles_v2.def_team_id`。精确命中显示战报 ID、时间、
攻守侧与真实武将；未命中固定显示“无同 ID 战报，阵容未知”，不会按玩家、同盟
或近期常用队伍猜测。

| `GET /api/profiles` | 账号档案列表 |
| `POST /api/switch_profile` | 切换当前账号 |
| `POST /api/refresh` | 触发数据入库 |

---

## Notes

- 当前主入口是 `python api_server.py`，不是 `start_pipeline.py`
- 依赖列表见 `requirements.txt`
- README 中的功能列表包含当前主 Dashboard 的主要能力，但部分页面仍在持续演化
- 本项目仅用于个人学习与团队内部数据管理，请遵守相关法律法规及游戏服务条款

---


## 功能演示
<img width="1265" height="675" alt="image" src="https://github.com/user-attachments/assets/3570d085-4055-421b-bb90-385ec4b763f7" />
<img width="1258" height="664" alt="image" src="https://github.com/user-attachments/assets/638fcef1-a27b-4a35-9ddb-22b2499ef8f4" />
<img width="1262" height="670" alt="image" src="https://github.com/user-attachments/assets/afd36633-c1ed-49b4-9737-ebc9c5b41653" />
<img width="1268" height="669" alt="image" src="https://github.com/user-attachments/assets/9f08f1b0-af04-46b8-b150-ac2079e146ce" />
<img width="1274" height="650" alt="image" src="https://github.com/user-attachments/assets/b579cf72-613a-4dbc-b747-e5ff9e7d71a0" />
<img width="1262" height="670" alt="image" src="https://github.com/user-attachments/assets/1769818a-10ae-4a87-8325-0001a345e590" />
<img width="1259" height="668" alt="image" src="https://github.com/user-attachments/assets/acd6285b-a4d7-46c5-bd70-23f70b872e3c" />
<img width="1251" height="671" alt="image" src="https://github.com/user-attachments/assets/cec2b2fe-6d72-4352-a51f-26320f939f1e" />
<img width="1280" height="668" alt="image" src="https://github.com/user-attachments/assets/569f56f0-d8c5-4768-be7f-f578233d2692" />

## License

MIT

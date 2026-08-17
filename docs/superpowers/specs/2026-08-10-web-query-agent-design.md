# Web 只读 Query Agent 设计

日期：2026-08-10

## 背景

网页端重构会把地图、行军和战场监控建立在协议优先的世界状态读模型上。对应研究见
[`docs/research/2026-08-10-world-scene-protocol-web-dashboard.md`](../../research/2026-08-10-world-scene-protocol-web-dashboard.md)。

在这个基础上，Agent 可以作为统一查询入口：用户用自然语言提问，系统从已解析、已入库、可追溯的数据中回答，并提供页面跳转和筛选动作。

## 目标

- 提供全局只读 Agent 查询入口，覆盖地图、行军、战场监控、战报、同盟成员等高频数据。
- 允许自然语言查询、态势解释和页面导航。
- 每个回答都带结构化证据：数据来源、更新时间、关联 WID / armyId / battleId / userId。
- Agent 能生成 UI 动作，例如打开页面、定位 WID、套用筛选条件、打开战报详情。
- Agent 不能发包、不能执行游戏动作、不能改数据库、不能启动自动化任务。

## 非目标

- 不接入自动化执行、Agent 控制、发包、屯田、练兵、压秒作战或任何写操作。
- 不允许模型直接访问 SQLite、文件系统、shell、协议原始包或日志。
- 不让模型自行编造 SQL、协议字段或未入库事实。
- 不把参考项目里的 AgentBrain 全量迁入；只借鉴“上下文白名单”和“事实投影”思想。

## 架构

```text
React Agent 面板
  -> POST /api/query-agent/messages
  -> QueryAgentService
  -> Intent Router
  -> Read-only Query Tools
  -> World/Battle/Alliance read models
  -> Answer + citations + UI actions
```

### 后端模块

`QueryAgentService`

- 接收用户消息、当前页面上下文和可选选中对象。
- 调用 Intent Router 判断查询类别。
- 调用只读工具，不直接访问数据库。
- 汇总工具结果，生成回答、证据和 UI 动作。

`QueryIntentRouter`

- 将自然语言归类为地图、行军、战报、同盟、监控、导航或综合分析。
- 对缺失关键信息的问题返回澄清请求。
- 对写操作、发包、自动化和执行类请求返回拒绝，并解释当前入口只读。

`QueryContextBuilder`

- 只向模型暴露白名单字段。
- 禁止 raw packet、SQL、文件路径、日志、shell、代码、token、内部配置进入上下文。
- 按数量限制裁剪上下文，例如最近战报、当前行军、视口地块、候选成员。

`QueryToolRegistry`

- 注册只读工具。
- 每个工具有固定输入 schema、输出 schema、超时和最大返回行数。
- 工具输出必须包含 `source`、`freshness` 和可追溯 ID。

## 第一批只读工具

`world.viewport`

- 输入：`rowUp,rowDown,colLeft,colRight,include`
- 输出：视口内地块、行军、玩家、同盟、数据新鲜度。

`world.tile`

- 输入：`wid`
- 输出：`WORLD_CITY` 投影、所有者、同盟、保护/免战时间、状态、raw chunk 摘要。

`world.armies`

- 输入：`state, owner, wid, within, limit`
- 输出：符合条件的 `MapArmyTuple` 投影。

`world.marches`

- 输入：`activeOnly, wid, owner, armyId`
- 输出：`MapArmyTuple + realMarch` 组合后的行军状态。

`world.explainArmy`

- 输入：`armyId`
- 输出：队伍当前状态、最近变更、直接删除或 block unlink 原因、关联战报候选。

`battle.search`

- 输入：玩家、同盟、WID、时间、结果、战斗类型。
- 输出：战报摘要列表。

`battle.detail`

- 输入：`battleId`
- 输出：战报详情、攻守方、武将、战法、来源字段。

`alliance.member`

- 输入：玩家名、UID、分组、同盟。
- 输出：成员资料、队伍关联、近期战报关联。

`monitor.summary`

- 输入：时间窗口、视口或默认全局。
- 输出：当前活跃行军、危险目标、最近变化、数据缺口。

`monitor.explainDelta`

- 输入：`seq`、`teamId` 或 `wid`
- 输出：某次监控变化的解释和来源包摘要。

`navigation.resolve`

- 输入：实体类型和 ID。
- 输出：前端路由、筛选参数和高亮目标。

## 前端交互

Agent 入口采用全局右侧抽屉或顶部命令面板，命名为“战术检索”。

用户可以在任何页面提问。前端会附带当前页面上下文，例如当前选中的 WID、队伍 ID、战报 ID、时间范围或筛选条件。

回答格式：

```text
结论：一句话回答
依据：3-5 条证据，每条带来源和更新时间
相关对象：WID / armyId / battleId / userId
可执行 UI 动作：打开页面、定位地图、设置筛选、打开详情
```

示例问题：

- “102008461 这支队伍现在在哪，什么时候到？”
- “这个 WID 周围有什么行军？”
- “某某盟正在打哪些城？”
- “为什么刚才那支队伍消失了？”
- “帮我打开张三最近的战报。”
- “今天战场监控里最危险的目标有哪些？”

## 安全边界

- Agent 只返回 `uiActions`，不执行后端写操作。
- 后端拒绝所有执行型意图：出征、召回、发包、自动化、建设、屯田、练兵、领取任务。
- 模型输出必须通过 schema 校验；无法校验时返回“无法生成可靠回答”。
- 所有回答必须来自工具返回事实，不能引用模型知识覆盖数据库事实。
- 所有工具只读；数据库连接使用只读查询路径。
- UI 动作只改变前端路由和筛选状态。

## 错误处理

- 缺少世界状态：提示先抓取 5026/5028 或进入地图刷新视野。
- 数据过旧：回答中标注 stale，并建议刷新数据。
- 问题过宽：返回澄清，例如要求用户指定时间范围、同盟、玩家或 WID。
- 查询无结果：说明是筛选无结果、未抓到数据，还是对应实体不存在。
- 模型不可用：保留手动搜索入口，页面不阻塞。

## 测试策略

- Intent Router 测试：只读查询、导航、分析、拒绝写操作。
- Query Tool schema 测试：输入校验、输出字段、行数限制、证据字段。
- Context Builder 测试：禁止 raw packet、SQL、日志、路径、shell、代码进入模型上下文。
- Agent Service 测试：回答必须包含证据和 UI 动作；写操作请求必须拒绝。
- 前端测试：Agent 面板可打开、可发送问题、可展示证据、可执行 UI 跳转。
- 回归测试：地图、行军、监控查询基于协议读模型，不回退到 `000013a4` 文件扫描。

## 与世界场景重构的关系

Query Agent 依赖协议优先读模型。实现顺序应为：

1. 先补 `5026/5028` 协议解析和 typed projection。
2. 再提供只读 world/battle/monitor 查询 API。
3. 最后接入 Query Agent。

如果协议读模型尚未完成，Agent 只能接入现有 `map_cells`、`battle_monitor_moves`、`battles_v2` 等兼容视图，但回答必须标注“数据口径较旧/字段不完整”。

## 验收标准

- 用户能通过自然语言查询 WID、队伍、玩家、战报和当前监控状态。
- Agent 回答都能点击跳转到对应页面或套用筛选。
- 写操作、自动化和发包类请求全部被拒绝。
- 回答包含证据和更新时间。
- Query Agent 不依赖旧 `static/app2.js` 的全局状态。
- 关键测试通过：路由、工具 schema、上下文白名单、前端 smoke test。

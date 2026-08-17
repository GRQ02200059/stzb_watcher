# STZB 统一世界状态与战场情报中心设计

日期：2026-08-14
状态：设计已确认，待实施
目标端：Web 优先，Android 后续复用同一读模型
数据真值来源：`/Users/bytedance/stzb`

## 1. 目标

本批次把 5026 与 5028 合并为一套可追溯、可回放、可解释的统一世界状态，并基于 `/Users/bytedance/stzb` 中的协议资料和客户端配置扩充两个产品模块：

1. 战场情报中心：风险热力地图、归属、保护、行军、情报新鲜度、预警、地块详情和状态时间线。
2. 阵容战法研究中心：客户端配置真值、当前战报统计和 Kotlin 模拟结果三层联动。

所有新增能力只读。不得主动发包、自动出征、修改游戏状态或将模型输出直接转为游戏动作。

## 2. 已验证事实

### 2.1 5026 与 5028 是同一领域状态

客户端 9.2.2 的两个命令均为固定 31 槽数组，并共享以下领域：

- visualField；
- mapUsers；
- unions；
- armies；
- warShips；
- assistArmies；
- armyGroups；
- shortMessages；
- worldChunks；
- careerSupport；
- realMarch。

区别：

- 5026 是请求视窗或 Block 范围内的完整基线，可多帧；
- 5028 是相对最近完成 5026 基线的增量更新与删除；
- 5028 的删除槽、`clearChunks` 和 `blockInfo` 不能被解释成另一份业务数据。

零售抓包证据：

- 245 条有效 5026；
- 342 条有效 5028；
- 两者全部为 31 槽；
- 5026 的 `worldChunks` 非空率为 95.1%；
- 5028 的 `armyChanges` 非空率为 57.0%；
- 5028 的 `blockInfo` 非空率为 100%。

### 2.2 数据快照真值

以下 `/Users/bytedance/stzb` 文件与当前 `battle-engine` 副本 SHA-256 完全一致：

- `hero_table.csv`：2,077 行；
- `skill_table.csv`：6,572 行；
- `skill_detail_table.csv`：12,694 行；
- `skill_effect_table.csv`：206 行。

这些文件可以作为客户端 9.2.2 的配置真值，但 Web 不应运行时依赖 `battle-engine` 或 `/Users/bytedance/stzb` 的绝对路径。

## 3. 非目标

- 不复制 6GB APK 解包树、DLL、调试截图或反编译目录。
- 不复制零售抓包中的账号、玩家、同盟和军团隐私数据。
- 不把 `cfg_tables_dump/*.strings.txt` 当成结构化行数据。
- 不实现自动开荒、自动出征、发包、召回、征兵或无人值守控制。
- 不根据未知配置猜测地块等级、守军强度或战法效果。
- 不用 JavaScript `Number` 中转 visualField 的任意 signed int64。

## 4. 版本化情报数据包

### 4.1 目录

```text
data/intelligence/client-9.2.2/
├── manifest.json
├── SOURCE.md
├── hero_table.csv
├── skill_table.csv
├── skill_detail_table.csv
├── skill_effect_table.csv
├── world_scene_schema.json
├── land_intelligence_rules.json
└── checksums.sha256
```

### 4.2 Manifest

`manifest.json` 必须包含：

- `datasetVersion`：`client-9.2.2`；
- `clientPackage`：`com.netease.stzb.netease`；
- `clientVersion`：`9.2.2`；
- `generatedAt`；
- 每个文件的源路径、目标路径、行数、字节数和 SHA-256；
- 生成脚本版本；
- 隐私声明；
- 派生文件的证据来源。

### 4.3 派生规则

`world_scene_schema.json` 记录：

- 31 个槽位名称；
- 5026 基线语义；
- 5028 增量语义；
- 数组/对象/null 类型；
- 删除语义；
- Block 语义；
- tuple 固定长度；
- 64 位字段列表；
- 文档证据路径。

`land_intelligence_rules.json` 记录：

- 土地等级 0–9 的语义和热力色；
- `NewResLv` 编码规则：十位为等级、个位为资源种类；
- 未知等级回退；
- 情报新鲜度阈值；
- 归属关系颜色；
- 风险评分各分量；
- 规则来源路径。

这些 JSON 是经过测试锁定的派生事实，不是从字符串池猜测的配置行。

### 4.4 更新工具

新增：

```text
scripts/sync_intelligence_snapshot.py
```

职责：

1. 从显式 `--source-root` 读取允许清单内文件；
2. 验证 CSV 列头、行数和主键；
3. 生成派生 JSON；
4. 输出 manifest 与 SHA-256；
5. 拒绝未知文件、符号链接逃逸和隐私路径；
6. 支持 `--check` 检测当前快照是否漂移。

运行时只读取项目内快照，不读取源目录。

## 5. 统一世界状态

## 5.1 领域模型

```text
WorldState
├── version
├── latestCompleted5026OrderId
├── latestEventSeq
├── observedAreas
├── visualFields
├── users
├── unions
├── tileChunks
├── tiles
├── armies
├── armyBlocks
├── warShips
├── shipBlocks
├── assistArmies
├── assistArmyBlocks
├── armyGroups
├── shortMessages
├── careerSupports
├── realMarches
└── provenance / freshness / completeness
```

5026 与 5028 不产生两份状态。它们只产生对 `WorldState` 的不同类型操作。

## 5.2 5026 多帧拼装

当前 `WorldSceneAssembler` 只记录是否处于拼装阶段，没有真正合并多帧槽 14。新实现必须：

1. 接收 5026 中间帧 `[18] == 0`；
2. 按 WID 和 chunk type 合并 `[14] worldChunks`；
3. 合并同轮用户、同盟、部队、实体、Block membership 和 visualField；
4. 最后一帧 `[18] > 0` 时生成一个完整 `WorldSceneTransaction`；
5. 中间帧只写原始包和拼装状态，不发布完整状态；
6. 拼装超时、来源切换或新轮次开始时丢弃未完成缓存并记录诊断事件。

## 5.3 5026 基线应用

5026 的“全量”是本次观测范围的权威基线，不代表整个服务器世界。

应用时：

- 根据 `[17] observedMapArea`、visualField Block 和 `[21..23]` membership 得到覆盖范围；
- 覆盖范围内未再出现的 tile chunk 可以失效；
- 覆盖 Block 中未再出现的 army/ship/assist membership 可以解绑；
- 只有实体不再属于任何 Block，或明确收到全局删除，才标记实体删除；
- 不在本次覆盖范围的历史状态保持不变，但新鲜度继续衰减；
- 5026 的 realMarch 按协议作为完整集合替换；
- 完成后递增 `WorldState.version` 并发布 `world_snapshot_complete`。

如果 5026 缺失 observed area 和可解释 Block，系统只 upsert，不做推断清理，并将 completeness 标为 `partial-baseline`。

## 5.4 5028 增量应用

普通 5028 必须满足：

- 已有完成的 5026；
- `serverOrderId > latestCompleted5026OrderId`；
- 特殊值 `-999999999` 可绕过门槛，但标记为 `special-bypass`。

应用规则：

- mapUsers、unions、armies、ships、assist、groups、messages、chunks 和 realMarch 按主键覆盖；
- `[15] clearChunks` 只清指定 WID 的指定 chunk type；
- `[7]/[9]/[11]` 在 `blockInfo.mode == 2` 时只解除当前 Block membership；
- `state=0` 为明确全局删除；
- realMarch 只覆盖传入项，不清空未出现项；
- visualFieldChanges 合并到现有 Block 掩码，不替换整个 visualField；
- 每次接受的 5028 递增 `WorldState.version` 并发布 `world_state_delta`。

## 5.5 数据表

保留：

- `world_scene_packets`
- `world_map_users`
- `world_unions`
- `world_tiles`
- `world_armies`
- `world_real_marches`
- `world_scene_entities`

新增：

- `world_state_versions`
- `world_observed_areas`
- `world_tile_chunks`
- `world_visual_field_blocks`
- `world_army_blocks`
- `world_ship_blocks`
- `world_assist_army_blocks`
- `world_state_events`

投影表增加：

- `first_seen_seq`
- `last_seen_seq`
- `last_changed_seq`
- `observed_at_ms`
- `source_cmd`
- `state_version`
- `completeness`
- `deleted_at_seq`

迁移必须幂等，旧库自动补列。旧 `map_cells` 和 `battle_monitor_moves` 继续作为兼容投影。

## 5.6 新鲜度

统一阈值：

- `fresh`：0–120 秒；
- `aging`：120–600 秒；
- `stale`：超过 600 秒；
- `unknown`：无可用观测时间。

阈值写入 `land_intelligence_rules.json`，后端与前端读取同一配置。

## 5.7 状态事件

`world_state_events` 保存可回放的领域变化：

- snapshot completed；
- tile owner changed；
- tile chunk cleared；
- army added/changed/deleted；
- block linked/unlinked；
- march changed；
- protection changed；
- data gap；
- stale packet rejected。

事件只保存必要差异和证据引用，不复制整包。

## 6. 战场情报中心

采用已确认的 A 布局：

```text
┌──────────────────────────────────────────────────────────────┐
│ 图层工具栏 / WorldState 版本 / 覆盖与新鲜度                  │
├───────────────────────────────────────┬──────────────────────┤
│                                       │ 选中地块情报         │
│ 统一风险热力地图                      │ 风险 / 行军 / 战报   │
│                                       │ 证据                 │
├───────────────────────────────────────┴──────────────────────┤
│ WorldState 变化时间线                                       │
└──────────────────────────────────────────────────────────────┘
```

## 6.1 图层

- 综合态势；
- 土地等级；
- 势力归属；
- 行军威胁；
- 情报新鲜度；
- 保护/免战；
- 高危目标；
- 关注对象；
- 变化热区。

图层组合状态保存在浏览器本地。

## 6.2 地图视觉

土地等级热力色：

| 等级 | 颜色 |
|---:|---|
| 未知/0 | `#18232d` |
| 1 | `#263746` |
| 2 | `#24536a` |
| 3 | `#167a78` |
| 4 | `#199f69` |
| 5 | `#75b83b` |
| 6 | `#d1b52c` |
| 7 | `#e87e25` |
| 8 | `#ed4936` |
| 9 | `#ff174f` |

覆盖层顺序：

1. 等级底色；
2. 高等级强化；
3. 归属角标；
4. 过期情报斜线；
5. 候选/关注边框；
6. 行军路径；
7. 活跃部队；
8. 选中双边框；
9. 放大后的等级文字。

地图使用 Canvas，不为每格创建 DOM。visualField 保持字符串或 BigInt 安全表示。

## 6.3 选中地块

右侧详情有四个标签：

### 风险

- 风险总分；
- 各分量；
- 归属；
- 土地等级；
- 保护/守卫剩余；
- 情报年龄；
- 数据完整性。

### 行军

- 当前前往该地块的部队；
- 玩家/同盟；
- 起点/当前/下一格；
- 抵达时间；
- 士气；
- 目标类型；
- 直接删除或 Block 解绑历史。

### 战报

- 关联战报数量；
- 最近战报；
- 攻守胜率；
- 常见阵容；
- 样本量；
- 一键筛选全部战报。

### 证据

- WorldState 版本；
- 最后完整快照；
- 最近增量；
- packet seq；
- serverOrderId；
- source cmd；
- completeness；
- 原始包引用，不直接展示隐私字段。

## 6.4 风险评分

风险评分 0–100，仅用于解释排序，不宣称胜负概率。

初始权重：

- 土地等级：0–25；
- 敌方归属：0–15；
- 前往部队数量：0–20；
- 最早抵达时间：0–15；
- 估算兵力：0–10；
- 保护/守卫状态：0–5；
- 情报过期惩罚：0–10。

每条评分必须返回分量，不能只返回总分。缺失数据不填 0，而是标记 unknown 并降低 confidence。

## 6.5 时间线

底部时间线按 `WorldState.version` 展示：

- 完整快照完成；
- 地块归属变化；
- 部队出现/变化/删除；
- 集结阈值触发；
- 到达时间变化；
- 情报变 stale；
- 数据缺口和拒绝包。

支持按实体、类型和时间过滤，并可跳转到对应地图状态。首阶段只回放事件和当前状态，不持久化完整历史地图副本。

## 6.6 API

新增：

```text
GET /api/intelligence/world/summary
GET /api/intelligence/world/viewport
GET /api/intelligence/world/tile/<wid>
GET /api/intelligence/world/events
GET /api/intelligence/world/risks
GET /api/intelligence/config/manifest
```

旧 `/api/world/*` 保持兼容，新页面只使用 `/api/intelligence/world/*`。

统一返回：

- `worldStateVersion`
- `latestBaseline`
- `latestDelta`
- `freshness`
- `completeness`
- `coverage`
- `data`

## 7. 阵容战法研究中心

## 7.1 三层证据

每个结论必须标明来源：

1. `CONFIG_FACT`：客户端配置事实；
2. `BATTLE_STAT`：当前数据库历史统计；
3. `SIMULATION`：Kotlin 模拟结果。

UI 使用不同标签和说明，禁止混成一个“推荐分”。

## 7.2 武将详情

- 名称、阵营、稀有度、兵种、攻击距离、cost；
- 五维基础值与成长；
- 初始战法；
- 赛季与卡种；
- 关联历史阵容；
- 使用率、胜率、样本量；
- 一键加入模拟器。

过滤掉默认画像和无真实战斗数值的外观/占位行；过滤规则写入 manifest。

## 7.3 战法详情

- 名称、品质、类型、距离；
- 准备回合；
- 初始/最大概率；
- 目标描述；
- 完整描述；
- detail 列表；
- effect 名称；
- 常量、谋略系数、持续回合、可用次数；
- 条件、选择器和状态；
- 配置引用完整性。

描述中的 `#effect_01#` 等占位符使用 detail/effect 数据解释；无法解析时保留原文并标记 unresolved。

## 7.4 阵容画像

- 三名武将；
- 速度顺序；
- 攻击距离覆盖；
- 兵种/阵营；
- 战法类型分布；
- 控制、恢复、伤害、增益画像；
- 历史样本；
- 常见对手；
- 优势/劣势对阵；
- 统计置信度。

## 7.5 模拟器联动

- 从研究中心把阵容送入现有模拟器；
- 保留等级、进阶、士气、战法；
- 模拟结果回链研究页面；
- 不将模拟结果描述为真实胜率；
- 显示引擎覆盖/降级信息。

## 7.6 API

```text
GET /api/intelligence/heroes
GET /api/intelligence/heroes/<id>
GET /api/intelligence/skills
GET /api/intelligence/skills/<id>
GET /api/intelligence/lineups
GET /api/intelligence/lineups/<key>
```

配置查询使用进程内只读索引和缓存，不为每次请求重扫 CSV。

## 8. Query Agent 扩充

新增只读工具：

- `intelligence.worldSummary`
- `intelligence.tile`
- `intelligence.risks`
- `intelligence.hero`
- `intelligence.skill`
- `intelligence.lineup`
- `intelligence.explainRisk`

回答必须引用：

- WorldState 版本；
- 数据新鲜度；
- 配置数据版本；
- 战报样本量；
- 模拟次数；
- 结论类型。

仍拒绝所有游戏动作、发包、自动化和数据库任意写操作。

## 9. 错误处理

- 无完成 5026：世界状态为 `uninitialized`，普通 5028 拒绝；
- 5026 拼装超时：记录 `snapshot_assembly_timeout`；
- stale 5028：写拒绝事件，不修改状态；
- observed area 缺失：不推断清理；
- Block membership 不完整：不全局删除；
- 配置主键重复：快照生成失败；
- skill/detail/effect 引用缺失：条目标记 incomplete，不中断整个服务；
- 情报过期：显示 stale，不沿用旧风险为实时结论；
- 未知土地等级：使用未知色和 unknown 风险分量。

## 10. 测试

### 10.1 世界状态

- 5026 单帧基线；
- 5026 多帧 chunk 拼装；
- 5026 覆盖范围内清理；
- 覆盖范围外保留；
- 无 observed area 时不清理；
- 5028 覆盖更新；
- `clearChunks` 子类型清理；
- direct delete；
- Block unlink 后仍有 membership；
- 最后 membership 删除后全局删除；
- realMarch 5026 替换/5028 覆盖；
- visualField int64 安全；
- stale 5028 与特殊 bypass；
- 状态版本和事件顺序。

### 10.2 数据快照

- SHA-256；
- 行数；
- CSV 列头；
- 主键唯一；
- hero 初始战法存在；
- skill main detail 存在；
- detail effect 存在；
- 占位武将过滤；
- 禁止路径逃逸；
- `--check` 漂移检测。

### 10.3 API 与前端

- summary/viewport/tile/events/risks；
- 缺表与空状态；
- 风险分量和 confidence；
- 配置检索；
- Query Agent 证据；
- Node 风险色板和图层顺序；
- Chrome E2E：地图、图层、详情标签、时间线、阵容详情和模拟器跳转；
- 375/768/1024/1440 宽度；
- 外部网络阻断时仍可使用。

## 11. 实施顺序

1. 情报数据快照工具与 manifest；
2. WorldState schema、5026 多帧事务和 5028 合并；
3. Block membership、范围清理、新鲜度与事件；
4. 情报 API；
5. Canvas 风险地图和 A 布局；
6. 地块详情、预警和时间线；
7. 阵容战法配置 API；
8. 阵容研究 UI 与模拟器联动；
9. Query Agent 扩充；
10. 全量测试、Chrome E2E 和完成审计。

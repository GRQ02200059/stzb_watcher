# 抓包数据利用缺口审计

日期：2026-08-15

范围：审计 `/Users/bytedance/stzb_watcher/capture_new` 中已经解码为 JSON 或文本的本地抓包，判断哪些数据当前尚未被 Web 系统正确利用。只统计消息类型、结构、字段与消费链，不记录账号、玩家、同盟、聊天正文等具体值。

## 1. 结论

本地抓包覆盖 2026-08-11 22:18 至 2026-08-15 02:53：

- 25,812 个带时间戳的抓包文件；
- 90 个消息类型目录；
- `capture_new` 约 129 MB；
- 实时 writer 明确监控并处理 16 个消息类型。

最值得做的不是继续增加零散排行榜，而是：

1. **P0：立即纠正 `0x18aa / 0x18ae` 的错误解释。**
2. **P1：利用 `0x18b6 / 0x18b7` 的同盟外交关系，修正地图敌我判断。**
3. **P1：把 `0x15f95 / 90005` 中的天气、战区、军队告警和资源变化投影成业务表。**
4. **P2：利用 `0x0edf / 3807` 的世界事件，增强战场地图和时间线。**
5. **P2：利用 `0x01fd / 509` 与 `0x0367 / 871` 的赛季历史和世界进度。**

`0x0898`、心跳、反作弊回执、SDK token、直播状态、黑名单、邮件等数据不适合进入当前战场指挥台，应继续忽略或只保留诊断统计。

## 2. 当前消费覆盖

`realtime_writer.py` 的文件扫描白名单只有 16 类：

```text
0000000a  完整战报
00000015  玩家自身信息
0000005c  同盟战报
00000067  同盟成员
0000012d  行军信息
000001fe  玩家赛季统计
0000029f  武将记录
000002bc  同盟/玩家势力排行
0000030c  游戏公告
00000834  聊天与战斗通知
000013a2  世界场景基线
000013a4  世界场景增量
00001863  战区玩家
000018aa  当前被错误解释为战场动态
000018ae  当前被错误解释为攻城队列
00015f95  通用 DB 更新日志
```

来源：[`realtime_writer.py:L2751-L2770`](file:///Users/bytedance/stzb_watcher/realtime_writer.py#L2751-L2770)。

其余约 74 类消息没有进入 writer 白名单。未进入白名单不等于都有价值；许多是心跳、回执、聊天、SDK 或活动边缘数据。

## 3. P0：`0x18aa / 0x18ae` 当前业务解释错误

### 3.1 当前代码

当前代码将：

- `000018aa / 6314` 解释为“攻城战场实时动态”；
- `000018ae / 6318` 解释为“攻城队列快照”。

它把 `0x18aa` 的三元组解释为：

```text
[wid, attacker_uid, nearby_uid_csv]
```

并写入 `battle_field`。来源：[`realtime_writer.py:L1686-L1743`](file:///Users/bytedance/stzb_watcher/realtime_writer.py#L1686-L1743)。

`0x18ae` 被解释为成员队列并写入 `battle_queue`。来源：[`realtime_writer.py:L1746-L1769`](file:///Users/bytedance/stzb_watcher/realtime_writer.py#L1746-L1769)。

writer 还会为这两类消息生成 `battle_field`、`battle_queue` 事件。来源：[`realtime_writer.py:L2447-L2501`](file:///Users/bytedance/stzb_watcher/realtime_writer.py#L2447-L2501)。

### 3.2 客户端真值

客户端命令目录和反编译代码证明：

| 消息 | 十进制 | 客户端命令 |
|---|---:|---|
| `0x18aa` | 6314 | `UNION_BUILDING_SPEED_UP_ADD` |
| `0x18ad` | 6317 | `UNION_BUILDING_SPEED_UP_REMOVE` |
| `0x18ae` | 6318 | `UNION_BUILDING_SPEED_UP_ALL_LIST` |
| `0x18af` | 6319 | `UNION_BUILDING_SPEED_UP_PROGRESS` |

`6318` 由 `UnionBuildingSpeedUpUI` 请求，返回 `[互助列表, 今日互助次数]`。来源：[`UnionBuildingSpeedUpUI.cs:L155-L203`](file:///Users/bytedance/stzb/stzb_9.2.2_out_branch_9.1.1776213/assets/decompiled/Game.UI.GamePlay/Tenth.UI/UnionBuildingSpeedUpUI.cs#L155-L203)。

列表项由 `UnionHelpViewInfo` 解析，字段包括：

- 玩家名；
- 记录 ID；
- 建筑 ID；
- 升级等级；
- 已帮助 ID；
- 已加速秒数；
- 用户 ID；
- 城池 WID；
- 城池类型；
- 头像与头像框。

本地抓包中：

- `0x18aa` 有 150 个样本；
- `0x18ad` 有 262 个样本；
- `0x18ae` 没有样本目录。

### 3.3 处理建议

在实现任何新抓包功能前：

1. 停止将 `0x18aa` 写入 `battle_field`。
2. 停止把 `0x18ae` 描述成攻城队列。
3. 清理或标记现有 `battle_field` / `battle_queue` 为不可信派生数据。
4. 如果需要保留这组包，应重命名为“同盟建筑互助”：
   - 待帮助建筑；
   - 互助完成率；
   - 今日帮助次数；
   - 玩家互助贡献；
   - 建筑剩余加速空间。

优先级：**P0，先修正确性，不应在错误语义上继续开发 UI。**

## 4. P1：同盟外交关系 `0x18b6 / 0x18b7`

### 4.1 样本与字段

| 消息 | 十进制 | 样本 | 语义 |
|---|---:|---:|---|
| `000018b6` | 6326 | 52 | 外交关系全量 |
| `000018b7` | 6327 | 13 | 外交关系增量 |

全量包中稳定出现的对象字段：

```text
id
union_id
target_union_id
relationship
next_time
cron_time
user_id
```

客户端使用全量包替换缓存，增量包分别应用更新和删除列表。来源：[`UnionRelationData.cs:L99-L185`](file:///Users/bytedance/stzb/stzb_9.2.2_out_branch_9.1.1776213/assets/decompiled/Game.Data.GamePlay/Tenth.Data/UnionRelationData.cs#L99-L185)。

关系枚举：

```text
0 None
1 Friendly
2 Hostility
3 SignedContract
```

双向关系计算规则是：

- 任一方向敌对 → 敌对；
- 任一方向未设置 → 中立；
- 双方友好 → 友盟。

### 4.2 当前缺口

当前 `WorldIntelligenceService` 只区分：

- 自己；
- 本盟；
- 其他全部视为敌军。

来源：[`intelligence/world_service.py:L672-L684`](file:///Users/bytedance/stzb_watcher/intelligence/world_service.py#L672-L684)。

因此：

- 友盟部队会被标红；
- 中立同盟会被当成敌人；
- 风险分 `enemyOwnership` 和敌军行军数会被高估；
- 全局热区敌我统计不准确。

### 4.3 推荐用途

新增 `union_relations` 当前状态表：

```text
relation_id
union_id
target_union_id
one_way_relation
real_relation
next_time
cron_time
source_seq
updated_at
```

用于：

- 地图颜色：自己 / 友盟 / 中立 / 敌对；
- 风险评分：只对敌对加敌军分；
- 行军列表：友盟援军与敌军威胁分开；
- 热区统计：盟友聚集、敌军聚集和中立区域；
- 外交关系变化时间线。

优先级：**P1，价值高、样本结构稳定、直接修正现有地图正确性。**

## 5. P1：`90005` DB 更新的业务投影

### 5.1 当前只保存原始日志

`parse_db_sync()` 目前只提取：

- 操作类型；
- 表名；
- 第一列 row ID；
- 原始 JSON；
- 文件名。

来源：[`realtime_writer.py:L816-L852`](file:///Users/bytedance/stzb_watcher/realtime_writer.py#L816-L852)。

前端只展示每张表的插入、更新、删除计数，没有业务投影。

本地 722 个 `90005` 文件中解析到：

- 2,327 个表操作；
- 约 16,490 个行级载荷；
- 操作分布：`1=439`、`2=1429`、`3=459`。

### 5.2 已存在但未利用的高价值表

#### `Tb_fight_area`

- 153 次操作；
- 79 个文件；
- 约 1,507 行载荷。

字段：

```text
block_id
belong_block_ids
union_ids
clan_ids
fight_type
time
total_hp
total_kill
fierce_begin_time
fierce_end_time
wuxun
block_wuxun_detail
union_current_get_land
clan_current_get_land
server_id
```

可用于：

- 真正的战区/交战区列表；
- 战区总兵力、总击杀、武勋；
- 激战开始/结束倒计时；
- 战区归属同盟与军团；
- 战场热区强度，而不是依赖错误的 `0x18aa`。

#### `Tb_army_alert`

- 10 次操作；
- 约 85 行载荷。

字段已包含：

```text
armyid
userid_alert
base_heroid
union_id
union_relationship
state
from_wid
to_wid
begin_time
end_time
army_group_id
force
user_id
```

可用于：

- 官方军队预警；
- 到达倒计时；
- 来源/目标 WID；
- 来袭同盟关系；
- 将风险评分从“推测行军”升级为“官方预警 + 世界场景”双证据。

#### `Tb_junxian_weather`

- 271 次更新；
- 3 个文件；
- 约 1,964 行载荷。

字段：

```text
junxian_id
today_weather
today_wind
tommorow_weather
tommorow_wind
```

可用于：

- 当前郡县天气；
- 明日天气；
- 天气影响筛选；
- 地图天气图层。

#### `Tb_junxian_special_weather`

- 1,072 次操作；
- 298 个文件；
- 约 6,997 行载荷。

字段：

```text
wid
area_range
area_range_ex
area_type
special_weather
special_weather_start_time
special_weather_end_time
disaster
disaster_state
disaster_start_time
disaster_end_time
except_wid_list
```

可用于：

- 特殊天气区域；
- 灾害圈范围；
- 开始/结束倒计时；
- 地图风险附加层；
- 例外格排除。

#### `Tb_user_res`

- 90 次更新；
- 约 1,142 行载荷。

包含当前资源、仓储上限、产量、消耗、武勋、战法经验、政策点、同盟贡献等。

适合做：

- 个人资源趋势；
- 可持续征兵/升级时长；
- 武勋和贡献的来源校验；
- 资源告警。

不适合直接暴露给所有浏览器用户，应视为账号私有数据。

#### 其他可投影表

- `Tb_army`：27 次更新，约 394 行；可补足自身部队征兵、驻守、士气和计划状态。
- `Tb_hero`：80 次更新，约 560 行；可做自身武将实时体力、兵力、受伤、战法与装备状态。
- `Tb_world_city`：14 次操作，约 195 行；可补动态城池耐久、保护、驻守与状态。
- `Tb_battle_report_attack`：14 次操作；可作为战报列表增量索引。
- `Tb_user_map_event`：13 次操作；可补个人地图事件状态。

### 5.3 推荐架构

不要继续把所有表塞进 `db_sync` 原始日志。

建议：

```text
DB update decoder
├── raw journal       保留审计与回放
├── typed projectors
│   ├── fight_area_state
│   ├── army_alerts
│   ├── weather_regions
│   ├── special_weather_areas
│   ├── user_resources
│   └── own_armies
└── domain events
```

每个 projector：

- 使用 `tb_field_types.json` 的字段顺序；
- 区分 insert / update / delete；
- 保留源消息号、文件时间和 row ID；
- 只处理白名单表；
- 未识别字段仍保留 raw JSON。

优先级：**P1，数据量大且已经稳定抓到，只缺业务投影。**

## 6. P2：世界事件 `0x0edf / 3807`

本地有 2 个全量样本：

- 205 条事件；
- 207 条事件；
- 每条固定 15 槽。

客户端字段映射：

| 槽位 | 字段 |
|---:|---|
| 0 | `event_id` |
| 1 | `event_type` |
| 2 | `event_subtype` |
| 3 | `wid` |
| 4 | `begin_time` |
| 5 | `end_time` |
| 6 | JSON `event_data` |
| 7 | `heat_index` |
| 8 | `is_heat` |
| 9 | `comment_count` |
| 10 | `zan_count` |
| 11 | JSON `tag` |
| 12 | `img_url` |
| 13 | `img_time` |
| 14 | `share_event` |

来源：[`MiniMapEventData.cs:L205-L300`](file:///Users/bytedance/stzb/stzb_9.2.2_out_branch_9.1.1776213/assets/decompiled/Game.Data.GamePlay/Tenth.UI/MiniMapEventData.cs#L205-L300)。

`event_data` 还能包含：

- 同盟/军团 ID；
- 参与同盟和军团；
- 战区类型；
- 国家战略；
- 土地等级；
- 联动旧事件 ID。

可用于：

- 战场情报地图事件图层；
- 热门事件和事件热度；
- 开始/结束倒计时；
- 按同盟/军团过滤；
- 战术时间线；
- 世界事件与战报、WID、行军联动。

限制：

- 当前只有 2 个全量样本；
- 事件子类型很多，应先保留 raw `event_data`；
- 不应下载或代理外部图片 URL。

优先级：**P2，产品价值高，但需要更完整的子类型字典。**

## 7. P2：赛季历史与世界进度

### 7.1 `0x01fd / 509` 赛季历史

26 个样本，每次稳定返回 9 条记录，每条通常为 5 个数字字段：

```text
season_id
server_open_time
cfg_db_id
season_assess_id
renown_level
```

客户端映射来源：`RoleSeasonCourseData.ReceiveSeasonHistoryData()`。

可用于：

- 赛季下拉框不再手工输入 `current`；
- 自动显示赛季起止边界；
- 积分规则按真实赛季隔离；
- 战报、武勋和阵容统计按赛季归档；
- `cfg_db_id` 与配置版本关联。

这是当前积分中心非常实用的补充，因为积分表有 `season_id`，但 UI 目前只是文本输入。

### 7.2 `0x0367 / 871` 世界进度

本地只有 1 个样本，包含 8 个对象，每个对象 12 个字段。

客户端将对象直接转换为 `WorldProgressInfo`，按 ID 缓存，并触发世界进度状态更新。来源：[`WorldProgressMgr.cs:L398-L455`](file:///Users/bytedance/stzb/stzb_9.2.2_out_branch_9.1.1776213/assets/decompiled/Game.Data.GamePlay/Tenth.Data/WorldProgressMgr.cs#L398-L455)。

可用于：

- 天下大势/世界进度时间线；
- 当前阶段、未开启、进行中、已完成；
- 阶段进度百分比；
- 开启和结束倒计时；
- 阶段奖励提示；
- 赛季运营看板。

限制：只有 1 个样本，应先收集更多状态转换样本。

优先级：

- 赛季历史：**P2，低成本高实用性**；
- 世界进度：**P2/P3，先补样本再实现**。

## 8. P2：攻城预约与外交包中的另一组数据

`6326/6327` 不只被 `UnionRelationData` 消费，也被 `FhwjDeclareData` 消费。

同一个包中还包含：

- 攻城预约；
- 防守预约；
- 目标城 WID；
- 集结地 WID；
- 开始、结束和预约时间；
- 发起同盟与防守同盟；
- 计划 ID；
- 发起者和官员 ID；
- 预约增量与删除。

客户端对全量包使用 replace，对增量包使用 update/remove。来源结构可见 `FhwjDeclareData.OnUnionDeclareFullNotify()` 与 `OnUnionDeclareChangeNotify()`。

可用于：

- 同盟攻城日历；
- 攻守预约冲突；
- 集结点和目标城联动；
- 计划开始前倒计时；
- 自动生成只读打城排表草稿；
- 与打城考勤关联。

风险：

- 这可能是赛季特定功能；
- 需要按 `cfg_db_id` 或赛季能力开关；
- 不应自动发起、修改或取消预约。

优先级：**P2，适合同盟管理，但需先区分赛季。**

## 9. P3：其他可以利用但不是当前核心的包

### `0x10ea / 4330 GET_LAND_NPC_ARMY`

72 个样本，稳定为 2 个数字。

客户端当前处理函数只可靠读取第二项作为守军恢复时间戳。它适合：

- 点击土地后展示守军恢复倒计时；
- 战术镜头显示“守军恢复中”；
- 风险解释增加恢复状态。

它不是完整守军阵容，不能替代地图/赛季守军配置。

### `0x13ce / 5070 DAILY_REPORT_GET_DETAIL`

2 个样本，包含日报详细列表、统计和文本。

适合做：

- 每日战斗摘要；
- 每日贡献卡片；
- 日报与积分中心联动。

当前样本少，且可能与现有战报统计重复。

### `0x1857 / 6231 GET_BRIEF_BATTLE_REPORT_DETAIL`

1 个样本，包含简要战报详情的长文本/对象。

适合：

- 当完整战报详情缺失时补详情；
- 轻量分享战报；
- 模组/特殊赛季战报兼容。

### `0x1f49 / 8009 WORLD_BOSS_RANKING_LIST`

26 个稳定样本。

适合活动期：

- 世界 Boss 排名；
- 个人击杀；
- Top 3 和自身名次；
- 活动倒计时。

它属于活动模块，不应进入默认战场情报首页。

### `0x16b4e / 93006`、`0x16b4f / 93007`、`0x16bac / 93100`

这是模组/赛季扩展数据：

- 全量模组数据；
- 增量变更；
- 模组请求/响应。

载荷深、结构随模组变化，适合建立通用“模组数据 envelope”，不适合现在就做统一 UI。

优先级：**P3，按具体赛季需要再做。**

## 10. 建议继续忽略的数据

| 消息 | 原因 |
|---|---|
| `0x02b6 / 694` | 服务器时间同步，已有本地时间口径，不需业务页 |
| `0x15f96 / 90006` | Ping |
| `0x15f98 / 90008` | SID 检查 |
| `0x00bf / 191` | 反作弊 SDK 回执 |
| `0x059c / 1436` | 社区 token，敏感且与当前产品无关 |
| `0x0907 / 2311` | 渠道认证回执 |
| `0x09d9 / 2521`、`0x09e1 / 2529`、`0x09e4 / 2532` | 直播模块 |
| `0x02c7 / 711` | 聊天历史，隐私和体积风险高 |
| `0x02ca / 714` | 黑名单 |
| 邮件系列 | 与战场指挥核心无关，且含私人内容 |
| `0x0898 / 2200` | 高频滚动通知，噪声高；只适合做去重后的系统事件统计 |

## 11. 推荐实施顺序

### 第 0 阶段：修正确性

1. 为 `6314/6318` 写协议语义测试。
2. 停用旧 `parse_battle_field_18aa()` 与 `parse_battle_queue_18ae()` 的战场命名。
3. 清理错误 UI 文案和不可信派生表。
4. 如果需要保留，改造成“同盟建筑互助”。

### 第 1 阶段：外交关系

1. 解析 `6326` 全量与 `6327` 增量。
2. 存储 one-way 与 real relation。
3. 修改 `WorldIntelligenceService._relation()`。
4. 地图图层增加友盟/中立。
5. 风险评分只把真实敌对算敌军。

### 第 2 阶段：`90005` 白名单投影

第一批只做：

- `Tb_fight_area`
- `Tb_army_alert`
- `Tb_junxian_weather`
- `Tb_junxian_special_weather`

第二批再做私有账号数据：

- `Tb_user_res`
- `Tb_army`
- `Tb_hero`

### 第 3 阶段：世界事件

1. 解析 15 槽固定字段。
2. raw 保存 `event_data` 和 `tag`。
3. 建立地图事件图层和时间线。
4. 按 WID、同盟、军团、时间过滤。

### 第 4 阶段：赛季元数据

1. 导入赛季历史。
2. 积分中心赛季输入改为真实下拉框。
3. 战报、武勋和阵容统计按真实赛季边界过滤。
4. 再评估世界进度看板。

## 12. 最终推荐

下一步不要直接“再加一个页面”。建议先做两个小而关键的闭环：

### A. 外交关系闭环

```text
6326/6327 抓包
→ union_relations
→ WorldIntelligenceService
→ 地图友盟/中立/敌对
→ 风险分修正
```

这是对现有战场情报正确性提升最大的抓包利用。

### B. 战区/天气/预警闭环

```text
90005 raw journal
→ typed projector
→ fight_area / army_alert / weather
→ 战场地图与时间线
```

这是现有抓包里数据量最大、字段最稳定、但业务价值尚未释放的一组数据。

在这两项之前，应先修正 `0x18aa / 0x18ae` 的错误业务解释。

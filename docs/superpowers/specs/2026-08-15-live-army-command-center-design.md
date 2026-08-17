# 实时部队三栏指挥台设计

日期：2026-08-15
状态：已批准
视觉方案：C，三栏联动指挥台

## 1. 目标

在左侧导航新增独立标签“实时部队”，将 5026 基线和 5028 增量形成的统一
WorldState 投影为可快速判断的实时部队指挥台。

页面必须让用户直接回答：

1. 当前战场上有哪些部队；
2. 每支部队处于什么状态；
3. 部队当前位于哪里、下一格和最终目标是什么；
4. 哪支部队即将到达；
5. 该部队对应的真实三人武将组合是什么；
6. 阵容结论来自哪一条精确战报；
7. 哪些部队在最近 10 分钟内离开了 WorldState。

所有能力只读，不增加游戏动作、发包或数据库写接口。

## 2. 已验证事实

### 2.1 5026 与 5028 是同一个 WorldState

- 5026 提供观测范围内的完整基线，可多帧拼装；
- 5028 提供相对最近完整基线的增量、删除和 Block membership 变化；
- 两者共同维护 `world_armies`、`world_real_marches`、用户、同盟和地块状态；
- 当前数据已经通过 `deleted_at_seq` 区分当前部队和已离线部队。

### 2.2 部队 ID 可精确命中战报阵容

`world_armies.army_id` 与 `battles_v2.atk_team_id / def_team_id` 使用同一队伍 ID
口径。

当前数据库已验证：

```text
army_id 18411352
→ 最新精确战报 #5289170
→ 攻方阵容 100705 / 100707 / 100101
→ 杜预 / 卫瓘 / 灵帝
```

因此阵容解析不使用玩家名、同盟或近期常用队伍进行猜测。

### 2.3 最近离线时间可追溯

`world_armies.deleted_at_seq` 可关联：

```text
world_scene_packets.seq
world_scene_packets.cmd_id
world_scene_packets.observed_at_ms
world_scene_packets.server_order_id
```

由此可得到删除时间和来源：

- `cmd_id = 5028`：5028 增量删除或 Block 解绑后的删除；
- `cmd_id = 5026`：5026 基线范围清理；
- 其他值：保留原始命令 ID，不做业务推断。

## 3. 范围

### 3.1 本期包含

- 左侧导航新增“实时部队”；
- 当前全部未删除部队；
- 最近离线 10 分钟部队；
- 部队、realMarch、用户、同盟、目标地块聚合；
- 部队 ID 精确阵容匹配；
- 武将名字、画像、等级、进阶和战法；
- 左列表、中地图、右详情三栏双向联动；
- 状态、玩家、同盟、武将名、目标 WID、部队 ID 搜索和筛选；
- SSE 驱动的可见页刷新；
- 每秒倒计时文本更新；
- 桌面、平板、移动端与 reduced-motion。

### 3.2 本期不包含

- 按玩家近期队伍猜测阵容；
- 自动出征、召回、驻守或增援；
- 自定义状态映射；
- 行军路径规划；
- 修改 5026 / 5028 协议语义；
- 修改隐藏兼容页。

## 4. 导航与视觉域

可见导航顺序在“战场情报”之后插入：

```text
...
设置中心
战场情报
实时部队
阵容战法研究
```

页面使用新 tab ID `35`，归入：

```html
data-visual-domain="intelligence"
```

导航仍保持平铺，不增加“更多”菜单。

## 5. 后端架构

### 5.1 深模块

新增：

```text
intelligence/live_army_service.py
intelligence/live_army_api.py
```

`LiveArmyService` 封装：

- WorldState 部队读取；
- realMarch 合并；
- 最近离线筛选；
- 精确阵容证据；
- 武将和战法元数据；
- 状态与删除来源映射；
- 汇总统计和地图 bounds。

Flask 路由只负责参数校验和 JSON 响应。

### 5.2 接口

```text
GET /api/intelligence/live-armies?offlineMinutes=10
```

约束：

- `offlineMinutes` 默认 10；
- 允许范围 `0..60`；
- 非法值返回 400；
- 接口只读；
- 可选表或关联数据缺失时返回降级结果，不返回 500。

### 5.3 响应

```json
{
  "ok": true,
  "generatedAtMs": 1786776035000,
  "worldStateVersion": 391,
  "freshness": "fresh",
  "summary": {
    "current": 18,
    "moving": 6,
    "stationary": 12,
    "exactLineups": 2,
    "unknownLineups": 16,
    "recentOffline": 5
  },
  "bounds": {
    "rowUp": 62,
    "rowDown": 224,
    "colLeft": 1313,
    "colRight": 1488
  },
  "current": [],
  "recentOffline": []
}
```

每支部队：

```json
{
  "armyId": 18411352,
  "userId": 11580,
  "ownerName": "无情的战",
  "ownerUnionId": 0,
  "ownerUnionName": "",
  "state": 4,
  "stateKey": "returning",
  "stateLabel": "返回中",
  "isMoving": true,
  "source": {
    "seq": 324,
    "observedAtMs": 1786718702000,
    "cmdId": 5026
  },
  "location": {
    "currentWid": 2081480,
    "nextWid": 0,
    "targetWid": 2081480,
    "fromWid": 2081480,
    "resideWid": 2081480,
    "stayWid": 0,
    "source": "army-fallback"
  },
  "timing": {
    "beginTime": 1786718702,
    "nextTime": 0,
    "endTime": 1786720969
  },
  "march": null,
  "morale": 0,
  "target": {
    "name": "",
    "force": 0,
    "unionId": 0
  },
  "lineup": {
    "status": "exact",
    "complete": true,
    "battleId": 5289170,
    "battleTime": 1786724967,
    "battleTimeText": "2026-08-14 21:49:27",
    "side": "atk",
    "heroes": []
  },
  "offline": null
}
```

## 6. 状态语义

只使用上游 `ArmyProtocolState` 已验证常量：

| state | key | 文案 | 分类 |
|---:|---|---|---|
| 0 | normal | 待命 | stationary |
| 1 | expedition | 出征中 | moving |
| 2 | reside-going | 驻守前往 | moving |
| 3 | reinforce-going | 增援前往 | moving |
| 4 | returning | 返回中 | moving |
| 5 | reside | 驻守 | stationary |
| 6 | reinforce | 增援 | stationary |
| 25 | stay | 停留 | stationary |
| 其他 | unknown | 状态 N | unknown |

未知状态必须保留原始数字，不映射成猜测文案。

## 7. 部队与 realMarch 合并

### 7.1 关联

```text
world_armies.real_march_id = world_real_marches.real_march_id
```

### 7.2 当前位置优先级

1. 有 realMarch：`current_wid`；
2. 无 realMarch 且 `stay_wid > 0`：`stay_wid`；
3. 否则 `reside_wid > 0`：`reside_wid`；
4. 否则 `wid_from`；
5. 全部无效时为 0，并显示“位置未知”。

### 7.3 下一格和目标

- 下一格只来自 `realMarch.next_wid`；
- 最终目标优先 `world_armies.wid_to`；
- 目标名称来自 `world_tiles`；
- 缺失地块映射时保留 WID。

### 7.4 无 realMarch 降级

状态仍按 `world_armies.state` 展示。

对于移动状态但没有 realMarch：

- `isMoving` 仍为 true；
- 路线标为“不完整”；
- 地图只显示当前 fallback 位置和最终目标虚线；
- 不伪造下一格。

## 8. 严格阵容证据

### 8.1 匹配规则

对每个 `army_id` 查询：

```sql
WHERE atk_team_id = :army_id OR def_team_id = :army_id
ORDER BY time DESC, battle_id DESC
```

从最新到最旧找到第一条“当前侧至少有一个有效武将 ID”的战报即停止。

不使用：

- 玩家名匹配；
- 同盟匹配；
- `team_users.team_id` 回退；
- 玩家近期队伍；
- 相似阵容；
- 旧战报补齐最新不完整战报。

### 8.2 证据状态

状态枚举只允许 `exact` 和 `unknown`；阵容是否完整由独立布尔字段
`complete` 表示，不新增第三种证据状态。

#### exact

- 同一 `army_id` 命中战报；
- 返回战报 ID、时间和攻守侧；
- 有 1–3 名有效武将；
- 少于 3 名时 `complete=false`，显示“阵容不完整”。

#### unknown

- 没有同 ID 有效战报；
- `heroes=[]`；
- 页面显示“无同 ID 战报，阵容未知”。

### 8.3 武将与战法映射

武将元数据使用 `sim_data.hero_index()`：

- `id`
- `name`
- `portraitUrl`
- `portraitFallbackUrl`
- `portraitLocal`
- `camp`
- `army`
- `quality`

未找到武将配置：

```text
name = "武将 <id>"
portraitUrl = /static/hero-portraits/placeholder.svg
```

等级和进阶：

- 等级来自战报侧 `heroN_level`；
- 进阶优先使用 `heroN_star`；
- 不从其他战报补值。

战法：

- 使用命中战报的 `all_skill_info`；
- 按攻守侧和位置提取；
- 战法名称使用 `sim_data.skill_index()`；
- 未知战法显示“战法 <id>”。

## 9. 最近离线

查询：

```text
deleted_at_seq IS NOT NULL
deletion observed_at_ms >= now - offlineMinutes
```

最近离线项保留最后一份：

- 部队字段；
- 玩家与同盟；
- 最后位置和目标；
- 严格阵容证据；
- 删除时间；
- 删除来源；
- 距离当前时间。

删除来源：

```text
5028 → 5028 增量
5026 → 5026 基线清理
其他 → cmd <id>
```

离线项不绘制实线行军路线，只使用灰色虚线和降低透明度。

## 10. 前端架构

新增：

```text
static/live-army-command.css
static/live-army-map.mjs
static/live-army-command.mjs
```

### 10.1 `live-army-map.mjs`

纯地图模块：

- WID 与 row/col 转换；
- 自动 bounds；
- world 到 Canvas 坐标；
- 部队标记 draw plan；
- 路线 draw plan；
- hit test；
- pan / zoom；
- reduced-motion 绘制配置。

不访问 API，不持有业务状态。

### 10.2 `live-army-command.mjs`

页面控制器：

- 加载聚合接口；
- 搜索、状态过滤和排序；
- 默认选中；
- 左中右联动；
- Canvas 绘制；
- SSE 脏标记；
- visibility-aware 刷新；
- 倒计时；
- 跳转战场情报；
- loading / empty / error。

公开：

```javascript
window.LiveArmyCommand = {
  load,
  selectArmy,
  locateArmy,
  openInIntelligence,
  setFilter,
  state,
};
```

## 11. C 三栏页面

### 11.1 左栏：部队索引

筛选：

- 部队 ID；
- 玩家名；
- 同盟名；
- 武将名；
- 当前 / 下一 / 目标 WID；
- 状态。

分区：

```text
当前部队
最近离线 10 分钟
```

默认排序：

1. 移动中且结束时间有效，按最早到达；
2. 其他移动中；
3. 当前静止部队，按状态和部队 ID；
4. 最近离线，按删除时间倒序。

卡片显示：

- 部队 ID；
- 状态；
- 玩家 / 同盟；
- 当前 WID → 目标 WID；
- 剩余时间；
- 三名武将缩略图或“阵容未知”；
- exact / incomplete / unknown 证据标识。

### 11.2 中栏：战术地图

- Canvas 渲染；
- 显示当前全部部队；
- 当前视窗外部队仍保留在左栏；
- 选中部队时自动居中；
- 移动中绘制当前 → 下一格 → 最终目标；
- 无 realMarch 时绘制当前 → 最终目标虚线；
- 离线项不默认显示，选择离线卡片时临时显示灰色标记；
- 双击部队标记跳到“战场情报”并定位当前 WID。

标记不能只依赖颜色：

- 移动：箭头；
- 驻守 / 增援：盾形；
- 停留 / 待命：圆形；
- 返回：回转箭头；
- 未知：菱形；
- 离线：虚线轮廓。

### 11.3 右栏：身份与阵容证据

身份：

- 部队 ID；
- 玩家 / 用户 ID；
- 同盟；
- 状态；
- 士气；
- WorldState seq、cmd 和观测时间。

空间与时间：

- 出发、当前、下一格、最终目标；
- 开始、下一跳、结束；
- 剩余时间；
- 路线完整 / 不完整。

阵容：

- 武将画像、名字、ID；
- 等级、进阶、兵种、阵营；
- 战法名字和等级；
- 阵容完整性。

证据：

- exact / unknown；
- 证据战报 ID；
- 战报时间；
- 攻守侧；
- “严格匹配，不做玩家队伍推测”说明。

## 12. 双向联动

### 左 → 中 → 右

点击左侧卡：

1. 更新 `selectedArmyId`；
2. 地图自动居中；
3. 高亮标记和路线；
4. 右栏更新详情；
5. 左栏保持滚动位置。

### 中 → 左 → 右

点击地图标记：

1. hit test 得到 army ID；
2. 更新选中；
3. 左栏滚动到对应卡；
4. 右栏更新。

### 跳转战场情报

双击标记或点击右栏“在战场情报中定位”：

1. `switchTab(33, intelligenceButton)`；
2. `IntelligenceCenter.openView("map")`；
3. `IntelligenceCenter.locateWid(currentWid)`；
4. 写入现有地图历史和 URL hash。

## 13. 默认选中

首次加载：

1. 最早到达且结束时间仍在未来的移动部队；
2. 否则第一支当前移动部队；
3. 否则第一支当前部队；
4. 当前为空且有最近离线时选第一支离线部队；
5. 全部为空时保持未选中。

后续刷新优先保留现有 `selectedArmyId`；若已离线且仍在 10 分钟窗口内，继续选中
离线项。

## 14. 实时刷新

监听统一 SSE：

```text
world_snapshot_complete
world_scene_delta
```

规则：

- 收到事件只标记 dirty；
- tab35 可见且文档可见时，350ms 防抖刷新；
- 标签隐藏时不请求；
- 切回 tab35 时如 dirty 立即刷新；
- 不创建第二条 EventSource；
- API 失败保留上一份成功数据并显示降级状态。

倒计时：

- 页面可见时每秒更新文本；
- 不重新请求 API；
- 页面隐藏或离开 tab35 时停止 ticker。

## 15. 响应式

### 桌面 `>= 1280px`

```text
左 31% / 中 42% / 右 27%
```

### 平板 `768..1279px`

```text
上：左栏 + 地图
下：右侧详情整行
```

### 移动 `< 768px`

单列：

1. 搜索和状态筛选；
2. 地图；
3. 当前选中详情；
4. 部队索引折叠区。

所有模式无文档横向溢出。

## 16. 动效与可访问性

- 常驻倒计时只改文本；
- 新 WorldState 事件可触发一次性 HUD 脉冲；
- 选中路线只做静态高亮；
- `prefers-reduced-motion` 或 reduced 设置下：
  - 无路线流光；
  - 无卡片位移；
  - 无选中脉冲；
  - Canvas 不做插值动画。
- Canvas 提供文本摘要；
- 列表使用按钮语义；
- 键盘上下键切换部队；
- Enter 选中；
- 状态同时使用图形、文字和颜色。

## 17. 错误与空状态

- WorldState 未初始化：提示“等待 5026 基线”；
- 当前无部队：显示空状态，仍显示最近离线；
- 无 realMarch：显示路线不完整；
- 无用户 / 同盟：显示原始 ID；
- 无目标地块：显示目标 WID；
- 阵容 unknown：显示“无同 ID 战报，阵容未知”；
- 武将配置缺失：显示“武将 ID”和占位画像；
- API 失败：保留旧数据，提供重试；
- SSE 断线：沿用现有全局连接状态，不新增重连逻辑。

## 18. 测试

### Python

- 当前部队和最近离线窗口；
- `offlineMinutes` 边界；
- 5026 / 5028 删除来源；
- 8 个权威状态和未知状态；
- realMarch 合并；
- 无 realMarch 降级；
- 当前位置优先级；
- 精确攻方阵容；
- 精确守方阵容；
- 最新有效战报；
- 阵容不完整；
- 阵容 unknown；
- 武将名字和画像；
- 战法名字；
- 缺失可选表不返回 500。

### Node

- WID 坐标和 bounds；
- 标记 draw plan；
- 路线完整 / 不完整 / 离线；
- hit test；
- 搜索；
- 状态过滤；
- 排序；
- 默认选中；
- 倒计时；
- SSE dirty 状态。

### 静态 / E2E

- 新导航顺序；
- tab35 intelligence 领域；
- 三栏 HUD；
- 当前和最近离线分区；
- exact / unknown；
- 武将名字和画像；
- 左右双向联动；
- 双击跳转战场情报；
- 1440 / 1024 / 768 / 390；
- reduced-motion；
- 无横向溢出；
- mtime 版本参数。

## 19. 完成标准

- 左侧出现“实时部队”；
- 页面展示所有当前状态部队；
- 最近离线 10 分钟独立展示；
- 部队 ID 精确命中真实阵容；
- 武将最终显示名字和画像；
- 未命中明确显示“阵容未知”；
- 左列表、地图、右详情双向联动；
- 能跳到战场情报实际 WID；
- 不新增写接口和游戏动作；
- 全量 Python、Node 和 Chrome E2E 通过。

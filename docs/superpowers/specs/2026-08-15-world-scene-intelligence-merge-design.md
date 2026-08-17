# 世界场景与战场情报合并设计

日期：2026-08-15

## 1. 目标

将 `tab30`“世界场景”和 `tab33`“战场情报”合并为一个产品入口：

- 左栏只保留“战场情报”；
- 全局雷达、热区和真实格子继续作为主地图；
- realMarch、活跃部队和协议实体成为战场情报内部子视图；
- 地图、行军、部队和协议实体共享当前视窗与选中 WID；
- `tab30` 保留为隐藏兼容页，旧入口自动跳转到 `tab33`。

所有能力继续保持只读。

## 2. 信息架构

`tab33` 顶部增加一级工作区切换：

```text
态势地图 | 实时行军 | 活跃部队 | 协议实体
```

### 2.1 态势地图

保留现有：

- 全域热区；
- 战区轮廓；
- 战术镜头；
- 全局雷达；
- 风险、行军、战报、证据详情；
- WorldState 时间线；
- WID 定位、Home、Back、Forward。

### 2.2 实时行军

迁移现有 `realMarch` 数据表，展示：

- 行军 ID；
- 上一格、当前格、下一格；
- 出发、下一跳、抵达时间；
- 路径 ID；
- 单格耗时；
- 行军类型；
- 归属 ID。

点击任意 WID：

1. 切回“态势地图”；
2. 定位该 WID；
3. 写入视窗历史。

### 2.3 活跃部队

迁移现有 MapArmyTuple 数据表，展示：

- 部队 ID；
- 状态；
- 玩家与同盟；
- 出发、目标、驻扎、停留 WID；
- 目标名称与类型；
- 兵种、士气、Buff；
- 障碍 WID；
- realMarch ID；
- 开始与结束时间；
- 战报摘要。

点击部队行：

- 优先定位 `wid_to`；
- 无目标时使用 `stay_wid`；
- 再回退到 `reside_wid`；
- 切回地图并选中对应格子。

### 2.4 协议实体

保留调试与证据用途：

- warShips；
- assistArmies；
- armyGroups；
- shortMessages；
- blockShips；
- blockAssistArmies。

默认按类别分组，可按类别过滤。原始内容必须转义后显示，不提供写操作。

## 3. 统一页面布局

```text
┌──────────────────────────────────────────────────────────────┐
│ 态势地图 | 实时行军 | 活跃部队 | 协议实体                   │
│ 图层 / WID / 模式 / WorldState / 刷新                       │
├──────────────────────────────────────────────────────────────┤
│ 当前子视图                                                   │
│ - 地图：地图 + 右侧详情 + 雷达                               │
│ - 行军：筛选条 + 行军表                                     │
│ - 部队：筛选条 + 部队表                                     │
│ - 实体：类别摘要 + 实体表                                   │
├──────────────────────────────────────────────────────────────┤
│ WorldState 时间线（所有子视图共享）                          │
└──────────────────────────────────────────────────────────────┘
```

地图专属图层按钮仅在“态势地图”显示。

## 4. 数据接口

第一阶段复用现有只读接口：

```text
GET /api/intelligence/world/summary
GET /api/intelligence/world/viewport
GET /api/intelligence/world/overview
GET /api/intelligence/world/tile/<wid>
GET /api/intelligence/world/events
GET /api/intelligence/world/risks
GET /api/world/armies
GET /api/world/marches
GET /api/world/entities
```

不复制后端数据表，也不新增第二套状态模型。

后续可将 armies、marches、entities 迁移到 `/api/intelligence/world/*`，但本轮保持接口兼容，避免扩大改动面。

## 5. 前端模块边界

### `static/intelligence-center.js`

负责：

- 一级子视图状态；
- 地图与详情协调；
- 子视图切换；
- 地图定位动作；
- WorldState 时间线。

新增公开方法：

```javascript
IntelligenceCenter.openView("map" | "march" | "army" | "entity")
IntelligenceCenter.openArmy(armyId)
IntelligenceCenter.openMarch(realMarchId)
IntelligenceCenter.locateWid(wid)
```

### `static/world_scene.js`

不再拥有独立页面导航，只作为只读场景数据渲染模块：

```javascript
WorldScenePanel.load()
WorldScenePanel.renderMarches(rows)
WorldScenePanel.renderArmies(rows)
WorldScenePanel.renderEntities(rows)
WorldScenePanel.locateFromArmy(army)
```

移除职责：

- 独立地图网格主视图；
- 独立 tab30 页面状态；
- 独立 row/col 查询控件；
- `wsSwitch()` 页面切换。

保留 `loadWorldScene()` 兼容别名，内部调用：

```javascript
IntelligenceCenter.openView("map")
```

## 6. WID 联动

所有 WID 链接使用统一行为：

```javascript
IntelligenceCenter.locateWid(wid)
```

来源包括：

- 行军上一格、当前格、下一格；
- 部队出发、目标、驻扎、停留、障碍；
- WorldState 时间线；
- Query Agent；
- 旧世界场景兼容入口。

不存在的 WID 不改变当前视窗，只显示错误提示。

## 7. 实时更新

继续复用共享 `stzb:stream-event`：

- `world_snapshot_complete`；
- `world_scene_delta`；
- `world_state_delta`。

行为：

- 当前为地图：刷新地图当前模式；
- 当前为行军/部队/实体：350ms 防抖刷新当前子视图；
- 非当前子视图只标记 dirty，不立即请求；
- 切换到 dirty 子视图时再加载；
- 不创建第二个 EventSource。

## 8. tab30 兼容

`tab30` 页面保留，但不出现在：

- 左侧导航；
- 命令面板；
- 默认首页设置；
- 快捷入口。

调用 `switchTab(30, ...)` 时：

1. 不显示旧页面；
2. 切换到 `tab33`；
3. 根据来源打开对应子视图；
4. 旧 `loadWorldScene()` 仍可调用。

兼容映射：

| 旧操作 | 新行为 |
|---|---|
| 打开 tab30 | 打开 tab33 态势地图 |
| `wsSwitch("march")` | `openView("march")` |
| `wsSwitch("army")` | `openView("army")` |
| `wsSwitch("entity")` | `openView("entity")` |
| 世界场景收藏 WID | 战场情报定位 WID |

## 9. 导航清理

删除以下入口：

- 左栏“世界场景”；
- 命令面板“世界场景”；
- 战场总览“打开世界场景”；
- 设置中心默认首页“世界场景”。

相关文案统一为：

- “战场情报”；
- “态势地图”；
- “实时行军”；
- “活跃部队”；
- “协议实体”。

## 10. 可访问性与响应式

- 一级子视图使用 `role="tablist"`；
- 按钮使用 `role="tab"` 和 `aria-selected`；
- 子面板使用 `role="tabpanel"`；
- 键盘左右键切换子视图；
- 375px 下子视图按钮横向滚动；
- 表格保持横向滚动，不撑宽文档；
- 地图雷达与右侧详情保持现有响应式行为。

## 11. 错误与空状态

- 行军为空：显示“暂无实时行军”；
- 部队为空：显示“暂无活跃部队”；
- 实体为空：显示“暂无协议实体”；
- 单个接口失败：只影响对应子视图；
- 地图仍可使用时，不因实体接口失败清空地图；
- 后端旧版本无接口时显示明确重启提示；
- 原始 JSON 必须经过 `esc()`。

## 12. 测试

### 静态测试

- 左栏不再包含 tab30；
- tab33 包含四个一级子视图；
- tab30 仍保留兼容 DOM；
- 命令面板和设置不包含世界场景；
- world_scene.js 不创建 EventSource；
- WID 链接统一调用 `IntelligenceCenter.locateWid()`。

### Node

- 场景面板行军/部队 WID 选择规则；
- dirty 子视图刷新策略；
- 旧 view 名到新 view 名映射；
- 原始实体内容转义。

### Chrome E2E

- 默认进入战场情报；
- 四个子视图均可切换；
- 点击行军 WID 返回地图并定位；
- 点击部队行定位目标 WID；
- 协议实体能展示；
- `switchTab(30)` 自动进入 tab33；
- 左栏和命令面板不存在世界场景；
- 375、768、1024、1440 无页面溢出。

## 13. 只读边界

- 不主动发包；
- 不自动出征；
- 不修改游戏状态；
- 不为合并页面新增写接口；
- 所有场景数据只来自现有 WorldState 与只读 API。

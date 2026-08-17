# 统一 WorldState 与战场情报中心完成审计

日期：2026-08-14

## 1. 交付结果

| 规格能力 | 主要 Artifact | 状态 |
|---|---|---|
| 5026 基线 + 5028 增量统一状态 | `world_scene/parser.py`、`world_scene/state_store.py` | 完成 |
| 5026 多帧合并与 observed area 清理 | `WorldSceneAssembler`、`WorldStateStore.apply_baseline()` | 完成 |
| Army / Ship / Assist Block membership | `world_army_blocks`、`world_ship_blocks`、`world_assist_army_blocks` | 完成 |
| realMarch 基线替换与增量覆盖 | `WorldStateStore` | 完成 |
| 所有 tile chunk 保存与 subtype clear | `world_tile_chunks` | 完成 |
| 情报配置快照 | `data/intelligence/client-9.2.2/`、`scripts/sync_intelligence_snapshot.py` | 完成 |
| 世界情报 API | `intelligence/world_service.py`、`intelligence/world_api.py` | 完成 |
| 准确敌我关系风险 | `WorldIntelligenceService` | 完成；身份不明时 fail-closed 为 unknown |
| Canvas 风险地图 | `static/intelligence-map.mjs`、`static/intelligence-center.js` | 完成 |
| 地图缩放、平移、关注 | wheel、pointer drag、Shift/按钮关注 | 完成 |
| 全域热区聚合 | `/api/intelligence/world/overview` | 完成 |
| 三级语义缩放 | 全域热区、战区轮廓、战术镜头 | 完成 |
| 全局雷达 | `static/intelligence-map-overview.mjs` | 完成 |
| 视窗防丢失 | Home、Back、Forward、雷达拖动、URL 状态 | 完成 |
| 世界场景合并 | 行军、部队、协议实体迁入 `tab33` | 完成 |
| tab30 兼容 | 旧调用自动重定向到战场情报 | 完成 |
| 地块战报统计 | `tile_detail().battleStats` | 完成 |
| 客户端武将/战法索引 | `intelligence/config_repository.py`、`config_api.py` | 完成 |
| 历史阵容统计 | `intelligence/lineup_service.py`、`lineup_api.py` | 完成 |
| 三层证据 UI | `CONFIG_FACT`、`BATTLE_STAT`、`SIMULATION` | 完成 |
| 研究中心到模拟器 | `window.StzbSimulator.loadLineup()` | 完成 |
| 模拟结果回链 | `stzb:simulation-completed` | 完成 |
| Query Agent 情报工具 | risk、hero、skill、lineup、world summary | 完成 |
| 可配置赛季积分 | 规则版本、三榜、构成解释、奖惩、预览确认 | 完成 |

## 2. 正确性约束

- 阵容 key 保留大营、中军、前锋位置顺序，不按名称排序。
- 历史胜率明确标为 `BATTLE_STAT`，低于 10 场显示低置信度。
- Kotlin 引擎结果明确标为 `SIMULATION`，不描述为真实胜率。
- 客户端配置明确标为 `CONFIG_FACT` 并返回 `datasetVersion`。
- 未识别当前角色/同盟时，归属与敌军数量标记 unknown，不把非零 owner 默认判敌。
- 5028 Block 删除先解除当前 Block；仅在最后一个 membership 消失后标记实体删除。
- 所有新增能力只读，不主动发包、不自动出征、不修改游戏状态。

## 3. 验证证据

```bash
.venv/bin/python -m unittest discover -s test -v
```

最新结果：144 项测试通过，包含系统 Chrome 的真实 Dashboard E2E。

```bash
node --test test/js/intelligence-map.test.mjs
```

结果：3 项地图纯函数与图层顺序测试通过。

```bash
.venv/bin/python scripts/sync_intelligence_snapshot.py \
  --source-root /Users/bytedance/stzb \
  --output-root data/intelligence/client-9.2.2 \
  --check
```

结果：退出码 0，快照无漂移。

主应用运行态 HTTP 冒烟：

- `/api/intelligence/config/manifest`：200，`client-9.2.2`；
- `/api/intelligence/lineups`：200，`BATTLE_STAT`，当前库 62 个阵容；
- `/api/intelligence/world/summary`：200；当前运行库无基线时正确返回 `v0 / unknown`。

真实数据库地图验证：

- WorldState：`v18`；
- 已知地块范围：row `62–224`、col `1313–1488`；
- 524 个地块聚合为 31 个非空热区；
- 所有热区 `focusWid` 均存在于 `world_tiles`；
- 任一热区进入 20×20 战术镜头后均可返回真实地块；
- Chrome E2E 已验证 Canvas 双击热区进入战术镜头，以及 Home、Back、Forward。
- Chrome E2E 已验证实时行军、活跃部队、协议实体子视图和 WID 回跳。
- 左栏、命令面板与默认首页设置不再出现“世界场景”。

应用内浏览器连接因本地工具报 `sandboxCwd must be an absolute file URI` 未建立；
未以此作为通过证据，改用系统 Chrome E2E 与 HTTP 冒烟。

## 4. 边界与后续增强

- 当前事件表保证 snapshot/delta 顺序；更细的 tile owner changed、army field diff、
  assembly timeout 诊断可继续扩展为逐字段事件。
- 当前阵容画像以配置事实、历史样本、常见对手和置信度为主；战法类型分布、
  控制/恢复/伤害标签可在规则语义进一步验证后增加，不能凭描述文本猜测。
- 当前地图保存关注对象和当前状态，不持久化完整历史地图副本。
- 未执行 `git commit`；仓库原本存在大量用户未提交和未跟踪改动。

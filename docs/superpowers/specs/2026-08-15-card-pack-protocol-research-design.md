# 卡包与协议研究中心设计

日期：2026-08-15

## 范围修订

2026-08-15 用户明确要求“别放协议上去”。最终产品范围调整为：

- 前端研究页只展示武将、战法和卡包；
- 查询助手只开放卡包查询与武将反查卡包；
- 协议命令与字段字典不进入产品界面，也不提供前端导航；
- 已生成的协议快照和内部只读 API 暂时保留为开发资料。

## 目标

一次性迁移并实现三类只读情报：

1. 客户端 9.2.2 全赛季卡包层级与武将池；
2. 客户端 9.2.2、9.2.4 协议命令目录及语义差异；
3. 与当前系统相关的客户端表字段类型白名单。

这些能力嵌入现有“阵容战法研究”、查询助手和情报研究交互，不新增左栏入口。

## 排除范围

- 不迁移任何静态地图、城池或守军数据。
- 不读取账号、玩家、同盟、聊天或抓包正文。
- 不在运行时读取 `/Users/bytedance/stzb`。
- 不实现发包、自动化动作、任意 SQL 或任意脚本执行。
- 不展示未经验证的抽卡概率、保底或活动权重。
- 不把反编译源码行号变化解释成协议语义变化。

## 数据架构

新增独立研究快照：

```text
data/intelligence/client-9.2.2/research/
├── card_packs.json
├── protocol_commands.json
├── table_fields.json
├── manifest.json
└── checksums.sha256
```

同步阶段从 `/Users/bytedance/stzb/server/src/main/resources` 读取原始数据，解析并写入项目内 JSON。运行时只加载上述快照。

### 卡包快照

每个卡包保存：

- `packId`
- `parentPackId`
- `containerPackId`
- `priority`
- `heroIds`
- `heroCount`
- `sourceConfigs`

父卡包没有直接武将池时，递归合并子卡包武将池。循环关系返回空池并在验证阶段失败。

### 协议快照

每个版本保存标准化命令：

- `id`
- `names`
- `requestSources`
- `receiveSources`
- `captureSendCount`
- `captureReceiveCount`

语义差异只包含：

- `added`
- `removed`
- `renamed`

同一命令仅源码路径或行号变化时，不进入语义差异。

### 字段字典

只迁移以下 12 张表：

- `Tb_army`
- `Tb_army_alert`
- `Tb_battle_report_attack`
- `Tb_battle_report_defend`
- `Tb_fight_area`
- `Tb_force_info`
- `Tb_hero`
- `Tb_union_army_group`
- `Tb_union_assembly`
- `Tb_user_union_attr`
- `Tb_war_ship`
- `Tb_world_city`

每张表只保存 `name`、`type` 和字段序号。

## 后端边界

新增 `ResearchCatalogRepository`，职责仅为：

- 加载一次研究快照；
- 分页搜索卡包；
- 反查武将所属卡包；
- 搜索命令并读取双版本详情；
- 返回协议差异摘要；
- 搜索白名单表和字段。

新增只读 API：

```text
GET /api/intelligence/research/summary
GET /api/intelligence/card-packs
GET /api/intelligence/card-packs/<pack_id>
GET /api/intelligence/protocol/commands
GET /api/intelligence/protocol/commands/<command_id>
GET /api/intelligence/protocol/schema
GET /api/intelligence/protocol/schema/<table_name>
```

所有分页 `size` 限制在 1–100。不存在的实体返回 404，非法分页和非法版本返回 400。

## 前端交互

“阵容战法研究”扩展为四个研究域：

- 武将
- 战法
- 卡包
- 协议

### 卡包

列表展示卡包 ID、武将数和父级关系。详情展示：

- 卡包层级；
- 武将总数；
- 阵营分布；
- 兵种分布；
- 武将卡片；
- 点击武将跳转现有武将详情。

武将详情增加“所属卡包”，支持反向跳转。

### 协议

列表展示命令 ID、命令名和版本状态。详情展示：

- 9.2.2 与 9.2.4 命令名；
- 发送/接收捕获计数；
- 请求端和接收端源码位置；
- 新增、删除或改名状态；
- Schema 白名单表浏览器。

不显示“执行”“发送”或“重放”按钮。

## 查询助手

新增只读问题：

- `查询卡包 802`
- `张辽在哪些卡包`
- `查询命令 5028`
- `查询字段 Tb_world_city`

响应继续返回类型化证据和 UI 导航：

- `packId`
- `commandId`
- `table`

现有执行词拒绝逻辑保持不变。

## 视觉

保持 Modern Dark Data Console：

- 卡包使用青色数据卡和阵营分布条；
- 协议新增/删除/改名分别使用绿、红、黄状态光；
- Schema 使用紧凑字段矩阵和等宽字体；
- 不使用 Emoji 作为正式标签。

## 测试

- 二进制卡包解析单元测试；
- 父子卡包合并和循环防护；
- 协议语义差异测试；
- Schema 白名单测试；
- 快照 manifest/checksum 漂移测试；
- Repository 搜索和详情测试；
- Flask API 测试；
- 查询助手卡包、命令、字段测试；
- 静态契约与真实 Chrome E2E。

## 验收

- 实际快照恰好生成 271 个唯一卡包。
- 802、808、837、840、1802、1808 均有非空武将池。
- 协议差异为新增 63、删除 2、命名变化 1。
- Schema 只包含指定 12 张表。
- 运行时断开 `/Users/bytedance/stzb` 后仍能查询。
- UI 不出现概率、保底、发包、执行 SQL 或地图静态数据入口。

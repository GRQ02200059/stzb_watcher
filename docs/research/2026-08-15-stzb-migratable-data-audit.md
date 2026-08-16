# `/Users/bytedance/stzb` 可迁移数据审计

日期：2026-08-15

目标：只审计能够提升 `/Users/bytedance/stzb_watcher` Web 系统的数据、规则与已验证解析契约；不复制数据、不实现功能。

## 1. 结论

最值得迁移的不是更多武将或战法表，而是当前 Web 明显缺少的四类“静态真值”：

1. **资源地守军编成快照**：价值最高、体积小、解析链完整，可以直接增强战场情报的地块详情、危险度解释和阵容研究。
2. **静态资源地图与城池目录**：可以让世界地图在没有实时视野时仍有稳定底图，解决“什么格子都不显示”和缩放后失去方向的问题。
3. **全赛季卡包武将池**：适合扩展武将详情和阵容战法研究，但当前只能可靠表达“卡包层级与武将池”，不能宣传为精确抽卡概率。
4. **协议命令目录与表字段字典**：适合情报研究、版本诊断和查询助手，不应进入普通用户主导航。

`hero_extra.json`、`skill_extra.json`、`army_extra.json` 和装备基础表已经存在于当前项目，且与源文件哈希一致，**不应再次迁移**。

推荐先做一个小而完整的第一批：

- `cfg=5` 的静态资源地图；
- `cfg=5` 的静态城池位置、类型和耐久；
- 资源地守军池 1–9 级的标准化 JSON；
- 所有产物的 manifest、SHA-256、源仓库提交号和解析器版本。

这一批可以直接落到当前默认首页“战场情报”，不需要增加新的左栏入口。

## 2. 审计边界

### 2.1 纳入范围

- `/Users/bytedance/stzb/server/src/main/resources/` 下已入库的静态配置。
- `/Users/bytedance/stzb/server/src/main/kotlin/` 下读取这些配置的第一方解析器。
- `/Users/bytedance/stzb/tools/monitor-agent/web/farming/` 下已入库的地图编码规则。
- 当前 `/Users/bytedance/stzb_watcher` 已有快照、战斗引擎、世界状态和战场情报接口，用于判断重复度与展示落点。

### 2.2 明确排除

- 账号、Cookie、Token、登录信息。
- 抓包中的玩家、同盟、军团或聊天数据。
- `/Users/bytedance/stzb/tools/monitor-agent/work/`，本机约 23 GB。
- `/Users/bytedance/stzb/tools/monitor-agent/captures/`，本机约 2.1 MB。
- `/Users/bytedance/stzb/work/emulator-backups/`，本机约 10 GB。
- `/Users/bytedance/stzb/stzb_9.2.2_out_branch_9.1.1776213/`，APK 解包树约 6 GB。
- `/Users/bytedance/stzb/analysis/`，反编译/分析目录约 336 MB。
- DLL、APK、NPK、反编译源码和调试数据库。
- `/Users/bytedance/stzb/cfg_tables_dump/*.strings.txt`：这是哈希文件名的字符串池派生物，不是有稳定字段契约的结构化表。

完整地形与城池位图虽然能在 APK 解包树和设备工作目录找到，但不满足本轮“排除解包物”的来源约束，因此不列为可直接迁移数据。

## 3. 来源与可复现性

本次审计基于：

- `server`：提交 `93ee999937d011b2a3dadf67ed39edfbb409aaca`，分支 `main`。
- `tools/monitor-agent`：提交 `376be7a958ee251a81b6045261d2caa02ea2478a`，分支 `main`。

候选资源和解析器在各自仓库中均已被 Git 跟踪，且审计时这些指定路径没有本地修改。

源解析器聚焦测试已通过：

```text
./gradlew test \
  --tests 'com.stzb.server.game.ClientCardPackCatalogTest' \
  --tests 'com.stzb.server.game.LandDefenderFactoryTest'

BUILD SUCCESSFUL
```

## 4. 当前系统已有内容

当前情报快照已经包含：

| 数据 | 数量 |
|---|---:|
| `hero_table.csv` | 2,077 行 |
| `skill_table.csv` | 6,572 行 |
| `skill_detail_table.csv` | 12,694 行 |
| `skill_effect_table.csv` | 206 行 |

当前快照同步器只允许上述四个 CSV，运行时读取项目内版本化快照，不依赖 `/Users/bytedance/stzb` 的绝对路径。来源：[`intelligence/snapshot.py:L12-L17`](file:///Users/bytedance/stzb_watcher/intelligence/snapshot.py#L12-L17)、[`intelligence/snapshot.py:L200-L218`](file:///Users/bytedance/stzb_watcher/intelligence/snapshot.py#L200-L218)。

当前 Web 已有：

- 武将和战法搜索接口；
- 5026/5028 统一世界状态；
- 世界视口接口；
- 战场情报 Canvas 地图；
- 地块风险解释；
- 查询助手；
- Kotlin 战斗引擎。

因此后续应补“静态世界和玩法关联”，而不是再做一套武将/战法基础库。

## 5. 排序总表

评分：5 为最高；体积以建议迁移的源数据或原始数据包为准。

| 排名 | 候选 | 业务价值 | 可靠性 | 原始体积 | 当前重复度 | 建议 |
|---:|---|---:|---:|---:|---|---|
| 1 | 资源地守军编成 | 5 | 5 | 1.47 MB | 解析器已有，资源缺失 | 第一批迁移 |
| 2 | `cfg=5` 静态资源地图 + 城池目录 | 5 | 4 | 4.40 MB | 当前无静态全图 | 第一批迁移 |
| 3 | 全赛季卡包武将池 | 4 | 4 | 436 KB | 当前无卡包关系 | 第二批迁移 |
| 4 | 9.2.2/9.2.4 协议命令目录 | 3 | 4 | 1.77 MB | 当前只有重点协议规则 | 第三批迁移 |
| 5 | 表字段类型字典 | 3 | 4 | 201 KB | 当前查询助手无 schema 工具 | 第三批迁移，白名单化 |
| 6 | 道具/外观配置 | 2 | 3 | 约 28 KB 起 | 当前页面无明确消费场景 | 暂缓 |
| - | 武将、战法、组合、装备基础配置 | 1 | 5 | 已存在 | 完全或高度重复 | 不迁移 |
| - | 字符串池、解包树、抓包、运行数据库 | 1 | 1 | 24 MB 至数十 GB | 不可维护或含隐私风险 | 禁止原样迁移 |

## 6. 第一优先级：资源地守军编成

### 6.1 数据组成

建议源文件：

| 文件 | 字节数 | SHA-256 |
|---|---:|---|
| `tb_cfg_army.bin` | 224,483 | `248f9b88517547d08a852aa1af3f660e0715d912e8bd91b5bef542770c6acde7` |
| `tb_cfg_army_count.bin` | 10,608 | `aeaae6ac9d1de598bd3c0d85947f689a514e195067c714e1a5551c74cfea5ea5` |
| `tb_cfg_hero_u.bin` | 1,179,109 | `6f59e896708ac3df62c4d7256f27b60a7ecff843f3ddfa730ded57e6233eb906` |
| `tb_cfg_gear_u.bin` | 7,038 | `f4bc962311d306968d421c02a7a84099e9cafa4ea62beab45f8197d4ccd99fbf` |
| `tb_cfg_gear_feature.bin` | 47,818 | `283c22bc1c8ad676db6dc5c1bdc4ba09f9689212d10e9cb23ab7c7518db33540` |

合计 1,469,056 字节。

### 6.2 已验证语义

现有解析器不是按字符串猜数据：

- `tb_cfg_army.bin` 将军队 ID、守军池和四个位置的武将实例关联起来；
- `tb_cfg_hero_u.bin` 提供武将 ID、等级、兵力、兵种、战法和兵种特性；
- `tb_cfg_gear_u.bin` 与 `tb_cfg_gear_feature.bin` 提供装备、装备战法和词条战法；
- `tb_cfg_army_count.bin` 提供 1–9 级地块的守军队数；
- 加载时强制校验 1–9 级守军池必须完整。

来源：[`ClientNpcArmyRepository.kt:L218-L231`](file:///Users/bytedance/stzb/server/src/main/kotlin/com/stzb/server/game/ClientNpcArmyRepository.kt#L218-L231)、[`ClientNpcArmyRepository.kt:L245-L299`](file:///Users/bytedance/stzb/server/src/main/kotlin/com/stzb/server/game/ClientNpcArmyRepository.kt#L245-L299)、[`ClientNpcArmyRepository.kt:L333-L450`](file:///Users/bytedance/stzb/server/src/main/kotlin/com/stzb/server/game/ClientNpcArmyRepository.kt#L333-L450)。

当前项目已经有几乎相同的 Kotlin 解析器：

`/Users/bytedance/stzb_watcher/battle-engine/src/main/kotlin/com/stzb/battle/core/ClientNpcArmyRepository.kt`

它与源解析器的有效差异只有包名，但当前 `battle-engine/src/main/resources/client-config/` 缺少上述守军资源，因此守军仓库没有形成可独立部署的数据能力。

### 6.3 前端用途

直接增强当前“战场情报”的地块抽屉：

- 地块等级与资源种类；
- 标准守军队数；
- 每个候选守军阵容；
- 武将等级、兵力、初始战法；
- 兵种特性；
- 装备和装备战法；
- “为什么危险”的可解释风险分解。

在“阵容战法研究”中增加：

- 按土地等级查看守军；
- 选择一个守军阵容，一键带入现有战斗模拟器；
- 对比己方阵容与守军阵容的速度、攻击距离、控制和恢复能力；
- 输出“配置事实”和“模拟推断”两个证据层，不能把模拟结果写成必胜结论。

### 6.4 推荐迁移形态

不要让 Flask 在运行时读取 MemoryPack 二进制。建议由 Kotlin 解析器离线生成：

```text
data/intelligence/client-9.2.2/npc/
├── npc_armies.json
├── npc_army_counts.json
├── manifest.json
└── checksums.sha256
```

`npc_armies.json` 至少保存：

- `pool`
- `armyId`
- `heroes[]`
- `heroId`
- `level`
- `troops`
- `heroType`
- `surfaceSkillId`
- `skillIds[]`
- `skillLevels[]`
- `troopFeatureIds[]`
- `equipmentIds[]`
- `equipmentSkillIds[]`
- `equipmentFeatureSkillIds[]`

### 6.5 风险

- 这是客户端 9.2.2 的配置快照，不能默认代表所有后续版本。
- 守军池与静态土地等级关联可靠，但真实战斗仍可能受赛季规则、活动规则和服务器调整影响。
- 必须在 UI 标注 `datasetVersion` 和证据类型 `CONFIG_FACT`。

## 7. 第一优先级：静态世界地图与城池目录

### 7.1 已入库资源地图

`/Users/bytedance/stzb/server/src/main/resources/map/` 有四个可追溯资源地图：

| cfg | 压缩字节 | 解压格子数 | 地图边长 |
|---:|---:|---:|---:|
| 2001 | 490,066 | 1,002,001 | 1,001 |
| 2002 | 658,463 | 1,442,401 | 1,201 |
| 5 | 4,249,560 | 9,006,001 | 3,001 |
| 984 | 8,009,302 | 16,008,001 | 4,001 |

合计压缩体积 13,407,391 字节，解压后共 27,458,404 个格子。

解析规则已经确认：

- WID 转坐标：`row = wid / 10000`、`col = wid % 10000`；
- 数组索引：`(row - 1) * mapSize + col - 1`；
- 地图大小由解压后字节数平方根推导；
- `cfg=5` 使用 legacy 编码，984/2001/2002 使用新编码；
- 资源类型的十位是土地等级。

来源：[`LandMapRepository.kt:L12-L35`](file:///Users/bytedance/stzb/server/src/main/kotlin/com/stzb/server/game/LandMapRepository.kt#L12-L35)、[`LandMapRepository.kt:L38-L87`](file:///Users/bytedance/stzb/server/src/main/kotlin/com/stzb/server/game/LandMapRepository.kt#L38-L87)、[`map_codec.py:L28-L68`](file:///Users/bytedance/stzb/tools/monitor-agent/web/farming/map_codec.py#L28-L68)。

### 7.2 `cfg=5` 静态城池

`tb_cfg_world_city_5.bin`：

- 146,519 字节；
- 2,354 条静态城池/建筑记录；
- 城池类型分布：
  - 类型 6：979；
  - 类型 7：613；
  - 类型 8：679；
  - 类型 10：62；
  - 类型 28：21；
- 耐久范围：100–200,000；
- SHA-256：`434e5a3956f9697f1d284977116f643a398e2a084b301eb080d1ee9f1d840b75`。

现有源解析器已经稳定读取 WID、城池类型和耐久，并校验 key 与行内 WID 一致。来源：[`WorldState.kt:L651-L705`](file:///Users/bytedance/stzb/server/src/main/kotlin/com/stzb/server/game/WorldState.kt#L651-L705)。

当前解析器没有解出静态城池名称，第一批不能承诺离线城名；城名应优先叠加实时 5026/5028 `WORLD_CITY` 数据。

### 7.3 对当前地图的直接提升

当前 Canvas 已经按视口遍历每个 WID；当实时状态里没有某格时，会创建 `landLevel: 0` 的空格。来源：[`static/intelligence-map.mjs:L55-L82`](file:///Users/bytedance/stzb_watcher/static/intelligence-map.mjs#L55-L82)。

静态底图接入后可实现：

- 无实时视野时仍显示资源等级与资源类型；
- 全局雷达缩远时按区域聚合资源热度；
- 进入局部镜头后展开真实格子；
- 静态城池永久显示为地标，实时所有权和名称作为覆盖层；
- 点击格子始终能定位，不再因为实时包未覆盖而“什么也不显示”；
- 风险评分可加入“静态土地等级”和“标准守军强度”。

推荐扩展现有接口，而不是新建一套地图：

```text
GET /api/intelligence/world/viewport
    ?rowUp=...
    &rowDown=...
    &colLeft=...
    &colRight=...
    &include=static,live,armies,risk
```

每格返回：

```json
{
  "wid": 10002,
  "row": 1,
  "col": 2,
  "staticResourceType": 51,
  "landLevel": 5,
  "staticCityType": null,
  "staticDurability": null,
  "live": {},
  "freshness": "unknown",
  "risk": {}
}
```

### 7.4 不可越界的结论

`resources_in_map.mbd` 只能可靠提供资源类型和等级，**不能单独判断可通行性**。

水域、山脉和城池需要：

- `map_all_data` 地形字节；
- `map_iscity_data` 城池位图；
- `decode_terrain_info()` 规则。

来源：[`map_codec.py:L71-L100`](file:///Users/bytedance/stzb/tools/monitor-agent/web/farming/map_codec.py#L71-L100)。

这些配套文件目前只在被本轮排除的客户端解包树或设备工作目录中找到，未出现在 `server/src/main/resources/map/` 的干净快照里。因此：

- 第一批可以做资源热力底图和城池地标；
- 第一批不能做“保证可行军”的路径规划；
- 后续若要迁移完整地形，必须先建立一份已入库、带版本和哈希的三文件地图快照。

### 7.5 体积策略

不要把 900 万或 1,600 万个格子展开成前端 JSON。

建议：

- 首期只迁移当前使用的 `cfg=5`；
- 保留 zlib 压缩源；
- 后端按视口解码，或导入紧凑 BLOB/分块索引；
- 全局雷达使用预计算多级瓦片聚合；
- API 只返回当前视口或热区桶。

## 8. 第二优先级：全赛季卡包武将池

### 8.1 数据与规模

候选由 12 个 `tb_cfg_card_extract*.bin` 和 11 个 `tb_cfg_card_prob*.bin` 组成：

- 23 个文件；
- 合计 436,215 字节；
- 组合 SHA-256：`5f10d85eccf6b53b7d3d3037fb2f59f113f03aadd1b7aea128ccd4b0105015e0`；
- 现有测试验证可合并得到 271 个唯一卡包。

来源测试：[`ClientCardPackCatalogTest.kt:L10-L18`](file:///Users/bytedance/stzb/server/src/test/kotlin/com/stzb/server/game/ClientCardPackCatalogTest.kt#L10-L18)。

### 8.2 可靠能力

现有解析器能够得到：

- `packId`
- `parentPackId`
- `containerPackId`
- `priority`
- 直接武将池
- 父子卡包递归合并后的武将池
- 某 `cfgDbId` 实际启用的卡包

来源：[`ClientCardPackCatalog.kt:L12-L26`](file:///Users/bytedance/stzb/server/src/main/kotlin/com/stzb/server/game/ClientCardPackCatalog.kt#L12-L26)、[`ClientCardPackCatalog.kt:L84-L134`](file:///Users/bytedance/stzb/server/src/main/kotlin/com/stzb/server/game/ClientCardPackCatalog.kt#L84-L134)。

### 8.3 前端用途

放在现有“阵容战法研究”，不新增侧栏：

- 武将详情增加“所属卡包”；
- 卡包详情展示武将池、阵营、兵种、稀有度分布；
- 搜索某武将在哪些赛季卡包出现；
- 对比两个卡包武将池差异；
- 查询助手支持“某卡包有哪些武将”。

### 8.4 重要限制

文件名含 `prob`，但当前解析器只从行 key 中分离 `packId` 和 `heroId`，没有解析权重、保底和活动修正。来源：[`ClientCardPackCatalog.kt:L205-L215`](file:///Users/bytedance/stzb/server/src/main/kotlin/com/stzb/server/game/ClientCardPackCatalog.kt#L205-L215)。

所以第一版功能应命名为：

- “卡包武将池”
- “卡包收录”
- “卡包关系”

不应命名为：

- “精确概率”
- “抽卡模拟器”
- “保底预测”

如果未来要做概率，必须单独完成字段语义、权重归一化和客户端行为验证。

## 9. 第三优先级：协议命令目录

### 9.1 数据规模

| 版本 | 命令数 | 文件字节 | SHA-256 |
|---|---:|---:|---|
| 9.2.2 | 2,594 | 872,348 | `b18cd8aa81a0023d847eb18cb086e47b54e7ad32c064b077e654aed6df15af59` |
| 9.2.4 | 2,655 | 901,156 | `797fc9c4cf0dcf13afcaeccfb8618bdc5f859e1beccf427bf09c1687a09c3c26` |

每条记录包含：

- 命令 ID；
- 一个或多个命令名；
- 请求端源码位置；
- 接收端源码位置；
- 抓包发送/接收计数。

### 9.2 版本差异

9.2.2 → 9.2.4：

- 新增 63 个命令 ID；
- 删除 2 个命令 ID；
- 1 个已有 ID 从无名称变为有名称；
- 678 条对象发生变化，但大部分是反编译源码行号漂移，不能当成 678 个协议语义变化。

这类目录能回答“某 cmd 是什么、在哪个版本出现、客户端哪里发送/接收”，但不能替代 5026/5028 这类经过字段验证的 payload schema。

### 9.3 系统用途

推荐只放在“情报研究”内部：

- 命令搜索；
- 9.2.2/9.2.4 版本差异；
- 未识别抓包 cmd 的名称补全；
- 当前捕获覆盖率；
- 点击命令跳到已有协议研究；
- 查询助手支持“5028 是什么”“9.2.4 新增了哪些命令”。

不建议：

- 出现在普通用户侧栏；
- 自动生成发包功能；
- 仅凭命令名推断请求参数；
- 将源码行号变化展示为协议行为变化。

## 10. 第三优先级：表字段类型字典

### 10.1 候选

`tb_field_types.json`：

- 379 张表；
- 201,254 字节；
- 每个字段包含字段名和类型；
- SHA-256：`1a8eec11c7804dccd4958ccae491e995d6ffba814c1388a47e51a64f4c34c7e2`。

`db_schema.txt`：

- 99,901 字节；
- 只包含表名和字段顺序；
- SHA-256：`a190fe3b0b8c2132156e7abb9e511cbe43bf78ae962bf919dcfb352fb8bd173d`。

两者重复时优先保留 `tb_field_types.json`，不要两份都迁移。

### 10.2 用途

当前查询助手已有武将、战法、战报、同盟成员和世界状态工具，但没有 schema 感知。来源：[`query_agent/tools.py:L9-L116`](file:///Users/bytedance/stzb_watcher/query_agent/tools.py#L9-L116)。

字段字典可以用于：

- 抓包表数据结构化校验；
- 解释某张 `Tb_*` 表有哪些字段；
- 检测客户端版本字段增删；
- 为人工研究生成只读字段浏览器；
- 帮助 5026/5028 外的结构化表解析。

### 10.3 安全边界

- 只提供表结构，不提供玩家数据。
- 查询助手只暴露白名单表。
- 不允许模型生成并执行任意 SQL。
- 不把字段名自动解释成确定业务语义。
- 当前业务不使用的 379 张表不必全部加载进提示词；应按需检索。

## 11. 已覆盖，不应重复迁移

### 11.1 武将/战法扩展 JSON

以下源文件与当前 `battle-engine` 副本 SHA-256 完全一致：

| 文件 | 条数 | 字节数 | SHA-256 |
|---|---:|---:|---|
| `hero_extra.json` | 395 | 888,629 | `e96cc20872e6620f25ecda5edde0a0bbdc04ed55480a1f3b207056542a52f73c` |
| `skill_extra.json` | 619 | 725,672 | `6fc583f0596db70c327ee32340c642ace5abebca26218149ff41ccbe584d065a` |
| `army_extra.json` | 42 | 32,259 | `167d045fe18c80198779d3b6b923e1995925cf7e917ad550ba30f9c9821b1a94` |

结论：不复制。若 Web 需要扩展描述，应从当前 `battle-engine` 版本化资源生成情报快照，避免建立第三份副本。

### 11.2 装备基础配置

`tb_cfg_gear.bin` 和 `tb_cfg_gear_feature.bin` 已存在于当前 `battle-engine`，并与源文件同哈希。

结论：不迁移基础表。后续只需给现有资源增加只读投影/API。

### 11.3 当前四个 CSV

武将、战法、明细和效果表已进入 `data/intelligence/client-9.2.2/`，有 manifest、哈希和隐私声明。

结论：继续沿用，不再从其他目录复制同类数据。

## 12. 暂缓项

### 12.1 `tb_cfg_item.bin`

源解析器目前只稳定读取 item ID 与 repo type，缺少对当前前端有价值的名称和说明。可以在未来做库存/道具研究时再迁移。

### 12.2 `tb_cfg_army_facade_shop.bin`

主要是外观能力，当前战场情报和阵容研究都不依赖它。除非后续需要在地图还原军队外观，否则优先级低。

### 12.3 完整地形包

价值高，但合格来源尚未形成：

- 客户端解包树有完整三件套；
- 设备工作目录也有地图缓存；
- `server/src/main/resources/map/` 目前只有资源地图。

正确做法是先在源仓库建立干净、版本化、可校验的 `resources + terrain + city bitset` 快照，再迁移到 Web。

## 13. 推荐的数据包设计

不要把所有内容继续塞进一个扁平目录。建议：

```text
data/intelligence/client-9.2.2/
├── manifest.json
├── config/
│   ├── hero_table.csv
│   ├── skill_table.csv
│   ├── skill_detail_table.csv
│   └── skill_effect_table.csv
├── npc/
│   ├── npc_armies.json
│   └── npc_army_counts.json
├── map/
│   ├── cfg-5-resources.mbd
│   ├── cfg-5-static-cities.json
│   └── cfg-5-overview-pyramid.bin
├── card-packs/
│   └── card_packs.json
├── protocol/
│   ├── commands-9.2.2.json
│   ├── commands-9.2.4.json
│   └── table_fields-selected.json
├── SOURCE.md
└── checksums.sha256
```

每个领域数据集必须记录：

- `datasetVersion`
- `clientVersion`
- `cfgDbId`
- `sourceRepository`
- `sourceCommit`
- `sourcePath`
- `sourceSha256`
- `extractorVersion`
- `generatedAt`
- `schemaVersion`
- 隐私声明
- 校验结果

现有同步器的“显式 allowlist、拒绝路径逃逸、生成 manifest 与 checksum、运行时不读源目录”原则应继续保留。来源：[`intelligence/snapshot.py:L96-L111`](file:///Users/bytedance/stzb_watcher/intelligence/snapshot.py#L96-L111)、[`scripts/sync_intelligence_snapshot.py:L15-L42`](file:///Users/bytedance/stzb_watcher/scripts/sync_intelligence_snapshot.py#L15-L42)。

## 14. 推荐实施顺序

### 阶段 A：地图与守军

1. 扩展快照清单，但按领域分目录。
2. 复用 Kotlin `ClientNpcArmyRepository`，生成标准化守军 JSON。
3. 迁移 `cfg=5` 的资源地图与静态城池投影。
4. 在后端加入静态地图仓库，按视口返回，不展开整张图。
5. 把静态格子叠加到现有 `/api/intelligence/world/viewport`。
6. 地块抽屉展示守军、风险解释和证据版本。
7. 全局雷达使用预聚合热区，局部镜头展开真实格子。

### 阶段 B：卡包

1. 用现有 Kotlin 解析器生成 `card_packs.json`。
2. 武将详情增加卡包关系。
3. 阵容战法研究增加卡包浏览和差异对比。
4. 查询助手增加只读卡包工具。

### 阶段 C：研发情报

1. 迁移两个版本的命令目录。
2. 生成命令语义差异，而不是简单 JSON diff。
3. 只迁移白名单表的字段类型。
4. 在“情报研究”内加入协议版本雷达。
5. 查询助手按需检索协议和 schema，不执行命令、不发包、不执行任意 SQL。

## 15. 验收条件

### 数据

- 所有迁移文件都能追溯到源仓库提交和源哈希。
- 原始配置、派生 JSON 和解析器版本分别记录。
- 更新后可以通过 `--check` 检测漂移。
- 不包含账号、玩家、同盟、聊天或抓包正文。

### 地图

- 实时视野为空时仍能显示静态资源格。
- 点击任意有效 WID 都能返回静态地块信息。
- `cfg=5` 资源等级与源解码器一致。
- 静态城池位置、类型和耐久可查询。
- 没有地形三件套时，API 不返回“可通行=true”。
- 缩远显示聚合热区，放近显示真实格子。

### 守军

- 1–9 级守军池全部非空。
- 武将、等级、兵力、战法、兵种特性和装备关系完整。
- UI 标明配置版本。
- 模拟结论与配置事实分层展示。

### 卡包

- 271 个唯一卡包通过源测试口径校验。
- 父子卡包武将池关系可解释。
- 页面不展示未经验证的概率或保底。

### 协议与 schema

- 9.2.2/9.2.4 新增、删除和改名命令可复现。
- 源码行号漂移不被算作协议语义变化。
- 查询助手只访问白名单与只读投影。

## 16. 最终建议

如果只选一个下一步，建议做：

> **“静态地块 + 标准守军”合并进入现有战场情报。**

它能同时提升：

- 地图不丢视野；
- 点击格子始终有内容；
- 风险热区更可信；
- 地块危险度可解释；
- 阵容研究有真实对手；
- 全局雷达和局部战术镜头的数据口径一致。

卡包关系适合作为第二个小功能；协议目录和字段字典属于研发情报，应放在后台研究能力，而不是再次扩张左栏。

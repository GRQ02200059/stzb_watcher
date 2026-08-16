# Android 迁移方案

## 目标

将当前 `stzb_watcher` 从桌面 Python 形态迁移为 **Kotlin 原生 Android 应用**，覆盖：

- 抓包 / 报文采集
- 报文解析
- 本地数据库存储
- 实时事件流
- 查询分析页面
- 战斗模拟器

当前结论：

- **页面、数据库、统计分析、实时推送可以迁移**
- **最大风险点是安卓本机抓包**
- 若要求“完全替代当前 PC 版抓包能力”，需要优先确认是否接受 `Root` 方案

---

## 当前项目的核心链路

当前仓库的主链路如下：

1. `scrapy_v2.py`
   - 使用 `scapy` 监听游戏端口流量
   - 解析明文 / zlib / XOR 数据
   - 将解析结果按消息类型写入 `capture_new/`
   - 尝试从报文中提取 `server_ip / role_id / role_name`

2. `realtime_writer.py`
   - 监控 `capture_new/`
   - 解析新报文并写入 SQLite
   - 维护内存事件队列
   - 向前端提供实时事件

3. `api_server.py`
   - 暴露全部 API
   - 提供 SSE 接口 `/api/stream`
   - 承担统计聚合与查询接口
   - 托管主页面 `static/dashboard.html`

4. `profile_manager.py`
   - 维护当前账号和历史账号
   - 按区服切换数据库
   - 支持多账号隔离

从职责上看，它们在安卓上的对应关系非常清晰：

| 当前模块 | Android 对应模块 |
|---|---|
| `scrapy_v2.py` | Capture 模块 |
| `realtime_writer.py` | Ingest / Parser / Event 模块 |
| `api_server.py` | Repository + UseCase + ViewModel |
| `profile_manager.py` | Profile 模块 |
| `static/*.js` | Compose UI |
| SQLite 直连 | Room |

---

## 安卓迁移的关键判断

## 1. 最难的不是 UI，而是抓包

桌面版当前依赖：

- `scapy`
- 原始 TCP 报文抓取
- 指定端口监听
- 直接访问本地文件系统

这些能力在安卓上存在天然限制。

### 方案 A：Root 抓包

特点：

- 最接近当前 Python + Scapy 能力
- 可以直接走原始流量采集
- 报文还原方式与当前项目最一致

代价：

- 只能服务 root 用户
- 兼容性和分发难度高
- 需要 JNI / `tcpdump` / `libpcap` / su 权限链路

结论：

- 如果你的目标是 **完整替代 PC 抓包能力**，这是最稳的路线

### 方案 B：非 Root，基于 `VpnService`

特点：

- 不依赖 root
- 可以做“本机 VPN 代理式抓包”
- 安卓原生可实现

代价：

- 实现复杂
- 对游戏流量是否完整可见并不完全可控
- 某些协议 / 加密 / 系统策略会影响可用性
- 性能、耗电、前台服务限制都需要认真处理

结论：

- 可以作为研究方向
- 但**不能承诺与桌面版抓包能力等价**

### 方案 C：混合方案

特点：

- 抓包仍在 PC / 服务器端
- 安卓只负责展示、分析、模拟器和账号管理
- 落地速度最快

结论：

- 这不是你当前选的目标
- 但它应该作为兜底方案保留

---

## 推荐的 Android 总体架构

推荐采用：

- `Kotlin`
- `Jetpack Compose`
- `Room`
- `DataStore`
- `Coroutines + Flow`
- `WorkManager`
- `ForegroundService`
- `Navigation Compose`

建议的分层：

```text
app
├── capture          # 抓包、报文采集、前台服务、权限管理
├── parser           # 明文/zlib/XOR 解码、消息类型解析
├── data
│   ├── db           # Room entities / dao / migrations
│   ├── repo         # Repository
│   └── profile      # 多账号/多区服配置
├── domain
│   ├── usecase      # 战报查询、排行、团报表、态势分析
│   └── model
├── realtime         # 事件总线、状态流、最近事件缓存
├── feature
│   ├── dashboard
│   ├── battles
│   ├── analysis
│   ├── attendance
│   ├── profiles
│   ├── monitor
│   └── simulator
└── common
```

---

## 模块映射建议

## 1. 抓包模块

目标：

- 替代 `scrapy_v2.py`

Android 设计：

- `CaptureService`
- `PacketSource`
- `PacketDecoder`
- `SessionAssembler`

职责拆分：

- `CaptureService`
  - 作为前台服务长期运行
  - 管理通知、生命周期、保活
- `PacketSource`
  - Root 模式读取原始流量
  - 或非 Root 模式对接 `VpnService`
- `SessionAssembler`
  - 完成 TCP 流重组
- `PacketDecoder`
  - 还原当前项目中的包头结构
  - 解析 `明文 / zlib / XOR`

说明：

- `scrapy_v2.py` 里的逻辑不能直接翻译成 Kotlin 后就运行
- 真正要重写的是“抓取来源”和“流量访问方式”

## 2. 报文解析与入库

目标：

- 替代 `realtime_writer.py`

Android 设计：

- `IngestCoordinator`
- `MessageParserRegistry`
- `BattleWriter`
- `EventBus`

职责：

- `IngestCoordinator`
  - 接收解码后的消息
  - 根据消息类型路由到不同解析器
- `MessageParserRegistry`
  - 维护 `0000000a`、`000013a2`、`000013a4` 等解析器
- `BattleWriter`
  - 将战报、武勋、排行、地图状态等写入 Room
- `EventBus`
  - 用 `SharedFlow` / `StateFlow` 对 UI 推送实时事件

迁移原则：

- **先忠实移植解析逻辑**
- 不要一上来就重构业务口径
- 先保证同一份报文在 Python 和 Kotlin 中能得到一致结果

## 3. 数据库存储

目标：

- 替代当前 SQLite 直写和运行时补列

Android 设计：

- `Room`
- 明确版本号和 migration

建议拆表：

- `battles_v2`
- `battle_heroes`
- `wuxun_log`
- `power_log`
- `team_users`
- `tasks`
- `task_reports`
- `union_list`
- `player_power_rank`
- `zone_players`

注意：

- 当前 Python 项目用了不少“运行时自动补列”逻辑
- 安卓上不建议继续这样做
- 应该在 Room migration 里显式管理 schema 演进

## 4. 账号与区服

目标：

- 替代 `profile_manager.py`

Android 设计：

- `ProfileStore`
- `ProfileRepository`
- `CurrentProfileManager`

存储方式：

- 轻量信息放 `DataStore`
- 多区服数据库路径可以放 app 私有目录

建议规则：

- 延续当前设计：
  - `server_ip -> db`
  - `server_ip + role_id -> profile_id`

这样迁移时认知成本最低。

## 5. 前端页面

目标：

- 将 `static/dashboard.html + app1.js + app2.js + sim.js` 改写为 Compose 页面

推荐页面拆分：

- 首页总览
- 实时战报流
- 战报列表 / 详情
- 团数据
- 战场分析
- 攻城考勤
- 地图态势
- 账号切换
- 战斗模拟器

Compose 对应结构建议：

- 每个页面一个 `feature`
- 每个页面：
  - `UiState`
  - `ViewModel`
  - `Route`
  - `Screen`

---

## 战斗模拟器迁移建议

当前仓库有两套模拟器资产：

- `static/sim.js`：已经嵌入主页面
- `stzbBattleSimulator-main/`：独立 Vue/Vite 子项目

推荐迁移策略：

1. 先不要直接迁 `Vue` 页面
2. 先把“模拟计算核心”抽出来
3. 用 Kotlin 重写计算模型和状态机
4. Compose 只负责展示和交互

原因：

- 你最终要的是安卓原生，不是 WebView 套壳
- 直接搬 UI 价值不高
- 模拟器真正值得保留的是计算逻辑和数据模型

---

## 迁移阶段建议

## Phase 0：先做技术验证

目标：

- 不做大规模重写
- 先回答“安卓抓包是否可行”

建议产出：

1. 一个最小 Android 原型
2. 验证两件事：
   - 能否采到目标游戏流量
   - 能否在安卓上还原你当前的包头和解码逻辑

如果这一步失败，就不要继续全量迁移。

## Phase 1：先迁解析与数据库

目标：

- 不依赖真实抓包
- 用已有 JSON / 报文文件喂给 Kotlin 解析器

建议产出：

- Kotlin 版 parser
- Kotlin 版 Room schema
- 可导入历史数据的本地版本

这一步完成后，你至少会拥有一个“离线可用”的安卓分析端。

## Phase 2：迁移主页面

目标：

- 将最常用页面先迁到 Compose

优先级建议：

1. 首页总览
2. 战报列表 / 战报详情
3. 团数据
4. 战场分析
5. 账号切换

## Phase 3：接入实时链路

目标：

- 让 UI 响应本地入库事件

Kotlin 对应方式：

- `Flow`
- `Room` 观察查询
- 应用内事件总线

## Phase 4：最后攻克安卓抓包

目标：

- 将 `scrapy_v2.py` 的能力替换成安卓 CaptureService

说明：

- 这是最后做，不是最先做
- 因为它最不确定，也最依赖设备环境

---

## 风险清单

## 高风险

1. 安卓非 Root 抓包能力可能无法达到桌面版水平
2. 游戏协议、加密和系统限制可能导致部分流量不可见
3. 长时前台服务会带来耗电、保活和权限问题
4. 当前 `api_server.py` 中大量 SQL 聚合需要重写为 Repository + DAO 组合

## 中风险

1. Python 宽松的字典/动态结构，迁到 Kotlin 后需要强类型建模
2. 当前 schema 演进较自由，Room migration 需要一次性梳理清楚
3. 统计页面较多，Compose 首版不宜一口气全上

## 低风险

1. 多账号切换
2. 本地数据库
3. 战报列表和详情页
4. 纯查询类页面

---

## 最推荐的落地顺序

如果你坚持“全迁移”，我建议实际执行时按这个顺序推进：

1. **先做 Android 技术验证工程**
   - 只验证抓包与解码可行性
2. **再做 Kotlin parser + Room**
   - 先支持离线导入
3. **再迁 Compose UI**
   - 先迁最常用页面
4. **最后再并入实时抓包**

也就是说：

- **先证明能抓**
- **再证明能算**
- **最后再做全产品形态**

---

## 我对这个项目的具体建议

基于当前代码结构，我不建议你直接“把 Python 文件逐个翻译成 Kotlin”。

更稳的做法是：

1. 保留现有业务口径
2. 重建 Android 原生架构
3. 逐步搬迁解析规则和 SQL 统计逻辑

一句话概括：

- **迁的是能力，不是文件形态**

---

## 下一步建议

最合理的下一步不是直接写全部安卓代码，而是先做两件事之一：

1. **输出 Android 模块拆分清单**
   - 细化到包结构、类名、ViewModel、DAO、Entity
2. **直接初始化 Android 工程骨架**
   - 建 `app` 模块
   - 建 `capture/parser/data/feature` 目录结构
   - 先把离线导入和本地数据库跑起来

如果继续推进，我建议优先做第 2 个：
**先在仓库里创建一个 Kotlin Android 工程骨架，并把 Room + Profile + Battle 列表这条最短主链路跑起来。**

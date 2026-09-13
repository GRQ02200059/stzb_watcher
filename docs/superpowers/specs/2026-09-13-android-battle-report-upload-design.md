# Android 战报静默上传设计

日期：2026-09-13

状态：已确认

## 1. 目标

为 `astzb/app` 安卓客户端增加战报云端同步能力。每次新 App 进程完成登录校验后，客户端读取当前选中账号档案的本地战报数据库，并把新增或发生变化的结构化战报静默上传到现有服务器：

```text
http://152.136.236.184:9080
```

上传不得阻塞 App 首页，不显示入口、进度、成功提示、失败提示或系统通知。网络失败不得影响本地功能。

本设计仅覆盖 Android 客户端上传和服务端接收、存储，不增加云端战报查看、下载、共享或管理页面。

## 2. 已确认范围

### 2.1 上传对象

每次只处理当前选中的本地账号档案，使用以下档案元数据区分数据来源：

- `profileId`
- `serverAddress`
- `roleId`
- `displayName`

上传以下本地表的数据：

- `battles_v2`：战报主记录；
- `battle_heroes`：攻守双方武将明细；
- `battle_skills`：攻守双方战法明细。

不上传：

- 整个 SQLite 数据库文件；
- `raw_json`、`source_msg_id` 等原始抓包或内部证据字段；
- `stzb_packets`、抓包文件和诊断导出；
- 登录密码、session 明文持久副本；
- AI 设置、对话、记忆、反馈或自动化数据；
- 其他非战报业务表。

`battles_v2` 中已经结构化的战斗时间、结果、类型、坐标、攻守玩家、同盟、兵力、武勋、天气、夜战、队伍和装备等字段可以上传。已经有独立明细表表达的数据，不再依赖 `raw_json` 还原。

### 2.2 启动口径

“每次打开”定义为：

1. 新 App 进程启动；
2. 已保存 session 的启动校验成功，或用户登录/注册成功；
3. 进程第一次进入认证 `Ready` 状态。

以下情况不重复触发：

- Activity 因旋转或系统配置变化重建；
- App 从后台切回前台；
- Compose 重组；
- 同一进程内重复进入首页。

同一进程中切换本地档案后不立即上传新档案；新档案在下次新进程启动并完成登录校验后同步。这样可严格保持“每次打开一次”的语义。

### 2.3 静默行为

- 不新增设置项、按钮、状态页或同步记录页面；
- 不显示 Toast、Snackbar、Dialog 或通知；
- 不改变现有登录页和业务页面；
- 上传成功或失败只写不含敏感数据的内部诊断日志；
- 同步任务失败时由系统后台调度器重试，App 仍正常使用本地数据。

## 3. 方案选择

采用“清单协商 + 内容指纹 + 分批上传”的增量同步方案。

未采用每次全量上传，因为随着战报增长会持续增加启动后的读取量、流量和服务器写入压力。未采用单纯时间游标，因为旧战报的武将名、位置或战法后来被补全时，时间游标无法可靠识别变化。

每条战报使用稳定内容指纹检测变化。客户端即使丢失本地同步状态或重装，服务端仍可根据用户、档案和战报 ID 幂等去重。

## 4. 总体架构

```text
StzbAppActivity 认证进入 Ready
        |
        v
进程内 BattleReportSyncLauncher（只允许触发一次）
        |
        v
WorkManager 唯一后台任务
        |
        +--> 当前 ProfileSnapshot
        +--> 只读打开当前 SQLite
        +--> 读取 battles_v2 / battle_heroes / battle_skills
        +--> 生成战报内容指纹
        |
        v
Auth session + HTTP API
        |
        v
152.136.236.184:9080
        |
        +--> 校验现有登录 session
        +--> 返回需要上传的战报 ID
        +--> 事务化 upsert 主表和子表
```

客户端同步模块独立于认证模块和战报展示仓库，避免把网络上传职责塞进 `LocalStzbDatabase`、`AuthRepository` 或 Compose 页面。服务端上传 API 复用认证用户和 session 验证逻辑，但战报存储使用独立数据表。

## 5. 客户端组件

### 5.1 `BattleReportSyncLauncher`

职责：

- 接收“认证已 Ready”的进程事件；
- 使用原子进程标记保证同一进程只调度一次；
- 获取当前 `LocalProfile`；
- 创建包含当前档案标识的唯一 WorkManager 任务。

任务名称包含稳定的 `profileId`，但本次进程仍只调度当前档案。

### 5.2 `BattleReportSyncWorker`

职责：

- 在后台线程读取当前档案数据库；
- 获取当前加密 session store 中的 token；
- 执行清单协商和分批上传；
- 根据错误类型返回成功、重试或永久失败。

约束：

- 仅在网络可用时运行；
- 使用唯一任务，避免重复并发；
- 唯一任务名为当前 `profileId` 的稳定派生值，使用 `REPLACE` 策略；新一次冷启动会替换上一次尚未完成的同档案任务，服务端事务和幂等键保证替换安全；
- 使用指数退避；
- 不要求充电或空闲设备；
- 不启动前台服务，不显示通知；
- Worker 输入只保存 `profileId`，不保存 session token、玩家名或战报正文；
- 执行时重新从 `ProfileManager` 校验档案仍存在，并从安全存储读取 token。

### 5.3 `AndroidBattleReportSource`

职责：

- 使用 `Context.getDatabasePath(profile.databaseName)` 定位当前数据库；
- 以只读模式打开数据库；
- 先确认三张表存在；
- 按 `battle_id` 稳定排序和分批读取；
- 将一条主记录及其武将、战法子项组装为一个同步对象；
- 不复制或锁定整个数据库文件。

SQLite 使用 WAL 时允许读取与抓包写入并行。每一批在一个只读事务中取得一致快照；同步不修改现有战报表。

### 5.4 `BattleReportCanonicalizer`

为每条战报生成确定性的 JSON 和 SHA-256 指纹：

- 主表字段使用固定字段顺序；
- `null`、数字、字符串保持明确类型；
- 武将按 `side, pos, hero_id` 排序；
- 战法按 `side, pos, skill_id` 排序；
- 排除本地自增 `id`、`raw_json`、`source_msg_id`；
- 指纹覆盖所有实际上传字段。

规范格式版本固定为 `schemaVersion = 1`。对象键按 Unicode 码点升序排列、无额外空白，整数使用十进制，字符串使用标准 JSON 转义且不强制 ASCII，空值编码为 `null`；数组按上面的业务键排序。Android 与 Python 使用共享黄金样例验证生成的 UTF-8 字节和 SHA-256 完全相同，避免不同 JSON 库的默认行为造成永久重传。

只要旧战报被补齐武将、战法或结构化字段，指纹就会变化，下一次启动会重新上传。

### 5.5 `BattleReportSyncTransport`

职责：

- 调用服务端同步接口；
- 将现有 session token放在请求 JSON 的 `token` 字段中，与当前认证 API 保持一致；
- 执行严格响应解析、大小限制和超时；
- 不记录请求正文、响应正文或完整 token。

## 6. 同步协议

所有接口继续位于 `/v1` 下，并返回 `Cache-Control: no-store`。

### 6.1 清单协商

```text
POST /v1/battle-reports/manifest
```

请求示例：

```json
{
  "token": "opaque-session-token",
  "clientVersion": "1.2.0",
  "profile": {
    "profileId": "stable-profile-id",
    "serverAddress": "game-server",
    "roleId": "role-id",
    "displayName": "档案名称"
  },
  "reports": [
    {"battleId": 123, "contentHash": "sha256-hex"}
  ]
}
```

服务端返回本批中缺失或指纹不同的 ID：

```json
{
  "ok": true,
  "requiredBattleIds": [123],
  "requestId": "uuid"
}
```

客户端按固定上限发送清单批次，避免一次把全部哈希装进单个请求。首版上限为每批 200 条。

### 6.2 上传战报批次

```text
POST /v1/battle-reports/upload
```

请求包含相同的 token、clientVersion、profile，以及最多 50 条完整结构化战报。每条对象包含：

- `battleId`；
- `contentHash`；
- `battle` 主表字段；
- `heroes` 数组；
- `skills` 数组。

请求顶层和每条战报都带 `schemaVersion = 1`。未知版本必须被拒绝，防止客户端与服务器对同一哈希使用不同字段定义。

服务端返回：

```json
{
  "ok": true,
  "accepted": 50,
  "requestId": "uuid"
}
```

服务端必须重新计算规范内容哈希并与客户端提交值比对，不能无条件信任客户端哈希。请求体硬限制为 2 MiB；单条异常超限时拆成单条重试，仍超限则作为不可上传记录跳过，不能阻断其他战报。

现有 Flask 应用的全局 `MAX_CONTENT_LENGTH = 16 KiB` 需要调整为 2 MiB，同时改为按路由执行更严格限制：原注册、登录、验证和退出接口继续限制为 16 KiB，manifest 限制为 128 KiB，upload 限制为 2 MiB。这样不会放宽原认证接口的攻击面。

### 6.3 幂等键

服务端的逻辑唯一键为：

```text
(auth_user_id, profile_id, battle_id)
```

同一登录账号下不同档案互相隔离；不同登录账号即使拥有相同 `profileId` 或 `battleId` 也互不覆盖。

## 7. 服务端数据模型

现有认证表保持不变，新增：

### 7.1 `battle_report_profiles`

- `id`
- `user_id`
- `profile_id`
- `server_address`
- `role_id`
- `display_name`
- `created_at`
- `updated_at`
- 唯一键 `(user_id, profile_id)`

### 7.2 `battle_reports`

- 服务端主键；
- `user_id`、`profile_id`、`battle_id`；
- `content_hash`；
- `payload_json`：不含原始抓包字段的规范化战报主记录；
- `battle_time`、`captured_at`：用于后续检索；
- `created_at`、`updated_at`；
- 唯一键 `(user_id, profile_id, battle_id)`。

### 7.3 `battle_report_heroes`

- 关联服务端战报主键；
- `side`、`pos`、`hero_id`、`hero_name`、`level`、`star`、`max_hp`、`remain_hp`、`damage_taken`；
- 唯一键 `(report_id, side, pos)`。

### 7.4 `battle_report_skills`

- 关联服务端战报主键；
- `side`、`pos`、`skill_id`、`skill_name`、`skill_level`；
- 唯一键 `(report_id, side, pos, skill_id)`。

主表使用规范 JSON 保存宽字段，武将和战法使用明细表支持汇总查询，避免复制 Android 当前宽表的每一列到服务端 schema。

每个上传批次在单个事务中执行：逐条更新主记录，删除对应旧武将和战法子项，再写入当前完整子项。任何一步失败都回滚整个批次。响应成功时 `accepted` 必须等于请求战报数；否则客户端重试整批，依靠幂等键消除重复。

同步语义是“上传归档和更新”，不是双向镜像。本地删除战报不会删除服务端已有归档；首版也不提供客户端或公开 HTTP 删除接口。

## 8. 认证、安全与隐私

- 两个新接口必须验证现有 session；
- 已撤销 session、被禁用账号或全局停服时拒绝同步；
- session 仍只以哈希形式存储在服务器；
- token、玩家名、UID 和完整战报不得进入服务日志；
- 日志仅保留 request ID、状态码、上传数量、耗时和经过截断的来源 IP；
- JSON 字段、数组长度、字符串长度、整数范围和请求体大小均需校验；
- 战报主表字段使用协议版本内的明确允许列表，未知字段拒绝；`raw_json` 和 `source_msg_id` 永远不在允许列表中；
- 客户端只允许读取 `ProfileManager` 给出的合法数据库名；
- 服务端不提供公开枚举、查询、下载或删除战报的 HTTP 接口；
- SQLite 外键、唯一索引、WAL、busy timeout 和显式事务沿用认证仓库的约束。

当前服务器使用 HTTP，session token、玩家名、UID 和战报数据在链路上不具备传输加密保护。用户已确认复用该服务器；本功能不宣称链路安全。迁移 HTTPS 时应只替换集中配置的基础 URL，不改同步协议。

本设计明确替代 `2026-08-16-android-startup-auth-design.md` 中“认证服务器不接收战报或游戏数据”的旧范围约束；其他认证安全约束继续有效。

## 9. 错误处理与重试

客户端分类：

- 无网络、超时、HTTP 5xx：返回 `Result.retry()`，指数退避；
- HTTP 429：尊重 `Retry-After` 并重试；
- session 无效或账号禁用：本次任务永久结束，不改登录界面、不删除 token，下一次正常启动认证流程负责处理；
- 档案已删除、数据库不存在或没有战报：视为成功；
- 本地数据库暂时繁忙：重试；
- 协议响应无效、请求数据永久不合法：记录最小诊断后结束，避免无限循环。

客户端不因为同步失败撤销本次已经通过的 App 访问权限。认证负责访问控制，同步是非阻塞附属任务。

服务端分类：

- 无效 JSON、字段或哈希：`400 INVALID_INPUT`；
- session 无效：`401 SESSION_INVALID`；
- 账号禁用：`403 ACCOUNT_DISABLED`；
- 全局停服：`503 SERVICE_DISABLED`；
- 请求过大：`413 PAYLOAD_TOO_LARGE`；
- 临时数据库错误：`503`，不泄露异常正文；
- 未知错误：`500 INTERNAL_ERROR`。

## 10. 部署边界

认证服务当前保留在 `codex/windows-desktop-auth` 分支及 `.worktrees/windows-desktop-auth` 工作树，Android app 位于当前 `main`。实施需要：

1. 在认证服务代码线新增 schema、repository、service 和 API；
2. 运行完整认证服务回归测试；
3. 使用现有版本化 Ubuntu 部署流程发布到 `152.136.236.184`；
4. 验证服务健康、旧登录接口和新上传接口；
5. 在当前 Android 代码线上接入同步模块；
6. 保持当前未提交的 AI、战术和 UI 改动不被覆盖或混入无关提交。

服务端部署是外部状态变更，实施计划必须在执行部署前复核目标主机、环境文件和当前服务状态；不得重建、清空或替换现有认证数据库。schema 迁移必须向前兼容且可重复执行。

## 11. 测试策略

### 11.1 Android JVM 测试

- 规范 JSON 的字段顺序和排序稳定；
- 任意已上传字段变化都会改变哈希；
- 本地自增 ID、`raw_json`、`source_msg_id` 不影响哈希；
- 清单按 200 条分批；
- 内容按 50 条和 2 MiB 上限分批；
- manifest 只选择缺失或变化的战报；
- 相同进程多次 `Ready` 只调度一次；
- 网络、鉴权、限流和协议错误正确映射到重试策略。

### 11.2 Android 仪器测试

- 当前档案数据库路径选择正确；
- 只读采集完整关联一条战报的英雄和战法；
- 多档案数据不串库；
- 数据库不存在、表为空或抓包同时写入时不崩溃；
- Activity 重建不会重复调度；
- Worker 输入和持久任务数据中没有 token 或战报正文。

### 11.3 服务端测试

- schema 可在已有认证数据库上幂等迁移；
- 原认证接口仍严格限制 16 KiB，manifest 限制 128 KiB，upload 限制 2 MiB；
- manifest 按用户、档案和战报 ID 隔离；
- 相同哈希不要求重传，变化哈希要求重传；
- upload 重算哈希并拒绝不匹配内容；
- 重复上传不会重复创建主记录；
- 更新战报会完整替换英雄和战法明细；
- 中途失败回滚整个上传批次；
- 非法、过大和超量请求被拒绝；
- 无效 session、禁用账号和停服被拒绝；
- 原有注册、登录、verify、logout 和管理 CLI 回归通过。

### 11.4 联调验收

1. 用测试账号登录并准备当前档案战报；
2. 冷启动 App，确认首页不等待同步且无任何同步 UI；
3. 服务端确认该用户、档案和战报已写入；
4. 再次冷启动，确认 manifest 检测为无需上传；
5. 补全一条旧战报的武将或战法，再次冷启动，确认只更新该战报；
6. 断网冷启动，确认 App 正常进入且任务等待网络；
7. 恢复网络，确认后台任务完成；
8. 切换 Activity、切后台再返回，确认同一进程不重复触发；
9. 使用另一个登录账号或本地档案，确认数据隔离。

## 12. 完成定义

- 每个新 App 进程在认证成功后静默调度一次当前档案同步；
- App 首页和本地功能不受上传时延或失败影响；
- 只上传 `battles_v2`、`battle_heroes`、`battle_skills` 的结构化允许字段；
- 不上传 SQLite 文件、原始抓包、`raw_json` 或其他业务数据；
- 服务端按登录用户、档案和战报 ID 隔离并幂等去重；
- 旧战报被补全后能够通过内容指纹再次上传；
- 无 UI、通知或用户操作入口；
- Android 单元测试、仪器测试和 Debug 构建通过；
- 服务端新增测试与原认证回归测试通过；
- 服务器上线后旧认证接口和新同步接口均通过健康验证。

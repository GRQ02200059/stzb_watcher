# 网页端战斗系统替换设计

日期：2026-08-10

## 背景

当前仓库有多套战斗相关实现：

- 网页端 `POST /api/simulate` 通过 Python `battle_sim` 模块计算，`static/sim.js` 消费旧响应格式。
- Android 端 `LocalBattleSimulator` 是本机轻量通用回合制内核。
- `~/stzb/server` 下存在更完整的 Kotlin 配置驱动战斗系统，包含 `BattleEngine`、`BattleConfigRepository`、`BattleTeamBuilder`、技能规则图、事件流和客户端战报编码。

本阶段目标是先替换网页端战斗模拟器的计算主路径。Android 端暂不替换，待网页端验证稳定后再规划第二阶段。

## 目标

- 网页端 `/api/simulate` 主路径改为使用 `~/stzb/server` 战斗系统迁入后的 Kotlin 引擎。
- 保持现有 `static/sim.js` 和 `/api/simulate` 响应结构尽量兼容，降低 UI 改动。
- 让单次模拟返回完整事件/日志，多次模拟胜率统计来自 Kotlin 引擎。
- 战斗资源使用 `battle-config` 中的客户端 CSV/JSON，而不是旧 Python/JS 简化数据。
- 为后续 Android 接入保留共享引擎边界，但本阶段不改 Android。

## 非目标

- 不迁移 `~/stzb/server` 的 Netty 服务端、账号存档、地图归属、出征结算、PVP 服务流程。
- 不替换 Android `LocalBattleSimulator`。
- 不重写抓包、战报入库或世界场景解析。
- 不重写网页战斗模拟 UI。
- 不在第一阶段承诺性能最优；先保证正确的引擎接入和接口兼容。

## 总体架构

```text
static/sim.js
  -> POST /api/simulate
  -> Flask simulate route
  -> Python BattleEngineAdapter
  -> battle-engine-cli
  -> battle-engine-core
```

### `battle-engine-core`

从 `~/stzb/server` 抽取战斗深模块：

```text
battle-engine/core/
  src/main/kotlin/com/stzb/battle/
    BattleEngine
    BattleModel
    BattleConfigRepository
    BattleTeamBuilder
    skill/*
  src/main/resources/battle-config/
    hero_table.csv
    skill_table.csv
    skill_detail_table.csv
    skill_effect_table.csv
    hero_extra.json
    skill_extra.json
    army_extra.json
```

迁入后允许做包名、资源路径和 Gradle 配置适配，但不改战斗语义。

### `battle-engine-cli`

提供 JSON stdin/stdout 或命令行参数输入。第一版采用 one-shot CLI，后续如果性能不足再升级常驻 daemon。

CLI 输入：

```json
{
  "seed": 20260810,
  "repeat": 100,
  "attacker": {
    "morale": 100,
    "heroes": [
      {
        "heroId": 100027,
        "position": 0,
        "level": 40,
        "advanceLevel": 5,
        "troops": 9000,
        "extraSkillIds": [200101, 200102],
        "attributePoints": {
          "attack": 0,
          "defense": 0,
          "strategy": 0,
          "speed": 0
        }
      }
    ]
  },
  "defender": {
    "morale": 100,
    "heroes": []
  }
}
```

CLI 输出：

```json
{
  "ok": true,
  "repeat": 100,
  "attackerWins": 61,
  "defenderWins": 32,
  "draws": 7,
  "firstRun": {
    "outcome": "ATTACKER_WIN",
    "attackerRemain": 12345,
    "defenderRemain": 4321,
    "events": [],
    "textLog": []
  }
}
```

## Flask 兼容层

`/api/simulate` 路由保留。新增 `BattleEngineAdapter`：

- 把旧请求的 `blue/red` 映射到 `attacker/defender`。
- 继续支持旧字段拼写 `heros`。
- 把旧 `id/level/up/equip_skills/extra_attrs` 转为 Kotlin CLI 输入。
- 调用 CLI，解析 JSON 输出。
- 将 CLI 输出转换回旧前端格式。

单次模拟响应保持：

```json
{
  "ok": true,
  "result": {},
  "engine": "stzb-kotlin",
  "engineResult": {}
}
```

多次模拟响应保持：

```json
{
  "ok": true,
  "repeat": 100,
  "blue_wins": 61,
  "red_wins": 32,
  "draws": 7,
  "blue_rate": 61.0,
  "red_rate": 32.0,
  "draw_rate": 7.0,
  "engine": "stzb-kotlin",
  "engineResult": {}
}
```

错误响应继续兼容：

```json
{
  "ok": false,
  "error": "...",
  "trace": "..."
}
```

## Fallback 策略

第一阶段保留旧 Python `battle_sim` 作为可选 fallback：

- 默认主路径是 Kotlin engine。
- 当 CLI 不存在、启动失败、输出不是合法 JSON 或超时时，后端可回退旧 `battle_sim`。
- fallback 响应必须标注 `engine = "legacy-fallback"`。
- 稳定后可以关闭 fallback，并让错误直接暴露。

## 性能策略

- CLI 不在请求时编译；Gradle 构建产物应提前生成。
- `/api/simulate` 后端硬限制 `repeat`，第一阶段只允许 `1/100/1000`。
- CLI 调用设置超时，防止请求挂死。
- 如果 1000 次模拟耗时不可接受，再引入常驻 JVM daemon 或进程池。

## 测试策略

### Kotlin

- `BattleEngineCliTest`
  - 固定 JSON fixture 可以运行单场战斗。
  - 输出包含 `ok/repeat/firstRun/outcome/events/textLog`。
  - 固定 seed 输出稳定。

### Python

- `test_battle_engine_adapter.py`
  - 旧 `blue/red/heros` 请求能转换为 CLI 输入。
  - CLI 单次输出能转换为旧 `result` 响应。
  - CLI 多次输出能转换为旧胜率字段。
  - CLI 超时、异常、非法 JSON 进入错误或 fallback 路径。

### API

- Flask test client 覆盖：
  - `POST /api/simulate` 单次模拟。
  - `POST /api/simulate` 100 次模拟。
  - 非法 payload 返回稳定错误。

### 前端 Smoke

- `static/sim.js` 能加载武将/战法资源。
- 单次模拟能显示战斗结果和日志。
- 100 / 1000 次模拟能显示胜率统计。

## 迁移顺序

1. 复制 `~/stzb/server` 战斗深模块和 `battle-config` 资源到 `battle-engine/core`。
2. 让 `battle-engine/core` 独立编译并通过核心测试。
3. 新增 `battle-engine-cli` 和固定 fixture 测试。
4. 新增 Python `BattleEngineAdapter`，先写 adapter 测试。
5. 将 `/api/simulate` 主路径切到 adapter，保留 legacy fallback。
6. 手动跑网页战斗模拟 smoke。
7. 稳定后再决定是否关闭 fallback 或规划 Android 第二阶段。

## 风险与约束

- `~/stzb/server` 战斗模块依赖较多配置和内部类，抽取时要严格避免把 Netty、账号、地图和出征服务一起迁入。
- Android 后续接入需要重新评估字节码目标、依赖体积、Jackson/资源加载和 APK 大小。
- 旧网页 `sim.js` 的结果字段可能比新引擎事件更简单，第一阶段优先兼容旧展示，后续再做新版战报事件 UI。
- 旧 Python `battle_sim` 是否实际存在、是否仍可 import，需要在实施前验证；fallback 不能掩盖 Kotlin 主路径失败。

## 验收标准

- `/api/simulate` 正常响应时 `engine == "stzb-kotlin"`。
- 单次模拟返回可展示日志，并且日志来自 Kotlin 引擎事件/文本适配。
- 多次模拟胜率来自 Kotlin 引擎 repeat 结果。
- 旧网页战斗模拟页面无需大改即可完成单次、100 次和 1000 次模拟。
- 测试覆盖 Kotlin CLI、Python adapter、Flask API。
- Android 相关代码没有被本阶段修改。

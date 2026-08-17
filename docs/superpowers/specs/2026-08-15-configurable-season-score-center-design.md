# 可配置赛季积分中心设计

日期：2026-08-15

## 1. 目标

把现有“自定义积分”从固定公式排行榜升级为可解释、可配置、可回溯的赛季积分中心。

核心能力：

- 综合积分榜；
- 战斗贡献分榜；
- 攻城贡献分榜；
- 可配置规则权重；
- 每位玩家的积分构成；
- 重算预览与确认；
- 手动奖励和扣分；
- 赛季规则版本；
- 历史结果不被新规则覆盖。

## 2. 默认规则

默认预设为“同盟综合贡献”：

```text
综合积分 =
  出战次数 × 1
+ 胜场 × 2
+ 平局 × 0.5
+ 武勋 ÷ 1000
+ 主力打城 × 5
+ 拆迁出勤 × 3
+ 普通出勤 × 1
+ 手动奖励
- 手动扣分
```

三个结果维度：

### 战斗贡献

```text
出战次数 × battleWeight
+ 胜场 × winWeight
+ 平局 × drawWeight
+ 武勋 ÷ gongxunDivisor
```

### 攻城贡献

```text
主力打城 × mainCityWeight
+ 拆迁出勤 × tearWeight
+ 普通出勤 × attendanceWeight
```

### 综合积分

```text
战斗贡献
+ 攻城贡献
+ 手动调整
```

所有权重可配置，但计算结果必须保留原始分量。

## 3. 使用预设

提供三个可复制的预设：

1. `alliance_contribution`：同盟综合贡献，默认；
2. `season_reward`：赛季奖励分配，更重视持续贡献和打城；
3. `siege_priority`：打城排班优先级，更重视出勤、主力和拆迁。

切换预设只修改编辑区，不立即覆盖生效规则。用户必须保存为新规则版本。

## 4. 数据来源

### 战斗数据

来源：`battles_v2`

- 玩家：`atk_name`；
- 玩家 UID：`atk_uid`；
- 玩家同盟：优先 `atk_union`，缺失时回退 `team_users`；
- 出战：有效攻方战报数量；
- 胜场：攻方胜结果集合；
- 平局：非明确胜负结果；
- 武勋：`atk_gongxun`；
- 战斗统计可按时间范围过滤。

禁止继续使用 `def_union` 作为攻方玩家同盟。

### 攻城与出勤

来源：`attendance`

- `role=main`：主力打城；
- `role=tear`：拆迁；
- 其他角色：普通出勤；
- 同一场次、玩家、角色按去重规则计数。

### 手动调整

来源：新增 `score_adjustments`

- 奖励为正数；
- 扣分为负数；
- 必须有原因；
- 记录创建人、创建时间和赛季；
- 不允许直接修改历史计算分量。

## 5. 数据模型

### `score_rule_versions`

```text
id
season_id
version
name
preset_key
config_json
status
created_at
activated_at
```

约束：

- 每个赛季只有一个 active 版本；
- 已被计算结果引用的版本不可原地修改；
- 修改规则会创建新版本。

### `score_adjustments`

```text
id
season_id
player_name
player_uid
points
reason
created_by
created_at
```

### `custom_scores` 扩展

保留旧字段，并新增：

```text
rule_version_id
draws
attendance_cnt
battle_score
siege_score
adjustment_score
breakdown_json
calculated_at
```

旧库通过幂等迁移自动补列。

## 6. 重算流程

重算分成两步：

### 预览

```text
POST /api/custom_scores/preview
```

输入：

- season；
- time range；
- rule config 或 rule version；
- union/group filter。

返回：

- 预计更新玩家数；
- 新旧总积分差异；
- 排名变化；
- 数据缺口；
- 每名玩家的积分构成；
- 不写数据库。

### 确认

```text
POST /api/custom_scores/recalc
```

必须携带：

- 预览 token；
- 对应规则版本；
- 相同过滤条件。

服务端重新校验预览摘要后写入结果，防止页面参数变化导致误算。

## 7. API

```text
GET  /api/custom_scores
GET  /api/custom_scores/<player>
GET  /api/custom_scores/rules
POST /api/custom_scores/rules
POST /api/custom_scores/rules/<id>/activate
POST /api/custom_scores/preview
POST /api/custom_scores/recalc
GET  /api/custom_scores/adjustments
POST /api/custom_scores/adjustments
DELETE /api/custom_scores/adjustments/<id>
```

写接口继续受可选 `STZB_API_TOKEN` 保护。

## 8. 页面结构

### 顶部

- 赛季选择；
- 时间范围；
- 同盟/分组过滤；
- 当前规则版本；
- 规则编辑；
- 预览重算。

### 概览

- 参评人数；
- 综合积分总量；
- 战斗贡献总量；
- 攻城贡献总量；
- 手动调整总量；
- 数据完整性。

### 榜单

使用三个页签：

- 综合榜；
- 战斗榜；
- 攻城榜。

列：

- 排名；
- 玩家；
- 同盟/分组；
- 综合积分；
- 战斗贡献；
- 攻城贡献；
- 手动调整；
- 趋势；
- 更新时间。

### 玩家详情

点击玩家展开：

- 原始指标；
- 每项权重；
- 每项得分；
- 手动调整记录；
- 最近战报和出勤证据；
- 规则版本；
- 数据缺口。

## 9. 规则编辑器

规则编辑器使用数字输入，不允许直接输入公式脚本。

字段：

- battleWeight；
- winWeight；
- drawWeight；
- gongxunDivisor；
- mainCityWeight；
- tearWeight；
- attendanceWeight。

校验：

- 权重必须为有限数值；
- 除数必须大于 0；
- 单项权重范围限制；
- 显示示例玩家的实时公式预览；
- 保存前展示规则差异。

不允许执行任意表达式、SQL 或 JavaScript。

## 10. 数据完整性

积分结果返回：

```text
dataCompleteness
missingSources
sampleSize
ruleVersion
calculatedAt
```

规则：

- 无武勋时武勋分为 0，并标记缺失来源；
- 无 attendance 表时攻城分为 unknown，不伪装成完整 0；
- 玩家缺同盟时显示“未知同盟”；
- 无战报或无出勤的玩家仍可因手动调整进入榜单；
- 排名相同按综合分、战斗分、攻城分、玩家名稳定排序。

## 11. 安全与审计

- 规则创建、激活、手动调整和重算均为写操作；
- 受 Token 保护；
- 记录操作时间与规则版本；
- 删除调整记录只允许删除当前赛季调整；
- 历史赛季结果只读；
- 不提供任意 SQL、表达式执行或文件访问。

## 12. 测试

### 后端

- 公式分量计算；
- 攻方同盟字段正确；
- 胜、负、平结果映射；
- attendance 去重；
- 缺表与缺字段降级；
- 规则版本不可变；
- 单赛季唯一 active 规则；
- 手动调整正负分；
- preview 不写库；
- recalc 需要有效 preview token；
- Token 鉴权；
- 旧库迁移幂等。

### 前端

- 三个榜单切换；
- 规则输入校验；
- 公式实时预览；
- 玩家积分构成；
- 预览排名变化；
- 确认重算；
- 缺失数据提示；
- 375/768/1024/1440 响应式。

## 13. 兼容

- 保留 `GET /api/custom_scores`；
- 旧 `POST /api/custom_scores/recalc` 请求若无预览 token，返回明确 400，不静默使用旧公式；
- 旧 `custom_scores` 数据可显示，但标记为 legacy rule；
- 原有 tab8 继续作为页面入口。

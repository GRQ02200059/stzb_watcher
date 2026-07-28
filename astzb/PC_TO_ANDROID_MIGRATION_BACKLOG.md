# PC -> Android 本机迁移总清单

目标：Android App 不依赖 PC Flask，直接在本机完成抓包、解析、入库、查询和展示。

## 1. 抓包与协议层

- [x] Android `VpnService`
- [x] `hev-socks5-tunnel` 开源桥接
- [x] App 内 SOCKS5 捕获器
- [x] STZB TCP 包头识别
- [x] 明文 / zlib / xor 初步解码
- [x] 十进制消息号显示
- [ ] 真机样本回归：逐消息确认 Android payload 与 PC payload 一致
- [ ] IPv6 / UDP / DNS 行为专项验证

## 2. PC `realtime_writer.py` 消息迁移

- [x] `10`：个人完整战报，已入 `battles_v2 / battle_heroes / wuxun_log / power_log / attendance`
- [x] `92`：同盟完整战报，已入 `battles_v2 / battle_heroes / wuxun_log / power_log / attendance`
- [x] `2100`：战报通知 / 聊天，已入 `battle_notices / chat_messages`
- [x] `5028`：行军监控，已入 `battle_monitor_moves`
- [x] `103`：同盟成员，已入 `team_users`，同时保留 `local_records` 兜底
- [x] `510`：玩家统计，已入 `player_stats`，同时保留 `local_records` 兜底
- [x] `5026`：地图格子，已入 `map_cells`，同时保留 `local_records` 兜底
- [x] `90005`：db_sync，已入 `db_sync`，同时保留 `local_records` 兜底
- [x] `6314`：攻城战场动态，已入 `battle_field`，同时保留 `local_records` 兜底
- [x] `6318`：攻城队列，已入 `battle_queue`，同时保留 `local_records` 兜底
- [x] `301`：玩家行军，已入 `march_events`，同时保留 `local_records` 兜底
- [x] `700`：联盟 / 个人势力排行，已入 `union_list / player_power_rank`，同时保留 `local_records` 兜底
- [x] `780`：公告，已入 `announcements`，同时保留 `local_records` 兜底
- [x] `671`：武将解锁，已入 `hero_unlock_log`，同时保留 `local_records` 兜底
- [x] `21`：玩家自身信息，已入 `player_self`，同时保留 `local_records` 兜底
- [x] `6243`：战区玩家，已入 `zone_players`，同时保留 `local_records` 兜底
- [x] `2200`：战报通知旧格式，已按通知数组兼容解析

## 3. PC API / 看板能力迁移

- [x] `/api/battles_v2`：本机完整战报列表，基础版
- [x] `/api/battles_v2/<id>`：本机战报详情，基础版
- [x] `/api/ranking_v2`：本机玩家/同盟/势力排行，基础版
- [x] `/api/battle_monitor`：本机 5028 行军监控，基础版
- [x] `/api/recent_events` / `/api/msg_history`：本机最近包 / 日志，基础版
- [x] `/api/team_users`：本机 `team_users` 基础列表 / 分组统计
- [x] `/api/player_stats`：本机 `player_stats` 基础列表
- [x] `/api/map_cells` / `/api/map_stats`：本机 `map_cells` 基础列表 / 类型统计
- [x] `/api/battle_field` / `/api/battle_queue`：本机攻城战场 / 队列基础列表
- [x] `/api/union_list` / `/api/union_power_rank`：本机 `union_list / player_power_rank` 基础列表
- [x] `/api/announcements`：本机 `announcements` 基础列表
- [x] `/api/hero_unlock_log`：本机 `hero_unlock_log` 基础列表
- [x] `/api/player_self`：本机 `player_self` 基础详情
- [x] `/api/zone_players`：本机 `zone_players` 基础列表
- [x] `/api/zone_players/stats`：本机战区玩家同盟聚合 / Top 玩家
- [x] `/api/heroes/freq` / `/api/heroes/combos` / `/api/heroes/combo_winrate`：本机武将频率 / 使用 / 组合胜率基础版
- [x] `/api/player_battle_teams` / `/api/player_teams_stats`：本机玩家队伍组合基础统计
- [x] `/api/team_report`：本机团报告分组 / 个人基础统计
- [x] `/api/tasks/*` 攻城任务与考勤：本机任务考勤基础统计
- [x] `/api/state_region_stats`：本机 region / area 聚合统计
- [x] `/api/simulate`：本机通用回合制战斗计算内核，支持多次模拟 / 胜率 / 回合日志
- [x] `/api/simulate/heroes`：本机模拟资源 / 可用武将基础列表

## 4. Android 页面迁移

- [x] 抓包主页面
- [x] 本机数据概览
- [x] 本机战报列表
- [x] 本机战报详情
- [x] 本机排行统计
- [x] 本机行军监控
- [x] 最近包 / 通用业务记录
- [x] 战报筛选：玩家、同盟、战斗类型、结果
- [x] 战报筛选：时间范围、地块 wid
- [x] 阵容统计：武将频率、组合胜率
- [x] 同盟成员页面
- [x] 地图格子 / 城池基础页面
- [x] 攻城战场动态 / 攻城队列页面
- [x] 玩家资料页面：`21 / 510`
- [x] 公告 / 武将解锁页面：`780 / 671`
- [x] 战区玩家页面
- [x] 任务考勤页面
- [x] 团数据报告页面
- [x] 战斗模拟页面：本机模拟资源 / 多次模拟结果 / 首场回合日志

## 5. 数据资源迁移

- [x] `herocfg.json` 内置到 APK assets
- [x] 武将 ID -> 名称解析
- [x] 技能配置 `skillcfg.json`
- [x] 队伍/武将组合静态资源
- [x] 地图/州郡静态资源：以本机 region / area 聚合统计覆盖基础能力

## 当前执行顺序

1. [x] 战报筛选能力
2. [x] `103` 同盟成员专表
3. [x] `5026` 地图格子专表
4. [x] `700` 排行专表
5. [x] `510 / 780 / 671 / 21 / 6243` 轻量消息专表与基础页面
6. [x] `90005` db_sync 专表
7. [x] `6314 / 6318 / 301` 攻城与行军专表
8. [x] 阵容统计
9. [x] 任务考勤
10. [x] 完整战斗模拟通用内核迁移

备注：原 JS 模拟器包含大量按战法 ID 手写的专属效果。Android 当前已迁移可运行的本机通用回合制内核，覆盖武将属性、兵力、速度排序、普攻、主动/追击/被动/指挥战法的通用效果、多次模拟与日志；若后续需要和 JS 对每个战法逐条完全一致，可继续按战法 ID 精修专属效果。

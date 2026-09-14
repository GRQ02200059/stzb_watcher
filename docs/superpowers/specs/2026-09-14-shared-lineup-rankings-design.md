# 全用户共享战报阵容榜设计

日期：2026-09-14

状态：已确认

## 目标

基于所有 Android 用户上传的战报提供三张匿名共享榜单：热门阵容榜、综合胜率榜、克制关系榜。第一阶段对应用户选择的全服热门阵容、综合胜率和克制关系榜，不做自动配将，也不替代现有战斗模拟器。

## 唯一事实源

统计只使用 battles_v2 主记录。线上事实源是 battle_reports.payload_json，它保存 Android 上传后的 battles_v2 结构化字段。榜单计算不得读取 battle_report_heroes 或 battle_report_skills。

阵容按以下优先级提取：

1. atk_hero1_id 到 atk_hero3_id，以及 def_hero1_id 到 def_hero3_id；
2. 显式字段不存在时，解析 attack_all_hero_info 和 defend_all_hero_info，每段第一项为武将 ID；
3. 任一侧不足三名非零且互不重复的武将，该战报不进入榜单。

all_skill_info 暂不参与第一阶段阵容键。阵容位置保持不变，A+B+C 与 C+B+A 是不同阵容。

## 本机种子样本

使用 /Users/bytedance/stzb_watcher/stzb_45.253.243.238.db 的 battles_v2 作为首批匿名样本。

审计结果：总战报 244 条；排除 NPC 等记录后 130 条；完整三对三 80 条；三人阵容 69 种；至少出现 2 次的阵容 37 种；至少出现 5 次的阵容 15 种；对阵组合 76 种，但重复至少 2 次的只有 4 种。

这批数据足够验证热门榜和保守胜率榜。克制榜必须显示低样本提示。

导入服务器时只保存匿名事实：指纹、时间、结果、攻守阵容 ID、战斗类型和夜战标记。不得保存玩家名、UID、同盟、坐标或原始战报正文。

## 有效样本与去重

有效战报必须同时满足：非 NPC；result 不等于 6；攻守双方完整三人；每侧武将 ID 大于 0 且互不重复；结果可映射为胜、负或平。

结果口径：攻方胜为 1、7、11；守方胜为 2、12；平局为 0、10；其他结果不计入胜率。

跨用户重复上传使用 SHA-256(schema-version | battle-id | battle-time | attacker-lineup | defender-lineup) 去重。线上事实与本机种子出现相同指纹时只计一次，线上事实优先。

## 排名算法

热门榜按样本数、最近出现时间排序，展示样本数、胜平负、使用占比和可信度。

原始胜率为 (胜 + 0.5 × 平) / 总场次。综合胜率榜最低 3 场，使用 95% Wilson 下界排序，避免一场一胜冲到榜首；同时展示原始胜率。

克制榜按有方向的我方阵容与对手阵容聚合。至少 2 场才入榜；低于 5 场标记低可信；原始胜率不高于 50% 不进入优势克制榜；按 Wilson 下界、样本数、原始胜率排序。

可信度：1-2 场为样本不足，3-9 场为低，10-29 场为中，30 场及以上为高。所有百分比必须同时显示样本数。

## 服务端

新增 lineup_rankings 模块：LineupFactExtractor 从 battles_v2 字段生成匿名事实；LineupSeedRepository 保存本机种子；LineupRankingService 合并线上 payload_json 与种子并去重；POST /v1/lineup-rankings/query 返回三张榜。

新增 lineup_seed_facts 表，字段包括 fingerprint、battle_id、battle_time、result、fight_type、in_night、attacker_key、attacker_hero1 到 3、defender_key、defender_hero1 到 3、imported_at。该表不含用户身份。

榜单按请求实时计算并使用最多 5 分钟的进程内缓存。种子只允许通过 SSH 管理 CLI 导入，公网 API 不提供导入能力。

查询 API 使用现有 session、账号状态、服务开关和最低版本 1.2.0 门禁。请求 limit 限制为 1 到 100。响应只包含武将 ID 和匿名聚合，不包含玩家、UID、同盟、战报 ID、坐标或正文。

## Android

升级现有阵容战法研究页面为全服阵容榜，保持 research 路由。页面包含共享样本摘要、热门/胜率/克制切换、武将搜索、可信度、刷新和带入模拟器。

页面使用加密 session 请求服务器，客户端用 APK 内置 HeroNameResolver 将武将 ID 显示为中文。网络失败显示共享榜暂不可用，库为空显示正在收集样本。不下载原始共享战报到本地数据库。

## 验收

服务端覆盖字段提取、过滤、去重、攻守结果、Wilson 下界、可信度、排序、鉴权和隐私测试；种子导入必须幂等。Android 覆盖 JSON 解析、三榜切换、空状态、低可信提示、搜索、中文武将名和模拟器回调。

完成条件：80 条本机完整三对三样本匿名导入；线上 API 返回三张榜；Android 真机可查看并带入模拟器；后续用户上传的 battles_v2 自动进入榜单；现有认证、上传和 App 功能回归通过。

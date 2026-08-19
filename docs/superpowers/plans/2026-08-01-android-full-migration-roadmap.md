# ASTZB Android Full Migration Roadmap

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement each linked plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将网页端全部有效业务能力迁移到 Android 本机，并用现代深色 Jetpack Compose 界面替换现有超大 XML 页面。

**Architecture:** 保留现有 VPN、SOCKS、协议解析、SQLite 和模拟器实现，在其上增加按领域划分的 Repository、不可变领域模型、ViewModel 和 Compose 页面。迁移按四个可独立验收的批次进行，只有新页面真机验证通过后才删除旧页面。

**Tech Stack:** Kotlin 2.0.21、Android SDK 35、Jetpack Compose、Material 3、Navigation Compose、Lifecycle ViewModel、Kotlin Coroutines/Flow、SQLiteOpenHelper、JUnit 4、Compose UI Test。

## Global Constraints

- 最低系统版本固定为 Android 13（minSdk 33），targetSdk 35。
- 默认首页固定为“实时战场动态”。
- 一级导航固定为战场、战报、同盟、更多四项。
- 视觉使用深蓝黑背景、琥珀主色、青绿成功色和朱红错误色。
- 保留现有 VPN、tun2socks、本机 SOCKS、协议解析、SQLite schema 和模拟器实现。
- 新页面不得直接访问 Cursor、表名或原始消息号。
- 新代码放入 `com.local.stzb`；旧 `com.example.myapplication` 通过适配器渐进接入。
- 新旧页面共存期间，旧功能必须仍可进入且可回退。
- 点击区域不小于 48dp；正文不小于 14sp；状态不能只通过颜色表达。
- 每批必须通过单元测试、Compose UI 测试、Debug 构建和真机核心链路验证。

---

## 迁移顺序

## 当前进度（2026-08-18）

- 已完成：Compose 应用壳、实时战场、本机战报列表/筛选/详情。
- 已完成：同盟成员与分组、地图与城池、游戏公告。
- 已完成：战功榜、同盟势力榜、个人势力榜，以及分组/成员团队报表（全部/今日/本周、分组筛选）。
- 已完成：攻城任务与考勤、玩家队伍、模拟器、真实抓包六阶段证据、抓包诊断导出。
- 已完成：多账号/区服独立数据库、实时部队、自定义积分、阵容研究与模拟器预填。
- 待完成：Android 13/14/15 物理设备矩阵验收、经典页剩余州郡/战区/诊断深页迁移与退役。
- “经典数据页面”只承载尚未迁移的长尾工具；上述核心与高级能力均可从 Compose 工具中心直接进入。

### 批次 1：Compose 基础壳与实时战场

详细计划：[2026-08-01-android-batch1-battlefield-compose.md](./2026-08-01-android-batch1-battlefield-compose.md)

交付物：

- Compose、Material 3、Navigation、ViewModel 和测试基础设施；
- 四入口应用壳；
- 现代深色设计系统；
- 战场领域模型和 `BattlefieldRepository` 适配器；
- 默认实时战场首页；
- 可暂停、可筛选且不会抢动阅读位置的事件 Feed；
- 行军、攻城、地图摘要；
- 旧抓包控制台和旧 Dashboard 的兼容入口。

进入下一批的门槛：

- `./gradlew testDebugUnitTest connectedDebugAndroidTest :app:assembleDebug` 通过；
- 真机能授权 VPN、启动桥接并在新首页看到事件；
- 暂停 Feed、恢复 Feed、切换一级导航和返回行为正确；
- 旧页面仍能打开。

### 批次 2：战报、地图和消息

计划文件在批次 1 接口验收后建立，范围固定为：

- `BattleRepository` 与战报分页、筛选模型；
- 战报列表、筛选 Bottom Sheet、战报详情；
- 战场消息统一列表；
- 城池/地块列表、坐标搜索、州郡摘要；
- PC 与 Android 战报黄金样本回归；
- 替换旧 `BattleDetailActivity` 和对应 Dashboard 页面。

进入下一批的门槛：

- 战报列表、筛选和详情与本机数据库口径一致；
- 空库、大数据量和坏数据均有明确状态；
- 地图与消息无需进入旧 Dashboard；
- 关键战报 fixture 测试通过。

### 批次 3：同盟与分析

计划文件在批次 2 接口验收后建立，范围固定为：

- `AllianceRepository`；
- 同盟成员、分组和成员详情；
- 团数据、分组武勋和 CSV 分享；
- 玩家/同盟/势力排行；
- 攻城任务、任务详情和考勤；
- 玩家队伍、武将频率、组合胜率和战场分析；
- 替换旧 Dashboard 对应页面。

进入下一批的门槛：

- 网页端同盟与统计能力均能在 Android 两次点击内到达；
- 统计口径有固定 SQLite fixture；
- 创建、刷新、导出和删除攻城任务均有测试；
- 大成员列表滚动无明显卡顿。

### 批次 4：工具、模拟器与退役旧 UI

计划文件在批次 3 接口验收后建立，范围固定为：

- `ToolRepository`；
- 本机战斗模拟器 Compose 页面；
- 玩家资料、战区玩家、公告和武将解锁；
- DB Sync、最近包、业务记录、导出和诊断；
- 抓包控制、账号与区服设置；
- Release 构建与长期运行验证；
- 移除已替代的 XML、`DashboardActivity` 和旧 Adapter；
- 最终整理 namespace、文档和发布说明。

完成门槛：

- 设计规格的能力迁移矩阵全部关闭；
- App 不依赖 PC Flask；
- `DashboardActivity` 不再渲染业务页面；
- Debug/Release、单元测试和 UI 测试通过；
- Android 13/14/15 真机验证通过；
- README、截图、迁移清单和实际实现一致。

## 每批共同执行规则

1. 从失败测试开始建立新接口。
2. 用旧 `LocalStzbRepository` 做首个 Adapter，不在 UI 任务中顺便重写 SQL。
3. 每个任务形成独立、可审查的提交。
4. 不把未完成入口伪装成可用页面；兼容入口必须明确标记“经典页面”。
5. 删除旧代码前，先执行新旧口径对比和真机回归。
6. 每批结束更新本路线图、迁移矩阵和用户文档。

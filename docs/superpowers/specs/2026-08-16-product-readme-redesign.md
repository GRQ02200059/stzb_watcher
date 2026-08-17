# stzb_watcher 产品型 README 重构设计

日期：2026-08-16  
状态：已确认，待实施

## 目标

将仓库 README 从偏开发者的长篇技术说明，重构为面向普通用户的双端产品主页。
读者应在一分钟内理解：

1. 这是什么产品；
2. Web 与 Android 如何提供一致的完整业务能力；
3. 能解决哪些游戏数据与战场决策问题；
4. 两个端的实际界面是什么样；
5. 如何启动 Web 或构建 Android；
6. 去哪里查看进阶技术资料。

README 不再承担完整架构文档、API 字典和 HUD 实现规范的职责。这些内容继续保留
在 `docs/`、源码和测试中。

## 内容策略

### 主要受众

- 想了解项目能力的普通用户；
- 希望使用 Web 或 Android 完整能力的个人使用者；
- 从 GitHub 首页判断项目成熟度的访问者。

开发者信息保留为末尾的精简章节，不占据首屏和主要阅读路径。

### 文案风格

- 使用中文；
- 先展示结果，再解释实现；
- 避免内部类名、请求 revision、loader 生命周期等实现细节；
- 每段不超过四行，优先使用短列表和卡片式表格；
- 不使用 Emoji；
- 不夸大未经验证的协议、概率或模拟准确度。

## 页面结构

README 目标长度约 200–280 行，按以下顺序组织：

1. **产品首屏**
   - 项目名称；
   - 一句话定位为 Web + Android 双端战场数据平台；
   - Python、Flask、Vanilla JS、Android、Kotlin、测试状态等简洁徽章；
   - 快速链接：双端预览、核心功能、快速开始、项目结构。

2. **双端定位**
   - Web 与 Android 使用一致的数据模型、业务口径和战斗模拟语义；
   - 两端均覆盖战场、战报、队伍、团队、分析与模拟等完整业务能力；
   - 差异只在交互载体和运行方式，不将 Android 描述为 PoC、精简端或兼容端。

3. **Web 全景主图预留位**
   - 使用“战场情报”页面；
   - README 先展示不会产生破图的文字占位卡；
   - 占位卡标明固定文件名与建议画面，用户上传后再启用图片标签。

4. **核心价值**
   - 战场态势统一；
   - 实时部队与行军；
   - 阵容研究与模拟；
   - 团队管理与赛季积分。

5. **Web 六图功能画廊预留位**
   - 实时部队；
   - 战斗模拟；
   - 阵容战法研究；
   - 自定义积分；
   - 打城考勤；
   - 玩家队伍。
   - 使用两列表格，每个位置标明固定文件名与一句用户价值说明；
   - 未上传图片时不得出现破图。

6. **Android 三图画廊预留位**
   - 战场总览；
   - 队伍与团队；
   - 战斗模拟；
   - 三个竖屏手机截图位置横排，使用 HTML table 保证 GitHub 展示稳定；
   - 每个位置标明固定文件名，并说明与 Web 端一致的业务能力；
   - 未上传图片时不得出现破图。

7. **双端能力矩阵**
   - 按“战场、战报、队伍、团队、分析、模拟、系统”归类；
   - Web 与 Android 均标记支持；
   - 说明 Web 侧重宽屏指挥密度，Android 侧重移动交互和本机运行；
   - 不使用“部分迁移”“功能简化”“经典页面兼容”等旧定位。

8. **功能总览**
   - 将 12 个主导航页面按“情报、行动、组织、分析、系统”五个视觉域归类；
   - 不展开每个 API 或内部状态机。

9. **双端快速开始**
   - Web：安装 Python 依赖、构建 Kotlin 战斗引擎、启动 `api_server.py`；
   - Android：检查 SDK/NDK、执行 `astzb/gradlew :app:assembleDebug`、安装 APK；
   - 分别说明 Web 访问地址和 Android APK 产物路径；
   - 补充 Python、Java、Chrome、Android SDK 与 Android 版本要求。

10. **数据与隐私**
   - 说明抓包、SQLite、运行态账号文件的本地存储位置；
   - 说明 Android 本机抓包与本地 SQLite 存储；
   - 说明 `current_profile.json`、`profiles.json`、数据库和抓包文件不会提交；
   - 提醒公开部署时配置写接口 Token。

11. **精简技术说明**
   - 用一张 Mermaid 图展示共享业务语义与双端运行链路；
   - Web 链路：Capture → Parse → SQLite → API/SSE → Dashboard；
   - Android 链路：VpnService → Local Parser → SQLite → Compose；
   - 用短表格列出 Web、Android、实时链路与共享战斗引擎；
   - 链接到 `docs/superpowers/specs/` 和 `docs/research/`。

12. **测试与许可**
   - 提供 Node、Python 与 Android Gradle 验证命令；
   - 保留项目用途和授权提示；
   - 保留作者联系方式，但降低视觉权重。

## 截图方案

### 图片清单

截图统一保存到：

`docs/assets/screenshots/`

文件名固定为：

- `overview-intelligence.webp`：全景主图，战场情报；
- `gallery-live-army.webp`：实时部队；
- `gallery-simulator.webp`：战斗模拟；
- `gallery-research.webp`：阵容战法研究；
- `gallery-score.webp`：自定义积分；
- `gallery-attendance.webp`：打城考勤；
- `gallery-player-teams.webp`：玩家队伍。
- `android-battlefield.webp`：Android 战场总览；
- `android-teams.webp`：Android 队伍与团队；
- `android-simulator.webp`：Android 战斗模拟。

### 截图与上传约定

- Web 端七张截图由本地真实页面采集，统一保存为 WebP 并在 README 中启用；
- Android 端三张截图由本地模拟器真实页面采集，统一保存为 WebP 并在 README 中启用；
- 十张截图全部使用仓库相对路径，不依赖外部图床；
- `docs/assets/screenshots/README.md` 记录十个固定文件名、建议画面和启用步骤；
- 上传前后都不得要求本机绝对路径或外部图床；
- 不创建示意图、GIF、视频或替代图片资产。

### 建议画面

- `overview-intelligence.webp`：战场情报地图、主要图层和情报区；
- `gallery-live-army.webp`：实时部队三栏指挥台；
- `gallery-simulator.webp`：战斗模拟双方阵容与执行区；
- `gallery-research.webp`：阵容战法研究工作台；
- `gallery-score.webp`：自定义积分榜单、筛选和规则入口；
- `gallery-attendance.webp`：打城考勤任务阶段和成员区；
- `gallery-player-teams.webp`：玩家队伍列表和主要操作区；
- `android-battlefield.webp`：Android 战场动态、筛选和主要状态；
- `android-teams.webp`：Android 队伍或团队列表与统计；
- `android-simulator.webp`：Android 双方阵容、配置入口和执行区。

## README 保留与删除

### 保留

- 项目定位；
- Web 与 Android 双端一致能力说明；
- 核心功能；
- Web 与 Android 快速启动；
- 数据目录和 Token 说明；
- 标准测试命令；
- 精简项目结构；
- 技术资料入口；
- 联系方式与许可说明。

### 移出 README

- HUD 事件白名单的逐项实现说明；
- Surface token 的完整规范；
- loader、revision、Abort 和竞态处理细节；
- 全部 API endpoint 字典；
- 画像、卡包、研究快照和战斗引擎同步的长篇命令说明；
- 已迁移或仅供历史参考的旧页面说明。
- 将 Android 描述为 PoC、精简版、部分迁移或 Web 附属端的旧表述。

这些内容已经存在于设计文档、计划、源码和测试中，不需要在产品首页重复。

## 验收标准

1. README 首屏明确展示 Web + Android 双端完整产品定位；
2. Web 与 Android 被描述为业务能力对等，不出现主端/精简端关系；
3. README 启用七张 Web 截图和三张 Android 截图，全部路径有效且不出现破图；
4. Web 六图画廊在 GitHub 桌面端保持两列，在窄屏自然纵向排列；
5. Android 三图占位画廊在桌面端横排，在窄屏保持可读；
6. 双端能力矩阵中的核心能力均标记 Web 与 Android 支持；
7. README 中的双端启动命令可以从干净环境理解并执行；
8. 不引用已经删除的 `dashboard/` 原型或根目录旧导出 JSON；
9. 不包含本机绝对路径作为用户必需配置；
10. 可见 Markdown 链接与十张图片全部指向仓库内存在的文件；
11. `docs/assets/screenshots/README.md` 完整记录上传与启用步骤；
12. Node、Python 与 Android 验证继续通过；
13. `git diff --check` 通过。

## 非目标

- 不修改 Web 或 Android 产品 UI；
- 不新增功能或 API；
- 不修改真实数据库；
- 不重新设计 Logo；
- 不创建动态图、视频或外部图床；
- 不重写 Git 历史；
- 不在本任务中删除旧 Vue 模拟器或合并重复配置快照；
- 不把 Android 描述为 Web 的精简版或后续迁移目标。

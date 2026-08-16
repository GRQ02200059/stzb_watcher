# STZB Web 战场指挥中心全量完善实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 Flask + Vanilla JS Dashboard 完善为功能可达、交互统一、具有总览、预警、时间线、收藏、设置和命令面板的 Web 战场指挥中心。

**Architecture:** 保留所有旧 Tab、DOM ID 和 API，通过新增只读聚合接口及独立的 `dashboard-command-center.js` 渐进增强。总览、偏好与全局交互不侵入 `app2.js` 业务函数，设计系统继续由现有 CSS/JS 覆盖层承载。

**Tech Stack:** Python 3、Flask、SQLite、Vanilla JavaScript、CSS、SSE、unittest

## Global Constraints

- Web 优先，Android 本轮不扩展。
- 保留现有 Tab 索引、接口和业务行为。
- 所有新增后端能力只读。
- 禁止游戏动作执行、主动发包和任意数据库写入。
- 新功能必须在缺失可选数据库表时优雅降级。
- 新生产行为必须先有失败测试。

---

### Task 1: 只读总览聚合接口

**Files:**
- Modify: `api_server.py`
- Create: `test/test_command_center_api.py`

**Interfaces:**
- Consumes: `get_db() -> sqlite3.Connection`
- Produces: `GET /api/command-center/overview`，返回 `ok/profile/metrics/battles/armies/alerts/freshness`

- [ ] 写 Flask test client 失败测试，使用临时 SQLite 创建 `battles_v2`、`team_users`、`world_armies`、`world_tiles`。
- [ ] 运行 `python -m unittest test.test_command_center_api -v`，确认因路由不存在失败。
- [ ] 实现独立安全查询辅助函数与路由，每项查询单独捕获 `sqlite3.OperationalError`。
- [ ] 增加缺表库测试，确认稳定返回默认 schema。
- [ ] 运行测试确认通过。

### Task 2: 总览、设置与完整功能导航

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/app1.js`
- Create: `test/test_command_center_static.py`

**Interfaces:**
- Produces: `tab31` 总览、`tab32` 设置、`switchTab(31)` 默认入口、所有既有业务 Tab 可达

- [ ] 写静态契约失败测试，断言新 Tab、总览挂载点、设置控件和新增脚本存在。
- [ ] 运行测试确认失败。
- [ ] 在 Dashboard 增加总览和设置语义 DOM。
- [ ] 将当前隐藏但有实现的 Tab 纳入增强导航数据，不删除旧按钮。
- [ ] 修改默认 Tab 为总览，并在切换时调用 `loadCommandCenterOverview()` / `loadCommandCenterSettings()`。
- [ ] 运行静态测试确认通过。

### Task 3: 命令面板、预警、时间线、收藏与偏好

**Files:**
- Create: `static/dashboard-command-center.js`
- Modify: `static/dashboard-design-system.js`
- Modify: `test/test_command_center_static.py`

**Interfaces:**
- Produces: `CommandCenter` 全局对象
- Produces: `loadCommandCenterOverview()`, `loadCommandCenterSettings()`
- Consumes: `/api/command-center/overview`, `/api/stream`, `switchTab(index)`

- [ ] 扩充失败测试，断言命令面板快捷键、localStorage 偏好、事件缓冲、预警规则和收藏 API。
- [ ] 运行测试确认失败。
- [ ] 实现总览数据加载、KPI、战报、行军、预警和快捷入口渲染。
- [ ] 实现 `Ctrl/Cmd+K` 命令面板、搜索、键盘选择和跳转。
- [ ] 实现 SSE 时间线暂停、缓冲、筛选和查看最新。
- [ ] 实现 WID/玩家/队伍/战报收藏及本地持久化。
- [ ] 实现设置即时生效、持久化和重置。
- [ ] 运行静态与 Query Agent/世界场景静态测试。

### Task 4: 炫酷但克制的指挥台视觉

**Files:**
- Modify: `static/dashboard-design-system.css`
- Modify: `test/test_command_center_static.py`

**Interfaces:**
- Consumes: Task 2/3 新增的 `cc-*` class 和 dialog
- Produces: 桌面/平板/手机布局、reduced-motion、骨架屏、数字动画、动态背景

- [ ] 写失败测试覆盖 command palette、overview grid、timeline、alerts、settings、responsive 和 reduced motion 选择器。
- [ ] 运行测试确认失败。
- [ ] 实现总览 12 列布局、情报栏、渐变边缘、动态网格和状态呼吸点。
- [ ] 实现命令面板、时间线、告警、收藏和设置组件。
- [ ] 实现 1280/1024/768/480 响应式降级以及 reduced motion。
- [ ] 运行静态回归测试。

### Task 5: 全量验证和完成审计

**Files:**
- Verify: `test/`
- Verify: `static/`
- Verify: `api_server.py`

**Interfaces:**
- Produces: 需求到证据的最终验收清单

- [ ] 运行 `.venv/bin/python -m unittest discover -s test -v`。
- [ ] 启动无抓包测试服务器并检查 `/` 与 `/api/command-center/overview`。
- [ ] 使用浏览器检查 1440、1024、768、375 宽度和核心交互。
- [ ] 核对每个显式要求均有对应页面、代码、测试或浏览器证据。
- [ ] 检查 `git diff --check` 与最终 diff，确认未覆盖用户既有改动。

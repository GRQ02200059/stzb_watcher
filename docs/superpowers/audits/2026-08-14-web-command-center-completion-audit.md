# STZB Web 战场指挥中心完成审计

日期：2026-08-14
审计目标：Web 优先，补齐仓库现有未完成能力，提升交互与视觉，并增加有价值的创意功能。

## 1. 验收标准

1. 现有 Web 业务模块全部可达，没有“加载函数存在但页面/入口不存在”的死链。
2. Web 首页具备可用的战场总览，而不是只展示旧表格集合。
3. 交互具备统一导航、命令检索、反馈、实时更新、设置和响应式行为。
4. 创意增强至少包含可解释预警、实时事件时间线、关注对象和自然语言战术检索。
5. 正确性与运行效率问题得到修复：资源缓存、当地日界、SSE 重连、请求风暴、导入副作用和安全转义。
6. 交付有自动化 API/行为测试和真实浏览器 E2E，不只依赖静态字符串断言。
7. Web 在外部字体/CDN 不可用时仍能渲染，并可使用 Excel/PDF 导出依赖。

## 2. Prompt 到 Artifact 清单

| 用户要求 / 评审要求 | Artifact | 验证证据 | 状态 |
|---|---|---|---|
| Web 优先 | `static/dashboard.html`、`static/dashboard-command-center.js` | Chrome E2E 以 Web 为主目标 | 完成 |
| 未实现功能全部补齐 | 32 个 `tab` 与 32 个导航入口；新增 `tab18`、`tab31`、`tab32` | HTML 审计断言页面与按钮集合一致；Chrome 逐页访问 32 个 Tab | 完成 |
| 战场总览 | `GET /api/command-center/overview`、`tab31` | `test_command_center_api.py`；真实 HTTP 探针 | 完成 |
| 同盟势力死链 | `tab18`、`loadUnionList()`、`loadUnionPowerRank()` | 静态契约与 Chrome Tab 遍历 | 完成 |
| 世界场景 | `world_scene/`、`static/world_scene.js`、`tab30` | parser/store/API/writer 测试；Chrome 子视图交互 | 完成 |
| Query Agent | `query_agent/`、Query Agent 面板 | API/service/context/LLM 测试；Chrome 实际查询 | 完成 |
| 更优秀、更炫酷交互 | `dashboard-design-system.css/js` | 响应式/可访问性契约；Chrome 1440px 与 375px E2E | 完成 |
| 全局命令面板 | `Ctrl/Cmd+K`、`cc-command-dialog` | Chrome 搜索“设置”并执行跳转 | 完成 |
| 创意功能：战术预警 | `_command_center_alerts()`、`cc-alert-list` | 集结、到达、writer 错误、数据陈旧测试 | 完成 |
| 创意功能：实时事件时间线 | `cc-timeline-list`、暂停/缓冲/恢复 | 静态契约、共享 SSE 分发 | 完成 |
| 创意功能：关注对象 | `addFavorite()`、`cc-favorites-list` | localStorage 契约与 UI 实现 | 完成 |
| 设置中心 | `tab32`、刷新/密度/动效/声音/首页/情报栏/Token | Chrome 设置持久化与 Token 会话测试 | 完成 |
| 自动资源版本 | `api_server._asset_version()` | mtime 注入测试覆盖 `.mjs/.js/.css` | 完成 |
| “今日”当地时区 | `_local_day_start_timestamp()` | 00:01/23:59 边界 API 测试 | 完成 |
| SSE 请求风暴 | 指数退避、`refreshActivePage()`、单一 ticker | Node 行为测试、静态硬化测试 | 完成 |
| 重复 SSE | 世界场景与命令中心复用 `stzb:stream-event` | 断言仅主连接创建 EventSource | 完成 |
| 模块导入副作用 | `start_runtime_services()` | 子进程纯导入仅有 `MainThread` | 完成 |
| 数据映射重复 | `static/dashboard-meta.js` | app1/app2/world_scene 共用元数据测试 | 完成 |
| app2 重复声明 | 第二份重复模块隔离出运行时代码 | 运行时代码重复函数审计为 0；Chrome E2E | 完成 |
| CSS 多层覆盖 | 旧 Modern Override 隔离；语义 token 由外部设计系统所有 | CSS 结构测试；Chrome E2E | 完成 |
| HTML 注入点 | `normalizeEventText()` / `esc()` | Node 恶意标记转义测试 | 完成 |
| 可选写接口鉴权 | `STZB_API_TOKEN`、设置中心 Token | 未授权 401、授权 200；只读 POST 不受影响 | 完成 |
| ARIA 多 main | 页面 `role=region` + `aria-hidden` | 可访问性回归测试 | 完成 |
| 外部依赖 | `static/vendor/` + LICENSE + SHA-256 | vendor 测试；外网请求阻断下 Chrome E2E | 完成 |
| 文档 | `README.md`、设计、实施计划、本审计 | 路径与命令核对 | 完成 |

## 3. 验证命令

```bash
.venv/bin/python -m unittest discover -s test -v
node --check static/dashboard-runtime.mjs
node --check static/dashboard-meta.js
node --check static/dashboard-command-center.js
node --check static/dashboard-design-system.js
node --check static/app1.js
node --check static/app2.js
node --check static/world_scene.js
.venv/bin/python -m py_compile api_server.py
git diff --check
```

Python discovery 同时执行：

- Node `node:test` 运行时行为测试；
- 系统 Chrome + Playwright 的真实 Dashboard E2E；
- 32 个 Tab 遍历、Query Agent、世界场景、设置、Token 与移动端溢出检查。

## 4. 范围边界

- 本轮不实现真实游戏动作、主动发包或无人值守控制。
- Android 不是本轮优先端，未把 Android 产品化 TODO 计入 Web 完成标准。
- 未执行 `git commit`：仓库已有大量用户未提交修改，且用户未明确授权提交。
- `app2.js` 仍是较大的兼容业务文件，但运行时代码已无重复顶层函数；进一步按业务域拆包属于后续可维护性重构，不是缺失功能。

## 5. 审计结论

上述七项验收标准均有代码、自动化测试和真实浏览器证据覆盖。未发现未实现、弱验证或仅凭代理信号判定完成的 Web 需求。

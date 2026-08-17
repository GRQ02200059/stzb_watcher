# Task 5 Report: Operations 域两页面

状态：DONE

## 修改文件

- `static/operations-hud.css`
  - 统一阶段节点的语义状态为 `data-state="active"`，并保留既有 `is-active` 兼容样式。
  - 完成阶段继续使用 success 色阶。
- `static/simulator-workbench.css`
  - 控制工具栏、结果摘要、回放详情使用 `var(--surface-overlay)`。
  - 武将/战法库抽屉与模板对话框使用 `var(--surface-modal)`。
  - 删除抽屉/对话框原有局部 `box-shadow`，由 Task 3 的 `hud-surface-modal` 共享类提供层级阴影。
  - 保留原有画像、扫描、fallback、响应式和 reduced-motion 规则。
- `static/simulator-workbench.js`
  - 新增纯函数 `simulationCompletionEvent(response, repeat, sourceContext)`。
  - 成功模拟后通过 `window.HudSystem?.emit` 发出 `simulation:completed`，按未支持战法效果选择 success/warning。
  - 删除旧视觉事件 `stzb:hud-pulse`。
  - 保留 `stzb:simulation-completed` 数据事件及其完整研究证据 payload。
  - 为控制工具栏、结果摘要、回放详情、库抽屉和模板对话框挂载 Task 3 共享 Overlay/Modal 类。
- `static/app2.js`
  - 增加前后任务阶段快照。
  - 初始成功快照只建基线，不发事件。
  - 仅当上一次快照中已存在的 task 发生真实 stage 变化时发出 `operation:stage-changed`。
  - task 行增加稳定的 `data-task-id` 事件目标。
  - `attendanceStage`、`operationStageStrip`、`loadTasks` 各保持单一活动定义。
- `test/test_operations_hud_static.py`
  - 增加 stage-changed、初始渲染守卫、existing-task 比较、HudSystem 和 active selector 契约。
- `test/test_battle_simulator_static.py`
  - 增加 Overlay/Modal tokens、语义完成事件、HudSystem、旧 pulse 移除和研究事件保留契约。
- `test/js/simulator-workbench.test.mjs`
  - 增加 completion helper 的 success 与 unsupported-effect warning 测试。

## RED

先修改测试，再运行 brief 指定 Python 命令：

```text
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_operations_hud_static \
  test.test_battle_simulator_static -v

Ran 13 tests
FAILED (failures=4)
```

四个预期失败分别为：

- 缺少 `operation:stage-changed` 与前后阶段快照比较。
- 缺少 `.operation-stage[data-state="active"]`。
- 仍存在 `stzb:hud-pulse`，且未使用 `HudSystem?.emit`。
- simulator CSS 尚未使用 `var(--surface-overlay)` / `var(--surface-modal)`。

再运行 Node 命令：

```text
node --test \
  test/js/simulator-workbench.test.mjs \
  test/js/simulator-analysis.test.mjs

SyntaxError: simulator-workbench.js does not provide an export named
simulationCompletionEvent
```

失败均由 Task 5 功能缺失导致，不是测试拼写或环境错误。

## GREEN

brief 指定 Node 命令：

```text
node --test \
  test/js/simulator-workbench.test.mjs \
  test/js/simulator-analysis.test.mjs

20 tests, 20 pass, 0 fail
```

brief 指定 Python 命令：

```text
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_operations_hud_static \
  test.test_battle_simulator_static -v

Ran 13 tests
OK
```

补充验证：

- `node --check static/simulator-workbench.js`：通过。
- `node --check static/app2.js`：通过。
- Task 5 文件 `git diff --check`：通过。
- `curl http://127.0.0.1:8080/`：HTTP 200。

## 自审

- 模拟成功事件：
  - helper 只返回 brief 规定的纯事件字段。
  - runtime 补充 message 与 timestamp。
  - `response.firstRun.diagnostics.unsupportedSkillEffects` 和 `response.result.replay.diagnostics.unsupportedSkillEffects` 任一路径有内容即 warning。
  - `dedupeKey` 使用 repeat 与 lineup key，缺省为 `manual`。
- 研究兼容：
  - `stzb:simulation-completed` 未删除、未改名，payload 中 response、sourceContext、engine、repeat 和 notice 均保留。
  - 只删除旧视觉 `stzb:hud-pulse`。
- 阶段事件：
  - `hasRenderedTaskStages` 防止初始渲染发事件。
  - `previousTaskStages.has(task.id)` 防止新 task 首次出现时发事件。
  - `previousStage !== stage.key` 防止轮询同一 stage 重复发事件。
  - 非数组/失败响应不覆盖成功阶段基线。
  - 空成功快照会更新基线，因此删除后重新出现的 task 视为新 task，不误发 stage change。
- Overlay：
  - 五个指定表面均挂载共享 `hud-surface-overlay` / `hud-surface-modal` 类。
  - CSS 同时使用共享 Surface token，局部 drawer/modal shadow 已移除。
- 重复函数：
  - 当前活动运行代码的函数名重复扫描结果为空。
  - Task 5 相关 `attendanceStage`、`operationStageStrip`、`loadTasks` 各仅一份。
  - 文件尾部前序成果保留的注释历史模块不参与运行，本任务未改动。
- 范围：
  - 只修改用户允许的七个实现/测试文件及指定报告。
  - 未回滚工作区前序成果，未 commit、merge、push。

## Concerns

- 应用内浏览器连接本地页面仍被工具环境错误 `sandboxCwd must be an absolute file URI` 阻断，因此未完成真实浏览器可视验收；HTTP 首页可达，自动化静态/行为测试均通过。
- 工作区在 Task 5 开始前已有大量未提交和未跟踪成果；本任务保留这些状态。`static/app2.js` 的整体 git diff 包含前序任务改动，Task 5 只改攻城考勤区域。
- Node 测试前 zsh 输出既有 `compdef:153: _comps: assignment to invalid subscript range` 启动警告，但测试进程退出码为 0，20 项测试全部通过。

## Review Fix RED

### 1. 完整 Dashboard E2E 旧契约

先运行完整 wrapper：

```text
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_dashboard_e2e -v

FAILED
Timeout waiting for:
#task-body tr:first-child .operation-stage[data-state='assembling']
```

该失败暴露出 E2E 在两处旧 pulse 之前还有一个 Task 5 阶段属性漂移：
生产语义为 `data-stage="assembling"` 与 `data-state="active"`，测试仍把 stage
值放在 `data-state` 中。

随后增加 E2E 静态契约并运行：

```text
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_battle_simulator_static.BattleSimulatorStaticTest.test_dashboard_e2e_tracks_semantic_event_lifecycle -v

FAILED
```

预期失败原因为 `test/js/dashboard-e2e.mjs` 仍包含 `hud-pulse-success`，且尚未
监听 `stzb:simulation-completed` 数据事件。

### 2. 考勤阶段状态机真实行为

在 Python 测试中通过 Node VM 执行期望的纯 helper，覆盖初始快照、新 task、
同 stage 和已有 task stage 变化：

```text
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_operations_hud_static -v

FAILED (failures=1)
Error: missing operationStageEvents helper
```

失败来自缺少可独立执行的状态机 helper，不是字符串断言。

### 3. firstRun warning shape

新增 `response.firstRun.diagnostics.unsupportedSkillEffects` 测试后聚焦运行：

```text
node --test --test-name-pattern='first-run diagnostics' \
  test/js/simulator-workbench.test.mjs

1 test, 1 pass
```

现有 helper 已正确覆盖该输入形态，因此该补充覆盖直接通过；未为制造 RED 而
破坏正确生产代码。

## Review Fix GREEN

### 实现

- `static/app2.js`
  - 提取纯函数 `operationStageEvents(previousStages, tasks, initialized)`。
  - 初始快照、新 task、同 stage 均返回空事件数组。
  - 已存在 task 的 stage 变化返回一个完整 `operation:stage-changed` payload。
  - renderer 只补 `timestamp` 后交给 `window.HudSystem?.emit`。
- `test/js/dashboard-e2e.mjs`
  - 阶段 selector 更新为 `data-stage="assembling"` + `data-state="active"`。
  - 积分 legacy bridge 验证其实际新生命周期类：
    `hud-event-success` + `hud-event-connection-restored`。
  - 模拟器验证：
    `hud-event-success` + `hud-event-simulation-completed`。
  - 两个场景都先等待 class 出现，再等待 class 消失，避免固定延迟。
  - 模拟运行前注入一次性 `stzb:simulation-completed` 监听，断言计数为 1。
  - 移动端索引折叠测试改用 DOM `click()`，避免 toast/FAB 浮层使命中区域测试
    阻断无关的折叠状态断言。

### 聚焦 GREEN

```text
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_operations_hud_static -v

Ran 5 tests
OK
```

```text
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_battle_simulator_static.BattleSimulatorStaticTest.test_dashboard_e2e_tracks_semantic_event_lifecycle -v

Ran 1 test
OK
```

```text
node --test \
  test/js/simulator-workbench.test.mjs \
  test/js/simulator-analysis.test.mjs

21 tests, 21 pass, 0 fail
```

### 完整 E2E GREEN

第一次修复后完整 E2E 已通过 Task 5 的阶段与事件断言，随后在移动端索引按钮处
被 toast/FAB 浮层拦截物理点击。改为 DOM click 后重跑：

```text
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_dashboard_e2e -v

Ran 1 test in 24.488s
OK
```

完整 E2E 覆盖新事件 class 的出现与清理、研究数据事件保留、35 tabs、
响应式矩阵和 reduced-motion。

## Review Fix Concerns

- 积分中心生产代码仍发出 legacy `stzb:hud-pulse`，Task 2 bridge 将其映射为
  `hud-event-connection-restored`。本次允许范围不含 `static/score-center.js`，
  因此 E2E 验证实际桥接类，不越界迁移该生产事件。
- `test/js/dashboard-e2e.mjs` 的移动端折叠按钮物理点击会被现有 toast/FAB 浮层
  覆盖；该测试目标是折叠状态机，已改用 DOM click。真实命中区域与浮层布局仍
  可由后续专门可视化测试评估。

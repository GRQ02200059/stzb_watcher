# 全站现代化质感与交互特效系统 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变业务结构、API、导航和 Vanilla JS 架构的前提下，为 12 个主导航页面建立统一的现代材质、微交互、页面状态和真实事件特效系统。

**Architecture:** `static/dashboard-hud.mjs` 继续作为唯一 HUD 行为深模块，统一处理动效等级、页面状态、事件去重、一次性效果、数字过渡和 Toast。`static/dashboard-design-system.css` 拥有 Surface、控件、状态和 Overlay 基础样式；五个域 CSS 只消费域 token 并表达业务语义，业务 JS 只调用 `window.HudSystem.emit()` 或 `renderState()`。

**Tech Stack:** Vanilla JavaScript ES modules、CSS Custom Properties、Flask 静态资源 mtime 版本、Node `node:test`、Python `unittest`、Playwright + 系统 Chrome。

## Global Constraints

- 保留当前 12 个主导航页面、`208px` 桌面侧栏和现有响应式导航。
- 不改变 API、数据库、业务字段、计算口径、页面可达性和现有兼容入口。
- 不引入 React、Vue、动画框架、新外部 CDN 或常驻粒子系统。
- 普通动画只允许 `transform` 和 `opacity`；不得新增常驻 `requestAnimationFrame` 循环。
- 同时活动动画元素不超过 6 个；同一视口活动 `backdrop-filter` 层不超过 4 个。
- 强特效只由真实业务事件触发，最长 2400ms。
- 系统 `prefers-reduced-motion` 始终优先于应用动效设置。
- 375、768、1024、1440、1920px 下不得出现页面级横向滚动。
- 页面保持单一 `<main>` landmark，200% 浏览器缩放时主要操作仍可完成。
- 不执行 Git commit、merge 或 push；当前工作区已有大量未提交改动。

---

## File Map

### Modify

- `static/dashboard-design-system.css`：全局 Surface、控件、Overlay、状态、Toast、事件效果和降级样式。
- `static/dashboard-hud.mjs`：HUD 状态模型、事件 API、Toast、去重、冷却、清理和可见性控制。
- `static/dashboard.html`：Toast live region、通用状态挂点和必要语义 class。
- `static/dashboard-command-center.js`：连接恢复、设置保存和系统域事件。
- `static/app1.js`：SSE 连接状态转换为语义事件。
- `static/intelligence-center.css/js`：情报域材质和高风险事件。
- `static/live-army-command.css/mjs`：实时部队状态和风险联动。
- `static/operations-hud.css`：作战域共享材质。
- `static/simulator-workbench.css/js`：模拟器 Overlay 和完成事件。
- `static/organization-hud.css`：组织域三个页面。
- `static/analysis-hud.css`：分析域共享材质。
- `static/score-center.css/js`：积分变化与重算事件。
- `static/intelligence-research.css/js`：研究工作台材质和模拟证据事件。
- `README.md`：HUD 运行时、Surface、事件和性能契约。
- `test/js/dashboard-hud.test.mjs`：HUD 深模块行为。
- `test/js/dashboard-e2e.mjs`：12 页面真实交互、状态、事件和响应式验收。
- `test/test_dashboard_hud_static.py`：全局 HUD 静态契约。
- `test/test_dashboard_css_structure.py`：CSS 所有权、性能和降级契约。
- `test/test_intelligence_center_static.py`
- `test/test_live_army_static.py`
- `test/test_operations_hud_static.py`
- `test/test_organization_hud_static.py`
- `test/test_analysis_hud_static.py`
- `test/test_score_center_static.py`
- `test/test_battle_simulator_static.py`
- `test/test_intelligence_research_static.py`
- `test/test_web_runtime_hardening.py`

---

### Task 1: 五级 Surface 与全局交互基础

**Files:**
- Modify: `static/dashboard-design-system.css`
- Modify: `test/test_dashboard_hud_static.py`
- Modify: `test/test_dashboard_css_structure.py`

**Interfaces:**
- Consumes: existing `data-visual-domain`, `data-motion-level`, `.hud-*` component classes.
- Produces: global Surface tokens and reusable interaction/state selectors consumed by Tasks 3–8.

- [ ] **Step 1: Write failing Surface token and selector tests**

Extend `test_shared_hud_components_and_tokens_exist()` with:

```python
for token in (
    "--surface-canvas",
    "--surface-panel",
    "--surface-raised",
    "--surface-overlay",
    "--surface-modal",
    "--surface-inner-highlight",
    "--shadow-raised",
    "--shadow-overlay",
    "--shadow-modal",
    "--motion-press",
    "--motion-event",
):
    self.assertIn(token, css)

for selector in (
    ".hud-surface-panel",
    ".hud-surface-raised",
    ".hud-surface-overlay",
    ".hud-surface-modal",
    ".hud-refresh-line",
    ".hud-skeleton",
    ".hud-toast-region",
    ".hud-toast",
):
    self.assertIn(selector, css)
```

Add to `test_dashboard_css_structure.py`:

```python
def test_global_polish_uses_bounded_glass_and_compositor_properties(self):
    css = (ROOT / "static/dashboard-design-system.css").read_text(
        encoding="utf-8"
    )
    self.assertIn("--surface-overlay:", css)
    self.assertIn("--surface-modal:", css)
    self.assertIn("@supports not (backdrop-filter: blur(1px))", css)
    self.assertNotRegex(
        css,
        r"transition\s*:\s*all\b",
    )
    self.assertNotRegex(
        css,
        r"animation(?:-[a-z]+)?\s*:[^;]*(?:width|height|left|top)",
    )
```

- [ ] **Step 2: Run tests and verify RED**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_dashboard_hud_static \
  test.test_dashboard_css_structure -v
```

Expected: failures for missing Surface tokens and selectors.

- [ ] **Step 3: Add the five-level token system**

Add to the root token block in `static/dashboard-design-system.css`:

```css
--surface-canvas: #060b18;
--surface-panel: rgba(12, 22, 40, 0.96);
--surface-raised: rgba(15, 29, 50, 0.98);
--surface-overlay: rgba(8, 18, 34, 0.78);
--surface-modal: rgba(7, 16, 31, 0.96);
--surface-inner-highlight: rgba(255, 255, 255, 0.055);
--shadow-raised: 0 10px 28px rgba(0, 4, 18, 0.24);
--shadow-overlay: 0 18px 48px rgba(0, 4, 18, 0.42);
--shadow-modal: 0 30px 90px rgba(0, 3, 16, 0.66);
--motion-press: 100ms;
--motion-event: 1200ms;
```

Keep compatibility aliases pointing to these tokens:

```css
--bg-canvas: var(--surface-canvas);
--surface-1: var(--surface-panel);
--surface-2: var(--surface-raised);
--surface-glass: var(--surface-overlay);
--surface-elevated: var(--surface-raised);
```

- [ ] **Step 4: Add reusable Surface and interaction selectors**

Implement:

```css
.hud-surface-panel,
.hud-panel {
  border: 1px solid color-mix(in srgb, var(--domain-accent) 12%, var(--border-subtle));
  background: var(--surface-panel);
  box-shadow:
    inset 0 1px var(--surface-inner-highlight),
    var(--shadow-panel);
}

.hud-surface-raised {
  border: 1px solid color-mix(in srgb, var(--domain-accent) 18%, var(--border-subtle));
  background: var(--surface-raised);
  box-shadow:
    inset 0 1px var(--surface-inner-highlight),
    var(--shadow-raised);
}

.hud-surface-overlay {
  border: 1px solid color-mix(in srgb, var(--domain-accent) 22%, var(--border-subtle));
  background: var(--surface-overlay);
  box-shadow:
    inset 0 1px var(--surface-inner-highlight),
    var(--shadow-overlay);
  backdrop-filter: blur(16px) saturate(1.12);
}

.hud-surface-modal {
  border: 1px solid color-mix(in srgb, var(--domain-accent) 28%, var(--border-strong));
  background: var(--surface-modal);
  box-shadow:
    inset 0 1px var(--surface-inner-highlight),
    var(--shadow-modal);
  backdrop-filter: blur(20px) saturate(1.14);
}

button:active:not(:disabled),
[role="button"]:active:not([aria-disabled="true"]) {
  transform: scale(0.98);
  transition-duration: var(--motion-press);
}

.hud-panel[data-interactive="true"]:hover {
  transform: translateY(-2px);
}
```

Do not apply `translateY` to table rows.

- [ ] **Step 5: Add feature and reduced-motion fallbacks**

```css
@supports not (backdrop-filter: blur(1px)) {
  .hud-surface-overlay,
  .hud-surface-modal {
    background: var(--surface-raised);
    border-color: var(--border-strong);
  }
}

body[data-motion-level="reduced"] button,
body[data-motion-level="reduced"] [role="button"] {
  transition-duration: 0ms;
}

body[data-motion-level="reduced"] .hud-panel[data-interactive="true"]:hover {
  transform: none;
}

@media (prefers-reduced-motion: reduce) {
  button,
  [role="button"],
  .hud-panel[data-interactive="true"] {
    transition-duration: 0ms !important;
    transform: none !important;
  }
}
```

Inside the existing reduced-motion media block, disable card translation, scanning and smooth numeric transitions while keeping focus and state colors.

- [ ] **Step 6: Run Task 1 GREEN**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_dashboard_hud_static \
  test.test_dashboard_css_structure -v
git diff --check
```

---

### Task 2: HUD 事件、页面状态与 Toast 深模块

**Files:**
- Modify: `static/dashboard-hud.mjs`
- Modify: `test/js/dashboard-hud.test.mjs`
- Modify: `test/test_dashboard_runtime_node.py`
- Modify: `test/test_web_runtime_hardening.py`

**Interfaces:**
- Consumes: Surface/state classes from Task 1.
- Produces:
  - `HudSystem.emit(event)`
  - `HudSystem.clearEffects()`
  - `HudSystem.renderState(container, state)`
  - `HudSystem.toast(notification)`
  - event shape `{ type, target, domain, severity, value, message, timestamp, dedupeKey }`

- [ ] **Step 1: Expand fake DOM support and write failing state tests**

Update the fake elements in `test/js/dashboard-hud.test.mjs` to support:

```javascript
setAttribute(name, value) {
  this.attributes ||= {};
  this.attributes[name] = String(value);
},
remove() {
  this.removed = true;
},
querySelector() {
  return null;
},
```

Replace the three-state test with:

```javascript
test("renderState supports the complete page state model", () => {
  const kinds = [
    "idle",
    "loading",
    "refreshing",
    "success",
    "empty",
    "stale",
    "warning",
    "error",
  ];
  for (const kind of kinds) {
    assert.equal(system.renderState(container, { kind }).kind, kind);
  }
  assert.equal(
    system.renderState(container, { kind: "invalid" }).kind,
    "empty",
  );
});
```

- [ ] **Step 2: Write failing event lifecycle tests**

Add:

```javascript
test("emit deduplicates active events and clears one-shot classes", () => {
  const target = fakeElement();
  const scheduled = [];
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "visible",
      querySelector(selector) {
        return selector === "#risk" ? target : null;
      },
      createElement() {
        return fakeElement();
      },
      getElementById() {
        return null;
      },
    },
    matchMediaFn: () => ({ matches: false }),
    setTimeoutFn(callback) {
      scheduled.push(callback);
      return scheduled.length;
    },
    nowFn: () => 1000,
  });

  assert.equal(system.emit({
    type: "intelligence:risk-detected",
    target: "#risk",
    severity: "critical",
    dedupeKey: "wid:10004",
  }), true);
  assert.equal(system.emit({
    type: "intelligence:risk-detected",
    target: "#risk",
    severity: "critical",
    dedupeKey: "wid:10004",
  }), false);
  assert.equal(target.classList.contains("hud-event-critical"), true);
  scheduled.at(-1)();
  assert.equal(target.classList.contains("hud-event-critical"), false);
});

test("emit does not animate hidden documents or reduced motion", () => {
  const hiddenTarget = fakeElement();
  const hidden = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "hidden",
      querySelector() {
        return hiddenTarget;
      },
      getElementById() {
        return null;
      },
      createElement() {
        return fakeElement();
      },
    },
    matchMediaFn: () => ({ matches: false }),
  });
  assert.equal(hidden.emit({
    type: "intelligence:risk-detected",
    target: "#risk",
    severity: "critical",
  }), false);
  assert.equal(hiddenTarget.classList.values.size, 0);

  const reducedTarget = fakeElement();
  const reduced = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "visible",
      querySelector() {
        return reducedTarget;
      },
      getElementById() {
        return null;
      },
      createElement() {
        return fakeElement();
      },
    },
    matchMediaFn: () => ({ matches: true }),
  });
  assert.equal(reduced.emit({
    type: "intelligence:risk-detected",
    target: "#risk",
    severity: "critical",
  }), true);
  assert.equal(reducedTarget.classList.values.size, 0);
});
```

- [ ] **Step 3: Write failing Toast tests**

```javascript
test("toast merges repeated notifications and assigns live priority", () => {
  const region = fakeElement();
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "visible",
      getElementById(id) {
        return id === "hud-toast-region" ? region : null;
      },
      createElement() {
        return fakeElement();
      },
    },
    matchMediaFn: () => ({ matches: false }),
    nowFn: () => 1000,
  });

  system.toast({
    severity: "warning",
    title: "数据陈旧",
    dedupeKey: "stale",
  });
  system.toast({
    severity: "warning",
    title: "数据陈旧",
    dedupeKey: "stale",
  });

  assert.equal(region.children.length, 1);
  assert.equal(region.children[0].dataset.count, "2");
  assert.equal(region.children[0].attributes["aria-live"], "polite");
});
```

Add a critical notification assertion for `aria-live="assertive"`.

- [ ] **Step 4: Verify RED**

```bash
node --test test/js/dashboard-hud.test.mjs
```

Expected: failures because `emit`, `toast`, complete states and `nowFn` do not exist.

- [ ] **Step 5: Implement the complete state model**

Use:

```javascript
const VALID_STATES = new Set([
  "idle",
  "loading",
  "refreshing",
  "success",
  "empty",
  "stale",
  "warning",
  "error",
]);
```

`renderState()` must:

- return a normalized model;
- use `hud-state-${kind}`;
- set `aria-busy="true"` only for `loading` and `refreshing`;
- retain prior content for `refreshing`, `stale`, `warning`, and non-blocking `error` when `state.replace !== true`;
- replace children for first-load `loading`, `empty`, and blocking `error`.

- [ ] **Step 6: Implement `emit`, event policy and cleanup**

Add to `createHudSystem()`:

```javascript
const VALID_EVENTS = new Set([
  "intelligence:risk-detected",
  "battle:report-arrived",
  "simulation:completed",
  "score:recalculated",
  "connection:restored",
  "data:stale",
  "operation:stage-changed",
]);
const activeEvents = new Map();
const eventTimers = new Set();
```

Rules:

- reject unknown event types with `false`;
- resolve `event.target` from a selector or element;
- return `false` when `documentRef.visibilityState === "hidden"`;
- use `dedupeKey || type`;
- suppress repeats for the event duration;
- add `hud-event-${severity}` and `hud-event-${event.type.replaceAll(":", "-")}`;
- remove both classes after a bounded duration;
- `clearEffects()` cancels timers and removes known event classes;
- reduced motion skips event classes but may still call `toast()`.

- [ ] **Step 7: Implement Toast merging**

`toast()` must:

- use existing `#hud-toast-region`;
- create one article per active `dedupeKey`;
- increment `data-count` for duplicates;
- include title, optional message/source, timestamp and optional action;
- set `aria-live="assertive"` for `critical` and `error`, otherwise `polite`;
- auto-remove info/success after 4200ms and warning after 6200ms;
- keep critical until action or close;
- expose no raw `innerHTML` path for API strings.

- [ ] **Step 8: Wire browser seams**

Keep legacy `stzb:hud-pulse` compatibility, but route it through the same lifecycle:

```javascript
window.addEventListener("stzb:hud-pulse", (event) => {
  defaultSystem.emit({
    type: legacyEventType(event.detail?.kind),
    target: event.detail?.selector,
    severity: event.detail?.kind || "info",
    dedupeKey: event.detail?.selector,
  });
});
```

Add:

```javascript
window.addEventListener("stzb:tab-changed", () => {
  defaultSystem.clearEffects();
});
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "hidden") defaultSystem.clearEffects();
});
```

- [ ] **Step 9: Run Task 2 GREEN**

```bash
node --test test/js/dashboard-hud.test.mjs
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_dashboard_runtime_node \
  test.test_web_runtime_hardening.WebRuntimeHardeningTest.test_index_rewrites_local_asset_versions_from_file_mtime -v
```

---

### Task 3: 全局 Overlay、Toast、Skeleton 与控件状态

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/dashboard-design-system.css`
- Modify: `static/dashboard-design-system.js`
- Modify: `test/test_dashboard_hud_static.py`
- Modify: `test/test_web_ui_design_system.py`

**Interfaces:**
- Consumes: Task 1 Surface classes and Task 2 HUD API.
- Produces: one live-region Toast host and shared DOM/CSS contracts for all pages.

- [ ] **Step 1: Write failing markup and accessibility tests**

Add to `test_dashboard_hud_static.py`:

```python
def test_dashboard_exposes_one_accessible_hud_toast_region(self):
    html = HTML.read_text(encoding="utf-8")
    self.assertEqual(html.count('id="hud-toast-region"'), 1)
    self.assertRegex(
        html,
        r'id="hud-toast-region"[^>]*role="region"',
    )
    self.assertIn('aria-label="系统通知"', html)
```

Add to `test_web_ui_design_system.py`:

```python
def test_modern_polish_components_have_accessible_states(self):
    css = CSS.read_text(encoding="utf-8")
    for selector in (
        ".hud-toast-region",
        ".hud-toast[data-severity=\"critical\"]",
        ".hud-skeleton",
        ".hud-refresh-line",
        ".hud-event-critical",
    ):
        self.assertIn(selector, css)
    self.assertIn(":focus-visible", css)
    self.assertIn(":disabled", css)
```

- [ ] **Step 2: Verify RED**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_dashboard_hud_static \
  test.test_web_ui_design_system -v
```

- [ ] **Step 3: Add one Toast region**

Before the closing body scripts in `static/dashboard.html`, add:

```html
<section
  id="hud-toast-region"
  class="hud-toast-region"
  role="region"
  aria-label="系统通知"
  aria-relevant="additions text"
></section>
```

Keep the legacy `#toast` available until all legacy calls are migrated; do not create a second HUD region.

- [ ] **Step 4: Implement shared Toast and event CSS**

Implement:

```css
.hud-toast-region {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 1200;
  display: grid;
  width: min(380px, calc(100vw - 32px));
  gap: 10px;
  pointer-events: none;
}

.hud-toast {
  pointer-events: auto;
  transform: translateY(8px);
  opacity: 0;
  animation: hud-toast-in var(--motion-standard) ease forwards;
}

.hud-event-critical {
  animation: hud-event-critical var(--motion-event) ease-out 1;
}
```

Add severity selectors, close/action controls, count badge and mobile placement.

- [ ] **Step 5: Implement Skeleton and refreshing primitives**

```css
.hud-skeleton {
  color: transparent;
  border-color: transparent;
  background:
    linear-gradient(
      100deg,
      transparent 20%,
      rgba(255,255,255,.055) 45%,
      transparent 70%
    ),
    var(--surface-raised);
  background-size: 220% 100%;
  animation: hud-skeleton-shift 1400ms ease-in-out infinite;
}

.hud-refresh-line {
  position: relative;
}

.hud-refresh-line::after {
  position: absolute;
  inset: 0 0 auto;
  height: 2px;
  content: "";
  transform-origin: left;
  background: var(--domain-accent);
  animation: hud-refresh-line 900ms ease-in-out infinite;
}
```

Reduced motion must display a static dim line and static Skeleton.

- [ ] **Step 6: Normalize existing Overlay seams**

In `dashboard-design-system.js`, add semantic classes without changing DOM order:

- Header → `hud-surface-overlay`;
- command dialog shell → `hud-surface-modal`;
- mobile navigation drawer → `hud-surface-overlay`;
- generic `.hud-panel-glass` → `hud-surface-overlay`.

Do not add classes to every table row.

- [ ] **Step 7: Run Task 3 GREEN**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_dashboard_hud_static \
  test.test_web_ui_design_system \
  test.test_dashboard_css_structure -v
```

---

### Task 4: Intelligence 域三页面

**Files:**
- Modify: `static/intelligence-center.css`
- Modify: `static/intelligence-center.js`
- Modify: `static/live-army-command.css`
- Modify: `static/live-army-command.mjs`
- Modify: `static/dashboard.html`
- Modify: `test/test_intelligence_center_static.py`
- Modify: `test/test_live_army_static.py`
- Modify: `test/js/live-army-command.test.mjs`

**Interfaces:**
- Consumes: `window.HudSystem.emit`, complete state model and Surface classes.
- Produces: semantic high-risk, stale and live-army events without local animation timers.

- [ ] **Step 1: Write failing static contracts**

Require in intelligence sources:

```python
self.assertIn("HudSystem?.emit", self.script)
self.assertIn("intelligence:risk-detected", self.script)
self.assertNotIn("stzb:hud-pulse", self.script)
```

Require in CSS:

```python
for token in (
    "var(--surface-panel)",
    "var(--surface-overlay)",
    "var(--domain-accent)",
):
    self.assertIn(token, self.css)
self.assertIn(".intel-risk-lock", self.css)
self.assertIn(".live-army-card[data-freshness=\"stale\"]", self.css)
```

- [ ] **Step 2: Verify RED**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_intelligence_center_static \
  test.test_live_army_static -v
```

- [ ] **Step 3: Upgrade battle intelligence materials**

Use global Surface variables for:

- `.intel-map-shell`;
- `.intel-detail-panel`;
- radar/floating toolbars;
- timeline and risk cards.

Add `.intel-risk-lock` only to the selected high-risk marker overlay. The Canvas itself must not use blur or CSS filters.

- [ ] **Step 4: Emit bounded high-risk events**

Replace legacy pulse dispatch in `intelligence-center.js`:

```javascript
window.HudSystem?.emit({
  type: "intelligence:risk-detected",
  target: "#intel-detail-panel",
  domain: "intelligence",
  severity: "critical",
  message: `WID ${wid} 风险 ${risk.score}`,
  timestamp: Date.now(),
  dedupeKey: `risk:${wid}:${risk.level}`,
});
```

Only emit when:

- risk is high;
- selected WID matches;
- risk key differs from the previously emitted key.

Do not change map selection or viewport.

- [ ] **Step 5: Upgrade live-army states**

Use attributes:

```html
data-freshness="fresh|aging|stale"
data-lineup-status="exact|unknown"
data-activity="current|offline"
```

CSS must express:

- stale with warning edge and timestamp;
- unknown lineup with neutral evidence treatment;
- recent offline with dashed boundary;
- selected army with domain accent;
- countdown changes without transform animation.

- [ ] **Step 6: Add stream-event behavior tests**

Extend `test/js/live-army-command.test.mjs` to verify:

- repeated world delta refresh remains debounced;
- no `HudSystem.emit` call occurs for ordinary countdown ticks;
- selecting a high-risk army may call the injected event callback once;
- stale state retains explicit time text.

If direct global access makes this hard, inject `emitHudEvent` into `createLiveArmyCommand()` with a no-op default.

- [ ] **Step 7: Run Task 4 GREEN**

```bash
node --test \
  test/js/intelligence-map.test.mjs \
  test/js/live-army-command.test.mjs \
  test/js/live-army-map.test.mjs
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_intelligence_center_static \
  test.test_live_army_static -v
```

---

### Task 5: Operations 域两页面

**Files:**
- Modify: `static/operations-hud.css`
- Modify: `static/simulator-workbench.css`
- Modify: `static/simulator-workbench.js`
- Modify: `static/app2.js`
- Modify: `test/test_operations_hud_static.py`
- Modify: `test/test_battle_simulator_static.py`
- Modify: `test/js/simulator-workbench.test.mjs`

**Interfaces:**
- Consumes: Task 2 event API and Task 3 Overlay classes.
- Produces: `simulation:completed` and `operation:stage-changed` semantic events.

- [ ] **Step 1: Write failing operations contracts**

Require:

```python
self.assertIn("operation:stage-changed", self.app2)
self.assertIn("simulation:completed", self.simulator)
self.assertIn("HudSystem?.emit", self.simulator)
self.assertNotIn("stzb:hud-pulse", self.simulator)
```

CSS assertions:

```python
self.assertIn("var(--surface-overlay)", self.simulator_css)
self.assertIn("var(--surface-modal)", self.simulator_css)
self.assertIn(".operation-stage[data-state=\"active\"]", self.operations_css)
```

- [ ] **Step 2: Verify RED**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_operations_hud_static \
  test.test_battle_simulator_static -v
```

- [ ] **Step 3: Normalize simulator Overlay materials**

Apply shared Surface tokens to:

- hero/skill library drawer;
- template dialog;
- replay detail panel;
- result summary;
- control toolbar.

Preserve current portrait, scan and fallback behavior. Remove duplicated local drawer/modal shadows only after the shared class owns them.

- [ ] **Step 4: Emit simulation completion with evidence severity**

After a successful result:

```javascript
const hasWarnings = Boolean(
  response?.firstRun?.diagnostics?.unsupportedSkillEffects?.length
  || response?.result?.replay?.diagnostics?.unsupportedSkillEffects?.length
);
window.HudSystem?.emit({
  type: "simulation:completed",
  target: "#sim-result-summary",
  domain: "operations",
  severity: hasWarnings ? "warning" : "success",
  value: repeat,
  message: hasWarnings
    ? "模拟完成，存在未支持效果"
    : `模拟完成 · ${repeat} 次`,
  timestamp: Date.now(),
  dedupeKey: `simulation:${repeat}:${browserRuntime.sourceContext?.lineupKey || "manual"}`,
});
```

Keep the existing `stzb:simulation-completed` data event because the research workbench consumes it.

- [ ] **Step 5: Emit real operation stage changes**

In the attendance renderer, compare the previous task stage map with the new map. Emit only when an existing task changes stage:

```javascript
window.HudSystem?.emit({
  type: "operation:stage-changed",
  target: `[data-task-id="${task.id}"]`,
  domain: "operations",
  severity: "info",
  message: `${task.name} 进入 ${stage.label}`,
  timestamp: Date.now(),
  dedupeKey: `task:${task.id}:${stage.key}`,
});
```

Initial render must not emit.

- [ ] **Step 6: Add simulator event tests**

Add a pure helper:

```javascript
export function simulationCompletionEvent(response, repeat, sourceContext) {
  return {
    type: "simulation:completed",
    target: "#sim-result-summary",
    domain: "operations",
    severity: hasUnsupportedEffects(response) ? "warning" : "success",
    value: repeat,
    dedupeKey: `simulation:${repeat}:${sourceContext?.lineupKey || "manual"}`,
  };
}
```

Test success and warning results.

- [ ] **Step 7: Run Task 5 GREEN**

```bash
node --test \
  test/js/simulator-workbench.test.mjs \
  test/js/simulator-analysis.test.mjs
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_operations_hud_static \
  test.test_battle_simulator_static -v
```

---

### Task 6: Organization 域三页面

**Files:**
- Modify: `static/organization-hud.css`
- Modify: `static/app1.js`
- Modify: `static/app2.js`
- Modify: `test/test_organization_hud_static.py`

**Interfaces:**
- Consumes: global Surface, refreshing and interaction classes.
- Produces: stable organization semantics without page-specific animation APIs.

- [ ] **Step 1: Write failing semantic style tests**

Add assertions for:

```python
for selector in (
    ".organization-identity",
    ".organization-group-chip",
    ".organization-lineup-card",
    ".organization-row[data-selected=\"true\"]",
    ".organization-row[data-state=\"stale\"]",
):
    self.assertIn(selector, self.css)

self.assertNotRegex(self.css, r"transition\s*:\s*all\b")
self.assertIn("var(--surface-panel)", self.css)
self.assertIn("var(--surface-raised)", self.css)
```

- [ ] **Step 2: Verify RED**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_organization_hud_static -v
```

- [ ] **Step 3: Normalize the three organization pages**

Apply organization semantics to:

- player team rows/cards;
- alliance member team rows/cards;
- group report KPI and rows.

Rules:

- identity and group remain visually distinct;
- missing lineup uses empty state, not a blank card;
- filter refresh adds `.hud-refresh-line` to the containing panel;
- existing content remains until the response arrives;
- export buttons use `aria-busy` and preserve width;
- table rows do not translate.

- [ ] **Step 4: Add renderer seams**

In `app1.js` and `app2.js`, render:

```javascript
row.dataset.selected = String(selected);
row.dataset.state = rowData.isStale ? "stale" : "current";
```

Use `window.HudSystem?.renderState()` for empty and blocking error states. Do not emit strong events for ordinary filters.

- [ ] **Step 5: Run Task 6 GREEN**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_organization_hud_static \
  test.test_dashboard_runtime_node.DashboardRuntimeNodeTest.test_app2_has_no_duplicate_top_level_function_names -v
node --check static/app1.js
node --check static/app2.js
```

---

### Task 7: Analysis 域三页面

**Files:**
- Modify: `static/analysis-hud.css`
- Modify: `static/score-center.css`
- Modify: `static/score-center.js`
- Modify: `static/intelligence-research.css`
- Modify: `static/intelligence-research.js`
- Modify: `static/research-workbench.mjs`
- Modify: `static/app2.js`
- Modify: `test/test_analysis_hud_static.py`
- Modify: `test/test_score_center_static.py`
- Modify: `test/test_intelligence_research_static.py`
- Modify: `test/js/research-workbench.test.mjs`

**Interfaces:**
- Consumes: Task 2 event API, Task 3 Overlay classes.
- Produces: `score:recalculated` and research simulation feedback.

- [ ] **Step 1: Write failing analysis contracts**

Require:

```python
self.assertIn("score:recalculated", self.score_script)
self.assertIn("HudSystem?.emit", self.score_script)
self.assertNotIn("stzb:hud-pulse", self.score_script)
self.assertIn("var(--surface-modal)", self.score_css)
self.assertIn("var(--surface-raised)", self.research_css)
self.assertIn(".analysis-row[data-delta=\"up\"]", self.analysis_css)
self.assertIn(".analysis-row[data-delta=\"down\"]", self.analysis_css)
```

- [ ] **Step 2: Verify RED**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_analysis_hud_static \
  test.test_score_center_static \
  test.test_intelligence_research_static -v
```

- [ ] **Step 3: Normalize score materials and delta states**

Use shared Surface tokens for:

- score hero;
- KPI cards;
- rule/preview/player dialogs;
- board shell.

After recalculation, annotate only changed rows:

```javascript
row.dataset.delta = delta > 0 ? "up" : delta < 0 ? "down" : "same";
```

Use domain-colored direction markers plus text; do not rely on color alone.

- [ ] **Step 4: Emit confirmed score recalculation**

Only after the write response succeeds:

```javascript
window.HudSystem?.emit({
  type: "score:recalculated",
  target: "#score-board",
  domain: "analysis",
  severity: "success",
  value: Number(response.updated || 0),
  message: `已更新 ${Number(response.updated || 0)} 名成员`,
  timestamp: Date.now(),
  dedupeKey: `score:${response.ruleVersion}:${response.updated}`,
});
```

Preview must not emit.

- [ ] **Step 5: Upgrade lineup and research cards**

For the hero lineup page:

- preserve non-Emoji ranking;
- use Raised Surface for top lineup cards;
- add `data-rank-tier="top|standard"`;
- update rows without full-list fade.

For the research workbench:

- library = Panel Surface;
- stage = Raised Surface;
- evidence = Overlay Surface only on desktop;
- template dialog = Modal Surface;
- selected mode/evidence/slot share one domain-accent pattern;
- config and simulation evidence badges remain distinct.

- [ ] **Step 6: Route research simulation completion through HUD**

In `intelligence-research.js`, when the controller receives a matching completed simulation, emit:

```javascript
window.HudSystem?.emit({
  type: "simulation:completed",
  target: "#research-evidence-body",
  domain: "analysis",
  severity: "success",
  message: "模拟证据已回传研究工作台",
  timestamp: Date.now(),
  dedupeKey: `research-simulation:${lineupKey}`,
});
```

Do not emit for external simulations or non-matching lineup keys.

- [ ] **Step 7: Add research controller callback test**

Inject `onSimulationEvidence` into `createResearchWorkbench()`:

```javascript
const evidenceEvents = [];
const workbench = createHarness(fetchJson, {
  onSimulationEvidence(detail) {
    evidenceEvents.push(detail.sourceContext.lineupKey);
  },
});
workbench.onSimulationCompleted({ detail });
assert.deepEqual(evidenceEvents, ["101.102.103"]);
```

The adapter owns `HudSystem.emit`; the pure controller only invokes the callback.

- [ ] **Step 8: Run Task 7 GREEN**

```bash
node --test \
  test/js/research-workbench.test.mjs \
  test/js/research-skill-chain.test.mjs \
  test/js/research-templates.test.mjs
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_analysis_hud_static \
  test.test_score_center_static \
  test.test_intelligence_research_static -v
```

---

### Task 8: System 域、连接恢复与设置反馈

**Files:**
- Modify: `static/dashboard-command-center.js`
- Modify: `static/app1.js`
- Modify: `static/dashboard-design-system.css`
- Modify: `static/dashboard.html`
- Modify: `test/test_command_center_static.py`
- Modify: `test/test_dashboard_hud_static.py`
- Modify: `test/test_web_runtime_hardening.py`

**Interfaces:**
- Consumes: `HudSystem.emit`, Toast and state model.
- Produces: `connection:restored`, `data:stale` and low-priority setting feedback.

- [ ] **Step 1: Write failing system-event tests**

Require:

```python
self.assertIn("connection:restored", self.app1)
self.assertIn("data:stale", self.command)
self.assertIn("HudSystem?.emit", self.command)
self.assertIn('data-visual-domain="system"', self.html)
self.assertNotIn("stzb:hud-pulse", self.command)
```

Add a static assertion that `#hud-health-grid` uses Panel Surface and the command dialog uses Modal Surface.

- [ ] **Step 2: Verify RED**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_command_center_static \
  test.test_dashboard_hud_static \
  test.test_web_runtime_hardening -v
```

- [ ] **Step 3: Track real connection transitions**

In `app1.js`, maintain:

```javascript
let streamConnectionState = "connecting";
```

On open:

- if previous state was `error` or `stale`, emit `connection:restored`;
- set live status;
- do not emit on the initial connection.

On error:

- transition to `error`;
- use a warning/critical Toast after the existing retry threshold;
- do not emit one Toast for every reconnect attempt.

- [ ] **Step 4: Emit stale data from existing health/overview truth**

When command-center overview or HUD health reports stale:

```javascript
window.HudSystem?.emit({
  type: "data:stale",
  target: "#hud-health-grid",
  domain: "system",
  severity: "warning",
  message: staleMessage,
  timestamp: Date.now(),
  dedupeKey: `stale:${component}:${ageBucket}`,
});
```

Do not infer stale status from missing optional tables.

- [ ] **Step 5: Add setting-save feedback**

After settings are persisted:

```javascript
window.HudSystem?.toast({
  severity: "success",
  title: "设置已保存",
  message: changedSettingLabel,
  source: "设置中心",
  timestamp: Date.now(),
  dedupeKey: `setting:${key}`,
});
```

Do not show a Toast while initializing controls from storage.

- [ ] **Step 6: Run Task 8 GREEN**

```bash
node --check static/app1.js
node --check static/dashboard-command-center.js
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_command_center_static \
  test.test_dashboard_hud_static \
  test.test_web_runtime_hardening -v
```

---

### Task 9: Chrome 验收、性能、文档和全量回归

**Files:**
- Modify: `test/js/dashboard-e2e.mjs`
- Modify: `README.md`
- Modify: `test/test_dashboard_css_structure.py`
- Modify: `test/test_web_ui_design_system.py`

**Interfaces:**
- Verifies Tasks 1–8.
- Produces the final 12-page modern polish system.

- [ ] **Step 1: Extend deterministic E2E fixtures**

Add fixture controls for:

- high-risk tile transition;
- new battle stream event;
- simulation with and without unsupported effects;
- score recalculation with up/down rows;
- stale health response;
- stream disconnect/reconnect;
- setting change.

Use route-local counters rather than time-dependent backend state.

- [ ] **Step 2: Add 12-page Surface and interaction assertions**

For every visible tab, assert:

```javascript
const pageRoot = page.locator(`#tab${tabId}`);
await expectVisible(pageRoot.locator(".hud-page-head").first());
assert.equal(
  await pageRoot.locator(".hud-panel").first().evaluate(
    (element) => getComputedStyle(element).backgroundColor !== "rgba(0, 0, 0, 0)",
  ),
  true,
);
```

For one representative control per domain:

- hover changes border or background;
- mouse down produces a non-identity transform;
- keyboard focus has a visible outline or box shadow;
- disabled controls do not transform.

- [ ] **Step 3: Add page-state E2E**

Verify:

- first-load Skeleton attaches;
- refreshing keeps existing content visible;
- empty state explains the next action;
- stale state displays age and warning;
- error retains prior content when non-blocking;
- no raw backend parse error leaks into the UI.

- [ ] **Step 4: Add real event E2E**

Verify:

- high-risk tile adds one event class and one critical notification;
- repeated same risk is deduplicated;
- new battle highlights only its row;
- simulation success uses success; unsupported effects use warning;
- score recalculation marks only changed rows;
- reconnect emits once after an actual error;
- switching tabs clears one-shot event classes.

- [ ] **Step 5: Extend responsive and 200% zoom matrix**

At:

```text
1440×1000
1024×900
768×900
390×844
```

Verify all 12 tabs:

- no document overflow;
- header and primary action reachable;
- Dialog fits viewport;
- Toast stays inside viewport;
- no more than four visible elements have a non-`none` backdrop filter.

Create a context with `deviceScaleFactor: 2` and assert primary navigation, selected page controls and Dialog close remain reachable.

- [ ] **Step 6: Extend reduced-motion E2E**

Verify:

- no event scan animation;
- no card translation;
- no button scale;
- no numeric interpolation;
- Toast and state text remain visible;
- focus and state colors remain present;
- map and chain content remain readable.

- [ ] **Step 7: Add CSS performance guardrails**

Extend `test_dashboard_css_structure.py`:

```python
def test_global_polish_keeps_css_and_hud_runtime_bounded(self):
    hud = ROOT / "static/dashboard-hud.mjs"
    self.assertLess(hud.stat().st_size, 35_000)
    css = (ROOT / "static/dashboard-design-system.css").read_text(
        encoding="utf-8"
    )
    self.assertNotRegex(css, r"transition\s*:\s*all\b")
    self.assertLess(css.count("backdrop-filter:"), 12)
```

Keep the existing check that `requestAnimationFrameFn` appears only inside `animateValue`.

- [ ] **Step 8: Update README**

Document:

- five Surface levels;
- five visual domains;
- `window.HudSystem.emit()` event list;
- page states;
- Full / Standard / Reduced behavior;
- performance limits;
- event effects only from real business changes;
- fallback behavior without backdrop filter.

- [ ] **Step 9: Run focused validation**

```bash
node --check \
  static/dashboard-hud.mjs \
  static/dashboard-design-system.js \
  static/dashboard-command-center.js \
  static/intelligence-center.js \
  static/live-army-command.mjs \
  static/simulator-workbench.js \
  static/score-center.js \
  static/intelligence-research.js

node --test test/js/*.test.mjs

PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_dashboard_hud_static \
  test.test_dashboard_css_structure \
  test.test_web_ui_design_system \
  test.test_intelligence_center_static \
  test.test_live_army_static \
  test.test_operations_hud_static \
  test.test_organization_hud_static \
  test.test_analysis_hud_static \
  test.test_score_center_static \
  test.test_battle_simulator_static \
  test.test_intelligence_research_static \
  test.test_command_center_static \
  test.test_dashboard_e2e -v
```

- [ ] **Step 10: Run full repository validation**

```bash
git diff --check
node --test test/js/*.test.mjs
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest discover -s test -v
```

Expected:

- every command exits 0;
- Chrome E2E reports 35 tabs, five HUD domains, responsive matrix and reduced motion;
- no page errors;
- no local HTTP 500 responses.

- [ ] **Step 11: Restart 8080 and smoke**

Restart only the current project listener. Start without packet capture:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -c \
  "import api_server; api_server.run_app(open_browser=False,start_sniffer=False,host='127.0.0.1',port=8080)"
```

Verify:

```bash
curl -fsS http://127.0.0.1:8080/api/status
curl -fsS http://127.0.0.1:8080/ |
  rg 'dashboard-(hud|design-system).*\?v='
curl -fsS http://127.0.0.1:8080/ |
  rg 'hud-toast-region|data-visual-domain'
```

- [ ] **Step 12: Final requirement audit**

Confirm:

- all 12 visible pages use shared Surface and interaction language;
- five domains remain visually distinct;
- business cards do not overuse blur or glow;
- strong effects only follow real events;
- loading, refreshing, empty, stale and error states exist;
- reduced motion preserves all information and actions;
- 375–1920px and 200% zoom remain operable;
- no API or business calculation changed;
- no new external dependency or persistent animation loop exists;
- no Git commit, merge or push occurred.

---

## Self-Review Checklist

- [ ] Every specification section maps to one or more tasks.
- [ ] `window.HudSystem` remains the only global HUD behavior surface.
- [ ] Surface ownership stays in `dashboard-design-system.css`.
- [ ] Domain CSS does not redefine global root tokens.
- [ ] Business modules emit semantic events rather than animation classes.
- [ ] Legacy `stzb:hud-pulse` remains compatible during migration.
- [ ] Strong effects are event-driven, deduplicated and bounded.
- [ ] Page states preserve prior content during refresh and non-blocking failure.
- [ ] Glass layers and active animations stay within the stated budgets.
- [ ] Reduced motion and document visibility suppress non-essential effects.
- [ ] All 12 pages, four responsive widths and 200% zoom are tested.
- [ ] No business API, calculation, navigation or compatibility behavior changes.
- [ ] No Git commit, merge or push is executed.

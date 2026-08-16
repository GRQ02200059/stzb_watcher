# 模块化沉浸战场 HUD 全系统实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前 11 个可见 Dashboard 页面统一升级为 B 方案“模块化沉浸 HUD”，保持业务、导航顺序、API 与兼容入口不变。

**Architecture:** 新增深模块 `static/dashboard-hud.mjs`，其小型接口封装领域映射、动效等级、事件脉冲、数值动画和统一状态渲染；`dashboard-design-system.css/js` 负责全局壳层和渐进增强。业务页面通过 `data-visual-domain` 与共享 HUD 类接入，不把视觉状态写入业务模块。

**Tech Stack:** Vanilla JavaScript ES Module、CSS Design Tokens、Flask、Python 3.9+、Node.js test runner、Playwright + 系统 Chrome。

## Global Constraints

- 视觉方向为 **B：模块化沉浸 HUD**。
- 范围仅为当前 11 个可见页面。
- 保留 `208px` 桌面固定侧栏与当前导航顺序。
- 保持 `data-tab-index`、`switchTab()`、业务 API、数据库和兼容入口不变。
- `static/dashboard-design-system.css` 是唯一全局 token 来源。
- 业务 CSS 禁止新增第二套 `:root`。
- 常驻动效只允许连接点呼吸、skeleton 和进度过渡。
- 强动效必须由真实事件或交互触发一次。
- `prefers-reduced-motion: reduce` 强制精简动效。
- 不新增持续 `requestAnimationFrame` 循环。
- 不新增大型前端框架或图表依赖。
- 11 页新增或改造区域不新增主题、布局或字体内联样式。
- 允许内联 CSS 自定义属性表达数据值。
- 不重构隐藏兼容页视觉。
- 不执行 Git commit。

---

## 文件结构

### 新建

```text
static/dashboard-hud.mjs
test/js/dashboard-hud.test.mjs
test/test_dashboard_hud_static.py
```

### 修改

```text
static/dashboard.html
static/dashboard-design-system.css
static/dashboard-design-system.js
static/dashboard-command-center.js
static/intelligence-center.css
static/intelligence-research.css
static/score-center.css
static/simulator-workbench.css
test/test_web_ui_design_system.py
test/test_sidebar_navigation.py
test/test_web_runtime_hardening.py
test/js/dashboard-e2e.mjs
README.md
```

---

### Task 1: 建立 HUD 深模块与领域映射

**Files:**
- Create: `static/dashboard-hud.mjs`
- Create: `test/js/dashboard-hud.test.mjs`
- Modify: `test/test_dashboard_runtime_node.py`

**Interfaces:**
- Produces: `domainForTab(tabId: number) -> string`
- Produces: `normalizeMotionLevel(level: string, reduced: boolean) -> "full" | "standard" | "reduced"`
- Produces: `createHudSystem(options?: object) -> HudSystem`
- Produces: `HudSystem.setDomain(tabId)`
- Produces: `HudSystem.setMotionLevel(level)`
- Produces: `HudSystem.pulse(element, kind)`
- Produces: `HudSystem.animateValue(element, from, to, options)`
- Produces: `HudSystem.renderState(container, state)`
- Produces browser global: `window.HudSystem`

- [ ] **Step 1: Write failing domain and motion tests**

Create `test/js/dashboard-hud.test.mjs`:

```javascript
import assert from "node:assert/strict";
import test from "node:test";

import {
  createHudSystem,
  domainForTab,
  normalizeMotionLevel,
} from "../../static/dashboard-hud.mjs";

test("visible tabs map to the five approved visual domains", () => {
  assert.deepEqual(
    [7, 8, 16, 17, 23, 24, 25, 26, 32, 33, 34].map(domainForTab),
    [
      "organization",
      "analysis",
      "operations",
      "organization",
      "analysis",
      "organization",
      "operations",
      "intelligence",
      "system",
      "intelligence",
      "analysis",
    ],
  );
  assert.equal(domainForTab(31), "compatibility");
});

test("reduced motion always wins and invalid settings use standard", () => {
  assert.equal(normalizeMotionLevel("full", true), "reduced");
  assert.equal(normalizeMotionLevel("full", false), "full");
  assert.equal(normalizeMotionLevel("standard", false), "standard");
  assert.equal(normalizeMotionLevel("bad", false), "standard");
});
```

- [ ] **Step 2: Write failing deep-module behavior tests**

Append:

```javascript
function fakeElement() {
  return {
    dataset: {},
    classList: {
      values: new Set(),
      add(value) { this.values.add(value); },
      remove(value) { this.values.delete(value); },
      contains(value) { return this.values.has(value); },
    },
    textContent: "",
    replaceChildren(...children) { this.children = children; },
  };
}

test("setDomain updates one body seam without business knowledge", () => {
  const body = fakeElement();
  const system = createHudSystem({
    documentRef: { body },
    matchMediaFn: () => ({ matches: false }),
  });

  assert.equal(system.setDomain(25), "operations");
  assert.equal(body.dataset.visualDomain, "operations");
});

test("pulse is one-shot and resets the same kind", () => {
  const element = fakeElement();
  const scheduled = [];
  const system = createHudSystem({
    documentRef: { body: fakeElement() },
    matchMediaFn: () => ({ matches: false }),
    setTimeoutFn(callback) {
      scheduled.push(callback);
      return scheduled.length;
    },
  });

  system.pulse(element, "danger");
  assert.equal(element.classList.contains("hud-pulse-danger"), true);
  scheduled.shift()();
  assert.equal(element.classList.contains("hud-pulse-danger"), false);
});

test("renderState creates stable loading empty and error models", () => {
  const container = fakeElement();
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      createElement(tag) {
        return { tagName: tag, className: "", textContent: "" };
      },
    },
    matchMediaFn: () => ({ matches: false }),
  });

  assert.equal(system.renderState(container, { kind: "loading" }).kind, "loading");
  assert.equal(system.renderState(container, { kind: "empty", message: "暂无" }).message, "暂无");
  assert.equal(system.renderState(container, { kind: "error", message: "失败" }).message, "失败");
});
```

- [ ] **Step 3: Run Node tests and verify RED**

Run:

```bash
node --test test/js/dashboard-hud.test.mjs
```

Expected: FAIL with `ERR_MODULE_NOT_FOUND`.

- [ ] **Step 4: Implement the HUD module**

Create `static/dashboard-hud.mjs`:

```javascript
export const VISIBLE_DOMAINS = Object.freeze({
  7: "organization",
  8: "analysis",
  16: "operations",
  17: "organization",
  23: "analysis",
  24: "organization",
  25: "operations",
  26: "intelligence",
  32: "system",
  33: "intelligence",
  34: "analysis",
});

const VALID_MOTION = new Set(["full", "standard", "reduced"]);

export function domainForTab(tabId) {
  return VISIBLE_DOMAINS[Number(tabId)] || "compatibility";
}

export function normalizeMotionLevel(level, reduced) {
  if (reduced) return "reduced";
  return VALID_MOTION.has(level) ? level : "standard";
}

function stateModel(state) {
  const kind = ["loading", "empty", "error"].includes(state?.kind)
    ? state.kind
    : "empty";
  return {
    kind,
    message:
      state?.message ||
      ({ loading: "正在加载…", empty: "暂无数据", error: "加载失败" })[kind],
    actionLabel: state?.actionLabel || "",
  };
}

export function createHudSystem({
  documentRef = globalThis.document,
  matchMediaFn = globalThis.matchMedia?.bind(globalThis),
  setTimeoutFn = globalThis.setTimeout?.bind(globalThis),
  requestAnimationFrameFn = globalThis.requestAnimationFrame?.bind(globalThis),
} = {}) {
  let motionLevel = normalizeMotionLevel(
    "standard",
    Boolean(matchMediaFn?.("(prefers-reduced-motion: reduce)")?.matches),
  );

  function setDomain(tabId) {
    const domain = domainForTab(tabId);
    if (documentRef?.body) documentRef.body.dataset.visualDomain = domain;
    return domain;
  }

  function setMotionLevel(level) {
    motionLevel = normalizeMotionLevel(
      level,
      Boolean(matchMediaFn?.("(prefers-reduced-motion: reduce)")?.matches),
    );
    if (documentRef?.body) documentRef.body.dataset.motionLevel = motionLevel;
    return motionLevel;
  }

  function pulse(element, kind = "info") {
    if (!element || motionLevel === "reduced") return false;
    const className = `hud-pulse-${kind}`;
    element.classList.remove(className);
    element.classList.add(className);
    setTimeoutFn?.(() => element.classList.remove(className), 720);
    return true;
  }

  function animateValue(element, from, to, { duration = 360, formatter } = {}) {
    const format = formatter || ((value) => Math.round(value).toLocaleString("zh-CN"));
    if (!element) return Promise.resolve(to);
    if (motionLevel === "reduced" || !requestAnimationFrameFn) {
      element.textContent = format(to);
      return Promise.resolve(to);
    }
    return new Promise((resolve) => {
      const started = performance.now();
      function frame(now) {
        const progress = Math.min(1, (now - started) / duration);
        const eased = 1 - Math.pow(1 - progress, 3);
        element.textContent = format(from + (to - from) * eased);
        if (progress < 1) requestAnimationFrameFn(frame);
        else resolve(to);
      }
      requestAnimationFrameFn(frame);
    });
  }

  function renderState(container, state) {
    const model = stateModel(state);
    if (!container || !documentRef?.createElement) return model;
    const element = documentRef.createElement("div");
    element.className = `hud-state hud-state-${model.kind}`;
    element.textContent = model.message;
    container.replaceChildren(element);
    return model;
  }

  return {
    setDomain,
    setMotionLevel,
    pulse,
    animateValue,
    renderState,
    get motionLevel() { return motionLevel; },
  };
}

const defaultSystem =
  typeof document === "undefined" ? null : createHudSystem();

if (typeof window !== "undefined" && defaultSystem) {
  window.HudSystem = defaultSystem;
}
```

- [ ] **Step 5: Wire the Node test runner**

Modify `test/test_dashboard_runtime_node.py`:

```python
    def test_hud_runtime_behavior(self):
        result = subprocess.run(
            ["node", "--test", "test/js/dashboard-hud.test.mjs"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            result.returncode,
            0,
            msg=f"{result.stdout}\n{result.stderr}",
        )
```

- [ ] **Step 6: Run Task 1 GREEN**

Run:

```bash
node --check static/dashboard-hud.mjs
node --test test/js/dashboard-hud.test.mjs
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_dashboard_runtime_node -v
```

Expected: all tests pass.

---

### Task 2: 建立全局 token、HUD 壳层与分组侧栏

**Files:**
- Modify: `static/dashboard-design-system.css`
- Modify: `static/dashboard-design-system.js`
- Modify: `static/dashboard.html`
- Create: `test/test_dashboard_hud_static.py`
- Modify: `test/test_web_ui_design_system.py`
- Modify: `test/test_sidebar_navigation.py`
- Modify: `test/test_web_runtime_hardening.py`

**Interfaces:**
- Consumes: Task 1 `HudSystem`
- Produces: domain attributes for 11 pages
- Produces: grouped navigation labels without changing button order
- Produces shared CSS interfaces: `hud-page-head`, `hud-panel`, `hud-kpi`, `hud-toolbar`, `hud-table-shell`, `hud-status-chip`

- [ ] **Step 1: Write failing static shell tests**

Create `test/test_dashboard_hud_static.py`:

```python
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HTML = ROOT / "static/dashboard.html"
CSS = ROOT / "static/dashboard-design-system.css"
DS_JS = ROOT / "static/dashboard-design-system.js"
HUD_JS = ROOT / "static/dashboard-hud.mjs"

VISIBLE_DOMAINS = {
    7: "organization",
    8: "analysis",
    16: "operations",
    17: "organization",
    23: "analysis",
    24: "organization",
    25: "operations",
    26: "intelligence",
    32: "system",
    33: "intelligence",
    34: "analysis",
}


class DashboardHudStaticTest(unittest.TestCase):
    def test_visible_pages_have_the_approved_domains(self):
        html = HTML.read_text(encoding="utf-8")
        for tab_id, domain in VISIBLE_DOMAINS.items():
            self.assertRegex(
                html,
                rf'id=["\']tab{tab_id}["\'][^>]*'
                rf'data-visual-domain=["\']{domain}["\']',
            )

    def test_dashboard_loads_the_versioned_hud_module(self):
        html = HTML.read_text(encoding="utf-8")
        self.assertIn(
            'type="module" src="/static/dashboard-hud.mjs"',
            html,
        )
        self.assertTrue(HUD_JS.is_file())

    def test_shared_hud_components_and_tokens_exist(self):
        css = CSS.read_text(encoding="utf-8")
        for token in (
            "--domain-intelligence",
            "--domain-operations",
            "--domain-organization",
            "--domain-analysis",
            "--domain-system",
            "--surface-glass",
            "--surface-elevated",
            "--shadow-hud",
            "--motion-fast",
            "--motion-standard",
            "--motion-slow",
        ):
            self.assertIn(token, css)
        for selector in (
            ".hud-page-head",
            ".hud-panel",
            ".hud-kpi-grid",
            ".hud-kpi",
            ".hud-toolbar",
            ".hud-table-shell",
            ".hud-status-chip",
            ".hud-state",
        ):
            self.assertIn(selector, css)
```

- [ ] **Step 2: Write failing grouped-navigation tests**

Append:

```python
    def test_navigation_has_visual_groups_without_more_menu(self):
        script = DS_JS.read_text(encoding="utf-8")
        for label in (
            "INTELLIGENCE",
            "OPERATIONS",
            "ORGANIZATION",
            "ANALYSIS",
            "SYSTEM",
        ):
            self.assertIn(label, script)
        self.assertIn("ds-nav-group", script)
        self.assertNotIn("ds-nav-more", script)

    def test_body_has_one_main_landmark(self):
        html = HTML.read_text(encoding="utf-8")
        self.assertEqual(len(re.findall(r"<main\b", html)), 1)
        self.assertIn('id="dashboard-main"', html)
```

- [ ] **Step 3: Run static tests and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_dashboard_hud_static -v
```

Expected: FAIL on missing page domains, HUD module, tokens and groups.

- [ ] **Step 4: Add domain attributes and the main landmark**

Modify the 11 page elements in `static/dashboard.html`:

```html
<div class="page hud-page" id="tab7" data-visual-domain="organization">
<div class="page hud-page" id="tab8" data-visual-domain="analysis">
<div class="page hud-page" id="tab16" data-visual-domain="operations">
<div class="page hud-page" id="tab17" data-visual-domain="organization">
<div class="page hud-page" id="tab23" data-visual-domain="analysis">
<div class="page hud-page" id="tab24" data-visual-domain="organization">
<div class="page hud-page" id="tab25" data-visual-domain="operations">
<div class="page hud-page" id="tab26" data-visual-domain="intelligence">
<div class="page hud-page" id="tab32" data-visual-domain="system">
<div class="page hud-page active" id="tab33" data-visual-domain="intelligence">
<div class="page hud-page" id="tab34" data-visual-domain="analysis">
```

Wrap all page elements in:

```html
<main id="dashboard-main" tabindex="-1">
  ...all .page elements...
</main>
```

Keep dialogs and the fixed Query Agent outside `main`.

Load before business modules:

```html
<script type="module" src="/static/dashboard-hud.mjs"></script>
```

- [ ] **Step 5: Add approved tokens**

In the existing `:root` of `dashboard-design-system.css` add:

```css
--domain-intelligence: #38bdf8;
--domain-operations: #f05267;
--domain-organization: #34d399;
--domain-analysis: #8b6cff;
--domain-system: #f5b84b;
--surface-glass: rgba(12, 20, 48, 0.82);
--surface-elevated: #101c3d;
--border-glow: rgba(56, 189, 248, 0.36);
--shadow-hud: 0 18px 52px rgba(0, 5, 22, 0.46);
--shadow-float: 0 24px 70px rgba(0, 5, 22, 0.62);
--motion-fast: 160ms;
--motion-standard: 240ms;
--motion-slow: 360ms;
```

Define the body seam:

```css
body[data-visual-domain="intelligence"] { --domain-accent: var(--domain-intelligence); }
body[data-visual-domain="operations"] { --domain-accent: var(--domain-operations); }
body[data-visual-domain="organization"] { --domain-accent: var(--domain-organization); }
body[data-visual-domain="analysis"] { --domain-accent: var(--domain-analysis); }
body[data-visual-domain="system"] { --domain-accent: var(--domain-system); }
body[data-visual-domain="compatibility"] { --domain-accent: var(--primary); }
```

- [ ] **Step 6: Add shared HUD CSS**

Add exact component shells:

```css
.hud-page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding: 16px 18px;
  background:
    radial-gradient(circle at 84% 18%, color-mix(in srgb, var(--domain-accent) 16%, transparent), transparent 34%),
    linear-gradient(135deg, var(--surface-2), var(--surface-1));
  border: 1px solid color-mix(in srgb, var(--domain-accent) 36%, var(--border-subtle));
  border-radius: 12px;
  box-shadow: inset 0 0 36px color-mix(in srgb, var(--domain-accent) 5%, transparent);
}
.hud-page-kicker {
  color: var(--domain-accent);
  font: 700 10px var(--font-mono);
  letter-spacing: .2em;
}
.hud-page-title { margin: 4px 0; color: var(--text-primary); font-size: 22px; }
.hud-page-summary { margin: 0; color: var(--text-tertiary); font-size: 12px; }
.hud-page-actions, .hud-toolbar, .hud-toolbar-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.hud-panel {
  overflow: hidden;
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 11px;
  box-shadow: var(--shadow-sm);
}
.hud-panel-glass { background: var(--surface-glass); backdrop-filter: blur(14px); }
.hud-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 45px;
  padding: 10px 13px;
  border-bottom: 1px solid var(--border-subtle);
}
.hud-kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 10px;
}
.hud-kpi {
  position: relative;
  overflow: hidden;
  padding: 14px;
  background: linear-gradient(145deg, var(--surface-1), var(--surface-2));
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
}
.hud-kpi::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 2px;
  content: "";
  background: var(--hud-kpi-accent, var(--domain-accent));
  box-shadow: 0 0 14px var(--hud-kpi-accent, var(--domain-accent));
}
.hud-kpi-label { color: var(--text-tertiary); font-size: 10px; }
.hud-kpi-value { margin-top: 5px; color: var(--text-primary); font: 700 24px var(--font-mono); }
.hud-table-shell { overflow: hidden; border: 1px solid var(--border-subtle); border-radius: 11px; }
.hud-table-scroll { overflow: auto; max-height: min(68vh, 650px); }
.hud-status-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 24px;
  padding: 3px 8px;
  border: 1px solid var(--border-subtle);
  border-radius: 999px;
  font: 9px var(--font-mono);
}
.hud-state {
  display: grid;
  min-height: 140px;
  place-items: center;
  padding: 20px;
  color: var(--text-tertiary);
  text-align: center;
}
```

Add responsive rules at existing breakpoints.

- [ ] **Step 7: Group the flat navigation progressively**

In `dashboard-design-system.js` define:

```javascript
const NAV_GROUPS = [
  { label: "INTELLIGENCE", tabs: [33, 26] },
  { label: "OPERATIONS", tabs: [25, 16] },
  { label: "ORGANIZATION", tabs: [7, 17, 24] },
  { label: "ANALYSIS", tabs: [8, 23, 34] },
  { label: "SYSTEM", tabs: [32] },
];
```

Do not reorder buttons. Insert a label before the first matching button only:

```javascript
function addNavigationGroups(nav) {
  const byTab = new Map(
    [...nav.querySelectorAll("[data-tab-index]")].map(
      (button) => [Number(button.dataset.tabIndex), button],
    ),
  );
  NAV_GROUPS.forEach((group) => {
    const first = group.tabs.map((tab) => byTab.get(tab)).find(Boolean);
    if (!first) return;
    const label = document.createElement("div");
    label.className = "ds-nav-group";
    label.textContent = group.label;
    first.before(label);
  });
}
```

Call after `enhanceNavigation(nav)`.

- [ ] **Step 8: Wire domain updates**

In `syncActiveState()`:

```javascript
window.HudSystem?.setDomain(activeIndex);
```

Because the HUD module is type module and may load after the classic script, also dispatch:

```javascript
window.dispatchEvent(
  new CustomEvent("stzb:tab-changed", { detail: { tabId: activeIndex } }),
);
```

In `dashboard-hud.mjs`, browser initialization listens for this event and calls `setDomain`.

- [ ] **Step 9: Update accessibility**

Change skip link:

```javascript
skip.href = "#dashboard-main";
```

Do not assign `role=main` to pages. Keep pages `role=region`.

- [ ] **Step 10: Extend mtime coverage**

Add `"dashboard-hud.mjs"` to the asset list in
`test/test_web_runtime_hardening.py`.

- [ ] **Step 11: Run Task 2 GREEN**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_dashboard_hud_static \
  test.test_web_ui_design_system \
  test.test_sidebar_navigation \
  test.test_web_runtime_hardening.WebRuntimeHardeningTest.test_index_rewrites_local_asset_versions_from_file_mtime -v

node --test test/js/dashboard-hud.test.mjs
```

Expected: all tests pass and visible navigation order remains unchanged.

---

### Task 3: 统一动效设置、事件脉冲与设置健康面板

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/dashboard-command-center.js`
- Modify: `static/dashboard-hud.mjs`
- Modify: `static/dashboard-design-system.css`
- Modify: `api_server.py`
- Create: `test/test_dashboard_hud_api.py`
- Modify: `test/js/dashboard-hud.test.mjs`
- Modify: `test/js/dashboard-e2e.mjs`

**Interfaces:**
- Consumes: Task 1 `HudSystem`
- Produces setting values: `full`, `standard`, `reduced`
- Produces read-only endpoint: `GET /api/hud/health`
- Produces event convention: `stzb:hud-pulse`

- [ ] **Step 1: Write failing motion persistence tests**

Append to Node tests:

```javascript
test("setMotionLevel writes the normalized body seam", () => {
  const body = fakeElement();
  const system = createHudSystem({
    documentRef: { body },
    matchMediaFn: () => ({ matches: false }),
  });

  assert.equal(system.setMotionLevel("full"), "full");
  assert.equal(body.dataset.motionLevel, "full");
  assert.equal(system.setMotionLevel("bad"), "standard");
});

test("reduced motion suppresses pulses", () => {
  const element = fakeElement();
  const system = createHudSystem({
    documentRef: { body: fakeElement() },
    matchMediaFn: () => ({ matches: true }),
  });

  assert.equal(system.pulse(element, "danger"), false);
  assert.equal(element.classList.values.size, 0);
});
```

- [ ] **Step 2: Write failing health endpoint tests**

Create `test/test_dashboard_hud_api.py`:

```python
import unittest
from unittest.mock import patch

import api_server


class DashboardHudApiTest(unittest.TestCase):
    def test_hud_health_reports_optional_components_without_500(self):
        with patch.object(api_server, "_writer") as writer:
            writer.stats = {"errors": 2, "battles": 3}
            response = api_server.app.test_client().get("/api/hud/health")

        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertTrue(body["ok"])
        self.assertEqual("degraded", body["overall"])
        self.assertIn("backend", body["components"])
        self.assertIn("writer", body["components"])
        self.assertIn("battleEngine", body["components"])
        self.assertIn("portraits", body["components"])

    def test_hud_health_degrades_when_optional_manifest_is_missing(self):
        with patch("api_server.os.path.isfile", return_value=False):
            response = api_server.app.test_client().get("/api/hud/health")

        self.assertEqual(200, response.status_code)
        self.assertEqual(
            "unknown",
            response.get_json()["components"]["portraits"]["status"],
        )
```

- [ ] **Step 3: Run tests and verify RED**

Run:

```bash
node --test test/js/dashboard-hud.test.mjs
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_dashboard_hud_api -v
```

Expected: Node test fails if motion seam is incomplete; Python returns 404.

- [ ] **Step 4: Upgrade settings control**

Change `#cc-setting-motion`:

```html
<select id="cc-setting-motion">
  <option value="full">完整</option>
  <option value="standard">标准</option>
  <option value="reduced">精简</option>
</select>
```

In `dashboard-command-center.js`:

```javascript
const DEFAULT_SETTINGS = {
  ...,
  motion: "standard",
};
```

When applying settings:

```javascript
document.body.dataset.motion = settings.motion;
window.HudSystem?.setMotionLevel(settings.motion);
```

Migrate old `"reduced"` unchanged and old `"full"` unchanged.

- [ ] **Step 5: Implement read-only health endpoint**

Add to `api_server.py`:

```python
def _health_component(status, label, detail="", **extra):
    return {
        "status": status,
        "label": label,
        "detail": detail,
        **extra,
    }


@app.route("/api/hud/health", methods=["GET"])
def api_hud_health():
    writer_stats = dict(getattr(_writer, "stats", {}) or {})
    writer_errors = int(writer_stats.get("errors") or 0)
    engine_path = (
        Path(BASE_DIR)
        / "battle-engine/build/install/stzb-battle-engine/bin/stzb-battle-engine"
    )
    portrait_manifest = os.path.join(
        RESOURCE_DIR, "static", "hero-portraits", "manifest.json"
    )
    components = {
        "backend": _health_component("live", "后端", "Flask API 可用"),
        "writer": _health_component(
            "degraded" if writer_errors else "live",
            "实时入库",
            f"errors={writer_errors}",
            stats=writer_stats,
        ),
        "battleEngine": _health_component(
            "live" if engine_path.is_file() else "unknown",
            "Kotlin 引擎",
            str(engine_path),
        ),
        "portraits": _health_component(
            "live" if os.path.isfile(portrait_manifest) else "unknown",
            "画像资源",
            portrait_manifest,
        ),
    }
    statuses = {row["status"] for row in components.values()}
    overall = (
        "degraded"
        if "degraded" in statuses
        else "live"
        if statuses == {"live"}
        else "unknown"
    )
    return jsonify({
        "ok": True,
        "overall": overall,
        "components": components,
    })
```

Import `Path` from `pathlib`.

- [ ] **Step 6: Add settings health markup**

In tab 32 add:

```html
<section class="hud-panel hud-health-panel">
  <div class="hud-panel-head">
    <div>
      <span class="hud-page-kicker">RUNTIME HEALTH</span>
      <h3 class="hud-panel-title">运行链路</h3>
    </div>
    <button class="btn" id="hud-health-refresh" type="button">刷新</button>
  </div>
  <div class="hud-health-grid" id="hud-health-grid">
    <div class="hud-state hud-state-loading">正在检查链路…</div>
  </div>
</section>
```

Add `static/dashboard-hud.mjs` browser method:

```javascript
async function loadHealth(fetchFn = fetch) {
  const container = documentRef.getElementById?.("hud-health-grid");
  if (!container) return null;
  renderState(container, { kind: "loading" });
  try {
    const response = await fetchFn("/api/hud/health", { cache: "no-store" });
    const body = await response.json();
    if (!response.ok || !body.ok) throw new Error(body.error || "health failed");
    container.innerHTML = Object.values(body.components)
      .map((row) => `
        <article class="hud-health-card" data-status="${row.status}">
          <span class="hud-status-chip">${row.status.toUpperCase()}</span>
          <strong>${row.label}</strong>
          <small>${row.detail}</small>
        </article>
      `)
      .join("");
    return body;
  } catch (error) {
    renderState(container, { kind: "error", message: error.message });
    return null;
  }
}
```

Include `loadHealth` in the module interface and bind refresh.

- [ ] **Step 7: Add one-shot event convention**

In `dashboard-hud.mjs`:

```javascript
window.addEventListener("stzb:hud-pulse", (event) => {
  const selector = event.detail?.selector;
  if (!selector) return;
  pulse(document.querySelector(selector), event.detail?.kind || "info");
});
```

In existing events:

```javascript
window.dispatchEvent(new CustomEvent("stzb:hud-pulse", {
  detail: { selector: "#sim-result-summary", kind: "success" },
}));
```

Add this only for:

- simulation completed;
- new timeline battle;
- high-risk intelligence refresh;
- score recalculation completion.

- [ ] **Step 8: Add CSS motion levels**

```css
body[data-motion-level="reduced"] *,
body[data-motion-level="reduced"] *::before,
body[data-motion-level="reduced"] *::after {
  animation-duration: .001ms !important;
  animation-iteration-count: 1 !important;
  transition-duration: .001ms !important;
  scroll-behavior: auto !important;
}
.hud-pulse-info { animation: hud-pulse-info 720ms ease-out 1; }
.hud-pulse-danger { animation: hud-pulse-danger 720ms ease-out 1; }
.hud-pulse-success { animation: hud-pulse-success 720ms ease-out 1; }
```

- [ ] **Step 9: Run Task 3 GREEN**

Run:

```bash
node --test test/js/dashboard-hud.test.mjs
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_dashboard_hud_api \
  test.test_command_center_static \
  test.test_web_runtime_hardening -v
```

Expected: all tests pass.

---

### Task 4: 将战场情报与州郡分布做成示范情报域

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/intelligence-center.css`
- Modify: `static/intelligence-center.js`
- Modify: `static/dashboard-design-system.css`
- Modify: `test/test_intelligence_center_static.py`
- Modify: `test/js/dashboard-e2e.mjs`

**Interfaces:**
- Consumes shared HUD classes
- Preserves: `IntelligenceCenter` public methods
- Produces page heads, HUD toolbar, HUD map shell and collapsible detail sections

- [ ] **Step 1: Write failing intelligence-domain static tests**

Append to `test/test_intelligence_center_static.py`:

```python
    def test_intelligence_pages_use_shared_hud_shells(self):
        html = self.dashboard
        tab33 = html.split('id="tab33"', 1)[1].split('id="tab34"', 1)[0]
        tab26 = html.split("id='tab26'", 1)[1].split("id='tab27'", 1)[0]
        for token in (
            "hud-page-head",
            "hud-toolbar",
            "hud-panel",
            "hud-status-chip",
        ):
            self.assertIn(token, tab33 + tab26)
        self.assertIn("hud-map-shell", tab33)
        self.assertIn("hud-detail-section", tab33)
```

- [ ] **Step 2: Add failing E2E domain checks**

After tab 33 opens:

```javascript
assert.equal(
  await page.locator("body").getAttribute("data-visual-domain"),
  "intelligence",
);
assert.equal(await page.locator("#tab33 > .hud-page-head").isVisible(), true);
assert.equal(await page.locator("#intel-view-map .hud-map-shell").isVisible(), true);
```

After tab 26:

```javascript
await page.evaluate(() => window.switchTab(26, null));
await page.waitForSelector("#tab26.active");
assert.equal(
  await page.locator("body").getAttribute("data-visual-domain"),
  "intelligence",
);
```

- [ ] **Step 3: Run tests and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_intelligence_center_static \
  test.test_dashboard_e2e -v
```

Expected: FAIL on missing HUD classes.

- [ ] **Step 4: Add semantic page heads**

Tab 33:

```html
<header class="hud-page-head">
  <div>
    <span class="hud-page-kicker">WORLD INTELLIGENCE / LIVE TACTICAL LAYER</span>
    <h2 class="hud-page-title">战场情报 · 全域态势</h2>
    <p class="hud-page-summary">统一 WorldState、热区、真实格子、行军、部队与证据时间线。</p>
  </div>
  <div class="hud-page-actions">
    <span class="hud-status-chip" id="hud-world-freshness">STATE UNKNOWN</span>
    <button class="btn btn-primary" type="button" onclick="loadIntelligenceCenter()">刷新情报</button>
  </div>
</header>
```

Tab 26:

```html
<header class="hud-page-head">
  <div>
    <span class="hud-page-kicker">REGIONAL DISTRIBUTION / INTELLIGENCE</span>
    <h2 class="hud-page-title">州郡分布</h2>
    <p class="hud-page-summary">观察州级人数、势力、同盟和分组分布。</p>
  </div>
  <div class="hud-page-actions">
    <span class="hud-status-chip" id="hud-region-updated">WAITING</span>
    <button class="btn btn-primary" type="button" onclick="loadStateRegionStats()">刷新</button>
  </div>
</header>
```

- [ ] **Step 5: Migrate toolbar and panels**

For tab 33:

- `.intel-toolbar` also gets `.hud-toolbar`;
- `.intel-workspace` also gets `.hud-panel`;
- `.intel-map-wrap` also gets `.hud-map-shell`;
- `.intel-detail` also gets `.hud-panel`;
- `.intel-timeline` also gets `.hud-panel`.

For tab 26:

- replace the top inline toolbar with `.hud-toolbar`;
- convert visible `.tbl-wrap` to `.hud-panel`;
- convert cards to `.hud-kpi-grid`.

- [ ] **Step 6: Add collapsible detail sections**

In intelligence detail rendering use native `details`:

```html
<details class="hud-detail-section" open>
  <summary>风险解释</summary>
  <div class="hud-detail-section-body">...</div>
</details>
```

Use for risk components, armies, battles and evidence. Preserve content and tab behavior.

- [ ] **Step 7: Update freshness chips and pulse**

In `intelligence-center.js`, after summary load:

```javascript
const freshness = document.getElementById("hud-world-freshness");
if (freshness) {
  freshness.textContent = `${data.freshness.toUpperCase()} · v${data.worldStateVersion}`;
  freshness.dataset.status = data.freshness === "fresh" ? "live" : "degraded";
}
if ((data.risks || []).some((risk) => risk.level === "high")) {
  window.dispatchEvent(new CustomEvent("stzb:hud-pulse", {
    detail: { selector: "#intel-detail-panel", kind: "danger" },
  }));
}
```

- [ ] **Step 8: Implement intelligence-domain CSS**

Update `intelligence-center.css` to consume tokens only. Add:

```css
.hud-map-shell::before,
.hud-map-shell::after { ...corner marks... }
.intel-radar { border-color: var(--domain-analysis); box-shadow: ...; }
.hud-detail-section { border-bottom: 1px solid var(--border-subtle); }
.hud-detail-section summary { ... }
```

Remove raw theme colors from modified selectors when a semantic token exists.

- [ ] **Step 9: Run Task 4 GREEN**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_intelligence_center_static \
  test.test_intelligence_map_node \
  test.test_dashboard_e2e -v
```

Expected: all tests pass.

---

### Task 5: 迁移作战域——模拟器与打城考勤

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/simulator-workbench.css`
- Modify: `static/simulator-workbench.js`
- Modify: `static/app2.js`
- Create: `static/operations-hud.css`
- Create: `test/test_operations_hud_static.py`
- Modify: `test/js/dashboard-e2e.mjs`

**Interfaces:**
- Preserves `window.StzbSimulator`
- Preserves attendance API and task functions
- Produces shared operation-stage markup and styles

- [ ] **Step 1: Write failing operations-domain tests**

Create `test/test_operations_hud_static.py`:

```python
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class OperationsHudStaticTest(unittest.TestCase):
    def setUp(self):
        self.html = (ROOT / "static/dashboard.html").read_text(encoding="utf-8")
        self.css = (ROOT / "static/operations-hud.css").read_text(encoding="utf-8") \
            if (ROOT / "static/operations-hud.css").is_file() else ""

    def test_operations_pages_have_hud_heads_and_panels(self):
        tab16 = self.html.split("id='tab16'", 1)[1].split("id='tab17'", 1)[0]
        tab25 = self.html.split('id="tab25"', 1)[1].split("id='tab26'", 1)[0]
        for token in ("hud-page-head", "hud-panel"):
            self.assertIn(token, tab16 + tab25)
        self.assertIn("operation-stage", tab16 + self.css)

    def test_operations_css_has_no_root_tokens(self):
        self.assertNotIn(":root", self.css)
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_operations_hud_static -v
```

Expected: FAIL because operations CSS and markup are missing.

- [ ] **Step 3: Add operations stylesheet**

Load:

```html
<link rel="stylesheet" href="/static/operations-hud.css">
```

Create semantic classes:

```css
.operations-task-grid { ... }
.operation-stage { ... }
.operation-stage[data-state="complete"] { ... }
.operation-progress { ... }
.operation-target { ... }
.operation-member-chip { ... }
.operation-battle-feed { ... }
```

Use existing domain tokens, no `:root`.

- [ ] **Step 4: Add page heads**

Tab 16:

```html
<header class="hud-page-head">
  <div>
    <span class="hud-page-kicker">SIEGE OPERATION / ATTENDANCE</span>
    <h2 class="hud-page-title">打城考勤</h2>
    <p class="hud-page-summary">管理攻城任务、集结成员、执行战报与出勤结果。</p>
  </div>
  <div class="hud-page-actions">
    <button class="btn btn-primary" type="button" onclick="openCreateTask()">新建任务</button>
    <button class="btn" type="button" onclick="loadTasks()">刷新</button>
  </div>
</header>
```

Tab 25 keeps its existing simulator header but wraps it with `hud-panel` conventions and domain tokens.

- [ ] **Step 5: Add task stage projection**

In `app2.js` define pure:

```javascript
function attendanceStage(task) {
  const now = Date.now() / 1000;
  const taskTime = Number(task.task_time || task.time || 0);
  if (task.statistics_done) return "complete";
  if (taskTime && taskTime <= now) return "executing";
  if (Number(task.actual_count || 0) > 0) return "assembling";
  return "preparing";
}
```

Render stage strip:

```html
<div class="operation-stage-strip">
  ${["preparing", "assembling", "executing", "complete"].map(...)}
</div>
```

Do not alter task state on the server.

- [ ] **Step 6: Bind simulator completion pulse**

After simulation result:

```javascript
window.dispatchEvent(new CustomEvent("stzb:hud-pulse", {
  detail: { selector: "#sim-result-summary", kind: "success" },
}));
```

Simulator CSS consumes global motion level:

```css
body[data-motion-level="standard"] .sim-hero-scan { display: none; }
body[data-motion-level="reduced"] .sim-hero-scan { display: none; }
```

- [ ] **Step 7: Extend E2E**

Verify:

- body domain is `operations` on tabs 16 and 25;
- attendance stage strip exists;
- simulator images remain visible;
- reduced motion hides scan;
- simulation completion adds and removes one pulse class.

- [ ] **Step 8: Run Task 5 GREEN**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_operations_hud_static \
  test.test_battle_simulator_static \
  test.test_simulate_api \
  test.test_dashboard_e2e -v

node --test test/js/simulator-workbench.test.mjs
```

Expected: all tests pass.

---

### Task 6: 迁移组织域——玩家、同盟成员与团数据

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/app2.js`
- Create: `static/organization-hud.css`
- Create: `test/test_organization_hud_static.py`
- Modify: `test/js/dashboard-e2e.mjs`

**Interfaces:**
- Preserves player/team/report loaders
- Produces shared classes for identity, team composition and group stats

- [ ] **Step 1: Write failing organization-domain tests**

Create test checking tabs 7, 17 and 24 for:

```text
hud-page-head
hud-toolbar
hud-panel
organization-identity
organization-lineup
organization-group-chip
```

Also assert `organization-hud.css` has no `:root`.

- [ ] **Step 2: Run RED**

Run the new test. Expected: FAIL.

- [ ] **Step 3: Add organization page heads**

Use:

```text
PLAYER TEAMS / ORGANIZATION
ALLIANCE LINEUPS / ORGANIZATION
GROUP PERFORMANCE / ORGANIZATION
```

Preserve existing controls and IDs inside `.hud-toolbar`.

- [ ] **Step 4: Add organization shared CSS**

Create:

```css
.organization-identity { ... }
.organization-avatar { ... }
.organization-lineup { ... }
.organization-hero-mini { ... }
.organization-group-chip { ... }
.organization-activity { ... }
```

Use local portrait thumbnails only where existing data provides hero IDs.

- [ ] **Step 5: Enhance rows without changing data**

In renderers:

- wrap player names in `.organization-identity`;
- hero IDs/names in `.organization-lineup`;
- alliance/group in `.organization-group-chip`;
- use `.hud-status-chip` for side and freshness.

- [ ] **Step 6: Remove migrated top-level inline layout**

For tabs 7, 17 and 24, replace top-level inline layout and theme styles with classes. Do not rewrite hidden pages.

- [ ] **Step 7: Extend E2E**

Verify domains, page heads, team rows and no document overflow at tablet/mobile.

- [ ] **Step 8: Run Task 6 GREEN**

Run organization static tests, related API/static tests and Chrome E2E.

---

### Task 7: 迁移分析域——积分、武将阵容与研究

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/score-center.css`
- Modify: `static/score-center.js`
- Modify: `static/intelligence-research.css`
- Modify: `static/intelligence-research.js`
- Create: `static/analysis-hud.css`
- Create: `test/test_analysis_hud_static.py`
- Modify: `test/js/dashboard-e2e.mjs`

**Interfaces:**
- Preserves `ScoreCenter`, `ResearchCenter` and lineup handoff
- Produces evidence and ranking visual interfaces

- [ ] **Step 1: Write failing analysis-domain tests**

Check tabs 8, 23 and 34 for:

```text
hud-page-head
hud-panel
analysis-evidence
analysis-rank
analysis-lineup-card
```

Assert no `:root` in `analysis-hud.css`.

- [ ] **Step 2: Run RED**

Run new test. Expected: FAIL.

- [ ] **Step 3: Add page heads**

Use:

```text
SCORING ENGINE / EXPLAINABLE ANALYSIS
LINEUP RANKING / BATTLE EVIDENCE
CONFIGURATION RESEARCH / EVIDENCE LAYERS
```

- [ ] **Step 4: Add analysis evidence interfaces**

```css
.analysis-evidence[data-kind="config"] { --evidence-accent: var(--domain-analysis); }
.analysis-evidence[data-kind="history"] { --evidence-accent: var(--domain-organization); }
.analysis-evidence[data-kind="simulation"] { --evidence-accent: var(--domain-operations); }
.analysis-rank[data-rank="1"] { --rank-accent: #f5b84b; }
.analysis-rank[data-rank="2"] { --rank-accent: #b8c4d8; }
.analysis-rank[data-rank="3"] { --rank-accent: #c98555; }
```

No emoji medals.

- [ ] **Step 5: Trigger one-shot score pulse**

After confirmed recalculation:

```javascript
window.dispatchEvent(new CustomEvent("stzb:hud-pulse", {
  detail: { selector: "#score-board", kind: "success" },
}));
```

- [ ] **Step 6: Standardize research details**

Use one detail shell for hero, skill, lineup and card-pack:

```text
analysis-detail-head
analysis-evidence-row
analysis-fact-grid
analysis-related
```

Preserve all buttons and navigation.

- [ ] **Step 7: Migrate combo ranking**

Add top lineup cards above the table and retain the full table. Use historical sample size and win-rate values only.

- [ ] **Step 8: Extend E2E**

Verify:

- analysis domain;
- evidence kinds;
- no emoji medal;
- score preview/recalc unchanged;
- research handoff to simulator unchanged;
- one-shot ranking pulse.

- [ ] **Step 9: Run Task 7 GREEN**

Run score, research, lineup, static and Chrome tests.

---

### Task 8: 收敛设置、内联样式、性能与全量回归

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/dashboard-design-system.css`
- Modify: `static/dashboard-hud.mjs`
- Modify: `README.md`
- Modify: `test/test_dashboard_css_structure.py`
- Modify: `test/test_web_runtime_hardening.py`
- Modify: `test/js/dashboard-e2e.mjs`

**Interfaces:**
- Verifies all previous task interfaces
- Produces final 11-page HUD system

- [ ] **Step 1: Write failing migrated-inline-style audit**

Add a test that extracts tabs:

```python
VISIBLE_TABS = (7, 8, 16, 17, 23, 24, 25, 26, 32, 33, 34)
```

For each:

- reject inline `background:`, `color:`, `font-`, `display:grid/flex`, `border:` in newly migrated page heads, toolbars and panel shells;
- allow `style="--..."` data variables;
- keep a small explicit allowlist for legacy chart dimensions not migrated yet.

- [ ] **Step 2: Run audit and verify RED**

Expected: remaining visible-page inline theme/layout styles are reported.

- [ ] **Step 3: Replace remaining migrated inline styles**

Move them to:

```text
dashboard-design-system.css
operations-hud.css
organization-hud.css
analysis-hud.css
intelligence-center.css
```

Do not touch hidden compatibility pages unless required to keep HTML nesting valid.

- [ ] **Step 4: Add no-feature-detection fallbacks**

```css
@supports not (backdrop-filter: blur(1px)) {
  .hud-panel-glass,
  .hud-page-head,
  .sim-hero-glass { background: var(--surface-elevated); }
}
@supports not (color: color-mix(in srgb, white, black)) {
  .hud-page-head,
  .hud-panel,
  .hud-kpi { border-color: var(--border-strong); }
}
```

- [ ] **Step 5: Enforce performance budget**

Test:

```python
self.assertLess((ROOT / "static/dashboard-hud.mjs").stat().st_size, 25_000)
```

For global added HUD CSS, either keep additions under 35KB or document exact combined size.

Assert no `requestAnimationFrame` loop exists outside `animateValue`.

- [ ] **Step 6: Complete Chrome responsive matrix**

Run at:

```text
1440×1000
1024×900
768×900
390×844
```

For each visible tab:

- activates;
- body domain is correct;
- no page error;
- no 500;
- document horizontal overflow ≤ 1px;
- page head visible;
- primary controls reachable.

Run a separate browser context with `reducedMotion: "reduce"` and assert:

- no `hud-pulse-*` remains;
- no portrait scan animation;
- no transformed hover card.

- [ ] **Step 7: Update README**

Document:

- five visual domains;
- HUD module interface;
- motion levels;
- health endpoint;
- domain attributes;
- responsive behavior;
- reduced-motion;
- no framework / no continuous animation constraints.

- [ ] **Step 8: Run focused validation**

```bash
node --check static/dashboard-hud.mjs
node --test \
  test/js/dashboard-hud.test.mjs \
  test/js/dashboard-runtime.test.mjs \
  test/js/simulator-workbench.test.mjs

PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_dashboard_hud_static \
  test.test_dashboard_hud_api \
  test.test_operations_hud_static \
  test.test_organization_hud_static \
  test.test_analysis_hud_static \
  test.test_web_ui_design_system \
  test.test_sidebar_navigation \
  test.test_dashboard_e2e -v
```

- [ ] **Step 9: Run full repository validation**

```bash
git diff --check

PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest discover -s test -v
```

- [ ] **Step 10: Restart and verify port 8080**

Restart only the current `api_server.py` listener and verify:

```bash
curl -fsS http://127.0.0.1:8080/api/hud/health
curl -fsS http://127.0.0.1:8080/ | rg 'dashboard-hud.mjs\\?v='
```

Open tab 33 and tab 25 in Chrome and confirm the final HUD and portrait cards.

---

## Self-Review Checklist

- [ ] The HUD module has a small interface and hides DOM/motion implementation.
- [ ] All 11 visible tabs map to exactly one approved domain.
- [ ] Visible navigation order remains unchanged.
- [ ] The desktop sidebar remains 208px.
- [ ] There is exactly one main landmark.
- [ ] Settings expose full, standard and reduced motion.
- [ ] Reduced motion wins over stored settings.
- [ ] Event pulses execute once and clean themselves up.
- [ ] Health failures degrade to unknown instead of 500.
- [ ] Intelligence is the Phase 1 reference page.
- [ ] Operations, organization and analysis migrations preserve public globals.
- [ ] Migrated areas do not add theme/layout inline styles.
- [ ] No continuous animation loop or large framework is introduced.
- [ ] Hidden compatibility pages remain functional.
- [ ] API and database semantics remain unchanged.
- [ ] Desktop, tablet, mobile, reduced-motion and full regressions are covered.
- [ ] No Git commit is executed.

# 阵容战法研究工作台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 tab34 升级为“阵容实验室 + 对阵分析 + 战法执行链”一体化研究工作台，并保持配置、历史、模拟三层证据可追溯。

**Architecture:** 后端在既有 `LineupStatisticsService` 中新增双阵容对阵统计接口；前端新增三个深模块分别承载阵容状态、战法链和本地模板，旧 `intelligence-research.js` 退化为兼容适配器。工作台复用现有配置接口、历史阵容接口、卡包模块和 Kotlin 模拟器，不建立第二套数据或引擎。

**Tech Stack:** Python 3.9+、Flask、SQLite、Vanilla JavaScript、ES Modules、CSS Design Tokens、Node `node:test`、Python `unittest`、Playwright/System Chrome

## Global Constraints

- 页面只读；不得增加游戏动作、发包或服务器数据库写接口。
- 模板只写 `localStorage` 键 `stzb.research.lineup-templates.v1`。
- 证据状态必须保持 `CONFIG_FACT / BATTLE_STAT / SIMULATION` 三层，不混淆口径。
- 历史胜率不得描述为确定性克制；低于 10 场必须标记低置信度。
- 模拟结果必须继续来自现有 Kotlin battle-engine，不新增第二套算法。
- 卡包能力保留；不得恢复协议页面。
- 桌面保持 208px 主侧栏，tab34 使用 analysis 视觉域。
- 所有 API 文本必须通过 `textContent` 或统一转义函数进入 DOM。
- reduced-motion 下无卡片位移、扫描线或常驻动画。
- 不执行 Git commit、merge、push；工作区包含大量现有未提交改动，不覆盖无关文件。

---

## File Map

### Create

- `static/research-workbench.mjs` — 阵容模型、三模式状态机、素材库、证据栏与浏览器控制器。
- `static/research-skill-chain.mjs` — 纯配置链 / 模拟链投影。
- `static/research-templates.mjs` — 纯模板 schema、深拷贝和 localStorage adapter。
- `test/js/research-workbench.test.mjs` — 阵容模型和控制器行为。
- `test/js/research-skill-chain.test.mjs` — 配置链、模拟链和诊断。
- `test/js/research-templates.test.mjs` — 本地模板导入导出。

### Modify

- `intelligence/lineup_service.py` — 深化阵容聚合，新增对阵统计。
- `intelligence/lineup_api.py` — 新增只读 matchup 路由。
- `static/dashboard.html` — tab34 三模式三栏壳层和新模块资源。
- `static/intelligence-research.js` — 旧全局入口适配新工作台。
- `static/intelligence-research-catalog.js` — 卡包适配统一素材库和详情舞台。
- `static/intelligence-research.css` — 工作台、三模式、响应式、reduced-motion。
- `test/test_intelligence_lineup_service.py`
- `test/test_intelligence_research_static.py`
- `test/test_dashboard_runtime_node.py`
- `test/test_web_runtime_hardening.py`
- `test/js/dashboard-e2e.mjs`
- `README.md`

---

### Task 1: 双阵容历史对阵统计

**Files:**
- Modify: `intelligence/lineup_service.py`
- Modify: `intelligence/lineup_api.py`
- Modify: `test/test_intelligence_lineup_service.py`

**Interfaces:**
- Consumes: `canonical_lineup_key(hero_ids)`, `_outcome(side, result)`, `_confidence(sample_size)`
- Produces:
  - `LineupStatisticsService.get_matchup(left_key: str, right_key: str) -> dict | None`
  - `GET /api/intelligence/lineups/<left_key>/matchup/<right_key>`

- [ ] **Step 1: Write failing service tests for exact matchup aggregation**

Add:

```python
def test_matchup_counts_each_battle_once_from_left_perspective(self):
    result = self.service.get_matchup("101.102.103", "201.202.203")

    self.assertEqual("101.102.103", result["leftKey"])
    self.assertEqual("201.202.203", result["rightKey"])
    self.assertEqual(
        {
            "evidenceClass": "BATTLE_STAT",
            "sampleSize": 2,
            "wins": 1,
            "draws": 1,
            "losses": 0,
            "winRate": 75.0,
            "latestBattleTime": 3000,
        },
        result["battleStats"],
    )
    self.assertEqual("low", result["confidence"]["label"])


def test_matchup_reverses_outcome_for_right_perspective(self):
    result = self.service.get_matchup("201.202.203", "101.102.103")

    self.assertEqual(0, result["battleStats"]["wins"])
    self.assertEqual(1, result["battleStats"]["draws"])
    self.assertEqual(1, result["battleStats"]["losses"])
    self.assertEqual(25.0, result["battleStats"]["winRate"])


def test_matchup_returns_zero_stats_for_valid_unknown_pair(self):
    result = self.service.get_matchup("101.102.103", "301.302.303")

    self.assertEqual(0, result["battleStats"]["sampleSize"])
    self.assertEqual(0.0, result["battleStats"]["winRate"])


def test_matchup_rejects_invalid_lineup_key(self):
    self.assertIsNone(self.service.get_matchup("101.102", "201.202.203"))
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_intelligence_lineup_service.LineupStatisticsServiceTest -v
```

Expected: `AttributeError: LineupStatisticsService has no attribute get_matchup`.

- [ ] **Step 3: Deepen `_aggregate()` to retain battle pair evidence**

Keep current aggregate output and add an internal private projection:

```python
def _battle_lineups(self):
    # Return one record per battle:
    # {
    #   "battleId": int,
    #   "time": int,
    #   "result": int,
    #   "atk": {"key": str, "heroes": list} | None,
    #   "def": {"key": str, "heroes": list} | None,
    # }
```

Refactor `_aggregate()` to consume `_battle_lineups()` so list/detail and matchup share one normalization implementation.

Implement:

```python
def get_matchup(self, left_key, right_key):
    try:
        left = canonical_lineup_key([int(part) for part in str(left_key).split(".")])
        right = canonical_lineup_key([int(part) for part in str(right_key).split(".")])
    except (TypeError, ValueError):
        return None
    stats = {
        "sampleSize": 0,
        "wins": 0,
        "draws": 0,
        "losses": 0,
        "latestBattleTime": 0,
    }
    for battle in self._battle_lineups():
        atk = battle.get("atk")
        defence = battle.get("def")
        if not atk or not defence:
            continue
        if atk["key"] == left and defence["key"] == right:
            side = "atk"
        elif atk["key"] == right and defence["key"] == left:
            side = "def"
        else:
            continue
        outcome = _outcome(side, battle["result"])
        stats["sampleSize"] += 1
        stats[outcome] += 1
        stats["latestBattleTime"] = max(
            stats["latestBattleTime"], battle["time"]
        )
    return {
        "leftKey": left,
        "rightKey": right,
        "battleStats": {
            "evidenceClass": "BATTLE_STAT",
            **stats,
            "winRate": _win_rate(stats),
        },
        "confidence": _confidence(stats["sampleSize"]),
    }
```

- [ ] **Step 4: Add failing API tests**

```python
def test_matchup_route_returns_zero_or_aggregated_stats(self):
    response = self.client.get(
        "/api/intelligence/lineups/101.102.103/"
        "matchup/201.202.203"
    )
    self.assertEqual(200, response.status_code)
    body = response.get_json()
    self.assertTrue(body["ok"])
    self.assertEqual(2, body["battleStats"]["sampleSize"])


def test_matchup_route_rejects_invalid_key(self):
    response = self.client.get(
        "/api/intelligence/lineups/101.102/"
        "matchup/201.202.203"
    )
    self.assertEqual(404, response.status_code)
```

- [ ] **Step 5: Add thin route**

```python
@app.route(
    "/api/intelligence/lineups/<left_key>/matchup/<right_key>"
)
def intelligence_lineup_matchup(left_key, right_key):
    result = service.get_matchup(left_key, right_key)
    if result is None:
        return jsonify({"ok": False, "error": "lineup not found"}), 404
    return jsonify({"ok": True, **result})
```

- [ ] **Step 6: Run Task 1 GREEN**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_intelligence_lineup_service -v
```

Expected: all lineup service/API tests pass.

---

### Task 2: 阵容状态深模块

**Files:**
- Create: `static/research-workbench.mjs`
- Create: `test/js/research-workbench.test.mjs`
- Modify: `test/test_dashboard_runtime_node.py`

**Interfaces:**
- Produces:
  - `normalizeResearchLineup(value)`
  - `validateResearchLineup(value)`
  - `swapResearchPositions(lineup, left, right)`
  - `replaceResearchHero(lineup, position, hero)`
  - `replaceResearchSkill(lineup, position, slot, skillId)`
  - `deriveMatchupState(history, simulation, completeness)`

- [ ] **Step 1: Write failing lineup model tests**

```javascript
test("normalization creates three stable positions and two skill slots", () => {
  assert.deepEqual(normalizeResearchLineup({
    morale: 110,
    heroes: [{ id: 100027, level: 45, up: 5, equip_skills: [200001] }],
  }), {
    schemaVersion: 1,
    name: "",
    morale: 110,
    heroes: [
      { id: 100027, position: 0, level: 45, up: 5, equip_skills: [200001, 0] },
      { id: 0, position: 1, level: 40, up: 0, equip_skills: [0, 0] },
      { id: 0, position: 2, level: 40, up: 0, equip_skills: [0, 0] },
    ],
  });
});

test("validation rejects duplicate and incomplete heroes", () => {
  const duplicate = lineup([100027, 100027, 100090]);
  assert.deepEqual(validateResearchLineup(duplicate), {
    valid: false,
    complete: true,
    errors: ["武将不能重复"],
  });
  assert.equal(validateResearchLineup(lineup([100027, 0, 100090])).complete, false);
});

test("position swap preserves nested hero configuration", () => {
  const initial = lineup([100027, 100016, 100090]);
  initial.heroes[0].equip_skills = [200001, 200027];
  const result = swapResearchPositions(initial, 0, 2);
  assert.equal(result.heroes[2].id, 100027);
  assert.deepEqual(result.heroes[2].equip_skills, [200001, 200027]);
  assert.notEqual(result, initial);
});

test("hero and skill replacement affect only one target", () => {
  const initial = lineup([100027, 100016, 100090]);
  const heroChanged = replaceResearchHero(initial, 1, { id: 100705 });
  assert.equal(heroChanged.heroes[1].id, 100705);
  assert.deepEqual(heroChanged.heroes[1].equip_skills, [0, 0]);
  const skillChanged = replaceResearchSkill(heroChanged, 1, 0, 200914);
  assert.deepEqual(skillChanged.heroes[1].equip_skills, [200914, 0]);
});
```

- [ ] **Step 2: Verify RED**

Run:

```bash
node --test test/js/research-workbench.test.mjs
```

Expected: missing module.

- [ ] **Step 3: Implement pure model**

Requirements:

- clamp `morale` to `0..200`;
- clamp `level` to `1..50`;
- clamp `up` to `0..9`;
- exactly three positions;
- exactly two optional skill IDs;
- deep copy every returned value;
- `replaceResearchHero` clears optional skills;
- `replaceResearchSkill` accepts `0` as empty;
- invalid positions/slots throw `RangeError`;
- invalid positive IDs throw `TypeError`.

- [ ] **Step 4: Write failing evidence derivation tests**

```javascript
test("matchup evidence uses discrete explainable states", () => {
  assert.equal(deriveMatchupState(
    { battleStats: { sampleSize: 0, winRate: 0 } },
    null,
    { left: true, right: true },
  ).key, "insufficient");
  assert.equal(deriveMatchupState(
    { battleStats: { sampleSize: 20, winRate: 65 } },
    null,
    { left: true, right: true },
  ).key, "history-advantage");
  assert.equal(deriveMatchupState(
    { battleStats: { sampleSize: 20, winRate: 35 } },
    { winRate: 62 },
    { left: true, right: true },
  ).key, "simulation-conflict");
});
```

- [ ] **Step 5: Implement evidence derivation**

Return:

```javascript
{
  key: "insufficient" | "verify" | "history-advantage"
    | "history-disadvantage" | "simulation-conflict",
  label: "证据不足" | "谨慎验证" | "历史占优"
    | "历史劣势" | "模拟分歧",
  reasons: string[],
}
```

Rules:

- incomplete side → `verify`;
- sample `< 10` → `insufficient`;
- history `>= 60` and simulation absent or `>= 50` → `history-advantage`;
- history `<= 40` and simulation absent or `<= 50` → `history-disadvantage`;
- history and simulation differ by at least 20 percentage points → `simulation-conflict`;
- otherwise → `verify`.

- [ ] **Step 6: Add Node bridge test**

Add `test_research_workbench_behavior` to `test/test_dashboard_runtime_node.py` running:

```bash
node --test test/js/research-workbench.test.mjs
```

- [ ] **Step 7: Run Task 2 GREEN**

Run:

```bash
node --test test/js/research-workbench.test.mjs
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_dashboard_runtime_node -v
```

Expected: all pass.

---

### Task 3: 本地实验模板模块

**Files:**
- Create: `static/research-templates.mjs`
- Create: `test/js/research-templates.test.mjs`
- Modify: `test/test_dashboard_runtime_node.py`

**Interfaces:**
- Consumes: normalized lineup schema from Task 2
- Produces:
  - `TEMPLATE_STORAGE_KEY`
  - `parseResearchTemplate(text)`
  - `serializeResearchTemplate(template)`
  - `createResearchTemplateStore(storage)`

- [ ] **Step 1: Write failing template tests**

```javascript
test("template round trip preserves a complete experiment", () => {
  const original = {
    schemaVersion: 1,
    id: "template-1",
    name: "张辽实验",
    createdAt: 1000,
    updatedAt: 1000,
    lineup: lineup([100027, 100016, 100090]),
  };
  assert.deepEqual(
    parseResearchTemplate(serializeResearchTemplate(original)),
    original,
  );
});

test("template parser rejects unsupported schema and duplicate heroes", () => {
  assert.throws(
    () => parseResearchTemplate('{"schemaVersion":2}'),
    /unsupported schema/,
  );
  assert.throws(
    () => parseResearchTemplate(JSON.stringify({
      schemaVersion: 1,
      lineup: lineup([100027, 100027, 100090]),
    })),
    /duplicate hero/,
  );
});

test("store saves renames loads and deletes deep copies", () => {
  const storage = fakeStorage();
  const store = createResearchTemplateStore(storage);
  const saved = store.save("张辽实验", lineup([100027, 100016, 100090]), 1000);
  const loaded = store.load(saved.id);
  loaded.lineup.heroes[0].id = 1;
  assert.equal(store.load(saved.id).lineup.heroes[0].id, 100027);
  store.rename(saved.id, "新版", 2000);
  assert.equal(store.list()[0].name, "新版");
  store.remove(saved.id);
  assert.deepEqual(store.list(), []);
});
```

- [ ] **Step 2: Verify RED**

Run:

```bash
node --test test/js/research-templates.test.mjs
```

Expected: missing module.

- [ ] **Step 3: Implement template module**

Use:

```javascript
export const TEMPLATE_STORAGE_KEY =
  "stzb.research.lineup-templates.v1";
```

Store shape:

```javascript
{
  schemaVersion: 1,
  templates: ResearchTemplate[],
}
```

Requirements:

- never mutate caller data;
- names trim to 40 characters;
- empty name becomes `未命名阵容`;
- generate IDs with `crypto.randomUUID()` when available and deterministic timestamp fallback otherwise;
- invalid JSON throws actionable error;
- storage parse failure returns an empty store but exposes `lastError`;
- imports replace same ID only after full validation.

- [ ] **Step 4: Add runtime bridge**

Add Python bridge test to run `test/js/research-templates.test.mjs`.

- [ ] **Step 5: Run Task 3 GREEN**

```bash
node --test test/js/research-templates.test.mjs
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_dashboard_runtime_node -v
```

---

### Task 4: 配置链与模拟战法链

**Files:**
- Create: `static/research-skill-chain.mjs`
- Create: `test/js/research-skill-chain.test.mjs`
- Modify: `test/test_dashboard_runtime_node.py`

**Interfaces:**
- Produces:
  - `buildConfigSkillChain(lineup, heroDetails, skillDetails)`
  - `buildSimulationSkillChain(simulationResult)`
  - `groupSkillChainByPhase(nodes)`
  - `findSkillChainNode(nodes, nodeId)`

- [ ] **Step 1: Write failing config chain tests**

```javascript
test("config chain orders initial and optional skills by stable phase", () => {
  const nodes = buildConfigSkillChain(
    lineup([100027, 100016, 100090]),
    heroDetailsFixture(),
    skillDetailsFixture(),
  );
  assert.deepEqual(
    nodes.map((node) => node.phase),
    ["PREPARATION", "PREPARATION", "ACTIVE", "ATTACK", "CHASE"],
  );
  assert.ok(nodes.every((node) => node.evidenceClass === "CONFIG_FACT"));
  assert.ok(nodes.some((node) => node.unresolvedDescription));
});

test("config node exposes target probability duration and parameters", () => {
  const node = buildConfigSkillChain(
    lineup([100027, 100016, 100090]),
    heroDetailsFixture(),
    skillDetailsFixture(),
  )[0];
  assert.deepEqual(node, {
    nodeId: "config:0:initial:200027",
    kind: "config",
    evidenceClass: "CONFIG_FACT",
    phase: "PREPARATION",
    heroId: 100027,
    heroName: "张辽",
    position: 0,
    slot: "initial",
    skillId: 200027,
    skillName: "其疾如风",
    prepareRounds: 0,
    probability: 100,
    targetDescription: "我军群体",
    hitRange: 0,
    mainEffectName: "速度提高",
    details: [{
      detailId: 20002701,
      effectId: 104,
      effectName: "速度提高",
      constantParam: 25,
      intelParam: 0,
      availableRound: 3,
      targetType: 3,
      selectType: 0,
    }],
    unresolvedDescription: false,
  });
});
```

- [ ] **Step 2: Verify RED**

```bash
node --test test/js/research-skill-chain.test.mjs
```

- [ ] **Step 3: Implement phase mapping**

Centralize:

```javascript
const PHASE_BY_SKILL_TYPE = {
  10: "PREPARATION",
  9: "PREPARATION",
  1: "ACTIVE",
  2: "ACTIVE",
  3: "ACTIVE",
  14: "ATTACK",
  16: "CHASE",
};
```

Unknown types map to `OTHER`; never invent semantics beyond config fields.

- [ ] **Step 4: Write failing simulation chain tests**

```javascript
test("simulation chain preserves engine event order and evidence", () => {
  const nodes = buildSimulationSkillChain({
    response: { result: { firstRun: simulationFixture() } },
  });
  assert.deepEqual(
    nodes.map((node) => node.eventSeq),
    [0, 1, 2, 3, 4, 5],
  );
  assert.ok(nodes.every((node) => node.evidenceClass === "SIMULATION"));
});

test("unsupported effects remain visible", () => {
  const nodes = buildSimulationSkillChain({
    response: { result: { firstRun: unsupportedFixture() } },
  });
  assert.equal(nodes.find((node) => node.type === "UnsupportedSkillEffect").warning, true);
});
```

- [ ] **Step 5: Implement simulation projection**

Accept both response shapes:

```javascript
detail.response.result.firstRun
detail.response.firstRun
```

Project only source-backed fields:

- eventSeq;
- phase;
- round;
- type;
- source / target;
- skillId / rootSkillId;
- effectId;
- replay action IDs;
- warning.

- [ ] **Step 6: Add grouping and lookup tests and implementation**

```javascript
const groups = groupSkillChainByPhase(nodes);
assert.deepEqual(groups.map((group) => group.key), [
  "PREPARATION", "ROUND:1", "FINAL",
]);
assert.equal(findSkillChainNode(nodes, "event:4").eventSeq, 4);
```

- [ ] **Step 7: Add bridge and run GREEN**

```bash
node --test test/js/research-skill-chain.test.mjs
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_dashboard_runtime_node -v
```

---

### Task 5: 三模式工作台壳层与控制器

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/intelligence-research.js`
- Modify: `static/intelligence-research-catalog.js`
- Modify: `static/intelligence-research.css`
- Modify: `static/research-workbench.mjs`
- Modify: `test/test_intelligence_research_static.py`
- Modify: `test/test_web_runtime_hardening.py`
- Modify: `test/js/research-workbench.test.mjs`

**Interfaces:**
- Consumes: Tasks 1–4
- Produces:
  - `createResearchWorkbench(options)`
  - `window.ResearchCenter` compatibility surface

- [ ] **Step 1: Write static RED tests**

Require tab34 tokens:

```text
research-mode-tabs
research-library-kind
research-search
research-library-filters
research-results
research-stage
research-evidence-tabs
research-evidence-body
research-template-dialog
```

Require assets:

```text
research-workbench.mjs
research-skill-chain.mjs
research-templates.mjs
```

Require CSS selectors:

```text
.research-workbench-shell
.research-library
.research-stage
.research-evidence-panel
.research-lineup-grid
.research-hero-card
.research-skill-slot
.research-matchup-stage
.research-skill-chain
```

Require breakpoints `1279px`, `767px`, reduced motion, and no new `:root`.

- [ ] **Step 2: Verify static RED**

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_intelligence_research_static -v
```

- [ ] **Step 3: Replace tab34 shell**

Use semantic HTML:

```html
<div class="research-mode-tabs" id="research-mode-tabs" role="tablist">
  <button role="tab" data-research-mode="lab">阵容实验室</button>
  <button role="tab" data-research-mode="matchup">对阵分析</button>
  <button role="tab" data-research-mode="chain">战法执行链</button>
</div>
<div class="research-workbench-shell">
  <aside class="research-library hud-panel">...</aside>
  <main class="research-stage hud-panel" id="research-stage">...</main>
  <aside class="research-evidence-panel hud-panel">...</aside>
</div>
```

Keep existing IDs `research-search`, `research-results`, and `research-detail` only as compatibility wrappers if old code/tests still call them; new stage and evidence IDs are authoritative.

- [ ] **Step 4: Write failing controller interaction tests**

Inject:

```javascript
{
  documentRef,
  windowRef,
  fetchJson,
  storage,
  setTimeoutFn,
  clearTimeoutFn,
  nowFn,
}
```

Verify:

- initial mode is `lab`;
- mode switches preserve `lineup` and `selectedEvidence`;
- hero search calls `/api/intelligence/heroes`;
- skill search calls `/api/intelligence/skills`;
- historical lineup selection calls `/api/intelligence/lineups/<key>`;
- matchup mode calls the new matchup route only when both sides complete;
- chain mode loads hero/skill detail once per ID and caches it;
- stale request responses do not overwrite newer selection;
- error retains the previous successful stage.

- [ ] **Step 5: Implement controller state**

State:

```javascript
{
  mode: "lab",
  libraryKind: "hero",
  query: "",
  filters: {},
  lineup: normalizeResearchLineup(null),
  opponent: normalizeResearchLineup(null),
  selectedPosition: 0,
  selectedSkillSlot: null,
  selectedEvidence: "history",
  selectedChainNodeId: "",
  activeHistoricalLineup: null,
  matchup: null,
  simulationByLineupKey: new Map(),
  heroDetails: new Map(),
  skillDetails: new Map(),
  requestRevision: 0,
}
```

- [ ] **Step 6: Render lab mode**

Render:

- three hero portrait cards;
- initial skill and two optional slots;
- level, advance, morale;
- exchange positions;
- empty slots;
- stable lineup key;
- history summary;
- config completeness;
- send-to-simulator state.

Use DOM construction or centralized `esc()` for every API value.

- [ ] **Step 7: Render matchup mode**

Render:

- left and right three-hero strips;
- historical matchup stats;
- derived discrete evidence state;
- latest battle time;
- simulation comparison if available;
- common/alternative lineups.

- [ ] **Step 8: Render chain mode**

Render:

- config chain by default;
- simulation chain when a matching simulation exists;
- phase navigation;
- selected node detail in evidence panel;
- unsupported effect warnings.

- [ ] **Step 9: Preserve global compatibility**

`static/intelligence-research.js` installs:

```javascript
window.loadIntelligenceResearch = workbench.load;
window.ResearchCenter = {
  load,
  openHero,
  openSkill,
  openCardPack,
  openLineup,
  sendToSimulator,
  sendHeroToSimulator,
  setMode,
  loadTemplate,
  state,
};
```

`ResearchCatalogCenter.openCardPack()` must update the shared library kind and stage instead of replacing unrelated DOM state.

- [ ] **Step 10: Add mtime coverage**

Add:

```text
research-workbench.mjs
research-skill-chain.mjs
research-templates.mjs
```

to `test/test_web_runtime_hardening.py`.

- [ ] **Step 11: Run Task 5 GREEN**

```bash
node --test \
  test/js/research-workbench.test.mjs \
  test/js/research-skill-chain.test.mjs \
  test/js/research-templates.test.mjs

PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_intelligence_research_static \
  test.test_dashboard_runtime_node \
  test.test_web_runtime_hardening.WebRuntimeHardeningTest.test_index_rewrites_local_asset_versions_from_file_mtime -v
```

---

### Task 6: 模拟器往返与模板工作流

**Files:**
- Modify: `static/research-workbench.mjs`
- Modify: `static/intelligence-research.js`
- Modify: `static/simulator-workbench.js`
- Modify: `test/js/research-workbench.test.mjs`
- Modify: `test/js/simulator-workbench.test.mjs`

**Interfaces:**
- Consumes: `window.StzbSimulator.loadLineup`
- Produces: complete `sourceContext` and research simulation cache

- [ ] **Step 1: Write failing handoff tests**

Research controller:

```javascript
test("send to simulator preserves optional skills and lineup key", async () => {
  await controller.sendToSimulator();
  assert.deepEqual(simulatorCalls[0], {
    lineup: currentLineup,
    options: {
      camp: "blue",
      source: "intelligence-research",
      lineupKey: "100027.100016.100090",
      returnTab: 34,
    },
  });
});
```

Simulator controller:

```javascript
test("loadLineup preserves research return context", async () => {
  await loadLineup(lineup, {
    source: "intelligence-research",
    lineupKey: "100027.100016.100090",
    returnTab: 34,
  });
  assert.equal(getState().sourceContext.returnTab, 34);
});
```

- [ ] **Step 2: Verify RED**

```bash
node --test \
  test/js/research-workbench.test.mjs \
  test/js/simulator-workbench.test.mjs
```

- [ ] **Step 3: Extend source context only**

Add optional `returnTab` to existing simulator source context. Do not alter simulation semantics.

- [ ] **Step 4: Cache completion event**

On `stzb:simulation-completed`:

- require source `intelligence-research`;
- cache by `lineupKey`;
- retain complete response;
- refresh right evidence panel;
- enable simulation chain;
- provide “返回研究工作台” action when `returnTab === 34`.

- [ ] **Step 5: Write template workflow tests**

Verify controller:

- save complete lineup;
- load restores positions and skills;
- rename updates list;
- delete removes;
- exported JSON round trips;
- invalid import displays error and preserves current lineup.

- [ ] **Step 6: Implement template dialog**

Use native `<dialog id="research-template-dialog">`; bind one delegated listener; no inline data from user-provided names without escaping.

- [ ] **Step 7: Run Task 6 GREEN**

```bash
node --test \
  test/js/research-workbench.test.mjs \
  test/js/research-templates.test.mjs \
  test/js/simulator-workbench.test.mjs
```

---

### Task 7: 浏览器验收、文档、全量回归和重启

**Files:**
- Modify: `test/js/dashboard-e2e.mjs`
- Modify: `README.md`

**Interfaces:**
- Verifies Tasks 1–6
- Produces the final tab34 workbench

- [ ] **Step 1: Extend E2E fixtures**

Mock:

- hero search/detail for six named heroes;
- skill search/detail for initial and optional skills;
- lineup listing/detail;
- matchup route with sample 7;
- simulation result with semantic events and diagnostics;
- card pack detail;
- template localStorage.

- [ ] **Step 2: Add lab mode E2E**

Verify:

- tab34 analysis domain;
- three-column workbench;
- search 张辽;
- add/replace hero;
- select optional skill;
- swap positions preserves skills;
- config evidence;
- history lineup load;
- save/load local template.

- [ ] **Step 3: Add matchup E2E**

Verify:

- select enemy lineup;
- matchup endpoint called with exact keys;
- sample, win rate, latest time and confidence render;
- “证据不足 / 谨慎验证 / 历史占优 / 历史劣势 / 模拟分歧” is rule-backed;
- no unsupported “综合分数”.

- [ ] **Step 4: Add chain E2E**

Verify:

- config chain nodes show hero, skill, target and duration;
- unresolved descriptions show warning;
- after simulation, simulation chain renders ordered events;
- clicking `EffectBlocked` displays replay evidence;
- diagnostics remain visible.

- [ ] **Step 5: Add simulator round trip E2E**

Verify:

- complete optional skills appear in simulator;
- run simulation;
- return to tab34;
- simulation evidence and chain are retained.

- [ ] **Step 6: Add responsive matrix**

At:

```text
1440×1000
1024×900
768×900
390×844
```

Verify:

- page head and mode tabs visible;
- correct panel order;
- material library reachable;
- evidence reachable;
- dialogs fit viewport;
- no document overflow.

Reduced motion:

- no card transform;
- no scan line;
- no HUD pulse class;
- chain remains readable.

- [ ] **Step 7: Update README**

Document:

- three modes;
- local templates;
- matchup route;
- evidence boundaries;
- simulator round trip;
- skill chain configuration vs simulation semantics.

- [ ] **Step 8: Run focused validation**

```bash
node --check \
  static/research-workbench.mjs \
  static/research-skill-chain.mjs \
  static/research-templates.mjs \
  static/intelligence-research.js

node --test \
  test/js/research-workbench.test.mjs \
  test/js/research-skill-chain.test.mjs \
  test/js/research-templates.test.mjs \
  test/js/simulator-workbench.test.mjs \
  test/js/simulator-analysis.test.mjs

PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_intelligence_lineup_service \
  test.test_intelligence_research_static \
  test.test_dashboard_runtime_node \
  test.test_dashboard_e2e -v
```

- [ ] **Step 9: Run full repository validation**

```bash
git diff --check
node --test test/js/*.test.mjs
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest discover -s test -v
```

Expected: all pass.

- [ ] **Step 10: Restart 8080 and verify**

Restart only the current `api_server.py` listener, then verify:

```bash
curl -fsS http://127.0.0.1:8080/api/status
curl -fsS \
  http://127.0.0.1:8080/api/intelligence/lineups/\
100027.100016.100090/matchup/100013.100649.100023
curl -fsS http://127.0.0.1:8080/ |
  rg 'research-(workbench|skill-chain|templates).*\\?v='
```

- [ ] **Step 11: Final requirement audit**

Confirm:

- one workbench, three modes;
- shared current lineup;
- matchup stats exact and perspective-correct;
- config chain and simulation chain remain separate;
- optional skills survive simulator handoff;
- local templates never call a write API;
- card packs remain available;
- no protocol UI;
- no game action;
- no Git commit, merge, or push.

---

## Self-Review Checklist

- [ ] Every approved spec section maps to a task.
- [ ] Matchup aggregation has one normalization implementation.
- [ ] Research model, templates and skill chain are pure modules.
- [ ] The browser controller is the only DOM/network owner.
- [ ] Legacy `window.ResearchCenter` remains stable.
- [ ] Card packs use the same material library.
- [ ] Optional skills survive every copy, swap, template and simulator seam.
- [ ] Low-sample evidence is never labeled as deterministic countering.
- [ ] Configuration order is not mislabeled as real execution order.
- [ ] Simulation chain is sourced only from Kotlin result events.
- [ ] No server write API is added.
- [ ] Mobile order and overflow are explicitly tested.
- [ ] mtime asset coverage includes every new module.
- [ ] No Git commit is executed.

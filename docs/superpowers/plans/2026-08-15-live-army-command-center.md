# 实时部队三栏指挥台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增左侧“实时部队”独立标签，通过 5026/5028 统一 WorldState 展示全部当前部队、最近离线 10 分钟、实时行军位置和按 `army_id` 精确匹配的真实武将阵容。

**Architecture:** 后端使用 `LiveArmyService` 一次性聚合 `world_armies`、`world_real_marches`、用户/同盟、目标地块、删除包和 `battles_v2` 精确阵容证据，并通过单一只读 API 返回稳定模型。前端由纯 Canvas 地图模块和页面控制器组成，三栏共享一个 `selectedArmyId`，复用现有 SSE、HUD、画像和战场情报定位入口。

**Tech Stack:** Python 3.9、Flask、SQLite、Vanilla JavaScript ES Module、Canvas 2D、Node `node:test`、Python `unittest`、Playwright Chrome E2E。

## Global Constraints

- 只读功能，不新增游戏动作、发包或数据库写接口。
- 阵容只允许 `army_id = atk_team_id / def_team_id` 精确匹配，不按玩家、同盟或近期队伍猜测。
- 阵容证据状态只允许 `exact` 和 `unknown`；完整性使用独立 `complete` 布尔值。
- 未命中必须显示“无同 ID 战报，阵容未知”。
- 武将最终显示本地配置名字和画像；缺失配置使用“武将 ID”和占位画像。
- 当前全部未删除部队均展示；最近离线窗口默认 10 分钟，允许 `0..60`。
- 状态仅使用权威映射 `0/1/2/3/4/5/6/25`，其他值显示“状态 N”。
- 新导航放在“战场情报”之后、“阵容战法研究”之前，保持平铺，无“更多”菜单。
- 新页面 tab ID 为 `35`，视觉域为 `intelligence`。
- 桌面保持 `208px` 侧栏；桌面三栏、平板两层、移动单列且无横向溢出。
- 复用现有 SSE，不创建第二条 `EventSource`。
- 支持 `prefers-reduced-motion` 和 HUD `full / standard / reduced`。
- 不修改 5026 / 5028 协议语义、数据库写入流程、Kotlin 战斗引擎和隐藏兼容页。
- 不执行 Git commit、merge 或 push。

---

### Task 1: 建立实时部队后端聚合深模块

**Files:**
- Create: `intelligence/live_army_service.py`
- Create: `test/test_live_army_service.py`

**Interfaces:**
- Consumes: SQLite connection with optional WorldState and battle tables
- Consumes: `sim_data.hero_index()` and `sim_data.skill_index()`
- Produces: `LiveArmyService(connection, *, now_ms=None)`
- Produces: `LiveArmyService.snapshot(offline_minutes=10) -> dict`
- Produces: `army_state_meta(state) -> dict`

- [ ] **Step 1: Write failing state and location tests**

Create tests with an in-memory schema containing `world_scene_packets`,
`world_state_versions`, `world_armies`, `world_real_marches`,
`world_map_users`, `world_unions`, `world_tiles`, and `battles_v2`.

```python
from intelligence.live_army_service import (
    LiveArmyService,
    army_state_meta,
)


def test_authoritative_army_states_and_unknown_state():
    assert army_state_meta(0)["label"] == "待命"
    assert army_state_meta(1)["key"] == "expedition"
    assert army_state_meta(2)["label"] == "驻守前往"
    assert army_state_meta(3)["label"] == "增援前往"
    assert army_state_meta(4)["label"] == "返回中"
    assert army_state_meta(5)["label"] == "驻守"
    assert army_state_meta(6)["label"] == "增援"
    assert army_state_meta(25)["label"] == "停留"
    assert army_state_meta(99) == {
        "key": "unknown",
        "label": "状态 99",
        "category": "unknown",
        "isMoving": False,
    }


def test_real_march_overrides_fallback_location(connection):
    insert_army(
        connection,
        army_id=18411352,
        state=1,
        real_march_id=9001,
        wid_from=10001,
        wid_to=10009,
        reside_wid=10002,
        stay_wid=10003,
    )
    insert_march(
        connection,
        real_march_id=9001,
        current_wid=10004,
        next_wid=10005,
        end_time=1900000060,
    )

    item = LiveArmyService(connection, now_ms=1_800_000_000_000).snapshot()["current"][0]

    assert item["location"] == {
        "currentWid": 10004,
        "nextWid": 10005,
        "targetWid": 10009,
        "fromWid": 10001,
        "resideWid": 10002,
        "stayWid": 10003,
        "source": "real-march",
    }
    assert item["march"]["realMarchId"] == 9001


def test_location_falls_back_stay_reside_from(connection):
    insert_army(connection, army_id=1, stay_wid=10007, reside_wid=10006, wid_from=10005)
    insert_army(connection, army_id=2, stay_wid=0, reside_wid=10006, wid_from=10005)
    insert_army(connection, army_id=3, stay_wid=0, reside_wid=0, wid_from=10005)

    rows = {
        row["armyId"]: row["location"]
        for row in LiveArmyService(connection, now_ms=1_800_000_000_000).snapshot()["current"]
    }

    assert rows[1]["currentWid"] == 10007
    assert rows[1]["source"] == "stay"
    assert rows[2]["currentWid"] == 10006
    assert rows[2]["source"] == "reside"
    assert rows[3]["currentWid"] == 10005
    assert rows[3]["source"] == "from"
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_live_army_service.LiveArmyStateTest \
  test.test_live_army_service.LiveArmyLocationTest -v
```

Expected: import failure because `intelligence.live_army_service` does not exist.

- [ ] **Step 3: Implement state and location projection**

Implement exact constants:

```python
ARMY_STATES = {
    0: ("normal", "待命", "stationary", False),
    1: ("expedition", "出征中", "moving", True),
    2: ("reside-going", "驻守前往", "moving", True),
    3: ("reinforce-going", "增援前往", "moving", True),
    4: ("returning", "返回中", "moving", True),
    5: ("reside", "驻守", "stationary", False),
    6: ("reinforce", "增援", "stationary", False),
    25: ("stay", "停留", "stationary", False),
}
```

`LiveArmyService` must:

- inspect optional tables before querying;
- read current WorldState version;
- load current armies and all real marches once;
- merge by `real_march_id`;
- apply location priority realMarch → stay → reside → from;
- return camelCase API fields.

- [ ] **Step 4: Write failing exact lineup tests**

```python
def test_exact_attack_lineup_uses_latest_valid_same_army_report(connection):
    insert_army(connection, army_id=18411352)
    insert_battle(
        connection,
        battle_id=5289170,
        time=1786724967,
        atk_team_id=18411352,
        def_team_id=999,
        atk_hero_ids=(100705, 100707, 100101),
        atk_levels=(45, 44, 43),
        atk_stars=(5, 4, 3),
        all_skill_info="1,200001,10,200027,10,0,0;2,200001,9;3,200027,8",
    )

    lineup = LiveArmyService(connection).snapshot()["current"][0]["lineup"]

    assert lineup["status"] == "exact"
    assert lineup["complete"] is True
    assert lineup["battleId"] == 5289170
    assert lineup["side"] == "atk"
    assert [hero["name"] for hero in lineup["heroes"]] == ["杜预", "卫瓘", "灵帝"]
    assert lineup["heroes"][0]["level"] == 45
    assert lineup["heroes"][0]["advance"] == 5
    assert lineup["heroes"][0]["portraitUrl"].startswith("/static/hero-portraits/")


def test_exact_defense_lineup_uses_defender_columns(connection):
    insert_army(connection, army_id=9002)
    insert_battle(
        connection,
        battle_id=20,
        time=2000,
        atk_team_id=111,
        def_team_id=9002,
        def_hero_ids=(100013, 100649, 100023),
    )

    lineup = LiveArmyService(connection).snapshot()["current"][0]["lineup"]

    assert lineup["status"] == "exact"
    assert lineup["side"] == "def"
    assert [hero["id"] for hero in lineup["heroes"]] == [100013, 100649, 100023]


def test_latest_invalid_report_falls_back_to_latest_valid_same_army_report(connection):
    insert_army(connection, army_id=77)
    insert_battle(connection, battle_id=2, time=200, atk_team_id=77, atk_hero_ids=(0, 0, 0))
    insert_battle(connection, battle_id=1, time=100, atk_team_id=77, atk_hero_ids=(100027, 100016, 0))

    lineup = LiveArmyService(connection).snapshot()["current"][0]["lineup"]

    assert lineup["battleId"] == 1
    assert lineup["status"] == "exact"
    assert lineup["complete"] is False
    assert len(lineup["heroes"]) == 2


def test_no_same_army_report_is_unknown_without_player_fallback(connection):
    insert_army(connection, army_id=814501, user_id=14455)
    insert_battle(
        connection,
        battle_id=30,
        time=3000,
        atk_team_id=999999,
        atk_name="same player",
        atk_hero_ids=(100705, 100707, 100101),
    )

    lineup = LiveArmyService(connection).snapshot()["current"][0]["lineup"]

    assert lineup == {
        "status": "unknown",
        "complete": False,
        "battleId": 0,
        "battleTime": 0,
        "battleTimeText": "",
        "side": "",
        "heroes": [],
        "message": "无同 ID 战报，阵容未知",
    }
```

- [ ] **Step 5: Run exact lineup tests and verify RED**

Expected: failures because lineup projection is missing.

- [ ] **Step 6: Implement strict lineup projection**

Implementation requirements:

- batch-query battles for all relevant army IDs, not one query per army;
- order by `time DESC, battle_id DESC`;
- choose the first row whose matched side has at least one positive hero ID;
- never combine multiple reports;
- map heroes through `sim_data.hero_index()`;
- map skills through `sim_data.skill_index()`;
- unknown hero and skill IDs keep the numeric ID;
- parse only the matched side's positions from `all_skill_info`;
- return `exact` or `unknown` only.

- [ ] **Step 7: Write failing recent-offline tests**

```python
def test_recent_offline_uses_packet_observed_time_and_source(connection):
    now_ms = 1_800_000_000_000
    deletion_seq = insert_packet(
        connection,
        cmd_id=5028,
        observed_at_ms=now_ms - 10 * 60 * 1000,
    )
    insert_army(connection, army_id=88, deleted_at_seq=deletion_seq)

    snapshot = LiveArmyService(connection, now_ms=now_ms).snapshot(offline_minutes=10)

    assert len(snapshot["recentOffline"]) == 1
    assert snapshot["recentOffline"][0]["offline"]["sourceLabel"] == "5028 增量"
    assert snapshot["recentOffline"][0]["offline"]["ageMs"] == 600_000


def test_recent_offline_excludes_rows_older_than_window(connection):
    now_ms = 1_800_000_000_000
    deletion_seq = insert_packet(
        connection,
        cmd_id=5026,
        observed_at_ms=now_ms - 600_001,
    )
    insert_army(connection, army_id=89, deleted_at_seq=deletion_seq)

    snapshot = LiveArmyService(connection, now_ms=now_ms).snapshot(offline_minutes=10)

    assert snapshot["recentOffline"] == []


def test_missing_optional_tables_degrades_without_exception(connection_without_world_tables):
    snapshot = LiveArmyService(
        connection_without_world_tables,
        now_ms=1_800_000_000_000,
    ).snapshot()

    assert snapshot["current"] == []
    assert snapshot["recentOffline"] == []
    assert snapshot["freshness"] == "unknown"
```

- [ ] **Step 8: Implement recent-offline, summary and bounds**

Use `deleted_at_seq → world_scene_packets` for deletion evidence.

Summary fields:

```python
summary = {
    "current": len(current),
    "moving": count_moving,
    "stationary": count_stationary,
    "exactLineups": count_exact,
    "unknownLineups": count_unknown,
    "recentOffline": len(recent_offline),
}
```

Bounds include positive current/fallback/next/target WIDs from current armies.

- [ ] **Step 9: Run Task 1 GREEN**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_live_army_service -v
```

Expected: all service tests pass.

---

### Task 2: 暴露只读聚合 API

**Files:**
- Create: `intelligence/live_army_api.py`
- Modify: `api_server.py`
- Create: `test/test_live_army_api.py`

**Interfaces:**
- Consumes: `LiveArmyService.snapshot(offline_minutes)`
- Produces: `register_live_army_api(app, get_connection)`
- Produces: `GET /api/intelligence/live-armies`

- [ ] **Step 1: Write failing API tests**

```python
class LiveArmyApiTest(unittest.TestCase):
    def test_default_window_returns_stable_envelope(self):
        response = self.client.get("/api/intelligence/live-armies")
        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertTrue(body["ok"])
        self.assertIn("summary", body)
        self.assertIn("current", body)
        self.assertIn("recentOffline", body)

    def test_offline_minutes_accepts_zero_and_sixty(self):
        self.assertEqual(
            200,
            self.client.get(
                "/api/intelligence/live-armies?offlineMinutes=0"
            ).status_code,
        )
        self.assertEqual(
            200,
            self.client.get(
                "/api/intelligence/live-armies?offlineMinutes=60"
            ).status_code,
        )

    def test_invalid_offline_minutes_is_400(self):
        for value in ("-1", "61", "bad"):
            with self.subTest(value=value):
                response = self.client.get(
                    f"/api/intelligence/live-armies?offlineMinutes={value}"
                )
                self.assertEqual(400, response.status_code)
                self.assertFalse(response.get_json()["ok"])
```

- [ ] **Step 2: Run API tests and verify RED**

Expected: 404 because the route is not registered.

- [ ] **Step 3: Implement API registration**

```python
def register_live_army_api(app, get_connection):
    @app.route("/api/intelligence/live-armies")
    def live_armies():
        raw = request.args.get("offlineMinutes", "10")
        try:
            offline_minutes = int(raw)
        except ValueError:
            return jsonify({
                "ok": False,
                "error": "offlineMinutes must be an integer",
            }), 400
        if not 0 <= offline_minutes <= 60:
            return jsonify({
                "ok": False,
                "error": "offlineMinutes must be between 0 and 60",
            }), 400
        connection = get_connection()
        try:
            return jsonify(
                LiveArmyService(connection).snapshot(offline_minutes)
            )
        finally:
            connection.close()
```

Register it beside existing intelligence APIs in `api_server.py`.

- [ ] **Step 4: Run Task 2 GREEN**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_live_army_api \
  test.test_world_scene_api \
  test.test_world_intelligence_api -v
```

Expected: all pass.

---

### Task 3: 建立纯 Canvas 实时部队地图模块

**Files:**
- Create: `static/live-army-map.mjs`
- Create: `test/js/live-army-map.test.mjs`
- Modify: `test/test_dashboard_runtime_node.py`

**Interfaces:**
- Produces: `widToPoint(wid)`
- Produces: `boundsForArmies(armies, fallback)`
- Produces: `buildArmyDrawPlan(armies, selectedArmyId, bounds)`
- Produces: `drawLiveArmyMap(canvas, plan, options)`
- Produces: `hitTestArmy(x, y, plan)`
- Produces: `panBounds(bounds, rowDelta, colDelta)`
- Produces: `zoomBounds(bounds, row, col, direction)`

- [ ] **Step 1: Write failing geometry and draw-plan tests**

```javascript
import {
  boundsForArmies,
  buildArmyDrawPlan,
  hitTestArmy,
  widToPoint,
} from "../../static/live-army-map.mjs";

test("WID converts to row and col", () => {
  assert.deepEqual(widToPoint(2081480), { row: 208, col: 1480 });
});

test("bounds include current next and target locations", () => {
  const bounds = boundsForArmies([
    {
      location: {
        currentWid: 2081480,
        nextWid: 2081481,
        targetWid: 1151300,
      },
    },
  ]);
  assert.ok(bounds.rowUp <= 115);
  assert.ok(bounds.rowDown >= 208);
  assert.ok(bounds.colLeft <= 1300);
  assert.ok(bounds.colRight >= 1481);
});

test("draw plan preserves shape route and selection semantics", () => {
  const plan = buildArmyDrawPlan(
    [
      fixtureArmy({ armyId: 1, stateKey: "returning" }),
      fixtureArmy({ armyId: 2, stateKey: "reside" }),
      fixtureArmy({
        armyId: 3,
        stateKey: "unknown",
        offline: { deletedAtMs: 1 },
      }),
    ],
    1,
    { rowUp: 1, rowDown: 300, colLeft: 1, colRight: 2000 },
  );

  assert.equal(plan.markers[0].shape, "return");
  assert.equal(plan.markers[0].selected, true);
  assert.equal(plan.markers[1].shape, "shield");
  assert.equal(plan.markers[2].shape, "diamond");
  assert.equal(plan.markers[2].offline, true);
  assert.equal(plan.routes[0].kind, "complete");
});

test("hit test returns exact marker army id", () => {
  const plan = {
    markers: [
      { armyId: 77, x: 100, y: 80, hitRadius: 14 },
    ],
  };
  assert.equal(hitTestArmy(105, 84, plan), 77);
  assert.equal(hitTestArmy(140, 84, plan), 0);
});
```

- [ ] **Step 2: Run Node test and verify RED**

Run:

```bash
node --test test/js/live-army-map.test.mjs
```

Expected: module not found.

- [ ] **Step 3: Implement deterministic pure map helpers**

Requirements:

- no API or DOM access in pure helpers;
- complete route requires positive current, next and target WIDs;
- incomplete route uses current and target only;
- offline route is dashed;
- marker shapes map by state key;
- bounds add padding but stay positive;
- hit test checks markers from last to first.

- [ ] **Step 4: Write draw smoke test with fake Canvas context**

Assert that:

- complete route uses solid segments;
- incomplete/offline route calls `setLineDash`;
- selected marker draws outer ring;
- text labels use army ID suffix;
- reduced motion option does not schedule animation.

- [ ] **Step 5: Implement Canvas drawing**

`drawLiveArmyMap()` must:

- scale for `devicePixelRatio`;
- render grid;
- render routes before markers;
- render all markers in current bounds;
- use shape and text, not color alone;
- return the plan used for hit testing;
- never start a continuous `requestAnimationFrame` loop.

- [ ] **Step 6: Wire Node test runner**

Add `live-army-map.test.mjs` to
`test/test_dashboard_runtime_node.py`.

- [ ] **Step 7: Run Task 3 GREEN**

Run:

```bash
node --test test/js/live-army-map.test.mjs
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_dashboard_runtime_node -v
```

Expected: all pass.

---

### Task 4: 新增导航和三栏 HUD 页面壳层

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/app1.js`
- Modify: `static/dashboard-hud.mjs`
- Modify: `static/dashboard-design-system.js`
- Create: `static/live-army-command.css`
- Create: `test/test_live_army_static.py`
- Modify: `test/test_sidebar_navigation.py`
- Modify: `test/test_web_runtime_hardening.py`

**Interfaces:**
- Consumes: tab ID `35`
- Consumes: `LiveArmyCommand.load()`
- Produces DOM IDs:
  - `live-army-search`
  - `live-army-status-filter`
  - `live-army-current-list`
  - `live-army-offline-list`
  - `live-army-map-canvas`
  - `live-army-map-status`
  - `live-army-detail`
  - `live-army-summary`

- [ ] **Step 1: Write failing navigation and shell tests**

```python
VISIBLE_LABELS = [
    "玩家队伍",
    "自定义积分",
    "打城考勤",
    "同盟成员队伍",
    "武将阵容",
    "团数据",
    "战斗模拟",
    "州郡分布",
    "设置中心",
    "战场情报",
    "实时部队",
    "阵容战法研究",
]


def test_live_army_is_flat_navigation_after_intelligence(self):
    self.assertIn(
        "<button onclick='switchTab(35,this)'>实时部队</button>",
        self.nav,
    )
    self.assertLess(
        self.nav.index("switchTab(33,this)"),
        self.nav.index("switchTab(35,this)"),
    )
    self.assertLess(
        self.nav.index("switchTab(35,this)"),
        self.nav.index("switchTab(34,this)"),
    )


def test_live_army_page_has_three_column_hud_shell():
    html = HTML.read_text(encoding="utf-8")
    section = html.split("id='tab35'", 1)[1].split("id='tab34'", 1)[0]
    for token in (
        "hud-page-head",
        "live-army-shell",
        "live-army-index",
        "live-army-map-panel",
        "live-army-detail-panel",
        "live-army-current-list",
        "live-army-offline-list",
        "live-army-map-canvas",
    ):
        self.assertIn(token, section)
    self.assertIn("data-visual-domain='intelligence'", html)
```

- [ ] **Step 2: Run tests and verify RED**

Expected: missing nav and tab35 failures.

- [ ] **Step 3: Add navigation and tab metadata**

Changes:

- add button after tab33;
- add `TAB_META[35]`;
- include `35` in `NAV_GROUPS` intelligence tabs;
- include `35: "intelligence"` in `VISIBLE_DOMAINS`;
- add `i===35 && window.LiveArmyCommand?.load()` to `switchTab`;
- keep original 11-page order otherwise unchanged.

- [ ] **Step 4: Add semantic HTML shell**

Use:

```html
<div class='page hud-page' id='tab35' data-visual-domain='intelligence'>
  <header class='hud-page-head'>...</header>
  <section id='live-army-summary' class='hud-kpi-grid'></section>
  <div class='live-army-shell'>
    <section class='hud-panel live-army-index'>...</section>
    <section class='hud-panel live-army-map-panel'>...</section>
    <aside class='hud-panel live-army-detail-panel'>...</aside>
  </div>
</div>
```

Load:

```html
<link rel="stylesheet" href="/static/live-army-command.css">
<script type="module" src="/static/live-army-command.mjs"></script>
```

- [ ] **Step 5: Add CSS**

Required selectors:

```css
.live-army-shell
.live-army-index
.live-army-list
.live-army-card
.live-army-card.is-selected
.live-army-card.is-offline
.live-army-map-panel
.live-army-map-wrap
.live-army-detail-panel
.live-army-hero-grid
.live-army-hero
.live-army-evidence
```

Breakpoints:

- `>=1280`: `31% 42% 27%`;
- `768..1279`: index + map, detail full row;
- `<768`: map, detail, collapsible index single column.

No new `:root`, no migrated shell inline theme/layout styles.

- [ ] **Step 6: Extend mtime and navigation contracts**

Add:

```text
live-army-command.css
live-army-command.mjs
live-army-map.mjs
```

to mtime coverage.

Update visible navigation count/order from 11 to 12.

- [ ] **Step 7: Run Task 4 GREEN**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_live_army_static \
  test.test_sidebar_navigation \
  test.test_dashboard_hud_static \
  test.test_dashboard_css_structure \
  test.test_web_runtime_hardening.WebRuntimeHardeningTest.test_index_rewrites_local_asset_versions_from_file_mtime -v
```

Expected: all pass and navigation remains flat.

---

### Task 5: 实现三栏控制器、筛选、排序和倒计时

**Files:**
- Create: `static/live-army-command.mjs`
- Create: `test/js/live-army-command.test.mjs`
- Modify: `static/live-army-command.css`
- Modify: `test/test_dashboard_runtime_node.py`

**Interfaces:**
- Consumes: `GET /api/intelligence/live-armies`
- Consumes: `drawLiveArmyMap()` and `hitTestArmy()`
- Produces:
  - `filterArmies(armies, query, stateFilter)`
  - `sortCurrentArmies(armies, nowSec)`
  - `chooseDefaultArmy(snapshot, nowSec)`
  - `formatArmyCountdown(endTime, nowSec)`
  - `createLiveArmyCommand(options)`
  - `window.LiveArmyCommand`

- [ ] **Step 1: Write failing pure controller tests**

```javascript
test("search matches army player union hero and WID", () => {
  const army = fixtureArmy({
    armyId: 18411352,
    ownerName: "无情的战",
    ownerUnionName: "甲盟",
    location: {
      currentWid: 2081480,
      nextWid: 2081481,
      targetWid: 1151300,
    },
    lineup: {
      status: "exact",
      heroes: [{ name: "杜预" }, { name: "卫瓘" }, { name: "灵帝" }],
    },
  });
  for (const query of (
    "18411352", "无情", "甲盟", "杜预", "2081480", "1151300"
  )) {
    assert.equal(filterArmies([army], query, "all").length, 1);
  }
});

test("state filter keeps current category", () => {
  const rows = [
    fixtureArmy({ armyId: 1, stateKey: "expedition" }),
    fixtureArmy({ armyId: 2, stateKey: "reside" }),
  ];
  assert.deepEqual(
    filterArmies(rows, "", "reside").map((row) => row.armyId),
    [2],
  );
});

test("moving armies sort by future end time before stationary", () => {
  const rows = sortCurrentArmies([
    fixtureArmy({ armyId: 1, isMoving: false }),
    fixtureArmy({ armyId: 2, isMoving: true, endTime: 300 }),
    fixtureArmy({ armyId: 3, isMoving: true, endTime: 200 }),
  ], 100);
  assert.deepEqual(rows.map((row) => row.armyId), [3, 2, 1]);
});

test("default selection keeps earliest future arrival", () => {
  const selected = chooseDefaultArmy({
    current: [
      fixtureArmy({ armyId: 1, isMoving: true, endTime: 400 }),
      fixtureArmy({ armyId: 2, isMoving: true, endTime: 300 }),
    ],
    recentOffline: [],
  }, 100);
  assert.equal(selected, 2);
});

test("countdown clamps completed values", () => {
  assert.equal(formatArmyCountdown(161, 100), "01:01");
  assert.equal(formatArmyCountdown(99, 100), "已到达");
  assert.equal(formatArmyCountdown(0, 100), "--:--");
});
```

- [ ] **Step 2: Run tests and verify RED**

Expected: missing module.

- [ ] **Step 3: Implement pure helpers**

Pure helpers must not access DOM or network.

- [ ] **Step 4: Write failing controller interaction tests**

Use fake document elements and injected functions to verify:

- `load()` requests `offlineMinutes=10`;
- selection persists across refresh;
- selected current army moved to recent offline remains selected;
- selection updates list, map and detail exactly once;
- SSE event marks dirty;
- visible tab schedules one debounced refresh;
- hidden document does not request;
- visibility/tab re-entry flushes dirty state;
- one-second ticker only updates text while visible.

- [ ] **Step 5: Implement controller**

`createLiveArmyCommand()` dependencies:

```javascript
{
  documentRef,
  windowRef,
  fetchFn,
  setTimeoutFn,
  clearTimeoutFn,
  setIntervalFn,
  clearIntervalFn,
  nowFn,
  mapModule,
}
```

Browser default installs `window.LiveArmyCommand`.

- [ ] **Step 6: Render cards and exact evidence**

Current/offline cards:

- button semantic;
- data army ID;
- status chip;
- countdown;
- owner/union;
- route;
- exact hero thumbnails or unknown state.

Right detail:

- identity;
- spatial/timing facts;
- exact hero cards with names and skills;
- unknown message;
- evidence report.

All API-derived text must be escaped or assigned via `textContent`.

- [ ] **Step 7: Bind list and map interaction**

- click card selects;
- keyboard ArrowUp/ArrowDown changes focused card;
- Enter selects;
- Canvas click hit-tests and selects;
- Canvas double click selects then opens in intelligence;
- search and state filter re-render without refetch;
- resize redraws without refetch.

- [ ] **Step 8: Run Task 5 GREEN**

Run:

```bash
node --test \
  test/js/live-army-command.test.mjs \
  test/js/live-army-map.test.mjs

PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_dashboard_runtime_node -v
```

Expected: all pass.

---

### Task 6: 浏览器联动、响应式和完整回归

**Files:**
- Modify: `test/js/dashboard-e2e.mjs`
- Modify: `README.md`
- Modify: `test/test_dashboard_css_structure.py`

**Interfaces:**
- Verifies all previous tasks
- Produces final 12-page HUD system

- [ ] **Step 1: Add E2E API fixture**

Mock:

- exact current moving army `18411352`;
- unknown current stationary army `814501`;
- unknown state army;
- recent offline army;
- exact heroes 杜预 / 卫瓘 / 灵帝;
- route and timing.

- [ ] **Step 2: Add desktop interaction assertions**

Verify:

- “实时部队” nav position;
- tab35 active and `intelligence` domain;
- current and offline sections;
- exact lineup names and images;
- unknown message;
- current status labels;
- card click updates map/detail;
- map click selects other army;
- double click calls tab33 and locates current WID;
- search by hero name;
- state filter;
- selection retained after mocked SSE refresh.

- [ ] **Step 3: Add responsive and reduced-motion assertions**

At `1440×1000`, `1024×900`, `768×900`, `390×844`:

- page head visible;
- controls reachable;
- no document overflow;
- correct panel order.

Reduced motion:

- no route animation;
- no HUD pulse class;
- no card transform;
- Canvas draw still succeeds.

- [ ] **Step 4: Update README**

Document:

- “实时部队” navigation;
- strict `army_id` evidence;
- exact/unknown;
- recent offline;
- three-column linkage;
- new API.

- [ ] **Step 5: Run focused validation**

```bash
node --check \
  static/live-army-map.mjs \
  static/live-army-command.mjs

node --test \
  test/js/live-army-map.test.mjs \
  test/js/live-army-command.test.mjs \
  test/js/dashboard-hud.test.mjs \
  test/js/dashboard-runtime.test.mjs

PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_live_army_service \
  test.test_live_army_api \
  test.test_live_army_static \
  test.test_sidebar_navigation \
  test.test_dashboard_hud_static \
  test.test_dashboard_css_structure \
  test.test_dashboard_e2e -v
```

Expected: all pass.

- [ ] **Step 6: Run full repository validation**

```bash
git diff --check

node --test test/js/*.test.mjs

PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest discover -s test -v
```

Expected: all pass.

- [ ] **Step 7: Restart and verify port 8080**

Restart only the current `api_server.py` listener and verify:

```bash
curl -fsS \
  'http://127.0.0.1:8080/api/intelligence/live-armies?offlineMinutes=10'

curl -fsS http://127.0.0.1:8080/ |
  rg 'live-army-(command|map).*\\?v='
```

Open tab35 and confirm the exact live example:

```text
18411352 → 杜预 / 卫瓘 / 灵帝
```

---

## Self-Review Checklist

- [ ] Every spec section maps to a task.
- [ ] Service is deep and API route is thin.
- [ ] No N+1 battle lookup.
- [ ] Exact lineup never falls back by player or alliance.
- [ ] Evidence status is only exact/unknown.
- [ ] Offline boundary is inclusive at exactly 10 minutes.
- [ ] Unknown protocol state preserves the raw value.
- [ ] Map module is pure and has no animation loop.
- [ ] Controller reuses existing SSE.
- [ ] Navigation order is explicit.
- [ ] All 12 visible tabs retain approved domains.
- [ ] Mobile has no horizontal overflow.
- [ ] No game write or action API is introduced.
- [ ] No Git commit is executed.

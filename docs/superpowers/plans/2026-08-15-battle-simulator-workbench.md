# 战斗模拟工作台实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用 `/Users/bytedance/stzb/server` 的 Kotlin 战斗引擎重构 Web 战斗模拟器，交付快速对阵、侧滑武将/战法库、批量胜率和 Server 级详细战斗回放。

**Architecture:** `/Users/bytedance/stzb/server` 是唯一战斗语义真值，`battle-engine/` 由同步脚本生成独立运行镜像；Kotlin CLI 输出完整 `BattleReportCodec` 事件流和 `ClientBattleTextReplayAdapter` 动作流，Python 只做进程适配，前端只做状态、展示和事件分析。模拟器 UI 拆为语义 HTML、独立 CSS、状态控制器和纯分析模块。

**Tech Stack:** Python 3.9+、Flask、Kotlin 1.9.23/JVM 17、Gradle、Jackson、Vanilla JavaScript、ES Module、CSS Design Tokens、Node.js test runner、Playwright + 系统 Chrome。

## Global Constraints

- 战斗语义唯一来源为 `/Users/bytedance/stzb/server`。
- 不在 `stzb_watcher` 中直接手改战斗公式；语义修改必须先进入源仓库再同步。
- 不提供 Python 战斗 fallback。
- 前端不计算伤害、胜率、状态或战法结论。
- 单场、100 次、1000 次均由 Kotlin 引擎执行。
- 批量模拟保留第一场完整事件与动作流。
- 复盘必须包含准备阶段、回合动作、状态生命周期、属性变化和最终兵力。
- 未支持战法/装备效果必须可见，不能静默忽略。
- 保留 `window.StzbSimulator.loadLineup()`、`getState()`、`run()` 兼容接口。
- 模拟结果继续标记为 `SIMULATION`，不宣称等同真实战报。
- 不增加新的前端依赖和第二套 `:root` token。
- 不执行 Git commit。

---

## 文件结构

### 新建

```text
scripts/sync_battle_engine.py
battle-engine/SOURCE.json
battle-engine/src/main/kotlin/com/stzb/battle/cli/BattleReplayContract.kt
battle-engine/src/test/kotlin/com/stzb/battle/cli/BattleReplayContractTest.kt
static/simulator-workbench.css
static/simulator-workbench.js
static/simulator-analysis.mjs
test/js/simulator-analysis.test.mjs
test/js/simulator-workbench.test.mjs
test/test_battle_engine_sync.py
test/test_battle_simulator_static.py
```

### 修改

```text
battle-engine/src/main/kotlin/com/stzb/battle/cli/BattleEngineCli.kt
battle-engine/src/test/kotlin/com/stzb/battle/cli/BattleEngineCliTest.kt
battle_engine_adapter.py
api_server.py
static/dashboard.html
static/sim.js
test/test_battle_engine_adapter.py
test/test_simulate_api.py
test/test_web_runtime_hardening.py
test/js/dashboard-e2e.mjs
README.md
```

### 由同步脚本管理

```text
battle-engine/src/main/kotlin/com/stzb/battle/core/**
battle-engine/src/main/resources/battle-config/**
battle-engine/src/main/resources/client-config/**
battle-engine/src/test/kotlin/com/stzb/battle/core/**
```

---

### Task 1: 建立 `/stzb/server` 战斗引擎同步清单

**Files:**
- Create: `scripts/sync_battle_engine.py`
- Create: `test/test_battle_engine_sync.py`
- Create: `battle-engine/SOURCE.json`

**Interfaces:**
- Produces: `sync_engine(source_root: Path, target_root: Path, check: bool = False) -> dict`
- Produces: CLI `python scripts/sync_battle_engine.py --source-root <path> --target-root battle-engine [--check]`
- Produces: `battle-engine/SOURCE.json`

- [ ] **Step 1: Write failing sync contract tests**

```python
class BattleEngineSyncTest(unittest.TestCase):
    def test_sync_records_source_commit_and_file_hashes(self):
        result = sync_engine(self.source, self.target)
        self.assertEqual(result["schemaVersion"], 1)
        self.assertEqual(result["sourceRepository"], str(self.source))
        self.assertRegex(result["sourceCommit"], r"^[0-9a-f]{40}$")
        self.assertIn(
            "src/main/kotlin/com/stzb/battle/core/BattleEngine.kt",
            {row["target"] for row in result["files"]},
        )

    def test_package_mapping_preserves_non_package_content(self):
        sync_engine(self.source, self.target)
        source = (
            self.source
            / "src/main/kotlin/com/stzb/server/game/battle/BattleEngine.kt"
        ).read_text()
        target = (
            self.target
            / "src/main/kotlin/com/stzb/battle/core/BattleEngine.kt"
        ).read_text()
        self.assertEqual(
            normalize_source_package(source),
            target,
        )

    def test_check_rejects_manual_core_edit(self):
        sync_engine(self.source, self.target)
        path = self.target / "src/main/kotlin/com/stzb/battle/core/BattleEngine.kt"
        path.write_text(path.read_text() + "\n// drift\n")
        with self.assertRaisesRegex(ValueError, "generated file drift"):
            sync_engine(self.source, self.target, check=True)

    def test_check_rejects_source_commit_drift(self):
        sync_engine(self.source, self.target)
        manifest = json.loads((self.target / "SOURCE.json").read_text())
        manifest["sourceCommit"] = "0" * 40
        (self.target / "SOURCE.json").write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, "source commit drift"):
            sync_engine(self.source, self.target, check=True)
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_battle_engine_sync -v
```

Expected: FAIL with `ModuleNotFoundError` or missing `sync_engine`.

- [ ] **Step 3: Implement exact source allowlist**

```python
CORE_SOURCE_DIRS = (
    "src/main/kotlin/com/stzb/server/game/battle",
)

EXTRA_SOURCE_FILES = (
    "src/main/kotlin/com/stzb/server/game/ClientNpcArmyRepository.kt",
    "src/main/kotlin/com/stzb/server/game/SkillInventoryCatalog.kt",
)

RESOURCE_GLOBS = (
    "src/main/resources/battle-config/*",
    "src/main/resources/client-config/tb_cfg_gear.bin",
    "src/main/resources/client-config/tb_cfg_gear_feature.bin",
    "src/main/resources/client-config/tb_cfg_hero_type_feature.bin",
)

PACKAGE_REPLACEMENTS = (
    ("com.stzb.server.game.battle.skill", "com.stzb.battle.core.skill"),
    ("com.stzb.server.game.battle", "com.stzb.battle.core"),
    ("com.stzb.server.game", "com.stzb.battle.core"),
)
```

The implementation must:

```python
def transform_kotlin(text: str) -> str:
    for source, target in PACKAGE_REPLACEMENTS:
        text = text.replace(source, target)
    return text
```

Apply four named independent-runtime adapters only:

```python
ALLOWED_ADAPTERS = {
    "BattleEquipmentRepository.kt": "standalone-resource-path",
    "BattleFormationCalculator.kt": "remove-redundant-same-package-imports",
    "BattleTeamBuilder.kt": "remove-redundant-same-package-imports",
    "ClientBattleReportStore.kt": "remove-server-config-and-preserve-report-surface",
}
```

Unknown generated differences must raise `ValueError`.

- [ ] **Step 4: Write SOURCE.json**

```json
{
  "schemaVersion": 1,
  "sourceRepository": "/Users/bytedance/stzb/server",
  "sourceCommit": "93ee999937d011b2a3dadf67ed39edfbb409aaca",
  "generatedAt": "ISO-8601",
  "packageMapping": {
    "com.stzb.server.game.battle": "com.stzb.battle.core"
  },
  "adapters": [],
  "files": [],
  "resources": [],
  "excludedTests": []
}
```

Every file row contains:

```json
{
  "source": "relative/source/path",
  "target": "relative/target/path",
  "sourceSha256": "...",
  "generatedSha256": "...",
  "adapter": ""
}
```

- [ ] **Step 5: Generate from the real source**

Run:

```bash
.venv/bin/python scripts/sync_battle_engine.py \
  --source-root /Users/bytedance/stzb/server \
  --target-root battle-engine
```

Expected:

```text
synced battle engine from 93ee999937d011b2a3dadf67ed39edfbb409aaca
```

- [ ] **Step 6: Verify GREEN and drift check**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_battle_engine_sync -v

.venv/bin/python scripts/sync_battle_engine.py \
  --source-root /Users/bytedance/stzb/server \
  --target-root battle-engine \
  --check
```

Expected: all tests pass and `battle engine mirror check: PASS`.

- [ ] **Step 7: Record checkpoint without commit**

Do not run `git commit`. Record changed paths in the task log.

---

### Task 2: 迁移源引擎测试保障

**Files:**
- Modify: `scripts/sync_battle_engine.py`
- Create/Generate: `battle-engine/src/test/kotlin/com/stzb/battle/core/**`
- Modify: `battle-engine/build.gradle.kts`
- Modify: `test/test_battle_engine_sync.py`

**Interfaces:**
- Consumes: Task 1 source mapping
- Produces: mirrored Kotlin tests under `com.stzb.battle.core`
- Produces: manifest `tests` and `excludedTests`

- [ ] **Step 1: Write failing test-manifest tests**

```python
def test_sync_includes_required_source_tests(self):
    manifest = sync_engine(self.source, self.target)
    included = {row["target"] for row in manifest["tests"]}
    required = {
        "src/test/kotlin/com/stzb/battle/core/BattleEngineTest.kt",
        "src/test/kotlin/com/stzb/battle/core/BattleFormationCalculatorTest.kt",
        "src/test/kotlin/com/stzb/battle/core/BattleDamageCalculatorTest.kt",
        "src/test/kotlin/com/stzb/battle/core/skill/CompleteSkillEngineIntegrationTest.kt",
        "src/test/kotlin/com/stzb/battle/core/skill/SkillRuleInterpreterTest.kt",
        "src/test/kotlin/com/stzb/battle/core/skill/SkillTimingTest.kt",
        "src/test/kotlin/com/stzb/battle/core/OfficialFullBattleReportDiffTest.kt",
        "src/test/kotlin/com/stzb/battle/core/OfficialPreparationReportDiffTest.kt",
    }
    self.assertTrue(required <= included)

def test_excluded_tests_have_a_reason(self):
    manifest = sync_engine(self.source, self.target)
    self.assertTrue(all(row["reason"] for row in manifest["excludedTests"]))
```

- [ ] **Step 2: Run test and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_battle_engine_sync.BattleEngineSyncTest.test_sync_includes_required_source_tests \
  test.test_battle_engine_sync.BattleEngineSyncTest.test_excluded_tests_have_a_reason -v
```

Expected: FAIL because the manifest has no mirrored tests.

- [ ] **Step 3: Add test allowlist and fixture mapping**

```python
REQUIRED_TEST_FILES = (
    "BattleEngineTest.kt",
    "BattleFormationCalculatorTest.kt",
    "BattleDamageCalculatorTest.kt",
    "BattleEffectStateTest.kt",
    "BattleTeamBuilderTest.kt",
    "BattleReportCodecTest.kt",
    "ClientBattleTextReplayAdapterTest.kt",
    "ClientBattleTextReplayProtocolTest.kt",
    "OfficialReportFixture.kt",
    "OfficialReportFixtureTest.kt",
    "OfficialFullBattleReportDiffTest.kt",
    "OfficialPreparationReportDiffTest.kt",
    "CompleteSkillEngineIntegrationTest.kt",
    "ControlEffectHandlersTest.kt",
    "SkillConditionInterpreterTest.kt",
    "SkillRuleInterpreterTest.kt",
    "SkillTimingTest.kt",
)
```

Copy required fixture resources from:

```text
/Users/bytedance/stzb/server/assent/cfg/paper.zip
/Users/bytedance/stzb/server/assent/cfg/paper/**
```

to:

```text
battle-engine/src/test/resources/assent/cfg/paper.zip
battle-engine/src/test/resources/assent/cfg/paper/**
```

Adapt test fixture paths to classpath or `src/test/resources`, not absolute paths.

- [ ] **Step 4: Run mirrored Kotlin tests**

Run:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  ./gradlew test
```

Workdir:

```text
/Users/bytedance/stzb_watcher/battle-engine
```

Expected: all mirrored tests and CLI tests pass.

- [ ] **Step 5: Verify source test accounting**

Run:

```bash
.venv/bin/python scripts/sync_battle_engine.py \
  --source-root /Users/bytedance/stzb/server \
  --target-root battle-engine \
  --check
```

Expected: every source battle test is either in `tests` or `excludedTests`.

- [ ] **Step 6: Record checkpoint without commit**

Do not run `git commit`.

---

### Task 3: 输出完整语义事件和 Server 动作流

**Files:**
- Create: `battle-engine/src/main/kotlin/com/stzb/battle/cli/BattleReplayContract.kt`
- Create: `battle-engine/src/test/kotlin/com/stzb/battle/cli/BattleReplayContractTest.kt`
- Modify: `battle-engine/src/main/kotlin/com/stzb/battle/cli/BattleEngineCli.kt`
- Modify: `battle-engine/src/test/kotlin/com/stzb/battle/cli/BattleEngineCliTest.kt`

**Interfaces:**
- Produces: `BattleReplayContract.from(result: BattleResult): ReplayPayload`
- Produces: `firstRun.events`
- Produces: `firstRun.replayActions`
- Produces: `firstRun.replayText`
- Produces: `entrySnapshots`, `roundSnapshots`, `finalSnapshots`, `diagnostics`

- [ ] **Step 1: Write failing replay contract tests**

```kotlin
class BattleReplayContractTest {
    @Test
    fun `contract preserves every semantic event in order`() {
        val result = fixtureBattleResult()
        val payload = BattleReplayContract.from(result)

        assertEquals(result.events.size, payload.events.size)
        assertEquals(
            result.events.indices.toList(),
            payload.events.map { it.eventSeq },
        )
        assertTrue(payload.events.any { it.type == "HeroActionStart" })
        assertTrue(payload.events.any { it.type == "EffectBlocked" })
        assertTrue(payload.events.any { it.type == "StatChanged" })
    }

    @Test
    fun `contract includes preparation and final snapshots`() {
        val payload = BattleReplayContract.from(fixtureBattleResult())
        assertEquals(6, payload.entrySnapshots.size)
        assertTrue(payload.roundSnapshots.isNotEmpty())
        assertEquals(6, payload.finalSnapshots.size)
    }

    @Test
    fun `server action stream matches text replay adapter order`() {
        val result = fixtureBattleResult()
        val payload = BattleReplayContract.from(result)
        val expected = ClientBattleTextReplayAdapter.adapt(result)
            .map(ClientReportAction::encode)
        assertEquals(expected, payload.replayActions.map { it.encoded })
    }
}
```

- [ ] **Step 2: Run Kotlin tests and verify RED**

Run:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  ./gradlew test --tests 'com.stzb.battle.cli.BattleReplayContractTest'
```

Expected: FAIL because `BattleReplayContract` does not exist.

- [ ] **Step 3: Define exact replay DTOs**

```kotlin
data class ReplayEventDto(
    val eventSeq: Int,
    val phase: String,
    val round: Int,
    val type: String,
    val source: HeroRefDto? = null,
    val target: HeroRefDto? = null,
    val rootSkillId: Int = 0,
    val skillId: Int = 0,
    val effectId: Int = 0,
    val trigger: String = "",
    val amount: Double = 0.0,
    val targetTroopsAfter: Int? = null,
    val status: String = "",
    val durationRounds: Int = 0,
    val blockingEffectId: Int = 0,
    val stat: String = "",
    val unit: String = "",
    val deltaExact: Double = 0.0,
    val valueAfterExact: Double? = null,
)

data class ReplayActionDto(
    val actionSeq: Int,
    val actionId: Int,
    val params: List<Any>,
    val encoded: String,
)

data class HeroRoundSnapshotDto(
    val round: Int,
    val side: String,
    val position: Int,
    val heroId: Int,
    val troops: Int,
    val roundDamageTaken: Int,
    val cumulativeDamageTaken: Int,
    val roundRecovery: Int,
    val cumulativeRecovery: Int,
    val alive: Boolean,
    val activeStatuses: List<String>,
)

data class ReplayDiagnosticsDto(
    val unsupportedSkillEffects: List<ReplayEventDto>,
    val unsupportedEquipmentEffects: List<ReplayEventDto>,
    val unprojectedReplayEvents: List<String>,
    val semanticEventCount: Int,
    val replayActionCount: Int,
)
```

- [ ] **Step 4: Use BattleReportCodec-compatible complete event projection**

Move or expose the complete `BattleEvent.toReportMap()` mapping as an internal reusable function:

```kotlin
object BattleReportProjection {
    fun events(result: BattleResult): List<Map<String, Any?>> =
        result.events.mapIndexed { index, event ->
            event.toReportMap() + mapOf(
                "eventSeq" to index,
                "phase" to if (event.roundOrZero() == 0) "PREPARATION" else "BATTLE",
            )
        }
}
```

`BattleReportCodec.toJson()` and `BattleReplayContract` both consume this projection.

- [ ] **Step 5: Export Server action stream**

Expose a CLI-safe DTO from the mirrored adapter:

```kotlin
val actions = ClientBattleTextReplayAdapter.adapt(
    result,
    diagnostic = diagnostics::add,
)
val actionDtos = actions.mapIndexed { index, action ->
    ReplayActionDto(
        actionSeq = index,
        actionId = action.id,
        params = action.params,
        encoded = action.encode(),
    )
}
```

Also export:

```kotlin
val replayText = ClientReportTextEncoder.encode(result)
```

- [ ] **Step 6: Build round snapshots from events**

The snapshot builder starts from entry teams and applies in-order:

```kotlin
NormalAttack
SkillDamage
OngoingDamage
Recovery
StatusApplied
StatusRemoved
EffectExpired
BattleEnd
```

Create a snapshot after every `RoundEnd`, plus entry and final snapshots.

- [ ] **Step 7: Replace partial structuredLog in CLI**

`BattleEngineCli.kt` must emit:

```kotlin
"firstRun" to mapOf(
    "outcome" to result.outcome.name,
    "roundsPlayed" to roundsPlayed,
    "attackerHeroes" to heroSnapshots(...),
    "defenderHeroes" to heroSnapshots(...),
    "entrySnapshots" to replay.entrySnapshots,
    "roundSnapshots" to replay.roundSnapshots,
    "finalSnapshots" to replay.finalSnapshots,
    "events" to replay.events,
    "replayActions" to replay.replayActions,
    "replayText" to replay.replayText,
    "diagnostics" to replay.diagnostics,
)
```

Remove the old partial `structuredEvents()` helper after tests pass.

- [ ] **Step 8: Verify Kotlin GREEN**

Run:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  ./gradlew test
```

Expected: all mirrored source tests, replay contract tests, and CLI tests pass.

- [ ] **Step 9: Record checkpoint without commit**

Do not run `git commit`.

---

### Task 4: 强化 Python Adapter 与模拟 API

**Files:**
- Modify: `battle_engine_adapter.py`
- Modify: `api_server.py`
- Modify: `test/test_battle_engine_adapter.py`
- Modify: `test/test_simulate_api.py`

**Interfaces:**
- Consumes: Task 3 CLI contract
- Produces: `BattleEngineAdapter.engine_metadata() -> dict`
- Produces: `GET /api/simulate/engine`
- Preserves: `POST /api/simulate`

- [ ] **Step 1: Write failing adapter tests**

```python
def test_single_result_preserves_complete_replay_contract(self):
    adapter = BattleEngineAdapter(run_cli=Mock(return_value=CLI_FIXTURE))
    result = adapter.simulate(LEGACY_REQUEST)
    replay = result["result"]["replay"]
    self.assertEqual(replay["events"], CLI_FIXTURE["firstRun"]["events"])
    self.assertEqual(
        replay["replayActions"],
        CLI_FIXTURE["firstRun"]["replayActions"],
    )
    self.assertEqual(
        replay["roundSnapshots"],
        CLI_FIXTURE["firstRun"]["roundSnapshots"],
    )

def test_multi_result_preserves_first_run_replay(self):
    output = {**CLI_FIXTURE, "repeat": 100, "attackerWins": 60}
    result = BattleEngineAdapter(run_cli=Mock(return_value=output)).simulate(
        {**LEGACY_REQUEST, "repeat": 100}
    )
    self.assertIn("firstRun", result)
    self.assertEqual(result["firstRun"]["events"], output["firstRun"]["events"])

def test_invalid_repeat_is_rejected_before_cli(self):
    adapter = BattleEngineAdapter(run_cli=Mock())
    with self.assertRaisesRegex(ValueError, "repeat must be one of"):
        adapter.simulate({**LEGACY_REQUEST, "repeat": 99})
    adapter.run_cli.assert_not_called()
```

- [ ] **Step 2: Run adapter tests and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_battle_engine_adapter -v
```

Expected: replay fields missing and invalid repeat accepted.

- [ ] **Step 3: Add payload validation**

```python
ALLOWED_REPEATS = {1, 100, 1000}

def validate_payload(payload):
    repeat = int(payload.get("repeat", 1))
    if repeat not in ALLOWED_REPEATS:
        raise ValueError("repeat must be one of 1, 100, 1000")
    for side in ("blue", "red"):
        team = payload.get(side) or {}
        heroes = team.get("heros") or []
        if not 1 <= len(heroes) <= 3:
            raise ValueError(f"{side} must contain 1 to 3 heroes")
        positions = [int(hero.get("position", index)) for index, hero in enumerate(heroes)]
        if len(set(positions)) != len(positions):
            raise ValueError(f"{side} hero positions must be unique")
```

- [ ] **Step 4: Preserve replay output**

Single response:

```python
response["result"]["replay"] = {
    "entrySnapshots": first.get("entrySnapshots", []),
    "roundSnapshots": first.get("roundSnapshots", []),
    "finalSnapshots": first.get("finalSnapshots", []),
    "events": first.get("events", []),
    "replayActions": first.get("replayActions", []),
    "replayText": first.get("replayText", ""),
    "diagnostics": first.get("diagnostics", {}),
}
```

Multi response:

```python
response["firstRun"] = first
```

- [ ] **Step 5: Add engine metadata**

```python
def engine_metadata(self):
    manifest = json.loads((_ENGINE_DIR / "SOURCE.json").read_text())
    return {
        "name": "stzb-kotlin",
        "sourceRepository": manifest["sourceRepository"],
        "sourceCommit": manifest["sourceCommit"],
        "generatedAt": manifest["generatedAt"],
        "maxRepeat": 1000,
        "repeatOptions": [1, 100, 1000],
        "supportsDetailedReplay": True,
    }
```

Add route:

```python
@app.route("/api/simulate/engine")
def api_simulate_engine():
    return jsonify({"ok": True, **BattleEngineAdapter().engine_metadata()})
```

- [ ] **Step 6: Ensure Kotlin failure is explicit**

Do not import or call `battle_sim`. Errors return:

```json
{
  "ok": false,
  "engine": "stzb-kotlin",
  "error": "..."
}
```

- [ ] **Step 7: Run Python/API GREEN**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_battle_engine_adapter \
  test.test_simulate_api -v
```

Expected: all tests pass.

- [ ] **Step 8: Record checkpoint without commit**

Do not run `git commit`.

---

### Task 5: 建立纯前端状态与分析模块

**Files:**
- Create: `static/simulator-analysis.mjs`
- Create: `test/js/simulator-analysis.test.mjs`
- Create: `static/simulator-workbench.js`
- Create: `test/js/simulator-workbench.test.mjs`

**Interfaces:**
- Produces: `createSimulatorState()`
- Produces: `simulatorReducer(state, action)`
- Produces: `analyzeReplay(firstRun)`
- Produces: `groupReplayByPhase(firstRun)`
- Produces: `buildEffectChains(events)`
- Produces: `serializeTemplate(state, scope)`
- Produces: `parseTemplate(json)`

- [ ] **Step 1: Write failing reducer tests**

```javascript
test("swap sides preserves complete hero configuration", () => {
  const initial = fixtureState();
  const result = simulatorReducer(initial, { type: "swapSides" });
  assert.deepEqual(result.attacker, initial.defender);
  assert.deepEqual(result.defender, initial.attacker);
});

test("copy side clones nested skill arrays", () => {
  const initial = fixtureState();
  const result = simulatorReducer(initial, {
    type: "copySide",
    from: "attacker",
    to: "defender",
  });
  result.defender.heroes[0].extraSkillIds[0] = 999;
  assert.notEqual(initial.attacker.heroes[0].extraSkillIds[0], 999);
});
```

- [ ] **Step 2: Write failing replay analysis tests**

```javascript
test("groups preparation and battle rounds without losing event order", () => {
  const grouped = groupReplayByPhase(FIRST_RUN_FIXTURE);
  assert.equal(grouped.preparation.events[0].eventSeq, 0);
  assert.deepEqual(
    grouped.rounds[3].events.map((event) => event.eventSeq),
    [81, 82, 83, 84],
  );
});

test("effect chain connects apply block and remove events", () => {
  const chains = buildEffectChains(EFFECT_FIXTURE);
  assert.deepEqual(chains[0].lifecycle, [
    "StatusApplied",
    "EffectBlocked",
    "StatusRemoved",
  ]);
});

test("analysis exposes unsupported effects instead of hiding them", () => {
  const result = analyzeReplay(FIRST_RUN_FIXTURE);
  assert.equal(result.completeness.status, "partial");
  assert.equal(result.completeness.unsupportedSkillEffects, 1);
});
```

- [ ] **Step 3: Run Node tests and verify RED**

Run:

```bash
node --test \
  test/js/simulator-analysis.test.mjs \
  test/js/simulator-workbench.test.mjs
```

Expected: imports fail because the modules do not exist.

- [ ] **Step 4: Implement immutable state reducer**

Supported actions:

```text
setHero
removeHero
setHeroLevel
setHeroAdvance
setHeroSkill
clearHeroSkill
setMorale
setRepeat
setSeed
swapSides
copySide
openDrawer
closeDrawer
setResult
setResultView
setActiveRound
setEventFilters
loadLineup
loadTemplate
reset
```

Every action returns a new state object and clones nested arrays.

- [ ] **Step 5: Implement replay analysis**

`analyzeReplay()` returns:

```javascript
{
  totals: {
    attackerDamage: 0,
    defenderDamage: 0,
    attackerRecovery: 0,
    defenderRecovery: 0,
    controlsApplied: 0,
    evades: 0,
    blockedEffects: 0
  },
  rounds: [],
  heroSummaries: [],
  effectChains: [],
  insights: [],
  completeness: {
    status: "complete",
    unsupportedSkillEffects: 0,
    unsupportedEquipmentEffects: 0,
    unprojectedReplayEvents: 0
  }
}
```

Insights contain an evidence pointer:

```javascript
{
  kind: "turning-point",
  severity: "warning",
  title: "第 3 回合控制被阻挡",
  eventSeqs: [83, 84],
  round: 3
}
```

- [ ] **Step 6: Implement template serialization**

Template schema:

```json
{
  "schemaVersion": 1,
  "name": "魏骑对阵",
  "scope": "matchup",
  "attacker": {},
  "defender": {},
  "repeat": 100,
  "seedMode": "fixed",
  "seed": 20260810
}
```

Reject unknown schema versions and malformed hero/skill IDs.

- [ ] **Step 7: Run Node GREEN**

Run:

```bash
node --test \
  test/js/simulator-analysis.test.mjs \
  test/js/simulator-workbench.test.mjs
```

Expected: all tests pass.

- [ ] **Step 8: Record checkpoint without commit**

Do not run `git commit`.

---

### Task 6: 重构语义 HTML 与设计系统 CSS

**Files:**
- Create: `static/simulator-workbench.css`
- Modify: `static/dashboard.html`
- Create: `test/test_battle_simulator_static.py`
- Modify: `test/test_web_runtime_hardening.py`

**Interfaces:**
- Produces DOM IDs consumed by `simulator-workbench.js`
- Loads `simulator-workbench.css`, `simulator-analysis.mjs`, `simulator-workbench.js`

- [ ] **Step 1: Write failing static contract tests**

```python
class BattleSimulatorStaticTest(unittest.TestCase):
    def test_dashboard_uses_semantic_simulator_shell(self):
        html = (ROOT / "static/dashboard.html").read_text()
        self.assertIn('id="sim-workbench"', html)
        self.assertIn('id="sim-attacker-team"', html)
        self.assertIn('id="sim-defender-team"', html)
        self.assertIn('id="sim-run-controls"', html)
        self.assertIn('id="sim-result-summary"', html)
        self.assertIn('id="sim-replay-view"', html)

    def test_dashboard_removes_legacy_inline_simulator_block(self):
        html = (ROOT / "static/dashboard.html").read_text()
        section = html.split("<!-- TAB 25", 1)[1].split("<!-- TAB 26", 1)[0]
        self.assertNotIn('style="', section)
        self.assertNotIn("游戏风格战斗模拟器", section)

    def test_simulator_assets_are_loaded(self):
        html = (ROOT / "static/dashboard.html").read_text()
        self.assertIn("/static/simulator-workbench.css", html)
        self.assertIn("/static/simulator-analysis.mjs", html)
        self.assertIn("/static/simulator-workbench.js", html)
```

- [ ] **Step 2: Run static tests and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_battle_simulator_static -v
```

Expected: semantic shell and assets missing.

- [ ] **Step 3: Replace tab25 markup**

Use this top-level shape:

```html
<div class="page" id="tab25">
  <section id="sim-workbench" class="sim-workbench" aria-label="战斗模拟工作台">
    <header class="sim-header"></header>
    <div class="sim-duel">
      <section id="sim-attacker-team" class="sim-team sim-team--attacker"></section>
      <section id="sim-run-controls" class="sim-run-controls"></section>
      <section id="sim-defender-team" class="sim-team sim-team--defender"></section>
    </div>
    <section id="sim-result-summary" class="sim-result-summary" hidden></section>
    <section id="sim-replay-view" class="sim-replay" hidden></section>
    <dialog id="sim-library-dialog" class="sim-library-dialog"></dialog>
    <dialog id="sim-template-dialog" class="sim-template-dialog"></dialog>
  </section>
</div>
```

- [ ] **Step 4: Implement CSS using existing tokens**

Required layout selectors:

```css
.sim-workbench
.sim-header
.sim-duel
.sim-team
.sim-team--attacker
.sim-team--defender
.sim-run-controls
.sim-hero-grid
.sim-hero-card
.sim-skill-slot
.sim-result-summary
.sim-insights
.sim-replay
.sim-replay-phases
.sim-replay-stream
.sim-replay-detail
.sim-library-dialog
.sim-template-dialog
```

Do not use raw theme colors when a semantic token exists.

- [ ] **Step 5: Add mobile contracts**

At `max-width: 900px`:

```css
.sim-duel { grid-template-columns: 1fr; }
.sim-run-controls { order: 2; position: sticky; bottom: 8px; }
.sim-library-dialog { width: 100vw; max-width: none; }
.sim-replay { grid-template-columns: 1fr; }
```

- [ ] **Step 6: Add mtime asset version test coverage**

Include:

```text
simulator-workbench.css
simulator-workbench.js
simulator-analysis.mjs
```

in `test_index_rewrites_local_asset_versions_from_file_mtime`.

- [ ] **Step 7: Run static GREEN**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_battle_simulator_static \
  test.test_web_runtime_hardening.WebRuntimeHardeningTest.test_index_rewrites_local_asset_versions_from_file_mtime -v
```

Expected: all tests pass.

- [ ] **Step 8: Record checkpoint without commit**

Do not run `git commit`.

---

### Task 7: 实现快速对阵、侧滑库和模板

**Files:**
- Modify: `static/simulator-workbench.js`
- Modify: `static/sim.js`
- Modify: `test/js/simulator-workbench.test.mjs`
- Modify: `test/test_battle_simulator_static.py`

**Interfaces:**
- Consumes: Task 5 state reducer
- Produces: `SimulatorWorkbench.init()`
- Preserves: `window.StzbSimulator`

- [ ] **Step 1: Add failing behavior tests**

```javascript
test("selecting a hero updates only the selected slot", () => {
  const state = fixtureState();
  const result = simulatorReducer(state, {
    type: "setHero",
    side: "attacker",
    position: 1,
    hero: { id: 100016, level: 40, up: 5, equip_skills: [] },
  });
  assert.equal(result.attacker.heroes[1].id, 100016);
  assert.deepEqual(result.defender, state.defender);
});

test("template round trip preserves matchup", () => {
  const encoded = serializeTemplate(fixtureState(), "matchup");
  const decoded = parseTemplate(JSON.stringify(encoded));
  assert.deepEqual(decoded.attacker, fixtureState().attacker);
  assert.deepEqual(decoded.defender, fixtureState().defender);
});
```

- [ ] **Step 2: Run Node tests and verify RED**

Run:

```bash
node --test test/js/simulator-workbench.test.mjs
```

- [ ] **Step 3: Implement hero cards**

Each card must render:

```text
position
hero name
country
army type
level
advance
two extra skill slots
remove/replace actions
validation status
```

No per-card `<select>` elements.

- [ ] **Step 4: Implement library dialog**

Hero tab:

```text
query
country
army type
quality
range
```

Skill tab:

```text
query
skill type
quality
range
target type
```

Clicking an item dispatches `setHero` or `setHeroSkill`, then closes the dialog.

- [ ] **Step 5: Implement matchup controls**

Buttons dispatch:

```text
setRepeat(1)
setRepeat(100)
setRepeat(1000)
swapSides
copySide
reset
run
```

Keyboard:

```text
Cmd/Ctrl+Enter -> run
1 -> repeat 1
2 -> repeat 100
3 -> repeat 1000
Escape -> close dialog
```

- [ ] **Step 6: Implement local templates**

Storage key:

```text
stzb.simulator.templates.v1
```

Use JSON array with schema-validated entries. Do not overwrite state when parsing fails.

- [ ] **Step 7: Preserve external handoff**

`window.StzbSimulator.loadLineup(lineup, options)` dispatches:

```javascript
{
  type: "loadLineup",
  side: options.camp === "red" ? "defender" : "attacker",
  lineup
}
```

and returns `getState()`.

- [ ] **Step 8: Run JS/static GREEN**

Run:

```bash
node --test test/js/simulator-workbench.test.mjs
node --check static/simulator-workbench.js
node --check static/sim.js
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_battle_simulator_static -v
```

- [ ] **Step 9: Record checkpoint without commit**

Do not run `git commit`.

---

### Task 8: 实现结果摘要与 Server 级详细复盘

**Files:**
- Modify: `static/simulator-workbench.js`
- Modify: `static/simulator-analysis.mjs`
- Modify: `static/simulator-workbench.css`
- Modify: `test/js/simulator-analysis.test.mjs`
- Modify: `test/js/simulator-workbench.test.mjs`

**Interfaces:**
- Consumes: adapter `result.replay` and multi `firstRun`
- Produces: summary/replay/detail views

- [ ] **Step 1: Write failing rendering-model tests**

```javascript
test("replay model exposes preparation stages in server order", () => {
  const model = groupReplayByPhase(FIRST_RUN_FIXTURE);
  assert.deepEqual(model.preparation.stages.map((stage) => stage.key), [
    "INITIALIZATION",
    "SYSTEM",
    "COUNTRY",
    "ARMY",
    "TROOP",
    "EQUIPMENT",
    "SURFACE",
    "PASSIVE",
    "COMMAND",
  ]);
});

test("hero action envelopes keep events scoped to one actor", () => {
  const model = groupReplayByPhase(FIRST_RUN_FIXTURE);
  assert.deepEqual(
    model.rounds[3].actions[0].events.map((event) => event.type),
    ["HeroActionStart", "SkillTriggered", "StatusApplied", "HeroActionEnd"],
  );
});

test("detail lookup connects semantic event and replay actions", () => {
  const detail = replayEventDetail(FIRST_RUN_FIXTURE, 84);
  assert.equal(detail.semanticEvent.type, "EffectBlocked");
  assert.ok(detail.replayActions.length > 0);
  assert.ok(detail.replayActions.every((action) => action.encoded));
});
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
node --test test/js/simulator-analysis.test.mjs
```

- [ ] **Step 3: Implement batch result summary**

Display:

```text
attacker wins/rate
defender wins/rate
draws/rate
repeat
engine source commit
first-run completeness
```

The “enter replay” button is enabled only when `firstRun.events` exists.

- [ ] **Step 4: Implement preparation tree**

Map replay action stage markers:

```text
INITIALIZATION_READY / INITIALIZATION_BEGIN
SYSTEM_STAGE_BEGIN
COUNTRY_STAGE_BEGIN / COUNTRY_STAGE_END
ARMY_STAGE_READY
TROOP_STAGE_BEGIN
EQUIPMENT_STAGE_BEGIN
SURFACE_STAGE_READY / SURFACE_STAGE_BEGIN / SURFACE_STAGE_END
PASSIVE_STAGE_BEGIN / PASSIVE_STAGE_END
PREPARE / COMMAND_STAGE_BEGIN / PREPARATION_END
```

Show counts and diagnostics per stage.

- [ ] **Step 5: Implement round/action stream**

Group semantic events between:

```text
RoundStart
HeroActionStart
HeroActionEnd
RoundEnd
```

Each row displays:

```text
event sequence
type
source
target
skill/effect
value
remaining troops
corresponding replay actions
```

- [ ] **Step 6: Implement event filters**

Filter categories:

```text
skill
damage
recovery
status
stat
blocked
unsupported
```

Filters never change source ordering.

- [ ] **Step 7: Implement detail panel**

Tabs:

```text
state snapshot
effect chain
raw actions
```

Raw actions show:

```text
actionSeq
actionId
params
encoded
```

- [ ] **Step 8: Implement completeness panel**

Display:

```text
unsupportedSkillEffects
unsupportedEquipmentEffects
unprojectedReplayEvents
semanticEventCount
replayActionCount
sourceCommit
```

Use warning/danger styles and never hide empty/non-empty state transitions.

- [ ] **Step 9: Run Node GREEN**

Run:

```bash
node --test \
  test/js/simulator-analysis.test.mjs \
  test/js/simulator-workbench.test.mjs
```

Expected: all tests pass.

- [ ] **Step 10: Record checkpoint without commit**

Do not run `git commit`.

---

### Task 9: Chrome E2E、文档和完整回归

**Files:**
- Modify: `test/js/dashboard-e2e.mjs`
- Modify: `README.md`
- Modify: `test/test_dashboard_e2e.py`

**Interfaces:**
- Verifies the complete user flow

- [ ] **Step 1: Extend E2E API mocks**

Mock:

```text
GET /api/simulate/heroes
GET /api/simulate/engine
POST /api/simulate repeat=1
POST /api/simulate repeat=100
POST /api/simulate repeat=1000
```

The single and batch responses include:

```text
entrySnapshots
roundSnapshots
finalSnapshots
events
replayActions
diagnostics
```

- [ ] **Step 2: Add failing Chrome flow**

```javascript
await page.evaluate(() => window.switchTab(25, null));
await page.getByRole("button", { name: "攻方大营 张辽" }).click();
await page.getByPlaceholder("搜索名称、ID、阵营、兵种").fill("刘备");
await page.getByRole("button", { name: /刘备/ }).click();
await page.getByRole("button", { name: "100 次" }).click();
await page.getByRole("button", { name: "开始模拟" }).click();
await page.waitForFunction(() =>
  document.querySelector("#sim-result-summary")?.textContent.includes("63.4%")
);
await page.getByRole("button", { name: "进入战术复盘" }).click();
await page.getByRole("button", { name: "第 3 回合" }).click();
await page.getByRole("button", { name: "阻挡" }).click();
assert.equal(
  (await page.locator("#sim-replay-detail").textContent()).includes("EffectBlocked"),
  true,
);
```

- [ ] **Step 3: Run E2E and verify RED**

Run:

```bash
PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest test.test_dashboard_e2e -v
```

Expected: fail on missing new simulator controls or replay view.

- [ ] **Step 4: Fix only simulator-related E2E failures**

Do not modify unrelated dashboard behavior.

- [ ] **Step 5: Update README**

Document:

```text
source engine repository
sync command
mirror check command
JDK 17 requirement
engine metadata endpoint
quick matchup workflow
detailed replay workflow
template storage key
SIMULATION evidence disclaimer
```

Commands:

```bash
.venv/bin/python scripts/sync_battle_engine.py \
  --source-root /Users/bytedance/stzb/server \
  --target-root battle-engine

.venv/bin/python scripts/sync_battle_engine.py \
  --source-root /Users/bytedance/stzb/server \
  --target-root battle-engine \
  --check

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  ./gradlew installDist test
```

- [ ] **Step 6: Run focused validation**

Run:

```bash
node --check static/sim.js
node --check static/simulator-workbench.js
node --test \
  test/js/simulator-analysis.test.mjs \
  test/js/simulator-workbench.test.mjs

PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest \
  test.test_battle_engine_sync \
  test.test_battle_engine_adapter \
  test.test_simulate_api \
  test.test_battle_simulator_static \
  test.test_dashboard_e2e -v
```

- [ ] **Step 7: Run Kotlin validation**

Run:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  ./gradlew clean installDist test
```

Workdir:

```text
/Users/bytedance/stzb_watcher/battle-engine
```

- [ ] **Step 8: Run full repository validation**

Run:

```bash
git diff --check

.venv/bin/python scripts/sync_battle_engine.py \
  --source-root /Users/bytedance/stzb/server \
  --target-root battle-engine \
  --check

PYTHONPYCACHEPREFIX=/private/tmp/stzb-pycache \
  .venv/bin/python -m unittest discover -s test -v
```

Expected: all checks pass.

- [ ] **Step 9: Verify no test server remains**

Run:

```bash
for port in 8080 8876; do
  lsof -nP -iTCP:$port -sTCP:LISTEN || true
done
```

Terminate only test-owned `api_server.py` processes.

- [ ] **Step 10: Record final checkpoint without commit**

Do not run `git commit`.

---

## Self-Review Checklist

- [ ] Every design requirement maps to a task.
- [ ] `/Users/bytedance/stzb/server` is the only battle semantics source.
- [ ] Mirror drift detection is implemented before UI changes.
- [ ] Source tests are mirrored or explicitly excluded with reasons.
- [ ] CLI outputs complete semantic events and Server replay actions.
- [ ] Batch simulation retains first-run replay.
- [ ] Adapter does not discard replay fields.
- [ ] Frontend analysis is pure and tested.
- [ ] Quick matchup, drawer, templates, summary and replay have separate boundaries.
- [ ] Preparation stages are preserved in Server order.
- [ ] Hero action and skill envelopes preserve event causality.
- [ ] Unsupported effects remain visible.
- [ ] Existing `window.StzbSimulator` consumers remain compatible.
- [ ] No Python fallback exists.
- [ ] No Git commit is executed.

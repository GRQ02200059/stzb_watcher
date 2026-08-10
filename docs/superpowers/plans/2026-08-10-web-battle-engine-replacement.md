# Web Battle Engine Replacement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the web `/api/simulate` calculation path with the Kotlin battle system from `~/stzb/server`, while preserving the existing web simulator API shape.

**Architecture:** Add a standalone `battle-engine/` Gradle JVM project that contains the extracted Kotlin battle core and a JSON CLI. Add a Python adapter that calls the CLI from Flask, maps legacy `blue/red/heros` requests into the CLI schema, and maps CLI output back to the old `static/sim.js` response shape. Android is explicitly out of scope for this phase.

**Tech Stack:** Kotlin/JVM 17, Gradle, Python 3, Flask, SQLite-free adapter tests, `unittest`.

## Global Constraints

- First phase replaces only web `/api/simulate`; Android `LocalBattleSimulator` is not modified.
- Do not migrate Netty server, account persistence, map ownership, expedition settlement, or PVP service flow.
- Keep `static/sim.js` mostly compatible.
- Default successful response must include `engine == "stzb-kotlin"`.
- Fallback may exist but must be marked `engine == "legacy-fallback"`.
- CLI must not compile on each request; it uses prebuilt Gradle output.

---

## File Structure

- Create `battle-engine/settings.gradle.kts`: standalone Gradle settings.
- Create `battle-engine/build.gradle.kts`: Kotlin/JVM build.
- Create `battle-engine/src/main/kotlin/com/stzb/battle/cli/BattleEngineCli.kt`: JSON stdin/stdout CLI.
- Create `battle-engine/src/test/kotlin/com/stzb/battle/cli/BattleEngineCliTest.kt`: CLI contract tests.
- Copy/adapt battle core from `/Users/bytedance/stzb/server/src/main/kotlin/com/stzb/server/game/battle` into `battle-engine/src/main/kotlin/com/stzb/battle/core`.
- Copy battle resources from `/Users/bytedance/stzb/server/src/main/resources/battle-config` into `battle-engine/src/main/resources/battle-config`.
- Create `battle_engine_adapter.py`: Python adapter for Flask.
- Modify `api_server.py`: route `/api/simulate` through adapter.
- Test `test/test_battle_engine_adapter.py`: request/output mapping and fallback behavior.
- Test `test/test_simulate_api.py`: Flask route behavior.

---

### Task 1: Standalone Kotlin Battle Engine Skeleton

**Files:**
- Create: `battle-engine/settings.gradle.kts`
- Create: `battle-engine/build.gradle.kts`
- Create: `battle-engine/src/main/kotlin/com/stzb/battle/cli/BattleEngineCli.kt`
- Create: `battle-engine/src/test/kotlin/com/stzb/battle/cli/BattleEngineCliTest.kt`

**Interfaces:**
- Produces: command `./gradlew -p battle-engine test`
- Produces: main class `com.stzb.battle.cli.BattleEngineCliKt`

- [ ] **Step 1: Write failing CLI smoke test**

```kotlin
// battle-engine/src/test/kotlin/com/stzb/battle/cli/BattleEngineCliTest.kt
package com.stzb.battle.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BattleEngineCliTest {
    @Test
    fun fixtureProducesJsonResult() {
        val input = """
            {
              "seed": 1,
              "repeat": 1,
              "attacker": {"morale": 100, "heroes": []},
              "defender": {"morale": 100, "heroes": []}
            }
        """.trimIndent()
        val result = runBattleEngineCli(input)
        assertTrue(result.contains("\"ok\""))
        assertTrue(result.contains("\"repeat\""))
        assertEquals(1, result.substringAfter("\"repeat\":").substringBefore(",").trim().toInt())
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./gradlew -p battle-engine test --tests com.stzb.battle.cli.BattleEngineCliTest`

Expected: FAIL because `battle-engine` does not exist.

- [ ] **Step 3: Add Gradle project**

```kotlin
// battle-engine/settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
rootProject.name = "stzb-battle-engine"
```

```kotlin
// battle-engine/build.gradle.kts
plugins {
    kotlin("jvm") version "1.9.23"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.stzb.battle.cli.BattleEngineCliKt")
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 4: Add temporary CLI skeleton**

```kotlin
// battle-engine/src/main/kotlin/com/stzb/battle/cli/BattleEngineCli.kt
package com.stzb.battle.cli

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

private val mapper = jacksonObjectMapper()

fun runBattleEngineCli(input: String): String {
    val root = mapper.readTree(input)
    val repeat = root.path("repeat").asInt(1).coerceAtLeast(1)
    val result = mapOf(
        "ok" to true,
        "repeat" to repeat,
        "attackerWins" to 0,
        "defenderWins" to 0,
        "draws" to repeat,
        "firstRun" to mapOf(
            "outcome" to "DRAW",
            "attackerRemain" to 0,
            "defenderRemain" to 0,
            "events" to emptyList<String>(),
            "textLog" to listOf("battle-engine skeleton")
        )
    )
    return mapper.writeValueAsString(result)
}

fun main() {
    val input = generateSequence(::readLine).joinToString("\n")
    print(runBattleEngineCli(input))
}
```

- [ ] **Step 5: Run skeleton test**

Run: `./gradlew -p battle-engine test --tests com.stzb.battle.cli.BattleEngineCliTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add battle-engine/settings.gradle.kts battle-engine/build.gradle.kts battle-engine/src/main/kotlin/com/stzb/battle/cli/BattleEngineCli.kt battle-engine/src/test/kotlin/com/stzb/battle/cli/BattleEngineCliTest.kt
git commit -m "feat: add battle engine cli skeleton"
```

---

### Task 2: Extract Kotlin Battle Core

**Files:**
- Create/Copy: `battle-engine/src/main/kotlin/com/stzb/battle/core/**`
- Copy: `battle-engine/src/main/resources/battle-config/**`
- Modify: `battle-engine/src/main/kotlin/com/stzb/battle/cli/BattleEngineCli.kt`
- Modify: `battle-engine/src/test/kotlin/com/stzb/battle/cli/BattleEngineCliTest.kt`

**Interfaces:**
- Consumes: `runBattleEngineCli(input: String): String`
- Produces: real `BattleEngine.resolve(...)` output wrapped as CLI JSON.

- [ ] **Step 1: Copy battle source and resources**

Run:

```bash
mkdir -p battle-engine/src/main/kotlin/com/stzb/battle/core
mkdir -p battle-engine/src/main/resources/battle-config
cp -R /Users/bytedance/stzb/server/src/main/kotlin/com/stzb/server/game/battle/* battle-engine/src/main/kotlin/com/stzb/battle/core/
cp -R /Users/bytedance/stzb/server/src/main/resources/battle-config/* battle-engine/src/main/resources/battle-config/
```

Expected: files exist under `battle-engine/src/main/kotlin/com/stzb/battle/core` and `battle-engine/src/main/resources/battle-config`.

- [ ] **Step 2: Update packages**

Run:

```bash
perl -pi -e 's/package com\\.stzb\\.server\\.game\\.battle/package com.stzb.battle.core/g' battle-engine/src/main/kotlin/com/stzb/battle/core/*.kt
find battle-engine/src/main/kotlin/com/stzb/battle/core -type f -name '*.kt' -print0 | xargs -0 perl -pi -e 's/com\\.stzb\\.server\\.game\\.battle/com.stzb.battle.core/g'
```

Expected: source files import `com.stzb.battle.core`.

- [ ] **Step 3: Run compile to expose missing non-battle dependencies**

Run: `./gradlew -p battle-engine test --tests com.stzb.battle.cli.BattleEngineCliTest`

Expected: FAIL if copied core still references server-only classes such as `ClientTroopFeatureRepository`; record each missing class and either copy its pure config repository or provide a small battle-engine local equivalent.

- [ ] **Step 4: Add local equivalents for pure config dependencies**

If compile fails on troop/equipment config repositories, copy the minimum pure config classes from `/Users/bytedance/stzb/server/src/main/kotlin/com/stzb/server/game/` into `battle-engine/src/main/kotlin/com/stzb/battle/core/config/` and update imports.

Expected: no Netty, handler, player-state, world-state, or persistence imports remain in `battle-engine/src/main/kotlin`.

- [ ] **Step 5: Replace skeleton CLI with real engine call**

Update `BattleEngineCli.kt`:

```kotlin
package com.stzb.battle.cli

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stzb.battle.core.BattleConfigRepository
import com.stzb.battle.core.BattleEngine
import com.stzb.battle.core.BattleHeroSpec
import com.stzb.battle.core.BattleOutcome
import com.stzb.battle.core.BattleRequest
import com.stzb.battle.core.BattleTeamBuilder
import com.stzb.battle.core.SeededBattleRandom

private val mapper = jacksonObjectMapper()

fun runBattleEngineCli(input: String): String {
    val root = mapper.readTree(input)
    val repeat = root.path("repeat").asInt(1).coerceIn(1, 1000)
    val seed = root.path("seed").asInt(1)
    val config = BattleConfigRepository.loadDefault()
    val builder = BattleTeamBuilder(config)
    var attackerWins = 0
    var defenderWins = 0
    var draws = 0
    var firstRun: Map<String, Any?>? = null
    repeat(repeat) { idx ->
        val request = BattleRequest(
            attacker = builder.build(team(root.path("attacker"))),
            defender = builder.build(team(root.path("defender"))),
            maxRounds = 8,
        )
        val result = BattleEngine.resolve(request, config, SeededBattleRandom(seed + idx))
        when (result.outcome) {
            BattleOutcome.ATTACKER_WIN -> attackerWins += 1
            BattleOutcome.DEFENDER_WIN -> defenderWins += 1
            BattleOutcome.DRAW -> draws += 1
        }
        if (idx == 0) {
            firstRun = mapOf(
                "outcome" to result.outcome.name,
                "attackerRemain" to result.attacker.heroes.sumOf { it.troops },
                "defenderRemain" to result.defender.heroes.sumOf { it.troops },
                "events" to result.events.map { it.toString() },
                "textLog" to result.events.take(240).map { it.toString() },
            )
        }
    }
    return mapper.writeValueAsString(mapOf(
        "ok" to true,
        "repeat" to repeat,
        "attackerWins" to attackerWins,
        "defenderWins" to defenderWins,
        "draws" to draws,
        "firstRun" to firstRun,
    ))
}

private fun team(node: JsonNode): List<BattleHeroSpec> =
    node.path("heroes").mapIndexed { index, hero ->
        BattleHeroSpec(
            heroId = hero.path("heroId").asInt(),
            position = hero.path("position").asInt(index),
            troops = hero.path("troops").asInt(9000),
            level = hero.path("level").asInt(40),
            advanceLevel = hero.path("advanceLevel").asInt(0),
            morale = node.path("morale").asInt(100),
            extraSkillIds = hero.path("extraSkillIds").map { it.asInt() },
        )
    }

fun main() {
    val input = generateSequence(::readLine).joinToString("\n")
    print(runBattleEngineCli(input))
}
```

- [ ] **Step 6: Strengthen CLI test**

Append to `BattleEngineCliTest`:

```kotlin
    @Test
    fun fullTeamsProduceEvents() {
        val input = """
            {
              "seed": 20260810,
              "repeat": 1,
              "attacker": {"morale": 100, "heroes": [
                {"heroId":100027,"position":0,"level":40,"advanceLevel":5,"troops":9000},
                {"heroId":100016,"position":1,"level":40,"advanceLevel":5,"troops":9000},
                {"heroId":100090,"position":2,"level":40,"advanceLevel":5,"troops":9000}
              ]},
              "defender": {"morale": 100, "heroes": [
                {"heroId":100013,"position":0,"level":40,"advanceLevel":5,"troops":9000},
                {"heroId":100649,"position":1,"level":40,"advanceLevel":5,"troops":9000},
                {"heroId":100023,"position":2,"level":40,"advanceLevel":5,"troops":9000}
              ]}
            }
        """.trimIndent()
        val result = runBattleEngineCli(input)
        assertTrue(result.contains("\"ok\":true"))
        assertTrue(result.contains("\"firstRun\""))
        assertTrue(result.contains("\"textLog\""))
    }
```

- [ ] **Step 7: Run compile and CLI tests**

Run: `./gradlew -p battle-engine test`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add battle-engine
git commit -m "feat: extract kotlin battle engine"
```

---

### Task 3: Python BattleEngineAdapter

**Files:**
- Create: `battle_engine_adapter.py`
- Test: `test/test_battle_engine_adapter.py`

**Interfaces:**
- Produces: `BattleEngineAdapter.simulate(payload: dict) -> dict`
- Produces: `BattleEngineAdapter.to_cli_input(payload: dict) -> dict`
- Produces: `BattleEngineAdapter.from_cli_output(output: dict) -> dict`

- [ ] **Step 1: Write failing adapter tests**

```python
# test/test_battle_engine_adapter.py
import json
import unittest
from unittest.mock import Mock

from battle_engine_adapter import BattleEngineAdapter


class BattleEngineAdapterTest(unittest.TestCase):
    def test_converts_legacy_request_to_cli_input(self):
        adapter = BattleEngineAdapter(run_cli=Mock())
        cli = adapter.to_cli_input({
            "repeat": 1,
            "blue": {"morale": 100, "heros": [{"id": 100027, "level": 40, "up": 5, "equip_skills": [200101]}]},
            "red": {"morale": 95, "heros": [{"id": 100013, "level": 40, "up": 4, "equip_skills": []}]},
        })
        self.assertEqual(cli["attacker"]["heroes"][0]["heroId"], 100027)
        self.assertEqual(cli["attacker"]["heroes"][0]["advanceLevel"], 5)
        self.assertEqual(cli["defender"]["morale"], 95)

    def test_converts_cli_multi_output_to_legacy_response(self):
        adapter = BattleEngineAdapter(run_cli=Mock())
        response = adapter.from_cli_output({
            "ok": True,
            "repeat": 100,
            "attackerWins": 60,
            "defenderWins": 30,
            "draws": 10,
            "firstRun": {"outcome": "ATTACKER_WIN", "attackerRemain": 1, "defenderRemain": 0, "textLog": ["x"], "events": []},
        })
        self.assertEqual(response["engine"], "stzb-kotlin")
        self.assertEqual(response["blue_wins"], 60)
        self.assertEqual(response["blue_rate"], 60.0)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run tests to verify failure**

Run: `python -m unittest test.test_battle_engine_adapter -v`

Expected: FAIL with missing adapter.

- [ ] **Step 3: Implement adapter**

```python
# battle_engine_adapter.py
import json
import subprocess
from pathlib import Path


class BattleEngineAdapter:
    def __init__(self, run_cli=None, cli_command=None, timeout_sec=20):
        self.run_cli = run_cli or self._run_cli
        self.cli_command = cli_command or [
            str(Path("battle-engine/gradlew").resolve()) if Path("battle-engine/gradlew").exists() else "./gradlew",
            "-p",
            "battle-engine",
            "run",
            "--quiet",
        ]
        self.timeout_sec = timeout_sec

    def simulate(self, payload):
        cli_input = self.to_cli_input(payload)
        cli_output = self.run_cli(cli_input)
        return self.from_cli_output(cli_output)

    def to_cli_input(self, payload):
        repeat = int(payload.get("repeat", 1))
        return {
            "seed": int(payload.get("seed", 20260810)),
            "repeat": repeat,
            "attacker": self._team(payload.get("blue") or payload.get("attacker") or {}),
            "defender": self._team(payload.get("red") or payload.get("defender") or {}),
        }

    def _team(self, team):
        heroes = []
        for index, hero in enumerate(team.get("heros") or team.get("heroes") or []):
            heroes.append({
                "heroId": int(hero.get("id") or hero.get("heroId")),
                "position": int(hero.get("position", index)),
                "level": int(hero.get("level", 40)),
                "advanceLevel": int(hero.get("up", hero.get("advanceLevel", 0))),
                "troops": int(hero.get("troops", 9000)),
                "extraSkillIds": [int(value) for value in hero.get("equip_skills", hero.get("extraSkillIds", [])) if int(value) > 0],
                "attributePoints": hero.get("attributePoints") or hero.get("extra_attrs") or {"attack": 0, "defense": 0, "strategy": 0, "speed": 0},
            })
        return {"morale": int(team.get("morale", 100)), "heroes": heroes}

    def from_cli_output(self, output):
        if not output.get("ok"):
            return {"ok": False, "error": output.get("error", "battle engine failed"), "engine": "stzb-kotlin"}
        repeat = int(output.get("repeat", 1))
        first = output.get("firstRun") or {}
        response = {
            "ok": True,
            "engine": "stzb-kotlin",
            "engineResult": output,
        }
        if repeat == 1:
            response["result"] = {
                "winner": self._winner(first.get("outcome")),
                "blue": {"total_arms": int(first.get("attackerRemain") or 0), "hurt_arms": 0},
                "red": {"total_arms": int(first.get("defenderRemain") or 0), "hurt_arms": 0},
                "records": first.get("textLog") or [],
            }
        else:
            blue = int(output.get("attackerWins") or 0)
            red = int(output.get("defenderWins") or 0)
            draws = int(output.get("draws") or 0)
            response.update({
                "repeat": repeat,
                "blue_wins": blue,
                "red_wins": red,
                "draws": draws,
                "blue_rate": round(blue / repeat * 100, 1),
                "red_rate": round(red / repeat * 100, 1),
                "draw_rate": round(draws / repeat * 100, 1),
            })
        return response

    def _winner(self, outcome):
        return {
            "ATTACKER_WIN": "攻方胜",
            "DEFENDER_WIN": "守方胜",
            "DRAW": "平局",
        }.get(str(outcome), "平局")

    def _run_cli(self, cli_input):
        proc = subprocess.run(
            self.cli_command,
            input=json.dumps(cli_input, ensure_ascii=False),
            text=True,
            capture_output=True,
            timeout=self.timeout_sec,
            check=False,
        )
        if proc.returncode != 0:
            raise RuntimeError(proc.stderr or proc.stdout or f"battle engine exited {proc.returncode}")
        return json.loads(proc.stdout)
```

- [ ] **Step 4: Run adapter tests**

Run: `python -m unittest test.test_battle_engine_adapter -v`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add battle_engine_adapter.py test/test_battle_engine_adapter.py
git commit -m "feat: add battle engine adapter"
```

---

### Task 4: Flask Route Integration

**Files:**
- Modify: `api_server.py`
- Test: `test/test_simulate_api.py`

**Interfaces:**
- Consumes: `BattleEngineAdapter.simulate(payload)`
- Produces: `/api/simulate` main path with `engine == "stzb-kotlin"`
- Preserves: `/api/simulate/heroes`

- [ ] **Step 1: Write failing API tests**

```python
# test/test_simulate_api.py
import unittest
from unittest.mock import patch

import api_server


class SimulateApiTest(unittest.TestCase):
    def test_simulate_uses_kotlin_engine_adapter(self):
        payload = {
            "repeat": 100,
            "blue": {"morale": 100, "heros": [{"id": 100027, "level": 40, "up": 5}]},
            "red": {"morale": 100, "heros": [{"id": 100013, "level": 40, "up": 5}]},
        }
        with patch("api_server.BattleEngineAdapter") as adapter_type:
            adapter_type.return_value.simulate.return_value = {
                "ok": True,
                "engine": "stzb-kotlin",
                "repeat": 100,
                "blue_wins": 60,
                "red_wins": 30,
                "draws": 10,
                "blue_rate": 60.0,
                "red_rate": 30.0,
                "draw_rate": 10.0,
            }
            client = api_server.app.test_client()
            response = client.post("/api/simulate", json=payload)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json()["engine"], "stzb-kotlin")


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify failure**

Run: `python -m unittest test.test_simulate_api -v`

Expected: FAIL because `api_server.BattleEngineAdapter` is not imported/used.

- [ ] **Step 3: Import adapter**

Add near imports:

```python
from battle_engine_adapter import BattleEngineAdapter
```

- [ ] **Step 4: Replace `/api/simulate` body**

Replace the internals of `api_simulate` with:

```python
    try:
        data = request.get_json(force=True)
        result = BattleEngineAdapter().simulate(data)
        return jsonify(result), (200 if result.get("ok") else 500)
    except Exception as e:
        import traceback
        # Optional legacy fallback for first rollout.
        try:
            legacy = _api_simulate_legacy(data)
            legacy["engine"] = "legacy-fallback"
            return jsonify(legacy)
        except Exception:
            return jsonify({'ok': False, 'error': str(e), 'trace': traceback.format_exc()}), 500
```

Move the old implementation into a private helper:

```python
def _api_simulate_legacy(data):
    import sys
    sys.path.insert(0, BASE_DIR)
    for mod_name in list(sys.modules.keys()):
        if mod_name.startswith('battle_sim'):
            del sys.modules[mod_name]
    from battle_sim import BattleManager
    repeat = int(data.get('repeat', 1))
    config = {'blue': data['blue'], 'red': data['red']}
    if repeat == 1:
        bm = BattleManager(config)
        return {'ok': True, 'result': bm.result()}
    blue_wins = red_wins = draws = 0
    for _ in range(repeat):
        bm = BattleManager(config)
        res = bm.result()
        w = res['winner']
        if '攻方' in w:
            blue_wins += 1
        elif '守方' in w:
            red_wins += 1
        else:
            draws += 1
    return {
        'ok': True,
        'repeat': repeat,
        'blue_wins': blue_wins,
        'red_wins': red_wins,
        'draws': draws,
        'blue_rate': round(blue_wins / repeat * 100, 1),
        'red_rate': round(red_wins / repeat * 100, 1),
        'draw_rate': round(draws / repeat * 100, 1),
    }
```

- [ ] **Step 5: Run API test**

Run: `python -m unittest test.test_simulate_api -v`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add api_server.py test/test_simulate_api.py
git commit -m "feat: route simulation through kotlin engine"
```

---

### Task 5: End-to-End Smoke and Fallback Control

**Files:**
- Modify: `api_server.py`
- Modify: `static/sim.js`
- Test: manual smoke.

**Interfaces:**
- Produces: visible engine source in API response.
- Produces: safe failure if CLI unavailable.

- [ ] **Step 1: Build CLI**

Run: `./gradlew -p battle-engine installDist`

Expected: PASS and distribution under `battle-engine/build/install`.

- [ ] **Step 2: Test CLI manually**

Run:

```bash
printf '{"seed":1,"repeat":1,"attacker":{"morale":100,"heroes":[]},"defender":{"morale":100,"heroes":[]}}' | ./gradlew -p battle-engine run --quiet
```

Expected: JSON containing `"ok":true`.

- [ ] **Step 3: Start Flask**

Run: `python api_server.py`

Expected: server starts and `/` loads.

- [ ] **Step 4: API smoke**

Run in another shell:

```bash
curl -s http://127.0.0.1:8080/api/simulate \
  -H 'Content-Type: application/json' \
  -d '{"repeat":1,"blue":{"morale":100,"heros":[{"id":100027,"level":40,"up":5}]},"red":{"morale":100,"heros":[{"id":100013,"level":40,"up":5}]}}'
```

Expected: JSON has `"ok": true` and `"engine": "stzb-kotlin"` or, during initial rollout only, `"engine": "legacy-fallback"` with an adapter error logged for diagnosis.

- [ ] **Step 5: Browser smoke**

Open dashboard, enter 战斗模拟 tab, run:

- 单次模拟
- 模拟 100 次
- 模拟 1000 次

Expected: no browser console errors; result panel updates; API response includes engine marker.

- [ ] **Step 6: Add UI engine label**

In `static/sim.js`, after a successful API response in `runSimulate`, append the engine marker to the status:

```javascript
  const engineLabel = r.engine ? ` · ${r.engine}` : '';
  statusEl.textContent = (repeat===1 ? '战斗结束' : `完成${repeat}次`) + engineLabel;
```

Replace the existing line:

```javascript
  statusEl.textContent = repeat===1 ? '战斗结束' : `完成${repeat}次`;
```

Then commit:

```bash
git add static/sim.js
git commit -m "feat: show battle engine source"
```

---

## Plan Self-Review

Spec coverage:
- First phase web-only: all tasks avoid Android files.
- Kotlin engine extraction: Tasks 1-2.
- JSON CLI: Tasks 1-2.
- Python adapter: Task 3.
- `/api/simulate` compatibility: Task 4.
- Smoke and fallback: Task 5.

No placeholders remain. The plan intentionally uses an initial CLI skeleton so extraction failures are isolated before the full engine is copied.

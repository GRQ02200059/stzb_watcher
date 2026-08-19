# Android Complete Battle Engine Integration Design

## Goal

Use the repository's complete `battle-engine` as the Android tactical drill's real simulation backend, while retaining the existing Android team editor, online hero cards, tactical report UI, and local/offline execution.

## Problem

The Android `BattleSimulatorViewModel` currently defaults to `LocalBattleSimulatorEngine`, which calls the simplified `LocalBattleSimulator`. That simulator records command/passive skills as preparation events but only applies direct attribute deltas and simple active/pursuit calculations. It has no post-damage reaction hook. As a result, Liu Bei's `皇裔流离` is displayed as executed but its 50% post-damage emergency recovery is never calculated.

## Chosen Architecture

Add an Android Library module named `battle-engine-android`. It compiles the complete engine's pure Kotlin core and packages its battle configuration resources in the APK. The module excludes the desktop CLI, JSON report codec, server-side report store, and client/NPC file-system repositories. These excluded surfaces are not used by Android tactical simulations.

The Android adapter builds `BattleHeroSpec` values from the existing `LocalSimulationConfig`, calls the complete `BattleEngine.resolve(request, config, SeededBattleRandom(seed))`, and maps `BattleResult` plus `BattleEvent` to existing `LocalSimulationSummary`, `LocalSimulationRun`, `LocalSimulationHeroSnapshot`, and `LocalSimulationEvent`. The Compose screens and their intents remain unchanged.

## Configuration and Resource Boundary

- Package `battle-config/` resources from `battle-engine/src/main/resources` in the Android module.
- Configure Jackson Kotlin support in the Android module because `BattleConfigRepository` parses the checked-in CSV/JSON configuration data.
- Use classpath resources for Android; do not use the desktop project-root/file-system fallback.
- Build teams without desktop-only equipment/client-config repositories for this delivery. Existing basic hero, level, advance, morale, intrinsic skills, and three configurable skills are included.

## Event Mapping

| Complete engine event | Existing Android report event |
| --- | --- |
| `RoundStart` | `ROUND_START` |
| `HeroActionStart` | `ACTION` |
| `SkillTriggered` / `SkillPreparationStarted` | `ACTION` |
| `NormalAttack` / `SkillDamage` / `OngoingDamage` | `DAMAGE` |
| `Recovery` | `RECOVERY` |
| `StatusApplied`, `StatChanged`, `ModifierApplied`, `Evaded`, blocked/expired effects | `STATUS` |
| `BattleEnd` | `RESULT` |

The source/target names resolve from the generated battle teams. Damage and recovery keep exact amounts and post-event troop counts, allowing the existing clickable event detail dialog to remain accurate.

## Explicit Scope

- The default Android simulator backend changes from the lightweight engine to the complete engine.
- The old lightweight engine remains in source as a compatibility/fallback implementation only; it is not the default tactical backend.
- No network calls, Android authentication changes, server API changes, Git commits, or remote writes.
- This delivery does not add desktop-only gear file scanning or server-side NPC report-store integration to Android.

## Acceptance Criteria

1. Android build compiles the complete engine module and packages its battle configuration resources.
2. A single Android simulation emits full engine-derived preparation, round, damage/recovery/status, and result events.
3. A Liu Bei team using intrinsic skill `皇裔流离` receives the emergency-recovery status during preparation.
4. Across deterministic seeds that produce a qualifying reaction, a post-damage recovery event attributed to `皇裔流离` appears in the Android report.
5. The current tactical report UI renders preparation, every round, and the recovery event without changes to user interaction.
6. Targeted unit/instrumentation tests, signed Release build, APK signature verification, and Pixel_6 cold start pass.

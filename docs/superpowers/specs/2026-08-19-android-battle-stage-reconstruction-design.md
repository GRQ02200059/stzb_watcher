# Android Battle Stage Reconstruction Design

## Goal

Rebuild the Android tactical drill screen around BorderHelper's battle-stage composition while preserving ST Assistant data, online hero-card mapping, existing three-skill editing, and local simulation/report behavior.

## Reference Findings

The current Android screen reuses a few BorderHelper skin assets but retains a vertically stacked form layout. BorderHelper's simulation screen instead uses one continuous tactical stage: an ink-brush title, opposing red/blue team bands, three dense horizontal hero rows, a central diamond primary action, and a three-way lower action area. Its report screen repeats that language with blue/red troop bars, six card snapshots, a centered outcome panel, and dark event stream.

## Scope

- Rebuild only the `DUEL`, `REPORTS`, and `DETAIL` views under `feature/simulator`.
- Preserve `LocalBattleSimulator`, `BattleSimulatorViewModel`, report creation, picker behavior, 3 skills per hero, and the existing online `BattlefieldHeroPortrait` mapping.
- Preserve the route from Tools to simulator and existing back navigation.
- Retain the imported BorderHelper texture/frame/emblem assets and provenance record.
- Do not import BorderHelper's hero-card library, change authentication, alter remote API behavior, or commit/push changes.

## Stage Layout

### Duel

1. Render a fixed-width tactical content canvas inside a vertically scrollable screen.
2. Use an ink-like title strip with a compact return control and a close/back affordance.
3. Render blue and red team bands around a centered `对决` mark.
4. Render three compact hero rows per team. Each row contains position/name, online card image, level/advance, three action slots, and a direct hero-picker action. The row is the primary editable unit; the current vertical mega-card pattern is removed.
5. Use a centered diamond `开始推演` primary action. Place `战报库`, `阵容编辑`, and `批量推演` as adjacent stage actions. Single simulation keeps its current behavior: save a report and enter detail automatically. Batch simulations keep their aggregate behavior without adding duplicate reports.

### Reports

- Show report cards as dark tactical slips with blue/red troop meters, outcome, round count, and a direct open action.
- Keep process-local report storage only.

### Report Detail

1. Show the blue total troop bar, three compact hero cards, center result seal, red total troop bar, then three compact hero cards.
2. Keep `回合`, `状态`, and `触发` filters.
3. Make event rows dark tactical strips whose accent indicates blue action, red action, damage, recovery, or state.
4. Tapping an event keeps the current detail dialog: source, target, action, amount, and remaining troops.

## Component Boundaries

- `BattleSimulatorScreen.kt`: routing and duel-stage composition only.
- `TacticalReportScreen.kt`: shared tactical background, stage primitives, report library/detail, troop meters, and event stream.
- Existing ViewModel/contract: unchanged public intent and state semantics unless a UI-only helper state is essential.
- Instrumentation test: asserts visible stage landmarks rather than incidental layout nodes.

## Visual Rules

- Use the reference's charcoal/ink base with restrained blue/red bands; do not use the global macOS white-glass treatment inside tactical views.
- Gold remains title/selection/primary-action color.
- Team identity is blue for attack and red for defense.
- Preserve readable text contrast and portrait fallback behavior on offline devices.

## Verification

- Start with a failing Compose instrumentation test for stage landmarks: opposing team bands, six row-level hero labels, `开始推演`, and three lower stage actions.
- Run targeted instrumentation tests on `emulator-5554`; do not target the unauthorized USB device.
- Execute a real one-run simulation in Pixel_6, verify it opens a report, tap a damage event, and inspect its detail.
- Run release unit tests, assemble/sign/inspect the final Release APK, install it on Pixel_6, and perform a cold-start check.

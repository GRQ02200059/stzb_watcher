# Android Tactical Drill Design

## Goal

Embed a BorderHelper-style tactical drill module inside the Android ST assistant. The module keeps the assistant's local hero, skill, and combat data, uses the existing online hero-card mapping, and presents simulations as immersive battles and inspectable reports.

## Scope

The existing Tools entry continues to open the simulator route. Inside that route, the UI becomes a dedicated dark tactical surface with three local views: 模拟对局, 战报库, and 战报详情.

The assistant's primary navigation, capture, battle field, alliance, database, and profile pages remain unchanged.

## Data and Rules

- Hero names, IDs, levels, advances, equipped skills, and online card images continue to use LocalBattleSimulator, HeroNameResolver, SkillNameResolver, and BattlefieldHeroPortrait.
- The current local simulation remains the execution engine in this delivery. Its output is upgraded from display-only text into typed report events and hero snapshots so every visible number can be traced to a simulator event.
- A deterministic seed is preserved for every report.
- The report history is process-local. It is intentionally not added to the user database in this delivery.
- The existing one-, 100-, and 1000-run aggregate buttons remain. Only a single-run simulation is appended to the report library; aggregate runs continue to expose the first-run replay.

## BorderHelper Assets

The user owns https://github.com/zzbChina/BorderHelper and authorizes its assets for this Android module. Import only the tactical module's shared visual resources: battlefield background, gold and red card frames, red battle emblem, and UI decoration required by these views.

Do not import the repository's large hero-card collection. Android keeps its existing online hero-card mapping and text fallback. Add an asset provenance note naming the user-owned source repository and commit revision.

## UI

模拟对局 uses a dark battlefield background, reference-style team rows, framed heroes, faction bars, and a central start action. Team editing remains available through existing hero and skill pickers.

战报库 shows one card per saved single-run report with outcome, elapsed rounds, the two remaining-troop totals, and the seed. Opening a card selects it for 战报详情.

战报详情 presents attacker and defender total troop bars, six framed hero cards with initial and remaining troops, outcome and round count, tabs for 回合, 状态, and 触发, event rows grouped by round, and a bottom-sheet detail for a selected damage, recovery, or status event.

Damage is red, recovery is green, and status/control events are gold. Unsupported or generic handling is visible as a neutral event rather than represented as an exact official effect.

## Tests

- Unit tests verify a single run records typed damage, recovery, and status events and hero snapshots.
- View-model tests verify single simulations append reports, aggregate simulations do not duplicate report cards, and report selection works.
- Compose tests verify tactical tabs, report library, and selected-event detail are visible.
- Release verification builds, signs, installs, and starts on emulator-5554.

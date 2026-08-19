# Android Tactical Drill Implementation Plan

Goal: Deliver a BorderHelper-style tactical simulator and inspectable battle reports inside the Android ST assistant.

Architecture: Keep the existing Android simulation data source and online hero-card mapping. Introduce typed report data next to the local simulator, keep a process-local report library in the simulator view model, and render the tactical pages in Compose with imported user-owned skin assets.

## Tasks

1. Import the user-authorized tactical skin resources and record their source revision.
2. Add typed simulation events and hero snapshots to LocalBattleSimulator with unit tests.
3. Add a process-local tactical report library and selection state to the simulator view model with unit tests.
4. Replace the current simulator result and text-log pages with the tactical duel, report library, and report-detail Compose views.
5. Add Compose coverage, build a signed APK, install it on emulator-5554, and inspect the tactical surface.

## Constraints

- BorderHelper assets are authorized by the repository owner for this Android module.
- Do not import its large hero-card collection; continue using the assistant's online hero-card mapping and fallback.
- Keep current Tools navigation and do not change unrelated assistant pages.
- Do not commit or push.

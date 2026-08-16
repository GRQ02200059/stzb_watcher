# STZB Watcher Web UI Design System Implementation Plan

> **Goal:** Apply the approved deep-navy command-console design system to the existing Flask/Vanilla JS dashboard without changing its data APIs or business behavior.

## Scope and guardrails

- Preserve the current tab indices, element IDs, API calls, query-agent behavior, battlefield monitor behavior, and world-scene behavior.
- Add a separate presentation layer instead of rewriting the large inline stylesheet or application scripts.
- Limit edits to the dashboard shell plus new design-system assets and their static regression tests.
- Treat the approved specification in `docs/superpowers/specs/2026-08-11-stzb-web-ui-design-system-design.md` as the source of truth.

## Task 1: Lock the integration contract with tests

**Files:**
- Create: `test/test_web_ui_design_system.py`

1. Assert that `dashboard.html` loads the design-system stylesheet and enhancement script.
2. Assert that the shell exposes accessible navigation and live status semantics.
3. Assert that all approved color, typography, spacing, radius, and shadow tokens exist in the stylesheet.
4. Assert that desktop side navigation, tablet collapse, mobile card/table behavior, focus visibility, and reduced-motion behavior are present.
5. Run `python3 -m unittest test.test_web_ui_design_system` and confirm the tests fail before implementation.

## Task 2: Add the semantic dashboard shell

**Files:**
- Modify: `static/dashboard.html`
- Create: `static/dashboard-design-system.js`

1. Load the new stylesheet after the existing inline styles so it can safely override legacy presentation.
2. Add labels and live-region semantics to the header and primary navigation without changing IDs or `onclick` handlers.
3. Enhance the navigation at runtime with a brand block, grouped primary/secondary entries, inline SVG icons, a mobile menu control, and `aria-current` state.
4. Generate a compact page heading and contextual subtitle for the active tab.
5. Keep all enhancements progressive: if the new script fails, existing page switching still works.

## Task 3: Implement tokens and shared components

**Files:**
- Create: `static/dashboard-design-system.css`

1. Define the approved semantic color, type, spacing, radius, border, gradient, and shadow variables.
2. Restyle the document canvas, header, side navigation, panels, cards, tables, inputs, buttons, tags, progress bars, dialogs, and toast messages.
3. Establish a consistent 44px interaction target and visible keyboard focus.
4. Replace decorative gold/serif presentation with restrained cyan/blue command-console styling.
5. Apply numeric typography to metrics, coordinates, times, percentages, and table values.

## Task 4: Migrate the core views through compatible selectors

**Files:**
- Modify: `static/dashboard-design-system.css`
- Modify: `static/dashboard-design-system.js`

1. Treat realtime battle, all-battles, monitor, rankings, map, team data, and attendance tabs as primary navigation destinations.
2. Style KPI rows, live feeds, filters, battle tables, ranking tables, monitor timelines, and map canvases using the shared tokens.
3. Add visible status markers and text treatment for success, warning, danger, information, and protected states.
4. Add consistent loading, empty, disabled, and error presentation for existing state containers.

## Task 5: Responsive and accessibility verification

**Files:**
- Modify: `static/dashboard-design-system.css`
- Modify: `static/dashboard-design-system.js`

1. Keep the 208px side rail at desktop widths.
2. Collapse to a compact rail below 1280px and a keyboard-operable drawer below 1024px.
3. Convert dense tables to horizontally safe card-like rows below 768px and keep KPI cards in two columns where possible.
4. Respect `prefers-reduced-motion` and avoid scale-on-hover interactions.
5. Verify no page-level horizontal overflow at 375, 768, 1024, 1440, and 1920px.

## Task 6: Regression and visual checks

**Files:**
- Verify: `test/test_web_ui_design_system.py`
- Verify: `test/test_query_agent_static.py`
- Verify: `test/test_world_scene_static.py`

1. Run the new design-system tests.
2. Run existing static integration tests to ensure asset hooks and business panels remain intact.
3. Start a local server and inspect desktop, tablet, and mobile renderings.
4. Check keyboard navigation, active state, menu dismissal, live status text, and reduced-motion rules.
5. Review the final diff to confirm no backend or business-logic changes were introduced by this UI pass.

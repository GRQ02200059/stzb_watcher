# Sidebar Navigation Pruning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove obsolete sidebar and auxiliary navigation entries while preserving their underlying pages and APIs.

**Architecture:** Treat `static/dashboard.html` as the navigation source of truth, then align command-palette metadata, design-system primary tabs, settings defaults, and startup fallback. No backend or page deletion is required.

**Tech Stack:** Vanilla JavaScript, HTML, Python unittest, Node test, Chrome E2E.

## Global Constraints

- Keep removed pages and APIs intact.
- Default and fallback page must be `tab33`.
- Do not commit changes.

---

### Task 1: Lock the New Navigation Contract

**Files:**
- Modify: `test/test_command_center_static.py`
- Modify: `test/js/dashboard-e2e.mjs`

- [ ] Add assertions that removed labels are absent from `<nav>` and command metadata.
- [ ] Assert `tab33` is the initial active page.
- [ ] Run focused tests and confirm RED.

### Task 2: Prune Navigation and Defaults

**Files:**
- Modify: `static/dashboard.html`
- Modify: `static/app1.js`
- Modify: `static/dashboard-command-center.js`
- Modify: `static/dashboard-design-system.js`

- [ ] Remove the approved navigation buttons and auxiliary entries.
- [ ] Change settings and startup fallback from `31` to `33`.
- [ ] Keep `tab31` and all removed pages in the DOM for compatibility.
- [ ] Run focused tests and confirm GREEN.

### Task 3: Browser Verification

**Files:**
- Test: `test/test_dashboard_e2e.py`

- [ ] Run static and runtime tests.
- [ ] Run Chrome E2E and verify the pruned navigation.
- [ ] Run `git diff --check`.

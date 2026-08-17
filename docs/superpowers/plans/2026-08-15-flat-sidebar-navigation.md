# Flat Sidebar Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display the approved sixteen navigation tabs directly in one flat sidebar and remove all grouping and “more” behavior.

**Architecture:** Keep `dashboard.html` as the source of visible navigation truth. Simplify `dashboard-design-system.js` so it only decorates existing buttons and no longer classifies or hides them. Preserve hidden compatibility pages and APIs.

**Tech Stack:** HTML, Vanilla JavaScript, CSS, Python unittest, Chrome E2E.

## Global Constraints

- Keep removed pages and APIs compatible.
- Keep desktop fixed sidebar and mobile drawer behavior.
- Do not commit.

---

### Task 1: Navigation Contract

- Modify `test/test_sidebar_navigation.py`.
- Assert exact visible labels and order.
- Assert removed labels and commands are absent.
- Assert no more button, labels or primary/secondary metadata.
- Run RED.

### Task 2: Flat Navigation

- Modify `static/dashboard.html`.
- Remove approved entries and reveal approved hidden entries.
- Modify `static/dashboard-command-center.js`.
- Remove deleted command entries.
- Modify `static/dashboard-design-system.js`.
- Remove grouping, more button and primary/secondary logic.
- Modify `static/dashboard-design-system.css`.
- Remove obsolete show-more selectors.
- Run focused tests GREEN.

### Task 3: Browser Closure

- Modify `test/js/dashboard-e2e.mjs`.
- Enumerate and click all sixteen visible navigation buttons.
- Verify mobile drawer and no document overflow.
- Run Chrome E2E and full unittest.
- Run JavaScript syntax and `git diff --check`.

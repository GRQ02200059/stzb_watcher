import os
import re
import sqlite3
import subprocess
import tempfile
import time
import unittest
from datetime import datetime
from pathlib import Path
from unittest.mock import patch

import api_server


class WebRuntimeHardeningTest(unittest.TestCase):
    def test_optional_api_token_guards_mutating_routes_only(self):
        client = api_server.app.test_client()
        with patch.dict(os.environ, {"STZB_API_TOKEN": "secret-token"}, clear=False):
            denied = client.post("/api/refresh")
            allowed_read_only_post = client.post(
                "/api/simulate",
                json={"repeat": 1, "blue": {"heros": []}, "red": {"heros": []}},
            )
            authorized = client.post(
                "/api/refresh",
                headers={"X-STZB-Token": "secret-token"},
            )

        self.assertEqual(denied.status_code, 401)
        self.assertNotEqual(allowed_read_only_post.status_code, 401)
        self.assertNotEqual(authorized.status_code, 401)

    def test_import_does_not_start_runtime_threads(self):
        result = subprocess.run(
            [
                os.path.join(os.path.dirname(os.path.dirname(__file__)), ".venv/bin/python"),
                "-c",
                (
                    "import threading, api_server; "
                    "print(','.join(sorted(t.name for t in threading.enumerate())))"
                ),
            ],
            cwd=os.path.dirname(os.path.dirname(__file__)),
            check=True,
            capture_output=True,
            text=True,
        )
        thread_names = set(result.stdout.strip().split(","))
        self.assertNotIn("profile-watcher", thread_names)
        self.assertNotIn("realtime-writer", thread_names)
        self.assertNotIn("sniff", thread_names)

    def test_index_rewrites_local_asset_versions_from_file_mtime(self):
        response = api_server.app.test_client().get("/")
        html = response.get_data(as_text=True)

        for asset in (
            "dashboard-runtime.mjs",
            "dashboard-hud.mjs",
            "dashboard-meta.js",
            "app1.js",
            "app2.js",
            "simulator-workbench.css",
            "operations-hud.css",
            "organization-hud.css",
            "analysis-hud.css",
            "live-army-command.css",
            "simulator-workbench.js",
            "live-army-command.mjs",
            "live-army-map.mjs",
            "simulator-analysis.mjs",
            "sim.js",
            "world_scene.js",
            "dashboard-design-system.css",
            "dashboard-design-system.js",
            "dashboard-command-center.js",
            "intelligence-research-catalog.js",
            "research-workbench.mjs",
            "research-skill-chain.mjs",
            "research-templates.mjs",
        ):
            path = os.path.join(api_server.RESOURCE_DIR, "static", asset)
            expected = int(os.path.getmtime(path))
            self.assertRegex(
                html,
                rf'/static/{re.escape(asset)}\?v={expected}(?:["\'])',
            )

    def test_local_day_start_uses_local_midnight(self):
        value = api_server._local_day_start_timestamp(
            datetime(2026, 8, 14, 7, 30, 0)
        )
        expected = int(datetime(2026, 8, 14, 0, 0, 0).timestamp())
        self.assertEqual(value, expected)

    def test_command_center_today_uses_local_midnight(self):
        fd, db_path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        try:
            local_now = datetime(2026, 8, 14, 7, 30, 0)
            midnight = int(datetime(2026, 8, 14, 0, 0, 0).timestamp())
            conn = sqlite3.connect(db_path)
            conn.executescript(
                """
                CREATE TABLE battles_v2(
                    battle_id INTEGER PRIMARY KEY,
                    time INTEGER,
                    time_str TEXT,
                    result INTEGER,
                    result_desc TEXT,
                    atk_name TEXT,
                    atk_union TEXT,
                    def_name TEXT,
                    def_union TEXT,
                    wid INTEGER,
                    atk_gongxun INTEGER
                );
                """
            )
            conn.execute(
                "INSERT INTO battles_v2 VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                (
                    1,
                    midnight + 60,
                    "00:01",
                    1,
                    "攻方胜",
                    "甲",
                    "",
                    "乙",
                    "",
                    10004,
                    1,
                ),
            )
            conn.execute(
                "INSERT INTO battles_v2 VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                (
                    2,
                    midnight - 60,
                    "23:59",
                    1,
                    "攻方胜",
                    "甲",
                    "",
                    "乙",
                    "",
                    10004,
                    1,
                ),
            )
            conn.commit()
            conn.close()

            def connect():
                connection = sqlite3.connect(db_path)
                connection.row_factory = sqlite3.Row
                return connection

            with (
                patch("api_server.get_db", connect),
                patch("api_server._local_now", return_value=local_now),
                patch("api_server.time.time", return_value=local_now.timestamp()),
            ):
                body = (
                    api_server.app.test_client()
                    .get("/api/command-center/overview")
                    .get_json()
                )
            self.assertEqual(body["metrics"]["battlesToday"], 1)
        finally:
            os.unlink(db_path)


class WebRuntimeStaticHardeningTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        cls.app1 = (root / "static/app1.js").read_text(encoding="utf-8")
        cls.app2 = (root / "static/app2.js").read_text(encoding="utf-8")
        cls.hud = (root / "static/dashboard-hud.mjs").read_text(
            encoding="utf-8"
        )
        cls.world = (root / "static/world_scene.js").read_text(encoding="utf-8")
        cls.command = (root / "static/dashboard-command-center.js").read_text(
            encoding="utf-8"
        )
        cls.meta = (root / "static/dashboard-meta.js").read_text(
            encoding="utf-8"
        ) if (root / "static/dashboard-meta.js").exists() else ""
        cls.html = (root / "static/dashboard.html").read_text(encoding="utf-8")

    def test_hud_browser_seams_use_the_single_global_lifecycle(self):
        self.assertEqual(self.hud.count("window.HudSystem ="), 1)
        self.assertIn('window.addEventListener("stzb:hud-pulse"', self.hud)
        self.assertIn("defaultSystem.emit({", self.hud)
        self.assertIn('window.addEventListener("stzb:tab-changed"', self.hud)
        self.assertIn("defaultSystem.clearEffects();", self.hud)
        self.assertIn(
            'document.addEventListener("visibilitychange"',
            self.hud,
        )

    def test_sse_uses_backoff_and_active_page_refresh(self):
        self.assertIn("scheduleSseReconnect", self.app1)
        self.assertIn("Math.min(30000", self.app1)
        self.assertIn("refreshActivePage", self.app2)
        self.assertNotIn("onopen=()=>{dot.className='conn-dot live';st.textContent='实时';showToast('已连接实时数据流');refreshAll();}", self.app1)

    def test_stream_connection_tracker_executes_transition_contract(self):
        script = r"""
const fs = require("node:fs");
const vm = require("node:vm");
const assert = require("node:assert/strict");
const source = fs.readFileSync("static/app1.js", "utf8");
const storage = { getItem() { return null; }, setItem() {}, removeItem() {} };
const sandbox = {
  STZB_META: { results: {}, fightTypes: {}, regions: {} },
  document: {
    visibilityState: "visible",
    addEventListener() {},
    querySelectorAll() { return []; },
    getElementById() { return null; },
  },
  window: { dispatchEvent() {} },
  localStorage: storage,
  sessionStorage: storage,
  setInterval() { return 1; },
  setTimeout() { return 1; },
  clearTimeout() {},
  CustomEvent: class CustomEvent {},
  console,
};
vm.runInNewContext(
  source + "\n;globalThis.__createTracker = createStreamConnectionTracker;",
  sandbox,
);
let restored = 0;
let toasted = 0;
const tracker = sandbox.__createTracker({
  errorToastThreshold: 3,
  onRestored() { restored += 1; },
  onErrorThreshold() { toasted += 1; },
});

assert.equal(tracker.hasOpened, false);
tracker.markStale();
tracker.open();
assert.equal(restored, 0);
assert.equal(tracker.hasOpened, true);
tracker.markStale();
tracker.open();
assert.equal(restored, 1);
tracker.error();
tracker.error();
assert.equal(toasted, 0);
tracker.error();
assert.equal(toasted, 1);
tracker.error();
assert.equal(toasted, 1);
tracker.open();
assert.equal(restored, 2);
tracker.markStale();
tracker.open();
assert.equal(restored, 3);
tracker.error();
tracker.error();
tracker.error();
assert.equal(toasted, 2);
assert.equal(tracker.state, "error");

let initialFailureRestored = 0;
let initialFailureToasted = 0;
const initialFailureTracker = sandbox.__createTracker({
  errorToastThreshold: 3,
  onRestored() { initialFailureRestored += 1; },
  onErrorThreshold() { initialFailureToasted += 1; },
});
initialFailureTracker.error();
initialFailureTracker.error();
assert.equal(initialFailureToasted, 0);
initialFailureTracker.error();
assert.equal(initialFailureToasted, 1);
initialFailureTracker.open();
assert.equal(initialFailureRestored, 0);
assert.equal(initialFailureTracker.hasOpened, true);
"""
        result = subprocess.run(
            ["node", "-e", script],
            cwd=Path(__file__).resolve().parents[1],
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            result.returncode,
            0,
            msg=f"{result.stdout}\n{result.stderr}",
        )

    def test_sse_uses_tracker_and_has_no_initial_connection_toast(self):
        self.assertIn("createStreamConnectionTracker", self.app1)
        self.assertIn("streamConnectionTracker.open()", self.app1)
        self.assertIn("streamConnectionTracker.error()", self.app1)
        self.assertIn("markStreamStale", self.app1)
        self.assertIn('dedupeKey: "connection:error"', self.app1)
        self.assertNotIn("showToast('已连接实时数据流')", self.app1)

    def test_polling_is_centralized_and_visibility_aware(self):
        self.assertIn("startDashboardTicker", self.app2)
        self.assertIn("visibilitychange", self.app2)
        self.assertNotIn(
            "setInterval(()=>{ if(typeof loadBattleMonitor==='function') loadBattleMonitor(); }, 5000);",
            self.app2,
        )
        self.assertNotIn("new EventSource", self.world)
        self.assertIn("stzb:stream-event", self.world)

    def test_event_fallback_is_escaped_before_inner_html(self):
        self.assertIn("esc(JSON.stringify(evt.data||{}).slice(0,60))", self.app1)

    def test_query_agent_actions_are_model_backed_and_delegated(self):
        start = self.app1.index("function renderQueryAgentResponse")
        end = self.app1.index(
            "\nasync function sendQueryAgentMessage",
            start,
        )
        renderer = self.app1[start:end]
        self.assertNotIn("onclick=", renderer)
        self.assertIn("createElement", renderer)
        self.assertIn("dataset.queryActionIndex", renderer)
        self.assertIn("_queryAgentActions", self.app1)
        self.assertIn(
            "closest('[data-query-action-index]')",
            self.app1,
        )

    def test_group_tags_are_model_backed_dom_nodes_with_delegated_actions(self):
        start = self.app2.index("async function loadGroupTags")
        end = self.app2.index("\nfunction toggleGroupTag", start)
        renderer = self.app2[start:end]
        self.assertIn("document.createElement('button')", renderer)
        self.assertIn("textContent", renderer)
        self.assertIn("dataset.groupTagIndex", renderer)
        self.assertIn("_groupTagModels", self.app2)
        self.assertRegex(
            self.app2,
            r"closest\?\.\(['\"]\[data-group-tag-index\]['\"]\)",
        )
        self.assertNotIn("onclick=", renderer)
        self.assertNotIn("data-group=", renderer)
        self.assertNotIn("innerHTML+=", renderer)

    def test_command_center_handles_stale_backend_and_event_timestamps(self):
        self.assertIn("parseJsonResponse", self.command)
        self.assertIn("normalizeTimestamp", self.command)
        self.assertIn("后端接口不可用", self.command)

    def test_api_fetch_applies_optional_browser_token(self):
        self.assertIn("stzb.apiToken", self.app1)
        self.assertIn("X-STZB-Token", self.app1)
        self.assertIn('id="cc-setting-api-token"', self.html)
        self.assertIn("sessionStorage.setItem('stzb.apiToken'", self.command)

    def test_shared_frontend_metadata_is_loaded_before_consumers(self):
        self.assertIn("window.STZB_META", self.meta)
        self.assertIn("fightTypes", self.meta)
        self.assertIn("regions", self.meta)
        self.assertIn("cityTypes", self.meta)
        meta_pos = self.html.index("/static/dashboard-meta.js")
        app_pos = self.html.index("/static/app1.js")
        self.assertLess(meta_pos, app_pos)
        self.assertIn("STZB_META.fightTypes", self.app1)
        self.assertIn("STZB_META.fightTypes", self.app2)
        self.assertIn("STZB_META.cityTypes", self.world)


if __name__ == "__main__":
    unittest.main()

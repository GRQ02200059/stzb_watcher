import subprocess
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class ScoreCenterStaticTest(unittest.TestCase):
    def _run_score_behavior(self, scenario):
        script = textwrap.dedent(
            f"""
            const assert = require("node:assert/strict");
            const fs = require("node:fs");
            const vm = require("node:vm");

            const listeners = new Map();
            const elements = new Map();
            const toasts = [];
            const makeElement = (id) => ({{
              id,
              innerHTML: "",
              textContent: "",
              value: id === "score-season" ? "current" : "",
              disabled: false,
              open: false,
              dataset: {{}},
              attributes: {{}},
              setAttribute(name, value) {{
                this.attributes[name] = String(value);
              }},
              removeAttribute(name) {{
                delete this.attributes[name];
              }},
              classList: {{
                toggle() {{}},
                add() {{}},
                remove() {{}},
                contains() {{ return false; }},
              }},
              addEventListener(type, callback) {{
                if (!listeners.has(id)) listeners.set(id, {{}});
                listeners.get(id)[type] = callback;
              }},
              showModal() {{ this.open = true; }},
              close() {{ this.open = false; }},
            }});
            const document = {{
              getElementById(id) {{
                if (!elements.has(id)) elements.set(id, makeElement(id));
                return elements.get(id);
              }},
              querySelectorAll(selector) {{
                if (selector === "[data-score-rule-field]") {{
                  return [...elements.values()].filter((element) =>
                    element.dataset.scoreRuleField
                  );
                }}
                return [];
              }},
            }};
            const deferred = () => {{
              let resolve;
              let reject;
              const promise = new Promise((done, fail) => {{
                resolve = done;
                reject = fail;
              }});
              return {{promise, resolve, reject}};
            }};
            const playerRequests = new Map();
            const boardRequests = [];
            const loaderStates = [];
            const hudEvents = [];
            const apiCalls = [];
            const queuedApi = [];
            let deferBoards = false;
            const queueApi = (prefix, behavior) => {{
              queuedApi.push({{prefix, behavior}});
              return behavior;
            }};
            const apiFetch = (url, options = {{}}) => {{
              apiCalls.push({{url, options}});
              const queuedIndex = queuedApi.findIndex((entry) =>
                url.startsWith(entry.prefix)
              );
              if (queuedIndex >= 0) {{
                const {{behavior}} = queuedApi.splice(queuedIndex, 1)[0];
                if (behavior?.syncThrow) throw behavior.syncThrow;
                if (behavior?.asyncThrow) return Promise.reject(behavior.asyncThrow);
                if (behavior?.promise) return behavior.promise;
                if (typeof behavior === "function") return behavior(url, options);
                return Promise.resolve(behavior);
              }}
              if (url.startsWith("/api/custom_scores/player/")) {{
                const playerName = decodeURIComponent(
                  new URL(url, "http://local").pathname.split("/").at(-1)
                );
                const request = deferred();
                playerRequests.set(playerName, request);
                return request.promise;
              }}
              if (url.startsWith("/api/custom_scores/rules?")) {{
                return Promise.resolve({{ok: true, presets: {{}}, activeRule: null}});
              }}
              if (url.startsWith("/api/custom_scores?")) {{
                if (deferBoards) {{
                  const request = deferred();
                  request.board = new URL(url, "http://local").searchParams.get("board");
                  boardRequests.push(request);
                  return request.promise;
                }}
                return Promise.resolve({{
                  ok: true,
                  seasonId: "current",
                  board: "overall",
                  ruleVersion: 1,
                  dataCompleteness: "complete",
                  summary: {{}},
                  rows: [],
                }});
              }}
              throw new Error(`unexpected request: ${{url}}`);
            }};
            const esc = (value) => String(value ?? "")
              .replace(/&/g, "&amp;")
              .replace(/</g, "&lt;")
              .replace(/>/g, "&gt;");
            const context = {{
              console,
              document,
              window: {{
                HudSystem: {{
                  renderState(container, model) {{
                    loaderStates.push({{...model}});
                    container.textContent = model.message || model.kind;
                    if (model.kind === "loading" || model.kind === "refreshing") {{
                      container.attributes = container.attributes || {{}};
                      container.attributes["aria-busy"] = "true";
                    }} else if (container.attributes) {{
                      delete container.attributes["aria-busy"];
                    }}
                    return model;
                  }},
                  emit(event) {{
                    hudEvents.push(event);
                    return event;
                  }},
                }},
              }},
              apiFetch,
              esc,
              showToast(message, color) {{
                toasts.push({{message, color}});
              }},
              URL,
              URLSearchParams,
            }};
            vm.runInNewContext(
              fs.readFileSync("static/score-center.js", "utf8"),
              context,
            );
            const scoreCenter = context.window.ScoreCenter;
            const playerData = (playerName) => ({{
              ok: true,
              playerName,
              unionName: `${{playerName}}盟`,
              score: 100,
              battleScore: 60,
              siegeScore: 40,
              breakdown: {{metrics: {{}}, components: {{}}}},
              rule: {{version: 1}},
              adjustments: [],
            }});
            const previewData = (token, playerName, score = 10) => ({{
              ok: true,
              previewToken: token,
              summary: {{
                players: 1,
                scoreTotal: score,
                battleTotal: score,
                siegeTotal: 0,
              }},
              rows: [{{
                playerName,
                oldRank: 1,
                newRank: 1,
                scoreDelta: score,
                rankDelta: 0,
              }}],
            }});
            const clickAdjustment = () => {{
              listeners.get("score-player-content").click({{
                target: {{
                  closest(selector) {{
                    return selector === '[data-score-action="open-adjustment"]'
                      ? {{dataset: {{scoreAction: "open-adjustment"}}}}
                      : null;
                  }},
                }},
              }});
              return document.getElementById("score-adjustment-player").value;
            }};

            (async () => {{
              await scoreCenter.load();
              {scenario}
            }})().catch((error) => {{
              console.error(error);
              process.exitCode = 1;
            }});
            """
        )
        result = subprocess.run(
            ["node", "-e", script],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            result.returncode,
            0,
            msg=f"{result.stdout}\n{result.stderr}",
        )

    def test_dashboard_contains_full_score_center(self):
        html = (ROOT / "static/dashboard.html").read_text(encoding="utf-8")
        for element_id in (
            "score-season",
            "score-start-date",
            "score-end-date",
            "score-date-preset",
            "score-union",
            "score-group",
            "score-rule-version",
            "score-kpis",
            "score-board-body",
            "score-player-dialog",
            "score-rule-dialog",
            "score-rule-close",
            "score-preview-dialog",
            "score-adjustment-dialog",
        ):
            self.assertIn(f'id="{element_id}"', html)
        for board in ("overall", "battle", "siege"):
            self.assertIn(f'data-score-board="{board}"', html)
        self.assertIn("/static/score-center.css", html)
        self.assertIn("/static/score-center.js", html)

    def test_score_center_script_has_explainable_preview_flow(self):
        js = (ROOT / "static/score-center.js").read_text(encoding="utf-8")
        for token in (
            "window.ScoreCenter",
            "switchBoard",
            "openPlayer",
            "openRuleEditor",
            "preview",
            "confirmRecalculation",
            "addAdjustment",
            "previewToken",
            "battleScore",
            "siegeScore",
            "adjustmentScore",
            "dataCompleteness",
            "startDate",
            "endDate",
        ):
            self.assertIn(token, js)
        self.assertIn("今天", (ROOT / "static/dashboard.html").read_text(encoding="utf-8"))
        self.assertIn("近 7 天", (ROOT / "static/dashboard.html").read_text(encoding="utf-8"))
        self.assertIn("本月", (ROOT / "static/dashboard.html").read_text(encoding="utf-8"))
        self.assertNotIn("eval(", js)
        self.assertNotIn("new Function", js)

    def test_app_uses_score_center_module(self):
        app1 = (ROOT / "static/app1.js").read_text(encoding="utf-8")
        app2 = (ROOT / "static/app2.js").read_text(encoding="utf-8")
        self.assertIn("ScoreCenter.load", app1)
        self.assertNotIn("async function recalcScores", app2)
        self.assertNotIn("async function loadScores", app2)

    def test_score_center_uses_shared_surfaces_and_semantic_completion(self):
        css = (ROOT / "static/score-center.css").read_text(encoding="utf-8")
        js = (ROOT / "static/score-center.js").read_text(encoding="utf-8")
        for token in (
            "var(--surface-panel)",
            "var(--surface-raised)",
            "var(--surface-modal)",
        ):
            self.assertIn(token, css)
        self.assertIn('type: "score:recalculated"', js)
        self.assertIn('target: "#score-board"', js)
        self.assertIn('domain: "analysis"', js)
        self.assertIn("Number(result.updated || 0)", js)
        self.assertIn("result.ruleVersion", js)
        self.assertNotIn("stzb:hud-pulse", js)

    def test_preview_does_not_emit_before_confirmed_write(self):
        js = (ROOT / "static/score-center.js").read_text(encoding="utf-8")
        preview_body = js.split(
            "async function preview()", 1
        )[1].split("async function confirmRecalculation()", 1)[0]
        confirm_body = js.split(
            "async function confirmRecalculation()", 1
        )[1].split("function openAdjustment", 1)[0]
        self.assertNotIn("HudSystem", preview_body)
        self.assertIn("if (!result?.ok)", confirm_body)
        self.assertIn("HudSystem?.emit", confirm_body)
        if "HudSystem?.emit" in confirm_body:
            self.assertGreater(
                confirm_body.index("HudSystem?.emit"),
                confirm_body.index("if (!result?.ok)"),
            )

    def test_score_renderers_delegate_dynamic_actions_without_inline_js(self):
        js = (ROOT / "static/score-center.js").read_text(encoding="utf-8")
        open_player_body = js.split(
            "async function openPlayer(playerName)", 1
        )[1].split("function openRuleEditor", 1)[0]
        self.assertNotIn("onclick=", open_player_body)
        self.assertIn('data-score-action="open-adjustment"', open_player_body)
        self.assertIn(
            'closest(\'[data-score-action="open-adjustment"]\')',
            js,
        )
        self.assertIn("openAdjustment(state.selectedPlayer)", js)

    def test_player_detail_latest_request_commits_display_state_and_action_atomically(self):
        self._run_score_behavior(
            """
            const requestA = scoreCenter.openPlayer("玩家A");
            assert.equal(scoreCenter.state.selectedPlayer, "");
            const requestB = scoreCenter.openPlayer("玩家B");
            assert.equal(scoreCenter.state.selectedPlayer, "");

            playerRequests.get("玩家B").resolve(playerData("玩家B"));
            await requestB;
            assert.match(
              document.getElementById("score-player-content").innerHTML,
              /<h2>玩家B<\\/h2>/,
            );
            assert.equal(scoreCenter.state.selectedPlayer, "玩家B");

            playerRequests.get("玩家A").resolve(playerData("玩家A"));
            await requestA;
            const finalDetail =
              document.getElementById("score-player-content").innerHTML;
            assert.match(finalDetail, /<h2>玩家B<\\/h2>/);
            assert.doesNotMatch(finalDetail, /<h2>玩家A<\\/h2>/);
            assert.equal(scoreCenter.state.selectedPlayer, "玩家B");
            assert.equal(clickAdjustment(), "玩家B");
            """
        )

    def test_player_detail_current_failure_preserves_last_consistent_action_target(self):
        self._run_score_behavior(
            """
            const stableRequest = scoreCenter.openPlayer("稳定玩家");
            playerRequests.get("稳定玩家").resolve(playerData("稳定玩家"));
            await stableRequest;
            const stableDetail =
              document.getElementById("score-player-content").innerHTML;
            assert.equal(scoreCenter.state.selectedPlayer, "稳定玩家");

            const failedRequest = scoreCenter.openPlayer("失败玩家");
            assert.equal(scoreCenter.state.selectedPlayer, "稳定玩家");
            assert.equal(
              document.getElementById("score-player-content").innerHTML,
              stableDetail,
            );
            playerRequests.get("失败玩家").resolve({
              ok: false,
              error: "detail unavailable",
            });
            await failedRequest;

            assert.equal(
              document.getElementById("score-player-content").innerHTML,
              stableDetail,
            );
            assert.equal(scoreCenter.state.selectedPlayer, "稳定玩家");
            assert.equal(clickAdjustment(), "稳定玩家");
            """
        )

    def test_player_detail_loader_lifecycle_handles_first_refresh_errors_and_finally(self):
        self._run_score_behavior(
            """
            const dialog = document.getElementById("score-player-dialog");
            const surface = document.getElementById("score-player-surface");
            const status = document.getElementById("score-player-status");
            const content = document.getElementById("score-player-content");

            const first = scoreCenter.openPlayer("首次玩家");
            assert.equal(dialog.open, true);
            assert.equal(loaderStates.at(-1).kind, "loading");
            assert.equal(surface.attributes["aria-busy"], "true");
            assert.equal(content.innerHTML, "");
            playerRequests.get("首次玩家").resolve(playerData("首次玩家"));
            await first;
            assert.equal(loaderStates.at(-1).kind, "success");
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.match(content.innerHTML, /首次玩家/);

            const stableContent = content.innerHTML;
            const refresh = scoreCenter.openPlayer("刷新玩家");
            assert.equal(loaderStates.at(-1).kind, "refreshing");
            assert.equal(surface.attributes["aria-busy"], "true");
            assert.equal(content.innerHTML, stableContent);
            playerRequests.get("刷新玩家").resolve({
              ok: false,
              error: "detail unavailable",
            });
            await refresh;
            assert.equal(loaderStates.at(-1).kind, "error");
            assert.equal(loaderStates.at(-1).replace, false);
            assert.equal(status.textContent, "detail unavailable");
            assert.equal(content.innerHTML, stableContent);
            assert.equal(scoreCenter.state.selectedPlayer, "首次玩家");
            assert.equal(surface.attributes["aria-busy"], undefined);

            content.innerHTML = "";
            scoreCenter.state.selectedPlayer = "";
            const blocking = scoreCenter.openPlayer("阻断玩家");
            playerRequests.get("阻断玩家").reject(new Error("network down"));
            await blocking;
            assert.equal(loaderStates.at(-1).kind, "error");
            assert.equal(loaderStates.at(-1).replace, true);
            assert.equal(status.textContent, "network down");
            assert.equal(scoreCenter.state.selectedPlayer, "");
            assert.equal(surface.attributes["aria-busy"], undefined);

            queueApi("/api/custom_scores/player/同步异常", {
              syncThrow: new Error("sync detail failure"),
            });
            await scoreCenter.openPlayer("同步异常");
            assert.equal(loaderStates.at(-1).kind, "error");
            assert.equal(status.textContent, "sync detail failure");
            assert.equal(surface.attributes["aria-busy"], undefined);

            queueApi("/api/custom_scores/player/异步异常", {
              asyncThrow: new Error("async detail failure"),
            });
            await scoreCenter.openPlayer("异步异常");
            assert.equal(loaderStates.at(-1).kind, "error");
            assert.equal(status.textContent, "async detail failure");
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.ok(toasts.some((toast) => toast.message === "async detail failure"));
            """
        )

    def test_rule_save_is_one_guarded_transaction_with_visible_failures_and_cleanup(self):
        self._run_score_behavior(
            """
            const dialog = document.getElementById("score-rule-dialog");
            const surface = document.getElementById("score-rule-surface");
            const status = document.getElementById("score-rule-status");
            const button = document.getElementById("score-rule-save");
            dialog.showModal();
            apiCalls.length = 0;

            const createRequest = deferred();
            const activateRequest = deferred();
            queueApi("/api/custom_scores/rules", createRequest);
            queueApi("/api/custom_scores/rules/2/activate", activateRequest);
            const saving = scoreCenter.saveRule();
            const duplicate = scoreCenter.saveRule();
            assert.equal(button.disabled, true);
            assert.equal(surface.attributes["aria-busy"], "true");
            assert.equal(loaderStates.at(-1).kind, "loading");
            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/rules"
              ).length,
              1,
            );
            await duplicate;

            createRequest.resolve({
              ok: true,
              rule: {id: 2, version: 2},
            });
            for (let attempt = 0; attempt < 5; attempt += 1) {
              if (apiCalls.some((call) =>
                call.url === "/api/custom_scores/rules/2/activate"
              )) break;
              await Promise.resolve();
            }
            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/rules/2/activate"
              ).length,
              1,
            );
            activateRequest.resolve({ok: false, error: "activate denied"});
            await saving;
            assert.equal(dialog.open, true);
            assert.equal(loaderStates.at(-1).kind, "error");
            assert.equal(status.textContent, "activate denied");
            assert.equal(button.disabled, false);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(scoreCenter.state.ruleSaving, false);

            listeners.get("score-rule-fields").input({});
            queueApi("/api/custom_scores/rules", {
              syncThrow: new Error("create sync failure"),
            });
            await scoreCenter.saveRule();
            assert.equal(status.textContent, "create sync failure");
            assert.equal(button.disabled, false);
            assert.equal(surface.attributes["aria-busy"], undefined);

            queueApi("/api/custom_scores/rules", {
              ok: true,
              rule: {id: 3, version: 3},
            });
            queueApi("/api/custom_scores/rules/3/activate", {
              asyncThrow: new Error("activate async failure"),
            });
            await scoreCenter.saveRule();
            assert.equal(status.textContent, "activate async failure");
            assert.equal(button.disabled, false);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.ok(toasts.some((toast) =>
              toast.message === "activate async failure"
            ));
            """
        )

    def test_rule_activation_retry_reuses_created_rule_and_restores_button_busy(self):
        self._run_score_behavior(
            """
            const dialog = document.getElementById("score-rule-dialog");
            const surface = document.getElementById("score-rule-surface");
            const button = document.getElementById("score-rule-save");
            dialog.showModal();
            apiCalls.length = 0;
            queueApi("/api/custom_scores/rules", {
              ok: true,
              rule: {id: 2, version: 2},
            });
            queueApi("/api/custom_scores/rules/2/activate", {
              ok: false,
              error: "activate denied",
            });

            await scoreCenter.saveRule();

            assert.equal(scoreCenter.state.pendingRuleId, 2);
            assert.equal(scoreCenter.state.ruleTransactionPhase, "activate");
            assert.equal(button.disabled, false);
            assert.equal(button.attributes["aria-busy"], undefined);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/rules"
              ).length,
              1,
            );
            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/rules/2/activate"
              ).length,
              1,
            );

            const retry = loaderStates.at(-1).action;
            const activationRetry = deferred();
            queueApi("/api/custom_scores/rules/2/activate", activationRetry);
            const retrying = retry();
            await Promise.resolve();
            assert.equal(button.disabled, true);
            assert.equal(button.attributes["aria-busy"], "true");
            assert.equal(surface.attributes["aria-busy"], "true");
            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/rules"
              ).length,
              1,
            );
            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/rules/2/activate"
              ).length,
              2,
            );

            activationRetry.resolve({ok: true});
            await retrying;

            assert.equal(scoreCenter.state.pendingRuleId, null);
            assert.equal(scoreCenter.state.ruleTransactionPhase, "idle");
            assert.equal(dialog.open, false);
            assert.equal(button.disabled, false);
            assert.equal(button.attributes["aria-busy"], undefined);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/rules"
              ).length,
              1,
            );
            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/rules/2/activate"
              ).length,
              2,
            );
            """
        )

    def test_rule_activation_inflight_locks_controls_and_ignores_edit_and_cancel(self):
        self._run_score_behavior(
            """
            const dialog = document.getElementById("score-rule-dialog");
            const surface = document.getElementById("score-rule-surface");
            const name = document.getElementById("score-rule-name");
            const preset = document.getElementById("score-rule-preset");
            const save = document.getElementById("score-rule-save");
            const close = document.getElementById("score-rule-close");
            const field = document.getElementById("mock-rule-field");
            field.dataset.scoreRuleField = "winWeight";
            field.value = "3";
            dialog.showModal();
            queueApi("/api/custom_scores/rules", {
              ok: true,
              rule: {id: 9, version: 9},
            });
            const activation = deferred();
            queueApi("/api/custom_scores/rules/9/activate", activation);

            const saving = scoreCenter.saveRule();
            for (let attempt = 0; attempt < 5; attempt += 1) {
              if (apiCalls.some((call) =>
                call.url === "/api/custom_scores/rules/9/activate"
              )) break;
              await Promise.resolve();
            }

            assert.equal(scoreCenter.state.pendingRuleId, 9);
            for (const control of [name, preset, field, save, close]) {
              assert.equal(control.disabled, true);
              assert.equal(control.attributes["aria-busy"], "true");
            }
            assert.equal(surface.attributes["aria-busy"], "true");

            listeners.get("score-rule-name").input({});
            listeners.get("score-rule-preset").change({
              target: {value: "season_reward"},
            });
            listeners.get("score-rule-fields").input({});
            listeners.get("score-rule-dialog").close({});
            let cancelPrevented = false;
            listeners.get("score-rule-dialog").cancel({
              preventDefault() {
                cancelPrevented = true;
              },
            });
            assert.equal(cancelPrevented, true);
            assert.equal(scoreCenter.state.pendingRuleId, 9);
            assert.equal(scoreCenter.state.ruleTransactionPhase, "activate");
            assert.equal(dialog.open, true);

            activation.resolve({ok: false, error: "activate deferred failure"});
            await saving;

            assert.equal(scoreCenter.state.pendingRuleId, 9);
            assert.equal(scoreCenter.state.ruleTransactionPhase, "activate");
            for (const control of [name, preset, field, save, close]) {
              assert.equal(control.disabled, false);
              assert.equal(control.attributes["aria-busy"], undefined);
            }
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(dialog.open, true);

            dialog.close();
            listeners.get("score-rule-dialog").close({});
            assert.equal(scoreCenter.state.pendingRuleId, null);
            assert.equal(scoreCenter.state.ruleTransactionPhase, "idle");
            assert.equal(dialog.open, false);
            """
        )

    def test_stale_rule_transaction_cannot_commit_or_release_newer_owner(self):
        self._run_score_behavior(
            """
            const dialog = document.getElementById("score-rule-dialog");
            const surface = document.getElementById("score-rule-surface");
            const save = document.getElementById("score-rule-save");
            const close = document.getElementById("score-rule-close");
            dialog.showModal();
            apiCalls.length = 0;
            queueApi("/api/custom_scores/rules", {
              ok: true,
              rule: {id: 10, version: 10},
            });
            const oldActivation = deferred();
            queueApi("/api/custom_scores/rules/10/activate", oldActivation);

            const oldSaving = scoreCenter.saveRule();
            for (let attempt = 0; attempt < 5; attempt += 1) {
              if (apiCalls.some((call) =>
                call.url === "/api/custom_scores/rules/10/activate"
              )) break;
              await Promise.resolve();
            }
            const oldRevision = scoreCenter.state.ruleTransactionRevision;
            assert.equal(scoreCenter.state.pendingRuleId, 10);

            scoreCenter.state.ruleSaving = false;
            scoreCenter.state.pendingRuleId = null;
            scoreCenter.state.pendingRuleVersion = null;
            scoreCenter.state.ruleTransactionPhase = "idle";
            queueApi("/api/custom_scores/rules", {
              ok: true,
              rule: {id: 11, version: 11},
            });
            const newActivation = deferred();
            queueApi("/api/custom_scores/rules/11/activate", newActivation);
            const newSaving = scoreCenter.saveRule();
            for (let attempt = 0; attempt < 5; attempt += 1) {
              if (apiCalls.some((call) =>
                call.url === "/api/custom_scores/rules/11/activate"
              )) break;
              await Promise.resolve();
            }
            assert.ok(scoreCenter.state.ruleTransactionRevision > oldRevision);
            assert.equal(scoreCenter.state.pendingRuleId, 11);
            assert.equal(scoreCenter.state.ruleSaving, true);

            oldActivation.resolve({ok: true});
            await oldSaving;

            assert.equal(dialog.open, true);
            assert.equal(scoreCenter.state.pendingRuleId, 11);
            assert.equal(scoreCenter.state.pendingRuleVersion, 11);
            assert.equal(scoreCenter.state.ruleTransactionPhase, "activate");
            assert.equal(scoreCenter.state.ruleSaving, true);
            assert.equal(save.disabled, true);
            assert.equal(save.attributes["aria-busy"], "true");
            assert.equal(close.disabled, true);
            assert.equal(surface.attributes["aria-busy"], "true");
            assert.equal(
              apiCalls.filter((call) =>
                call.url.startsWith("/api/custom_scores?")
              ).length,
              0,
            );
            assert.ok(!toasts.some((toast) =>
              toast.message === "规则 v10 已激活"
            ));

            newActivation.resolve({ok: true});
            await newSaving;

            assert.equal(dialog.open, false);
            assert.equal(scoreCenter.state.pendingRuleId, null);
            assert.equal(scoreCenter.state.ruleTransactionPhase, "idle");
            assert.equal(scoreCenter.state.ruleSaving, false);
            assert.equal(save.disabled, false);
            assert.equal(save.attributes["aria-busy"], undefined);
            assert.equal(close.disabled, false);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(
              apiCalls.filter((call) =>
                call.url.startsWith("/api/custom_scores?")
              ).length,
              1,
            );
            assert.ok(toasts.some((toast) =>
              toast.message === "规则 v11 已激活"
            ));
            """
        )

    def test_rule_pending_activation_clears_on_configuration_edit_and_cancel(self):
        self._run_score_behavior(
            """
            const failActivation = async (ruleId) => {
              queueApi("/api/custom_scores/rules", {
                ok: true,
                rule: {id: ruleId, version: ruleId},
              });
              queueApi(`/api/custom_scores/rules/${ruleId}/activate`, {
                ok: false,
                error: "activate denied",
              });
              await scoreCenter.saveRule();
              assert.equal(scoreCenter.state.pendingRuleId, ruleId);
              assert.equal(scoreCenter.state.ruleTransactionPhase, "activate");
            };

            await failActivation(5);
            listeners.get("score-rule-fields").input({});
            assert.equal(scoreCenter.state.pendingRuleId, null);
            assert.equal(scoreCenter.state.ruleTransactionPhase, "idle");

            await failActivation(6);
            listeners.get("score-rule-name").input({});
            assert.equal(scoreCenter.state.pendingRuleId, null);
            assert.equal(scoreCenter.state.ruleTransactionPhase, "idle");

            await failActivation(7);
            listeners.get("score-rule-preset").change({
              target: {value: "season_reward"},
            });
            assert.equal(scoreCenter.state.pendingRuleId, null);
            assert.equal(scoreCenter.state.ruleTransactionPhase, "idle");

            await failActivation(8);
            listeners.get("score-rule-dialog").close({});
            assert.equal(scoreCenter.state.pendingRuleId, null);
            assert.equal(scoreCenter.state.ruleTransactionPhase, "idle");
            """
        )

    def test_rule_and_adjustment_reopen_reset_stale_state_without_clearing_business_values(self):
        self._run_score_behavior(
            """
            scoreCenter.state.rules = {
              presets: {
                alliance_contribution: {winWeight: 2},
              },
              activeRule: {
                preset_key: "alliance_contribution",
                config: {winWeight: 3},
              },
            };
            const ruleName = document.getElementById("score-rule-name");
            const ruleStatus = document.getElementById("score-rule-status");
            ruleName.value = "保留规则名";
            ruleStatus.textContent = "旧规则错误";
            scoreCenter.openRuleEditor();
            assert.equal(loaderStates.at(-1).kind, "idle");
            assert.equal(ruleStatus.textContent, "idle");
            assert.equal(ruleName.value, "保留规则名");

            ruleStatus.textContent = "旧规则成功";
            document.getElementById("score-rule-dialog").close();
            scoreCenter.openRuleEditor();
            assert.equal(loaderStates.at(-1).kind, "idle");
            assert.equal(ruleStatus.textContent, "idle");
            assert.equal(ruleName.value, "保留规则名");

            const adjustmentStatus =
              document.getElementById("score-adjustment-status");
            adjustmentStatus.textContent = "旧调整错误";
            scoreCenter.openAdjustment("保留玩家");
            assert.equal(loaderStates.at(-1).kind, "idle");
            assert.equal(adjustmentStatus.textContent, "idle");
            assert.equal(
              document.getElementById("score-adjustment-player").value,
              "保留玩家",
            );

            adjustmentStatus.textContent = "旧调整成功";
            document.getElementById("score-adjustment-dialog").close();
            scoreCenter.openAdjustment("再次玩家");
            assert.equal(loaderStates.at(-1).kind, "idle");
            assert.equal(adjustmentStatus.textContent, "idle");
            assert.equal(
              document.getElementById("score-adjustment-player").value,
              "再次玩家",
            );
            """
        )

    def test_rule_save_success_activates_once_closes_and_refreshes(self):
        self._run_score_behavior(
            """
            const dialog = document.getElementById("score-rule-dialog");
            const surface = document.getElementById("score-rule-surface");
            const button = document.getElementById("score-rule-save");
            dialog.showModal();
            apiCalls.length = 0;
            queueApi("/api/custom_scores/rules", {
              ok: true,
              rule: {id: 4, version: 4},
            });
            queueApi("/api/custom_scores/rules/4/activate", {ok: true});

            await scoreCenter.saveRule();

            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/rules"
              ).length,
              1,
            );
            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/rules/4/activate"
              ).length,
              1,
            );
            assert.equal(
              apiCalls.filter((call) =>
                call.url.startsWith("/api/custom_scores?")
              ).length,
              1,
            );
            assert.equal(dialog.open, false);
            assert.equal(button.disabled, false);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(scoreCenter.state.ruleSaving, false);
            assert.ok(toasts.some((toast) =>
              toast.message === "规则 v4 已激活"
            ));
            """
        )

    def test_preview_latest_revision_owns_content_busy_and_preserving_errors(self):
        self._run_score_behavior(
            """
            const dialog = document.getElementById("score-preview-dialog");
            const surface = document.getElementById("score-preview-surface");
            const status = document.getElementById("score-preview-status");
            const openButton = document.getElementById("score-preview-open");
            const confirmButton = document.getElementById("score-preview-confirm");
            const summary = document.getElementById("score-preview-summary");
            const rows = document.getElementById("score-preview-rows");

            queueApi(
              "/api/custom_scores/preview",
              previewData("stable-token", "稳定预览", 12),
            );
            const first = scoreCenter.preview();
            assert.equal(dialog.open, true);
            assert.equal(loaderStates.at(-1).kind, "loading");
            assert.equal(surface.attributes["aria-busy"], "true");
            assert.equal(openButton.disabled, true);
            assert.equal(confirmButton.disabled, true);
            await first;
            assert.equal(scoreCenter.state.preview.previewToken, "stable-token");
            assert.match(rows.innerHTML, /稳定预览/);
            assert.equal(loaderStates.at(-1).kind, "success");
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(openButton.disabled, false);
            assert.equal(confirmButton.disabled, false);

            const stableSummary = summary.innerHTML;
            const stableRows = rows.innerHTML;
            const olderRequest = deferred();
            const newerRequest = deferred();
            queueApi("/api/custom_scores/preview", olderRequest);
            queueApi("/api/custom_scores/preview", newerRequest);
            const older = scoreCenter.preview();
            assert.equal(loaderStates.at(-1).kind, "refreshing");
            assert.equal(summary.innerHTML, stableSummary);
            assert.equal(rows.innerHTML, stableRows);
            const newer = scoreCenter.preview();
            assert.equal(surface.attributes["aria-busy"], "true");
            assert.equal(openButton.disabled, true);
            assert.equal(confirmButton.disabled, true);

            olderRequest.resolve(previewData("old-token", "旧预览", 8));
            await older;
            assert.equal(scoreCenter.state.preview.previewToken, "stable-token");
            assert.match(rows.innerHTML, /稳定预览/);
            assert.doesNotMatch(rows.innerHTML, /旧预览/);
            assert.equal(surface.attributes["aria-busy"], "true");
            assert.equal(openButton.disabled, true);

            newerRequest.resolve(previewData("new-token", "新预览", 20));
            await newer;
            assert.equal(scoreCenter.state.preview.previewToken, "new-token");
            assert.match(rows.innerHTML, /新预览/);
            assert.doesNotMatch(rows.innerHTML, /旧预览/);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(openButton.disabled, false);
            assert.equal(confirmButton.disabled, false);

            const newRows = rows.innerHTML;
            queueApi("/api/custom_scores/preview", {
              asyncThrow: new Error("preview async failure"),
            });
            await scoreCenter.preview();
            assert.equal(loaderStates.at(-1).kind, "error");
            assert.equal(loaderStates.at(-1).replace, false);
            assert.equal(status.textContent, "preview async failure");
            assert.equal(rows.innerHTML, newRows);
            assert.equal(scoreCenter.state.preview.previewToken, "new-token");
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(openButton.disabled, false);
            assert.equal(confirmButton.disabled, false);

            scoreCenter.state.preview = null;
            summary.innerHTML = "孤立旧摘要";
            rows.innerHTML = "孤立旧预览";
            queueApi("/api/custom_scores/preview", {
              syncThrow: new Error("preview sync failure"),
            });
            const blockingFailure = scoreCenter.preview();
            assert.equal(summary.innerHTML, "");
            assert.equal(rows.innerHTML, "");
            await blockingFailure;
            assert.equal(loaderStates.at(-1).kind, "error");
            assert.equal(loaderStates.at(-1).replace, true);
            assert.equal(status.textContent, "preview sync failure");
            assert.equal(scoreCenter.state.preview, null);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(openButton.disabled, false);
            assert.equal(confirmButton.disabled, true);
            assert.ok(toasts.some((toast) =>
              toast.message === "preview sync failure"
            ));
            """
        )

    def test_state_retry_action_handles_sync_and_async_failures_without_busy_leaks(self):
        self._run_score_behavior(
            """
            const surface = document.getElementById("score-preview-surface");
            const openButton = document.getElementById("score-preview-open");
            queueApi("/api/custom_scores/preview", {
              syncThrow: new Error("initial preview failure"),
            });
            await scoreCenter.preview();
            const syncRetry = loaderStates.at(-1).action;
            assert.equal(typeof syncRetry, "function");
            queueApi("/api/custom_scores/preview", {
              syncThrow: new Error("retry sync failure"),
            });
            await syncRetry();
            assert.ok(toasts.some((toast) =>
              toast.message === "retry sync failure"
            ));
            assert.equal(openButton.disabled, false);
            assert.equal(surface.attributes["aria-busy"], undefined);

            const asyncRetry = loaderStates.at(-1).action;
            assert.equal(typeof asyncRetry, "function");
            queueApi("/api/custom_scores/preview", {
              asyncThrow: new Error("retry async failure"),
            });
            await asyncRetry();
            assert.ok(toasts.some((toast) =>
              toast.message === "retry async failure"
            ));
            assert.equal(openButton.disabled, false);
            assert.equal(surface.attributes["aria-busy"], undefined);
            """
        )

    def test_confirm_recalculation_guards_duplicate_writes_and_restores_all_failures(self):
        self._run_score_behavior(
            """
            const dialog = document.getElementById("score-preview-dialog");
            const surface = document.getElementById("score-preview-surface");
            const status = document.getElementById("score-preview-status");
            const button = document.getElementById("score-preview-confirm");
            const openButton = document.getElementById("score-preview-open");
            dialog.showModal();
            apiCalls.length = 0;

            button.disabled = false;
            button.setAttribute("aria-busy", "true");
            openButton.disabled = true;
            openButton.setAttribute("aria-busy", "true");
            surface.setAttribute("aria-busy", "true");
            await scoreCenter.confirmRecalculation();
            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/recalc"
              ).length,
              0,
            );
            assert.equal(button.disabled, false);
            assert.equal(button.attributes["aria-busy"], undefined);
            assert.equal(openButton.disabled, false);
            assert.equal(openButton.attributes["aria-busy"], undefined);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(scoreCenter.state.recalculating, false);

            scoreCenter.state.preview =
              previewData("failed-token", "待确认预览", 15);
            const failedRequest = deferred();
            queueApi("/api/custom_scores/recalc", failedRequest);
            const failing = scoreCenter.confirmRecalculation();
            const duplicate = scoreCenter.confirmRecalculation();
            assert.equal(button.disabled, true);
            assert.equal(button.attributes["aria-busy"], "true");
            assert.equal(surface.attributes["aria-busy"], "true");
            assert.equal(loaderStates.at(-1).kind, "refreshing");
            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/recalc"
              ).length,
              1,
            );
            await duplicate;
            failedRequest.resolve({ok: false, error: "recalc rejected"});
            await failing;
            assert.equal(dialog.open, true);
            assert.equal(status.textContent, "recalc rejected");
            assert.equal(loaderStates.at(-1).kind, "error");
            assert.equal(loaderStates.at(-1).replace, false);
            assert.equal(button.disabled, false);
            assert.equal(button.attributes["aria-busy"], undefined);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(scoreCenter.state.recalculating, false);
            assert.equal(
              scoreCenter.state.preview.previewToken,
              "failed-token",
            );
            assert.equal(hudEvents.length, 0);

            queueApi("/api/custom_scores/recalc", {
              syncThrow: new Error("recalc sync failure"),
            });
            await scoreCenter.confirmRecalculation();
            assert.equal(status.textContent, "recalc sync failure");
            assert.equal(button.disabled, false);
            assert.equal(button.attributes["aria-busy"], undefined);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(scoreCenter.state.recalculating, false);

            queueApi("/api/custom_scores/recalc", {
              asyncThrow: new Error("recalc async failure"),
            });
            await scoreCenter.confirmRecalculation();
            assert.equal(status.textContent, "recalc async failure");
            assert.equal(button.disabled, false);
            assert.equal(button.attributes["aria-busy"], undefined);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(scoreCenter.state.recalculating, false);
            assert.equal(hudEvents.length, 0);
            """
        )

    def test_confirm_recalculation_success_writes_refreshes_and_emits_once(self):
        self._run_score_behavior(
            """
            const dialog = document.getElementById("score-preview-dialog");
            const surface = document.getElementById("score-preview-surface");
            const button = document.getElementById("score-preview-confirm");
            dialog.showModal();
            scoreCenter.state.preview =
              previewData("success-token", "成功预览", 25);
            apiCalls.length = 0;
            queueApi("/api/custom_scores/recalc", {
              ok: true,
              updated: 3,
              ruleVersion: 7,
            });

            const success = scoreCenter.confirmRecalculation();
            const duplicate = scoreCenter.confirmRecalculation();
            await Promise.all([success, duplicate]);

            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/recalc"
              ).length,
              1,
            );
            assert.equal(
              apiCalls.filter((call) =>
                call.url.startsWith("/api/custom_scores?")
              ).length,
              1,
            );
            assert.equal(dialog.open, false);
            assert.equal(scoreCenter.state.preview, null);
            assert.equal(scoreCenter.state.recalculating, false);
            assert.equal(button.disabled, false);
            assert.equal(button.attributes["aria-busy"], undefined);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(hudEvents.length, 1);
            assert.equal(hudEvents[0].type, "score:recalculated");
            assert.equal(hudEvents[0].dedupeKey, "score:7:3");
            """
        )

    def test_adjustment_guarded_write_shows_errors_and_restores_busy(self):
        self._run_score_behavior(
            """
            const dialog = document.getElementById("score-adjustment-dialog");
            const surface = document.getElementById("score-adjustment-surface");
            const status = document.getElementById("score-adjustment-status");
            const button = document.getElementById("score-adjustment-save");
            document.getElementById("score-adjustment-player").value = "奖惩玩家";
            document.getElementById("score-adjustment-points").value = "5";
            document.getElementById("score-adjustment-reason").value = "组织奖励";
            dialog.showModal();
            apiCalls.length = 0;

            const failedRequest = deferred();
            queueApi("/api/custom_scores/adjustments", failedRequest);
            const failing = scoreCenter.addAdjustment();
            const duplicate = scoreCenter.addAdjustment();
            assert.equal(button.disabled, true);
            assert.equal(button.attributes["aria-busy"], "true");
            assert.equal(surface.attributes["aria-busy"], "true");
            assert.equal(loaderStates.at(-1).kind, "loading");
            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/adjustments"
              ).length,
              1,
            );
            await duplicate;
            failedRequest.resolve({ok: false, error: "adjustment denied"});
            await failing;
            assert.equal(dialog.open, true);
            assert.equal(status.textContent, "adjustment denied");
            assert.equal(loaderStates.at(-1).kind, "error");
            assert.equal(loaderStates.at(-1).replace, false);
            assert.equal(button.disabled, false);
            assert.equal(button.attributes["aria-busy"], undefined);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(scoreCenter.state.adjustmentSaving, false);

            queueApi("/api/custom_scores/adjustments", {
              syncThrow: new Error("adjustment sync failure"),
            });
            await scoreCenter.addAdjustment();
            assert.equal(status.textContent, "adjustment sync failure");
            assert.equal(button.disabled, false);
            assert.equal(surface.attributes["aria-busy"], undefined);

            queueApi("/api/custom_scores/adjustments", {
              asyncThrow: new Error("adjustment async failure"),
            });
            await scoreCenter.addAdjustment();
            assert.equal(status.textContent, "adjustment async failure");
            assert.equal(button.disabled, false);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.ok(toasts.some((toast) =>
              toast.message === "adjustment async failure"
            ));
            """
        )

    def test_adjustment_success_refreshes_player_and_board_once(self):
        self._run_score_behavior(
            """
            const adjustmentDialog =
              document.getElementById("score-adjustment-dialog");
            const playerDialog =
              document.getElementById("score-player-dialog");
            const surface =
              document.getElementById("score-adjustment-surface");
            const button =
              document.getElementById("score-adjustment-save");
            document.getElementById("score-adjustment-player").value =
              "刷新玩家";
            document.getElementById("score-adjustment-points").value = "5";
            document.getElementById("score-adjustment-reason").value =
              "组织奖励";
            adjustmentDialog.showModal();
            playerDialog.showModal();
            apiCalls.length = 0;
            queueApi("/api/custom_scores/adjustments", {
              ok: true,
              adjustment: {id: 1},
            });

            const saving = scoreCenter.addAdjustment();
            for (let attempt = 0; attempt < 5; attempt += 1) {
              if (playerRequests.has("刷新玩家")) break;
              await Promise.resolve();
            }
            assert.equal(playerRequests.has("刷新玩家"), true);
            playerRequests.get("刷新玩家").resolve(playerData("刷新玩家"));
            await saving;

            assert.equal(
              apiCalls.filter((call) =>
                call.url === "/api/custom_scores/adjustments"
              ).length,
              1,
            );
            assert.equal(
              apiCalls.filter((call) =>
                call.url.startsWith(
                  "/api/custom_scores/player/%E5%88%B7%E6%96%B0%E7%8E%A9%E5%AE%B6"
                )
              ).length,
              1,
            );
            assert.equal(
              apiCalls.filter((call) =>
                call.url.startsWith("/api/custom_scores?")
              ).length,
              1,
            );
            assert.equal(adjustmentDialog.open, false);
            assert.equal(playerDialog.open, true);
            assert.equal(
              scoreCenter.state.selectedPlayer,
              "刷新玩家",
            );
            assert.equal(button.disabled, false);
            assert.equal(button.attributes["aria-busy"], undefined);
            assert.equal(surface.attributes["aria-busy"], undefined);
            assert.equal(scoreCenter.state.adjustmentSaving, false);
            assert.ok(toasts.some((toast) =>
              toast.message === "手动调整已保存"
            ));
            """
        )

    def test_score_board_latest_request_wins_and_refresh_uses_loader_lifecycle(self):
        self._run_score_behavior(
            """
            deferBoards = true;
            scoreCenter.state.board = "battle";
            const older = scoreCenter.load();
            scoreCenter.state.board = "siege";
            const newer = scoreCenter.load();
            assert.equal(loaderStates.at(-1).kind, "refreshing");
            assert.equal(
              document.getElementById("score-board").attributes["aria-busy"],
              "true",
            );

            boardRequests[1].resolve({
              ok: true,
              seasonId: "current",
              board: "siege",
              ruleVersion: 2,
              dataCompleteness: "complete",
              summary: {players: 1},
              rows: [{playerName: "新榜", rank: 1, score: 20}],
            });
            await newer;
            boardRequests[0].resolve({
              ok: true,
              seasonId: "current",
              board: "battle",
              ruleVersion: 1,
              dataCompleteness: "complete",
              summary: {players: 1},
              rows: [{playerName: "旧榜", rank: 1, score: 10}],
            });
            await older;

            assert.equal(scoreCenter.state.data.board, "siege");
            assert.match(
              document.getElementById("score-board-body").innerHTML,
              /新榜/,
            );
            assert.doesNotMatch(
              document.getElementById("score-board-body").innerHTML,
              /旧榜/,
            );
            assert.equal(scoreCenter.state.loading, false);
            assert.equal(
              document.getElementById("score-board").attributes["aria-busy"],
              undefined,
            );
            assert.ok(loaderStates.some((model) => model.kind === "success"));
            """
        )

    def test_score_data_completeness_uses_an_allowlisted_class(self):
        source = (ROOT / "static/score-center.js").read_text(encoding="utf-8")
        self.assertIn("function normalizeDataCompleteness", source)
        self.assertIn(
            "const completeness = normalizeDataCompleteness("
            "row.dataCompleteness);",
            source,
        )
        self.assertIn(
            '${completeness}',
            source,
        )
        self.assertNotIn(
            '${esc(row.dataCompleteness)}',
            source,
        )


if __name__ == "__main__":
    unittest.main()

import unittest
from pathlib import Path
import re
import json
import subprocess
import textwrap


ROOT = Path(__file__).resolve().parents[1]


class OrganizationHudStaticTest(unittest.TestCase):
    def setUp(self):
        self.html = (ROOT / "static/dashboard.html").read_text(
            encoding="utf-8"
        )
        self.app1 = (ROOT / "static/app1.js").read_text(encoding="utf-8")
        self.app2 = (ROOT / "static/app2.js").read_text(encoding="utf-8")
        css_path = ROOT / "static/organization-hud.css"
        self.css = (
            css_path.read_text(encoding="utf-8")
            if css_path.is_file()
            else ""
        )

    def test_organization_pages_use_shared_hud_shell(self):
        page_sections = (
            self.html.split("id='tab7'", 1)[1].split("id='tab8'", 1)[0],
            self.html.split("id='tab17'", 1)[1].split("id='tab18'", 1)[0],
            self.html.split("id='tab24'", 1)[1].split("id='tab23'", 1)[0],
        )
        for page in page_sections:
            self.assertIn("hud-page-head", page)
            self.assertIn("hud-toolbar", page)
            self.assertIn("hud-panel", page)
        self.assertIn("/static/organization-hud.css", self.html)

    def test_renderers_use_organization_semantics(self):
        for class_name in (
            "organization-identity",
            "organization-lineup",
            "organization-group-chip",
            "organization-activity",
            "hud-status-chip",
        ):
            self.assertIn(class_name, self.app2)

    def test_organization_css_uses_global_tokens(self):
        self.assertNotIn(":root", self.css)
        for selector in (
            ".organization-identity",
            ".organization-avatar",
            ".organization-lineup",
            ".organization-lineup-card",
            ".organization-hero-mini",
            ".organization-group-chip",
            ".organization-activity",
            ".organization-status-host",
            '.organization-row[data-selected="true"]',
            '.organization-row[data-state="stale"]',
        ):
            self.assertIn(selector, self.css)
        self.assertIn(".organization-status-host[hidden]", self.css)
        self.assertNotRegex(self.css, r"transition\s*:\s*all\b")
        self.assertIn("var(--surface-panel)", self.css)
        self.assertIn("var(--surface-raised)", self.css)

    def test_organization_rows_have_stable_semantic_state_seams(self):
        snippets = (
            (self.app1, "applyOrganizationRowState"),
            (self.app2, "syncOrganizationRowState"),
        )
        for source, helper_name in snippets:
            self.assertIn("row.dataset.selected = String(selected)", source)
            self.assertIn(
                'row.dataset.state = rowData.isStale ? "stale" : "current"',
                source,
            )
            self.assertIn(f"function {helper_name}", source)
        for renderer_name in (
            "renderPlayerBattleTeams",
            "renderAllianceGroupTeams",
            "loadTeamReport",
        ):
            renderer = self._function_body(self.app2, renderer_name)
            self.assertIn("organization-row", renderer)
            self.assertIn("syncOrganizationRows", renderer)

    def test_expandable_renderers_sync_state_from_row_models(self):
        for renderer_name in (
            "renderPlayerBattleTeams",
            "renderAllianceGroupTeams",
        ):
            renderer = self._function_body(self.app2, renderer_name)
            self.assertIn("const rowStates = []", renderer)
            self.assertIn("rowStates.push({", renderer)
            self.assertIn("isStale:Boolean(item.isStale)", renderer)
            self.assertIn("syncOrganizationRows(b, rowStates)", renderer)
            self.assertNotIn("row.dataset.state==='stale'", renderer)

    def test_empty_and_blocking_errors_use_hud_render_state(self):
        for loader_name in (
            "loadPlayerBattleTeams",
            "loadAllianceGroupTeams",
            "loadTeamReport",
        ):
            loader = self._function_body(self.app2, loader_name)
            self.assertIn("renderOrganizationTableState", loader)
            self.assertRegex(loader, r"kind:\s*['\"]empty['\"]")
            self.assertRegex(loader, r"kind:\s*['\"]error['\"]")
            self.assertRegex(loader, r"replace:\s*true")
        state_helper = self._function_body(
            self.app2,
            "renderOrganizationStateHost",
        )
        self.assertIn("window.HudSystem?.renderState", state_helper)

    def test_filter_refresh_preserves_content_and_uses_local_refresh_line(self):
        for loader_name in (
            "loadPlayerBattleTeams",
            "loadAllianceGroupTeams",
            "loadTeamReport",
        ):
            loader = self._function_body(self.app2, loader_name)
            fetch_pos = loader.find("await apiFetch")
            self.assertGreater(fetch_pos, 0)
            before_fetch = loader[:fetch_pos]
            self.assertNotIn("加载中", before_fetch)
            self.assertNotIn("统计中", before_fetch)
            self.assertNotRegex(before_fetch, r"\b(?:b|tbody)\.innerHTML\s*=")
            self.assertIn("beginOrganizationRequest(", loader)
            self.assertIn("organization-table-panel", loader)

        alliance_loader = self._function_body(
            self.app2,
            "loadAllianceGroupTeams",
        )
        self.assertLess(
            alliance_loader.index("beginOrganizationRequest("),
            alliance_loader.index("await ensureAllianceGroupOptions("),
        )
        request_helper = self._function_body(
            self.app2,
            "beginOrganizationRequest",
        )
        self.assertIn("hud-refresh-line", request_helper)
        self.assertIn("aria-busy", request_helper)

    def test_missing_lineups_render_an_explicit_empty_state(self):
        for renderer_name in (
            "renderPlayerBattleTeams",
            "renderAllianceGroupTeams",
        ):
            renderer = self._function_body(self.app2, renderer_name)
            self.assertIn("organizationLineupCard", renderer)
        lineup_helper = self._function_body(
            self.app2,
            "organizationLineupCard",
        )
        self.assertIn(
            "function organizationLineupCard("
            "content, emptyMessage='暂无阵容')",
            self.app2,
        )
        self.assertIn("organization-lineup-card", lineup_helper)
        self.assertIn("hud-state-empty", lineup_helper)

    def test_team_report_exports_have_busy_width_guard(self):
        for export_name in (
            "exportTeamReportPDF",
            "exportTeamReportLongImage",
        ):
            renderer = self._function_body(self.app2, export_name)
            self.assertIn("withOrganizationExportBusy", renderer)
        busy_helper = self._function_body(
            self.app2,
            "withOrganizationExportBusy",
        )
        self.assertIn('setAttribute("aria-busy", "true")', busy_helper)
        self.assertIn("style.minWidth", busy_helper)
        self.assertIn('removeAttribute("aria-busy")', busy_helper)

    def test_ordinary_organization_filters_emit_no_strong_events(self):
        for function_name in (
            "loadPlayerBattleTeams",
            "renderPlayerBattleTeams",
            "loadAllianceGroupTeams",
            "renderAllianceGroupTeams",
            "loadTeamReport",
        ):
            function_body = self._function_body(
                self.app2,
                function_name,
            )
            self.assertNotIn("HudSystem?.emit", function_body)
            self.assertNotIn("stzb:hud-pulse", function_body)

    def test_organization_table_rows_never_translate(self):
        row_rule = re.search(
            r"\.organization-row\s*\{(?P<body>[^}]*)\}",
            self.css,
            re.DOTALL,
        )
        self.assertIsNotNone(row_rule)
        self.assertIn("transform: none", row_rule.group("body"))

    def test_malicious_identity_never_enters_inline_organization_action(self):
        node_script = r"""
const fs = require("fs");
const vm = require("vm");
const source = fs.readFileSync("static/app2.js", "utf8");
const start = source.indexOf("let _pbtCurrentRows");
const end = source.indexOf("\nasync function loadPlayerBattleTeams", start);
if (start < 0 || end < 0) throw new Error("missing player renderer");
let listener = null;
const body = {
  innerHTML: "",
  querySelectorAll() { return []; },
  addEventListener(type, callback) {
    if (type === "click") listener = callback;
  },
  contains() { return true; },
};
const count = {innerHTML: "", textContent: ""};
const context = {
  console,
  globalThis: {pwned: 0},
  window: {},
  document: {
    getElementById(id) {
      if (id === "pbt-body") return body;
      if (id === "pbt-count") return count;
      return null;
    },
  },
  esc(value) {
    return String(value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  },
  applyOrganizationRowState(row) { return row; },
  buildAllianceHeroMiniCard() { return ""; },
};
vm.runInNewContext(source.slice(start, end), context);
const payload = "x');globalThis.pwned=1;//";
const attrPayload = "name' onmouseover='globalThis.pwned=2";
context.renderPlayerBattleTeams([{
  player_name: payload,
  union: attrPayload,
  clan_name: "",
  _player_team_count: 1,
  _player_total_battles: 3,
  _player_total_wins: 2,
  _player_total_draws: 0,
  _player_win_rate: 66.7,
  _player_main_team_text: attrPayload,
  _player_main_team_count: 3,
  _player_main_team_heroes: [],
  heroes_str: "",
  skills: "",
  hero_stars: [],
  hero_levels: "",
  cnt: 3,
  wins: 2,
  draws: 0,
}]);
const openingTag = body.innerHTML.match(/<tr[^>]*>/)?.[0] || "";
if (!listener) throw new Error("missing delegated click listener");
const trigger = {
  dataset: {organizationAction: "toggle-player", organizationIndex: "0"},
};
listener({
  target: {closest() { return trigger; }},
  preventDefault() {},
  stopPropagation() {},
});
process.stdout.write(JSON.stringify({
  html: body.innerHTML,
  openingTag,
  pwned: context.globalThis.pwned,
}));
"""
        result = self._run_node(node_script)
        payload = json.loads(result.stdout)
        self.assertNotIn("onclick=", payload["html"])
        self.assertNotIn("onmouseover=", payload["openingTag"])
        self.assertRegex(
            payload["openingTag"],
            r"data-organization-index=['\"]0['\"]",
        )
        self.assertEqual(payload["pwned"], 0)
        self.assertRegex(
            payload["html"],
            r"data-selected=['\"]true['\"]",
        )

    def test_organization_attribute_and_options_use_safe_dom_paths(self):
        node_script = r"""
const fs = require("fs");
const vm = require("vm");
const source = fs.readFileSync("static/app2.js", "utf8");
const start = source.indexOf("function escapeOrganizationAttribute");
const end = source.indexOf("\nfunction getTeamHeroNames", start);
if (start < 0 || end < 0) {
  throw new Error("missing organization attribute helpers");
}
const created = [];
const context = {
  esc(value) {
    return String(value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  },
  HERO_CFG: {
    7: {
      name: "hero' onmouseover='globalThis.pwned=1",
      iconId: 7,
      country: "魏",
    },
  },
  document: {
    createElement(tag) {
      const node = {tag, value: "", textContent: ""};
      created.push(node);
      return node;
    },
  },
};
vm.runInNewContext(source.slice(start, end), context);
const select = {
  options: [],
  replaceChildren(...nodes) { this.options = nodes; },
};
const malicious = "group' onfocus='globalThis.pwned=3";
context.replaceOrganizationOptions(select, [malicious], "全部分组");
const card = context.buildAllianceHeroMiniCard(7);
const openingTag = card.match(/<span[^>]*>/)?.[0] || "";
process.stdout.write(JSON.stringify({
  optionValues: select.options.map((option) => option.value),
  optionTexts: select.options.map((option) => option.textContent),
  openingTag,
  card,
}));
"""
        result = self._run_node(node_script)
        payload = json.loads(result.stdout)
        self.assertEqual(
            payload["optionValues"],
            ["", "group' onfocus='globalThis.pwned=3"],
        )
        self.assertEqual(
            payload["optionTexts"],
            ["全部分组", "group' onfocus='globalThis.pwned=3"],
        )
        self.assertNotIn(" onmouseover='", payload["openingTag"])
        self.assertIn("&#39; onmouseover=&#39;", payload["openingTag"])
        self.assertNotIn("onerror=", payload["card"])
        self.assertIn("data-organization-image", payload["card"])

    def test_organization_renderers_have_no_dynamic_inline_handlers(self):
        for renderer_name in (
            "renderPlayerBattleTeams",
            "renderAllianceGroupTeams",
        ):
            renderer = self._function_body(self.app2, renderer_name)
            self.assertNotIn("onclick=", renderer)
            self.assertNotIn("JSON.stringify", renderer)
            self.assertIn("bindOrganizationActions", renderer)
        options = self._function_body(
            self.app2,
            "ensureAllianceGroupOptions",
        )
        self.assertNotIn("innerHTML", options)
        self.assertIn("replaceOrganizationOptions", options)

    def test_request_revision_ignores_stale_out_of_order_completion(self):
        node_script = r"""
const fs = require("fs");
const vm = require("vm");
const source = fs.readFileSync("static/app2.js", "utf8");
const start = source.indexOf("const _organizationRequestRevisions");
const end = source.indexOf("\nfunction organizationIdentityMarkup", start);
if (start < 0 || end < 0) {
  throw new Error("missing organization request helpers");
}
const context = {};
vm.runInNewContext(source.slice(start, end), context);
const classes = new Set();
const attributes = new Map();
const panel = {
  classList: {
    add(value) { classes.add(value); },
    remove(value) { classes.delete(value); },
    contains(value) { return classes.has(value); },
  },
  setAttribute(name, value) { attributes.set(name, value); },
  removeAttribute(name) { attributes.delete(name); },
  getAttribute(name) { return attributes.get(name) ?? null; },
};
const deferred = () => {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return {promise, resolve};
};
const firstDeferred = deferred();
const secondDeferred = deferred();
const rendered = [];
const run = async (request, delayed, label) => {
  await delayed.promise;
  if (context.isOrganizationRequestCurrent(request)) rendered.push(label);
  context.finishOrganizationRequest(request);
};
const first = context.beginOrganizationRequest("players", panel);
const firstRun = run(first, firstDeferred, "old");
const second = context.beginOrganizationRequest("players", panel);
const secondRun = run(second, secondDeferred, "new");
firstDeferred.resolve();
await Promise.resolve();
await Promise.resolve();
const afterOld = {
  busy: panel.getAttribute("aria-busy"),
  refreshing: panel.classList.contains("hud-refresh-line"),
  rendered: rendered.slice(),
};
secondDeferred.resolve();
await Promise.all([firstRun, secondRun]);
const afterNew = {
  busy: panel.getAttribute("aria-busy"),
  refreshing: panel.classList.contains("hud-refresh-line"),
  rendered: rendered.slice(),
};
process.stdout.write(JSON.stringify({afterOld, afterNew}));
"""
        result = self._run_node_async(node_script)
        payload = json.loads(result.stdout)
        self.assertEqual(payload["afterOld"]["busy"], "true")
        self.assertTrue(payload["afterOld"]["refreshing"])
        self.assertEqual(payload["afterOld"]["rendered"], [])
        self.assertIsNone(payload["afterNew"]["busy"])
        self.assertFalse(payload["afterNew"]["refreshing"])
        self.assertEqual(payload["afterNew"]["rendered"], ["new"])

    def test_loaders_use_shared_request_revision_contract(self):
        for loader_name in (
            "loadPlayerBattleTeams",
            "loadAllianceGroupTeams",
            "loadTeamReport",
        ):
            loader = self._function_body(self.app2, loader_name)
            self.assertIn("beginOrganizationRequest(", loader)
            self.assertIn("isOrganizationRequestCurrent(", loader)
            self.assertIn("finishOrganizationRequest(", loader)
            self.assertNotIn(
                "panel?.classList.remove('hud-refresh-line')",
                loader,
            )

    def test_refresh_error_preserves_existing_content_and_uses_status_host(self):
        node_script = r"""
const fs = require("fs");
const vm = require("vm");
const source = fs.readFileSync("static/app2.js", "utf8");
const start = source.indexOf("function renderOrganizationStateHost");
const end = source.indexOf("\nfunction expandAllPlayerBattleTeams", start);
if (start < 0 || end < 0) {
  throw new Error("missing organization state helpers");
}
const renderCalls = [];
const context = {
  window: {
    HudSystem: {
      renderState(target, state) {
        renderCalls.push({
          target: target.id,
          kind: state.kind,
          replace: state.replace,
        });
        return {};
      },
    },
  },
  document: {
    createElement(tag) {
      return {
        tag,
        className: "",
        textContent: "",
        hidden: false,
        appendChild() {},
      };
    },
  },
};
vm.runInNewContext(source.slice(start, end), context);
const statusHost = {
  id: "status",
  hidden: true,
  className: "",
  textContent: "",
  replaceChildren() {},
};
let preservedReplacements = 0;
const populated = {
  childElementCount: 1,
  querySelector(selector) {
    return selector === ".organization-row" ? {id: "existing"} : null;
  },
  replaceChildren() { preservedReplacements += 1; },
};
const nonblocking = context.renderOrganizationLoadError(
  populated,
  statusHost,
  {kind: "error", message: "refresh failed"},
  8,
);
const blockingBody = {
  childElementCount: 0,
  querySelector() { return null; },
  replaceChildren() { this.replaced = true; },
  replaced: false,
};
const blocking = context.renderOrganizationLoadError(
  blockingBody,
  statusHost,
  {kind: "error", message: "initial failed"},
  8,
);
process.stdout.write(JSON.stringify({
  nonblocking,
  blocking,
  preservedReplacements,
  blockingReplaced: blockingBody.replaced,
  statusHidden: statusHost.hidden,
  renderCalls,
}));
"""
        result = self._run_node(node_script)
        payload = json.loads(result.stdout)
        self.assertEqual(payload["nonblocking"], "nonblocking")
        self.assertEqual(payload["preservedReplacements"], 0)
        self.assertEqual(payload["renderCalls"][0]["target"], "status")
        self.assertEqual(payload["blocking"], "blocking")
        self.assertTrue(payload["blockingReplaced"])

    def test_refresh_error_preserves_previous_empty_state(self):
        node_script = r"""
const fs = require("fs");
const vm = require("vm");
const source = fs.readFileSync("static/app2.js", "utf8");
const start = source.indexOf("function renderOrganizationStateHost");
const end = source.indexOf("\nfunction expandAllPlayerBattleTeams", start);
if (start < 0 || end < 0) {
  throw new Error("missing organization state helpers");
}
const context = {
  window: {
    HudSystem: {
      renderState() { return {}; },
    },
  },
  document: {
    createElement() {
      return {appendChild() {}, textContent: "", className: ""};
    },
  },
};
vm.runInNewContext(source.slice(start, end), context);
let replacements = 0;
const previousEmpty = {
  childElementCount: 1,
  querySelector() { return null; },
  replaceChildren() { replacements += 1; },
};
const statusHost = {
  hidden: true,
  replaceChildren() {},
  textContent: "",
  className: "",
};
const mode = context.renderOrganizationLoadError(
  previousEmpty,
  statusHost,
  {kind: "error", message: "refresh failed"},
  8,
);
process.stdout.write(JSON.stringify({
  mode,
  replacements,
  statusHidden: statusHost.hidden,
}));
"""
        result = self._run_node(node_script)
        payload = json.loads(result.stdout)
        self.assertEqual(payload["mode"], "nonblocking")
        self.assertEqual(payload["replacements"], 0)
        self.assertFalse(payload["statusHidden"])

    def test_loaders_route_failures_through_nonblocking_error_helper(self):
        for loader_name in (
            "loadPlayerBattleTeams",
            "loadAllianceGroupTeams",
            "loadTeamReport",
        ):
            loader = self._function_body(self.app2, loader_name)
            self.assertIn("ensureOrganizationStatusHost", loader)
            self.assertIn("renderOrganizationLoadError", loader)
        report_loader = self._function_body(self.app2, "loadTeamReport")
        failure_branch = report_loader.split("if(!res)", 1)[1].split(
            "_trData = res",
            1,
        )[0]
        self.assertNotIn("cards.innerHTML", failure_branch)
        self.assertNotIn("tbody.innerHTML", failure_branch)

    def test_activity_percent_uses_collection_maximum(self):
        node_script = r"""
const fs = require("fs");
const vm = require("vm");
const source = fs.readFileSync("static/app2.js", "utf8");
const start = source.indexOf("function organizationActivityPercent");
const end = source.indexOf("\nfunction syncOrganizationRowState", start);
if (start < 0 || end < 0) throw new Error("missing activity helper");
const context = {
  esc(value) { return String(value || ""); },
};
vm.runInNewContext(source.slice(start, end), context);
const maximum = Math.max(1, ...[20, 100]);
const low = context.organizationActivityMarkup(20, maximum, "战数");
const high = context.organizationActivityMarkup(100, maximum, "战数");
process.stdout.write(JSON.stringify({
  lowPercent: context.organizationActivityPercent(20, maximum),
  highPercent: context.organizationActivityPercent(100, maximum),
  low,
  high,
}));
"""
        result = self._run_node(node_script)
        payload = json.loads(result.stdout)
        self.assertEqual(payload["lowPercent"], 20)
        self.assertEqual(payload["highPercent"], 100)
        self.assertIn("--organization-activity:20%", payload["low"])
        self.assertIn("--organization-activity:100%", payload["high"])
        self.assertNotEqual(payload["low"], payload["high"])
        for renderer_name in (
            "renderPlayerBattleTeams",
            "renderAllianceGroupTeams",
        ):
            renderer = self._function_body(self.app2, renderer_name)
            self.assertIn("activityMaximum", renderer)
            self.assertNotRegex(
                renderer,
                r"organizationActivityMarkup\("
                r"item\.totalBattles\s*,\s*item\.totalBattles",
            )

    def test_export_busy_restores_button_after_throw_and_rejection(self):
        node_script = r"""
const fs = require("fs");
const vm = require("vm");
const source = fs.readFileSync("static/app2.js", "utf8");
const start = source.indexOf("function withOrganizationExportBusy");
const end = source.indexOf("\nfunction exportTeamReportPDF", start);
if (start < 0 || end < 0) throw new Error("missing export busy helper");
const context = {Promise};
vm.runInNewContext(source.slice(start, end), context);
const makeButton = () => {
  const attributes = new Map();
  return {
    style: {minWidth: "41px"},
    disabled: false,
    matches(selector) { return selector === "button"; },
    getBoundingClientRect() { return {width: 88.2}; },
    setAttribute(name, value) { attributes.set(name, value); },
    removeAttribute(name) { attributes.delete(name); },
    getAttribute(name) { return attributes.get(name) ?? null; },
  };
};
const snapshot = (button) => ({
  minWidth: button.style.minWidth,
  busy: button.getAttribute("aria-busy"),
  disabled: button.disabled,
});
const syncButton = makeButton();
let syncMessage = "";
try {
  context.withOrganizationExportBusy(() => {
    throw new Error("sync boom");
  }, syncButton);
} catch (error) {
  syncMessage = error.message;
}
const rejectedButton = makeButton();
let rejectionMessage = "";
try {
  await context.withOrganizationExportBusy(
    () => Promise.reject(new Error("async boom")),
    rejectedButton,
  );
} catch (error) {
  rejectionMessage = error.message;
}
process.stdout.write(JSON.stringify({
  syncMessage,
  syncState: snapshot(syncButton),
  rejectionMessage,
  rejectionState: snapshot(rejectedButton),
}));
"""
        result = self._run_node_async(node_script)
        payload = json.loads(result.stdout)
        self.assertEqual(payload["syncMessage"], "sync boom")
        self.assertEqual(payload["rejectionMessage"], "async boom")
        for key in ("syncState", "rejectionState"):
            self.assertEqual(payload[key]["minWidth"], "41px")
            self.assertIsNone(payload[key]["busy"])
            self.assertFalse(payload[key]["disabled"])

    def test_player_loader_cleans_current_renderer_error_and_ignores_stale_error(self):
        node_script = r"""
const fs = require("fs");
const vm = require("vm");
const source = fs.readFileSync("static/app2.js", "utf8");
const helperStart = source.indexOf("const _organizationRequestRevisions");
const helperEnd = source.indexOf("\nfunction bindOrganizationActions", helperStart);
const loaderStart = source.indexOf("async function loadPlayerBattleTeams");
const loaderEnd = source.indexOf("\n// ===== 分组武勋", loaderStart);
if (helperStart < 0 || helperEnd < 0 || loaderStart < 0 || loaderEnd < 0) {
  throw new Error("missing player loader or request helpers");
}
const classes = new Set();
const attributes = new Map();
const panel = {
  classList: {
    add(value) { classes.add(value); },
    remove(value) { classes.delete(value); },
    contains(value) { return classes.has(value); },
  },
  setAttribute(name, value) { attributes.set(name, value); },
  removeAttribute(name) { attributes.delete(name); },
  getAttribute(name) { return attributes.get(name) ?? null; },
  querySelector() { return null; },
  insertBefore() {},
};
const body = {
  childElementCount: 1,
  closest() { return panel; },
  querySelector() { return {id: "existing"}; },
};
const elements = {
  "pbt-player": {value: ""},
  "pbt-union": {value: ""},
  "pbt-side": {value: ""},
  "pbt-body": body,
  "pbt-count": {textContent: ""},
  "pbt-hint": {textContent: ""},
};
const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return {promise, resolve, reject};
};
const queue = [];
const errors = [];
let rendererError = null;
const validRow = {
  player_name: "玩家甲",
  union: "同盟甲",
  heroes_str: "1+2+3",
  skills: "1,2,3,4,5,6,7,8,9",
  max_troops: 10000,
  cnt: 1,
  wins: 1,
  draws: 0,
  win_rate: 100,
};
const context = {
  console,
  Date,
  Map,
  Set,
  Promise,
  encodeURIComponent,
  document: {
    getElementById(id) { return elements[id] || null; },
  },
  apiFetch() {
    const next = queue.shift();
    if (!next) throw new Error("missing queued API result");
    return next.promise;
  },
  ensureOrganizationStatusHost() {
    return {hidden: true, replaceChildren() {}, textContent: ""};
  },
  clearOrganizationStatusHost() {},
  renderOrganizationLoadError(_tbody, _host, state) {
    errors.push(state.message);
    return "nonblocking";
  },
  renderOrganizationTableState() {},
  dedupeBattleTeamsByHeroNames(rows) { return rows; },
  renderPlayerBattleTeams() {
    if (rendererError) throw rendererError;
  },
};
vm.runInNewContext(
  source.slice(helperStart, helperEnd) + "\n" +
    source.slice(loaderStart, loaderEnd),
  context,
);
const snapshot = () => ({
  busy: panel.getAttribute("aria-busy"),
  refreshing: panel.classList.contains("hud-refresh-line"),
  errors: errors.slice(),
});

const current = deferred();
queue.push(current);
rendererError = new Error("renderer boom");
const currentRun = context.loadPlayerBattleTeams();
current.resolve([validRow]);
await currentRun;
const afterCurrentError = snapshot();

errors.length = 0;
rendererError = null;
const oldRequest = deferred();
const newRequest = deferred();
queue.push(oldRequest, newRequest);
const oldRun = context.loadPlayerBattleTeams();
const newRun = context.loadPlayerBattleTeams();
oldRequest.reject(new Error("old request boom"));
await oldRun;
const afterOldError = snapshot();
newRequest.resolve([]);
await newRun;
const afterNewFinishes = snapshot();

process.stdout.write(JSON.stringify({
  afterCurrentError,
  afterOldError,
  afterNewFinishes,
}));
"""
        result = self._run_node_async(node_script)
        payload = json.loads(result.stdout)
        self.assertIsNone(payload["afterCurrentError"]["busy"])
        self.assertFalse(payload["afterCurrentError"]["refreshing"])
        self.assertEqual(
            payload["afterCurrentError"]["errors"],
            ["renderer boom"],
        )
        self.assertEqual(payload["afterOldError"]["busy"], "true")
        self.assertTrue(payload["afterOldError"]["refreshing"])
        self.assertEqual(payload["afterOldError"]["errors"], [])
        self.assertIsNone(payload["afterNewFinishes"]["busy"])
        self.assertFalse(payload["afterNewFinishes"]["refreshing"])

    def test_organization_loaders_use_try_catch_finally(self):
        for loader_name in (
            "loadPlayerBattleTeams",
            "loadAllianceGroupTeams",
            "loadTeamReport",
        ):
            loader = self._function_body(self.app2, loader_name)
            self.assertRegex(loader, r"\btry\s*\{")
            self.assertRegex(loader, r"\bcatch\s*\(")
            self.assertRegex(loader, r"\bfinally\s*\{")
            finally_body = loader.rsplit("finally", 1)[1]
            self.assertIn("finishOrganizationRequest(request)", finally_body)

    def test_pdf_export_rejects_print_failures_and_restores_busy_button(self):
        node_script = r"""
const fs = require("fs");
const vm = require("vm");
const source = fs.readFileSync("static/app2.js", "utf8");
const start = source.indexOf("function withOrganizationExportBusy");
const end = source.indexOf("\nfunction exportTeamReportLongImage", start);
if (start < 0 || end < 0) throw new Error("missing PDF export helpers");
const makeButton = () => {
  const attributes = new Map();
  return {
    style: {minWidth: "37px"},
    disabled: false,
    matches(selector) { return selector === "button"; },
    getBoundingClientRect() { return {width: 91.2}; },
    setAttribute(name, value) { attributes.set(name, value); },
    removeAttribute(name) { attributes.delete(name); },
    getAttribute(name) { return attributes.get(name) ?? null; },
  };
};
const snapshot = (button) => ({
  minWidth: button.style.minWidth,
  busy: button.getAttribute("aria-busy"),
  disabled: button.disabled,
});
let activeButton = null;
let exportWindow = null;
const context = {
  Promise,
  document: {
    get activeElement() { return activeButton; },
  },
  setTimeout(callback) {
    Promise.resolve().then(callback);
    return 1;
  },
  openTeamReportExportWindow() { return exportWindow; },
};
vm.runInNewContext(source.slice(start, end), context);

const printButton = makeButton();
activeButton = printButton;
exportWindow = {
  closed: false,
  print() { throw new Error("print boom"); },
};
let printMessage = "";
try {
  await context.exportTeamReportPDF();
} catch (error) {
  printMessage = error.message;
}

const closedButton = makeButton();
activeButton = closedButton;
exportWindow = {
  closed: true,
  print() { throw new Error("must not print"); },
};
let closedMessage = "";
try {
  await context.exportTeamReportPDF();
} catch (error) {
  closedMessage = error.message;
}

process.stdout.write(JSON.stringify({
  printMessage,
  printState: snapshot(printButton),
  closedMessage,
  closedState: snapshot(closedButton),
}));
"""
        result = self._run_node_async(node_script)
        payload = json.loads(result.stdout)
        self.assertEqual(payload["printMessage"], "print boom")
        self.assertIn("关闭", payload["closedMessage"])
        for key in ("printState", "closedState"):
            self.assertEqual(payload[key]["minWidth"], "37px")
            self.assertIsNone(payload[key]["busy"])
            self.assertFalse(payload[key]["disabled"])

    def _run_node(self, node_script):
        result = subprocess.run(
            ["node", "-e", textwrap.dedent(node_script)],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(
            result.returncode,
            0,
            msg=f"{result.stdout}\n{result.stderr}",
        )
        return result

    def _run_node_async(self, node_script):
        wrapped = (
            "(async () => {\n"
            + textwrap.dedent(node_script)
            + "\n})().catch((error) => {"
            " console.error(error); process.exitCode = 1; });"
        )
        return self._run_node(wrapped)

    @staticmethod
    def _function_body(source, name):
        marker = re.search(
            rf"(?:async\s+)?function\s+{re.escape(name)}\s*\(",
            source,
        )
        if not marker:
            raise AssertionError(f"missing function {name}")
        open_brace = source.find("{", marker.end())
        depth = 0
        quote = None
        escaped = False
        template_expression_depth = []
        for index in range(open_brace, len(source)):
            char = source[index]
            if quote:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif quote == "`" and char == "$" and source[
                    index + 1 : index + 2
                ] == "{":
                    template_expression_depth.append(depth)
                elif char == quote and not template_expression_depth:
                    quote = None
                elif (
                    quote == "`"
                    and char == "}"
                    and template_expression_depth
                    and depth == template_expression_depth[-1]
                ):
                    template_expression_depth.pop()
                continue
            if char in ("'", '"', "`"):
                quote = char
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    return source[open_brace + 1 : index]
        raise AssertionError(f"unterminated function {name}")


if __name__ == "__main__":
    unittest.main()

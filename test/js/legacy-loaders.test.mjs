import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import vm from "node:vm";

const SOURCE = fs.readFileSync(
  new URL("../../static/app2.js", import.meta.url),
  "utf8",
);

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

class FakeClassList {
  constructor() {
    this.values = new Set();
  }

  add(...values) {
    values.forEach((value) => this.values.add(value));
  }

  remove(...values) {
    values.forEach((value) => this.values.delete(value));
  }

  contains(value) {
    return this.values.has(value);
  }
}

class FakeElement {
  constructor(id = "", tagName = "div") {
    this.id = id;
    this.tagName = tagName.toUpperCase();
    this.classList = new FakeClassList();
    this.attributes = new Map();
    this.children = [];
    this.dataset = {};
    this.disabled = false;
    this.hidden = false;
    this.parentElement = null;
    this.panel = null;
    this.style = {};
    this.value = "";
    this._innerHTML = "";
    this._textContent = "";
  }

  set className(value) {
    this.classList = new FakeClassList();
    String(value || "")
      .split(/\s+/)
      .filter(Boolean)
      .forEach((className) => this.classList.add(className));
  }

  get className() {
    return [...this.classList.values].join(" ");
  }

  set innerHTML(value) {
    this._innerHTML = String(value ?? "");
    this._textContent = "";
    this.children = [];
  }

  get innerHTML() {
    return this._innerHTML;
  }

  set textContent(value) {
    this._textContent = String(value ?? "");
    this._innerHTML = "";
    this.children = [];
  }

  get textContent() {
    return this._textContent;
  }

  get childElementCount() {
    return this.children.length || (this._innerHTML ? 1 : 0);
  }

  get firstChild() {
    return this.children[0] || null;
  }

  append(...children) {
    children.filter(Boolean).forEach((child) => {
      child.parentElement = this;
      this.children.push(child);
    });
  }

  appendChild(child) {
    this.append(child);
    return child;
  }

  insertBefore(child, reference) {
    child.parentElement = this;
    const index = reference ? this.children.indexOf(reference) : -1;
    if (index < 0) this.children.push(child);
    else this.children.splice(index, 0, child);
    return child;
  }

  replaceChildren(...children) {
    this.children = [];
    this._innerHTML = "";
    this._textContent = "";
    this.append(...children);
  }

  setAttribute(name, value) {
    this.attributes.set(name, String(value));
  }

  getAttribute(name) {
    return this.attributes.get(name) ?? null;
  }

  hasAttribute(name) {
    return this.attributes.has(name);
  }

  removeAttribute(name) {
    this.attributes.delete(name);
  }

  closest() {
    return this.panel || this.parentElement;
  }

  querySelector(selector) {
    if (!selector.startsWith(".")) return null;
    const className = selector.slice(1);
    return descendants(this).find(
      (element) => element.classList.contains(className),
    ) || null;
  }
}

function descendants(element) {
  return (element.children || []).flatMap((child) => [
    child,
    ...descendants(child),
  ]);
}

function combinedText(element) {
  return [
    element?.textContent || "",
    element?.innerHTML || "",
    ...(element?.children || []).map(combinedText),
  ].join(" ");
}

function loaderSource() {
  const taskStart = SOURCE.indexOf("let _currentTaskDetail");
  const helperStart = SOURCE.indexOf("const _legacyLoaderRequestOwners");
  const taskEnd = SOURCE.indexOf("\nasync function viewTaskDetail", taskStart);
  const comboStart = SOURCE.indexOf("let hasHeroComboSnapshot");
  const comboEnd = SOURCE.indexOf("\n// ===== 团数据", comboStart);
  const regionStart = SOURCE.indexOf("let _srData = null");
  const regionEnd = SOURCE.indexOf(
    "\nfunction exportTeamReportPretty",
    regionStart,
  );
  for (const [name, value] of Object.entries({
    taskStart,
    taskEnd,
    comboStart,
    comboEnd,
    regionStart,
    regionEnd,
  })) {
    assert.notEqual(value, -1, `missing app2 loader boundary ${name}`);
  }
  return [
    SOURCE.slice(helperStart >= 0 ? helperStart : taskStart, taskEnd),
    SOURCE.slice(comboStart, comboEnd),
    SOURCE.slice(regionStart, regionEnd),
  ].join("\n");
}

function createHarness() {
  const elements = new Map();
  const requests = [];
  const panels = {
    tasks: new FakeElement("task-panel", "section"),
    combo: new FakeElement("tab23", "section"),
    region: new FakeElement("tab26", "section"),
  };
  const register = (id, panel = null, tagName = "div") => {
    const element = new FakeElement(id, tagName);
    element.panel = panel;
    elements.set(id, element);
    return element;
  };

  register("task-body", panels.tasks, "tbody");
  register("task-cards", panels.tasks);
  register("task-count", panels.tasks);
  register("combo-min", panels.combo, "input").value = "3";
  register("combo-cards", panels.combo);
  register("combo-body", panels.combo, "tbody");
  register("combo-top-lineups", panels.combo, "section");
  elements.set("tab23", panels.combo);
  elements.set("tab26", panels.region);
  register("sr-scope", panels.region, "select").value = "all";
  register("sr-group", panels.region, "select").value = "";
  register("sr-cards", panels.region);
  register("sr-note", panels.region);
  register("sr-count", panels.region);
  register("sr-update-time", panels.region);
  register("hud-region-updated", panels.region);
  register("sr-state-body", panels.region, "tbody");
  register("sr-state-bars", panels.region);
  register("sr-group-body", panels.region, "tbody");
  register("sr-group-bars", panels.region);
  register("sr-map-svg", panels.region, "svg");
  register("sr-map-metric", panels.region, "select").value = "player_count";
  register("sr-map-legend-bar", panels.region);
  register("sr-map-legend-high", panels.region);
  register("sr-map-legend-low", panels.region);

  const document = {
    createElement(tagName) {
      return new FakeElement("", tagName);
    },
    getElementById(id) {
      return elements.get(id) || null;
    },
  };
  const window = {
    HudSystem: {
      emit() {},
      renderState(host, state) {
        if (["loading", "refreshing"].includes(state.kind)) {
          host.setAttribute("aria-busy", "true");
        } else {
          host.removeAttribute("aria-busy");
        }
        host.className = `hud-state hud-state-${state.kind}`;
        host.textContent = state.message || "";
        return host;
      },
    },
  };
  const context = {
    AbortController,
    Date,
    Error,
    Map,
    Math,
    Number,
    Promise,
    Set,
    String,
    console,
    document,
    encodeURIComponent,
    esc(value) {
      return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
    },
    fmt(value) {
      return String(value ?? 0);
    },
    apiFetch(url, options = {}) {
      const next = deferred();
      requests.push({ url, options, ...next });
      return next.promise;
    },
    window,
  };
  vm.runInNewContext(loaderSource(), context);
  return { context, elements, panels, requests };
}

function task(id, name) {
  return {
    id,
    name,
    status: 0,
    time: Math.floor(Date.now() / 1000) + 3600,
    pos: "10004",
    pos_xy: "1,4",
    wid_name: "城池",
    target_groups: ["一团"],
    target_user_num: 20,
    complete_user_num: 0,
  };
}

function combo(name, total = 10) {
  return {
    combo: name,
    total,
    win: 6,
    lose: 3,
    draw: 1,
    win_rate: 65,
  };
}

function region(state, groups = ["一团"]) {
  return {
    meta: {},
    groups,
    summary: {
      total_players: 1,
      total_power: 100,
      state_count: 1,
      alliance_count: 1,
      group_count: 1,
      grouped_players: 1,
    },
    state_rows: [{
      state,
      player_count: 1,
      total_power: 100,
      avg_power: 100,
      max_power: 100,
    }],
    group_rows: [{
      alliance_name: "甲盟",
      group_name: groups[0] || "未分组",
      player_count: 1,
      total_power: 100,
      state_summary: `${state} 1`,
    }],
    alliance_rows: [],
  };
}

function assertBusy(panel, expected, label) {
  assert.equal(
    panel.getAttribute("aria-busy"),
    expected ? "true" : null,
    `${label} aria-busy`,
  );
  assert.equal(
    panel.classList.contains("hud-refresh-line"),
    expected,
    `${label} refresh line`,
  );
  if (!expected && panel._legacyLoaderStatusHost) {
    assert.equal(
      panel._legacyLoaderStatusHost.getAttribute("aria-busy"),
      null,
      `${label} status host aria-busy`,
    );
  }
}

test("loadTasks owns deferred requests and preserves the latest task truth", async () => {
  const harness = createHarness();
  const { context, elements, panels, requests } = harness;
  const body = elements.get("task-body");

  const firstRun = context.loadTasks();
  assertBusy(panels.tasks, true, "first tasks load");
  assert.match(combinedText(panels.tasks), /加载|正在/);
  requests[0].resolve([task(1, "基线任务")]);
  await firstRun;
  assert.match(body.innerHTML, /基线任务/);
  assertBusy(panels.tasks, false, "completed tasks load");

  const oldRun = context.loadTasks();
  const newRun = context.loadTasks();
  assert.match(body.innerHTML, /基线任务/, "refresh erased task snapshot");
  requests[1].resolve([task(2, "旧任务")]);
  await oldRun;
  assert.match(body.innerHTML, /基线任务/, "stale task response committed");
  assertBusy(panels.tasks, true, "newer tasks request");
  requests[2].resolve([task(3, "最新任务")]);
  await newRun;
  assert.match(body.innerHTML, /最新任务/);
  assert.doesNotMatch(body.innerHTML, /旧任务/);
  assertBusy(panels.tasks, false, "latest tasks request");

  const errorRun = context.loadTasks();
  requests[3].resolve(null);
  await errorRun;
  assert.match(body.innerHTML, /最新任务/, "task error erased snapshot");
  assert.match(combinedText(panels.tasks), /失败|不可用/);
  assertBusy(panels.tasks, false, "task error");

  const emptyRun = context.loadTasks();
  requests[4].resolve([]);
  await emptyRun;
  assert.match(body.innerHTML, /暂无任务/);
  assert.doesNotMatch(body.innerHTML, /加载失败/);
});

test("loadHeroCombo snapshots context and keeps refresh errors nonblocking", async () => {
  const harness = createHarness();
  const { context, elements, panels, requests } = harness;
  const body = elements.get("combo-body");
  const minimum = elements.get("combo-min");

  const firstRun = context.loadHeroCombo();
  assertBusy(panels.combo, true, "first combo load");
  assert.match(combinedText(panels.combo), /计算|加载|正在/);
  requests[0].resolve([combo("基线+阵容+甲")]);
  await firstRun;
  assert.match(body.innerHTML, /基线/);
  assertBusy(panels.combo, false, "completed combo load");

  minimum.value = "4";
  const oldRun = context.loadHeroCombo();
  minimum.value = "8";
  const newRun = context.loadHeroCombo();
  assert.match(body.innerHTML, /基线/, "combo refresh erased snapshot");
  assert.match(requests[1].url, /min=4/);
  assert.match(requests[2].url, /min=8/);
  requests[1].resolve([combo("旧+阵容+乙")]);
  await oldRun;
  assert.match(body.innerHTML, /基线/, "stale combo response committed");
  assertBusy(panels.combo, true, "newer combo request");
  requests[2].resolve([combo("最新+阵容+丙")]);
  await newRun;
  assert.match(body.innerHTML, /最新/);
  assert.doesNotMatch(body.innerHTML, /旧/);
  assertBusy(panels.combo, false, "latest combo request");

  const errorRun = context.loadHeroCombo();
  requests[3].resolve({ error: "临时不可用" });
  await errorRun;
  assert.match(body.innerHTML, /最新/, "combo error erased snapshot");
  assert.match(combinedText(panels.combo), /临时不可用|失败/);
  assertBusy(panels.combo, false, "combo error");
});

test("loadStateRegionStats aborts stale contexts and preserves regional truth", async () => {
  const harness = createHarness();
  const { context, elements, panels, requests } = harness;
  const stateBody = elements.get("sr-state-body");
  const scope = elements.get("sr-scope");
  const group = elements.get("sr-group");

  const firstRun = context.loadStateRegionStats();
  assertBusy(panels.region, true, "first region load");
  assert.match(combinedText(panels.region), /加载|正在/);
  requests[0].resolve(region("益州"));
  await firstRun;
  assert.match(stateBody.innerHTML, /益州/);
  assertBusy(panels.region, false, "completed region load");

  scope.value = "group";
  group.value = "一团";
  const oldRun = context.loadStateRegionStats();
  group.value = "二团";
  const newRun = context.loadStateRegionStats();
  assert.equal(requests[1].options.signal.aborted, true);
  assert.match(requests[1].url, /group=%E4%B8%80%E5%9B%A2/);
  assert.match(requests[2].url, /group=%E4%BA%8C%E5%9B%A2/);
  assert.match(stateBody.innerHTML, /益州/, "region refresh erased snapshot");
  requests[1].resolve(region("旧州", ["一团"]));
  await oldRun;
  assert.match(stateBody.innerHTML, /益州/, "stale region response committed");
  assertBusy(panels.region, true, "newer region request");
  requests[2].resolve(region("扬州", ["一团", "二团"]));
  await newRun;
  assert.match(stateBody.innerHTML, /扬州/);
  assert.doesNotMatch(stateBody.innerHTML, /旧州/);
  assertBusy(panels.region, false, "latest region request");

  const errorRun = context.loadStateRegionStats();
  requests[3].resolve({ error: "区域接口不可用" });
  await errorRun;
  assert.match(stateBody.innerHTML, /扬州/, "region error erased snapshot");
  assert.match(combinedText(panels.region), /区域接口不可用|失败/);
  assertBusy(panels.region, false, "region error");
});

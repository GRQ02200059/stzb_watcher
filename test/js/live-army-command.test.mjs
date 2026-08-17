import assert from "node:assert/strict";
import test from "node:test";

import {
  chooseDefaultArmy,
  createLiveArmyCommand,
  filterArmies,
  filterArmiesByTime,
  formatArmyAge,
  formatArmyCountdown,
  sortCurrentArmies,
} from "../../static/live-army-command.mjs";

function fixtureArmy(overrides = {}) {
  const base = {
    armyId: 18411352,
    state: 1,
    stateKey: "expedition",
    stateLabel: "出征中",
    stateCategory: "moving",
    isMoving: true,
    owner: {
      userId: 42,
      name: "无情的战",
      unionId: 1005,
      unionName: "甲盟",
    },
    location: {
      currentWid: 2081480,
      nextWid: 2081481,
      targetWid: 1151300,
      targetName: "前线要塞",
    },
    timing: {
      beginTime: 100,
      nextTime: 180,
      endTime: 300,
    },
    lineup: {
      status: "exact",
      complete: true,
      heroes: [
        { heroId: 100705, name: "杜预" },
        { heroId: 100707, name: "卫瓘" },
        { heroId: 100101, name: "灵帝" },
      ],
    },
    source: {
      observedAtMs: 190_000,
      ageMs: 10_000,
      freshness: "fresh",
      isStale: false,
      cmdId: 5028,
    },
    offline: null,
  };
  return {
    ...base,
    ...overrides,
    owner: { ...base.owner, ...(overrides.owner || {}) },
    location: { ...base.location, ...(overrides.location || {}) },
    timing: { ...base.timing, ...(overrides.timing || {}) },
    lineup: { ...base.lineup, ...(overrides.lineup || {}) },
    source: { ...base.source, ...(overrides.source || {}) },
  };
}

test("search matches army player union hero and WID", () => {
  const army = fixtureArmy();
  for (const query of [
    "18411352",
    "无情",
    "甲盟",
    "杜预",
    "2081480",
    "1151300",
    "前线要塞",
  ]) {
    assert.equal(
      filterArmies([army], query, "all").length,
      1,
      `query ${query} did not match`,
    );
  }
});

test("search is normalized and does not infer unknown lineups", () => {
  const exact = fixtureArmy();
  const unknown = fixtureArmy({
    armyId: 814501,
    lineup: {
      status: "unknown",
      complete: false,
      heroes: [],
      message: "无同 ID 战报，阵容未知",
    },
  });
  assert.deepEqual(
    filterArmies([exact, unknown], "  du  yu ", "all").map(
      (row) => row.armyId,
    ),
    [],
  );
  assert.deepEqual(
    filterArmies([exact, unknown], " 阵容未知 ", "all").map(
      (row) => row.armyId,
    ),
    [814501],
  );
});

test("state filter keeps only the selected current category", () => {
  const rows = [
    fixtureArmy({ armyId: 1, stateKey: "expedition" }),
    fixtureArmy({
      armyId: 2,
      stateKey: "reside",
      stateLabel: "驻守",
      stateCategory: "stationary",
      isMoving: false,
    }),
    fixtureArmy({
      armyId: 3,
      state: 99,
      stateKey: "unknown",
      stateLabel: "状态 99",
      stateCategory: "unknown",
      isMoving: false,
    }),
  ];
  assert.deepEqual(
    filterArmies(rows, "", "reside").map((row) => row.armyId),
    [2],
  );
  assert.deepEqual(
    filterArmies(rows, "", "unknown").map((row) => row.armyId),
    [3],
  );
});

test("time filter uses current observation and offline deletion times", () => {
  const nowMs = 2_000_000;
  const recent = fixtureArmy({
    armyId: 1,
    source: {
      observedAtMs: nowMs - 90_000,
      freshness: "fresh",
      isStale: false,
    },
  });
  const aging = fixtureArmy({
    armyId: 2,
    source: {
      observedAtMs: nowMs - 8 * 60_000,
      freshness: "aging",
      isStale: false,
    },
  });
  const stale = fixtureArmy({
    armyId: 3,
    source: {
      observedAtMs: nowMs - 11 * 60_000,
      freshness: "stale",
      isStale: true,
    },
  });
  const offline = fixtureArmy({
    armyId: 4,
    source: {
      observedAtMs: nowMs - 60 * 60_000,
      freshness: "stale",
      isStale: true,
    },
    offline: {
      deletedAtMs: nowMs - 4 * 60_000,
      ageMs: 4 * 60_000,
    },
  });
  const missing = fixtureArmy({
    armyId: 5,
    source: {
      observedAtMs: 0,
      freshness: "unknown",
      isStale: false,
    },
  });
  const rows = [recent, aging, stale, offline, missing];

  assert.deepEqual(
    filterArmiesByTime(rows, "2", nowMs).map((row) => row.armyId),
    [1],
  );
  assert.deepEqual(
    filterArmiesByTime(rows, "10", nowMs).map((row) => row.armyId),
    [1, 2, 4],
  );
  assert.deepEqual(
    filterArmiesByTime(rows, "30", nowMs).map((row) => row.armyId),
    [1, 2, 3, 4],
  );
  assert.deepEqual(
    filterArmiesByTime(rows, "all", nowMs).map((row) => row.armyId),
    [1, 2, 3, 4, 5],
  );
});

test("age formatting is explicit and never hides stale duration", () => {
  assert.equal(formatArmyAge(0), "时间未知");
  assert.equal(formatArmyAge(30_000), "30秒前");
  assert.equal(formatArmyAge(90_000), "1分30秒前");
  assert.equal(formatArmyAge(3_900_000), "1小时5分前");
});

test("moving armies sort by future arrival before stationary armies", () => {
  const rows = sortCurrentArmies(
    [
      fixtureArmy({
        armyId: 1,
        isMoving: false,
        timing: { endTime: 0 },
      }),
      fixtureArmy({
        armyId: 2,
        isMoving: true,
        timing: { endTime: 300 },
      }),
      fixtureArmy({
        armyId: 3,
        isMoving: true,
        timing: { endTime: 200 },
      }),
      fixtureArmy({
        armyId: 4,
        isMoving: true,
        timing: { endTime: 90 },
      }),
    ],
    100,
  );
  assert.deepEqual(
    rows.map((row) => row.armyId),
    [3, 2, 4, 1],
  );
});

test("stationary armies sort by state then army id", () => {
  const rows = sortCurrentArmies(
    [
      fixtureArmy({
        armyId: 1,
        state: 6,
        stateKey: "reinforce",
        isMoving: false,
        timing: { endTime: 0 },
      }),
      fixtureArmy({
        armyId: 8,
        state: 5,
        stateKey: "reside",
        isMoving: false,
        timing: { endTime: 0 },
      }),
      fixtureArmy({
        armyId: 7,
        state: 5,
        stateKey: "reside",
        isMoving: false,
        timing: { endTime: 0 },
      }),
    ],
    100,
  );
  assert.deepEqual(
    rows.map((row) => row.armyId),
    [7, 8, 1],
  );
});

test("default selection keeps earliest future arrival", () => {
  const selected = chooseDefaultArmy(
    {
      current: [
        fixtureArmy({
          armyId: 1,
          isMoving: true,
          timing: { endTime: 400 },
        }),
        fixtureArmy({
          armyId: 2,
          isMoving: true,
          timing: { endTime: 300 },
        }),
      ],
      recentOffline: [],
    },
    100,
  );
  assert.equal(selected, 2);
});

test("default selection falls back to current then recent offline", () => {
  assert.equal(
    chooseDefaultArmy(
      {
        current: [
          fixtureArmy({
            armyId: 7,
            isMoving: false,
            timing: { endTime: 0 },
          }),
        ],
        recentOffline: [fixtureArmy({ armyId: 8 })],
      },
      100,
    ),
    7,
  );
  assert.equal(
    chooseDefaultArmy(
      {
        current: [],
        recentOffline: [fixtureArmy({ armyId: 8 })],
      },
      100,
    ),
    8,
  );
  assert.equal(
    chooseDefaultArmy({ current: [], recentOffline: [] }, 100),
    0,
  );
});

test("countdown clamps completed values and formats hours", () => {
  assert.equal(formatArmyCountdown(161, 100), "01:01");
  assert.equal(formatArmyCountdown(3_761, 100), "1:01:01");
  assert.equal(formatArmyCountdown(99, 100), "已到达");
  assert.equal(formatArmyCountdown(0, 100), "--:--");
});

class FakeTarget {
  constructor() {
    this.listeners = new Map();
  }

  addEventListener(type, callback) {
    const rows = this.listeners.get(type) || [];
    rows.push(callback);
    this.listeners.set(type, rows);
  }

  removeEventListener(type, callback) {
    const rows = this.listeners.get(type) || [];
    this.listeners.set(
      type,
      rows.filter((row) => row !== callback),
    );
  }

  dispatch(type, detail = {}) {
    const event = {
      type,
      detail,
      target: this,
      currentTarget: this,
      clientX: detail.clientX || 0,
      clientY: detail.clientY || 0,
      key: detail.key || "",
      preventDefault() {},
    };
    for (const callback of this.listeners.get(type) || []) callback(event);
    return event;
  }
}

class FakeElement extends FakeTarget {
  constructor(tagName = "div", id = "") {
    super();
    this.tagName = tagName.toUpperCase();
    this.id = id;
    this.children = [];
    this.dataset = {};
    this.attributes = new Map();
    this._classes = new Set();
    this._textContent = "";
    this.value = "";
    this.disabled = false;
    this.replaceCount = 0;
    this.clientWidth = this.tagName === "CANVAS" ? 760 : 320;
    this.clientHeight = this.tagName === "CANVAS" ? 560 : 240;
    this.style = {
      values: new Map(),
      setProperty: (name, value) => this.style.values.set(name, value),
    };
    this.classList = {
      add: (...values) => values.forEach((value) => this._classes.add(value)),
      remove: (...values) => values.forEach((value) => this._classes.delete(value)),
      toggle: (value, enabled) => {
        const active = enabled ?? !this._classes.has(value);
        if (active) this._classes.add(value);
        else this._classes.delete(value);
        return active;
      },
      contains: (value) => this._classes.has(value),
    };
  }

  set className(value) {
    this._classes = new Set(String(value || "").split(/\s+/).filter(Boolean));
  }

  get className() {
    return [...this._classes].join(" ");
  }

  set textContent(value) {
    this._textContent = String(value ?? "");
    this.children = [];
  }

  get textContent() {
    return this._textContent;
  }

  append(...children) {
    this.children.push(...children.filter(Boolean));
  }

  appendChild(child) {
    this.append(child);
    return child;
  }

  replaceChildren(...children) {
    this.children = children.filter(Boolean);
    this._textContent = "";
    this.replaceCount += 1;
  }

  setAttribute(name, value) {
    this.attributes.set(name, String(value));
    if (name === "class") this.className = value;
    if (name.startsWith("data-")) {
      const key = name
        .slice(5)
        .replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
      this.dataset[key] = String(value);
    }
  }

  getAttribute(name) {
    if (name === "class") return this.className;
    return this.attributes.get(name) || null;
  }

  hasAttribute(name) {
    return this.attributes.has(name);
  }

  removeAttribute(name) {
    this.attributes.delete(name);
  }

  querySelectorAll(selector) {
    return descendants(this).filter((element) => matches(element, selector));
  }

  querySelector(selector) {
    return this.querySelectorAll(selector)[0] || null;
  }

  getBoundingClientRect() {
    return {
      left: 0,
      top: 0,
      width: this.clientWidth,
      height: this.clientHeight,
    };
  }

  scrollIntoView() {
    this.scrolledIntoView = true;
  }

  focus() {
    this.focused = true;
  }
}

class FakeDocument extends FakeTarget {
  constructor() {
    super();
    this.visibilityState = "visible";
    this.elements = new Map();
    this.body = new FakeElement("body", "body");
    this.navButtons = [];
    for (const id of [
      "tab35",
      "live-army-index",
      "live-army-summary",
      "live-army-freshness",
      "live-army-observed-at",
      "live-army-index-count",
      "live-army-search",
      "live-army-status-filter",
      "live-army-time-filter",
      "live-army-index-toggle",
      "live-army-current-count",
      "live-army-current-list",
      "live-army-offline-count",
      "live-army-offline-list",
      "live-army-map-status",
      "live-army-map-home",
      "live-army-map-selected",
      "live-army-open-intelligence",
      "live-army-detail",
    ]) {
      this.register(new FakeElement("div", id));
    }
    this.register(new FakeElement("canvas", "live-army-map-canvas"));
    this.getElementById("tab35").classList.add("active");
    this.getElementById("live-army-index").classList.add("live-army-index");
    const intelligenceButton = new FakeElement("button");
    intelligenceButton.setAttribute("onclick", "switchTab(33,this)");
    this.navButtons.push(intelligenceButton);
  }

  register(element) {
    this.elements.set(element.id, element);
    return element;
  }

  createElement(tagName) {
    return new FakeElement(tagName);
  }

  getElementById(id) {
    return this.elements.get(id) || null;
  }

  querySelectorAll(selector) {
    if (selector === "nav button") return this.navButtons;
    const roots = [...this.elements.values()];
    return roots.flatMap((root) => [
      ...(matches(root, selector) ? [root] : []),
      ...root.querySelectorAll(selector),
    ]);
  }

  querySelector(selector) {
    return this.querySelectorAll(selector)[0] || null;
  }
}

class FakeWindow extends FakeTarget {
  constructor() {
    super();
    this.devicePixelRatio = 1;
    this.switchCalls = [];
    this.locatedWids = [];
    this.openedViews = [];
    this.switchTab = (tabId) => this.switchCalls.push(tabId);
    this.IntelligenceCenter = {
      openView: async (view) => this.openedViews.push(view),
      locateWid: async (wid) => this.locatedWids.push(wid),
    };
    this.matchMedia = () => ({ matches: false });
  }
}

function descendants(element) {
  const result = [];
  for (const child of element.children || []) {
    if (!(child instanceof FakeElement)) continue;
    result.push(child, ...descendants(child));
  }
  return result;
}

function matches(element, selector) {
  if (selector.startsWith("#")) return element.id === selector.slice(1);
  if (selector.startsWith(".")) {
    const [className, dataPart] = selector.slice(1).split("[");
    if (!element.classList.contains(className)) return false;
    if (!dataPart) return true;
    return matchesData(element, `[${dataPart}`);
  }
  if (selector.startsWith("[")) return matchesData(element, selector);
  return element.tagName.toLowerCase() === selector.toLowerCase();
}

function matchesData(element, selector) {
  const match = selector.match(
    /^\[data-([a-z0-9-]+)(?:=['"]?([^'"\]]+)['"]?)?\]$/,
  );
  if (!match) return false;
  const key = match[1].replace(
    /-([a-z])/g,
    (_, letter) => letter.toUpperCase(),
  );
  if (!(key in element.dataset)) return false;
  return match[2] === undefined || element.dataset[key] === match[2];
}

function textTree(element) {
  return [
    element?.textContent || "",
    ...(element?.children || []).map(textTree),
  ].join(" ");
}

function response(body, ok = true) {
  return {
    ok,
    status: ok ? 200 : 500,
    async json() {
      return body;
    },
  };
}

function apiArmy(overrides = {}) {
  const base = {
    armyId: 18411352,
    userId: 42,
    ownerName: "无情的战",
    ownerUnionId: 1005,
    ownerUnionName: "甲盟",
    state: 1,
    stateKey: "expedition",
    stateLabel: "出征中",
    stateCategory: "moving",
    isMoving: true,
    location: {
      currentWid: 2081480,
      nextWid: 2081481,
      targetWid: 1151300,
      fromWid: 2081479,
      source: "real-march",
    },
    timing: {
      beginTime: 100,
      nextTime: 180,
      endTime: 300,
    },
    march: {
      realMarchId: 99001,
      pathId: 77,
      currentWid: 2081480,
      nextWid: 2081481,
      endTime: 300,
    },
    morale: 97,
    target: { name: "前线要塞", force: 123, unionId: 0 },
    source: {
      seq: 101,
      observedAtMs: 190_000,
      ageMs: 10_000,
      freshness: "fresh",
      isStale: false,
      cmdId: 5028,
    },
    lineup: {
      status: "exact",
      complete: true,
      battleId: 5289170,
      battleTime: 1_786_711_800,
      battleTimeText: "2026-08-14 20:10:00",
      side: "atk",
      message: "精确阵容",
      heroes: [
        {
          id: 100705,
          name: "杜预",
          level: 50,
          advance: 5,
          portraitUrl: "/static/hero-portraits/cards/100705.webp",
          portraitFallbackUrl: "/static/hero-portraits/placeholder.svg",
          skills: [{ id: 200001, name: "文伐", level: 10 }],
        },
        {
          id: 100707,
          name: "卫瓘",
          level: 50,
          advance: 5,
          portraitUrl: "/static/hero-portraits/cards/100707.webp",
          portraitFallbackUrl: "/static/hero-portraits/placeholder.svg",
          skills: [],
        },
        {
          id: 100101,
          name: "灵帝",
          level: 50,
          advance: 5,
          portraitUrl: "/static/hero-portraits/cards/100101.webp",
          portraitFallbackUrl: "/static/hero-portraits/placeholder.svg",
          skills: [],
        },
      ],
    },
    offline: null,
  };
  return {
    ...base,
    ...overrides,
    location: { ...base.location, ...(overrides.location || {}) },
    timing: { ...base.timing, ...(overrides.timing || {}) },
    target: { ...base.target, ...(overrides.target || {}) },
    source: { ...base.source, ...(overrides.source || {}) },
    lineup: { ...base.lineup, ...(overrides.lineup || {}) },
  };
}

function snapshot({
  current = [apiArmy()],
  recentOffline = [],
  generatedAtMs = 200_000,
} = {}) {
  return {
    ok: true,
    generatedAtMs,
    worldStateObservedAtMs: generatedAtMs - 15_000,
    worldStateAgeMs: 15_000,
    worldStateVersion: 7,
    freshness: "fresh",
    completeness: "full-baseline",
    summary: {
      current: current.length,
      usableCurrent: current.filter(
        (row) => row.source?.freshness !== "stale",
      ).length,
      staleCurrent: current.filter(
        (row) => row.source?.freshness === "stale",
      ).length,
      moving: current.filter((row) => row.isMoving).length,
      stationary: current.filter((row) => !row.isMoving).length,
      exactLineups: current.filter(
        (row) => row.lineup.status === "exact",
      ).length,
      unknownLineups: current.filter(
        (row) => row.lineup.status !== "exact",
      ).length,
      recentOffline: recentOffline.length,
    },
    bounds: { rowUp: 100, rowDown: 220, colLeft: 1200, colRight: 1500 },
    current,
    recentOffline,
  };
}

function fakeTimers() {
  let nextId = 1;
  const timeouts = new Map();
  const intervals = new Map();
  return {
    timeouts,
    intervals,
    setTimeoutFn(callback, delay) {
      const id = nextId++;
      timeouts.set(id, { callback, delay });
      return id;
    },
    clearTimeoutFn(id) {
      timeouts.delete(id);
    },
    setIntervalFn(callback, delay) {
      const id = nextId++;
      intervals.set(id, { callback, delay });
      return id;
    },
    clearIntervalFn(id) {
      intervals.delete(id);
    },
    async runTimeout(delay = null) {
      const row = [...timeouts.entries()].find(
        ([, value]) => delay === null || value.delay === delay,
      );
      assert.ok(row, `missing timeout ${delay}`);
      timeouts.delete(row[0]);
      await row[1].callback();
    },
  };
}

function fakeMapModule() {
  return {
    drawCalls: [],
    hitArmyId: 0,
    boundsForArmies() {
      return { rowUp: 200, rowDown: 216, colLeft: 1470, colRight: 1486 };
    },
    buildArmyDrawPlan(armies, selectedArmyId, bounds) {
      return {
        armies,
        selectedArmyId,
        bounds,
        markers: armies.map((army, index) => ({
          armyId: army.armyId,
          x: 20 + index * 30,
          y: 20,
        })),
        routes: [],
        mapRect: { x: 0, y: 0, width: 760, height: 560 },
      };
    },
    drawLiveArmyMap(canvas, plan) {
      this.drawCalls.push({ canvas, plan });
      return plan;
    },
    hitTestArmy() {
      return this.hitArmyId;
    },
  };
}

function controllerHarness(
  bodies,
  {
    emitHudEvent = () => {},
    resolveHudEvent = () => false,
  } = {},
) {
  const documentRef = new FakeDocument();
  const windowRef = new FakeWindow();
  const timers = fakeTimers();
  const mapModule = fakeMapModule();
  const requested = [];
  let index = 0;
  const controller = createLiveArmyCommand({
    documentRef,
    windowRef,
    fetchFn: async (url, options) => {
      requested.push([url, options]);
      const body = bodies[Math.min(index, bodies.length - 1)];
      index += 1;
      return response(body);
    },
    setTimeoutFn: timers.setTimeoutFn,
    clearTimeoutFn: timers.clearTimeoutFn,
    setIntervalFn: timers.setIntervalFn,
    clearIntervalFn: timers.clearIntervalFn,
    nowFn: () => 100_000,
    mapModule,
    emitHudEvent,
    resolveHudEvent,
  });
  return {
    controller,
    documentRef,
    windowRef,
    timers,
    mapModule,
    requested,
  };
}

test("load requests ten minute window and renders linked columns once", async () => {
  const movingLater = apiArmy({
    armyId: 2,
    timing: { endTime: 400 },
    lineup: {
      status: "unknown",
      complete: false,
      heroes: [],
      message: "无同 ID 战报，阵容未知",
    },
  });
  const movingSooner = apiArmy({
    armyId: 1,
    timing: { endTime: 300 },
  });
  const harness = controllerHarness([
    snapshot({ current: [movingLater, movingSooner] }),
  ]);

  await harness.controller.load();

  assert.equal(
    harness.requested[0][0],
    "/api/intelligence/live-armies?offlineMinutes=10",
  );
  assert.equal(harness.requested[0][1].cache, "no-store");
  assert.equal(harness.controller.state.selectedArmyId, 1);
  assert.equal(
    harness.documentRef.getElementById("live-army-current-list").replaceCount,
    1,
  );
  assert.equal(
    harness.documentRef.getElementById("live-army-offline-list").replaceCount,
    1,
  );
  assert.equal(
    harness.documentRef.getElementById("live-army-detail").replaceCount,
    1,
  );
  assert.equal(harness.mapModule.drawCalls.length, 1);
  assert.match(
    textTree(harness.documentRef.getElementById("live-army-detail")),
    /杜预.*卫瓘.*灵帝/,
  );
});

test("default ten minute filter hides stale current armies", async () => {
  const recent = apiArmy({
    armyId: 1,
    source: {
      observedAtMs: 790_000,
      ageMs: 10_000,
      freshness: "fresh",
      isStale: false,
    },
  });
  const stale = apiArmy({
    armyId: 814501,
    isMoving: false,
    source: {
      observedAtMs: 100_000,
      ageMs: 100_000,
      freshness: "stale",
      isStale: true,
    },
  });
  const harness = controllerHarness([
    snapshot({
      generatedAtMs: 800_000,
      current: [recent, stale],
    }),
  ]);

  await harness.controller.load();

  assert.equal(harness.controller.state.timeFilter, "10");
  assert.equal(
    harness.documentRef
      .getElementById("live-army-current-list")
      .querySelectorAll(".live-army-card").length,
    1,
  );
  assert.equal(harness.controller.state.selectedArmyId, 1);
  assert.match(
    harness.documentRef.getElementById("live-army-observed-at").textContent,
    /数据时间/,
  );
});

test("all time filter reveals stale army with explicit time warning", async () => {
  const stale = apiArmy({
    armyId: 814501,
    isMoving: false,
    state: 5,
    stateKey: "reside",
    stateLabel: "驻守",
    stateCategory: "stationary",
    timing: { endTime: 0 },
    source: {
      observedAtMs: 100_000,
      ageMs: 700_000,
      freshness: "stale",
      isStale: true,
      cmdId: 5026,
    },
  });
  const harness = controllerHarness([
    snapshot({
      generatedAtMs: 800_000,
      current: [stale],
    }),
  ]);
  await harness.controller.load();

  harness.controller.setFilter({
    query: "",
    stateFilter: "all",
    timeFilter: "all",
  });
  harness.controller.selectArmy(814501);

  assert.equal(
    harness.documentRef
      .getElementById("live-army-current-list")
      .querySelector(".live-army-card")
      .classList.contains("is-stale"),
    true,
  );
  const staleCard = harness.documentRef
    .getElementById("live-army-current-list")
    .querySelector(".live-army-card");
  assert.equal(staleCard.dataset.freshness, "stale");
  assert.equal(staleCard.dataset.activity, "current");
  assert.match(textTree(staleCard), /观测.*11分40秒前/s);
  assert.match(
    textTree(harness.documentRef.getElementById("live-army-current-list")),
    /过期待确认.*11分40秒前/s,
  );
  const detailText = textTree(
    harness.documentRef.getElementById("live-army-detail"),
  );
  assert.match(detailText, /过期待确认/);
  assert.match(detailText, /最后观测/);
  assert.doesNotMatch(detailText, /已到达/);
});

test("selection persists when a current army moves into recent offline", async () => {
  const selected = apiArmy({ armyId: 18411352 });
  const offline = apiArmy({
    armyId: 18411352,
    offline: {
      deletedAtMs: 190_000,
      ageMs: 10_000,
      sourceCmd: 5028,
      sourceLabel: "5028 增量",
    },
  });
  const harness = controllerHarness([
    snapshot({ current: [selected] }),
    snapshot({ current: [], recentOffline: [offline] }),
  ]);
  await harness.controller.load();
  await harness.controller.load(true);

  assert.equal(harness.controller.state.selectedArmyId, 18411352);
  assert.equal(
    harness.documentRef
      .getElementById("live-army-offline-list")
      .querySelector(".live-army-card")
      .classList.contains("is-selected"),
    true,
  );
  assert.match(
    textTree(harness.documentRef.getElementById("live-army-detail")),
    /最近离线/,
  );
});

test("selection updates list map and strict unknown detail once", async () => {
  const unknown = apiArmy({
    armyId: 814501,
    isMoving: false,
    state: 5,
    stateKey: "reside",
    stateLabel: "驻守",
    stateCategory: "stationary",
    timing: { endTime: 0 },
    lineup: {
      status: "unknown",
      complete: false,
      heroes: [],
      message: "无同 ID 战报，阵容未知",
    },
  });
  const harness = controllerHarness([
    snapshot({ current: [apiArmy(), unknown] }),
  ]);
  await harness.controller.load();
  const currentList = harness.documentRef.getElementById(
    "live-army-current-list",
  );
  const offlineList = harness.documentRef.getElementById(
    "live-army-offline-list",
  );
  const detail = harness.documentRef.getElementById("live-army-detail");
  const before = {
    current: currentList.replaceCount,
    offline: offlineList.replaceCount,
    detail: detail.replaceCount,
    map: harness.mapModule.drawCalls.length,
  };

  harness.controller.selectArmy(814501);

  assert.equal(currentList.replaceCount, before.current + 1);
  assert.equal(offlineList.replaceCount, before.offline + 1);
  assert.equal(detail.replaceCount, before.detail + 1);
  assert.equal(harness.mapModule.drawCalls.length, before.map + 1);
  const selectedCard = currentList.querySelector(
    "[data-army-id='814501']",
  );
  assert.equal(selectedCard.dataset.lineupStatus, "unknown");
  assert.equal(selectedCard.dataset.activity, "current");
  assert.match(textTree(detail), /无同 ID 战报，阵容未知/);
  assert.doesNotMatch(textTree(detail), /杜预/);
});

test("repeated world delta events share one debounced refresh while visible", async () => {
  const harness = controllerHarness([snapshot(), snapshot()]);
  await harness.controller.load();
  for (let index = 0; index < 3; index += 1) {
    harness.windowRef.dispatch("stzb:stream-event", {
      type: "world_scene_delta",
    });
  }

  assert.equal(harness.controller.state.dirty, true);
  assert.equal(harness.timers.timeouts.size, 1);
  assert.equal(
    [...harness.timers.timeouts.values()][0].delay,
    350,
  );
  await harness.timers.runTimeout(350);
  assert.equal(harness.requested.length, 2);
  assert.equal(harness.controller.state.dirty, false);

  harness.documentRef.visibilityState = "hidden";
  harness.documentRef.dispatch("visibilitychange");
  harness.windowRef.dispatch("stzb:stream-event", {
    type: "world_snapshot_complete",
  });
  assert.equal(harness.controller.state.dirty, true);
  assert.equal(harness.timers.timeouts.size, 0);
  assert.equal(harness.requested.length, 2);
});

test("visibility and tab re-entry flush dirty state without a second SSE", async () => {
  const harness = controllerHarness([snapshot(), snapshot(), snapshot()]);
  await harness.controller.load();
  harness.documentRef.visibilityState = "hidden";
  harness.documentRef.dispatch("visibilitychange");
  harness.windowRef.dispatch("stzb:stream-event", {
    type: "world_scene_delta",
  });

  harness.documentRef.visibilityState = "visible";
  harness.documentRef.dispatch("visibilitychange");
  await harness.timers.runTimeout(0);
  assert.equal(harness.requested.length, 2);

  harness.windowRef.dispatch("stzb:tab-changed", { tabId: 33 });
  harness.windowRef.dispatch("stzb:stream-event", {
    type: "world_scene_delta",
  });
  assert.equal(harness.timers.timeouts.size, 0);
  harness.windowRef.dispatch("stzb:tab-changed", { tabId: 35 });
  await harness.timers.runTimeout(0);
  assert.equal(harness.requested.length, 3);
});

test("stream event during an in-flight request schedules one follow-up load", async () => {
  const documentRef = new FakeDocument();
  const windowRef = new FakeWindow();
  const timers = fakeTimers();
  const mapModule = fakeMapModule();
  const requested = [];
  let resolveSecond;
  const controller = createLiveArmyCommand({
    documentRef,
    windowRef,
    fetchFn: async (url, options) => {
      requested.push([url, options]);
      if (requested.length === 2) {
        return new Promise((resolve) => {
          resolveSecond = resolve;
        });
      }
      return response(snapshot());
    },
    setTimeoutFn: timers.setTimeoutFn,
    clearTimeoutFn: timers.clearTimeoutFn,
    setIntervalFn: timers.setIntervalFn,
    clearIntervalFn: timers.clearIntervalFn,
    nowFn: () => 100_000,
    mapModule,
  });
  await controller.load();
  const inFlight = controller.load(true);
  windowRef.dispatch("stzb:stream-event", {
    type: "world_scene_delta",
  });
  const refreshTimer = [...timers.timeouts.entries()].find(
    ([, value]) => value.delay === 350,
  );
  assert.ok(refreshTimer);
  timers.timeouts.delete(refreshTimer[0]);
  const scheduledRefresh = refreshTimer[1].callback();
  assert.equal(requested.length, 2);

  resolveSecond(response(snapshot()));
  await inFlight;
  await scheduledRefresh;

  assert.equal(controller.state.dirty, true);
  await timers.runTimeout(0);
  assert.equal(requested.length, 3);
  assert.equal(controller.state.dirty, false);
});

test("load lifecycle preserves snapshot and cleans busy after refresh error", async () => {
  const documentRef = new FakeDocument();
  const windowRef = new FakeWindow();
  const timers = fakeTimers();
  const mapModule = fakeMapModule();
  const pending = [];
  const fetchFn = () => {
    const request = {};
    request.promise = new Promise((resolve, reject) => {
      request.resolve = resolve;
      request.reject = reject;
    });
    pending.push(request);
    return request.promise;
  };
  const controller = createLiveArmyCommand({
    documentRef,
    windowRef,
    fetchFn,
    setTimeoutFn: timers.setTimeoutFn,
    clearTimeoutFn: timers.clearTimeoutFn,
    setIntervalFn: timers.setIntervalFn,
    clearIntervalFn: timers.clearIntervalFn,
    nowFn: () => 100_000,
    mapModule,
  });
  const summary = documentRef.getElementById("live-army-summary");

  const firstLoad = controller.load();
  assert.equal(summary.getAttribute("aria-busy"), "true");
  assert.equal(summary.classList.contains("hud-refresh-line"), true);
  assert.match(textTree(summary), /正在聚合实时部队/);
  pending[0].resolve(response(snapshot()));
  await firstLoad;
  const retainedSummary = textTree(summary);
  assert.match(retainedSummary, /10分钟内/);
  assert.equal(summary.hasAttribute("aria-busy"), false);
  assert.equal(summary.classList.contains("hud-refresh-line"), false);

  const refresh = controller.load(true);
  assert.equal(summary.getAttribute("aria-busy"), "true");
  assert.equal(summary.classList.contains("hud-refresh-line"), true);
  assert.equal(textTree(summary), retainedSummary);
  pending[1].reject(new Error("temporary live failure"));
  await refresh;

  assert.equal(textTree(summary), retainedSummary);
  assert.equal(
    documentRef.getElementById("live-army-freshness").textContent,
    "DEGRADED / RETAINED",
  );
  assert.equal(summary.hasAttribute("aria-busy"), false);
  assert.equal(summary.classList.contains("hud-refresh-line"), false);
  assert.equal(controller.state.loading, false);
});

test("merged in-flight load keeps one owner until dirty follow-up completes", async () => {
  const documentRef = new FakeDocument();
  const windowRef = new FakeWindow();
  const timers = fakeTimers();
  const mapModule = fakeMapModule();
  const pending = [];
  const fetchFn = () => {
    const request = {};
    request.promise = new Promise((resolve) => {
      request.resolve = resolve;
    });
    pending.push(request);
    return request.promise;
  };
  const controller = createLiveArmyCommand({
    documentRef,
    windowRef,
    fetchFn,
    setTimeoutFn: timers.setTimeoutFn,
    clearTimeoutFn: timers.clearTimeoutFn,
    setIntervalFn: timers.setIntervalFn,
    clearIntervalFn: timers.clearIntervalFn,
    nowFn: () => 100_000,
    mapModule,
  });
  const summary = documentRef.getElementById("live-army-summary");

  const first = controller.load();
  const merged = controller.load(true);
  assert.equal(pending.length, 1);
  windowRef.dispatch("stzb:stream-event", {
    type: "world_scene_delta",
  });
  const dirtyTimer = [...timers.timeouts.entries()].find(
    ([, value]) => value.delay === 350,
  );
  assert.ok(dirtyTimer);
  timers.timeouts.delete(dirtyTimer[0]);
  const scheduled = dirtyTimer[1].callback();

  pending[0].resolve(response(snapshot()));
  await Promise.all([first, merged]);
  await scheduled;
  assert.equal(pending.length, 1);
  assert.equal(summary.hasAttribute("aria-busy"), false);
  assert.equal(summary.classList.contains("hud-refresh-line"), false);
  assert.equal(controller.state.dirty, true);

  const followUpTimer = [...timers.timeouts.entries()].find(
    ([, value]) => value.delay === 0,
  );
  assert.ok(followUpTimer);
  timers.timeouts.delete(followUpTimer[0]);
  const followUp = followUpTimer[1].callback();
  assert.equal(pending.length, 2);
  assert.equal(summary.getAttribute("aria-busy"), "true");
  pending[1].resolve(response(snapshot({ generatedAtMs: 300_000 })));
  await followUp;
  assert.equal(controller.state.dirty, false);
  assert.equal(summary.hasAttribute("aria-busy"), false);
  assert.equal(summary.classList.contains("hud-refresh-line"), false);
});

test("one second ticker changes countdown without requests or HUD events", async () => {
  const hudEvents = [];
  const harness = controllerHarness(
    [snapshot()],
    { emitHudEvent: (event) => hudEvents.push(event) },
  );
  await harness.controller.load();
  const countdown = harness.documentRef.querySelector(
    "[data-live-army-countdown]",
  );
  const detailCountdown = harness.documentRef.querySelector(
    "[data-live-army-detail-countdown]",
  );
  assert.ok(countdown);
  assert.ok(detailCountdown);
  const before = countdown.textContent;
  const detailBefore = detailCountdown.textContent;
  const interval = [...harness.timers.intervals.values()][0];
  assert.equal(interval.delay, 1000);

  harness.controller.state.nowMs = 110_000;
  interval.callback();

  assert.notEqual(countdown.textContent, before);
  assert.notEqual(detailCountdown.textContent, detailBefore);
  assert.equal(harness.requested.length, 1);
  assert.deepEqual(hudEvents, []);
  harness.documentRef.visibilityState = "hidden";
  harness.documentRef.dispatch("visibilitychange");
  assert.equal(harness.timers.intervals.size, 0);
});

test("selecting the same high-risk army emits one allowlisted HUD event", async () => {
  const hudEvents = [];
  const highRisk = apiArmy({
    armyId: 814501,
    location: { currentWid: 2081499 },
    risk: { level: "high", score: 87 },
  });
  const harness = controllerHarness(
    [snapshot({ current: [apiArmy(), highRisk] })],
    { emitHudEvent: (event) => hudEvents.push(event) },
  );
  await harness.controller.load();

  harness.controller.selectArmy(814501);
  harness.controller.selectArmy(814501);

  assert.equal(hudEvents.length, 1);
  assert.deepEqual(hudEvents[0], {
    type: "intelligence:risk-detected",
    target: "#live-army-detail",
    domain: "intelligence",
    severity: "critical",
    message: "ARMY 814501 · WID 2081499 风险 87",
    timestamp: 100_000,
    dedupeKey: "live-army-risk:814501:2081499:high",
    cooldownMs: 10_000,
  });
});

test("resolved live-army risk releases its key for a later recurrence", async () => {
  const hudCalls = [];
  const highRisk = apiArmy({
    armyId: 814501,
    location: { currentWid: 2081499 },
    risk: { level: "high", score: 87 },
  });
  const safeRisk = apiArmy({
    armyId: 814501,
    location: { currentWid: 2081499 },
    risk: { level: "low", score: 12 },
  });
  const harness = controllerHarness(
    [
      snapshot({ current: [apiArmy(), highRisk] }),
      snapshot({ current: [apiArmy(), safeRisk] }),
      snapshot({ current: [apiArmy(), highRisk] }),
    ],
    {
      emitHudEvent(event) {
        hudCalls.push(["emit", event]);
      },
      resolveHudEvent(key) {
        hudCalls.push(["resolve", key]);
      },
    },
  );
  await harness.controller.load();
  harness.controller.selectArmy(814501);
  await harness.controller.load(true);
  harness.controller.selectArmy(814501);
  await harness.controller.load(true);
  harness.controller.selectArmy(814501);

  assert.deepEqual(
    hudCalls.map(([kind, value]) => [
      kind,
      kind === "resolve" ? value : value.dedupeKey,
    ]),
    [
      ["emit", "live-army-risk:814501:2081499:high"],
      ["resolve", "live-army-risk:814501:2081499:high"],
      ["emit", "live-army-risk:814501:2081499:high"],
    ],
  );
});

test("age ticker preserves observation and deletion labels", async () => {
  const offline = apiArmy({
    armyId: 8,
    offline: {
      deletedAtMs: 190_000,
      ageMs: 10_000,
      sourceCmd: 5028,
      sourceLabel: "5028 增量",
    },
  });
  const harness = controllerHarness([
    snapshot({
      current: [apiArmy()],
      recentOffline: [offline],
    }),
  ]);
  await harness.controller.load();
  const ages = harness.documentRef.querySelectorAll("[data-live-army-age]");
  assert.equal(ages.length, 2);

  harness.controller.state.nowMs = 210_000;
  const interval = [...harness.timers.intervals.values()][0];
  interval.callback();

  assert.match(ages[0].textContent, /^观测 /);
  assert.match(ages[1].textContent, /^删除 /);
});

test("first load detects tab activation that happened before binding", async () => {
  const harness = controllerHarness([snapshot()]);
  harness.documentRef.getElementById("tab35").classList.remove("active");
  harness.controller.state.activeTab = 0;
  harness.documentRef.getElementById("tab35").classList.add("active");

  await harness.controller.load();

  assert.equal(harness.controller.state.activeTab, 35);
  assert.equal(harness.timers.intervals.size, 1);
});

test("open in intelligence uses the selected current WID", async () => {
  const harness = controllerHarness([snapshot()]);
  await harness.controller.load();

  await harness.controller.openInIntelligence();

  assert.deepEqual(harness.windowRef.switchCalls, [33]);
  assert.deepEqual(harness.windowRef.openedViews, ["map"]);
  assert.deepEqual(harness.windowRef.locatedWids, [2081480]);
});

test("mobile index toggle exposes a collapsed and expanded state", async () => {
  const harness = controllerHarness([snapshot()]);
  await harness.controller.load();
  const index = harness.documentRef.querySelector(".live-army-index");
  const toggle = harness.documentRef.getElementById("live-army-index-toggle");
  assert.ok(index);
  assert.ok(toggle);

  toggle.dispatch("click");
  assert.equal(index.classList.contains("is-collapsed"), true);
  assert.equal(toggle.getAttribute("aria-expanded"), "false");

  toggle.dispatch("click");
  assert.equal(index.classList.contains("is-collapsed"), false);
  assert.equal(toggle.getAttribute("aria-expanded"), "true");
});

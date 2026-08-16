import assert from "node:assert/strict";
import test from "node:test";

import {
  createRequestOwnerRegistry,
  createResearchContextSnapshot,
  createResearchWorkbench,
  deriveMatchupState,
  isResearchContextSnapshotCurrent,
  normalizeResearchLineup,
  replaceResearchHero,
  replaceResearchSkill,
  swapResearchPositions,
  validateResearchLineup,
} from "../../static/research-workbench.mjs";

const lineup = (heroIds) =>
  normalizeResearchLineup({
    heroes: heroIds.map((id) => ({ id })),
  });

const fakeDocument = () => ({
  getElementById() {
    return null;
  },
  querySelectorAll() {
    return [];
  },
  addEventListener() {},
});

const fakeWindow = () => ({
  addEventListener() {},
});

const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
};

const waitFor = async (predicate, message = "condition was not reached") => {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    if (predicate()) return;
    await Promise.resolve();
  }
  assert.equal(predicate(), true, message);
};

test("request owner registry releases invalidated work without clearing a newer owner", () => {
  const owners = createRequestOwnerRegistry();
  const older = owners.acquire("detail");
  const newer = owners.acquire("detail");

  assert.equal(owners.release("detail", older), false);
  assert.equal(owners.isCurrent("detail", newer), true);
  assert.equal(owners.release("detail", newer), true);
  assert.equal(owners.current("detail"), null);
});

test("research context snapshots include selection and lineup identity", () => {
  const state = {
    modelRevision: 4,
    lineup: completeLineup([101, 102, 103]),
    opponent: completeLineup([201, 202, 203]),
    selectedPosition: 1,
    selectedSkillSlot: { position: 1, slot: 0 },
  };
  const snapshot = createResearchContextSnapshot(state, { side: "right" });

  assert.deepEqual(snapshot, {
    modelRevision: 4,
    side: "right",
    position: 1,
    skillSlot: { position: 1, slot: 0 },
    leftKey: "101.102.103",
    rightKey: "201.202.203",
    currentKey: "201.202.203",
  });
  assert.equal(
    isResearchContextSnapshotCurrent(snapshot, state, { side: "right" }),
    true,
  );
  state.selectedPosition = 2;
  assert.equal(
    isResearchContextSnapshotCurrent(snapshot, state, { side: "right" }),
    false,
  );
});

const completeLineup = (heroIds) => normalizeResearchLineup({
  heroes: heroIds.map((id) => ({ id })),
});

const createHarness = (fetchJson, options = {}) => createResearchWorkbench({
  documentRef: fakeDocument(),
  windowRef: fakeWindow(),
  fetchJson,
  storage: {
    getItem() {
      return null;
    },
    setItem() {},
  },
  setTimeoutFn: (callback) => {
    callback();
    return 1;
  },
  clearTimeoutFn() {},
  nowFn: () => 1_900_000_000_000,
  ...options,
});

test("normalization creates three stable positions and two skill slots", () => {
  assert.deepEqual(normalizeResearchLineup({
    morale: 110,
    heroes: [{ id: 100027, level: 45, up: 5, equip_skills: [200001] }],
  }), {
    schemaVersion: 1,
    name: "",
    morale: 110,
    heroes: [
      { id: 100027, position: 0, level: 45, up: 5, equip_skills: [200001, 0] },
      { id: 0, position: 1, level: 40, up: 0, equip_skills: [0, 0] },
      { id: 0, position: 2, level: 40, up: 0, equip_skills: [0, 0] },
    ],
  });
});

test("validation rejects duplicate and incomplete heroes", () => {
  const duplicate = lineup([100027, 100027, 100090]);
  assert.deepEqual(validateResearchLineup(duplicate), {
    valid: false,
    complete: true,
    errors: ["武将不能重复"],
  });
  assert.equal(validateResearchLineup(lineup([100027, 0, 100090])).complete, false);
});

test("position swap preserves nested hero configuration", () => {
  const initial = lineup([100027, 100016, 100090]);
  initial.heroes[0].equip_skills = [200001, 200027];
  const result = swapResearchPositions(initial, 0, 2);
  assert.equal(result.heroes[2].id, 100027);
  assert.deepEqual(result.heroes[2].equip_skills, [200001, 200027]);
  assert.notEqual(result, initial);
});

test("hero and skill replacement affect only one target", () => {
  const initial = lineup([100027, 100016, 100090]);
  const heroChanged = replaceResearchHero(initial, 1, { id: 100705 });
  assert.equal(heroChanged.heroes[1].id, 100705);
  assert.deepEqual(heroChanged.heroes[1].equip_skills, [0, 0]);
  const skillChanged = replaceResearchSkill(heroChanged, 1, 0, 200914);
  assert.deepEqual(skillChanged.heroes[1].equip_skills, [200914, 0]);
});

test("normalization clamps lineup ranges without retaining nested input", () => {
  const skills = [200001, 200027, 200999];
  const input = {
    morale: 999,
    heroes: [
      { id: 100027, level: 0, up: -3, equip_skills: skills },
      { id: 100016, level: 99, up: 12 },
    ],
  };

  const result = normalizeResearchLineup(input);

  assert.equal(result.morale, 200);
  assert.equal(result.heroes[0].level, 1);
  assert.equal(result.heroes[0].up, 0);
  assert.equal(result.heroes[1].level, 50);
  assert.equal(result.heroes[1].up, 9);
  assert.deepEqual(result.heroes[0].equip_skills, [200001, 200027]);
  assert.notEqual(result.heroes[0].equip_skills, skills);
  result.heroes[0].equip_skills[0] = 0;
  assert.equal(skills[0], 200001);
});

test("invalid positions and skill slots throw RangeError", () => {
  const initial = lineup([100027, 100016, 100090]);

  assert.throws(() => swapResearchPositions(initial, -1, 2), RangeError);
  assert.throws(() => replaceResearchHero(initial, 3, { id: 100705 }), RangeError);
  assert.throws(() => replaceResearchSkill(initial, 0, 2, 200914), RangeError);
});

test("IDs must be non-negative integers while empty skill zero is accepted", () => {
  assert.throws(
    () => normalizeResearchLineup({ heroes: [{ id: "100027" }] }),
    TypeError,
  );
  assert.throws(
    () => normalizeResearchLineup({
      heroes: [{ id: 100027, equip_skills: [-1] }],
    }),
    TypeError,
  );

  const initial = lineup([100027, 100016, 100090]);
  assert.throws(
    () => replaceResearchHero(initial, 0, { id: 100027.5 }),
    TypeError,
  );
  assert.throws(
    () => replaceResearchSkill(initial, 0, 0, "200914"),
    TypeError,
  );
  assert.deepEqual(
    replaceResearchSkill(initial, 0, 0, 0).heroes[0].equip_skills,
    [0, 0],
  );
});

test("matchup evidence uses discrete explainable states", () => {
  assert.equal(deriveMatchupState(
    { battleStats: { sampleSize: 0, winRate: 0 } },
    null,
    { left: true, right: true },
  ).key, "insufficient");
  assert.equal(deriveMatchupState(
    { battleStats: { sampleSize: 20, winRate: 65 } },
    null,
    { left: true, right: true },
  ).key, "history-advantage");
  assert.equal(deriveMatchupState(
    { battleStats: { sampleSize: 20, winRate: 35 } },
    { winRate: 62 },
    { left: true, right: true },
  ).key, "simulation-conflict");
});

test("matchup evidence covers completeness disadvantage and cautious verification", () => {
  const incomplete = deriveMatchupState(
    { battleStats: { sampleSize: 20, winRate: 65 } },
    null,
    { left: true, right: false },
  );
  assert.equal(incomplete.key, "verify");
  assert.equal(incomplete.label, "谨慎验证");
  assert.ok(incomplete.reasons.length > 0);

  const disadvantage = deriveMatchupState(
    { battleStats: { sampleSize: 20, winRate: 40 } },
    { winRate: 45 },
    { left: true, right: true },
  );
  assert.equal(disadvantage.key, "history-disadvantage");
  assert.equal(disadvantage.label, "历史劣势");

  const verify = deriveMatchupState(
    { battleStats: { sampleSize: 20, winRate: 55 } },
    { winRate: 52 },
    { left: true, right: true },
  );
  assert.deepEqual(verify, {
    key: "verify",
    label: "谨慎验证",
    reasons: ["历史与模拟证据均未形成明确方向"],
  });
});

test("matching threshold evidence keeps the historical conclusion", () => {
  assert.equal(deriveMatchupState(
    { battleStats: { sampleSize: 10, winRate: 60 } },
    { winRate: 50 },
    { left: true, right: true },
  ).key, "history-advantage");
  assert.equal(deriveMatchupState(
    { battleStats: { sampleSize: 10, winRate: 40 } },
    { winRate: 50 },
    { left: true, right: true },
  ).key, "history-disadvantage");
});

test("missing or invalid historical win rates require verification", () => {
  for (const winRate of [undefined, null, "", "35", NaN, -1, 101]) {
    assert.equal(
      deriveMatchupState(
        { battleStats: { sampleSize: 20, winRate } },
        null,
        { left: true, right: true },
      ).key,
      "verify",
      `expected verify for historical winRate ${String(winRate)}`,
    );
  }
});

test("invalid simulation win rates are treated as absent evidence", () => {
  for (const winRate of [null, "", "80", NaN, -1, 101]) {
    assert.equal(
      deriveMatchupState(
        { battleStats: { sampleSize: 20, winRate: 35 } },
        { winRate },
        { left: true, right: true },
      ).key,
      "history-disadvantage",
      `expected no simulation conflict for simulation winRate ${String(winRate)}`,
    );
  }
});

test("controller starts in lab and mode switches preserve research selections", async () => {
  const workbench = createHarness(async () => ({ ok: true, rows: [] }));
  const selectedLineup = completeLineup([100027, 100016, 100090]);
  workbench.state.lineup = selectedLineup;
  workbench.state.selectedEvidence = "simulation";

  await workbench.setMode("matchup");
  await workbench.setMode("chain");

  assert.equal(workbench.state.mode, "chain");
  assert.equal(workbench.state.lineup, selectedLineup);
  assert.equal(workbench.state.selectedEvidence, "simulation");
});

test("library search routes hero and skill queries to their approved endpoints", async () => {
  const urls = [];
  const workbench = createHarness(async (url) => {
    urls.push(url);
    return { ok: true, rows: [] };
  });

  await workbench.search("张辽");
  workbench.setLibraryKind("skill");
  await workbench.search("其疾如风");

  assert.match(urls[0], /^\/api\/intelligence\/heroes\?/);
  assert.match(urls[0], /q=%E5%BC%A0%E8%BE%BD/);
  assert.match(urls[1], /^\/api\/intelligence\/skills\?/);
  assert.match(urls[1], /q=%E5%85%B6%E7%96%BE%E5%A6%82%E9%A3%8E/);
});

test("historical lineup selection loads its detail and applies the lineup", async () => {
  const urls = [];
  const workbench = createHarness(async (url) => {
    urls.push(url);
    return {
      ok: true,
      key: "100027.100016.100090",
      configFacts: {
        heroes: [
          { heroId: 100027, level: 45 },
          { heroId: 100016, level: 42 },
          { heroId: 100090, level: 40 },
        ],
      },
      battleStats: { sampleSize: 18, winRate: 61.1 },
      simulationLink: {
        lineup: completeLineup([100027, 100016, 100090]),
      },
    };
  });

  await workbench.openLineup("100027.100016.100090");

  assert.equal(
    urls[0],
    "/api/intelligence/lineups/100027.100016.100090",
  );
  assert.equal(workbench.state.activeHistoricalLineup.key, "100027.100016.100090");
  assert.deepEqual(
    workbench.state.lineup.heroes.map((hero) => hero.id),
    [100027, 100016, 100090],
  );
});

test("card pack compatibility updates shared selection without replacing lineup", async () => {
  const selected = completeLineup([101, 102, 103]);
  const workbench = createHarness(async () => ({
    ok: true,
    packId: 802,
    heroCount: 12,
    countryDistribution: [],
  }));
  workbench.setLineup(selected);
  const preservedLineup = workbench.state.lineup;

  await workbench.openCardPack(802);

  assert.equal(workbench.state.libraryKind, "card-pack");
  assert.equal(workbench.state.selectedLibraryItem.packId, 802);
  assert.equal(workbench.state.selectedEvidence, "config");
  assert.equal(workbench.state.lineup, preservedLineup);
  assert.equal(workbench.state.mode, "lab");
});

test("matchup request runs only after both lineups are complete", async () => {
  const urls = [];
  const workbench = createHarness(async (url) => {
    urls.push(url);
    return {
      ok: true,
      battleStats: { sampleSize: 12, winRate: 58.3 },
    };
  });
  workbench.setLineup(completeLineup([101, 102, 103]));
  workbench.setOpponent(completeLineup([201, 0, 203]));

  await workbench.setMode("matchup");
  assert.equal(urls.length, 0);

  workbench.setOpponent(completeLineup([201, 202, 203]));
  await workbench.refreshMatchup();

  assert.deepEqual(urls, [
    "/api/intelligence/lineups/101.102.103/matchup/201.202.203",
  ]);
  assert.equal(workbench.state.matchup.battleStats.sampleSize, 12);
});

test("matchup target selection writes library heroes to the chosen side", async () => {
  const workbench = createHarness(async () => ({
    ok: true,
    hero: { heroid: 202, name: "opponent" },
    initialSkill: null,
  }));
  workbench.setLineup(completeLineup([101, 102, 103]));
  workbench.setOpponent(completeLineup([201, 0, 203]));

  workbench.selectPosition(1, "right");
  await workbench.openHero(202);

  assert.deepEqual(
    workbench.state.lineup.heroes.map((hero) => hero.id),
    [101, 102, 103],
  );
  assert.deepEqual(
    workbench.state.opponent.heroes.map((hero) => hero.id),
    [201, 202, 203],
  );
});

test("skill slot click takes priority over the surrounding hero card", () => {
  const listeners = new Map();
  const stage = {
    addEventListener(type, listener) {
      listeners.set(type, listener);
    },
  };
  const documentRef = {
    getElementById(id) {
      return id === "research-stage" ? stage : null;
    },
    querySelectorAll() {
      return [];
    },
    addEventListener() {},
  };
  const workbench = createHarness(
    async () => ({ ok: true, rows: [] }),
    { documentRef },
  );
  workbench.bind();
  const skillTarget = {
    dataset: { skillPosition: "0", skillSlot: "1" },
  };
  const heroTarget = {
    dataset: { selectPosition: "0" },
  };
  listeners.get("click")({
    target: {
      closest(selector) {
        if (selector === "[data-skill-position]") return skillTarget;
        if (selector === "[data-select-position]") return heroTarget;
        return null;
      },
    },
  });

  assert.deepEqual(workbench.state.selectedSkillSlot, {
    position: 0,
    slot: 1,
  });
  assert.equal(workbench.state.libraryKind, "skill");
});

test("chain mode loads each hero and skill detail once and caches by ID", async () => {
  const urls = [];
  const workbench = createHarness(async (url) => {
    urls.push(url);
    if (url.startsWith("/api/intelligence/heroes/")) {
      const heroId = Number(url.split("/").at(-1));
      return {
        ok: true,
        hero: { heroid: heroId, name: `hero-${heroId}` },
        initialSkill: { skill_id: heroId + 1, name: `initial-${heroId}` },
      };
    }
    const skillId = Number(url.split("/").at(-1));
    return {
      ok: true,
      skill: { skill_id: skillId, name: `skill-${skillId}` },
      details: [],
    };
  });
  const selected = completeLineup([101, 102, 103]);
  selected.heroes[0].equip_skills = [201, 202];
  workbench.setLineup(selected);

  await workbench.setMode("chain");
  await workbench.refreshChain();

  assert.equal(urls.filter((url) => url === "/api/intelligence/heroes/101").length, 1);
  assert.equal(urls.filter((url) => url === "/api/intelligence/skills/102").length, 1);
  assert.equal(urls.filter((url) => url === "/api/intelligence/skills/201").length, 1);
  assert.equal(urls.filter((url) => url === "/api/intelligence/skills/202").length, 1);
  assert.equal(workbench.state.heroDetails.has(101), true);
  assert.equal(workbench.state.skillDetails.has(201), true);
});

test("stale detail responses cannot overwrite the newer selection", async () => {
  const first = deferred();
  const second = deferred();
  const workbench = createHarness((url) => (
    url.endsWith("/101") ? first.promise : second.promise
  ));

  const older = workbench.openHero(101);
  const newer = workbench.openHero(202);
  second.resolve({
    ok: true,
    hero: { heroid: 202, name: "newer" },
    initialSkill: null,
  });
  await newer;
  first.resolve({
    ok: true,
    hero: { heroid: 101, name: "older" },
    initialSkill: null,
  });
  await older;

  assert.equal(workbench.state.selectedLibraryItem.hero.heroid, 202);
});

test("failed requests retain the previous successful stage", async () => {
  let fail = false;
  const workbench = createHarness(async () => {
    if (fail) throw new Error("network down");
    return {
      ok: true,
      hero: { heroid: 101, name: "stable detail" },
      initialSkill: null,
    };
  });
  await workbench.openHero(101);
  const previous = workbench.state.selectedLibraryItem;

  fail = true;
  await assert.rejects(workbench.openHero(202), /network down/);

  assert.equal(workbench.state.selectedLibraryItem, previous);
  assert.match(workbench.state.error, /network down/);
});

test("openDetail owns a latest-wins lifecycle with retry and preserved refresh content", async () => {
  const requests = [];
  const lifecycle = [];
  const workbench = createHarness(
    () => {
      const request = deferred();
      requests.push(request);
      return request.promise;
    },
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );

  const first = workbench.openHero(101);
  assert.deepEqual(lifecycle.at(-1), {
    surface: "detail",
    kind: "loading",
    message: "正在加载研究详情…",
    replace: true,
    busy: true,
    ownerToken: 1,
    actionLabel: "",
    action: undefined,
  });
  requests[0].resolve({
    ok: true,
    hero: { heroid: 101, name: "stable detail" },
    initialSkill: null,
  });
  await first;
  assert.equal(workbench.state.selectedLibraryItem.hero.heroid, 101);
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);

  const older = workbench.openHero(202);
  assert.equal(lifecycle.at(-1).kind, "refreshing");
  assert.equal(lifecycle.at(-1).replace, false);
  assert.equal(workbench.state.selectedLibraryItem.hero.heroid, 101);
  const newer = workbench.openHero(303);
  requests[2].resolve({
    ok: true,
    hero: { heroid: 303, name: "latest detail" },
    initialSkill: null,
  });
  await newer;
  requests[1].reject(new Error("stale detail failure"));
  await assert.rejects(older, /stale detail failure/);
  assert.equal(workbench.state.selectedLibraryItem.hero.heroid, 303);
  assert.equal(workbench.state.error, "");
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);

  const failed = workbench.openHero(404);
  requests[3].reject(new Error("detail unavailable"));
  await assert.rejects(failed, /detail unavailable/);
  assert.equal(workbench.state.selectedLibraryItem.hero.heroid, 303);
  assert.equal(lifecycle.at(-1).kind, "error");
  assert.equal(lifecycle.at(-1).replace, false);
  assert.equal(lifecycle.at(-1).busy, false);
  assert.equal(lifecycle.at(-1).actionLabel, "重试");

  const retry = lifecycle.at(-1).action();
  requests[4].resolve({
    ok: true,
    hero: { heroid: 404, name: "recovered detail" },
    initialSkill: null,
  });
  await retry;
  assert.equal(workbench.state.selectedLibraryItem.hero.heroid, 404);
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);
});

test("openDetail exposes blocking error and empty states without a DOM", async () => {
  const lifecycle = [];
  let response = Promise.reject(new Error("first detail failure"));
  const workbench = createHarness(
    () => response,
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );

  await assert.rejects(workbench.openSkill(201), /first detail failure/);
  assert.equal(lifecycle.at(-1).surface, "detail");
  assert.equal(lifecycle.at(-1).kind, "error");
  assert.equal(lifecycle.at(-1).replace, true);
  assert.equal(lifecycle.at(-1).busy, false);

  response = Promise.resolve({ ok: true });
  await lifecycle.at(-1).action();
  assert.equal(workbench.state.selectedLibraryItem, null);
  assert.equal(lifecycle.at(-1).kind, "empty");
  assert.equal(lifecycle.at(-1).replace, true);
  assert.equal(lifecycle.at(-1).busy, false);
});

test("openDetail cannot apply a response after its target selection changes", async () => {
  const request = deferred();
  const lifecycle = [];
  const workbench = createHarness(
    () => request.promise,
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );
  workbench.setLineup(completeLineup([101, 102, 103]));
  workbench.selectPosition(0);

  const stale = workbench.openHero(201);
  workbench.selectPosition(1);
  request.resolve({
    ok: true,
    hero: { heroid: 201, name: "stale target" },
    initialSkill: null,
  });

  assert.equal(await stale, null);
  assert.deepEqual(
    workbench.state.lineup.heroes.map((hero) => hero.id),
    [101, 102, 103],
  );
  assert.equal(workbench.state.selectedLibraryItem, null);
  assert.equal(lifecycle.at(-1).busy, false);
});

test("openDetail cannot publish an error after its target selection changes", async () => {
  const request = deferred();
  const lifecycle = [];
  const workbench = createHarness(
    () => request.promise,
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );
  workbench.setLineup(completeLineup([101, 102, 103]));
  workbench.selectPosition(0);

  const stale = workbench.openHero(201);
  workbench.selectPosition(1);
  request.reject(new Error("stale target failure"));

  await assert.rejects(stale, /stale target failure/);
  assert.equal(workbench.state.error, "");
  assert.notEqual(lifecycle.at(-1).kind, "error");
  assert.equal(lifecycle.at(-1).busy, false);
});

test("openLineup owns an independent latest-wins lifecycle and preserves a stable lineup", async () => {
  const requests = [];
  const lifecycle = [];
  const workbench = createHarness(
    () => {
      const request = deferred();
      requests.push(request);
      return request.promise;
    },
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );

  const first = workbench.openLineup("101.102.103");
  assert.equal(lifecycle.at(-1).surface, "lineup");
  assert.equal(lifecycle.at(-1).kind, "loading");
  assert.equal(lifecycle.at(-1).replace, true);
  assert.equal(lifecycle.at(-1).busy, true);
  requests[0].resolve({
    ok: true,
    key: "101.102.103",
    simulationLink: { lineup: completeLineup([101, 102, 103]) },
  });
  await first;
  assert.equal(workbench.state.activeHistoricalLineup.key, "101.102.103");
  assert.deepEqual(
    workbench.state.lineup.heroes.map((hero) => hero.id),
    [101, 102, 103],
  );
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);

  const older = workbench.openLineup("201.202.203");
  assert.equal(lifecycle.at(-1).kind, "refreshing");
  assert.equal(lifecycle.at(-1).replace, false);
  const newer = workbench.openLineup("301.302.303");
  requests[2].resolve({
    ok: true,
    key: "301.302.303",
    simulationLink: { lineup: completeLineup([301, 302, 303]) },
  });
  await newer;
  requests[1].reject(new Error("stale lineup failure"));
  await assert.rejects(older, /stale lineup failure/);
  assert.equal(workbench.state.activeHistoricalLineup.key, "301.302.303");
  assert.deepEqual(
    workbench.state.lineup.heroes.map((hero) => hero.id),
    [301, 302, 303],
  );
  assert.equal(workbench.state.error, "");
  assert.equal(lifecycle.at(-1).kind, "success");

  const failed = workbench.openLineup("401.402.403");
  requests[3].reject(new Error("lineup unavailable"));
  await assert.rejects(failed, /lineup unavailable/);
  assert.equal(workbench.state.activeHistoricalLineup.key, "301.302.303");
  assert.equal(lifecycle.at(-1).kind, "error");
  assert.equal(lifecycle.at(-1).replace, false);
  assert.equal(lifecycle.at(-1).busy, false);
  assert.equal(lifecycle.at(-1).actionLabel, "重试");

  const retry = lifecycle.at(-1).action();
  requests[4].resolve({
    ok: true,
    key: "401.402.403",
    simulationLink: { lineup: completeLineup([401, 402, 403]) },
  });
  await retry;
  assert.equal(workbench.state.activeHistoricalLineup.key, "401.402.403");
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);
});

test("openLineup exposes blocking error and empty states without replacing controller state", async () => {
  const lifecycle = [];
  let response = Promise.reject(new Error("first lineup failure"));
  const workbench = createHarness(
    () => response,
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );

  await assert.rejects(
    workbench.openLineup("101.102.103"),
    /first lineup failure/,
  );
  assert.equal(lifecycle.at(-1).surface, "lineup");
  assert.equal(lifecycle.at(-1).kind, "error");
  assert.equal(lifecycle.at(-1).replace, true);
  assert.equal(lifecycle.at(-1).busy, false);

  response = Promise.resolve({ ok: true, key: "", configFacts: { heroes: [] } });
  await lifecycle.at(-1).action();
  assert.equal(workbench.state.activeHistoricalLineup, null);
  assert.deepEqual(
    workbench.state.lineup.heroes.map((hero) => hero.id),
    [0, 0, 0],
  );
  assert.equal(lifecycle.at(-1).kind, "empty");
  assert.equal(lifecycle.at(-1).replace, true);
  assert.equal(lifecycle.at(-1).busy, false);
});

test("openLineup cannot overwrite a lineup edited after the request starts", async () => {
  const request = deferred();
  const lifecycle = [];
  const workbench = createHarness(
    () => request.promise,
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );
  workbench.setLineup(completeLineup([101, 102, 103]));

  const stale = workbench.openLineup("201.202.203");
  workbench.setLineup(completeLineup([301, 302, 303]));
  request.resolve({
    ok: true,
    key: "201.202.203",
    simulationLink: { lineup: completeLineup([201, 202, 203]) },
  });

  assert.equal(await stale, null);
  assert.deepEqual(
    workbench.state.lineup.heroes.map((hero) => hero.id),
    [301, 302, 303],
  );
  assert.equal(lifecycle.at(-1).busy, false);
});

test("openLineup cannot publish an error after the lineup is edited", async () => {
  const request = deferred();
  const lifecycle = [];
  const workbench = createHarness(
    () => request.promise,
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );
  workbench.setLineup(completeLineup([101, 102, 103]));

  const stale = workbench.openLineup("201.202.203");
  workbench.setLineup(completeLineup([301, 302, 303]));
  request.reject(new Error("stale lineup failure"));

  await assert.rejects(stale, /stale lineup failure/);
  assert.equal(workbench.state.error, "");
  assert.notEqual(lifecycle.at(-1).kind, "error");
  assert.equal(lifecycle.at(-1).busy, false);
});

test("refreshMatchup owns latest-wins loading refresh empty error retry and busy cleanup", async () => {
  const requests = [];
  const lifecycle = [];
  const workbench = createHarness(
    () => {
      const request = deferred();
      requests.push(request);
      return request.promise;
    },
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );
  workbench.setLineup(completeLineup([101, 102, 103]));
  workbench.setOpponent(completeLineup([201, 202, 203]));

  const first = workbench.refreshMatchup();
  assert.equal(lifecycle.at(-1).surface, "matchup");
  assert.equal(lifecycle.at(-1).kind, "loading");
  assert.equal(lifecycle.at(-1).replace, true);
  assert.equal(lifecycle.at(-1).busy, true);
  requests[0].resolve({
    ok: true,
    leftKey: "101.102.103",
    rightKey: "201.202.203",
    battleStats: { sampleSize: 12, winRate: 58.3 },
  });
  await first;
  assert.equal(workbench.state.matchup.battleStats.sampleSize, 12);
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);

  const older = workbench.refreshMatchup();
  assert.equal(lifecycle.at(-1).kind, "refreshing");
  assert.equal(lifecycle.at(-1).replace, false);
  workbench.setOpponent(completeLineup([301, 302, 303]));
  const newer = workbench.refreshMatchup();
  requests[2].resolve({
    ok: true,
    leftKey: "101.102.103",
    rightKey: "301.302.303",
    battleStats: { sampleSize: 24, winRate: 62.5 },
  });
  await newer;
  requests[1].reject(new Error("stale matchup failure"));
  await assert.rejects(older, /stale matchup failure/);
  assert.equal(workbench.state.matchup.rightKey, "301.302.303");
  assert.equal(workbench.state.error, "");
  assert.equal(lifecycle.at(-1).kind, "success");

  const failed = workbench.refreshMatchup();
  requests[3].reject(new Error("matchup unavailable"));
  await assert.rejects(failed, /matchup unavailable/);
  assert.equal(workbench.state.matchup.rightKey, "301.302.303");
  assert.equal(lifecycle.at(-1).kind, "error");
  assert.equal(lifecycle.at(-1).replace, false);
  assert.equal(lifecycle.at(-1).busy, false);
  assert.equal(lifecycle.at(-1).actionLabel, "重试");

  const retry = lifecycle.at(-1).action();
  requests[4].resolve({
    ok: true,
    leftKey: "101.102.103",
    rightKey: "301.302.303",
    battleStats: { sampleSize: 0, winRate: 0 },
  });
  await retry;
  assert.equal(workbench.state.matchup.battleStats.sampleSize, 0);
  assert.equal(lifecycle.at(-1).kind, "empty");
  assert.equal(lifecycle.at(-1).replace, false);
  assert.equal(lifecycle.at(-1).busy, false);
});

test("refreshMatchup blocks first errors and invalidates pending requests when lineups reset", async () => {
  const requests = [];
  const lifecycle = [];
  const workbench = createHarness(
    () => {
      const request = deferred();
      requests.push(request);
      return request.promise;
    },
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );
  workbench.setLineup(completeLineup([101, 102, 103]));
  workbench.setOpponent(completeLineup([201, 202, 203]));

  const failed = workbench.refreshMatchup();
  requests[0].reject(new Error("first matchup failure"));
  await assert.rejects(failed, /first matchup failure/);
  assert.equal(lifecycle.at(-1).kind, "error");
  assert.equal(lifecycle.at(-1).replace, true);
  assert.equal(lifecycle.at(-1).busy, false);

  const stale = lifecycle.at(-1).action();
  workbench.setLineup(completeLineup([301, 302, 303]));
  requests[1].resolve({
    ok: true,
    leftKey: "101.102.103",
    rightKey: "201.202.203",
    battleStats: { sampleSize: 99, winRate: 99 },
  });
  assert.equal(await stale, null);
  assert.equal(workbench.state.matchup, null);
  assert.equal(workbench.state.error, "");
});

test("invalidating matchup without replacement releases its busy owner", async () => {
  const request = deferred();
  const lifecycle = [];
  const workbench = createHarness(
    () => request.promise,
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );
  workbench.setLineup(completeLineup([101, 102, 103]));
  workbench.setOpponent(completeLineup([201, 202, 203]));

  const stale = workbench.refreshMatchup();
  workbench.setOpponent(completeLineup([301, 302, 303]));
  request.resolve({
    ok: true,
    leftKey: "101.102.103",
    rightKey: "201.202.203",
    battleStats: { sampleSize: 12, winRate: 55 },
  });

  assert.equal(await stale, null);
  assert.equal(workbench.state.matchup, null);
  assert.equal(lifecycle.at(-1).surface, "matchup");
  assert.equal(lifecycle.at(-1).busy, false);
});

test("matchup cannot publish an error after lineup keys change directly", async () => {
  const request = deferred();
  const lifecycle = [];
  const workbench = createHarness(
    () => request.promise,
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );
  workbench.setLineup(completeLineup([101, 102, 103]));
  workbench.setOpponent(completeLineup([201, 202, 203]));

  const stale = workbench.refreshMatchup();
  workbench.state.opponent = completeLineup([301, 302, 303]);
  request.reject(new Error("stale matchup failure"));

  await assert.rejects(stale, /stale matchup failure/);
  assert.equal(workbench.state.error, "");
  assert.notEqual(lifecycle.at(-1).kind, "error");
  assert.equal(lifecycle.at(-1).busy, false);
});

test("refreshChain commits only the latest lineup across concurrent hero and skill loads", async () => {
  const pending = new Map();
  const lifecycle = [];
  const calls = [];
  const workbench = createHarness(
    (url) => {
      calls.push(url);
      const request = deferred();
      pending.set(url, request);
      return request.promise;
    },
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );
  const firstLineup = completeLineup([101, 102, 103]);
  firstLineup.heroes[0].equip_skills = [501, 0];
  workbench.setLineup(firstLineup);

  const older = workbench.refreshChain();
  assert.equal(lifecycle.at(-1).surface, "chain");
  assert.equal(lifecycle.at(-1).kind, "loading");
  assert.equal(lifecycle.at(-1).replace, true);
  assert.equal(lifecycle.at(-1).busy, true);

  const latestLineup = completeLineup([201, 202, 203]);
  latestLineup.heroes[0].equip_skills = [601, 0];
  workbench.setLineup(latestLineup);
  const newer = workbench.refreshChain();

  for (const heroId of [201, 202, 203]) {
    pending.get(`/api/intelligence/heroes/${heroId}`).resolve({
      ok: true,
      hero: { heroid: heroId, name: `latest-${heroId}` },
      initialSkill: { skill_id: heroId + 1000, name: `initial-${heroId}` },
    });
  }
  await waitFor(
    () => pending.has("/api/intelligence/skills/1201"),
    "latest lineup skill requests did not start",
  );
  for (const skillId of [1201, 1202, 1203, 601]) {
    pending.get(`/api/intelligence/skills/${skillId}`).resolve({
      ok: true,
      skill: { skill_id: skillId, name: `latest-skill-${skillId}` },
      details: [],
    });
  }
  await newer;
  assert.equal(
    workbench.state.chainNodes.every((node) =>
      !String(node.heroId || "").startsWith("10")
      && !String(node.skillId || "").startsWith("5")),
    true,
  );
  const latestNodes = workbench.state.chainNodes;
  assert.ok(latestNodes.length > 0);
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);

  pending.get("/api/intelligence/heroes/101").reject(
    new Error("stale chain failure"),
  );
  pending.get("/api/intelligence/heroes/102").resolve({
    ok: true,
    hero: { heroid: 102, name: "stale-102" },
    initialSkill: null,
  });
  pending.get("/api/intelligence/heroes/103").resolve({
    ok: true,
    hero: { heroid: 103, name: "stale-103" },
    initialSkill: null,
  });
  await assert.rejects(older, /stale chain failure/);
  assert.equal(workbench.state.chainNodes, latestNodes);
  assert.equal(workbench.state.error, "");
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(
    calls.filter((url) => url === "/api/intelligence/heroes/201").length,
    1,
  );
});

test("refreshChain reuses pending detail promises and preserves nodes on retryable errors", async () => {
  const lifecycle = [];
  const requests = new Map();
  const callCounts = new Map();
  const workbench = createHarness(
    (url) => {
      callCounts.set(url, (callCounts.get(url) || 0) + 1);
      if (!requests.has(url)) requests.set(url, deferred());
      return requests.get(url).promise;
    },
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );
  workbench.setLineup(completeLineup([101, 102, 103]));

  const older = workbench.refreshChain();
  const newer = workbench.refreshChain();
  assert.equal(callCounts.get("/api/intelligence/heroes/101"), 1);
  for (const heroId of [101, 102, 103]) {
    requests.get(`/api/intelligence/heroes/${heroId}`).resolve({
      ok: true,
      hero: { heroid: heroId, name: `hero-${heroId}` },
      initialSkill: { skill_id: heroId + 1000, name: `initial-${heroId}` },
    });
  }
  await waitFor(
    () => requests.has("/api/intelligence/skills/1101"),
    "shared chain skill requests did not start",
  );
  for (const skillId of [1101, 1102, 1103]) {
    requests.get(`/api/intelligence/skills/${skillId}`).resolve({
      ok: true,
      skill: { skill_id: skillId, name: `skill-${skillId}` },
      details: [],
    });
  }
  assert.equal(await older, null);
  await newer;
  const stableNodes = workbench.state.chainNodes;
  assert.ok(stableNodes.length > 0);

  workbench.state.skillDetails.delete(1101);
  requests.delete("/api/intelligence/skills/1101");
  const failedRequest = deferred();
  requests.set("/api/intelligence/skills/1101", failedRequest);
  const failed = workbench.refreshChain();
  assert.equal(lifecycle.at(-1).kind, "refreshing");
  assert.equal(lifecycle.at(-1).replace, false);
  assert.equal(workbench.state.chainNodes, stableNodes);
  failedRequest.reject(new Error("chain unavailable"));
  await assert.rejects(failed, /chain unavailable/);
  assert.equal(workbench.state.chainNodes, stableNodes);
  assert.equal(lifecycle.at(-1).kind, "error");
  assert.equal(lifecycle.at(-1).replace, false);
  assert.equal(lifecycle.at(-1).busy, false);
  assert.equal(lifecycle.at(-1).actionLabel, "重试");

  requests.delete("/api/intelligence/skills/1101");
  const recoveredRequest = deferred();
  requests.set("/api/intelligence/skills/1101", recoveredRequest);
  const retry = lifecycle.at(-1).action();
  recoveredRequest.resolve({
    ok: true,
    skill: { skill_id: 1101, name: "recovered skill" },
    details: [],
  });
  await retry;
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);
});

test("invalidating chain without replacement releases its busy owner", async () => {
  const request = deferred();
  const lifecycle = [];
  const workbench = createHarness(
    () => request.promise,
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );
  workbench.setLineup(completeLineup([101, 0, 0]));

  const stale = workbench.refreshChain();
  workbench.setLineup(completeLineup([201, 0, 0]));
  request.resolve({
    ok: true,
    hero: { heroid: 101, name: "stale chain hero" },
    initialSkill: null,
  });

  assert.equal(await stale, null);
  assert.deepEqual(workbench.state.chainNodes, []);
  assert.equal(lifecycle.at(-1).surface, "chain");
  assert.equal(lifecycle.at(-1).busy, false);
});

test("chain cannot publish an error after lineup keys change directly", async () => {
  const request = deferred();
  const lifecycle = [];
  const workbench = createHarness(
    () => request.promise,
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );
  workbench.setLineup(completeLineup([101, 0, 0]));

  const stale = workbench.refreshChain();
  workbench.state.lineup = completeLineup([201, 0, 0]);
  request.reject(new Error("stale chain failure"));

  await assert.rejects(stale, /stale chain failure/);
  assert.equal(workbench.state.error, "");
  assert.notEqual(lifecycle.at(-1).kind, "error");
  assert.equal(lifecycle.at(-1).busy, false);
});

test("refreshChain exposes blocking error and empty states", async () => {
  const lifecycle = [];
  let fail = true;
  const workbench = createHarness(
    async (url) => {
      if (fail) throw new Error("first chain failure");
      const id = Number(url.split("/").at(-1));
      if (url.includes("/heroes/")) {
        return {
          ok: true,
          hero: { heroid: id, name: `hero-${id}` },
          initialSkill: null,
        };
      }
      return { ok: true, skill: { skill_id: id, name: `skill-${id}` } };
    },
    {
      renderRequestState(surface, model) {
        lifecycle.push({ surface, ...model });
      },
    },
  );
  workbench.setLineup(completeLineup([101, 102, 103]));

  await assert.rejects(workbench.refreshChain(), /first chain failure/);
  assert.equal(lifecycle.at(-1).surface, "chain");
  assert.equal(lifecycle.at(-1).kind, "error");
  assert.equal(lifecycle.at(-1).replace, true);
  assert.equal(lifecycle.at(-1).busy, false);

  fail = false;
  workbench.setLineup(completeLineup([0, 0, 0]));
  await lifecycle.at(-1).action();
  assert.deepEqual(workbench.state.chainNodes, []);
  assert.equal(lifecycle.at(-1).kind, "empty");
  assert.equal(lifecycle.at(-1).replace, true);
  assert.equal(lifecycle.at(-1).busy, false);
});

test("research loader preserves results during refresh and cleans up errors", async () => {
  const target = {
    innerHTML: "stable rows",
    attributes: {},
    setAttribute(name, value) {
      this.attributes[name] = String(value);
    },
    removeAttribute(name) {
      delete this.attributes[name];
    },
  };
  const documentRef = {
    getElementById(id) {
      return id === "research-results" ? target : null;
    },
    querySelectorAll() {
      return [];
    },
    addEventListener() {},
  };
  const states = [];
  const refresh = deferred();
  let requestCount = 0;
  const workbench = createHarness(
    async () => {
      requestCount += 1;
      if (requestCount === 1) {
        return { ok: true, rows: [{ heroid: 101, name: "stable" }] };
      }
      return refresh.promise;
    },
    {
      documentRef,
      renderState(container, model) {
        states.push({ ...model, content: container.innerHTML });
        if (["loading", "refreshing"].includes(model.kind)) {
          container.setAttribute("aria-busy", "true");
        } else {
          container.removeAttribute("aria-busy");
        }
        return model;
      },
    },
  );

  await workbench.load();
  assert.equal(states.at(-1).kind, "loading");
  const preserved = target.innerHTML;
  const pending = workbench.search("new");
  assert.equal(states.at(-1).kind, "refreshing");
  assert.equal(states.at(-1).content, preserved);
  assert.equal(target.attributes["aria-busy"], "true");
  refresh.resolve(Promise.reject(new Error("network down")));
  await assert.rejects(pending, /network down/);

  assert.equal(workbench.state.loading, false);
  assert.equal(target.attributes["aria-busy"], undefined);
  assert.equal(states.at(-1).kind, "error");
  assert.equal(states.at(-1).replace, false);
  assert.equal(target.innerHTML, preserved);
});

test("simulation chain builder is injected without importing simulator analysis", async () => {
  const calls = [];
  const workbench = createHarness(
    async () => ({ ok: true, rows: [] }),
    {
      buildSimulationSkillChain: (detail) => {
        calls.push(detail);
        return [{ nodeId: "event:1", phase: "PREPARATION" }];
      },
    },
  );
  workbench.state.simulationByLineupKey.set(
    "101.102.103",
    { response: { result: { firstRun: { events: [] } } } },
  );
  workbench.setLineup(completeLineup([101, 102, 103]));

  await workbench.setMode("chain");

  assert.equal(calls.length, 1);
  assert.equal(workbench.getChainNodes()[0].nodeId, "event:1");
});

test("send to simulator preserves optional skills and research return context", async () => {
  const simulatorCalls = [];
  const windowRef = fakeWindow();
  windowRef.StzbSimulator = {
    async loadLineup(lineup, options) {
      simulatorCalls.push({ lineup, options });
    },
  };
  windowRef.switchTab = () => {};
  const workbench = createHarness(
    async () => ({ ok: true, rows: [] }),
    { windowRef },
  );
  const currentLineup = completeLineup([100027, 100016, 100090]);
  currentLineup.heroes[0].equip_skills = [200001, 200027];
  currentLineup.heroes[1].equip_skills = [200914, 0];
  workbench.setLineup(currentLineup);

  assert.equal(await workbench.sendToSimulator(), true);
  assert.deepEqual(simulatorCalls, [{
    lineup: currentLineup,
    options: {
      camp: "blue",
      source: "intelligence-research",
      lineupKey: "100027.100016.100090",
      returnTab: 34,
    },
  }]);
});

test("template workflow preserves positions skills and supports local CRUD", () => {
  const workbench = createHarness(async () => ({ ok: true, rows: [] }));
  const selected = completeLineup([100027, 100016, 100090]);
  selected.heroes[0].equip_skills = [200001, 200027];
  selected.heroes[1].equip_skills = [200914, 0];
  workbench.setLineup(selected);

  const saved = workbench.saveTemplate("魏骑实验");
  workbench.setLineup(completeLineup([101, 102, 103]));
  const loaded = workbench.loadTemplate(saved.id);

  assert.equal(loaded.name, "魏骑实验");
  assert.deepEqual(workbench.state.lineup, selected);
  assert.equal(workbench.renameTemplate(saved.id, "魏骑二版").name, "魏骑二版");
  assert.equal(
    JSON.parse(workbench.exportTemplate(saved.id)).name,
    "魏骑二版",
  );
  assert.equal(workbench.deleteTemplate(saved.id), true);
  assert.deepEqual(workbench.listTemplates(), []);
});

test("template import round trips and invalid import preserves current lineup", () => {
  const workbench = createHarness(async () => ({ ok: true, rows: [] }));
  const selected = completeLineup([100027, 100016, 100090]);
  selected.heroes[2].equip_skills = [200501, 200502];
  workbench.setLineup(selected);
  const saved = workbench.saveTemplate("导出测试");
  const exported = workbench.exportTemplate(saved.id);
  workbench.deleteTemplate(saved.id);

  const imported = workbench.importTemplate(exported);
  assert.equal(imported.name, "导出测试");
  assert.deepEqual(workbench.loadTemplate(imported.id).lineup, selected);

  const before = workbench.state.lineup;
  assert.throws(() => workbench.importTemplate("{broken"), /invalid template JSON/);
  assert.equal(workbench.state.lineup, before);
  assert.match(workbench.state.error, /invalid template JSON/);
});

test("simulation completion caches only matching research evidence and invokes callback", () => {
  const evidenceEvents = [];
  const workbench = createHarness(
    async () => ({ ok: true, rows: [] }),
    {
      onSimulationEvidence(detail) {
        evidenceEvents.push(detail.sourceContext.lineupKey);
      },
    },
  );
  workbench.setLineup(completeLineup([101, 102, 103]));
  const ignored = {
    detail: {
      sourceContext: { source: "external", lineupKey: "101.102.103" },
      response: { winRate: 80 },
    },
  };
  workbench.onSimulationCompleted(ignored);
  assert.equal(workbench.state.simulationByLineupKey.size, 0);
  assert.deepEqual(evidenceEvents, []);

  workbench.onSimulationCompleted({
    detail: {
      sourceContext: {
        source: "intelligence-research",
        lineupKey: "201.202.203",
      },
      response: { winRate: 70 },
    },
  });
  assert.equal(workbench.state.simulationByLineupKey.size, 0);
  assert.deepEqual(evidenceEvents, []);

  const detail = {
    sourceContext: {
      source: "intelligence-research",
      lineupKey: "101.102.103",
      returnTab: 34,
    },
    response: { winRate: 60 },
  };
  workbench.onSimulationCompleted({ detail });

  assert.equal(workbench.state.simulationByLineupKey.get("101.102.103"), detail);
  assert.equal(workbench.state.simulationReturnTab, 34);
  assert.deepEqual(evidenceEvents, ["101.102.103"]);
});

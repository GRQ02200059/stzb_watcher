import assert from "node:assert/strict";
import test from "node:test";

import {
  advancePortraitFallback,
  createSharedLoadStateRegistry,
  createSimulatorInitializer,
  createSimulationRequestSnapshot,
  createSimulatorState,
  createSourceContext,
  loadTemplateAt,
  simulationActionAffectsRunIdentity,
  simulationSourceContextAfterAction,
  parseTemplate,
  portraitPresentation,
  serializeTemplate,
  simulationCompletionEvent,
  simulatorReducer,
  shouldCommitSimulationRequest,
} from "../../static/simulator-workbench.js";

const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
};

const hero = (id, skillId) => ({
  id,
  position: 0,
  level: 40,
  up: 5,
  equip_skills: [skillId, 0],
});

const fixtureState = () =>
  createSimulatorState({
    attacker: {
      morale: 100,
      heroes: [hero(100027, 200101)],
    },
    defender: {
      morale: 95,
      heroes: [hero(100013, 200102)],
    },
    repeat: 100,
    seedMode: "fixed",
    seed: 20260810,
  });

test("swap sides preserves complete hero configuration", () => {
  const initial = fixtureState();

  const result = simulatorReducer(initial, { type: "swapSides" });

  assert.deepEqual(result.attacker, initial.defender);
  assert.deepEqual(result.defender, initial.attacker);
  assert.notEqual(result.attacker, initial.defender);
});

test("copy side clones nested skill arrays", () => {
  const initial = fixtureState();
  const result = simulatorReducer(initial, {
    type: "copySide",
    from: "attacker",
    to: "defender",
  });

  result.defender.heroes[0].equip_skills[0] = 999;

  assert.notEqual(initial.attacker.heroes[0].equip_skills[0], 999);
});

test("selecting a hero updates only the selected slot", () => {
  const state = createSimulatorState({
    attacker: {
      morale: 100,
      heroes: [
        { ...hero(100027, 200101), position: 0 },
        { ...hero(100016, 200102), position: 1 },
      ],
    },
    defender: fixtureState().defender,
  });

  const result = simulatorReducer(state, {
    type: "setHero",
    side: "attacker",
    position: 1,
    hero: { id: 100090, position: 1, level: 45, up: 5, equip_skills: [] },
  });

  assert.equal(result.attacker.heroes[1].id, 100090);
  assert.equal(result.attacker.heroes[0].id, 100027);
  assert.deepEqual(result.defender, state.defender);
});

test("template round trip preserves matchup", () => {
  const initial = fixtureState();
  const encoded = serializeTemplate(initial, "matchup", "魏骑对阵");
  const decoded = parseTemplate(JSON.stringify(encoded));

  assert.deepEqual(decoded.attacker, initial.attacker);
  assert.deepEqual(decoded.defender, initial.defender);
  assert.equal(decoded.name, "魏骑对阵");
  assert.equal(decoded.schemaVersion, 1);
});

test("template rejects unsupported schema and invalid ids", () => {
  assert.throws(
    () => parseTemplate('{"schemaVersion":2}'),
    /unsupported template schema/,
  );
  const encoded = serializeTemplate(fixtureState(), "matchup");
  encoded.attacker.heroes[0].id = 0;
  assert.throws(() => parseTemplate(JSON.stringify(encoded)), /invalid hero id/);
});

test("load lineup preserves compatibility aliases", () => {
  const initial = fixtureState();
  const result = simulatorReducer(initial, {
    type: "loadLineup",
    side: "defender",
    lineup: {
      morale: 88,
      heroes: [
        {
          heroId: 100649,
          position: 2,
          level: 42,
          advanceLevel: 4,
          extraSkillIds: [200501],
        },
      ],
    },
  });

  assert.equal(result.defender.morale, 88);
  assert.deepEqual(result.defender.heroes[0], {
    id: 100649,
    position: 2,
    level: 42,
    up: 4,
    equip_skills: [200501, 0],
  });
});

test("load lineup accepts the legacy array form", () => {
  const initial = fixtureState();

  const result = simulatorReducer(initial, {
    type: "loadLineup",
    side: "attacker",
    lineup: [
      {
        id: 100027,
        level: 45,
        up: 5,
        equip_skills: [200027],
      },
    ],
  });

  assert.equal(result.attacker.heroes[0].id, 100027);
  assert.equal(result.attacker.heroes[0].position, 0);
  assert.deepEqual(result.attacker.heroes[0].equip_skills, [200027, 0]);
});

test("portrait presentation keeps local CDN and placeholder sources", () => {
  const model = portraitPresentation(
    {
      id: 100027,
      name: "张辽",
      portraitUrl: "/static/hero-portraits/cards/100027.webp",
      portraitFallbackUrl: "https://cdn/card_medium_100027.jpg",
    },
    "eager",
  );

  assert.deepEqual(model, {
    src: "/static/hero-portraits/cards/100027.webp",
    fallbackSrc: "https://cdn/card_medium_100027.jpg",
    placeholderSrc: "/static/hero-portraits/placeholder.svg",
    alt: "张辽武将画像",
    loading: "eager",
  });
});

test("portrait fallback advances once through CDN placeholder and done", () => {
  const image = {
    src: "/static/hero-portraits/cards/100027.webp",
    dataset: {
      portraitStep: "local",
      fallbackSrc: "https://cdn/card_medium_100027.jpg",
      placeholderSrc: "/static/hero-portraits/placeholder.svg",
    },
  };

  assert.equal(advancePortraitFallback(image), "cdn");
  assert.equal(image.src, image.dataset.fallbackSrc);
  assert.equal(advancePortraitFallback(image), "placeholder");
  assert.equal(image.src, image.dataset.placeholderSrc);
  assert.equal(advancePortraitFallback(image), "done");
});

test("templates do not serialize portrait metadata", () => {
  const encoded = serializeTemplate(fixtureState(), "matchup");

  assert.equal(JSON.stringify(encoded).includes("portrait"), false);
});

test("source context preserves the research return tab", () => {
  assert.deepEqual(createSourceContext({
    source: "intelligence-research",
    lineupKey: "100027.100016.100090",
    camp: "blue",
    returnTab: 34,
  }), {
    source: "intelligence-research",
    lineupKey: "100027.100016.100090",
    camp: "blue",
    returnTab: 34,
  });
});

test("simulation completion event reports a successful manual run", () => {
  assert.deepEqual(simulationCompletionEvent({}, 100, null), {
    type: "simulation:completed",
    target: "#sim-result-summary",
    domain: "operations",
    severity: "success",
    value: 100,
    dedupeKey: "simulation:100:manual",
  });
});

test("simulation completion event warns about unsupported effects", () => {
  const response = {
    result: {
      replay: {
        diagnostics: {
          unsupportedSkillEffects: [{ skillId: 200101 }],
        },
      },
    },
  };

  assert.deepEqual(
    simulationCompletionEvent(response, 1, {
      lineupKey: "100027.100016.100090",
    }),
    {
      type: "simulation:completed",
      target: "#sim-result-summary",
      domain: "operations",
      severity: "warning",
      value: 1,
      dedupeKey: "simulation:1:100027.100016.100090",
    },
  );
});

test("simulation completion event warns for first-run diagnostics", () => {
  const response = {
    firstRun: {
      diagnostics: {
        unsupportedSkillEffects: [{ skillId: 200102 }],
      },
    },
  };

  assert.equal(
    simulationCompletionEvent(response, 1000, null).severity,
    "warning",
  );
});

test("simulation requests snapshot source context and reject stale revisions", () => {
  const sourceContext = {
    source: "intelligence-research",
    lineupKey: "100027.100016.100090",
    returnTab: 34,
  };
  const snapshot = createSimulationRequestSnapshot({
    revision: 7,
    stateRevision: 11,
    repeat: 100,
    payload: { repeat: 100, blue: { morale: 100 } },
    sourceContext,
  });
  sourceContext.lineupKey = "changed";

  assert.deepEqual(snapshot, {
    revision: 7,
    stateRevision: 11,
    repeat: 100,
    payload: { repeat: 100, blue: { morale: 100 } },
    sourceContext: {
      source: "intelligence-research",
      lineupKey: "100027.100016.100090",
      returnTab: 34,
    },
  });
  assert.equal(
    shouldCommitSimulationRequest(snapshot, {
      revision: 7,
      stateRevision: 11,
    }),
    true,
  );
  assert.equal(
    shouldCommitSimulationRequest(snapshot, {
      revision: 7,
      stateRevision: 12,
    }),
    false,
  );
  assert.equal(
    shouldCommitSimulationRequest(snapshot, {
      revision: 8,
      stateRevision: 11,
    }),
    false,
  );
});

test("simulation input actions invalidate run identity while view actions do not", () => {
  for (const type of [
    "setHero",
    "removeHero",
    "setHeroLevel",
    "setHeroAdvance",
    "setHeroSkill",
    "clearHeroSkill",
    "setMorale",
    "setRepeat",
    "setSeed",
    "swapSides",
    "copySide",
    "loadLineup",
    "loadTemplate",
    "reset",
  ]) {
    assert.equal(simulationActionAffectsRunIdentity({ type }), true, type);
  }
  for (const type of [
    "openDrawer",
    "closeDrawer",
    "setResult",
    "setResultView",
    "setActiveRound",
    "setEventFilters",
  ]) {
    assert.equal(simulationActionAffectsRunIdentity({ type }), false, type);
  }
});

test("manual simulation input clears external ownership while view actions preserve it", () => {
  const external = {
    source: "intelligence-research",
    lineupKey: "100027.100016.100090",
    camp: "blue",
    returnTab: 34,
  };
  for (const type of [
    "setHero",
    "removeHero",
    "setHeroLevel",
    "setHeroAdvance",
    "setHeroSkill",
    "clearHeroSkill",
    "setMorale",
    "setRepeat",
    "setSeed",
    "swapSides",
    "copySide",
    "loadLineup",
    "loadTemplate",
    "reset",
  ]) {
    assert.equal(
      simulationSourceContextAfterAction(external, { type }),
      null,
      type,
    );
  }
  for (const type of [
    "openDrawer",
    "closeDrawer",
    "setResult",
    "setResultView",
    "setActiveRound",
    "setEventFilters",
  ]) {
    assert.deepEqual(
      simulationSourceContextAfterAction(external, { type }),
      external,
      type,
    );
  }
});

test("shared simulator load state keeps busy priority and cross-owner terminal errors", () => {
  const registry = createSharedLoadStateRegistry();
  const simulationA = registry.acquire("simulation");
  const initializationA = registry.acquire("initialization");

  registry.update("simulation", simulationA, {
    kind: "loading",
    message: "running A",
    busy: true,
  });
  registry.update("initialization", initializationA, {
    kind: "refreshing",
    message: "refreshing catalog",
    busy: true,
  });
  assert.equal(registry.current().busy, true);

  registry.update("simulation", simulationA, {
    kind: "error",
    message: "run failed",
    busy: false,
    actionLabel: "重试",
  });
  assert.equal(registry.current().kind, "refreshing");

  registry.update("initialization", initializationA, {
    kind: "success",
    message: "",
    busy: false,
  });
  assert.equal(registry.current().kind, "error");
  assert.equal(registry.current().message, "run failed");

  const simulationB = registry.acquire("simulation");
  registry.update("simulation", simulationB, {
    kind: "loading",
    message: "running B",
    busy: true,
  });
  registry.update("simulation", simulationB, {
    kind: "success",
    message: "",
    busy: false,
  });
  assert.equal(registry.current().kind, "success");
  assert.equal(registry.isBusy("simulation"), false);
});

test("shared simulator load state releases only its own busy token", () => {
  const registry = createSharedLoadStateRegistry();
  const oldToken = registry.acquire("simulation");
  const currentToken = registry.acquire("simulation");
  registry.update("simulation", oldToken, { kind: "loading", busy: true });
  registry.update("simulation", currentToken, { kind: "loading", busy: true });

  registry.release("simulation", oldToken);
  assert.equal(registry.isBusy("simulation"), true);
  registry.release("simulation", currentToken);
  assert.equal(registry.isBusy("simulation"), false);
  assert.equal(registry.current().kind, "success");
});

test("deferred run A cannot commit or emit after input B changes run identity", async () => {
  const responses = [deferred(), deferred()];
  const current = {
    revision: 0,
    stateRevision: 0,
    sourceContext: { lineupKey: "A" },
  };
  const committed = [];
  const emitted = [];

  const run = async (response) => {
    const request = createSimulationRequestSnapshot({
      revision: ++current.revision,
      stateRevision: current.stateRevision,
      repeat: 100,
      payload: { repeat: 100, marker: current.sourceContext.lineupKey },
      sourceContext: current.sourceContext,
    });
    const result = await response.promise;
    if (!shouldCommitSimulationRequest(request, current)) return null;
    committed.push(result.marker);
    emitted.push(request.sourceContext.lineupKey);
    return result;
  };

  const stale = run(responses[0]);
  current.stateRevision += 1;
  current.sourceContext = { lineupKey: "B" };
  const latest = run(responses[1]);
  responses[0].resolve({ marker: "A-result" });
  assert.equal(await stale, null);
  assert.deepEqual(committed, []);
  assert.deepEqual(emitted, []);

  responses[1].resolve({ marker: "B-result" });
  assert.deepEqual(await latest, { marker: "B-result" });
  assert.deepEqual(committed, ["B-result"]);
  assert.deepEqual(emitted, ["B"]);
});

test("loadTemplateAt dispatches template input and invalidates a deferred run", async () => {
  const response = deferred();
  const template = serializeTemplate(
    createSimulatorState({
      attacker: {
        morale: 88,
        heroes: [hero(100649, 200501)],
      },
      defender: fixtureState().defender,
      repeat: 100,
      seedMode: "fixed",
      seed: 20260816,
    }),
    "matchup",
    "模板 B",
  );
  const runtime = {
    state: fixtureState(),
    stateRevision: 0,
    sourceContext: {
      source: "intelligence-research",
      lineupKey: "100027.100016.100090",
    },
  };
  const request = createSimulationRequestSnapshot({
    revision: 1,
    stateRevision: runtime.stateRevision,
    repeat: 100,
    payload: { marker: "run-A" },
    sourceContext: runtime.sourceContext,
  });
  const committed = [];
  const emitted = [];
  const run = response.promise.then((result) => {
    if (!shouldCommitSimulationRequest(request, {
      revision: 1,
      stateRevision: runtime.stateRevision,
    })) return null;
    committed.push(result);
    emitted.push(request.sourceContext);
    return result;
  });

  loadTemplateAt(0, {
    loadTemplates: () => [template],
    dispatch(action) {
      runtime.state = simulatorReducer(runtime.state, action);
      if (simulationActionAffectsRunIdentity(action)) {
        runtime.stateRevision += 1;
      }
    },
    closeDialogs() {},
    renderWorkbench() {},
  });
  response.resolve({ marker: "stale-run-A" });

  assert.equal(await run, null);
  assert.equal(runtime.state.attacker.heroes[0].id, template.attacker.heroes[0].id);
  assert.deepEqual(committed, []);
  assert.deepEqual(emitted, []);
});

test("simulator initializer reuses concurrent promises and distinguishes first load from refresh", async () => {
  let state = null;
  const requests = [];
  const lifecycle = [];
  const releases = [];
  const commits = [];
  const initializer = createSimulatorInitializer({
    hasState: () => state !== null,
    prepareState() {
      state = createSimulatorState();
    },
    getState: () => state,
    loadCatalog() {
      const request = deferred();
      requests.push({ type: "catalog", request });
      return request.promise;
    },
    loadEngine() {
      const request = deferred();
      requests.push({ type: "engine", request });
      return request.promise;
    },
    commit(payload) {
      commits.push(payload);
    },
    renderState(model) {
      lifecycle.push({ ...model });
    },
    releaseState(ownerToken) {
      releases.push(ownerToken);
    },
  });

  const first = initializer.initialize();
  const concurrent = initializer.initialize();
  assert.equal(first, concurrent);
  assert.deepEqual(lifecycle.at(-1), {
    kind: "loading",
    message: "正在初始化战斗模拟工作台…",
    replace: true,
    busy: true,
    actionLabel: "",
    action: undefined,
    ownerToken: 1,
  });
  requests[0].request.resolve({ heroes: [{ id: 101 }], skills: [] });
  requests[1].request.resolve({ name: "stzb-kotlin" });
  assert.equal(await first, state);
  assert.equal(commits.length, 1);
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);
  assert.deepEqual(releases, [1]);

  const refresh = initializer.initialize();
  assert.notEqual(refresh, first);
  assert.equal(lifecycle.at(-1).kind, "refreshing");
  assert.equal(lifecycle.at(-1).replace, false);
  assert.equal(lifecycle.at(-1).busy, true);
  requests[2].request.resolve({ heroes: [{ id: 202 }], skills: [] });
  requests[3].request.resolve({ name: "stzb-kotlin-next" });
  await refresh;
  assert.equal(commits.length, 2);
  assert.equal(commits[1].catalog.heroes[0].id, 202);
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);
  assert.deepEqual(releases, [1, 2]);
});

test("simulator initializer exposes actionable errors and can retry after rejection", async () => {
  let state = null;
  let attempt = 0;
  const lifecycle = [];
  const commits = [];
  const initializer = createSimulatorInitializer({
    hasState: () => state !== null,
    prepareState() {
      state = createSimulatorState();
    },
    getState: () => state,
    async loadCatalog() {
      attempt += 1;
      if (attempt === 1) throw new Error("catalog unavailable");
      return { heroes: [{ id: 303 }], skills: [] };
    },
    async loadEngine() {
      return { name: "stzb-kotlin" };
    },
    commit(payload) {
      commits.push(payload);
    },
    renderState(model) {
      lifecycle.push({ ...model });
    },
  });

  await assert.rejects(initializer.initialize(), /catalog unavailable/);
  assert.equal(lifecycle.at(-1).kind, "error");
  assert.equal(lifecycle.at(-1).replace, true);
  assert.equal(lifecycle.at(-1).busy, false);
  assert.equal(lifecycle.at(-1).actionLabel, "重试");
  assert.equal(typeof lifecycle.at(-1).action, "function");

  const retry = lifecycle.at(-1).action();
  assert.equal(
    lifecycle.filter((model) => model.kind).at(-1).kind,
    "loading",
  );
  await retry;
  assert.equal(attempt, 2);
  assert.equal(commits.length, 1);
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);
});

test("simulator initializer cannot commit or clear busy state from a generation invalidated by reset", async () => {
  let state = createSimulatorState({
    attacker: { heroes: [hero(101, 0)] },
  });
  const requests = [];
  const lifecycle = [];
  const releases = [];
  const commits = [];
  const initializer = createSimulatorInitializer({
    hasState: () => state !== null,
    prepareState() {
      state ||= createSimulatorState();
    },
    getState: () => state,
    loadCatalog() {
      const request = deferred();
      requests.push({ type: "catalog", request });
      return request.promise;
    },
    loadEngine() {
      const request = deferred();
      requests.push({ type: "engine", request });
      return request.promise;
    },
    commit(payload) {
      commits.push(payload);
    },
    renderState(model) {
      lifecycle.push({ ...model });
    },
    releaseState(ownerToken) {
      releases.push(ownerToken);
    },
  });

  const stale = initializer.initialize();
  initializer.invalidate();
  state = createSimulatorState({
    defender: { heroes: [hero(909, 0)] },
  });
  const current = initializer.initialize();
  assert.notEqual(stale, current);
  assert.equal(lifecycle.at(-1).kind, "refreshing");
  assert.equal(lifecycle.at(-1).busy, true);
  assert.deepEqual(releases, []);

  requests[0].request.resolve({ heroes: [{ id: 101 }], skills: [] });
  requests[1].request.resolve({ name: "stale-engine" });
  assert.equal(await stale, null);
  assert.equal(commits.length, 0);
  assert.equal(state.defender.heroes[0].id, 909);
  assert.equal(lifecycle.at(-1).kind, "refreshing");
  assert.equal(lifecycle.at(-1).busy, true);
  assert.deepEqual(releases, [1]);

  requests[2].request.resolve({ heroes: [{ id: 909 }], skills: [] });
  requests[3].request.resolve({ name: "current-engine" });
  assert.equal(await current, state);
  assert.equal(commits.length, 1);
  assert.equal(commits[0].engine.name, "current-engine");
  assert.equal(state.defender.heroes[0].id, 909);
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);
  assert.deepEqual(releases, [1, 3]);
});

import assert from "node:assert/strict";
import test from "node:test";

import {
  assertIntelligenceAggregateVersions,
  createIntelligenceDetailOwner,
  createIntelligenceLoaderCoordinator,
  createUnversionedSceneCompatibility,
  isIntelligenceContextCurrent,
  intelligenceResultState,
  requireIntelligenceResponse,
} from "../../static/intelligence-loader.mjs";

const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
};

const context = (rowUp, selectedWid = 0) => ({
  activeView: "map",
  bounds: {
    rowUp,
    rowDown: rowUp + 19,
    colLeft: 100,
    colRight: 119,
  },
  selectedWid,
});

test("initial aggregate context becomes stale when bounds or selected WID changes", () => {
  const snapshot = {
    ...context(0, 10001),
    initialized: false,
  };

  assert.equal(
    isIntelligenceContextCurrent(snapshot, {
      ...context(40, 40001),
      initialized: false,
    }),
    false,
  );
});

test("aggregate generations commit atomically and an older viewport cannot clear newer busy state", async () => {
  let currentContext = context(0, 10001);
  const batches = [];
  const commits = [];
  const lifecycle = [];
  const coordinator = createIntelligenceLoaderCoordinator({
    captureContext: () => structuredClone(currentContext),
    isContextCurrent: (snapshot) =>
      JSON.stringify(snapshot) === JSON.stringify(currentContext),
    hasContent: () => commits.length > 0,
    perform({ context: snapshot, signal }) {
      const requests = {
        summary: deferred(),
        viewport: deferred(),
        risks: deferred(),
        events: deferred(),
        detail: deferred(),
      };
      batches.push({ snapshot, signal, requests });
      return Promise.all([
        requests.summary.promise,
        requests.viewport.promise,
        requests.risks.promise,
        requests.events.promise,
        requests.detail.promise,
      ]).then(([summary, viewport, risks, events, detail]) => ({
        status: "success",
        snapshot,
        summary,
        viewport,
        risks,
        events,
        detail,
      }));
    },
    commit(result) {
      commits.push(result);
    },
    renderState(model) {
      lifecycle.push({ ...model });
    },
  });

  const older = coordinator.load();
  assert.equal(lifecycle.at(-1).kind, "loading");
  assert.equal(lifecycle.at(-1).busy, true);

  currentContext = context(40, 40001);
  const newer = coordinator.load();
  assert.equal(batches[0].signal.aborted, true);
  assert.equal(lifecycle.at(-1).ownerToken, 2);
  assert.equal(lifecycle.at(-1).busy, true);

  for (const [key, request] of Object.entries(batches[0].requests)) {
    request.resolve({ marker: `old-${key}` });
  }
  assert.equal(await older, null);
  assert.deepEqual(commits, []);
  assert.equal(lifecycle.at(-1).ownerToken, 2);
  assert.equal(lifecycle.at(-1).busy, true);

  for (const [key, request] of Object.entries(batches[1].requests)) {
    request.resolve({ marker: `new-${key}` });
  }
  const result = await newer;
  assert.equal(result.snapshot.bounds.rowUp, 40);
  assert.equal(commits.length, 1);
  assert.equal(commits[0].viewport.marker, "new-viewport");
  assert.equal(commits[0].risks.marker, "new-risks");
  assert.equal(commits[0].summary.marker, "new-summary");
  assert.equal(commits[0].detail.marker, "new-detail");
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);
  assert.equal(lifecycle.at(-1).ownerToken, 2);
});

test("version mismatch retries once in a new generation and commits only matching v8", async () => {
  const attempts = [];
  const commits = [];
  const lifecycle = [];
  const coordinator = createIntelligenceLoaderCoordinator({
    captureContext: () => context(0, 10001),
    isContextCurrent: () => true,
    hasContent: () => true,
    perform({ signal, generation }) {
      attempts.push({ signal, generation });
      const version = attempts.length === 1 ? 7 : 8;
      const memberVersion = 8;
      assertIntelligenceAggregateVersions(
        { worldStateVersion: version },
        [
          { name: "viewport", response: { worldStateVersion: memberVersion } },
          { name: "risks", response: { worldStateVersion: memberVersion } },
        ],
      );
      return {
        status: "success",
        summary: { worldStateVersion: version },
      };
    },
    commit(result) {
      commits.push(result);
    },
    renderState(model) {
      lifecycle.push({ ...model });
    },
  });

  const result = await coordinator.load();

  assert.equal(attempts.length, 2);
  assert.equal(attempts[0].signal.aborted, true);
  assert.notEqual(attempts[0].generation, attempts[1].generation);
  assert.equal(result.summary.worldStateVersion, 8);
  assert.deepEqual(
    commits.map((item) => item.summary.worldStateVersion),
    [8],
  );
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);
});

test("two version mismatches preserve the old map and end in retryable stale state", async () => {
  const stableMap = { version: 6 };
  let visibleMap = stableMap;
  let attempts = 0;
  const lifecycle = [];
  const coordinator = createIntelligenceLoaderCoordinator({
    captureContext: () => context(0, 10001),
    isContextCurrent: () => true,
    hasContent: () => true,
    perform() {
      attempts += 1;
      assertIntelligenceAggregateVersions(
        { worldStateVersion: 7 },
        [{
          name: "viewport",
          response: { worldStateVersion: 8 },
        }],
      );
      return { status: "success", map: { version: 7 } };
    },
    commit(result) {
      visibleMap = result.map;
    },
    renderState(model) {
      lifecycle.push({ ...model });
    },
  });

  assert.equal(await coordinator.load(), null);
  assert.equal(attempts, 2);
  assert.equal(visibleMap, stableMap);
  assert.equal(lifecycle.at(-1).kind, "stale");
  assert.equal(lifecycle.at(-1).replace, false);
  assert.equal(lifecycle.at(-1).busy, false);
  assert.match(lifecycle.at(-1).message, /版本不一致/);
  assert.equal(typeof lifecycle.at(-1).action, "function");
});

test("a missing core member version retries and commits only a versioned generation", async () => {
  let attempts = 0;
  const commits = [];
  const coordinator = createIntelligenceLoaderCoordinator({
    captureContext: () => context(0, 10001),
    isContextCurrent: () => true,
    hasContent: () => true,
    perform() {
      attempts += 1;
      const viewport = attempts === 1
        ? { tiles: [] }
        : { worldStateVersion: 8, tiles: [] };
      assertIntelligenceAggregateVersions(
        { worldStateVersion: 8 },
        [{ name: "viewport", response: viewport }],
      );
      return {
        status: "success",
        summary: { worldStateVersion: 8 },
      };
    },
    commit(result) {
      commits.push(result);
    },
  });

  const result = await coordinator.load();

  assert.equal(attempts, 2);
  assert.equal(result.summary.worldStateVersion, 8);
  assert.equal(commits.length, 1);
});

test("two missing core member versions preserve the old map as stale", async () => {
  const stableMap = { version: 7 };
  let visibleMap = stableMap;
  let attempts = 0;
  const lifecycle = [];
  const coordinator = createIntelligenceLoaderCoordinator({
    captureContext: () => context(0, 10001),
    isContextCurrent: () => true,
    hasContent: () => true,
    perform() {
      attempts += 1;
      assertIntelligenceAggregateVersions(
        { worldStateVersion: 8 },
        [{ name: "risks", response: { risks: [] } }],
      );
      return {
        status: "success",
        map: { version: 8 },
      };
    },
    commit(result) {
      visibleMap = result.map;
    },
    renderState(model) {
      lifecycle.push({ ...model });
    },
  });

  assert.equal(await coordinator.load(), null);
  assert.equal(attempts, 2);
  assert.equal(visibleMap, stableMap);
  assert.equal(lifecycle.at(-1).kind, "stale");
  assert.equal(lifecycle.at(-1).replace, false);
  assert.equal(lifecycle.at(-1).busy, false);
  assert.match(lifecycle.at(-1).message, /版本/);
});

test("scene rows use an explicit unversioned compatibility envelope", () => {
  const rows = [{ real_march_id: 7 }];
  const compatibility = createUnversionedSceneCompatibility("march", rows);

  assert.deepEqual(compatibility, {
    kind: "scene-compatibility",
    view: "march",
    versioned: false,
    rows,
  });
  assert.equal("worldStateVersion" in compatibility, false);
});

test("a failed aggregate member aborts the remaining same-generation work", async () => {
  const failed = deferred();
  let siblingAborted = false;
  let requestSignal;
  const coordinator = createIntelligenceLoaderCoordinator({
    captureContext: () => context(0),
    isContextCurrent: () => true,
    hasContent: () => true,
    perform({ signal }) {
      requestSignal = signal;
      const sibling = new Promise((resolve, reject) => {
        signal.addEventListener("abort", () => {
          siblingAborted = true;
          const error = new Error("aborted");
          error.name = "AbortError";
          reject(error);
        });
      });
      return Promise.all([failed.promise, sibling]);
    },
    commit() {},
    renderState() {},
  });

  const pending = coordinator.load();
  failed.reject(new Error("risks unavailable"));
  assert.equal(await pending, null);
  assert.equal(requestSignal.aborted, true);
  assert.equal(siblingAborted, true);
});

test("an ok-false aggregate member rejects early and aborts a deferred sibling", async () => {
  let siblingAborted = false;
  const coordinator = createIntelligenceLoaderCoordinator({
    captureContext: () => context(0),
    isContextCurrent: () => true,
    hasContent: () => true,
    perform({ signal }) {
      const failed = Promise.resolve({
        ok: false,
        error: "events unavailable",
      }).then((response) =>
        requireIntelligenceResponse(response, "events unavailable")
      );
      const sibling = new Promise((resolve, reject) => {
        signal.addEventListener("abort", () => {
          siblingAborted = true;
          const error = new Error("aborted");
          error.name = "AbortError";
          reject(error);
        });
      });
      return Promise.all([failed, sibling]);
    },
    commit() {},
    renderState() {},
  });

  assert.equal(await coordinator.load(), null);
  assert.equal(siblingAborted, true);
});

test("one failed aggregate request preserves the old map and exposes a retryable nonblocking error", async () => {
  const stableMap = { marker: "stable-map" };
  let visibleMap = stableMap;
  let attempt = 0;
  const retry = deferred();
  const lifecycle = [];
  const coordinator = createIntelligenceLoaderCoordinator({
    captureContext: () => context(0, 10001),
    isContextCurrent: () => true,
    hasContent: () => Boolean(visibleMap),
    async perform() {
      attempt += 1;
      if (attempt === 1) {
        await Promise.resolve();
        throw new Error("risks unavailable");
      }
      return retry.promise;
    },
    commit(result) {
      visibleMap = result.map;
    },
    renderState(model) {
      lifecycle.push({ ...model });
    },
  });

  assert.equal(await coordinator.load(), null);
  assert.equal(visibleMap, stableMap);
  assert.equal(lifecycle[0].kind, "refreshing");
  assert.equal(lifecycle[0].replace, false);
  assert.equal(lifecycle.at(-1).kind, "error");
  assert.equal(lifecycle.at(-1).replace, false);
  assert.equal(lifecycle.at(-1).busy, false);
  assert.equal(lifecycle.at(-1).actionLabel, "重试");
  assert.equal(typeof lifecycle.at(-1).action, "function");

  const retried = lifecycle.at(-1).action();
  assert.equal(lifecycle.at(-1).kind, "refreshing");
  retry.resolve({ status: "success", map: { marker: "retried-map" } });
  await retried;
  assert.deepEqual(visibleMap, { marker: "retried-map" });
  assert.equal(lifecycle.at(-1).kind, "success");
  assert.equal(lifecycle.at(-1).busy, false);
});

test("abort invalidation rejects a late result and releases only the aborted busy owner", async () => {
  const request = deferred();
  const lifecycle = [];
  const commits = [];
  let requestSignal;
  const coordinator = createIntelligenceLoaderCoordinator({
    captureContext: () => context(0),
    isContextCurrent: () => true,
    hasContent: () => true,
    perform({ signal }) {
      requestSignal = signal;
      return request.promise;
    },
    commit(result) {
      commits.push(result);
    },
    renderState(model) {
      lifecycle.push({ ...model });
    },
  });

  const pending = coordinator.load();
  const ownerToken = lifecycle.at(-1).ownerToken;
  coordinator.invalidate();
  assert.equal(requestSignal.aborted, true);
  assert.deepEqual(lifecycle.at(-1), {
    kind: "success",
    message: "",
    replace: false,
    busy: false,
    actionLabel: "",
    action: undefined,
    ownerToken,
  });
  request.resolve({ status: "success", marker: "late" });
  assert.equal(await pending, null);
  assert.deepEqual(commits, []);
  assert.equal(lifecycle.at(-1).ownerToken, ownerToken);
  assert.equal(lifecycle.at(-1).busy, false);
});

test("same-context refreshes share one request to bound SSE and pan request storms", async () => {
  const request = deferred();
  let calls = 0;
  const coordinator = createIntelligenceLoaderCoordinator({
    captureContext: () => context(0, 10001),
    isContextCurrent: () => true,
    hasContent: () => true,
    perform() {
      calls += 1;
      return request.promise;
    },
    commit() {},
    renderState() {},
  });

  const first = coordinator.load();
  const duplicate = coordinator.load();
  assert.equal(first, duplicate);
  assert.equal(calls, 1);
  request.resolve({ status: "success" });
  await first;
});

test("an explicit forced refresh replaces an in-flight request for the same context", async () => {
  const requests = [];
  const coordinator = createIntelligenceLoaderCoordinator({
    captureContext: () => context(0, 10001),
    isContextCurrent: () => true,
    hasContent: () => true,
    perform({ signal }) {
      const request = deferred();
      requests.push({ request, signal });
      return request.promise;
    },
    commit() {},
    renderState() {},
  });

  const older = coordinator.load();
  const newer = coordinator.load({ force: true });
  assert.notEqual(older, newer);
  assert.equal(requests.length, 2);
  assert.equal(requests[0].signal.aborted, true);
  requests[0].request.resolve({ status: "success", marker: "old" });
  requests[1].request.resolve({ status: "success", marker: "new" });
  assert.equal(await older, null);
  assert.equal((await newer).marker, "new");
});

test("standalone and aggregate detail requests share one latest owner in both directions", () => {
  const owners = createIntelligenceDetailOwner();
  const aborted = [];
  const standalone = owners.begin({
    source: "standalone",
    abort: () => aborted.push("standalone"),
  });
  const aggregate = owners.begin({
    source: "aggregate",
    abort: () => aborted.push("aggregate"),
  });

  assert.deepEqual(aborted, ["standalone"]);
  assert.equal(owners.finish(standalone), false);
  assert.equal(owners.isCurrent(aggregate), true);

  const newerStandalone = owners.begin({
    source: "standalone",
    abort: () => aborted.push("newer-standalone"),
  });
  assert.deepEqual(aborted, ["standalone", "aggregate"]);
  assert.equal(owners.finish(aggregate), false);
  assert.equal(owners.isCurrent(newerStandalone), true);
  assert.equal(owners.finish(newerStandalone), true);
  assert.equal(owners.current, null);
});

test("missing detail owners never match or finish the current owner", () => {
  const owners = createIntelligenceDetailOwner();
  assert.equal(owners.isCurrent(null), false);
  assert.equal(owners.finish(null), false);

  const standalone = owners.begin({ source: "standalone" });
  assert.equal(owners.isCurrent(null), false);
  assert.equal(owners.finish(null), false);
  assert.equal(owners.isCurrent(standalone), true);
});

test("first loading, actionable empty, stale truth and explicit partial states stay distinct", async () => {
  const retry = () => {};
  assert.deepEqual(
    intelligenceResultState(
      { status: "empty", message: "等待 5026 基线" },
      { hadContent: false, retry },
    ),
    {
      kind: "empty",
      message: "等待 5026 基线",
      replace: true,
      busy: false,
      actionLabel: "重试",
      action: retry,
    },
  );
  assert.deepEqual(
    intelligenceResultState(
      { status: "stale", message: "后端真值已陈旧" },
      { hadContent: true, retry },
    ),
    {
      kind: "stale",
      message: "后端真值已陈旧",
      replace: false,
      busy: false,
      actionLabel: "重试",
      action: retry,
    },
  );
  assert.deepEqual(
    intelligenceResultState(
      { status: "partial", message: "时间线暂不可用" },
      { hadContent: true, retry },
    ),
    {
      kind: "error",
      message: "时间线暂不可用",
      replace: false,
      busy: false,
      actionLabel: "重试",
      action: retry,
    },
  );
});

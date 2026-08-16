import assert from "node:assert/strict";
import test from "node:test";

import {
  boundsForArmies,
  buildArmyDrawPlan,
  drawLiveArmyMap,
  hitTestArmy,
  panBounds,
  widToPoint,
  zoomBounds,
} from "../../static/live-army-map.mjs";


function fixtureArmy(overrides = {}) {
  return {
    armyId: 1,
    stateKey: "expedition",
    stateLabel: "出征中",
    isMoving: true,
    location: {
      currentWid: 10004,
      nextWid: 10005,
      targetWid: 10009,
    },
    timing: {
      endTime: 1_900_000_060,
    },
    lineup: {
      status: "unknown",
      heroes: [],
    },
    offline: null,
    ...overrides,
  };
}


function fakeCanvas(width = 800, height = 560) {
  const calls = [];
  const context = new Proxy(
    {
      calls,
      setLineDash(values) {
        calls.push(["setLineDash", [...values]]);
      },
      measureText(text) {
        return { width: String(text).length * 6 };
      },
    },
    {
      get(target, property) {
        if (property in target) return target[property];
        if (typeof property === "symbol") return undefined;
        return (...args) => calls.push([property, ...args]);
      },
      set(target, property, value) {
        target[property] = value;
        calls.push(["set", property, value]);
        return true;
      },
    },
  );
  return {
    clientWidth: width,
    clientHeight: height,
    width,
    height,
    style: {},
    getContext() {
      return context;
    },
    context,
  };
}


test("WID converts to row and col", () => {
  assert.deepEqual(widToPoint(2081480), { row: 208, col: 1480 });
  assert.deepEqual(widToPoint(0), { row: 0, col: 0 });
});


test("bounds include current next and target locations", () => {
  const bounds = boundsForArmies([
    fixtureArmy({
      location: {
        currentWid: 2081480,
        nextWid: 2081481,
        targetWid: 1151300,
      },
    }),
  ]);

  assert.ok(bounds.rowUp <= 115);
  assert.ok(bounds.rowDown >= 208);
  assert.ok(bounds.colLeft <= 1300);
  assert.ok(bounds.colRight >= 1481);
});


test("draw plan preserves shape route and selection semantics", () => {
  const plan = buildArmyDrawPlan(
    [
      fixtureArmy({ armyId: 1, stateKey: "returning" }),
      fixtureArmy({
        armyId: 2,
        stateKey: "reside",
        isMoving: false,
        location: {
          currentWid: 10006,
          nextWid: 0,
          targetWid: 10006,
        },
      }),
      fixtureArmy({
        armyId: 3,
        stateKey: "unknown",
        isMoving: false,
        offline: { deletedAtMs: 1 },
        location: {
          currentWid: 10007,
          nextWid: 0,
          targetWid: 10008,
        },
      }),
    ],
    1,
    { rowUp: 1, rowDown: 300, colLeft: 1, colRight: 2000 },
    { width: 800, height: 560 },
  );

  assert.equal(plan.markers[0].shape, "return");
  assert.equal(plan.markers[0].selected, true);
  assert.equal(plan.markers[1].shape, "shield");
  assert.equal(plan.markers[2].shape, "diamond");
  assert.equal(plan.markers[2].offline, true);
  assert.equal(plan.routes[0].kind, "complete");
  assert.equal(plan.routes[1].kind, "stationary");
  assert.equal(plan.routes[2].kind, "offline");
});


test("stale current army is warning evidence instead of live state", () => {
  const plan = buildArmyDrawPlan(
    [
      fixtureArmy({
        armyId: 99,
        source: {
          observedAtMs: 1_000,
          freshness: "stale",
          isStale: true,
        },
      }),
    ],
    99,
    { rowUp: 1, rowDown: 2, colLeft: 1, colRight: 20 },
    { width: 800, height: 560 },
  );

  assert.equal(plan.markers[0].stale, true);
  assert.equal(plan.markers[0].shape, "diamond");
  assert.equal(plan.markers[0].color, "#f5b84b");
  assert.equal(plan.routes[0].kind, "stale");
});


test("missing next WID creates an incomplete route", () => {
  const plan = buildArmyDrawPlan(
    [
      fixtureArmy({
        location: {
          currentWid: 10004,
          nextWid: 0,
          targetWid: 10009,
        },
      }),
    ],
    0,
    { rowUp: 1, rowDown: 2, colLeft: 1, colRight: 20 },
    { width: 800, height: 560 },
  );

  assert.equal(plan.routes[0].kind, "incomplete");
  assert.equal(plan.routes[0].points.length, 2);
});


test("hit test returns exact marker army id", () => {
  const plan = {
    markers: [
      { armyId: 77, x: 100, y: 80, hitRadius: 14 },
      { armyId: 88, x: 200, y: 180, hitRadius: 14 },
    ],
  };
  assert.equal(hitTestArmy(105, 84, plan), 77);
  assert.equal(hitTestArmy(204, 184, plan), 88);
  assert.equal(hitTestArmy(140, 84, plan), 0);
});


test("pan and zoom keep positive deterministic bounds", () => {
  assert.deepEqual(
    panBounds(
      { rowUp: 10, rowDown: 29, colLeft: 20, colRight: 39 },
      -50,
      -50,
    ),
    { rowUp: 0, rowDown: 19, colLeft: 0, colRight: 19 },
  );
  const zoomed = zoomBounds(
    { rowUp: 10, rowDown: 29, colLeft: 20, colRight: 39 },
    20,
    30,
    -1,
  );
  assert.ok(zoomed.rowDown - zoomed.rowUp < 19);
  assert.ok(zoomed.colRight - zoomed.colLeft < 19);
});


test("drawing uses dashed incomplete and offline routes with selected ring", () => {
  const canvas = fakeCanvas();
  const plan = buildArmyDrawPlan(
    [
      fixtureArmy({
        armyId: 1,
        location: {
          currentWid: 10004,
          nextWid: 0,
          targetWid: 10009,
        },
      }),
      fixtureArmy({
        armyId: 2,
        stateKey: "unknown",
        offline: { deletedAtMs: 1 },
        location: {
          currentWid: 10005,
          nextWid: 0,
          targetWid: 10008,
        },
      }),
    ],
    1,
    { rowUp: 1, rowDown: 2, colLeft: 1, colRight: 20 },
    { width: 800, height: 560 },
  );

  drawLiveArmyMap(canvas, plan, {
    devicePixelRatio: 1,
    reducedMotion: true,
  });

  const dashCalls = canvas.context.calls.filter(
    ([name]) => name === "setLineDash",
  );
  assert.ok(dashCalls.some(([, values]) => values.length > 0));
  assert.ok(
    canvas.context.calls.some(
      ([name, text]) => name === "fillText" && String(text).includes("1"),
    ),
  );
  assert.ok(
    canvas.context.calls.filter(([name]) => name === "arc").length >= 1,
  );
  assert.ok(
    canvas.context.calls.filter(([name]) => name === "lineTo").length >= 4,
  );
});

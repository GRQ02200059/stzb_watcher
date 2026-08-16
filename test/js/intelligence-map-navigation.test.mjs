import assert from "node:assert/strict";
import test from "node:test";

import {
  createViewportHistory,
  interpolateBounds,
  isBoundsUseful,
  parseMapState,
  serializeMapState,
} from "../../static/intelligence-map-navigation.mjs";

const state = (rowUp, selectedWid = 0) => ({
  bounds: { rowUp, rowDown: rowUp + 19, colLeft: 100, colRight: 119 },
  selectedWid,
  layers: { ownership: true, freshness: true, paths: false, armies: true },
});

test("viewport history supports branches limits and duplicates", () => {
  const history = createViewportHistory(state(0), 3);
  history.push(state(10));
  history.push(state(20));
  history.push(state(20));
  assert.equal(history.current().bounds.rowUp, 20);
  assert.equal(history.back().bounds.rowUp, 10);
  history.push(state(30));
  assert.equal(history.canForward(), false);
  history.push(state(40));
  assert.equal(history.back().bounds.rowUp, 30);
  assert.equal(history.back().bounds.rowUp, 10);
  assert.equal(history.canBack(), false);
});

test("map state serializes safely and invalid state falls back", () => {
  const original = state(12, 2241486);
  const encoded = serializeMapState(original);
  assert.deepEqual(parseMapState(encoded, state(0)), original);
  assert.deepEqual(parseMapState("#map=bad", state(0)), state(0));
  assert.equal(
    isBoundsUseful(
      { rowUp: 200, rowDown: 219, colLeft: 1470, colRight: 1489 },
      { rowUp: 62, rowDown: 224, colLeft: 1313, colRight: 1488 },
    ),
    true,
  );
  assert.equal(
    isBoundsUseful(
      { rowUp: 1, rowDown: 20, colLeft: 1, colRight: 20 },
      { rowUp: 62, rowDown: 224, colLeft: 1313, colRight: 1488 },
    ),
    false,
  );
});

test("bounds interpolation is deterministic", () => {
  const from = { rowUp: 0, rowDown: 19, colLeft: 0, colRight: 19 };
  const to = { rowUp: 20, rowDown: 39, colLeft: 40, colRight: 59 };
  assert.deepEqual(interpolateBounds(from, to, 0), from);
  assert.deepEqual(interpolateBounds(from, to, 0.5), {
    rowUp: 10, rowDown: 29, colLeft: 20, colRight: 39,
  });
  assert.deepEqual(interpolateBounds(from, to, 1), to);
});

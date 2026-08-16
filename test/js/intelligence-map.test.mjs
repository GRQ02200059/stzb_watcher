import assert from "node:assert/strict";
import test from "node:test";

import {
  LEVEL_COLORS,
  levelColor,
  panBounds,
  tileDrawPlan,
  zoomBounds,
} from "../../static/intelligence-map.mjs";

test("land level colors match the intelligence rules", () => {
  assert.equal(LEVEL_COLORS[0], "#18232d");
  assert.equal(LEVEL_COLORS[7], "#e87e25");
  assert.equal(LEVEL_COLORS[9], "#ff174f");
  assert.equal(levelColor(-1), "#18232d");
  assert.equal(levelColor(10), "#18232d");
});

test("draw plan preserves tactical overlay order", () => {
  const plan = tileDrawPlan(
    {
      landLevel: 8,
      relation: "enemy",
      freshness: "stale",
      isPath: true,
      armies: 2,
      selected: true,
    },
    16,
    {
      ownership: true,
      freshness: true,
      paths: true,
      armies: true,
    },
  );
  assert.deepEqual(
    plan.map((item) => item.kind),
    ["level", "high-level", "ownership", "stale", "path", "armies", "selected", "label"],
  );
});

test("bounds pan and zoom stay deterministic", () => {
  assert.deepEqual(
    panBounds(
      { rowUp: 10, rowDown: 19, colLeft: 20, colRight: 29 },
      2,
      -3,
    ),
    { rowUp: 12, rowDown: 21, colLeft: 17, colRight: 26 },
  );
  assert.deepEqual(
    zoomBounds(
      { rowUp: 1, rowDown: 20, colLeft: 1, colRight: 20 },
      10,
      10,
      -1,
    ),
    { rowUp: 5, rowDown: 14, colLeft: 5, colRight: 14 },
  );
  assert.deepEqual(
    zoomBounds(
      { rowUp: 5, rowDown: 14, colLeft: 5, colRight: 14 },
      10,
      10,
      1,
    ),
    { rowUp: 0, rowDown: 19, colLeft: 0, colRight: 19 },
  );
});

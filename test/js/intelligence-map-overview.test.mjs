import assert from "node:assert/strict";
import test from "node:test";

import {
  bucketGridForBounds,
  hitTestBucket,
  radarToWorld,
  radarViewportRect,
  semanticMapMode,
  worldToRadar,
} from "../../static/intelligence-map-overview.mjs";

test("semantic zoom selects far middle and near modes", () => {
  assert.equal(
    semanticMapMode(
      { rowUp: 0, rowDown: 160, colLeft: 0, colRight: 160 },
      900,
      560,
    ),
    "far",
  );
  assert.equal(
    semanticMapMode(
      { rowUp: 0, rowDown: 80, colLeft: 0, colRight: 80 },
      900,
      560,
    ),
    "middle",
  );
  assert.equal(
    semanticMapMode(
      { rowUp: 0, rowDown: 20, colLeft: 0, colRight: 20 },
      900,
      560,
    ),
    "near",
  );
  assert.equal(
    semanticMapMode(
      { rowUp: 0, rowDown: 40, colLeft: 0, colRight: 40 },
      100,
      100,
    ),
    "far",
  );
});

test("bucket grid never exceeds the configured maximum", () => {
  const grid = bucketGridForBounds(
    { rowUp: 0, rowDown: 999, colLeft: 0, colRight: 999 },
    1000,
    700,
    2500,
  );
  assert.ok(grid.gridRows * grid.gridCols <= 2500);
  assert.ok(grid.bucketRows > 0);
  assert.ok(grid.bucketCols > 0);
});

test("radar coordinates round trip and viewport stays clipped", () => {
  const dataBounds = { rowUp: 62, rowDown: 224, colLeft: 1313, colRight: 1488 };
  const radarRect = { x: 10, y: 20, width: 176, height: 122 };
  for (const point of [
    { row: 62, col: 1313 },
    { row: 143, col: 1400 },
    { row: 224, col: 1488 },
  ]) {
    const radar = worldToRadar(point.row, point.col, dataBounds, radarRect);
    const world = radarToWorld(radar.x, radar.y, dataBounds, radarRect);
    assert.ok(Math.abs(world.row - point.row) <= 1);
    assert.ok(Math.abs(world.col - point.col) <= 1);
  }
  const viewport = radarViewportRect(
    { rowUp: 200, rowDown: 260, colLeft: 1470, colRight: 1530 },
    dataBounds,
    radarRect,
  );
  assert.ok(viewport.x >= radarRect.x);
  assert.ok(viewport.y >= radarRect.y);
  assert.ok(viewport.x + viewport.width <= radarRect.x + radarRect.width);
  assert.ok(viewport.y + viewport.height <= radarRect.y + radarRect.height);
});

test("bucket hit testing returns the exact bucket", () => {
  const first = { rowUp: 0, rowDown: 19, colLeft: 0, colRight: 19 };
  const second = { rowUp: 0, rowDown: 19, colLeft: 20, colRight: 39 };
  const state = {
    hitAreas: [
      { x: 10, y: 10, width: 30, height: 30, bucket: first },
      { x: 50, y: 10, width: 30, height: 30, bucket: second },
    ],
  };
  assert.equal(hitTestBucket(20, 20, state), first);
  assert.equal(hitTestBucket(60, 20, state), second);
  assert.equal(hitTestBucket(100, 100, state), null);
});

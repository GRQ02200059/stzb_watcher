import assert from "node:assert/strict";
import test from "node:test";

import {
  backoffDelay,
  createVisibilityTicker,
  escapeHtml,
  parseJsonResponse,
  normalizeTimestamp,
  normalizeEventText,
} from "../../static/dashboard-runtime.mjs";

test("backoff grows exponentially and caps at 30 seconds", () => {
  assert.deepEqual(
    [0, 1, 2, 3, 4, 5, 6].map((attempt) => backoffDelay(attempt)),
    [1000, 2000, 4000, 8000, 16000, 30000, 30000],
  );
});

test("visibility ticker only invokes active work while visible", () => {
  let callback;
  let ticks = 0;
  const documentRef = { visibilityState: "hidden" };
  const ticker = createVisibilityTicker({
    documentRef,
    intervalMs: 5000,
    setIntervalFn(fn, delay) {
      callback = fn;
      assert.equal(delay, 5000);
      return 99;
    },
    clearIntervalFn() {},
    onVisibleTick() {
      ticks += 1;
    },
  });

  assert.equal(ticker.start(), 99);
  callback();
  assert.equal(ticks, 0);
  documentRef.visibilityState = "visible";
  callback();
  assert.equal(ticks, 1);
});

test("event fallback escapes markup before insertion", () => {
  assert.equal(
    normalizeEventText({ message: "</div><script>alert(1)</script>" }),
    '{&quot;message&quot;:&quot;&lt;/div&gt;&lt;script&gt;alert(1)&lt;/script&gt;&quot;}',
  );
  assert.equal(escapeHtml(`"'&<>`), "&quot;&#39;&amp;&lt;&gt;");
});

test("event timestamps accept unix, ISO and HH:MM:SS values", () => {
  const now = new Date(2026, 7, 14, 20, 30, 0).getTime() / 1000;
  assert.equal(normalizeTimestamp(1_786_711_800, now), 1_786_711_800);
  assert.equal(normalizeTimestamp("1786711800", now), 1_786_711_800);
  assert.equal(
    normalizeTimestamp("2026-08-14T20:15:00+08:00", now),
    new Date("2026-08-14T20:15:00+08:00").getTime() / 1000,
  );
  assert.equal(
    normalizeTimestamp("20:15:00", now),
    new Date(2026, 7, 14, 20, 15, 0).getTime() / 1000,
  );
  assert.equal(normalizeTimestamp("not-a-time", now), now);
});

test("HTML API response becomes an actionable backend restart error", async () => {
  const response = {
    ok: false,
    status: 404,
    headers: { get: () => "text/html; charset=utf-8" },
    text: async () => "<!doctype html><title>404 Not Found</title>",
  };
  await assert.rejects(
    () => parseJsonResponse(response, "/api/command-center/overview"),
    /后端接口不可用.*重新启动后端/,
  );
});

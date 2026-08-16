import assert from "node:assert/strict";
import test from "node:test";

import {
  createHudSystem,
  domainForTab,
  normalizeMotionLevel,
} from "../../static/dashboard-hud.mjs";

function fakeElement() {
  return {
    attributes: {},
    children: [],
    dataset: {},
    classList: {
      values: new Set(),
      add(value) {
        this.values.add(value);
      },
      remove(value) {
        this.values.delete(value);
      },
      contains(value) {
        return this.values.has(value);
      },
    },
    textContent: "",
    setAttribute(name, value) {
      this.attributes ||= {};
      this.attributes[name] = String(value);
    },
    removeAttribute(name) {
      delete this.attributes[name];
    },
    addEventListener(type, callback) {
      this.listeners ||= {};
      this.listeners[type] = callback;
    },
    append(...children) {
      for (const child of children) {
        child.parentElement = this;
        this.children.push(child);
      }
    },
    replaceChildren(...children) {
      this.children = children;
      for (const child of children) child.parentElement = this;
    },
    remove() {
      this.removed = true;
      if (this.parentElement) {
        this.parentElement.children = this.parentElement.children.filter(
          (child) => child !== this,
        );
      }
    },
    querySelector() {
      return null;
    },
  };
}

test("visible tabs map to the five approved visual domains", () => {
  assert.deepEqual(
    [7, 8, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35].map(domainForTab),
    [
      "organization",
      "analysis",
      "operations",
      "organization",
      "analysis",
      "organization",
      "operations",
      "intelligence",
      "system",
      "intelligence",
      "analysis",
      "intelligence",
    ],
  );
  assert.equal(domainForTab(31), "compatibility");
});

test("reduced motion always wins and invalid settings use standard", () => {
  assert.equal(normalizeMotionLevel("full", true), "reduced");
  assert.equal(normalizeMotionLevel("full", false), "full");
  assert.equal(normalizeMotionLevel("standard", false), "standard");
  assert.equal(normalizeMotionLevel("bad", false), "standard");
});

test("setDomain updates one body seam without business knowledge", () => {
  const body = fakeElement();
  const system = createHudSystem({
    documentRef: { body },
    matchMediaFn: () => ({ matches: false }),
  });

  assert.equal(system.setDomain(25), "operations");
  assert.equal(body.dataset.visualDomain, "operations");
});

test("setMotionLevel writes the normalized body seam", () => {
  const body = fakeElement();
  const system = createHudSystem({
    documentRef: { body },
    matchMediaFn: () => ({ matches: false }),
  });

  assert.equal(system.setMotionLevel("full"), "full");
  assert.equal(body.dataset.motionLevel, "full");
  assert.equal(system.setMotionLevel("bad"), "standard");
  assert.equal(body.dataset.motionLevel, "standard");
});

test("pulse is one-shot and resets the same kind", () => {
  const element = fakeElement();
  const scheduled = [];
  const system = createHudSystem({
    documentRef: { body: fakeElement() },
    matchMediaFn: () => ({ matches: false }),
    setTimeoutFn(callback) {
      scheduled.push(callback);
      return scheduled.length;
    },
  });

  system.pulse(element, "danger");
  assert.equal(element.classList.contains("hud-pulse-danger"), true);
  scheduled.shift()();
  assert.equal(element.classList.contains("hud-pulse-danger"), false);
});

test("reduced motion suppresses pulses", () => {
  const element = fakeElement();
  const system = createHudSystem({
    documentRef: { body: fakeElement() },
    matchMediaFn: () => ({ matches: true }),
  });

  assert.equal(system.pulse(element, "danger"), false);
  assert.equal(element.classList.values.size, 0);
});

test("animateValue bounds invalid durations to a finite animation", async () => {
  for (const duration of [Infinity, Number.NaN, 0, -10]) {
    const element = fakeElement();
    const frames = [];
    const system = createHudSystem({
      documentRef: { body: fakeElement() },
      matchMediaFn: () => ({ matches: false }),
      performanceNow: () => 0,
      requestAnimationFrameFn(callback) {
        frames.push(callback);
      },
    });

    const completed = system.animateValue(element, 0, 10, {
      duration,
      formatter: String,
    });
    let frameTime = 360;
    let frameCount = 0;
    while (frames.length && frameCount < 3) {
      frames.shift()(frameTime);
      frameTime += 360;
      frameCount += 1;
    }

    assert.ok(frameCount <= 2, `duration ${duration} exceeded frame bound`);
    assert.equal(frames.length, 0, `duration ${duration} kept scheduling`);
    assert.equal(await completed, 10);
    assert.equal(element.textContent, "10");
  }
});

test("animateValue accounts current and peak RAF animations", async () => {
  const frames = [];
  const system = createHudSystem({
    documentRef: { body: fakeElement() },
    matchMediaFn: () => ({ matches: false }),
    performanceNow: () => 0,
    requestAnimationFrameFn(callback) {
      frames.push(callback);
    },
  });

  const animations = [1, 2, 3].map((value) =>
    system.animateValue(fakeElement(), 0, value, {
      duration: 360,
      formatter: String,
    }),
  );
  assert.deepEqual(system.getAnimationStats(), {
    activeValueAnimations: 3,
    peakValueAnimations: 3,
  });

  while (frames.length) frames.shift()(360);
  await Promise.all(animations);
  assert.deepEqual(system.getAnimationStats(), {
    activeValueAnimations: 0,
    peakValueAnimations: 3,
  });
  system.resetAnimationStats();
  assert.deepEqual(system.getAnimationStats(), {
    activeValueAnimations: 0,
    peakValueAnimations: 0,
  });
});

test("animateValue enforces the six-animation runtime budget", async () => {
  const frames = [];
  const system = createHudSystem({
    documentRef: { body: fakeElement() },
    matchMediaFn: () => ({ matches: false }),
    performanceNow: () => 0,
    requestAnimationFrameFn(callback) {
      frames.push(callback);
    },
  });
  const elements = Array.from({ length: 7 }, () => fakeElement());
  const animations = elements.map((element, index) =>
    system.animateValue(element, 0, index + 1, {
      duration: 360,
      formatter: String,
    }),
  );

  assert.deepEqual(system.getAnimationStats(), {
    activeValueAnimations: 6,
    peakValueAnimations: 6,
  });
  assert.equal(elements[6].textContent, "7");
  assert.equal(frames.length, 6);

  while (frames.length) frames.shift()(360);
  await Promise.all(animations);
  assert.equal(system.getAnimationStats().activeValueAnimations, 0);
});

test("renderState supports the complete page state model", () => {
  const container = fakeElement();
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      createElement(tag) {
        return { ...fakeElement(), tagName: tag };
      },
    },
    matchMediaFn: () => ({ matches: false }),
  });

  const kinds = [
    "idle",
    "loading",
    "refreshing",
    "success",
    "empty",
    "stale",
    "warning",
    "error",
  ];
  for (const kind of kinds) {
    assert.equal(system.renderState(container, { kind }).kind, kind);
  }
  assert.equal(
    system.renderState(container, { kind: "invalid" }).kind,
    "empty",
  );
});

test("renderState preserves usable content for non-blocking states", () => {
  const existing = fakeElement();
  const container = fakeElement();
  container.append(existing);
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      createElement(tag) {
        return { ...fakeElement(), tagName: tag };
      },
    },
    matchMediaFn: () => ({ matches: false }),
  });

  for (const kind of ["refreshing", "stale", "warning"]) {
    system.renderState(container, { kind });
    assert.equal(container.children[0], existing);
    assert.equal(
      container.children.at(-1).className,
      `hud-state hud-state-${kind}`,
    );
  }
  system.renderState(container, { kind: "error", replace: false });
  assert.equal(container.children[0], existing);

  system.renderState(container, { kind: "error", replace: true });
  assert.equal(container.children.length, 1);
  assert.equal(
    container.children[0].className,
    "hud-state hud-state-error",
  );
});

test("renderState marks only loading states as busy", () => {
  const container = fakeElement();
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      createElement(tag) {
        return { ...fakeElement(), tagName: tag };
      },
    },
    matchMediaFn: () => ({ matches: false }),
  });

  system.renderState(container, { kind: "loading" });
  assert.equal(container.attributes["aria-busy"], "true");
  system.renderState(container, { kind: "success" });
  assert.equal(container.attributes["aria-busy"], undefined);
  system.renderState(container, { kind: "refreshing" });
  assert.equal(container.attributes["aria-busy"], "true");
});

test("renderState binds an optional safe action without inline handlers", () => {
  const container = fakeElement();
  let calls = 0;
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      createElement(tag) {
        return { ...fakeElement(), tagName: tag };
      },
    },
    matchMediaFn: () => ({ matches: false }),
  });

  system.renderState(container, {
    kind: "error",
    message: "加载失败",
    actionLabel: "重试",
    action() {
      calls += 1;
    },
  });

  const action = container.children[0].children[0];
  assert.equal(action.tagName, "button");
  assert.equal(action.textContent, "重试");
  assert.equal(action.attributes.onclick, undefined);
  action.listeners.click();
  assert.equal(calls, 1);
});

test("emit deduplicates active events and clears one-shot classes", () => {
  const target = fakeElement();
  const scheduled = [];
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "visible",
      querySelector(selector) {
        return selector === "#risk" ? target : null;
      },
      createElement() {
        return fakeElement();
      },
      getElementById() {
        return null;
      },
    },
    matchMediaFn: () => ({ matches: false }),
    setTimeoutFn(callback) {
      scheduled.push(callback);
      return scheduled.length;
    },
    nowFn: () => 1000,
  });

  assert.equal(
    system.emit({
      type: "intelligence:risk-detected",
      target: "#risk",
      severity: "critical",
      dedupeKey: "wid:10004",
    }),
    true,
  );
  assert.equal(
    system.emit({
      type: "intelligence:risk-detected",
      target: "#risk",
      severity: "critical",
      dedupeKey: "wid:10004",
    }),
    false,
  );
  assert.equal(target.classList.contains("hud-event-critical"), true);
  assert.equal(
    target.classList.contains(
      "hud-event-intelligence-risk-detected",
    ),
    true,
  );
  scheduled.at(-1)();
  assert.equal(target.classList.contains("hud-event-critical"), false);
  assert.equal(
    target.classList.contains(
      "hud-event-intelligence-risk-detected",
    ),
    false,
  );
});

test("emit keeps a bounded business cooldown after animation cleanup", () => {
  const target = fakeElement();
  const scheduled = [];
  let now = 1000;
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "visible",
      querySelector() {
        return target;
      },
      createElement() {
        return fakeElement();
      },
      getElementById() {
        return null;
      },
    },
    matchMediaFn: () => ({ matches: false }),
    setTimeoutFn(callback) {
      scheduled.push(callback);
      return scheduled.length;
    },
    nowFn: () => now,
  });

  const event = {
    type: "intelligence:risk-detected",
    target,
    severity: "critical",
    dedupeKey: "risk:10004:high",
    cooldownMs: 5000,
  };
  assert.equal(system.emit(event), true);
  scheduled.shift()();
  now = 2000;
  assert.equal(system.emit(event), false);
  now = 6001;
  assert.equal(system.emit(event), true);
});

test("resolveEvent releases risk cooldown and active classes immediately", () => {
  const target = fakeElement();
  const cancelled = [];
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "visible",
      querySelector() {
        return target;
      },
      createElement() {
        return fakeElement();
      },
      getElementById() {
        return null;
      },
    },
    matchMediaFn: () => ({ matches: false }),
    setTimeoutFn() {
      return 41;
    },
    clearTimeoutFn(timer) {
      cancelled.push(timer);
    },
    nowFn: () => 1000,
  });
  const event = {
    type: "intelligence:risk-detected",
    target,
    severity: "critical",
    dedupeKey: "risk:10004:high",
    cooldownMs: 10_000,
  };

  assert.equal(system.emit(event), true);
  assert.equal(system.resolveEvent("risk:10004:high"), true);
  assert.deepEqual(cancelled, [41]);
  assert.equal(target.classList.values.size, 0);
  assert.equal(system.emit(event), true);
});

test("emit catches invalid selectors and falls back to a Toast", () => {
  const region = fakeElement();
  const diagnostics = [];
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "visible",
      querySelector() {
        throw new SyntaxError("invalid selector");
      },
      getElementById(id) {
        return id === "hud-toast-region" ? region : null;
      },
      createElement() {
        return fakeElement();
      },
    },
    matchMediaFn: () => ({ matches: false }),
    setTimeoutFn() {
      return 1;
    },
    onDiagnostic(diagnostic) {
      diagnostics.push(diagnostic);
    },
  });

  assert.doesNotThrow(() => {
    assert.equal(system.emit({
      type: "battle:report-arrived",
      target: "[",
      severity: "info",
      message: "新战报已到达",
      dedupeKey: "battle:1",
    }), true);
  });
  assert.equal(region.children.length, 1);
  assert.equal(region.children[0].children[0].textContent, "新战报已到达");
  assert.equal(diagnostics[0].code, "invalid-selector");
});

test("emit diagnoses invalid event types and Toasts missing targets", () => {
  const region = fakeElement();
  const diagnostics = [];
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "visible",
      querySelector() {
        return null;
      },
      getElementById(id) {
        return id === "hud-toast-region" ? region : null;
      },
      createElement() {
        return fakeElement();
      },
    },
    matchMediaFn: () => ({ matches: false }),
    setTimeoutFn() {
      return 1;
    },
    onDiagnostic(diagnostic) {
      diagnostics.push(diagnostic);
    },
  });

  assert.equal(system.emit({
    type: "invalid:event",
    target: "#missing",
  }), false);
  assert.equal(diagnostics[0].code, "invalid-event-type");
  assert.equal(system.emit({
    type: "data:stale",
    target: "#missing",
    severity: "warning",
    message: "数据陈旧",
    dedupeKey: "stale:missing",
  }), true);
  assert.equal(region.children.length, 1);
  assert.equal(diagnostics[1].code, "missing-target");
});

test("emit retains shared classes until every owning event completes", () => {
  const target = fakeElement();
  const scheduled = [];
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "visible",
      querySelector() {
        return target;
      },
      createElement() {
        return fakeElement();
      },
      getElementById() {
        return null;
      },
    },
    matchMediaFn: () => ({ matches: false }),
    setTimeoutFn(callback) {
      scheduled.push(callback);
      return scheduled.length;
    },
  });

  system.emit({
    type: "battle:report-arrived",
    target,
    severity: "info",
    dedupeKey: "battle:1",
  });
  system.emit({
    type: "battle:report-arrived",
    target,
    severity: "info",
    dedupeKey: "battle:2",
  });

  assert.equal(target.classList.contains("hud-event-info"), true);
  scheduled[0]();
  assert.equal(target.classList.contains("hud-event-info"), true);
  assert.equal(
    target.classList.contains("hud-event-battle-report-arrived"),
    true,
  );
  scheduled[1]();
  assert.equal(target.classList.contains("hud-event-info"), false);
  assert.equal(
    target.classList.contains("hud-event-battle-report-arrived"),
    false,
  );
});

test("emit rejects unsupported events and unresolved targets", () => {
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "visible",
      querySelector() {
        return null;
      },
      getElementById() {
        return null;
      },
      createElement() {
        return fakeElement();
      },
    },
    matchMediaFn: () => ({ matches: false }),
  });

  assert.equal(
    system.emit({ type: "unknown:event", target: "#missing" }),
    false,
  );
  assert.equal(
    system.emit({
      type: "battle:report-arrived",
      target: "#missing",
    }),
    false,
  );
});

test("emit does not animate hidden documents or reduced motion", () => {
  const hiddenTarget = fakeElement();
  const hidden = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "hidden",
      querySelector() {
        return hiddenTarget;
      },
      getElementById() {
        return null;
      },
      createElement() {
        return fakeElement();
      },
    },
    matchMediaFn: () => ({ matches: false }),
  });
  assert.equal(
    hidden.emit({
      type: "intelligence:risk-detected",
      target: "#risk",
      severity: "critical",
    }),
    false,
  );
  assert.equal(hiddenTarget.classList.values.size, 0);

  const reducedTarget = fakeElement();
  const reduced = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "visible",
      querySelector() {
        return reducedTarget;
      },
      getElementById() {
        return null;
      },
      createElement() {
        return fakeElement();
      },
    },
    matchMediaFn: () => ({ matches: true }),
    setTimeoutFn() {
      return 1;
    },
  });
  assert.equal(
    reduced.emit({
      type: "intelligence:risk-detected",
      target: "#risk",
      severity: "critical",
    }),
    true,
  );
  assert.equal(reducedTarget.classList.values.size, 0);
});

test("clearEffects cancels active event timers and removes classes", () => {
  const target = fakeElement();
  const cancelled = [];
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "visible",
      querySelector() {
        return target;
      },
      getElementById() {
        return null;
      },
      createElement() {
        return fakeElement();
      },
    },
    matchMediaFn: () => ({ matches: false }),
    setTimeoutFn() {
      return 41;
    },
    clearTimeoutFn(timer) {
      cancelled.push(timer);
    },
  });

  system.emit({
    type: "operation:stage-changed",
    target,
    severity: "warning",
  });
  system.clearEffects();

  assert.deepEqual(cancelled, [41]);
  assert.equal(target.classList.values.size, 0);
  assert.equal(
    system.emit({
      type: "operation:stage-changed",
      target,
      severity: "warning",
    }),
    true,
  );
});

test("toast merges repeated notifications and assigns live priority", () => {
  const region = fakeElement();
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      visibilityState: "visible",
      getElementById(id) {
        return id === "hud-toast-region" ? region : null;
      },
      createElement() {
        return fakeElement();
      },
    },
    matchMediaFn: () => ({ matches: false }),
    setTimeoutFn() {
      return 1;
    },
    nowFn: () => 1000,
  });

  system.toast({
    severity: "warning",
    title: "数据陈旧",
    dedupeKey: "stale",
  });
  system.toast({
    severity: "warning",
    title: "数据陈旧",
    dedupeKey: "stale",
  });

  assert.equal(region.children.length, 1);
  assert.equal(region.children[0].dataset.count, "2");
  assert.equal(
    region.children[0].attributes["aria-live"],
    "polite",
  );

  system.toast({
    severity: "critical",
    title: "高危目标",
    dedupeKey: "risk",
  });
  assert.equal(region.children.length, 2);
  assert.equal(
    region.children[1].attributes["aria-live"],
    "assertive",
  );
});

test("toast uses text nodes and follows severity lifetime policy", () => {
  const region = fakeElement();
  const scheduled = [];
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      getElementById() {
        return region;
      },
      createElement() {
        return fakeElement();
      },
    },
    matchMediaFn: () => ({ matches: false }),
    setTimeoutFn(callback, delay) {
      scheduled.push({ callback, delay });
      return scheduled.length;
    },
    nowFn: () => 1000,
  });

  const info = system.toast({
    severity: "info",
    title: "<img src=x onerror=alert(1)>",
    message: "<script>bad()</script>",
    source: "API",
    dedupeKey: "safe",
  });
  system.toast({
    severity: "warning",
    title: "警告",
    dedupeKey: "warning",
  });
  const critical = system.toast({
    severity: "critical",
    title: "严重",
    action: { label: "查看", handler() {} },
    dedupeKey: "critical",
  });

  assert.equal(info.children[0].textContent, "<img src=x onerror=alert(1)>");
  assert.equal(info.children[1].textContent, "<script>bad()</script>");
  assert.deepEqual(
    scheduled.map(({ delay }) => delay),
    [4200, 6200],
  );
  assert.equal(critical.dataset.count, "1");

  scheduled[0].callback();
  assert.equal(region.children.includes(info), false);
  assert.notEqual(
    system.toast({
      severity: "info",
      title: "新通知",
      dedupeKey: "safe",
    }),
    info,
  );
});

test("loadHealth renders component cards from the read-only endpoint", async () => {
  const container = fakeElement();
  const requested = [];
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      getElementById(id) {
        return id === "hud-health-grid" ? container : null;
      },
      createElement(tag) {
        return {...fakeElement(), tagName: tag};
      },
    },
    matchMediaFn: () => ({ matches: false }),
  });

  const body = await system.loadHealth(async (url, options) => {
    requested.push([url, options]);
    return {
      ok: true,
      async json() {
        return {
          ok: true,
          overall: "live",
          components: {
            backend: {
              status: "live",
              label: "后端",
              detail: "Flask API 可用",
            },
          },
        };
      },
    };
  });

  assert.equal(requested[0][0], "/api/hud/health");
  assert.equal(requested[0][1].cache, "no-store");
  assert.equal(body.overall, "live");
  assert.equal(container.children.length, 1);
  assert.equal(container.children[0].dataset.status, "live");
});

test("loadHealth renders an error state without throwing", async () => {
  const container = fakeElement();
  const system = createHudSystem({
    documentRef: {
      body: fakeElement(),
      getElementById() {
        return container;
      },
      createElement(tag) {
        return {...fakeElement(), tagName: tag};
      },
    },
    matchMediaFn: () => ({ matches: false }),
  });

  const body = await system.loadHealth(async () => {
    throw new Error("offline");
  });

  assert.equal(body, null);
  assert.equal(
    container.children[0].className,
    "hud-state hud-state-error",
  );
  assert.equal(container.children[0].textContent, "offline");
});

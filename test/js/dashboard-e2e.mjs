import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import process from "node:process";

const require = createRequire(import.meta.url);
const { chromium } = require("playwright");
const ROOT = new URL("../../", import.meta.url).pathname;
const BASE = "http://127.0.0.1:8876";
const CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

const server = spawn(
  `${ROOT}.venv/bin/python`,
  [
    "-c",
    "import api_server; api_server.run_app(open_browser=False,start_sniffer=False,host='127.0.0.1',port=8876)",
  ],
  { cwd: ROOT, stdio: ["ignore", "pipe", "pipe"] },
);

async function waitForServer() {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const response = await fetch(`${BASE}/api/status`);
      if (response.ok) return;
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error("dashboard test server did not start");
}

function isIdentityTransform(value) {
  return ["", "none", "matrix(1, 0, 0, 1, 0, 0)"].includes(value);
}

async function assertSurfaceAndInteraction(
  page,
  tabId,
  interactionSelector = "",
  surfaceIssues = [],
) {
  const pageRoot = page.locator(`#tab${tabId}`);
  assert.equal(
    await pageRoot.locator(".hud-page-head").first().isVisible(),
    true,
    `tab ${tabId} hid its shared page head`,
  );
  const panel = pageRoot.locator(".hud-panel").first();
  assert.equal(await panel.isVisible(), true, `tab ${tabId} hid its first shared panel`);
  const panelBackground = await panel.evaluate((element) => {
    const style = getComputedStyle(element);
    return {
      backgroundColor: style.backgroundColor,
      backgroundImage: style.backgroundImage,
    };
  });
  if (panelBackground.backgroundColor === "rgba(0, 0, 0, 0)") {
    surfaceIssues.push(
      `tab ${tabId} first shared panel was transparent ` +
        `(backgroundImage=${panelBackground.backgroundImage})`,
    );
  }
  if (!interactionSelector) return;

  const control = pageRoot.locator(interactionSelector).first();
  assert.equal(await control.isVisible(), true, `tab ${tabId} interaction control was hidden`);
  await control.scrollIntoViewIfNeeded();
  await control.evaluate((element) => {
    element.dataset.taskNineFocusTarget = "true";
  });
  await page.evaluate(() => document.activeElement?.blur());
  for (let step = 0; step < 240; step += 1) {
    await page.keyboard.press("Tab");
    if (await control.evaluate((element) => document.activeElement === element)) break;
  }
  assert.equal(
    await control.evaluate((element) => document.activeElement === element),
    true,
    `tab ${tabId} representative control was unreachable by keyboard`,
  );
  const focusStyle = await control.evaluate((element) => {
    const style = getComputedStyle(element);
    return { outline: style.outlineStyle, boxShadow: style.boxShadow };
  });
  assert.equal(
    focusStyle.outline !== "none" || focusStyle.boxShadow !== "none",
    true,
    `tab ${tabId} keyboard focus was not visible`,
  );
  await control.evaluate((element) => {
    element.blur();
    delete element.dataset.taskNineFocusTarget;
  });

  const idleStyle = await control.evaluate((element) => {
    const style = getComputedStyle(element);
    return { borderColor: style.borderColor, backgroundColor: style.backgroundColor };
  });
  await control.hover();
  await page.waitForTimeout(190);
  const hoverStyle = await control.evaluate((element) => {
    const style = getComputedStyle(element);
    return { borderColor: style.borderColor, backgroundColor: style.backgroundColor };
  });
  assert.notDeepEqual(
    hoverStyle,
    idleStyle,
    `tab ${tabId} hover changed neither border nor background`,
  );

  const box = await control.boundingBox();
  assert.ok(box, `tab ${tabId} interaction control had no hit box`);
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  await page.waitForTimeout(30);
  const pressedTransform = await control.evaluate(
    (element) => getComputedStyle(element).transform,
  );
  assert.equal(
    isIdentityTransform(pressedTransform),
    false,
    `tab ${tabId} pointer press kept an identity transform`,
  );
  await page.mouse.up();
  await page.waitForTimeout(120);

  await control.evaluate((element) => {
    element.disabled = true;
  });
  await page.mouse.down();
  await page.waitForTimeout(30);
  const disabledTransform = await control.evaluate(
    (element) => getComputedStyle(element).transform,
  );
  await page.mouse.up();
  await control.evaluate((element) => {
    element.disabled = false;
  });
  assert.equal(
    isIdentityTransform(disabledTransform),
    true,
    `tab ${tabId} disabled control still transformed`,
  );
}

async function assertPageStateLifecycle(page, controls) {
  await page.evaluate(() => window.switchTab(7, null));
  await page.waitForSelector("#tab7.active");
  await page.waitForFunction(() =>
    document.querySelector("#pbt-body")?.textContent.includes("玩家甲")
      && !document.querySelector("#pbt-body")
        ?.closest(".organization-table-panel")
        ?.hasAttribute("aria-busy"),
  );

  const refreshingFixture = controls.queueOrganizationResponse(
    "success",
    { deferred: true },
  );
  const refreshingRequest = page.evaluate(() => window.loadPlayerBattleTeams());
  await refreshingFixture.started;
  const refreshingState = await page.evaluate(() => {
    const panel = document.querySelector("#pbt-body")?.closest(
      ".organization-table-panel",
    );
    return {
      oldContentVisible: document.querySelector("#pbt-body")
        ?.textContent.includes("玩家甲"),
      refreshLine: panel?.classList.contains("hud-refresh-line"),
      busy: panel?.getAttribute("aria-busy"),
    };
  });
  assert.deepEqual(refreshingState, {
    oldContentVisible: true,
    refreshLine: true,
    busy: "true",
  });
  refreshingFixture.release();
  await refreshingRequest;
  assert.equal(
    await page.locator(".organization-table-panel").first().getAttribute("aria-busy"),
    null,
  );

  controls.queueOrganizationResponse("empty");
  await page.evaluate(() => window.loadPlayerBattleTeams());
  const emptyState = page.locator("#pbt-body .hud-state-empty");
  assert.equal(await emptyState.isVisible(), true);
  assert.match(await emptyState.textContent(), /暂无符合条件的玩家队伍/);

  controls.queueOrganizationResponse("stale");
  await page.evaluate(() => window.loadPlayerBattleTeams());
  const staleRow = page.locator("#pbt-body .organization-row[data-state='stale']").first();
  assert.equal(await staleRow.isVisible(), true);
  assert.match(await staleRow.textContent(), /陈旧玩家/);

  controls.queueOrganizationResponse("error");
  await page.evaluate(() => window.loadPlayerBattleTeams());
  const nonblockingError = page.locator(
    ".organization-table-panel .organization-status-host .hud-state-error",
  );
  assert.equal(await nonblockingError.isVisible(), true);
  assert.match(await nonblockingError.textContent(), /玩家队伍临时不可用/);
  assert.equal(
    await page.locator("#pbt-body").textContent().then((text) =>
      text.includes("陈旧玩家"),
    ),
    true,
    "nonblocking organization error replaced prior stale content",
  );
  assert.equal(
    await page.locator("#pbt-body .organization-row[data-state='stale']").count() > 0,
    true,
  );

  controls.queueOrganizationResponse("success");
  await page.evaluate(() => window.loadPlayerBattleTeams());
  await page.waitForFunction(() =>
    document.querySelector("#pbt-body")?.textContent.includes("玩家甲"),
  );

  controls.requestStaleHealth();
  await page.evaluate(() => window.loadCommandCenterSettings());
  await page.waitForFunction(() =>
    document.querySelector("#hud-health-grid [data-status='stale']")
      ?.textContent.includes("12 分钟前"),
  );
  assert.equal(
    await page.locator("#hud-health-grid [data-status='stale']").count(),
    1,
  );
  assert.equal(
    await page.evaluate(() =>
      window.__hudEmitCalls.some((event) => event.type === "data:stale"),
    ),
    true,
  );
}

async function assertLegacyLoaderLifecycle(
  page,
  controls,
  liveArmySnapshot,
) {
  const task = (id, name) => ({
    id,
    status: 0,
    name,
    time: Math.floor(Date.now() / 1000) + 3600,
    pos: "10004",
    pos_xy: "1,4",
    wid_name: "七级资源地",
    target_groups: ["一团"],
    target_user_num: 20,
    complete_user_num: 6,
  });
  const combo = (name) => ({
    combo: name,
    total: 18,
    win: 11,
    lose: 5,
    draw: 2,
    win_rate: 66.7,
  });
  const region = (state, groups = ["一团"]) => ({
    meta: {},
    groups,
    summary: {
      total_players: 18,
      total_power: 360000,
      state_count: 1,
      alliance_count: 1,
      group_count: groups.length,
      grouped_players: 18,
    },
    state_rows: [{
      state,
      player_count: 18,
      total_power: 360000,
      avg_power: 20000,
      max_power: 30000,
    }],
    group_rows: [{
      alliance_name: "甲盟",
      group_name: groups.at(-1) || "未分组",
      player_count: 18,
      total_power: 360000,
      state_summary: `${state} 18`,
    }],
    alliance_rows: [],
  });
  const lifecycleState = async (surfaceSelector) => {
    const surface = page.locator(surfaceSelector);
    return {
      busy: await surface.getAttribute("aria-busy"),
      refreshing: await surface.evaluate(
        (element) => element.classList.contains("hud-refresh-line"),
      ),
    };
  };
  const exerciseLatestWins = async ({
    key,
    surfaceSelector,
    contentSelector,
    baseline,
    oldBody,
    latestBody,
    latestText,
    oldText,
    errorBody,
    errorText,
    startOld,
    startLatest,
    startError,
    inspectRequests = () => {},
  }) => {
    const oldFixture = controls.queueLifecycleResponse(key);
    const latestFixture = controls.queueLifecycleResponse(key);
    const oldRun = startOld();
    const oldRequest = await oldFixture.started;
    const latestRun = startLatest();
    const latestRequest = await latestFixture.started;
    inspectRequests(oldRequest, latestRequest);
    assert.match(await page.locator(contentSelector).textContent(), baseline);
    assert.deepEqual(await lifecycleState(surfaceSelector), {
      busy: "true",
      refreshing: true,
    });
    oldFixture.respond(oldBody);
    await oldRun;
    assert.match(await page.locator(contentSelector).textContent(), baseline);
    assert.deepEqual(await lifecycleState(surfaceSelector), {
      busy: "true",
      refreshing: true,
    });
    latestFixture.respond(latestBody);
    await latestRun;
    assert.match(
      await page.locator(contentSelector).textContent(),
      latestText,
    );
    assert.doesNotMatch(
      await page.locator(contentSelector).textContent(),
      oldText,
    );
    assert.deepEqual(await lifecycleState(surfaceSelector), {
      busy: null,
      refreshing: false,
    });

    const errorFixture = controls.queueLifecycleResponse(key);
    const errorRun = startError();
    await errorFixture.started;
    assert.match(
      await page.locator(contentSelector).textContent(),
      latestText,
    );
    errorFixture.respond(errorBody);
    await errorRun;
    assert.match(
      await page.locator(contentSelector).textContent(),
      latestText,
    );
    assert.match(
      await page.locator(`${surfaceSelector} .legacy-loader-status`)
        .textContent(),
      errorText,
    );
    assert.deepEqual(await lifecycleState(surfaceSelector), {
      busy: null,
      refreshing: false,
    });
  };

  await page.evaluate(() => window.switchTab(16, null));
  await page.waitForSelector("#tab16.active");
  await page.waitForFunction(() =>
    document.querySelector("#task-body")?.textContent.includes("集结测试")
  );
  await exerciseLatestWins({
    key: "legacyTasks",
    surfaceSelector: "#tab16 .operation-task-list",
    contentSelector: "#task-body",
    baseline: /集结测试/,
    oldBody: [task(91, "旧任务不应提交")],
    latestBody: [task(92, "最新任务真值")],
    latestText: /最新任务真值/,
    oldText: /旧任务不应提交/,
    errorBody: { error: "任务接口临时不可用" },
    errorText: /任务接口临时不可用/,
    startOld: () => page.evaluate(() => window.loadTasks()),
    startLatest: () => page.evaluate(() => window.loadTasks()),
    startError: () => page.evaluate(() => window.loadTasks()),
  });

  await page.evaluate(() => window.switchTab(23, null));
  await page.waitForSelector("#tab23.active");
  await page.waitForFunction(() =>
    document.querySelector("#combo-body")?.textContent.includes("张辽")
  );
  await exerciseLatestWins({
    key: "legacyCombo",
    surfaceSelector: "#tab23 .hud-table-shell",
    contentSelector: "#combo-body",
    baseline: /张辽/,
    oldBody: [combo("旧将+旧将+旧将")],
    latestBody: [combo("最新将+最新将+最新将")],
    latestText: /最新将/,
    oldText: /旧将/,
    errorBody: { error: "阵容接口临时不可用" },
    errorText: /阵容接口临时不可用/,
    startOld: async () => {
      await page.locator("#combo-min").fill("4");
      return page.evaluate(() => window.loadHeroCombo());
    },
    startLatest: async () => {
      await page.locator("#combo-min").fill("8");
      return page.evaluate(() => window.loadHeroCombo());
    },
    startError: () => page.evaluate(() => window.loadHeroCombo()),
    inspectRequests(oldRequest, latestRequest) {
      assert.match(oldRequest.url(), /min=4/);
      assert.match(latestRequest.url(), /min=8/);
    },
  });

  await page.evaluate(() => window.switchTab(26, null));
  await page.waitForSelector("#tab26.active");
  await page.waitForFunction(() =>
    document.querySelector("#sr-state-body")?.textContent.includes("益州")
  );
  await page.evaluate(() => {
    const group = document.querySelector("#sr-group");
    if (![...group.options].some((option) => option.value === "二团")) {
      group.append(new Option("二团", "二团"));
    }
    document.querySelector("#sr-scope").value = "group";
    group.disabled = false;
    group.style.opacity = "1";
  });
  await exerciseLatestWins({
    key: "legacyRegion",
    surfaceSelector: "#tab26",
    contentSelector: "#sr-state-body",
    baseline: /益州/,
    oldBody: region("旧州不应提交", ["一团"]),
    latestBody: region("扬州最新真值", ["一团", "二团"]),
    latestText: /扬州最新真值/,
    oldText: /旧州不应提交/,
    errorBody: { error: "州郡接口临时不可用" },
    errorText: /州郡接口临时不可用/,
    startOld: async () => {
      await page.locator("#sr-group").evaluate((select) => {
        select.value = "一团";
      });
      return page.evaluate(() => window.loadStateRegionStats());
    },
    startLatest: async () => {
      await page.locator("#sr-group").evaluate((select) => {
        select.value = "二团";
      });
      return page.evaluate(() => window.loadStateRegionStats());
    },
    startError: () => page.evaluate(() => window.loadStateRegionStats()),
    inspectRequests(oldRequest, latestRequest) {
      assert.match(oldRequest.url(), /group=%E4%B8%80%E5%9B%A2/);
      assert.match(latestRequest.url(), /group=%E4%BA%8C%E5%9B%A2/);
    },
  });

  await page.evaluate(() => window.switchTab(35, null));
  await page.waitForSelector("#tab35.active");
  await page.waitForFunction(() =>
    document.querySelector("#live-army-current-list")
      ?.textContent.includes("无情的战")
  );
  const oldLive = controls.queueLifecycleResponse("legacyLiveArmy");
  const latestLive = controls.queueLifecycleResponse("legacyLiveArmy");
  const oldLiveRun = page.evaluate(
    () => window.LiveArmyCommand.load(true),
  );
  await oldLive.started;
  assert.equal(
    await page.locator("#live-army-summary").getAttribute("aria-busy"),
    "true",
  );
  assert.match(
    await page.locator("#live-army-current-list").textContent(),
    /无情的战/,
  );
  await page.evaluate(() => {
    window.dispatchEvent(new CustomEvent("stzb:stream-event", {
      detail: { type: "world_scene_delta", data: {} },
    }));
  });
  oldLive.respond(liveArmySnapshot("旧部队真值"));
  await oldLiveRun;
  await latestLive.started;
  assert.match(
    await page.locator("#live-army-current-list").textContent(),
    /旧部队真值/,
  );
  latestLive.respond(liveArmySnapshot("最新部队真值"));
  await page.waitForFunction(() =>
    document.querySelector("#live-army-current-list")
      ?.textContent.includes("最新部队真值")
  );
  assert.doesNotMatch(
    await page.locator("#live-army-current-list").textContent(),
    /旧部队真值/,
  );
  assert.equal(
    await page.locator("#live-army-summary").getAttribute("aria-busy"),
    null,
  );

  const liveError = controls.queueLifecycleResponse("legacyLiveArmy");
  const liveErrorRun = page.evaluate(
    () => window.LiveArmyCommand.load(true),
  );
  await liveError.started;
  assert.match(
    await page.locator("#live-army-current-list").textContent(),
    /最新部队真值/,
  );
  liveError.respond({ ok: false, error: "实时部队接口临时不可用" });
  await liveErrorRun;
  assert.match(
    await page.locator("#live-army-current-list").textContent(),
    /最新部队真值/,
  );
  assert.equal(
    await page.locator("#live-army-freshness").textContent(),
    "DEGRADED / RETAINED",
  );
  assert.equal(
    await page.locator("#live-army-summary").getAttribute("aria-busy"),
    null,
  );
}

async function assertRealEventLifecycle(page, controls, eventIssues) {
  await page.evaluate(() => window.switchTab(33, null));
  await page.waitForSelector("#tab33.active");
  controls.promoteRisk();
  await page.evaluate(() => window.IntelligenceCenter.load(true));
  await page.waitForFunction(() =>
    document.querySelector("#intel-detail-panel")
      ?.classList.contains("hud-event-intelligence-risk-detected"),
  );
  assert.equal(
    await page.evaluate(() =>
      window.__hudEmitCalls.filter(
        (event) =>
          event.type === "intelligence:risk-detected"
          && event.dedupeKey === "risk:10004:high",
      ).length,
    ),
    1,
  );
  assert.equal(
    await page.locator(
      "#hud-toast-region .hud-toast[data-severity='critical'][data-dedupe-key='risk:10004:high']",
    ).count(),
    1,
  );
  await page.evaluate(() => window.IntelligenceCenter.selectWid(10004));
  assert.equal(
    await page.evaluate(() =>
      window.__hudEmitCalls.filter(
        (event) =>
          event.type === "intelligence:risk-detected"
          && event.dedupeKey === "risk:10004:high",
      ).length,
    ),
    1,
    "repeated identical risk emitted twice",
  );

  await page.evaluate(() => window.switchTab(31, null));
  await page.waitForSelector("#tab31.active");
  await page.evaluate(() => {
    const source = window.__eventSources.at(-1);
    source.triggerMessage({
      type: "world_scene_delta",
      data: { message: "世界状态更新" },
      timestamp: 1_786_712_000_000,
    });
    source.triggerMessage({
        type: "battle",
        data: {
          battle_id: 5289171,
          atk_name: "新战报攻方",
          def_name: "新战报守方",
          result_desc: "胜利",
        },
        timestamp: 1_786_712_001_000,
    });
  });
  await page.waitForFunction(() =>
    document.querySelector("#cc-timeline-list")
      ?.textContent.includes("新战报攻方"),
  );
  const highlightedBattleRows = await page.locator(
    "#cc-timeline-list .cc-timeline-item.hud-event-battle-report-arrived",
  ).count();
  if (highlightedBattleRows !== 1) {
    eventIssues.push(
      `new battle highlighted ${highlightedBattleRows} rows instead of exactly one`,
    );
  }
  assert.equal(
    await page.locator(
      "#cc-timeline-list .cc-timeline-item--world.hud-event-battle-report-arrived",
    ).count(),
    0,
    "new battle highlighted a non-battle row",
  );

  assert.deepEqual(
    await page.evaluate(() =>
      window.__hudEmitCalls
        .filter(
          (event) =>
            event.type === "simulation:completed" &&
            event.target === "#sim-result-summary",
        )
        .map((event) => event.severity),
    ),
    ["success", "success", "warning", "success"],
  );
  assert.deepEqual(
    await page.evaluate(() => window.__scoreDeltaStates),
    ["up", "down", "same"],
  );

  const restoredBefore = await page.evaluate(() =>
    window.__hudEmitCalls.filter(
      (event) => event.type === "connection:restored",
    ).length,
  );
  await page.evaluate(() => {
    const current = window.__eventSources.at(-1);
    current.triggerError();
    window.connectSSE();
    window.__eventSources.at(-1).triggerOpen();
    window.__eventSources.at(-1).triggerOpen();
  });
  assert.equal(
    await page.evaluate(() =>
      window.__hudEmitCalls.filter(
        (event) => event.type === "connection:restored",
      ).length,
    ),
    restoredBefore + 1,
    "reconnect did not emit exactly once after a real error transition",
  );

  await page.evaluate(() => {
    window.HudSystem.emit({
      type: "battle:report-arrived",
      target: "#cc-timeline-list",
      severity: "info",
      dedupeKey: "task-nine-tab-clear",
    });
  });
  await page.waitForFunction(() =>
    document.querySelector("#cc-timeline-list")
      ?.classList.contains("hud-event-battle-report-arrived"),
  );
  await page.evaluate(() => window.switchTab(7, null));
  assert.equal(
    await page.locator("#cc-timeline-list").evaluate((element) =>
      [...element.classList].some((name) => name.startsWith("hud-event-")),
    ),
    false,
    "tab switch did not clear one-shot event classes",
  );
}

async function closeHudToasts(page) {
  const closeButtons = page.locator("#hud-toast-region .hud-toast-close");
  while (await closeButtons.count()) {
    await closeButtons.first().click();
  }
}

async function collectVisibleVisualBudget(page, viewport, tabId) {
  return page.evaluate(({ currentViewport, currentTabId }) => {
    const isVisible = (element, style = getComputedStyle(element)) => {
      const rect = element.getBoundingClientRect();
      return style.display !== "none"
        && style.visibility !== "hidden"
        && Number(style.opacity) > 0
        && rect.width > 0
        && rect.height > 0
        && rect.right > 0
        && rect.bottom > 0
        && rect.left < window.innerWidth
        && rect.top < window.innerHeight;
    };
    const describe = (element, pseudo, style) => ({
      tag: element.tagName.toLowerCase(),
      id: element.id,
      className: String(element.className || ""),
      pseudo,
      backdropFilter: style.backdropFilter || style.webkitBackdropFilter || "none",
      animationName: style.animationName || "none",
    });
    const blur = [];
    const animation = [];
    for (const element of document.querySelectorAll("body *")) {
      const elementStyle = getComputedStyle(element);
      if (!isVisible(element, elementStyle)) continue;
      const styles = [["element", elementStyle]];
      for (const pseudo of ["::before", "::after"]) {
        const pseudoStyle = getComputedStyle(element, pseudo);
        if (
          !["none", "normal"].includes(pseudoStyle.content)
          && pseudoStyle.display !== "none"
          && pseudoStyle.visibility !== "hidden"
          && Number(pseudoStyle.opacity) > 0
        ) {
          styles.push([pseudo, pseudoStyle]);
        }
      }
      for (const [pseudo, style] of styles) {
        const detail = describe(element, pseudo, style);
        if (detail.backdropFilter !== "none") blur.push(detail);
        if (detail.animationName !== "none") animation.push(detail);
      }
    }
    for (const dialog of document.querySelectorAll("dialog[open]")) {
      if (!isVisible(dialog)) continue;
      const backdropStyle = getComputedStyle(dialog, "::backdrop");
      const detail = describe(dialog, "::backdrop", backdropStyle);
      if (detail.backdropFilter !== "none") blur.push(detail);
      if (detail.animationName !== "none") animation.push(detail);
    }
    return {
      viewport: `${currentViewport.width}x${currentViewport.height}`,
      tabId: currentTabId,
      blurCount: blur.length,
      animationCount: animation.length,
      blur,
      animation,
    };
  }, { currentViewport: viewport, currentTabId: tabId });
}

async function sampleAnimationPeak(page, durationMs = 360) {
  await page.evaluate(() => {
    window.__rafAnimationStats.peak = window.__rafAnimationStats.pending;
    window.HudSystem?.resetAnimationStats?.();
  });
  const deadline = Date.now() + durationMs;
  let peakAnimationCount = 0;
  let peakAnimations = [];
  while (Date.now() < deadline) {
    const sample = await page.evaluate(() => {
      const isVisible = (element) => {
        const style = getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        return style.display !== "none"
          && style.visibility !== "hidden"
          && Number(style.opacity) > 0
          && rect.width > 0
          && rect.height > 0
          && rect.right > 0
          && rect.bottom > 0
          && rect.left < window.innerWidth
          && rect.top < window.innerHeight;
      };
      const animations = document.getAnimations({ subtree: true })
        .filter((animation) => {
          const target = animation.effect?.target;
          return animation.playState === "running"
            && target instanceof Element
            && isVisible(target);
        })
        .map((animation) => {
          const target = animation.effect.target;
          return {
            id: target.id,
            className: String(target.className || ""),
            animationName: animation.animationName || "",
          };
        });
      return { animationCount: animations.length, animations };
    });
    if (sample.animationCount > peakAnimationCount) {
      peakAnimationCount = sample.animationCount;
      peakAnimations = sample.animations;
    }
    await page.waitForTimeout(24);
  }
  return {
    peakAnimationCount,
    peakAnimations,
    raf: await page.evaluate(() => ({ ...window.__rafAnimationStats })),
    hud: await page.evaluate(() =>
      window.HudSystem?.getAnimationStats?.() || {
        activeValueAnimations: 0,
        peakValueAnimations: 0,
      }
    ),
  };
}

async function assertRafQuiescence(page, windowMs = 320) {
  await page.waitForTimeout(windowMs);
  const firstWindow = await page.evaluate(
    () => ({ ...window.__rafAnimationStats }),
  );
  assert.equal(
    firstWindow.pending,
    0,
    `RAF did not drain after short animations: ${JSON.stringify(firstWindow)}`,
  );

  await page.waitForTimeout(windowMs);
  const secondWindow = await page.evaluate(
    () => ({ ...window.__rafAnimationStats }),
  );
  assert.equal(
    secondWindow.pending,
    0,
    `RAF became pending in the second stable window: ${
      JSON.stringify(secondWindow)
    }`,
  );
  assert.equal(
    secondWindow.scheduled,
    firstWindow.scheduled,
    `RAF kept scheduling after animations settled: ${
      JSON.stringify({ firstWindow, secondWindow })
    }`,
  );
  return { firstWindow, secondWindow };
}

async function assertResponsiveViewport(
  page,
  viewport,
  visibleDomainByTab,
  surfaceIssues,
  performanceIssues,
  visualBudgetSamples,
) {
  await page.setViewportSize(viewport);
  for (const [tabIdText, domain] of Object.entries(visibleDomainByTab)) {
    const tabId = Number(tabIdText);
    await page.evaluate((id) => window.switchTab(id, null), tabId);
    await page.waitForSelector(`#tab${tabId}.active`);
    await page.waitForFunction(
      (expected) => document.body.dataset.visualDomain === expected,
      domain,
    );
    assert.equal(
      await page.locator("body").getAttribute("data-visual-domain"),
      domain,
      `tab ${tabId} used the wrong domain at ${viewport.width}px`,
    );
    await assertSurfaceAndInteraction(page, tabId, "", surfaceIssues);
    assert.equal(
      await page.locator(`#tab${tabId} :is(button, input, select)`).first().isVisible(),
      true,
      `tab ${tabId} had no reachable primary control at ${viewport.width}px`,
    );
    const viewportOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    );
    assert.ok(
      viewportOverflow <= 1,
      `tab ${tabId} overflowed by ${viewportOverflow}px at ${viewport.width}px`,
    );
    if (tabId === 35 && viewport.width === 375) {
      const mobileOrder = await page.evaluate(() => {
        const selectors = [
          ".live-army-toolbar",
          ".live-army-map-panel",
          ".live-army-detail-panel",
          "#live-army-index > .hud-panel-head",
        ];
        return selectors.map((selector) => ({
          selector,
          top: document.querySelector(selector).getBoundingClientRect().top,
        }));
      });
      assert.ok(
        mobileOrder.every(
          (item, index) => index === 0 || item.top > mobileOrder[index - 1].top,
        ),
        `live army mobile order was ${JSON.stringify(mobileOrder)}`,
      );
      await closeHudToasts(page);
      const toggle = page.locator("#live-army-index-toggle");
      await toggle.scrollIntoViewIfNeeded();
      await toggle.click();
      assert.equal(
        await page.locator("#live-army-index").evaluate(
          (element) => element.classList.contains("is-collapsed"),
        ),
        true,
      );
      assert.equal(await page.locator("#live-army-list-shell").isVisible(), false);
      await toggle.click();
      assert.equal(await page.locator("#live-army-list-shell").isVisible(), true);
    }

    await closeHudToasts(page);
    await page.evaluate(() => window.CommandCenter.openPalette());
    await page.waitForSelector("#cc-command-dialog[open]");
    const dialogBox = await page.locator("#cc-command-dialog").boundingBox();
    assert.ok(dialogBox);
    assert.ok(dialogBox.x >= -1 && dialogBox.y >= -1);
    assert.ok(dialogBox.x + dialogBox.width <= viewport.width + 1);
    assert.ok(dialogBox.y + dialogBox.height <= viewport.height + 1);
    await page.evaluate(({ currentViewport, currentTabId }) => window.HudSystem.toast({
      severity: "info",
      title: "响应式验收",
      message: `${currentViewport.width}×${currentViewport.height} · tab ${currentTabId}`,
      dedupeKey: `responsive:${currentViewport.width}:${currentTabId}`,
    }), { currentViewport: viewport, currentTabId: tabId });
    const toast = page.locator(
      `#hud-toast-region .hud-toast[data-dedupe-key='responsive:${viewport.width}:${tabId}']`,
    );
    await page.waitForFunction((dedupeKey) => {
      const currentToast = document.querySelector(
        `#hud-toast-region .hud-toast[data-dedupe-key='${dedupeKey}']`,
      );
      return currentToast && Number(getComputedStyle(currentToast).opacity) >= 0.99;
    }, `responsive:${viewport.width}:${tabId}`);
    const toastBox = await toast.boundingBox();
    assert.ok(toastBox);
    assert.ok(toastBox.x >= -1 && toastBox.y >= -1);
    assert.ok(toastBox.x + toastBox.width <= viewport.width + 1);
    assert.ok(toastBox.y + toastBox.height <= viewport.height + 1);

    const visualBudget = await collectVisibleVisualBudget(page, viewport, tabId);
    visualBudgetSamples.push(visualBudget);
    if (visualBudget.blurCount > 4) {
      performanceIssues.push(
        `${visualBudget.viewport} tab ${tabId} used ${visualBudget.blurCount} visible blur layers: ` +
          JSON.stringify(visualBudget.blur),
      );
    }
    if (visualBudget.animationCount > 6) {
      performanceIssues.push(
        `${visualBudget.viewport} tab ${tabId} used ${visualBudget.animationCount} ` +
          `visible animation layers: ${JSON.stringify(visualBudget.animation)}`,
      );
    }
    await page.keyboard.press("Escape");
    await closeHudToasts(page);
  }

  if (viewport.width < 1024) {
    const menuToggle = page.locator(".ds-menu-toggle");
    assert.equal(await menuToggle.isVisible(), true, "mobile navigation toggle was unreachable");
    await menuToggle.click();
    assert.equal(await page.locator("body").evaluate(
      (body) => body.classList.contains("ds-nav-open"),
    ), true);
    await page.locator(".ds-nav-close").click();
    assert.equal(await page.locator("body").evaluate(
      (body) => body.classList.contains("ds-nav-open"),
    ), false);
    await page.waitForTimeout(260);
  } else {
    assert.equal(await page.locator("nav").isVisible(), true);
    if (viewport.width === 1024) {
      const visibleNavLabels = await page.locator(
        "nav > button[data-tab-index]",
      ).evaluateAll((buttons) => buttons.filter((button) => {
        const style = getComputedStyle(button);
        const rect = button.getBoundingClientRect();
        return style.display !== "none"
          && style.visibility !== "hidden"
          && Number(style.opacity) > 0
          && rect.width > 0
          && rect.height > 0;
      }).map((button) => ({
        tabId: button.dataset.tabIndex,
        ariaLabel: button.getAttribute("aria-label"),
      })));
      assert.ok(visibleNavLabels.length > 0);
      assert.equal(
        visibleNavLabels.every((item) => Boolean(item.ariaLabel?.trim())),
        true,
        `1024px nav buttons lacked explicit aria-label: ${JSON.stringify(visibleNavLabels)}`,
      );
    }
  }

}

let browser;
try {
  await waitForServer();
  browser = await chromium.launch({ headless: true, executablePath: CHROME });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.__settingToastCalls = [];
    window.__hudEmitCalls = [];
    window.__eventSources = [];
    window.__unsafePayload = 0;
    window.__taskXss = 0;
    window.__rafAnimationStats = {
      pending: 0,
      peak: 0,
      scheduled: 0,
    };
    const nativeRequestAnimationFrame =
      window.requestAnimationFrame.bind(window);
    const nativeCancelAnimationFrame =
      window.cancelAnimationFrame.bind(window);
    const pendingAnimationFrames = new Map();
    window.requestAnimationFrame = (callback) => {
      window.__rafAnimationStats.pending += 1;
      window.__rafAnimationStats.scheduled += 1;
      window.__rafAnimationStats.peak = Math.max(
        window.__rafAnimationStats.peak,
        window.__rafAnimationStats.pending,
      );
      const frameId = nativeRequestAnimationFrame((timestamp) => {
        if (pendingAnimationFrames.delete(frameId)) {
          window.__rafAnimationStats.pending -= 1;
        }
        callback(timestamp);
      });
      pendingAnimationFrames.set(frameId, true);
      return frameId;
    };
    window.cancelAnimationFrame = (frameId) => {
      if (pendingAnimationFrames.delete(frameId)) {
        window.__rafAnimationStats.pending -= 1;
      }
      nativeCancelAnimationFrame(frameId);
    };
    class DeterministicEventSource {
      constructor(url) {
        this.url = url;
        this.readyState = 0;
        window.__eventSources.push(this);
        queueMicrotask(() => this.triggerOpen());
      }

      close() {
        this.readyState = 2;
      }

      triggerOpen() {
        if (this.readyState === 2) return;
        this.readyState = 1;
        this.onopen?.();
      }

      triggerError() {
        if (this.readyState === 2) return;
        this.readyState = 0;
        this.onerror?.(new Event("error"));
      }

      triggerMessage(detail) {
        if (this.readyState === 2) return;
        this.onmessage?.({ data: JSON.stringify(detail) });
      }
    }
    window.EventSource = DeterministicEventSource;
    let hudSystem;
    Object.defineProperty(window, "HudSystem", {
      configurable: true,
      get() {
        return hudSystem;
      },
      set(value) {
        if (value && !value.__settingToastRecorder) {
          const toast = value.toast.bind(value);
          const emit = value.emit.bind(value);
          value.toast = (detail) => {
            if (detail?.title === "设置已保存") {
              window.__settingToastCalls.push(structuredClone(detail));
            }
            return toast(detail);
          };
          value.emit = (detail) => {
            const snapshot = { ...detail };
            if (snapshot.target instanceof Element) {
              snapshot.target = {
                eventId: snapshot.target.dataset.eventId || "",
                className: snapshot.target.className,
              };
            }
            window.__hudEmitCalls.push(structuredClone(snapshot));
            return emit(detail);
          };
          Object.defineProperty(value, "__settingToastRecorder", {
            value: true,
          });
        }
        hudSystem = value;
      },
    });
  });
  const errors = [];
  const serverErrors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("response", (response) => {
    if (response.url().startsWith(BASE) && response.status() >= 500) {
      serverErrors.push(`${response.status()} ${response.url()}`);
    }
  });
  await page.route(/^https?:\/\/(?!127\.0\.0\.1:8876)/, (route) => route.abort());
  const worldEnvelope = {
    ok: true,
    worldStateVersion: 7,
    latestBaseline: { latest_baseline_order_id: 700, observed_at_ms: Date.now() - 20_000 },
    latestDelta: { version: 7, observed_at_ms: Date.now() - 5_000 },
    freshness: "fresh",
    completeness: "full-baseline",
    coverage: { rowUp: 1, rowDown: 160, colLeft: 1, colRight: 160 },
  };
  const intelligenceSummaryFixture = (overrides = {}) => ({
    ...worldEnvelope,
    counts: { tiles: 1, armies: 1, marches: 0, events: 1 },
    dataBounds: { rowUp: 1, rowDown: 160, colLeft: 1, colRight: 160 },
    focusWid: 10004,
    suggestedBounds: { rowUp: 0, rowDown: 19, colLeft: 0, colRight: 19 },
    ...overrides,
  });
  const intelligenceEventsFixture = (marker = "snapshot_completed") => ({
    ...worldEnvelope,
    events: [{
      seq: 1,
      state_version: 7,
      event_type: marker,
      entity_id: "7",
      observed_at_ms: Date.now(),
      evidence: {},
      diff: {},
    }],
  });
  const intelligenceViewportFixture = (
    name = "七级资源地",
    wid = 10004,
  ) => ({
    ...worldEnvelope,
    tiles: [{
      wid,
      row: Math.floor(wid / 10000),
      col: wid % 10000,
      name,
      landLevel: 7,
      resourceKind: 3,
      freshness: "fresh",
      user_id: 42,
      protect_end_time: 0,
      guard_end_time: 0,
    }],
  });
  const intelligenceRisksFixture = (score = 38, level = "medium") => ({
    ...worldEnvelope,
    risks: [{
      wid: 10004,
      score,
      level,
      confidence: 0.71,
      freshness: "fresh",
      components: {
        landLevel: 21,
        enemyOwnership: 15,
        incomingArmyCount: 14,
        earliestArrival: 15,
        estimatedTroops: 0,
        protectionGuard: 0,
        staleIntel: 0,
      },
      unknownComponents: ["estimatedTroops"],
    }],
  });
  const intelligenceDetailFixture = (wid, name) => ({
    ...worldEnvelope,
    tile: {
      wid,
      row: Math.floor(wid / 10000),
      col: wid % 10000,
      name,
      landLevel: 7,
      resourceKind: 3,
      freshness: "fresh",
    },
    incomingArmies: [],
    risk: {
      score: 24,
      level: "low",
      confidence: 0.9,
      components: {},
      unknownComponents: [],
    },
    battleStats: {
      sampleSize: 0,
      commonLineups: [],
      recentBattles: [],
    },
    events: [],
  });
  const fixtureCounters = {
    health: 0,
    risk: 0,
    simulation: 0,
    scores: 0,
    intelligence: {
      summary: 0,
      overview: 0,
      viewport: 0,
      risks: 0,
      events: 0,
      detail: 0,
      march: 0,
      army: 0,
      entity: 0,
    },
    scoreRoutes: {
      player: 0,
      preview: 0,
      recalc: 0,
      adjustment: 0,
      ruleCreate: 0,
      ruleActivate: 0,
    },
  };
  const organizationResponseQueue = [];
  const scoreResponseQueues = {
    player: [],
    preview: [],
    recalc: [],
    adjustment: [],
    ruleCreate: [],
    ruleActivate: [],
  };
  const lifecycleResponseQueues = new Map();
  function queueOrganizationResponse(kind, { deferred = false } = {}) {
    let release;
    let markStarted;
    const started = new Promise((resolve) => {
      markStarted = resolve;
    });
    const gate = deferred
      ? new Promise((resolve) => {
          release = resolve;
        })
      : Promise.resolve();
    organizationResponseQueue.push({ kind, gate, markStarted });
    return { started, release: release || (() => {}) };
  }
  function queueScoreResponse(kind) {
    let respond;
    let markStarted;
    const started = new Promise((resolve) => {
      markStarted = resolve;
    });
    const response = new Promise((resolve) => {
      respond = resolve;
    });
    scoreResponseQueues[kind].push({ markStarted, response });
    return { started, respond };
  }
  function queueLifecycleResponse(key) {
    let respond;
    let markStarted;
    const started = new Promise((resolve) => {
      markStarted = resolve;
    });
    const response = new Promise((resolve) => {
      respond = resolve;
    });
    const queue = lifecycleResponseQueues.get(key) || [];
    queue.push({ markStarted, response });
    lifecycleResponseQueues.set(key, queue);
    return { started, respond };
  }
  async function fulfillQueuedLifecycleResponse(route, key) {
    const queue = lifecycleResponseQueues.get(key);
    const fixture = queue?.shift();
    if (!fixture) return false;
    fixture.markStarted(route.request());
    const body = await fixture.response;
    if (
      key === "intelligenceSummary"
      && body?.worldStateVersion !== undefined
    ) {
      worldEnvelope.worldStateVersion = body.worldStateVersion;
      worldEnvelope.latestDelta = {
        ...worldEnvelope.latestDelta,
        version: body.worldStateVersion,
      };
    }
    try {
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify(body),
      });
    } catch (error) {
      if (key !== "legacyRegion") throw error;
    }
    return true;
  }
  async function fulfillQueuedScoreResponse(route, kind) {
    fixtureCounters.scoreRoutes[kind] += 1;
    const fixture = scoreResponseQueues[kind].shift();
    if (!fixture) return false;
    fixture.markStarted(route.request());
    const body = await fixture.response;
    if (kind === "recalc" && body?.ok) fixtureCounters.scores += 1;
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(body),
    });
    return true;
  }
  const fixtureControls = {
    requestStaleHealth() {
      fixtureCounters.health = 1;
    },
    promoteRisk() {
      fixtureCounters.risk = 1;
    },
    queueOrganizationResponse,
    queueScoreResponse,
    queueLifecycleResponse,
    intelligenceCounters() {
      return structuredClone(fixtureCounters.intelligence);
    },
  };
  const portrait = (id, local = true) => ({
    iconId: id,
    portraitUrl: local
      ? `/static/hero-portraits/cards/${id}.webp`
      : "/static/hero-portraits/placeholder.svg",
    portraitFallbackUrl:
      "https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/" +
      `cut/card_medium_${id}.jpg?gameid=g10`,
    portraitLocal: local,
  });
  const liveNowSec = Math.floor(Date.now() / 1000);
  const exactLiveArmy = {
    armyId: 18411352,
    userId: 7001,
    ownerName: "无情的战",
    ownerUnionId: 1005,
    ownerUnionName: "甲盟",
    state: 1,
    stateKey: "expedition",
    stateLabel: "出征中",
    stateCategory: "moving",
    isMoving: true,
    source: {
      seq: 101,
      observedAtMs: Date.now() - 2_000,
      ageMs: 2_000,
      freshness: "fresh",
      isStale: false,
      cmdId: 5028,
    },
    location: {
      currentWid: 2081480,
      nextWid: 2081481,
      targetWid: 1151300,
      fromWid: 2081479,
      resideWid: 0,
      stayWid: 0,
      source: "real-march",
    },
    timing: {
      beginTime: liveNowSec - 90,
      nextTime: liveNowSec + 20,
      endTime: liveNowSec + 95,
    },
    march: {
      realMarchId: 99001,
      lastWid: 2081479,
      currentWid: 2081480,
      nextWid: 2081481,
      startTime: liveNowSec - 90,
      nextTime: liveNowSec + 20,
      endTime: liveNowSec + 95,
      pathId: 77,
      unitTimeCost: 3,
      marchType: 1,
      belongId: 7001,
    },
    morale: 97,
    risk: { level: "high", score: 87 },
    targetType: 1,
    target: { name: "前线要塞", force: 123, unionId: 0 },
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
          ...portrait(100705),
          skills: [{ id: 200001, name: "文伐", level: 10 }],
        },
        {
          id: 100707,
          name: "卫瓘",
          level: 50,
          advance: 5,
          ...portrait(100707, false),
          skills: [{ id: 200002, name: "避其锋芒", level: 10 }],
        },
        {
          id: 100101,
          name: "灵帝",
          level: 50,
          advance: 5,
          ...portrait(100101),
          skills: [{ id: 200003, name: "道行险阻", level: 10 }],
        },
      ],
    },
    offline: null,
  };
  const unknownLiveArmy = {
    ...exactLiveArmy,
    armyId: 814501,
    userId: 7002,
    ownerName: "守望者",
    ownerUnionName: "乙盟",
    state: 5,
    stateKey: "reside",
    stateLabel: "驻守",
    stateCategory: "stationary",
    isMoving: false,
    location: {
      currentWid: 2081468,
      nextWid: 0,
      targetWid: 2081468,
      fromWid: 2081468,
      resideWid: 2081468,
      stayWid: 0,
      source: "reside",
    },
    timing: { beginTime: 0, nextTime: 0, endTime: 0 },
    march: null,
    risk: { level: "low", score: 12 },
    source: {
      seq: 88,
      observedAtMs: Date.now() - 80 * 60_000,
      ageMs: 80 * 60_000,
      freshness: "stale",
      isStale: true,
      cmdId: 5026,
    },
    lineup: {
      status: "unknown",
      complete: false,
      battleId: 0,
      battleTime: 0,
      battleTimeText: "",
      side: "",
      heroes: [],
      message: "无同 ID 战报，阵容未知",
    },
  };
  const unknownStateArmy = {
    ...unknownLiveArmy,
    armyId: 990099,
    ownerName: "侦察目标",
    state: 99,
    stateKey: "unknown",
    stateLabel: "状态 99",
    stateCategory: "unknown",
    location: {
      ...unknownLiveArmy.location,
      currentWid: 2081458,
      targetWid: 2081458,
      resideWid: 0,
      source: "from",
    },
    source: {
      seq: 100,
      observedAtMs: Date.now() - 4 * 60_000,
      ageMs: 4 * 60_000,
      freshness: "aging",
      isStale: false,
      cmdId: 5028,
    },
  };
  const recentOfflineArmy = {
    ...unknownLiveArmy,
    armyId: 773311,
    ownerName: "离线样本",
    state: 4,
    stateKey: "returning",
    stateLabel: "返回中",
    stateCategory: "moving",
    isMoving: true,
    location: {
      ...unknownLiveArmy.location,
      currentWid: 2081448,
      targetWid: 2081401,
      source: "real-march",
    },
    offline: {
      deletedAtMs: Date.now() - 65_000,
      ageMs: 65_000,
      sourceCmd: 5028,
      sourceLabel: "5028 增量",
      serverOrderId: 701,
    },
    source: {
      seq: 99,
      observedAtMs: Date.now() - 9 * 60_000,
      ageMs: 9 * 60_000,
      freshness: "aging",
      isStale: false,
      cmdId: 5028,
    },
  };
  const liveArmySnapshot = (ownerName = exactLiveArmy.ownerName) => ({
    ok: true,
    generatedAtMs: Date.now(),
    worldStateObservedAtMs: Date.now() - 5_000,
    worldStateAgeMs: 5_000,
    worldStateVersion: 7,
    freshness: "fresh",
    completeness: "full-baseline",
    summary: {
      current: 3,
      usableCurrent: 2,
      staleCurrent: 1,
      moving: 1,
      stationary: 1,
      exactLineups: 1,
      unknownLineups: 2,
      recentOffline: 1,
    },
    bounds: {
      rowUp: 111,
      rowDown: 212,
      colLeft: 1296,
      colRight: 1485,
    },
    current: [
      {...exactLiveArmy, ownerName},
      unknownLiveArmy,
      unknownStateArmy,
    ],
    recentOffline: [recentOfflineArmy],
  });
  let liveArmyRequests = 0;
  const simulatorHeroes = [
    { id: 100027, name: "张辽", camp: 2, army: 3, quality: 4, ...portrait(100027) },
    { id: 100016, name: "刘备", camp: 1, army: 2, quality: 4, ...portrait(100016) },
    { id: 100090, name: "太史慈", camp: 3, army: 1, quality: 4, ...portrait(100090) },
    { id: 100013, name: "马超", camp: 5, army: 3, quality: 4, ...portrait(100013) },
    { id: 100649, name: "魏延", camp: 1, army: 2, quality: 4, ...portrait(100649, false) },
    { id: 100023, name: "曹操", camp: 2, army: 3, quality: 4, ...portrait(100023) },
  ];
  const simulatorSkills = [
    {
      id: 200001, name: "衣带密诏", desc: "攻击提升与恢复",
      skill_type: 2, level: "C", study: true,
    },
    {
      id: 200027, name: "其疾如风", desc: "速度提升与连击",
      skill_type: 1, level: "S", study: true,
    },
  ];
  const simulatorCatalog = {
    ok: true,
    heroes: simulatorHeroes,
    skills: simulatorSkills,
  };
  const simulatorEngine = {
    ok: true,
    name: "stzb-kotlin",
    sourceCommit: "93ee999937d011b2a3dadf67ed39edfbb409aaca",
    generatedAt: "2026-08-15T03:43:50+08:00",
    maxRepeat: 1000,
    repeatOptions: [1, 100, 1000],
    supportsDetailedReplay: true,
  };
  const attackerRef = { side: "ATTACKER", position: 0, heroId: 100016 };
  const defenderRef = { side: "DEFENDER", position: 2, heroId: 100023 };
  const simulatorEvents = [
    { eventSeq: 0, phase: "PREPARATION", round: 0, type: "BattleStart", payload: { type: "BattleStart" } },
    {
      eventSeq: 1, phase: "PREPARATION", round: 0, type: "StatChanged",
      source: attackerRef, target: attackerRef, rootSkillId: 200027,
      skillId: 200027, effectId: 104, stat: "SPEED", unit: "FLAT",
      deltaExact: 25, valueAfterExact: 225,
      payload: { type: "StatChanged", round: 0, skillId: 200027, effectId: 104 },
    },
    { eventSeq: 2, phase: "BATTLE", round: 1, type: "RoundStart", payload: { type: "RoundStart", round: 1 } },
    {
      eventSeq: 3, phase: "BATTLE", round: 1, type: "HeroActionStart",
      source: attackerRef, payload: { type: "HeroActionStart", round: 1 },
    },
    {
      eventSeq: 4, phase: "BATTLE", round: 1, type: "SkillDamage",
      source: attackerRef, target: defenderRef, rootSkillId: 200001,
      skillId: 200001, effectId: 301, damage: 634,
      targetTroopsAfter: 8366,
      payload: { type: "SkillDamage", round: 1, skillId: 200001, effectId: 301 },
    },
    {
      eventSeq: 5, phase: "BATTLE", round: 1, type: "EffectBlocked",
      source: attackerRef, target: defenderRef, rootSkillId: 200001,
      skillId: 200001, effectId: 401, blockingEffectId: 207,
      payload: {
        type: "EffectBlocked", round: 1, skillId: 200001,
        effectId: 401, blockingEffectId: 207,
      },
    },
    {
      eventSeq: 6, phase: "BATTLE", round: 1, type: "HeroActionEnd",
      source: attackerRef, payload: { type: "HeroActionEnd", round: 1 },
    },
    { eventSeq: 7, phase: "BATTLE", round: 1, type: "RoundEnd", payload: { type: "RoundEnd", round: 1 } },
    {
      eventSeq: 8, phase: "FINAL", round: 0, type: "BattleEnd",
      payload: { type: "BattleEnd", outcome: "ATTACKER_WIN" },
    },
  ];
  const simulatorActions = [
    { actionSeq: 0, actionId: 654, params: [], encoded: "i6" },
    { actionSeq: 1, actionId: 641, params: [], encoded: "ht" },
    { actionSeq: 2, actionId: 631, params: [], encoded: "hj" },
    { actionSeq: 3, actionId: 632, params: [], encoded: "hk" },
    { actionSeq: 4, actionId: 640, params: [], encoded: "hs" },
    { actionSeq: 5, actionId: 635, params: [], encoded: "hn" },
    { actionSeq: 6, actionId: 634, params: [], encoded: "hm" },
    { actionSeq: 7, actionId: 639, params: [], encoded: "hr" },
    { actionSeq: 8, actionId: 637, params: [], encoded: "hp" },
    { actionSeq: 9, actionId: 4, params: [], encoded: "04" },
    { actionSeq: 10, actionId: 651, params: [], encoded: "i3" },
    { actionSeq: 11, actionId: 8, params: [], encoded: "08" },
    { actionSeq: 12, actionId: 9, params: [1], encoded: "091" },
    { actionSeq: 13, actionId: 10, params: [1], encoded: "0a1" },
    { actionSeq: 14, actionId: 60, params: [1, 200001, 4, 634, 8366], encoded: "1o1,200001,4,634,8366" },
    { actionSeq: 15, actionId: 210, params: [4, 207], encoded: "5u4,207" },
    { actionSeq: 16, actionId: 11, params: [1], encoded: "0b1" },
  ];
  const simulatorSnapshots = simulatorHeroes.map((hero, index) => ({
    round: 1,
    side: index < 3 ? "ATTACKER" : "DEFENDER",
    position: index < 3 ? index : index - 3,
    heroId: hero.id,
    troops: index === 5 ? 8366 : 9000,
    roundDamageTaken: index === 5 ? 634 : 0,
    cumulativeDamageTaken: index === 5 ? 634 : 0,
    roundRecovery: 0,
    cumulativeRecovery: 0,
    alive: true,
    activeStatuses: [],
  }));
  const simulatorFirstRun = {
    outcome: "ATTACKER_WIN",
    attackerRemain: 27000,
    defenderRemain: 26366,
    roundsPlayed: 1,
    attackerHeroes: simulatorSnapshots.slice(0, 3).map((hero) => ({
      heroId: hero.heroId, position: hero.position, troops: hero.troops,
      initialTroops: 9000, hurt: 9000 - hero.troops, alive: true,
    })),
    defenderHeroes: simulatorSnapshots.slice(3).map((hero) => ({
      heroId: hero.heroId, position: hero.position, troops: hero.troops,
      initialTroops: 9000, hurt: 9000 - hero.troops, alive: true,
    })),
    entrySnapshots: simulatorSnapshots.map((hero) => ({
      ...hero, round: 0, troops: 9000, roundDamageTaken: 0, cumulativeDamageTaken: 0,
    })),
    roundSnapshots: simulatorSnapshots,
    finalSnapshots: simulatorSnapshots,
    events: simulatorEvents,
    replayActions: simulatorActions,
    replayText: simulatorActions.map((action) => action.encoded).join("#"),
    diagnostics: {
      unsupportedSkillEffects: [],
      unsupportedEquipmentEffects: [],
      unprojectedReplayEvents: [],
      semanticEventCount: simulatorEvents.length,
      replayActionCount: simulatorActions.length,
    },
  };
  const maliciousDomPayload =
    String.raw`payload'"\\ onmouseover="window.__unsafePayload=1`;
  const maliciousGroupName =
    String.raw`攻城组'"\\"><img src=x onerror="window.__unsafePayload=2">`;
  const maliciousCompleteness =
    String.raw`missing" onmouseover="window.__unsafePayload=3`;
  const maliciousTaskId =
    String.raw`task'"\) ; window.__taskXss=1;//`;
  const taskActionRequests = [];
  let maliciousTaskCompleteUserNum = 0;
  await page.route("**/api/command-center/overview", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        metrics: {
          battlesToday: 1,
          activeArmies: 1,
          allianceMembers: 1,
          knownTiles: 1,
          battlesTotal: 1,
        },
        battles: [],
        armies: [],
        alerts: [{
          id: maliciousDomPayload,
          level: maliciousDomPayload,
          kind: maliciousDomPayload,
          entityType: "wid",
          entityId: maliciousDomPayload,
          title: maliciousDomPayload,
          message: maliciousDomPayload,
        }],
        profile: { roleName: maliciousDomPayload, serverName: "S1" },
        freshness: { generatedAt: Math.floor(Date.now() / 1000) },
      }),
    }),
  );
  await page.route("**/api/hud/health", (route) => {
    const stale = fixtureCounters.health > 0;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        overall: stale ? "stale" : "live",
        components: {
          backend: {
            status: stale ? "stale" : "live",
            label: "后端",
            detail: stale ? "最后成功响应在 12 分钟前" : "Flask API 可用",
            updatedAt: stale ? Date.now() - 12 * 60_000 : Date.now(),
          },
          writer: { status: "live", label: "实时入库", detail: "errors=0" },
          battleEngine: { status: "live", label: "Kotlin 引擎", detail: "ready" },
          portraits: { status: "live", label: "画像资源", detail: "ready" },
        },
      }),
    });
  });
  await page.route("**/api/state_region_stats?*", async (route) => {
    if (await fulfillQueuedLifecycleResponse(route, "legacyRegion")) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        meta: {},
        groups: ["一团"],
        summary: {
          total_players: 18,
          total_power: 360000,
          state_count: 2,
          alliance_count: 1,
          group_count: 1,
          grouped_players: 18,
        },
        state_rows: [
          {
            state: "益州",
            player_count: 12,
            total_power: 240000,
            avg_power: 20000,
            max_power: 30000,
          },
          {
            state: "荆州",
            player_count: 6,
            total_power: 120000,
            avg_power: 20000,
            max_power: 26000,
          },
        ],
        group_rows: [{
          alliance_name: "甲盟",
          group_name: "一团",
          player_count: 18,
          total_power: 360000,
          state_summary: "益州 12 / 荆州 6",
        }],
        alliance_rows: [],
      }),
    });
  });
  await page.route(/\/api\/tasks\/[^/?]+(?:\/statistics)?$/, (route) => {
    const request = route.request();
    const path = decodeURIComponent(new URL(request.url()).pathname);
    const method = request.method();
    taskActionRequests.push({ method, path });
    if (method === "GET" && path === "/api/tasks/1") {
      return route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          id: 1,
          status: 0,
          name: "集结测试",
          time: Math.floor(Date.now() / 1000) + 3600,
          pos: "10004",
          pos_xy: "1,4",
          wid_name: "七级资源地",
          target_groups: ["一团"],
          target_user_num: 20,
          complete_user_num: 6,
          user_list: {
            1001: {
              uid: 1001,
              name: "测试成员",
              group: "一团",
              atk_num: 1,
              dis_num: 0,
              atk_team_num: 1,
              dis_team_num: 0,
            },
          },
        }),
      });
    }
    if (method === "POST" && path === "/api/tasks/1/statistics") {
      return route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({ ok: true, msg: "统计完成，实到1人" }),
      });
    }
    if (method === "DELETE" && path === "/api/tasks/1") {
      return route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({ ok: true, msg: "删除成功" }),
      });
    }
    return route.fulfill({
      status: 400,
      contentType: "application/json",
      body: JSON.stringify({ error: "unexpected task action request" }),
    });
  });
  await page.route("**/api/tasks", async (route) => {
    if (route.request().method() !== "GET") return route.continue();
    if (await fulfillQueuedLifecycleResponse(route, "legacyTasks")) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify([
        {
          id: 1,
          status: 0,
          name: "集结测试",
          time: Math.floor(Date.now() / 1000) + 3600,
          pos: "10004",
          pos_xy: "1,4",
          wid_name: "七级资源地",
          target_groups: ["一团"],
          target_user_num: 20,
          complete_user_num: 6,
        },
        {
          id: 2,
          status: 1,
          name: "已完成任务",
          time: Math.floor(Date.now() / 1000) - 3600,
          pos: "10005",
          pos_xy: "1,5",
          wid_name: "城池",
          target_groups: [],
          target_user_num: 18,
          complete_user_num: 18,
        },
        {
          id: maliciousTaskId,
          status: 0,
          name: "恶意任务 ID",
          time: Math.floor(Date.now() / 1000) + 7200,
          pos: "10006",
          pos_xy: "1,6",
          wid_name: "测试地块",
          target_groups: ["安全组"],
          target_user_num: 1,
          complete_user_num: maliciousTaskCompleteUserNum,
        },
      ]),
    });
  });
  const organizationTeamRows = [{
    player_name: "玩家甲",
    union: "甲盟",
    union_name: "甲盟",
    clan_name: "一团",
    side: "atk",
    heroes_str: "100027+100016+100090",
    skills: "200001,200027,200001,200027,200001,200027,200001,200027,200001",
    hero_stars: [5, 5, 5],
    hero_levels: "40,40,40",
    cnt: 12,
    wins: 8,
    draws: 1,
    win_rate: 70.8,
    max_troops: 27000,
  }];
  await page.route("**/api/player_battle_teams?*", async (route) => {
    const response = organizationResponseQueue.shift() || { kind: "success" };
    response.markStarted?.();
    await response.gate;
    if (response.kind === "error") {
      return route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({ error: "玩家队伍临时不可用" }),
      });
    }
    const rows = response.kind === "empty"
      ? []
      : response.kind === "stale"
        ? [{
            ...organizationTeamRows[0],
            player_name: "陈旧玩家",
            isStale: true,
          }]
        : organizationTeamRows;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(rows),
    });
  });
  await page.route("**/api/team_users", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify([{
        uid: 1,
        name: "玩家甲",
        union_name: "甲盟",
        group_name: "一团",
      }]),
    }),
  );
  await page.route("**/api/team_groups", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(["一团", maliciousGroupName]),
    }),
  );
  await page.route("**/api/team_report?*", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        summary: {
          total_battles: 18,
          win_rate: 66.7,
          total_players: 12,
          total_draws: 2,
          total_city: 4,
          total_gongxun: 8200,
        },
        rows: [{
          name: "一团",
          player_cnt: 12,
          battles: 18,
          wins: 11,
          loses: 5,
          draws: 2,
          win_rate: 66.7,
          city_battles: 4,
          total_gongxun: 8200,
          avg_gongxun: 683,
          avg_power: 32000,
        }],
      }),
    }),
  );
  await page.route("**/api/heroes/combo_winrate?*", async (route) => {
    if (await fulfillQueuedLifecycleResponse(route, "legacyCombo")) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify([
        { combo: "张辽+刘备+太史慈", total: 18, win: 11, lose: 5, draw: 2, win_rate: 66.7 },
        { combo: "马超+魏延+曹操", total: 15, win: 8, lose: 5, draw: 2, win_rate: 60 },
        { combo: "张辽+马超+曹操", total: 12, win: 6, lose: 4, draw: 2, win_rate: 58.3 },
      ]),
    });
  });
  await page.route("**/api/simulate/heroes", async (route) => {
    if (await fulfillQueuedLifecycleResponse(route, "simulatorCatalog")) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(simulatorCatalog),
    });
  });
  await page.route("**/api/simulate/engine", async (route) => {
    if (await fulfillQueuedLifecycleResponse(route, "simulatorEngine")) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(simulatorEngine),
    });
  });
  await page.route("**/api/simulate", async (route) => {
    if (await fulfillQueuedLifecycleResponse(route, "simulation")) return;
    const request = route.request().postDataJSON();
    const repeat = Number(request.repeat || 1);
    fixtureCounters.simulation += 1;
    const firstRun = fixtureCounters.simulation === 2
      ? {
          ...simulatorFirstRun,
          diagnostics: {
            ...simulatorFirstRun.diagnostics,
            unsupportedSkillEffects: [{ skillId: 200914, effectId: 901 }],
          },
        }
      : simulatorFirstRun;
    const body = repeat === 1
      ? {
          ok: true,
          engine: "stzb-kotlin",
          engineResult: { ok: true, repeat, firstRun },
          result: {
            winner: "攻方胜",
            rounds_played: 1,
            blue: { total_arms: 27000, hurt_arms: 0, heros: [] },
            red: { total_arms: 26366, hurt_arms: 634, heros: [] },
            records: [],
            replay: firstRun,
          },
        }
      : {
          ok: true,
          engine: "stzb-kotlin",
          repeat,
          blue_wins: 63,
          red_wins: 29,
          draws: 8,
          blue_rate: 63,
          red_rate: 29,
          draw_rate: 8,
          firstRun,
        };
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(body),
    });
  });
  await page.route("**/api/intelligence/world/summary", async (route) => {
    fixtureCounters.intelligence.summary += 1;
    if (await fulfillQueuedLifecycleResponse(route, "intelligenceSummary")) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ...worldEnvelope,
        counts: { tiles: 1, armies: 1, marches: 0, events: 1 },
        dataBounds: { rowUp: 1, rowDown: 160, colLeft: 1, colRight: 160 },
        focusWid: 10004,
        suggestedBounds: { rowUp: 0, rowDown: 19, colLeft: 0, colRight: 19 },
      }),
    });
  });
  await page.route("**/api/intelligence/world/overview?*", async (route) => {
    fixtureCounters.intelligence.overview += 1;
    if (await fulfillQueuedLifecycleResponse(route, "intelligenceOverview")) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ...worldEnvelope,
        dataBounds: { rowUp: 1, rowDown: 160, colLeft: 1, colRight: 160 },
        bucketRows: 20,
        bucketCols: 20,
        buckets: [{
          rowUp: 61, rowDown: 100, colLeft: 61, colRight: 100,
          tileCount: 46, riskMax: 72, riskAverage: 31.4,
          selfCount: 12, allyCount: 9, enemyCount: 18,
          unknownCount: 7, unownedCount: 0, armyCount: 3,
          changeCount: 8, focusWid: 10004,
        }],
      }),
    });
  });
  await page.route("**/api/intelligence/world/viewport?*", async (route) => {
    fixtureCounters.intelligence.viewport += 1;
    if (await fulfillQueuedLifecycleResponse(route, "intelligenceViewport")) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ...worldEnvelope,
        tiles: [{
          wid: 10004, row: 1, col: 4, name: "七级资源地",
          landLevel: 7, resourceKind: 3, freshness: "fresh",
          user_id: 42, protect_end_time: 0, guard_end_time: 0,
        }],
      }),
    });
  });
  await page.route("**/api/intelligence/world/risks?*", async (route) => {
    fixtureCounters.intelligence.risks += 1;
    if (await fulfillQueuedLifecycleResponse(route, "intelligenceRisks")) return;
    const highRisk = fixtureCounters.risk > 0;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ...worldEnvelope,
        risks: [{
          wid: 10004,
          score: highRisk ? 72 : 38,
          level: highRisk ? "high" : "medium",
          confidence: 0.71,
          freshness: "fresh",
          components: {
            landLevel: 21, enemyOwnership: 15, incomingArmyCount: 14,
            earliestArrival: 15, estimatedTroops: 0, protectionGuard: 0,
            staleIntel: 0,
          },
          unknownComponents: ["estimatedTroops"],
        }],
      }),
    });
  });
  await page.route("**/api/intelligence/world/events?*", async (route) => {
    fixtureCounters.intelligence.events += 1;
    if (await fulfillQueuedLifecycleResponse(route, "intelligenceEvents")) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ...worldEnvelope,
        events: [{
          seq: 1, state_version: 7, event_type: "snapshot_completed",
          entity_id: "7", observed_at_ms: Date.now(), evidence: {}, diff: {},
        }],
      }),
    });
  });
  await page.route("**/api/intelligence/world/tile/10004", async (route) => {
    fixtureCounters.intelligence.detail += 1;
    if (
      await fulfillQueuedLifecycleResponse(route, "intelligenceDetail:10004")
    ) return;
    const highRisk = fixtureCounters.risk > 0;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ...worldEnvelope,
        tile: {
          wid: 10004, row: 1, col: 4, name: "七级资源地",
          landLevel: 7, resourceKind: 3, freshness: "fresh",
        },
        incomingArmies: [{ army_id: 100, user_id: 42, wid_to: 10004, end_time: 1_900_000_000 }],
        risk: {
          score: highRisk ? 72 : 38,
          level: highRisk ? "high" : "medium",
          confidence: 0.71,
          components: { landLevel: 21, incomingArmyCount: 14 },
          unknownComponents: ["estimatedTroops"],
        },
        battleStats: {
          sampleSize: 1,
          attackWinRate: 100,
          attackWins: 1,
          attackDraws: 0,
          attackLosses: 0,
          commonLineups: [{
            key: maliciousDomPayload,
            names: [maliciousDomPayload],
            sampleSize: 1,
          }],
          recentBattles: [{
            battle_id: 1,
            atk_name: maliciousDomPayload,
            def_name: maliciousDomPayload,
          }],
        },
        events: [],
      }),
    });
  });
  await page.route("**/api/intelligence/world/tile/2081480", async (route) => {
    fixtureCounters.intelligence.detail += 1;
    if (
      await fulfillQueuedLifecycleResponse(route, "intelligenceDetail:2081480")
    ) return;
    const wid = Number(route.request().url().split("/").pop()) || 0;
    const row = Math.floor(wid / 10000);
    const col = wid % 10000;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ...worldEnvelope,
        tile: {
          wid,
          row,
          col,
          name: `实时部队格 ${wid}`,
          landLevel: 0,
          resourceKind: 0,
          freshness: "fresh",
        },
        incomingArmies: [],
        risk: {
          score: 24,
          level: "low",
          confidence: 0.9,
          components: {},
          unknownComponents: [],
        },
        events: [],
      }),
    });
  });
  await page.route("**/api/intelligence/live-armies?*", async (route) => {
    liveArmyRequests += 1;
    if (await fulfillQueuedLifecycleResponse(route, "legacyLiveArmy")) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(liveArmySnapshot()),
    });
  });
  await page.route("**/api/world/marches", async (route) => {
    fixtureCounters.intelligence.march += 1;
    if (await fulfillQueuedLifecycleResponse(route, "intelligenceScene:march")) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        marches: [{
          real_march_id: 9001,
          last_wid: 10001,
          current_wid: 10004,
          next_wid: 10005,
          start_time: 1_900_000_000,
          next_time: 1_900_000_030,
          end_time: 1_900_000_060,
          path_id: 77,
          unit_time_cost: 3,
          march_type: 1,
          belong_id: 42,
        }],
      }),
    });
  });
  await page.route("**/api/world/armies", async (route) => {
    fixtureCounters.intelligence.army += 1;
    if (await fulfillQueuedLifecycleResponse(route, "intelligenceScene:army")) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        armies: [{
          army_id: 100,
          state: 1,
          user_id: 42,
          owner_name: "主公",
          owner_union_name: "同盟",
          wid_from: 10001,
          wid_to: 10004,
          target_name: "七级资源地",
          target_type: 1,
          reside_wid: 10001,
          stay_wid: 10004,
          army_hero_type: "骑",
          morale: 100,
          buff_ids: "",
          obstacle_wid: 0,
          real_march_id: 9001,
          begin_time: 1_900_000_000,
          end_time: 1_900_000_060,
          battle_show: "",
        }],
      }),
    });
  });
  await page.route("**/api/world/entities", async (route) => {
    fixtureCounters.intelligence.entity += 1;
    if (await fulfillQueuedLifecycleResponse(route, "intelligenceScene:entity")) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        entities: [{
          category: "war_ship",
          entity_id: 801,
          source_seq: 7,
          raw: [1, 42],
        }],
      }),
    });
  });
  const cardPackSummary = {
    packId: 802,
    parentPackId: 0,
    containerPackId: 0,
    priority: 8,
    heroCount: 2,
    sourceConfigs: ["2", "5"],
    heroPreview: [
      { heroid: 100027, name: "张辽" },
      { heroid: 100016, name: "刘备" },
    ],
  };
  await page.route("**/api/intelligence/card-packs?*", (route) => {
    const params = new URL(route.request().url()).searchParams;
    const query = params.get("q") || "";
    const heroId = params.get("heroId");
    const rows = (query === "" || query.includes("802") || heroId === "100027")
      ? [cardPackSummary]
      : [];
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        datasetVersion: "client-9.2.2-research",
        evidenceClass: "CONFIG_FACT",
        total: rows.length,
        page: 1,
        size: 80,
        rows,
      }),
    });
  });
  await page.route("**/api/intelligence/card-packs/802", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        datasetVersion: "client-9.2.2-research",
        evidenceClass: "CONFIG_FACT",
        ...cardPackSummary,
        heroIds: [100027, 100016],
        heroes: [
          {
            heroid: 100027, name: "张辽", country_name: "魏",
            hero_type: 3, hit_range: 3, quality_name: "五星",
          },
          {
            heroid: 100016, name: "刘备", country_name: "蜀",
            hero_type: 2, hit_range: 3, quality_name: "五星",
          },
        ],
        countryDistribution: [
          { name: "魏", count: 1 },
          { name: "蜀", count: 1 },
        ],
        heroTypeDistribution: [
          { name: "2", count: 1 },
          { name: "3", count: 1 },
        ],
        children: [],
      }),
    }),
  );
  await page.route("**/api/query-agent/messages", (route) => {
    const message = route.request().postDataJSON()?.message || "";
    if (message.includes("恶意动作")) {
      return route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          ok: true,
          answer: maliciousDomPayload,
          evidence: [{
            source: maliciousDomPayload,
            label: maliciousDomPayload,
            entityType: maliciousDomPayload,
            entityId: maliciousDomPayload,
            freshness: maliciousDomPayload,
          }],
          uiActions: [{
            type: "open",
            route: "intelligence-research",
            params: { packId: 802, marker: maliciousDomPayload },
          }],
        }),
      });
    }
    if (message.includes("卡包")) {
      return route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          ok: true,
          answer: "卡包 802 收录 2 名武将。",
          evidence: [{
            source: "client-9.2.2-research",
            label: "客户端卡包武将池",
            entityType: "card-pack",
            entityId: "802",
            freshness: "versioned",
          }],
          uiActions: [{
            type: "open",
            route: "intelligence-research",
            params: { packId: 802 },
          }],
        }),
      });
    }
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        answer: "WID 10004 当前有 1 支行军。",
        evidence: [],
        uiActions: [],
      }),
    });
  });
  const maliciousScorePlayerName = `玩家' onmouseover='window.__scoreXss=1`;
  const scoreRows = [{
    rank: 1,
    playerName: maliciousScorePlayerName,
    playerUid: "1",
    unionName: "甲盟",
    groupName: "一团",
    score: 39.5,
    battleScore: 22,
    siegeScore: 16,
    adjustmentScore: 1.5,
    dataCompleteness: maliciousCompleteness,
    missingSources: [],
    metrics: {
      battles: 10, wins: 4, draws: 2, gongxunTotal: 3000,
      mainCityCnt: 2, tearCnt: 1, attendanceCnt: 3,
    },
  }, {
    rank: 2,
    playerName: "下降样本",
    playerUid: "2",
    unionName: "甲盟",
    groupName: "一团",
    score: 30,
    battleScore: 18,
    siegeScore: 12,
    adjustmentScore: 0,
    dataCompleteness: "complete",
    missingSources: [],
    metrics: {
      battles: 8, wins: 3, draws: 1, gongxunTotal: 2200,
      mainCityCnt: 1, tearCnt: 1, attendanceCnt: 2,
    },
  }, {
    rank: 3,
    playerName: "未变化样本",
    playerUid: "3",
    unionName: "甲盟",
    groupName: "一团",
    score: 20,
    battleScore: 12,
    siegeScore: 8,
    adjustmentScore: 0,
    dataCompleteness: "complete",
    missingSources: [],
    metrics: {
      battles: 6, wins: 2, draws: 1, gongxunTotal: 1600,
      mainCityCnt: 1, tearCnt: 0, attendanceCnt: 1,
    },
  }];
  const recalculatedScoreRows = [
    { ...scoreRows[0], score: 49, rank: 1 },
    { ...scoreRows[1], score: 24, rank: 2 },
    { ...scoreRows[2], score: 20, rank: 3 },
  ];
  await page.route("**/api/custom_scores?*", (route) => {
    const board = new URL(route.request().url()).searchParams.get("board") || "overall";
    const rows = fixtureCounters.scores > 0 ? recalculatedScoreRows : scoreRows;
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        seasonId: "current",
        board,
        ruleVersion: 1,
        dataCompleteness: "complete",
        missingSources: [],
        summary: {
          players: rows.length,
          scoreTotal: rows.reduce((total, row) => total + row.score, 0),
          battleTotal: rows.reduce((total, row) => total + row.battleScore, 0),
          siegeTotal: 16, adjustmentTotal: 1.5,
          dataCompleteness: "complete", missingSources: [],
        },
        rows,
      }),
    });
  });
  await page.route("**/api/custom_scores/rules?*", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        seasonId: "current",
        presets: {
          alliance_contribution: {
            battleWeight: 1, winWeight: 2, drawWeight: 0.5,
            gongxunDivisor: 1000, mainCityWeight: 5,
            tearWeight: 3, attendanceWeight: 1,
          },
          season_reward: {
            battleWeight: 1.5, winWeight: 2.5, drawWeight: 0.5,
            gongxunDivisor: 800, mainCityWeight: 8,
            tearWeight: 4, attendanceWeight: 2,
          },
          siege_priority: {
            battleWeight: 0.5, winWeight: 1, drawWeight: 0.25,
            gongxunDivisor: 2000, mainCityWeight: 12,
            tearWeight: 7, attendanceWeight: 4,
          },
        },
        activeRule: {
          id: 1, version: 1, name: "同盟综合贡献",
          preset_key: "alliance_contribution",
          config: {
            battleWeight: 1, winWeight: 2, drawWeight: 0.5,
            gongxunDivisor: 1000, mainCityWeight: 5,
            tearWeight: 3, attendanceWeight: 1,
          },
        },
        rules: [],
      }),
    }),
  );
  await page.route("**/api/custom_scores/player/**", async (route) => {
    const requestedPlayer = decodeURIComponent(
      new URL(route.request().url()).pathname.split("/").at(-1),
    );
    if (await fulfillQueuedScoreResponse(route, "player")) return;
    assert.equal(requestedPlayer, maliciousScorePlayerName);
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        ...scoreRows[0],
        breakdown: {
          metrics: scoreRows[0].metrics,
          components: {
            battles: 10, wins: 8, draws: 1, gongxun: 3,
            mainCity: 10, tear: 3, attendance: 3,
          },
        },
        rule: { version: 1 },
        adjustments: [{ points: 1.5, reason: "组织奖励" }],
      }),
    });
  });
  await page.route("**/api/custom_scores/preview", async (route) => {
      if (await fulfillQueuedScoreResponse(route, "preview")) return;
      const requestBody = route.request().postDataJSON();
      assert.equal(requestBody.startDate, "2026-08-01");
      assert.equal(requestBody.endDate, "2026-08-15");
      return route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          ok: true,
          previewToken: "preview-1",
          dateRange: { startDate: "2026-08-01", endDate: "2026-08-15" },
          summary: {
            players: 3, scoreTotal: 93, battleTotal: 52,
            siegeTotal: 16, adjustmentTotal: 1.5,
          },
          rows: [
            {
              ...recalculatedScoreRows[0],
              oldScore: scoreRows[0].score,
              scoreDelta: 9.5,
              oldRank: 1, newRank: 1, rankDelta: 0,
              breakdown: {},
            },
            {
              ...recalculatedScoreRows[1],
              oldScore: scoreRows[1].score,
              scoreDelta: -6,
              oldRank: 2, newRank: 2, rankDelta: 0,
              breakdown: {},
            },
            {
              ...recalculatedScoreRows[2],
              oldScore: scoreRows[2].score,
              scoreDelta: 0,
              oldRank: 3, newRank: 3, rankDelta: 0,
              breakdown: {},
            },
          ],
        }),
      });
    },
  );
  await page.route("**/api/custom_scores/recalc", async (route) => {
    if (await fulfillQueuedScoreResponse(route, "recalc")) return;
    fixtureCounters.scores += 1;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ ok: true, updated: 3, ruleVersion: 1 }),
    });
  });
  await page.route("**/api/custom_scores/adjustments", async (route) => {
    if (route.request().method() === "POST") {
      if (await fulfillQueuedScoreResponse(route, "adjustment")) return;
      route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          ok: true,
          adjustment: { id: 1, points: 5, reason: "组织奖励" },
        }),
      });
    } else {
      route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({ ok: true, rows: [] }),
      });
    }
  });
  await page.route("**/api/custom_scores/rules", async (route) => {
    if (route.request().method() === "POST") {
      if (await fulfillQueuedScoreResponse(route, "ruleCreate")) return;
      route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          ok: true,
          rule: { id: 2, version: 2, name: "新规则" },
        }),
      });
    } else route.continue();
  });
  await page.route("**/api/custom_scores/rules/2/activate", async (route) => {
    if (await fulfillQueuedScoreResponse(route, "ruleActivate")) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ ok: true, rule: { id: 2, version: 2 } }),
    });
  });
  const lineupSummary = {
    key: "100027.100016.100090",
    configFacts: {
      evidenceClass: "CONFIG_FACT",
      datasetVersion: "client-9.2.2",
      heroes: [
        { position: 0, heroId: 100027, name: "张辽", level: 40, resolved: true },
        { position: 1, heroId: 100016, name: "刘备", level: 40, resolved: true },
        { position: 2, heroId: 100090, name: "太史慈", level: 40, resolved: true },
      ],
    },
    battleStats: {
      evidenceClass: "BATTLE_STAT",
      sampleSize: 18,
      wins: 10,
      draws: 2,
      losses: 6,
      winRate: 61.1,
      latestBattleTime: 1_900_000_000,
    },
    confidence: {
      label: "medium",
      sampleSize: 18,
      minimumRecommendedSample: 10,
      notice: "样本达到基础参考线，仍需结合对手与战法配置判断。",
    },
  };
  const lineupDetail = {
    ok: true,
    ...lineupSummary,
    datasetVersion: "client-9.2.2",
    battleStats: {
      ...lineupSummary.battleStats,
      commonOpponents: [{
        key: "100013.100649.100023",
        sampleSize: 7,
        wins: 4,
        draws: 1,
        losses: 2,
        winRate: 64.3,
      }],
    },
    simulationLink: {
      evidenceClass: "SIMULATION",
      hasResult: false,
      notice: "尚未运行模拟；模拟结果不等同于真实历史胜率。",
      lineup: {
        morale: 100,
        heroes: [
          { id: 100027, level: 40, up: 5, equip_skills: [] },
          { id: 100016, level: 40, up: 5, equip_skills: [] },
          { id: 100090, level: 40, up: 5, equip_skills: [] },
        ],
      },
    },
  };
  await page.route("**/api/intelligence/lineups?*", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        datasetVersion: "client-9.2.2",
        evidenceClass: "BATTLE_STAT",
        total: 1,
        page: 1,
        size: 8,
        rows: [lineupSummary],
      }),
    }),
  );
  await page.route(/\/api\/intelligence\/lineups\/[^/?]+$/, async (route) => {
    const lineupKey = decodeURIComponent(
      new URL(route.request().url()).pathname.split("/").pop(),
    );
    if (
      await fulfillQueuedLifecycleResponse(
        route,
        `researchLineup:${lineupKey}`,
      )
    ) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(lineupDetail),
    });
  });
  const researchHeroes = [
    { heroid: 100027, name: "张辽", country_name: "魏", quality_name: "五星", hit_range: 3, skill_init: 200027 },
    { heroid: 100016, name: "刘备", country_name: "蜀", quality_name: "五星", hit_range: 3, skill_init: 200016 },
    { heroid: 100090, name: "太史慈", country_name: "吴", quality_name: "五星", hit_range: 4, skill_init: 200090 },
    { heroid: 100013, name: "马超", country_name: "群", quality_name: "五星", hit_range: 3, skill_init: 200013 },
    { heroid: 100649, name: "魏延", country_name: "蜀", quality_name: "五星", hit_range: 3, skill_init: 200649 },
    { heroid: 100023, name: "曹操", country_name: "魏", quality_name: "五星", hit_range: 2, skill_init: 200023 },
  ];
  const researchSkills = [
    { skill_id: 200001, name: "衣带密诏", skill_type: 2, probability_init: 100, prepare: 0 },
    { skill_id: 200027, name: "其疾如风", skill_type: 1, probability_init: 100, prepare: 0 },
    { skill_id: 200914, name: "兵无常势", skill_type: 4, probability_init: 100, prepare: 0 },
  ];
  const researchHeroDetail = (id, options = {}) => {
    const hero = researchHeroes.find((row) => row.heroid === id) || {
      heroid: id,
      name: options.name || `武将 ${id}`,
      country_name: "未知",
      quality_name: "五星",
      hit_range: 3,
      skill_init: options.skillId || id + 100_000,
    };
    const skillId = Number(options.skillId || hero.skill_init || id + 100_000);
    return {
      ok: true,
      hero: {
        ...hero,
        ...(options.name ? { name: options.name } : {}),
        skill_init: skillId,
      },
      initialSkill: {
        skill_id: skillId,
        name: options.skillName || `初始战法 ${options.name || hero.name || id}`,
      },
    };
  };
  const researchSkillDetail = (id, name = "") => {
    const skill = researchSkills.find((row) => row.skill_id === id) || {
      skill_id: id,
      name: name || `战法 ${id}`,
    };
    return {
      ok: true,
      skill: { ...skill, ...(name ? { name } : {}) },
      details: [{
        detail_id: id + 1,
        effect_id: id + 2,
        effect_name: `${name || skill.name}效果`,
      }],
    };
  };
  const researchMatchup = (sampleSize, winRate, marker = "") => ({
    ok: true,
    leftKey: "100027.100016.100090",
    rightKey: "100013.100649.100023",
    evidenceClass: "BATTLE_STAT",
    marker,
    battleStats: {
      sampleSize,
      wins: Math.max(0, Math.floor(sampleSize * winRate / 100)),
      draws: 0,
      losses: Math.max(
        0,
        sampleSize - Math.floor(sampleSize * winRate / 100),
      ),
      winRate,
      latestBattleTime: 1_900_000_000,
    },
  });
  await page.route("**/api/intelligence/heroes?*", (route) => {
    const query = new URL(route.request().url()).searchParams.get("q") || "";
    const rows = researchHeroes.filter((hero) =>
      !query || hero.name.includes(query) || String(hero.heroid).includes(query)
    );
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ ok: true, datasetVersion: "client-9.2.2", rows }),
    });
  });
  await page.route(/\/api\/intelligence\/heroes\/\d+$/, async (route) => {
    const id = Number(route.request().url().split("/").pop());
    if (
      await fulfillQueuedLifecycleResponse(route, `researchHero:${id}`)
    ) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(researchHeroDetail(id)),
    });
  });
  await page.route("**/api/intelligence/skills?*", (route) => {
    const query = new URL(route.request().url()).searchParams.get("q") || "";
    const rows = researchSkills.filter((skill) =>
      !query || skill.name.includes(query) || String(skill.skill_id).includes(query)
    );
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ ok: true, datasetVersion: "client-9.2.2", rows }),
    });
  });
  await page.route(/\/api\/intelligence\/skills\/\d+$/, async (route) => {
    const id = Number(route.request().url().split("/").pop());
    if (
      await fulfillQueuedLifecycleResponse(route, `researchSkill:${id}`)
    ) return;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(researchSkillDetail(id)),
    });
  });
  await page.route(
    "**/api/intelligence/lineups/100027.100016.100090/matchup/100013.100649.100023",
    async (route) => {
      if (
        await fulfillQueuedLifecycleResponse(route, "researchMatchup")
      ) return;
      return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ...researchMatchup(7, 64.3),
        battleStats: {
          sampleSize: 7,
          wins: 4,
          draws: 1,
          losses: 2,
          winRate: 64.3,
          latestBattleTime: 1_900_000_000,
        },
      }),
      });
    },
  );
  const initialIntelligenceSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const navigation = page.goto(BASE, { waitUntil: "domcontentloaded" });
  await page.waitForSelector("#cc-kpi-grid .cc-skeleton", {
    state: "attached",
  });
  assert.equal(
    await page.locator("#cc-kpi-grid .cc-skeleton").count(),
    5,
    "first-load Skeleton did not attach before asynchronous data completed",
  );
  const loadingAnimationPeak = await sampleAnimationPeak(page);
  assert.ok(
    loadingAnimationPeak.peakAnimationCount <= 6,
    `loading peak used ${loadingAnimationPeak.peakAnimationCount} visible animations`,
  );
  assert.ok(
    loadingAnimationPeak.hud.peakValueAnimations <= 6,
    `loading peak used ${loadingAnimationPeak.hud.peakValueAnimations} HUD value animations`,
  );
  await navigation;
  await initialIntelligenceSummary.started;
  assert.equal(
    await page.locator("#intel-loader-state .hud-state-loading").isVisible(),
    true,
    "Intelligence first load did not expose a loading state",
  );
  assert.equal(
    await page.locator("#intel-view-map").getAttribute("aria-busy"),
    "true",
  );
  initialIntelligenceSummary.respond({
    ...worldEnvelope,
    counts: { tiles: 1, armies: 1, marches: 0, events: 1 },
    dataBounds: { rowUp: 1, rowDown: 160, colLeft: 1, colRight: 160 },
    focusWid: 10004,
    suggestedBounds: { rowUp: 0, rowDown: 19, colLeft: 0, colRight: 19 },
  });

  assert.equal(await page.evaluate(() => typeof window.ExcelJS), "object");
  assert.equal(await page.evaluate(() => typeof window.jspdf?.jsPDF), "function");
  assert.equal(await page.evaluate(() => typeof window.jspdf?.jsPDF?.API?.autoTable), "function");
  await page.waitForSelector("#tab33.active");
  await page.waitForFunction(() => document.querySelector("#intel-state-meta")?.textContent.includes("v7"));
  await page.waitForFunction(() => document.querySelector("#intel-detail-title")?.textContent.includes("10004"));
  assert.equal(
    await page.locator("body").getAttribute("data-visual-domain"),
    "intelligence",
  );
  assert.equal(await page.locator("#tab33 > .hud-page-head").isVisible(), true);
  assert.equal(await page.locator("#intel-view-map .hud-map-shell").isVisible(), true);
  assert.equal(await page.locator("#intel-radar-canvas").isVisible(), true);
  assert.equal(await page.locator("#intel-map-mode").textContent(), "全域热区");
  const intelligenceCanvas = page.locator("#intel-map-canvas");
  const canvasBox = await intelligenceCanvas.boundingBox();
  await page.waitForFunction(() =>
    window.IntelligenceCenter?.state?.render?.hitAreas?.length > 0
  );
  const overviewHitArea = await page.evaluate(() => ({
    x: window.IntelligenceCenter.state.render.hitAreas[0].x
      + window.IntelligenceCenter.state.render.hitAreas[0].width / 2,
    y: window.IntelligenceCenter.state.render.hitAreas[0].y
      + window.IntelligenceCenter.state.render.hitAreas[0].height / 2,
  }));
  await page.mouse.dblclick(
    canvasBox.x + overviewHitArea.x,
    canvasBox.y + overviewHitArea.y,
  );
  await page.waitForFunction(() => document.querySelector("#intel-map-mode")?.textContent === "战术镜头");
  await page.locator("#intel-map-home").click();
  await page.waitForFunction(() => document.querySelector("#intel-map-mode")?.textContent === "战术镜头");
  await page.locator("#intel-map-back").click();
  await page.waitForFunction(() => document.querySelector("#intel-map-mode")?.textContent === "全域热区");
  await page.locator("#intel-map-forward").click();
  await page.waitForFunction(() => document.querySelector("#intel-map-mode")?.textContent === "战术镜头");

  const mismatchSummaryV8 =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const mismatchViewportV7 =
    fixtureControls.queueLifecycleResponse("intelligenceViewport");
  const retrySummaryV8 =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const mismatchRetryPromise = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await mismatchSummaryV8.started;
  mismatchSummaryV8.respond(intelligenceSummaryFixture({
    worldStateVersion: 8,
  }));
  await mismatchViewportV7.started;
  mismatchViewportV7.respond({
    ...intelligenceViewportFixture("错误版本地块", 10004),
    worldStateVersion: 7,
  });
  await retrySummaryV8.started;
  assert.doesNotMatch(
    await page.locator("#intel-detail-panel").textContent(),
    /错误版本地块/,
    "a mismatched v7 viewport committed before the bounded retry",
  );
  retrySummaryV8.respond(intelligenceSummaryFixture({
    worldStateVersion: 8,
  }));
  await mismatchRetryPromise;
  assert.match(
    await page.locator("#intel-state-meta").textContent(),
    /v8/,
  );
  assert.doesNotMatch(
    await page.locator("#intel-detail-panel").textContent(),
    /错误版本地块/,
  );

  const firstPersistentMismatchSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const firstPersistentMismatchViewport =
    fixtureControls.queueLifecycleResponse("intelligenceViewport");
  const secondPersistentMismatchSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const secondPersistentMismatchViewport =
    fixtureControls.queueLifecycleResponse("intelligenceViewport");
  const persistentMismatchPromise = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await firstPersistentMismatchSummary.started;
  firstPersistentMismatchSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 9,
  }));
  await firstPersistentMismatchViewport.started;
  firstPersistentMismatchViewport.respond({
    ...intelligenceViewportFixture("首轮不一致", 10004),
    worldStateVersion: 8,
  });
  await secondPersistentMismatchSummary.started;
  secondPersistentMismatchSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 9,
  }));
  await secondPersistentMismatchViewport.started;
  secondPersistentMismatchViewport.respond({
    ...intelligenceViewportFixture("次轮不一致", 10004),
    worldStateVersion: 8,
  });
  assert.equal(await persistentMismatchPromise, null);
  assert.equal(
    await page.locator("#intel-loader-state .hud-state-stale").isVisible(),
    true,
    "two version mismatches did not end in a visible stale state",
  );
  assert.match(
    await page.locator("#intel-loader-state").textContent(),
    /版本不一致.*重试/s,
  );
  assert.match(
    await page.locator("#intel-state-meta").textContent(),
    /v8/,
    "continuous version mismatch replaced the prior stable map",
  );

  const restoreAfterMismatchSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  await page.locator("#intel-loader-state .hud-state-action").click();
  await restoreAfterMismatchSummary.started;
  restoreAfterMismatchSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 9,
  }));
  await page.waitForFunction(() =>
    document.querySelector("#intel-state-meta")?.textContent.includes("v9")
      && !document.querySelector("#intel-view-map")
        ?.hasAttribute("aria-busy")
  );

  const missingSummaryVersionFirst =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const missingSummaryVersionRetry =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const missingSummaryVersionPromise = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await missingSummaryVersionFirst.started;
  const missingSummaryVersionBody = intelligenceSummaryFixture({
    dataBounds: null,
    focusWid: 0,
    counts: { tiles: 0, armies: 0, marches: 0, events: 0 },
  });
  delete missingSummaryVersionBody.worldStateVersion;
  missingSummaryVersionFirst.respond(missingSummaryVersionBody);
  const missingSummaryVersionRetried = await Promise.race([
    missingSummaryVersionRetry.started.then(() => true),
    page.waitForTimeout(500).then(() => false),
  ]);
  assert.equal(
    missingSummaryVersionRetried,
    true,
    "a summary without worldStateVersion committed instead of retrying",
  );
  assert.ok(
    await page.evaluate(() =>
      window.IntelligenceCenter.state.summary?.dataBounds
    ),
    "a summary without worldStateVersion cleared the prior stable map",
  );
  missingSummaryVersionRetry.respond(intelligenceSummaryFixture({
    worldStateVersion: 9,
  }));
  await missingSummaryVersionPromise;

  const missingMemberVersionFirst =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const missingMemberVersionViewport =
    fixtureControls.queueLifecycleResponse("intelligenceViewport");
  const missingMemberVersionRetry =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const missingMemberVersionPromise = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await missingMemberVersionFirst.started;
  missingMemberVersionFirst.respond(intelligenceSummaryFixture({
    worldStateVersion: 9,
  }));
  await missingMemberVersionViewport.started;
  const missingMemberVersionBody =
    intelligenceViewportFixture("缺版本不得提交", 10004);
  delete missingMemberVersionBody.worldStateVersion;
  missingMemberVersionViewport.respond(missingMemberVersionBody);
  await missingMemberVersionRetry.started;
  assert.equal(
    await page.evaluate(() =>
      window.IntelligenceCenter.state.tiles.some(
        (tile) => tile.name === "缺版本不得提交",
      )
    ),
    false,
    "a core response without worldStateVersion committed before retry",
  );
  missingMemberVersionRetry.respond(intelligenceSummaryFixture({
    worldStateVersion: 9,
  }));
  await missingMemberVersionPromise;
  assert.equal(
    await page.evaluate(() =>
      window.IntelligenceCenter.state.tiles.some(
        (tile) => tile.name === "缺版本不得提交",
      )
    ),
    false,
    "a core response without worldStateVersion survived the bounded retry",
  );

  const layerViewport =
    fixtureControls.queueLifecycleResponse("intelligenceViewport");
  const layerRequestsBefore = fixtureControls.intelligenceCounters();
  const layerRefresh = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await layerViewport.started;
  await page.locator("[data-layer='ownership']").click();
  layerViewport.respond({
    ...intelligenceViewportFixture("切层仍提交", 10004),
    worldStateVersion: 9,
  });
  await layerRefresh;
  const layerRequestsAfter = fixtureControls.intelligenceCounters();
  assert.equal(
    layerRequestsAfter.summary - layerRequestsBefore.summary,
    1,
    "a render-only layer toggle started another aggregate request",
  );
  assert.equal(
    await page.locator("#intel-view-map").getAttribute("aria-busy"),
    null,
    "a render-only layer toggle stranded the active busy owner",
  );
  assert.equal(
    await page.evaluate(() =>
      window.IntelligenceCenter.state.tiles.some(
        (tile) => tile.name === "切层仍提交",
      )
    ),
    true,
    "a layer toggle made an otherwise current aggregate response stale",
  );

  const failedIntelligenceEvents =
    fixtureControls.queueLifecycleResponse("intelligenceEvents");
  const failedIntelligenceRefresh = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await failedIntelligenceEvents.started;
  assert.equal(
    await page.locator("#intel-loader-state .hud-state-refreshing").isVisible(),
    true,
  );
  assert.equal(
    await page.locator("#intel-view-map").getAttribute("aria-busy"),
    "true",
  );
  const preservedMapBeforeFailure = await page.evaluate(() => ({
    bounds: { ...window.IntelligenceCenter.state.bounds },
    tiles: window.IntelligenceCenter.state.tiles.map((tile) => tile.wid),
  }));
  failedIntelligenceEvents.respond({
    ok: false,
    error: "世界事件暂不可用",
  });
  assert.equal(await failedIntelligenceRefresh, null);
  assert.equal(
    await page.locator("#intel-loader-state .hud-state-error").isVisible(),
    true,
    "a failed aggregate member did not surface a nonblocking error",
  );
  assert.match(
    await page.locator("#intel-loader-state").textContent(),
    /世界事件暂不可用.*重试/s,
  );
  assert.deepEqual(
    await page.evaluate(() => ({
      bounds: { ...window.IntelligenceCenter.state.bounds },
      tiles: window.IntelligenceCenter.state.tiles.map((tile) => tile.wid),
    })),
    preservedMapBeforeFailure,
    "failed aggregate refresh replaced the prior map",
  );
  assert.equal(
    await page.locator("#intel-view-map").getAttribute("aria-busy"),
    null,
  );

  const retriedIntelligenceEvents =
    fixtureControls.queueLifecycleResponse("intelligenceEvents");
  await page.locator("#intel-loader-state .hud-state-action").click();
  await retriedIntelligenceEvents.started;
  retriedIntelligenceEvents.respond(
    intelligenceEventsFixture("retry_snapshot_completed"),
  );
  await page.waitForFunction(() =>
    document.querySelector("#intel-timeline")
      ?.textContent.includes("retry_snapshot_completed")
      && !document.querySelector("#intel-view-map")
        ?.hasAttribute("aria-busy")
  );

  const staleViewport =
    fixtureControls.queueLifecycleResponse("intelligenceViewport");
  const staleViewportRefresh = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await staleViewport.started;
  const currentViewport =
    fixtureControls.queueLifecycleResponse("intelligenceViewport");
  await page.evaluate(() => window.IntelligenceCenter.zoomIn());
  await currentViewport.started;
  staleViewport.respond(
    intelligenceViewportFixture("过期视窗地块", 90009),
  );
  assert.equal(await staleViewportRefresh, null);
  assert.equal(
    await page.locator("#intel-view-map").getAttribute("aria-busy"),
    "true",
    "old generation finally cleared the current map owner",
  );
  assert.equal(
    await page.evaluate(() =>
      window.IntelligenceCenter.state.tiles.some(
        (tile) => tile.name === "过期视窗地块",
      )
    ),
    false,
  );
  currentViewport.respond(
    intelligenceViewportFixture("当前视窗地块", 10004),
  );
  await page.waitForFunction(() =>
    window.IntelligenceCenter.state.tiles.some(
      (tile) => tile.name === "当前视窗地块",
    )
      && !document.querySelector("#intel-view-map")
        ?.hasAttribute("aria-busy")
  );
  assert.equal(
    await page.evaluate(() =>
      window.IntelligenceCenter.state.tiles.some(
        (tile) => tile.name === "过期视窗地块",
      )
    ),
    false,
  );

  const staleRisks =
    fixtureControls.queueLifecycleResponse("intelligenceRisks");
  const staleRisksRefresh = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await staleRisks.started;
  const currentRisks =
    fixtureControls.queueLifecycleResponse("intelligenceRisks");
  const currentRisksRefresh = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await currentRisks.started;
  currentRisks.respond(intelligenceRisksFixture(44, "medium"));
  await currentRisksRefresh;
  staleRisks.respond(intelligenceRisksFixture(99, "high"));
  assert.equal(await staleRisksRefresh, null);
  assert.equal(
    await page.evaluate(() =>
      window.IntelligenceCenter.state.risks.get(10004)?.score
    ),
    44,
    "an old risks response overwrote the current generation",
  );
  assert.equal(
    await page.locator("#intel-view-map").getAttribute("aria-busy"),
    null,
  );

  const staleDetail =
    fixtureControls.queueLifecycleResponse("intelligenceDetail:10004");
  const staleDetailPromise = page.evaluate(() =>
    window.IntelligenceCenter.selectWid(10004, false)
  ).catch((error) => error.message);
  await staleDetail.started;
  const currentDetail =
    fixtureControls.queueLifecycleResponse("intelligenceDetail:2081480");
  const currentDetailPromise = page.evaluate(() =>
    window.IntelligenceCenter.selectWid(2081480, false)
  ).catch((error) => error.message);
  await currentDetail.started;
  assert.doesNotMatch(
    await page.locator("#intel-detail-title").textContent(),
    /10004/,
    "selected WID changed but the old detail heading remained visible",
  );
  staleDetail.respond(
    intelligenceDetailFixture(10004, "过期详情地块"),
  );
  assert.equal(await staleDetailPromise, null);
  assert.equal(
    await page.locator("#intel-detail-panel").getAttribute("aria-busy"),
    "true",
    "old detail finally cleared the current detail owner",
  );
  currentDetail.respond(
    intelligenceDetailFixture(2081480, "当前详情地块"),
  );
  await currentDetailPromise;
  const currentDetailState = await page.evaluate(() => ({
    selectedWid: window.IntelligenceCenter.state.selectedWid,
    detailWid: window.IntelligenceCenter.state.detail?.tile?.wid || 0,
    detailLoading: window.IntelligenceCenter.state.detailLoading,
    busy: document.querySelector("#intel-detail-panel")
      ?.getAttribute("aria-busy") || null,
    status: document.querySelector("#intel-detail-status")?.textContent || "",
  }));
  assert.deepEqual(
    currentDetailState,
    {
      selectedWid: 2081480,
      detailWid: 2081480,
      detailLoading: false,
      busy: null,
      status: "加载完成",
    },
    "the latest standalone detail did not atomically commit and release busy",
  );
  assert.match(
    await page.locator("#intel-detail-title").textContent(),
    /2081480/,
  );
  assert.doesNotMatch(
    await page.locator("#intel-detail-panel").textContent(),
    /过期详情地块/,
  );

  const oldStandaloneDetail =
    fixtureControls.queueLifecycleResponse("intelligenceDetail:10004");
  const oldStandaloneDetailPromise = page.evaluate(() =>
    window.IntelligenceCenter.selectWid(10004, false)
  );
  await oldStandaloneDetail.started;
  const currentAggregateDetail =
    fixtureControls.queueLifecycleResponse("intelligenceDetail:10004");
  const currentAggregateDetailPromise = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await currentAggregateDetail.started;
  assert.equal(
    await oldStandaloneDetailPromise,
    null,
    "an aggregate detail request did not invalidate the older standalone detail",
  );
  assert.equal(
    await page.locator("#intel-detail-panel").getAttribute("aria-busy"),
    "true",
    "the old standalone finally cleared the newer aggregate detail owner",
  );
  oldStandaloneDetail.respond(
    intelligenceDetailFixture(10004, "过期独立详情"),
  );
  currentAggregateDetail.respond(
    intelligenceDetailFixture(10004, "当前聚合详情"),
  );
  await currentAggregateDetailPromise;
  assert.match(
    await page.locator("#intel-detail-panel").textContent(),
    /当前聚合详情/,
  );
  assert.doesNotMatch(
    await page.locator("#intel-detail-panel").textContent(),
    /过期独立详情/,
  );
  assert.equal(
    await page.locator("#intel-detail-panel").getAttribute("aria-busy"),
    null,
  );

  await page.evaluate(() => window.IntelligenceCenter.selectWid(10004, false));
  const oldAggregateDetail =
    fixtureControls.queueLifecycleResponse("intelligenceDetail:10004");
  const oldAggregateDetailPromise = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await oldAggregateDetail.started;
  const currentStandaloneDetail =
    fixtureControls.queueLifecycleResponse("intelligenceDetail:10004");
  const currentStandaloneDetailPromise = page.evaluate(() =>
    window.IntelligenceCenter.selectWid(10004, false)
  );
  await currentStandaloneDetail.started;
  currentStandaloneDetail.respond(
    intelligenceDetailFixture(10004, "当前同 WID 详情"),
  );
  await currentStandaloneDetailPromise;
  oldAggregateDetail.respond(
    intelligenceDetailFixture(10004, "过期聚合详情"),
  );
  assert.equal(
    await oldAggregateDetailPromise,
    null,
    "a standalone detail request did not invalidate the older aggregate detail",
  );
  assert.match(
    await page.locator("#intel-detail-panel").textContent(),
    /当前同 WID 详情/,
  );
  assert.doesNotMatch(
    await page.locator("#intel-detail-panel").textContent(),
    /过期聚合详情/,
  );

  const staleSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const staleSummaryPromise = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await staleSummary.started;
  staleSummary.respond(intelligenceSummaryFixture({
    freshness: "stale",
    worldStateVersion: 8,
  }));
  await staleSummaryPromise;
  assert.equal(
    await page.locator("#intel-loader-state .hud-state-stale").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#intel-loader-state").textContent(),
    /WorldState 真值已陈旧.*重试/s,
  );
  assert.match(
    await page.locator("#intel-state-meta").textContent(),
    /v8.*stale/,
  );

  const restoreFreshSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  await page.locator("#intel-loader-state .hud-state-action").click();
  await restoreFreshSummary.started;
  restoreFreshSummary.respond(intelligenceSummaryFixture());
  await page.waitForFunction(() =>
    document.querySelector("#intel-state-meta")?.textContent.includes("fresh")
      && !document.querySelector("#intel-view-map")
        ?.hasAttribute("aria-busy")
  );

  const hiddenSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const hiddenRefresh = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await hiddenSummary.started;
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      value: "hidden",
    });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  assert.equal(
    await page.locator("#intel-view-map").getAttribute("aria-busy"),
    null,
    "hidden page did not release the aggregate busy owner",
  );
  hiddenSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 999,
  }));
  assert.equal(await hiddenRefresh, null);
  assert.doesNotMatch(
    await page.locator("#intel-state-meta").textContent(),
    /v999/,
  );
  const visibleSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      value: "visible",
    });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await visibleSummary.started;
  visibleSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 9,
  }));
  await page.waitForFunction(() =>
    document.querySelector("#intel-state-meta")?.textContent.includes("v9")
      && !document.querySelector("#intel-view-map")
        ?.hasAttribute("aria-busy")
  );

  const oldGenerationSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const oldGenerationSummaryRefresh = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await oldGenerationSummary.started;
  const currentSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const currentSummaryRefresh = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await currentSummary.started;
  currentSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 10,
  }));
  await currentSummaryRefresh;
  oldGenerationSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 998,
  }));
  assert.equal(await oldGenerationSummaryRefresh, null);
  assert.match(
    await page.locator("#intel-state-meta").textContent(),
    /v10/,
  );
  assert.doesNotMatch(
    await page.locator("#intel-state-meta").textContent(),
    /v998/,
  );

  const tabAbortSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const tabAbortRefresh = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await tabAbortSummary.started;
  await page.evaluate(() => window.switchTab(31, null));
  assert.equal(
    await page.locator("#intel-view-map").getAttribute("aria-busy"),
    null,
    "tab switch did not release the aggregate busy owner",
  );
  tabAbortSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 997,
  }));
  assert.equal(await tabAbortRefresh, null);
  assert.doesNotMatch(
    await page.locator("#intel-state-meta").textContent(),
    /v997/,
  );
  const tabReturnSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  await page.evaluate(() => window.switchTab(33, null));
  await tabReturnSummary.started;
  tabReturnSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 11,
  }));
  await page.waitForFunction(() =>
    document.querySelector("#intel-state-meta")?.textContent.includes("v11")
      && !document.querySelector("#intel-view-map")
        ?.hasAttribute("aria-busy")
  );

  const eventSourceCountBeforeScene = await page.evaluate(
    () => window.__eventSources.length,
  );
  const initialMarchScene =
    fixtureControls.queueLifecycleResponse("intelligenceScene:march");
  const initialMarchPromise = page.evaluate(() =>
    window.IntelligenceCenter.openView("march")
  );
  await initialMarchScene.started;
  assert.equal(
    await page.locator(
      "#intel-scene-march-status .hud-state-loading",
    ).isVisible(),
    true,
  );
  assert.equal(
    await page.locator("#intel-view-march").getAttribute("aria-busy"),
    "true",
  );
  initialMarchScene.respond({
    ok: true,
    marches: [{
      real_march_id: 9901,
      last_wid: 10001,
      current_wid: 10004,
      next_wid: 10005,
      start_time: 1_900_000_000,
      next_time: 1_900_000_030,
      end_time: 1_900_000_060,
      path_id: 77,
      unit_time_cost: 3,
      march_type: 1,
      belong_id: 42,
    }],
  });
  await initialMarchPromise;
  assert.match(
    await page.locator("#ws-march-body").textContent(),
    /9901/,
  );

  const failedMarchScene =
    fixtureControls.queueLifecycleResponse("intelligenceScene:march");
  const failedMarchPromise = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await failedMarchScene.started;
  failedMarchScene.respond({
    ok: false,
    error: "实时行军暂不可用",
  });
  assert.equal(await failedMarchPromise, null);
  assert.equal(
    await page.locator(
      "#intel-scene-march-status .hud-state-error",
    ).isVisible(),
    true,
  );
  assert.match(
    await page.locator("#intel-scene-march-status").textContent(),
    /实时行军暂不可用.*重试/s,
  );
  assert.doesNotMatch(
    await page.locator("#intel-map-status").textContent(),
    /实时行军暂不可用|场景情报加载失败/,
    "a scene failure polluted the map status text",
  );
  assert.equal(
    await page.locator("#intel-map-status").evaluate(
      (element) => element.classList.contains("intel-error"),
    ),
    false,
    "a scene failure polluted the map status error class",
  );
  assert.match(
    await page.locator("#ws-march-body").textContent(),
    /9901/,
    "failed scene refresh did not preserve the prior scene rows",
  );

  const retriedMarchScene =
    fixtureControls.queueLifecycleResponse("intelligenceScene:march");
  await page.locator(
    "#intel-scene-march-status .hud-state-action",
  ).click();
  await retriedMarchScene.started;
  retriedMarchScene.respond({
    ok: true,
    marches: [{
      real_march_id: 9902,
      last_wid: 10002,
      current_wid: 10005,
      next_wid: 10006,
    }],
  });
  await page.waitForFunction(() =>
    document.querySelector("#ws-march-body")?.textContent.includes("9902")
      && !document.querySelector("#intel-view-march")
        ?.hasAttribute("aria-busy")
  );
  assert.equal(
    await page.locator(
      "#intel-scene-march-status .hud-state-error",
    ).count(),
    0,
    "a successful scene retry left the scene host error visible",
  );

  const compatibilityMarchScene =
    fixtureControls.queueLifecycleResponse("intelligenceScene:march");
  const compatibilityMarchPromise = page.evaluate(() =>
    window.WorldScenePanel.load("march", true)
  );
  await compatibilityMarchScene.started;
  compatibilityMarchScene.respond({
    ok: true,
    marches: [{
      real_march_id: 99025,
      last_wid: 10002,
      current_wid: 10005,
      next_wid: 10006,
    }],
  });
  await compatibilityMarchPromise;
  await page.waitForFunction(() =>
    document.querySelector("#ws-march-body")?.textContent.includes("99025")
  );

  const requestsBeforeLegacyScene =
    fixtureControls.intelligenceCounters();
  const legacyMarchScene =
    fixtureControls.queueLifecycleResponse("intelligenceScene:march");
  const legacyMarchPromise = page.evaluate(() =>
    window.loadWorldScenePanel("march", true)
  );
  await legacyMarchScene.started;
  legacyMarchScene.respond({
    ok: true,
    marches: [{
      real_march_id: 99026,
      last_wid: 10002,
      current_wid: 10005,
      next_wid: 10006,
    }],
  });
  await legacyMarchPromise;
  const requestsAfterLegacyScene =
    fixtureControls.intelligenceCounters();
  assert.equal(
    requestsAfterLegacyScene.summary - requestsBeforeLegacyScene.summary,
    1,
    "the legacy scene entry bypassed the aggregate coordinator",
  );
  assert.equal(
    requestsAfterLegacyScene.march - requestsBeforeLegacyScene.march,
    1,
    "the legacy scene entry issued duplicate scene requests",
  );

  const requestsBeforeSceneSse = fixtureControls.intelligenceCounters();
  const sseMarchScene =
    fixtureControls.queueLifecycleResponse("intelligenceScene:march");
  await page.evaluate(() => {
    for (let index = 0; index < 4; index += 1) {
      window.dispatchEvent(new CustomEvent("stzb:stream-event", {
        detail: { type: "world_scene_delta", data: { seq: index + 1 } },
      }));
    }
  });
  await sseMarchScene.started;
  sseMarchScene.respond({
    ok: true,
    marches: [{
      real_march_id: 9903,
      last_wid: 10003,
      current_wid: 10006,
      next_wid: 10007,
    }],
  });
  await page.waitForFunction(() =>
    document.querySelector("#ws-march-body")?.textContent.includes("9903")
  );
  const requestsAfterSceneSse = fixtureControls.intelligenceCounters();
  assert.equal(
    requestsAfterSceneSse.summary - requestsBeforeSceneSse.summary,
    1,
    "an SSE burst caused duplicate Intelligence summary requests",
  );
  assert.equal(
    requestsAfterSceneSse.march - requestsBeforeSceneSse.march,
    1,
    "an SSE burst caused duplicate scene requests",
  );
  assert.equal(
    await page.evaluate(() => window.__eventSources.length),
    eventSourceCountBeforeScene,
    "Intelligence scene loading created another EventSource",
  );
  assert.equal(
    await page.locator(
      "#intel-scene-march-status .hud-state-error",
    ).count(),
    0,
    "a successful scene retry left the old scene error visible",
  );

  await page.evaluate(() => window.IntelligenceCenter.openView("map"));
  await page.waitForFunction(() =>
    document.querySelector("[data-intel-view='map']")
      ?.classList.contains("active")
      && !document.querySelector("#intel-view-map")
        ?.hasAttribute("aria-busy")
  );
  assert.doesNotMatch(
    await page.locator("#intel-map-status").textContent(),
    /实时行军暂不可用|场景情报加载失败/,
    "returning to the map revealed a stale scene error",
  );
  assert.equal(
    await page.locator("#intel-map-status").evaluate(
      (element) => element.classList.contains("intel-error"),
    ),
    false,
    "returning to the map retained a stale scene error class",
  );
  const emptySummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const emptySummaryPromise = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  );
  await emptySummary.started;
  emptySummary.respond(intelligenceSummaryFixture({
    dataBounds: null,
    focusWid: 0,
    worldStateVersion: 12,
    counts: { tiles: 0, armies: 0, marches: 0, events: 0 },
  }));
  await emptySummaryPromise;
  assert.equal(
    await page.locator("#intel-loader-state .hud-state-empty").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#intel-loader-state").textContent(),
    /等待 5026 基线.*重试/s,
  );
  assert.equal(
    await page.evaluate(() =>
      window.IntelligenceCenter.state.tiles.length
        + window.IntelligenceCenter.state.buckets.length
    ),
    0,
  );
  assert.match(
    await page.locator("#intel-state-meta").textContent(),
    /v12/,
    "empty backend truth did not update the visible WorldState version",
  );
  assert.match(
    await page.locator("#intel-detail-body").textContent(),
    /选择热区或真实地块查看情报/,
    "empty backend truth left an obsolete selected-tile detail visible",
  );

  const restoreAfterEmpty =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  await page.locator("#intel-loader-state .hud-state-action").click();
  await restoreAfterEmpty.started;
  restoreAfterEmpty.respond(intelligenceSummaryFixture());
  await page.waitForFunction(() =>
    window.IntelligenceCenter.state.summary?.dataBounds
      && !document.querySelector("#intel-view-map")
        ?.hasAttribute("aria-busy")
  );

  const panOldSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const panOldPromise = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  ).catch((error) => error.message);
  await panOldSummary.started;
  const panCurrentSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  await page.evaluate(() => {
    const canvas = document.querySelector("#intel-map-canvas");
    const rect = canvas.getBoundingClientRect();
    canvas.dispatchEvent(new WheelEvent("wheel", {
      bubbles: true,
      cancelable: true,
      clientX: rect.left + rect.width / 2,
      clientY: rect.top + rect.height / 2,
      deltaY: -120,
    }));
  });
  await page.waitForTimeout(50);
  assert.equal(
    await page.locator("#intel-view-map").getAttribute("aria-busy"),
    null,
    "rapid map zoom did not immediately abort and release the old busy owner",
  );
  await panCurrentSummary.started;
  assert.equal(
    await page.locator("#intel-view-map").getAttribute("aria-busy"),
    "true",
  );
  panCurrentSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 13,
  }));
  await page.waitForFunction(() =>
    document.querySelector("#intel-state-meta")?.textContent.includes("v13")
      && !document.querySelector("#intel-view-map")
        ?.hasAttribute("aria-busy")
  );
  panOldSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 996,
  }));
  assert.equal(await panOldPromise, null);
  assert.doesNotMatch(
    await page.locator("#intel-state-meta").textContent(),
    /v996/,
  );

  const dragOldSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const dragOldPromise = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  ).catch((error) => error.message);
  await dragOldSummary.started;
  const dragCurrentSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const dragCanvasBox = await intelligenceCanvas.boundingBox();
  await page.mouse.move(
    dragCanvasBox.x + dragCanvasBox.width / 2,
    dragCanvasBox.y + dragCanvasBox.height / 2,
  );
  await page.mouse.down();
  await page.mouse.move(
    dragCanvasBox.x + dragCanvasBox.width / 2 + 32,
    dragCanvasBox.y + dragCanvasBox.height / 2 + 18,
  );
  assert.equal(
    await page.locator("#intel-view-map").getAttribute("aria-busy"),
    null,
    "rapid map pan did not immediately abort and release the old busy owner",
  );
  await page.mouse.up();
  await dragCurrentSummary.started;
  dragCurrentSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 15,
  }));
  await page.waitForTimeout(500);
  const dragCommitState = await page.evaluate(() => ({
    version: window.IntelligenceCenter.state.summary?.worldStateVersion,
    bounds: { ...window.IntelligenceCenter.state.bounds },
    busy: document.querySelector("#intel-view-map")
      ?.getAttribute("aria-busy"),
    loader: document.querySelector("#intel-loader-state")?.textContent,
  }));
  assert.deepEqual(
    {
      version: dragCommitState.version,
      busy: dragCommitState.busy,
    },
    {
      version: 15,
      busy: null,
    },
    `drag generation did not commit consistently: ${JSON.stringify(dragCommitState)}`,
  );
  dragOldSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 995,
  }));
  assert.equal(await dragOldPromise, null);
  assert.doesNotMatch(
    await page.locator("#intel-state-meta").textContent(),
    /v995/,
  );

  const radarOldSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const radarOldPromise = page.evaluate(() =>
    window.loadIntelligenceCenter(true)
  ).catch((error) => error.message);
  await radarOldSummary.started;
  const radarCanvas = page.locator("#intel-radar-canvas");
  await radarCanvas.scrollIntoViewIfNeeded();
  const radarBox = await radarCanvas.boundingBox();
  await page.mouse.move(
    radarBox.x + radarBox.width / 2,
    radarBox.y + radarBox.height / 2,
  );
  const radarHitTarget = await page.evaluate(({ x, y }) =>
    document.elementFromPoint(x, y)?.id || "",
  {
    x: radarBox.x + radarBox.width / 2,
    y: radarBox.y + radarBox.height / 2,
  });
  await page.mouse.down();
  const radarDownState = await page.evaluate(() => ({
    busy: document.querySelector("#intel-view-map")
      ?.getAttribute("aria-busy") || null,
    dragging: window.IntelligenceCenter.state.radarDrag !== null,
  }));
  assert.deepEqual(
    { ...radarDownState, hitTarget: radarHitTarget },
    {
      busy: null,
      dragging: true,
      hitTarget: "intel-radar-canvas",
    },
    "radar pointerdown did not immediately abort the old aggregate",
  );
  await page.evaluate(() => {
    const radar = document.querySelector("#intel-radar-canvas");
    radar.dispatchEvent(new PointerEvent("pointercancel", {
      bubbles: true,
      pointerId: 1,
    }));
  });
  await page.mouse.move(0, 0);
  await page.mouse.up();
  assert.equal(
    await page.evaluate(() => window.IntelligenceCenter.state.radarDrag),
    null,
    "radar pointercancel did not clear drag ownership",
  );
  radarOldSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 994,
  }));
  assert.equal(await radarOldPromise, null);

  const cancelCanvasBox = await intelligenceCanvas.boundingBox();
  await page.mouse.move(
    cancelCanvasBox.x + cancelCanvasBox.width / 2,
    cancelCanvasBox.y + cancelCanvasBox.height / 2,
  );
  await page.mouse.down();
  await page.evaluate(() => {
    const canvas = document.querySelector("#intel-map-canvas");
    canvas.dispatchEvent(new PointerEvent("lostpointercapture", {
      bubbles: true,
      pointerId: 1,
    }));
  });
  await page.mouse.move(0, 0);
  await page.mouse.up();
  assert.equal(
    await page.evaluate(() => window.IntelligenceCenter.state.drag),
    null,
    "main canvas lostpointercapture did not clear drag ownership",
  );

  const requestsBeforeHiddenDebounce =
    fixtureControls.intelligenceCounters();
  await page.evaluate(() => {
    const canvas = document.querySelector("#intel-map-canvas");
    const rect = canvas.getBoundingClientRect();
    canvas.dispatchEvent(new WheelEvent("wheel", {
      bubbles: true,
      cancelable: true,
      clientX: rect.left + rect.width / 2,
      clientY: rect.top + rect.height / 2,
      deltaY: -120,
    }));
    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      value: "hidden",
    });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await page.waitForTimeout(240);
  const requestsAfterHiddenDebounce =
    fixtureControls.intelligenceCounters();
  assert.equal(
    requestsAfterHiddenDebounce.summary
      - requestsBeforeHiddenDebounce.summary,
    0,
    "a hidden page allowed a deferred map reload to start",
  );
  const restoreVisibleSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      value: "visible",
    });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await restoreVisibleSummary.started;
  restoreVisibleSummary.respond(intelligenceSummaryFixture({
    worldStateVersion: 16,
  }));
  await page.waitForTimeout(500);
  const restoredVisibleState = await page.evaluate(() => ({
    version: window.IntelligenceCenter.state.summary?.worldStateVersion || 0,
    selectedWid: window.IntelligenceCenter.state.selectedWid,
    detailWid: window.IntelligenceCenter.state.detail?.tile?.wid || 0,
    detailLoading: window.IntelligenceCenter.state.detailLoading,
    busy: document.querySelector("#intel-view-map")
      ?.getAttribute("aria-busy") || null,
    detailBusy: document.querySelector("#intel-detail-panel")
      ?.getAttribute("aria-busy") || null,
    loader: document.querySelector("#intel-loader-state")?.textContent || "",
  }));
  assert.deepEqual(
    {
      version: restoredVisibleState.version,
      busy: restoredVisibleState.busy,
      detailBusy: restoredVisibleState.detailBusy,
      detailLoading: restoredVisibleState.detailLoading,
    },
    {
      version: 16,
      busy: null,
      detailBusy: null,
      detailLoading: false,
    },
    `visible restore did not settle consistently: ${JSON.stringify(restoredVisibleState)}`,
  );

  const blockingSummary =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  const blockingPromise = page.evaluate(async () => {
    window.IntelligenceCenter.state.tiles = [];
    window.IntelligenceCenter.state.buckets = [];
    window.IntelligenceCenter.state.radarBuckets = [];
    return window.loadIntelligenceCenter(true);
  });
  await blockingSummary.started;
  assert.equal(
    await page.locator("#intel-loader-state .hud-state-loading").isVisible(),
    true,
  );
  blockingSummary.respond({
    ok: false,
    error: "世界状态首次加载失败",
  });
  assert.equal(await blockingPromise, null);
  assert.equal(
    await page.locator("#intel-loader-state .hud-state-error").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#intel-loader-state").textContent(),
    /世界状态首次加载失败.*重试/s,
  );
  assert.equal(
    await page.locator("#intel-view-map").getAttribute("aria-busy"),
    null,
  );
  const restoreAfterBlocking =
    fixtureControls.queueLifecycleResponse("intelligenceSummary");
  await page.locator("#intel-loader-state .hud-state-action").click();
  await restoreAfterBlocking.started;
  restoreAfterBlocking.respond(intelligenceSummaryFixture({
    worldStateVersion: 14,
  }));
  await page.waitForFunction(() =>
    document.querySelector("#intel-state-meta")?.textContent.includes("v14")
      && !document.querySelector("#intel-view-map")
        ?.hasAttribute("aria-busy")
  );
  worldEnvelope.worldStateVersion = 7;
  worldEnvelope.latestDelta = {
    ...worldEnvelope.latestDelta,
    version: 7,
  };

  await page.getByRole("button", { name: "实时部队", exact: true }).click();
  await page.waitForSelector("#tab35.active");
  await page.waitForFunction(() =>
    document.querySelectorAll("#live-army-current-list .live-army-card").length === 2
  );
  assert.equal(
    await page.locator("body").getAttribute("data-visual-domain"),
    "intelligence",
  );
  assert.equal(await page.locator("#tab35 > .hud-page-head").isVisible(), true);
  assert.equal(await page.locator("#live-army-summary .hud-kpi").count(), 5);
  assert.equal(
    await page.locator("#live-army-current-list .live-army-card").count(),
    2,
  );
  assert.equal(
    await page.locator("#live-army-offline-list .live-army-card").count(),
    1,
  );
  assert.match(
    await page.locator("#live-army-detail").textContent(),
    /ARMY 18411352.*杜预.*卫瓘.*灵帝.*精确命中战报 #5289170/s,
  );
  assert.equal(
    await page.locator("#live-army-detail .live-army-hero img").count(),
    3,
  );
  assert.equal(
    await page.locator(
      "#live-army-current-list [data-army-id='18411352']",
    ).getAttribute("aria-pressed"),
    "true",
  );
  assert.match(
    await page.locator("#live-army-observed-at").textContent(),
    /数据时间.*秒前/,
  );
  assert.equal(
    await page.locator(
      "#live-army-current-list [data-army-id='814501']",
    ).count(),
    0,
  );
  await page.locator("#live-army-time-filter").selectOption("all");
  assert.equal(
    await page.locator("#live-army-current-list .live-army-card").count(),
    3,
  );
  assert.equal(
    await page.locator(
      "#live-army-current-list [data-army-id='814501']",
    ).getAttribute("class"),
    "live-army-card is-stale",
  );
  await page.locator(
    "#live-army-current-list [data-army-id='814501']",
  ).click();
  assert.match(
    await page.locator("#live-army-detail").textContent(),
    /过期待确认.*ARMY 814501.*最后观测.*无同 ID 战报，阵容未知/s,
  );
  assert.equal(
    (await page.locator("#live-army-detail").textContent()).includes("杜预"),
    false,
  );
  await page.locator("#live-army-search").fill("杜预");
  assert.equal(
    await page.locator("#live-army-current-list .live-army-card").count(),
    1,
  );
  assert.equal(
    await page.locator(
      "#live-army-current-list [data-army-id='18411352']",
    ).count(),
    1,
  );
  await page.locator("#live-army-search").fill("");
  await page.locator("#live-army-status-filter").selectOption("unknown");
  assert.equal(
    await page.locator("#live-army-current-list .live-army-card").count(),
    1,
  );
  assert.match(
    await page.locator("#live-army-current-list").textContent(),
    /状态 99/,
  );
  await page.locator("#live-army-status-filter").selectOption("all");
  await page.locator(
    "#live-army-offline-list [data-army-id='773311']",
  ).click();
  assert.match(
    await page.locator("#live-army-detail").textContent(),
    /最近离线.*ARMY 773311/s,
  );
  await page.locator("#live-army-map-home").click();

  const unknownMarker = await page.evaluate(() => {
    const marker = window.LiveArmyCommand.state.mapPlan.markers.find(
      (row) => row.armyId === 814501,
    );
    return { x: marker.x, y: marker.y };
  });
  const liveCanvas = page.locator("#live-army-map-canvas");
  const liveCanvasBox = await liveCanvas.boundingBox();
  await page.mouse.click(
    liveCanvasBox.x + unknownMarker.x,
    liveCanvasBox.y + unknownMarker.y,
  );
  await page.waitForFunction(() =>
    window.LiveArmyCommand.state.selectedArmyId === 814501
  );
  assert.match(
    await page.locator("#live-army-detail").textContent(),
    /无同 ID 战报，阵容未知/,
  );
  await page.evaluate(() => window.LiveArmyCommand.selectArmy(18411352));
  await page.waitForFunction(() =>
    document.querySelector("#live-army-detail")
      ?.classList.contains("hud-event-intelligence-risk-detected")
  );
  assert.deepEqual(
    await page.evaluate(() =>
      window.__hudEmitCalls
        .filter((event) =>
          String(event.dedupeKey || "").startsWith("live-army-risk:")
        )
        .map(({ type, target, dedupeKey, cooldownMs }) => ({
          type,
          target,
          dedupeKey,
          cooldownMs,
        }))
    ),
    [{
      type: "intelligence:risk-detected",
      target: "#live-army-detail",
      dedupeKey: "live-army-risk:18411352:2081480:high",
      cooldownMs: 10_000,
    }],
  );
  const exactMarker = await page.evaluate(() => {
    const marker = window.LiveArmyCommand.state.mapPlan.markers.find(
      (row) => row.armyId === 18411352,
    );
    return { x: marker.x, y: marker.y };
  });
  await page.mouse.dblclick(
    liveCanvasBox.x + exactMarker.x,
    liveCanvasBox.y + exactMarker.y,
  );
  await page.waitForSelector("#tab33.active");
  await page.waitForFunction(() =>
    window.IntelligenceCenter.state.selectedWid === 2081480
  );
  assert.equal(
    await page.locator("#intel-wid-input").inputValue(),
    "2081480",
  );
  await page.getByRole("button", { name: "实时部队", exact: true }).click();
  await page.waitForSelector("#tab35.active");
  const liveRequestsBeforeSse = liveArmyRequests;
  await page.evaluate(() => {
    window.dispatchEvent(new CustomEvent("stzb:stream-event", {
      detail: { type: "world_scene_delta", data: {} },
    }));
  });
  await page.waitForTimeout(500);
  assert.ok(liveArmyRequests > liveRequestsBeforeSse);
  assert.equal(
    await page.evaluate(() => window.LiveArmyCommand.state.selectedArmyId),
    18411352,
  );
  await page.evaluate(() => window.switchTab(26, null));
  await page.waitForSelector("#tab26.active");
  assert.equal(
    await page.locator("body").getAttribute("data-visual-domain"),
    "intelligence",
  );
  assert.equal(await page.locator("#tab26 > .hud-page-head").isVisible(), true);
  await page.waitForFunction(() => document.querySelectorAll("#sr-cards .hud-kpi").length === 6);
  await page.evaluate(() => window.switchTab(31, null));
  await page.waitForSelector("#tab31.active");
  await page.waitForFunction(() => {
    const metrics = document.querySelectorAll("#cc-kpi-grid .cc-kpi-card");
    return metrics.length === 5;
  });
  assert.equal(await page.locator("#cc-kpi-grid .cc-kpi-card").count(), 5);
  const maliciousAlert = page.locator("#cc-alert-list .cc-alert").first();
  assert.equal(await maliciousAlert.isVisible(), true);
  assert.equal(await maliciousAlert.getAttribute("onclick"), null);
  assert.equal(await maliciousAlert.getAttribute("onmouseover"), null);
  assert.equal(
    await maliciousAlert.evaluate((button) =>
      [...button.attributes].every((attribute) => !attribute.name.startsWith("on"))
    ),
    true,
  );
  assert.match(await maliciousAlert.textContent(), /payload/);
  await maliciousAlert.click();
  const maliciousFavorite = page
    .locator("#cc-favorites-list .cc-favorite")
    .filter({ hasText: "payload" });
  assert.equal(await maliciousFavorite.isVisible(), true);
  assert.equal(
    await maliciousFavorite.evaluate((favorite) =>
      [...favorite.querySelectorAll("*")].every((element) =>
        [...element.attributes].every((attribute) => !attribute.name.startsWith("on"))
      )
    ),
    true,
  );
  assert.match(await maliciousFavorite.textContent(), /payload/);
  assert.equal(await page.evaluate(() => window.__unsafePayload), 0);

  await page.keyboard.press(process.platform === "darwin" ? "Meta+K" : "Control+K");
  await page.waitForSelector("#cc-command-dialog[open]");
  await page.locator("#cc-command-input").fill("设置");
  await page.keyboard.press("Enter");
  await page.waitForSelector("#tab32.active");
  assert.equal(
    await page.evaluate(() => window.__settingToastCalls.length),
    0,
    "settings initialization emitted a saved toast",
  );

  await page.locator("#cc-setting-density").selectOption("compact");
  assert.equal(
    await page.evaluate(() => window.__settingToastCalls.length),
    1,
    "one density change must emit exactly one saved toast",
  );
  assert.equal(await page.locator("body").getAttribute("data-density"), "compact");
  assert.equal(
    await page.evaluate(() => JSON.parse(localStorage.getItem("stzb.commandCenter.settings")).density),
    "compact",
  );

  await page.locator("#cc-setting-api-token").fill("browser-secret");
  await page.locator("#cc-setting-api-token").dispatchEvent("change");
  assert.equal(
    await page.evaluate(() => window.__settingToastCalls.length),
    2,
    "one token change must emit exactly one saved toast",
  );
  assert.equal(
    await page.evaluate(() => sessionStorage.getItem("stzb.apiToken")),
    "browser-secret",
  );
  const settingToastText = await page.evaluate(
    () => JSON.stringify(window.__settingToastCalls),
  );
  assert.equal(settingToastText.includes("browser-secret"), false);
  assert.equal(settingToastText.includes("compact"), false);

  const firstSimulatorCatalog =
    fixtureControls.queueLifecycleResponse("simulatorCatalog");
  const firstSimulatorEngine =
    fixtureControls.queueLifecycleResponse("simulatorEngine");
  await page.getByRole("button", { name: "战斗模拟", exact: true }).click();
  await Promise.all([
    firstSimulatorCatalog.started,
    firstSimulatorEngine.started,
  ]);
  assert.equal(
    await page.locator("#sim-loader-state .hud-state-loading").isVisible(),
    true,
  );
  assert.equal(
    await page.locator("#sim-workbench").getAttribute("aria-busy"),
    "true",
  );
  firstSimulatorCatalog.respond(simulatorCatalog);
  firstSimulatorEngine.respond(simulatorEngine);
  await page.waitForFunction(() =>
    document.querySelector("#sim-engine-badge")?.textContent.includes("93ee999937")
      && !document.querySelector("#sim-workbench")?.hasAttribute("aria-busy"),
  );

  const failedSimulatorCatalog =
    fixtureControls.queueLifecycleResponse("simulatorCatalog");
  const failedSimulatorEngine =
    fixtureControls.queueLifecycleResponse("simulatorEngine");
  const failedSimulatorInitialization = page.evaluate(() =>
    window.initSimulator().catch((error) => error.message)
  );
  await Promise.all([
    failedSimulatorCatalog.started,
    failedSimulatorEngine.started,
  ]);
  assert.equal(
    await page.locator("#sim-loader-state .hud-state-refreshing").isVisible(),
    true,
  );
  assert.equal(
    await page.locator("#sim-attacker-team .sim-hero-card").count(),
    3,
  );
  failedSimulatorCatalog.respond({
    ok: false,
    error: "模拟目录暂不可用",
  });
  failedSimulatorEngine.respond(simulatorEngine);
  assert.equal(
    await failedSimulatorInitialization,
    "模拟目录暂不可用",
  );
  assert.equal(
    await page.locator("#sim-loader-state .hud-state-error").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#sim-loader-state").textContent(),
    /模拟目录暂不可用.*重试/s,
  );
  assert.equal(
    await page.locator("#sim-workbench").getAttribute("aria-busy"),
    null,
  );

  const retrySimulatorCatalog =
    fixtureControls.queueLifecycleResponse("simulatorCatalog");
  const retrySimulatorEngine =
    fixtureControls.queueLifecycleResponse("simulatorEngine");
  await page.locator("#sim-loader-state .hud-state-action").click();
  await Promise.all([
    retrySimulatorCatalog.started,
    retrySimulatorEngine.started,
  ]);
  assert.equal(
    await page.locator("#sim-loader-state .hud-state-refreshing").isVisible(),
    true,
  );
  retrySimulatorCatalog.respond(simulatorCatalog);
  retrySimulatorEngine.respond({
    ...simulatorEngine,
    sourceCommit: "retry999937d011b2a3dadf67ed39edfbb409aaca",
  });
  await page.waitForFunction(() =>
    document.querySelector("#sim-engine-badge")?.textContent.includes("retry99993")
      && !document.querySelector("#sim-workbench")?.hasAttribute("aria-busy"),
  );

  const staleResetCatalog =
    fixtureControls.queueLifecycleResponse("simulatorCatalog");
  const staleResetEngine =
    fixtureControls.queueLifecycleResponse("simulatorEngine");
  const staleInitialization = page.evaluate(() => window.initSimulator());
  await Promise.all([
    staleResetCatalog.started,
    staleResetEngine.started,
  ]);
  const resetCatalog =
    fixtureControls.queueLifecycleResponse("simulatorCatalog");
  const resetEngine =
    fixtureControls.queueLifecycleResponse("simulatorEngine");
  await page.locator("[data-sim-action='reset']").click();
  await Promise.all([resetCatalog.started, resetEngine.started]);
  resetCatalog.respond(simulatorCatalog);
  resetEngine.respond({
    ...simulatorEngine,
    sourceCommit: "reset999937d011b2a3dadf67ed39edfbb409aaca",
  });
  await page.waitForFunction(() =>
    document.querySelector("#sim-engine-badge")?.textContent.includes("reset99993")
      && !document.querySelector("#sim-workbench")?.hasAttribute("aria-busy"),
  );
  staleResetCatalog.respond({
    ...simulatorCatalog,
    heroes: simulatorHeroes.map((hero) => ({
      ...hero,
      name: `过期${hero.name}`,
    })),
  });
  staleResetEngine.respond({
    ...simulatorEngine,
    sourceCommit: "stale999937d011b2a3dadf67ed39edfbb409aaca",
  });
  assert.equal(await staleInitialization, null);
  assert.match(
    await page.locator("#sim-engine-badge").textContent(),
    /reset99993/,
  );
  assert.equal(
    (await page.locator("#sim-workbench").textContent()).includes("过期张辽"),
    false,
  );
  assert.equal(
    await page.locator("#sim-workbench").getAttribute("aria-busy"),
    null,
  );

  const overlappingSimulatorCatalog =
    fixtureControls.queueLifecycleResponse("simulatorCatalog");
  const overlappingSimulatorEngine =
    fixtureControls.queueLifecycleResponse("simulatorEngine");
  const overlappingInitialization = page.evaluate(() => window.initSimulator());
  await Promise.all([
    overlappingSimulatorCatalog.started,
    overlappingSimulatorEngine.started,
  ]);
  const overlappingSimulation =
    fixtureControls.queueLifecycleResponse("simulation");
  const overlappingSimulationPromise = page.evaluate(() =>
    window.runSimulate().catch((error) => error.message)
  );
  await overlappingSimulation.started;
  overlappingSimulatorCatalog.respond(simulatorCatalog);
  overlappingSimulatorEngine.respond({
    ...simulatorEngine,
    sourceCommit: "overlap937d011b2a3dadf67ed39edfbb409aaca",
  });
  await overlappingInitialization;
  const overlappingStateAfterInitialization = await page.evaluate(() => ({
    busy: document.querySelector("#sim-workbench")
      ?.getAttribute("aria-busy"),
    simulationLoading: Boolean(
      document.querySelector("#sim-loader-state .hud-state-loading"),
    ),
  }));
  overlappingSimulation.respond({
    ok: false,
    error: "初始化重叠模拟失败",
  });
  assert.equal(
    await overlappingSimulationPromise,
    "初始化重叠模拟失败",
  );
  assert.deepEqual(overlappingStateAfterInitialization, {
    busy: "true",
    simulationLoading: true,
  });
  assert.equal(
    await page.locator("#sim-workbench").getAttribute("aria-busy"),
    null,
  );

  const reverseOverlapCatalog =
    fixtureControls.queueLifecycleResponse("simulatorCatalog");
  const reverseOverlapEngine =
    fixtureControls.queueLifecycleResponse("simulatorEngine");
  const reverseOverlapInitialization = page.evaluate(() =>
    window.initSimulator()
  );
  await Promise.all([
    reverseOverlapCatalog.started,
    reverseOverlapEngine.started,
  ]);
  const reverseOverlapSimulation =
    fixtureControls.queueLifecycleResponse("simulation");
  const reverseOverlapSimulationPromise = page.evaluate(() =>
    window.runSimulate().catch((error) => error.message)
  );
  await reverseOverlapSimulation.started;
  reverseOverlapSimulation.respond({
    ok: false,
    error: "重叠模拟失败",
  });
  assert.equal(
    await reverseOverlapSimulationPromise,
    "重叠模拟失败",
  );
  const reverseOverlapState = await page.evaluate(() => ({
    busy: document.querySelector("#sim-workbench")
      ?.getAttribute("aria-busy"),
    initializationRefreshing: Boolean(
      document.querySelector("#sim-loader-state .hud-state-refreshing"),
    ),
  }));
  reverseOverlapCatalog.respond(simulatorCatalog);
  reverseOverlapEngine.respond({
    ...simulatorEngine,
    sourceCommit: "reverse937d011b2a3dadf67ed39edfbb409aaca",
  });
  await reverseOverlapInitialization;
  assert.deepEqual(reverseOverlapState, {
    busy: "true",
    initializationRefreshing: true,
  });
  assert.equal(
    await page.locator("#sim-loader-state .hud-state-error").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#sim-loader-state").textContent(),
    /重叠模拟失败.*重试/s,
  );
  assert.equal(
    await page.locator("#sim-workbench").getAttribute("aria-busy"),
    null,
  );

  const staleSimulation = fixtureControls.queueLifecycleResponse("simulation");
  const simulationEventsBeforeReset = await page.evaluate(() =>
    window.__hudEmitCalls.filter(
      (event) => event.type === "simulation:completed",
    ).length
  );
  const staleSimulationPromise = page.evaluate(() => window.runSimulate());
  await staleSimulation.started;
  assert.equal(
    await page.locator("#sim-workbench").getAttribute("aria-busy"),
    "true",
  );
  const simulationResetCatalog =
    fixtureControls.queueLifecycleResponse("simulatorCatalog");
  const simulationResetEngine =
    fixtureControls.queueLifecycleResponse("simulatorEngine");
  await page.locator("[data-sim-action='reset']").click();
  await Promise.all([
    simulationResetCatalog.started,
    simulationResetEngine.started,
  ]);
  simulationResetCatalog.respond(simulatorCatalog);
  simulationResetEngine.respond({
    ...simulatorEngine,
    sourceCommit: "simreset937d011b2a3dadf67ed39edfbb409aaca",
  });
  await page.waitForFunction(() =>
    document.querySelector("#sim-engine-badge")?.textContent
      .includes("simreset93")
      && !document.querySelector("#sim-workbench")?.hasAttribute("aria-busy"),
  );
  staleSimulation.respond({
    ok: true,
    engine: "stale-engine",
    marker: "stale-after-reset",
  });
  assert.equal(await staleSimulationPromise, null);
  assert.deepEqual(
    await page.evaluate(() => ({
      result: window.StzbSimulator.getState().result,
      sourceContext: window.StzbSimulator.getState().sourceContext,
    })),
    { result: null, sourceContext: null },
  );
  assert.equal(
    await page.evaluate(() =>
      window.__hudEmitCalls.filter(
        (event) => event.type === "simulation:completed",
      ).length
    ),
    simulationEventsBeforeReset,
  );
  assert.equal(
    await page.locator("#sim-workbench").getAttribute("aria-busy"),
    null,
  );

  const staleEditedSimulation =
    fixtureControls.queueLifecycleResponse("simulation");
  const currentEditedSimulation =
    fixtureControls.queueLifecycleResponse("simulation");
  const simulationEventsBeforeEdit = await page.evaluate(() =>
    window.__hudEmitCalls.filter(
      (event) => event.type === "simulation:completed",
    ).length
  );
  await page.evaluate(() => {
    window.__simulationEvidenceKeys = [];
    window.addEventListener("stzb:simulation-completed", (event) => {
      window.__simulationEvidenceKeys.push(
        event.detail?.sourceContext?.lineupKey || "",
      );
    });
  });
  const staleEditedPromise = page.evaluate(() => window.runSimulate());
  await staleEditedSimulation.started;
  const editedCatalog =
    fixtureControls.queueLifecycleResponse("simulatorCatalog");
  const editedEngine =
    fixtureControls.queueLifecycleResponse("simulatorEngine");
  const editedLineupPromise = page.evaluate(() =>
    window.StzbSimulator.loadLineup(
      {
        heroes: [
          { id: 100013, position: 0, level: 40, up: 5, equip_skills: [] },
          { id: 100649, position: 1, level: 40, up: 5, equip_skills: [] },
          { id: 100023, position: 2, level: 40, up: 5, equip_skills: [] },
        ],
      },
      {
        camp: "blue",
        source: "intelligence-research",
        lineupKey: "100013.100649.100023",
        returnTab: 34,
      },
    )
  );
  await Promise.all([editedCatalog.started, editedEngine.started]);
  staleEditedSimulation.respond({
    ok: true,
    engine: "stale-edit-engine",
    marker: "stale-after-edit",
  });
  assert.equal(await staleEditedPromise, null);
  assert.equal(
    await page.evaluate(() =>
      window.__hudEmitCalls.filter(
        (event) => event.type === "simulation:completed",
      ).length
    ),
    simulationEventsBeforeEdit,
  );
  assert.deepEqual(
    await page.evaluate(() => window.__simulationEvidenceKeys),
    [],
  );
  editedCatalog.respond(simulatorCatalog);
  editedEngine.respond(simulatorEngine);
  await editedLineupPromise;
  const currentEditedPromise = page.evaluate(() => window.runSimulate());
  await currentEditedSimulation.started;
  currentEditedSimulation.respond({
    ok: true,
    engine: "current-edit-engine",
    marker: "current-after-edit",
  });
  await currentEditedPromise;
  assert.deepEqual(
    await page.evaluate((eventsBeforeEdit) => ({
      marker: window.StzbSimulator.getState().result?.marker,
      lineupKey: window.StzbSimulator.getState().sourceContext?.lineupKey,
      emittedKeys: window.__hudEmitCalls
        .filter((event) => event.type === "simulation:completed")
        .slice(eventsBeforeEdit)
        .map((event) => event.dedupeKey),
      evidenceKeys: window.__simulationEvidenceKeys,
    }), simulationEventsBeforeEdit),
    {
      marker: "current-after-edit",
      lineupKey: "100013.100649.100023",
      emittedKeys: ["simulation:100:100013.100649.100023"],
      evidenceKeys: ["100013.100649.100023"],
    },
  );

  await page.evaluate(() => {
    localStorage.setItem("stzb.simulator.templates.v1", JSON.stringify([{
      schemaVersion: 1,
      name: "真实模板 B",
      scope: "attacker",
      repeat: 100,
      seedMode: "fixed",
      seed: 20260816,
      attacker: {
        morale: 88,
        heroes: [{
          id: 100649,
          position: 0,
          level: 45,
          up: 5,
          equip_skills: [0, 0],
        }],
      },
    }]));
  });
  await page.locator(
    "#sim-workbench [data-sim-action='open-templates']",
  ).first().click();
  await page.waitForSelector("#sim-template-dialog[open]");
  const staleTemplateSimulation =
    fixtureControls.queueLifecycleResponse("simulation");
  const simulationEventsBeforeTemplate = await page.evaluate(() =>
    window.__hudEmitCalls.filter(
      (event) => event.type === "simulation:completed",
    ).length
  );
  const staleTemplatePromise = page.evaluate(() => window.runSimulate());
  await staleTemplateSimulation.started;
  await page.locator(
    "#sim-template-dialog [data-sim-action='load-template']",
  ).click();
  staleTemplateSimulation.respond({
    ok: true,
    engine: "stale-template-engine",
    marker: "stale-after-template",
  });
  assert.equal(await staleTemplatePromise, null);
  assert.deepEqual(
    await page.evaluate(() => ({
      heroId: window.StzbSimulator.getState().blue[0]?.id,
      sourceContext: window.StzbSimulator.getState().sourceContext,
      marker: window.StzbSimulator.getState().result?.marker,
      simulationEvents: window.__hudEmitCalls.filter(
        (event) => event.type === "simulation:completed",
      ).length,
    })),
    {
      heroId: 100649,
      sourceContext: null,
      marker: "current-after-edit",
      simulationEvents: simulationEventsBeforeTemplate,
    },
  );

  const expectedVisibleNavigation = [
    "玩家队伍",
    "自定义积分",
    "打城考勤",
    "同盟成员队伍",
    "武将阵容",
    "团数据",
    "战斗模拟",
    "州郡分布",
    "设置中心",
    "战场情报",
    "实时部队",
    "阵容战法研究",
  ];
  const visibleNavigation = await page.evaluate(() =>
    [...document.querySelectorAll("nav > button[data-tab-index]")]
      .filter((button) => getComputedStyle(button).display !== "none")
      .map((button) => button.textContent.trim()),
  );
  assert.deepEqual(visibleNavigation, expectedVisibleNavigation);
  assert.equal(await page.locator(".ds-nav-more").count(), 0);
  assert.equal(await page.locator(".ds-nav-label").count(), 0);
  for (const label of expectedVisibleNavigation) {
    await page.getByRole("button", { name: label, exact: true }).click();
    const activeTab = await page.locator("nav > button.active").textContent();
    assert.equal(activeTab.trim(), label, `visible nav ${label} did not activate`);
  }

  await page.getByRole("button", { name: "玩家队伍", exact: true }).click();
  await page.waitForSelector("#tab7.active");
  assert.equal(
    await page.locator("body").getAttribute("data-visual-domain"),
    "organization",
  );
  await page.waitForFunction(() =>
    document.querySelector("#pbt-body")?.textContent.includes("玩家甲"),
  );
  assert.equal(await page.locator("#tab7 > .hud-page-head").isVisible(), true);
  assert.equal(await page.locator("#pbt-body .organization-identity").count(), 1);

  await page.getByRole("button", { name: "同盟成员队伍", exact: true }).click();
  await page.waitForSelector("#tab17.active");
  assert.equal(
    await page.locator("body").getAttribute("data-visual-domain"),
    "organization",
  );
  await page.waitForFunction(() =>
    document.querySelector("#agt-body")?.textContent.includes("玩家甲"),
  );
  assert.equal(await page.locator("#agt-body .organization-group-chip").count() > 0, true);

  await page.getByRole("button", { name: "团数据", exact: true }).click();
  await page.waitForSelector("#tab24.active");
  assert.equal(
    await page.locator("body").getAttribute("data-visual-domain"),
    "organization",
  );
  await page.waitForFunction(() =>
    document.querySelector("#tr-body")?.textContent.includes("一团"),
  );
  assert.equal(await page.locator("#tr-cards .hud-kpi").count(), 6);

  await page.getByRole("button", { name: "打城考勤", exact: true }).click();
  await page.waitForSelector("#tab16.active");
  assert.equal(
    await page.locator("body").getAttribute("data-visual-domain"),
    "operations",
  );
  await page.waitForFunction(() => document.querySelectorAll("#task-body tr").length === 3);
  assert.equal(await page.locator("#tab16 > .hud-page-head").isVisible(), true);
  assert.equal(await page.locator("#task-body .operation-stage-strip").count(), 3);
  assert.equal(
    await page.locator(
      "#task-body tr:first-child .operation-stage[data-stage='assembling'][data-state='active']",
    ).getAttribute("class"),
    "operation-stage is-active",
  );
  const maliciousTaskRow = page.locator("#task-body tr").filter({
    hasText: "恶意任务 ID",
  });
  assert.equal(await maliciousTaskRow.count(), 1);
  assert.equal(
    await maliciousTaskRow.evaluate((row) =>
      [row, ...row.querySelectorAll("*")].every((element) =>
        [...element.attributes].every((attribute) =>
          !attribute.name.toLowerCase().startsWith("on")
          && attribute.name !== "data-task-id"
        )
      )
    ),
    true,
  );
  for (const actionName of ["考勤详情", "开始统计", "删除"]) {
    await maliciousTaskRow.getByRole("button", {
      name: actionName,
      exact: true,
    }).click();
    assert.match(
      await page.locator("#toast").textContent(),
      /任务 ID 无效|已拒绝/,
    );
    assert.equal(await page.evaluate(() => window.__taskXss), 0);
    assert.equal(taskActionRequests.length, 0);
  }
  maliciousTaskCompleteUserNum = 1;
  await page.evaluate(() => window.loadTasks());
  const maliciousStageToast = page.locator(
    "#hud-toast-region .hud-toast",
  ).filter({ hasText: "恶意任务 ID 进入 成员集结" });
  assert.equal(await maliciousStageToast.count(), 1);
  assert.equal(
    await maliciousStageToast.getAttribute("data-dedupe-key"),
    "operation-task:index-2:assembling",
  );
  assert.equal(
    await page.evaluate((payload) =>
      [...document.querySelectorAll("*")].every((element) =>
        [...element.attributes]
          .filter((attribute) => attribute.name.startsWith("data-"))
          .every((attribute) => !attribute.value.includes(payload))
      ), maliciousTaskId
    ),
    true,
  );
  assert.equal(
    await page.evaluate(() => window.__hudEmitCalls.some((event) =>
      event.type === "operation:stage-changed"
      && event.dedupeKey === "operation-task:index-2:assembling"
    )),
    true,
  );
  assert.equal(await page.evaluate(() => window.__taskXss), 0);

  const normalTaskRow = page.locator("#task-body tr").filter({
    hasText: "集结测试",
  });
  const detailResponse = page.waitForResponse((response) =>
    response.request().method() === "GET"
      && new URL(response.url()).pathname === "/api/tasks/1"
  );
  await normalTaskRow.getByRole("button", {
    name: "考勤详情",
    exact: true,
  }).click();
  await detailResponse;
  assert.equal(
    await page.locator("#task-detail-panel").evaluate(
      (panel) => !panel.classList.contains("is-hidden"),
    ),
    true,
  );
  assert.match(
    await page.locator("#task-detail-title").textContent(),
    /集结测试/,
  );
  await page.evaluate(() => window.closeTaskDetail());

  const statisticsDetailResponse = page.waitForResponse((response) =>
    response.request().method() === "GET"
      && new URL(response.url()).pathname === "/api/tasks/1"
  );
  await normalTaskRow.getByRole("button", {
    name: "开始统计",
    exact: true,
  }).click();
  await statisticsDetailResponse;
  await page.waitForFunction(() =>
    document.querySelector("#stats-confirm-modal")?.style.display === "flex"
  );
  const statisticsResponse = page.waitForResponse((response) =>
    response.request().method() === "POST"
      && new URL(response.url()).pathname === "/api/tasks/1/statistics"
  );
  await page.getByRole("button", { name: "确认统计", exact: false }).click();
  await statisticsResponse;

  const deleteResponse = page.waitForResponse((response) =>
    response.request().method() === "DELETE"
      && new URL(response.url()).pathname === "/api/tasks/1"
  );
  page.once("dialog", (dialog) => dialog.accept());
  await normalTaskRow.getByRole("button", {
    name: "删除",
    exact: true,
  }).click();
  await deleteResponse;
  assert.deepEqual(taskActionRequests, [
    { method: "GET", path: "/api/tasks/1" },
    { method: "GET", path: "/api/tasks/1" },
    { method: "POST", path: "/api/tasks/1/statistics" },
    { method: "DELETE", path: "/api/tasks/1" },
  ]);
  assert.equal(await page.evaluate(() => window.__taskXss), 0);
  await page.getByRole("button", { name: "新建任务", exact: true }).first().click();
  await page.waitForFunction(() =>
    document.querySelector("#create-task-modal")?.style.display === "flex"
      && document.querySelector("#ct-group-tags")?.textContent.includes("攻城组")
  );
  const maliciousGroupButton = page.locator("#ct-group-tags button").filter({
    hasText: "攻城组",
  });
  assert.equal(await maliciousGroupButton.count(), 1);
  assert.equal(
    await maliciousGroupButton.evaluate((button) =>
      [...button.attributes].every((attribute) =>
        !attribute.name.startsWith("on")
        && attribute.name !== "data-group"
      )
    ),
    true,
  );
  assert.equal(
    await maliciousGroupButton.evaluate((button) => button.querySelector("img")),
    null,
  );
  await maliciousGroupButton.click();
  assert.equal(
    await page.locator("#ct-groups").inputValue(),
    maliciousGroupName,
  );
  assert.equal(await page.evaluate(() => window.__unsafePayload), 0);
  await page.evaluate(() => window.closeCreateTask());

  await page.getByRole("button", { name: "自定义积分", exact: true }).click();
  await page.waitForSelector("#tab8.active");
  assert.equal(
    await page.locator("body").getAttribute("data-visual-domain"),
    "analysis",
  );
  assert.equal(await page.locator("#tab8 .hud-page-head").isVisible(), true);
  await page.waitForFunction(
    (playerName) => document.querySelector("#score-board-body")?.textContent.includes(playerName),
    maliciousScorePlayerName,
  );
  assert.equal(await page.locator("#score-kpis .score-kpi").count(), 5);
  const maliciousCompletenessBadge =
    page.locator("#score-board-body .score-status").first();
  assert.equal(
    await maliciousCompletenessBadge.evaluate((badge) =>
      [...badge.attributes].every((attribute) =>
        !attribute.name.startsWith("on")
      )
    ),
    true,
  );
  assert.deepEqual(
    await maliciousCompletenessBadge.evaluate((badge) =>
      [...badge.classList].filter((name) =>
        ["complete", "partial", "missing"].includes(name)
      )
    ),
    ["partial"],
  );
  assert.equal(await page.evaluate(() => window.__unsafePayload), 0);
  for (const board of ["overall", "battle", "siege"]) {
    await page.locator(`[data-score-board='${board}']`).click();
    await page.waitForFunction((value) =>
      document.querySelector(`[data-score-board='${value}']`)?.classList.contains("active"),
    board);
  }
  const firstPlayerDetail = fixtureControls.queueScoreResponse("player");
  await page.locator("#score-board-body .score-player-row").first().click();
  await firstPlayerDetail.started;
  await page.waitForSelector("#score-player-dialog[open]");
  assert.equal(
    await page.locator("#score-player-surface").getAttribute("aria-busy"),
    "true",
  );
  assert.equal(
    await page.locator("#score-player-status .hud-state-loading").isVisible(),
    true,
  );
  firstPlayerDetail.respond({
    ok: true,
    ...scoreRows[0],
    breakdown: {
      metrics: scoreRows[0].metrics,
      components: {
        battles: 10, wins: 8, draws: 1, gongxun: 3,
        mainCity: 10, tear: 3, attendance: 3,
      },
    },
    rule: { version: 1 },
    adjustments: [{ points: 1.5, reason: "组织奖励" }],
  });
  await page.waitForFunction(() =>
    document.querySelector("#score-player-content")
      ?.textContent.includes("战斗贡献")
      && !document.querySelector("#score-player-surface")
        ?.hasAttribute("aria-busy"),
  );
  assert.equal((await page.locator("#score-player-content").textContent()).includes("战斗贡献"), true);

  const olderPlayerDetail = fixtureControls.queueScoreResponse("player");
  const newerPlayerDetail = fixtureControls.queueScoreResponse("player");
  const olderPlayerPromise = page.evaluate(() =>
    window.ScoreCenter.openPlayer("旧详情")
  );
  await olderPlayerDetail.started;
  assert.equal(
    await page.locator("#score-player-status .hud-state-refreshing").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#score-player-content").textContent(),
    /战斗贡献/,
  );
  const newerPlayerPromise = page.evaluate(() =>
    window.ScoreCenter.openPlayer("新详情")
  );
  await newerPlayerDetail.started;
  olderPlayerDetail.respond({
    ok: true,
    playerName: "旧详情",
    unionName: "旧盟",
    score: 1,
    battleScore: 1,
    siegeScore: 0,
    breakdown: { metrics: {}, components: {} },
    rule: { version: 1 },
    adjustments: [],
  });
  await olderPlayerPromise;
  assert.equal(
    await page.locator("#score-player-surface").getAttribute("aria-busy"),
    "true",
  );
  assert.equal(
    (await page.locator("#score-player-content").textContent()).includes("旧详情"),
    false,
  );
  newerPlayerDetail.respond({
    ok: true,
    playerName: "新详情",
    unionName: "新盟",
    score: 2,
    battleScore: 2,
    siegeScore: 0,
    breakdown: { metrics: {}, components: {} },
    rule: { version: 2 },
    adjustments: [],
  });
  await newerPlayerPromise;
  assert.match(await page.locator("#score-player-content").textContent(), /新详情/);
  assert.equal(
    await page.evaluate(() => window.ScoreCenter.state.selectedPlayer),
    "新详情",
  );
  assert.equal(
    await page.locator("#score-player-surface").getAttribute("aria-busy"),
    null,
  );

  const failedPlayerDetail = fixtureControls.queueScoreResponse("player");
  const failedPlayerPromise = page.evaluate(() =>
    window.ScoreCenter.openPlayer("失败详情")
  );
  await failedPlayerDetail.started;
  assert.match(await page.locator("#score-player-content").textContent(), /新详情/);
  failedPlayerDetail.respond({ ok: false, error: "详情暂不可用" });
  await failedPlayerPromise;
  assert.equal(
    await page.locator("#score-player-status .hud-state-error").isVisible(),
    true,
  );
  assert.match(await page.locator("#score-player-status").textContent(), /详情暂不可用/);
  assert.match(await page.locator("#score-player-content").textContent(), /新详情/);
  assert.equal(
    await page.evaluate(() => window.ScoreCenter.state.selectedPlayer),
    "新详情",
  );
  assert.equal(
    await page.locator("#score-player-surface").getAttribute("aria-busy"),
    null,
  );

  const adjustmentAction = page.getByRole("button", { name: "添加奖惩" });
  assert.equal(await adjustmentAction.getAttribute("data-score-action"), "open-adjustment");
  assert.equal(await adjustmentAction.getAttribute("onclick"), null);
  assert.equal(await adjustmentAction.getAttribute("onmouseover"), null);
  assert.equal(await adjustmentAction.evaluate((button) => button.attributes.length), 3);
  await adjustmentAction.click();
  await page.waitForSelector("#score-adjustment-dialog[open]");
  assert.equal(
    await page.locator("#score-adjustment-player").inputValue(),
    "新详情",
  );
  assert.equal(await page.evaluate(() => window.__scoreXss), undefined);
  await page.locator("#score-adjustment-points").fill("5");
  await page.locator("#score-adjustment-reason").fill("组织奖励");
  const failedAdjustment = fixtureControls.queueScoreResponse("adjustment");
  const adjustmentPostsBefore =
    fixtureCounters.scoreRoutes.adjustment;
  const adjustmentSave = page.locator("#score-adjustment-save");
  await adjustmentSave.evaluate((button) => {
    button.click();
    button.click();
  });
  await failedAdjustment.started;
  assert.equal(await adjustmentSave.isDisabled(), true);
  assert.equal(
    await page.locator("#score-adjustment-surface").getAttribute("aria-busy"),
    "true",
  );
  assert.equal(
    fixtureCounters.scoreRoutes.adjustment - adjustmentPostsBefore,
    1,
  );
  failedAdjustment.respond({ ok: false, error: "奖惩保存失败" });
  await page.waitForFunction(() =>
    !document.querySelector("#score-adjustment-save")?.disabled
      && !document.querySelector("#score-adjustment-surface")
        ?.hasAttribute("aria-busy"),
  );
  assert.equal(
    await page.locator("#score-adjustment-status .hud-state-error").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#score-adjustment-status").textContent(),
    /奖惩保存失败/,
  );
  assert.equal(
    await page.locator("#score-adjustment-dialog").getAttribute("open"),
    "",
  );
  await page.locator("#score-adjustment-dialog .score-dialog-close").click();
  await page.waitForFunction(() =>
    !document.querySelector("#score-adjustment-dialog")?.open
  );
  await page.evaluate(() => window.ScoreCenter.openAdjustment("新详情"));
  await page.waitForSelector("#score-adjustment-dialog[open]");
  assert.equal(
    await page.locator("#score-adjustment-status .hud-state-idle").isVisible(),
    true,
  );
  assert.doesNotMatch(
    await page.locator("#score-adjustment-status").textContent(),
    /奖惩保存失败/,
  );
  assert.equal(
    await page.locator("#score-adjustment-player").inputValue(),
    "新详情",
  );
  await page.locator("#score-adjustment-points").fill("5");
  await page.locator("#score-adjustment-reason").fill("组织奖励");

  const successfulAdjustment =
    fixtureControls.queueScoreResponse("adjustment");
  const refreshedPlayer = fixtureControls.queueScoreResponse("player");
  await adjustmentSave.click();
  await successfulAdjustment.started;
  successfulAdjustment.respond({
    ok: true,
    adjustment: { id: 2, points: 5, reason: "组织奖励" },
  });
  await refreshedPlayer.started;
  refreshedPlayer.respond({
    ok: true,
    playerName: "新详情",
    unionName: "新盟",
    score: 7,
    battleScore: 2,
    siegeScore: 0,
    breakdown: { metrics: {}, components: {} },
    rule: { version: 2 },
    adjustments: [{ points: 5, reason: "组织奖励" }],
  });
  await page.waitForFunction(() => !document.querySelector("#score-adjustment-dialog")?.open);
  await page.waitForFunction(() =>
    document.querySelector("#score-player-content")?.textContent
      .includes("组织奖励")
  );
  assert.equal(
    await page.locator("#score-player-dialog").getAttribute("open"),
    "",
  );
  await page.locator("#score-player-dialog .score-dialog-close").click();
  await page.waitForFunction(() =>
    !document.querySelector("#score-player-dialog")?.open
  );

  await page.getByRole("button", { name: "规则控制台" }).click();
  await page.waitForSelector("#score-rule-dialog[open]");
  await page.locator("#score-rule-preset").selectOption("season_reward");
  await page.locator("[data-score-rule-field='winWeight']").fill("3");
  assert.equal((await page.locator("#score-formula-preview").textContent()).includes("胜场×3"), true);
  const createdRule = fixtureControls.queueScoreResponse("ruleCreate");
  const failedRuleActivation =
    fixtureControls.queueScoreResponse("ruleActivate");
  const ruleCreatesBefore = fixtureCounters.scoreRoutes.ruleCreate;
  const ruleActivationsBefore = fixtureCounters.scoreRoutes.ruleActivate;
  const ruleSave = page.locator("#score-rule-save");
  const ruleClose = page.locator("#score-rule-close");
  const ruleName = page.locator("#score-rule-name");
  const rulePreset = page.locator("#score-rule-preset");
  const ruleField = page.locator("[data-score-rule-field='winWeight']");
  const ruleControls = page.locator(
    "#score-rule-name, #score-rule-preset, [data-score-rule-field], "
      + "#score-rule-save, #score-rule-close",
  );
  await ruleSave.evaluate((button) => {
    button.click();
    button.click();
  });
  await createdRule.started;
  assert.equal(await ruleSave.isDisabled(), true);
  assert.equal(await ruleSave.getAttribute("aria-busy"), "true");
  assert.equal(
    fixtureCounters.scoreRoutes.ruleCreate - ruleCreatesBefore,
    1,
  );
  createdRule.respond({
    ok: true,
    rule: { id: 2, version: 2, name: "待激活规则" },
  });
  await failedRuleActivation.started;
  assert.equal(
    await ruleControls.evaluateAll((controls) => controls.every((control) =>
      control.disabled && control.getAttribute("aria-busy") === "true"
    )),
    true,
  );
  await ruleName.evaluate((input) => {
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
  await rulePreset.evaluate((select) => {
    select.dispatchEvent(new Event("change", { bubbles: true }));
  });
  await ruleField.evaluate((input) => {
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
  await ruleClose.evaluate((button) => button.click());
  await page.keyboard.press("Escape");
  assert.equal(
    await page.locator("#score-rule-dialog").getAttribute("open"),
    "",
  );
  assert.deepEqual(
    await page.evaluate(() => ({
      pendingRuleId: window.ScoreCenter.state.pendingRuleId,
      phase: window.ScoreCenter.state.ruleTransactionPhase,
    })),
    { pendingRuleId: 2, phase: "activate" },
  );
  assert.equal(
    fixtureCounters.scoreRoutes.ruleActivate - ruleActivationsBefore,
    1,
  );
  failedRuleActivation.respond({ ok: false, error: "规则激活失败" });
  await page.waitForFunction(() =>
    !document.querySelector("#score-rule-save")?.disabled
      && !document.querySelector("#score-rule-save")
        ?.hasAttribute("aria-busy")
      && !document.querySelector("#score-rule-surface")
        ?.hasAttribute("aria-busy")
      && [...document.querySelectorAll(
        "#score-rule-name, #score-rule-preset, [data-score-rule-field], "
          + "#score-rule-save, #score-rule-close",
      )].every((control) =>
        !control.disabled && !control.hasAttribute("aria-busy")
      ),
  );
  assert.equal(
    await page.locator("#score-rule-status .hud-state-error").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#score-rule-status").textContent(),
    /规则激活失败/,
  );
  assert.deepEqual(
    await page.evaluate(() => ({
      pendingRuleId: window.ScoreCenter.state.pendingRuleId,
      phase: window.ScoreCenter.state.ruleTransactionPhase,
    })),
    { pendingRuleId: 2, phase: "activate" },
  );

  const successfulRuleActivation =
    fixtureControls.queueScoreResponse("ruleActivate");
  await page.locator("#score-rule-status .hud-state-action").evaluate(
    (button) => {
      button.click();
      button.click();
    },
  );
  await successfulRuleActivation.started;
  assert.equal(await ruleSave.isDisabled(), true);
  assert.equal(await ruleSave.getAttribute("aria-busy"), "true");
  assert.equal(
    await ruleControls.evaluateAll((controls) => controls.every((control) =>
      control.disabled && control.getAttribute("aria-busy") === "true"
    )),
    true,
  );
  assert.equal(
    fixtureCounters.scoreRoutes.ruleCreate - ruleCreatesBefore,
    1,
  );
  assert.equal(
    fixtureCounters.scoreRoutes.ruleActivate - ruleActivationsBefore,
    2,
  );
  successfulRuleActivation.respond({
    ok: true,
    rule: { id: 2, version: 2 },
  });
  await page.waitForFunction(() =>
    !document.querySelector("#score-rule-dialog")?.open
      && !document.querySelector("#score-rule-save")
        ?.hasAttribute("aria-busy")
      && [...document.querySelectorAll(
        "#score-rule-name, #score-rule-preset, [data-score-rule-field], "
          + "#score-rule-save, #score-rule-close",
      )].every((control) =>
        !control.disabled && !control.hasAttribute("aria-busy")
      )
  );
  assert.deepEqual(
    await page.evaluate(() => ({
      pendingRuleId: window.ScoreCenter.state.pendingRuleId,
      phase: window.ScoreCenter.state.ruleTransactionPhase,
    })),
    { pendingRuleId: null, phase: "idle" },
  );

  await page.getByRole("button", { name: "规则控制台" }).click();
  await page.waitForSelector("#score-rule-dialog[open]");
  assert.equal(
    await page.locator("#score-rule-status .hud-state-idle").isVisible(),
    true,
  );
  assert.doesNotMatch(
    await page.locator("#score-rule-status").textContent(),
    /规则激活失败|已激活/,
  );
  assert.equal(
    await page.locator("#score-rule-name").inputValue(),
    "同盟综合贡献",
  );
  await page.locator("#score-rule-dialog .score-dialog-close").click();

  await page.locator("#score-date-preset").selectOption("7d");
  assert.notEqual(await page.locator("#score-start-date").inputValue(), "");
  assert.notEqual(await page.locator("#score-end-date").inputValue(), "");
  await page.locator("#score-start-date").fill("2026-08-01");
  await page.locator("#score-end-date").fill("2026-08-15");
  await page.evaluate(() => {
    window.__scoreRecalculationEvents = [];
    const emit = window.HudSystem.emit.bind(window.HudSystem);
    window.HudSystem.emit = (detail) => {
      if (detail?.type === "score:recalculated") {
        window.__scoreRecalculationEvents.push(detail);
      }
      return emit(detail);
    };
  });
  const firstPreview = fixtureControls.queueScoreResponse("preview");
  await page.getByRole("button", { name: "预览重算" }).click();
  await firstPreview.started;
  await page.waitForSelector("#score-preview-dialog[open]");
  assert.equal(
    await page.locator("#score-preview-status .hud-state-loading").isVisible(),
    true,
  );
  assert.equal(
    await page.locator("#score-preview-surface").getAttribute("aria-busy"),
    "true",
  );
  firstPreview.respond({
    ok: true,
    previewToken: "preview-stable",
    summary: {
      players: 3, scoreTotal: 93, battleTotal: 52,
      siegeTotal: 16, adjustmentTotal: 1.5,
    },
    rows: [{
      ...recalculatedScoreRows[0],
      oldScore: scoreRows[0].score,
      scoreDelta: 9.5,
      oldRank: 1,
      newRank: 1,
      rankDelta: 0,
      breakdown: {},
    }],
  });
  await page.waitForFunction(() =>
    document.querySelector("#score-preview-rows")
      ?.textContent.includes("+9.5")
      && !document.querySelector("#score-preview-surface")
        ?.hasAttribute("aria-busy"),
  );
  assert.equal((await page.locator("#score-preview-rows").textContent()).includes("+9.5"), true);

  const olderPreview = fixtureControls.queueScoreResponse("preview");
  const newerPreview = fixtureControls.queueScoreResponse("preview");
  const olderPreviewPromise = page.evaluate(() => window.ScoreCenter.preview());
  await olderPreview.started;
  assert.equal(
    await page.locator("#score-preview-status .hud-state-refreshing").isVisible(),
    true,
  );
  assert.match(await page.locator("#score-preview-rows").textContent(), /\+9.5/);
  const newerPreviewPromise = page.evaluate(() => window.ScoreCenter.preview());
  await newerPreview.started;
  olderPreview.respond({
    ok: true,
    previewToken: "preview-old",
    summary: {
      players: 1, scoreTotal: 1, battleTotal: 1, siegeTotal: 0,
    },
    rows: [{
      playerName: "旧预览",
      oldRank: 1,
      newRank: 1,
      scoreDelta: 1,
      rankDelta: 0,
    }],
  });
  await olderPreviewPromise;
  assert.equal(
    await page.evaluate(() => window.ScoreCenter.state.preview.previewToken),
    "preview-stable",
  );
  assert.equal(
    (await page.locator("#score-preview-rows").textContent()).includes("旧预览"),
    false,
  );
  assert.equal(
    await page.locator("#score-preview-surface").getAttribute("aria-busy"),
    "true",
  );
  newerPreview.respond({
    ok: true,
    previewToken: "preview-new",
    summary: {
      players: 1, scoreTotal: 20, battleTotal: 20, siegeTotal: 0,
    },
    rows: [{
      playerName: "新预览",
      oldRank: 1,
      newRank: 1,
      scoreDelta: 20,
      rankDelta: 0,
    }],
  });
  await newerPreviewPromise;
  assert.equal(
    await page.evaluate(() => window.ScoreCenter.state.preview.previewToken),
    "preview-new",
  );
  assert.match(await page.locator("#score-preview-rows").textContent(), /新预览/);
  assert.equal(
    await page.locator("#score-preview-surface").getAttribute("aria-busy"),
    null,
  );

  const failedPreview = fixtureControls.queueScoreResponse("preview");
  const failedPreviewPromise = page.evaluate(() => window.ScoreCenter.preview());
  await failedPreview.started;
  failedPreview.respond({ ok: false, error: "预览刷新失败" });
  await failedPreviewPromise;
  assert.equal(
    await page.locator("#score-preview-status .hud-state-error").isVisible(),
    true,
  );
  assert.match(await page.locator("#score-preview-status").textContent(), /预览刷新失败/);
  assert.match(await page.locator("#score-preview-rows").textContent(), /新预览/);
  assert.equal(
    await page.evaluate(() => window.ScoreCenter.state.preview.previewToken),
    "preview-new",
  );
  assert.equal(
    await page.locator("#score-preview-surface").getAttribute("aria-busy"),
    null,
  );
  assert.equal(
    await page.evaluate(() => window.__scoreRecalculationEvents.length),
    0,
  );
  const recalcResponse = fixtureControls.queueScoreResponse("recalc");
  const recalcWritesBefore = fixtureCounters.scoreRoutes.recalc;
  await page.locator("#score-preview-confirm").evaluate((button) => {
    button.click();
    button.click();
  });
  const recalcRequest = await recalcResponse.started;
  assert.equal(recalcRequest.postDataJSON().previewToken, "preview-new");
  assert.equal(
    fixtureCounters.scoreRoutes.recalc - recalcWritesBefore,
    1,
  );
  assert.equal(
    await page.locator("#score-preview-confirm").isDisabled(),
    true,
  );
  recalcResponse.respond({ ok: true, updated: 3, ruleVersion: 1 });
  await page.waitForFunction(() =>
    document.querySelector("#score-board")?.classList.contains("hud-event-success") &&
    document.querySelector("#score-board")?.classList.contains("hud-event-score-recalculated"),
  );
  assert.deepEqual(
    await page.evaluate(() => window.__scoreRecalculationEvents),
    [{
      type: "score:recalculated",
      target: "#score-board",
      domain: "analysis",
      severity: "success",
      value: 3,
      message: "已更新 3 名成员",
      timestamp: await page.evaluate(
        () => window.__scoreRecalculationEvents[0].timestamp,
      ),
      dedupeKey: "score:1:3",
    }],
  );
  await page.waitForFunction(() =>
    [...document.querySelectorAll("#score-board-body .analysis-row")]
      .some((row) => row.dataset.delta === "up"),
  );
  await page.evaluate(() => {
    window.__scoreDeltaStates = [
      ...document.querySelectorAll("#score-board-body .analysis-row"),
    ].map((row) => row.dataset.delta || "same");
  });
  await page.waitForFunction(() => !document.querySelector("#score-preview-dialog")?.open);
  assert.equal(
    await page.locator("#score-preview-confirm").isDisabled(),
    false,
  );
  assert.equal(
    await page.locator("#score-preview-confirm").getAttribute("aria-busy"),
    null,
  );
  assert.equal(
    await page.locator("#score-preview-surface").getAttribute("aria-busy"),
    null,
  );
  await page.waitForFunction(() =>
    !document.querySelector("#score-board")?.classList.contains("hud-event-success") &&
    !document.querySelector("#score-board")?.classList.contains("hud-event-score-recalculated"),
  );

  await page.getByRole("button", { name: "武将阵容", exact: true }).click();
  await page.waitForSelector("#tab23.active");
  assert.equal(
    await page.locator("body").getAttribute("data-visual-domain"),
    "analysis",
  );
  await page.waitForFunction(() =>
    document.querySelectorAll("#combo-top-lineups .analysis-lineup-card").length === 3,
  );
  assert.equal(await page.locator("#combo-top-lineups .analysis-rank").count(), 3);
  assert.equal(
    await page.locator(
      '#combo-top-lineups .analysis-lineup-card[data-rank-tier="top"]',
    ).count(),
    3,
  );
  assert.equal(/[🥇🥈🥉]/u.test(await page.locator("#tab23").textContent()), false);

  const tabIds = await page.evaluate(() =>
    [...document.querySelectorAll(".page[id^='tab']")]
      .map((element) => Number(element.id.replace("tab", "")))
      .sort((a, b) => a - b),
  );
  assert.equal(tabIds.length, 35);
  for (const tabId of tabIds) {
    await page.evaluate((id) => {
      const button = [...document.querySelectorAll("nav button")].find((candidate) =>
        String(candidate.getAttribute("onclick") || "").includes(`switchTab(${id},`),
      );
      window.switchTab(id, button);
    }, tabId);
    await page.waitForTimeout(35);
    if (tabId === 30) {
      assert.equal(
        await page.locator("#tab33").evaluate((element) => element.classList.contains("active")),
        true,
        "legacy tab 30 did not redirect to tab 33",
      );
    } else {
      assert.equal(
        await page.locator(`#tab${tabId}`).evaluate((element) => element.classList.contains("active")),
        true,
        `tab ${tabId} did not activate`,
      );
    }
  }

  await page.evaluate(() => {
    const button = [...document.querySelectorAll("nav button")].find((candidate) =>
      String(candidate.getAttribute("onclick") || "").includes("switchTab(33,"),
    );
    window.switchTab(33, button);
  });
  await page.waitForSelector("#tab33.active");
  await page.locator("[data-intel-view='march']").click();
  await page.waitForSelector("#intel-view-march:not([hidden])");
  await page.waitForFunction(() => document.querySelector("#ws-march-body")?.textContent.includes("9001"));
  await page.locator("#ws-march-body .ws-wid-link").nth(1).click();
  await page.waitForSelector("#intel-view-map:not([hidden])");
  await page.waitForFunction(() => document.querySelector("#intel-detail-title")?.textContent.includes("10004"));

  await page.locator("[data-intel-view='army']").click();
  await page.waitForSelector("#intel-view-army:not([hidden])");
  await page.waitForFunction(() => document.querySelector("#ws-army-body")?.textContent.includes("主公"));
  await page.locator("#ws-army-body .ws-locatable-row").click();
  await page.waitForSelector("#intel-view-map:not([hidden])");

  await page.locator("[data-intel-view='entity']").click();
  await page.waitForSelector("#intel-view-entity:not([hidden])");
  await page.waitForFunction(() => document.querySelector("#ws-entity-body")?.textContent.includes("war_ship"));

  await page.evaluate(() => window.switchTab(30, null));
  await page.waitForSelector("#tab33.active");
  assert.equal(await page.locator("#tab30").evaluate((element) => element.classList.contains("active")), false);

  await page.evaluate(() => {
    const button = [...document.querySelectorAll("nav button")].find((candidate) =>
      String(candidate.getAttribute("onclick") || "").includes("switchTab(33,"),
    );
    window.switchTab(33, button);
  });
  await page.waitForSelector("#tab33.active");
  await page.waitForFunction(() => document.querySelector("#intel-state-meta")?.textContent.includes("v7"));
  await page.evaluate(() => window.IntelligenceCenter.selectWid(10004));
  await page.waitForFunction(() => document.querySelector("#intel-detail-title")?.textContent.includes("10004"));
  assert.equal((await page.locator("#intel-detail-body").textContent()).includes("estimatedTroops"), true);
  await page.locator(".intel-detail-tabs [data-intel-tab='battles']").click();
  const maliciousLineup = page.locator("#intel-detail-body .intel-lineup-link");
  assert.equal(await maliciousLineup.isVisible(), true);
  assert.equal(await maliciousLineup.getAttribute("onclick"), null);
  assert.equal(await maliciousLineup.getAttribute("onmouseover"), null);
  assert.equal(
    await maliciousLineup.evaluate((button) =>
      [...button.attributes].every((attribute) => !attribute.name.startsWith("on"))
    ),
    true,
  );
  assert.match(await maliciousLineup.textContent(), /payload/);
  await page.evaluate(() => {
    window.__intelLineupKey = "";
    window.__originalIntelOpenLineup = window.ResearchCenter.openLineup;
    window.ResearchCenter.openLineup = (key) => {
      window.__intelLineupKey = key;
    };
  });
  await maliciousLineup.click();
  assert.equal(
    await page.evaluate(() => window.__intelLineupKey),
    maliciousDomPayload,
  );
  await page.evaluate(() => {
    window.ResearchCenter.openLineup = window.__originalIntelOpenLineup;
    delete window.__originalIntelOpenLineup;
  });
  assert.equal(await page.evaluate(() => window.__unsafePayload), 0);

  await page.evaluate(() => {
    const button = [...document.querySelectorAll("nav button")].find((candidate) =>
      String(candidate.getAttribute("onclick") || "").includes("switchTab(34,"),
    );
    window.switchTab(34, button);
  });
  await page.waitForSelector("#tab34.active");
  assert.equal(
    await page.locator("body").getAttribute("data-visual-domain"),
    "analysis",
  );
  assert.equal(await page.locator("#tab34 > .hud-page-head").isVisible(), true);
  assert.equal(await page.locator(".research-workbench-shell").isVisible(), true);
  assert.equal(await page.locator("#research-mode-tabs [role='tab']").count(), 3);
  await page.locator("[data-evidence='config']").click();

  const firstResearchDetail =
    fixtureControls.queueLifecycleResponse("researchHero:100013");
  const firstResearchDetailPromise = page.evaluate(() =>
    window.ResearchCenter.openHero(100013)
  );
  await firstResearchDetail.started;
  assert.equal(
    await page.locator("#research-detail-status .hud-state-loading").isVisible(),
    true,
  );
  assert.equal(
    await page.locator("#research-evidence-panel").getAttribute("aria-busy"),
    "true",
  );
  firstResearchDetail.respond(researchHeroDetail(100013));
  await firstResearchDetailPromise;
  assert.match(
    await page.locator("#research-evidence-body").textContent(),
    /马超/,
  );
  assert.equal(
    await page.locator("#research-evidence-panel").getAttribute("aria-busy"),
    null,
  );

  const staleResearchDetail =
    fixtureControls.queueLifecycleResponse("researchHero:100649");
  const currentResearchDetail =
    fixtureControls.queueLifecycleResponse("researchHero:100023");
  const staleResearchDetailPromise = page.evaluate(() =>
    window.ResearchCenter.openHero(100649).catch((error) => error.message)
  );
  await staleResearchDetail.started;
  const staleResearchDetailState = await page.evaluate(() => ({
    refreshing: Boolean(
      document.querySelector(
        "#research-detail-status .hud-state-refreshing",
      ),
    ),
    statusHtml: document.querySelector("#research-detail-status")?.innerHTML || "",
    selectedLibraryId: window.ResearchCenter.state.selectedLibraryId,
    hasSelectedLibraryItem: Boolean(
      window.ResearchCenter.state.selectedLibraryItem,
    ),
    content: document.querySelector("#research-evidence-body")?.textContent || "",
  }));
  const currentResearchDetailPromise = page.evaluate(() =>
    window.ResearchCenter.openHero(100023)
  );
  await currentResearchDetail.started;
  currentResearchDetail.respond(researchHeroDetail(100023));
  await currentResearchDetailPromise;
  staleResearchDetail.respond({
    ok: false,
    error: "过期武将详情错误",
  });
  assert.equal(
    await staleResearchDetailPromise,
    "过期武将详情错误",
  );
  assert.equal(
    staleResearchDetailState.refreshing,
    true,
    JSON.stringify(staleResearchDetailState),
  );
  assert.match(staleResearchDetailState.content, /马超/);
  assert.match(
    await page.locator("#research-evidence-body").textContent(),
    /曹操/,
  );
  assert.doesNotMatch(
    await page.locator("#research-detail-status").textContent(),
    /过期武将详情错误/,
  );
  assert.equal(
    await page.locator("#research-evidence-panel").getAttribute("aria-busy"),
    null,
  );

  const failedResearchDetail =
    fixtureControls.queueLifecycleResponse("researchHero:100013");
  const failedResearchDetailPromise = page.evaluate(() =>
    window.ResearchCenter.openHero(100013).catch((error) => error.message)
  );
  await failedResearchDetail.started;
  failedResearchDetail.respond({
    ok: false,
    error: "武将详情暂不可用",
  });
  assert.equal(
    await failedResearchDetailPromise,
    "武将详情暂不可用",
  );
  assert.equal(
    await page.locator("#research-detail-status .hud-state-error").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#research-detail-status").textContent(),
    /武将详情暂不可用.*重试/s,
  );
  assert.match(
    await page.locator("#research-evidence-body").textContent(),
    /曹操/,
  );
  const retriedResearchDetail =
    fixtureControls.queueLifecycleResponse("researchHero:100013");
  await page.locator("#research-detail-status .hud-state-action").click();
  await retriedResearchDetail.started;
  retriedResearchDetail.respond(researchHeroDetail(100013, {
    name: "重试马超",
  }));
  await page.waitForFunction(() =>
    document.querySelector("#research-evidence-body")
      ?.textContent.includes("重试马超")
      && !document.querySelector("#research-evidence-panel")
        ?.hasAttribute("aria-busy"),
  );

  const firstResearchLineup = fixtureControls.queueLifecycleResponse(
    "researchLineup:100027.100016.100090",
  );
  const firstResearchLineupPromise = page.evaluate(() =>
    window.ResearchCenter.openLineup("100027.100016.100090")
  );
  await firstResearchLineup.started;
  assert.equal(
    await page.locator("#research-lineup-status .hud-state-loading").isVisible(),
    true,
  );
  assert.equal(
    await page.locator("#research-stage").getAttribute("aria-busy"),
    "true",
  );
  firstResearchLineup.respond(lineupDetail);
  await firstResearchLineupPromise;
  await page.waitForFunction(() =>
    window.ResearchCenter.state.lineup.heroes.every((hero) => hero.id > 0)
  );
  assert.match(
    await page.locator("#research-stage").textContent(),
    /100027\.100016\.100090.*18 场.*胜率 61\.1%/s,
  );
  assert.equal(
    await page.locator("#research-stage").getAttribute("aria-busy"),
    null,
  );

  const alternateLineupDetail = {
    ...lineupDetail,
    key: "100013.100649.100023",
    battleStats: {
      ...lineupDetail.battleStats,
      sampleSize: 12,
      winRate: 52.5,
    },
    simulationLink: {
      ...lineupDetail.simulationLink,
      lineup: {
        morale: 100,
        heroes: [
          { id: 100013, level: 40, up: 5, equip_skills: [] },
          { id: 100649, level: 40, up: 5, equip_skills: [] },
          { id: 100023, level: 40, up: 5, equip_skills: [] },
        ],
      },
    },
  };
  const staleResearchLineup = fixtureControls.queueLifecycleResponse(
    "researchLineup:100027.100016.100090",
  );
  const currentResearchLineup = fixtureControls.queueLifecycleResponse(
    "researchLineup:100013.100649.100023",
  );
  const staleResearchLineupPromise = page.evaluate(() =>
    window.ResearchCenter.openLineup("100027.100016.100090")
      .catch((error) => error.message)
  );
  await staleResearchLineup.started;
  assert.equal(
    await page.locator("#research-lineup-status .hud-state-refreshing").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#research-stage").textContent(),
    /100027\.100016\.100090.*18 场/s,
  );
  const currentResearchLineupPromise = page.evaluate(() =>
    window.ResearchCenter.openLineup("100013.100649.100023")
  );
  await currentResearchLineup.started;
  currentResearchLineup.respond(alternateLineupDetail);
  await currentResearchLineupPromise;
  staleResearchLineup.respond({
    ok: false,
    error: "过期阵容加载错误",
  });
  assert.equal(
    await staleResearchLineupPromise,
    "过期阵容加载错误",
  );
  assert.equal(
    await page.evaluate(() =>
      window.ResearchCenter.state.activeHistoricalLineup.key
    ),
    "100013.100649.100023",
  );
  assert.match(
    await page.locator("#research-stage").textContent(),
    /100013\.100649\.100023.*12 场.*胜率 52\.5%/s,
  );
  assert.doesNotMatch(
    await page.locator("#research-lineup-status").textContent(),
    /过期阵容加载错误/,
  );

  const failedResearchLineup = fixtureControls.queueLifecycleResponse(
    "researchLineup:100027.100016.100090",
  );
  const failedResearchLineupPromise = page.evaluate(() =>
    window.ResearchCenter.openLineup("100027.100016.100090")
      .catch((error) => error.message)
  );
  await failedResearchLineup.started;
  failedResearchLineup.respond({
    ok: false,
    error: "历史阵容暂不可用",
  });
  assert.equal(
    await failedResearchLineupPromise,
    "历史阵容暂不可用",
  );
  assert.equal(
    await page.locator("#research-lineup-status .hud-state-error").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#research-stage").textContent(),
    /100013\.100649\.100023.*12 场/s,
  );
  const retriedResearchLineup = fixtureControls.queueLifecycleResponse(
    "researchLineup:100027.100016.100090",
  );
  await page.locator("#research-lineup-status .hud-state-action").click();
  await retriedResearchLineup.started;
  retriedResearchLineup.respond(lineupDetail);
  await page.waitForFunction(() =>
    window.ResearchCenter.state.activeHistoricalLineup?.key
      === "100027.100016.100090"
      && !document.querySelector("#research-stage")?.hasAttribute("aria-busy"),
  );

  await page.locator("[data-skill-position='0'][data-skill-slot='0']").click();
  await page.locator("#research-search").fill("衣带密诏");
  try {
    await page.waitForFunction(
      () => document.querySelector("#research-results")
        ?.textContent.includes("衣带密诏"),
      null,
      { timeout: 5_000 },
    );
  } catch (error) {
    const diagnostic = await page.evaluate(() => ({
      query: window.ResearchCenter.state.query,
      libraryKind: window.ResearchCenter.state.libraryKind,
      loading: window.ResearchCenter.state.loading,
      error: window.ResearchCenter.state.error,
      revision: window.ResearchCenter.state.requestRevision,
      rows: window.ResearchCenter.state.libraryRows,
      text: document.querySelector("#research-results")?.textContent,
      html: document.querySelector("#research-results")?.innerHTML,
    }));
    throw new Error(`research search did not render: ${JSON.stringify(diagnostic)}`, {
      cause: error,
    });
  }
  await page.locator("#research-results [data-research-id='200001']").click();
  await page.waitForFunction(() =>
    window.ResearchCenter.state.lineup.heroes[0].equip_skills[0] === 200001
  );
  assert.equal(
    await page.evaluate(() =>
      window.ResearchCenter.state.lineup.heroes[0].equip_skills[0]
    ),
    200001,
  );
  await page.getByRole("button", { name: "交换 1 / 2" }).click();
  assert.deepEqual(
    await page.evaluate(() => ({
      ids: window.ResearchCenter.state.lineup.heroes.map((hero) => hero.id),
      skills: window.ResearchCenter.state.lineup.heroes[1].equip_skills,
    })),
    { ids: [100016, 100027, 100090], skills: [200001, 0] },
  );
  await page.getByRole("button", { name: "交换 1 / 2" }).click();

  await page.getByRole("button", { name: "阵容模板" }).click();
  await page.waitForSelector("#research-template-dialog[open]");
  await page.locator("#research-template-name").fill("E2E 魏骑");
  await page.getByRole("button", { name: "保存当前阵容" }).click();
  await page.waitForTimeout(100);
  const researchTemplateState = await page.evaluate(() => ({
    name: document.querySelector("[data-template-name]")?.value || "",
    stored: localStorage.getItem("stzb.research.lineup-templates.v1"),
    error: window.ResearchCenter.state.error,
    open: document.querySelector("#research-template-dialog")?.open,
  }));
  assert.equal(
    researchTemplateState.name,
    "E2E 魏骑",
    JSON.stringify(researchTemplateState),
  );
  assert.equal(
    await page.evaluate(() =>
      JSON.parse(localStorage.getItem("stzb.research.lineup-templates.v1"))
        .templates[0].lineup.heroes[0].equip_skills[0]
    ),
    200001,
  );
  await page.evaluate(() => {
    window.ResearchWorkbench.setLineup({
      heroes: [{ id: 100013 }, { id: 100649 }, { id: 100023 }],
    });
  });
  await page.locator("[data-template-action='load']").click();
  await page.waitForFunction(() =>
    window.ResearchCenter.state.lineup.heroes[0].equip_skills[0] === 200001
  );
  assert.deepEqual(
    await page.evaluate(() =>
      window.ResearchCenter.state.lineup.heroes.map((hero) => hero.id)
    ),
    [100027, 100016, 100090],
  );

  await page.evaluate(() => {
    window.ResearchWorkbench.setOpponent({
      heroes: [{ id: 100013 }, { id: 100649 }, { id: 100023 }],
    });
  });
  const firstResearchMatchup =
    fixtureControls.queueLifecycleResponse("researchMatchup");
  await page.locator("[data-research-mode='matchup']").click();
  await firstResearchMatchup.started;
  assert.equal(
    await page.locator("#research-matchup-status .hud-state-loading").isVisible(),
    true,
  );
  assert.equal(
    await page.locator("#research-stage").getAttribute("aria-busy"),
    "true",
  );
  firstResearchMatchup.respond(researchMatchup(7, 64.3, "first"));
  await page.waitForFunction(() =>
    window.ResearchCenter.state.matchup?.battleStats?.sampleSize === 7
  );
  assert.match(
    await page.locator("#research-stage").textContent(),
    /100027\.100016\.100090.*100013\.100649\.100023.*证据不足.*7 场.*64\.3%/s,
  );
  assert.equal(
    (await page.locator("#research-stage").textContent()).includes("综合分数"),
    false,
  );
  assert.equal(
    await page.locator("#research-stage").getAttribute("aria-busy"),
    null,
  );

  const staleResearchMatchup =
    fixtureControls.queueLifecycleResponse("researchMatchup");
  const currentResearchMatchup =
    fixtureControls.queueLifecycleResponse("researchMatchup");
  const staleResearchMatchupPromise = page.evaluate(() =>
    window.ResearchWorkbench.refreshMatchup()
      .catch((error) => error.message)
  );
  await staleResearchMatchup.started;
  assert.equal(
    await page.locator("#research-matchup-status .hud-state-refreshing").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#research-stage").textContent(),
    /7 场.*64\.3%/s,
  );
  const currentResearchMatchupPromise = page.evaluate(() =>
    window.ResearchWorkbench.refreshMatchup()
  );
  await currentResearchMatchup.started;
  currentResearchMatchup.respond(researchMatchup(19, 73.7, "current"));
  await currentResearchMatchupPromise;
  staleResearchMatchup.respond({
    ok: false,
    error: "过期对阵统计错误",
  });
  assert.equal(
    await staleResearchMatchupPromise,
    "过期对阵统计错误",
  );
  assert.deepEqual(
    await page.evaluate(() => ({
      marker: window.ResearchCenter.state.matchup.marker,
      sampleSize: window.ResearchCenter.state.matchup.battleStats.sampleSize,
    })),
    { marker: "current", sampleSize: 19 },
  );
  assert.match(
    await page.locator("#research-stage").textContent(),
    /19 场.*73\.7%/s,
  );
  assert.doesNotMatch(
    await page.locator("#research-matchup-status").textContent(),
    /过期对阵统计错误/,
  );
  assert.equal(
    await page.locator("#research-stage").getAttribute("aria-busy"),
    null,
  );

  const failedResearchMatchup =
    fixtureControls.queueLifecycleResponse("researchMatchup");
  const failedResearchMatchupPromise = page.evaluate(() =>
    window.ResearchWorkbench.refreshMatchup()
      .catch((error) => error.message)
  );
  await failedResearchMatchup.started;
  failedResearchMatchup.respond({
    ok: false,
    error: "对阵统计暂不可用",
  });
  assert.equal(
    await failedResearchMatchupPromise,
    "对阵统计暂不可用",
  );
  assert.equal(
    await page.locator("#research-matchup-status .hud-state-error").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#research-stage").textContent(),
    /19 场.*73\.7%/s,
  );
  const retriedResearchMatchup =
    fixtureControls.queueLifecycleResponse("researchMatchup");
  await page.locator("#research-matchup-status .hud-state-action").click();
  await retriedResearchMatchup.started;
  retriedResearchMatchup.respond(researchMatchup(21, 76.2, "retry"));
  await page.waitForFunction(() =>
    window.ResearchCenter.state.matchup?.marker === "retry"
      && !document.querySelector("#research-stage")?.hasAttribute("aria-busy"),
  );

  const firstChainHeroes = [100027, 100016, 100090].map((id) => (
    fixtureControls.queueLifecycleResponse(`researchHero:${id}`)
  ));
  const firstChainSkills = [200027, 200016, 200090].map((id) => (
    fixtureControls.queueLifecycleResponse(`researchSkill:${id}`)
  ));
  await page.locator("[data-research-mode='chain']").click();
  await Promise.all(firstChainHeroes.map((fixture) => fixture.started));
  assert.equal(
    await page.locator("#research-chain-status .hud-state-loading").isVisible(),
    true,
  );
  assert.equal(
    await page.locator("#research-stage").getAttribute("aria-busy"),
    "true",
  );
  [100027, 100016, 100090].forEach((id, index) => {
    firstChainHeroes[index].respond(researchHeroDetail(id, {
      skillId: id + 100_000,
    }));
  });
  await Promise.all(firstChainSkills.map((fixture) => fixture.started));
  firstChainSkills.forEach((fixture, index) => {
    const skillId = [200027, 200016, 200090][index];
    fixture.respond(researchSkillDetail(skillId));
  });
  await page.waitForFunction(() =>
    document.querySelectorAll("#research-stage [data-chain-node-id]").length > 0
  );
  assert.match(
    await page.locator("#research-stage").textContent(),
    /CONFIG CHAIN.*其疾如风.*衣带密诏/s,
  );
  assert.equal(
    await page.locator("#research-stage").getAttribute("aria-busy"),
    null,
  );

  await page.evaluate(() => {
    window.ResearchCenter.state.skillDetails.delete(200001);
  });
  const failedResearchChain =
    fixtureControls.queueLifecycleResponse("researchSkill:200001");
  const failedResearchChainPromise = page.evaluate(() =>
    window.ResearchWorkbench.refreshChain()
      .catch((error) => error.message)
  );
  await failedResearchChain.started;
  assert.equal(
    await page.locator("#research-chain-status .hud-state-refreshing").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#research-stage").textContent(),
    /CONFIG CHAIN.*衣带密诏/s,
  );
  failedResearchChain.respond({
    ok: false,
    error: "执行链战法暂不可用",
  });
  assert.equal(
    await failedResearchChainPromise,
    "执行链战法暂不可用",
  );
  assert.equal(
    await page.locator("#research-chain-status .hud-state-error").isVisible(),
    true,
  );
  assert.match(
    await page.locator("#research-stage").textContent(),
    /CONFIG CHAIN.*衣带密诏/s,
  );
  assert.equal(
    await page.locator("#research-stage").getAttribute("aria-busy"),
    null,
  );
  const retriedResearchChain =
    fixtureControls.queueLifecycleResponse("researchSkill:200001");
  await page.locator("#research-chain-status .hud-state-action").click();
  await retriedResearchChain.started;
  retriedResearchChain.respond(researchSkillDetail(200001, "重试衣带密诏"));
  await page.waitForFunction(() =>
    document.querySelector("#research-stage")?.textContent.includes("重试衣带密诏")
      && !document.querySelector("#research-stage")?.hasAttribute("aria-busy"),
  );

  const originalResearchLineup = await page.evaluate(() =>
    structuredClone(window.ResearchCenter.state.lineup)
  );
  const staleResearchChainHero =
    fixtureControls.queueLifecycleResponse("researchHero:100701");
  const currentResearchChainHero =
    fixtureControls.queueLifecycleResponse("researchHero:100702");
  const currentResearchChainSkill =
    fixtureControls.queueLifecycleResponse("researchSkill:200702");
  const staleResearchChainPromise = page.evaluate(() => {
    window.ResearchCenter.state.lineup = {
      morale: 100,
      heroes: [
        { id: 100701, level: 40, up: 5, equip_skills: [0, 0] },
        { id: 0, level: 40, up: 0, equip_skills: [0, 0] },
        { id: 0, level: 40, up: 0, equip_skills: [0, 0] },
      ],
    };
    return window.ResearchWorkbench.refreshChain()
      .catch((error) => error.message);
  });
  await staleResearchChainHero.started;
  const currentResearchChainPromise = page.evaluate(() => {
    window.ResearchCenter.state.lineup = {
      morale: 100,
      heroes: [
        { id: 100702, level: 40, up: 5, equip_skills: [0, 0] },
        { id: 0, level: 40, up: 0, equip_skills: [0, 0] },
        { id: 0, level: 40, up: 0, equip_skills: [0, 0] },
      ],
    };
    return window.ResearchWorkbench.refreshChain();
  });
  await currentResearchChainHero.started;
  currentResearchChainHero.respond(researchHeroDetail(100702, {
    name: "当前链武将",
    skillId: 200702,
    skillName: "当前链初始战法",
  }));
  await currentResearchChainSkill.started;
  currentResearchChainSkill.respond(
    researchSkillDetail(200702, "当前链初始战法"),
  );
  await currentResearchChainPromise;
  staleResearchChainHero.respond({
    ok: false,
    error: "过期执行链错误",
  });
  assert.equal(
    await staleResearchChainPromise,
    "过期执行链错误",
  );
  assert.equal(
    await page.evaluate(() => window.ResearchCenter.state.lineup.heroes[0].id),
    100702,
  );
  assert.match(
    await page.locator("#research-stage").textContent(),
    /当前链初始战法/,
  );
  assert.doesNotMatch(
    await page.locator("#research-chain-status").textContent(),
    /过期执行链错误/,
  );
  assert.equal(
    await page.locator("#research-stage").getAttribute("aria-busy"),
    null,
  );
  await page.evaluate((lineup) => {
    window.ResearchWorkbench.setLineup(lineup);
  }, originalResearchLineup);

  await page.locator("[data-research-mode='lab']").click();
  await page.getByRole("button", { name: "送入模拟器验证" }).click();
  await page.waitForSelector("#tab25.active");
  assert.equal(
    await page.locator("body").getAttribute("data-visual-domain"),
    "operations",
  );
  await page.waitForFunction(() => window.StzbSimulator?.getState().blue[0]?.id === 100027);
  assert.deepEqual(
    await page.evaluate(() => window.StzbSimulator.getState().blue.map((hero) => hero.id)),
    [100027, 100016, 100090],
  );
  assert.equal(
    await page.evaluate(() =>
      window.StzbSimulator.getState().blue[0].equip_skills[0]
    ),
    200001,
  );
  assert.deepEqual(
    await page.evaluate(() => window.StzbSimulator.getState().sourceContext),
    {
      source: "intelligence-research",
      lineupKey: "100027.100016.100090",
      camp: "blue",
      returnTab: 34,
    },
  );
  await page.waitForFunction(() =>
    document.querySelector("#sim-engine-badge")?.textContent.includes("93ee999937"),
  );
  await page.waitForFunction(() => {
    const images = [...document.querySelectorAll(
      "#sim-attacker-team [data-sim-portrait], #sim-defender-team [data-sim-portrait]",
    )];
    return images.length === 6 &&
      images.every((image) => image.complete && image.naturalWidth > 0);
  });
  assert.equal(
    await page.locator(
      "#sim-attacker-team [data-position='0'] [data-sim-portrait]",
    ).getAttribute("src"),
    "/static/hero-portraits/cards/100027.webp",
  );
  assert.equal(
    await page.locator(".sim-hero-scan").first().evaluate(
      (element) => getComputedStyle(element).display,
    ),
    "none",
  );
  await page.locator(
    "#sim-attacker-team [data-position='0'] [data-sim-action='open-library'][data-kind='hero']",
  ).click();
  await page.waitForSelector("#sim-library-dialog[open]");
  await page.locator("[data-sim-input='library-query']").fill("刘备");
  await page.getByRole("button", { name: /刘备/ }).click();
  await page.waitForFunction(() =>
    document.querySelector("#sim-attacker-team")?.textContent.includes("刘备"),
  );
  assert.equal(
    await page.evaluate(() => window.StzbSimulator.getState().sourceContext),
    null,
  );
  await page.waitForFunction(() =>
    document.querySelector(
      "#sim-attacker-team [data-position='0'] [data-sim-portrait]",
    )?.getAttribute("src").includes("100016.webp"),
  );
  await page.evaluate(() => {
    const image = document.querySelector(
      "#sim-attacker-team [data-position='0'] [data-sim-portrait]",
    );
    image.dataset.fallbackSrc = "/static/missing-fallback.jpg";
    image.src = "/static/missing-local.webp";
  });
  await page.waitForFunction(() =>
    document.querySelector(
      "#sim-attacker-team [data-position='0'] [data-sim-portrait]",
    )?.dataset.portraitStep === "placeholder",
  );
  assert.equal(
    await page.locator(
      "#sim-attacker-team [data-position='0'] [data-sim-portrait]",
    ).getAttribute("src"),
    "/static/hero-portraits/placeholder.svg",
  );
  await page.locator("[data-sim-action='set-repeat'][data-repeat='100']").click();
  await page.evaluate(() => {
    window.simulationCompletedCount = 0;
    window.simulationCompletionSources = [];
    window.addEventListener("stzb:simulation-completed", () => {
      window.simulationCompletedCount += 1;
    }, { once: true });
    window.addEventListener("stzb:simulation-completed", (event) => {
      window.simulationCompletionSources.push(
        structuredClone(event.detail?.sourceContext ?? null),
      );
    });
  });
  await page.locator("#sim-run-button").click();
  await page.waitForFunction(() =>
    document.querySelector("#sim-result-summary")?.classList.contains("hud-event-success") &&
    document.querySelector("#sim-result-summary")?.classList.contains(
      "hud-event-simulation-completed",
    ),
  );
  await page.waitForFunction(() =>
    document.querySelector("#sim-result-summary")?.textContent.includes("63%"),
  );
  await page.waitForFunction(() => window.simulationCompletedCount === 1);
  assert.deepEqual(
    await page.evaluate(() => window.simulationCompletionSources),
    [null],
  );
  assert.equal(
    await page.evaluate(() =>
      window.ResearchCenter.state.simulationByLineupKey.has(
        "100027.100016.100090",
      )
    ),
    false,
  );
  await page.waitForFunction(() =>
    !document.querySelector("#sim-result-summary")?.classList.contains("hud-event-success") &&
    !document.querySelector("#sim-result-summary")?.classList.contains(
      "hud-event-simulation-completed",
    ),
  );
  assert.equal(
    (await page.locator("#sim-result-summary").textContent()).includes("首场事件"),
    true,
  );
  await page.locator("[data-sim-action='set-repeat'][data-repeat='1000']").click();
  await page.locator("#sim-run-button").click();
  await page.waitForFunction(() =>
    document.querySelector("#sim-result-summary")?.classList.contains("hud-event-warning") &&
    document.querySelector("#sim-result-summary")?.classList.contains(
      "hud-event-simulation-completed",
    ),
  );
  assert.equal(
    await page.locator(
      "#hud-toast-region .hud-toast[data-severity='warning']",
    ).count(),
    1,
  );
  await page.waitForFunction(() =>
    !document.querySelector("#sim-result-summary")?.classList.contains("hud-event-warning") &&
    !document.querySelector("#sim-result-summary")?.classList.contains(
      "hud-event-simulation-completed",
    ),
  );
  await page.locator("[data-sim-action='set-result-view'][data-view='semantic']").click();
  await page.waitForSelector("#sim-replay-view:not([hidden])");
  assert.equal(
    (await page.locator("#sim-replay-phases").textContent()).includes("系统效果"),
    true,
  );
  assert.equal(
    (await page.locator("#sim-replay-phases").textContent()).includes("指挥战法"),
    true,
  );
  await page.locator("[data-sim-action='set-round'][data-round='1']").click();
  await page.locator("[data-sim-action='set-event-filter'][data-filter='control']").click();
  await page.locator("[data-sim-action='select-event'][data-event-seq='5']").click();
  const replayDetailText = await page.locator("#sim-replay-detail").textContent();
  assert.equal(replayDetailText.includes("EffectBlocked"), true);
  assert.equal(replayDetailText.includes("CORRELATED SERVER ACTIONS"), true);
  assert.equal(replayDetailText.includes("5u4,207"), true);
  await page.locator("[data-sim-action='set-result-view'][data-view='actions']").click();
  await page.waitForFunction(() =>
    document.querySelector("#sim-replay-stream")?.textContent.includes("ClientBattleTextReplayAdapter"),
  );
  assert.equal(
    (await page.locator("#sim-replay-stream").textContent()).includes("5u4,207"),
    true,
  );
  await page.locator("[data-sim-action='set-repeat'][data-repeat='100']").click();

  await page.evaluate(() => window.switchTab(34, null));
  await page.waitForSelector("#tab34.active");
  await page.locator("[data-evidence='simulation']").click();
  assert.equal(
    (await page.locator("#research-evidence-body").textContent()).includes("63%"),
    false,
  );
  assert.equal(
    await page.evaluate(() =>
      window.ResearchCenter.state.simulationByLineupKey.has(
        "100027.100016.100090",
      )
    ),
    false,
  );
  await page.locator("[data-research-mode='lab']").click();
  await page.getByRole("button", { name: "送入模拟器验证" }).click();
  await page.waitForSelector("#tab25.active");
  await page.waitForFunction(() =>
    window.StzbSimulator?.getState().sourceContext?.lineupKey
      === "100027.100016.100090"
  );
  await page.locator("#sim-run-button").click();
  await page.waitForFunction(() =>
    window.simulationCompletionSources.length === 3
  );
  assert.deepEqual(
    await page.evaluate(() => window.simulationCompletionSources),
    [
      null,
      null,
      {
        source: "intelligence-research",
        lineupKey: "100027.100016.100090",
        camp: "blue",
        returnTab: 34,
      },
    ],
  );
  await page.evaluate(() => window.switchTab(34, null));
  await page.waitForSelector("#tab34.active");
  await page.locator("[data-evidence='simulation']").click();
  await page.waitForFunction(() =>
    document.querySelector("#research-evidence-body")?.textContent.includes("63%")
  );
  assert.equal(
    await page.getByRole("button", { name: "返回研究工作台" }).isVisible(),
    true,
  );
  await page.locator("[data-research-mode='chain']").click();
  await page.waitForFunction(() =>
    document.querySelector("#research-stage")?.textContent.includes("SIMULATION CHAIN")
  );
  await page.locator("[data-chain-node-id='event:5']").click();
  assert.match(
    await page.locator("#research-evidence-body").textContent(),
    /EffectBlocked.*401/s,
  );

  await page.locator("[data-research-mode='lab']").click();
  await page.locator("#research-library-kind [data-kind='card-pack']").click();
  await page.locator("#research-search").fill("802");
  await page.waitForFunction(() =>
    document.querySelector("#research-results")?.textContent.includes("卡包 802"),
  );
  await page.locator("#research-results [data-card-pack-id='802']").click();
  await page.waitForFunction(() =>
    document.querySelector("#research-evidence-body")?.textContent.includes("卡包武将池"),
  );
  assert.equal(
    (await page.locator("#research-evidence-body").textContent()).includes("2 名武将"),
    true,
  );

  await page.evaluate(() => window.toggleQueryAgent(true));
  await page.locator("#query-agent-input").fill("执行恶意动作");
  await page.locator("#query-agent-panel .btn-primary").click();
  await page.waitForFunction(() =>
    document.querySelector("#query-agent-answer")?.textContent.includes("payload")
  );
  const maliciousAction = page.locator("#query-agent-answer button").first();
  assert.equal(await maliciousAction.getAttribute("onclick"), null);
  assert.equal(await maliciousAction.getAttribute("onmouseover"), null);
  assert.equal(
    await maliciousAction.evaluate((button) =>
      [...button.attributes].every((attribute) => !attribute.name.startsWith("on"))
    ),
    true,
  );
  await maliciousAction.click();
  await page.waitForFunction(() =>
    document.querySelector("#research-evidence-body")?.textContent.includes("卡包武将池")
  );
  assert.equal(await page.evaluate(() => window.__unsafePayload), 0);

  await page.locator("#query-agent-input").fill("查询卡包 802");
  await page.locator("#query-agent-panel .btn-primary").click();
  await page.waitForFunction(() => {
    const answer = document.querySelector("#query-agent-answer")?.textContent || "";
    return answer.includes("卡包 802");
  });
  await page.locator("#query-agent-answer button").click();
  await page.waitForFunction(() =>
    document.querySelector("#research-evidence-body")?.textContent.includes("卡包武将池"),
  );
  assert.equal(
    (await page.locator("#research-results").textContent()).includes("卡包 802"),
    true,
  );
  assert.equal(
    await page.locator("#research-library-kind [data-kind='card-pack']").evaluate(
      (element) => element.classList.contains("active"),
    ),
    true,
  );
  await page.evaluate(() => window.toggleQueryAgent(false));
  const visibleDomainByTab = {
    7: "organization",
    8: "analysis",
    16: "operations",
    17: "organization",
    23: "analysis",
    24: "organization",
    25: "operations",
    26: "intelligence",
    32: "system",
    33: "intelligence",
    34: "analysis",
    35: "intelligence",
  };
  const interactionControls = {
    8: ".score-toolbar .btn",
    16: ".hud-page-actions .btn:nth-child(2)",
    24: "#tr-btn-yesterday",
    32: "#cc-settings-reset",
    35: "#live-army-map-home",
  };
  const surfaceIssues = [];
  const eventIssues = [];
  const performanceIssues = [];
  const visualBudgetSamples = [];
  for (const tabIdText of Object.keys(visibleDomainByTab)) {
    const tabId = Number(tabIdText);
    await page.evaluate((id) => window.switchTab(id, null), tabId);
    await page.waitForSelector(`#tab${tabId}.active`);
    await assertSurfaceAndInteraction(page, tabId, "", surfaceIssues);
  }
  for (const [tabIdText, selector] of Object.entries(interactionControls)) {
    const tabId = Number(tabIdText);
    await page.evaluate((id) => window.switchTab(id, null), tabId);
    await page.waitForSelector(`#tab${tabId}.active`);
    await assertSurfaceAndInteraction(page, tabId, selector);
  }
  await assertPageStateLifecycle(page, fixtureControls);
  await assertLegacyLoaderLifecycle(
    page,
    fixtureControls,
    liveArmySnapshot,
  );
  await page.evaluate(() => {
    window.__rafAnimationStats.peak = window.__rafAnimationStats.pending;
    window.HudSystem?.resetAnimationStats?.();
  });
  const eventAnimationPeakPromise = sampleAnimationPeak(page, 3_000);
  let eventLifecycleError;
  try {
    await assertRealEventLifecycle(page, fixtureControls, eventIssues);
  } catch (error) {
    eventLifecycleError = error;
  }
  const eventAnimationPeak = await eventAnimationPeakPromise;
  if (eventLifecycleError) throw eventLifecycleError;
  assert.ok(
    eventAnimationPeak.peakAnimationCount <= 6,
    `event burst peak used ${eventAnimationPeak.peakAnimationCount} visible animations: ` +
      JSON.stringify(eventAnimationPeak.peakAnimations),
  );
  assert.ok(
    eventAnimationPeak.hud.peakValueAnimations <= 6,
    `event burst peak used ${eventAnimationPeak.hud.peakValueAnimations} HUD value animations`,
  );
  console.log(
    "animation peaks:",
    JSON.stringify({
      loading: loadingAnimationPeak,
      eventBurst: eventAnimationPeak,
    }),
  );

  for (const viewport of [
    { width: 375, height: 812 },
    { width: 768, height: 900 },
    { width: 1024, height: 900 },
    { width: 1440, height: 1000 },
    { width: 1920, height: 1080 },
  ]) {
    await assertResponsiveViewport(
      page,
      viewport,
      visibleDomainByTab,
      surfaceIssues,
      performanceIssues,
      visualBudgetSamples,
    );
  }
  assert.equal(
    visualBudgetSamples.length,
    Object.keys(visibleDomainByTab).length * 5,
    `visual budget sampled ${visualBudgetSamples.length} tab/viewports instead of 60`,
  );
  console.log(
    "task 9 visual budgets:\n" +
      visualBudgetSamples.map((sample) =>
        `${sample.viewport} tab ${sample.tabId}: ` +
          `blur=${sample.blurCount} ${JSON.stringify(sample.blur)}; ` +
          `animation=${sample.animationCount} ${JSON.stringify(sample.animation)}`,
      ).join("\n"),
  );
  await page.evaluate(() => window.switchTab(31, null));
  await page.evaluate(() => {
    window.dispatchEvent(new CustomEvent("stzb:stream-event", {
      detail: { type: "ping", data: {}, ts: "20:15:00" },
    }));
  });
  await page.waitForSelector("#cc-timeline-list .cc-timeline-item");
  assert.equal((await page.locator("#cc-timeline-list").textContent()).includes("NaN"), false);

  await page.setViewportSize({ width: 375, height: 812 });
  await page.waitForTimeout(100);
  for (const tabId of [7, 17, 24]) {
    await page.evaluate((id) => window.switchTab(id, null), tabId);
    await page.waitForSelector(`#tab${tabId}.active`);
    const organizationOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    assert.ok(
      organizationOverflow <= 1,
      `organization tab ${tabId} overflowed by ${organizationOverflow}px`,
    );
  }
  await page.evaluate(() => window.switchTab(25, null));
  await page.waitForSelector("#tab25.active");
  assert.equal(
    await page.locator(".sim-hero-grid").first().evaluate(
      (element) => element.scrollWidth > element.clientWidth,
    ),
    true,
  );
  assert.equal(await page.locator(".sim-hero-glass").first().isVisible(), true);
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  assert.ok(overflow <= 1, `mobile page overflowed by ${overflow}px`);

  const zoomContext = await browser.newContext({
    viewport: { width: 1024, height: 900 },
    deviceScaleFactor: 2,
  });
  const zoomPage = await zoomContext.newPage();
  await zoomPage.addInitScript(() => {
    class QuietEventSource {
      constructor() {
        queueMicrotask(() => this.onopen?.());
      }
      close() {}
    }
    window.EventSource = QuietEventSource;
  });
  await zoomPage.route(/^https?:\/\/(?!127\.0\.0\.1:8876)/, (route) => route.abort());
  await zoomPage.route("**/api/intelligence/live-armies?*", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(liveArmySnapshot()),
    }),
  );
  await zoomPage.goto(BASE, { waitUntil: "domcontentloaded" });
  await zoomPage.waitForSelector("#tab33.active");
  const zoomClient = await zoomContext.newCDPSession(zoomPage);
  const beforePageScale = await zoomPage.evaluate(() => ({
    scale: visualViewport.scale,
    width: visualViewport.width,
    layoutWidth: document.documentElement.clientWidth,
  }));
  await zoomClient.send("Emulation.setPageScaleFactor", {
    pageScaleFactor: 2,
  });
  await zoomPage.waitForFunction(() => visualViewport.scale >= 1.99);
  const afterPageScale = await zoomPage.evaluate(() => ({
    scale: visualViewport.scale,
    width: visualViewport.width,
    layoutWidth: document.documentElement.clientWidth,
    overflow:
      document.documentElement.scrollWidth -
      document.documentElement.clientWidth,
  }));
  assert.equal(beforePageScale.scale, 1);
  assert.equal(afterPageScale.scale, 2);
  assert.ok(
    afterPageScale.width < beforePageScale.width * 0.55,
    `200% page zoom did not change visual layout width: ${JSON.stringify({
      beforePageScale,
      afterPageScale,
    })}`,
  );
  assert.ok(afterPageScale.overflow <= 1);
  const zoomSettingsNavigation = zoomPage.locator("nav [data-tab-index='32']");
  assert.equal(await zoomSettingsNavigation.isVisible(), true);
  for (let step = 0; step < 240; step += 1) {
    if (await zoomSettingsNavigation.evaluate(
      (element) => document.activeElement === element,
    )) break;
    await zoomPage.keyboard.press("Tab");
  }
  assert.equal(
    await zoomSettingsNavigation.evaluate(
      (element) => document.activeElement === element,
    ),
    true,
    "200% zoom settings navigation was not keyboard reachable",
  );
  await zoomPage.keyboard.press("Enter");
  await zoomPage.waitForSelector("#tab32.active");
  const zoomPrimaryControl = zoomPage.locator("#cc-settings-reset");
  assert.equal(await zoomPrimaryControl.isVisible(), true);
  for (let step = 0; step < 240; step += 1) {
    if (await zoomPrimaryControl.evaluate(
      (element) => document.activeElement === element,
    )) break;
    await zoomPage.keyboard.press("Tab");
  }
  assert.equal(
    await zoomPrimaryControl.evaluate(
      (element) => document.activeElement === element,
    ),
    true,
    "200% zoom primary control was not keyboard reachable",
  );
  await zoomPage.keyboard.press("Enter");
  const zoomToast = zoomPage.locator(
    "#hud-toast-region .hud-toast[data-dedupe-key='setting:reset']",
  );
  await zoomToast.waitFor({ state: "visible" });
  assert.match(await zoomToast.textContent(), /本地偏好已重置/);
  await zoomPage.keyboard.press("Control+K");
  await zoomPage.waitForSelector("#cc-command-dialog[open]");
  assert.equal(await zoomPage.locator("#cc-command-input").isVisible(), true);
  await zoomPage.keyboard.press("Escape");
  assert.equal(await zoomPage.locator("#cc-command-dialog").getAttribute("open"), null);
  await zoomContext.close();

  const reducedContext = await browser.newContext({
    viewport: { width: 390, height: 844 },
    reducedMotion: "reduce",
  });
  const reducedPage = await reducedContext.newPage();
  let reducedOrganizationRequests = 0;
  await reducedPage.route(/^https?:\/\/(?!127\.0\.0\.1:8876)/, (route) => route.abort());
  await reducedPage.route("**/api/player_battle_teams?*", (route) => {
    reducedOrganizationRequests += 1;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(
        reducedOrganizationRequests === 1
          ? []
          : { error: "Reduced 状态仍可读" },
      ),
    });
  });
  await reducedPage.route("**/api/intelligence/live-armies?*", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(liveArmySnapshot()),
    }),
  );
  await reducedPage.route("**/api/intelligence/lineups?*", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        datasetVersion: "client-9.2.2",
        evidenceClass: "BATTLE_STAT",
        total: 1,
        page: 1,
        size: 8,
        rows: [lineupSummary],
      }),
    }),
  );
  await reducedPage.route(
    "**/api/intelligence/lineups/100027.100016.100090",
    (route) => route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(lineupDetail),
    }),
  );
  await reducedPage.route(/\/api\/intelligence\/heroes\/\d+$/, (route) => {
    const id = Number(route.request().url().split("/").pop());
    const hero = researchHeroes.find((row) => row.heroid === id);
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        hero,
        initialSkill: {
          skill_id: hero?.skill_init || 0,
          name: `初始战法 ${hero?.name || id}`,
        },
      }),
    });
  });
  await reducedPage.route(/\/api\/intelligence\/skills\/\d+$/, (route) => {
    const id = Number(route.request().url().split("/").pop());
    const skill = researchSkills.find((row) => row.skill_id === id) || {
      skill_id: id,
      name: `战法 ${id}`,
    };
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        ok: true,
        skill,
        details: [{
          detail_id: id + 1,
          effect_id: id + 2,
          effect_name: `${skill.name}效果`,
        }],
      }),
    });
  });
  await reducedPage.goto(BASE, { waitUntil: "domcontentloaded" });
  await reducedPage.waitForSelector("#tab33.active");
  assert.equal(
    await reducedPage.locator("body").getAttribute("data-motion-level"),
    "reduced",
  );
  const reducedValueResult = await reducedPage.evaluate(async () => {
    const value = document.createElement("output");
    value.id = "task-nine-reduced-value";
    document.body.append(value);
    const mutations = [];
    const observer = new MutationObserver(() => {
      mutations.push(value.textContent);
    });
    observer.observe(value, { childList: true, characterData: true, subtree: true });
    const promise = window.HudSystem.animateValue(
      value,
      0,
      9876,
      { duration: 1200, formatter: (current) => `VALUE:${Math.round(current)}` },
    );
    const immediateText = value.textContent;
    await promise;
    await new Promise((resolve) => setTimeout(resolve, 40));
    observer.disconnect();
    const result = {
      immediateText,
      finalText: value.textContent,
      mutations: [...new Set(mutations)],
    };
    value.remove();
    return result;
  });
  assert.deepEqual(reducedValueResult, {
    immediateText: "VALUE:9876",
    finalText: "VALUE:9876",
    mutations: ["VALUE:9876"],
  });

  await reducedPage.evaluate(() => window.switchTab(7, null));
  await reducedPage.waitForSelector("#tab7.active");
  await reducedPage.waitForFunction(() =>
    document.querySelector("#pbt-body")?.textContent.includes("暂无符合条件的玩家队伍"),
  );
  const reducedEmptyState = reducedPage.locator("#pbt-body .hud-state-empty");
  assert.equal(await reducedEmptyState.isVisible(), true);
  assert.match(await reducedEmptyState.textContent(), /暂无符合条件的玩家队伍/);
  const reducedStateColors = await reducedEmptyState.evaluate((element) => {
    const style = getComputedStyle(element);
    return {
      color: style.color,
      backgroundColor: style.backgroundColor,
      borderColor: style.borderColor,
    };
  });
  assert.equal(reducedStateColors.color === "rgba(0, 0, 0, 0)", false);
  await reducedPage.evaluate(() => window.loadPlayerBattleTeams());
  const reducedErrorState = reducedPage.locator(
    ".organization-table-panel .organization-status-host .hud-state-error",
  );
  assert.equal(await reducedErrorState.isVisible(), true);
  assert.match(await reducedErrorState.textContent(), /Reduced 状态仍可读/);
  const reducedErrorColor = await reducedErrorState.evaluate(
    (element) => getComputedStyle(element).color,
  );
  assert.equal(reducedErrorColor === "rgba(0, 0, 0, 0)", false);

  const reducedButton = reducedPage.locator("#tab7 .hud-page-actions button").first();
  await reducedButton.scrollIntoViewIfNeeded();
  const reducedButtonBox = await reducedButton.boundingBox();
  assert.ok(reducedButtonBox);
  await reducedPage.mouse.move(
    reducedButtonBox.x + reducedButtonBox.width / 2,
    reducedButtonBox.y + reducedButtonBox.height / 2,
  );
  await reducedPage.mouse.down();
  const reducedPressedTransform = await reducedButton.evaluate(
    (element) => getComputedStyle(element).transform,
  );
  await reducedPage.mouse.up();
  assert.equal(
    isIdentityTransform(reducedPressedTransform),
    true,
    `reduced-motion button press scaled: ${reducedPressedTransform}`,
  );
  await reducedButton.focus();
  const reducedFocusStyle = await reducedButton.evaluate((element) => {
    const style = getComputedStyle(element);
    return {
      outlineStyle: style.outlineStyle,
      outlineColor: style.outlineColor,
      boxShadow: style.boxShadow,
    };
  });
  assert.equal(
    reducedFocusStyle.outlineStyle !== "none" ||
      reducedFocusStyle.boxShadow !== "none",
    true,
    `reduced-motion focus indicator missing: ${JSON.stringify(reducedFocusStyle)}`,
  );
  assert.equal(
    reducedFocusStyle.outlineColor === "rgba(0, 0, 0, 0)",
    false,
  );
  await reducedPage.evaluate(() => window.HudSystem.toast({
    severity: "warning",
    title: "Reduced 状态",
    message: "即时结果与状态文字保持可见",
    dedupeKey: "task-nine-reduced-toast",
    duration: 0,
  }));
  const reducedToast = reducedPage.locator(
    "#hud-toast-region .hud-toast[data-dedupe-key='task-nine-reduced-toast']",
  );
  assert.equal(await reducedToast.isVisible(), true);
  assert.match(
    await reducedToast.textContent(),
    /Reduced 状态.*即时结果与状态文字保持可见/s,
  );
  const reducedToastStyle = await reducedToast.evaluate((element) => {
    const style = getComputedStyle(element);
    return {
      opacity: style.opacity,
      color: style.color,
      borderColor: style.borderColor,
    };
  });
  assert.equal(reducedToastStyle.opacity, "1");
  assert.equal(reducedToastStyle.color === "rgba(0, 0, 0, 0)", false);
  assert.equal(reducedToastStyle.borderColor === "rgba(0, 0, 0, 0)", false);

  await reducedPage.evaluate(() => window.switchTab(25, null));
  await reducedPage.waitForSelector("#tab25.active");
  await reducedPage.waitForSelector(".sim-hero-scan", { state: "attached" });
  assert.equal(
    await reducedPage.locator(".sim-hero-scan").first().evaluate(
      (element) => getComputedStyle(element).display,
    ),
    "none",
  );
  await reducedPage.evaluate(() => {
    window.dispatchEvent(new CustomEvent("stzb:hud-pulse", {
      detail: { selector: "#sim-workbench", kind: "success" },
    }));
  });
  assert.equal(
    await reducedPage.locator("#sim-workbench").evaluate(
      (element) => [...element.classList].some(
        (className) => className.startsWith("hud-pulse-"),
      ),
    ),
    false,
  );
  await reducedPage.locator(".sim-hero-card").first().hover();
  assert.ok(
    ["", "none"].includes(
      await reducedPage.locator(".sim-hero-card").first().evaluate(
        (element) => getComputedStyle(element).transform,
      ),
    ),
    "reduced-motion hero card applied a transform",
  );
  await reducedPage.evaluate(() => window.switchTab(35, null));
  await reducedPage.waitForSelector("#tab35.active");
  await reducedPage.waitForSelector("#live-army-current-list .live-army-card");
  await reducedPage.locator("#live-army-current-list .live-army-card").first().hover();
  assert.ok(
    ["", "none"].includes(
      await reducedPage.locator(".live-army-card").first().evaluate(
        (element) => getComputedStyle(element).transform,
      ),
    ),
    "reduced-motion live army card applied a transform",
  );
  assert.equal(
    await reducedPage.locator("#live-army-map-canvas").isVisible(),
    true,
  );
  await reducedPage.evaluate(() => window.switchTab(34, null));
  await reducedPage.waitForSelector("#tab34.active");
  await reducedPage.evaluate(() =>
    window.ResearchCenter.openLineup("100027.100016.100090")
  );
  await reducedPage.waitForFunction(() =>
    window.ResearchCenter.state.lineup.heroes.every((hero) => hero.id > 0)
  );
  await reducedPage.locator("[data-research-mode='chain']").click();
  await reducedPage.waitForFunction(() =>
    document.querySelectorAll("#research-stage [data-chain-node-id]").length > 0
  );
  const reducedChain = reducedPage.locator("#research-stage .research-skill-chain");
  assert.equal(await reducedChain.isVisible(), true);
  assert.match(
    await reducedPage.locator("#research-stage").textContent(),
    /CONFIG CHAIN.*其疾如风/s,
  );
  assert.equal(
    await reducedPage.locator("#research-stage [data-chain-node-id]").count() > 0,
    true,
  );
  await reducedContext.close();
  const rafQuiescence = await assertRafQuiescence(page);
  console.log("RAF quiescence:", JSON.stringify(rafQuiescence));

  const acceptanceIssues = [
    ...new Set([...surfaceIssues, ...eventIssues, ...performanceIssues]),
  ];

  const stalePage = await context.newPage();
  await stalePage.route(`${BASE}/api/command-center/overview`, (route) =>
    route.fulfill({
      status: 404,
      contentType: "text/html; charset=utf-8",
      body: "<!doctype html><title>404 Not Found</title>",
    }),
  );
  await stalePage.route(/^https?:\/\/(?!127\.0\.0\.1:8876)/, (route) => route.abort());
  await stalePage.goto(BASE, { waitUntil: "domcontentloaded" });
  await stalePage.waitForFunction(() =>
    (document.querySelector("#cc-battle-list")?.textContent || "").includes("后端接口不可用"),
  );
  const staleText = await stalePage.locator("#tab31").textContent();
  assert.equal(staleText.includes("Unexpected token"), false);
  assert.equal(staleText.includes("重新启动后端"), true);
  await stalePage.close();

  if (errors.length) acceptanceIssues.push(`page errors: ${errors.join("\n")}`);
  if (serverErrors.length) {
    acceptanceIssues.push(`server errors: ${serverErrors.join("\n")}`);
  }
  assert.deepEqual(acceptanceIssues, [], acceptanceIssues.join("\n"));

  console.log("dashboard e2e: 35 tabs, five HUD domains, responsive matrix, reduced motion OK");
} finally {
  if (browser) await browser.close();
  server.kill("SIGTERM");
}

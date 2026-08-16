import * as liveArmyMap from "./live-army-map.mjs";

const LIVE_ARMY_ENDPOINT =
  "/api/intelligence/live-armies?offlineMinutes=10";
const WORLD_EVENT_TYPES = new Set([
  "world_snapshot_complete",
  "world_scene_delta",
]);
const PORTRAIT_PLACEHOLDER = "/static/hero-portraits/placeholder.svg";
const STATE_COLORS = Object.freeze({
  normal: "#94a3b8",
  expedition: "#f05267",
  "reside-going": "#38bdf8",
  "reinforce-going": "#8b6cff",
  returning: "#f5b84b",
  reside: "#34d399",
  reinforce: "#54a6ff",
  stay: "#9aabca",
  unknown: "#c98555",
});

export function filterArmies(armies, query = "", stateFilter = "all") {
  const normalizedQuery = normalizeSearch(query);
  const normalizedFilter = String(stateFilter || "all");
  return (armies || []).filter((army) => {
    if (
      normalizedFilter !== "all"
      && String(army?.stateKey || "unknown") !== normalizedFilter
    ) {
      return false;
    }
    if (!normalizedQuery) return true;
    return normalizeSearch(searchTextForArmy(army)).includes(
      normalizedQuery,
    );
  });
}

export function filterArmiesByTime(
  armies,
  timeFilter = "10",
  nowMs = Date.now(),
) {
  const normalized = String(timeFilter || "10");
  if (normalized === "all") return [...(armies || [])];
  const minutes = Number(normalized);
  if (!Number.isFinite(minutes) || minutes < 0) return [];
  const maximumAgeMs = minutes * 60_000;
  return (armies || []).filter((army) => {
    const observedAtMs = armyObservedAtMs(army);
    if (observedAtMs <= 0) return false;
    const ageMs = Math.max(0, Number(nowMs) - observedAtMs);
    return ageMs <= maximumAgeMs;
  });
}

export function sortCurrentArmies(armies, nowSec = Date.now() / 1000) {
  const now = Number(nowSec) || 0;
  return [...(armies || [])].sort((left, right) => {
    const leftRank = arrivalRank(left, now);
    const rightRank = arrivalRank(right, now);
    if (leftRank.group !== rightRank.group) {
      return leftRank.group - rightRank.group;
    }
    if (leftRank.time !== rightRank.time) {
      return leftRank.time - rightRank.time;
    }
    if (leftRank.group === 2) {
      const stateDelta =
        Number(left?.state ?? Number.MAX_SAFE_INTEGER)
        - Number(right?.state ?? Number.MAX_SAFE_INTEGER);
      if (stateDelta) return stateDelta;
    }
    return Number(left?.armyId || 0) - Number(right?.armyId || 0);
  });
}

export function chooseDefaultArmy(snapshot, nowSec = Date.now() / 1000) {
  const current = sortCurrentArmies(snapshot?.current || [], nowSec);
  if (current.length) return Number(current[0].armyId) || 0;
  return Number(snapshot?.recentOffline?.[0]?.armyId) || 0;
}

export function formatArmyCountdown(endTime, nowSec = Date.now() / 1000) {
  const end = Number(endTime) || 0;
  const now = Number(nowSec) || 0;
  if (end <= 0) return "--:--";
  const remaining = Math.ceil(end - now);
  if (remaining <= 0) return "已到达";
  const hours = Math.floor(remaining / 3600);
  const minutes = Math.floor((remaining % 3600) / 60);
  const seconds = remaining % 60;
  const minuteText = String(minutes).padStart(2, "0");
  const secondText = String(seconds).padStart(2, "0");
  if (hours > 0) return `${hours}:${minuteText}:${secondText}`;
  return `${minuteText}:${secondText}`;
}

export function formatArmyAge(ageMs) {
  const age = Number(ageMs) || 0;
  if (age <= 0) return "时间未知";
  const totalSeconds = Math.max(0, Math.floor(age / 1000));
  if (totalSeconds < 60) return `${totalSeconds}秒前`;
  const totalMinutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (totalMinutes < 60) {
    return seconds
      ? `${totalMinutes}分${String(seconds).padStart(2, "0")}秒前`
      : `${totalMinutes}分钟前`;
  }
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return minutes ? `${hours}小时${minutes}分前` : `${hours}小时前`;
}

export function createLiveArmyCommand({
  documentRef = globalThis.document,
  windowRef = globalThis.window,
  fetchFn = globalThis.fetch?.bind(globalThis),
  setTimeoutFn = globalThis.setTimeout?.bind(globalThis),
  clearTimeoutFn = globalThis.clearTimeout?.bind(globalThis),
  setIntervalFn = globalThis.setInterval?.bind(globalThis),
  clearIntervalFn = globalThis.clearInterval?.bind(globalThis),
  nowFn = Date.now,
  mapModule = liveArmyMap,
  emitHudEvent = () => {},
  resolveHudEvent = () => false,
} = {}) {
  const initialTab = documentRef
    ?.getElementById?.("tab35")
    ?.classList?.contains?.("active")
    ? 35
    : 0;
  const state = {
    snapshot: null,
    selectedArmyId: 0,
    query: "",
    stateFilter: "all",
    timeFilter: "10",
    activeTab: initialTab,
    dirty: false,
    eventRevision: 0,
    loadedRevision: 0,
    loadRevision: 0,
    loadingOwner: 0,
    loading: false,
    initialized: false,
    lastError: "",
    nowMs: Number(nowFn?.()) || Date.now(),
    bounds: null,
    mapPlan: null,
    refreshTimer: null,
    ticker: null,
    resizeTimer: null,
    drag: null,
    lastEmittedRiskKey: "",
  };

  async function load(force = false) {
    bindOnce();
    if (state.loading) return state.loading;
    if (!fetchFn) {
      renderError("浏览器不支持数据请求");
      return null;
    }
    if (!state.snapshot) renderLoading();
    const loadOwner = state.loadRevision + 1;
    state.loadRevision = loadOwner;
    state.loadingOwner = loadOwner;
    beginLoadLifecycle();
    const requestRevision = state.eventRevision;
    const request = (async () => {
      try {
        const response = await fetchFn(LIVE_ARMY_ENDPOINT, {
          cache: "no-store",
        });
        const body = await response.json();
        if (!response.ok || body?.ok === false) {
          throw new Error(body?.error || `HTTP ${response.status || 500}`);
        }
        state.snapshot = body;
        state.nowMs = Math.max(
          Number(nowFn?.()) || Date.now(),
          Number(body.generatedAtMs) || 0,
        );
        const filteredSnapshot = visibleSnapshot();
        const selectedStillVisible = findArmy(
          filteredSnapshot,
          state.selectedArmyId,
        );
        state.selectedArmyId = selectedStillVisible
          ? state.selectedArmyId
          : chooseDefaultArmy(filteredSnapshot, state.nowMs / 1000);
        state.bounds = normalizedBounds(
          body.bounds
          || mapModule.boundsForArmies?.(body.current || [])
          || state.bounds,
        );
        state.loadedRevision = Math.max(
          state.loadedRevision,
          requestRevision,
        );
        state.dirty = state.eventRevision > state.loadedRevision;
        state.lastError = "";
        renderAll();
        syncTicker();
        return body;
      } catch (error) {
        state.lastError = error?.message || "实时部队加载失败";
        renderError(state.lastError, Boolean(state.snapshot));
        return null;
      } finally {
        if (state.loadingOwner === loadOwner) {
          state.loading = false;
          state.loadingOwner = 0;
          finishLoadLifecycle();
        }
        if (state.dirty && isVisible()) scheduleRefresh(0);
      }
    })();
    state.loading = request;
    return request;
  }

  function selectArmy(armyId, { scroll = true, center = true } = {}) {
    const id = Number(armyId) || 0;
    const army = findArmy(state.snapshot, id);
    if (!army) return null;
    state.selectedArmyId = id;
    if (center && mapModule.boundsForArmies) {
      state.bounds = normalizedBounds(
        mapModule.boundsForArmies([army], state.bounds),
      );
    }
    renderAll();
    emitSelectedRisk(army);
    if (scroll) {
      documentRef
        ?.querySelector?.(`[data-army-id='${id}']`)
        ?.scrollIntoView?.({ block: "nearest" });
    }
    return army;
  }

  function emitSelectedRisk(army) {
    const risk = army?.risk || {};
    const armyId = Number(army?.armyId) || 0;
    const wid = Number(army?.location?.currentWid) || 0;
    const riskKey = risk.level === "high" && wid
      ? `live-army-risk:${armyId}:${wid}:${risk.level}`
      : "";
    if (
      state.lastEmittedRiskKey
      && state.lastEmittedRiskKey !== riskKey
    ) {
      resolveHudEvent(state.lastEmittedRiskKey);
      state.lastEmittedRiskKey = "";
    }
    if (
      risk.level !== "high"
      || armyId !== Number(state.selectedArmyId)
      || !wid
    ) {
      return;
    }
    if (riskKey === state.lastEmittedRiskKey) return;
    state.lastEmittedRiskKey = riskKey;
    emitHudEvent({
      type: "intelligence:risk-detected",
      target: "#live-army-detail",
      domain: "intelligence",
      severity: "critical",
      message: `ARMY ${armyId} · WID ${wid} 风险 ${risk.score}`,
      timestamp: Number(nowFn?.()) || Date.now(),
      dedupeKey: riskKey,
      cooldownMs: 10_000,
    });
  }

  function locateArmy(armyId = state.selectedArmyId) {
    const army = findArmy(state.snapshot, armyId);
    if (!army) return null;
    if (mapModule.boundsForArmies) {
      state.bounds = normalizedBounds(
        mapModule.boundsForArmies([army], state.bounds),
      );
    }
    renderMap();
    return army;
  }

  async function openInIntelligence() {
    const army = findArmy(state.snapshot, state.selectedArmyId);
    const wid = Number(army?.location?.currentWid) || 0;
    if (!wid) return false;
    const button = [...(
      documentRef?.querySelectorAll?.("nav button") || []
    )].find((candidate) =>
      String(candidate.getAttribute?.("onclick") || "").includes(
        "switchTab(33,",
      )
    );
    const switchTab = windowRef?.switchTab || globalThis.switchTab;
    switchTab?.(33, button);
    await windowRef?.IntelligenceCenter?.openView?.("map");
    await windowRef?.IntelligenceCenter?.locateWid?.(wid);
    return true;
  }

  function setFilter(
    query = state.query,
    stateFilter = state.stateFilter,
    timeFilter = state.timeFilter,
  ) {
    if (query && typeof query === "object") {
      state.query = String(query.query ?? state.query);
      state.stateFilter = String(
        query.stateFilter ?? query.status ?? state.stateFilter,
      );
      state.timeFilter = String(
        query.timeFilter ?? query.time ?? state.timeFilter,
      );
    } else {
      state.query = String(query ?? "");
      state.stateFilter = String(stateFilter || "all");
      state.timeFilter = String(timeFilter || "10");
    }
    const search = element("live-army-search");
    const select = element("live-army-status-filter");
    const timeSelect = element("live-army-time-filter");
    if (search && search.value !== state.query) search.value = state.query;
    if (select && select.value !== state.stateFilter) {
      select.value = state.stateFilter;
    }
    if (timeSelect && timeSelect.value !== state.timeFilter) {
      timeSelect.value = state.timeFilter;
    }
    const filteredSnapshot = visibleSnapshot();
    if (!findArmy(filteredSnapshot, state.selectedArmyId)) {
      state.selectedArmyId = chooseDefaultArmy(
        filteredSnapshot,
        state.nowMs / 1000,
      );
    }
    renderAll();
    return visibleArmies();
  }

  function bindOnce() {
    if (state.initialized) return;
    state.initialized = true;
    if (
      documentRef
        ?.getElementById?.("tab35")
        ?.classList?.contains?.("active")
    ) {
      state.activeTab = 35;
    }
    element("live-army-search")?.addEventListener?.("input", (event) => {
      setFilter(event.target?.value || "", state.stateFilter);
    });
    element("live-army-status-filter")?.addEventListener?.(
      "change",
      (event) => {
        setFilter(
          state.query,
          event.target?.value || "all",
          state.timeFilter,
        );
      },
    );
    element("live-army-time-filter")?.addEventListener?.(
      "change",
      (event) => {
        setFilter(
          state.query,
          state.stateFilter,
          event.target?.value || "10",
        );
      },
    );
    element("live-army-map-home")?.addEventListener?.("click", () => {
      const current = state.snapshot?.current || [];
      state.bounds = normalizedBounds(
        state.snapshot?.bounds
        || mapModule.boundsForArmies?.(current, state.bounds),
      );
      renderMap();
    });
    element("live-army-map-selected")?.addEventListener?.(
      "click",
      () => locateArmy(),
    );
    element("live-army-open-intelligence")?.addEventListener?.(
      "click",
      openInIntelligence,
    );
    element("live-army-index-toggle")?.addEventListener?.("click", () => {
      const index = element("live-army-index");
      const toggle = element("live-army-index-toggle");
      const collapsed = index?.classList?.toggle?.("is-collapsed");
      toggle?.setAttribute?.("aria-expanded", String(!collapsed));
      if (toggle) toggle.textContent = collapsed ? "展开索引" : "收起索引";
    });
    const canvas = element("live-army-map-canvas");
    canvas?.addEventListener?.("click", (event) => {
      const point = canvasPoint(canvas, event);
      const armyId = mapModule.hitTestArmy?.(
        point.x,
        point.y,
        state.mapPlan,
      );
      if (armyId) selectArmy(armyId);
    });
    canvas?.addEventListener?.("dblclick", async (event) => {
      const point = canvasPoint(canvas, event);
      const armyId = mapModule.hitTestArmy?.(
        point.x,
        point.y,
        state.mapPlan,
      );
      if (armyId) selectArmy(armyId);
      await openInIntelligence();
    });
    canvas?.addEventListener?.("wheel", (event) => {
      if (!state.mapPlan?.bounds || !mapModule.zoomBounds) return;
      event.preventDefault?.();
      const point = canvasPoint(canvas, event);
      const anchor = worldPointForCanvas(point, state.mapPlan);
      state.bounds = mapModule.zoomBounds(
        state.mapPlan.bounds,
        anchor.row,
        anchor.col,
        Number(event.deltaY) < 0 ? -1 : 1,
      );
      renderMap();
    });
    canvas?.addEventListener?.("pointerdown", (event) => {
      state.drag = {
        x: Number(event.clientX) || 0,
        y: Number(event.clientY) || 0,
        bounds: state.bounds,
      };
      canvas.setPointerCapture?.(event.pointerId);
    });
    canvas?.addEventListener?.("pointermove", (event) => {
      if (!state.drag || !state.mapPlan?.mapRect || !mapModule.panBounds) {
        return;
      }
      const rect = state.mapPlan.mapRect;
      const bounds = normalizedBounds(state.drag.bounds);
      const rows = bounds.rowDown - bounds.rowUp + 1;
      const cols = bounds.colRight - bounds.colLeft + 1;
      const rowDelta = -(
        ((Number(event.clientY) || 0) - state.drag.y)
        / Math.max(1, rect.height)
      ) * rows;
      const colDelta = -(
        ((Number(event.clientX) || 0) - state.drag.x)
        / Math.max(1, rect.width)
      ) * cols;
      state.bounds = mapModule.panBounds(
        state.drag.bounds,
        rowDelta,
        colDelta,
      );
      renderMap();
    });
    const stopDrag = () => {
      state.drag = null;
    };
    canvas?.addEventListener?.("pointerup", stopDrag);
    canvas?.addEventListener?.("pointercancel", stopDrag);

    windowRef?.addEventListener?.("stzb:stream-event", onStreamEvent);
    windowRef?.addEventListener?.("stzb:tab-changed", onTabChanged);
    windowRef?.addEventListener?.("resize", () => {
      if (state.resizeTimer) clearTimeoutFn?.(state.resizeTimer);
      state.resizeTimer = setTimeoutFn?.(() => {
        state.resizeTimer = null;
        if (isVisible()) renderMap();
      }, 80);
    });
    documentRef?.addEventListener?.("visibilitychange", () => {
      if (isVisible() && state.dirty) scheduleRefresh(0);
      syncTicker();
    });
  }

  function onStreamEvent(event) {
    if (!WORLD_EVENT_TYPES.has(event?.detail?.type)) return;
    state.eventRevision += 1;
    state.dirty = true;
    if (isVisible()) scheduleRefresh(350);
  }

  function onTabChanged(event) {
    state.activeTab = Number(event?.detail?.tabId) || 0;
    if (isVisible() && state.dirty) scheduleRefresh(0);
    syncTicker();
  }

  function scheduleRefresh(delay) {
    if (state.refreshTimer || !setTimeoutFn) return;
    state.refreshTimer = setTimeoutFn(async () => {
      state.refreshTimer = null;
      if (!state.dirty || !isVisible()) return;
      await load(true);
    }, delay);
  }

  function syncTicker() {
    if (isVisible() && state.snapshot) {
      if (!state.ticker && setIntervalFn) {
        state.ticker = setIntervalFn(() => {
          state.nowMs = Math.max(
            Number(nowFn?.()) || Date.now(),
            state.nowMs + 1000,
          );
          updateCountdowns();
        }, 1000);
      }
      return;
    }
    if (state.ticker && clearIntervalFn) clearIntervalFn(state.ticker);
    state.ticker = null;
  }

  function isVisible() {
    return (
      state.activeTab === 35
      && documentRef?.visibilityState !== "hidden"
    );
  }

  function renderLoading() {
    replace(
      element("live-army-summary"),
      stateBox("loading", "正在聚合实时部队…"),
    );
  }

  function beginLoadLifecycle() {
    const summary = element("live-army-summary");
    summary?.classList?.add?.("hud-refresh-line");
    summary?.setAttribute?.("aria-busy", "true");
  }

  function finishLoadLifecycle() {
    const summary = element("live-army-summary");
    summary?.classList?.remove?.("hud-refresh-line");
    summary?.removeAttribute?.("aria-busy");
  }

  function renderError(message, retained) {
    const freshness = element("live-army-freshness");
    if (freshness) {
      freshness.textContent = retained ? "DEGRADED / RETAINED" : "LOAD ERROR";
      freshness.dataset.status = "degraded";
    }
    if (!retained) {
      replace(
        element("live-army-summary"),
        stateBox("error", `实时部队加载失败：${message}`),
      );
      replace(
        element("live-army-current-list"),
        stateBox("error", "无法读取部队数据，请重试"),
      );
      replace(
        element("live-army-detail"),
        stateBox("error", "当前没有可展示的部队证据"),
      );
    }
  }

  function renderAll() {
    if (!state.snapshot) return;
    renderFreshness();
    renderSummary();
    renderLists();
    renderMap();
    renderDetail();
    updateActionState();
  }

  function renderFreshness() {
    const freshness = element("live-army-freshness");
    const label = String(state.snapshot?.freshness || "unknown").toUpperCase();
    if (freshness) {
      freshness.textContent =
        `WORLDSTATE v${Number(state.snapshot?.worldStateVersion) || 0} / ${label}`;
      freshness.dataset.status =
        ["fresh", "live"].includes(String(state.snapshot?.freshness))
          ? "live"
          : "degraded";
    }
    const observedAtMs = Number(
      state.snapshot?.worldStateObservedAtMs,
    ) || 0;
    setText(
      "live-army-observed-at",
      observedAtMs
        ? `数据时间 ${formatDateTime(observedAtMs)} · ${formatArmyAge(
          Math.max(0, state.nowMs - observedAtMs),
        )}`
        : "数据时间未知",
    );
  }

  function renderSummary() {
    const visible = visibleArmies();
    const visibleCurrent = visible.current;
    const summary = state.snapshot?.summary || {};
    const visibleExact = visibleCurrent.filter(
      (army) => army?.lineup?.status === "exact",
    ).length;
    const models = [
      [timeFilterLabel(state.timeFilter), visibleCurrent.length, "#38bdf8"],
      [
        "正在移动",
        visibleCurrent.filter((army) => army.isMoving).length,
        "#f05267",
      ],
      [
        "过期待确认",
        Number(summary.staleCurrent)
          || (state.snapshot?.current || []).filter(
            (army) => army?.source?.isStale,
          ).length,
        "#f5b84b",
      ],
      ["精确阵容", visibleExact, "#8b6cff"],
      ["最近离线", visible.recentOffline.length, "#f5b84b"],
    ];
    const cards = models.map(([label, value, color]) => {
      const card = node("article", "hud-kpi");
      card.style?.setProperty?.("--hud-kpi-accent", color);
      card.append(
        textNode("span", "hud-kpi-label", label),
        textNode("strong", "hud-kpi-value", String(value)),
      );
      return card;
    });
    replace(element("live-army-summary"), ...cards);
  }

  function renderLists() {
    const { current, recentOffline } = visibleArmies();
    const currentList = element("live-army-current-list");
    const offlineList = element("live-army-offline-list");
    replace(
      currentList,
      ...(current.length
        ? current.map((army) => armyCard(army, false))
        : [stateBox("empty", "当前筛选下没有部队")]),
    );
    replace(
      offlineList,
      ...(recentOffline.length
        ? recentOffline.map((army) => armyCard(army, true))
        : [stateBox("empty", "最近 10 分钟无匹配离线部队")]),
    );
    setText("live-army-current-count", current.length);
    setText("live-army-offline-count", recentOffline.length);
    setText(
      "live-army-index-count",
      `${current.length + recentOffline.length}`,
    );
  }

  function armyCard(army, offline) {
    const button = node("button", "live-army-card");
    button.setAttribute?.("type", "button");
    button.setAttribute?.("data-army-id", Number(army.armyId) || 0);
    button.setAttribute?.(
      "data-freshness",
      normalizeFreshness(army?.source?.freshness, army?.source?.isStale),
    );
    button.setAttribute?.(
      "data-lineup-status",
      army?.lineup?.status === "exact" ? "exact" : "unknown",
    );
    button.setAttribute?.(
      "data-activity",
      offline ? "offline" : "current",
    );
    button.setAttribute?.(
      "aria-pressed",
      Number(army.armyId) === Number(state.selectedArmyId)
        ? "true"
        : "false",
    );
    button.style?.setProperty?.(
      "--army-color",
      STATE_COLORS[army.stateKey] || STATE_COLORS.unknown,
    );
    button.classList?.toggle?.(
      "is-selected",
      Number(army.armyId) === Number(state.selectedArmyId),
    );
    button.classList?.toggle?.("is-offline", offline);
    button.classList?.toggle?.(
      "is-stale",
      !offline && Boolean(army?.source?.isStale),
    );

    const heading = node("span", "live-army-card-head");
    heading.append(
      textNode(
        "span",
        "live-army-card-id",
        `ARMY ${Number(army.armyId) || 0}`,
      ),
      textNode(
        "span",
        "live-army-card-state",
        offline
          ? `最近离线 · ${army.stateLabel}`
          : army?.source?.isStale
            ? `过期待确认 · ${army.stateLabel}`
            : army.stateLabel,
      ),
    );
    const meta = node("span", "live-army-card-meta");
    meta.append(
      textNode(
        "span",
        "live-army-card-owner",
        `${armyOwnerName(army)} · ${armyUnionName(army)}`,
      ),
    );
    const observation = textNode(
      "span",
      "live-army-card-observed",
      `${offline ? "删除" : "观测"} ${formatDateTime(
        armyObservedAtMs(army),
      )} · ${formatArmyAge(armyAgeMs(army, state.nowMs))}`,
    );
    observation.setAttribute?.("data-live-army-age", "");
    observation.dataset.observedAtMs = String(armyObservedAtMs(army));
    observation.dataset.ageKind = offline ? "删除" : "观测";
    if (army.isMoving && !offline) {
      const countdown = textNode(
        "span",
        "live-army-card-countdown",
        formatArmyCountdown(army?.timing?.endTime, state.nowMs / 1000),
      );
      countdown.setAttribute?.("data-live-army-countdown", "");
      countdown.dataset.endTime = String(
        Number(army?.timing?.endTime) || 0,
      );
      meta.append(countdown);
    }
    const route = node("span", "live-army-card-route");
    route.append(
      textNode(
        "span",
        "",
        `${widLabel(army?.location?.currentWid)} →`,
      ),
      textNode(
        "strong",
        "",
        `${widLabel(army?.location?.targetWid)} ${armyTargetName(army)}`,
      ),
    );
    button.append(
      heading,
      meta,
      observation,
      route,
      lineupPreview(army.lineup),
    );
    button.addEventListener?.("click", () => selectArmy(army.armyId));
    button.addEventListener?.("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault?.();
        selectArmy(army.armyId);
        return;
      }
      if (!["ArrowUp", "ArrowDown"].includes(event.key)) return;
      event.preventDefault?.();
      const cards = [
        ...(documentRef?.querySelectorAll?.(".live-army-card") || []),
      ];
      const index = cards.indexOf(button);
      const delta = event.key === "ArrowDown" ? 1 : -1;
      const target = cards[
        Math.max(0, Math.min(cards.length - 1, index + delta))
      ];
      target?.focus?.();
    });
    return button;
  }

  function lineupPreview(lineup) {
    const wrapper = node("span", "live-army-card-lineup");
    if (lineup?.status !== "exact" || !lineup?.heroes?.length) {
      wrapper.append(
        textNode(
          "strong",
          "",
          lineup?.message || "无同 ID 战报，阵容未知",
        ),
      );
      return wrapper;
    }
    for (const hero of lineup.heroes.slice(0, 3)) {
      wrapper.append(heroImage(hero, false));
    }
    wrapper.append(
      textNode(
        "strong",
        "",
        lineup.heroes.map(heroName).join(" / "),
      ),
    );
    return wrapper;
  }

  function renderMap() {
    const canvas = element("live-army-map-canvas");
    if (!canvas || !state.snapshot || !mapModule.buildArmyDrawPlan) return;
    const { current, recentOffline } = visibleArmies();
    const selectedOffline = recentOffline.find(
      (army) => Number(army.armyId) === Number(state.selectedArmyId),
    ) || (state.snapshot?.recentOffline || []).find(
      (army) => Number(army.armyId) === Number(state.selectedArmyId),
    );
    const armies = [...current];
    if (
      selectedOffline
      && !armies.some(
        (army) => Number(army.armyId) === Number(selectedOffline.armyId),
      )
    ) {
      armies.push(selectedOffline);
    }
    const fallback = mapModule.boundsForArmies?.(
      armies,
      state.snapshot.bounds,
    );
    state.bounds = normalizedBounds(state.bounds || fallback);
    state.mapPlan = mapModule.buildArmyDrawPlan(
      armies,
      state.selectedArmyId,
      state.bounds,
      {
        width: canvas.clientWidth || 800,
        height: canvas.clientHeight || 560,
      },
    );
    mapModule.drawLiveArmyMap?.(canvas, state.mapPlan, {
      devicePixelRatio: windowRef?.devicePixelRatio || 1,
      reducedMotion: reducedMotion(),
    });
    const selected = findArmy(state.snapshot, state.selectedArmyId);
    setText(
      "live-army-map-status",
      selected
        ? `已锁定 ARMY ${selected.armyId} · WID ${Number(selected?.location?.currentWid) || 0} · ${formatArmyAge(
          armyAgeMs(selected, state.nowMs),
        )}`
        : `显示 ${armies.length} 支部队`,
    );
  }

  function renderDetail() {
    const container = element("live-army-detail");
    const army = findArmy(state.snapshot, state.selectedArmyId);
    if (!container) return;
    if (!army) {
      replace(
        container,
        stateBox("empty", "选择左侧卡片或地图标记查看部队"),
      );
      return;
    }
    const identity = node("section", "live-army-detail-identity");
    identity.append(
      textNode(
        "span",
        "hud-page-kicker",
        army.offline
          ? "RECENT OFFLINE / 最近离线"
          : army?.source?.isStale
            ? "STALE CURRENT / 过期待确认"
          : "CURRENT ARMY / 当前部队",
      ),
      textNode(
        "h3",
        "live-army-detail-id",
        `ARMY ${Number(army.armyId) || 0}`,
      ),
      textNode(
        "p",
        "live-army-detail-owner",
        `${armyOwnerName(army)} · ${armyUnionName(army)} · ${army.stateLabel}`,
      ),
    );
    const facts = node("div", "live-army-fact-grid");
    const models = [
      ["当前位置", widLabel(army?.location?.currentWid)],
      ["下一格", widLabel(army?.location?.nextWid)],
      ["最终目标", widLabel(army?.location?.targetWid)],
      [
        army.offline ? "删除时间" : "最后观测",
        `${formatDateTime(armyObservedAtMs(army))} · ${formatArmyAge(
          armyAgeMs(army, state.nowMs),
        )}`,
      ],
      [
        army.offline ? "离线时间" : "抵达倒计时",
        army.offline
          ? offlineText(army.offline, state.nowMs)
          : army.isMoving
            ? formatArmyCountdown(
            army?.timing?.endTime,
            state.nowMs / 1000,
          )
            : "静止状态",
      ],
      ["士气", Number(army.morale) || "--"],
      [
        "行军证据",
        army?.march?.realMarchId
          ? `realMarch ${army.march.realMarchId}`
          : army?.location?.source || "无实时行军",
      ],
    ];
    for (const [label, value] of models) {
      const fact = node("div", "live-army-fact");
      const factValue = textNode("strong", "", String(value));
      if (label === "抵达倒计时") {
        factValue.setAttribute?.("data-live-army-detail-countdown", "");
        factValue.dataset.endTime = String(
          Number(army?.timing?.endTime) || 0,
        );
      }
      fact.append(
        textNode("span", "", label),
        factValue,
      );
      facts.append(fact);
    }
    identity.append(facts);

    const body = node("div", "live-army-detail-body");
    const lineupSection = node("section", "live-army-detail-section");
    lineupSection.append(
      textNode("h4", "", "武将组合"),
    );
    if (
      army.lineup?.status === "exact"
      && army.lineup?.heroes?.length
    ) {
      const heroes = node("div", "live-army-hero-grid");
      for (const hero of army.lineup.heroes) {
        heroes.append(heroCard(hero));
      }
      lineupSection.append(heroes);
    } else {
      lineupSection.append(
        textNode(
          "div",
          "live-army-empty",
          army.lineup?.message || "无同 ID 战报，阵容未知",
        ),
      );
    }
    const evidenceSection = node(
      "section",
      "live-army-detail-section",
    );
    evidenceSection.append(textNode("h4", "", "严格证据"));
    const evidence = node("div", "live-army-evidence");
    const status =
      army.lineup?.status === "exact" ? "exact" : "unknown";
    evidence.setAttribute?.("data-status", status);
    if (status === "exact") {
      evidence.append(
        textNode(
          "strong",
          "",
          `精确命中战报 #${Number(army.lineup.battleId) || 0}`,
        ),
        textNode(
          "div",
          "",
          `${army.lineup.battleTimeText || formatDateTime(
            army.lineup.battleTime,
          )} · ${army.lineup.side === "def" ? "防守方" : "进攻方"} · ${
            army.lineup.complete ? "三将完整" : "阵容不完整"
          }`,
        ),
        textNode(
          "small",
          "",
          "仅按 army_id 与 atk_team_id / def_team_id 精确匹配，不按玩家或同盟推测。",
        ),
      );
    } else {
      evidence.append(
        textNode(
          "strong",
          "",
          army.lineup?.message || "无同 ID 战报，阵容未知",
        ),
        textNode(
          "small",
          "",
          "严格模式已启用：不会使用同玩家近期阵容补全。",
        ),
      );
    }
    if (army?.source?.isStale && !army.offline) {
      evidence.append(
        textNode(
          "div",
          "live-army-stale-warning",
          `该部队已 ${formatArmyAge(
            armyAgeMs(army, state.nowMs),
          )} 未再次观测，仍保留在 WorldState，但不能视为实时位置。`,
        ),
      );
    }
    evidenceSection.append(evidence);
    body.append(lineupSection, evidenceSection);
    replace(container, identity, body);
  }

  function heroCard(hero) {
    const card = node("article", "live-army-hero");
    card.append(
      heroImage(hero, true),
      textNode("strong", "", heroName(hero)),
      textNode(
        "small",
        "",
        `Lv.${Number(hero.level) || 0} · 进阶 ${Number(hero.advance) || 0}`,
      ),
    );
    const skills = node("div", "live-army-hero-skills");
    const rows = hero.skills || [];
    if (rows.length) {
      for (const skill of rows) {
        skills.append(
          textNode(
            "span",
            "",
            `${skill.name || `战法 ${Number(skill.id) || 0}`} Lv.${Number(skill.level) || 0}`,
          ),
        );
      }
    } else {
      skills.append(textNode("span", "", "战法数据未记录"));
    }
    card.append(skills);
    return card;
  }

  function heroImage(hero, full) {
    const image = node("img");
    image.setAttribute?.("src", hero.portraitUrl || PORTRAIT_PLACEHOLDER);
    image.setAttribute?.("alt", `${heroName(hero)}画像`);
    image.setAttribute?.("loading", "lazy");
    image.setAttribute?.("decoding", "async");
    if (!full) image.setAttribute?.("width", "26");
    image.dataset.fallbackSrc =
      hero.portraitFallbackUrl || PORTRAIT_PLACEHOLDER;
    image.addEventListener?.("error", () => {
      const fallback = image.dataset.fallbackSrc || PORTRAIT_PLACEHOLDER;
      if (image.getAttribute?.("src") !== fallback) {
        image.setAttribute?.("src", fallback);
        image.dataset.fallbackSrc = PORTRAIT_PLACEHOLDER;
      } else if (fallback !== PORTRAIT_PLACEHOLDER) {
        image.setAttribute?.("src", PORTRAIT_PLACEHOLDER);
      }
    });
    return image;
  }

  function updateActionState() {
    const army = findArmy(state.snapshot, state.selectedArmyId);
    const button = element("live-army-open-intelligence");
    if (button) {
      button.disabled = !Number(army?.location?.currentWid);
    }
  }

  function updateCountdowns() {
    for (const target of (
      documentRef?.querySelectorAll?.("[data-live-army-countdown]") || []
    )) {
      target.textContent = formatArmyCountdown(
        target.dataset?.endTime,
        state.nowMs / 1000,
      );
    }
    for (const target of (
      documentRef?.querySelectorAll?.("[data-live-army-age]") || []
    )) {
      const observedAtMs = Number(target.dataset?.observedAtMs) || 0;
      const label = target.dataset?.ageKind || "观测";
      target.textContent = observedAtMs
        ? `${label} ${formatDateTime(observedAtMs)} · ${formatArmyAge(
          Math.max(0, state.nowMs - observedAtMs),
        )}`
        : `${label}时间未知`;
    }
    renderFreshness();
    const army = findArmy(state.snapshot, state.selectedArmyId);
    if (army) {
      for (const target of (
        documentRef?.querySelectorAll?.(
          "[data-live-army-detail-countdown]",
        ) || []
      )) {
        target.textContent = formatArmyCountdown(
          army?.timing?.endTime,
          state.nowMs / 1000,
        );
      }
    }
  }

  function visibleArmies() {
    const nowSec = state.nowMs / 1000;
    const current = sortCurrentArmies(
      filterArmiesByTime(
        filterArmies(
          state.snapshot?.current || [],
          state.query,
          state.stateFilter,
        ),
        state.timeFilter,
        state.nowMs,
      ),
      nowSec,
    );
    const recentOffline = filterArmiesByTime(
      filterArmies(
        state.snapshot?.recentOffline || [],
        state.query,
        state.stateFilter,
      ),
      state.timeFilter,
      state.nowMs,
    ).sort((left, right) =>
      Number(right?.offline?.deletedAtMs || 0)
      - Number(left?.offline?.deletedAtMs || 0)
    );
    return { current, recentOffline };
  }

  function visibleSnapshot() {
    const rows = visibleArmies();
    return {
      current: rows.current,
      recentOffline: rows.recentOffline,
    };
  }

  function reducedMotion() {
    return (
      documentRef?.body?.dataset?.motionLevel === "reduced"
      || Boolean(
        windowRef?.matchMedia?.(
          "(prefers-reduced-motion: reduce)",
        )?.matches,
      )
    );
  }

  function element(id) {
    return documentRef?.getElementById?.(id) || null;
  }

  function node(tag, className = "") {
    const result = documentRef?.createElement?.(tag);
    if (result && className) result.className = className;
    return result;
  }

  function textNode(tag, className, text) {
    const result = node(tag, className);
    if (result) result.textContent = String(text ?? "");
    return result;
  }

  function stateBox(kind, message) {
    return textNode(
      "div",
      `hud-state hud-state-${kind}`,
      message,
    );
  }

  function replace(target, ...children) {
    target?.replaceChildren?.(...children.filter(Boolean));
  }

  function setText(id, value) {
    const target = element(id);
    if (target) target.textContent = String(value ?? "");
  }

  return {
    load,
    selectArmy,
    locateArmy,
    openInIntelligence,
    setFilter,
    state,
  };
}

function normalizeSearch(value) {
  return String(value ?? "").trim().toLocaleLowerCase();
}

function searchTextForArmy(army) {
  const owner = army?.owner || {};
  const location = army?.location || {};
  const lineup = army?.lineup || {};
  const heroes = lineup.status === "exact"
    ? (lineup.heroes || []).flatMap((hero) => [
      hero?.heroId ?? hero?.id,
      hero?.name,
    ])
    : [lineup.message || "阵容未知"];
  return [
    army?.armyId,
    army?.state,
    army?.stateKey,
    army?.stateLabel,
    army?.userId ?? owner.userId,
    army?.ownerName ?? owner.name,
    army?.ownerUnionId ?? owner.unionId,
    army?.ownerUnionName ?? owner.unionName,
    location.currentWid,
    location.nextWid,
    location.targetWid,
    location.targetName ?? army?.target?.name,
    ...heroes,
  ]
    .filter((value) => value !== null && value !== undefined)
    .join(" ");
}

function arrivalRank(army, nowSec) {
  const endTime = Number(
    army?.timing?.endTime
    ?? army?.march?.endTime
    ?? army?.endTime
    ?? 0,
  );
  if (army?.isMoving && endTime > nowSec) {
    return { group: 0, time: endTime };
  }
  if (army?.isMoving) {
    return { group: 1, time: endTime || Number.MAX_SAFE_INTEGER };
  }
  return { group: 2, time: Number.MAX_SAFE_INTEGER };
}

function armyObservedAtMs(army) {
  if (army?.offline) {
    return Number(army.offline.deletedAtMs) || 0;
  }
  return Number(army?.source?.observedAtMs) || 0;
}

function armyAgeMs(army, nowMs) {
  const observedAtMs = armyObservedAtMs(army);
  if (observedAtMs <= 0) return 0;
  return Math.max(0, Number(nowMs) - observedAtMs);
}

function normalizeFreshness(value, isStale) {
  if (isStale) return "stale";
  const normalized = String(value || "").toLowerCase();
  return ["fresh", "aging", "stale"].includes(normalized)
    ? normalized
    : "aging";
}

function timeFilterLabel(timeFilter) {
  const value = String(timeFilter || "10");
  if (value === "all") return "全部时间";
  if (value === "60") return "1小时内";
  return `${value}分钟内`;
}

function findArmy(snapshot, armyId) {
  const id = Number(armyId) || 0;
  if (!id) return null;
  return [
    ...(snapshot?.current || []),
    ...(snapshot?.recentOffline || []),
  ].find((army) => Number(army?.armyId) === id) || null;
}

function armyOwnerName(army) {
  return (
    army?.ownerName
    || army?.owner?.name
    || (Number(army?.userId ?? army?.owner?.userId)
      ? `玩家 ${Number(army?.userId ?? army?.owner?.userId)}`
      : "玩家未知")
  );
}

function armyUnionName(army) {
  return (
    army?.ownerUnionName
    || army?.owner?.unionName
    || (Number(army?.ownerUnionId ?? army?.owner?.unionId)
      ? `同盟 ${Number(army?.ownerUnionId ?? army?.owner?.unionId)}`
      : "同盟未知")
  );
}

function armyTargetName(army) {
  return army?.target?.name || army?.location?.targetName || "";
}

function heroName(hero) {
  return (
    hero?.name
    || `武将 ${Number(hero?.id ?? hero?.heroId) || 0}`
  );
}

function widLabel(value) {
  const wid = Number(value) || 0;
  return wid ? `WID ${wid}` : "WID --";
}

function offlineText(offline, nowMs) {
  const ageMs = Math.max(
    0,
    Number(offline?.ageMs)
    || (Number(nowMs) - Number(offline?.deletedAtMs || 0)),
  );
  const minutes = Math.floor(ageMs / 60_000);
  const seconds = Math.floor((ageMs % 60_000) / 1000);
  return `${minutes}分${String(seconds).padStart(2, "0")}秒前`;
}

function formatDateTime(value) {
  const number = Number(value) || 0;
  if (!number) return "时间未知";
  const milliseconds = number < 10_000_000_000 ? number * 1000 : number;
  return new Date(milliseconds).toLocaleString("zh-CN", {
    hour12: false,
  });
}

function normalizedBounds(bounds) {
  if (!bounds) {
    return { rowUp: 0, rowDown: 19, colLeft: 0, colRight: 19 };
  }
  return {
    rowUp: Math.max(0, Math.round(Number(bounds.rowUp) || 0)),
    rowDown: Math.max(
      Math.round(Number(bounds.rowUp) || 0) + 1,
      Math.round(Number(bounds.rowDown) || 19),
    ),
    colLeft: Math.max(0, Math.round(Number(bounds.colLeft) || 0)),
    colRight: Math.max(
      Math.round(Number(bounds.colLeft) || 0) + 1,
      Math.round(Number(bounds.colRight) || 19),
    ),
  };
}

function canvasPoint(canvas, event) {
  const rect = canvas?.getBoundingClientRect?.() || {
    left: 0,
    top: 0,
  };
  return {
    x: (Number(event?.clientX) || 0) - Number(rect.left || 0),
    y: (Number(event?.clientY) || 0) - Number(rect.top || 0),
  };
}

function worldPointForCanvas(point, plan) {
  const rect = plan.mapRect;
  const bounds = plan.bounds;
  const rowRatio = Math.max(
    0,
    Math.min(1, (point.y - rect.y) / Math.max(1, rect.height)),
  );
  const colRatio = Math.max(
    0,
    Math.min(1, (point.x - rect.x) / Math.max(1, rect.width)),
  );
  return {
    row:
      bounds.rowUp + rowRatio * (bounds.rowDown - bounds.rowUp),
    col:
      bounds.colLeft + colRatio * (bounds.colRight - bounds.colLeft),
  };
}

if (typeof window !== "undefined") {
  window.LiveArmyCommand = createLiveArmyCommand({
    documentRef: window.document,
    windowRef: window,
    fetchFn: window.fetch.bind(window),
    mapModule: liveArmyMap,
    emitHudEvent: (event) => window.HudSystem?.emit(event),
    resolveHudEvent: (key) => window.HudSystem?.resolveEvent(key),
  });
}

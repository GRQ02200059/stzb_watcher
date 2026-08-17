export const VISIBLE_DOMAINS = Object.freeze({
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
});

const VALID_MOTION = new Set(["full", "standard", "reduced"]);
const VALID_STATES = new Set([
  "idle",
  "loading",
  "refreshing",
  "success",
  "empty",
  "stale",
  "warning",
  "error",
]);
const VALID_EVENTS = new Set([
  "intelligence:risk-detected",
  "battle:report-arrived",
  "simulation:completed",
  "score:recalculated",
  "connection:restored",
  "data:stale",
  "operation:stage-changed",
]);
const STATE_MESSAGES = Object.freeze({
  idle: "等待数据",
  loading: "正在加载…",
  refreshing: "正在刷新…",
  success: "加载完成",
  empty: "暂无数据",
  stale: "数据可能已过期",
  warning: "数据需要关注",
  error: "加载失败",
});
const PERSISTENT_TOAST_SEVERITIES = new Set([
  "critical",
  "error",
]);
const MAX_EVENT_COOLDOWN_MS = 60_000;
const MAX_ACTIVE_VALUE_ANIMATIONS = 6;

export function domainForTab(tabId) {
  return VISIBLE_DOMAINS[Number(tabId)] || "compatibility";
}

export function normalizeMotionLevel(level, reduced) {
  if (reduced) return "reduced";
  return VALID_MOTION.has(level) ? level : "standard";
}

function stateModel(state) {
  const kind = VALID_STATES.has(state?.kind) ? state.kind : "empty";
  return {
    kind,
    message: state?.message || STATE_MESSAGES[kind],
    actionLabel: state?.actionLabel || "",
    action: state?.action || state?.onAction,
    replace: state?.replace,
  };
}

function legacyEventType(kind) {
  if (["danger", "critical", "error"].includes(kind)) {
    return "intelligence:risk-detected";
  }
  if (kind === "warning") return "data:stale";
  if (kind === "success") return "connection:restored";
  return "battle:report-arrived";
}

function healthCard(documentRef, component) {
  const card = documentRef.createElement("article");
  card.className = "hud-health-card";
  card.dataset.status = component?.status || "unknown";

  const chip = documentRef.createElement("span");
  chip.className = "hud-status-chip";
  chip.textContent = (component?.status || "unknown").toUpperCase();

  const label = documentRef.createElement("strong");
  label.textContent = component?.label || "未知组件";

  const detail = documentRef.createElement("small");
  detail.textContent = component?.detail || "暂无详情";

  card.append(chip, label, detail);
  return card;
}

export function createHudSystem({
  documentRef = globalThis.document,
  matchMediaFn = globalThis.matchMedia?.bind(globalThis),
  setTimeoutFn = globalThis.setTimeout?.bind(globalThis),
  clearTimeoutFn = globalThis.clearTimeout?.bind(globalThis),
  requestAnimationFrameFn =
    globalThis.requestAnimationFrame?.bind(globalThis),
  performanceNow = () => globalThis.performance?.now?.() || Date.now(),
  nowFn = () => Date.now(),
  onDiagnostic = () => {},
} = {}) {
  const activeEvents = new Map();
  const eventCooldowns = new Map();
  const eventTimers = new Set();
  const eventClassOwners = new Map();
  const activeToasts = new Map();
  let activeValueAnimations = 0;
  let peakValueAnimations = 0;
  let motionLevel = normalizeMotionLevel(
    "standard",
    Boolean(
      matchMediaFn?.("(prefers-reduced-motion: reduce)")?.matches,
    ),
  );

  function setDomain(tabId) {
    const domain = domainForTab(tabId);
    if (documentRef?.body) {
      documentRef.body.dataset.visualDomain = domain;
    }
    return domain;
  }

  function setMotionLevel(level) {
    motionLevel = normalizeMotionLevel(
      level,
      Boolean(
        matchMediaFn?.("(prefers-reduced-motion: reduce)")?.matches,
      ),
    );
    if (documentRef?.body) {
      documentRef.body.dataset.motionLevel = motionLevel;
    }
    return motionLevel;
  }

  function pulse(element, kind = "info") {
    if (!element || motionLevel === "reduced") return false;
    const className = `hud-pulse-${kind}`;
    element.classList.remove(className);
    element.classList.add(className);
    setTimeoutFn?.(
      () => element.classList.remove(className),
      720,
    );
    return true;
  }

  function animateValue(
    element,
    from,
    to,
    { duration = 360, formatter } = {},
  ) {
    const animationDuration =
      Number.isFinite(duration) && duration > 0 ? duration : 360;
    const format =
      formatter ||
      ((value) => Math.round(value).toLocaleString("zh-CN"));
    if (!element) return Promise.resolve(to);
    if (motionLevel === "reduced" || !requestAnimationFrameFn) {
      element.textContent = format(to);
      return Promise.resolve(to);
    }
    if (activeValueAnimations >= MAX_ACTIVE_VALUE_ANIMATIONS) {
      element.textContent = format(to);
      return Promise.resolve(to);
    }
    activeValueAnimations += 1;
    peakValueAnimations = Math.max(
      peakValueAnimations,
      activeValueAnimations,
    );
    return new Promise((resolve, reject) => {
      const started = performanceNow();
      let finished = false;
      function finish(callback, value) {
        if (finished) return;
        finished = true;
        activeValueAnimations = Math.max(0, activeValueAnimations - 1);
        callback(value);
      }
      function frame(now) {
        try {
          const progress = Math.min(
            1,
            (now - started) / animationDuration,
          );
          const eased = 1 - Math.pow(1 - progress, 3);
          element.textContent = format(from + (to - from) * eased);
          if (progress < 1) requestAnimationFrameFn(frame);
          else finish(resolve, to);
        } catch (error) {
          finish(reject, error);
        }
      }
      try {
        requestAnimationFrameFn(frame);
      } catch (error) {
        finish(reject, error);
      }
    });
  }

  function getAnimationStats() {
    return {
      activeValueAnimations,
      peakValueAnimations,
    };
  }

  function resetAnimationStats() {
    peakValueAnimations = activeValueAnimations;
    return getAnimationStats();
  }

  function renderState(container, state) {
    const model = stateModel(state);
    if (!container || !documentRef?.createElement) return model;
    if (["loading", "refreshing"].includes(model.kind)) {
      container.setAttribute?.("aria-busy", "true");
    } else {
      container.removeAttribute?.("aria-busy");
    }
    const element = documentRef.createElement("div");
    element.className = `hud-state hud-state-${model.kind}`;
    element.textContent = model.message;
    if (model.actionLabel && typeof model.action === "function") {
      const action = documentRef.createElement("button");
      action.type = "button";
      action.className = "hud-state-action";
      action.textContent = String(model.actionLabel);
      action.addEventListener?.("click", () => model.action(model));
      element.append(action);
    }
    const retainContent =
      ["refreshing", "stale", "warning"].includes(model.kind) ||
      (model.kind === "error" && model.replace !== true);
    if (retainContent) {
      container.querySelector?.(".hud-state")?.remove();
      container.append(element);
    } else {
      container.replaceChildren(element);
    }
    return model;
  }

  function removeToast(key, article) {
    const active = activeToasts.get(key);
    if (!active || active.article !== article) return;
    if (active.timer !== undefined) {
      clearTimeoutFn?.(active.timer);
    }
    activeToasts.delete(key);
    article.remove?.();
  }

  function toast(notification = {}) {
    const region =
      documentRef?.getElementById?.("hud-toast-region");
    if (!region || !documentRef?.createElement) return null;

    const severity = notification.severity || "info";
    const title = notification.title || notification.message || "系统通知";
    const key =
      notification.dedupeKey || `${severity}:${String(title)}`;
    const active = activeToasts.get(key);
    if (active) {
      active.count += 1;
      active.article.dataset.count = String(active.count);
      return active.article;
    }

    const article = documentRef.createElement("article");
    article.className = "hud-toast";
    article.dataset.severity = severity;
    article.dataset.count = "1";
    article.dataset.dedupeKey = String(key);
    article.setAttribute(
      "aria-live",
      ["critical", "error"].includes(severity)
        ? "assertive"
        : "polite",
    );

    const heading = documentRef.createElement("strong");
    heading.textContent = String(title);
    article.append(heading);

    if (notification.message && notification.message !== title) {
      const message = documentRef.createElement("p");
      message.textContent = String(notification.message);
      article.append(message);
    }

    const metadata = documentRef.createElement("small");
    const timestamp = notification.timestamp ?? nowFn();
    const timeLabel = new Date(timestamp).toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
    metadata.textContent = notification.source
      ? `${String(notification.source)} · ${timeLabel}`
      : timeLabel;
    article.append(metadata);

    const close = documentRef.createElement("button");
    close.type = "button";
    close.className = "hud-toast-close";
    close.textContent = "关闭";
    close.setAttribute("aria-label", "关闭通知");
    close.addEventListener?.("click", () => removeToast(key, article));
    article.append(close);

    const action = notification.action;
    if (action?.label) {
      const actionButton = documentRef.createElement("button");
      actionButton.type = "button";
      actionButton.className = "hud-toast-action";
      actionButton.textContent = String(action.label);
      actionButton.addEventListener?.("click", () => {
        const handler = action.handler || action.onClick;
        if (typeof handler === "function") handler(notification);
        removeToast(key, article);
      });
      article.append(actionButton);
    }

    region.append(article);
    const entry = { article, count: 1, timer: undefined };
    activeToasts.set(key, entry);

    const delay =
      severity === "warning"
        ? 6200
        : ["info", "success"].includes(severity)
          ? 4200
          : null;
    if (delay !== null) {
      entry.timer = setTimeoutFn?.(
        () => removeToast(key, article),
        delay,
      );
    } else if (!PERSISTENT_TOAST_SEVERITIES.has(severity)) {
      entry.timer = setTimeoutFn?.(
        () => removeToast(key, article),
        4200,
      );
    }
    return article;
  }

  function diagnose(code, detail = {}) {
    try {
      onDiagnostic?.({ code, ...detail });
    } catch {}
  }

  function resolveEventTarget(target) {
    if (typeof target === "string") {
      try {
        return documentRef?.querySelector?.(target) || null;
      } catch (error) {
        diagnose("invalid-selector", {
          selector: target,
          message: error?.message || String(error),
        });
        return null;
      }
    }
    return target?.classList ? target : null;
  }

  function acquireEventClasses(target, classNames) {
    let classOwners = eventClassOwners.get(target);
    if (!classOwners) {
      classOwners = new Map();
      eventClassOwners.set(target, classOwners);
    }
    for (const className of classNames) {
      const owners = classOwners.get(className) || 0;
      if (owners === 0) target.classList.add(className);
      classOwners.set(className, owners + 1);
    }
  }

  function releaseEventClasses(target, classNames) {
    const classOwners = eventClassOwners.get(target);
    if (!classOwners) return;
    for (const className of classNames) {
      const owners = classOwners.get(className) || 0;
      if (owners <= 1) {
        classOwners.delete(className);
        target.classList.remove(className);
      } else {
        classOwners.set(className, owners - 1);
      }
    }
    if (classOwners.size === 0) eventClassOwners.delete(target);
  }

  function emit(event = {}) {
    if (!VALID_EVENTS.has(event.type)) {
      diagnose("invalid-event-type", { type: event.type });
      return false;
    }
    if (documentRef?.visibilityState === "hidden") {
      return false;
    }
    const key = String(event.dedupeKey || event.type);
    const now = Number(nowFn?.()) || Date.now();
    const cooldownUntil = eventCooldowns.get(key) || 0;
    if (activeEvents.has(key) || cooldownUntil > now) return false;
    if (cooldownUntil) eventCooldowns.delete(key);

    const target = resolveEventTarget(event.target);
    const severity = event.severity || "info";
    if (!target) {
      diagnose("missing-target", {
        type: event.type,
        target: event.target,
      });
      const notification = toast({
        severity,
        title: event.message || "事件已记录",
        timestamp: event.timestamp,
        source: event.domain,
        dedupeKey: key,
      });
      if (!notification) return false;
      const cooldownMs = Math.min(
        MAX_EVENT_COOLDOWN_MS,
        Math.max(0, Number(event.cooldownMs) || 0),
      );
      if (cooldownMs) eventCooldowns.set(key, now + cooldownMs);
      return true;
    }

    const classNames = [
      `hud-event-${severity}`,
      `hud-event-${event.type.replaceAll(":", "-")}`,
    ];
    if (motionLevel !== "reduced") {
      acquireEventClasses(target, classNames);
    }

    const active = {
      target,
      classNames:
        motionLevel === "reduced" ? [] : classNames,
      timer: undefined,
    };
    activeEvents.set(key, active);
    const cooldownMs = Math.min(
      MAX_EVENT_COOLDOWN_MS,
      Math.max(0, Number(event.cooldownMs) || 0),
    );
    if (cooldownMs) eventCooldowns.set(key, now + cooldownMs);
    const duration = severity === "critical" ? 1200 : 720;
    active.timer = setTimeoutFn?.(() => {
      if (activeEvents.get(key) !== active) return;
      releaseEventClasses(target, active.classNames);
      activeEvents.delete(key);
      eventTimers.delete(active.timer);
    }, duration);
    if (active.timer !== undefined) eventTimers.add(active.timer);

    if (event.message) {
      toast({
        severity,
        title: event.message,
        timestamp: event.timestamp,
        dedupeKey: key,
      });
    }
    return true;
  }

  function resolveEvent(dedupeKey) {
    const key = String(dedupeKey || "");
    if (!key) return false;
    const active = activeEvents.get(key);
    const hadCooldown = eventCooldowns.delete(key);
    if (!active) return hadCooldown;
    if (active.timer !== undefined) {
      clearTimeoutFn?.(active.timer);
      eventTimers.delete(active.timer);
    }
    releaseEventClasses(active.target, active.classNames);
    activeEvents.delete(key);
    return true;
  }

  function clearEffects() {
    for (const timer of eventTimers) clearTimeoutFn?.(timer);
    eventTimers.clear();
    for (const [target, classOwners] of eventClassOwners) {
      for (const className of classOwners.keys()) {
        target.classList.remove(className);
      }
    }
    eventClassOwners.clear();
    activeEvents.clear();
  }

  async function loadHealth(
    fetchFn = globalThis.fetch?.bind(globalThis),
  ) {
    const container =
      documentRef?.getElementById?.("hud-health-grid");
    if (!container || !fetchFn) return null;
    renderState(container, { kind: "loading" });
    try {
      const response = await fetchFn("/api/hud/health", {
        cache: "no-store",
      });
      const body = await response.json();
      if (!response.ok || !body.ok) {
        throw new Error(body.error || "health failed");
      }
      const cards = Object.values(body.components || {}).map(
        (component) => healthCard(documentRef, component),
      );
      if (cards.length) container.replaceChildren(...cards);
      else renderState(container, { kind: "empty" });
      container.dataset.overall = body.overall || "unknown";
      return body;
    } catch (error) {
      renderState(container, {
        kind: "error",
        message: error?.message || "运行链路检查失败",
        replace: true,
      });
      return null;
    }
  }

  return {
    setDomain,
    setMotionLevel,
    pulse,
    animateValue,
    getAnimationStats,
    resetAnimationStats,
    renderState,
    emit,
    resolveEvent,
    clearEffects,
    toast,
    loadHealth,
    get motionLevel() {
      return motionLevel;
    },
  };
}

const defaultSystem =
  typeof document === "undefined" ? null : createHudSystem();

function currentActiveTab(documentRef = document) {
  const active = documentRef.querySelector?.(
    "nav [data-tab-index].active",
  );
  return Number(active?.dataset?.tabIndex || 33);
}

if (
  typeof window !== "undefined" &&
  typeof document !== "undefined" &&
  defaultSystem
) {
  window.HudSystem = defaultSystem;
  const initializeBrowserSeams = () => {
    defaultSystem.setDomain(currentActiveTab());
    const stored = document.body.dataset.motionLevel || "standard";
    defaultSystem.setMotionLevel(stored);
    document
      .getElementById("hud-health-refresh")
      ?.addEventListener("click", () => defaultSystem.loadHealth());
  };
  if (document.readyState === "loading") {
    document.addEventListener(
      "DOMContentLoaded",
      initializeBrowserSeams,
      { once: true },
    );
  } else {
    initializeBrowserSeams();
  }
  window.addEventListener("stzb:tab-changed", (event) => {
    defaultSystem.clearEffects();
    defaultSystem.setDomain(event.detail?.tabId);
  });
  window.addEventListener("stzb:hud-pulse", (event) => {
    defaultSystem.emit({
      type: legacyEventType(event.detail?.kind),
      target: event.detail?.selector,
      severity: event.detail?.kind || "info",
      dedupeKey: event.detail?.selector,
    });
  });
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") {
      defaultSystem.clearEffects();
    }
  });
}

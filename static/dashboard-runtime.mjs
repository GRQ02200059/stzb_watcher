export function backoffDelay(attempt, options = {}) {
  const baseMs = Number(options.baseMs || 1000);
  const capMs = Number(options.capMs || 30000);
  return Math.min(capMs, baseMs * (2 ** Math.max(0, Number(attempt) || 0)));
}

export function escapeHtml(value) {
  return String(value == null ? "" : value)
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

export function normalizeEventText(data, limit = 60) {
  return escapeHtml(JSON.stringify(data || {}).slice(0, limit));
}

export function normalizeTimestamp(value, nowSeconds = Date.now() / 1000) {
  const now = Number(nowSeconds);
  const fallback = Number.isFinite(now) ? now : Date.now() / 1000;
  if (typeof value === "number" && Number.isFinite(value)) {
    return value > 1e12 ? value / 1000 : value;
  }
  const text = String(value == null ? "" : value).trim();
  if (!text) return fallback;
  if (/^\d+(?:\.\d+)?$/.test(text)) {
    const numeric = Number(text);
    return numeric > 1e12 ? numeric / 1000 : numeric;
  }
  if (/^\d{2}:\d{2}:\d{2}$/.test(text)) {
    const [hour, minute, second] = text.split(":").map(Number);
    const current = new Date(fallback * 1000);
    current.setHours(hour, minute, second, 0);
    return current.getTime() / 1000;
  }
  const parsed = Date.parse(text);
  return Number.isFinite(parsed) ? parsed / 1000 : fallback;
}

export async function parseJsonResponse(response, endpoint = "") {
  const contentType = response.headers?.get?.("content-type") || "";
  const text = await response.text();
  if (/text\/html/i.test(contentType) || /^\s*</.test(text)) {
    throw new Error(
      `后端接口不可用（${response.status} ${endpoint}），当前后端可能仍是旧版本，请停止并重新启动后端。`,
    );
  }
  let data;
  try {
    data = JSON.parse(text);
  } catch {
    throw new Error(`后端返回了无效 JSON（${endpoint}），请检查后端日志并重启服务。`);
  }
  if (!response.ok) {
    throw new Error(data?.message || data?.error || `HTTP ${response.status}`);
  }
  return data;
}

export function createVisibilityTicker({
  documentRef = document,
  intervalMs,
  setIntervalFn = setInterval,
  clearIntervalFn = clearInterval,
  onVisibleTick,
}) {
  let timer = null;
  return {
    start() {
      if (timer !== null) return timer;
      timer = setIntervalFn(() => {
        if (documentRef.visibilityState !== "hidden") onVisibleTick();
      }, intervalMs);
      return timer;
    },
    stop() {
      if (timer === null) return;
      clearIntervalFn(timer);
      timer = null;
    },
  };
}

if (typeof window !== "undefined") {
  window.DashboardRuntime = Object.freeze({
    backoffDelay,
    createVisibilityTicker,
    escapeHtml,
    normalizeTimestamp,
    normalizeEventText,
    parseJsonResponse,
  });
  window.dispatchEvent(new CustomEvent("stzb:runtime-ready"));
}

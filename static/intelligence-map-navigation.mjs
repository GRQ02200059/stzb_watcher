export function createViewportHistory(initialState, limit = 30) {
  const maximum = Math.max(1, Number(limit) || 30);
  let entries = [cloneState(initialState)];
  let index = 0;
  return {
    push(nextState) {
      const next = cloneState(nextState);
      if (sameState(entries[index], next)) return cloneState(entries[index]);
      entries = entries.slice(0, index + 1);
      entries.push(next);
      if (entries.length > maximum) entries.shift();
      index = entries.length - 1;
      return cloneState(entries[index]);
    },
    replace(nextState) {
      entries[index] = cloneState(nextState);
      return cloneState(entries[index]);
    },
    back() {
      if (index > 0) index -= 1;
      return cloneState(entries[index]);
    },
    forward() {
      if (index < entries.length - 1) index += 1;
      return cloneState(entries[index]);
    },
    current() {
      return cloneState(entries[index]);
    },
    canBack() {
      return index > 0;
    },
    canForward() {
      return index < entries.length - 1;
    },
  };
}

export function serializeMapState(state) {
  const payload = {
    b: [
      state.bounds.rowUp,
      state.bounds.rowDown,
      state.bounds.colLeft,
      state.bounds.colRight,
    ],
    w: Number(state.selectedWid || 0),
    l: state.layers || {},
  };
  return `#map=${encodeURIComponent(JSON.stringify(payload))}`;
}

export function parseMapState(hash, fallback) {
  try {
    const text = String(hash || "");
    const marker = text.indexOf("#map=");
    if (marker < 0) return cloneState(fallback);
    const payload = JSON.parse(decodeURIComponent(text.slice(marker + 5)));
    if (!Array.isArray(payload.b) || payload.b.length !== 4) {
      return cloneState(fallback);
    }
    const bounds = {
      rowUp: Number(payload.b[0]),
      rowDown: Number(payload.b[1]),
      colLeft: Number(payload.b[2]),
      colRight: Number(payload.b[3]),
    };
    if (!validBounds(bounds)) return cloneState(fallback);
    return {
      bounds,
      selectedWid: Number.isInteger(Number(payload.w))
        ? Math.max(0, Number(payload.w))
        : 0,
      layers: {
        ...fallback.layers,
        ...(payload.l && typeof payload.l === "object" ? payload.l : {}),
      },
    };
  } catch {
    return cloneState(fallback);
  }
}

export function isBoundsUseful(bounds, dataBounds) {
  if (!validBounds(bounds) || !validBounds(dataBounds)) return false;
  return !(
    bounds.rowDown < dataBounds.rowUp
    || bounds.rowUp > dataBounds.rowDown
    || bounds.colRight < dataBounds.colLeft
    || bounds.colLeft > dataBounds.colRight
  );
}

export function interpolateBounds(from, to, progress) {
  const value = Math.min(1, Math.max(0, Number(progress) || 0));
  const mix = (left, right) => Math.round(left + (right - left) * value);
  return {
    rowUp: mix(from.rowUp, to.rowUp),
    rowDown: mix(from.rowDown, to.rowDown),
    colLeft: mix(from.colLeft, to.colLeft),
    colRight: mix(from.colRight, to.colRight),
  };
}

function validBounds(bounds) {
  return bounds
    && ["rowUp", "rowDown", "colLeft", "colRight"].every((key) =>
      Number.isFinite(bounds[key]) && bounds[key] >= 0
    )
    && bounds.rowUp <= bounds.rowDown
    && bounds.colLeft <= bounds.colRight;
}

function cloneState(state) {
  return {
    bounds: { ...state.bounds },
    selectedWid: Number(state.selectedWid || 0),
    layers: { ...(state.layers || {}) },
  };
}

function sameState(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

if (typeof window !== "undefined") {
  window.IntelligenceMapNavigation = Object.freeze({
    createViewportHistory,
    serializeMapState,
    parseMapState,
    isBoundsUseful,
    interpolateBounds,
  });
}

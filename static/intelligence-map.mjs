export const LEVEL_COLORS = Object.freeze([
  "#18232d", "#263746", "#24536a", "#167a78", "#199f69",
  "#75b83b", "#d1b52c", "#e87e25", "#ed4936", "#ff174f",
]);

export function levelColor(level) {
  const value = Number(level);
  return Number.isInteger(value) && value >= 0 && value <= 9
    ? LEVEL_COLORS[value]
    : LEVEL_COLORS[0];
}

export function panBounds(bounds, rowDelta, colDelta) {
  const rowSpan = bounds.rowDown - bounds.rowUp;
  const colSpan = bounds.colRight - bounds.colLeft;
  const rowUp = Math.max(0, Math.round(bounds.rowUp + rowDelta));
  const colLeft = Math.max(0, Math.round(bounds.colLeft + colDelta));
  return {
    rowUp,
    rowDown: rowUp + rowSpan,
    colLeft,
    colRight: colLeft + colSpan,
  };
}

export function zoomBounds(bounds, anchorRow, anchorCol, direction) {
  const currentRows = bounds.rowDown - bounds.rowUp + 1;
  const currentCols = bounds.colRight - bounds.colLeft + 1;
  const factor = direction < 0 ? 0.5 : 2;
  const rows = Math.min(160, Math.max(5, Math.round(currentRows * factor)));
  const cols = Math.min(160, Math.max(5, Math.round(currentCols * factor)));
  const rowUp = Math.max(0, Math.round(anchorRow - rows / 2));
  const colLeft = Math.max(0, Math.round(anchorCol - cols / 2));
  return {
    rowUp,
    rowDown: rowUp + rows - 1,
    colLeft,
    colRight: colLeft + cols - 1,
  };
}

export function tileDrawPlan(tile, size, layers = {}) {
  const plan = [{ kind: "level", color: levelColor(tile.landLevel) }];
  if (Number(tile.landLevel) >= 7 && size >= 8) plan.push({ kind: "high-level" });
  if (layers.ownership && tile.relation && tile.relation !== "unknown" && size >= 8) plan.push({ kind: "ownership" });
  if (layers.freshness && tile.freshness === "stale") plan.push({ kind: "stale" });
  if (layers.paths && tile.isPath) plan.push({ kind: "path" });
  if (layers.armies && Number(tile.armies || 0) > 0) plan.push({ kind: "armies" });
  if (tile.favorite) plan.push({ kind: "favorite" });
  if (tile.selected) plan.push({ kind: "selected" });
  if (size >= 14) plan.push({ kind: "label" });
  return plan;
}

export function drawIntelligenceMap(canvas, tiles, options = {}) {
  const context = canvas.getContext("2d");
  const ratio = Math.min(2, window.devicePixelRatio || 1);
  const bounds = options.bounds || { rowUp: 0, rowDown: 0, colLeft: 0, colRight: 0 };
  const rows = Math.max(1, bounds.rowDown - bounds.rowUp + 1);
  const cols = Math.max(1, bounds.colRight - bounds.colLeft + 1);
  const width = canvas.clientWidth || 800;
  const height = canvas.clientHeight || 520;
  canvas.width = Math.round(width * ratio);
  canvas.height = Math.round(height * ratio);
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
  context.clearRect(0, 0, width, height);
  context.fillStyle = "#080d22";
  context.fillRect(0, 0, width, height);
  const size = Math.max(3, Math.min(width / cols, height / rows));
  const tileMap = new Map(tiles.map((tile) => [Number(tile.wid), tile]));
  const hitAreas = [];
  for (let row = bounds.rowUp; row <= bounds.rowDown; row += 1) {
    for (let col = bounds.colLeft; col <= bounds.colRight; col += 1) {
      const wid = row * 10000 + col;
      const tile = tileMap.get(wid) || { wid, row, col, landLevel: 0, freshness: "unknown", relation: "unknown" };
      const x = (col - bounds.colLeft) * size;
      const y = (row - bounds.rowUp) * size;
      drawPlan(context, tileDrawPlan(tile, size, options.layers || {}), tile, x, y, size);
      hitAreas.push({ wid, x, y, size });
    }
  }
  return { bounds, size, hitAreas };
}

function drawPlan(context, plan, tile, x, y, size) {
  for (const step of plan) {
    if (step.kind === "level") {
      context.fillStyle = step.color;
      context.fillRect(x + 1, y + 1, size - 2, size - 2);
    } else if (step.kind === "high-level") {
      context.strokeStyle = "rgba(255,255,255,.32)";
      context.strokeRect(x + 2, y + 2, size - 4, size - 4);
    } else if (step.kind === "ownership") {
      context.fillStyle = { self: "#34d399", ally: "#54a6ff", enemy: "#f05267" }[tile.relation] || "#7183a7";
      context.beginPath();
      context.moveTo(x + size - 1, y + 1);
      context.lineTo(x + size - 1, y + Math.min(8, size / 3));
      context.lineTo(x + size - Math.min(8, size / 3), y + 1);
      context.fill();
    } else if (step.kind === "stale") {
      context.strokeStyle = "rgba(255,255,255,.22)";
      for (let offset = -size; offset < size; offset += 6) {
        context.beginPath();
        context.moveTo(x + Math.max(0, offset), y + Math.max(0, -offset));
        context.lineTo(x + Math.min(size, size + offset), y + Math.min(size, size - offset));
        context.stroke();
      }
    } else if (step.kind === "path") {
      context.strokeStyle = "#8cecff";
      context.lineWidth = 2;
      context.strokeRect(x + 2, y + 2, size - 4, size - 4);
    } else if (step.kind === "armies") {
      context.fillStyle = "#f4f7ff";
      context.beginPath();
      context.arc(x + size / 2, y + size / 2, Math.max(2, size / 7), 0, Math.PI * 2);
      context.fill();
    } else if (step.kind === "favorite") {
      context.strokeStyle = "#f5b84b";
      context.lineWidth = 2;
      context.strokeRect(x + 3, y + 3, size - 6, size - 6);
    } else if (step.kind === "selected") {
      context.strokeStyle = "#fff";
      context.lineWidth = 2;
      context.strokeRect(x + 1, y + 1, size - 2, size - 2);
      context.strokeStyle = "#061127";
      context.strokeRect(x + 4, y + 4, size - 8, size - 8);
    } else if (step.kind === "label") {
      context.fillStyle = "#f4f7ff";
      context.font = `${Math.max(9, size * .36)}px system-ui`;
      context.textAlign = "center";
      context.textBaseline = "middle";
      context.fillText(String(tile.landLevel || ""), x + size / 2, y + size / 2);
    }
  }
  context.lineWidth = 1;
}

export function hitTestMap(pointX, pointY, renderState) {
  return renderState?.hitAreas?.find((item) =>
    pointX >= item.x && pointX < item.x + item.size &&
    pointY >= item.y && pointY < item.y + item.size
  )?.wid || 0;
}

if (typeof window !== "undefined") {
  window.IntelligenceMap = Object.freeze({
    LEVEL_COLORS,
    levelColor,
    panBounds,
    zoomBounds,
    tileDrawPlan,
    drawIntelligenceMap,
    hitTestMap,
  });
  window.dispatchEvent(new CustomEvent("stzb:intelligence-map-ready"));
}

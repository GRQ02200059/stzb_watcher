export function semanticMapMode(bounds, width, height) {
  const rows = Math.max(1, bounds.rowDown - bounds.rowUp + 1);
  const cols = Math.max(1, bounds.colRight - bounds.colLeft + 1);
  const cellSize = Math.min(width / cols, height / rows);
  if (rows > 120 || cols > 120 || cellSize < 3) return "far";
  if (rows > 40 || cols > 40 || cellSize < 8) return "middle";
  return "near";
}

export function bucketGridForBounds(
  bounds,
  width,
  height,
  maxBuckets = 2500,
) {
  const rows = Math.max(1, bounds.rowDown - bounds.rowUp + 1);
  const cols = Math.max(1, bounds.colRight - bounds.colLeft + 1);
  const aspect = Math.max(0.1, Number(width || 1) / Number(height || 1));
  const targetRows = Math.max(1, Math.floor(Math.sqrt(maxBuckets / aspect)));
  const targetCols = Math.max(1, Math.floor(maxBuckets / targetRows));
  const bucketRows = Math.max(1, Math.ceil(rows / targetRows));
  const bucketCols = Math.max(1, Math.ceil(cols / targetCols));
  return {
    bucketRows,
    bucketCols,
    gridRows: Math.ceil(rows / bucketRows),
    gridCols: Math.ceil(cols / bucketCols),
  };
}

export function worldToRadar(row, col, dataBounds, radarRect) {
  const rowSpan = Math.max(1, dataBounds.rowDown - dataBounds.rowUp);
  const colSpan = Math.max(1, dataBounds.colRight - dataBounds.colLeft);
  return {
    x: radarRect.x
      + ((Number(col) - dataBounds.colLeft) / colSpan) * radarRect.width,
    y: radarRect.y
      + ((Number(row) - dataBounds.rowUp) / rowSpan) * radarRect.height,
  };
}

export function radarToWorld(x, y, dataBounds, radarRect) {
  const rowRatio = clamp((Number(y) - radarRect.y) / radarRect.height, 0, 1);
  const colRatio = clamp((Number(x) - radarRect.x) / radarRect.width, 0, 1);
  return {
    row: Math.round(
      dataBounds.rowUp + rowRatio * (dataBounds.rowDown - dataBounds.rowUp),
    ),
    col: Math.round(
      dataBounds.colLeft + colRatio * (dataBounds.colRight - dataBounds.colLeft),
    ),
  };
}

export function radarViewportRect(viewBounds, dataBounds, radarRect) {
  const topLeft = worldToRadar(
    Math.max(dataBounds.rowUp, viewBounds.rowUp),
    Math.max(dataBounds.colLeft, viewBounds.colLeft),
    dataBounds,
    radarRect,
  );
  const bottomRight = worldToRadar(
    Math.min(dataBounds.rowDown, viewBounds.rowDown),
    Math.min(dataBounds.colRight, viewBounds.colRight),
    dataBounds,
    radarRect,
  );
  const x = clamp(topLeft.x, radarRect.x, radarRect.x + radarRect.width);
  const y = clamp(topLeft.y, radarRect.y, radarRect.y + radarRect.height);
  return {
    x,
    y,
    width: Math.max(
      2,
      clamp(bottomRight.x, x, radarRect.x + radarRect.width) - x,
    ),
    height: Math.max(
      2,
      clamp(bottomRight.y, y, radarRect.y + radarRect.height) - y,
    ),
  };
}

export function drawOverviewMap(canvas, buckets, options = {}) {
  const { context, width, height } = prepareCanvas(canvas);
  const bounds = options.bounds;
  context.fillStyle = "#03091a";
  context.fillRect(0, 0, width, height);
  drawGrid(context, width, height);
  const hitAreas = [];
  for (const bucket of buckets || []) {
    const rect = bucketRect(bucket, bounds, width, height);
    const centerX = rect.x + rect.width / 2;
    const centerY = rect.y + rect.height / 2;
    const radius = Math.max(
      5,
      Math.min(30, 5 + Math.sqrt(Number(bucket.tileCount || 0)) * 2.4),
    );
    const color = bucketColor(bucket);
    context.save();
    context.globalAlpha = 0.14;
    context.fillStyle = color;
    context.beginPath();
    context.arc(centerX, centerY, radius * 2.1, 0, Math.PI * 2);
    context.fill();
    context.globalAlpha = 0.28;
    context.beginPath();
    context.arc(centerX, centerY, radius * 1.45, 0, Math.PI * 2);
    context.fill();
    context.globalAlpha = 0.95;
    context.beginPath();
    context.arc(centerX, centerY, radius, 0, Math.PI * 2);
    context.fill();
    context.fillStyle = "#f4fbff";
    context.font = "10px ui-monospace, monospace";
    context.textAlign = "center";
    context.textBaseline = "middle";
    context.fillText(String(bucket.tileCount || 0), centerX, centerY);
    context.restore();
    hitAreas.push({
      x: centerX - Math.max(radius, 16),
      y: centerY - Math.max(radius, 16),
      width: Math.max(radius, 16) * 2,
      height: Math.max(radius, 16) * 2,
      bucket,
    });
  }
  return { bounds, width, height, hitAreas };
}

export function drawRadar(canvas, buckets, options = {}) {
  const { context, width, height } = prepareCanvas(canvas);
  const dataBounds = options.dataBounds;
  const radarRect = { x: 7, y: 7, width: width - 14, height: height - 14 };
  context.fillStyle = "rgba(3,13,30,.94)";
  context.fillRect(0, 0, width, height);
  context.strokeStyle = "#315b7d";
  context.strokeRect(
    radarRect.x,
    radarRect.y,
    radarRect.width,
    radarRect.height,
  );
  const hitAreas = [];
  if (dataBounds) {
    for (const bucket of buckets || []) {
      const point = worldToRadar(
        (bucket.rowUp + bucket.rowDown) / 2,
        (bucket.colLeft + bucket.colRight) / 2,
        dataBounds,
        radarRect,
      );
      const color = bucketColor(bucket);
      const radius = Math.max(2, Math.min(6, Math.sqrt(bucket.tileCount || 1)));
      context.fillStyle = color;
      context.beginPath();
      context.arc(point.x, point.y, radius, 0, Math.PI * 2);
      context.fill();
      hitAreas.push({
        x: point.x - 8,
        y: point.y - 8,
        width: 16,
        height: 16,
        bucket,
      });
    }
    const viewport = radarViewportRect(
      options.viewBounds,
      dataBounds,
      radarRect,
    );
    context.fillStyle = "rgba(56,189,248,.09)";
    context.fillRect(viewport.x, viewport.y, viewport.width, viewport.height);
    context.strokeStyle = "#67e5ff";
    context.lineWidth = 2;
    context.strokeRect(
      viewport.x,
      viewport.y,
      viewport.width,
      viewport.height,
    );
    if (options.selectedWid) {
      const row = Math.floor(Number(options.selectedWid) / 10000);
      const col = Number(options.selectedWid) % 10000;
      const selected = worldToRadar(row, col, dataBounds, radarRect);
      context.strokeStyle = "#f4fbff";
      context.lineWidth = 1;
      context.beginPath();
      context.moveTo(selected.x - 5, selected.y);
      context.lineTo(selected.x + 5, selected.y);
      context.moveTo(selected.x, selected.y - 5);
      context.lineTo(selected.x, selected.y + 5);
      context.stroke();
    }
    context.lineWidth = 1;
    return { radarRect, viewport, hitAreas, dataBounds };
  }
  return { radarRect, viewport: null, hitAreas, dataBounds: null };
}

export function hitTestBucket(pointX, pointY, renderState) {
  return renderState?.hitAreas?.find((item) =>
    pointX >= item.x
    && pointX < item.x + item.width
    && pointY >= item.y
    && pointY < item.y + item.height
  )?.bucket || null;
}

function prepareCanvas(canvas) {
  const context = canvas.getContext("2d");
  const ratio = Math.min(2, globalThis.window?.devicePixelRatio || 1);
  const width = canvas.clientWidth || 800;
  const height = canvas.clientHeight || 520;
  canvas.width = Math.round(width * ratio);
  canvas.height = Math.round(height * ratio);
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
  context.clearRect(0, 0, width, height);
  return { context, width, height };
}

function bucketRect(bucket, bounds, width, height) {
  const rows = Math.max(1, bounds.rowDown - bounds.rowUp + 1);
  const cols = Math.max(1, bounds.colRight - bounds.colLeft + 1);
  return {
    x: ((bucket.colLeft - bounds.colLeft) / cols) * width,
    y: ((bucket.rowUp - bounds.rowUp) / rows) * height,
    width: Math.max(1, ((bucket.colRight - bucket.colLeft + 1) / cols) * width),
    height: Math.max(1, ((bucket.rowDown - bucket.rowUp + 1) / rows) * height),
  };
}

function bucketColor(bucket) {
  if (Number(bucket.riskMax || 0) >= 70 || bucket.enemyCount > 0) {
    return "#ff536e";
  }
  if (Number(bucket.changeCount || 0) > 0) return "#f6bd45";
  if (Number(bucket.selfCount || 0) + Number(bucket.allyCount || 0) > 0) {
    return "#21d49a";
  }
  return "#38bdf8";
}

function drawGrid(context, width, height) {
  context.strokeStyle = "rgba(39,83,120,.13)";
  context.lineWidth = 1;
  for (let x = 0; x <= width; x += 20) {
    context.beginPath();
    context.moveTo(x, 0);
    context.lineTo(x, height);
    context.stroke();
  }
  for (let y = 0; y <= height; y += 20) {
    context.beginPath();
    context.moveTo(0, y);
    context.lineTo(width, y);
    context.stroke();
  }
}

function clamp(value, minimum, maximum) {
  return Math.min(maximum, Math.max(minimum, value));
}

if (typeof window !== "undefined") {
  window.IntelligenceMapOverview = Object.freeze({
    semanticMapMode,
    bucketGridForBounds,
    worldToRadar,
    radarToWorld,
    radarViewportRect,
    drawOverviewMap,
    drawRadar,
    hitTestBucket,
  });
}

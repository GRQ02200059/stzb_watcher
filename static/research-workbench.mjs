import {
  buildConfigSkillChain,
  findSkillChainNode,
  groupSkillChainByPhase,
} from "./research-skill-chain.mjs";
import {
  createResearchTemplateStore,
  serializeResearchTemplate,
} from "./research-templates.mjs";

const POSITION_COUNT = 3;
const SKILL_SLOT_COUNT = 2;

function clamp(value, minimum, maximum, fallback) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.min(maximum, Math.max(minimum, Math.trunc(number)));
}

function normalizeId(value, label) {
  if (value === undefined || value === null) return 0;
  if (!Number.isInteger(value) || value < 0) {
    throw new TypeError(`${label} must be zero or a positive integer`);
  }
  return value;
}

function normalizeSkills(value) {
  const skills = Array.isArray(value) ? value : [];
  return Array.from(
    { length: SKILL_SLOT_COUNT },
    (_, slot) => normalizeId(skills[slot], "skill id"),
  );
}

function normalizeHero(value, position) {
  const hero = value && typeof value === "object" ? value : {};
  return {
    id: normalizeId(hero.id, "hero id"),
    position,
    level: clamp(hero.level, 1, 50, 40),
    up: clamp(hero.up, 0, 9, 0),
    equip_skills: normalizeSkills(hero.equip_skills),
  };
}

function assertPosition(position) {
  if (!Number.isInteger(position) || position < 0 || position >= POSITION_COUNT) {
    throw new RangeError("invalid research lineup position");
  }
}

function assertSkillSlot(slot) {
  if (!Number.isInteger(slot) || slot < 0 || slot >= SKILL_SLOT_COUNT) {
    throw new RangeError("invalid research skill slot");
  }
}

export function normalizeResearchLineup(value) {
  const lineup = value && typeof value === "object" ? value : {};
  const heroes = Array.isArray(lineup.heroes) ? lineup.heroes : [];
  return {
    schemaVersion: 1,
    name: typeof lineup.name === "string" ? lineup.name : "",
    morale: clamp(lineup.morale, 0, 200, 100),
    heroes: Array.from(
      { length: POSITION_COUNT },
      (_, position) => normalizeHero(heroes[position], position),
    ),
  };
}

export function validateResearchLineup(value) {
  const lineup = normalizeResearchLineup(value);
  const heroIds = lineup.heroes.map((hero) => hero.id);
  const complete = heroIds.every((id) => id > 0);
  const selectedIds = heroIds.filter((id) => id > 0);
  const errors = [];
  if (new Set(selectedIds).size !== selectedIds.length) {
    errors.push("武将不能重复");
  }
  return {
    valid: complete && errors.length === 0,
    complete,
    errors,
  };
}

export function swapResearchPositions(lineup, left, right) {
  assertPosition(left);
  assertPosition(right);
  const result = normalizeResearchLineup(lineup);
  [result.heroes[left], result.heroes[right]] = [
    result.heroes[right],
    result.heroes[left],
  ];
  result.heroes[left].position = left;
  result.heroes[right].position = right;
  return result;
}

export function replaceResearchHero(lineup, position, hero) {
  assertPosition(position);
  const result = normalizeResearchLineup(lineup);
  result.heroes[position] = normalizeHero(
    { ...hero, equip_skills: [0, 0] },
    position,
  );
  return result;
}

export function replaceResearchSkill(lineup, position, slot, skillId) {
  assertPosition(position);
  assertSkillSlot(slot);
  const result = normalizeResearchLineup(lineup);
  result.heroes[position].equip_skills[slot] = normalizeId(skillId, "skill id");
  return result;
}

const MATCHUP_LABELS = {
  insufficient: "证据不足",
  verify: "谨慎验证",
  "history-advantage": "历史占优",
  "history-disadvantage": "历史劣势",
  "simulation-conflict": "模拟分歧",
};

function matchupState(key, reasons) {
  return {
    key,
    label: MATCHUP_LABELS[key],
    reasons: [...reasons],
  };
}

function validWinRate(value) {
  return Number.isFinite(value) && value >= 0 && value <= 100;
}

export function deriveMatchupState(history, simulation, completeness) {
  if (completeness?.left !== true || completeness?.right !== true) {
    return matchupState("verify", ["对阵双方阵容尚未完整"]);
  }

  const sampleSize = Number(history?.battleStats?.sampleSize) || 0;
  const historyWinRate = history?.battleStats?.winRate;
  const simulationWinRate = simulation?.winRate;
  const hasSimulation = validWinRate(simulationWinRate);

  if (sampleSize < 10) {
    return matchupState("insufficient", [`历史样本仅 ${sampleSize} 场`]);
  }
  if (!validWinRate(historyWinRate)) {
    return matchupState("verify", ["历史胜率数据无效"]);
  }
  if (
    historyWinRate >= 60
    && (!hasSimulation || simulationWinRate >= 50)
  ) {
    return matchupState("history-advantage", [
      `历史胜率 ${historyWinRate}%`,
      ...(hasSimulation ? [`模拟胜率 ${simulationWinRate}%`] : []),
    ]);
  }
  if (
    historyWinRate <= 40
    && (!hasSimulation || simulationWinRate <= 50)
  ) {
    return matchupState("history-disadvantage", [
      `历史胜率 ${historyWinRate}%`,
      ...(hasSimulation ? [`模拟胜率 ${simulationWinRate}%`] : []),
    ]);
  }
  if (
    hasSimulation
    && Math.abs(historyWinRate - simulationWinRate) >= 20
  ) {
    return matchupState("simulation-conflict", [
      `历史胜率 ${historyWinRate}%`,
      `模拟胜率 ${simulationWinRate}%`,
    ]);
  }
  return matchupState("verify", ["历史与模拟证据均未形成明确方向"]);
}

const RESEARCH_MODES = new Set(["lab", "matchup", "chain"]);
const LIBRARY_KINDS = new Set(["hero", "skill", "card-pack"]);

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function lineupKey(lineup) {
  return normalizeResearchLineup(lineup).heroes
    .map((hero) => hero.id)
    .join(".");
}

export function createRequestOwnerRegistry() {
  const owners = new Map();
  let sequence = 0;

  function acquire(surface) {
    const token = ++sequence;
    owners.set(surface, token);
    return token;
  }

  function isCurrent(surface, token) {
    return owners.get(surface) === token;
  }

  function release(surface, token) {
    if (!isCurrent(surface, token)) return false;
    owners.delete(surface);
    return true;
  }

  function current(surface) {
    return owners.get(surface) ?? null;
  }

  return {
    acquire,
    isCurrent,
    release,
    current,
  };
}

export function createResearchContextSnapshot(state, options = {}) {
  const side = options.side === "right" ? "right" : "left";
  const leftKey = lineupKey(state.lineup);
  const rightKey = lineupKey(state.opponent);
  return {
    modelRevision: Number(state.modelRevision) || 0,
    side,
    position: Number(state.selectedPosition) || 0,
    skillSlot: state.selectedSkillSlot
      ? {
          position: Number(state.selectedSkillSlot.position) || 0,
          slot: Number(state.selectedSkillSlot.slot) || 0,
        }
      : null,
    leftKey,
    rightKey,
    currentKey: side === "right" ? rightKey : leftKey,
  };
}

export function isResearchContextSnapshotCurrent(
  snapshot,
  state,
  options = {},
) {
  if (!snapshot) return false;
  const current = createResearchContextSnapshot(state, {
    side: options.side ?? snapshot.side,
  });
  const selectionCurrent = options.compareSelection === false || (
    snapshot.side === current.side
    && snapshot.position === current.position
    && snapshot.skillSlot?.position === current.skillSlot?.position
    && snapshot.skillSlot?.slot === current.skillSlot?.slot
  );
  return (
    snapshot.modelRevision === current.modelRevision
    && snapshot.leftKey === current.leftKey
    && snapshot.rightKey === current.rightKey
    && snapshot.currentKey === current.currentKey
    && selectionCurrent
  );
}

function lineupFromHistorical(detail) {
  if (detail?.simulationLink?.lineup) {
    return normalizeResearchLineup(detail.simulationLink.lineup);
  }
  const heroes = Array.isArray(detail?.configFacts?.heroes)
    ? detail.configFacts.heroes
    : [];
  return normalizeResearchLineup({
    heroes: heroes.map((hero) => ({
      id: Number(hero?.heroId) || 0,
      level: Number(hero?.level) || 40,
    })),
  });
}

function simulationWinRate(detail) {
  const response = detail?.response || {};
  const value = response.blue_rate ?? response.winRate;
  return validWinRate(value) ? value : null;
}

function initialSkillId(detail) {
  return Number(
    detail?.initialSkill?.skill_id
    ?? detail?.hero?.skill_init
    ?? 0,
  );
}

function selectedSimulation(state) {
  return state.simulationByLineupKey.get(lineupKey(state.lineup)) || null;
}

function formatTime(timestamp) {
  const value = Number(timestamp);
  if (!Number.isFinite(value) || value <= 0) return "暂无";
  return new Date(value * 1000).toLocaleString("zh-CN", { hour12: false });
}

function confidenceName(label) {
  return ({ low: "低", medium: "中", high: "高" })[label] || label || "暂无";
}

function detailTarget(documentRef) {
  return (
    documentRef?.getElementById?.("research-detail")
    || documentRef?.getElementById?.("research-evidence-body")
  );
}

function defaultStorage() {
  return {
    getItem() {
      return null;
    },
    setItem() {},
  };
}

export function createResearchWorkbench(options = {}) {
  const documentRef = options.documentRef ?? globalThis.document;
  const windowRef = options.windowRef ?? globalThis.window;
  const fetchJson = options.fetchJson ?? (async () => {
    throw new Error("fetchJson dependency is required");
  });
  const storage = options.storage ?? windowRef?.localStorage ?? defaultStorage();
  const setTimeoutFn = options.setTimeoutFn ?? globalThis.setTimeout;
  const clearTimeoutFn = options.clearTimeoutFn ?? globalThis.clearTimeout;
  const nowFn = options.nowFn ?? Date.now;
  const renderState = options.renderState ?? null;
  const renderRequestState = options.renderRequestState ?? null;
  const buildSimulationChain = options.buildSimulationSkillChain ?? null;
  const onSimulationEvidence = options.onSimulationEvidence ?? null;
  const templateStore = createResearchTemplateStore(storage);
  const pendingHeroDetails = new Map();
  const pendingSkillDetails = new Map();
  const requestOwners = createRequestOwnerRegistry();
  let selectedLineupSide = "left";
  const state = {
    mode: "lab",
    libraryKind: "hero",
    query: "",
    filters: {},
    lineup: normalizeResearchLineup(null),
    opponent: normalizeResearchLineup(null),
    selectedPosition: 0,
    selectedSkillSlot: null,
    selectedEvidence: "history",
    selectedChainNodeId: "",
    activeHistoricalLineup: null,
    matchup: null,
    simulationByLineupKey: new Map(),
    heroDetails: new Map(),
    skillDetails: new Map(),
    modelRevision: 0,
    requestRevision: 0,
    requestRevisions: {
      detail: 0,
      lineup: 0,
      matchup: 0,
      chain: 0,
    },
    libraryRows: [],
    selectedLibraryId: 0,
    selectedLibraryItem: null,
    datasetVersion: "",
    chainNodes: [],
    simulationReturnTab: null,
    error: "",
    loading: false,
    timer: null,
    initialized: false,
  };

  const getElement = (id) => documentRef?.getElementById?.(id) || null;

  function updateActiveControls() {
    documentRef?.querySelectorAll?.("[data-research-mode]")?.forEach((button) => {
      const active = button.dataset.researchMode === state.mode;
      button.classList?.toggle("active", active);
      button.setAttribute?.("aria-selected", String(active));
    });
    documentRef?.querySelectorAll?.("[data-kind]")?.forEach((button) => {
      button.classList?.toggle(
        "active",
        button.dataset.kind === state.libraryKind,
      );
    });
    documentRef?.querySelectorAll?.("[data-evidence]")?.forEach((button) => {
      const active = button.dataset.evidence === state.selectedEvidence;
      button.classList?.toggle("active", active);
      button.setAttribute?.("aria-selected", String(active));
    });
  }

  function renderLibrary() {
    const target = getElement("research-results");
    if (!target) return;
    if (!state.libraryRows.length) {
      target.innerHTML = '<div class="research-empty-inline">没有匹配结果</div>';
      return;
    }
    target.innerHTML = state.libraryRows.map((row) => {
      if (state.libraryKind === "card-pack") {
        const packId = Number(row?.packId) || 0;
        const preview = (row?.heroPreview || [])
          .map((hero) => hero?.name || hero?.heroid)
          .filter(Boolean)
          .join(" / ");
        return `<button class="research-result${state.selectedLibraryId === packId ? " active" : ""}" data-card-pack-id="${packId}"><b>卡包 ${packId}</b><small>${escapeHtml(preview || `${Number(row?.heroCount) || 0} 名武将`)}</small></button>`;
      }
      const hero = state.libraryKind === "hero";
      const id = Number(row?.[hero ? "heroid" : "skill_id"]) || 0;
      const metadata = hero
        ? `${row?.country_name || row?.country || "未知阵营"} · ${row?.quality_name || "未知品质"} · 距离 ${Number(row?.hit_range) || 0}`
        : `类型 ${Number(row?.skill_type) || 0} · 概率 ${Number(row?.probability_init) || 0}% · 准备 ${Number(row?.prepare) || 0}`;
      return `<button class="research-result${state.selectedLibraryId === id ? " active" : ""}" data-research-id="${id}"><b>${escapeHtml(row?.name || id)}</b><small>${escapeHtml(metadata)}</small></button>`;
    }).join("");
  }

  function renderHeroStrip(lineup, side) {
    return `<div class="research-lineup-grid" data-lineup-side="${side}">${lineup.heroes.map((hero, position) => {
      const detail = state.heroDetails.get(hero.id);
      const name = detail?.hero?.name || (hero.id > 0 ? `武将 ${hero.id}` : "空位");
      return `<button class="research-hero-card${state.selectedPosition === position && side === "left" ? " active" : ""}" data-position="${position}" data-side="${side}"><span class="research-hero-portrait">${hero.id > 0 ? escapeHtml(name.slice(0, 1)) : "+"}</span><b>${escapeHtml(name)}</b><small>Lv.${hero.level} · 进阶 ${hero.up}</small></button>`;
    }).join("")}</div>`;
  }

  function renderSkillSlots(hero, position) {
    const heroDetail = state.heroDetails.get(hero.id);
    const initial = heroDetail?.initialSkill;
    const optional = hero.equip_skills.map((skillId, slot) => {
      const detail = state.skillDetails.get(skillId);
      const selected = (
        state.selectedSkillSlot?.position === position
        && state.selectedSkillSlot?.slot === slot
      );
      return `<button class="research-skill-slot${selected ? " active" : ""}" data-skill-position="${position}" data-skill-slot="${slot}"><span>可选战法 ${slot + 1}</span><b>${escapeHtml(detail?.skill?.name || (skillId > 0 ? `战法 ${skillId}` : "空槽"))}</b></button>`;
    }).join("");
    return `<div class="research-hero-skills"><div class="research-skill-slot is-initial"><span>初始战法</span><b>${escapeHtml(initial?.name || (hero.id > 0 ? "加载配置后显示" : "空"))}</b></div>${optional}</div>`;
  }

  function renderLabStage() {
    const validation = validateResearchLineup(state.lineup);
    const historical = state.activeHistoricalLineup;
    return `<div class="research-stage-head"><div><span class="cc-panel-kicker">LINEUP LAB</span><h3>阵容实验室</h3></div><span class="hud-status-chip">${escapeHtml(lineupKey(state.lineup))}</span></div>
      <div class="research-lineup-grid">${state.lineup.heroes.map((hero, position) => {
        const detail = state.heroDetails.get(hero.id);
        const name = detail?.hero?.name || (hero.id > 0 ? `武将 ${hero.id}` : "选择武将");
        return `<article class="research-hero-card${state.selectedPosition === position ? " active" : ""}" data-position="${position}">
          <button class="research-hero-select" data-select-position="${position}" type="button"><span class="research-hero-portrait">${hero.id > 0 ? escapeHtml(name.slice(0, 1)) : "+"}</span><b>${escapeHtml(name)}</b><small>位置 ${position + 1}</small></button>
          <div class="research-hero-config"><label>等级<input data-hero-field="level" data-position="${position}" type="number" min="1" max="50" value="${hero.level}"></label><label>进阶<input data-hero-field="up" data-position="${position}" type="number" min="0" max="9" value="${hero.up}"></label></div>
          ${renderSkillSlots(hero, position)}
        </article>`;
      }).join("")}</div>
      <div class="research-lineup-controls"><label>士气<input id="research-lineup-morale" type="number" min="0" max="200" value="${state.lineup.morale}"></label><button type="button" data-swap-left="0" data-swap-right="1">交换 1 / 2</button><button type="button" data-swap-left="1" data-swap-right="2">交换 2 / 3</button></div>
      <div class="research-stage-summary">
        <div><span>配置完整性</span><b>${validation.valid ? "可验证" : validation.errors.join("；") || "尚未完整"}</b></div>
        <div><span>历史摘要</span><b>${historical ? `${Number(historical.battleStats?.sampleSize) || 0} 场 · 胜率 ${Number(historical.battleStats?.winRate) || 0}%` : "尚未选择历史阵容"}</b></div>
        <div class="research-stage-actions"><button class="btn btn-ghost" type="button" data-research-action="open-templates">阵容模板</button><button class="btn btn-primary" type="button" data-research-action="send-simulator" ${validation.valid ? "" : "disabled"}>${selectedSimulation(state) ? "重新送入模拟器" : "送入模拟器验证"}</button></div>
      </div>`;
  }

  function renderMatchupStage() {
    const leftComplete = validateResearchLineup(state.lineup).complete;
    const rightComplete = validateResearchLineup(state.opponent).complete;
    const simulation = selectedSimulation(state);
    const evidence = deriveMatchupState(
      state.matchup,
      simulation ? { winRate: simulationWinRate(simulation) } : null,
      { left: leftComplete, right: rightComplete },
    );
    const stats = state.matchup?.battleStats || {};
    const alternatives = state.libraryRows
      .filter((row) => row?.key)
      .slice(0, 3);
    const common = state.activeHistoricalLineup?.battleStats?.commonOpponents || [];
    return `<div class="research-matchup-stage">
      <section><div class="research-stage-head"><h3>我方阵容</h3><span>${escapeHtml(lineupKey(state.lineup))}</span></div>${renderHeroStrip(state.lineup, "left")}</section>
      <div class="research-matchup-versus">VS</div>
      <section><div class="research-stage-head"><h3>对手阵容</h3><span>${escapeHtml(lineupKey(state.opponent))}</span></div>${renderHeroStrip(state.opponent, "right")}</section>
      <div class="research-matchup-summary">
        <span class="evidence-badge evidence-stat">${escapeHtml(evidence.label)}</span>
        <b>${Number(stats.sampleSize) || 0} 场 · 历史胜率 ${Number(stats.winRate) || 0}%</b>
        <small>最近战斗 ${escapeHtml(formatTime(stats.latestBattleTime))}</small>
        <p>${evidence.reasons.map(escapeHtml).join("；")}</p>
        <p>${simulation ? `模拟胜率 ${escapeHtml(simulationWinRate(simulation) ?? "未知")}%` : "尚无匹配模拟结果"}</p>
      </div>
      <div class="research-matchup-related"><section><h4>常见对手</h4>${common.length ? common.slice(0, 4).map((row) => `<button data-lineup-key="${escapeHtml(row.key)}">${escapeHtml(row.key)} · ${Number(row.sampleSize) || 0} 场</button>`).join("") : "<span>暂无</span>"}</section><section><h4>备选阵容</h4>${alternatives.length ? alternatives.map((row) => `<button data-lineup-key="${escapeHtml(row.key)}">${escapeHtml(row.key)}</button>`).join("") : "<span>暂无</span>"}</section></div>
    </div>`;
  }

  function renderChainStage() {
    const groups = groupSkillChainByPhase(state.chainNodes);
    const selected = findSkillChainNode(
      state.chainNodes,
      state.selectedChainNodeId,
    );
    return `<div class="research-stage-head"><div><span class="cc-panel-kicker">${selectedSimulation(state) ? "SIMULATION CHAIN" : "CONFIG CHAIN"}</span><h3>战法执行链</h3></div><span>${state.chainNodes.length} 节点</span></div>
      <div class="research-chain-phases">${groups.map((group) => `<a href="#research-chain-${escapeHtml(group.key)}">${escapeHtml(group.key)}</a>`).join("") || "<span>等待完整阵容配置</span>"}</div>
      <div class="research-skill-chain">${groups.map((group) => `<section id="research-chain-${escapeHtml(group.key)}"><h4>${escapeHtml(group.key)}</h4><div>${group.nodes.map((node) => `<button class="${node.nodeId === state.selectedChainNodeId ? "active" : ""}${node.warning || node.unresolvedDescription ? " is-warning" : ""}" data-chain-node-id="${escapeHtml(node.nodeId)}"><span>${escapeHtml(node.evidenceClass)}</span><b>${escapeHtml(node.skillName || node.type || node.nodeId)}</b><small>${node.warning || node.unresolvedDescription ? "存在未支持效果" : `阶段 ${escapeHtml(node.phase)}`}</small></button>`).join("")}</div></section>`).join("")}</div>
      ${selected?.warning || selected?.unresolvedDescription ? '<div class="research-notice">当前节点包含尚未支持或未解析的效果，请结合原始证据谨慎判断。</div>' : ""}`;
  }

  function renderStage() {
    const target = (
      getElement("research-stage-content")
      || getElement("research-stage")
    );
    if (!target) return;
    if (state.mode === "matchup") {
      target.innerHTML = renderMatchupStage();
    } else if (state.mode === "chain") {
      target.innerHTML = renderChainStage();
    } else {
      target.innerHTML = renderLabStage();
    }
  }

  function renderSelectedLibraryEvidence() {
    const data = state.selectedLibraryItem;
    if (!data) return '<div class="research-empty-inline">选择目录项目查看配置事实</div>';
    if (state.libraryKind === "card-pack") {
      const distribution = data.countryDistribution || [];
      return `<div class="research-title"><div><span class="cc-panel-kicker">CARD PACK ${Number(data.packId) || 0}</span><h3>卡包武将池</h3></div><span class="evidence-badge evidence-config">CONFIG FACT</span></div><p>配置版本 ${escapeHtml(data.datasetVersion || "")} · ${Number(data.heroCount) || 0} 名武将</p><div class="research-details">${distribution.map((row) => `<div class="research-detail-row"><b>${escapeHtml(row.name)}</b><small>${Number(row.count) || 0}</small></div>`).join("")}</div>`;
    }
    if (state.libraryKind === "hero") {
      const hero = data.hero || {};
      return `<div class="research-title"><div><span class="cc-panel-kicker">HERO ${Number(hero.heroid) || 0}</span><h3>${escapeHtml(hero.name || "武将")}</h3></div><span class="evidence-badge evidence-config">CONFIG FACT</span></div><div class="research-grid"><div class="research-card"><span>阵营</span><b>${escapeHtml(hero.country_name || hero.country || "-")}</b></div><div class="research-card"><span>品质</span><b>${escapeHtml(hero.quality_name || hero.quality || "-")}</b></div><div class="research-card"><span>初始战法</span><b>${escapeHtml(data.initialSkill?.name || "-")}</b></div></div>`;
    }
    const skill = data.skill || {};
    return `<div class="research-title"><div><span class="cc-panel-kicker">SKILL ${Number(skill.skill_id) || 0}</span><h3>${escapeHtml(skill.name || "战法")}</h3></div><span class="evidence-badge evidence-config">CONFIG FACT</span></div><p>${escapeHtml(skill.description || skill.brief_description || "暂无描述")}</p><div class="research-details">${(data.details || []).map((row) => `<div class="research-detail-row"><b>${escapeHtml(row.effect_name || row.effect?.name || `效果 ${Number(row.effect_id) || 0}`)}</b><small>detail ${Number(row.detail_id) || 0} · effect ${Number(row.effect_id) || 0}</small></div>`).join("")}</div>`;
  }

  function renderEvidence() {
    const target = detailTarget(documentRef);
    if (!target) return;
    if (state.mode === "chain") {
      const node = findSkillChainNode(
        state.chainNodes,
        state.selectedChainNodeId,
      );
      target.innerHTML = node
        ? `<div class="research-title"><div><span class="cc-panel-kicker">${escapeHtml(node.evidenceClass)}</span><h3>${escapeHtml(node.skillName || node.type || node.nodeId)}</h3></div></div><div class="research-grid"><div class="research-card"><span>阶段</span><b>${escapeHtml(node.phase)}</b></div><div class="research-card"><span>战法</span><b>${Number(node.skillId) || 0}</b></div><div class="research-card"><span>效果</span><b>${Number(node.effectId) || 0}</b></div></div><p>${escapeHtml(node.targetDescription || (node.warning ? "未支持的模拟事件" : "选择节点查看证据"))}</p>`
        : '<div class="research-empty-inline">选择执行链节点查看证据</div>';
      return;
    }
    if (state.selectedEvidence === "config") {
      target.innerHTML = renderSelectedLibraryEvidence();
      return;
    }
    if (state.selectedEvidence === "simulation") {
      const simulation = selectedSimulation(state);
      target.innerHTML = simulation
        ? `<div class="research-title"><h3>模拟验证</h3><span class="evidence-badge evidence-sim">SIMULATION</span></div><p>重复 ${Number(simulation.repeat) || 1} 次 · 攻方胜率 ${escapeHtml(simulationWinRate(simulation) ?? "未知")}%</p>${state.simulationReturnTab === 34 ? '<button class="btn btn-ghost" type="button" data-research-action="return-research">返回研究工作台</button>' : ""}`
        : '<div class="research-empty-inline">当前阵容尚无模拟结果</div>';
      return;
    }
    const historical = state.activeHistoricalLineup;
    target.innerHTML = historical
      ? `<div class="research-title"><div><span class="cc-panel-kicker">${escapeHtml(historical.key)}</span><h3>历史阵容摘要</h3></div><span class="evidence-badge evidence-stat">BATTLE STAT</span></div><div class="research-grid"><div class="research-card"><span>样本</span><b>${Number(historical.battleStats?.sampleSize) || 0}</b></div><div class="research-card"><span>胜率</span><b>${Number(historical.battleStats?.winRate) || 0}%</b></div><div class="research-card"><span>置信度</span><b>${escapeHtml(confidenceName(historical.confidence?.label))}</b></div></div><p>${escapeHtml(historical.confidence?.notice || "历史统计不代表确定性克制。")}</p>`
      : '<div class="research-empty-inline">选择历史阵容查看统计证据</div>';
  }

  function renderError() {
    const target = getElement("research-workbench-error");
    if (!target) return;
    target.textContent = state.error;
    target.hidden = !state.error;
  }

  function renderLoaderState(kind, message, options = {}) {
    const target = getElement("research-results");
    if (!target || typeof renderState !== "function") return null;
    return renderState(target, {
      kind,
      message,
      replace: options.replace ?? !state.libraryRows.length,
      actionLabel: kind === "error" ? "重试" : undefined,
      action: kind === "error" ? () => search() : undefined,
    });
  }

  function updateRequestState(surface, model) {
    if (typeof renderRequestState !== "function") return null;
    return renderRequestState(surface, {
      kind: model.kind,
      message: model.message || "",
      replace: Boolean(model.replace),
      busy: Boolean(model.busy),
      ownerToken: model.ownerToken,
      actionLabel: model.actionLabel || "",
      action: model.action,
    });
  }

  function beginRequest(surface, model) {
    const ownerToken = requestOwners.acquire(surface);
    updateRequestState(surface, {
      ...model,
      ownerToken,
    });
    return ownerToken;
  }

  function finishRequest(surface, ownerToken, terminalRendered) {
    const released = requestOwners.release(surface, ownerToken);
    if (released && !terminalRendered) {
      updateRequestState(surface, {
        kind: "success",
        replace: false,
        busy: false,
        ownerToken,
      });
    }
  }

  function invalidateResearchModel({ clearDerived = false } = {}) {
    state.modelRevision += 1;
    state.requestRevisions.matchup += 1;
    state.requestRevisions.chain += 1;
    if (clearDerived) {
      state.matchup = null;
      state.chainNodes = [];
    }
    return state.modelRevision;
  }

  function render() {
    updateActiveControls();
    renderLibrary();
    renderStage();
    renderEvidence();
    renderError();
  }

  function setLibraryKind(kind) {
    if (!LIBRARY_KINDS.has(kind)) {
      throw new RangeError("invalid research library kind");
    }
    state.libraryKind = kind;
    state.selectedLibraryId = 0;
    state.selectedLibraryItem = null;
    state.libraryRows = [];
    state.error = "";
    const input = getElement("research-search");
    if (input) {
      input.placeholder = kind === "card-pack"
        ? "搜索卡包 ID 或武将名称…"
        : "搜索名称或 ID…";
    }
    render();
    return state;
  }

  async function search(query = state.query) {
    state.query = String(query ?? "").trim();
    const revision = ++state.requestRevision;
    const hasContent = state.libraryRows.length > 0;
    state.loading = true;
    renderLoaderState(
      hasContent ? "refreshing" : "loading",
      hasContent ? "正在刷新研究目录…" : "正在加载研究目录…",
      { replace: !hasContent },
    );
    const endpoint = state.libraryKind === "hero"
      ? "/api/intelligence/heroes"
      : state.libraryKind === "skill"
        ? "/api/intelligence/skills"
        : "/api/intelligence/card-packs";
    try {
      const data = await fetchJson(
        `${endpoint}?q=${encodeURIComponent(state.query)}&page=1&size=${state.libraryKind === "card-pack" ? 80 : 60}`,
      );
      if (!data?.ok) throw new Error(data?.error || "目录加载失败");
      if (revision !== state.requestRevision) return null;
      state.libraryRows = Array.isArray(data.rows) ? data.rows : [];
      state.datasetVersion = String(data.datasetVersion || "");
      state.error = "";
      render();
      return data;
    } catch (error) {
      if (revision === state.requestRevision) {
        state.error = error?.message || "目录加载失败";
        renderError();
        renderLoaderState("error", state.error, { replace: false });
      }
      throw error;
    } finally {
      if (revision === state.requestRevision) {
        state.loading = false;
        const target = getElement("research-results");
        target?.removeAttribute?.("aria-busy");
      }
    }
  }

  async function openDetail(kind, id) {
    const numericId = Number(id);
    if (!Number.isInteger(numericId) || numericId <= 0) {
      throw new TypeError("research detail id must be a positive integer");
    }
    const revision = ++state.requestRevisions.detail;
    const contextSnapshot = createResearchContextSnapshot(state, {
      side: selectedLineupSide,
    });
    const hadContent = Boolean(state.selectedLibraryItem);
    const endpoint = kind === "hero"
      ? `/api/intelligence/heroes/${numericId}`
      : kind === "skill"
        ? `/api/intelligence/skills/${numericId}`
        : `/api/intelligence/card-packs/${numericId}`;
    const retry = () => openDetail(kind, numericId);
    const ownerToken = beginRequest("detail", {
      kind: hadContent ? "refreshing" : "loading",
      message: hadContent
        ? "正在刷新研究详情…"
        : "正在加载研究详情…",
      replace: !hadContent,
      busy: true,
    });
    let terminalRendered = false;
    try {
      const data = await fetchJson(endpoint);
      if (!data?.ok) throw new Error(data?.error || "详情加载失败");
      if (
        revision !== state.requestRevisions.detail
        || !isResearchContextSnapshotCurrent(
          contextSnapshot,
          state,
          { side: selectedLineupSide },
        )
      ) return null;
      const hasDetail = kind === "hero"
        ? Boolean(data.hero)
        : kind === "skill"
          ? Boolean(data.skill)
          : Number(data.packId) > 0;
      if (!hasDetail) {
        state.selectedLibraryId = 0;
        state.selectedLibraryItem = null;
        state.error = "";
        render();
        updateRequestState("detail", {
          kind: "empty",
          message: "暂无研究详情",
          replace: true,
          busy: false,
          ownerToken,
        });
        terminalRendered = true;
        return data;
      }
      state.libraryKind = kind;
      state.selectedLibraryId = numericId;
      state.selectedLibraryItem = data;
      state.error = "";
      if (kind === "card-pack") {
        state.selectedEvidence = "config";
      }
      if (kind === "hero") {
        state.heroDetails.set(numericId, data);
        const targetLineup = selectedLineupSide === "right"
          ? state.opponent
          : state.lineup;
        const updatedLineup = replaceResearchHero(
          targetLineup,
          state.selectedPosition,
          {
            id: numericId,
            level: targetLineup.heroes[state.selectedPosition].level,
            up: targetLineup.heroes[state.selectedPosition].up,
          },
        );
        if (selectedLineupSide === "right") {
          state.opponent = updatedLineup;
        } else {
          state.lineup = updatedLineup;
        }
        invalidateResearchModel();
      } else if (kind === "skill" && state.selectedSkillSlot) {
        state.skillDetails.set(numericId, data);
        state.lineup = replaceResearchSkill(
          state.lineup,
          state.selectedSkillSlot.position,
          state.selectedSkillSlot.slot,
          numericId,
        );
        invalidateResearchModel();
      }
      render();
      updateRequestState("detail", {
        kind: "success",
        replace: false,
        busy: false,
        ownerToken,
      });
      terminalRendered = true;
      return data;
    } catch (error) {
      if (
        revision === state.requestRevisions.detail
        && isResearchContextSnapshotCurrent(
          contextSnapshot,
          state,
          { side: selectedLineupSide },
        )
      ) {
        state.error = error?.message || "详情加载失败";
        renderError();
        updateRequestState("detail", {
          kind: "error",
          message: state.error,
          replace: !hadContent,
          busy: false,
          actionLabel: "重试",
          action: retry,
          ownerToken,
        });
        terminalRendered = true;
      }
      throw error;
    } finally {
      finishRequest("detail", ownerToken, terminalRendered);
    }
  }

  const openHero = (id) => openDetail("hero", id);
  const openSkill = (id) => openDetail("skill", id);
  const openCardPack = (id) => openDetail("card-pack", id);

  async function openLineup(key) {
    const lineupRequestKey = String(key);
    const revision = ++state.requestRevisions.lineup;
    const contextSnapshot = createResearchContextSnapshot(state, {
      side: "left",
    });
    const hadContent = Boolean(state.activeHistoricalLineup);
    const retry = () => openLineup(lineupRequestKey);
    const ownerToken = beginRequest("lineup", {
      kind: hadContent ? "refreshing" : "loading",
      message: hadContent
        ? "正在刷新历史阵容…"
        : "正在加载历史阵容…",
      replace: !hadContent,
      busy: true,
    });
    let terminalRendered = false;
    try {
      const data = await fetchJson(
        `/api/intelligence/lineups/${encodeURIComponent(lineupRequestKey)}`,
      );
      if (!data?.ok) throw new Error(data?.error || "历史阵容加载失败");
      if (
        revision !== state.requestRevisions.lineup
        || !isResearchContextSnapshotCurrent(
          contextSnapshot,
          state,
          { side: "left", compareSelection: false },
        )
      ) return null;
      const loadedLineup = lineupFromHistorical(data);
      if (!String(data.key || "") || !validateResearchLineup(loadedLineup).complete) {
        state.activeHistoricalLineup = null;
        state.error = "";
        render();
        updateRequestState("lineup", {
          kind: "empty",
          message: "暂无完整历史阵容",
          replace: true,
          busy: false,
          ownerToken,
        });
        terminalRendered = true;
        return data;
      }
      state.activeHistoricalLineup = data;
      state.lineup = loadedLineup;
      invalidateResearchModel();
      state.selectedEvidence = "history";
      state.error = "";
      render();
      updateRequestState("lineup", {
        kind: "success",
        replace: false,
        busy: false,
        ownerToken,
      });
      terminalRendered = true;
      return data;
    } catch (error) {
      if (
        revision === state.requestRevisions.lineup
        && isResearchContextSnapshotCurrent(
          contextSnapshot,
          state,
          { side: "left", compareSelection: false },
        )
      ) {
        state.error = error?.message || "历史阵容加载失败";
        renderError();
        updateRequestState("lineup", {
          kind: "error",
          message: state.error,
          replace: !hadContent,
          busy: false,
          actionLabel: "重试",
          action: retry,
          ownerToken,
        });
        terminalRendered = true;
      }
      throw error;
    } finally {
      finishRequest("lineup", ownerToken, terminalRendered);
    }
  }

  function setLineup(value) {
    invalidateResearchModel({ clearDerived: true });
    state.lineup = normalizeResearchLineup(value);
    state.error = "";
    render();
    return state.lineup;
  }

  function setOpponent(value) {
    invalidateResearchModel({ clearDerived: true });
    state.opponent = normalizeResearchLineup(value);
    state.error = "";
    render();
    return state.opponent;
  }

  function selectPosition(position, side = "left") {
    assertPosition(position);
    invalidateResearchModel();
    selectedLineupSide = side === "right" ? "right" : "left";
    state.selectedPosition = position;
    state.selectedSkillSlot = null;
    render();
    return state;
  }

  async function refreshMatchup() {
    const left = validateResearchLineup(state.lineup);
    const right = validateResearchLineup(state.opponent);
    if (!left.valid || !right.valid) {
      render();
      return null;
    }
    const revision = ++state.requestRevisions.matchup;
    const contextSnapshot = createResearchContextSnapshot(state, {
      side: "left",
    });
    const leftKey = contextSnapshot.leftKey;
    const rightKey = contextSnapshot.rightKey;
    const hadContent = Boolean(state.matchup);
    const retry = () => refreshMatchup();
    const ownerToken = beginRequest("matchup", {
      kind: hadContent ? "refreshing" : "loading",
      message: hadContent
        ? "正在刷新对阵统计…"
        : "正在加载对阵统计…",
      replace: !hadContent,
      busy: true,
    });
    let terminalRendered = false;
    try {
      const data = await fetchJson(
        `/api/intelligence/lineups/${encodeURIComponent(leftKey)}/matchup/${encodeURIComponent(rightKey)}`,
      );
      if (!data?.ok) throw new Error(data?.error || "对阵统计加载失败");
      if (
        revision !== state.requestRevisions.matchup
        || !isResearchContextSnapshotCurrent(
          contextSnapshot,
          state,
          { side: "left", compareSelection: false },
        )
      ) return null;
      state.matchup = data;
      state.error = "";
      render();
      const empty = Number(data?.battleStats?.sampleSize) <= 0;
      updateRequestState("matchup", {
        kind: empty ? "empty" : "success",
        message: empty ? "暂无历史对阵样本" : "",
        replace: !hadContent && empty,
        busy: false,
        ownerToken,
      });
      terminalRendered = true;
      return data;
    } catch (error) {
      if (
        revision === state.requestRevisions.matchup
        && isResearchContextSnapshotCurrent(
          contextSnapshot,
          state,
          { side: "left", compareSelection: false },
        )
      ) {
        state.error = error?.message || "对阵统计加载失败";
        renderError();
        updateRequestState("matchup", {
          kind: "error",
          message: state.error,
          replace: !hadContent,
          busy: false,
          actionLabel: "重试",
          action: retry,
          ownerToken,
        });
        terminalRendered = true;
      }
      throw error;
    } finally {
      finishRequest("matchup", ownerToken, terminalRendered);
    }
  }

  async function cachedHeroDetail(heroId) {
    if (state.heroDetails.has(heroId)) return state.heroDetails.get(heroId);
    if (pendingHeroDetails.has(heroId)) {
      return pendingHeroDetails.get(heroId);
    }
    let request;
    request = (async () => {
      const data = await fetchJson(`/api/intelligence/heroes/${heroId}`);
      if (!data?.ok) throw new Error(data?.error || `武将 ${heroId} 加载失败`);
      state.heroDetails.set(heroId, data);
      return data;
    })().finally(() => {
      if (pendingHeroDetails.get(heroId) === request) {
        pendingHeroDetails.delete(heroId);
      }
    });
    pendingHeroDetails.set(heroId, request);
    return request;
  }

  async function cachedSkillDetail(skillId) {
    if (state.skillDetails.has(skillId)) return state.skillDetails.get(skillId);
    if (pendingSkillDetails.has(skillId)) {
      return pendingSkillDetails.get(skillId);
    }
    let request;
    request = (async () => {
      const data = await fetchJson(`/api/intelligence/skills/${skillId}`);
      if (!data?.ok) throw new Error(data?.error || `战法 ${skillId} 加载失败`);
      state.skillDetails.set(skillId, data);
      return data;
    })().finally(() => {
      if (pendingSkillDetails.get(skillId) === request) {
        pendingSkillDetails.delete(skillId);
      }
    });
    pendingSkillDetails.set(skillId, request);
    return request;
  }

  async function refreshChain() {
    const revision = ++state.requestRevisions.chain;
    const lineupSnapshot = normalizeResearchLineup(state.lineup);
    const contextSnapshot = createResearchContextSnapshot(state, {
      side: "left",
    });
    const hadContent = state.chainNodes.length > 0;
    const retry = () => refreshChain();
    const ownerToken = beginRequest("chain", {
      kind: hadContent ? "refreshing" : "loading",
      message: hadContent
        ? "正在刷新战法执行链…"
        : "正在加载战法执行链…",
      replace: !hadContent,
      busy: true,
    });
    let terminalRendered = false;
    const simulation = selectedSimulation(state);
    if (simulation && typeof buildSimulationChain === "function") {
      try {
        if (
          revision !== state.requestRevisions.chain
          || !isResearchContextSnapshotCurrent(
            contextSnapshot,
            state,
            { side: "left", compareSelection: false },
          )
        ) return null;
        state.chainNodes = buildSimulationChain(simulation) || [];
        state.selectedChainNodeId = (
          state.chainNodes.find(
            (node) => node.nodeId === state.selectedChainNodeId,
          )?.nodeId
          || state.chainNodes[0]?.nodeId
          || ""
        );
        state.error = "";
        render();
        updateRequestState("chain", {
          kind: state.chainNodes.length ? "success" : "empty",
          message: state.chainNodes.length ? "" : "暂无模拟执行链",
          replace: !hadContent && !state.chainNodes.length,
          busy: false,
          ownerToken,
        });
        terminalRendered = true;
        return state.chainNodes;
      } finally {
        finishRequest("chain", ownerToken, terminalRendered);
      }
    }
    const heroIds = [...new Set(
      lineupSnapshot.heroes.map((hero) => hero.id).filter((id) => id > 0),
    )];
    if (!heroIds.length) {
      try {
        if (
          revision !== state.requestRevisions.chain
          || !isResearchContextSnapshotCurrent(
            contextSnapshot,
            state,
            { side: "left", compareSelection: false },
          )
        ) return null;
        state.chainNodes = [];
        state.selectedChainNodeId = "";
        state.error = "";
        render();
        updateRequestState("chain", {
          kind: "empty",
          message: "请先配置研究阵容",
          replace: true,
          busy: false,
          ownerToken,
        });
        terminalRendered = true;
        return state.chainNodes;
      } finally {
        finishRequest("chain", ownerToken, terminalRendered);
      }
    }
    try {
      const heroDetails = await Promise.all(heroIds.map(cachedHeroDetail));
      if (
        revision !== state.requestRevisions.chain
        || !isResearchContextSnapshotCurrent(
          contextSnapshot,
          state,
          { side: "left", compareSelection: false },
        )
      ) return null;
      const heroDetailMap = new Map(
        heroIds.map((heroId, index) => [heroId, heroDetails[index]]),
      );
      const skillIds = new Set();
      for (const hero of lineupSnapshot.heroes) {
        if (hero.id <= 0) continue;
        const heroDetail = heroDetailMap.get(hero.id);
        const initial = initialSkillId(heroDetail);
        if (initial > 0) skillIds.add(initial);
        hero.equip_skills.filter((id) => id > 0).forEach((id) => skillIds.add(id));
      }
      const skillIdList = [...skillIds];
      const skillDetails = await Promise.all(
        skillIdList.map(cachedSkillDetail),
      );
      if (
        revision !== state.requestRevisions.chain
        || !isResearchContextSnapshotCurrent(
          contextSnapshot,
          state,
          { side: "left", compareSelection: false },
        )
      ) return null;
      const skillDetailMap = new Map(
        skillIdList.map((skillId, index) => [skillId, skillDetails[index]]),
      );
      state.chainNodes = buildConfigSkillChain(
        lineupSnapshot,
        heroDetailMap,
        skillDetailMap,
      );
      state.selectedChainNodeId = (
        state.chainNodes.find(
          (node) => node.nodeId === state.selectedChainNodeId,
        )?.nodeId
        || state.chainNodes[0]?.nodeId
        || ""
      );
      state.error = "";
      render();
      updateRequestState("chain", {
        kind: state.chainNodes.length ? "success" : "empty",
        message: state.chainNodes.length ? "" : "暂无可展示的战法执行链",
        replace: !hadContent && !state.chainNodes.length,
        busy: false,
        ownerToken,
      });
      terminalRendered = true;
      return state.chainNodes;
    } catch (error) {
      if (
        revision === state.requestRevisions.chain
        && isResearchContextSnapshotCurrent(
          contextSnapshot,
          state,
          { side: "left", compareSelection: false },
        )
      ) {
        state.error = error?.message || "执行链加载失败";
        renderError();
        updateRequestState("chain", {
          kind: "error",
          message: state.error,
          replace: !hadContent,
          busy: false,
          actionLabel: "重试",
          action: retry,
          ownerToken,
        });
        terminalRendered = true;
      }
      throw error;
    } finally {
      finishRequest("chain", ownerToken, terminalRendered);
    }
  }

  async function setMode(mode) {
    if (!RESEARCH_MODES.has(mode)) {
      throw new RangeError("invalid research mode");
    }
    state.mode = mode;
    state.error = "";
    render();
    if (mode === "matchup") return refreshMatchup();
    if (mode === "chain") return refreshChain();
    return state;
  }

  function getChainNodes() {
    return state.chainNodes;
  }

  async function sendToSimulator(lineup = state.lineup) {
    const selected = normalizeResearchLineup(lineup);
    if (!validateResearchLineup(selected).valid) {
      state.error = "请先完成三名不重复武将的阵容配置";
      renderError();
      return false;
    }
    const simulator = windowRef?.StzbSimulator;
    if (!simulator?.loadLineup) {
      throw new Error("模拟器尚未就绪");
    }
    const navButton = [...(documentRef?.querySelectorAll?.("nav button") || [])]
      .find((button) => String(button.getAttribute?.("onclick")).includes("switchTab(25,"));
    windowRef?.switchTab?.(25, navButton);
    await simulator.loadLineup(selected, {
      camp: "blue",
      source: "intelligence-research",
      lineupKey: lineupKey(selected),
      returnTab: 34,
    });
    return true;
  }

  async function sendHeroToSimulator(id) {
    const selected = normalizeResearchLineup({
      heroes: [
        { id: Number(id), level: 40, up: 5 },
        { id: 0 },
        { id: 0 },
      ],
    });
    const simulator = windowRef?.StzbSimulator;
    if (!simulator?.loadLineup) throw new Error("模拟器尚未就绪");
    await simulator.loadLineup(selected, {
      camp: "blue",
      source: "intelligence-research",
      lineupKey: "",
    });
    return true;
  }

  function loadTemplate(id) {
    const template = templateStore.load(id);
    if (!template) return null;
    invalidateResearchModel({ clearDerived: true });
    state.lineup = normalizeResearchLineup(template.lineup);
    state.activeHistoricalLineup = null;
    render();
    return template;
  }

  function listTemplates() {
    return templateStore.list();
  }

  function saveTemplate(name) {
    const validation = validateResearchLineup(state.lineup);
    if (!validation.valid) {
      throw new Error("请先完成三名不重复武将的阵容配置");
    }
    const template = templateStore.save(name, state.lineup, nowFn());
    state.error = "";
    renderTemplateDialog();
    return template;
  }

  function renameTemplate(id, name) {
    const template = templateStore.rename(id, name, nowFn());
    renderTemplateDialog();
    return template;
  }

  function deleteTemplate(id) {
    const removed = templateStore.remove(id);
    renderTemplateDialog();
    return removed;
  }

  function exportTemplate(id) {
    const template = templateStore.load(id);
    if (!template) throw new Error("阵容模板不存在");
    return serializeResearchTemplate(template);
  }

  function importTemplate(text) {
    const previousLineup = state.lineup;
    try {
      const template = templateStore.import(text);
      state.error = "";
      renderTemplateDialog();
      return template;
    } catch (error) {
      state.lineup = previousLineup;
      state.error = error?.message || "阵容模板导入失败";
      renderError();
      throw error;
    }
  }

  function renderTemplateDialog() {
    const target = getElement("research-template-list");
    if (!target) return;
    const templates = listTemplates();
    target.innerHTML = `<div class="research-template-form">
      <label>模板名称<input id="research-template-name" maxlength="40" value="阵容 ${templates.length + 1}"></label>
      <button class="btn btn-primary" type="button" data-template-action="save">保存当前阵容</button>
    </div>
    <div class="research-template-items">${templates.length
      ? templates.map((template) => `<article class="research-template-item" data-template-id="${escapeHtml(template.id)}">
          <input data-template-name maxlength="40" value="${escapeHtml(template.name)}" aria-label="模板名称">
          <div>
            <button class="btn btn-ghost" type="button" data-template-action="load">载入</button>
            <button class="btn btn-ghost" type="button" data-template-action="rename">重命名</button>
            <button class="btn btn-ghost" type="button" data-template-action="export">导出</button>
            <button class="btn btn-ghost" type="button" data-template-action="delete">删除</button>
          </div>
        </article>`).join("")
      : '<div class="research-empty-inline">尚无本地模板</div>'}</div>
    <label>模板 JSON<textarea id="research-template-json" rows="6" placeholder="粘贴导出的模板 JSON"></textarea></label>
    <button class="btn btn-ghost" type="button" data-template-action="import">导入 JSON</button>`;
  }

  function openTemplateDialog() {
    renderTemplateDialog();
    getElement("research-template-dialog")?.showModal?.();
  }

  function onTemplateDialogClick(event) {
    const action = event.target?.closest?.("[data-template-action]")
      ?.dataset?.templateAction;
    if (!action) return;
    const item = event.target?.closest?.("[data-template-id]");
    const id = item?.dataset?.templateId || "";
    const name = item?.querySelector?.("[data-template-name]")?.value || "";
    try {
      if (action === "save") {
        saveTemplate(getElement("research-template-name")?.value);
      } else if (action === "load") {
        loadTemplate(id);
        getElement("research-template-dialog")?.close?.();
      } else if (action === "rename") {
        renameTemplate(id, name);
      } else if (action === "delete") {
        deleteTemplate(id);
      } else if (action === "export") {
        const textarea = getElement("research-template-json");
        if (textarea) textarea.value = exportTemplate(id);
      } else if (action === "import") {
        importTemplate(getElement("research-template-json")?.value || "");
      }
    } catch (error) {
      state.error = error?.message || "阵容模板操作失败";
      renderError();
    }
  }

  function onSimulationCompleted(event) {
    const detail = event?.detail || {};
    const sourceContext = detail.sourceContext || {};
    if (
      sourceContext.source !== "intelligence-research"
      || !sourceContext.lineupKey
      || sourceContext.lineupKey !== lineupKey(state.lineup)
    ) return;
    state.simulationByLineupKey.set(sourceContext.lineupKey, detail);
    state.simulationReturnTab = Number.isInteger(sourceContext.returnTab)
      ? sourceContext.returnTab
      : null;
    if (typeof onSimulationEvidence === "function") {
      onSimulationEvidence(detail);
    }
    if (state.mode === "chain") {
      refreshChain().catch(() => {});
    } else {
      render();
    }
  }

  function onLibraryClick(event) {
    const researchId = event.target?.closest?.("[data-research-id]")
      ?.dataset?.researchId;
    if (researchId) {
      const action = state.libraryKind === "skill" ? openSkill : openHero;
      action(Number(researchId)).catch(() => {});
      return;
    }
    const packId = event.target?.closest?.("[data-card-pack-id]")
      ?.dataset?.cardPackId;
    if (packId) openCardPack(Number(packId)).catch(() => {});
  }

  function onStageClick(event) {
    const skillTarget = event.target?.closest?.("[data-skill-position]");
    if (skillTarget) {
      invalidateResearchModel();
      state.selectedPosition = Number(skillTarget.dataset.skillPosition);
      state.selectedSkillSlot = {
        position: state.selectedPosition,
        slot: Number(skillTarget.dataset.skillSlot),
      };
      if (state.libraryKind !== "skill") setLibraryKind("skill");
      else render();
      return;
    }
    const selectPosition = event.target?.closest?.("[data-select-position]")
      ?.dataset?.selectPosition;
    if (selectPosition !== undefined) {
      invalidateResearchModel();
      selectedLineupSide = "left";
      state.selectedPosition = Number(selectPosition);
      state.selectedSkillSlot = null;
      if (state.libraryKind !== "hero") setLibraryKind("hero");
      else render();
      return;
    }
    const lineupPosition = event.target?.closest?.("[data-position][data-side]");
    if (lineupPosition) {
      invalidateResearchModel();
      selectedLineupSide = lineupPosition.dataset.side === "right"
        ? "right"
        : "left";
      state.selectedPosition = Number(lineupPosition.dataset.position);
      state.selectedSkillSlot = null;
      if (state.libraryKind !== "hero") setLibraryKind("hero");
      else render();
      return;
    }
    const swap = event.target?.closest?.("[data-swap-left]");
    if (swap) {
      invalidateResearchModel({ clearDerived: true });
      state.lineup = swapResearchPositions(
        state.lineup,
        Number(swap.dataset.swapLeft),
        Number(swap.dataset.swapRight),
      );
      render();
      return;
    }
    const lineupButton = event.target?.closest?.("[data-lineup-key]");
    if (lineupButton) {
      openLineup(lineupButton.dataset.lineupKey).catch(() => {});
      return;
    }
    const chainNode = event.target?.closest?.("[data-chain-node-id]");
    if (chainNode) {
      state.selectedChainNodeId = chainNode.dataset.chainNodeId;
      render();
      return;
    }
    if (event.target?.closest?.('[data-research-action="send-simulator"]')) {
      sendToSimulator().catch((error) => {
        state.error = error?.message || "模拟器装载失败";
        renderError();
      });
      return;
    }
    if (event.target?.closest?.('[data-research-action="open-templates"]')) {
      openTemplateDialog();
      return;
    }
    if (event.target?.closest?.('[data-research-action="return-research"]')) {
      windowRef?.switchTab?.(state.simulationReturnTab || 34);
    }
  }

  function onStageChange(event) {
    const field = event.target?.dataset?.heroField;
    const position = Number(event.target?.dataset?.position);
    if (field && Number.isInteger(position)) {
      invalidateResearchModel({ clearDerived: true });
      const next = normalizeResearchLineup(state.lineup);
      next.heroes[position][field] = clamp(
        event.target.value,
        field === "level" ? 1 : 0,
        field === "level" ? 50 : 9,
        next.heroes[position][field],
      );
      state.lineup = next;
      render();
      return;
    }
    if (event.target?.id === "research-lineup-morale") {
      invalidateResearchModel({ clearDerived: true });
      const next = normalizeResearchLineup(state.lineup);
      next.morale = clamp(event.target.value, 0, 200, next.morale);
      state.lineup = next;
      render();
    }
  }

  function bind() {
    if (state.initialized) return;
    state.initialized = true;
    getElement("research-mode-tabs")?.addEventListener?.("click", (event) => {
      const mode = event.target?.closest?.("[data-research-mode]")
        ?.dataset?.researchMode;
      if (mode) setMode(mode).catch(() => {});
    });
    getElement("research-library-kind")?.addEventListener?.("click", (event) => {
      const kind = event.target?.closest?.("[data-kind]")?.dataset?.kind;
      if (!kind) return;
      setLibraryKind(kind);
      search().catch(() => {});
    });
    getElement("research-search")?.addEventListener?.("input", (event) => {
      state.query = event.target.value;
      if (state.timer) clearTimeoutFn(state.timer);
      state.timer = setTimeoutFn(() => search().catch(() => {}), 220);
    });
    getElement("research-results")?.addEventListener?.("click", onLibraryClick);
    getElement("research-stage")?.addEventListener?.("click", onStageClick);
    getElement("research-stage")?.addEventListener?.("change", onStageChange);
    getElement("research-evidence-body")?.addEventListener?.(
      "click",
      onStageClick,
    );
    getElement("research-evidence-tabs")?.addEventListener?.("click", (event) => {
      const evidence = event.target?.closest?.("[data-evidence]")
        ?.dataset?.evidence;
      if (!evidence) return;
      state.selectedEvidence = evidence;
      render();
    });
    getElement("research-template-dialog")?.addEventListener?.(
      "click",
      onTemplateDialogClick,
    );
    windowRef?.addEventListener?.(
      "stzb:simulation-completed",
      onSimulationCompleted,
    );
  }

  async function load() {
    bind();
    render();
    return search();
  }

  return {
    state,
    load,
    bind,
    render,
    search,
    setMode,
    setLibraryKind,
    setLineup,
    setOpponent,
    selectPosition,
    openHero,
    openSkill,
    openCardPack,
    openLineup,
    refreshMatchup,
    refreshChain,
    getChainNodes,
    sendToSimulator,
    sendHeroToSimulator,
    loadTemplate,
    listTemplates,
    saveTemplate,
    renameTemplate,
    deleteTemplate,
    exportTemplate,
    importTemplate,
    openTemplateDialog,
    onSimulationCompleted,
  };
}

import {
  analyzeReplay,
  buildEffectChains,
  groupReplayByPhase,
  replayEventDetail,
} from "./simulator-analysis.mjs";

const TEMPLATE_SCHEMA_VERSION = 1;
const REPEAT_OPTIONS = new Set([1, 100, 1000]);

const DEFAULT_ATTACKER = {
  morale: 100,
  heroes: [
    { id: 100027, position: 0, level: 40, up: 5, equip_skills: [0, 0] },
    { id: 100016, position: 1, level: 40, up: 5, equip_skills: [0, 0] },
    { id: 100090, position: 2, level: 40, up: 5, equip_skills: [0, 0] },
  ],
};

const DEFAULT_DEFENDER = {
  morale: 100,
  heroes: [
    { id: 100013, position: 0, level: 40, up: 5, equip_skills: [0, 0] },
    { id: 100649, position: 1, level: 40, up: 5, equip_skills: [0, 0] },
    { id: 100023, position: 2, level: 40, up: 5, equip_skills: [0, 0] },
  ],
};

function clone(value) {
  return value == null ? value : structuredClone(value);
}

function integer(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isInteger(parsed) ? parsed : fallback;
}

function normalizeSkillIds(hero) {
  const source =
    hero?.equip_skills || hero?.equipSkills || hero?.extraSkillIds || [];
  return [0, 1].map((index) => {
    const skillId = integer(source[index], 0);
    return skillId > 0 ? skillId : 0;
  });
}

function normalizeHero(hero, fallbackPosition = 0) {
  const id = integer(hero?.id || hero?.heroId, 0);
  if (id <= 0) throw new Error("invalid hero id");
  const position = integer(hero?.position, fallbackPosition);
  if (position < 0 || position > 2) throw new Error("invalid hero position");
  return {
    id,
    position,
    level: Math.min(45, Math.max(1, integer(hero?.level, 40))),
    up: Math.min(
      9,
      Math.max(0, integer(hero?.up ?? hero?.advanceLevel, 0)),
    ),
    equip_skills: normalizeSkillIds(hero),
  };
}

function normalizeTeam(team, fallback) {
  const source = team || fallback;
  const normalizedSource = Array.isArray(source)
    ? { morale: 100, heroes: source }
    : source;
  const heroes = (normalizedSource.heroes || normalizedSource.heros || []).map(
    normalizeHero,
  );
  if (heroes.length < 1 || heroes.length > 3) {
    throw new Error("team must contain 1 to 3 heroes");
  }
  const positions = heroes.map((hero) => hero.position);
  if (new Set(positions).size !== positions.length) {
    throw new Error("hero positions must be unique");
  }
  return {
    morale: Math.min(200, Math.max(0, integer(normalizedSource.morale, 100))),
    heroes,
  };
}

function normalizeState(input = {}) {
  const repeat = integer(input.repeat, 100);
  if (!REPEAT_OPTIONS.has(repeat)) throw new Error("invalid repeat");
  return {
    attacker: normalizeTeam(input.attacker, DEFAULT_ATTACKER),
    defender: normalizeTeam(input.defender, DEFAULT_DEFENDER),
    repeat,
    seedMode: input.seedMode === "random" ? "random" : "fixed",
    seed: integer(input.seed, 20260810),
    selectedSlot: input.selectedSlot ? clone(input.selectedSlot) : null,
    drawer: input.drawer ? clone(input.drawer) : null,
    result: input.result ? clone(input.result) : null,
    activeResultView: input.activeResultView || "summary",
    activeRound: integer(input.activeRound, 0),
    eventFilters: clone(input.eventFilters || {}),
  };
}

export function createSimulatorState(input = {}) {
  return normalizeState(input);
}

function updateTeam(state, side, updater) {
  if (!["attacker", "defender"].includes(side)) {
    throw new Error("invalid simulator side");
  }
  const next = clone(state);
  next[side] = updater(clone(state[side]));
  return next;
}

function setHeroAtPosition(team, position, hero) {
  const normalized = normalizeHero(hero, position);
  normalized.position = position;
  const heroes = team.heroes.filter((item) => item.position !== position);
  heroes.push(normalized);
  heroes.sort((left, right) => left.position - right.position);
  return { ...team, heroes };
}

function updateHero(team, position, updater) {
  const heroes = team.heroes.map((hero) =>
    hero.position === position ? updater(clone(hero)) : hero,
  );
  return { ...team, heroes };
}

export function simulatorReducer(state, action) {
  const current = normalizeState(state);
  switch (action?.type) {
    case "setHero":
      return updateTeam(current, action.side, (team) =>
        setHeroAtPosition(team, integer(action.position), action.hero),
      );
    case "removeHero":
      return updateTeam(current, action.side, (team) => {
        const heroes = team.heroes.filter(
          (hero) => hero.position !== integer(action.position),
        );
        if (!heroes.length) throw new Error("team must contain 1 to 3 heroes");
        return { ...team, heroes };
      });
    case "setHeroLevel":
      return updateTeam(current, action.side, (team) =>
        updateHero(team, integer(action.position), (hero) => ({
          ...hero,
          level: Math.min(45, Math.max(1, integer(action.level, hero.level))),
        })),
      );
    case "setHeroAdvance":
      return updateTeam(current, action.side, (team) =>
        updateHero(team, integer(action.position), (hero) => ({
          ...hero,
          up: Math.min(9, Math.max(0, integer(action.advance, hero.up))),
        })),
      );
    case "setHeroSkill":
      return updateTeam(current, action.side, (team) =>
        updateHero(team, integer(action.position), (hero) => {
          const equip_skills = [...hero.equip_skills];
          const skillId = integer(action.skillId);
          if (skillId <= 0) throw new Error("invalid skill id");
          equip_skills[integer(action.slot)] = skillId;
          return { ...hero, equip_skills };
        }),
      );
    case "clearHeroSkill":
      return updateTeam(current, action.side, (team) =>
        updateHero(team, integer(action.position), (hero) => {
          const equip_skills = [...hero.equip_skills];
          equip_skills[integer(action.slot)] = 0;
          return { ...hero, equip_skills };
        }),
      );
    case "setMorale":
      return updateTeam(current, action.side, (team) => ({
        ...team,
        morale: Math.min(200, Math.max(0, integer(action.morale, team.morale))),
      }));
    case "setRepeat": {
      const repeat = integer(action.repeat);
      if (!REPEAT_OPTIONS.has(repeat)) throw new Error("invalid repeat");
      return { ...clone(current), repeat };
    }
    case "setSeed":
      return {
        ...clone(current),
        seed: integer(action.seed, current.seed),
        seedMode: action.seedMode === "random" ? "random" : "fixed",
      };
    case "swapSides":
      return {
        ...clone(current),
        attacker: clone(current.defender),
        defender: clone(current.attacker),
      };
    case "copySide":
      return {
        ...clone(current),
        [action.to]: clone(current[action.from]),
      };
    case "openDrawer":
      return {
        ...clone(current),
        selectedSlot: clone(action.selectedSlot || null),
        drawer: clone(action.drawer || action.kind || null),
      };
    case "closeDrawer":
      return { ...clone(current), selectedSlot: null, drawer: null };
    case "setResult":
      return { ...clone(current), result: clone(action.result) };
    case "setResultView":
      return { ...clone(current), activeResultView: action.view || "summary" };
    case "setActiveRound":
      return { ...clone(current), activeRound: integer(action.round, 0) };
    case "setEventFilters":
      return { ...clone(current), eventFilters: clone(action.filters || {}) };
    case "loadLineup":
      return updateTeam(current, action.side, () =>
        normalizeTeam(action.lineup, DEFAULT_ATTACKER),
      );
    case "loadTemplate": {
      const template =
        typeof action.template === "string"
          ? parseTemplate(action.template)
          : parseTemplate(JSON.stringify(action.template));
      return createSimulatorState({ ...current, ...template });
    }
    case "reset":
      return createSimulatorState();
    default:
      return clone(current);
  }
}

function validateTemplateHero(hero) {
  const normalized = normalizeHero(hero, integer(hero?.position));
  for (const skillId of normalized.equip_skills) {
    if (skillId < 0) throw new Error("invalid skill id");
  }
  return normalized;
}

function validateTemplateTeam(team) {
  if (!team || !Array.isArray(team.heroes)) {
    throw new Error("invalid template team");
  }
  return normalizeTeam(
    {
      morale: team.morale,
      heroes: team.heroes.map(validateTemplateHero),
    },
    DEFAULT_ATTACKER,
  );
}

export function serializeTemplate(state, scope = "matchup", name = "未命名模板") {
  const current = normalizeState(state);
  if (!["matchup", "attacker", "defender"].includes(scope)) {
    throw new Error("invalid template scope");
  }
  const template = {
    schemaVersion: TEMPLATE_SCHEMA_VERSION,
    name: String(name || "未命名模板").trim() || "未命名模板",
    scope,
    repeat: current.repeat,
    seedMode: current.seedMode,
    seed: current.seed,
  };
  if (scope === "matchup" || scope === "attacker") {
    template.attacker = clone(current.attacker);
  }
  if (scope === "matchup" || scope === "defender") {
    template.defender = clone(current.defender);
  }
  return template;
}

export function parseTemplate(json) {
  let value;
  try {
    value = typeof json === "string" ? JSON.parse(json) : clone(json);
  } catch {
    throw new Error("invalid template json");
  }
  if (!value || typeof value !== "object") {
    throw new Error("invalid template");
  }
  if (value.schemaVersion !== TEMPLATE_SCHEMA_VERSION) {
    throw new Error("unsupported template schema");
  }
  if (!["matchup", "attacker", "defender"].includes(value.scope)) {
    throw new Error("invalid template scope");
  }
  const parsed = {
    schemaVersion: TEMPLATE_SCHEMA_VERSION,
    name: String(value.name || "未命名模板"),
    scope: value.scope,
    repeat: REPEAT_OPTIONS.has(integer(value.repeat))
      ? integer(value.repeat)
      : 100,
    seedMode: value.seedMode === "random" ? "random" : "fixed",
    seed: integer(value.seed, 20260810),
  };
  if (value.attacker) parsed.attacker = validateTemplateTeam(value.attacker);
  if (value.defender) parsed.defender = validateTemplateTeam(value.defender);
  if (value.scope === "matchup" && (!parsed.attacker || !parsed.defender)) {
    throw new Error("invalid matchup template");
  }
  return parsed;
}

export const SimulatorWorkbenchState = {
  createSimulatorState,
  simulatorReducer,
  serializeTemplate,
  parseTemplate,
};

export function createSourceContext(options = {}) {
  return {
    source: options.source || "external",
    lineupKey: options.lineupKey || "",
    camp: options.camp === "red" ? "red" : "blue",
    ...(Number.isInteger(options.returnTab)
      ? { returnTab: options.returnTab }
      : {}),
  };
}

export function createSimulationRequestSnapshot({
  revision,
  stateRevision,
  repeat,
  payload,
  sourceContext,
} = {}) {
  return {
    revision: Number(revision) || 0,
    stateRevision: Number(stateRevision) || 0,
    repeat: Number(repeat) || 1,
    payload: clone(payload),
    sourceContext: clone(sourceContext),
  };
}

export function shouldCommitSimulationRequest(
  request,
  current,
) {
  if (
    request
    && current
    && typeof request === "object"
    && typeof current === "object"
  ) {
    return (
      Number(request.revision) === Number(current.revision)
      && Number(request.stateRevision) === Number(current.stateRevision)
    );
  }
  return Number(request) === Number(current);
}

const SIMULATION_IDENTITY_ACTIONS = new Set([
  "setHero",
  "removeHero",
  "setHeroLevel",
  "setHeroAdvance",
  "setHeroSkill",
  "clearHeroSkill",
  "setMorale",
  "setRepeat",
  "setSeed",
  "swapSides",
  "copySide",
  "loadLineup",
  "loadTemplate",
  "reset",
]);

export function simulationActionAffectsRunIdentity(action) {
  return SIMULATION_IDENTITY_ACTIONS.has(action?.type);
}

export function simulationSourceContextAfterAction(sourceContext, action) {
  return simulationActionAffectsRunIdentity(action)
    ? null
    : sourceContext;
}

export function createSharedLoadStateRegistry() {
  const owners = new Map();
  let sequence = 0;

  function acquire(owner) {
    const token = ++sequence;
    owners.set(owner, {
      token,
      model: null,
      sequence,
    });
    return token;
  }

  function update(owner, token, model) {
    const entry = owners.get(owner);
    if (!entry || entry.token !== token) return false;
    entry.model = { ...model };
    entry.sequence = ++sequence;
    return true;
  }

  function release(owner, token) {
    const entry = owners.get(owner);
    if (!entry || entry.token !== token) return false;
    if (entry.model?.busy) {
      entry.model = {
        ...entry.model,
        kind: ["loading", "refreshing"].includes(entry.model.kind)
          ? "success"
          : entry.model.kind,
        message: ["loading", "refreshing"].includes(entry.model.kind)
          ? ""
          : entry.model.message,
        actionLabel: ["loading", "refreshing"].includes(entry.model.kind)
          ? ""
          : entry.model.actionLabel,
        action: ["loading", "refreshing"].includes(entry.model.kind)
          ? undefined
          : entry.model.action,
        busy: false,
      };
      entry.sequence = ++sequence;
    }
    return true;
  }

  function current() {
    const entries = [...owners.values()].filter((entry) => entry.model);
    const busy = entries
      .filter((entry) => entry.model.busy)
      .sort((left, right) => right.sequence - left.sequence)[0];
    if (busy) return { ...busy.model, busy: true };
    const error = entries
      .filter((entry) => entry.model.kind === "error")
      .sort((left, right) => right.sequence - left.sequence)[0];
    if (error) return { ...error.model, busy: false };
    const terminal = entries
      .sort((left, right) => right.sequence - left.sequence)[0];
    return terminal
      ? { ...terminal.model, busy: false }
      : {
          kind: "success",
          message: "",
          replace: false,
          busy: false,
        };
  }

  function isBusy(owner) {
    return Boolean(owners.get(owner)?.model?.busy);
  }

  return {
    acquire,
    update,
    release,
    current,
    isBusy,
  };
}

export function createSimulatorInitializer(options = {}) {
  const hasState = options.hasState ?? (() => false);
  const prepareState = options.prepareState ?? (() => {});
  const getState = options.getState ?? (() => null);
  const loadCatalog = options.loadCatalog ?? (async () => ({}));
  const loadEngine = options.loadEngine ?? (async () => ({}));
  const commit = options.commit ?? (() => {});
  const renderState = options.renderState ?? (() => {});
  const acquireState = options.acquireState ?? null;
  const releaseState = options.releaseState ?? (() => {});
  let generation = 0;
  let initializing = null;
  let hasUsableState = Boolean(hasState());

  function initialize() {
    if (initializing?.generation === generation) {
      return initializing.promise;
    }
    const hadState = hasUsableState && Boolean(hasState());
    if (!hadState) prepareState();
    const requestGeneration = generation + 1;
    generation = requestGeneration;
    const ownerToken = acquireState?.() ?? requestGeneration;
    renderState({
      kind: hadState ? "refreshing" : "loading",
      message: hadState
        ? "正在刷新战斗模拟工作台…"
        : "正在初始化战斗模拟工作台…",
      replace: !hadState,
      busy: true,
      actionLabel: "",
      action: undefined,
      ownerToken,
    });
    let promise;
    promise = (async () => {
      try {
        const [catalog, engine] = await Promise.all([
          loadCatalog(),
          loadEngine(),
        ]);
        if (requestGeneration !== generation) return null;
        commit({ catalog, engine });
        hasUsableState = true;
        renderState({
          kind: "success",
          message: "",
          replace: false,
          busy: false,
          actionLabel: "",
          action: undefined,
          ownerToken,
        });
        return getState();
      } catch (error) {
        if (requestGeneration !== generation) return null;
        renderState({
          kind: "error",
          message: error?.message || "战斗模拟工作台初始化失败",
          replace: !hadState,
          busy: false,
          actionLabel: "重试",
          action: initialize,
          ownerToken,
        });
        throw error;
      } finally {
        releaseState(ownerToken);
        if (
          requestGeneration === generation
          && initializing?.promise === promise
        ) {
          initializing = null;
        }
      }
    })();
    initializing = { generation: requestGeneration, promise };
    return promise;
  }

  function invalidate() {
    generation += 1;
    initializing = null;
  }

  return {
    initialize,
    invalidate,
  };
}

function hasUnsupportedEffects(response) {
  return Boolean(
    response?.firstRun?.diagnostics?.unsupportedSkillEffects?.length ||
      response?.result?.replay?.diagnostics?.unsupportedSkillEffects?.length,
  );
}

export function simulationCompletionEvent(response, repeat, sourceContext) {
  return {
    type: "simulation:completed",
    target: "#sim-result-summary",
    domain: "operations",
    severity: hasUnsupportedEffects(response) ? "warning" : "success",
    value: repeat,
    dedupeKey: `simulation:${repeat}:${sourceContext?.lineupKey || "manual"}`,
  };
}

const TEMPLATE_STORAGE_KEY = "stzb.simulator.templates.v1";
const POSITION_NAMES = ["大营", "中军", "前锋"];
const CAMP_NAMES = ["", "蜀", "魏", "吴", "汉", "群", "晋"];
const ARMY_NAMES = ["", "弓", "步", "骑"];
const SKILL_TYPE_NAMES = ["", "指挥", "主动", "追击", "被动"];
const PORTRAIT_PLACEHOLDER = "/static/hero-portraits/placeholder.svg";
const CAMP_COLORS = {
  0: "#60758a",
  1: "#46b06e",
  2: "#4a8fe0",
  3: "#e05050",
  4: "#c8a044",
  5: "#9060d0",
  6: "#3ab8c8",
};
const EVENT_FILTERS = {
  all: null,
  damage: new Set(["NormalAttack", "SkillDamage", "OngoingDamage"]),
  recovery: new Set(["Recovery"]),
  control: new Set([
    "StatusApplied",
    "StatusRemoved",
    "EffectExpired",
    "EffectBlocked",
    "Evaded",
  ]),
  stat: new Set(["StatChanged", "ModifierApplied", "SkillRangeChanged"]),
  skill: new Set([
    "SkillTriggered",
    "SkillPreparationStarted",
    "SkillPreparationCompleted",
    "SkillPreparationCancelled",
  ]),
};

const browserRuntime = {
  initialized: false,
  state: null,
  heroes: [],
  skills: [],
  heroById: new Map(),
  skillById: new Map(),
  engine: null,
  sourceContext: null,
  selectedReplayItem: null,
  replayMode: "semantic",
  eventFilter: "all",
  libraryKind: "hero",
  libraryQuery: "",
  libraryFilter: "",
  libraryQuality: "",
  requestRevision: 0,
  stateRevision: 0,
  loading: false,
};
const simulatorLoadStates = createSharedLoadStateRegistry();

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString("zh-CN");
}

function shortCommit(value) {
  const commit = String(value || "");
  return commit ? commit.slice(0, 10) : "unknown";
}

export function portraitPresentation(hero, loading = "lazy") {
  const fallbackSrc = hero?.portraitFallbackUrl || "";
  const local = hero?.portraitLocal !== false;
  return {
    src:
      (!local && fallbackSrc) ||
      hero?.portraitUrl ||
      PORTRAIT_PLACEHOLDER,
    fallbackSrc: local ? fallbackSrc : "",
    placeholderSrc: PORTRAIT_PLACEHOLDER,
    alt: `${hero?.name || `武将 ${hero?.id || "?"}`}武将画像`,
    loading,
  };
}

export function advancePortraitFallback(image) {
  const step = image.dataset.portraitStep || "local";
  if (step === "local" && image.dataset.fallbackSrc) {
    image.dataset.portraitStep = "cdn";
    image.src = image.dataset.fallbackSrc;
    return "cdn";
  }
  if (step !== "placeholder" && step !== "done") {
    image.dataset.portraitStep = "placeholder";
    image.src =
      image.dataset.placeholderSrc || PORTRAIT_PLACEHOLDER;
    return "placeholder";
  }
  image.dataset.portraitStep = "done";
  return "done";
}

function heroInfo(id) {
  return browserRuntime.heroById.get(Number(id)) || {
    id: Number(id) || 0,
    name: `武将 ${id || "?"}`,
    camp: 0,
    army: 0,
    quality: 0,
    range: 0,
  };
}

function skillInfo(id) {
  return browserRuntime.skillById.get(Number(id)) || {
    id: Number(id) || 0,
    name: id ? `战法 ${id}` : "未装备",
    skill_type: 0,
    level: "",
    quality: "",
    range: 0,
    target: "",
  };
}

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  const contentType = response.headers.get("content-type") || "";
  if (!contentType.includes("application/json")) {
    throw new Error(`接口 ${url} 未返回 JSON，请重启后端`);
  }
  const body = await response.json();
  if (!response.ok || body?.ok === false) {
    throw new Error(body?.error || `${response.status} ${response.statusText}`);
  }
  return body;
}

function dispatch(action, render = true) {
  browserRuntime.state = simulatorReducer(browserRuntime.state, action);
  if (simulationActionAffectsRunIdentity(action)) {
    browserRuntime.stateRevision += 1;
  }
  browserRuntime.sourceContext = simulationSourceContextAfterAction(
    browserRuntime.sourceContext,
    action,
  );
  if (render) renderWorkbench();
  return browserRuntime.state;
}

function renderEngineBadge(error = "") {
  const badge = document.getElementById("sim-engine-badge");
  if (!badge) return;
  if (error) {
    badge.innerHTML = `
      <strong class="sim-error-text">引擎不可用</strong>
      <span>${escapeHtml(error)}</span>
    `;
    return;
  }
  const metadata = browserRuntime.engine;
  if (!metadata) {
    badge.innerHTML = `
      <strong>引擎连接中</strong>
      <span>正在读取源提交与回放能力</span>
    `;
    return;
  }
  badge.innerHTML = `
    <strong>${escapeHtml(metadata.name)}</strong>
    <span>source ${escapeHtml(shortCommit(metadata.sourceCommit))} · 回放 ${
      metadata.supportsDetailedReplay ? "完整" : "基础"
    }</span>
  `;
}

function teamMarkup(side) {
  const team = browserRuntime.state[side];
  const attacker = side === "attacker";
  const opposite = attacker ? "defender" : "attacker";
  const label = attacker ? "攻方编队" : "守方编队";
  const code = attacker ? "ATK" : "DEF";
  const heroes = [0, 1, 2]
    .map((position) => {
      const hero = team.heroes.find((item) => item.position === position);
      return hero ? heroCardMarkup(side, hero) : emptyHeroCardMarkup(side, position);
    })
    .join("");
  return `
    <div class="sim-team-head">
      <div class="sim-team-title">
        <span class="sim-team-mark">${code.slice(0, 1)}</span>
        <div><h3>${label}</h3><span>${code} · 3 SLOT FORMATION</span></div>
      </div>
      <div class="sim-team-tools">
        <button class="sim-icon-button" type="button" data-sim-action="copy-side"
          data-from="${side}" data-to="${opposite}">复制至${attacker ? "守方" : "攻方"}</button>
      </div>
    </div>
    <div class="sim-team-meta">
      <label>士气
        <input type="number" min="0" max="200" value="${team.morale}"
          data-sim-change="morale" data-side="${side}" aria-label="${label}士气">
      </label>
      <span>${team.heroes.length}/3 武将</span>
      <span>${team.heroes.every((hero) => hero.id > 0) ? "配置可运行" : "阵容不完整"}</span>
    </div>
    <div class="sim-hero-grid">${heroes}</div>
  `;
}

function emptyHeroCardMarkup(side, position) {
  return `
    <button class="sim-hero-card is-empty" type="button"
      data-sim-action="open-library" data-kind="hero" data-side="${side}"
      data-position="${position}">
      <span class="sim-position">${POSITION_NAMES[position]}</span>
      <strong>选择武将</strong>
      <span>打开武将库</span>
    </button>
  `;
}

function heroCardMarkup(side, hero) {
  const info = heroInfo(hero.id);
  const monogram = String(info.name || "?").slice(-1);
  const portrait = portraitPresentation(info, "eager");
  const portraitStep =
    info.portraitLocal === false &&
    portrait.src === info.portraitFallbackUrl
      ? "cdn"
      : "local";
  const accent = CAMP_COLORS[Number(info.camp) || 0];
  const skills = [0, 1]
    .map((slot) => skillSlotMarkup(side, hero, slot))
    .join("");
  return `
    <article class="sim-hero-card" data-side="${side}" data-position="${hero.position}"
      data-camp="${Number(info.camp) || 0}"
      style="--sim-camp-accent:${accent};--sim-camp-glow:${accent}55">
      <div class="sim-hero-visual" data-monogram="${escapeHtml(monogram)}">
        <div class="sim-hero-portrait">
          <img class="sim-hero-portrait-image" data-sim-portrait
            data-portrait-step="${portraitStep}"
            data-fallback-src="${escapeHtml(portrait.fallbackSrc)}"
            data-placeholder-src="${escapeHtml(portrait.placeholderSrc)}"
            src="${escapeHtml(portrait.src)}"
            alt="${escapeHtml(portrait.alt)}"
            loading="${portrait.loading}" decoding="async">
          <div class="sim-hero-scan"></div>
        </div>
        <span class="sim-position">${POSITION_NAMES[hero.position]}</span>
        <div class="sim-hero-tags">
          <span>${escapeHtml(CAMP_NAMES[info.camp] || "未")}</span>
          <span>${escapeHtml(ARMY_NAMES[info.army] || "兵")}</span>
          <span>${info.quality || "?"}星</span>
        </div>
        <h4 class="sim-hero-name">${escapeHtml(info.name)}</h4>
        <span class="sim-hero-id">HERO ${hero.id}</span>
      </div>
      <div class="sim-hero-body sim-hero-glass">
        <div class="sim-hero-runtime-stats">
          <span>LV <b>${hero.level}</b></span>
          <span>ADV <b>+${hero.up}</b></span>
          <span>MORALE <b>${browserRuntime.state[side].morale}</b></span>
        </div>
        <div class="sim-hero-fields">
          <label class="sim-control-field">等级
            <input type="number" min="1" max="45" value="${hero.level}"
              data-sim-change="hero-level" data-side="${side}" data-position="${hero.position}">
          </label>
          <label class="sim-control-field">进阶
            <input type="number" min="0" max="9" value="${hero.up}"
              data-sim-change="hero-advance" data-side="${side}" data-position="${hero.position}">
          </label>
        </div>
        <div class="sim-skill-stack">${skills}</div>
        <div class="sim-action-grid">
          <button class="sim-ghost-button" type="button" data-sim-action="open-library"
            data-kind="hero" data-side="${side}" data-position="${hero.position}">替换</button>
          <button class="sim-ghost-button" type="button" data-sim-action="remove-hero"
            data-side="${side}" data-position="${hero.position}">移除</button>
        </div>
      </div>
    </article>
  `;
}

function skillSlotMarkup(side, hero, slot) {
  const skillId = Number(hero.equip_skills?.[slot] || 0);
  const skill = skillInfo(skillId);
  const type = SKILL_TYPE_NAMES[skill.skill_type] || "+";
  return `
    <button class="sim-skill-slot" type="button" data-sim-action="open-library"
      data-kind="skill" data-side="${side}" data-position="${hero.position}" data-slot="${slot}">
      <span class="sim-skill-type">${escapeHtml(type.slice(0, 1))}</span>
      <span class="sim-skill-name">${escapeHtml(skill.name)}</span>
      ${
        skillId
          ? `<span class="sim-skill-clear" data-sim-action="clear-skill"
              data-side="${side}" data-position="${hero.position}" data-slot="${slot}"
              aria-label="清除战法">×</span>`
          : `<span class="sim-skill-clear">ADD</span>`
      }
    </button>
  `;
}

function controlsMarkup() {
  const state = browserRuntime.state;
  const repeatButtons = [1, 100, 1000]
    .map(
      (repeat, index) => `
        <button type="button" class="${state.repeat === repeat ? "is-active" : ""}"
          data-sim-action="set-repeat" data-repeat="${repeat}">
          ${index + 1} · ${repeat === 1 ? "单场" : `×${repeat}`}
        </button>`,
    )
    .join("");
  return `
    <div class="sim-vs-orbit" aria-hidden="true">VS</div>
    <div class="sim-repeat-switch" aria-label="模拟次数">${repeatButtons}</div>
    <label class="sim-control-field">固定随机种子
      <input id="sim-seed" type="number" value="${state.seed}" data-sim-change="seed">
    </label>
    <button id="sim-run-button" class="sim-run-button" type="button"
      data-sim-action="run">开始推演</button>
    <div class="sim-action-grid">
      <button class="sim-ghost-button" type="button" data-sim-action="swap-sides">交换攻守</button>
      <button class="sim-ghost-button" type="button" data-sim-action="open-templates">阵容模板</button>
      <button class="sim-ghost-button" type="button" data-sim-action="reset">重置阵容</button>
      <button class="sim-ghost-button" type="button" data-sim-action="open-replay">战术复盘</button>
    </div>
    <div id="sim-status" class="sim-status" aria-live="polite">
      ${
        state.result
          ? `已完成 ${state.result.repeat || 1} 次 · ${escapeHtml(state.result.engine || "stzb-kotlin")}`
          : "Cmd/Ctrl + Enter 运行 · 数字 1/2/3 切换次数"
      }
    </div>
  `;
}

function setStatus(message, kind = "") {
  const element = document.getElementById("sim-status");
  if (!element) return;
  element.className = `sim-status${kind ? ` is-${kind}` : ""}`;
  element.textContent = message;
}

function renderWorkbench() {
  if (typeof document === "undefined" || !browserRuntime.state) return;
  const attacker = document.getElementById("sim-attacker-team");
  const defender = document.getElementById("sim-defender-team");
  const controls = document.getElementById("sim-run-controls");
  if (!attacker || !defender || !controls) return;
  normalizeOverlayMaterials();
  attacker.innerHTML = teamMarkup("attacker");
  defender.innerHTML = teamMarkup("defender");
  controls.innerHTML = controlsMarkup();
  renderResult();
}

function normalizeOverlayMaterials() {
  const surfaces = [
    ["sim-run-controls", "hud-surface-raised"],
    ["sim-result-summary", "hud-surface-raised"],
    ["sim-replay-detail", "hud-surface-raised"],
    ["sim-library-dialog", "hud-surface-modal"],
    ["sim-template-dialog", "hud-surface-modal"],
  ];
  for (const [id, className] of surfaces) {
    document.getElementById(id)?.classList.add(className);
  }
}

function firstRunFromResponse(response) {
  return (
    response?.firstRun ||
    response?.engineResult?.firstRun ||
    response?.result?.firstRun ||
    null
  );
}

function renderResult() {
  const summary = document.getElementById("sim-result-summary");
  const replay = document.getElementById("sim-replay-view");
  if (!summary || !replay) return;
  const response = browserRuntime.state.result;
  if (!response) {
    summary.hidden = true;
    replay.hidden = true;
    return;
  }
  const firstRun = firstRunFromResponse(response);
  const analysis = firstRun ? analyzeReplay(firstRun) : null;
  summary.hidden = false;
  summary.innerHTML = resultSummaryMarkup(response, firstRun, analysis);
  const view = browserRuntime.state.activeResultView;
  replay.hidden = view === "summary";
  if (!replay.hidden && firstRun) renderReplay(firstRun, analysis);
}

function resultSummaryMarkup(response, firstRun, analysis) {
  const single = Number(response.repeat || response.engineResult?.repeat || 1) === 1;
  const result = response.result || {};
  const outcome = single
    ? result.winner || outcomeLabel(firstRun?.outcome)
    : `攻方 ${response.blue_rate || 0}%`;
  const subtitle = single
    ? `${result.rounds_played || firstRun?.roundsPlayed || 0} 回合 · ${
        firstRun?.events?.length || 0
      } 语义事件`
    : `${response.repeat} 次批量模拟 · 第一场已保留完整回放`;
  const metrics = single
    ? [
        ["攻方剩余", result.blue?.total_arms || firstRun?.attackerRemain || 0],
        ["守方剩余", result.red?.total_arms || firstRun?.defenderRemain || 0],
        ["攻方伤害", analysis?.totals.attackerDamage || 0],
        ["守方伤害", analysis?.totals.defenderDamage || 0],
        ["攻方恢复", analysis?.totals.attackerRecovery || 0],
        ["守方恢复", analysis?.totals.defenderRecovery || 0],
        ["控制施加", analysis?.totals.controlsApplied || 0],
        ["效果阻挡", analysis?.totals.blockedEffects || 0],
      ]
    : [
        ["攻方胜率", `${response.blue_rate || 0}%`],
        ["守方胜率", `${response.red_rate || 0}%`],
        ["平局率", `${response.draw_rate || 0}%`],
        ["实际次数", response.repeat || 0],
        ["攻方胜场", response.blue_wins || 0],
        ["守方胜场", response.red_wins || 0],
        ["平局", response.draws || 0],
        ["首场事件", firstRun?.events?.length || 0],
      ];
  const completeness = analysis?.completeness || {
    status: "partial",
    unsupportedSkillEffects: 0,
    unsupportedEquipmentEffects: 0,
    unprojectedReplayEvents: 0,
    semanticEventCount: firstRun?.events?.length || 0,
    replayActionCount: firstRun?.replayActions?.length || 0,
  };
  return `
    <div class="sim-result-toolbar">
      <div>
        <span class="sim-kicker">SIMULATION RESULT</span>
        <strong>模拟结果 · 不等同真实历史胜率</strong>
      </div>
      <div class="sim-view-switch">
        ${viewButton("summary", "结果摘要")}
        ${viewButton("semantic", "语义事件")}
        ${viewButton("actions", "动作原码")}
      </div>
    </div>
    <div class="sim-result-hero">
      <div class="sim-outcome-card"><strong>${escapeHtml(outcome)}</strong><span>${escapeHtml(
        subtitle,
      )}</span></div>
      <div class="sim-metrics">
        ${metrics
          .map(
            ([label, value]) => `
              <div class="sim-metric-card"><span>${escapeHtml(label)}</span>
                <strong>${escapeHtml(formatNumber(value))}</strong></div>`,
          )
          .join("")}
      </div>
    </div>
    ${
      single
        ? ""
        : `<div class="sim-rate-track" aria-label="胜率分布">
            <span class="sim-rate-attacker" style="width:${Number(response.blue_rate || 0)}%"></span>
            <span class="sim-rate-draw" style="width:${Number(response.draw_rate || 0)}%"></span>
            <span class="sim-rate-defender" style="width:${Number(response.red_rate || 0)}%"></span>
          </div>`
    }
    ${insightsMarkup(analysis?.insights || [])}
    ${completenessMarkup(completeness)}
  `;
}

function viewButton(view, label) {
  return `
    <button type="button" class="${
      browserRuntime.state.activeResultView === view ? "is-active" : ""
    }" data-sim-action="set-result-view" data-view="${view}">${label}</button>
  `;
}

function insightsMarkup(insights) {
  if (!insights.length) return "";
  return `
    <section class="sim-insights">
      <span class="sim-kicker">EVIDENCE BACKED INSIGHTS</span>
      ${insights
        .map(
          (insight) => `
            <button class="sim-insight ${
              insight.severity === "warning" ? "is-warning" : ""
            }" type="button" data-sim-action="focus-evidence"
              data-round="${insight.round || 0}" data-event-seq="${
                insight.eventSeqs?.[0] ?? ""
              }">
              <span>${insight.round ? `R${insight.round}` : "WARN"}</span>
              <strong>${escapeHtml(insight.title)}</strong>
              <code>#${escapeHtml((insight.eventSeqs || []).join(","))}</code>
            </button>`,
        )
        .join("")}
    </section>
  `;
}

function completenessMarkup(completeness) {
  return `
    <section class="sim-completeness-card ${
      completeness.status === "partial" ? "is-partial" : ""
    }">
      <span class="sim-kicker">REPLAY COMPLETENESS</span>
      <strong>${
        completeness.status === "complete" ? "完整回放" : "部分效果未完整执行或投影"
      }</strong>
      <div class="sim-completeness-list">
        ${completenessItem("语义事件", completeness.semanticEventCount)}
        ${completenessItem("Server 动作", completeness.replayActionCount)}
        ${completenessItem("未支持战法", completeness.unsupportedSkillEffects)}
        ${completenessItem("未支持装备", completeness.unsupportedEquipmentEffects)}
        ${completenessItem("动作投影告警", completeness.unprojectedReplayEvents)}
        ${completenessItem("源提交", shortCommit(browserRuntime.engine?.sourceCommit))}
      </div>
    </section>
  `;
}

function completenessItem(label, value) {
  return `<span>${escapeHtml(label)}<strong>${escapeHtml(formatNumber(value))}</strong></span>`;
}

function renderReplay(firstRun, analysis) {
  const phases = document.getElementById("sim-replay-phases");
  const stream = document.getElementById("sim-replay-stream");
  const detail = document.getElementById("sim-replay-detail");
  if (!phases || !stream || !detail) return;
  const grouped = analysis?.grouped || groupReplayByPhase(firstRun);
  const activeRound = browserRuntime.state.activeRound;
  phases.innerHTML = [
    phaseButtonMarkup(0, "准备阶段", grouped.preparation.events.length),
    ...(activeRound === 0
      ? grouped.preparation.stages.map(
          (stage) => `
            <div class="sim-prep-stage">
              <span>${escapeHtml(stage.label)}</span>
              <code>${stage.actions.length} actions</code>
            </div>`,
        )
      : []),
    ...grouped.rounds.map((group) =>
      phaseButtonMarkup(
        group.round,
        `第 ${group.round} 回合`,
        group.events.length,
        group.actions.length,
      ),
    ),
  ].join("");
  if (browserRuntime.replayMode === "actions") {
    stream.innerHTML = replayToolbarMarkup("actions") + actionListMarkup(firstRun.replayActions || []);
  } else {
    const events =
      activeRound === 0
        ? grouped.preparation.events
        : grouped.rounds.find((group) => group.round === activeRound)?.events || [];
    const actionEnvelopes =
      grouped.rounds.find((group) => group.round === activeRound)?.actions || [];
    const filtered = filterEvents(events, browserRuntime.eventFilter);
    stream.innerHTML =
      replayToolbarMarkup("semantic") +
      eventListMarkup(filtered, actionEnvelopes);
  }
  detail.innerHTML = replayDetailMarkup(firstRun, analysis);
}

function phaseButtonMarkup(round, label, count, actionCount = 0) {
  return `
    <button class="sim-phase-button ${
      browserRuntime.state.activeRound === round ? "is-active" : ""
    }" type="button" data-sim-action="set-round" data-round="${round}">
      <strong>${label}</strong><span>${count} events${
        actionCount ? ` · ${actionCount} actions` : ""
      }</span>
    </button>
  `;
}

function replayToolbarMarkup(mode) {
  return `
    <div class="sim-replay-toolbar">
      <div class="sim-view-switch">
        <button type="button" class="${mode === "semantic" ? "is-active" : ""}"
          data-sim-action="set-replay-mode" data-mode="semantic">语义事件</button>
        <button type="button" class="${mode === "actions" ? "is-active" : ""}"
          data-sim-action="set-replay-mode" data-mode="actions">动作原码</button>
      </div>
      ${
        mode === "semantic"
          ? `<div class="sim-replay-filters">
              ${Object.keys(EVENT_FILTERS)
                .map(
                  (filter) => `
                    <button type="button" class="${
                      browserRuntime.eventFilter === filter ? "is-active" : ""
                    }" data-sim-action="set-event-filter" data-filter="${filter}">
                      ${eventFilterLabel(filter)}
                    </button>`,
                )
                .join("")}
            </div>`
          : `<span class="sim-event-seq">ClientBattleTextReplayAdapter</span>`
      }
    </div>
  `;
}

function eventFilterLabel(filter) {
  return {
    all: "全部",
    damage: "伤害",
    recovery: "恢复",
    control: "控制",
    stat: "属性",
    skill: "战法",
  }[filter];
}

function filterEvents(events, filter) {
  const accepted = EVENT_FILTERS[filter];
  return accepted ? events.filter((event) => accepted.has(event.type)) : events;
}

function eventListMarkup(events, actionEnvelopes = []) {
  if (!events.length) {
    return `<div class="sim-empty-state">当前筛选没有事件</div>`;
  }
  const actionByStart = new Map(
    actionEnvelopes.map((action) => [Number(action.startedAt), action]),
  );
  const actionEnds = new Map(
    actionEnvelopes
      .filter((action) => action.endedAt != null)
      .map((action) => [Number(action.endedAt), action]),
  );
  return `
    <div class="sim-event-list">
      ${events
        .map((event) => {
          const active =
            browserRuntime.selectedReplayItem?.kind === "event" &&
            browserRuntime.selectedReplayItem.eventSeq === Number(event.eventSeq);
          const envelopeStart = actionByStart.get(Number(event.eventSeq));
          const envelopeEnd = actionEnds.get(Number(event.eventSeq));
          return `
            ${
              envelopeStart
                ? `<div class="sim-action-envelope-head">
                    <span>HERO ACTION</span>
                    <strong>${escapeHtml(refLabel(envelopeStart.source))}</strong>
                    <code>#${envelopeStart.startedAt} → #${
                      envelopeStart.endedAt ?? "?"
                    }</code>
                  </div>`
                : ""
            }
            <button class="sim-event-row ${active ? "is-active" : ""}" type="button"
              data-sim-action="select-event" data-event-seq="${event.eventSeq}">
              <span class="sim-event-seq">#${event.eventSeq}</span>
              <span class="sim-event-type">${escapeHtml(event.type)}</span>
              <span class="sim-event-summary">${escapeHtml(eventSummary(event))}</span>
              <span class="sim-event-value">${escapeHtml(eventValue(event))}</span>
            </button>
            ${
              envelopeEnd
                ? `<div class="sim-action-envelope-end">行动结束 · ${escapeHtml(
                    refLabel(envelopeEnd.source),
                  )}</div>`
                : ""
            }`;
        })
        .join("")}
    </div>
  `;
}

function actionListMarkup(actions) {
  if (!actions.length) {
    return `<div class="sim-empty-state">没有 Server action 数据</div>`;
  }
  return `
    <div class="sim-action-list">
      ${actions
        .map((action) => {
          const active =
            browserRuntime.selectedReplayItem?.kind === "action" &&
            browserRuntime.selectedReplayItem.actionSeq === Number(action.actionSeq);
          return `
            <button class="sim-action-row ${active ? "is-active" : ""}" type="button"
              data-sim-action="select-action" data-action-seq="${action.actionSeq}">
              <span class="sim-event-seq">#${action.actionSeq}</span>
              <span class="sim-action-id">ID ${action.actionId}</span>
              <span class="sim-action-encoded">${escapeHtml(action.encoded)}</span>
              <span class="sim-event-value">${Number(action.actionId).toString(36)}</span>
            </button>`;
        })
        .join("")}
    </div>
  `;
}

function eventSummary(event) {
  const source = refLabel(event.source);
  const target = refLabel(event.target);
  if (event.type === "RoundStart") return `第 ${event.round} 回合开始`;
  if (event.type === "RoundEnd") return `第 ${event.round} 回合结束`;
  if (event.type === "HeroActionStart") return `${source} 行动开始`;
  if (event.type === "HeroActionEnd") return `${source} 行动结束`;
  if (event.type === "SkillTriggered") {
    return `${source} 发动 ${skillInfo(event.skillId).name}`;
  }
  if (["NormalAttack", "SkillDamage", "OngoingDamage"].includes(event.type)) {
    return `${source} → ${target}`;
  }
  if (event.type === "Recovery") return `${source} 恢复 ${target}`;
  if (event.type === "StatusApplied") return `${source} 对 ${target} 施加 ${event.status}`;
  if (event.type === "EffectBlocked") return `${target} 阻挡效果 ${event.effectId}`;
  if (event.type === "StatChanged") return `${target} ${event.stat} 变化`;
  if (event.type === "BattleEnd") return `战斗结束 ${outcomeLabel(event.payload?.outcome)}`;
  return [source, target].filter(Boolean).join(" → ") || event.type;
}

function eventValue(event) {
  if (event.damage) return `-${formatNumber(event.damage)}`;
  if (event.amount) return `+${formatNumber(event.amount)}`;
  if (event.deltaExact) return `${event.deltaExact > 0 ? "+" : ""}${event.deltaExact}`;
  if (event.targetTroopsAfter != null) return formatNumber(event.targetTroopsAfter);
  return "";
}

function refLabel(ref) {
  if (!ref) return "";
  const side = ref.side === "ATTACKER" ? "攻" : "守";
  return `${side}·${POSITION_NAMES[Number(ref.position)] || ref.position}·${
    heroInfo(ref.heroId).name
  }`;
}

function outcomeLabel(outcome) {
  return {
    ATTACKER_WIN: "攻方胜",
    DEFENDER_WIN: "守方胜",
    DRAW: "平局",
  }[outcome] || "待定";
}

function replayDetailMarkup(firstRun, analysis) {
  const selected = browserRuntime.selectedReplayItem;
  const snapshots =
    browserRuntime.state.activeRound === 0
      ? firstRun.entrySnapshots || []
      : (firstRun.roundSnapshots || []).filter(
          (item) => Number(item.round) === browserRuntime.state.activeRound,
        );
  if (!selected) {
    return `
      <span class="sim-kicker">TACTICAL DETAIL</span>
      <h3>选择事件或动作</h3>
      <p class="sim-event-summary">点击中间时间线，查看完整字段、效果链和当前回合兵力快照。</p>
      ${snapshotMarkup(snapshots)}
    `;
  }
  if (selected.kind === "action") {
    const action = (firstRun.replayActions || []).find(
      (item) => Number(item.actionSeq) === selected.actionSeq,
    );
    if (!action) return snapshotMarkup(snapshots);
    return `
      <span class="sim-kicker">SERVER ACTION #${action.actionSeq}</span>
      <h3>Action ${action.actionId} · ${escapeHtml(action.encoded)}</h3>
      <div class="sim-detail-grid">
        ${detailItem("actionSeq", action.actionSeq)}
        ${detailItem("actionId", action.actionId)}
        ${detailItem("base36", Number(action.actionId).toString(36))}
        ${detailItem("params", JSON.stringify(action.params || []))}
      </div>
      <pre class="sim-detail-json">${escapeHtml(JSON.stringify(action, null, 2))}</pre>
      ${snapshotMarkup(snapshots)}
    `;
  }
  const event = (firstRun.events || []).find(
    (item) => Number(item.eventSeq) === selected.eventSeq,
  );
  if (!event) return snapshotMarkup(snapshots);
  const lookup = replayEventDetail(firstRun, event.eventSeq);
  const chains = lookup.effectChains?.length
    ? lookup.effectChains
    : (analysis?.effectChains || buildEffectChains(firstRun.events || [])).filter(
        (chain) => chain.eventSeqs.includes(Number(event.eventSeq)),
      );
  return `
    <span class="sim-kicker">SEMANTIC EVENT #${event.eventSeq}</span>
    <h3>${escapeHtml(event.type)} · ${escapeHtml(eventSummary(event))}</h3>
    <div class="sim-detail-grid">
      ${detailItem("phase / round", `${event.phase} / ${event.round}`)}
      ${detailItem("source", refLabel(event.source) || "-")}
      ${detailItem("target", refLabel(event.target) || "-")}
      ${detailItem("rootSkillId", event.rootSkillId || 0)}
      ${detailItem("skillId", event.skillId || 0)}
      ${detailItem("effectId", event.effectId || 0)}
      ${detailItem("trigger", event.trigger || "-")}
      ${detailItem("value", eventValue(event) || "-")}
    </div>
    ${
      chains.length
        ? `<pre class="sim-detail-json">${escapeHtml(
            chains
              .map(
                (chain) =>
                  `EFFECT ${chain.effectId} · ${chain.lifecycle.join(" → ")} · #${chain.eventSeqs.join(
                    ",#",
                  )}`,
              )
              .join("\n"),
          )}</pre>`
        : ""
    }
    ${
      lookup.replayActions.length
        ? `<span class="sim-kicker">CORRELATED SERVER ACTIONS</span>
          <div class="sim-correlated-actions">
            ${lookup.replayActions
              .map(
                (action) => `
                  <button type="button" data-sim-action="select-action"
                    data-action-seq="${action.actionSeq}">
                    <code>#${action.actionSeq}</code>
                    <strong>ID ${action.actionId}</strong>
                    <span>${escapeHtml(action.encoded)}</span>
                  </button>`,
              )
              .join("")}
          </div>`
        : `<div class="sim-correlation-empty">没有足够参数证据关联到唯一 Server action</div>`
    }
    <pre class="sim-detail-json">${escapeHtml(JSON.stringify(event.payload || event, null, 2))}</pre>
    ${snapshotMarkup(snapshots)}
  `;
}

function detailItem(label, value) {
  return `
    <div class="sim-detail-item"><span>${escapeHtml(label)}</span>
      <code>${escapeHtml(value)}</code></div>
  `;
}

function snapshotMarkup(snapshots) {
  if (!snapshots.length) return "";
  return `
    <span class="sim-kicker">ROUND SNAPSHOT</span>
    <div class="sim-snapshot-grid">
      ${snapshots
        .map(
          (item) => `
            <div class="sim-snapshot">
              <strong>${escapeHtml(refLabel(item))}</strong>
              <span>兵力 ${formatNumber(item.troops)} · 本回合损失 ${formatNumber(
                item.roundDamageTaken,
              )}</span><br>
              <span>累计损失 ${formatNumber(item.cumulativeDamageTaken)} · 恢复 ${formatNumber(
                item.cumulativeRecovery,
              )}</span>
            </div>`,
        )
        .join("")}
    </div>
  `;
}

function payloadFromState() {
  const mapTeam = (team) => ({
    morale: team.morale,
    heros: team.heroes.map((hero) => ({
      id: hero.id,
      position: hero.position,
      level: hero.level,
      up: hero.up,
      equip_skills: hero.equip_skills.filter((skillId) => skillId > 0),
    })),
  });
  return {
    repeat: browserRuntime.state.repeat,
    seed:
      browserRuntime.state.seedMode === "random"
        ? Math.floor(Math.random() * 2_000_000_000)
        : browserRuntime.state.seed,
    blue: mapTeam(browserRuntime.state.attacker),
    red: mapTeam(browserRuntime.state.defender),
  };
}

async function runSimulation(repeat = browserRuntime.state.repeat) {
  if (repeat !== browserRuntime.state.repeat) {
    dispatch({ type: "setRepeat", repeat }, false);
    renderWorkbench();
  }
  const ownerToken = simulatorLoadStates.acquire("simulation");
  const request = createSimulationRequestSnapshot({
    revision: ++browserRuntime.requestRevision,
    stateRevision: browserRuntime.stateRevision,
    repeat,
    payload: payloadFromState(),
    sourceContext: browserRuntime.sourceContext,
  });
  const button = document.getElementById("sim-run-button");
  if (button) button.disabled = true;
  browserRuntime.loading = true;
  renderSimulationLoadState(
    ownerToken,
    browserRuntime.state.result ? "refreshing" : "loading",
  );
  setStatus(`正在调用 Kotlin 引擎执行 ${repeat} 次模拟…`);
  try {
    const response = await fetchJson("/api/simulate", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request.payload),
    });
    if (!shouldCommitSimulationRequest(
      request,
      {
        revision: browserRuntime.requestRevision,
        stateRevision: browserRuntime.stateRevision,
      },
    )) return null;
    dispatch({ type: "setResult", result: response }, false);
    dispatch({ type: "setResultView", view: "summary" }, false);
    dispatch({ type: "setActiveRound", round: 0 }, false);
    browserRuntime.selectedReplayItem = null;
    browserRuntime.replayMode = "semantic";
    renderWorkbench();
    setStatus(`模拟完成 · ${repeat} 次 · ${response.engine || "stzb-kotlin"}`, "success");
    const completionEvent = simulationCompletionEvent(
      response,
      request.repeat,
      request.sourceContext,
    );
    window.HudSystem?.emit({
      ...completionEvent,
      message:
        completionEvent.severity === "warning"
          ? "模拟完成，存在未支持效果"
          : `模拟完成 · ${repeat} 次`,
      timestamp: Date.now(),
    });
    window.dispatchEvent(
      new CustomEvent("stzb:simulation-completed", {
        detail: {
          evidenceClass: "SIMULATION",
          repeat: request.repeat,
          engine: response.engine || "stzb-kotlin",
          response,
          sourceContext: clone(request.sourceContext),
          notice: "模拟结果来自 Kotlin 战斗引擎，不等同于真实历史胜率。",
        },
      }),
    );
    renderSimulationLoadState(ownerToken, "success");
    return response;
  } catch (error) {
    if (!shouldCommitSimulationRequest(
      request,
      {
        revision: browserRuntime.requestRevision,
        stateRevision: browserRuntime.stateRevision,
      },
    )) return null;
    setStatus(`模拟失败：${error.message}`, "error");
    renderSimulationLoadState(
      ownerToken,
      "error",
      error?.message || "模拟失败",
      !browserRuntime.state.result,
    );
    throw error;
  } finally {
    releaseSimulatorLoadState("simulation", ownerToken);
    if (!simulatorLoadStates.isBusy("simulation")) {
      browserRuntime.loading = false;
      document.getElementById("sim-run-button")?.removeAttribute("disabled");
    }
  }
}

function renderSimulationLoadState(ownerToken, kind, message, replace) {
  return renderSimulatorLoadState("simulation", ownerToken, {
    kind,
    message,
    replace,
    busy: ["loading", "refreshing"].includes(kind),
    actionLabel: kind === "error" ? "重试" : "",
    action: kind === "error" ? () => runSimulation() : undefined,
  });
}

function renderSimulatorLoadState(owner, ownerToken, model) {
  simulatorLoadStates.update(owner, ownerToken, model);
  return renderCurrentSimulatorLoadState();
}

function releaseSimulatorLoadState(owner, ownerToken) {
  simulatorLoadStates.release(owner, ownerToken);
  return renderCurrentSimulatorLoadState();
}

function renderCurrentSimulatorLoadState() {
  const visibleState = simulatorLoadStates.current();
  const busy = (
    simulatorLoadStates.isBusy("simulation")
    || simulatorLoadStates.isBusy("initialization")
  );
  const workbench = document.getElementById("sim-workbench");
  if (busy) workbench?.setAttribute("aria-busy", "true");
  else workbench?.removeAttribute("aria-busy");
  return window.HudSystem?.renderState(
    document.getElementById("sim-loader-state"),
    {
      kind: visibleState.kind,
      message: visibleState.message,
      replace: visibleState.replace,
      actionLabel: visibleState.actionLabel,
      action: visibleState.action,
    },
  );
}

function renderInitializationState(model) {
  const action = typeof model.action === "function"
    ? () => Promise.resolve()
      .then(model.action)
      .catch((error) => {
        window.HudSystem?.toast({
          severity: "error",
          title: error?.message || "战斗模拟工作台初始化失败",
          dedupeKey: "simulator:initialization:retry",
        });
      })
    : undefined;
  return renderSimulatorLoadState("initialization", model.ownerToken, {
    ...model,
    action,
  });
}

function openLibrary(kind, selectedSlot) {
  browserRuntime.libraryKind = kind;
  browserRuntime.libraryQuery = "";
  browserRuntime.libraryFilter = "";
  browserRuntime.libraryQuality = "";
  dispatch(
    {
      type: "openDrawer",
      drawer: { kind },
      selectedSlot,
    },
    false,
  );
  renderLibraryDialog();
  const dialog = document.getElementById("sim-library-dialog");
  if (dialog && !dialog.open) dialog.showModal();
}

function renderLibraryDialog() {
  const dialog = document.getElementById("sim-library-dialog");
  if (!dialog) return;
  dialog.innerHTML = `
    <div class="sim-dialog-shell">
      <div class="sim-dialog-head">
        <div><span class="sim-kicker">TACTICAL LIBRARY</span><h2 id="sim-library-title">武将与战法库</h2></div>
        <button class="sim-icon-button" type="button" data-sim-action="close-dialog">关闭</button>
      </div>
      <div class="sim-library-tabs">
        ${libraryTab("hero", "武将")}
        ${libraryTab("skill", "战法")}
        <button type="button" data-sim-action="open-templates">模板</button>
      </div>
      <div class="sim-library-content">
        ${libraryToolbarMarkup()}
        <div id="sim-library-list" class="sim-library-list">${libraryListMarkup()}</div>
      </div>
    </div>
  `;
}

function libraryTab(kind, label) {
  return `
    <button type="button" class="${browserRuntime.libraryKind === kind ? "is-active" : ""}"
      data-sim-action="set-library-kind" data-kind="${kind}">${label}</button>
  `;
}

function libraryToolbarMarkup() {
  const hero = browserRuntime.libraryKind === "hero";
  const options = hero
    ? CAMP_NAMES.map((name, index) =>
        index && name ? `<option value="${index}">${name}</option>` : "",
      ).join("")
    : SKILL_TYPE_NAMES.map((name, index) =>
        index && name ? `<option value="${index}">${name}</option>` : "",
      ).join("");
  return `
    <div class="sim-library-toolbar">
      <input type="search" placeholder="搜索名称或 ID" value="${escapeHtml(
        browserRuntime.libraryQuery,
      )}" data-sim-input="library-query" autofocus>
      <select data-sim-change="library-filter">
        <option value="">${hero ? "全部阵营" : "全部类型"}</option>${options}
      </select>
      <select data-sim-change="library-quality">
        <option value="">全部品质</option>
        ${[6, 5, 4, 3, 2, 1]
          .map((quality) => `<option value="${quality}">${quality} / ${quality}星</option>`)
          .join("")}
      </select>
    </div>
  `;
}

function libraryListMarkup() {
  const query = browserRuntime.libraryQuery.trim().toLowerCase();
  const filter = Number(browserRuntime.libraryFilter || 0);
  const quality = String(browserRuntime.libraryQuality || "");
  const items =
    browserRuntime.libraryKind === "hero" ? browserRuntime.heroes : browserRuntime.skills;
  const filtered = items
    .filter((item) => {
      const matchesQuery =
        !query ||
        String(item.id).includes(query) ||
        String(item.name || "").toLowerCase().includes(query);
      const matchesFilter =
        !filter ||
        (browserRuntime.libraryKind === "hero"
          ? Number(item.camp) === filter
          : Number(item.skill_type) === filter);
      const itemQuality = String(item.quality || item.level || "");
      const matchesQuality = !quality || itemQuality === quality;
      return matchesQuery && matchesFilter && matchesQuality;
    })
    .slice(0, 160);
  if (!filtered.length) return `<div class="sim-empty-state">没有匹配条目</div>`;
  return filtered
    .map((item) =>
      browserRuntime.libraryKind === "hero"
        ? heroLibraryItem(item)
        : skillLibraryItem(item),
    )
    .join("");
}

function heroLibraryItem(hero) {
  const portrait = portraitPresentation(hero, "lazy");
  const portraitStep =
    hero.portraitLocal === false &&
    portrait.src === hero.portraitFallbackUrl
      ? "cdn"
      : "local";
  return `
    <button class="sim-library-item" type="button" data-sim-action="choose-library-item"
      data-kind="hero" data-id="${hero.id}">
      <span class="sim-library-portrait">
        <img data-sim-portrait data-portrait-step="${portraitStep}"
          data-fallback-src="${escapeHtml(portrait.fallbackSrc)}"
          data-placeholder-src="${escapeHtml(portrait.placeholderSrc)}"
          src="${escapeHtml(portrait.src)}"
          alt="${escapeHtml(portrait.alt)}"
          loading="${portrait.loading}" decoding="async">
      </span>
      <span><strong>${escapeHtml(hero.name)}</strong>
        <small>${hero.id} · ${CAMP_NAMES[hero.camp] || "未知"} · ${
          ARMY_NAMES[hero.army] || "未知兵种"
        } · 距离 ${hero.range || "-"}</small></span>
      <small>${hero.quality || "?"}星</small>
    </button>
  `;
}

function skillLibraryItem(skill) {
  return `
    <button class="sim-library-item" type="button" data-sim-action="choose-library-item"
      data-kind="skill" data-id="${skill.id}">
      <span class="sim-library-avatar">${escapeHtml(
        (SKILL_TYPE_NAMES[skill.skill_type] || "战").slice(0, 1),
      )}</span>
      <span><strong>${escapeHtml(skill.name)}</strong>
        <small>${skill.id} · ${SKILL_TYPE_NAMES[skill.skill_type] || "未知"} · 距离 ${
          skill.range || "-"
        } · ${escapeHtml(skill.target || "目标按配置")}</small></span>
      <small>${escapeHtml(skill.level || skill.quality || "-")}</small>
    </button>
  `;
}

function chooseLibraryItem(kind, id) {
  const selected = browserRuntime.state.selectedSlot;
  if (!selected) return;
  if (kind === "hero") {
    dispatch(
      {
        type: "setHero",
        side: selected.side,
        position: selected.position,
        hero: {
          id,
          position: selected.position,
          level: 40,
          up: 5,
          equip_skills: [0, 0],
        },
      },
      false,
    );
  } else {
    dispatch(
      {
        type: "setHeroSkill",
        side: selected.side,
        position: selected.position,
        slot: selected.slot,
        skillId: id,
      },
      false,
    );
  }
  closeDialogs();
  renderWorkbench();
}

function loadTemplates() {
  try {
    const parsed = JSON.parse(localStorage.getItem(TEMPLATE_STORAGE_KEY) || "[]");
    return Array.isArray(parsed)
      ? parsed
          .map((item) => {
            try {
              return parseTemplate(item);
            } catch {
              return null;
            }
          })
          .filter(Boolean)
      : [];
  } catch {
    return [];
  }
}

function saveTemplates(templates) {
  localStorage.setItem(TEMPLATE_STORAGE_KEY, JSON.stringify(templates));
}

function renderTemplateDialog() {
  const dialog = document.getElementById("sim-template-dialog");
  if (!dialog) return;
  const templates = loadTemplates();
  dialog.innerHTML = `
    <div class="sim-dialog-shell">
      <div class="sim-dialog-head">
        <div><span class="sim-kicker">LOCAL TEMPLATES</span><h2 id="sim-template-title">阵容模板</h2></div>
        <button class="sim-icon-button" type="button" data-sim-action="close-dialog">关闭</button>
      </div>
      <div class="sim-template-form">
        <label>模板名称<input id="sim-template-name" value="对阵 ${templates.length + 1}"></label>
        <div class="sim-template-actions">
          <button class="sim-run-button" type="button" data-sim-action="save-template" data-scope="matchup">保存完整对阵</button>
          <button class="sim-ghost-button" type="button" data-sim-action="save-template" data-scope="attacker">只保存攻方</button>
          <button class="sim-ghost-button" type="button" data-sim-action="save-template" data-scope="defender">只保存守方</button>
        </div>
        <label>导入 / 导出 JSON
          <textarea id="sim-template-json" rows="6" placeholder="粘贴模板 JSON，或点击条目的导出"></textarea>
        </label>
        <div class="sim-template-actions">
          <button class="sim-ghost-button" type="button" data-sim-action="import-template">导入 JSON</button>
        </div>
      </div>
      <div class="sim-template-list">
        ${
          templates.length
            ? templates.map((template, index) => templateItemMarkup(template, index)).join("")
            : `<div class="sim-empty-state">还没有本地模板</div>`
        }
      </div>
    </div>
  `;
  if (!dialog.open) dialog.showModal();
}

function templateItemMarkup(template, index) {
  return `
    <div class="sim-template-item">
      <span class="sim-library-avatar">${template.scope === "matchup" ? "VS" : "队"}</span>
      <button type="button" class="sim-template-main" data-sim-action="load-template"
        data-template-index="${index}">
        <strong>${escapeHtml(template.name)}</strong>
        <small>${escapeHtml(template.scope)} · ×${template.repeat}</small>
      </button>
      <span>
        <button class="sim-icon-button" type="button" data-sim-action="export-template"
          data-template-index="${index}">导出</button>
        <button class="sim-icon-button" type="button" data-sim-action="delete-template"
          data-template-index="${index}">删除</button>
      </span>
    </div>
  `;
}

function saveCurrentTemplate(scope) {
  const input = document.getElementById("sim-template-name");
  const template = serializeTemplate(
    browserRuntime.state,
    scope,
    input?.value || "未命名模板",
  );
  const templates = loadTemplates();
  templates.push(template);
  saveTemplates(templates);
  renderTemplateDialog();
}

export function loadTemplateAt(index, dependencies = {}) {
  const loadTemplatesFn = dependencies.loadTemplates || loadTemplates;
  const dispatchFn = dependencies.dispatch || dispatch;
  const closeDialogsFn = dependencies.closeDialogs || closeDialogs;
  const renderWorkbenchFn = dependencies.renderWorkbench || renderWorkbench;
  const template = loadTemplatesFn()[index];
  if (!template) return;
  dispatchFn({ type: "loadTemplate", template }, false);
  closeDialogsFn();
  renderWorkbenchFn();
}

function importTemplateFromDialog() {
  const textarea = document.getElementById("sim-template-json");
  const parsed = parseTemplate(textarea?.value || "");
  const templates = loadTemplates();
  templates.push(parsed);
  saveTemplates(templates);
  renderTemplateDialog();
}

function closeDialogs() {
  for (const id of ["sim-library-dialog", "sim-template-dialog"]) {
    const dialog = document.getElementById(id);
    if (dialog?.open) dialog.close();
  }
  dispatch({ type: "closeDrawer" }, false);
}

function handleClick(event) {
  const trigger = event.target.closest("[data-sim-action]");
  if (!trigger) return;
  const action = trigger.dataset.simAction;
  const side = trigger.dataset.side;
  const position = Number(trigger.dataset.position);
  if (action === "clear-skill") {
    event.stopPropagation();
    dispatch({
      type: "clearHeroSkill",
      side,
      position,
      slot: Number(trigger.dataset.slot),
    });
  } else if (action === "open-library") {
    openLibrary(trigger.dataset.kind, {
      side,
      position,
      slot: Number(trigger.dataset.slot || 0),
    });
  } else if (action === "remove-hero") {
    try {
      dispatch({ type: "removeHero", side, position });
    } catch (error) {
      setStatus(error.message, "error");
    }
  } else if (action === "copy-side") {
    dispatch({ type: "copySide", from: trigger.dataset.from, to: trigger.dataset.to });
  } else if (action === "set-repeat") {
    dispatch({ type: "setRepeat", repeat: Number(trigger.dataset.repeat) });
  } else if (action === "swap-sides") {
    dispatch({ type: "swapSides" });
  } else if (action === "reset") {
    simulatorInitializer.invalidate();
    browserRuntime.requestRevision += 1;
    browserRuntime.loading = false;
    dispatch({ type: "reset" }, false);
    browserRuntime.sourceContext = null;
    browserRuntime.selectedReplayItem = null;
    renderWorkbench();
    const ownerToken = simulatorLoadStates.acquire("simulation");
    renderSimulationLoadState(ownerToken, "success");
    releaseSimulatorLoadState("simulation", ownerToken);
    initializeWorkbench().catch(() => {});
  } else if (action === "run") {
    runSimulation().catch(() => {});
  } else if (action === "open-replay") {
    if (!browserRuntime.state.result) {
      setStatus("请先完成一次模拟", "error");
      return;
    }
    dispatch({ type: "setResultView", view: "semantic" });
  } else if (action === "set-result-view") {
    const view = trigger.dataset.view;
    browserRuntime.replayMode = view === "actions" ? "actions" : "semantic";
    dispatch({ type: "setResultView", view });
  } else if (action === "focus-evidence") {
    const round = Number(trigger.dataset.round || 0);
    const eventSeq = Number(trigger.dataset.eventSeq);
    browserRuntime.selectedReplayItem = Number.isInteger(eventSeq)
      ? { kind: "event", eventSeq }
      : null;
    dispatch({ type: "setActiveRound", round }, false);
    dispatch({ type: "setResultView", view: "semantic" });
  } else if (action === "set-round") {
    dispatch({ type: "setActiveRound", round: Number(trigger.dataset.round) });
  } else if (action === "set-replay-mode") {
    browserRuntime.replayMode = trigger.dataset.mode;
    const firstRun = firstRunFromResponse(browserRuntime.state.result);
    renderReplay(firstRun, analyzeReplay(firstRun));
  } else if (action === "set-event-filter") {
    browserRuntime.eventFilter = trigger.dataset.filter;
    const firstRun = firstRunFromResponse(browserRuntime.state.result);
    renderReplay(firstRun, analyzeReplay(firstRun));
  } else if (action === "select-event") {
    browserRuntime.selectedReplayItem = {
      kind: "event",
      eventSeq: Number(trigger.dataset.eventSeq),
    };
    const firstRun = firstRunFromResponse(browserRuntime.state.result);
    renderReplay(firstRun, analyzeReplay(firstRun));
  } else if (action === "select-action") {
    browserRuntime.selectedReplayItem = {
      kind: "action",
      actionSeq: Number(trigger.dataset.actionSeq),
    };
    const firstRun = firstRunFromResponse(browserRuntime.state.result);
    renderReplay(firstRun, analyzeReplay(firstRun));
  } else if (action === "close-dialog") {
    closeDialogs();
  } else if (action === "set-library-kind") {
    browserRuntime.libraryKind = trigger.dataset.kind;
    browserRuntime.libraryFilter = "";
    browserRuntime.libraryQuality = "";
    renderLibraryDialog();
  } else if (action === "choose-library-item") {
    chooseLibraryItem(trigger.dataset.kind, Number(trigger.dataset.id));
  } else if (action === "open-templates") {
    const library = document.getElementById("sim-library-dialog");
    if (library?.open) library.close();
    renderTemplateDialog();
  } else if (action === "save-template") {
    saveCurrentTemplate(trigger.dataset.scope);
  } else if (action === "load-template") {
    loadTemplateAt(Number(trigger.dataset.templateIndex));
  } else if (action === "delete-template") {
    const templates = loadTemplates();
    templates.splice(Number(trigger.dataset.templateIndex), 1);
    saveTemplates(templates);
    renderTemplateDialog();
  } else if (action === "export-template") {
    const template = loadTemplates()[Number(trigger.dataset.templateIndex)];
    const textarea = document.getElementById("sim-template-json");
    if (template && textarea) textarea.value = JSON.stringify(template, null, 2);
  } else if (action === "import-template") {
    try {
      importTemplateFromDialog();
    } catch (error) {
      setStatus(`模板导入失败：${error.message}`, "error");
    }
  }
}

function handleChange(event) {
  const target = event.target;
  const action = target.dataset.simChange;
  if (!action) return;
  if (action === "morale") {
    dispatch({
      type: "setMorale",
      side: target.dataset.side,
      morale: Number(target.value),
    });
  } else if (action === "hero-level") {
    dispatch({
      type: "setHeroLevel",
      side: target.dataset.side,
      position: Number(target.dataset.position),
      level: Number(target.value),
    });
  } else if (action === "hero-advance") {
    dispatch({
      type: "setHeroAdvance",
      side: target.dataset.side,
      position: Number(target.dataset.position),
      advance: Number(target.value),
    });
  } else if (action === "seed") {
    dispatch({ type: "setSeed", seed: Number(target.value), seedMode: "fixed" });
  } else if (action === "library-filter") {
    browserRuntime.libraryFilter = target.value;
    document.getElementById("sim-library-list").innerHTML = libraryListMarkup();
  } else if (action === "library-quality") {
    browserRuntime.libraryQuality = target.value;
    document.getElementById("sim-library-list").innerHTML = libraryListMarkup();
  }
}

function handleInput(event) {
  if (event.target.dataset.simInput === "library-query") {
    browserRuntime.libraryQuery = event.target.value;
    document.getElementById("sim-library-list").innerHTML = libraryListMarkup();
  }
}

function handlePortraitError(event) {
  const image = event.target;
  if (
    typeof HTMLImageElement === "undefined" ||
    !(image instanceof HTMLImageElement) ||
    !image.matches("[data-sim-portrait]")
  ) {
    return;
  }
  advancePortraitFallback(image);
}

function handleKeyboard(event) {
  const editable = ["INPUT", "TEXTAREA", "SELECT"].includes(document.activeElement?.tagName);
  if ((event.metaKey || event.ctrlKey) && event.key === "Enter") {
    event.preventDefault();
    runSimulation().catch(() => {});
    return;
  }
  if (event.key === "Escape") {
    closeDialogs();
    return;
  }
  if (!editable && ["1", "2", "3"].includes(event.key)) {
    const repeat = { 1: 1, 2: 100, 3: 1000 }[event.key];
    dispatch({ type: "setRepeat", repeat });
  }
}

const simulatorInitializer = createSimulatorInitializer({
  hasState: () => Boolean(browserRuntime.state),
  prepareState() {
    browserRuntime.state = createSimulatorState();
    renderWorkbench();
    renderEngineBadge();
  },
  getState: () => browserRuntime.state,
  loadCatalog: () => fetchJson("/api/simulate/heroes"),
  loadEngine: () => fetchJson("/api/simulate/engine"),
  acquireState: () => simulatorLoadStates.acquire("initialization"),
  releaseState: (ownerToken) => {
    releaseSimulatorLoadState("initialization", ownerToken);
  },
  commit({ catalog, engine }) {
    browserRuntime.heroes = (catalog.heroes || []).slice();
    browserRuntime.skills = (catalog.skills || []).slice();
    browserRuntime.heroById = new Map(
      browserRuntime.heroes.map((hero) => [Number(hero.id), hero]),
    );
    browserRuntime.skillById = new Map(
      browserRuntime.skills.map((skill) => [Number(skill.id), skill]),
    );
    browserRuntime.engine = engine;
    browserRuntime.initialized = true;
    renderEngineBadge();
    renderWorkbench();
  },
  renderState(model) {
    if (model.kind === "error") {
      renderEngineBadge(model.message);
      setStatus(model.message, "error");
    }
    renderInitializationState(model);
  },
});

function initializeWorkbench() {
  return simulatorInitializer.initialize();
}

async function loadLineup(lineup, options = {}) {
  browserRuntime.stateRevision += 1;
  await initializeWorkbench();
  const side = options.camp === "red" ? "defender" : "attacker";
  dispatch({ type: "loadLineup", side, lineup }, false);
  browserRuntime.sourceContext = createSourceContext(options);
  renderWorkbench();
  return getWorkbenchState();
}

function getWorkbenchState() {
  return {
    ...clone(browserRuntime.state),
    blue: clone(browserRuntime.state.attacker.heroes),
    red: clone(browserRuntime.state.defender.heroes),
    sourceContext: clone(browserRuntime.sourceContext),
  };
}

function installBrowserController() {
  const root = document.getElementById("sim-workbench");
  if (!root || root.dataset.controllerReady === "1") return;
  root.dataset.controllerReady = "1";
  root.addEventListener("click", handleClick);
  root.addEventListener("change", handleChange);
  root.addEventListener("input", handleInput);
  document.addEventListener("keydown", handleKeyboard);
  document.addEventListener("error", handlePortraitError, true);
}

if (typeof window !== "undefined" && typeof document !== "undefined") {
  installBrowserController();
  window.initSimulator = initializeWorkbench;
  window.runSimulate = runSimulation;
  window.StzbSimulator = {
    init: initializeWorkbench,
    loadLineup,
    getState: getWorkbenchState,
    run: runSimulation,
  };
  window.SimulatorWorkbench = {
    init: initializeWorkbench,
    dispatch,
    getState: getWorkbenchState,
  };
}

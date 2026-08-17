const PHASE_BY_SKILL_TYPE = {
  10: "PREPARATION",
  9: "PREPARATION",
  1: "ACTIVE",
  2: "ACTIVE",
  3: "ACTIVE",
  14: "ATTACK",
  16: "CHASE",
};

const CONFIG_PHASE_ORDER = new Map([
  ["PREPARATION", 0],
  ["ACTIVE", 1],
  ["ATTACK", 2],
  ["CHASE", 3],
  ["OTHER", 4],
]);

function collectionValue(collection, key) {
  if (collection instanceof Map) return collection.get(Number(key));
  if (collection && typeof collection === "object") {
    return collection[key] ?? collection[String(key)];
  }
  return undefined;
}

function numeric(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function projectDetails(details) {
  if (!Array.isArray(details)) return [];
  return details.map((detail) => ({
    detailId: numeric(detail?.detail_id),
    effectId: numeric(detail?.effect_id),
    effectName: String(detail?.effect_name || detail?.effect?.name || ""),
    constantParam: numeric(detail?.constant_param),
    intelParam: numeric(detail?.intel_param),
    availableRound: numeric(detail?.available_round),
    targetType: numeric(detail?.target_type),
    selectType: numeric(detail?.select_type),
  }));
}

function configNode(hero, position, slot, skillId, heroDetail, skillDetail) {
  const skill = skillDetail?.skill || (
    slot === "initial" ? heroDetail?.initialSkill : null
  ) || {};
  const phase = PHASE_BY_SKILL_TYPE[numeric(skill.skill_type)] || "OTHER";
  return {
    nodeId: `config:${position}:${slot}:${skillId}`,
    kind: "config",
    evidenceClass: "CONFIG_FACT",
    phase,
    heroId: numeric(hero?.id ?? hero?.heroId ?? heroDetail?.hero?.heroid),
    heroName: String(heroDetail?.hero?.name || hero?.name || ""),
    position,
    slot,
    skillId,
    skillName: String(skill.name || ""),
    prepareRounds: numeric(skill.prepare),
    probability: numeric(skill.probability_init),
    targetDescription: String(skill.target_description || ""),
    hitRange: numeric(skill.hit_range),
    mainEffectName: String(skill.main_effect_name || ""),
    details: projectDetails(skillDetail?.details),
    unresolvedDescription: Boolean(skillDetail?.unresolvedDescription),
  };
}

export function buildConfigSkillChain(lineup, heroDetails, skillDetails) {
  const heroes = Array.isArray(lineup?.heroes) ? lineup.heroes : [];
  const nodes = [];
  let sequence = 0;

  heroes.forEach((hero, index) => {
    const heroId = numeric(hero?.id ?? hero?.heroId);
    const position = Number.isInteger(Number(hero?.position))
      ? Number(hero.position)
      : index;
    const heroDetail = collectionValue(heroDetails, heroId);
    const initialSkillId = numeric(
      heroDetail?.initialSkill?.skill_id ?? heroDetail?.hero?.skill_init,
    );
    if (initialSkillId > 0) {
      nodes.push({
        sequence,
        node: configNode(
          hero,
          position,
          "initial",
          initialSkillId,
          heroDetail,
          collectionValue(skillDetails, initialSkillId),
        ),
      });
      sequence += 1;
    }

    const optionalSkillIds = Array.isArray(hero?.equip_skills)
      ? hero.equip_skills
      : Array.isArray(hero?.extraSkillIds)
        ? hero.extraSkillIds
        : [];
    optionalSkillIds.forEach((value, slotIndex) => {
      const skillId = numeric(value);
      if (skillId <= 0) return;
      nodes.push({
        sequence,
        node: configNode(
          hero,
          position,
          `optional:${slotIndex}`,
          skillId,
          heroDetail,
          collectionValue(skillDetails, skillId),
        ),
      });
      sequence += 1;
    });
  });

  return nodes
    .sort((left, right) => {
      const phaseDifference =
        CONFIG_PHASE_ORDER.get(left.node.phase) -
        CONFIG_PHASE_ORDER.get(right.node.phase);
      return phaseDifference || left.sequence - right.sequence;
    })
    .map((item) => item.node);
}

function firstRunFromSimulation(simulationResult) {
  return (
    simulationResult?.response?.result?.firstRun ||
    simulationResult?.response?.firstRun ||
    null
  );
}

export function buildSimulationSkillChain(
  simulationResult,
  { replayEventDetailFn = null } = {},
) {
  const firstRun = firstRunFromSimulation(simulationResult);
  const events = Array.isArray(firstRun?.events) ? firstRun.events : [];

  return events
    .filter((event) => event && typeof event === "object")
    .slice()
    .sort((left, right) => numeric(left.eventSeq) - numeric(right.eventSeq))
    .map((event) => {
      const eventSeq = numeric(event.eventSeq);
      const replayActions = (
        typeof replayEventDetailFn === "function"
          ? replayEventDetailFn(firstRun, eventSeq)?.replayActions
          : []
      )
        ?.filter((action) => action && typeof action === "object")
        .map((action) => ({
          actionSeq: numeric(action.actionSeq),
          actionId: numeric(action.actionId),
          encoded: String(action.encoded || ""),
        })) || [];
      return {
        nodeId: `event:${eventSeq}`,
        kind: "simulation",
        evidenceClass: "SIMULATION",
        eventSeq,
        phase: String(event.phase || ""),
        round: numeric(event.round),
        type: String(event.type || ""),
        source: event.source ?? null,
        target: event.target ?? null,
        skillId: numeric(event.skillId),
        rootSkillId: numeric(event.rootSkillId),
        effectId: numeric(event.effectId),
        replayActions,
        warning: String(event.type || "").startsWith("Unsupported"),
      };
    });
}

function groupKey(node) {
  const phase = String(node?.phase || "OTHER");
  const round = numeric(node?.round);
  if (phase === "PREPARATION" || phase === "FINAL") return phase;
  if (round > 0) return `ROUND:${round}`;
  return phase;
}

export function groupSkillChainByPhase(nodes) {
  if (!Array.isArray(nodes)) return [];
  const groups = new Map();
  for (const node of nodes) {
    const key = groupKey(node);
    if (!groups.has(key)) groups.set(key, { key, nodes: [] });
    groups.get(key).nodes.push(node);
  }
  return [...groups.values()];
}

export function findSkillChainNode(nodes, nodeId) {
  if (!Array.isArray(nodes)) return null;
  return nodes.find((node) => node?.nodeId === nodeId) || null;
}

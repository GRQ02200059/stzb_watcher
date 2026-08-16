const DAMAGE_TYPES = new Set([
  "NormalAttack",
  "SkillDamage",
  "OngoingDamage",
]);

const EFFECT_TYPES = new Set([
  "StatusApplied",
  "StatChanged",
  "ModifierApplied",
  "SkillRangeChanged",
  "EffectBlocked",
  "StatusRemoved",
  "EffectExpired",
]);

const PREPARATION_STAGE_MARKERS = [
  { key: "INITIALIZATION", label: "初始化", actionIds: new Set([654, 641]) },
  { key: "SYSTEM", label: "系统效果", actionIds: new Set([631]) },
  { key: "COUNTRY", label: "阵营 / 国家", actionIds: new Set([632]) },
  { key: "ARMY", label: "部队组合", actionIds: new Set([640]) },
  { key: "TROOP", label: "兵种效果", actionIds: new Set([635]) },
  { key: "EQUIPMENT", label: "装备效果", actionIds: new Set([634]) },
  { key: "SURFACE", label: "外观 / 进阶", actionIds: new Set([639, 636]) },
  { key: "PASSIVE", label: "被动战法", actionIds: new Set([637]) },
  { key: "COMMAND", label: "指挥战法", actionIds: new Set([4, 651]) },
];

const EVENT_ACTION_FAMILIES = {
  RoundStart: new Set([9]),
  HeroActionStart: new Set([10]),
  HeroActionEnd: new Set([11]),
  SkillPreparationStarted: new Set([25]),
  SkillPreparationCancelled: new Set([27]),
  NormalAttack: new Set([119, 121, 213, 214]),
  SkillDamage: new Set([59, 60, 213, 214, 301]),
  OngoingDamage: new Set([62, 242, 243]),
  Recovery: new Set([63, 64, 202, 213, 214]),
  EffectBlocked: new Set([110, 210, 337, 338, 339, 340]),
  Evaded: new Set([110]),
  StatChanged: new Set([31, 32, 33, 34, 35, 36, 45, 46, 47, 48, 49, 50, 52, 53, 54, 55]),
  ModifierApplied: new Set([694]),
  SkillRangeChanged: new Set([36]),
  SkillTriggered: new Set([21, 22, 23, 24, 300, 301]),
  BattleEnd: new Set([13, 127, 206, 207, 224]),
};

function eventSide(event, field) {
  const ref = event?.[field];
  return ref && typeof ref.side === "string" ? ref.side : "";
}

function number(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function normalizeEvents(firstRun) {
  return Array.isArray(firstRun?.events)
    ? firstRun.events
        .filter((event) => event && typeof event === "object")
        .slice()
        .sort((left, right) => number(left.eventSeq) - number(right.eventSeq))
    : [];
}

function effectKey(event) {
  const target = event?.target || {};
  return [
    event?.skillId || 0,
    event?.effectId || 0,
    target.side || "",
    target.position ?? "",
    target.heroId || 0,
  ].join(":");
}

function clientPosition(ref) {
  if (!ref || !Number.isInteger(Number(ref.position))) return null;
  return ref.side === "ATTACKER"
    ? Number(ref.position) + 1
    : 6 - Number(ref.position);
}

function groupPreparationActions(firstRun) {
  const actions = Array.isArray(firstRun?.replayActions)
    ? firstRun.replayActions
    : [];
  const stages = PREPARATION_STAGE_MARKERS.map((stage) => ({
    key: stage.key,
    label: stage.label,
    actions: [],
  }));
  let stageIndex = 0;
  for (const action of actions) {
    const actionId = number(action?.actionId);
    if (actionId === 9 || actionId === 10) break;
    for (let index = stageIndex + 1; index < PREPARATION_STAGE_MARKERS.length; index += 1) {
      if (PREPARATION_STAGE_MARKERS[index].actionIds.has(actionId)) {
        stageIndex = index;
        break;
      }
    }
    stages[stageIndex].actions.push(action);
    if (stageIndex === stages.length - 1 && actionId === 8) break;
  }
  return stages;
}

function buildActionEnvelopes(events) {
  const actions = [];
  const looseEvents = [];
  let current = null;
  for (const event of events) {
    if (event.type === "HeroActionStart") {
      if (current) actions.push(current);
      current = {
        source: event.source || null,
        startedAt: number(event.eventSeq),
        endedAt: null,
        events: [event],
      };
      continue;
    }
    if (current) {
      current.events.push(event);
      if (event.type === "HeroActionEnd") {
        current.endedAt = number(event.eventSeq);
        actions.push(current);
        current = null;
      }
      continue;
    }
    looseEvents.push(event);
  }
  if (current) actions.push(current);
  return { actions, looseEvents };
}

export function groupReplayByPhase(firstRun) {
  const events = normalizeEvents(firstRun);
  const preparation = [];
  const final = [];
  const rounds = new Map();

  for (const event of events) {
    const round = number(event.round);
    if (event.phase === "FINAL" || event.type === "BattleEnd") {
      final.push(event);
      continue;
    }
    if (event.phase === "PREPARATION" || round <= 0) {
      preparation.push(event);
      continue;
    }
    if (!rounds.has(round)) {
      rounds.set(round, { round, events: [] });
    }
    rounds.get(round).events.push(event);
  }

  return {
    preparation: {
      phase: "PREPARATION",
      events: preparation,
      stages: groupPreparationActions(firstRun),
    },
    final: { phase: "FINAL", events: final },
    rounds: [...rounds.values()]
      .sort((left, right) => left.round - right.round)
      .map((group) => ({
        ...group,
        ...buildActionEnvelopes(group.events),
      })),
  };
}

function roundActionRange(actions, round) {
  if (round <= 0) {
    const firstRound = actions.findIndex((action) => number(action.actionId) === 9);
    return firstRound < 0 ? actions : actions.slice(0, firstRound);
  }
  const start = actions.findIndex(
    (action) =>
      number(action.actionId) === 9 &&
      number(action.params?.[0]) === round,
  );
  if (start < 0) return [];
  const next = actions.findIndex(
    (action, index) => index > start && number(action.actionId) === 9,
  );
  return actions.slice(start, next < 0 ? actions.length : next);
}

function actionEvidenceScore(event, action) {
  const family = EVENT_ACTION_FAMILIES[event.type];
  const actionId = number(action.actionId);
  if (!family?.has(actionId)) return -1;
  const params = Array.isArray(action.params) ? action.params.map(number) : [];
  const sourcePosition = clientPosition(event.source);
  const targetPosition = clientPosition(event.target);
  const evidence = [
    event.rootSkillId,
    event.skillId,
    event.effectId,
    event.blockingEffectId,
    event.damage,
    event.amount,
    event.targetTroopsAfter,
    sourcePosition,
    targetPosition,
  ]
    .map(number)
    .filter((value) => value > 0);
  const matched = evidence.filter((value) => params.includes(value)).length;
  if (["RoundStart", "HeroActionStart", "HeroActionEnd"].includes(event.type)) {
    return matched > 0 ? 4 + matched : -1;
  }
  if (event.type === "BattleEnd") return 1;
  return matched > 0 ? 2 + matched : -1;
}

export function replayEventDetail(firstRun, eventSeq) {
  const semanticEvent = normalizeEvents(firstRun).find(
    (event) => number(event.eventSeq) === number(eventSeq),
  );
  if (!semanticEvent) {
    return { semanticEvent: null, replayActions: [], effectChains: [] };
  }
  const actions = roundActionRange(
    Array.isArray(firstRun?.replayActions) ? firstRun.replayActions : [],
    number(semanticEvent.round),
  );
  const scored = actions
    .map((action) => ({
      action,
      score: actionEvidenceScore(semanticEvent, action),
    }))
    .filter((item) => item.score >= 0);
  const maximum = scored.reduce(
    (current, item) => Math.max(current, item.score),
    -1,
  );
  const replayActions =
    maximum < 0
      ? []
      : scored
          .filter((item) => item.score === maximum)
          .map((item) => item.action);
  return {
    semanticEvent,
    replayActions,
    effectChains: buildEffectChains(firstRun?.events || []).filter((chain) =>
      chain.eventSeqs.includes(number(eventSeq)),
    ),
  };
}

export function buildEffectChains(events) {
  const chains = new Map();
  for (const event of Array.isArray(events) ? events : []) {
    if (!EFFECT_TYPES.has(event?.type)) continue;
    const key = effectKey(event);
    if (!chains.has(key)) {
      chains.set(key, {
        key,
        source: event.source || null,
        target: event.target || null,
        rootSkillId: number(event.rootSkillId),
        skillId: number(event.skillId),
        effectId: number(event.effectId),
        status: event.status || "",
        lifecycle: [],
        eventSeqs: [],
        events: [],
      });
    }
    const chain = chains.get(key);
    chain.lifecycle.push(event.type);
    chain.eventSeqs.push(number(event.eventSeq));
    chain.events.push(event);
    if (!chain.status && event.status) chain.status = event.status;
  }
  return [...chains.values()].sort(
    (left, right) => left.eventSeqs[0] - right.eventSeqs[0],
  );
}

function buildRoundSummaries(grouped) {
  return grouped.rounds.map((group) => {
    const summary = {
      round: group.round,
      eventSeqs: group.events.map((event) => number(event.eventSeq)),
      attackerDamage: 0,
      defenderDamage: 0,
      attackerRecovery: 0,
      defenderRecovery: 0,
      controlsApplied: 0,
      blockedEffects: 0,
      evades: 0,
      activeSkills: 0,
      heroActions: 0,
      damageEventSeqs: [],
    };
    for (const event of group.events) {
      if (DAMAGE_TYPES.has(event.type)) {
        const side = eventSide(event, "source");
        const key = side === "ATTACKER" ? "attackerDamage" : "defenderDamage";
        summary[key] += number(event.damage);
        summary.damageEventSeqs.push(number(event.eventSeq));
      } else if (event.type === "Recovery") {
        const side = eventSide(event, "target");
        const key = side === "ATTACKER" ? "attackerRecovery" : "defenderRecovery";
        summary[key] += number(event.amount);
      } else if (event.type === "StatusApplied") {
        summary.controlsApplied += 1;
      } else if (event.type === "EffectBlocked") {
        summary.blockedEffects += 1;
      } else if (event.type === "Evaded") {
        summary.evades += 1;
      } else if (event.type === "HeroActionStart") {
        summary.heroActions += 1;
      } else if (
        event.type === "SkillTriggered" &&
        event.trigger === "ACTIVE_SKILL_ATTEMPT"
      ) {
        summary.activeSkills += 1;
      }
    }
    return summary;
  });
}

function buildHeroSummaries(events) {
  const heroes = new Map();
  const ensure = (ref) => {
    if (!ref || !ref.side) return null;
    const key = `${ref.side}:${ref.position}:${ref.heroId}`;
    if (!heroes.has(key)) {
      heroes.set(key, {
        key,
        ...ref,
        damageDealt: 0,
        damageTaken: 0,
        recoveryDone: 0,
        recoveryReceived: 0,
        actions: 0,
        activeSkills: 0,
        controlsApplied: 0,
        eventSeqs: [],
      });
    }
    return heroes.get(key);
  };

  for (const event of events) {
    const source = ensure(event.source);
    const target = ensure(event.target);
    if (source) source.eventSeqs.push(number(event.eventSeq));
    if (target && target !== source) target.eventSeqs.push(number(event.eventSeq));
    if (DAMAGE_TYPES.has(event.type)) {
      if (source) source.damageDealt += number(event.damage);
      if (target) target.damageTaken += number(event.damage);
    } else if (event.type === "Recovery") {
      if (source) source.recoveryDone += number(event.amount);
      if (target) target.recoveryReceived += number(event.amount);
    } else if (event.type === "HeroActionStart" && source) {
      source.actions += 1;
    } else if (event.type === "StatusApplied" && source) {
      source.controlsApplied += 1;
    } else if (
      event.type === "SkillTriggered" &&
      event.trigger === "ACTIVE_SKILL_ATTEMPT" &&
      source
    ) {
      source.activeSkills += 1;
    }
  }
  return [...heroes.values()];
}

function buildInsights(rounds, completeness) {
  const insights = [];
  const damageRounds = rounds
    .map((round) => ({
      round: round.round,
      magnitude: Math.abs(round.attackerDamage - round.defenderDamage),
      advantage:
        round.attackerDamage >= round.defenderDamage ? "ATTACKER" : "DEFENDER",
      eventSeqs: round.damageEventSeqs,
    }))
    .filter((round) => round.magnitude > 0)
    .sort((left, right) => right.magnitude - left.magnitude);
  if (damageRounds.length) {
    const turningPoint = damageRounds[0];
    const evidence = rounds
      .find((round) => round.round === turningPoint.round)
      ?.damageEventSeqs.filter((eventSeq) => Number.isInteger(eventSeq));
    insights.push({
      kind: "turning-point",
      severity: "info",
      title: `第 ${turningPoint.round} 回合出现最大伤害差`,
      round: turningPoint.round,
      eventSeqs: evidence?.length
        ? [evidence.find((eventSeq) => eventSeq >= 0)]
        : [],
      advantage: turningPoint.advantage,
      magnitude: turningPoint.magnitude,
    });
  }
  if (completeness.status === "partial") {
    insights.push({
      kind: "completeness",
      severity: "warning",
      title: "部分战法或动作未完整投影",
      round: 0,
      eventSeqs: [],
    });
  }
  return insights;
}

export function analyzeReplay(firstRun) {
  const events = normalizeEvents(firstRun);
  const grouped = groupReplayByPhase({ ...firstRun, events });
  const rounds = buildRoundSummaries(grouped);
  const diagnostics = firstRun?.diagnostics || {};
  const unsupportedSkillEffects = Array.isArray(
    diagnostics.unsupportedSkillEffects,
  )
    ? diagnostics.unsupportedSkillEffects.length
    : events.filter((event) => event.type === "UnsupportedSkillEffect").length;
  const unsupportedEquipmentEffects = Array.isArray(
    diagnostics.unsupportedEquipmentEffects,
  )
    ? diagnostics.unsupportedEquipmentEffects.length
    : events.filter((event) => event.type === "UnsupportedEquipmentEffect").length;
  const unprojectedReplayEvents = Array.isArray(
    diagnostics.unprojectedReplayEvents,
  )
    ? diagnostics.unprojectedReplayEvents.length
    : 0;
  const completeness = {
    status:
      unsupportedSkillEffects +
        unsupportedEquipmentEffects +
        unprojectedReplayEvents >
      0
        ? "partial"
        : "complete",
    unsupportedSkillEffects,
    unsupportedEquipmentEffects,
    unprojectedReplayEvents,
    semanticEventCount: events.length,
    replayActionCount: Array.isArray(firstRun?.replayActions)
      ? firstRun.replayActions.length
      : 0,
  };
  const totals = {
    attackerDamage: rounds.reduce(
      (total, round) => total + round.attackerDamage,
      0,
    ),
    defenderDamage: rounds.reduce(
      (total, round) => total + round.defenderDamage,
      0,
    ),
    attackerRecovery: rounds.reduce(
      (total, round) => total + round.attackerRecovery,
      0,
    ),
    defenderRecovery: rounds.reduce(
      (total, round) => total + round.defenderRecovery,
      0,
    ),
    controlsApplied: rounds.reduce(
      (total, round) => total + round.controlsApplied,
      0,
    ),
    evades: rounds.reduce((total, round) => total + round.evades, 0),
    blockedEffects: rounds.reduce(
      (total, round) => total + round.blockedEffects,
      0,
    ),
  };
  return {
    totals,
    rounds,
    heroSummaries: buildHeroSummaries(events),
    effectChains: buildEffectChains(events),
    insights: buildInsights(rounds, completeness),
    completeness,
    grouped,
  };
}

if (typeof window !== "undefined") {
  window.StzbSimulatorAnalysis = {
    analyzeReplay,
    buildEffectChains,
    groupReplayByPhase,
    replayEventDetail,
  };
}

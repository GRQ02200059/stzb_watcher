import assert from "node:assert/strict";
import test from "node:test";

import {
  analyzeReplay,
  buildEffectChains,
  groupReplayByPhase,
  replayEventDetail,
} from "../../static/simulator-analysis.mjs";

const event = (eventSeq, type, round = 0, fields = {}) => ({
  eventSeq,
  phase: round === 0 ? "PREPARATION" : "BATTLE",
  round,
  type,
  ...fields,
});

const attacker = { side: "ATTACKER", position: 0, heroId: 100027 };
const defender = { side: "DEFENDER", position: 2, heroId: 100023 };

const FIRST_RUN_FIXTURE = {
  events: [
    event(0, "BattleStart"),
    event(1, "StatusApplied", 0, {
      source: attacker,
      target: attacker,
      skillId: 200001,
      effectId: 101,
      status: "ATTACK_BUFF",
    }),
    event(2, "RoundStart", 1),
    event(3, "HeroActionStart", 1, { source: attacker }),
    event(4, "SkillDamage", 1, {
      source: attacker,
      target: defender,
      skillId: 200001,
      effectId: 301,
      damage: 400,
      targetTroopsAfter: 8600,
    }),
    event(5, "EffectBlocked", 1, {
      source: attacker,
      target: defender,
      skillId: 200001,
      effectId: 401,
      blockingEffectId: 207,
    }),
    event(6, "Recovery", 1, {
      source: defender,
      target: defender,
      skillId: 200002,
      amount: 100,
      targetTroopsAfter: 8700,
    }),
    event(7, "HeroActionEnd", 1, { source: attacker }),
    event(8, "RoundEnd", 1),
    event(9, "UnsupportedSkillEffect", 1, {
      source: attacker,
      skillId: 299999,
      effectId: 999,
    }),
    event(10, "BattleEnd", 0),
  ],
  diagnostics: {
    unsupportedSkillEffects: [{ type: "UnsupportedSkillEffect" }],
    unsupportedEquipmentEffects: [],
    unprojectedReplayEvents: ["fixture replay projection warning"],
  },
  replayActions: [
    { actionSeq: 0, actionId: 654, params: [], encoded: "i6" },
    { actionSeq: 1, actionId: 641, params: [], encoded: "ht" },
    { actionSeq: 2, actionId: 631, params: [], encoded: "hj" },
    { actionSeq: 3, actionId: 632, params: [], encoded: "hk" },
    { actionSeq: 4, actionId: 640, params: [], encoded: "hs" },
    { actionSeq: 5, actionId: 635, params: [], encoded: "hn" },
    { actionSeq: 6, actionId: 634, params: [], encoded: "hm" },
    { actionSeq: 7, actionId: 639, params: [], encoded: "hr" },
    { actionSeq: 8, actionId: 637, params: [], encoded: "hp" },
    { actionSeq: 9, actionId: 4, params: [], encoded: "04" },
    { actionSeq: 10, actionId: 651, params: [], encoded: "i3" },
    { actionSeq: 11, actionId: 8, params: [], encoded: "08" },
    { actionSeq: 12, actionId: 9, params: [1], encoded: "091" },
    { actionSeq: 13, actionId: 10, params: [1], encoded: "0a1" },
    { actionSeq: 14, actionId: 213, params: [], encoded: "5x" },
    { actionSeq: 15, actionId: 60, params: [1, 200001, 4, 400, 8600], encoded: "1o1,200001,4,400,8600" },
    { actionSeq: 16, actionId: 214, params: [], encoded: "5y" },
    { actionSeq: 17, actionId: 210, params: [4, 207], encoded: "5u4,207" },
    { actionSeq: 18, actionId: 11, params: [1], encoded: "0b1" },
  ],
};

test("groups preparation and battle rounds without losing event order", () => {
  const grouped = groupReplayByPhase(FIRST_RUN_FIXTURE);

  assert.deepEqual(
    grouped.preparation.events.map((item) => item.eventSeq),
    [0, 1],
  );
  assert.deepEqual(
    grouped.final.events.map((item) => item.eventSeq),
    [10],
  );
  assert.deepEqual(
    grouped.rounds[0].events.map((item) => item.eventSeq),
    [2, 3, 4, 5, 6, 7, 8, 9],
  );
});

test("effect chain connects apply block and remove events", () => {
  const events = [
    event(10, "StatusApplied", 1, {
      source: attacker,
      target: defender,
      skillId: 200001,
      effectId: 501,
      status: "CONFUSION",
    }),
    event(11, "EffectBlocked", 1, {
      source: attacker,
      target: defender,
      skillId: 200001,
      effectId: 501,
      blockingEffectId: 207,
    }),
    event(12, "StatusRemoved", 2, {
      source: attacker,
      target: defender,
      skillId: 200001,
      effectId: 501,
    }),
  ];

  const chains = buildEffectChains(events);

  assert.equal(chains.length, 1);
  assert.deepEqual(chains[0].lifecycle, [
    "StatusApplied",
    "EffectBlocked",
    "StatusRemoved",
  ]);
  assert.deepEqual(chains[0].eventSeqs, [10, 11, 12]);
});

test("analysis exposes unsupported effects instead of hiding them", () => {
  const result = analyzeReplay(FIRST_RUN_FIXTURE);

  assert.equal(result.completeness.status, "partial");
  assert.equal(result.completeness.unsupportedSkillEffects, 1);
  assert.equal(result.completeness.unprojectedReplayEvents, 1);
  assert.equal(result.totals.attackerDamage, 400);
  assert.equal(result.totals.defenderRecovery, 100);
  assert.equal(result.totals.blockedEffects, 1);
});

test("analysis creates evidence backed turning point insights", () => {
  const result = analyzeReplay(FIRST_RUN_FIXTURE);
  const turningPoint = result.insights.find(
    (insight) => insight.kind === "turning-point",
  );

  assert.ok(turningPoint);
  assert.equal(turningPoint.round, 1);
  assert.deepEqual(turningPoint.eventSeqs, [4]);
});

test("replay model exposes preparation stages in server order", () => {
  const model = groupReplayByPhase(FIRST_RUN_FIXTURE);

  assert.deepEqual(
    model.preparation.stages.map((stage) => stage.key),
    [
      "INITIALIZATION",
      "SYSTEM",
      "COUNTRY",
      "ARMY",
      "TROOP",
      "EQUIPMENT",
      "SURFACE",
      "PASSIVE",
      "COMMAND",
    ],
  );
  assert.deepEqual(
    model.preparation.stages.map((stage) => stage.actions.length),
    [2, 1, 1, 1, 1, 1, 1, 1, 3],
  );
});

test("hero action envelopes keep events scoped to one actor", () => {
  const model = groupReplayByPhase(FIRST_RUN_FIXTURE);

  assert.deepEqual(
    model.rounds[0].actions[0].events.map((item) => item.type),
    [
      "HeroActionStart",
      "SkillDamage",
      "EffectBlocked",
      "Recovery",
      "HeroActionEnd",
    ],
  );
  assert.equal(model.rounds[0].actions[0].source.heroId, 100027);
});

test("detail lookup connects semantic event and replay actions", () => {
  const detail = replayEventDetail(FIRST_RUN_FIXTURE, 5);

  assert.equal(detail.semanticEvent.type, "EffectBlocked");
  assert.ok(detail.replayActions.length > 0);
  assert.ok(detail.replayActions.every((action) => action.encoded));
  assert.ok(detail.replayActions.some((action) => action.actionId === 210));
});

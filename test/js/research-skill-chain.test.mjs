import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import { replayEventDetail } from "../../static/simulator-analysis.mjs";
import {
  buildConfigSkillChain,
  buildSimulationSkillChain,
  findSkillChainNode,
  groupSkillChainByPhase,
} from "../../static/research-skill-chain.mjs";

function lineup(heroIds) {
  return {
    heroes: heroIds.map((id, position) => ({
      id,
      position,
      equip_skills:
        position === 0 ? [200114] : position === 2 ? [200116] : [],
    })),
  };
}

function heroDetailsFixture() {
  return new Map([
    [100027, {
      hero: { heroid: 100027, name: "张辽" },
      initialSkill: { skill_id: 200027 },
    }],
    [100016, {
      hero: { heroid: 100016, name: "刘备" },
      initialSkill: { skill_id: 200016 },
    }],
    [100090, {
      hero: { heroid: 100090, name: "太史慈" },
      initialSkill: { skill_id: 200090 },
    }],
  ]);
}

function skillDetail(skill, details = [], unresolvedDescription = false) {
  return { skill, details, unresolvedDescription };
}

function skillDetailsFixture() {
  return new Map([
    [200027, skillDetail({
      skill_id: 200027,
      name: "其疾如风",
      skill_type: 10,
      prepare: 0,
      probability_init: 100,
      target_description: "我军群体",
      hit_range: 0,
      main_effect_name: "速度提高",
    }, [{
      detail_id: 20002701,
      effect_id: 104,
      effect_name: "速度提高",
      constant_param: 25,
      intel_param: 0,
      available_round: 3,
      target_type: 3,
      select_type: 0,
      effect: { effect_id: 104, name: "速度提高" },
    }])],
    [200016, skillDetail({
      skill_id: 200016,
      name: "皇裔流离",
      skill_type: 9,
    })],
    [200090, skillDetail({
      skill_id: 200090,
      name: "方阵突击",
      skill_type: 1,
    }, [], true)],
    [200114, skillDetail({
      skill_id: 200114,
      name: "攻其不备",
      skill_type: 14,
    })],
    [200116, skillDetail({
      skill_id: 200116,
      name: "温酒斩将",
      skill_type: 16,
    })],
  ]);
}

const attacker = { side: "ATTACKER", position: 0, heroId: 100027 };
const defender = { side: "DEFENDER", position: 2, heroId: 100090 };

function simulationFixture() {
  return {
    events: [
      {
        eventSeq: 6,
        phase: "FINAL",
        round: 0,
        type: "BattleEnd",
      },
      {
        eventSeq: 0,
        phase: "PREPARATION",
        round: 0,
        type: "BattleStart",
      },
      {
        eventSeq: 1,
        phase: "PREPARATION",
        round: 0,
        type: "StatChanged",
        source: attacker,
        target: attacker,
        rootSkillId: 200027,
        skillId: 200027,
        effectId: 104,
      },
      {
        eventSeq: 2,
        phase: "BATTLE",
        round: 1,
        type: "RoundStart",
      },
      {
        eventSeq: 3,
        phase: "BATTLE",
        round: 1,
        type: "HeroActionStart",
        source: attacker,
      },
      {
        eventSeq: 4,
        phase: "BATTLE",
        round: 1,
        type: "SkillDamage",
        source: attacker,
        target: defender,
        rootSkillId: 200114,
        skillId: 200114,
        effectId: 301,
        damage: 400,
        targetTroopsAfter: 8600,
      },
      {
        eventSeq: 5,
        phase: "BATTLE",
        round: 1,
        type: "EffectBlocked",
        source: attacker,
        target: defender,
        rootSkillId: 200114,
        skillId: 200114,
        effectId: 401,
        blockingEffectId: 207,
      },
    ],
    replayActions: [
      { actionSeq: 0, actionId: 654, params: [], encoded: "i6" },
      {
        actionSeq: 1,
        actionId: 31,
        params: [1, 200027, 104],
        encoded: "0v1,200027,104",
      },
      { actionSeq: 2, actionId: 9, params: [1], encoded: "091" },
      { actionSeq: 3, actionId: 10, params: [1], encoded: "0a1" },
      {
        actionSeq: 4,
        actionId: 60,
        params: [1, 200114, 4, 400, 8600],
        encoded: "1o1,200114,4,400,8600",
      },
      { actionSeq: 5, actionId: 210, params: [4, 207], encoded: "5u4,207" },
      { actionSeq: 6, actionId: 13, params: [], encoded: "0d" },
    ],
  };
}

function unsupportedFixture() {
  return {
    events: [{
      eventSeq: 0,
      phase: "BATTLE",
      round: 1,
      type: "UnsupportedSkillEffect",
      source: attacker,
      skillId: 299999,
      effectId: 999,
    }],
    replayActions: [],
  };
}

test("config chain orders initial and optional skills by stable phase", () => {
  const nodes = buildConfigSkillChain(
    lineup([100027, 100016, 100090]),
    heroDetailsFixture(),
    skillDetailsFixture(),
  );

  assert.deepEqual(
    nodes.map((node) => node.phase),
    ["PREPARATION", "PREPARATION", "ACTIVE", "ATTACK", "CHASE"],
  );
  assert.ok(nodes.every((node) => node.evidenceClass === "CONFIG_FACT"));
  assert.ok(nodes.some((node) => node.unresolvedDescription));
});

test("config node exposes target probability duration and parameters", () => {
  const node = buildConfigSkillChain(
    lineup([100027, 100016, 100090]),
    heroDetailsFixture(),
    skillDetailsFixture(),
  )[0];

  assert.deepEqual(node, {
    nodeId: "config:0:initial:200027",
    kind: "config",
    evidenceClass: "CONFIG_FACT",
    phase: "PREPARATION",
    heroId: 100027,
    heroName: "张辽",
    position: 0,
    slot: "initial",
    skillId: 200027,
    skillName: "其疾如风",
    prepareRounds: 0,
    probability: 100,
    targetDescription: "我军群体",
    hitRange: 0,
    mainEffectName: "速度提高",
    details: [{
      detailId: 20002701,
      effectId: 104,
      effectName: "速度提高",
      constantParam: 25,
      intelParam: 0,
      availableRound: 3,
      targetType: 3,
      selectType: 0,
    }],
    unresolvedDescription: false,
  });
});

test("unknown config skill types remain OTHER", () => {
  const heroes = heroDetailsFixture();
  const skills = skillDetailsFixture();
  skills.set(200027, skillDetail({
    skill_id: 200027,
    name: "未归类战法",
    skill_type: 999,
  }));

  const nodes = buildConfigSkillChain(lineup([100027]), heroes, skills);
  assert.equal(
    nodes.find((node) => node.nodeId === "config:0:initial:200027").phase,
    "OTHER",
  );
});

test("simulation chain preserves engine event order and evidence", () => {
  const nodes = buildSimulationSkillChain({
    response: { result: { firstRun: simulationFixture() } },
  });

  assert.deepEqual(
    nodes.map((node) => node.eventSeq),
    [0, 1, 2, 3, 4, 5, 6],
  );
  assert.ok(nodes.every((node) => node.evidenceClass === "SIMULATION"));
});

test("simulation node projects only source-backed event fields", () => {
  const node = buildSimulationSkillChain({
    response: { firstRun: simulationFixture() },
  }, {
    replayEventDetailFn: replayEventDetail,
  }).find((item) => item.eventSeq === 4);

  assert.deepEqual(node, {
    nodeId: "event:4",
    kind: "simulation",
    evidenceClass: "SIMULATION",
    eventSeq: 4,
    phase: "BATTLE",
    round: 1,
    type: "SkillDamage",
    source: attacker,
    target: defender,
    skillId: 200114,
    rootSkillId: 200114,
    effectId: 301,
    replayActions: [{
      actionSeq: 4,
      actionId: 60,
      encoded: "1o1,200114,4,400,8600",
    }],
    warning: false,
  });
});

test("simulation node links real replay action evidence", () => {
  const node = buildSimulationSkillChain({
    response: { result: { firstRun: simulationFixture() } },
  }, {
    replayEventDetailFn: replayEventDetail,
  }).find((item) => item.eventSeq === 5);

  assert.deepEqual(node.replayActions, [{
    actionSeq: 5,
    actionId: 210,
    encoded: "5u4,207",
  }]);
});

test("simulation chain degrades to empty replay actions without dependency", () => {
  const node = buildSimulationSkillChain({
    response: { result: { firstRun: simulationFixture() } },
  }).find((item) => item.eventSeq === 5);

  assert.deepEqual(node.replayActions, []);
});

test("unsupported effects remain visible", () => {
  const nodes = buildSimulationSkillChain({
    response: { result: { firstRun: unsupportedFixture() } },
  });

  assert.equal(
    nodes.find((node) => node.type === "UnsupportedSkillEffect").warning,
    true,
  );
});

test("ordinary simulation events ignore external warning flags", () => {
  const firstRun = {
    events: [{
      eventSeq: 0,
      phase: "BATTLE",
      round: 1,
      type: "SkillDamage",
      warning: true,
    }],
    replayActions: [],
  };

  assert.equal(
    buildSimulationSkillChain({ response: { firstRun } })[0].warning,
    false,
  );
});

test("skill chain module has no analysis browser global or network dependency", () => {
  const source = readFileSync(
    new URL("../../static/research-skill-chain.mjs", import.meta.url),
    "utf8",
  );

  assert.doesNotMatch(source, /simulator-analysis/);
  assert.doesNotMatch(
    source,
    /\b(?:window|globalThis|document|fetch|XMLHttpRequest|WebSocket)\b/,
  );
});

test("simulation nodes group into preparation rounds and final", () => {
  const nodes = buildSimulationSkillChain({
    response: { result: { firstRun: simulationFixture() } },
  });
  const groups = groupSkillChainByPhase(nodes);

  assert.deepEqual(
    groups.map((group) => group.key),
    ["PREPARATION", "ROUND:1", "FINAL"],
  );
  assert.deepEqual(
    groups.map((group) => group.nodes.map((node) => node.eventSeq)),
    [[0, 1], [2, 3, 4, 5], [6]],
  );
});

test("config nodes group by their source-backed phase", () => {
  const nodes = buildConfigSkillChain(
    lineup([100027, 100016, 100090]),
    heroDetailsFixture(),
    skillDetailsFixture(),
  );

  assert.deepEqual(
    groupSkillChainByPhase(nodes).map((group) => group.key),
    ["PREPARATION", "ACTIVE", "ATTACK", "CHASE"],
  );
});

test("node lookup returns the exact node or null", () => {
  const nodes = buildSimulationSkillChain({
    response: { result: { firstRun: simulationFixture() } },
  });

  assert.equal(findSkillChainNode(nodes, "event:4").eventSeq, 4);
  assert.equal(findSkillChainNode(nodes, "event:404"), null);
  assert.equal(findSkillChainNode(null, "event:4"), null);
});

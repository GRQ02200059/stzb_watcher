package com.stzb.battle.core

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientBattleTextReplayAdapterTest {
    @Test
    fun `preparation emits official initialization and source stage boundaries`() {
        val base = twoRoundResult()
        val attacker = base.attacker.copy(
            preparationSources = listOf(
                BattlePreparationSource(BattlePreparationStage.ARMY, 295020),
                BattlePreparationSource(BattlePreparationStage.ARMY, 291005),
                BattlePreparationSource(BattlePreparationStage.TROOP, 296104, 0),
                BattlePreparationSource(BattlePreparationStage.EQUIPMENT, 400001, 0),
            ),
        )
        val result = base.copy(
            attacker = attacker,
            entryAttacker = attacker,
            defender = BattleTeam(emptyList()),
            entryDefender = BattleTeam(emptyList()),
            events = emptyList(),
        )

        val encoded = ClientBattleTextReplayAdapter.adapt(result).map(ClientReportAction::encode)
        val retained = encoded.filter {
            it in setOf(
                "i6", "ht", "0f", "0g", "02", "5z", "i1", "i2", "hj", "hk", "hl", "hs",
                "hn", "hm", "hr", "ho", "hq", "hp", "5s0,295020", "5s0,291005",
                "7x1,296104", "ba1,400001",
            )
        }

        assertEquals(
            listOf(
                "i6", "ht", "0f", "0g", "02", "5z", "i1", "i2", "hj", "hk",
                "5s0,295020", "hl", "hs", "5s0,291005", "hn", "7x1,296104",
                "hm", "ba1,400001", "hr", "ho", "hq", "hp",
            ),
            retained,
        )
    }

    @Test
    fun `preparation keeps passive before 04 and wraps command heroes before 08`() {
        val first = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val second = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(4))
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.TriggerPoint(
                    0,
                    first,
                    com.stzb.battle.core.skill.BattleTrigger.BATTLE_PASSIVE,
                ),
                BattleEvent.SkillTriggered(
                    0,
                    first,
                    200001,
                    200001,
                    com.stzb.battle.core.skill.BattleTrigger.BATTLE_PASSIVE,
                ),
                BattleEvent.TriggerPoint(
                    0,
                    first,
                    com.stzb.battle.core.skill.BattleTrigger.BATTLE_COMMAND,
                ),
                BattleEvent.SkillTriggered(
                    0,
                    first,
                    200002,
                    200002,
                    com.stzb.battle.core.skill.BattleTrigger.BATTLE_COMMAND,
                ),
                BattleEvent.TriggerPoint(
                    0,
                    second,
                    com.stzb.battle.core.skill.BattleTrigger.BATTLE_COMMAND,
                ),
                BattleEvent.RoundStart(1),
            ),
        )

        val preparation = ClientBattleTextReplayAdapter.adapt(result)
            .map(ClientReportAction::encode)
            .filter {
                it in setOf(
                    "0l1,200001",
                    "60",
                    "04",
                    "i3",
                    "051",
                    "hw1",
                    "0m1,200002",
                    "hx1",
                    "056",
                    "hw6",
                    "hx6",
                    "08",
                    "091",
                )
            }

        assertEquals(
            listOf(
                "0l1,200001",
                "60",
                "04",
                "i3",
                "051",
                "hw1",
                "0m1,200002",
                "hx1",
                "056",
                "hw6",
                "hx6",
                "08",
                "091",
            ),
            preparation,
        )
    }

    @Test
    fun `distinguishes passive command active and pursuit preparation actions`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.SkillTriggered(
                    0,
                    source,
                    200001,
                    200001,
                    com.stzb.battle.core.skill.BattleTrigger.BATTLE_PASSIVE,
                ),
                BattleEvent.SkillTriggered(
                    0,
                    source,
                    200002,
                    200002,
                    com.stzb.battle.core.skill.BattleTrigger.BATTLE_COMMAND,
                ),
                BattleEvent.SkillTriggered(
                    1,
                    source,
                    200003,
                    200003,
                    com.stzb.battle.core.skill.BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                ),
                BattleEvent.SkillTriggered(
                    1,
                    source,
                    200004,
                    200004,
                    com.stzb.battle.core.skill.BattleTrigger.PURSUIT_ATTEMPT,
                ),
            ),
        )

        assertEquals(
            listOf("0l1,200001", "0m1,200002", "0n1,200003", "0o1,200004"),
            ClientBattleTextReplayAdapter.adapt(result)
                .filter { it.id in 21..24 }
                .map(ClientReportAction::encode),
        )
    }

    @Test
    fun `round zero command effects never reuse battle skill wrappers`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val target = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(4))
        val diagnostics = mutableListOf<String>()
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.SkillTriggered(
                    round = 0,
                    source = source,
                    rootSkillId = 200648,
                    skillId = 200648,
                    trigger = com.stzb.battle.core.skill.BattleTrigger.BATTLE_COMMAND,
                ),
                BattleEvent.SkillDamage(
                    round = 0,
                    skillId = 200648,
                    effectId = 301,
                    source = source,
                    target = target,
                    damage = 358,
                    targetTroopsAfter = 642,
                ),
                BattleEvent.RoundStart(1),
            ),
        )

        val preparation = ClientBattleTextReplayAdapter.adapt(result, diagnostics::add)
            .takeWhile { it.id != ClientBattleTextReplayProtocol.ROUND }

        assertTrue(
            preparation.none {
                it.id in setOf(
                    ClientBattleTextReplayProtocol.SKILL_BEGIN,
                    ClientBattleTextReplayProtocol.SKILL_END,
                    ClientBattleTextReplayProtocol.SKILL_CAST,
                    ClientBattleTextReplayProtocol.SKILL_DAMAGE,
                )
            },
        )
        assertTrue(
            preparation.any {
                it.id == ClientBattleTextReplayProtocol.SKILL_TRIGGERED_COMMAND
            },
        )
        assertTrue(diagnostics.single().contains("round-zero SkillDamage"))
    }

    @Test
    fun `hero initialization carries real skill levels troop features and equipment slots`() {
        val result = twoRoundResult().let { base ->
            base.copy(
                attacker = base.attacker.copy(
                    heroes = listOf(
                        base.attacker.heroes.first().copy(
                            level = 45,
                            maxTroops = 9_700,
                            skillIds = listOf(200028, 200689, 200233),
                            skillLevels = listOf(10, 8, 6),
                            troopFeatureIds = listOf(3106, 3104),
                            equipment = listOf(
                                BattleEquipmentSlot(400114, 6),
                                BattleEquipmentSlot(400115, 6),
                                BattleEquipmentSlot(400112, 1),
                            ),
                        ),
                    ),
                ),
            )
        }

        val encoded = ClientBattleTextReplayAdapter.adapt(result)
            .first { it.id == ClientBattleTextReplayProtocol.HERO_INFO }
            .encode()

        assertEquals(
            "5p1,45,9700,200028,10,200689,8,200233,6,3106,3104,,400114,6,400115,6,400112,1,0",
            encoded,
        )
    }

    @Test
    fun `hero initialization declares selected surface skill with official 96 action`() {
        val result = twoRoundResult().let { base ->
            val entryAttacker = (base.entryAttacker ?: base.attacker).copy(
                heroes = (base.entryAttacker ?: base.attacker).heroes.mapIndexed { index, hero ->
                    if (index == 0) hero.copy(surfaceSkillId = 281012) else hero
                },
            )
            base.copy(
                attacker = entryAttacker,
                entryAttacker = entryAttacker,
            )
        }

        val surfaceSkills = ClientBattleTextReplayAdapter.adapt(result)
            .filter { it.id == "96".toInt(36) }
            .map(ClientReportAction::encode)

        assertEquals(listOf("961,281012"), surfaceSkills)
    }

    @Test
    fun `surface cautious attack uses official compact status action`() {
        val team = BattleTeamBuilder(BattleConfigRepository.loadDefault()).build(
            listOf(
                BattleHeroSpec(
                    heroId = 100479,
                    position = 0,
                    troops = 1000,
                    surfaceSkillId = 281004,
                ),
            ),
        )
        val result = twoRoundResult().copy(
            attacker = team,
            entryAttacker = team,
            defender = BattleTeam(emptyList()),
            entryDefender = BattleTeam(emptyList()),
            events = emptyList(),
        )

        val encoded = ClientBattleTextReplayAdapter.adapt(result).map(ClientReportAction::encode)

        assertTrue(encoded.contains("961,281004"))
        assertTrue(encoded.contains("0s1,522"))
        assertTrue(encoded.none { it.startsWith("0s1,281004,") })
    }

    @Test
    fun `preparation does not invent source-free initial combat attributes`() {
        val hero = twoRoundResult().attacker.heroes.first().copy(
            stats = BattleStats(attack = 191, defense = 224, strategy = 275, speed = 129, siege = 12, hitRange = 3),
        )
        val result = twoRoundResult().copy(
            attacker = BattleTeam(listOf(hero)),
            defender = BattleTeam(emptyList()),
            events = emptyList(),
        )

        val attributes = ClientBattleTextReplayAdapter.adapt(result)
            .filter { it.id in setOf(45, 46, 47, 48, 49, 50) }
            .map(ClientReportAction::encode)

        assertEquals(emptyList(), attributes)
    }

    @Test
    fun `preparation emits army bonus attributes with their official source`() {
        val base = twoRoundResult()
        val result = base.copy(
            attacker = base.attacker.copy(
                preparationEffects = listOf(
                    BattlePreparationEffect(
                        stage = BattlePreparationStage.ARMY,
                        sourceId = 291005,
                        targetPosition = 0,
                        stat = BattleStat.ATTACK,
                        strength = 23,
                        delta = 23,
                        valueAfter = 191,
                    ),
                ),
            ),
            events = emptyList(),
        )

        val encoded = ClientBattleTextReplayAdapter.adapt(result).map(ClientReportAction::encode)

        assertTrue(encoded.contains("5s0,291005"))
        assertTrue(encoded.contains("190,291005,1,23,23,191"))
        assertTrue(encoded.indexOf("5s0,291005") < encoded.indexOf("60"))
    }

    @Test
    fun `preparation source effects use official inner envelopes`() {
        val base = twoRoundResult()
        val attacker = BattleTeam(
            heroes = listOf(hero(1, 0)),
            preparationSources = listOf(
                BattlePreparationSource(BattlePreparationStage.SYSTEM, 295094),
            ),
            preparationEffects = listOf(
                BattlePreparationEffect(
                    stage = BattlePreparationStage.SYSTEM,
                    sourceId = 295094,
                    targetPosition = 0,
                    stat = BattleStat.SPEED,
                    strength = 10,
                    delta = 10,
                    valueAfter = 120,
                    percent = false,
                ),
            ),
        )
        val actions = ClientBattleTextReplayAdapter.adapt(
            base.copy(
                attacker = attacker,
                defender = BattleTeam(emptyList()),
                entryAttacker = attacker,
                entryDefender = BattleTeam(emptyList()),
                events = emptyList(),
            ),
        )

        val sourceIndex = actions.indexOf(
            ClientReportAction(
                ClientBattleTextReplayProtocol.SYSTEM_EFFECT_SOURCE,
                listOf(0, 295094),
            ),
        )
        assertEquals(
            listOf(
                ClientReportAction(
                    ClientBattleTextReplayProtocol.SYSTEM_EFFECT_SOURCE,
                    listOf(0, 295094),
                ),
                ClientReportAction(ClientBattleTextReplayProtocol.PREPARATION_EFFECT_BEGIN),
                ClientReportAction(
                    ClientBattleTextReplayProtocol.FLAT_SPEED,
                    listOf(0, 295094, 1, 10, 120),
                ),
                ClientReportAction(ClientBattleTextReplayProtocol.PREPARATION_EFFECT_END),
                ClientReportAction(ClientBattleTextReplayProtocol.PREPARATION_EFFECT_BOUNDARY),
            ),
            actions.subList(sourceIndex, sourceIndex + 5),
        )
    }

    @Test
    fun `defender preparation sources use official team position seven and decimal values`() {
        val base = twoRoundResult()
        val result = base.copy(
            attacker = BattleTeam(emptyList()),
            defender = base.defender.copy(
                preparationEffects = listOf(
                    BattlePreparationEffect(
                        stage = BattlePreparationStage.ARMY,
                        sourceId = 295040,
                        targetPosition = 0,
                        stat = BattleStat.ATTACK,
                        strength = 10,
                        delta = 13,
                        valueAfter = 164,
                        deltaExact = 13.1,
                        valueAfterExact = 164.4,
                    ),
                ),
            ),
            events = emptyList(),
        )

        val encoded = ClientBattleTextReplayAdapter.adapt(result).map(ClientReportAction::encode)

        assertTrue(encoded.contains("5s7,295040"))
        assertTrue(encoded.contains("197,295040,6,10,13.1,164.4"))
    }

    @Test
    fun `preparation declares troop and equipment sources even without attribute changes`() {
        val base = twoRoundResult()
        val result = base.copy(
            attacker = base.attacker.copy(
                preparationSources = listOf(
                    BattlePreparationSource(BattlePreparationStage.TROOP, 296106, sourcePosition = 0),
                    BattlePreparationSource(BattlePreparationStage.EQUIPMENT, 1102, sourcePosition = 0),
                ),
            ),
            defender = BattleTeam(emptyList()),
            events = emptyList(),
        )

        val encoded = ClientBattleTextReplayAdapter.adapt(result).map(ClientReportAction::encode)

        assertTrue(encoded.contains("7x1,296106"))
        assertTrue(encoded.contains("ba1,1102"))
    }

    @Test
    fun `equipment skill effects stay under the base equipment source`() {
        val base = twoRoundResult()
        val result = base.copy(
            attacker = base.attacker.copy(
                preparationSources = listOf(
                    BattlePreparationSource(BattlePreparationStage.EQUIPMENT, 1024, 0),
                ),
                preparationEffects = listOf(
                    BattlePreparationEffect(
                        stage = BattlePreparationStage.EQUIPMENT,
                        sourceId = 400022,
                        sourcePosition = 0,
                        targetPosition = 0,
                        stat = BattleStat.ATTACK,
                        strength = 2,
                        delta = 2,
                        valueAfter = 102,
                        percent = false,
                        containerSourceId = 1024,
                    ),
                ),
            ),
            defender = BattleTeam(emptyList()),
            events = emptyList(),
        )

        val encoded = ClientBattleTextReplayAdapter.adapt(result).map(ClientReportAction::encode)

        assertTrue(encoded.contains("ba1,1024"))
        assertTrue(encoded.contains("0v1,400022,1,2,102"))
        assertFalse(encoded.contains("ba1,400022"))
    }

    @Test
    fun `equipment special effects use their official preparation actions`() {
        fun encoded(
            equipmentId: Int,
            skillIds: List<Int>,
            skillLevels: List<Int>,
        ): List<String> {
            val team = BattleTeamBuilder(
                BattleConfigRepository.loadDefault(),
                BattleEquipmentRepository.loadDefault(),
            ).build(
                listOf(
                    BattleHeroSpec(
                        heroId = 100479,
                        position = 0,
                        troops = 1000,
                        equipmentIds = listOf(equipmentId),
                        equipmentSkillIds = skillIds,
                        equipmentSkillLevels = skillLevels,
                    ),
                ),
            )
            val base = twoRoundResult()
            return ClientBattleTextReplayAdapter.adapt(
                base.copy(
                    attacker = team,
                    defender = BattleTeam(emptyList()),
                    entryAttacker = team,
                    entryDefender = BattleTeam(emptyList()),
                    events = emptyList(),
                ),
            ).map(ClientReportAction::encode)
        }

        assertTrue(encoded(1102, listOf(400114, 400115), listOf(6, 6)).containsAll(
            listOf("6x1,400114,1,6", "7d1,400115,1,12"),
        ))
        assertTrue(encoded(1048, listOf(400046, 400047, 400048), listOf(6, 6, 1)).containsAll(
            listOf(
                "7g1,400046,1,6",
                "7m1,400046,1,6",
                "7i1,400046,1,6",
                "7k1,400046,1,6",
                "781,400047,1,12",
                "9b1,400048,1,25",
            ),
        ))
        assertTrue(encoded(1048, listOf(400041), listOf(6)).contains("791,400041,1,6"))
        assertTrue(encoded(1048, listOf(400032), listOf(6)).contains("bf1,400032,1,12"))
        assertTrue(encoded(1048, listOf(400071), listOf(6)).contains("6w1,400071,1,12"))
        assertTrue(encoded(1048, listOf(400050), listOf(6)).contains("dr1,400050,1,3,6"))
        assertTrue(encoded(1048, listOf(400051), listOf(1)).contains("a41,400051,1,10"))
        assertTrue(encoded(1048, listOf(400056), listOf(1)).contains("a31,400056,1,1.5"))
        assertTrue(encoded(1048, listOf(400087), listOf(1)).contains("a51,400087,1,1"))
        assertTrue(encoded(1048, listOf(400042), listOf(1)).containsAll(
            listOf(
                "991,400042,1,304,10",
                "991,400042,1,305,10",
                "991,400042,1,306,10",
                "991,400042,1,307,10",
            ),
        ))

        val guardTeam = BattleTeamBuilder(
            BattleConfigRepository.loadDefault(),
            BattleEquipmentRepository.loadDefault(),
        ).build(
            listOf(
                BattleHeroSpec(
                    heroId = 100479,
                    position = 2,
                    troops = 1000,
                    equipmentIds = listOf(1072),
                    equipmentSkillIds = listOf(400072),
                    equipmentSkillLevels = listOf(1),
                ),
            ),
        )
        val base = twoRoundResult()
        val guardEncoded = ClientBattleTextReplayAdapter.adapt(
            base.copy(
                attacker = guardTeam,
                defender = BattleTeam(emptyList()),
                entryAttacker = guardTeam,
                entryDefender = BattleTeam(emptyList()),
                events = emptyList(),
            ),
        ).map(ClientReportAction::encode)
        assertTrue(guardEncoded.contains("1w3,400072,1,3"))
    }

    @Test
    fun `equipment feature action encodes the official 8x shape`() {
        val team = BattleTeam(
            heroes = listOf(hero(1, 0)),
            preparationActions = listOf(
                BattlePreparationAction(
                    stage = BattlePreparationStage.EQUIPMENT,
                    sourceId = 450037,
                    sourcePosition = 0,
                    targetPosition = 0,
                    actionId = "8x".toInt(36),
                    amountExact = 8.0,
                    actionParameter = 200957,
                    containerSourceId = 1102,
                ),
            ),
        )
        val base = twoRoundResult()
        val actions = ClientBattleTextReplayAdapter.adapt(
            base.copy(
                attacker = team,
                defender = BattleTeam(emptyList()),
                entryAttacker = team,
                entryDefender = BattleTeam(emptyList()),
                events = emptyList(),
            ),
        )

        assertTrue(
            actions.contains(
                ClientReportAction("8x".toInt(36), listOf(1, 450037, 1, 200957, 8)),
            ),
        )
    }

    @Test
    fun `troop feature modifiers are emitted inside their source block`() {
        val base = twoRoundResult()
        val result = base.copy(
            attacker = base.attacker.copy(
                preparationSources = listOf(
                    BattlePreparationSource(BattlePreparationStage.TROOP, 296105, 0),
                ),
                preparationModifiers = listOf(
                    BattlePreparationModifier(
                        stage = BattlePreparationStage.TROOP,
                        sourceId = 296105,
                        sourcePosition = 0,
                        targetPosition = 0,
                        effectId = 522,
                        amount = 8,
                    ),
                ),
            ),
            defender = BattleTeam(emptyList()),
            events = emptyList(),
        )

        val encoded = ClientBattleTextReplayAdapter.adapt(result).map(ClientReportAction::encode)

        assertTrue(encoded.contains("7x1,296105"))
        assertTrue(encoded.contains("ja1,296105,1,522,8"))
    }

    @Test
    fun `preparation sources follow official stage order regardless of input order`() {
        val base = twoRoundResult()
        val result = base.copy(
            attacker = base.attacker.copy(
                preparationSources = listOf(
                    BattlePreparationSource(BattlePreparationStage.EQUIPMENT, 1102, 0),
                    BattlePreparationSource(BattlePreparationStage.TROOP, 296104, 0),
                    BattlePreparationSource(BattlePreparationStage.ARMY, 295040),
                    BattlePreparationSource(BattlePreparationStage.SYSTEM, 295090),
                ),
            ),
            defender = BattleTeam(emptyList()),
            events = emptyList(),
        )

        val encoded = ClientBattleTextReplayAdapter.adapt(result).map(ClientReportAction::encode)
        val sources = encoded.filter {
            it.startsWith("0k") || it.startsWith("5s") ||
                it.startsWith("7x") || it.startsWith("ba")
        }

        assertEquals(
            listOf("0k0,295090", "5s0,295040", "7x1,296104", "ba1,1102"),
            sources,
        )
    }

    @Test
    fun `preparation stages merge both sides before advancing to the next stage`() {
        val base = twoRoundResult()
        val result = base.copy(
            attacker = base.attacker.copy(
                preparationSources = listOf(
                    BattlePreparationSource(BattlePreparationStage.EQUIPMENT, 1024, 0),
                    BattlePreparationSource(BattlePreparationStage.ARMY, 295020),
                ),
            ),
            defender = base.defender.copy(
                preparationSources = listOf(
                    BattlePreparationSource(BattlePreparationStage.TROOP, 296105, 0),
                    BattlePreparationSource(BattlePreparationStage.ARMY, 291005),
                ),
            ),
            events = emptyList(),
        )

        val sources = ClientBattleTextReplayAdapter.adapt(result)
            .map(ClientReportAction::encode)
            .filter { it.startsWith("5s") || it.startsWith("7x") || it.startsWith("ba") }

        assertEquals(
            listOf("5s0,295020", "5s7,291005", "7x6,296105", "ba1,1024"),
            sources,
        )
    }

    @Test
    fun `flat preparation attributes use official five-field actions`() {
        val base = twoRoundResult()
        val result = base.copy(
            attacker = base.attacker.copy(
                preparationEffects = listOf(
                    BattlePreparationEffect(
                        stage = BattlePreparationStage.TROOP,
                        sourceId = 296104,
                        targetPosition = 0,
                        stat = BattleStat.DEFENSE,
                        strength = 6,
                        delta = 6,
                        valueAfter = 172,
                        valueAfterExact = 172.8,
                        percent = false,
                        sourcePosition = 0,
                    ),
                ),
            ),
            defender = BattleTeam(emptyList()),
            events = emptyList(),
        )

        val encoded = ClientBattleTextReplayAdapter.adapt(result).map(ClientReportAction::encode)

        assertTrue(encoded.contains("7x1,296104"))
        assertTrue(encoded.contains("0w1,296104,1,6,172.8"))
    }

    @Test
    fun `stat changes retain effect delta duration and resulting value`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.StatChanged(
                    round = 0,
                    source = source,
                    target = source,
                    stat = BattleStat.ATTACK,
                    delta = 23,
                    durationRounds = 8,
                    skillId = 200001,
                    valueAfter = 197,
                ),
            ),
        )

        assertTrue(
            ClientBattleTextReplayAdapter.adapt(result).any {
                it.encode() == "191,200001,1,8,23,197"
            },
        )
    }

    @Test
    fun `stat changes emit exact decimal delta and resulting value`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.StatChanged(
                    round = 0,
                    source = source,
                    target = source,
                    stat = BattleStat.ATTACK,
                    delta = 28,
                    durationRounds = 8,
                    skillId = 200001,
                    valueAfter = 135,
                    deltaExact = 28.9,
                    valueAfterExact = 135.5,
                ),
            ),
        )

        assertTrue(
            ClientBattleTextReplayAdapter.adapt(result).any {
                it.encode() == "191,200001,1,8,28.9,135.5"
            },
        )
    }

    @Test
    fun `stat changes round client display values to one decimal`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.StatChanged(
                    round = 0,
                    source = source,
                    target = source,
                    stat = BattleStat.ATTACK,
                    delta = 13,
                    durationRounds = 8,
                    skillId = 200001,
                    valueAfter = 144,
                    deltaExact = 13.15,
                    valueAfterExact = 144.65,
                ),
            ),
        )

        assertTrue(
            ClientBattleTextReplayAdapter.adapt(result).any {
                it.encode() == "191,200001,1,8,13.2,144.7"
            },
        )
    }

    @Test
    fun `stat changes retain the caster separately from the affected hero`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val target = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(4))
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.StatChanged(
                    round = 0,
                    source = source,
                    target = target,
                    stat = BattleStat.DEFENSE,
                    delta = -18,
                    durationRounds = 3,
                    skillId = 200014,
                    strength = 22,
                    valueAfter = 152,
                ),
            ),
        )

        assertTrue(
            ClientBattleTextReplayAdapter.adapt(result).any {
                it.encode() == "1h1,200014,6,22,18,152"
            },
        )
    }

    @Test
    fun `preparation metadata uses entry snapshot instead of final buffed stats`() {
        val entry = twoRoundResult().attacker.heroes.first().copy(
            stats = BattleStats(100, 110, 120, 130, 10, 3),
        )
        val final = entry.copy(stats = BattleStats(123, 110, 120, 130, 10, 3))
        val result = twoRoundResult().copy(
            attacker = BattleTeam(listOf(final)),
            defender = BattleTeam(emptyList()),
            entryAttacker = BattleTeam(listOf(entry)),
            entryDefender = BattleTeam(emptyList()),
            events = emptyList(),
        )

        assertEquals(
            1_000,
            ClientBattleTextReplayAdapter.adapt(result)
                .first { it.id == ClientBattleTextReplayProtocol.HERO_INFO }
                .params[2],
        )
    }

    @Test
    fun `persistent modifiers retain source target effect and configured strength`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val target = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(4))
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.ModifierApplied(
                    round = 0,
                    source = source,
                    target = target,
                    skillId = 200204,
                    effectId = 523,
                    amount = 53,
                    durationRounds = 8,
                ),
            ),
        )

        assertTrue(
            ClientBattleTextReplayAdapter.adapt(result).any {
                it.encode() == "ja1,200204,6,523,53"
            },
        )
    }

    @Test
    fun `successful preparation statuses use applied action and configured effect ids`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val target = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(4))
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.StatusApplied(
                    0,
                    source,
                    source,
                    BattleStatus.FIRST_ACTION,
                    durationRounds = 3,
                    skillId = 200233,
                    effectId = 761,
                ),
                BattleEvent.StatusApplied(
                    0,
                    source,
                    target,
                    BattleStatus.CONFUSION,
                    durationRounds = 2,
                    skillId = 200002,
                    effectId = 701,
                ),
            ),
        )

        assertEquals(
            listOf("0s1,761", "0s6,701"),
            ClientBattleTextReplayAdapter.adapt(result)
                .filter {
                    it.id in setOf(
                        28,
                        29,
                    )
                }
                .map(ClientReportAction::encode),
        )
    }

    @Test
    fun `derived skills use the nested effect action instead of another root activation`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.SkillTriggered(
                    0,
                    source,
                    rootSkillId = 200900,
                    skillId = 200900,
                    trigger = com.stzb.battle.core.skill.BattleTrigger.BATTLE_PASSIVE,
                ),
                BattleEvent.SkillTriggered(
                    0,
                    source,
                    rootSkillId = 200900,
                    skillId = 211900,
                    trigger = com.stzb.battle.core.skill.BattleTrigger.BATTLE_PASSIVE,
                ),
            ),
        )

        assertEquals(
            listOf("0l1,200900", "8c1,211900"),
            ClientBattleTextReplayAdapter.adapt(result)
                .filter {
                    it.id in setOf(
                        ClientBattleTextReplayProtocol.SKILL_TRIGGERED_PASSIVE,
                        300,
                    )
                }
                .map(ClientReportAction::encode),
        )
    }

    @Test
    fun `projects skill trigger using client action selected by skill kind`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.SkillTriggered(
                    0,
                    source,
                    200001,
                    200001,
                    com.stzb.battle.core.skill.BattleTrigger.BATTLE_PASSIVE,
                ),
                BattleEvent.SkillTriggered(
                    1,
                    source,
                    200002,
                    200002,
                    com.stzb.battle.core.skill.BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                ),
                BattleEvent.SkillTriggered(
                    1,
                    source,
                    200003,
                    200003,
                    com.stzb.battle.core.skill.BattleTrigger.PURSUIT_ATTEMPT,
                ),
            ),
        )

        assertEquals(
            listOf(
                ClientReportAction(ClientBattleTextReplayProtocol.SKILL_TRIGGERED_PASSIVE, listOf(1, 200001)),
                ClientReportAction(ClientBattleTextReplayProtocol.SKILL_TRIGGERED_ACTIVE, listOf(1, 200002)),
                ClientReportAction(ClientBattleTextReplayProtocol.SKILL_TRIGGERED_PURSUIT, listOf(1, 200003)),
            ),
            ClientBattleTextReplayAdapter.adapt(result).filter {
                it.id in setOf(
                    ClientBattleTextReplayProtocol.SKILL_TRIGGERED_ACTIVE,
                    ClientBattleTextReplayProtocol.SKILL_TRIGGERED_PASSIVE,
                    ClientBattleTextReplayProtocol.SKILL_TRIGGERED_PURSUIT,
                )
            },
        )
    }

    @Test
    fun `projects preparation lifecycle in engine event order without inventing action ids`() {
        val source = BattleHeroRef(Side.ATTACKER, 1, BattleHeroId(1))
        val trigger = com.stzb.battle.core.skill.BattleTrigger.ACTIVE_SKILL_ATTEMPT
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.SkillPreparationStarted(1, source, 200031, readyRound = 2),
                BattleEvent.SkillPreparationCompleted(2, source, 200031, 200031, 1, 2, trigger),
                BattleEvent.SkillTriggered(2, source, 200031, 200031, trigger),
                BattleEvent.SkillPreparationStarted(2, source, 200032, readyRound = 3),
                BattleEvent.SkillPreparationCancelled(2, source, 200032, 200032, "CONFUSION"),
            ),
        )

        assertEquals(
            listOf(
                ClientReportAction(ClientBattleTextReplayProtocol.SKILL_PREPARATION_STARTED, listOf(2, 200031)),
                ClientReportAction(ClientBattleTextReplayProtocol.SKILL_TRIGGERED_ACTIVE, listOf(2, 200031)),
                ClientReportAction(ClientBattleTextReplayProtocol.SKILL_PREPARATION_STARTED, listOf(2, 200032)),
                ClientReportAction(ClientBattleTextReplayProtocol.SKILL_PREPARATION_CANCELLED, listOf(2, 200032)),
            ),
            ClientBattleTextReplayAdapter.adapt(result).filter {
                it.id in setOf(
                    ClientBattleTextReplayProtocol.SKILL_PREPARATION_STARTED,
                    ClientBattleTextReplayProtocol.SKILL_PREPARATION_CANCELLED,
                    ClientBattleTextReplayProtocol.SKILL_TRIGGERED_ACTIVE,
                )
            },
        )
    }

    @Test
    fun `projects removed and expired effects through verified client status removal action`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val target = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(4))
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.StatusRemoved(1, source, target, 200014, 522),
                BattleEvent.EffectExpired(2, source, target, 200014, 524),
            ),
        )

        assertEquals(
            listOf(
                listOf<Any>(6, 1, 200014, 522),
                listOf<Any>(6, 1, 200014, 524),
            ),
            ClientBattleTextReplayAdapter.adapt(result)
                .filter { it.id == ClientBattleTextReplayProtocol.STATUS_REMOVED }
                .map(ClientReportAction::params),
        )
    }

    @Test
    fun `safe projection diagnoses unsupported effect blocks while strict projection fails`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val target = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(4))
        val result = twoRoundResult().copy(
            events = listOf(BattleEvent.EffectBlocked(1, source, target, 200014, 401, 999)),
        )
        val diagnostics = mutableListOf<String>()

        val safeActions = ClientBattleTextReplayAdapter.adapt(result, diagnostics::add)

        assertTrue(safeActions.none { it.id == 999 })
        assertTrue(diagnostics.single().contains("EffectBlocked"))
        assertFailsWith<UnsupportedBattleReportProjectionException> {
            ClientBattleTextReplayAdapter.adaptStrict(result)
        }
    }

    @Test
    fun `battle unrecoverable block uses official 5u action`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val target = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(4))
        val result = twoRoundResult().copy(
            events = listOf(BattleEvent.EffectBlocked(1, source, target, 200884, 401, 207)),
        )
        val diagnostics = mutableListOf<String>()

        val actions = ClientBattleTextReplayAdapter.adapt(result, diagnostics::add)

        assertEquals(
            listOf("5u6,207"),
            actions.filter { it.id == "5u".toInt(36) }.map(ClientReportAction::encode),
        )
        assertEquals(emptyList(), diagnostics)
    }

    @Test
    fun `creates one preparation stage and one client round per engine round`() {
        val actions = ClientBattleTextReplayAdapter.adapt(twoRoundResult())

        assertEquals(1, actions.count { it.id == ClientBattleTextReplayProtocol.PREPARE })
        assertEquals(
            listOf(listOf<Any>(1), listOf<Any>(2)),
            actions.filter { it.id == ClientBattleTextReplayProtocol.ROUND }.map { it.params },
        )
        assertEquals(
            listOf(1, 2, 6),
            actions.filter { it.id == ClientBattleTextReplayProtocol.HERO_NAME }.map { it.params.first() },
        )
    }

    @Test
    fun `initializes every named hero with fixed width detail data`() {
        val result = twoRoundResult().let { base ->
            base.copy(
                attacker = base.attacker.copy(
                    heroes = base.attacker.heroes.mapIndexed { index, hero ->
                        if (index == 0) hero.copy(level = 20, skillIds = listOf(200012, 200834)) else hero
                    },
                ),
            )
        }

        val actions = ClientBattleTextReplayAdapter.adapt(result)
        val heroInfo = actions.filter { it.id == ClientBattleTextReplayProtocol.HERO_INFO }

        assertEquals(3, heroInfo.size)
        assertEquals(
            listOf<Any>(
                1, 20, 1_000,
                200012, 1, 200834, 1, 0, 0,
                0, 0, "",
                0, 0, 0, 0, 0, 0,
                0,
            ),
            heroInfo.first().params,
        )
        assertTrue(heroInfo.all { it.params.size == 19 })
        assertTrue(heroInfo.all { it.params.takeLast(7) == List<Any>(7) { 0 } })
    }

    @Test
    fun `projects normal skill damage and recovery into distinct text actions`() {
        val actions = ClientBattleTextReplayAdapter.adapt(eventResult())

        val normalDamageIndex = actions.indexOfFirst {
            it.id == ClientBattleTextReplayProtocol.NORMAL_DAMAGE &&
                it.params == listOf<Any>(6, 120, 880)
        }
        assertTrue(normalDamageIndex > 0)
        assertEquals(
            ClientReportAction(ClientBattleTextReplayProtocol.NORMAL_ATTACK, listOf(1, 6)),
            actions[normalDamageIndex - 2],
        )
        assertEquals(ClientBattleTextReplayProtocol.SKILL_BEGIN, actions[normalDamageIndex - 1].id)
        assertEquals(ClientBattleTextReplayProtocol.SKILL_END, actions[normalDamageIndex + 1].id)

        val skillDamageIndex = actions.indexOfFirst {
            it.id == ClientBattleTextReplayProtocol.SKILL_DAMAGE &&
                it.params == listOf<Any>(1, 200012, 6, 180, 700)
        }
        assertTrue(skillDamageIndex > 1)
        assertEquals(ClientBattleTextReplayProtocol.SKILL_BEGIN, actions[skillDamageIndex - 2].id)
        assertTrue(actions.any {
            it.id == ClientBattleTextReplayProtocol.SKILL_CAST &&
                it.params == listOf<Any>(1, 1, 200012)
        })
        assertEquals(ClientBattleTextReplayProtocol.SKILL_END, actions[skillDamageIndex + 1].id)
        assertTrue(actions.any {
            it.id == ClientBattleTextReplayProtocol.RECOVERY &&
                it.params == listOf<Any>(1, 200001, 1, 70, 950)
        })
    }

    @Test
    fun `projects panic burn and hex damage with their distinct client actions`() {
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val target = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(4))
        val result = eventResult().copy(
            events = listOf(
                BattleEvent.OngoingDamage(
                    1, source, target, BattleStatus.PANIC, 30, 970, 200020,
                ),
                BattleEvent.OngoingDamage(
                    1, source, target, BattleStatus.BURN, 40, 930, 200020,
                ),
                BattleEvent.OngoingDamage(
                    1, source, target, BattleStatus.HEX, 50, 880, 200020,
                ),
            ),
        )

        val ongoingDamageActions = ClientBattleTextReplayAdapter.adapt(result)
            .filter {
                it.params.size == 5 &&
                    it.params[0] == 6 &&
                    it.params[1] == 1 &&
                    it.params[2] == 200020
            }

        assertEquals(listOf(62, 242, 243), ongoingDamageActions.map(ClientReportAction::id))
    }

    @Test
    fun `projects preparation start with the real client action`() {
        val source = BattleHeroRef(Side.ATTACKER, 1, BattleHeroId(1))
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.RoundStart(1),
                BattleEvent.SkillPreparationStarted(1, source, 200031, readyRound = 2),
                BattleEvent.RoundEnd(1),
            ),
        )

        val actions = ClientBattleTextReplayAdapter.adapt(result)

        assertTrue(actions.any {
            it.id == ClientBattleTextReplayProtocol.SKILL_PREPARATION_STARTED &&
                it.params == listOf<Any>(2, 200031)
        })
    }

    @Test
    fun `projects every hero action with the real client lifecycle`() {
        val source = BattleHeroRef(Side.DEFENDER, 2, BattleHeroId(4))
        val result = twoRoundResult().copy(
            events = listOf(
                BattleEvent.RoundStart(1),
                BattleEvent.HeroActionStart(1, source),
                BattleEvent.HeroActionEnd(1, source),
                BattleEvent.RoundEnd(1),
            ),
        )

        val actions = ClientBattleTextReplayAdapter.adapt(result)
        val startIndex = actions.indexOfFirst {
            it.id == ClientBattleTextReplayProtocol.HERO_ACTION_START &&
                it.params == listOf<Any>(4)
        }

        assertTrue(startIndex >= 0)
        assertEquals(
            ClientReportAction(ClientBattleTextReplayProtocol.HERO_ACTION_END, listOf(4)),
            actions[startIndex + 1],
        )
    }

    @Test
    fun `projects status dot evade and stat change with their original skill id`() {
        val actions = ClientBattleTextReplayAdapter.adapt(stateResult())

        val statusIndex = actions.indexOfFirst {
            it.id == ClientBattleTextReplayProtocol.SKILL_CAST &&
                it.params == listOf<Any>(6, 1, 200002)
        }
        assertTrue(statusIndex > 1)
        assertEquals(ClientBattleTextReplayProtocol.SKILL_BEGIN, actions[statusIndex - 2].id)
        assertEquals(ClientBattleTextReplayProtocol.SKILL_CAST, actions[statusIndex - 1].id)
        assertEquals(ClientBattleTextReplayProtocol.SKILL_END, actions[statusIndex + 1].id)
        assertTrue(actions.any {
            it.id == "6q".toInt(36) &&
                it.params == listOf<Any>(6, 1, 200002, 60, 640)
        })
        assertTrue(actions.any {
            it.id == ClientBattleTextReplayProtocol.DAMAGE_EVADED &&
                it.params == listOf<Any>(6)
        })
        assertTrue(actions.any {
            it.id == ClientBattleTextReplayProtocol.INITIAL_ATTACK &&
                it.params == listOf<Any>(1, 200036, 1, 2, 10, 10)
        })
    }

    @Test
    fun `omits unattributed effect text actions while retaining evade`() {
        val actions = ClientBattleTextReplayAdapter.adapt(unattributedEffectsResult())

        assertTrue(actions.none { it.id == ClientBattleTextReplayProtocol.RECOVERY })
        assertTrue(actions.none { it.id == ClientBattleTextReplayProtocol.ONGOING_DAMAGE })
        assertEquals(
            listOf(listOf<Any>(6)),
            actions.filter { it.id == ClientBattleTextReplayProtocol.DAMAGE_EVADED }.map { it.params },
        )
        assertTrue(actions.none { it.id == ClientBattleTextReplayProtocol.SKILL_CAST })
    }

    @Test
    fun `safe projection ignores unsupported skill effects and strict projection fails`() {
        val baseResult = twoRoundResult()
        val source = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val resultWithUnsupportedEffect = baseResult.copy(
            events = baseResult.events + BattleEvent.UnsupportedSkillEffect(
                round = 2,
                skillId = 200999,
                effectId = 999,
                source = source,
                rawDescription = "unsupported effect",
            ),
        )

        val json = BattleReportCodec.toJson(resultWithUnsupportedEffect)

        assertTrue(json.contains("UnsupportedSkillEffect"))
        assertTrue(json.contains("\"skillId\":200999"))
        val diagnostics = mutableListOf<String>()
        val actions = ClientBattleTextReplayAdapter.adapt(resultWithUnsupportedEffect, diagnostics::add)
        assertTrue(actions.none {
            it.id == ClientBattleTextReplayProtocol.SKILL_CAST &&
                it.params == listOf<Any>(1, 1, 200999)
        })
        assertTrue(diagnostics.single().contains("skill=200999"))
        assertFailsWith<UnsupportedBattleReportProjectionException> {
            ClientBattleTextReplayAdapter.adaptStrict(resultWithUnsupportedEffect)
        }
    }

    @Test
    fun `ends the report before writing final troops for both sides`() {
        val actions = ClientBattleTextReplayAdapter.adapt(eventResult())
        val endIndex = actions.indexOfFirst { it.id == ClientBattleTextReplayProtocol.END }
        assertTrue(endIndex >= 0)
        assertTrue(
            actions.take(endIndex).none { it.id == ClientBattleTextReplayProtocol.FINAL_TROOPS },
        )
        val finalTroops = actions.drop(endIndex + 1)
            .filter { it.id == ClientBattleTextReplayProtocol.FINAL_TROOPS }

        assertEquals(3, finalTroops.size)
        assertEquals(listOf<Any>(1, 950, 50), finalTroops[0].params)
        assertEquals(listOf<Any>(2, 1000, 0), finalTroops[1].params)
        assertEquals(listOf<Any>(6, 700, 300), finalTroops[2].params)
    }

    @Test
    fun `writes the client battle result immediately after the end marker`() {
        val base = eventResult()
        val expected = listOf(
            BattleOutcome.ATTACKER_WIN to ClientReportAction(ClientBattleTextReplayProtocol.ATTACKER_WIN),
            BattleOutcome.DEFENDER_WIN to ClientReportAction(ClientBattleTextReplayProtocol.DEFENDER_WIN),
            BattleOutcome.DRAW to ClientReportAction(ClientBattleTextReplayProtocol.DRAW, listOf(3)),
        )

        expected.forEach { (outcome, expectedAction) ->
            val actions = ClientBattleTextReplayAdapter.adapt(base.copy(outcome = outcome))
            val endIndex = actions.indexOfFirst { it.id == ClientBattleTextReplayProtocol.END }

            assertEquals(expectedAction, actions[endIndex + 1])
        }
    }

    private fun twoRoundResult(): BattleResult =
        BattleResult(
            outcome = BattleOutcome.ATTACKER_WIN,
            attacker = BattleTeam(listOf(hero(1, 0), hero(2, 1))),
            defender = BattleTeam(listOf(hero(4, 0))),
            events = listOf(
                BattleEvent.BattleStart,
                BattleEvent.RoundStart(1),
                BattleEvent.RoundStart(2),
                BattleEvent.BattleEnd(BattleOutcome.ATTACKER_WIN),
            ),
        )

    private fun eventResult(): BattleResult {
        val attacker = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val defender = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(4))
        return BattleResult(
            outcome = BattleOutcome.ATTACKER_WIN,
            attacker = BattleTeam(
                listOf(
                    hero(1, 0, troops = 950),
                    hero(2, 1),
                ),
            ),
            defender = BattleTeam(listOf(hero(4, 0, troops = 700))),
            events = listOf(
                BattleEvent.NormalAttack(1, attacker, defender, 120, 880),
                BattleEvent.SkillDamage(1, 200012, 301, attacker, defender, 180, 700),
                BattleEvent.Recovery(1, attacker, attacker, 70, 950, skillId = 200001),
            ),
        )
    }

    private fun unattributedEffectsResult(): BattleResult {
        val attacker = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val defender = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(4))
        return BattleResult(
            outcome = BattleOutcome.ATTACKER_WIN,
            attacker = BattleTeam(listOf(hero(1, 0))),
            defender = BattleTeam(listOf(hero(4, 0))),
            events = listOf(
                BattleEvent.Recovery(1, attacker, attacker, 70, 950),
                BattleEvent.StatusApplied(1, attacker, defender, BattleStatus.BURN, 2),
                BattleEvent.OngoingDamage(2, attacker, defender, BattleStatus.BURN, 60, 640),
                BattleEvent.StatChanged(2, attacker, attacker, BattleStat.ATTACK, 10, 2),
                BattleEvent.Evaded(2, attacker, defender),
            ),
        )
    }

    private fun stateResult(): BattleResult {
        val attacker = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(1))
        val defender = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(4))
        return BattleResult(
            outcome = BattleOutcome.ATTACKER_WIN,
            attacker = BattleTeam(listOf(hero(1, 0))),
            defender = BattleTeam(listOf(hero(4, 0))),
            events = listOf(
                BattleEvent.StatusApplied(1, attacker, defender, BattleStatus.BURN, 2, skillId = 200002),
                BattleEvent.OngoingDamage(2, attacker, defender, BattleStatus.BURN, 60, 640, skillId = 200002),
                BattleEvent.Evaded(2, attacker, defender),
                BattleEvent.StatChanged(2, attacker, attacker, BattleStat.ATTACK, 10, 2, skillId = 200036),
            ),
        )
    }

    private fun hero(id: Int, position: Int, troops: Int = 1_000): BattleHero =
        BattleHero(
            id = BattleHeroId(id),
            position = position,
            stats = BattleStats(1, 1, 1, 1, 0, 1),
            troops = troops,
            maxTroops = 1_000,
        )
}

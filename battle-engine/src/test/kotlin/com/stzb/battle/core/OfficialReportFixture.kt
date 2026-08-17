package com.stzb.battle.core

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stzb.battle.core.skill.BattleTargetDecisionSource
import com.stzb.battle.core.ClientTroopFeatureRepository
import com.stzb.battle.core.ClientTroopTypeRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import kotlin.math.roundToInt

internal object OfficialReportFixture {
    private data class PrecisePaperStats(
        val entry: Map<Int, Map<BattleStat, Int>>,
        val inherent: Map<Int, Map<BattleStat, Int>>,
    )

    private data class DecisionKey(
        val sourcePosition: Int,
        val skillId: Int,
        val effectId: Int,
    )

    data class Action(
        val id: Int,
        val raw: String,
        val params: List<String>,
    )

    data class JaTuple(
        val sourcePosition: Int,
        val sourceId: Int,
        val targetPosition: Int,
        val effectId: Int,
        val amount: Int,
    )

    data class FullBattleSummary(
        val rounds: Int,
        val actionRoundsByPosition: Map<Int, List<Int>>,
        val skillTriggers: Map<Int, Int>,
        val damageBySide: Map<Side, Int>,
        val recoveryBySide: Map<Side, Int>,
        val finalTroopsByPosition: Map<Int, Int>,
        val outcome: BattleOutcome,
    )

    private val mapper = jacksonObjectMapper()
    private val customSkillMapper = jacksonObjectMapper().enable(
        JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature(),
    )

    fun read(path: Path): List<Action> =
        parseText(mapper.readTree(path.toFile())[1]["report"].asText())

    fun hasReport(path: Path): Boolean =
        mapper.readTree(path.toFile()).path(1).path("report").isTextual

    fun readableReports(): List<Path> =
        listOf(
            Path.of("src/test/resources/assent/cfg/paper/11"),
            Path.of("src/test/resources/assent/cfg/paper/6231"),
        ).flatMap { directory ->
            Files.list(directory).use { paths ->
                paths
                    .filter { it.fileName.toString().endsWith(".json") }
                    .filter(::hasReport)
                    .toList()
            }
        }.sortedBy(Path::toString)

    fun fullBattleSummary(actions: List<Action>): FullBattleSummary {
        var round = 0
        var currentActor: Int? = null
        val actionRounds = linkedMapOf<Int, MutableList<Int>>()
        val skillTriggers = linkedMapOf<Int, Int>()
        val damageBySide = linkedMapOf(
            Side.ATTACKER to 0,
            Side.DEFENDER to 0,
        )
        val recoveryBySide = linkedMapOf(
            Side.ATTACKER to 0,
            Side.DEFENDER to 0,
        )
        val finalTroops = linkedMapOf<Int, Int>()
        var outcome: BattleOutcome? = null

        actions.forEach { action ->
            when (action.id) {
                ClientBattleTextReplayProtocol.ROUND -> {
                    round = action.intParam(0)
                    currentActor = null
                }
                ClientBattleTextReplayProtocol.HERO_ACTION_START -> {
                    val position = action.intParam(0)
                    currentActor = position
                    actionRounds.getOrPut(position, ::mutableListOf).add(round)
                }
                ClientBattleTextReplayProtocol.HERO_ACTION_END -> currentActor = null
                ClientBattleTextReplayProtocol.SKILL_TRIGGERED_PASSIVE,
                ClientBattleTextReplayProtocol.SKILL_TRIGGERED_COMMAND,
                ClientBattleTextReplayProtocol.SKILL_TRIGGERED_ACTIVE,
                ClientBattleTextReplayProtocol.SKILL_TRIGGERED_PURSUIT,
                -> {
                    val skillId = action.intParam(1)
                    skillTriggers[skillId] = skillTriggers.getOrDefault(skillId, 0) + 1
                }
                ClientBattleTextReplayProtocol.NORMAL_DAMAGE -> {
                    val sourcePosition = requireNotNull(currentActor) {
                        "normal damage outside hero action: ${action.raw}"
                    }
                    damageBySide.add(sideForPosition(sourcePosition), action.intParam(1))
                }
                ClientBattleTextReplayProtocol.ATTACK_SKILL_DAMAGE,
                ClientBattleTextReplayProtocol.SKILL_DAMAGE,
                -> {
                    val sourcePosition = action.intParam(0)
                    damageBySide.add(sideForPosition(sourcePosition), action.intParam(3))
                }
                ClientBattleTextReplayProtocol.PANIC_ONGOING_DAMAGE,
                ClientBattleTextReplayProtocol.ONGOING_DAMAGE,
                ClientBattleTextReplayProtocol.HEX_ONGOING_DAMAGE,
                -> {
                    val sourcePosition = action.intParam(1)
                    damageBySide.add(sideForPosition(sourcePosition), action.intParam(3))
                }
                ClientBattleTextReplayProtocol.RECOVERY,
                ClientBattleTextReplayProtocol.ONGOING_RECOVERY,
                -> {
                    val sourcePosition = action.intParam(0)
                    recoveryBySide.add(sideForPosition(sourcePosition), action.intParam(3))
                }
                ClientBattleTextReplayProtocol.ATTACK_DAMAGE_RECOVERY -> {
                    val sourcePosition = action.intParam(0)
                    recoveryBySide.add(sideForPosition(sourcePosition), action.intParam(1))
                }
                ClientBattleTextReplayProtocol.FINAL_TROOPS ->
                    finalTroops[action.intParam(0)] = action.intParam(1)
                ClientBattleTextReplayProtocol.ATTACKER_WIN ->
                    outcome = BattleOutcome.ATTACKER_WIN
                ClientBattleTextReplayProtocol.DRAW ->
                    outcome = BattleOutcome.DRAW
                ClientBattleTextReplayProtocol.DEFENDER_WIN ->
                    outcome = BattleOutcome.DEFENDER_WIN
            }
        }

        return FullBattleSummary(
            rounds = round,
            actionRoundsByPosition = actionRounds.mapValues { (_, rounds) -> rounds.toList() },
            skillTriggers = skillTriggers.toMap(),
            damageBySide = damageBySide.toMap(),
            recoveryBySide = recoveryBySide.toMap(),
            finalTroopsByPosition = finalTroops.toMap(),
            outcome = requireNotNull(outcome) { "paper report has no battle outcome" },
        )
    }

    fun fullBattleSummary(result: BattleResult): FullBattleSummary {
        val actionRounds = result.events
            .filterIsInstance<BattleEvent.HeroActionStart>()
            .groupBy(
                { ClientBattleTextReplayProtocol.position(it.source) },
                BattleEvent.HeroActionStart::round,
            )
        val skillTriggers = result.events
            .filterIsInstance<BattleEvent.SkillTriggered>()
            .groupingBy(BattleEvent.SkillTriggered::skillId)
            .eachCount()
        val damageBySide = linkedMapOf(
            Side.ATTACKER to 0,
            Side.DEFENDER to 0,
        )
        val recoveryBySide = linkedMapOf(
            Side.ATTACKER to 0,
            Side.DEFENDER to 0,
        )
        result.events.forEach { event ->
            when (event) {
                is BattleEvent.NormalAttack ->
                    damageBySide.add(event.source.side, event.damage)
                is BattleEvent.SkillDamage ->
                    damageBySide.add(event.source.side, event.damage)
                is BattleEvent.OngoingDamage ->
                    damageBySide.add(event.source.side, event.damage)
                is BattleEvent.Recovery ->
                    recoveryBySide.add(event.source.side, event.amount)
                else -> Unit
            }
        }
        val finalTroops = buildMap {
            result.attacker.heroes.forEach { hero ->
                put(ClientBattleTextReplayProtocol.position(Side.ATTACKER, hero.position), hero.troops)
            }
            result.defender.heroes.forEach { hero ->
                put(ClientBattleTextReplayProtocol.position(Side.DEFENDER, hero.position), hero.troops)
            }
        }
        return FullBattleSummary(
            rounds = result.events.filterIsInstance<BattleEvent.RoundStart>()
                .maxOfOrNull(BattleEvent.RoundStart::round) ?: 0,
            actionRoundsByPosition = actionRounds,
            skillTriggers = skillTriggers,
            damageBySide = damageBySide,
            recoveryBySide = recoveryBySide,
            finalTroopsByPosition = finalTroops,
            outcome = result.outcome,
        )
    }

    fun parseText(text: String): List<Action> =
        text.split('#').map { raw ->
            require(raw.length >= 2) { "invalid report action: $raw" }
            Action(
                id = raw.take(2).toInt(36),
                raw = raw,
                params = raw.drop(2).takeIf(String::isNotEmpty)?.split(',') ?: emptyList(),
            )
        }

    fun preparation(actions: List<Action>): List<Action> =
        actions.takeWhile { it.id != ClientBattleTextReplayProtocol.ROUND }

    fun jaTuples(actions: List<Action>): List<JaTuple> =
        actions
            .filter { it.id == "ja".toInt(36) }
            .map { action ->
                require(action.params.size == 5) {
                    "invalid ja action width ${action.params.size}: ${action.raw}"
                }
                JaTuple(
                    sourcePosition = action.intParam(0),
                    sourceId = action.intParam(1),
                    targetPosition = action.intParam(2),
                    effectId = action.intParam(3),
                    amount = action.intParam(4),
                )
            }

    fun reconstructBattleRequest(
        actions: List<Action>,
        config: BattleConfigRepository,
    ): BattleRequest {
        val preparation = preparation(actions)
        fun recordedMorale(actionId: Int): Int {
            val values = preparation
                .filter { it.id == actionId }
                .map { it.intParam(0) }
                .distinct()
            require(values.size <= 1) {
                "conflicting paper morale values for action=${actionId.toString(36)}: $values"
            }
            return values.singleOrNull() ?: 100
        }
        val moraleBySide = mapOf(
            Side.ATTACKER to recordedMorale("44".toInt(36)),
            Side.DEFENDER to recordedMorale("45".toInt(36)),
        )
        val heroIdsByClientPosition = preparation
            .filter { it.id == ClientBattleTextReplayProtocol.HERO_NAME }
            .associate { it.intParam(0) to it.intParam(1) }
        val equipmentIdsByClientPosition = preparation
            .filter { it.id == ClientBattleTextReplayProtocol.EQUIPMENT_EFFECT_SOURCE }
            .groupBy { it.intParam(0) }
            .mapValues { (_, sources) -> sources.map { it.intParam(1) }.distinct() }
        val explicitEquipmentFeaturesByClientPosition = preparation
            .mapNotNull { action ->
                if (action.params.size < 4) return@mapNotNull null
                val position = action.params[0].toIntOrNull()
                    ?.takeIf { it in 1..6 }
                    ?: return@mapNotNull null
                val featureSkillId = action.params[1].toIntOrNull()
                    ?.takeIf { it / 1_000 in setOf(450, 460) }
                    ?: return@mapNotNull null
                val level = action.params.drop(3).asReversed()
                    .firstNotNullOfOrNull { value ->
                        value.toIntOrNull()?.takeIf { it in 1..30 }
                    }
                    ?: return@mapNotNull null
                position to (featureSkillId to level)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, features) ->
                features.groupBy(Pair<Int, Int>::first)
                    .map { (featureSkillId, levels) ->
                        featureSkillId to levels.minOf(Pair<Int, Int>::second)
                    }
            }
        val featureParentByChild = config.allSkillIds()
            .asSequence()
            .filter { it in 450_000..469_999 }
            .flatMap { parentId ->
                config.skillDetails(parentId).asSequence()
                    .filter { it.effectId == 122 && it.constantParam > 0 }
                    .map { detail -> detail.constantParam to parentId }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, parents) -> parents.distinct().single() }
        val derivedFeatureLevels = jaTuples(actions)
            .filter { it.amount > 0 }
            .groupBy { tuple -> tuple.sourcePosition to tuple.sourceId }
            .mapValues { (_, tuples) -> tuples.minOf(JaTuple::amount) }
        val derivedEquipmentFeaturesByClientPosition = preparation
            .filter { it.id == "8c".toInt(36) && it.params.size == 2 }
            .mapNotNull { action ->
                val position = action.intParam(0)
                val childId = action.intParam(1)
                val parentId = featureParentByChild[childId] ?: return@mapNotNull null
                val level = derivedFeatureLevels[position to childId] ?: 1
                position to (parentId to level)
            }
            .groupBy({ it.first }, { it.second })
        val battlePhaseEquipmentFeaturesByClientPosition = actions
            .filter { it.params.size >= 4 }
            .mapNotNull { action ->
                val position = action.params.getOrNull(0)?.toIntOrNull()
                    ?.takeIf { it in 1..6 }
                    ?: return@mapNotNull null
                val childId = action.params.getOrNull(1)?.toIntOrNull()
                    ?: return@mapNotNull null
                val parentId = featureParentByChild[childId]
                    ?: return@mapNotNull null
                val level = action.params.last().toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?: return@mapNotNull null
                position to (parentId to level)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, features) ->
                features.groupBy(Pair<Int, Int>::first)
                    .map { (parentId, levels) ->
                        parentId to levels.minOf(Pair<Int, Int>::second)
                    }
            }
        val equipmentFeaturesByClientPosition =
            (explicitEquipmentFeaturesByClientPosition.keys +
                derivedEquipmentFeaturesByClientPosition.keys +
                battlePhaseEquipmentFeaturesByClientPosition.keys).associateWith { position ->
                (explicitEquipmentFeaturesByClientPosition[position].orEmpty() +
                    derivedEquipmentFeaturesByClientPosition[position].orEmpty() +
                    battlePhaseEquipmentFeaturesByClientPosition[position].orEmpty())
                    .distinctBy(Pair<Int, Int>::first)
            }
        val learnedTroopSkillsByClientPosition = preparation
            .filter { it.id == ClientBattleTextReplayProtocol.HERO_INFO }
            .associate { heroInfo ->
                val featureRepository = ClientTroopFeatureRepository.loadDefault()
                heroInfo.intParam(0) to listOf(heroInfo.intParam(9), heroInfo.intParam(10))
                    .flatMap(featureRepository::skillIds)
                    .toSet()
            }
        val troopTypeRepository = ClientTroopTypeRepository.loadDefault()
        val surfaceSkillByClientPosition = preparation
            .filter { it.id == ClientBattleTextReplayProtocol.SURFACE_EFFECT_SOURCE }
            .associate { it.intParam(0) to it.intParam(1) }
        val troopTypeByClientPosition = preparation
            .filter { it.id == ClientBattleTextReplayProtocol.TROOP_EFFECT_SOURCE }
            .groupBy { it.intParam(0) }
            .mapValues { (clientPosition, sources) ->
                val learnedSkills = learnedTroopSkillsByClientPosition[clientPosition].orEmpty()
                troopTypeRepository.heroTypeForSkillIds(
                    sources.map { it.intParam(1) }.filterNot(learnedSkills::contains),
                )
            }

        val specsByClientPosition = preparation
            .filter { it.id == ClientBattleTextReplayProtocol.HERO_INFO }
            .associate { heroInfo ->
                val clientPosition = heroInfo.intParam(0)
                val skillIds = listOf(
                    heroInfo.intParam(3),
                    heroInfo.intParam(5),
                    heroInfo.intParam(7),
                )
                clientPosition to BattleHeroSpec(
                    heroId = requireNotNull(heroIdsByClientPosition[clientPosition]) {
                        "missing 0e hero identity for client position $clientPosition"
                    },
                    position = formationPosition(clientPosition),
                    troops = heroInfo.intParam(2),
                    initialSkillId = skillIds.first(),
                    extraSkillIds = skillIds.drop(1),
                    skillLevels = listOf(
                        heroInfo.intParam(4),
                        heroInfo.intParam(6),
                        heroInfo.intParam(8),
                    ),
                    troopFeatureIds = listOf(heroInfo.intParam(9), heroInfo.intParam(10)),
                    heroType = troopTypeByClientPosition[clientPosition],
                    surfaceSkillId = surfaceSkillByClientPosition[clientPosition] ?: 0,
                    equipmentIds = equipmentIdsByClientPosition[clientPosition].orEmpty(),
                    equipmentSkillIds = listOf(
                        heroInfo.intParam(12),
                        heroInfo.intParam(14),
                        heroInfo.intParam(16),
                    ),
                    equipmentSkillLevels = listOf(
                        heroInfo.intParam(13),
                        heroInfo.intParam(15),
                        heroInfo.intParam(17),
                    ),
                    equipmentFeatureSkillIds =
                        equipmentFeaturesByClientPosition[clientPosition]
                            .orEmpty()
                            .map(Pair<Int, Int>::first),
                    equipmentFeatureSkillLevels =
                        equipmentFeaturesByClientPosition[clientPosition]
                            .orEmpty()
                            .map(Pair<Int, Int>::second),
                    level = heroInfo.intParam(1),
                    morale = moraleBySide.getValue(
                        if (clientPosition <= 3) Side.ATTACKER else Side.DEFENDER,
                    ),
                )
            }

        val teamBuilder = BattleTeamBuilder(
            config = config,
            equipmentRepository = BattleEquipmentRepository.loadDefault(),
        )
        val preciseStats = preciseStatsBeforeBattle(actions)
        val commandEntryStats = commandEntryStats(actions)
        fun withPreciseStats(team: BattleTeam, side: Side): BattleTeam = team.copy(
            heroes = team.heroes.map { hero ->
                val clientPosition = ClientBattleTextReplayProtocol.position(side, hero.position)
                val recorded = preciseStats.entry[clientPosition].orEmpty()
                val commandEntry = commandEntryStats[clientPosition].orEmpty()
                if (recorded.isEmpty() && commandEntry.isEmpty()) return@map hero
                val inherent = preciseStats.inherent[clientPosition].orEmpty()
                val stats = hero.stats
                hero.copy(
                    stats = BattleStats.fromHundredths(
                        attack = commandEntry[BattleStat.ATTACK]
                            ?: recorded[BattleStat.ATTACK]
                            ?: (stats.precise(BattleStat.ATTACK) * 100).roundToInt(),
                        defense = commandEntry[BattleStat.DEFENSE]
                            ?: recorded[BattleStat.DEFENSE]
                            ?: (stats.precise(BattleStat.DEFENSE) * 100).roundToInt(),
                        strategy = commandEntry[BattleStat.STRATEGY]
                            ?: recorded[BattleStat.STRATEGY]
                            ?: (stats.precise(BattleStat.STRATEGY) * 100).roundToInt(),
                        speed = commandEntry[BattleStat.SPEED]
                            ?: recorded[BattleStat.SPEED]
                            ?: (stats.precise(BattleStat.SPEED) * 100).roundToInt(),
                        siege = (stats.precise(BattleStat.SIEGE) * 100).roundToInt(),
                        hitRange = stats.hitRange,
                    ),
                    inherentStats = BattleStats.fromHundredths(
                        attack = inherent[BattleStat.ATTACK]
                            ?: (hero.inherentStats.precise(BattleStat.ATTACK) * 100).roundToInt(),
                        defense = inherent[BattleStat.DEFENSE]
                            ?: (hero.inherentStats.precise(BattleStat.DEFENSE) * 100).roundToInt(),
                        strategy = inherent[BattleStat.STRATEGY]
                            ?: (hero.inherentStats.precise(BattleStat.STRATEGY) * 100).roundToInt(),
                        speed = inherent[BattleStat.SPEED]
                            ?: (hero.inherentStats.precise(BattleStat.SPEED) * 100).roundToInt(),
                        siege = (hero.inherentStats.precise(BattleStat.SIEGE) * 100).roundToInt(),
                        hitRange = hero.inherentStats.hitRange,
                    ),
                )
            },
        )
        fun withPaperPreparationValues(team: BattleTeam, side: Side): BattleTeam {
            data class PaperEffectFormat(
                val stat: BattleStat,
                val deltaIndex: Int,
                val valueAfterIndex: Int,
            )
            val formats = mapOf(
                "19".toInt(36) to PaperEffectFormat(BattleStat.ATTACK, 4, 5),
                "1a".toInt(36) to PaperEffectFormat(BattleStat.DEFENSE, 4, 5),
                "1b".toInt(36) to PaperEffectFormat(BattleStat.STRATEGY, 4, 5),
                "1c".toInt(36) to PaperEffectFormat(BattleStat.SPEED, 4, 5),
                "0v".toInt(36) to PaperEffectFormat(BattleStat.ATTACK, 3, 4),
                "0w".toInt(36) to PaperEffectFormat(BattleStat.DEFENSE, 3, 4),
                "0x".toInt(36) to PaperEffectFormat(BattleStat.STRATEGY, 3, 4),
                "0y".toInt(36) to PaperEffectFormat(BattleStat.SPEED, 3, 4),
                "0z".toInt(36) to PaperEffectFormat(BattleStat.SIEGE, 3, 4),
                "10".toInt(36) to PaperEffectFormat(BattleStat.HIT_RANGE, 3, 4),
                "17".toInt(36) to PaperEffectFormat(BattleStat.HIT_RANGE, 3, 4),
            )
            data class EffectKey(val sourceId: Int, val target: Int, val stat: BattleStat)
            val paperValues = preparation(actions).mapNotNull { action ->
                val format = formats[action.id] ?: return@mapNotNull null
                if (action.params.size <= format.valueAfterIndex) return@mapNotNull null
                EffectKey(action.intParam(1), action.intParam(2), format.stat) to
                    (
                        action.params[format.deltaIndex].toDouble() to
                            action.params[format.valueAfterIndex].toDouble()
                        )
            }.toMap()
            return team.copy(
                preparationEffects = team.preparationEffects.map { effect ->
                    val target = ClientBattleTextReplayProtocol.position(side, effect.targetPosition)
                    val (delta, after) = paperValues[
                        EffectKey(effect.sourceId, target, effect.stat)
                    ] ?: return@map effect
                    effect.copy(
                        delta = delta.toInt(),
                        valueAfter = after.toInt(),
                        deltaExact = delta,
                        valueAfterExact = after,
                    )
                },
            )
        }
        val request = BattleRequest(
            attacker = withPaperPreparationValues(
                withPreciseStats(
                    teamBuilder.build((1..3).mapNotNull(specsByClientPosition::get)),
                    Side.ATTACKER,
                ),
                Side.ATTACKER,
            ),
            defender = withPaperPreparationValues(
                withPreciseStats(
                    teamBuilder.build((4..6).mapNotNull(specsByClientPosition::get)),
                    Side.DEFENDER,
                ),
                Side.DEFENDER,
            ),
            skillRuleOverrides = customSkillRuleOverrides(actions, config),
        )
        val authoritativeStrategyPositions = (
            preciseStats.entry.filterValues { BattleStat.STRATEGY in it }.keys +
                commandEntryStats.filterValues { BattleStat.STRATEGY in it }.keys
            ).toSet()
        return inferHiddenRecoveryStrategies(
            request = request,
            actions = actions,
            config = config,
            authoritativeStrategyPositions = authoritativeStrategyPositions,
        )
    }

    private fun customSkillRuleOverrides(
        actions: List<Action>,
        config: BattleConfigRepository,
    ): Map<Int, BattleSkillRuleOverride> {
        val overrides = linkedMapOf<Int, BattleSkillRuleOverride>()

        fun register(skillId: Int, override: BattleSkillRuleOverride) {
            val previous = overrides[skillId]
            require(previous == null || previous == override) {
                "paper contains conflicting custom definitions for skill=$skillId"
            }
            overrides[skillId] = override
        }

        actions
            .filter { it.id == "fq".toInt(36) }
            .forEach { action ->
                val payload = customSkillMapper.readTree(action.raw.drop(2))
                payload.fields().asSequence().forEach { (_, slots) ->
                    slots.elements().asSequence()
                        .filterNot { it.isNull }
                        .forEach { customSkill ->
                            val rootSkillId = customSkill.path("skill_id").asInt()
                            val rootSkill = requireNotNull(config.skill(rootSkillId)) {
                                "missing static custom root skill=$rootSkillId"
                            }
                            val customDetails = customSkill
                                .path("mpWorkCustomSkillDetail")
                                .fields()
                                .asSequence()
                                .associate { (detailId, node) -> detailId.toInt() to node }
                            require(customDetails.isNotEmpty()) {
                                "custom root skill=$rootSkillId has no configured details"
                            }
                            val childSkillIds = customDetails.values
                                .mapTo(linkedSetOf()) { it.path("skill_id").asInt() }
                            val containerSkillId = config.skillDetails(rootSkillId)
                                .asSequence()
                                .filter { it.effectId in setOf(122, 123) }
                                .map { it.constantParam }
                                .filter { config.skill(it) != null }
                                .distinct()
                                .single()
                            val containerDetails = config.skillDetails(containerSkillId)
                                .filter {
                                    it.effectId in setOf(122, 123) &&
                                        it.constantParam in childSkillIds
                                }
                            require(
                                containerDetails.mapTo(linkedSetOf()) { it.constantParam } ==
                                    childSkillIds,
                            ) {
                                "custom root skill=$rootSkillId cannot link children=$childSkillIds"
                            }

                            register(
                                rootSkillId,
                                BattleSkillRuleOverride(
                                    probability = customSkill.path("probability_max")
                                        .asInt(rootSkill.probabilityMax),
                                    prepareRounds = customSkill.path("prepare")
                                        .asInt(rootSkill.prepareRounds),
                                ),
                            )
                            register(
                                containerSkillId,
                                BattleSkillRuleOverride(details = containerDetails),
                            )

                            customDetails.entries
                                .groupBy { (_, node) -> node.path("skill_id").asInt() }
                                .forEach { (childSkillId, entries) ->
                                    val customByDetailId = entries.associate { it.key to it.value }
                                    val staticDetails = config.skillDetails(childSkillId)
                                    require(
                                        customByDetailId.keys.all { detailId ->
                                            staticDetails.any { it.detailId == detailId }
                                        },
                                    ) {
                                        "custom skill=$childSkillId references unknown details=" +
                                            customByDetailId.keys
                                    }
                                    register(
                                        childSkillId,
                                        BattleSkillRuleOverride(
                                            details = staticDetails.map { detail ->
                                                val custom = customByDetailId[detail.detailId]
                                                    ?: return@map detail
                                                detail.copy(
                                                    availableHit = custom.path("available_hit")
                                                        .asInt(detail.availableHit),
                                                    availableRounds = custom.path("available_round")
                                                        .asInt(detail.availableRounds),
                                                    constantParam = custom.path("constant_param")
                                                        .asInt(detail.constantParam),
                                                    intelParam = custom.path("intel_param")
                                                        .asInt(detail.intelParam),
                                                    probabilityInit =
                                                        custom.path("prob_init_param")
                                                            .asInt(detail.probabilityInit),
                                                    probabilityMax =
                                                        custom.path("prob_max_param")
                                                            .asInt(detail.probabilityMax),
                                                )
                                            },
                                        ),
                                    )
                                }
                        }
                }
            }

        return overrides.toMap()
    }

    private fun inferHiddenRecoveryStrategies(
        request: BattleRequest,
        actions: List<Action>,
        config: BattleConfigRepository,
        authoritativeStrategyPositions: Set<Int>,
    ): BattleRequest {
        val heroesByPosition = buildMap {
            request.attacker.heroes.forEach { hero ->
                put(ClientBattleTextReplayProtocol.position(Side.ATTACKER, hero.position), hero)
            }
            request.defender.heroes.forEach { hero ->
                put(ClientBattleTextReplayProtocol.position(Side.DEFENDER, hero.position), hero)
            }
        }
        val recoveriesBySource = actions
            .filter { it.id == ClientBattleTextReplayProtocol.RECOVERY && it.intParam(3) > 0 }
            .groupBy { it.intParam(0) }
        val inferredStrategies = heroesByPosition.mapNotNull { (sourcePosition, source) ->
            if (sourcePosition in authoritativeStrategyPositions) return@mapNotNull null
            val inferred = recoveriesBySource[sourcePosition]
                .orEmpty()
                .groupBy { it.intParam(1) }
                .mapNotNull { (skillId, recoveries) ->
                    inferRecoveryStrategy(
                        source = source,
                        skillId = skillId,
                        recoveries = recoveries,
                        heroesByPosition = heroesByPosition,
                        config = config,
                    )
                }
                .distinct()
                .singleOrNull()
                ?: return@mapNotNull null
            sourcePosition to inferred
        }.toMap()

        fun update(team: BattleTeam, side: Side): BattleTeam = team.copy(
            heroes = team.heroes.map { hero ->
                val position = ClientBattleTextReplayProtocol.position(side, hero.position)
                inferredStrategies[position]?.let { inferred ->
                    hero.withInferredStrategy(inferred)
                } ?: hero
            },
        )
        return request.copy(
            attacker = update(request.attacker, Side.ATTACKER),
            defender = update(request.defender, Side.DEFENDER),
        )
    }

    private fun inferRecoveryStrategy(
        source: BattleHero,
        skillId: Int,
        recoveries: List<Action>,
        heroesByPosition: Map<Int, BattleHero>,
        config: BattleConfigRepository,
    ): Int? {
        if (config.skill(skillId)?.kind != SkillKind.COMMAND) return null
        val detail = config.skillDetails(skillId).singleOrNull {
            it.effectId in setOf(401, 402) &&
                it.intelParam > 0 &&
                it.calculationTypes.isEmpty()
        } ?: return null
        val skillIndex = source.skillIds.indexOf(skillId).takeIf { it >= 0 } ?: return null
        val skillLevel = source.skillLevels.getOrElse(skillIndex) { 1 }.coerceIn(1, 10)
        val requestedAmounts = recoveries.mapNotNull { recovery ->
            val target = heroesByPosition[recovery.intParam(2)] ?: return@mapNotNull null
            val recoveryPercent = source.modifiers
                .filterIsInstance<BattleModifier.RecoveryDealtPercent>()
                .sumOf(BattleModifier.RecoveryDealtPercent::percent) +
                target.modifiers
                    .filterIsInstance<BattleModifier.RecoveryTakenPercent>()
                    .sumOf(BattleModifier.RecoveryTakenPercent::percent)
            val modifier = (100 + recoveryPercent).coerceAtLeast(0)
            if (modifier == 0) return@mapNotNull null
            ceilDiv(recovery.intParam(3).toLong() * 100, modifier.toLong()).toInt()
        }
        val fullRequestedAmount = requestedAmounts
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= 2 }
            .keys
            .maxOrNull()
            ?: return null
        val troopBase = (
            source.troops * 300.0 / (3_500 + source.troops)
            ).roundToInt()
        val levelRatio =
            detail.initEffectRatio + (skillLevel - 1) * (100 - detail.initEffectRatio) / 9.0
        return (0..1_000).firstOrNull { strategy ->
            val recoveryRate = kotlin.math.floor(
                levelRatio *
                    (detail.constantParam + detail.intelParam * strategy / 200.0) /
                    100.0,
            ).toInt().coerceAtLeast(1)
            troopBase * recoveryRate / 100 == fullRequestedAmount
        }?.takeIf { it > source.stats.strategy }
    }

    private fun BattleHero.withInferredStrategy(strategy: Int): BattleHero {
        val currentHundredths = (stats.precise(BattleStat.STRATEGY) * 100).roundToInt()
        val inferredHundredths = strategy * 100
        val deltaHundredths = inferredHundredths - currentHundredths
        return copy(
            stats = stats.withStrategyHundredths(inferredHundredths),
            inherentStats = inherentStats.withStrategyHundredths(
                (inherentStats.precise(BattleStat.STRATEGY) * 100).roundToInt() +
                    deltaHundredths,
            ),
        )
    }

    private fun BattleStats.withStrategyHundredths(strategy: Int): BattleStats =
        BattleStats.fromHundredths(
            attack = (precise(BattleStat.ATTACK) * 100).roundToInt(),
            defense = (precise(BattleStat.DEFENSE) * 100).roundToInt(),
            strategy = strategy,
            speed = (precise(BattleStat.SPEED) * 100).roundToInt(),
            siege = (precise(BattleStat.SIEGE) * 100).roundToInt(),
            hitRange = hitRange,
        )

    private fun ceilDiv(value: Long, divisor: Long): Long =
        (value + divisor - 1) / divisor

    fun targetDecisions(actions: List<Action>): BattleTargetDecisionSource {
        val queues = linkedMapOf<DecisionKey, ArrayDeque<List<Int>>>()
        var currentKey: DecisionKey? = null
        var currentTargets = mutableListOf<Int>()
        fun flush() {
            val key = currentKey ?: return
            queues.getOrPut(key, ::ArrayDeque).addLast(currentTargets.toList())
            currentKey = null
            currentTargets = mutableListOf()
        }
        actions.forEach { action ->
            if (action.id != "ja".toInt(36)) {
                flush()
                return@forEach
            }
            val tuple = jaTuples(listOf(action)).single()
            val key = DecisionKey(tuple.sourcePosition, tuple.sourceId, tuple.effectId)
            if (currentKey != null && currentKey != key) flush()
            currentKey = key
            currentTargets += tuple.targetPosition
        }
        flush()

        var commandSourcePosition: Int? = null
        var commandSkillId: Int? = null
        var statusKey: DecisionKey? = null
        var statusTargets = mutableListOf<Int>()
        fun flushStatuses() {
            val key = statusKey ?: return
            queues.getOrPut(key, ::ArrayDeque).addLast(statusTargets.toList())
            statusKey = null
            statusTargets = mutableListOf()
        }
        actions.forEach { action ->
            when (action.id) {
                "0m".toInt(36) -> {
                    flushStatuses()
                    commandSourcePosition = action.intParam(0)
                    commandSkillId = action.intParam(1)
                }
                "0s".toInt(36) -> {
                    val sourcePosition = commandSourcePosition ?: return@forEach
                    val skillId = commandSkillId ?: return@forEach
                    val key = DecisionKey(sourcePosition, skillId, action.intParam(1))
                    if (statusKey != null && statusKey != key) flushStatuses()
                    statusKey = key
                    statusTargets += action.intParam(0)
                }
                "7a".toInt(36) -> {
                    val key = DecisionKey(action.intParam(0), action.intParam(1), 332)
                    if (statusKey != null && statusKey != key) flushStatuses()
                    statusKey = key
                    statusTargets += action.intParam(2)
                }
                "hx".toInt(36) -> {
                    flushStatuses()
                    commandSourcePosition = null
                    commandSkillId = null
                }
                else -> if (statusKey != null) flushStatuses()
            }
        }
        flushStatuses()

        actions.forEach { action ->
            val effectId = when (action.id) {
                ClientBattleTextReplayProtocol.ATTACK_SKILL_DAMAGE -> 301
                ClientBattleTextReplayProtocol.SKILL_DAMAGE -> 302
                ClientBattleTextReplayProtocol.PANIC_ONGOING_DAMAGE -> 304
                ClientBattleTextReplayProtocol.ONGOING_DAMAGE -> 305
                ClientBattleTextReplayProtocol.HEX_ONGOING_DAMAGE -> 306
                else -> return@forEach
            }
            val key = DecisionKey(
                sourcePosition = action.intParam(0),
                skillId = action.intParam(1),
                effectId = effectId,
            )
            queues.getOrPut(key, ::ArrayDeque).addLast(
                listOf(action.intParam(2)),
            )
        }

        return BattleTargetDecisionSource { request ->
            val sourcePosition = ClientBattleTextReplayProtocol.position(
                request.context.source.side,
                request.context.source.position,
            )
            val keys = listOf(
                request.context.rootSkillId,
                request.context.currentSkillId,
            ).distinct().map { skillId ->
                DecisionKey(sourcePosition, skillId, request.rule.effectId)
            }
            val (key, queue) = keys.firstNotNullOfOrNull { candidate ->
                queues[candidate]?.let { candidate to it }
            } ?: return@BattleTargetDecisionSource null
            if (queue.isEmpty()) return@BattleTargetDecisionSource null
            val targetPositions = buildList {
                addAll(queue.removeFirst())
                while (size < request.limit) {
                    val next = queue.firstOrNull()?.singleOrNull() ?: break
                    if (next in this) break
                    addAll(queue.removeFirst())
                }
            }
            targetPositions.map { position ->
                requireNotNull(
                    request.candidates.find { candidate ->
                        ClientBattleTextReplayProtocol.position(candidate.side, candidate.position) == position
                    },
                ) {
                    "Paper target position $position is absent from candidates ${request.candidates} for $key"
                }
            }
        }
    }

    fun commonWidthMismatches(
        official: List<Action>,
        generated: List<Action>,
    ): Map<Int, Pair<Set<Int>, Set<Int>>> {
        val officialWidths = official.groupBy(Action::id)
            .mapValues { (_, values) -> values.map { it.params.size }.toSet() }
        val generatedWidths = generated.groupBy(Action::id)
            .mapValues { (_, values) -> values.map { it.params.size }.toSet() }
        return generatedWidths.keys.intersect(officialWidths.keys)
            .mapNotNull { id ->
                val unexpected = generatedWidths.getValue(id) - officialWidths.getValue(id)
                if (unexpected.isEmpty()) {
                    null
                } else {
                    id to (officialWidths.getValue(id) to generatedWidths.getValue(id))
                }
            }
            .toMap()
    }

    private fun Action.intParam(index: Int): Int =
        params[index].toInt()

    private fun MutableMap<Side, Int>.add(side: Side, amount: Int) {
        this[side] = getValue(side) + amount
    }

    private fun sideForPosition(position: Int): Side =
        when (position) {
            in 1..3 -> Side.ATTACKER
            in 4..6 -> Side.DEFENDER
            else -> error("invalid client battle position=$position")
        }

    private fun preciseStatsBeforeBattle(actions: List<Action>): PrecisePaperStats {
        val formats = mapOf(
            "19".toInt(36) to (BattleStat.ATTACK to 5),
            "1a".toInt(36) to (BattleStat.DEFENSE to 5),
            "1b".toInt(36) to (BattleStat.STRATEGY to 5),
            "1c".toInt(36) to (BattleStat.SPEED to 5),
            "0v".toInt(36) to (BattleStat.ATTACK to 4),
            "0w".toInt(36) to (BattleStat.DEFENSE to 4),
            "0x".toInt(36) to (BattleStat.STRATEGY to 4),
            "0y".toInt(36) to (BattleStat.SPEED to 4),
            "0z".toInt(36) to (BattleStat.SIEGE to 4),
        )
        val result = linkedMapOf<Int, MutableMap<BattleStat, Int>>()
        val inherent = linkedMapOf<Int, MutableMap<BattleStat, Int>>()
        actions.takeWhile { it.id != "hr".toInt(36) }.forEach { action ->
            val (stat, valueIndex) = formats[action.id] ?: return@forEach
            require(action.params.size > valueIndex) { "invalid precise stat action: ${action.raw}" }
            val targetPosition = action.intParam(2)
            val hundredths = action.params[valueIndex].toBigDecimal().movePointRight(2).intValueExact()
            result.getOrPut(targetPosition, ::linkedMapOf)[stat] = hundredths
            if (valueIndex == 5 && action.params[3].toBigDecimal().signum() != 0) {
                val inferredHundredths = action.params[4].toBigDecimal()
                    .movePointRight(4)
                    .divide(action.params[3].toBigDecimal(), 0, java.math.RoundingMode.HALF_UP)
                    .intValueExact()
                inherent.getOrPut(targetPosition, ::linkedMapOf).putIfAbsent(stat, inferredHundredths)
            }
        }
        return PrecisePaperStats(result, inherent)
    }

    private fun commandEntryStats(actions: List<Action>): Map<Int, Map<BattleStat, Int>> {
        val formats = mapOf(
            "0v".toInt(36) to BattleStat.ATTACK,
            "0w".toInt(36) to BattleStat.DEFENSE,
            "0x".toInt(36) to BattleStat.STRATEGY,
            "0y".toInt(36) to BattleStat.SPEED,
        )
        val result = linkedMapOf<Int, MutableMap<BattleStat, Int>>()
        actions.dropWhile { it.id != "hp".toInt(36) }
            .takeWhile { it.id != ClientBattleTextReplayProtocol.ROUND }
            .forEach { action ->
                val stat = formats[action.id] ?: return@forEach
                if (action.params.size != 5) return@forEach
                val targetPosition = action.intParam(2)
                val delta = action.params[3].toBigDecimal()
                val valueAfter = action.params[4].toBigDecimal()
                val hundredths = valueAfter.subtract(delta)
                    .movePointRight(2)
                    .intValueExact()
                result.getOrPut(targetPosition, ::linkedMapOf).putIfAbsent(stat, hundredths)
            }
        return result
    }

    private fun formationPosition(clientPosition: Int): Int =
        if (clientPosition <= 3) clientPosition - 1 else 6 - clientPosition
}

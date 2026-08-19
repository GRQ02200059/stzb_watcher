package com.stzb.server.game.battle.skill

import com.stzb.server.game.battle.ConfiguredBattleEffectValue
import com.stzb.server.game.battle.SkillDetailConfig
import com.stzb.server.game.battle.SkillKind
import java.util.Collections

data class SkillRule(
    val skillId: Int,
    val kind: SkillKind,
    val rawSkillType: Int,
    val probability: Int,
    val prepareRounds: Int,
    val hitRange: Int?,
    val details: List<SkillEffectRule>,
)

data class SkillEffectRule(
    val detailId: Int,
    val effectId: Int,
    val childSkillIds: Set<Int>,
    val raw: SkillDetailConfig,
    val skillHitRange: Int? = null,
    val configuredValue: ConfiguredBattleEffectValue? = null,
    val effectBuffType: Int = raw.buffType,
    val effectReplaceType: Int = 0,
    val skillKind: SkillKind = SkillKind.UNKNOWN,
    val rawSkillType: Int = 0,
)

enum class BattleCoefficientSource {
    NONE,
    ATTACK,
    DEFENSE,
    STRATEGY,
    SPEED,
}

val SkillEffectRule.coefficientSource: BattleCoefficientSource
    get() =
        when (raw.attributeType) {
            1 -> BattleCoefficientSource.ATTACK
            2 -> BattleCoefficientSource.DEFENSE
            3 -> BattleCoefficientSource.STRATEGY
            4 -> BattleCoefficientSource.SPEED
            99 -> BattleCoefficientSource.STRATEGY
            0 -> when (raw.calcParam) {
                1 -> BattleCoefficientSource.ATTACK
                2 -> BattleCoefficientSource.STRATEGY
                else -> BattleCoefficientSource.STRATEGY
            }
            else -> BattleCoefficientSource.NONE
        }

data class SkillDiagnostic(
    val skillId: Int,
    val detailId: Int?,
    val effectId: Int?,
    val code: String,
    val dependencyPath: String,
)

class SkillRuleGraph(
    rules: Map<Int, SkillRule>,
    effectIds: Set<Int>,
    rootSkillIds: Set<Int> = rules.keys,
    skillEnhancementUnlockIds: Set<Int> = rules.values
        .flatMap { it.details }
        .filter { it.effectId == 132 }
        .mapTo(linkedSetOf()) { it.raw.effectParam },
) {
    private val rules: Map<Int, SkillRule> = immutableMap(
        rules.mapValues { (_, rule) ->
            rule.copy(
                details = immutableList(
                    rule.details.map { detail ->
                        detail.copy(
                            childSkillIds = immutableSet(detail.childSkillIds),
                            raw = detail.raw.copy(
                                calculationTypes = immutableList(detail.raw.calculationTypes),
                            ),
                        )
                    },
                ),
            )
        },
    )
    val rootSkillIds: Set<Int> = immutableSet(rootSkillIds)
    val skillEnhancementUnlockIds: Set<Int> = immutableSet(
        skillEnhancementUnlockIds.filter { it > 0 },
    )

    val executionNodeIds: Set<Int> = immutableSet(this.rules.keys)
    val effectIds: Set<Int> = immutableSet(effectIds)
    val details: List<SkillEffectRule> = immutableList(this.rules.values.flatMap { it.details })

    fun rule(skillId: Int): SkillRule? = rules[skillId]

    fun validate(): List<SkillDiagnostic> =
        immutableList(
            missingRootDiagnostics() +
                missingDetailsDiagnostics() +
                missingDependencyDiagnostics() +
                missingEffectDiagnostics() +
                cycleDiagnostics(),
        )

    private fun missingRootDiagnostics(): List<SkillDiagnostic> =
        rootSkillIds
            .filterNot(rules::containsKey)
            .map { skillId ->
                SkillDiagnostic(
                    skillId = skillId,
                    detailId = null,
                    effectId = null,
                    code = "MISSING_SKILL",
                    dependencyPath = skillId.toString(),
                )
            }

    private fun missingDetailsDiagnostics(): List<SkillDiagnostic> =
        rules.values
            .filter { it.details.isEmpty() }
            .map { rule ->
                SkillDiagnostic(
                    skillId = rule.skillId,
                    detailId = null,
                    effectId = null,
                    code = "MISSING_DETAILS",
                    dependencyPath = dependencyPathTo(rule.skillId),
                )
            }

    private fun missingDependencyDiagnostics(): List<SkillDiagnostic> =
        rules.values.flatMap { rule ->
            rule.details.flatMap { detail ->
                detail.childSkillIds
                    .filterNot(rules::containsKey)
                    .map { childSkillId ->
                        SkillDiagnostic(
                            skillId = rule.skillId,
                            detailId = detail.detailId,
                            effectId = detail.effectId,
                            code = "MISSING_SKILL",
                            dependencyPath = "${dependencyPathTo(rule.skillId)} -> $childSkillId",
                        )
                    }
            }
        }

    private fun missingEffectDiagnostics(): List<SkillDiagnostic> =
        rules.values.flatMap { rule ->
            rule.details
                .filter { it.effectId != 0 && it.effectId !in effectIds }
                .map { detail ->
                    SkillDiagnostic(
                        skillId = rule.skillId,
                        detailId = detail.detailId,
                        effectId = detail.effectId,
                        code = "MISSING_EFFECT",
                        dependencyPath = dependencyPathTo(rule.skillId),
                    )
                }
        }

    private fun dependencyPathTo(targetSkillId: Int): String {
        val queuedPaths = ArrayDeque<List<Int>>()
        val visited = mutableSetOf<Int>()
        rootSkillIds.filter(rules::containsKey).forEach { queuedPaths += listOf(it) }
        while (queuedPaths.isNotEmpty()) {
            val path = queuedPaths.removeFirst()
            val skillId = path.last()
            if (skillId == targetSkillId) return path.joinToString(" -> ")
            if (!visited.add(skillId)) continue
            rules.getValue(skillId).details
                .flatMap { it.childSkillIds }
                .filter(rules::containsKey)
                .forEach { childSkillId -> queuedPaths += path + childSkillId }
        }
        return targetSkillId.toString()
    }

    private fun cycleDiagnostics(): List<SkillDiagnostic> {
        val visited = mutableSetOf<Int>()
        val activePath = mutableListOf<Int>()
        val activeIndices = mutableMapOf<Int, Int>()
        val diagnostics = mutableListOf<SkillDiagnostic>()
        val reportedPaths = mutableSetOf<String>()

        fun visit(skillId: Int) {
            activeIndices[skillId] = activePath.size
            activePath += skillId

            rules.getValue(skillId).details.forEach { detail ->
                detail.childSkillIds.filter(rules::containsKey).forEach { childSkillId ->
                    val cycleStart = activeIndices[childSkillId]
                    when {
                        cycleStart != null -> {
                            val path = (activePath.drop(cycleStart) + childSkillId).joinToString(" -> ")
                            if (reportedPaths.add(path)) {
                                diagnostics += SkillDiagnostic(
                                    skillId = skillId,
                                    detailId = detail.detailId,
                                    effectId = detail.effectId,
                                    code = "DEPENDENCY_CYCLE",
                                    dependencyPath = path,
                                )
                            }
                        }
                        childSkillId !in visited -> visit(childSkillId)
                    }
                }
            }

            activePath.removeAt(activePath.lastIndex)
            activeIndices.remove(skillId)
            visited += skillId
        }

        rules.keys.forEach { skillId ->
            if (skillId !in visited) visit(skillId)
        }
        return diagnostics
    }

    private companion object {
        fun <K, V> immutableMap(values: Map<K, V>): Map<K, V> =
            Collections.unmodifiableMap(LinkedHashMap(values))

        fun <T> immutableList(values: Collection<T>): List<T> =
            Collections.unmodifiableList(ArrayList(values))

        fun <T> immutableSet(values: Collection<T>): Set<T> =
            Collections.unmodifiableSet(LinkedHashSet(values))
    }
}

package com.stzb.battle.core.skill

import com.stzb.battle.core.BattleConfigRepository
import com.stzb.battle.core.BattleSkillRuleOverride
import com.stzb.battle.core.SkillDetailConfig

object SkillRuleCatalog {
    fun build(
        scope: SkillScope,
        config: BattleConfigRepository,
        overrides: Map<Int, BattleSkillRuleOverride> = emptyMap(),
    ): SkillRuleGraph {
        val rules = linkedMapOf<Int, SkillRule>()

        fun visit(skillId: Int) {
            if (skillId in rules) return
            val skill = config.skill(skillId) ?: return
            val override = overrides[skillId]
            val details = (override?.details ?: config.skillDetails(skillId)).map { detail ->
                val effect = config.skillEffect(detail.effectId)
                SkillEffectRule(
                    detailId = detail.detailId,
                    effectId = detail.effectId,
                    childSkillIds = childSkillIds(detail, config),
                    raw = detail,
                    skillHitRange = skill.hitRange,
                    configuredValue = effect?.let { config.configuredValue(detail) },
                    effectBuffType = effect?.buffType ?: detail.buffType,
                    effectReplaceType = effect?.replaceType ?: 0,
                    skillKind = skill.kind,
                    rawSkillType = skill.rawSkillType,
                )
            }
            rules[skillId] = SkillRule(
                skillId = skill.id,
                kind = skill.kind,
                rawSkillType = skill.rawSkillType,
                probability = override?.probability ?: skill.probabilityMax,
                prepareRounds = override?.prepareRounds ?: skill.prepareRounds,
                hitRange = skill.hitRange,
                details = details,
            )
            details.flatMapTo(linkedSetOf()) { it.childSkillIds }.forEach(::visit)
        }

        scope.mainSkillIds.sorted().forEach(::visit)
        val effectIds = rules.values
            .flatMap { it.details }
            .mapNotNull { detail ->
                when {
                    detail.effectId == 0 -> 0
                    else -> config.skillEffect(detail.effectId)?.effectId
                }
            }
            .toSet()
        return SkillRuleGraph(
            rules = rules,
            effectIds = effectIds,
            rootSkillIds = scope.mainSkillIds,
            skillEnhancementUnlockIds = config.skillEnhancementUnlockIds(),
        )
    }

    private fun childSkillIds(
        detail: SkillDetailConfig,
        config: BattleConfigRepository,
    ): Set<Int> {
        val candidates = when (detail.effectId) {
            122, 123 -> listOf(detail.constantParam)
            else -> IMPLICIT_CHILD_SKILL_IDS[detail.detailId].orEmpty()
        }
        return candidates
            .filter { config.skill(it) != null }
            .toSet()
    }

    private val IMPLICIT_CHILD_SKILL_IDS = mapOf(
        20003402 to listOf(210034),
        20024901 to listOf(214249),
        20024902 to listOf(214249),
        20024911 to listOf(215249),
        20024912 to listOf(215249),
    )
}

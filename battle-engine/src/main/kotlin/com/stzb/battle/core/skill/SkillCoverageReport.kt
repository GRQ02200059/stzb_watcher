package com.stzb.battle.core.skill

import java.util.Collections

data class SkillCoverageReport(
    val mainSkills: Int,
    val executionNodes: Int,
    val effectIds: Int,
    val detailRules: Int,
    val unsupportedEffects: Set<Int>,
    val unconsumedMetaEffects: Set<Int>,
    val unknownConditions: Set<SkillConditionCode>,
    val unknownSelectors: Set<Int>,
    val brokenDependencies: Set<SkillDiagnostic>,
    val noBehaviorSkills: Set<Int>,
    val missingPluginSkillIds: Set<Int>,
    val duplicateExecutionSkillIds: Set<Int>,
    val pendingConditionCodes: Set<SkillConditionCode>,
    val unresolvedConditionOwnerSkillIds: Set<Int>,
    val executionPluginSkillIds: Set<Int>,
) {
    companion object {
        fun generate(
            graph: SkillRuleGraph,
            conditionInterpreter: SkillConditionInterpreter,
            executionPlugins: SpecialSkillPluginRegistry,
            ownershipCatalog: SkillExecutionOwnershipCatalog =
                ConfiguredSpecialSkillPlugins.ownershipCatalog,
        ): SkillCoverageReport {
            val pending = graph.details
                .flatMap { conditionInterpreter.compile(it).conditions }
                .filterIsInstance<SpecialConditionRequirement>()
                .mapTo(linkedSetOf()) { it.code }
            val executionIds = executionPlugins.ownedSkillIds()
            val replacingExecutionIds = executionPlugins.all()
                .filter(SkillExecutionPlugin::replacesConfiguredExecution)
                .flatMapTo(linkedSetOf(), SkillExecutionPlugin::skillIds)
            val requiredIds = ownershipCatalog.requiredNonDeclarativeSkillIds
            val effectRegistry = BattleEffectRegistry.strict(graph)
                .registerCoreEffects(BattleEffectStore())
                .registerControlEffects(BattleEffectStore())
                .registerMetaEffects()
            val unsupportedEffects = graph.effectIds - effectRegistry.implementedEffectIds()
            val unconsumedMetaEffects = graph.effectIds
                .intersect(MetaEffectHandlers.effectIds)
                .minus(RUNTIME_CONSUMED_META_EFFECT_IDS)
                .minus(0)
            val selector = SkillTargetSelector()
            val unknownSelectors = graph.details.mapNotNullTo(linkedSetOf()) { detail ->
                runCatching { selector.compile(detail) }
                    .exceptionOrNull()
                    ?.let { detail.detailId }
            }
            val brokenDependencies = graph.validate().toSet()
            val noBehaviorSkills = graph.rootSkillIds.filterTo(linkedSetOf()) { rootSkillId ->
                !hasImplementedBehavior(
                    skillId = rootSkillId,
                    graph = graph,
                    implementedEffectIds = effectRegistry.implementedEffectIds(),
                    visited = linkedSetOf(),
                )
            }
            return SkillCoverageReport(
                mainSkills = graph.rootSkillIds.size,
                executionNodes = graph.executionNodeIds.size,
                effectIds = graph.effectIds.size,
                detailRules = graph.details.size,
                unsupportedEffects = immutableSet(unsupportedEffects),
                unconsumedMetaEffects = immutableSet(unconsumedMetaEffects),
                unknownConditions = immutableSet(pending),
                unknownSelectors = immutableSet(unknownSelectors),
                brokenDependencies = immutableSet(brokenDependencies),
                noBehaviorSkills = immutableSet(noBehaviorSkills),
                missingPluginSkillIds = immutableSet(
                    requiredIds - replacingExecutionIds,
                ),
                duplicateExecutionSkillIds = immutableSet(
                    executionIds.filter { skillId ->
                        graph.rule(skillId) != null &&
                            executionPlugins.pluginFor(skillId)?.replacesConfiguredExecution != true
                    },
                ),
                pendingConditionCodes = immutableSet(pending),
                unresolvedConditionOwnerSkillIds = immutableSet(pending.map { it.skillId }),
                executionPluginSkillIds = immutableSet(executionIds),
            )
        }

        fun generateDefault(): SkillCoverageReport {
            val config = com.stzb.battle.core.BattleConfigRepository.loadDefault()
            val graph = SkillRuleCatalog.build(SkillScopeCatalog.loadDefault(), config)
            return generate(
                graph = graph,
                conditionInterpreter = SkillConditionInterpreter(graph),
                executionPlugins = ConfiguredSpecialSkillPlugins.registry(config),
            )
        }

        private fun hasImplementedBehavior(
            skillId: Int,
            graph: SkillRuleGraph,
            implementedEffectIds: Set<Int>,
            visited: MutableSet<Int>,
        ): Boolean {
            if (!visited.add(skillId)) return false
            val rule = graph.rule(skillId) ?: return false
            return rule.details.any { detail ->
                detail.effectId in implementedEffectIds ||
                    detail.childSkillIds.any { childSkillId ->
                        hasImplementedBehavior(childSkillId, graph, implementedEffectIds, visited)
                    }
            }
        }

        private fun <T> immutableSet(values: Collection<T>): Set<T> =
            Collections.unmodifiableSet(LinkedHashSet(values))

        /**
         * Meta handlers are declarative adapters. An effect only belongs here
         * after its emitted state change is consumed by the interpreter,
         * engine, or state applier. Merely registering a MetaEffectChange does
         * not constitute executable coverage.
         */
        private val RUNTIME_CONSUMED_META_EFFECT_IDS = setOf(
            77, 79, 80, 81, 82, 83, 88,
            111, 112,
            113, 114, 118, 121,
            122, 123, 125,
            127, 128, 129, 130, 131, 132, 141, 149,
            151, 152, 153, 161,
            171, 181, 199, 200,
            210, 231, 261, 271, 281, 311, 312, 313,
            404, 407, 408, 409,
        )
    }
}

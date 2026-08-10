package com.stzb.battle.core.skill

import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.SkillKind
import java.util.Collections

enum class SpecialSkillPhase {
    BATTLE_PREPARE,
    AFTER_SUCCESSFUL_SKILL,
}

data class SpecialSkillInvocation(
    val phase: SpecialSkillPhase,
    val owner: BattleHeroRef,
    val actor: BattleHeroRef,
    val successfulSkillId: Int? = null,
    val successfulSkillKind: SkillKind? = null,
    val context: SkillBattleContext,
)

interface SkillExecutionPlugin {
    val id: String
    val skillIds: Set<Int>
    val replacesConfiguredExecution: Boolean

    fun execute(invocation: SpecialSkillInvocation): SkillExecutionResult
}

class SpecialSkillPluginRegistry(
    plugins: List<SkillExecutionPlugin>,
) {
    private val plugins: List<SkillExecutionPlugin>
    private val bySkillId: Map<Int, SkillExecutionPlugin>

    init {
        val indexed = linkedMapOf<Int, SkillExecutionPlugin>()
        plugins.forEach { plugin ->
            require(plugin.id.isNotBlank()) { "Special skill plugin ID must not be blank" }
            require(plugin.skillIds.isNotEmpty()) { "Special skill plugin ${plugin.id} owns no skills" }
            plugin.skillIds.forEach { skillId ->
                val previous = indexed.putIfAbsent(skillId, plugin)
                require(previous == null) {
                    "duplicate special skill execution plugin: skill=$skillId " +
                        "owners=${previous?.id},${plugin.id}"
                }
            }
        }
        this.plugins = Collections.unmodifiableList(ArrayList(plugins))
        bySkillId = Collections.unmodifiableMap(indexed)
    }

    fun pluginFor(skillId: Int): SkillExecutionPlugin? = bySkillId[skillId]

    fun all(): List<SkillExecutionPlugin> = plugins

    fun ownedSkillIds(): Set<Int> =
        Collections.unmodifiableSet(LinkedHashSet(bySkillId.keys))
}

data class SkillExecutionOwnershipCatalog(
    val requiredNonDeclarativeSkillIds: Set<Int>,
) {
    init {
        require(requiredNonDeclarativeSkillIds.all { it > 0 }) {
            "non-declarative execution skill IDs must be positive"
        }
    }
}

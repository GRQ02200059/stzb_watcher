package com.stzb.server.game.battle

enum class BattleEffectPhase {
    ACTION_START,
    ACTION_END,
    ROUND_END,
}

enum class BattleEffectDirection {
    DEALT,
    TAKEN,
}

sealed interface BattleEffectPayload {
    data class Status(val status: BattleStatus) : BattleEffectPayload
    data class Stat(val delta: BattleStats) : BattleEffectPayload
    data class Damage(
        val school: DamageSchool? = null,
        val origin: DamageOrigin? = null,
        val tag: DamageTag? = null,
        val percent: Int,
        val direction: BattleEffectDirection,
    ) : BattleEffectPayload
}

data class BattleEffect(
    val source: BattleHeroRef,
    val target: BattleHeroRef,
    val skillId: Int,
    val category: String,
    val durationRounds: Int,
    val strength: Int,
    val payload: BattleEffectPayload,
    val expiresAt: BattleEffectPhase = BattleEffectPhase.ROUND_END,
) {
    companion object {
        fun status(
            source: BattleHeroRef,
            target: BattleHeroRef,
            skillId: Int,
            status: BattleStatus,
            durationRounds: Int,
            category: String,
            value: Int = 0,
        ) = BattleEffect(
            source, target, skillId, category, durationRounds, value,
            BattleEffectPayload.Status(status),
        )

        fun stat(
            source: BattleHeroRef,
            target: BattleHeroRef,
            skillId: Int,
            delta: BattleStats,
            durationRounds: Int,
            category: String,
        ) = BattleEffect(
            source, target, skillId, category, durationRounds,
            listOf(delta.attack, delta.defense, delta.strategy, delta.speed, delta.siege).maxOf { kotlin.math.abs(it) },
            BattleEffectPayload.Stat(delta),
        )

        fun damage(
            source: BattleHeroRef,
            target: BattleHeroRef,
            skillId: Int,
            school: DamageSchool? = null,
            origin: DamageOrigin? = null,
            tag: DamageTag? = null,
            percent: Int,
            durationRounds: Int,
            category: String,
            direction: BattleEffectDirection = BattleEffectDirection.DEALT,
        ) = BattleEffect(
            source, target, skillId, category, durationRounds,
            kotlin.math.abs(percent),
            BattleEffectPayload.Damage(school, origin, tag, percent, direction),
        )
    }
}

class BattleEffectState {
    private data class Active(val effect: BattleEffect, var remaining: Int)

    private val active = mutableMapOf<BattleHeroRef, MutableList<Active>>()

    fun apply(effect: BattleEffect): Boolean {
        if (effect.durationRounds == 0) return false
        val effects = active.getOrPut(effect.target) { mutableListOf() }
        val conflict = effects.firstOrNull {
            it.effect.category == effect.category &&
                it.effect.payload::class == effect.payload::class
        }
        if (conflict != null) {
            if (conflict.effect.strength > effect.strength) return false
            effects.remove(conflict)
        }
        effects += Active(effect, effect.durationRounds)
        return true
    }

    fun hasStatus(target: BattleHeroRef, status: BattleStatus): Boolean =
        active[target].orEmpty().any { (it.effect.payload as? BattleEffectPayload.Status)?.status == status }

    fun effectiveStats(target: BattleHeroRef, hero: BattleHero): BattleStats =
        active[target].orEmpty()
            .mapNotNull { (it.effect.payload as? BattleEffectPayload.Stat)?.delta }
            .fold(hero.stats, BattleStats::plus)

    fun damageFactor(
        target: BattleHeroRef,
        school: DamageSchool,
        origin: DamageOrigin? = null,
        tags: Set<DamageTag> = emptySet(),
        direction: BattleEffectDirection = BattleEffectDirection.DEALT,
    ): Double {
        val percent = active[target].orEmpty()
            .mapNotNull { it.effect.payload as? BattleEffectPayload.Damage }
            .filter {
                it.direction == direction &&
                    (it.school == null || it.school == school) &&
                    (it.origin == null || it.origin == origin) &&
                    (it.tag == null || it.tag in tags)
            }
            .sumOf { it.percent }
        return (100 + percent).coerceAtLeast(0) / 100.0
    }

    fun effects(target: BattleHeroRef): List<BattleEffect> =
        active[target].orEmpty().map { it.effect }

    fun tick(phase: BattleEffectPhase) {
        val emptyTargets = mutableListOf<BattleHeroRef>()
        active.forEach { (target, effects) ->
            effects.forEach { activeEffect ->
                if (activeEffect.effect.expiresAt == phase && activeEffect.remaining > 0) {
                    activeEffect.remaining--
                }
            }
            effects.removeAll { it.remaining == 0 }
            if (effects.isEmpty()) emptyTargets += target
        }
        emptyTargets.forEach(active::remove)
    }
}

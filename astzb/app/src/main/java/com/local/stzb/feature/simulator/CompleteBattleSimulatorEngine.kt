package com.local.stzb.feature.simulator

import com.example.myapplication.LocalBattleSimulator
import com.example.myapplication.LocalSimHeroConfig
import com.example.myapplication.LocalSimHeroOption
import com.example.myapplication.LocalSimSkillOption
import com.example.myapplication.LocalSimulationConfig
import com.example.myapplication.LocalSimulationEvent
import com.example.myapplication.LocalSimulationEventKind
import com.example.myapplication.LocalSimulationHeroSnapshot
import com.example.myapplication.LocalSimulationRun
import com.example.myapplication.LocalSimulationSummary
import com.stzb.server.game.battle.BattleConfigRepository
import com.stzb.server.game.battle.BattleEngine
import com.stzb.server.game.battle.BattleEvent
import com.stzb.server.game.battle.BattleHero
import com.stzb.server.game.battle.BattleHeroRef
import com.stzb.server.game.battle.BattleHeroSpec
import com.stzb.server.game.battle.BattleOutcome
import com.stzb.server.game.battle.BattleRandom
import com.stzb.server.game.battle.BattleRequest
import com.stzb.server.game.battle.BattleResult
import com.stzb.server.game.battle.BattleTeamBuilder
import com.stzb.server.game.battle.SeededBattleRandom
import com.stzb.server.game.battle.Side
import kotlin.math.max

/**
 * Android adapter for the self-contained complete Kotlin battle engine.
 *
 * UI configuration and report models remain local to the Android client; this
 * adapter only translates between them and the authoritative battle core.
 */
object CompleteBattleSimulatorEngine : BattleSimulatorEngine {
    private val config: BattleConfigRepository by lazy { BattleConfigRepository.loadDefault() }

    override fun defaultConfig(): LocalSimulationConfig = LocalBattleSimulator.defaultWebConfig()

    override fun heroes(): List<LocalSimHeroOption> = LocalBattleSimulator.selectableHeroes()

    override fun skills(): List<LocalSimSkillOption> = LocalBattleSimulator.selectableSkills()

    override fun heroName(id: Long): String =
        config.hero(id.toInt())?.name ?: LocalBattleSimulator.heroName(id)

    override fun heroIconId(id: Long): Long = LocalBattleSimulator.heroIconId(id)

    override fun skillName(id: Long): String =
        config.skill(id.toInt())?.name ?: LocalBattleSimulator.skillName(id)

    override fun simulate(config: LocalSimulationConfig): LocalSimulationSummary {
        val repeat = config.repeat.coerceIn(1, 1_000)
        val runs = (0 until repeat).map { offset ->
            resolve(config.copy(seed = config.seed + offset))
        }
        val blueWins = runs.count { it.winner == "攻方" }
        val redWins = runs.count { it.winner == "守方" }
        val draws = runs.size - blueWins - redWins
        return LocalSimulationSummary(
            repeat = repeat,
            blueWins = blueWins,
            redWins = redWins,
            draws = draws,
            blueWinRate = blueWins * 100.0 / repeat,
            redWinRate = redWins * 100.0 / repeat,
            drawRate = draws * 100.0 / repeat,
            firstRun = runs.first(),
        )
    }

    private fun resolve(local: LocalSimulationConfig): LocalSimulationRun {
        val request = BattleRequest(
            attacker = BattleTeamBuilder(config).build(local.blue.toBattleSpecs()),
            defender = BattleTeamBuilder(config).build(local.red.toBattleSpecs()),
            maxRounds = MAX_ROUNDS,
        )
        val result = BattleEngine.resolve(request, config, SeededBattleRandom(local.seed))
        return result.toLocalRun(local.seed)
    }

    private fun List<LocalSimHeroConfig>.toBattleSpecs(): List<BattleHeroSpec> =
        take(3).mapIndexed { position, hero ->
            val level = hero.level.coerceIn(1, MAX_LEVEL)
            val advance = hero.advance.coerceIn(0, MAX_ADVANCE)
            BattleHeroSpec(
                heroId = hero.heroId.toInt(),
                position = position,
                troops = baseTroops(level, advance),
                level = level,
                advanceLevel = advance,
                morale = 100,
                extraSkillIds = hero.equipSkillIds.filter { it > 0L }.take(3).map(Long::toInt),
            )
        }

    private fun com.example.myapplication.LocalSimTeamConfig.toBattleSpecs(): List<BattleHeroSpec> =
        heroes.toBattleSpecs().map { it.copy(morale = morale.coerceIn(0, 100)) }

    private fun BattleResult.toLocalRun(seed: Int): LocalSimulationRun {
        val allNames = (entryAttacker?.heroes.orEmpty() + entryDefender?.heroes.orEmpty() + attacker.heroes + defender.heroes)
            .associate { it.id.value to (config.hero(it.id.value)?.name ?: it.id.value.toString()) }
        val entryAttackerByPosition = entryAttacker?.heroes.orEmpty().associateBy(BattleHero::position)
        val entryDefenderByPosition = entryDefender?.heroes.orEmpty().associateBy(BattleHero::position)
        val localEvents = events.mapNotNull { event -> event.toLocalEvent(allNames) }
        return LocalSimulationRun(
            winner = outcome.toWinner(),
            blueRemain = attacker.heroes.sumOf(BattleHero::troops),
            redRemain = defender.heroes.sumOf(BattleHero::troops),
            records = localEvents.map(LocalSimulationEvent::description).filter(String::isNotBlank).take(MAX_RECORDS),
            attackerHeroes = attacker.heroes.sortedBy(BattleHero::position).map { hero ->
                hero.toSnapshot(entryAttackerByPosition[hero.position])
            },
            defenderHeroes = defender.heroes.sortedBy(BattleHero::position).map { hero ->
                hero.toSnapshot(entryDefenderByPosition[hero.position])
            },
            events = localEvents,
            roundsPlayed = events.count { it is BattleEvent.RoundStart },
            seed = seed,
        )
    }

    private fun BattleHero.toSnapshot(entry: BattleHero?): LocalSimulationHeroSnapshot =
        LocalSimulationHeroSnapshot(
            heroId = id.value.toLong(),
            name = config.hero(id.value)?.name ?: id.value.toString(),
            positionName = positionName(position),
            initialTroops = entry?.troops ?: maxTroops,
            remainingTroops = troops,
            level = level,
            advance = advanceLevel,
        )

    private fun BattleEvent.toLocalEvent(names: Map<Int, String>): LocalSimulationEvent? = when (this) {
        BattleEvent.BattleStart -> null
        is BattleEvent.RoundStart -> LocalSimulationEvent(round, LocalSimulationEventKind.ROUND_START, description = "第${round}回合开始")
        is BattleEvent.HeroActionStart -> LocalSimulationEvent(
            round, LocalSimulationEventKind.ACTION, sourceName = nameOf(source, names), targetName = nameOf(source, names),
            targetRemaining = 0, description = "${nameOf(source, names)} 行动开始",
        )
        is BattleEvent.SkillTriggered -> LocalSimulationEvent(
            round = round,
            kind = if (round == 0) LocalSimulationEventKind.PREPARATION else LocalSimulationEventKind.ACTION,
            sourceName = nameOf(source, names),
            targetName = nameOf(source, names),
            skillName = skillName(skillId.toLong()),
            description = "${nameOf(source, names)} 执行${trigger.triggerLabel()}战法【${skillName(skillId.toLong())}】",
        )
        is BattleEvent.SkillPreparationStarted -> LocalSimulationEvent(
            round, LocalSimulationEventKind.ACTION, sourceName = nameOf(source, names), targetName = nameOf(source, names),
            skillName = skillName(skillId.toLong()), description = "${nameOf(source, names)} 准备【${skillName(skillId.toLong())}】，第${readyRound}回合生效",
        )
        is BattleEvent.SkillPreparationCompleted -> LocalSimulationEvent(
            round, LocalSimulationEventKind.ACTION, sourceName = nameOf(source, names), targetName = nameOf(source, names),
            skillName = skillName(skillId.toLong()), description = "${nameOf(source, names)} 完成准备并发动【${skillName(skillId.toLong())}】",
        )
        is BattleEvent.NormalAttack -> damageEvent(round, source, target, damage, targetTroopsAfter, "普通攻击", names)
        is BattleEvent.SkillDamage -> damageEvent(round, source, target, damage, targetTroopsAfter, "战法【${skillName(skillId.toLong())}】", names)
        is BattleEvent.OngoingDamage -> damageEvent(round, source, target, damage, targetTroopsAfter, "${status.label()}效果", names)
        is BattleEvent.Recovery -> LocalSimulationEvent(
            round, LocalSimulationEventKind.RECOVERY, nameOf(source, names), nameOf(target, names), skillName(skillId.toLong()),
            amount, targetTroopsAfter, "${nameOf(source, names)} 触发【${skillName(skillId.toLong())}】为 ${nameOf(target, names)} 恢复 $amount 兵力",
        )
        is BattleEvent.StatusApplied -> LocalSimulationEvent(
            round, if (round == 0) LocalSimulationEventKind.PREPARATION else LocalSimulationEventKind.STATUS,
            nameOf(source, names), nameOf(target, names), skillName(skillId.toLong()), targetRemaining = 0,
            description = "${nameOf(source, names)} 对 ${nameOf(target, names)} 施加【${status.label()}】${durationRounds}回合",
        )
        is BattleEvent.StatChanged -> LocalSimulationEvent(
            round, LocalSimulationEventKind.STATUS, nameOf(source, names), nameOf(target, names), skillName(skillId.toLong()),
            description = "${nameOf(target, names)} ${stat.label()}${if (delta >= 0) "提高" else "降低"}${kotlin.math.abs(delta)}，持续${durationRounds}回合",
        )
        is BattleEvent.ModifierApplied -> LocalSimulationEvent(
            round, LocalSimulationEventKind.STATUS, nameOf(source, names), nameOf(target, names), skillName(skillId.toLong()),
            description = "${nameOf(source, names)} 的【${skillName(skillId.toLong())}】效果作用于 ${nameOf(target, names)}",
        )
        is BattleEvent.Evaded -> LocalSimulationEvent(
            round, LocalSimulationEventKind.STATUS, nameOf(source, names), nameOf(target, names),
            description = "${nameOf(target, names)} 闪避了 ${nameOf(source, names)} 的攻击",
        )
        is BattleEvent.EffectBlocked -> LocalSimulationEvent(
            round, LocalSimulationEventKind.STATUS, nameOf(source, names), nameOf(target, names), skillName(skillId.toLong()),
            description = "${nameOf(target, names)} 阻挡了【${skillName(skillId.toLong())}】效果",
        )
        is BattleEvent.StatusRemoved, is BattleEvent.EffectExpired, is BattleEvent.SkillRangeChanged,
        is BattleEvent.SkillPreparationCancelled, is BattleEvent.UnsupportedSkillEffect,
        is BattleEvent.UnsupportedEquipmentEffect, is BattleEvent.TriggerPoint, is BattleEvent.HeroActionEnd,
        is BattleEvent.RoundEnd -> null
        is BattleEvent.BattleEnd -> LocalSimulationEvent(
            round = MAX_ROUNDS, kind = LocalSimulationEventKind.RESULT, sourceName = outcome.toWinner(),
            description = "战斗结束：${outcome.toWinner()}",
        )
    }

    private fun damageEvent(
        round: Int,
        source: BattleHeroRef,
        target: BattleHeroRef,
        damage: Int,
        targetTroopsAfter: Int,
        action: String,
        names: Map<Int, String>,
    ) = LocalSimulationEvent(
        round, LocalSimulationEventKind.DAMAGE, nameOf(source, names), nameOf(target, names), action, damage, targetTroopsAfter,
        "${nameOf(source, names)} 对 ${nameOf(target, names)} 造成 $damage 伤害（$action）",
    )

    private fun nameOf(ref: BattleHeroRef, names: Map<Int, String>): String =
        names[ref.heroId.value] ?: config.hero(ref.heroId.value)?.name ?: ref.heroId.value.toString()

    private fun BattleOutcome.toWinner(): String = when (this) {
        BattleOutcome.ATTACKER_WIN -> "攻方"
        BattleOutcome.DEFENDER_WIN -> "守方"
        BattleOutcome.DRAW -> "平局"
    }

    private fun com.stzb.server.game.battle.skill.BattleTrigger.triggerLabel(): String = when (this) {
        com.stzb.server.game.battle.skill.BattleTrigger.BATTLE_PASSIVE -> "被动"
        com.stzb.server.game.battle.skill.BattleTrigger.BATTLE_COMMAND -> "指挥"
        else -> ""
    }

    private fun com.stzb.server.game.battle.BattleStatus.label(): String = when (this) {
        com.stzb.server.game.battle.BattleStatus.EMERGENCY_RECOVERY -> "急救"
        com.stzb.server.game.battle.BattleStatus.CONFUSION -> "混乱"
        com.stzb.server.game.battle.BattleStatus.HESITATION -> "犹豫"
        com.stzb.server.game.battle.BattleStatus.DISARM -> "怯战"
        com.stzb.server.game.battle.BattleStatus.EVADE -> "规避"
        else -> name
    }

    private fun com.stzb.server.game.battle.BattleStat.label(): String = when (this) {
        com.stzb.server.game.battle.BattleStat.ATTACK -> "攻击"
        com.stzb.server.game.battle.BattleStat.DEFENSE -> "防御"
        com.stzb.server.game.battle.BattleStat.STRATEGY -> "谋略"
        com.stzb.server.game.battle.BattleStat.SPEED -> "速度"
        com.stzb.server.game.battle.BattleStat.SIEGE -> "攻城"
        com.stzb.server.game.battle.BattleStat.HIT_RANGE -> "距离"
    }

    private fun positionName(position: Int): String = when (position) {
        0 -> "大营"
        1 -> "中军"
        else -> "前锋"
    }

    private fun baseTroops(level: Int, advance: Int): Int =
        5_000 + level * 100 + advance * 200

    private const val MAX_LEVEL = 50
    private const val MAX_ADVANCE = 5
    private const val MAX_ROUNDS = 8
    private const val MAX_RECORDS = 240
}

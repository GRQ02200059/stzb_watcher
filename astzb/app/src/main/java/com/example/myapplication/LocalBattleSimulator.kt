package com.example.myapplication

import android.content.Context
import org.json.JSONArray
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

object LocalBattleSimulator {
    private var loaded = false
    private val heroes = mutableMapOf<Long, SimHeroResource>()
    private val skills = mutableMapOf<Long, SimSkillResource>()

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            context.assets.open("simulator_hero_extra.json").bufferedReader(Charsets.UTF_8).use { reader ->
                val arr = JSONArray(reader.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    if (id <= 0L) continue
                    heroes[id] = SimHeroResource(
                        id = id,
                        name = obj.optString("name", "武将$id"),
                        country = obj.optString("country", ""),
                        armyType = obj.optString("type", ""),
                        iconId = obj.optLong("iconId", id),
                        distance = obj.optInt("distance", 1),
                        attack = obj.optDouble("attack", 50.0),
                        defense = obj.optDouble("def", 50.0),
                        strategy = obj.optDouble("ruse", 50.0),
                        speed = obj.optDouble("speed", 30.0),
                        attackGrow = obj.optDouble("attGrow", 0.5),
                        defenseGrow = obj.optDouble("defGrow", 0.5),
                        strategyGrow = obj.optDouble("ruseGrow", 0.5),
                        speedGrow = obj.optDouble("speedGrow", 0.5),
                        builtInSkillId = obj.optLong("methodId", 0L),
                    )
                }
            }
            context.assets.open("simulator_skill_extra.json").bufferedReader(Charsets.UTF_8).use { reader ->
                val arr = JSONArray(reader.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    if (id <= 0L) continue
                    skills[id] = SimSkillResource(
                        id = id,
                        name = obj.optString("name", "战法$id"),
                        type = obj.optString("type", ""),
                        study = obj.optString("studyDesc", "").isNotBlank() ||
                            obj.optString("studyStar", "").isNotBlank() ||
                            obj.optLong("id", 0L) >= 200000L,
                        probability = parsePercent(obj.optString("probability", "--")),
                        distance = obj.optInt("distance", 1),
                        effect = obj.optString("effect", ""),
                        desc = obj.optString("desc", ""),
                    )
                }
            }
            PacketLogStore.add("本机战斗模拟内核已加载：武将=${heroes.size} 战法=${skills.size}")
        }.onFailure {
            PacketLogStore.add("本机战斗模拟内核加载失败：${it.message}")
        }
    }

    fun resourceSummary(): LocalSkillResourceSummary {
        return LocalSkillResourceSummary(
            skillCount = skills.size,
            simulatorHeroCount = heroes.size,
            simulatorSkillCount = skills.size,
        )
    }

    fun defaultHeroIds(limit: Int = 6): List<Long> {
        return heroes.values
            .sortedWith(compareByDescending<SimHeroResource> { it.attack + it.defense + it.strategy + it.speed }.thenBy { it.id })
            .take(limit)
            .map { it.id }
    }

    fun heroName(heroId: Long): String {
        val normalizedId = HeroIdNormalizer.normalize(heroId)
        return heroes[normalizedId]?.name ?: HeroNameResolver.nameOf(normalizedId)
    }

    fun heroIconId(heroId: Long): Long {
        val normalizedId = HeroIdNormalizer.normalize(heroId)
        val hero = heroes[normalizedId]
        return if (hero == null) {
            HeroNameResolver.iconIdOf(normalizedId)
        } else {
            HeroNameResolver.iconIdForName(hero.name, hero.iconId)
        }
    }

    fun selectableHeroes(limit: Int = 400): List<LocalSimHeroOption> {
        return heroes.values
            .sortedWith(compareBy<SimHeroResource> { it.name }.thenBy { it.id })
            .take(limit)
            .map { LocalSimHeroOption(it.id, it.name, it.country, it.armyType, it.iconId) }
    }

    fun skillName(skillId: Long): String {
        return skills[skillId]?.name ?: SkillNameResolver.nameOf(skillId)
    }

    fun selectableSkills(limit: Int = 800): List<LocalSimSkillOption> {
        return skills.values
            .filter { it.study }
            .sortedWith(compareBy<SimSkillResource> { it.type }.thenBy { it.name }.thenBy { it.id })
            .take(limit)
            .map { LocalSimSkillOption(it.id, it.name, it.type, it.study, it.probability, it.distance, it.desc) }
    }

    fun defaultWebConfig(): LocalSimulationConfig {
        return LocalSimulationConfig(
            blue = LocalSimTeamConfig(
                morale = 100,
                heroes = listOf(
                    LocalSimHeroConfig(heroId = 100027, level = 40, advance = 5),
                    LocalSimHeroConfig(heroId = 100016, level = 40, advance = 5),
                    LocalSimHeroConfig(heroId = 100090, level = 40, advance = 5),
                ),
            ),
            red = LocalSimTeamConfig(
                morale = 100,
                heroes = listOf(
                    LocalSimHeroConfig(heroId = 100013, level = 40, advance = 5),
                    LocalSimHeroConfig(heroId = 100649, level = 40, advance = 5),
                    LocalSimHeroConfig(heroId = 100023, level = 40, advance = 5),
                ),
            ),
            repeat = 1,
            seed = 20260709,
        )
    }

    fun simulate(config: LocalSimulationConfig): LocalSimulationSummary {
        val repeat = max(1, config.repeat)
        val runs = (0 until repeat).map { idx ->
            BattleRun(config, seed = config.seed + idx).run()
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

    fun buildDemoConfig(heroIds: List<Long>): LocalSimulationConfig {
        val ids = if (heroIds.size >= 6) heroIds.take(6) else defaultHeroIds(6)
        val blue = ids.take(3).map { LocalSimHeroConfig(heroId = it) }
        val red = ids.drop(3).take(3).map { LocalSimHeroConfig(heroId = it) }
        return LocalSimulationConfig(
            blue = LocalSimTeamConfig(morale = 100, heroes = blue),
            red = LocalSimTeamConfig(morale = 100, heroes = red),
            repeat = 20,
            seed = 20260709,
        )
    }

    private class BattleRun(
        private val config: LocalSimulationConfig,
        seed: Int,
    ) {
        private val random = Random(seed)
        private val records = mutableListOf<String>()
        private val events = mutableListOf<LocalSimulationEvent>()
        private val blue = config.blue.heroes.mapIndexedNotNull { idx, cfg -> createHero(cfg, "攻方", idx) }
        private val red = config.red.heroes.mapIndexedNotNull { idx, cfg -> createHero(cfg, "守方", idx) }
        private val all = blue + red

        fun run(): LocalSimulationRun {
            val entryBlue = blue.map { it.snapshot() }
            val entryRed = red.map { it.snapshot() }
            records += "【攻方阵容】"
            blue.forEach { records += "(${it.positionName}) ${it.name} Lv.${it.level} 兵力=${it.arms} 攻=${it.attack.round1()} 防=${it.defense.round1()} 谋=${it.strategy.round1()} 速=${it.speed.round1()}" }
            records += "【守方阵容】"
            red.forEach { records += "(${it.positionName}) ${it.name} Lv.${it.level} 兵力=${it.arms} 攻=${it.attack.round1()} 防=${it.defense.round1()} 谋=${it.strategy.round1()} 速=${it.speed.round1()}" }

            applyPreparationSkills()
            var roundsPlayed = 0
            for (round in 1..8) {
                if (isOver()) break
                roundsPlayed = round
                records += "第${round}回合"
                events += LocalSimulationEvent(
                    round = round,
                    kind = LocalSimulationEventKind.ROUND_START,
                    description = "第${round}回合开始",
                )
                all.filter { it.alive }.sortedByDescending { it.speed }.forEach { actor ->
                    if (!actor.alive || isOver()) return@forEach
                    actorTurn(actor, round)
                    decayWounded(actor)
                }
            }

            val blueArms = blue.sumOf { it.arms }
            val redArms = red.sumOf { it.arms }
            val winner = when {
                blue.firstOrNull()?.alive == false -> "守方"
                red.firstOrNull()?.alive == false -> "攻方"
                blueArms > redArms -> "攻方"
                redArms > blueArms -> "守方"
                else -> "平局"
            }
            records += "战斗结束：$winner 胜负判定，攻方剩余=$blueArms 守方剩余=$redArms"
            events += LocalSimulationEvent(
                round = roundsPlayed,
                kind = LocalSimulationEventKind.RESULT,
                sourceName = winner,
                amount = blueArms - redArms,
                description = "战斗结束：$winner，攻方剩余=$blueArms，守方剩余=$redArms",
            )
            return LocalSimulationRun(
                winner = winner,
                blueRemain = blueArms,
                redRemain = redArms,
                records = records.take(240),
                attackerHeroes = entryBlue.mapIndexed { index, hero ->
                    hero.copy(remainingTroops = blue.getOrNull(index)?.arms ?: 0)
                },
                defenderHeroes = entryRed.mapIndexed { index, hero ->
                    hero.copy(remainingTroops = red.getOrNull(index)?.arms ?: 0)
                },
                events = events.toList(),
                roundsPlayed = roundsPlayed,
                seed = config.seed,
            )
        }

        private fun createHero(config: LocalSimHeroConfig, camp: String, index: Int): SimBattleHero? {
            val normalizedId = HeroIdNormalizer.normalize(config.heroId)
            val res = heroes[normalizedId] ?: return null
            val level = config.level.coerceIn(1, 50)
            val attrs = SimAttrs(
                attack = res.attack + (level - 1) * res.attackGrow + config.extraAttack + 20.0,
                defense = res.defense + (level - 1) * res.defenseGrow + config.extraDefense + 20.0,
                strategy = res.strategy + (level - 1) * res.strategyGrow + config.extraStrategy + 20.0,
                speed = res.speed + (level - 1) * res.speedGrow + config.extraSpeed + 20.0,
            )
            val skillIds = buildList {
                if (res.builtInSkillId > 0L) add(res.builtInSkillId)
                addAll(config.equipSkillIds.filter { it > 0L })
            }
            return SimBattleHero(
                id = res.id,
                name = res.name,
                camp = camp,
                positionName = positionName(index),
                level = level,
                advance = config.advance,
                distance = res.distance,
                arms = 5000 + level * 100 + config.advance * 200,
                maxArms = 5000 + level * 100 + config.advance * 200,
                attack = attrs.attack,
                defense = attrs.defense,
                strategy = attrs.strategy,
                speed = attrs.speed,
                skillIds = skillIds,
            )
        }

        private fun applyPreparationSkills() {
            all.forEach { hero ->
                hero.skillIds.mapNotNull { skills[it] }.filter { it.type == "被动" || it.type == "指挥" }.forEach { skill ->
                    val value = extractFirstNumber(skill.desc)
                    when {
                        skill.desc.contains("攻击属性提高") -> hero.attack += value * 0.35
                        skill.desc.contains("防御属性提高") -> hero.defense += value * 0.35
                        skill.desc.contains("谋略属性提高") -> hero.strategy += value * 0.35
                        skill.desc.contains("速度属性提高") -> hero.speed += value * 0.35
                    }
                    records += "${hero.name} 执行${skill.type}战法【${skill.name}】"
                    events += LocalSimulationEvent(
                        round = 0,
                        kind = LocalSimulationEventKind.PREPARATION,
                        sourceName = hero.name,
                        targetName = hero.name,
                        skillName = skill.name,
                        targetRemaining = hero.arms,
                        description = "${hero.name} 执行${skill.type}战法【${skill.name}】",
                    )
                }
            }
        }

        private fun actorTurn(actor: SimBattleHero, round: Int) {
            records += "${actor.name} 行动开始，兵力=${actor.arms}"
            events += LocalSimulationEvent(
                round = round,
                kind = LocalSimulationEventKind.ACTION,
                sourceName = actor.name,
                targetName = actor.name,
                targetRemaining = actor.arms,
                description = "${actor.name} 行动开始，兵力=${actor.arms}",
            )
            actor.skillIds.mapNotNull { skills[it] }.filter { it.type == "主动" }.forEach { skill ->
                if (roll(skill.probability)) castSkill(actor, skill, round)
            }
            val target = chooseTarget(actor) ?: return
            doAttack(actor, target, rate = 100.0, source = "普通攻击", round = round)
            actor.skillIds.mapNotNull { skills[it] }.filter { it.type == "追击" }.forEach { skill ->
                if (roll(skill.probability)) castSkill(actor, skill, round)
            }
        }

        private fun castSkill(actor: SimBattleHero, skill: SimSkillResource, round: Int) {
            val target = chooseTarget(actor) ?: return
            val rate = extractDamageRate(skill.desc).ifNaN { 120.0 }
            when {
                skill.desc.contains("恢复") || skill.effect.contains("休整") -> {
                    val heal = ((actor.maxArms * 300.0) / (3500.0 + actor.maxArms) * (rate / 100.0)).roundToInt()
                    val before = actor.arms
                    actor.arms = min(actor.maxArms, actor.arms + heal)
                    records += "${actor.name} 发动【${skill.name}】恢复 ${actor.arms - before} 兵力"
                    events += LocalSimulationEvent(
                        round = round,
                        kind = LocalSimulationEventKind.RECOVERY,
                        sourceName = actor.name,
                        targetName = actor.name,
                        skillName = skill.name,
                        amount = actor.arms - before,
                        targetRemaining = actor.arms,
                        description = "${actor.name} 发动【${skill.name}】恢复 ${actor.arms - before} 兵力",
                    )
                }
                skill.desc.contains("策略") || skill.desc.contains("谋略") || skill.effect.contains("恐慌") || skill.effect.contains("燃烧") -> {
                    doStrategyDamage(actor, target, rate, "战法【${skill.name}】", round)
                }
                skill.desc.contains("伤害") || skill.desc.contains("攻击") -> {
                    doAttack(actor, target, rate, "战法【${skill.name}】", round)
                }
                else -> {
                    records += "${actor.name} 发动【${skill.name}】，当前通用内核按状态战法记录处理"
                    events += LocalSimulationEvent(
                        round = round,
                        kind = LocalSimulationEventKind.STATUS,
                        sourceName = actor.name,
                        targetName = target.name,
                        skillName = skill.name,
                        targetRemaining = target.arms,
                        description = "${actor.name} 发动【${skill.name}】，当前通用内核按状态战法记录处理",
                    )
                }
            }
        }

        private fun doAttack(actor: SimBattleHero, target: SimBattleHero, rate: Double, source: String, round: Int) {
            val armsDamage = (actor.arms * 373.0) / (7700.0 + actor.arms)
            val baseDamage = actor.attack * random.nextDouble(0.30, 0.40) * (rate / 100.0)
            val diffFactor = calcAttackDefenseDiff(actor.attack, target.defense)
            val mainDamage = ((300.0 * actor.arms) / (3500.0 + actor.arms)) * (rate / 100.0) * diffFactor
            val damage = max(1, (armsDamage + baseDamage + mainDamage).roundToInt())
            applyDamage(actor, target, damage, source, round)
        }

        private fun doStrategyDamage(actor: SimBattleHero, target: SimBattleHero, rate: Double, source: String, round: Int) {
            val armsDamage = (actor.arms * 178.0) / (6459.0 + actor.arms)
            val strategyEffect = calcStrategyEffect(target.strategy)
            val baseDamage = actor.strategy * 0.5 * strategyEffect
            val mainDamage = ((300.0 * actor.arms) / (3500.0 + actor.arms)) * (rate / 100.0) * strategyEffect
            val damage = max(1, (armsDamage + baseDamage + mainDamage).roundToInt())
            applyDamage(actor, target, damage, source, round)
        }

        private fun applyDamage(actor: SimBattleHero, target: SimBattleHero, damage: Int, source: String, round: Int) {
            val realDamage = min(target.arms, damage)
            target.arms -= realDamage
            target.hurtArms += (realDamage * 0.35).roundToInt()
            records += "${actor.name} 对 ${target.name} 造成 $realDamage 伤害（$source），${target.name}剩余=${target.arms}"
            events += LocalSimulationEvent(
                round = round,
                kind = LocalSimulationEventKind.DAMAGE,
                sourceName = actor.name,
                targetName = target.name,
                skillName = source,
                amount = realDamage,
                targetRemaining = target.arms,
                description = "${actor.name} 对 ${target.name} 造成 $realDamage 伤害（$source）",
            )
        }

        private fun decayWounded(hero: SimBattleHero) {
            if (hero.hurtArms > 0) hero.hurtArms = (hero.hurtArms * 0.87).roundToInt()
        }

        private fun chooseTarget(actor: SimBattleHero): SimBattleHero? {
            val enemies = if (actor.camp == "攻方") red else blue
            return enemies.filter { it.alive }.minByOrNull { it.positionOrder }
        }

        private fun isOver(): Boolean {
            return blue.none { it.alive } || red.none { it.alive } ||
                blue.firstOrNull()?.alive == false || red.firstOrNull()?.alive == false
        }

        private fun roll(probability: Double): Boolean {
            if (probability <= 0.0) return false
            if (probability >= 100.0) return true
            return random.nextDouble(100.0) < probability
        }
    }

    private fun positionName(index: Int): String = when (index) {
        0 -> "大营"
        1 -> "中军"
        else -> "前锋"
    }

    private fun calcAttackDefenseDiff(attack: Double, defense: Double): Double {
        val diff = attack - defense
        return if (diff >= 0) 3.0 - (500.0 / (250.0 + diff)) else 100.0 / (100.0 - diff)
    }

    private fun calcStrategyEffect(strategy: Double): Double {
        return if (strategy <= 50.0) 1.0 else kotlin.math.ceil(100.0 - (75.0 - (9375.0 / (75.0 + strategy)))) / 100.0
    }

    private fun parsePercent(text: String): Double {
        return text.replace("%", "").trim().toDoubleOrNull() ?: 0.0
    }

    private fun extractDamageRate(text: String): Double {
        val regexes = listOf(
            Regex("伤害率([0-9]+(?:\\.[0-9]+)?)%"),
            Regex("恢复率([0-9]+(?:\\.[0-9]+)?)%"),
        )
        return regexes.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull() } ?: Double.NaN
    }

    private fun extractFirstNumber(text: String): Double {
        return Regex("([0-9]+(?:\\.[0-9]+)?)").find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun Double.ifNaN(default: () -> Double): Double = if (isNaN()) default() else this
    private fun Double.round1(): String = "%.1f".format(this)
}

data class LocalSimulationConfig(
    val blue: LocalSimTeamConfig,
    val red: LocalSimTeamConfig,
    val repeat: Int = 1,
    val seed: Int = 1,
)

data class LocalSimTeamConfig(
    val morale: Int = 100,
    val heroes: List<LocalSimHeroConfig>,
)

data class LocalSimHeroConfig(
    val heroId: Long,
    val level: Int = 40,
    val advance: Int = 0,
    val equipSkillIds: List<Long> = emptyList(),
    val extraAttack: Double = 0.0,
    val extraDefense: Double = 0.0,
    val extraStrategy: Double = 0.0,
    val extraSpeed: Double = 0.0,
)

data class LocalSimulationSummary(
    val repeat: Int,
    val blueWins: Int,
    val redWins: Int,
    val draws: Int,
    val blueWinRate: Double,
    val redWinRate: Double,
    val drawRate: Double,
    val firstRun: LocalSimulationRun,
)

data class LocalSimulationRun(
    val winner: String,
    val blueRemain: Int,
    val redRemain: Int,
    val records: List<String>,
    val attackerHeroes: List<LocalSimulationHeroSnapshot> = emptyList(),
    val defenderHeroes: List<LocalSimulationHeroSnapshot> = emptyList(),
    val events: List<LocalSimulationEvent> = emptyList(),
    val roundsPlayed: Int = 0,
    val seed: Int = 0,
)

enum class LocalSimulationEventKind {
    PREPARATION,
    ROUND_START,
    ACTION,
    DAMAGE,
    RECOVERY,
    STATUS,
    RESULT,
}

data class LocalSimulationEvent(
    val round: Int,
    val kind: LocalSimulationEventKind,
    val sourceName: String = "",
    val targetName: String = "",
    val skillName: String = "",
    val amount: Int = 0,
    val targetRemaining: Int = 0,
    val description: String = "",
)

data class LocalSimulationHeroSnapshot(
    val heroId: Long,
    val name: String,
    val positionName: String,
    val initialTroops: Int,
    val remainingTroops: Int,
    val level: Int,
    val advance: Int,
) {
    val alive: Boolean get() = remainingTroops > 0
}

private data class SimHeroResource(
    val id: Long,
    val name: String,
    val country: String,
    val armyType: String,
    val iconId: Long,
    val distance: Int,
    val attack: Double,
    val defense: Double,
    val strategy: Double,
    val speed: Double,
    val attackGrow: Double,
    val defenseGrow: Double,
    val strategyGrow: Double,
    val speedGrow: Double,
    val builtInSkillId: Long,
)

data class LocalSimHeroOption(
    val id: Long,
    val name: String,
    val country: String,
    val armyType: String,
    val iconId: Long,
)

data class LocalSimSkillOption(
    val id: Long,
    val name: String,
    val type: String,
    val study: Boolean,
    val probability: Double,
    val distance: Int,
    val desc: String,
)

private data class SimSkillResource(
    val id: Long,
    val name: String,
    val type: String,
    val study: Boolean,
    val probability: Double,
    val distance: Int,
    val effect: String,
    val desc: String,
)

private data class SimAttrs(
    val attack: Double,
    val defense: Double,
    val strategy: Double,
    val speed: Double,
)

private data class SimBattleHero(
    val id: Long,
    val name: String,
    val camp: String,
    val positionName: String,
    val level: Int,
    val advance: Int,
    val distance: Int,
    var arms: Int,
    val maxArms: Int,
    var attack: Double,
    var defense: Double,
    var strategy: Double,
    var speed: Double,
    val skillIds: List<Long>,
) {
    val alive: Boolean get() = arms > 0
    var hurtArms: Int = 0
    val positionOrder: Int = when (positionName) {
        "前锋" -> 0
        "中军" -> 1
        else -> 2
    }

    fun snapshot() = LocalSimulationHeroSnapshot(
        heroId = id,
        name = name,
        positionName = positionName,
        initialTroops = maxArms,
        remainingTroops = arms,
        level = level,
        advance = advance,
    )
}

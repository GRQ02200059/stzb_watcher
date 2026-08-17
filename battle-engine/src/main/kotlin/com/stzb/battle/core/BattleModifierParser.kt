package com.stzb.battle.core

object BattleModifierParser {
    fun parseEquipment(
        config: EquipmentConfig,
        features: List<EquipmentFeatureConfig>,
    ): List<BattleModifier> {
        val modifiers = mutableListOf<BattleModifier>()
        val descriptions = buildList {
            add(config.skillDescription)
            features.forEach { add(it.description) }
        }.flatMap { it.split('；', ';') }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        descriptions.forEach { description ->
            val parsed = parseDescription(config.id, description)
            modifiers.addAll(parsed)
        }
        return modifiers.ifEmpty {
            listOf(BattleModifier.Unsupported(config.id, config.skillDescription))
        }
    }

    private fun parseDescription(sourceId: Int, description: String): List<BattleModifier> {
        val modifiers = mutableListOf<BattleModifier>()
        statPatterns.forEach { (stat, regex) ->
            regex.find(description)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()?.let {
                modifiers += BattleModifier.Stat(stat, it)
            }
        }
        Regex("""造成的?攻击伤害提高([0-9.]+)%""").find(description)
            ?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
            ?.let { modifiers += BattleModifier.DamageDealtPercent(school = DamageSchool.PHYSICAL, percent = it) }
        Regex("""受到的?普通攻击伤害降低([0-9.]+)%""").find(description)
            ?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
            ?.let { modifiers += BattleModifier.DamageTakenPercent(origin = DamageOrigin.NORMAL, percent = -it) }
        Regex("""受到的?主动战法伤害降低#?([0-9.]+)""").find(description)
            ?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
            ?.let { modifiers += BattleModifier.DamageTakenPercent(origin = DamageOrigin.ACTIVE, percent = -it) }
        Regex("""主动战法伤害提高#?([0-9.]+)""").find(description)
            ?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
            ?.let { modifiers += BattleModifier.DamageDealtPercent(origin = DamageOrigin.ACTIVE, percent = it) }
        Regex("""发动率提高#?([0-9.]+)""").find(description)
            ?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
            ?.let { modifiers += BattleModifier.SkillProbabilityPercent(it) }
        Regex("""无视目标#?([0-9.]+)""").find(description)
            ?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
            ?.let { modifiers += BattleModifier.DefenseIgnorePercent(it) }

        return modifiers.ifEmpty { listOf(BattleModifier.Unsupported(sourceId, description)) }
    }

    private val statPatterns = listOf(
        BattleStat.ATTACK to Regex("""攻击属性提高([0-9.]+)"""),
        BattleStat.DEFENSE to Regex("""防御属性提高([0-9.]+)"""),
        BattleStat.DEFENSE to Regex("""防御属性提升#?([0-9.]+)"""),
        BattleStat.STRATEGY to Regex("""谋略属性提高([0-9.]+)"""),
        BattleStat.SPEED to Regex("""速度属性提高#?([0-9.]+)"""),
        BattleStat.SIEGE to Regex("""攻城属性提高([0-9.]+)"""),
        BattleStat.HIT_RANGE to Regex("""攻击距离\+([0-9.]+)"""),
    )
}

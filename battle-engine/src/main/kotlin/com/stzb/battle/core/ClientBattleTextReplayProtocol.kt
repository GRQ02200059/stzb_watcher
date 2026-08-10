package com.stzb.battle.core

internal data class ClientReportAction(
    val id: Int,
    val params: List<Any> = emptyList(),
) {
    fun encode(): String =
        buildString {
            append(id.toString(36).padStart(2, '0'))
            if (params.isNotEmpty()) append(params.joinToString(","))
        }
}

internal object ClientBattleTextReplayProtocol {
    const val HERO_NAME = 14
    const val SYSTEM_EFFECT_SOURCE = 20
    const val HERO_INFO = 205
    const val ARMY_EFFECT_SOURCE = 208
    const val SKILL_TRIGGERED_PASSIVE = 21
    const val SKILL_TRIGGERED_COMMAND = 22
    const val SKILL_TRIGGERED_ACTIVE = 23
    const val SKILL_TRIGGERED_PURSUIT = 24
    const val PREPARATION_STATUS_APPLIED = 28
    const val PREPARATION_STATUS_UNRESOLVED = 29
    const val PREPARE = 4
    const val PREPARATION_HERO = 5
    const val PREPARATION_END = 8
    const val ROUND = 9
    const val HERO_ACTION_START = 10
    const val HERO_ACTION_END = 11
    const val SKILL_PREPARATION_STARTED = 25
    const val SKILL_PREPARATION_CANCELLED = 27
    const val NORMAL_ATTACK = 119
    const val NORMAL_DAMAGE = 121
    const val SKILL_BEGIN = 213
    const val SKILL_END = 214
    const val PASSIVE_STAGE_END = 216
    const val PREPARATION_EFFECT_BOUNDARY = 217
    const val PREPARATION_EFFECT_BEGIN = 222
    const val PREPARATION_EFFECT_END = 223
    const val SKILL_CAST = 301
    const val DERIVED_SKILL_TRIGGERED = 300
    const val ATTACK_SKILL_DAMAGE = 59
    const val SKILL_DAMAGE = 60
    const val PANIC_ONGOING_DAMAGE = 62
    const val ONGOING_DAMAGE = 242
    const val HEX_ONGOING_DAMAGE = 243
    const val RECOVERY = 63
    const val ONGOING_RECOVERY = 64
    const val ATTACK_DAMAGE_RECOVERY = 202
    const val STATUS = 102
    const val STATUS_REMOVED = 102
    const val DAMAGE_EVADED = 110
    const val END = 13
    const val ATTACKER_WIN = 127
    const val DRAW = 206
    const val DEFENDER_WIN = 207
    const val FINAL_TROOPS = 224
    const val INITIAL_ATTACK = 45
    const val INITIAL_DEFENSE = 46
    const val INITIAL_STRATEGY = 47
    const val INITIAL_SPEED = 48
    const val INITIAL_SIEGE = 49
    const val INITIAL_ATTACK_RANGE = 50
    const val ATTACK_DECREASED = 52
    const val DEFENSE_DECREASED = 53
    const val STRATEGY_DECREASED = 54
    const val SPEED_DECREASED = 55
    const val FLAT_ATTACK = 31
    const val FLAT_DEFENSE = 32
    const val SKILL_RANGE_CHANGED = 36
    const val FLAT_STRATEGY = 33
    const val FLAT_SPEED = 34
    const val FLAT_SIEGE = 35
    const val FLAT_ATTACK_RANGE = 36
    const val MODIFIER_APPLIED = 694
    const val TROOP_EFFECT_SOURCE = 285
    const val EQUIPMENT_EFFECT_SOURCE = 406
    const val COMMAND_STAGE_BEGIN = 651
    const val COMMAND_HERO_BEGIN = 644
    const val COMMAND_HERO_END = 645
    const val SURFACE_STAGE_READY = 639
    const val SURFACE_STAGE_BEGIN = 636
    const val SURFACE_EFFECT_SOURCE = 330
    const val SURFACE_STAGE_END = 638
    const val PASSIVE_STAGE_BEGIN = 637
    const val ACTIVE_SKILL_DAMAGE_REDUCTION = 262
    const val EFFECT_BLOCKED = 210
    const val INITIALIZATION_READY = 654
    const val INITIALIZATION_BEGIN = 641
    const val ATTACKER_INFO_BEGIN = 15
    const val DEFENDER_INFO_BEGIN = 16
    const val HERO_INFO_END = 2
    const val PREPARATION_READY = 215
    const val PREPARATION_BEGIN = 649
    const val PREPARATION_RULES_BEGIN = 650
    const val SYSTEM_STAGE_BEGIN = 631
    const val COUNTRY_STAGE_BEGIN = 632
    const val COUNTRY_STAGE_END = 633
    const val ARMY_STAGE_READY = 640
    const val TROOP_STAGE_BEGIN = 635
    const val EQUIPMENT_STAGE_BEGIN = 634

    fun position(side: Side, formationPosition: Int): Int {
        require(formationPosition in 0..2) { "battle formation position must be 0..2: $formationPosition" }
        return when (side) {
            Side.ATTACKER -> formationPosition + 1
            Side.DEFENDER -> 6 - formationPosition
        }
    }

    fun position(ref: BattleHeroRef): Int = position(ref.side, ref.position)

    fun teamPosition(side: Side): Int =
        when (side) {
            Side.ATTACKER -> 0
            Side.DEFENDER -> 7
        }

    fun effectId(status: BattleStatus): Int = when (status) {
        BattleStatus.CONFUSION -> 501
        BattleStatus.BERSERK -> 503
        BattleStatus.HESITATION -> 502
        BattleStatus.DISARM -> 552
        BattleStatus.SHAKE -> 303
        BattleStatus.PANIC -> 304
        BattleStatus.BURN -> 305
        BattleStatus.HEX -> 306
        BattleStatus.INSIGHT -> 771
        BattleStatus.EVADE -> 514
        BattleStatus.IGNORE_EVADE -> 515
        BattleStatus.DOUBLE_ATTACK -> 506
        BattleStatus.FIRST_ACTION -> 761
        BattleStatus.EMERGENCY_RECOVERY -> 401
        BattleStatus.ATTACK_BUFF -> 101
        BattleStatus.DEFENSE_BUFF -> 102
        BattleStatus.STRATEGY_BUFF -> 103
        BattleStatus.SPEED_BUFF -> 104
        BattleStatus.ATTACK_DEBUFF -> 151
        BattleStatus.DEFENSE_DEBUFF -> 152
        BattleStatus.STRATEGY_DEBUFF -> 153
        BattleStatus.SPEED_DEBUFF -> 154
        BattleStatus.PHYSICAL_DAMAGE_DEALT_INCREASED -> 531
        BattleStatus.PHYSICAL_DAMAGE_DEALT_REDUCED -> 532
        BattleStatus.STRATEGY_DAMAGE_DEALT_INCREASED -> 533
        BattleStatus.STRATEGY_DAMAGE_DEALT_REDUCED -> 534
        BattleStatus.PHYSICAL_DAMAGE_TAKEN_INCREASED -> 521
        BattleStatus.PHYSICAL_DAMAGE_TAKEN_REDUCED -> 522
        BattleStatus.STRATEGY_DAMAGE_TAKEN_INCREASED -> 523
        BattleStatus.STRATEGY_DAMAGE_TAKEN_REDUCED -> 524
    }

    fun effectId(stat: BattleStat, delta: Int): Int = when (stat) {
        BattleStat.ATTACK -> if (delta >= 0) 101 else 151
        BattleStat.DEFENSE -> if (delta >= 0) 102 else 152
        BattleStat.STRATEGY -> if (delta >= 0) 103 else 153
        BattleStat.SPEED -> if (delta >= 0) 104 else 154
        BattleStat.SIEGE, BattleStat.HIT_RANGE -> 0
    }

    fun statusAppliedAction(status: BattleStatus): Int = when (status) {
        BattleStatus.CONFUSION -> 65
        BattleStatus.BERSERK -> 67
        BattleStatus.HESITATION -> 66
        BattleStatus.INSIGHT -> 70
        BattleStatus.EVADE -> 73
        BattleStatus.IGNORE_EVADE -> 74
        BattleStatus.FIRST_ACTION -> 83
        BattleStatus.DOUBLE_ATTACK -> 84
        BattleStatus.DISARM -> 87
        else -> 301
    }

    fun ongoingDamageAction(status: BattleStatus): Int = when (status) {
        BattleStatus.PANIC -> PANIC_ONGOING_DAMAGE
        BattleStatus.HEX -> HEX_ONGOING_DAMAGE
        else -> ONGOING_DAMAGE
    }

    fun initialAttributeAction(stat: BattleStat): Int = when (stat) {
        BattleStat.ATTACK -> INITIAL_ATTACK
        BattleStat.DEFENSE -> INITIAL_DEFENSE
        BattleStat.STRATEGY -> INITIAL_STRATEGY
        BattleStat.SPEED -> INITIAL_SPEED
        BattleStat.SIEGE -> INITIAL_SIEGE
        BattleStat.HIT_RANGE -> INITIAL_ATTACK_RANGE
    }

    fun attributeChangeAction(stat: BattleStat, delta: Int): Int =
        if (delta >= 0) {
            initialAttributeAction(stat)
        } else {
            when (stat) {
                BattleStat.ATTACK -> ATTACK_DECREASED
                BattleStat.DEFENSE -> DEFENSE_DECREASED
                BattleStat.STRATEGY -> STRATEGY_DECREASED
                BattleStat.SPEED -> SPEED_DECREASED
                BattleStat.SIEGE, BattleStat.HIT_RANGE -> initialAttributeAction(stat)
            }
        }

    fun flatAttributeAction(stat: BattleStat): Int =
        when (stat) {
            BattleStat.ATTACK -> FLAT_ATTACK
            BattleStat.DEFENSE -> FLAT_DEFENSE
            BattleStat.STRATEGY -> FLAT_STRATEGY
            BattleStat.SPEED -> FLAT_SPEED
            BattleStat.SIEGE -> FLAT_SIEGE
            BattleStat.HIT_RANGE -> FLAT_ATTACK_RANGE
        }

    fun preparationSourceAction(stage: BattlePreparationStage): Int =
        when (stage) {
            BattlePreparationStage.SYSTEM -> SYSTEM_EFFECT_SOURCE
            BattlePreparationStage.ARMY -> ARMY_EFFECT_SOURCE
            BattlePreparationStage.TROOP -> TROOP_EFFECT_SOURCE
            BattlePreparationStage.EQUIPMENT -> EQUIPMENT_EFFECT_SOURCE
            BattlePreparationStage.SURFACE -> SURFACE_EFFECT_SOURCE
        }

    fun supportsPreparationModifier(effectId: Int): Boolean =
        effectId in setOf(521, 522, 523, 524, 531, 532, 533, 534)

    fun supportsDerivedPreparationSkill(skillId: Int): Boolean =
        skillId in 210_000..213_999 || skillId == 221_006 || skillId in 450_000..459_999
}

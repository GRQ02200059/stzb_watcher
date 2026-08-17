package com.stzb.battle.core.skill

import com.stzb.battle.core.BattleConfigRepository
import com.stzb.battle.core.BattleHero
import com.stzb.battle.core.BattleHeroId
import com.stzb.battle.core.BattleHeroRef
import com.stzb.battle.core.BattleRandom
import com.stzb.battle.core.BattleRequest
import com.stzb.battle.core.BattleStats
import com.stzb.battle.core.BattleStatus
import com.stzb.battle.core.BattleTeam
import com.stzb.battle.core.FixedBattleRandom
import com.stzb.battle.core.Side
import com.stzb.battle.core.SkillDetailConfig
import com.stzb.battle.core.SkillKind
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SkillConditionInterpreterTest {
    @Test
    fun `scoped condition inventory is an independent literal`() {
        val graph = realGraph()
        val castRows = graph.details.filter { it.raw.castCondition != 0 }
        val preconditionRows = graph.details.filter { it.raw.precondition != 0 }
        val conditionRows = graph.details.filter { it.raw.condition != 0 }

        assertEquals(308, SkillScopeCatalog.loadDefault().mainSkillIds.size)
        assertEquals(666, graph.executionNodeIds.size)
        assertEquals(1933, graph.details.size)
        assertEquals(298, castRows.size)
        assertEquals(110, preconditionRows.size)
        assertEquals(63, conditionRows.size)
        assertEquals(EXPECTED_CAST_CONDITIONS, castRows.mapTo(linkedSetOf()) { it.raw.castCondition })
        assertEquals(
            EXPECTED_PRECONDITIONS,
            preconditionRows.mapTo(linkedSetOf()) { it.raw.precondition },
        )
        assertEquals(EXPECTED_CONDITIONS, conditionRows.mapTo(linkedSetOf()) { it.raw.condition })
    }

    @Test
    fun `every scoped condition code compiles through an explicit typed or plugin requirement`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        val compiled = graph.details.map(interpreter::compile)
        val requirements = compiled
            .flatMap { it.conditions }
            .filterIsInstance<SpecialConditionRequirement>()
        val expectedOwners = loadExpectedPluginOwners()
        val resolvedTargetCodes = expectedOwners.map(SpecialConditionRequirement::code)
            .filterTo(linkedSetOf(), ::isBuiltInTargetCondition)
        val pendingOwners = expectedOwners.filterNot {
            it.code in resolvedTargetCodes
        }.toSet()

        assertEquals(281, expectedOwners.size)
        assertEquals(
            expectedOwners.mapTo(linkedSetOf(), SpecialConditionRequirement::code),
            ScopedConditionCodeCatalog.codes,
        )
        assertEquals(pendingOwners, requirements.toSet())
        assertTrue(
            compiled.flatMap { it.conditions }
                .filterIsInstance<SkillCondition.TargetPredicate>()
                .isNotEmpty(),
        )
        assertTrue(interpreter.unknownCodes().isEmpty())
    }

    private fun isBuiltInTargetCondition(code: SkillConditionCode): Boolean =
        code.field == SkillConditionField.PRECONDITION &&
            code.value in setOf(
                -80, -70, 70, 80, -14, 14, 16,
                100003, 100010, 100479, 100661,
                1, 2, -2, 13, 19, 2099, 3100, 6000, -6000,
            ) ||
            code.field == SkillConditionField.CAST_CONDITION &&
            code.value in setOf(
                104, 203, 205, 207, 303,
                1103, 1123, 2313, 2414, 2434, 3103, 3123, 4003, 4013, 5300,
                6207, 6306, 11079, 11099, 12080, 12100, 14100,
            ) ||
            code.skillId == 200957 &&
            code.field == SkillConditionField.CAST_CONDITION &&
            code.value in 400..406 ||
            code.field == SkillConditionField.CAST_CONDITION &&
            (
                    code.value.toString().startsWith("127") ||
                    code.value.toString().startsWith("227") ||
                    code.value in setOf(
                        130001912, 230001912,
                        130005101, 230005101, 130005205, 130005301, 230005301,
                    )
                ) ||
            code.field == SkillConditionField.CAST_CONDITION &&
            code.value in setOf(
                320000301, 121002401, 321001701, 421001701,
                420024301, 420024302, 121079601, 321098402,
                321024601, 320024601, 321324601,
                320025101, 321525101,
                321226402, 321126401,
                321125401,
                321025601,
                320026811,
                320024411, 320024421,
                321325201,
                421325701,
                121329301, 321529301, 421529301,
                421196502, 321296501, 321396501, 321496501,
                321299001, 321399101, 321199301, 322200801,
                320025122, 321025111, 320025111,
                121384301, 221384301,
                220097913,
                320092602, 221095712,
                121196601, 421196601,
                220028331,
                327002401,
            ) ||
            code.field == SkillConditionField.PRECONDITION &&
            code.value in setOf(18, -18) ||
            code.field == SkillConditionField.CONDITION &&
            code.value == 17000 ||
            code.field == SkillConditionField.CONDITION &&
            code.value in setOf(1030, 1050, 1060, 1070, 1080, 1090, 2050, 2060) ||
            code.field == SkillConditionField.CAST_CONDITION &&
            code.value in setOf(500, 4000, 7001) ||
            code.field == SkillConditionField.CONDITION &&
            code.value in setOf(20160, 32002, 32011, 18306) ||
            code == SkillConditionCode(200016, SkillConditionField.CONDITION, 5003)
            || code == SkillConditionCode(200016, SkillConditionField.CONDITION, 21110)
            || code == SkillConditionCode(200253, SkillConditionField.CONDITION, 5003)
            || code == SkillConditionCode(200244, SkillConditionField.CONDITION, 5003)
            || code == SkillConditionCode(200244, SkillConditionField.CONDITION, 5005)
            || code == SkillConditionCode(200275, SkillConditionField.CONDITION, 5009)
            || code == SkillConditionCode(200277, SkillConditionField.CONDITION, 5006)
            || code == SkillConditionCode(200277, SkillConditionField.CONDITION, 5008)
            || code == SkillConditionCode(200294, SkillConditionField.CONDITION, 5006)
            || code == SkillConditionCode(200297, SkillConditionField.CONDITION, 5005)
            || code == SkillConditionCode(200950, SkillConditionField.CONDITION, 5007)
            || code == SkillConditionCode(
                200008,
                SkillConditionField.CAST_CONDITION,
                420000802,
            )
            || code == SkillConditionCode(200008, SkillConditionField.CONDITION, 26636)
            || code == SkillConditionCode(
                200268,
                SkillConditionField.CAST_CONDITION,
                420026822,
            )
            || code == SkillConditionCode(210270, SkillConditionField.CONDITION, 15002)
            || code == SkillConditionCode(210270, SkillConditionField.CONDITION, 15003)
            || code == SkillConditionCode(214254, SkillConditionField.CONDITION, 25011)
            || code == SkillConditionCode(210282, SkillConditionField.PRECONDITION, 500)
            || code == SkillConditionCode(210269, SkillConditionField.CONDITION, 25002)
            || code == SkillConditionCode(210269, SkillConditionField.CONDITION, 25003)
            || code == SkillConditionCode(200989, SkillConditionField.CONDITION, 24001)
            || code == SkillConditionCode(210257, SkillConditionField.CONDITION, 33003)
            || code == SkillConditionCode(210257, SkillConditionField.CONDITION, 24001)
            || code == SkillConditionCode(210257, SkillConditionField.CONDITION, 33004)
            || code == SkillConditionCode(210298, SkillConditionField.CONDITION, 5007)
            || code == SkillConditionCode(210298, SkillConditionField.CONDITION, 33005)
            || code == SkillConditionCode(200255, SkillConditionField.CONDITION, 29004)
            || code == SkillConditionCode(200255, SkillConditionField.CONDITION, 30000)
            || code == SkillConditionCode(212255, SkillConditionField.PRECONDITION, 4040)
            || code == SkillConditionCode(200264, SkillConditionField.CONDITION, 29001)
            || code == SkillConditionCode(
                200264,
                SkillConditionField.CAST_CONDITION,
                420026421,
            )
            || code == SkillConditionCode(
                211264,
                SkillConditionField.CAST_CONDITION,
                320026412,
            )
            || code == SkillConditionCode(
                200968,
                SkillConditionField.CAST_CONDITION,
                220096801,
            )
            || code == SkillConditionCode(
                200968,
                SkillConditionField.CAST_CONDITION,
                220096802,
            )
            || code == SkillConditionCode(200293, SkillConditionField.CONDITION, 5001)
            || code == SkillConditionCode(201006, SkillConditionField.CONDITION, 24001)
            || code == SkillConditionCode(200961, SkillConditionField.CONDITION, 5005)
            || code.field == SkillConditionField.PRECONDITION && code.value == 43

    @Test
    fun `real unresolved rows retain exact field code and skill plugin ownership`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.EventTrigger(BattleTrigger.DAMAGE_AFTER),
            interpreter.compile(graph.detail(20000802)).conditions.single(),
        )
        assertEquals(
            SkillCondition.FormationRoster(
                SkillCondition.FormationRoster.Kind.DISTINCT_BASE_ATTACK_RANGE,
            ),
            interpreter.compile(graph.detail(20024801)).conditions.single(),
        )
        assertEquals(
            listOf(90, 70, 50, 30),
            (20093902..20093905).map { detailId ->
                val predicate = interpreter.compile(graph.detail(detailId)).conditions.single()
                (predicate as SkillCondition.TargetPredicate).value
            },
        )
    }

    @Test
    fun `qixurulin damage template is restricted to strategy damage events`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)
        val detail = graph.detail(21028202)

        assertEquals(
            SkillCondition.EventTrigger(BattleTrigger.DAMAGE_AFTER),
            interpreter.compile(detail).conditions.single(),
        )
        assertFalse(interpreter.matches(detail, BattleTrigger.BATTLE_COMMAND, context()))
        assertTrue(interpreter.matches(detail, BattleTrigger.DAMAGE_AFTER, context()))
    }

    @Test
    fun `wubingzhilie selects exactly one branch for each treasure type`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)
        val detailByCondition = graph.details
            .filter { it.detailId / 100 == 200957 }
            .filter { it.raw.castCondition in 400..406 }
            .groupBy { it.raw.castCondition }
        val equipmentByCondition = linkedMapOf(
            400 to null,
            401 to 1040,
            402 to 1025,
            403 to 1034,
            404 to 1058,
            405 to 1049,
            406 to 1024,
        )

        assertEquals(equipmentByCondition.keys, detailByCondition.keys)
        equipmentByCondition.forEach { (expectedCondition, equipmentId) ->
            val request = request(equipmentId)
            detailByCondition.forEach { (condition, details) ->
                details.forEach { detail ->
                    assertEquals(
                        condition == expectedCondition,
                        interpreter.matches(
                            detail,
                            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                            context(
                                skillId = 200957,
                                view = SkillBattleView.entrySnapshot(request),
                                request = request,
                            ),
                        ),
                        "equipment=$equipmentId condition=$condition detail=${detail.detailId}",
                    )
                }
            }
        }
    }

    @Test
    fun `xinzhan ninth damage listener is restricted to damage events`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.EventTrigger(BattleTrigger.DAMAGE_AFTER),
            interpreter.compile(graph.detail(20027523)).conditions.single(),
        )
    }

    @Test
    fun `shoujing condition codes compile to exact sixth and eighth rounds`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.RoundRange(6, 6),
            interpreter.compile(graph.detail(20027704)).conditions.single(),
        )
        assertEquals(
            SkillCondition.RoundRange(8, 8),
            interpreter.compile(graph.detail(20027705)).conditions.single(),
        )
    }

    @Test
    fun `huiyan sixth allied damage condition is restricted to damage events`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.EventTrigger(BattleTrigger.DAMAGE_AFTER),
            interpreter.compile(graph.detail(20029402)).conditions.single(),
        )
    }

    @Test
    fun `manwang fifth hit condition is restricted to hurt events`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.EventTrigger(BattleTrigger.HURT_AFTER),
            interpreter.compile(graph.detail(20029725)).conditions.single(),
        )
    }

    @Test
    fun `qibu seventh team action condition accepts its three event families`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.EventTriggerSet(
                setOf(
                    BattleTrigger.NORMAL_ATTACK_AFTER,
                    BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                    BattleTrigger.PURSUIT_ATTEMPT,
                ),
            ),
            interpreter.compile(graph.detail(20095002)).conditions.single(),
        )
    }

    @Test
    fun `qiqinqizong conditions bind its seventh boundary and guards to harmful events`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)
        val harmfulEvents = SkillCondition.EventTriggerSet(
            setOf(
                BattleTrigger.DAMAGE_BEFORE,
                BattleTrigger.EFFECT_APPLYING,
            ),
        )

        assertEquals(
            harmfulEvents,
            interpreter.compile(graph.detail(21029801)).conditions.single(),
        )
        listOf(21029812, 21029813).forEach { detailId ->
            assertEquals(
                harmfulEvents,
                interpreter.compile(graph.detail(detailId)).conditions.single(),
            )
        }
    }

    @Test
    fun `fuboyangsha conditions bind normal attacks and forty percent progress`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        listOf(20025502, 20025503, 20025514).forEach { detailId ->
            assertEquals(
                SkillCondition.EventTrigger(BattleTrigger.NORMAL_ATTACK_AFTER),
                interpreter.compile(graph.detail(detailId)).conditions.single(),
            )
        }
        assertEquals(
            SkillCondition.RuntimeCounter(
                subject = Subject.SOURCE,
                namespace = "skill.200255.normal-damage-uplift",
                comparison = Comparison.GREATER_THAN_OR_EQUAL,
                value = 40,
            ),
            interpreter.compile(graph.detail(21225501)).conditions.single(),
        )
    }

    @Test
    fun `pibingjuyi conditions bind damage before and burn progression markers`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.EventTrigger(BattleTrigger.DAMAGE_BEFORE),
            interpreter.compile(graph.detail(20026402)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.LACKS_DETAIL_MARKER,
                20026421,
            ),
            interpreter.compile(graph.detail(20026421)).conditions.single(),
        )
        listOf(21126401, 21126402).forEach { detailId ->
            assertTrue(
                SkillCondition.TargetPredicate(
                    SkillCondition.TargetPredicate.Kind.HAS_DETAIL_MARKER,
                    20026412,
                ) in interpreter.compile(graph.detail(detailId)).conditions,
            )
        }
    }

    @Test
    fun `nanzhi precondition requires an inherent active skill target`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        listOf(21082801, 21082802, 21082803, 21182801, 21182802, 21182803, 21382801)
            .forEach { detailId ->
                assertEquals(
                    SkillCondition.TargetPredicate(
                        SkillCondition.TargetPredicate.Kind.INHERENT_ACTIVE_SKILL,
                    ),
                    interpreter.compile(graph.detail(detailId)).conditions.single(),
                )
            }
    }

    @Test
    fun `target preconditions compile as typed target predicates instead of pending plugins`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.TargetPredicate(SkillCondition.TargetPredicate.Kind.ALLY),
            interpreter.compile(graph.detail(20027302)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.MORALE_LOWER_THAN_SOURCE,
            ),
            interpreter.compile(graph.detail(20098204)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HERO_ID,
                100003,
            ),
            interpreter.compile(graph.detail(20090234)).conditions.single(),
        )
    }

    @Test
    fun `verified client round codes compile to exact round ranges`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.RoundRange(1, 3),
            interpreter.compile(graph.detail(20088501)).conditions.single(),
        )
        assertEquals(
            SkillCondition.RoundRange(4, 8),
            interpreter.compile(graph.detail(20029211)).conditions.single(),
        )
        assertEquals(
            listOf(
                SkillCondition.RoundRange(3, 3),
                SkillCondition.TargetPredicate(SkillCondition.TargetPredicate.Kind.ALLY),
            ),
            interpreter.compile(graph.detail(21026501)).conditions,
        )
        assertEquals(
            SkillCondition.RoundRange(5, 5),
            interpreter.compile(graph.detail(21026503)).conditions.first(),
        )
        assertEquals(
            SkillCondition.RoundRange(7, 7),
            interpreter.compile(graph.detail(21026505)).conditions.first(),
        )
    }

    @Test
    fun `verified troop ratio codes compile as target predicates`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.TROOPS_BELOW_PERCENT,
                50,
            ),
            interpreter.compile(graph.detail(20025601)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.TROOPS_ABOVE_PERCENT,
                60,
            ),
            interpreter.compile(graph.detail(20094404)).conditions.single(),
        )
    }

    @Test
    fun `verified status codes compile as target predicates`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_CONFUSION_OR_BERSERK,
            ),
            interpreter.compile(graph.detail(20000311)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_CONTROL_STATUS,
            ),
            interpreter.compile(graph.detail(20024303)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_ONGOING_DAMAGE_STATUS,
            ),
            interpreter.compile(graph.detail(20024305)).conditions.single(),
        )
    }

    @Test
    fun `verified morale threshold compiles as target predicate`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.MORALE_BELOW,
                160,
            ),
            interpreter.compile(graph.detail(20024101)).conditions.single(),
        )
    }

    @Test
    fun `verified attack range and hex conditions compile to typed semantics`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.AttackRange(Comparison.GREATER_THAN, 1),
            interpreter.compile(graph.detail(20025801)).conditions.single(),
        )
        assertEquals(
            SkillCondition.AttackRange(Comparison.LESS_THAN_OR_EQUAL, 1),
            interpreter.compile(graph.detail(20025803)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(SkillCondition.TargetPredicate.Kind.HAS_HEX),
            interpreter.compile(graph.detail(20079534)).conditions.single(),
        )
    }

    @Test
    fun `verified formation preconditions compile to exact roster semantics`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.FormationRoster(
                SkillCondition.FormationRoster.Kind.SAME_COUNTRY,
            ),
            interpreter.compile(graph.detail(20078403)).conditions.single(),
        )
        assertEquals(
            SkillCondition.FormationRoster(
                SkillCondition.FormationRoster.Kind.SAME_TROOP_TYPE,
            ),
            interpreter.compile(graph.detail(20078901)).conditions.single(),
        )
        assertEquals(
            SkillCondition.FormationRoster(
                SkillCondition.FormationRoster.Kind.SAME_TROOP_TYPE,
                negated = true,
            ),
            interpreter.compile(graph.detail(20078902)).conditions.single(),
        )
        assertEquals(
            SkillCondition.FormationRoster(
                SkillCondition.FormationRoster.Kind.DISTINCT_COUNTRY,
            ),
            interpreter.compile(graph.detail(20096401)).conditions.single(),
        )
        assertEquals(
            SkillCondition.FormationRoster(
                SkillCondition.FormationRoster.Kind.DISTINCT_BASE_ATTACK_RANGE,
            ),
            interpreter.compile(graph.detail(20024801)).conditions.single(),
        )
    }

    @Test
    fun `verified morale band and special troop preconditions compile as target predicates`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.MORALE_ABOVE,
                100,
            ),
            interpreter.compile(graph.detail(20070705)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.MORALE_AT_OR_BELOW,
                100,
            ),
            interpreter.compile(graph.detail(20076202)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.SPECIAL_TROOP_CATEGORY,
            ),
            interpreter.compile(graph.detail(20029713)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.NOT_SPECIAL_TROOP_CATEGORY,
            ),
            interpreter.compile(graph.detail(20029751)).conditions.single(),
        )
    }

    @Test
    fun `verified attribute and berserk cast conditions preserve source versus target scope`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.StatComparison(
                left = SkillCondition.StatRef(Subject.SOURCE, SkillCondition.CombatStat.ATTACK),
                comparison = Comparison.GREATER_THAN_OR_EQUAL,
                right = SkillCondition.StatRef(Subject.SOURCE, SkillCondition.CombatStat.STRATEGY),
            ),
            interpreter.compile(graph.detail(21329401)).conditions.single(),
        )
        assertEquals(
            SkillCondition.StatComparison(
                left = SkillCondition.StatRef(Subject.SOURCE, SkillCondition.CombatStat.STRATEGY),
                comparison = Comparison.GREATER_THAN,
                right = SkillCondition.StatRef(Subject.SOURCE, SkillCondition.CombatStat.ATTACK),
            ),
            interpreter.compile(graph.detail(21329402)).conditions.single(),
        )
        assertTrue(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.ATTACK_NOT_LOWER_THAN_STRATEGY,
            ) in interpreter.compile(graph.detail(20027304)).conditions,
        )
        assertTrue(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.STRATEGY_GREATER_THAN_ATTACK,
            ) in interpreter.compile(graph.detail(20027305)).conditions,
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.STRATEGY_LOWER_THAN_SOURCE,
            ),
            interpreter.compile(graph.detail(21096804)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_BERSERK,
            ),
            interpreter.compile(graph.detail(21096805)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.SPEED_LOWER_THAN_SOURCE,
            ),
            interpreter.compile(graph.detail(21296113)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.SPEED_NOT_LOWER_THAN_SOURCE,
            ),
            interpreter.compile(graph.detail(21296114)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_CONFUSION_OR_BERSERK,
            ),
            interpreter.compile(graph.detail(20000301)).conditions.single(),
        )
    }

    @Test
    fun `verified effect and morale cast conditions compile as target predicates`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)
        val expected = mapOf(
            20002402 to SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_RECOVERY_BLOCK,
            ),
            20079602 to SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_HEX,
            ),
            21098101 to SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.MORALE_ABOVE,
                100,
            ),
            21098103 to SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.MORALE_AT_OR_BELOW,
                100,
            ),
            21167701 to SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.MORALE_EQUAL,
                100,
            ),
            21167702 to SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.MORALE_BELOW,
                100,
            ),
            21067701 to SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.MORALE_EQUAL,
                100,
            ),
        )

        expected.forEach { (detailId, predicate) ->
            assertTrue(predicate in interpreter.compile(graph.detail(detailId)).conditions)
        }
    }

    @Test
    fun `huangtian recovery conditions compile to its sorcery damage event`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.EventTrigger(BattleTrigger.DAMAGE_AFTER),
            interpreter.compile(graph.detail(20000802)).conditions.single(),
        )
    }

    @Test
    fun `zhongke follow up condition compiles to attack damage event`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.EventTrigger(BattleTrigger.DAMAGE_AFTER),
            interpreter.compile(graph.detail(20026822)).conditions.single(),
        )
    }

    @Test
    fun `tianzi current and legacy hurt thresholds bind to round end`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            listOf(
                SkillCondition.ConfigBranch(false),
                SkillCondition.EventTrigger(BattleTrigger.ROUND_END),
            ),
            interpreter.compile(graph.detail(21027015)).conditions,
        )
        assertEquals(
            listOf(
                SkillCondition.ConfigBranch(true),
                SkillCondition.EventTrigger(BattleTrigger.ROUND_END),
            ),
            interpreter.compile(graph.detail(21027016)).conditions,
        )
    }

    @Test
    fun `lianhuan branches inspect the original target strategy and berserk state`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.StatComparison(
                left = SkillCondition.StatRef(
                    Subject.CURRENT_TARGET,
                    SkillCondition.CombatStat.STRATEGY,
                ),
                comparison = Comparison.LESS_THAN,
                right = SkillCondition.StatRef(
                    Subject.SOURCE,
                    SkillCondition.CombatStat.STRATEGY,
                ),
            ),
            interpreter.compile(graph.detail(20096801)).conditions.single(),
        )
        assertEquals(
            SkillCondition.HasAnyEffect(
                Subject.CURRENT_TARGET,
                effectIds = setOf(503, 703, 903),
                negated = false,
            ),
            interpreter.compile(graph.detail(20096802)).conditions.single(),
        )
    }

    @Test
    fun `lianhuan branch predicates fail closed and match only qualifying original target`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)
        val lowerStrategyTarget = state().copy(stats = STATS.copy(strategy = 90))
        val higherStrategyTarget = state().copy(stats = STATS.copy(strategy = 110))

        assertTrue(
            interpreter.matches(
                graph.detail(20096801),
                trigger(),
                context(view = view(targetState = lowerStrategyTarget)),
            ),
        )
        assertFalse(
            interpreter.matches(
                graph.detail(20096801),
                trigger(),
                context(view = view(targetState = higherStrategyTarget)),
            ),
        )
        assertTrue(
            interpreter.matches(
                graph.detail(20096802),
                trigger(),
                context(view = view(effects = mapOf(TARGET to setOf(703)))),
            ),
        )
        assertFalse(
            interpreter.matches(
                graph.detail(20096802),
                trigger(),
                context(view = view()),
            ),
        )
    }

    @Test
    fun `dingjun fourth round branch binds to owner action before`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)
        val expected = listOf(
            SkillCondition.RoundRange(4, 4),
            SkillCondition.EventTrigger(BattleTrigger.ACTION_BEFORE),
        )

        assertEquals(
            expected,
            interpreter.compile(graph.detail(20029307)).conditions,
        )
        assertEquals(
            expected,
            interpreter.compile(graph.detail(20029311)).conditions,
        )
    }

    @Test
    fun `huangyi successful emergency recovery branch binds to recovery after`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.EventTrigger(BattleTrigger.RECOVERY_AFTER),
            interpreter.compile(graph.detail(20001603)).conditions.single(),
        )
    }

    @Test
    fun `tongchou branches bind to actual hurt after`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        listOf(20100601, 20100602).forEach { detailId ->
            assertEquals(
                SkillCondition.EventTrigger(BattleTrigger.HURT_AFTER),
                interpreter.compile(graph.detail(detailId)).conditions.single(),
            )
        }
    }

    @Test
    fun `fenji threshold branch requires forty percent inside its command chain`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)
        val compiled = interpreter.compile(graph.detail(20096101))

        assertEquals(
            SkillCondition.EffectStrength(
                subject = Subject.SOURCE,
                detailId = 21396101,
                comparison = Comparison.GREATER_THAN_OR_EQUAL,
                value = 40,
            ),
            compiled.conditions.single(),
        )
        assertFalse(
            compiled.matches(
                BattleTrigger.BATTLE_COMMAND,
                context(view = view(effectStrengths = mapOf(SOURCE to mapOf(21396101 to 32)))),
            ),
        )
        assertTrue(
            compiled.matches(
                BattleTrigger.BATTLE_COMMAND,
                context(view = view(effectStrengths = mapOf(SOURCE to mapOf(21396101 to 40)))),
            ),
        )
    }

    @Test
    fun `client balance branch conditions enable current 127 and disable legacy 227`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.ConfigBranch(enabled = true),
            interpreter.compile(graph.detail(20000511)).conditions.single(),
        )
        assertEquals(
            SkillCondition.ConfigBranch(enabled = false),
            interpreter.compile(graph.detail(20000501)).conditions.single(),
        )
        assertTrue(
            interpreter.matches(
                graph.detail(20000511),
                trigger(),
                context(),
            ),
        )
        assertFalse(
            interpreter.matches(
                graph.detail(20000501),
                trigger(),
                context(),
            ),
        )
        assertEquals(
            SkillCondition.ConfigBranch(enabled = true),
            interpreter.compile(graph.detail(21267701)).conditions.single(),
        )
        assertEquals(
            SkillCondition.ConfigBranch(enabled = true),
            interpreter.compile(graph.detail(20068903)).conditions.single(),
        )
        assertEquals(
            SkillCondition.ConfigBranch(enabled = false),
            interpreter.compile(graph.detail(20068904)).conditions.single(),
        )
    }

    @Test
    fun `terrain branches stay disabled while battles use ordinary terrain`() {
        val config = BattleConfigRepository.loadDefault()
        val graph = SkillRuleCatalog.build(
            SkillScope(
                fiveStarInitialSkillIds = config.allSkillIds(),
                learnableSaSkillIds = emptySet(),
            ),
            config,
        )
        val interpreter = SkillConditionInterpreter(graph)

        listOf(
            30013101,
            30013203,
            30013311,
            30013424,
            30013501,
            30014712,
            30013734,
        ).forEach { detailId ->
            val detail = graph.detail(detailId)
            assertEquals(
                SkillCondition.ConfigBranch(enabled = false),
                interpreter.compile(detail).conditions.single(),
                "detail=$detailId",
            )
            assertFalse(
                interpreter.matches(detail, trigger(), context()),
                "detail=$detailId",
            )
        }
    }

    @Test
    fun `current parameter branches enable 230 and recurring 130205 only`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.ConfigBranch(enabled = true),
            interpreter.compile(graph.detail(20018401)).conditions.single(),
        )
        assertEquals(
            SkillCondition.ConfigBranch(enabled = false),
            interpreter.compile(graph.detail(20018405)).conditions.single(),
        )
        assertEquals(
            SkillCondition.ConfigBranch(enabled = true),
            interpreter.compile(graph.detail(20019401)).conditions.single(),
        )
        assertEquals(
            SkillCondition.ConfigBranch(enabled = false),
            interpreter.compile(graph.detail(20019403)).conditions.single(),
        )
        assertEquals(
            SkillCondition.ConfigBranch(enabled = true),
            interpreter.compile(graph.detail(20064303)).conditions.single(),
        )
        assertEquals(
            SkillCondition.ConfigBranch(enabled = true),
            interpreter.compile(graph.detail(21064301)).conditions.single(),
        )
        assertEquals(
            SkillCondition.ConfigBranch(enabled = true),
            interpreter.compile(graph.detail(20071912)).conditions.single(),
        )
        assertEquals(
            SkillCondition.ConfigBranch(enabled = false),
            interpreter.compile(graph.detail(20071922)).conditions.single(),
        )
    }

    @Test
    fun `current recovery duration branch and foreign country target condition are explicit`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.ConfigBranch(enabled = true),
            interpreter.compile(graph.detail(20088414)).conditions.first(),
        )
        assertEquals(
            SkillCondition.ConfigBranch(enabled = false),
            interpreter.compile(graph.detail(20088415)).conditions.first(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.COUNTRY_DIFFERENT_FROM_SOURCE,
            ),
            interpreter.compile(graph.detail(20096402)).conditions.last(),
        )
    }

    @Test
    fun `formation preconditions require complete friendly formation and exact metadata`() {
        val sameCountryDistinctRanges = view(
            sourceState = state(attackRange = 1),
            additionalStates = mapOf(
                ALLY_MIDDLE to state(attackRange = 2),
                ALLY_FRONT to state(attackRange = 3),
            ),
            metadata = mapOf(
                SOURCE to metadata(country = 1, troopType = SkillTroopType.INFANTRY),
                ALLY_MIDDLE to metadata(country = 1, troopType = SkillTroopType.INFANTRY),
                ALLY_FRONT to metadata(country = 1, troopType = SkillTroopType.INFANTRY),
                TARGET to metadata(country = 2, troopType = SkillTroopType.CAVALRY),
            ),
        )
        val distinctCountries = view(
            additionalStates = mapOf(ALLY_MIDDLE to state(), ALLY_FRONT to state()),
            metadata = mapOf(
                SOURCE to metadata(country = 1),
                ALLY_MIDDLE to metadata(country = 2),
                ALLY_FRONT to metadata(country = 3),
                TARGET to metadata(country = 4),
            ),
        )
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertTrue(
            interpreter.matches(
                graph.detail(20078403),
                trigger(),
                context(view = sameCountryDistinctRanges),
            ),
        )
        assertTrue(
            interpreter.matches(
                graph.detail(20078901),
                trigger(),
                context(view = sameCountryDistinctRanges),
            ),
        )
        assertFalse(
            interpreter.matches(
                graph.detail(20078902),
                trigger(),
                context(view = sameCountryDistinctRanges),
            ),
        )
        assertTrue(
            interpreter.matches(
                graph.detail(20024801),
                trigger(),
                context(view = sameCountryDistinctRanges),
            ),
        )
        assertTrue(
            interpreter.matches(
                graph.detail(20096401),
                trigger(),
                context(view = distinctCountries),
            ),
        )
    }

    @Test
    fun `shu country cast condition uses live source metadata`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertTrue(
            interpreter.matches(
                graph.detail(21429803),
                trigger(),
                context(view = view(metadata = mapOf(SOURCE to metadata(country = 3)))),
            ),
        )
        assertFalse(
            interpreter.matches(
                graph.detail(21429803),
                trigger(),
                context(view = view(metadata = mapOf(SOURCE to metadata(country = 1)))),
            ),
        )
    }

    @Test
    fun `marker branches compile as per target predicates`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_DETAIL_MARKER,
                21001701,
            ),
            interpreter.compile(graph.detail(20001705)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.LACKS_DETAIL_MARKER,
                21001701,
            ),
            interpreter.compile(graph.detail(20001706)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_DETAIL_MARKER,
                21002401,
            ),
            interpreter.compile(graph.detail(20002414)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_DETAIL_MARKER,
                20000301,
            ),
            interpreter.compile(graph.detail(20000331)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.LACKS_DETAIL_MARKER,
                20024301,
            ),
            interpreter.compile(graph.detail(20024307)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.LACKS_DETAIL_MARKER,
                20024302,
            ),
            interpreter.compile(graph.detail(21124301)).conditions.single(),
        )
        assertEquals(
            SkillCondition.RuntimeMarker(Subject.SOURCE, 21079601),
            interpreter.compile(graph.detail(20079626)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_DETAIL_MARKER,
                21098402,
            ),
            interpreter.compile(graph.detail(20098424)).conditions.single(),
        )
        assertEquals(
            listOf(21024601, 20024601, 21324601),
            listOf(
                interpreter.compile(graph.detail(21224601)).conditions.single(),
                interpreter.compile(graph.detail(21324612)).conditions.single {
                    it == SkillCondition.TargetPredicate(
                        SkillCondition.TargetPredicate.Kind.HAS_DETAIL_MARKER,
                        20024601,
                    )
                },
                interpreter.compile(graph.detail(21124601)).conditions.single(),
            ).map { (it as SkillCondition.TargetPredicate).value },
        )
        assertEquals(
            listOf(20025101, 21525101),
            listOf(
                interpreter.compile(graph.detail(20025121)).conditions.single(),
                interpreter.compile(graph.detail(21025111)).conditions.single(),
            ).map { (it as SkillCondition.TargetPredicate).value },
        )
        assertEquals(
            listOf(21226402, 21126401),
            listOf(
                interpreter.compile(graph.detail(21426401)).conditions.single(),
                interpreter.compile(graph.detail(21526401)).conditions.single(),
            ).map { (it as SkillCondition.TargetPredicate).value },
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_DETAIL_MARKER,
                21125401,
            ),
            interpreter.compile(graph.detail(21225401)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_DETAIL_MARKER,
                21025601,
            ),
            interpreter.compile(graph.detail(21125601)).conditions.single {
                it == SkillCondition.TargetPredicate(
                    SkillCondition.TargetPredicate.Kind.HAS_DETAIL_MARKER,
                    21025601,
                )
            },
        )
        assertEquals(
            SkillCondition.RuntimeMarker(Subject.SOURCE, 20026811),
            interpreter.compile(graph.detail(21126801)).conditions.single(),
        )
        assertEquals(
            SkillCondition.RuntimeMarker(Subject.SOURCE, 20024411),
            interpreter.compile(graph.detail(21424401)).conditions.first(),
        )
        assertEquals(
            SkillCondition.RuntimeMarker(Subject.SOURCE, 20024421),
            interpreter.compile(graph.detail(21424411)).conditions.first(),
        )
        assertEquals(
            SkillCondition.RuntimeMarker(Subject.SOURCE, 27002401),
            interpreter.compile(graph.detail(21002401)).conditions.single(),
        )
        assertEquals(
            SkillCondition.RuntimeMarker(Subject.SOURCE, 21325201),
            interpreter.compile(graph.detail(21225203)).conditions.single(),
        )
        assertEquals(
            SkillCondition.RuntimeMarker(Subject.SOURCE, 21325201),
            interpreter.compile(graph.detail(21425203)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.LACKS_DETAIL_MARKER,
                21325701,
            ),
            interpreter.compile(graph.detail(21425701)).conditions.single(),
        )
        assertEquals(
            SkillCondition.RuntimeMarker(Subject.SOURCE, 21329301),
            interpreter.compile(graph.detail(21429301)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_DETAIL_MARKER,
                21529301,
            ),
            interpreter.compile(graph.detail(21129316)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.LACKS_DETAIL_MARKER,
                21529301,
            ),
            interpreter.compile(graph.detail(21129317)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.LACKS_DETAIL_MARKER,
                21196502,
            ),
            interpreter.compile(graph.detail(21196502)).conditions.single(),
        )
        listOf(
            21196504 to 21296501,
            21196505 to 21396501,
            21196506 to 21496501,
        ).forEach { (detailId, markerId) ->
            assertEquals(
                SkillCondition.TargetPredicate(
                    SkillCondition.TargetPredicate.Kind.HAS_DETAIL_MARKER,
                    markerId,
                ),
                interpreter.compile(graph.detail(detailId)).conditions.single(),
            )
        }
        listOf(
            21399002 to 21299001,
            21299102 to 21399101,
            21299301 to 21199301,
            22100801 to 22200801,
        ).forEach { (detailId, markerId) ->
            assertEquals(
                SkillCondition.TargetPredicate(
                    SkillCondition.TargetPredicate.Kind.HAS_DETAIL_MARKER,
                    markerId,
                ),
                interpreter.compile(graph.detail(detailId)).conditions.single(),
            )
        }
        listOf(
            21225101 to 20025122,
            21225102 to 21025111,
            21325101 to 20025111,
        ).forEach { (detailId, markerId) ->
            assertEquals(
                SkillCondition.TargetPredicate(
                    SkillCondition.TargetPredicate.Kind.HAS_DETAIL_MARKER,
                    markerId,
                ),
                interpreter.compile(graph.detail(detailId)).conditions.single(),
            )
        }
        assertEquals(
            SkillCondition.RuntimeMarker(Subject.SOURCE, 21384301),
            interpreter.compile(graph.detail(21484301)).conditions.single(),
        )
        listOf(21284301, 21284302, 21284303).forEach { detailId ->
            assertEquals(
                SkillCondition.RuntimeMarker(Subject.SOURCE, 21384301),
                interpreter.compile(graph.detail(detailId)).conditions.single(),
            )
        }
        assertEquals(
            SkillCondition.RuntimeMarker(Subject.SOURCE, 20097913),
            interpreter.compile(graph.detail(20097901)).conditions.single(),
        )
        assertEquals(
            SkillCondition.TargetPredicate(
                SkillCondition.TargetPredicate.Kind.HAS_DETAIL_MARKER,
                20092602,
            ),
            interpreter.compile(graph.detail(20092601)).conditions.single(),
        )
        assertEquals(
            SkillCondition.RuntimeMarker(Subject.SOURCE, 21095712),
            interpreter.compile(graph.detail(21195701)).conditions.single(),
        )
        assertEquals(
            SkillCondition.RuntimeMarker(Subject.SOURCE, 21196601),
            interpreter.compile(graph.detail(21096611)).conditions.single(),
        )
        assertEquals(
            SkillCondition.RuntimeMarker(Subject.SOURCE, 21196601, negated = true),
            interpreter.compile(graph.detail(21196601)).conditions.single(),
        )
        assertEquals(
            SkillCondition.RuntimeMarker(Subject.SOURCE, 20028331),
            interpreter.compile(graph.detail(20028321)).conditions.single(),
        )
    }

    @Test
    fun `unknown diagnostics report every field with skill detail and raw value`() {
        val detail = effectRule(
            detailId = 101,
            castCondition = 991,
            precondition = 992,
            condition = 993,
        )
        val interpreter = SkillConditionInterpreter(graph(rule(1, detail)))

        val error = assertFailsWith<UnsupportedPendingSkillConditionException> {
            interpreter.compile(detail)
        }

        assertTrue(error.message.orEmpty().contains("skill=1 detail=101"))
        assertTrue(error.message.orEmpty().contains("cast_condition=991"))
        assertTrue(error.message.orEmpty().contains("precondition=992"))
        assertTrue(error.message.orEmpty().contains("condition=993"))
        assertEquals(
            setOf(
                SkillConditionCode(1, SkillConditionField.CAST_CONDITION, 991),
                SkillConditionCode(1, SkillConditionField.PRECONDITION, 992),
                SkillConditionCode(1, SkillConditionField.CONDITION, 993),
            ),
            interpreter.unknownCodes(),
        )
    }

    @Test
    fun `round ranges include both boundaries and exclude adjacent rounds`() {
        val rule = effectRule(101, castCondition = 9001)
        val plugin = plugin(
            "test.round",
            SkillConditionCode(1, SkillConditionField.CAST_CONDITION, 9001) to
                listOf(SkillCondition.RoundRange(first = 2, last = 4)),
        )
        val interpreter = SkillConditionInterpreter(graph(rule(1, rule)), listOf(plugin))

        assertFalse(interpreter.matches(rule, BattleTrigger.ROUND_START, context(round = 1)))
        assertTrue(interpreter.matches(rule, BattleTrigger.ROUND_START, context(round = 2)))
        assertTrue(interpreter.matches(rule, BattleTrigger.ROUND_START, context(round = 4)))
        assertFalse(interpreter.matches(rule, BattleTrigger.ROUND_START, context(round = 5)))
    }

    @Test
    fun `troop ratios use exact integer boundaries and the requested subject`() {
        val sourceAtBoundary = state(troops = 50, maxTroops = 100)
        val targetAboveBoundary = state(troops = 51, maxTroops = 100)
        val view = view(sourceAtBoundary, targetAboveBoundary)
        val mappings = listOf(
            9010 to SkillCondition.TroopRatio(
                Subject.SOURCE,
                Comparison.LESS_THAN_OR_EQUAL,
                50,
            ),
            9011 to SkillCondition.TroopRatio(
                Subject.SOURCE,
                Comparison.GREATER_THAN,
                50,
            ),
            9012 to SkillCondition.TroopRatio(
                Subject.CURRENT_TARGET,
                Comparison.GREATER_THAN,
                50,
            ),
            9013 to SkillCondition.TroopRatio(
                Subject.CURRENT_TARGET,
                Comparison.LESS_THAN_OR_EQUAL,
                50,
            ),
        )
        val plugin = plugin(
            id = "test.ratio",
            *mappings.map { (code, condition) ->
                SkillConditionCode(1, SkillConditionField.CAST_CONDITION, code) to
                    listOf(condition)
            }.toTypedArray(),
        )
        val interpreter = SkillConditionInterpreter(
            graph(rule(1, *mappings.mapIndexed { index, (code, _) ->
                effectRule(101 + index, castCondition = code)
            }.toTypedArray())),
            listOf(plugin),
        )

        assertTrue(interpreter.matches(effectRule(101, castCondition = 9010), trigger(), context(view = view)))
        assertFalse(interpreter.matches(effectRule(102, castCondition = 9011), trigger(), context(view = view)))
        assertTrue(interpreter.matches(effectRule(103, castCondition = 9012), trigger(), context(view = view)))
        assertFalse(interpreter.matches(effectRule(104, castCondition = 9013), trigger(), context(view = view)))
    }

    @Test
    fun `hero status and effect requirements honor target and negation`() {
        val sourceState = state(statuses = setOf(BattleStatus.BURN))
        val targetState = state(statuses = setOf(BattleStatus.INSIGHT))
        val view = view(
            sourceState = sourceState,
            targetState = targetState,
            effects = mapOf(TARGET to setOf(305)),
        )
        val conditions = listOf(
            9020 to SkillCondition.HeroId(Subject.SOURCE, SOURCE.heroId.value, negated = false),
            9021 to SkillCondition.HeroId(Subject.CURRENT_TARGET, TARGET.heroId.value, negated = false),
            9022 to SkillCondition.HeroId(Subject.CURRENT_TARGET, SOURCE.heroId.value, negated = true),
            9023 to SkillCondition.HasStatus(Subject.SOURCE, BattleStatus.BURN, negated = false),
            9024 to SkillCondition.HasStatus(Subject.SOURCE, BattleStatus.CONFUSION, negated = true),
            9025 to SkillCondition.HasEffect(Subject.CURRENT_TARGET, 305, negated = false),
            9026 to SkillCondition.HasEffect(Subject.CURRENT_TARGET, 501, negated = true),
        )
        val plugin = plugin(
            id = "test.identity-and-state",
            *conditions.map { (code, condition) ->
                SkillConditionCode(1, SkillConditionField.CONDITION, code) to listOf(condition)
            }.toTypedArray(),
        )
        val interpreter = SkillConditionInterpreter(
            graph(rule(1, *conditions.mapIndexed { index, (code, _) ->
                effectRule(110 + index, condition = code)
            }.toTypedArray())),
            listOf(plugin),
        )

        conditions.forEachIndexed { index, (code, _) ->
            assertTrue(
                interpreter.matches(
                    effectRule(110 + index, condition = code),
                    trigger(),
                    context(view = view),
                ),
                "condition=$code",
            )
        }
    }

    @Test
    fun `missing live target state and effect data fail closed including negation`() {
        val conditions = listOf(
            9030 to SkillCondition.TroopRatio(
                Subject.SOURCE,
                Comparison.LESS_THAN_OR_EQUAL,
                100,
            ),
            9031 to SkillCondition.HeroId(
                Subject.CURRENT_TARGET,
                TARGET.heroId.value,
                negated = true,
            ),
            9032 to SkillCondition.HasStatus(
                Subject.SOURCE,
                BattleStatus.CONFUSION,
                negated = true,
            ),
            9033 to SkillCondition.HasEffect(
                Subject.SOURCE,
                501,
                negated = true,
            ),
        )
        val plugin = plugin(
            id = "test.fail-closed",
            *conditions.map { (code, condition) ->
                SkillConditionCode(1, SkillConditionField.PRECONDITION, code) to
                    listOf(condition)
            }.toTypedArray(),
        )
        val interpreter = SkillConditionInterpreter(
            graph(rule(1, *conditions.mapIndexed { index, (code, _) ->
                effectRule(120 + index, precondition = code)
            }.toTypedArray())),
            listOf(plugin),
        )
        val entryOnly = SkillBattleView.entrySnapshot(request())

        conditions.forEachIndexed { index, (code, _) ->
            assertFalse(
                interpreter.matches(
                    effectRule(120 + index, precondition = code),
                    trigger(),
                    context(view = entryOnly),
                ),
                "condition=$code",
            )
        }
    }

    @Test
    fun `trigger counters isolate hero trigger and skill and support battle event history`() {
        val runtime = SkillRuntimeState()
        runtime.recordSuccessfulExecution(SOURCE, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 1)
        runtime.recordSuccessfulExecution(SOURCE, BattleTrigger.PURSUIT_ATTEMPT, 2)
        runtime.recordSuccessfulExecution(TARGET, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 1)
        runtime.recordSuccessfulExecution(TARGET, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 1)
        runtime.recordBattleTriggerOccurrence(SOURCE, BattleTrigger.NORMAL_ATTACK_AFTER)
        runtime.recordBattleTriggerOccurrence(SOURCE, BattleTrigger.NORMAL_ATTACK_AFTER)
        runtime.recordBattleTriggerOccurrence(SOURCE, BattleTrigger.HURT_AFTER)
        val conditions = listOf(
            9040 to SkillCondition.TriggerCount(
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                comparison = Comparison.EQUAL,
                value = 1,
                subject = Subject.SOURCE,
                skillId = 1,
            ),
            9041 to SkillCondition.TriggerCount(
                trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                comparison = Comparison.GREATER_THAN,
                value = 1,
                subject = Subject.CURRENT_TARGET,
                skillId = 1,
            ),
            9042 to SkillCondition.TriggerCount(
                trigger = BattleTrigger.PURSUIT_ATTEMPT,
                comparison = Comparison.EQUAL,
                value = 1,
            ),
            9043 to SkillCondition.TriggerCount(
                trigger = BattleTrigger.NORMAL_ATTACK_AFTER,
                comparison = Comparison.GREATER_THAN_OR_EQUAL,
                value = 2,
            ),
            9044 to SkillCondition.TriggerCount(
                trigger = BattleTrigger.HURT_AFTER,
                comparison = Comparison.LESS_THAN,
                value = 2,
            ),
        )
        val plugin = plugin(
            id = "test.counts",
            *conditions.map { (code, condition) ->
                SkillConditionCode(1, SkillConditionField.CAST_CONDITION, code) to
                    listOf(condition)
            }.toTypedArray(),
        )
        val interpreter = SkillConditionInterpreter(
            graph(rule(1, *conditions.mapIndexed { index, (code, _) ->
                effectRule(130 + index, castCondition = code)
            }.toTypedArray())),
            listOf(plugin),
        )
        val context = context(runtime = runtime, view = view())

        conditions.forEachIndexed { index, (code, _) ->
            assertTrue(
                interpreter.matches(
                    effectRule(130 + index, castCondition = code),
                    trigger(),
                    context,
                ),
                "condition=$code",
            )
        }
        assertEquals(1, runtime.count(SOURCE, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 1))
        assertEquals(2, runtime.count(SOURCE, BattleTrigger.NORMAL_ATTACK_AFTER))
        assertEquals(1, runtime.count(SOURCE, BattleTrigger.HURT_AFTER))
    }

    @Test
    fun `huangyi recovery threshold detail is restricted to recovery events`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.EventTrigger(BattleTrigger.RECOVERY_AFTER),
            interpreter.compile(graph.detail(20001602)).conditions.single(),
        )
    }

    @Test
    fun `bingzhe threshold detail is restricted to active and pursuit attempts`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.EventTriggerSet(
                setOf(
                    BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                    BattleTrigger.PURSUIT_ATTEMPT,
                ),
            ),
            interpreter.compile(graph.detail(20025301)).conditions.single(),
        )
    }

    @Test
    fun `zhengshi threshold and delayed action details retain event boundaries`() {
        val graph = realGraph()
        val interpreter = SkillConditionInterpreter(graph)

        assertEquals(
            SkillCondition.EventTrigger(BattleTrigger.DAMAGE_AFTER),
            interpreter.compile(graph.detail(20024403)).conditions.single(),
        )
        assertEquals(
            SkillCondition.EventTrigger(BattleTrigger.ACTION_BEFORE),
            interpreter.compile(graph.detail(20024406)).conditions.single(),
        )
    }

    @Test
    fun `compiled conditions are immutable cached conjunctions and matching is pure`() {
        val compileCalls = AtomicInteger()
        val random = CountingRandom()
        val codes = listOf(
            SkillConditionCode(1, SkillConditionField.CAST_CONDITION, 9050),
            SkillConditionCode(1, SkillConditionField.PRECONDITION, 9051),
            SkillConditionCode(1, SkillConditionField.CONDITION, 9052),
        )
        val plugin = object : SpecialSkillPlugin {
            override val id: String = "test.conjunction"
            override val ownedConditions: Set<SkillConditionCode> = codes.toSet()

            override fun compile(
                code: SkillConditionCode,
                rule: SkillEffectRule,
            ): List<SkillCondition> {
                compileCalls.incrementAndGet()
                return when (code.field) {
                    SkillConditionField.CAST_CONDITION ->
                        listOf(SkillCondition.RoundRange(3, 3))
                    SkillConditionField.PRECONDITION ->
                        listOf(SkillCondition.HeroId(Subject.SOURCE, SOURCE.heroId.value, false))
                    SkillConditionField.CONDITION ->
                        listOf(SkillCondition.HasStatus(Subject.SOURCE, BattleStatus.BURN, false))
                }
            }
        }
        val detail = effectRule(101, castCondition = 9050, precondition = 9051, condition = 9052)
        val interpreter = SkillConditionInterpreter(graph(rule(1, detail)), listOf(plugin))
        val context = context(
            random = random,
            view = view(sourceState = state(statuses = setOf(BattleStatus.BURN))),
        )

        val first = interpreter.compile(detail)
        val second = interpreter.compile(detail)

        assertSame(first, second)
        assertEquals(3, compileCalls.get())
        assertEquals(3, first.conditions.size)
        assertTrue(first.matches(trigger(), context))
        assertTrue(first.matches(trigger(), context))
        assertEquals(0, random.calls)
        assertEquals(0, context.runtime.count(SOURCE, trigger(), 1))
        assertFailsWith<UnsupportedOperationException> {
            (first.conditions as MutableList<SkillCondition>).clear()
        }
    }

    @Test
    fun `special plugin may resolve only the exact owned skill field and code`() {
        val graph = realGraph()
        val detail = graph.detail(20000301)
        val key = SkillConditionCode(200003, SkillConditionField.CAST_CONDITION, 4013)
        val plugin = plugin(
            id = "skill.200003",
            key to listOf(SkillCondition.RoundRange(3, 3)),
        )
        val interpreter = SkillConditionInterpreter(graph, listOf(plugin))

        assertTrue(interpreter.matches(detail, trigger(), context(skillId = 200003, round = 3)))
        assertFalse(interpreter.matches(detail, trigger(), context(skillId = 200003, round = 2)))

        val wrongOwner = effectRule(20000401, castCondition = 4013)
        val error = assertFailsWith<UnsupportedPendingSkillConditionException> {
            interpreter.compile(wrongOwner)
        }
        assertTrue(error.message.orEmpty().contains("skill=200004"))
        assertEquals(
            setOf(SkillConditionCode(200004, SkillConditionField.CAST_CONDITION, 4013)),
            interpreter.unknownCodes(),
        )
        assertFailsWith<IllegalArgumentException> {
            SkillConditionInterpreter(
                graph,
                listOf(
                    plugin(
                        id = "skill.someone-else",
                        key to listOf(SkillCondition.RoundRange(3, 3)),
                    ),
                ),
            )
        }
    }

    @Test
    fun `unresolved plugin requirement throws in strict mode and becomes safe diagnostic`() {
        val detail = effectRule(20000301, castCondition = 420000802)
        val graph = graph(rule(200003, detail))
        val context = context(skillId = 200003)
        val registry = BattleEffectRegistry.strict(graph).registerMetaEffects()

        val strictError = assertFailsWith<UnsupportedPendingSkillConditionException> {
            SkillRuleInterpreter(graph, registry).execute(
                200003,
                BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                context,
            )
        }
        assertTrue(strictError.message.orEmpty().contains("cast_condition=420000802"))

        val safeContext = context(skillId = 200003)
        val safeResult = SkillRuleInterpreter.safe(
            graph = graph,
            registry = registry,
            diagnosticSink = {},
        ).execute(
            200003,
            BattleTrigger.ACTIVE_SKILL_ATTEMPT,
            safeContext,
        )

        assertEquals("UNSUPPORTED_CONDITION", safeResult.diagnostics.single().code)
        assertTrue(safeResult.diagnostics.single().reason.contains("cast_condition=420000802"))
        assertTrue(safeResult.stateChanges.isEmpty())
    }

    @Test
    fun `successful execution count includes the current invocation before detail matching`() {
        val detail = effectRule(101, effectId = 77, castCondition = 9060)
        val graph = graph(rule(1, detail))
        val plugin = plugin(
            id = "test.current-execution",
            SkillConditionCode(1, SkillConditionField.CAST_CONDITION, 9060) to
                listOf(
                    SkillCondition.TriggerCount(
                        trigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT,
                        comparison = Comparison.EQUAL,
                        value = 1,
                        skillId = 1,
                    ),
                ),
        )
        val interpreter = SkillRuleInterpreter(
            graph = graph,
            registry = BattleEffectRegistry.strict(graph).registerMetaEffects(),
            conditionInterpreter = SkillConditionInterpreter(graph, listOf(plugin)),
        )
        val context = context()

        val first = interpreter.execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)
        val second = interpreter.execute(1, BattleTrigger.ACTIVE_SKILL_ATTEMPT, context)

        assertEquals(1, first.stateChanges.filterIsInstance<MarkerEffectChange>().size)
        assertEquals(0, second.stateChanges.filterIsInstance<MarkerEffectChange>().size)
        assertEquals(2, context.runtime.count(SOURCE, BattleTrigger.ACTIVE_SKILL_ATTEMPT, 1))
    }

    @Test
    fun `compiled snapshots do not alias plugin condition lists`() {
        val mutable = mutableListOf<SkillCondition>(SkillCondition.RoundRange(3, 3))
        val key = SkillConditionCode(1, SkillConditionField.CAST_CONDITION, 9070)
        val plugin = object : SpecialSkillPlugin {
            override val id: String = "test.snapshot"
            override val ownedConditions: Set<SkillConditionCode> = setOf(key)

            override fun compile(
                code: SkillConditionCode,
                rule: SkillEffectRule,
            ): List<SkillCondition> = mutable
        }
        val detail = effectRule(101, castCondition = 9070)
        val interpreter = SkillConditionInterpreter(graph(rule(1, detail)), listOf(plugin))

        val compiled = interpreter.compile(detail)
        mutable.clear()

        assertEquals(1, compiled.conditions.size)
        assertNotSame(mutable, compiled.conditions)
        assertTrue(compiled.matches(trigger(), context(round = 3)))
    }

    private fun realGraph(): SkillRuleGraph =
        SkillRuleCatalog.build(
            SkillScopeCatalog.loadDefault(),
            BattleConfigRepository.loadDefault(),
        )

    private fun loadExpectedPluginOwners(): Set<SpecialConditionRequirement> {
        val resource = checkNotNull(
            javaClass.getResourceAsStream("/skill-condition-plugin-owners.csv"),
        ) { "Missing independent skill-condition-plugin-owners.csv fixture" }
        return resource.bufferedReader().useLines { lines ->
            lines.drop(1).filter(String::isNotBlank).map { line ->
                val columns = line.split(',')
                check(columns.size == 4) { "Invalid owner fixture row: $line" }
                SpecialConditionRequirement(
                    code = SkillConditionCode(
                        skillId = columns[0].toInt(),
                        field = SkillConditionField.valueOf(columns[1]),
                        value = columns[2].toInt(),
                    ),
                    owner = columns[3],
                )
            }.toSet()
        }
    }

    private fun SkillRuleGraph.detail(detailId: Int): SkillEffectRule =
        details.single { it.detailId == detailId }

    private fun plugin(
        id: String,
        vararg mappings: Pair<SkillConditionCode, List<SkillCondition>>,
    ): SpecialSkillPlugin {
        val conditions = mappings.toMap()
        return object : SpecialSkillPlugin {
            override val id: String = id
            override val ownedConditions: Set<SkillConditionCode> = conditions.keys

            override fun compile(
                code: SkillConditionCode,
                rule: SkillEffectRule,
            ): List<SkillCondition> = conditions.getValue(code)
        }
    }

    private fun graph(vararg rules: SkillRule): SkillRuleGraph =
        SkillRuleGraph(
            rules = rules.associateBy(SkillRule::skillId),
            effectIds = rules.flatMap { it.details }.mapTo(linkedSetOf()) { it.effectId },
            rootSkillIds = rules.mapTo(linkedSetOf()) { it.skillId },
        )

    private fun rule(
        skillId: Int,
        vararg details: SkillEffectRule,
        kind: SkillKind = SkillKind.ACTIVE,
    ): SkillRule =
        SkillRule(
            skillId = skillId,
            kind = kind,
            rawSkillType = when (kind) {
                SkillKind.PASSIVE -> 1
                SkillKind.COMMAND -> 2
                SkillKind.ACTIVE -> 3
                SkillKind.PURSUIT -> 4
                SkillKind.UNKNOWN -> 99
            },
            probability = 100,
            prepareRounds = 0,
            hitRange = 5,
            details = details.toList(),
        )

    private fun effectRule(
        detailId: Int,
        effectId: Int = 0,
        castCondition: Int = 0,
        precondition: Int = 0,
        condition: Int = 0,
    ): SkillEffectRule =
        SkillEffectRule(
            detailId = detailId,
            effectId = effectId,
            childSkillIds = emptySet(),
            raw = SkillDetailConfig(
                detailId = detailId,
                effectId = effectId,
                attackType = 0,
                targetType = 0,
                selectType = 0,
                intelParam = 0,
                constantParam = 0,
                probabilityInit = 100,
                probabilityMax = 100,
                castCondition = castCondition,
                precondition = precondition,
                condition = condition,
                attackMax = 1,
                availableRounds = 0,
                effectName = "condition-fixture",
            ),
            skillHitRange = 5,
            skillKind = SkillKind.ACTIVE,
            rawSkillType = 3,
        )

    private fun trigger(): BattleTrigger = BattleTrigger.ACTIVE_SKILL_ATTEMPT

    private fun context(
        skillId: Int = 1,
        round: Int = 3,
        runtime: SkillRuntimeState = SkillRuntimeState(),
        random: BattleRandom = FixedBattleRandom(0),
        view: SkillBattleView = view(),
        request: BattleRequest = request(),
    ): SkillBattleContext =
        SkillBattleContext(
            request = request,
            runtime = runtime,
            random = random,
            round = round,
            source = SOURCE,
            rootSkillId = skillId,
            currentSkillId = skillId,
            trigger = trigger(),
            battleView = view,
        )

    private fun request(equipmentId: Int? = null): BattleRequest =
        BattleRequest(
            attacker = BattleTeam(
                listOf(
                    BattleHero(
                        id = SOURCE.heroId,
                        position = SOURCE.position,
                        stats = STATS,
                        troops = 100,
                        maxTroops = 100,
                        equipmentIds = listOfNotNull(equipmentId),
                    ),
                ),
            ),
            defender = BattleTeam(
                listOf(
                    BattleHero(
                        id = TARGET.heroId,
                        position = TARGET.position,
                        stats = STATS,
                        troops = 100,
                        maxTroops = 100,
                    ),
                ),
            ),
        )

    private fun state(
        troops: Int = 100,
        maxTroops: Int = 100,
        statuses: Set<BattleStatus> = emptySet(),
        attackRange: Int = 5,
    ): SkillBattleHeroState =
        SkillBattleHeroState(
            stats = STATS.copy(hitRange = attackRange),
            troops = troops,
            maxTroops = maxTroops,
            statuses = statuses,
            morale = 100,
            attackRange = attackRange,
        )

    private fun view(
        sourceState: SkillBattleHeroState = state(),
        targetState: SkillBattleHeroState = state(),
        effects: Map<BattleHeroRef, Set<Int>> = emptyMap(),
        effectStrengths: Map<BattleHeroRef, Map<Int, Int>> = emptyMap(),
        additionalStates: Map<BattleHeroRef, SkillBattleHeroState> = emptyMap(),
        metadata: Map<BattleHeroRef, SkillBattleHeroMetadata> = emptyMap(),
    ): SkillBattleView =
        ConditionBattleView(
            states = mapOf(SOURCE to sourceState, TARGET to targetState) + additionalStates,
            currentTargets = mapOf(SOURCE to TARGET),
            effects = effects,
            effectStrengths = effectStrengths,
            metadata = metadata,
        )

    private fun metadata(
        country: Int,
        troopType: SkillTroopType = SkillTroopType.INFANTRY,
    ) = SkillBattleHeroMetadata(
        gender = SkillHeroGender.UNKNOWN,
        troopType = troopType,
        country = country,
    )

    private class ConditionBattleView(
        private val states: Map<BattleHeroRef, SkillBattleHeroState>,
        private val currentTargets: Map<BattleHeroRef, BattleHeroRef>,
        private val effects: Map<BattleHeroRef, Set<Int>>,
        private val effectStrengths: Map<BattleHeroRef, Map<Int, Int>>,
        private val metadata: Map<BattleHeroRef, SkillBattleHeroMetadata>,
    ) : SkillBattleView {
        override val capabilities: Set<SkillBattleViewCapability> = buildSet {
            add(SkillBattleViewCapability.HERO_ROSTER)
            add(SkillBattleViewCapability.ENTRY_STATE)
            add(SkillBattleViewCapability.LIVE_STATE)
            add(SkillBattleViewCapability.TARGET_HISTORY)
            add(SkillBattleViewCapability.ACTIVE_EFFECTS)
            add(SkillBattleViewCapability.NORMAL_ATTACK_RANGE)
            if (metadata.isNotEmpty()) add(SkillBattleViewCapability.HERO_METADATA)
        }

        override fun heroes(): List<BattleHeroRef> = states.keys.toList()

        override fun entryState(ref: BattleHeroRef): SkillBattleHeroState? = states[ref]

        override fun state(ref: BattleHeroRef): SkillBattleHeroState? = states[ref]

        override fun metadata(ref: BattleHeroRef): SkillBattleHeroMetadata? = metadata[ref]

        override fun accumulatedDamageDealt(ref: BattleHeroRef): Int = 0

        override fun currentMorale(ref: BattleHeroRef): Int? = states[ref]?.morale

        override fun currentAttackRange(ref: BattleHeroRef): Int? = states[ref]?.attackRange

        override fun linkedTarget(source: BattleHeroRef): BattleHeroRef? = null

        override fun currentTarget(source: BattleHeroRef): BattleHeroRef? = currentTargets[source]

        override fun previousTarget(source: BattleHeroRef): BattleHeroRef? = null

        override fun matchesStateFilter(
            filter: SkillTargetStateFilter,
            source: BattleHeroRef,
            target: BattleHeroRef,
        ): Boolean = false

        override fun activeEffectIds(ref: BattleHeroRef): Set<Int> = effects[ref].orEmpty()

        override fun activeEffectStrength(ref: BattleHeroRef, detailId: Int): Int =
            effectStrengths[ref]?.get(detailId) ?: 0
    }

    private class CountingRandom : BattleRandom {
        var calls: Int = 0

        override fun nextInt(bound: Int): Int {
            calls += 1
            return 0
        }
    }

    private companion object {
        val SOURCE = BattleHeroRef(Side.ATTACKER, 0, BattleHeroId(100003))
        val ALLY_MIDDLE = BattleHeroRef(Side.ATTACKER, 1, BattleHeroId(100011))
        val ALLY_FRONT = BattleHeroRef(Side.ATTACKER, 2, BattleHeroId(100012))
        val TARGET = BattleHeroRef(Side.DEFENDER, 0, BattleHeroId(100010))
        val STATS = BattleStats(100, 100, 100, 100, 100, 5)

        val EXPECTED_CAST_CONDITIONS = setOf(
            104, 203, 205, 207, 303, 400, 401, 402, 403, 404, 405, 406, 500,
            1103, 1123, 2313, 2414, 2434, 3103, 3123, 4000, 4003, 4013, 5300,
            6207, 6306, 7001, 11079, 11099, 12080, 12100, 14100, 121002401,
            121079601, 121196601, 121329301, 121384301, 127000501, 127000601,
            127001101, 127001701, 127001901, 127002201, 127002301, 127007201,
            127008001, 127027001, 127065501, 127067701, 127068001, 127068101,
            127068901, 127072301, 127073201, 127075601, 127076401, 127077101,
            127082801, 127084801, 127084901, 127091501, 127092701, 127093901,
            127094701, 130001912, 130005101, 130005205, 130005301, 220028331,
            220096801, 220096802, 220097913, 221095712, 221384301, 227000501,
            227002201, 227002301, 227003301, 227007201, 227008001, 227027001,
            227065501, 227068001, 227068101, 227068901, 227072301, 227073201,
            227075601, 227077101, 227082801, 227084801, 227084901, 227091501,
            227092701, 227094701, 230001912, 230005101, 230005301, 320000301,
            320024411, 320024421, 320024601, 320025101, 320025111, 320025122,
            320026412, 320026811, 320092602, 321001701, 321024601, 321025111,
            321025601, 321098402, 321125401, 321126401, 321199301, 321226402,
            321296501, 321299001, 321324601, 321325201, 321396501, 321399101,
            321496501, 321525101, 321529301, 322200801, 327002401, 420000802,
            420024301, 420024302, 420026421, 420026822, 421001701, 421196502,
            421196601, 421325701, 421529301,
        )

        val EXPECTED_PRECONDITIONS = setOf(
            -6000, -80, -70, -18, -14, -2, 1, 2, 13, 14, 16, 18, 19, 43, 70,
            80, 500, 2099, 3100, 4040, 6000, 100003, 100010, 100479, 100661,
        )

        val EXPECTED_CONDITIONS = setOf(
            1030, 1050, 1060, 1070, 1080, 1090, 2050, 2060, 5001, 5003, 5005,
            5006, 5007, 5008, 5009, 15002, 15003, 17000, 18306, 20160, 21110,
            24001, 25002, 25003, 25011, 26636, 29001, 29004, 30000, 32002,
            32011, 33003, 33004, 33005,
        )
    }
}

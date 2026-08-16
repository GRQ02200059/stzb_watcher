package com.stzb.battle.core

import java.nio.file.Path
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficialPreparationReportDiffTest {
    private val officialReport =
        Path.of("src/test/resources/assent/cfg/paper/11/cap_20260312014510506_0000000b_zlib.json")
    private val levelFourFlatStatReport =
        Path.of("src/test/resources/assent/cfg/paper/6231/cap_20260313025235816_00001857_zlib.json")
    private val fourAttributeSurfaceReport =
        Path.of("src/test/resources/assent/cfg/paper/11/cap_20260311223905520_0000000b_zlib.json")
    private val percentAttributeSurfaceReport =
        Path.of("src/test/resources/assent/cfg/paper/11/cap_20260311222842345_0000000b_zlib.json")
    private val siegeAttributeSurfaceReport =
        Path.of("src/test/resources/assent/cfg/paper/6231/cap_20260312074501252_00001857_zlib.json")
    private val firstRoundDefenderWinReport =
        Path.of("src/test/resources/assent/cfg/paper/6231/cap_20260311223648438_00001857_zlib.json")

    @Test
    fun `paper command damage modifiers preserve exact configured values`() {
        val config = BattleConfigRepository.loadDefault()
        val officialActions = OfficialReportFixture.read(firstRoundDefenderWinReport)
        val officialPreparation = OfficialReportFixture.preparation(officialActions)
        val result = BattleEngine.resolve(
            OfficialReportFixture.reconstructBattleRequest(officialActions, config),
            config,
            FixedBattleRandom(0),
        )
        val generatedPreparation = OfficialReportFixture.preparation(
            OfficialReportFixture.parseText(ClientReportTextEncoder.encode(result)),
        )
        fun relevant(actions: List<OfficialReportFixture.Action>) =
            OfficialReportFixture.jaTuples(actions)
                .filter { it.sourceId in setOf(200198, 200204) }
                .map { Triple(it.sourceId, it.effectId, it.amount) }
                .sortedWith(
                    compareBy(
                        Triple<Int, Int, Int>::first,
                        Triple<Int, Int, Int>::second,
                        Triple<Int, Int, Int>::third,
                    ),
                )

        assertEquals(
            relevant(officialPreparation),
            relevant(generatedPreparation),
            result.events.filterIsInstance<BattleEvent.StatChanged>()
                .filter { it.round == 0 && it.skillId == 214989 }
                .joinToString(separator = "\n"),
        )
    }

    @Test
    fun `all reviewed paper surface stages preserve exact source order envelopes and values`() {
        val config = BattleConfigRepository.loadDefault()
        val reports = listOf(Path.of("src/test/resources/assent/cfg/paper/11"), Path.of("src/test/resources/assent/cfg/paper/6231"))
            .flatMap { directory ->
                Files.list(directory).use { paths ->
                    paths.filter { it.fileName.toString().endsWith(".json") }.toList()
                }
            }
            .filter(OfficialReportFixture::hasReport)
            .sortedBy(Path::toString)
        var compared = 0
        reports.forEach { report ->
            val officialActions = OfficialReportFixture.read(report)
            val officialStage = surfaceStage(OfficialReportFixture.preparation(officialActions))
            if (officialStage.none { it.id == ClientBattleTextReplayProtocol.SURFACE_EFFECT_SOURCE }) {
                return@forEach
            }
            val officialPreparation = OfficialReportFixture.preparation(officialActions)
            val result = BattleEngine.resolve(
                OfficialReportFixture.reconstructBattleRequest(officialActions, config),
                config,
                FixedBattleRandom(0),
                OfficialReportFixture.targetDecisions(officialPreparation),
            )
            val generatedStage = surfaceStage(
                OfficialReportFixture.preparation(
                    OfficialReportFixture.parseText(ClientReportTextEncoder.encode(result)),
                ),
            )

            assertEquals(
                officialStage.map(OfficialReportFixture.Action::raw),
                generatedStage.map(OfficialReportFixture.Action::raw),
                report.toString(),
            )
            compared++
        }
        assertEquals(23, compared, "reviewed paper fixture count changed")
    }

    @Test
    fun `four attribute hero feature preserves the official surface stream`() {
        assertSurfaceActionsEqual(
            report = fourAttributeSurfaceReport,
            sourceId = 286314,
            actionIds = setOf("0v", "0w", "0x", "0y").mapTo(mutableSetOf()) { it.toInt(36) },
            compareEnvelope = true,
        )
    }

    @Test
    fun `percent attribute hero feature preserves official strength delta and final value`() {
        assertSurfaceActionsEqual(
            report = percentAttributeSurfaceReport,
            sourceId = 281006,
            actionIds = setOf("19".toInt(36)),
        )
    }

    @Test
    fun `siege attribute hero feature uses the official flat siege action`() {
        assertSurfaceActionsEqual(
            report = siegeAttributeSurfaceReport,
            sourceId = 281018,
            actionIds = setOf("0z".toInt(36)),
        )
    }

    @Test
    fun `level four flat attributes retain official decimal interpolation`() {
        val config = BattleConfigRepository.loadDefault()
        val officialActions = OfficialReportFixture.read(levelFourFlatStatReport)
        val preparation = OfficialReportFixture.preparation(officialActions)
        val result = BattleEngine.resolve(
            OfficialReportFixture.reconstructBattleRequest(officialActions, config),
            config,
            FixedBattleRandom(0),
            OfficialReportFixture.targetDecisions(preparation),
        )
        val changes = result.events
            .filterIsInstance<BattleEvent.StatChanged>()
            .filter { it.round == 0 && it.skillId == 200689 }
            .associateBy(BattleEvent.StatChanged::effectId)

        assertEquals(66.67, changes.getValue(102).deltaExact, 0.001)
        assertEquals(16.67, changes.getValue(103).deltaExact, 0.001)
        val generated = OfficialReportFixture.preparation(
            OfficialReportFixture.parseText(ClientReportTextEncoder.encode(result)),
        )
        assertEquals(
            listOf("0w2,200689,2,66.7,211.8", "0x2,200689,2,16.7,270.9"),
            generated
                .filter { it.params.getOrNull(1) == "200689" && it.id in setOf("0w".toInt(36), "0x".toInt(36)) }
                .map(OfficialReportFixture.Action::raw),
        )
    }

    @Test
    fun `representative official preparation report remains structurally compatible`() {
        val config = BattleConfigRepository.loadDefault()
        val officialActions = OfficialReportFixture.read(officialReport)
        val officialPreparation = OfficialReportFixture.preparation(officialActions)
        val request = OfficialReportFixture.reconstructBattleRequest(officialActions, config)

        val firstResult = BattleEngine.resolve(
            request,
            config,
            FixedBattleRandom(0),
            OfficialReportFixture.targetDecisions(officialPreparation),
        )
        val firstText = ClientReportTextEncoder.encode(firstResult)
        val secondText = ClientReportTextEncoder.encode(
            BattleEngine.resolve(
                request,
                config,
                FixedBattleRandom(0),
                OfficialReportFixture.targetDecisions(officialPreparation),
            ),
        )
        assertEquals(firstText, secondText, "fixed-random report projection must be deterministic")
        val appliedEffectIds = firstResult.events
            .filterIsInstance<BattleEvent.StatusApplied>()
            .filter { it.round == 0 }
            .mapNotNull(BattleEvent.StatusApplied::effectId)
            .toSet()
        assertTrue(702 in appliedEffectIds, "hesitation must retain configured effect 702")
        assertTrue(752 in appliedEffectIds, "disarm must retain configured effect 752")
        val verifiedFlatStats = firstResult.events
            .filterIsInstance<BattleEvent.StatChanged>()
            .filter { it.round == 0 && it.skillId in setOf(200233, 200689) }
            .map { Triple(it.skillId, it.effectId, it.delta) }
            .toSet()
        assertTrue(Triple(200233, 101, 30) in verifiedFlatStats, "actual=$verifiedFlatStats")
        assertTrue(Triple(200689, 102, 100) in verifiedFlatStats, "actual=$verifiedFlatStats")
        assertTrue(Triple(200689, 103, 25) in verifiedFlatStats, "actual=$verifiedFlatStats")
        val whiteClothesDamage = firstResult.events
            .filterIsInstance<BattleEvent.SkillDamage>()
            .filter { it.skillId == 200648 && it.effectId == 302 }
        assertTrue(
            whiteClothesDamage.none { it.round == 0 },
            "white clothes delayed damage must not execute during preparation",
        )
        assertTrue(
            whiteClothesDamage.any { it.round == 3 },
            "white clothes delayed damage must execute after its two-round delay: " +
                "actual=${whiteClothesDamage.map { it to firstResult.events.indexOf(it) }} " +
                "roundStarts=${firstResult.events.mapIndexedNotNull { index, event ->
                    (event as? BattleEvent.RoundStart)?.let { it.round to index }
                }} preparation302=${firstResult.events
                    .takeWhile { it !is BattleEvent.RoundStart }
                    .filter {
                        it is BattleEvent.StatusApplied &&
                            it.skillId == 200648 &&
                            it.effectId == 302
                    }} outcome=${firstResult.outcome}",
        )

        val generatedPreparation =
            OfficialReportFixture.preparation(OfficialReportFixture.parseText(firstText))
        val officialJa = OfficialReportFixture.jaTuples(officialPreparation)
        val generatedJa = OfficialReportFixture.jaTuples(generatedPreparation)
        val repeatedGeneratedJa = OfficialReportFixture.jaTuples(
            OfficialReportFixture.preparation(OfficialReportFixture.parseText(secondText)),
        )
        val battleOnlyWrappers = setOf(
            ClientBattleTextReplayProtocol.SKILL_BEGIN,
            ClientBattleTextReplayProtocol.SKILL_END,
            ClientBattleTextReplayProtocol.SKILL_CAST,
            ClientBattleTextReplayProtocol.SKILL_DAMAGE,
        )

        val officialTroopSources = officialPreparation
            .filter { it.id == ClientBattleTextReplayProtocol.TROOP_EFFECT_SOURCE }
            .map { it.params.take(2) }
        val generatedTroopSources = generatedPreparation
            .filter { it.id == ClientBattleTextReplayProtocol.TROOP_EFFECT_SOURCE }
            .map { it.params.take(2) }

        assertEquals(21, officialTroopSources.size, "reviewed paper fixture 7x count changed")
        assertEquals(
            officialTroopSources,
            generatedTroopSources,
            "generated report must preserve the exact paper troop-source stream",
        )
        assertEquals(
            officialPreparation
                .filter { it.id == ClientBattleTextReplayProtocol.SURFACE_EFFECT_SOURCE }
                .map { it.params.take(2) },
            generatedPreparation
                .filter { it.id == ClientBattleTextReplayProtocol.SURFACE_EFFECT_SOURCE }
                .map { it.params.take(2) },
            "generated report must preserve the exact paper surface-skill source stream",
        )
        assertEquals(
            listOf("172,296133,2,1,3"),
            generatedPreparation
                .filter { it.id == "17".toInt(36) && it.params.getOrNull(1) == "296133" }
                .map { it.raw },
            "troop attack-range reduction must retain the official 17 action",
        )
        listOf(702, 752).forEach { effectId ->
            assertEquals(
                officialPreparation
                    .filter { it.id == "0s".toInt(36) && it.params.getOrNull(1) == effectId.toString() }
                    .map { it.params.first() },
                generatedPreparation
                    .filter { it.id == "0s".toInt(36) && it.params.getOrNull(1) == effectId.toString() }
                    .map { it.params.first() },
                "generated preparation must preserve official targets for effect $effectId",
            )
        }
        assertEquals(
            officialPreparation
                .filter { it.id == "7a".toInt(36) && it.params.getOrNull(1) == "200220" }
                .map { it.params[2] },
            generatedPreparation
                .filter { it.id == "7a".toInt(36) && it.params.getOrNull(1) == "200220" }
                .map { it.params[2] },
            "counter-strategy damage reduction must preserve official targets",
        )
        assertEquals(
            officialPreparation
                .filter { it.id == "10".toInt(36) && it.params.getOrNull(1) == "200023" }
                .map { it.raw },
            generatedPreparation
                .filter { it.id == "10".toInt(36) && it.params.getOrNull(1) == "200023" }
                .map { it.raw },
            "Wei Wu active-skill range increase must preserve official values",
        )
        assertEquals(
            officialPreparation
                .filter { it.id == "8c".toInt(36) && it.params.getOrNull(1) == "221006" }
                .map(OfficialReportFixture.Action::raw),
            generatedPreparation
                .filter { it.id == "8c".toInt(36) && it.params.getOrNull(1) == "221006" }
                .map(OfficialReportFixture.Action::raw),
            "Tongchou passive registration must preserve all allied positions",
        )
        assertEquals(
            officialPreparation
                .filter { it.id == "8c".toInt(36) }
                .map(OfficialReportFixture.Action::raw),
            generatedPreparation
                .filter { it.id == "8c".toInt(36) }
                .map(OfficialReportFixture.Action::raw),
            "all passive and equipment child-skill registrations must preserve the paper stream",
        )
        assertTrue(
            generatedPreparation.none { it.id in battleOnlyWrappers },
            "round-zero projection leaked battle-only wrappers",
        )
        assertTrue(
            generatedPreparation.any { it.id == ClientBattleTextReplayProtocol.PREPARATION_EFFECT_BEGIN },
            "generated preparation is missing the 66 effect envelope",
        )
        assertTrue(
            generatedPreparation.any { it.id == ClientBattleTextReplayProtocol.PREPARATION_EFFECT_END },
            "generated preparation is missing the 67 effect envelope",
        )
        assertTrue(
            generatedPreparation.any { it.id == ClientBattleTextReplayProtocol.PREPARATION_EFFECT_BOUNDARY },
            "generated preparation is missing the 61 effect boundary",
        )
        assertTrue(
            generatedPreparation.any { it.id == "8x".toInt(36) },
            "equipment feature projection is missing the official 8x action",
        )
        assertEquals(
            officialPreparation.filter { it.id == "9c".toInt(36) }.map(OfficialReportFixture.Action::raw),
            generatedPreparation.filter { it.id == "9c".toInt(36) }.map(OfficialReportFixture.Action::raw),
            "equipment feature level action must preserve the official fields",
        )
        assertTrue(
            generatedPreparation.none { it.id == "0t".toInt(36) },
            "successful applied statuses must not be projected as 0t",
        )
        assertTrue(
            generatedPreparation.any { it.params.lastOrNull() == "702" },
            "hesitation must retain configured effect 702",
        )
        assertTrue(
            generatedPreparation.any { it.params.lastOrNull() == "752" },
            "disarm must retain configured effect 752",
        )
        assertEquals(
            emptySet(),
            generatedPreparation.map { it.id }.toSet() -
                officialPreparation.map { it.id }.toSet(),
            "generated preparation must not invent action families absent from the official report",
        )
        val reviewedMissingActionFamilies = setOf(
            "0k", "1y", "22", "2u", "33", "3r", "44", "45", "6m", "8p", "8q", "hc", "hd",
        ).mapTo(mutableSetOf()) { it.toInt(36) }
        assertEquals(
            reviewedMissingActionFamilies,
            officialPreparation.map { it.id }.toSet() -
                generatedPreparation.map { it.id }.toSet(),
            "reviewed missing-action baseline changed; classify and implement new differences",
        )
        assertEquals(
            emptyMap(),
            OfficialReportFixture.commonWidthMismatches(
                official = officialPreparation,
                generated = generatedPreparation,
            ),
            "generated common action families use parameter widths absent from the official report",
        )
        assertEquals(25, officialJa.size, "reviewed paper fixture ja count changed")
        assertEquals(
            25,
            generatedJa.size,
            "preparation ja exact parity regressed; missing=${officialJa - generatedJa.toSet()}; " +
                "extra=${generatedJa - officialJa.toSet()}",
        )
        assertEquals(generatedJa, repeatedGeneratedJa, "generated ja tuples must be deterministic")
        val tupleOrdering = compareBy<OfficialReportFixture.JaTuple>(
            { it.sourcePosition },
            { it.sourceId },
            { it.targetPosition },
            { it.effectId },
            { it.amount },
        )
        assertEquals(
            officialJa.sortedWith(tupleOrdering),
            generatedJa.sortedWith(tupleOrdering),
            "generated preparation ja tuples must exactly match the paper multiset",
        )
        assertTrue(generatedJa.none { it.sourceId == 223006 })
        assertTrue(
            generatedJa.containsAll(
                listOf(
                    OfficialReportFixture.JaTuple(1, 296132, 1, 531, 8),
                    OfficialReportFixture.JaTuple(1, 296132, 1, 533, 8),
                    OfficialReportFixture.JaTuple(6, 296232, 6, 531, 8),
                    OfficialReportFixture.JaTuple(6, 296232, 6, 533, 8),
                ),
            ),
            "generated report is missing verified troop-feature ja tuples",
        )
    }

    private fun assertSurfaceActionsEqual(
        report: Path,
        sourceId: Int,
        actionIds: Set<Int>,
        compareEnvelope: Boolean = false,
    ) {
        val config = BattleConfigRepository.loadDefault()
        val officialActions = OfficialReportFixture.read(report)
        val officialPreparation = OfficialReportFixture.preparation(officialActions)
        val result = BattleEngine.resolve(
            OfficialReportFixture.reconstructBattleRequest(officialActions, config),
            config,
            FixedBattleRandom(0),
            OfficialReportFixture.targetDecisions(officialPreparation),
        )
        val generatedPreparation = OfficialReportFixture.preparation(
            OfficialReportFixture.parseText(ClientReportTextEncoder.encode(result)),
        )
        fun relevant(actions: List<OfficialReportFixture.Action>): List<String> =
            actions.filter {
                it.id in actionIds && it.params.getOrNull(1) == sourceId.toString()
            }.map(OfficialReportFixture.Action::raw)

        assertTrue(relevant(officialPreparation).isNotEmpty(), "paper has no actions for $sourceId")
        assertEquals(
            relevant(officialPreparation),
            relevant(generatedPreparation),
            "generated source actions=" + generatedPreparation
                .filter { it.params.getOrNull(1) == sourceId.toString() }
                .map(OfficialReportFixture.Action::raw),
        )
        if (compareEnvelope) {
            fun sourceBlock(actions: List<OfficialReportFixture.Action>): List<String> {
                val start = actions.indexOfFirst {
                    it.id == ClientBattleTextReplayProtocol.SURFACE_EFFECT_SOURCE &&
                        it.params.getOrNull(1) == sourceId.toString()
                }
                assertTrue(start >= 0, "missing surface source $sourceId")
                val end = (start until actions.size).first {
                    actions[it].id == ClientBattleTextReplayProtocol.PREPARATION_EFFECT_BOUNDARY
                }
                return actions.subList(start, end + 1).map(OfficialReportFixture.Action::raw)
            }
            assertEquals(sourceBlock(officialPreparation), sourceBlock(generatedPreparation))
        }
    }

    private fun surfaceStage(
        actions: List<OfficialReportFixture.Action>,
    ): List<OfficialReportFixture.Action> {
        val start = actions.indexOfFirst {
            it.id == ClientBattleTextReplayProtocol.SURFACE_STAGE_BEGIN
        }
        val end = actions.indexOfFirst {
            it.id == ClientBattleTextReplayProtocol.SURFACE_STAGE_END
        }
        assertTrue(start >= 0 && end >= start, "missing surface stage envelope")
        return actions.subList(start, end + 1)
    }
}

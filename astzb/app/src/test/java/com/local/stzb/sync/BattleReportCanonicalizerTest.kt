package com.local.stzb.sync

import java.nio.charset.StandardCharsets
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BattleReportCanonicalizerTest {
    @Test
    fun goldenFixtureMatchesPythonCanonicalBytesAndHash() {
        val fixture = JSONObject(
            requireNotNull(javaClass.classLoader?.getResourceAsStream("battle_report_schema_v1.json"))
                .readBytes()
                .toString(StandardCharsets.UTF_8),
        )
        val payload = BattleReportPayload(
            battleId = 42,
            battle = linkedMapOf(
                "battle_id" to 42L,
                "atk_name" to "赵云",
                "result" to 1L,
                "captured_at" to 1_700_000_010L,
            ),
            heroes = listOf(linkedMapOf(
                "side" to "atk", "pos" to 0L, "hero_id" to 1001L,
                "hero_name" to "赵云", "level" to 50L,
            )),
            skills = listOf(linkedMapOf(
                "side" to "atk", "pos" to 0L, "skill_id" to 2001L,
                "skill_name" to "一骑当千", "skill_level" to 10L,
            )),
        )

        val hashed = BattleReportCanonicalizer.hash(payload)

        assertEquals(fixture.getString("canonical"), hashed.canonicalJson)
        assertEquals(fixture.getString("sha256"), hashed.contentHash)
        assertEquals(hashed.canonicalJson.toByteArray(StandardCharsets.UTF_8).size, hashed.encodedBytes)
    }

    @Test
    fun childrenAreSortedAndLocalIdsDoNotEnterPayload() {
        val payload = BattleReportPayload(
            7,
            mapOf("battle_id" to 7L, "result" to null),
            listOf(
                mapOf("side" to "def", "pos" to 1L, "hero_id" to 2L),
                mapOf("side" to "atk", "pos" to 0L, "hero_id" to 1L),
            ),
            listOf(
                mapOf("side" to "def", "pos" to 1L, "skill_id" to 4L),
                mapOf("side" to "atk", "pos" to 0L, "skill_id" to 3L),
            ),
        )
        val canonical = BattleReportCanonicalizer.hash(payload).canonicalJson
        assertEquals(true, canonical.indexOf("\"side\":\"atk\"") < canonical.indexOf("\"side\":\"def\""))
        assertEquals(false, canonical.contains("\"id\""))
    }

    @Test
    fun uploadedFieldChangesHashAndStringsUseJsonEscaping() {
        val base = BattleReportPayload(
            8,
            mapOf("battle_id" to 8L, "atk_name" to "甲\n\"乙\""),
            emptyList(),
            emptyList(),
        )
        val changed = base.copy(battle = base.battle + ("atk_name" to "甲乙"))
        assertNotEquals(
            BattleReportCanonicalizer.hash(base).contentHash,
            BattleReportCanonicalizer.hash(changed).contentHash,
        )
        assertEquals(true, BattleReportCanonicalizer.hash(base).canonicalJson.contains("甲\\n\\\"乙\\\""))
    }

    @Test
    fun stringEscapingMatchesPythonJsonWithoutEscapingSlashOrUnicodeSeparators() {
        assertEquals("\"</tag>\u2028\"", BattleReportCanonicalizer.canonicalJson("</tag>\u2028"))
    }
}

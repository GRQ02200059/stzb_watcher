package com.stzb.battle.core

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClientBattleTextReplayProtocolTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `reference cmd 11 contains every text replay action family`() {
        val report = ZipFile(Path.of("src/test/resources/assent/cfg/paper.zip").toFile()).use { zip ->
            val entry = zip.getEntry("0000000b/cap_20260311222842345_0000000b_zlib.json")!!
            zip.getInputStream(entry).bufferedReader().use { reader ->
                mapper.readTree(reader)[1]["report"].asText()
            }
        }
        val ids = report.split("#").filter(String::isNotBlank).map { record ->
            record.take(2).toInt(36)
        }

        assertEquals(1, ids.count { it == ClientBattleTextReplayProtocol.PREPARE })
        assertEquals(8, ids.count { it == ClientBattleTextReplayProtocol.ROUND })
        assertEquals(119, ClientBattleTextReplayProtocol.NORMAL_ATTACK)
        assertEquals(121, ClientBattleTextReplayProtocol.NORMAL_DAMAGE)
        assertTrue(
            setOf(
                ClientBattleTextReplayProtocol.PANIC_ONGOING_DAMAGE,
                ClientBattleTextReplayProtocol.ONGOING_DAMAGE,
                ClientBattleTextReplayProtocol.HEX_ONGOING_DAMAGE,
            ).all(ids::contains),
        )
        assertTrue(
            setOf(
                ClientBattleTextReplayProtocol.HERO_NAME,
                ClientBattleTextReplayProtocol.HERO_INFO,
                ClientBattleTextReplayProtocol.NORMAL_ATTACK,
                ClientBattleTextReplayProtocol.NORMAL_DAMAGE,
                ClientBattleTextReplayProtocol.SKILL_CAST,
                ClientBattleTextReplayProtocol.SKILL_DAMAGE,
                ClientBattleTextReplayProtocol.ONGOING_DAMAGE,
                ClientBattleTextReplayProtocol.RECOVERY,
                ClientBattleTextReplayProtocol.STATUS,
                ClientBattleTextReplayProtocol.END,
                ClientBattleTextReplayProtocol.FINAL_TROOPS,
            ).all(ids::contains),
        )
    }

    @Test
    fun `client positions run from each base toward each front`() {
        assertEquals(1, ClientBattleTextReplayProtocol.position(Side.ATTACKER, 0))
        assertEquals(2, ClientBattleTextReplayProtocol.position(Side.ATTACKER, 1))
        assertEquals(3, ClientBattleTextReplayProtocol.position(Side.ATTACKER, 2))
        assertEquals(6, ClientBattleTextReplayProtocol.position(Side.DEFENDER, 0))
        assertEquals(5, ClientBattleTextReplayProtocol.position(Side.DEFENDER, 1))
        assertEquals(4, ClientBattleTextReplayProtocol.position(Side.DEFENDER, 2))
    }

    @Test
    fun `evade replay ids match the client protocol`() {
        assertEquals(514, ClientBattleTextReplayProtocol.effectId(BattleStatus.EVADE))
        assertEquals(515, ClientBattleTextReplayProtocol.effectId(BattleStatus.IGNORE_EVADE))
    }
}

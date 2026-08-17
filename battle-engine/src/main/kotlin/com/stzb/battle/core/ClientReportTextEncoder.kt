package com.stzb.battle.core

object ClientReportTextEncoder {
    fun encode(result: BattleResult): String =
        ClientBattleTextReplayAdapter.adapt(result).joinToString("#") { it.encode() }
}

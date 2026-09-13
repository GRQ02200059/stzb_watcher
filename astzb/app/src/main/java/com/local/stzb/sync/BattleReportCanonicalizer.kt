package com.local.stzb.sync

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object BattleReportCanonicalizer {
    const val SCHEMA_VERSION = 1

    fun hash(payload: BattleReportPayload): HashedBattleReport {
        val canonical = canonicalJson(
            mapOf(
                "schemaVersion" to SCHEMA_VERSION,
                "battle" to payload.battle,
                "heroes" to payload.heroes.sortedWith(childComparator("hero_id")),
                "skills" to payload.skills.sortedWith(childComparator("skill_id")),
            ),
        )
        val bytes = canonical.toByteArray(StandardCharsets.UTF_8)
        return HashedBattleReport(
            payload = payload,
            contentHash = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { byte -> "%02x".format(byte) },
            canonicalJson = canonical,
            encodedBytes = bytes.size,
        )
    }

    private fun childComparator(identity: String) =
        compareBy<Map<String, Any?>>(
            { it["side"] as? String ?: "" },
            { (it["pos"] as? Number)?.toLong() ?: 0L },
            { (it[identity] as? Number)?.toLong() ?: 0L },
        )

    internal fun canonicalJson(value: Any?): String = when (value) {
        null -> "null"
        is String -> quoteString(value)
        is Boolean -> if (value) "true" else "false"
        is Byte, is Short, is Int, is Long -> value.toString()
        is Map<*, *> -> value.entries
            .sortedBy { entry -> entry.key as String }
            .joinToString(",", "{", "}") { entry ->
                quoteString(entry.key as String) + ":" + canonicalJson(entry.value)
            }
        is List<*> -> value.joinToString(",", "[", "]") { item -> canonicalJson(item) }
        else -> error("Unsupported canonical value")
    }

    private fun quoteString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
        append('"')
    }
}

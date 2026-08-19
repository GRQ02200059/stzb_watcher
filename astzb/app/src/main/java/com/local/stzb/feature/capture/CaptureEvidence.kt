package com.local.stzb.feature.capture

import java.security.MessageDigest

enum class CaptureEvidenceStage(val label: String) {
    NATIVE_READY("Native 组件"),
    VPN_ESTABLISHED("VPN 通道"),
    SOCKS_CONNECTED("SOCKS 连接"),
    KNOWN_PROTOCOL("已知协议"),
    DATABASE_UPDATED("本地入库"),
    STOP_AND_RECOVERY("停止与网络恢复"),
}

data class CaptureEvidence(
    val nativeReady: Boolean,
    val vpnEstablished: Boolean,
    val socksConnections: Long,
    val protocolCounts: Map<String, Int>,
    val databaseRowDelta: Int,
    val stopped: Boolean,
    val networkRestored: Boolean,
    val targetPackageHash: String,
) {
    val knownProtocolCount: Int
        get() = protocolCounts.filterKeys(KNOWN_PROTOCOL_IDS::contains).values.sum()

    val nextRequiredStage: CaptureEvidenceStage?
        get() = when {
            !nativeReady -> CaptureEvidenceStage.NATIVE_READY
            !vpnEstablished -> CaptureEvidenceStage.VPN_ESTABLISHED
            socksConnections <= 0 -> CaptureEvidenceStage.SOCKS_CONNECTED
            knownProtocolCount <= 0 -> CaptureEvidenceStage.KNOWN_PROTOCOL
            databaseRowDelta <= 0 -> CaptureEvidenceStage.DATABASE_UPDATED
            !stopped || !networkRestored -> CaptureEvidenceStage.STOP_AND_RECOVERY
            else -> null
        }

    val complete: Boolean
        get() = nextRequiredStage == null

    fun stagePassed(stage: CaptureEvidenceStage): Boolean = when (stage) {
        CaptureEvidenceStage.NATIVE_READY -> nativeReady
        CaptureEvidenceStage.VPN_ESTABLISHED -> vpnEstablished
        CaptureEvidenceStage.SOCKS_CONNECTED -> socksConnections > 0
        CaptureEvidenceStage.KNOWN_PROTOCOL -> knownProtocolCount > 0
        CaptureEvidenceStage.DATABASE_UPDATED -> databaseRowDelta > 0
        CaptureEvidenceStage.STOP_AND_RECOVERY -> stopped && networkRestored
    }

    fun toRedactedText(): String = buildString {
        appendLine("capture_evidence_version=1")
        appendLine("complete=$complete")
        appendLine("native_ready=$nativeReady")
        appendLine("vpn_established=$vpnEstablished")
        appendLine("socks_connections=$socksConnections")
        appendLine("known_protocols=${protocolCounts.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }}")
        appendLine("database_row_delta=$databaseRowDelta")
        appendLine("stopped=$stopped")
        appendLine("network_restored=$networkRestored")
        appendLine("target_package_hash=$targetPackageHash")
        appendLine("next_required_stage=${nextRequiredStage?.name.orEmpty()}")
    }

    companion object {
        val KNOWN_PROTOCOL_IDS = setOf("10", "21", "92", "103", "301", "510", "700", "780", "2100", "5026", "5028", "6243", "90005")

        fun from(
            nativeReady: Boolean,
            vpnEstablished: Boolean,
            socksConnections: Long,
            protocolCounts: Map<String, Int>,
            databaseRowDelta: Int,
            stopped: Boolean,
            networkRestored: Boolean,
            targetPackage: String = "",
        ) = CaptureEvidence(
            nativeReady = nativeReady,
            vpnEstablished = vpnEstablished,
            socksConnections = socksConnections.coerceAtLeast(0),
            protocolCounts = protocolCounts.filterValues { it > 0 },
            databaseRowDelta = databaseRowDelta.coerceAtLeast(0),
            stopped = stopped,
            networkRestored = networkRestored,
            targetPackageHash = targetPackage.takeIf(String::isNotBlank)?.sha256()?.take(16).orEmpty(),
        )
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

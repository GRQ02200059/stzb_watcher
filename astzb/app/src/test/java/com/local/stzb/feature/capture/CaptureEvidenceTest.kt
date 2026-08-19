package com.local.stzb.feature.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureEvidenceTest {
    @Test
    fun evidenceRequiresEveryRealCaptureStage() {
        val incomplete = CaptureEvidence.from(
            nativeReady = true,
            vpnEstablished = true,
            socksConnections = 1,
            protocolCounts = mapOf("5028" to 1),
            databaseRowDelta = 1,
            stopped = false,
            networkRestored = false,
        )

        assertFalse(incomplete.complete)
        assertEquals(CaptureEvidenceStage.STOP_AND_RECOVERY, incomplete.nextRequiredStage)

        val complete = CaptureEvidence.from(
            nativeReady = true,
            vpnEstablished = true,
            socksConnections = 1,
            protocolCounts = mapOf("5028" to 1),
            databaseRowDelta = 1,
            stopped = true,
            networkRestored = true,
        )

        assertTrue(complete.complete)
        assertEquals(null, complete.nextRequiredStage)
    }

    @Test
    fun unknownProtocolsDoNotSatisfyKnownProtocolGate() {
        val evidence = CaptureEvidence.from(
            nativeReady = true,
            vpnEstablished = true,
            socksConnections = 2,
            protocolCounts = mapOf("99999" to 5),
            databaseRowDelta = 3,
            stopped = false,
            networkRestored = false,
        )

        assertFalse(evidence.complete)
        assertEquals(CaptureEvidenceStage.KNOWN_PROTOCOL, evidence.nextRequiredStage)
    }

    @Test
    fun exportedEvidenceRedactsTargetPackageAndContainsStageCounts() {
        val evidence = CaptureEvidence.from(
            nativeReady = true,
            vpnEstablished = true,
            socksConnections = 1,
            protocolCounts = mapOf("10" to 2, "5026" to 1),
            databaseRowDelta = 3,
            stopped = true,
            networkRestored = true,
            targetPackage = "com.example.secret.game",
        )

        val text = evidence.toRedactedText()

        assertTrue(text.contains("known_protocols=10:2,5026:1"))
        assertTrue(text.contains("database_row_delta=3"))
        assertTrue(text.contains("target_package_hash="))
        assertFalse(text.contains("com.example.secret.game"))
    }
}

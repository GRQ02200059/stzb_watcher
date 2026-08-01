package com.local.stzb.data.capture

import com.local.stzb.feature.capture.CaptureExportKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class CaptureExportTest {
    @Test fun convertsProducerFileIntoDocumentPayload() {
        val file = File.createTempFile("capture", ".txt").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val export = captureExport(CaptureExportKind.STZB, file, 1234L)
        assertEquals("STZB解析包_19700101_0800.txt", export.name)
        assertEquals("text/plain", export.mimeType)
        assertArrayEquals(byteArrayOf(1, 2, 3), export.bytes)
        file.delete()
    }
}

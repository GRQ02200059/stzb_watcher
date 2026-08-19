package com.example.myapplication

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets

class ProtocolContractTest {
    private val contract: JSONObject by lazy {
        val path = Path.of("src/main/assets/protocol_contract_client_9_2_2.json")
        JSONObject(String(Files.readAllBytes(path), StandardCharsets.UTF_8))
    }

    @Test
    fun commandIdsMatchHexAndDecimalRepresentations() {
        assertEquals(103, command("00000067").getInt("decimalId"))
        assertEquals(5026, command("000013a2").getInt("decimalId"))
        assertEquals(5028, command("000013a4").getInt("decimalId"))
    }

    @Test
    fun memberWuxunIsClientConfirmedAndApproved() {
        val field = field("00000067", "[][10]")
        assertEquals("memberWuxun", field.getString("name"))
        assertEquals("CLIENT_CONFIRMED", field.getString("evidence"))
        assertEquals(true, field.getBoolean("businessApproved"))
    }

    @Test
    fun widConventionMatchesClientSource() {
        assertEquals(
            "x=wid/10000,y=wid%10000",
            contract.getJSONObject("conventions").getString("wid"),
        )
    }

    private fun command(hexId: String): JSONObject {
        val commands = contract.getJSONArray("commands")
        for (index in 0 until commands.length()) {
            val value = commands.getJSONObject(index)
            if (value.getString("hexId") == hexId) return value
        }
        throw AssertionError("missing command $hexId")
    }

    private fun field(hexId: String, path: String): JSONObject {
        val fields = contract.getJSONArray("fields")
        for (index in 0 until fields.length()) {
            val value = fields.getJSONObject(index)
            if (value.getString("hexId") == hexId && value.getString("path") == path) {
                return value
            }
        }
        throw AssertionError("missing field $hexId $path")
    }
}

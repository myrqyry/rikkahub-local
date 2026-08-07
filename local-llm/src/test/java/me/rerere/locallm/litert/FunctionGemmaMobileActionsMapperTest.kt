package me.rerere.locallm.litert

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionGemmaMobileActionsMapperTest {

    @Test
    fun `flashlight actions map to set_torch boolean`() {
        val on = mapped("turn_on_flashlight", buildJsonObject { })
        val off = mapped("turn_off_flashlight", buildJsonObject { })

        assertEquals("set_torch", on.toolName)
        assertTrue(on.arguments.getValue("on").jsonPrimitive.content.toBoolean())
        assertEquals("set_torch", off.toolName)
        assertFalse(off.arguments.getValue("on").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `contact preserves supplied optional fields`() {
        val result = mapped(
            "create_contact",
            buildJsonObject {
                put("first_name", "John")
                put("last_name", "Doe")
                put("phone_number", "+1 555 0100")
                put("email", "john@example.com")
            },
        )

        assertEquals("create_contact", result.toolName)
        assertEquals("John", result.arguments.getValue("first_name").jsonPrimitive.content)
        assertEquals("Doe", result.arguments.getValue("last_name").jsonPrimitive.content)
        assertEquals("+1 555 0100", result.arguments.getValue("phone_number").jsonPrimitive.content)
        assertEquals("john@example.com", result.arguments.getValue("email").jsonPrimitive.content)
    }

    @Test
    fun `contact omits null optional fields`() {
        val result = mapped(
            "create_contact",
            buildJsonObject {
                put("first_name", "John")
                put("last_name", "Doe")
                put("phone_number", JsonNull)
            },
        )

        assertFalse(result.arguments.containsKey("phone_number"))
        assertFalse(result.arguments.containsKey("email"))
    }

    @Test
    fun `email maps to user-reviewed email intent`() {
        val result = mapped(
            "send_email",
            buildJsonObject {
                put("to", "jane@example.com")
                put("subject", "Lunch")
                put("body", "Noon works for me.")
            },
        )

        assertEquals("send_email_intent", result.toolName)
        assertEquals("jane@example.com", result.arguments.getValue("to").jsonPrimitive.content)
        assertEquals("Lunch", result.arguments.getValue("subject").jsonPrimitive.content)
        assertEquals("Noon works for me.", result.arguments.getValue("body").jsonPrimitive.content)
    }

    @Test
    fun `map and wifi use existing Rikka tool names`() {
        val map = mapped(
            "show_map",
            buildJsonObject { put("query", "Eiffel Tower") },
        )
        val wifi = mapped("open_wifi_settings", buildJsonObject { })

        assertEquals("show_location_on_map", map.toolName)
        assertEquals("Eiffel Tower", map.arguments.getValue("query").jsonPrimitive.content)
        assertEquals("open_wifi_settings", wifi.toolName)
        assertTrue(wifi.arguments.isEmpty())
    }

    @Test
    fun `calendar converts local ISO datetime using supplied timezone`() {
        val zone = ZoneId.of("America/Chicago")
        val result = mapped(
            "create_calendar_event",
            buildJsonObject {
                put("title", "Lunch")
                put("datetime", "2026-08-07T12:30:00")
            },
            zone,
        )
        val expected = ZonedDateTime.of(2026, 8, 7, 12, 30, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

        assertEquals("create_calendar_event", result.toolName)
        assertEquals("Lunch", result.arguments.getValue("title").jsonPrimitive.content)
        assertEquals(
            expected,
            result.arguments.getValue("start_time_unix_ms").jsonPrimitive.content.toLong(),
        )
    }

    @Test
    fun `malformed calendar datetime is rejected`() {
        val result = FunctionGemmaMobileActionsMapper.map(
            "create_calendar_event",
            buildJsonObject {
                put("title", "Lunch")
                put("datetime", "tomorrow at noon")
            },
            ZoneId.of("UTC"),
        )

        assertTrue(result is FunctionGemmaMobileActionsMapper.Result.Rejected)
        assertEquals(
            "invalid_datetime",
            (result as FunctionGemmaMobileActionsMapper.Result.Rejected).code,
        )
    }

    @Test
    fun `missing required argument is rejected`() {
        val result = FunctionGemmaMobileActionsMapper.map(
            "send_email",
            buildJsonObject { put("to", "jane@example.com") },
        )

        assertTrue(result is FunctionGemmaMobileActionsMapper.Result.Rejected)
        assertEquals(
            "missing_argument",
            (result as FunctionGemmaMobileActionsMapper.Result.Rejected).code,
        )
    }

    @Test
    fun `unknown function is rejected instead of guessed`() {
        val result = FunctionGemmaMobileActionsMapper.map(
            "make_phone_call",
            buildJsonObject { },
        )

        assertTrue(result is FunctionGemmaMobileActionsMapper.Result.Rejected)
        assertEquals(
            "unsupported_function",
            (result as FunctionGemmaMobileActionsMapper.Result.Rejected).code,
        )
    }

    private fun mapped(
        name: String,
        arguments: kotlinx.serialization.json.JsonObject,
        zoneId: ZoneId = ZoneId.of("UTC"),
    ): FunctionGemmaMobileActionsMapper.Result.Mapped {
        val result = FunctionGemmaMobileActionsMapper.map(name, arguments, zoneId)
        assertTrue("Expected mapped result for $name, got $result", result is FunctionGemmaMobileActionsMapper.Result.Mapped)
        return result as FunctionGemmaMobileActionsMapper.Result.Mapped
    }
}

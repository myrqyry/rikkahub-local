package me.rerere.locallm.litert

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Pure compatibility mapper from FunctionGemma Mobile Actions function calls to RikkaHub's
 * existing local-tool names and argument shapes.
 *
 * This layer never executes tools and never changes which tools are enabled. The eventual
 * LiteRT provider integration should turn [Result.Mapped] into a normal Rikka tool call so
 * the existing hardline, approval, loop-guard, and execution pipeline remains authoritative.
 */
object FunctionGemmaMobileActionsMapper {

    sealed interface Result {
        data class Mapped(
            val toolName: String,
            val arguments: JsonObject,
        ) : Result

        data class Rejected(
            val code: String,
            val detail: String,
        ) : Result
    }

    fun map(
        functionName: String,
        arguments: JsonObject,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Result = when (functionName) {
        "turn_on_flashlight" -> Result.Mapped(
            toolName = "set_torch",
            arguments = buildJsonObject { put("on", true) },
        )

        "turn_off_flashlight" -> Result.Mapped(
            toolName = "set_torch",
            arguments = buildJsonObject { put("on", false) },
        )

        "create_contact" -> mapContact(arguments)
        "send_email" -> mapEmail(arguments)
        "show_map" -> mapMap(arguments)

        "open_wifi_settings" -> Result.Mapped(
            toolName = "open_wifi_settings",
            arguments = buildJsonObject { },
        )

        "create_calendar_event" -> mapCalendar(arguments, zoneId)

        else -> Result.Rejected(
            code = "unsupported_function",
            detail = "FunctionGemma function '$functionName' is not part of the Mobile Actions profile.",
        )
    }

    private fun mapContact(args: JsonObject): Result {
        val firstName = requiredString(args, "first_name") ?: return missing("first_name")
        val lastName = requiredString(args, "last_name") ?: return missing("last_name")

        return Result.Mapped(
            toolName = "create_contact",
            arguments = buildJsonObject {
                put("first_name", firstName)
                put("last_name", lastName)
                optionalString(args, "phone_number")?.let { put("phone_number", it) }
                optionalString(args, "email")?.let { put("email", it) }
            },
        )
    }

    private fun mapEmail(args: JsonObject): Result {
        val to = requiredString(args, "to") ?: return missing("to")
        val subject = requiredString(args, "subject") ?: return missing("subject")

        return Result.Mapped(
            toolName = "send_email_intent",
            arguments = buildJsonObject {
                put("to", to)
                put("subject", subject)
                optionalString(args, "body")?.let { put("body", it) }
            },
        )
    }

    private fun mapMap(args: JsonObject): Result {
        val query = requiredString(args, "query") ?: return missing("query")
        return Result.Mapped(
            toolName = "show_location_on_map",
            arguments = buildJsonObject { put("query", query) },
        )
    }

    private fun mapCalendar(args: JsonObject, zoneId: ZoneId): Result {
        val title = requiredString(args, "title") ?: return missing("title")
        val datetime = requiredString(args, "datetime") ?: return missing("datetime")
        val startTimeMs = try {
            LocalDateTime.parse(datetime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        } catch (_: DateTimeException) {
            return Result.Rejected(
                code = "invalid_datetime",
                detail = "datetime must be a local ISO date-time in YYYY-MM-DDTHH:MM:SS form.",
            )
        }

        return Result.Mapped(
            toolName = "create_calendar_event",
            arguments = buildJsonObject {
                put("title", title)
                put("start_time_unix_ms", startTimeMs)
            },
        )
    }

    private fun requiredString(args: JsonObject, name: String): String? =
        optionalString(args, name)?.takeIf { it.isNotBlank() }

    private fun optionalString(args: JsonObject, name: String): String? {
        val value = args[name] ?: return null
        if (value is JsonNull) return null
        val primitive = runCatching { value.jsonPrimitive }.getOrNull() ?: return null
        if (!primitive.isString) return null
        return primitive.content
    }

    private fun missing(name: String): Result.Rejected = Result.Rejected(
        code = "missing_argument",
        detail = "Required FunctionGemma argument '$name' is missing or is not a non-blank string.",
    )
}

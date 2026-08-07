package me.rerere.locallm.litert

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Compact runtime context for the FunctionGemma Mobile Actions fine-tune.
 *
 * The training prompt supplies local date/time and weekday so relative requests such as
 * "tomorrow at noon" can be converted into the action schema's absolute local datetime.
 * Function declarations themselves come from LiteRT-LM's ToolSet and must not be duplicated
 * here; the 1024-token router has very little context to waste.
 */
object FunctionGemmaMobileActionsPrompt {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")

    fun build(now: ZonedDateTime = ZonedDateTime.now()): String {
        val localDateTime = now.toLocalDateTime().format(dateTimeFormatter)
        val weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return buildString {
            append("Current date and time given in YYYY-MM-DDTHH:MM:SS format: ")
            append(localDateTime)
            append('\n')
            append("Day of week is ")
            append(weekday)
            append('\n')
            append("You are a model that can do function calling with the following functions.")
        }
    }
}

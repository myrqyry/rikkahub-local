package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.service.MAX_HISTORY_RETENTION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleJobValidatorTest {

    @Test
    fun `max runs at retained history ceiling is accepted`() {
        assertNull(
            ScheduleJobValidator.validate(
                validInput(maxRuns = MAX_HISTORY_RETENTION),
                knownToolNames = emptyList(),
            ),
        )
    }

    @Test
    fun `max runs above retained history ceiling is rejected`() {
        val error = ScheduleJobValidator.validate(
            validInput(maxRuns = MAX_HISTORY_RETENTION + 1),
            knownToolNames = emptyList(),
        )

        assertEquals("max_runs_invalid", error?.code)
        assertEquals(
            "max_runs must be <= $MAX_HISTORY_RETENTION",
            error?.detail,
        )
    }

    private fun validInput(maxRuns: Int) = buildJsonObject {
        put("name", "bounded job")
        put("mode", "llm")
        put("prompt", "Do the scheduled task.")
        put("schedule_type", "once")
        put("at_unix_ms", 1L)
        put("max_runs", maxRuns)
    }
}

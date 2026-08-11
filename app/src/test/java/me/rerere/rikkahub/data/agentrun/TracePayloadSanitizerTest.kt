package me.rerere.rikkahub.data.agentrun

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TracePayloadSanitizerTest {
    @Test
    fun redactsCredentialFieldsAndBearerValues() {
        val sanitized = TracePayloadSanitizer().sanitize(
            summary = "Authorization: Bearer secret-value",
            payloadJson = "{\"token\":\"secret-value\",\"artifactId\":\"img_1\"}",
        )

        assertFalse(sanitized.summary!!.contains("secret-value"))
        assertFalse(sanitized.payloadJson!!.contains("secret-value"))
        assertTrue(sanitized.payloadJson.contains("img_1"))
    }

    @Test
    fun boundsLargePayloads() {
        val sanitized = TracePayloadSanitizer().sanitize(null, "x".repeat(100_000))

        assertTrue(sanitized.payloadJson!!.length <= 48 * 1024)
    }
}

package me.rerere.rikkahub.data.ai.tools.local

import org.junit.Assert.assertTrue
import org.junit.Test

class FindNodeToolTest {

    @Test
    fun `find_node rejects bad by value`() {
        val tool = findNodeTool()
        val result = execTool(tool, """{"by":"class","value":"FooView"}""")
        assertTrue("expected error envelope, got: $result", result.contains("\"error\""))
    }

    @Test
    fun `find_node rejects missing value`() {
        val tool = findNodeTool()
        val result = execTool(tool, """{"by":"text"}""")
        assertTrue("expected error envelope, got: $result", result.contains("\"error\""))
    }

    @Test
    fun `find_node hits service-not-active when offline`() {
        val tool = findNodeTool()
        val result = execTool(tool, """{"by":"text","value":"OK"}""")
        assertTrue(
            "expected service-not-active envelope, got: $result",
            result.contains("AccessibilityService not active")
        )
    }

    @Test
    fun `click_node rejects negative nth`() {
        val tool = clickNodeTool()
        val result = execTool(tool, """{"by":"text","value":"OK","nth":-1}""")
        assertTrue("expected error envelope, got: $result", result.contains("\"error\""))
    }

    @Test
    fun `click_node rejects bad by`() {
        val tool = clickNodeTool()
        val result = execTool(tool, """{"by":"id","value":"x"}""")
        assertTrue("expected error envelope, got: $result", result.contains("\"error\""))
    }

    @Test
    fun `click_node rejects contradictory postconditions before dispatch`() {
        val result = execTool(clickNodeTool(),
            """{"by":"text","value":"OK","expect_text":"Ready","expect_absent_text":"Ready"}""")
        assertTrue(result.contains("invalid_verification_expectation"))
    }

    @Test
    fun `click_node rejects timeout without an expected state`() {
        val result = execTool(clickNodeTool(),
            """{"by":"text","value":"OK","verify_timeout_ms":1000}""")
        assertTrue(result.contains("invalid_verify_timeout_or_missing_expectation"))
    }

    @Test
    fun `click_node accepts a verified-click request and reaches service gate`() {
        val result = execTool(clickNodeTool(),
            """{"by":"text","value":"OK","expect_text":"Done","expect_package":"com.example","verify_timeout_ms":1000}""")
        assertTrue(result.contains("AccessibilityService not active"))
    }
}

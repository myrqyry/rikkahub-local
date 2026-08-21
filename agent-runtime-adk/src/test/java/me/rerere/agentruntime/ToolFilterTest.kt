package me.rerere.agentruntime

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolFilterTest {

    private class FakeTool(name: String) : BaseTool(name = name, description = name) {
        override fun declaration(): FunctionDeclaration? = null
        override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any = mapOf<String, Any>()
    }

    private val tools = listOf(
        FakeTool("delegate"),
        FakeTool("proposeMemory"),
        FakeTool("github"),
        FakeTool("browser"),
        FakeTool("mcp_search"),
        FakeTool("composite_report"),
    )

    @Test
    fun `cloud tier with no budget returns every tool unbounded`() {
        val result = ToolFilter.filter(
            tools = tools,
            capabilities = ToolCapabilities(tier = ModelTier.CLOUD),
        )
        assertEquals(tools.size, result.size)
        assertEquals(tools.map { it.name }, result.map { it.name })
    }

    @Test
    fun `budget keeps priority tools first then fills the rest in declaration order`() {
        val result = ToolFilter.filter(
            tools = tools,
            capabilities = ToolCapabilities(tier = ModelTier.LOCAL_SMALL),
            budget = 4,
        )
        assertEquals(listOf("delegate", "proposeMemory", "github", "browser"), result.map { it.name })
    }

    @Test
    fun `small local model gets a narrow tool set`() {
        val result = ToolFilter.filter(
            tools = tools,
            capabilities = ToolCapabilities(tier = ModelTier.LOCAL_SMALL, toolBudget = 3),
        )
        assertEquals(listOf("delegate", "proposeMemory", "github"), result.map { it.name })
    }

    @Test
    fun `budget smaller than priority count still keeps all priority tools`() {
        val result = ToolFilter.filter(
            tools = tools,
            capabilities = ToolCapabilities(tier = ModelTier.LOCAL_SMALL),
            budget = 1,
        )
        assertEquals(listOf("delegate", "proposeMemory"), result.map { it.name })
    }

    @Test
    fun `budget from capabilities is used when no explicit budget is passed`() {
        val result = ToolFilter.filter(
            tools = tools,
            capabilities = ToolCapabilities(tier = ModelTier.LOCAL_LARGE, toolBudget = 5),
        )
        assertEquals(listOf("delegate", "proposeMemory", "github", "browser", "mcp_search"), result.map { it.name })
    }

    @Test
    fun `filter is deterministic for the same input`() {
        val capabilities = ToolCapabilities(tier = ModelTier.LOCAL_LARGE, toolBudget = 4)
        val a = ToolFilter.filter(tools, capabilities)
        val b = ToolFilter.filter(tools, capabilities)
        assertEquals(a.map { it.name }, b.map { it.name })
        assertTrue(a.size <= 4)
    }

    @Test
    fun `empty tool list stays empty`() {
        assertEquals(0, ToolFilter.filter(emptyList(), ToolCapabilities(tier = ModelTier.CLOUD)).size)
    }
}

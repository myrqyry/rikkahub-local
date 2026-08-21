package me.rerere.agentruntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredQueryTest {

    @Test
    fun `structured protocol distinguishes wire format from schema`() {
        val json = StructuredProtocol(name = "json", version = "1.0")
        val python = StructuredProtocol(name = "python", version = "2.0")
        assertEquals("json", json.name)
        assertNotEquals(json, python)
        assertEquals("json:1.0", json.id())
    }

    @Test
    fun `structured query carries a schema independent of protocol`() {
        val query = StructuredQuery<String>(
            prompt = "Extract the capital from the text",
            schema = "string",
        )
        assertEquals("string", query.schema)
        assertTrue(query.prompt.contains("capital"))
    }

    @Test
    fun `compiling a request does not execute any model`() {
        val query = StructuredQuery<String>(prompt = "Return the answer as JSON", schema = "string")
        val request = QueryCompiler.compile(query, StructuredProtocol(name = "json", version = "1.0"))
        assertEquals("json:1.0", request.protocol)
        assertTrue(request.compiledPayload.contains("Return the answer as JSON"))
        assertTrue(request.compiledPayload.contains("string"))
    }

    @Test
    fun `different protocols compile to different requests for the same query`() {
        val query = StructuredQuery<String>(prompt = "Return the answer", schema = "string")
        val json = QueryCompiler.compile(query, StructuredProtocol(name = "json", version = "1.0"))
        val python = QueryCompiler.compile(query, StructuredProtocol(name = "python", version = "2.0"))
        assertNotEquals(json.compiledPayload, python.compiledPayload)
    }

    @Test
    fun `evaluator runs a case and returns a deterministic result`() {
        val evaluator = Evaluator<String, String> { case -> case.input.uppercase() }
        val result = evaluator.evaluate(EvaluationCase(input = "hello"))
        assertEquals("HELLO", result.output)
        assertTrue(result.error == null)
        assertEquals("hello", result.metadata["input"])
    }

    @Test
    fun `evaluator surfaces failures as errors not exceptions`() {
        val evaluator = Evaluator<String, String> { case ->
            if (case.input == "boom") throw IllegalStateException("boom failed") else case.input
        }
        val result = evaluator.evaluate(EvaluationCase(input = "boom"))
        assertTrue(result.error?.contains("boom") == true)
        assertTrue(result.output == null)
    }

    @Test
    fun `evaluator compares output against ground truth`() {
        val evaluator = Evaluator<String, String> { case -> case.input.lowercase() }
        val result = evaluator.evaluate(EvaluationCase(input = "HELLO", groundTruth = "hello"))
        assertEquals("hello", result.output)
        assertTrue(result.matchesGroundTruth)
    }
}

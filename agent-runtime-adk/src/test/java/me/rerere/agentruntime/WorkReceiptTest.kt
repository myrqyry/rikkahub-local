package me.rerere.agentruntime

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkReceiptTest {

    @Test
    fun `collector records command evidence and preserves verification facts`() = runBlocking {
        val store = InMemoryEvidenceStore()
        val receipt = WorkReceiptCollector(store, nowMs = { 123L }).collect(
            WorkReceiptInput(
                taskId = "task-1",
                claim = "Add evidence receipts",
                changedFiles = listOf(
                    ChangedFile("src/main.kt", ChangeKind.Modified),
                    ChangedFile("src/test.kt", ChangeKind.Added),
                ),
                checks = listOf(
                    VerificationResult(
                        command = "./gradlew test",
                        exitCode = 0,
                        output = "all tests passed",
                        durationMs = 42,
                    ),
                    VerificationResult(
                        command = "./gradlew lint",
                        exitCode = 1,
                        output = "lint failed",
                        durationMs = 18,
                    ),
                ),
                artifacts = listOf(ReceiptArtifact("receipt.json", ArtifactKind.Receipt)),
                unresolvedItems = listOf("Device check was not run"),
            ),
        )

        assertEquals(123L, receipt.createdAtMs)
        assertEquals(listOf(0, 1), receipt.checks.map { it.exitCode })
        assertEquals(listOf("src/main.kt", "src/test.kt"), receipt.changedFiles.map { it.path })
        assertEquals(2, store.query(EvidenceQuery(type = WorkReceiptCollector.EVIDENCE_TYPE)).size)
    }

    @Test
    fun `collector bounds command output without changing the exit result`() = runBlocking {
        val store = InMemoryEvidenceStore()
        val receipt = WorkReceiptCollector(store, maxOutputChars = 5).collect(
            WorkReceiptInput(
                taskId = "task-2",
                claim = "Bound output",
                checks = listOf(VerificationResult(command = "check", exitCode = 7, output = "123456789", durationMs = 0)),
            ),
        )

        assertEquals(7, receipt.checks.single().exitCode)
        assertEquals("56789", receipt.checks.single().outputTail)
        assertTrue(store.query().single().payload.contains("123456789"))
    }

    @Test
    fun `receipt serialization is stable and includes unverified boundaries`() {
        val receipt = WorkReceipt(
            taskId = "task-3",
            claim = "Ship safely",
            changedFiles = emptyList(),
            checks = listOf(VerificationSummary("device", null, "not run", 0)),
            artifacts = emptyList(),
            unresolvedItems = listOf("Device check was not run"),
            createdAtMs = 99,
        )

        val json = Json.encodeToString(receipt)

        assertTrue(json.contains("\"exitCode\":null"))
        assertTrue(json.contains("Device check was not run"))
    }
}

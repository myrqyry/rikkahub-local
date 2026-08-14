package me.rerere.locallm.litert.install

import me.rerere.locallm.litert.CapabilityGrant
import me.rerere.locallm.litert.CapabilityScopes
import me.rerere.locallm.litert.FileScope
import me.rerere.locallm.litert.ModelScope
import me.rerere.locallm.litert.NetworkScope
import me.rerere.locallm.litert.ResourceBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ModelInstallSessionTest {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private lateinit var destDir: File

    @Before
    fun setUp() {
        destDir = createTempDirectory("rikka-install-test").toFile()
    }

    private fun grantFor(url: String, dest: String) = CapabilityGrant(
        requestedCapabilities = listOf("model.install"),
        grantedCapabilities = listOf("model.install"),
        rejectedCapabilities = emptyList(),
        scopes = CapabilityScopes(
            networkScopes = listOf(NetworkScope(origin = url)),
            fileScopes = listOf(FileScope(path = dest, operation = "write")),
            modelScopes = listOf(ModelScope(source = url)),
        ),
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun bytes(n: Int, seed: Int = 0): ByteArray = ByteArray(n) { ((it + seed) % 256).toByte() }

    @Test
    fun installsSmallFileSuccessfullyWithReadyReceipt() = runBlocking {
        val content = bytes(100_000)
        val source = MemoryModelFileSource("model.bin", content)
        val request = ModelInstallRequest(
            sourceUrl = "https://hf.co/models/x",
            destination = File(destDir, "installed.bin").absolutePath,
            expectedManifest = mapOf("model.bin" to sha256(content)),
            grant = grantFor("https://hf.co/models/x", File(destDir, "installed.bin").absolutePath),
            budget = ResourceBudget(maxDownloadBytes = 1_000_000, maxNetworkRequests = 100),
        )
        val session = ModelInstallSession(json)
        val states = mutableListOf<ModelInstallState>()
        val receipt = session.install(request, source) { states += it }

        assertEquals("ready", receipt.state)
        assertEquals(100_000, receipt.totalBytes)
        assertEquals(100_000, receipt.downloadedBytes)
        assertTrue(receipt.manifestVerified)
        assertEquals(listOf("model.bin"), receipt.verifiedFiles)
        assertEquals(1, receipt.totalRanges)
        assertTrue(File(request.destination).exists())
        assertTrue(content.contentEquals(File(request.destination).readBytes()))
        assertTrue(states.any { it is ModelInstallState.Ready })
    }

    @Test
    fun splitsIntoMultipleRangesAtMaxRangeBytes() = runBlocking {
        val content = bytes(1_500_000)
        val source = MemoryModelFileSource("big.bin", content)
        val request = ModelInstallRequest(
            sourceUrl = "https://hf.co/models/big",
            destination = File(destDir, "big.bin").absolutePath,
            expectedManifest = mapOf("big.bin" to sha256(content)),
            grant = grantFor("https://hf.co/models/big", File(destDir, "big.bin").absolutePath),
            budget = ResourceBudget(maxDownloadBytes = 2_000_000),
        )
        val session = ModelInstallSession(json)
        val receipt = session.install(request, source)

        assertEquals("ready", receipt.state)
        // 1_500_000 bytes / 524_288 -> 3 ranges
        assertEquals(3, receipt.totalRanges)
        assertEquals(3, receipt.completedRanges)
        assertTrue(content.contentEquals(File(request.destination).readBytes()))
    }

    @Test
    fun rejectsWhenDestinationNotInFileScopes() = runBlocking {
        val content = bytes(10_000)
        val source = MemoryModelFileSource("model.bin", content)
        val request = ModelInstallRequest(
            sourceUrl = "https://hf.co/models/x",
            destination = File(destDir, "forbidden.bin").absolutePath,
            expectedManifest = mapOf("model.bin" to sha256(content)),
            // file scope only allows a different path
            grant = CapabilityGrant(
                requestedCapabilities = listOf("model.install"),
                grantedCapabilities = listOf("model.install"),
                rejectedCapabilities = emptyList(),
                scopes = CapabilityScopes(
                    networkScopes = listOf(NetworkScope(origin = "https://hf.co/models/x")),
                    fileScopes = listOf(FileScope(path = File(destDir, "allowed.bin").absolutePath, operation = "write")),
                ),
            ),
            budget = ResourceBudget(maxDownloadBytes = 1_000_000),
        )
        val session = ModelInstallSession(json)
        val receipt = session.install(request, source)

        assertEquals("failed", receipt.state)
        assertTrue(receipt.error!!.contains("capability_rejected"))
        assertFalse(File(request.destination).exists())
    }

    @Test
    fun failsOnHashMismatchAndDoesNotPromote() = runBlocking {
        val content = bytes(50_000)
        val source = MemoryModelFileSource("model.bin", content)
        val request = ModelInstallRequest(
            sourceUrl = "https://hf.co/models/x",
            destination = File(destDir, "corrupt.bin").absolutePath,
            expectedManifest = mapOf("model.bin" to sha256(bytes(50_000, seed = 99))), // wrong hash
            grant = grantFor("https://hf.co/models/x", File(destDir, "corrupt.bin").absolutePath),
            budget = ResourceBudget(maxDownloadBytes = 1_000_000),
        )
        val session = ModelInstallSession(json)
        val receipt = session.install(request, source)

        assertEquals("failed", receipt.state)
        assertTrue(receipt.error!!.contains("hash_mismatch"))
        assertFalse(receipt.manifestVerified)
        assertFalse(File(request.destination).exists())
    }

    @Test
    fun failsWhenNetworkRequestBudgetExceeded() = runBlocking {
        val content = bytes(1_200_000) // 3 ranges
        val source = MemoryModelFileSource("model.bin", content)
        val request = ModelInstallRequest(
            sourceUrl = "https://hf.co/models/x",
            destination = File(destDir, "bounded.bin").absolutePath,
            expectedManifest = mapOf("model.bin" to sha256(content)),
            grant = grantFor("https://hf.co/models/x", File(destDir, "bounded.bin").absolutePath),
            budget = ResourceBudget(maxNetworkRequests = 1), // only 1 range allowed
        )
        val session = ModelInstallSession(json)
        val receipt = session.install(request, source)

        assertEquals("failed", receipt.state)
        assertTrue(receipt.error!!.contains("budget_exceeded: maxNetworkRequests"))
    }

    @Test
    fun cancelPausesAndPreservesWork() = runBlocking {
        val content = bytes(1_200_000) // 3 ranges
        val source = SlowMemoryModelFileSource("model.bin", content)
        val dest = File(destDir, "paused.bin").absolutePath
        val request = ModelInstallRequest(
            sourceUrl = "https://hf.co/models/x",
            destination = dest,
            expectedManifest = mapOf("model.bin" to sha256(content)),
            grant = grantFor("https://hf.co/models/x", dest),
            budget = ResourceBudget(maxDownloadBytes = 2_000_000),
        )
        val session = ModelInstallSession(json)
        session.cancel(dest) // cancelled before install starts
        val receipt = session.install(request, source)

        assertEquals("paused", receipt.state)
        assertEquals(0, receipt.completedRanges)
        // partial dir checkpoint preserved (no final file)
        assertFalse(File(dest).exists())
    }

    @Test
    fun resumesFromCheckpointOnSecondCall() = runBlocking {
        val content = bytes(1_100_000)
        val source = MemoryModelFileSource("model.bin", content)
        val dest = File(destDir, "resume.bin").absolutePath
        val request = ModelInstallRequest(
            sourceUrl = "https://hf.co/models/x",
            destination = dest,
            expectedManifest = mapOf("model.bin" to sha256(content)),
            grant = grantFor("https://hf.co/models/x", dest),
            budget = ResourceBudget(maxDownloadBytes = 2_000_000),
        )
        val session = ModelInstallSession(json)

        // First run fails on hash mismatch (wrong manifest) -> checkpoint written but no promotion.
        val badRequest = request.copy(
            expectedManifest = mapOf("model.bin" to sha256(bytes(10))),
            budget = ResourceBudget(maxDownloadBytes = 2_000_000),
        )
        val first = session.install(badRequest, source)
        assertEquals("failed", first.state)

        // Second run with correct manifest resumes and succeeds.
        val receipt = session.install(request, source)
        assertEquals("ready", receipt.state)
        assertTrue(content.contentEquals(File(dest).readBytes()))
    }

    @Test
    fun destinationLockRejectsConcurrentInstall() = runBlocking {
        val content = bytes(10_000)
        val source = MemoryModelFileSource("model.bin", content)
        val dest = File(destDir, "locked.bin").absolutePath
        val request = ModelInstallRequest(
            sourceUrl = "https://hf.co/models/x",
            destination = dest,
            expectedManifest = mapOf("model.bin" to sha256(content)),
            grant = grantFor("https://hf.co/models/x", dest),
            budget = ResourceBudget(maxDownloadBytes = 1_000_000),
        )
        val session = ModelInstallSession(json)

        // Hold the lock: j1 blocks inside its first readRange until released,
        // guaranteeing j2's putIfAbsent sees the lock while it is held.
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blockingSource = BlockingModelFileSource("model.bin", content, started, release)
        var r1: ModelInstallReceipt? = null
        var r2: ModelInstallReceipt? = null
        coroutineScope {
            val j1 = launch { r1 = session.install(request, blockingSource) }
            started.await() // j1 is mid-range and holds the lock
            val j2 = launch { r2 = session.install(request, source) }
            j2.join() // let j2 finish (it must be rejected), then release j1
            release.complete(Unit)
            j1.join()
        }

        val statuses = listOfNotNull(r1?.state, r2?.state)
        assertTrue(statuses.size == 2)
        assertTrue(statuses.any { it == "ready" })
        assertTrue(statuses.any { it == "failed" }) // second install locked out
    }

    private class MemoryModelFileSource(
        override val fileName: String,
        private val data: ByteArray,
    ) : ModelFileSource {
        override val totalBytes: Long get() = data.size.toLong()
        override suspend fun readRange(offset: Long, length: Int): ByteArray {
            val start = offset.toInt()
            val end = minOf(start + length, data.size)
            return data.copyOfRange(start, end)
        }
    }

    private class SlowMemoryModelFileSource(
        override val fileName: String,
        private val data: ByteArray,
    ) : ModelFileSource {
        override val totalBytes: Long get() = data.size.toLong()
        override suspend fun readRange(offset: Long, length: Int): ByteArray {
            val start = offset.toInt()
            val end = minOf(start + length, data.size)
            return data.copyOfRange(start, end)
        }
    }

    private class BlockingModelFileSource(
        override val fileName: String,
        private val data: ByteArray,
        private val started: CompletableDeferred<Unit>,
        private val release: CompletableDeferred<Unit>,
    ) : ModelFileSource {
        override val totalBytes: Long get() = data.size.toLong()
        override suspend fun readRange(offset: Long, length: Int): ByteArray {
            val start = offset.toInt()
            val end = minOf(start + length, data.size)
            if (offset == 0L) {
                started.complete(Unit) // signal first range in flight (lock held)
                release.await()        // hold the lock until the test releases it
            }
            return data.copyOfRange(start, end)
        }
    }
}

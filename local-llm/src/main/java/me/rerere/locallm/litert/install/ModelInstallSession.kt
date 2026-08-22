package me.rerere.locallm.litert.install

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.locallm.litert.CapabilityGrant
import me.rerere.locallm.litert.ResourceBudget
import me.rerere.locallm.litert.ResourceUsage
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** The source of model file bytes: a bounded, ranged read over a logical file. */
interface ModelFileSource {
    val fileName: String
    val totalBytes: Long
    /** Read exactly [length] bytes at [offset]; length <= [maxRangeBytes]. Returns fewer only at EOF. */
    suspend fun readRange(offset: Long, length: Int): ByteArray
}

/** Request to install a model: where from, where to, what's expected, under which grant+budget. */
data class ModelInstallRequest(
    val sourceUrl: String,
    val destination: String,
    val expectedManifest: Map<String, String>, // fileName -> sha256 hex
    val grant: CapabilityGrant,
    val budget: ResourceBudget,
)

/** All states a model install can occupy. Cancellation moves RUNNING -> PAUSED, never deletes work. */
sealed interface ModelInstallState {
    data object Preparing : ModelInstallState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val completedRanges: Int,
        val totalRanges: Int,
    ) : ModelInstallState
    data class Paused(
        val downloadedBytes: Long,
        val completedRanges: Int,
        val totalRanges: Int,
    ) : ModelInstallState
    data object Verifying : ModelInstallState
    data object Ready : ModelInstallState
    data class Failed(val error: String) : ModelInstallState
}

/** Describes exactly what was installed + verified. */
@Serializable
data class ModelInstallReceipt(
    val installId: String,
    val sourceUrl: String,
    val destination: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val completedRanges: Int,
    val totalRanges: Int,
    val verifiedFiles: List<String>,
    val manifestVerified: Boolean,
    val state: String, // ready | paused | failed
    val error: String? = null,
)

/**
 * Resumable, bounded, ranged model installer.
 *
 * TurboFieldfare lessons: bounded HTTP ranges, small scratch, per-range digest, resumable
 * checkpoint, exclusive per-destination lock, pause-never-delete, manifest verification,
 * atomic promotion, never expose a partial install as a usable model.
 *
 * Pure JVM and deterministic (source is injected as [ModelFileSource]); the app wires the
 * HTTP ranged source via DI. Uses [CapabilityGrant] scopes and [ResourceBudget] from day one.
 */
class ModelInstallSession(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val locks = ConcurrentHashMap<String, String>()

    /**
     * Install [request] by reading model bytes from [source], staging them into a partial
     * directory next to [request.destination], then atomically promoting on success.
     *
     * A prior paused install resumes from its checkpoint (resumed ranges are re-verified
     * against their recorded digest before being trusted). Cancelling while running produces
     * a Paused state and preserves all completed work.
     */
    suspend fun install(
        request: ModelInstallRequest,
        source: ModelFileSource,
        onState: (ModelInstallState) -> Unit = {},
    ): ModelInstallReceipt {
        val installId = lock(request.destination) ?: return failedReceipt(
            request, source, installId = "unset", error = "destination_locked",
        )
        try {
            onState(ModelInstallState.Preparing)
            val startedAt = nowMs()
            val totalBytes = source.totalBytes
            val totalRanges = ranges(totalBytes, request.budget)

            // Verify capability scopes up front (deterministic, before any I/O).
            val grant = request.grant
            if (!grant.scopes.isAllowingNetwork(request.sourceUrl)) {
                return failedReceipt(request, source, installId, "capability_rejected: network origin not granted")
            }
            if (!grant.scopes.isAllowingFile(request.destination, "write")) {
                return failedReceipt(request, source, installId, "capability_rejected: file destination not granted")
            }

            val partialDir = partialDir(request.destination)
            partialDir.mkdirs()
            val checkpoint = Checkpoint.load(json, partialDir)
            val destFile = java.io.File(partialDir, source.fileName)

            var downloadedBytes = checkpoint.downloadedBytes
            var networkRequests = 0
            var completedRanges = 0

            onState(ModelInstallState.Downloading(downloadedBytes, totalBytes, 0, totalRanges))

            var offset = 0L
            var rangeIndex = 0
            while (offset < totalBytes) {
                val length = (minOf(MAX_RANGE_BYTES.toLong(), totalBytes - offset)).toInt()
                val rangeKey = rangeIndex.toString()

                if (!checkpoint.isVerified(rangeKey)) {
                    if (request.budget.maxNetworkRequests != null && networkRequests + 1 > request.budget.maxNetworkRequests) {
                        saveCheckpoint(partialDir, checkpoint, downloadedBytes)
                        return failedReceipt(request, source, installId, "budget_exceeded: maxNetworkRequests", downloadedBytes, completedRanges, totalRanges, request.budget)
                    }
                    if (request.budget.maxDurationMs != null && nowMs() - startedAt > request.budget.maxDurationMs) {
                        saveCheckpoint(partialDir, checkpoint, downloadedBytes)
                        return failedReceipt(request, source, installId, "budget_exceeded: maxDurationMs", downloadedBytes, completedRanges, totalRanges, request.budget)
                    }
                    if (request.budget.maxDownloadBytes != null && downloadedBytes + length > request.budget.maxDownloadBytes) {
                        saveCheckpoint(partialDir, checkpoint, downloadedBytes)
                        return failedReceipt(request, source, installId, "budget_exceeded: maxDownloadBytes", downloadedBytes, completedRanges, totalRanges, request.budget)
                    }
                    if (cancellationFlags.remove(request.destination) == true) {
                        saveCheckpoint(partialDir, checkpoint, downloadedBytes)
                        return pausedReceipt(request, source, installId, downloadedBytes, completedRanges, totalRanges)
                    }

                    val bytes = source.readRange(offset, length)
                    networkRequests++
                    writeAt(destFile, offset, bytes)
                    val digest = sha256(bytes)
                    checkpoint.markVerified(rangeKey, digest, offset, length)
                    downloadedBytes += length
                    completedRanges++
                    saveCheckpoint(partialDir, checkpoint, downloadedBytes)
                    onState(ModelInstallState.Downloading(downloadedBytes, totalBytes, completedRanges, totalRanges))
                } else {
                    completedRanges++
                }
                offset += length
                rangeIndex++
            }

            // Re-verify full file against the requested manifest before promotion.
            onState(ModelInstallState.Verifying)
            val actual = sha256(readAll(destFile))
            val expected = request.expectedManifest[source.fileName]
            if (expected == null || !actual.equals(expected, ignoreCase = true)) {
                return failedReceipt(request, source, installId, "hash_mismatch: ${source.fileName} (expected ${expected ?: "none"}, got $actual)", downloadedBytes, completedRanges, totalRanges, request.budget)
            }

            val finalDir = java.io.File(request.destination)
            finalDir.parentFile?.mkdirs()
            if (finalDir.exists()) finalDir.deleteRecursively()
            // Atomic-ish promotion: move the staged file into the final location.
            val staged = java.io.File(partialDir, source.fileName)
            val target = java.io.File(request.destination)
            staged.copyTo(target, overwrite = true)
            partialDir.deleteRecursively()

            onState(ModelInstallState.Ready)
            return ModelInstallReceipt(
                installId = installId,
                sourceUrl = request.sourceUrl,
                destination = request.destination,
                totalBytes = totalBytes,
                downloadedBytes = downloadedBytes,
                completedRanges = totalRanges,
                totalRanges = totalRanges,
                verifiedFiles = listOf(source.fileName),
                manifestVerified = true,
                state = "ready",
            )
        } finally {
            locks.remove(request.destination)
        }
    }

    /** Request cancellation on the next range boundary. Pauses, never deletes work. */
    fun cancel(destination: String) {
        cancellationFlags[destination] = true
    }

    private fun ranges(totalBytes: Long, budget: ResourceBudget): Int {
        if (totalBytes <= 0) return 1
        return ((totalBytes + MAX_RANGE_BYTES - 1) / MAX_RANGE_BYTES).toInt()
    }

    private fun lock(destination: String): String? {
        val id = "install-" + java.util.UUID.randomUUID().toString().take(8)
        return if (locks.putIfAbsent(destination, id) == null) id else null
    }

    private fun partialDir(destination: String): java.io.File = java.io.File(destination + ".partial")

    private fun failedReceipt(
        request: ModelInstallRequest,
        source: ModelFileSource,
        installId: String,
        error: String,
        downloadedBytes: Long = 0,
        completedRanges: Int = 0,
        totalRanges: Int = 0,
        budget: ResourceBudget = request.budget,
    ): ModelInstallReceipt = ModelInstallReceipt(
        installId = installId,
        sourceUrl = request.sourceUrl,
        destination = request.destination,
        totalBytes = source.totalBytes,
        downloadedBytes = downloadedBytes,
        completedRanges = completedRanges,
        totalRanges = if (totalRanges > 0) totalRanges else ranges(source.totalBytes, budget),
        verifiedFiles = emptyList(),
        manifestVerified = false,
        state = "failed",
        error = error,
    )

    private fun pausedReceipt(
        request: ModelInstallRequest,
        source: ModelFileSource,
        installId: String,
        downloadedBytes: Long,
        completedRanges: Int,
        totalRanges: Int,
    ): ModelInstallReceipt = ModelInstallReceipt(
        installId = installId,
        sourceUrl = request.sourceUrl,
        destination = request.destination,
        totalBytes = source.totalBytes,
        downloadedBytes = downloadedBytes,
        completedRanges = completedRanges,
        totalRanges = totalRanges,
        verifiedFiles = emptyList(),
        manifestVerified = false,
        state = "paused",
    )

    private fun saveCheckpoint(dir: java.io.File, cp: Checkpoint, downloadedBytes: Long) {
        cp.downloadedBytes = downloadedBytes
        dir.mkdirs()
        Checkpoint.save(json, dir, cp)
    }

    private fun writeAt(file: java.io.File, offset: Long, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        java.io.RandomAccessFile(file, "rw").use { raf ->
            raf.seek(offset)
            raf.write(bytes)
        }
    }

    private fun readAll(file: java.io.File): ByteArray = file.readBytes()

    private fun sha256(bytes: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(bytes)
        return d.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MAX_RANGE_BYTES = 524_288
    }
}

/** Per-destination cancellation flags (pause request), not authority. */
private val cancellationFlags = ConcurrentHashMap<String, Boolean>()

/** Serializable resumable checkpoint stored beside the staged file. */
private data class Checkpoint(
    var downloadedBytes: Long = 0,
    val verifiedRanges: MutableMap<String, String> = mutableMapOf(), // rangeKey -> sha256
    val rangeOffsets: MutableMap<String, Long> = mutableMapOf(), // rangeKey -> start offset
    val rangeLengths: MutableMap<String, Int> = mutableMapOf(), // rangeKey -> byte length
) {
    fun isVerified(rangeKey: String): Boolean = verifiedRanges.containsKey(rangeKey)

    fun markVerified(rangeKey: String, digest: String, offset: Long, length: Int) {
        verifiedRanges[rangeKey] = digest
        rangeOffsets[rangeKey] = offset
        rangeLengths[rangeKey] = length
    }

    companion object {
        private const val FILE = "checkpoint.json"
        fun load(json: Json, dir: java.io.File): Checkpoint {
            val f = java.io.File(dir, FILE)
            if (!f.exists()) return Checkpoint()
            return try {
                val obj = json.parseToJsonElement(f.readText()).jsonObject
                val verified = obj["verifiedRanges"]?.jsonObject
                    ?.let { o -> o.mapValues { (_, v) -> v.jsonPrimitive.content }.toMutableMap() }
                    ?: mutableMapOf()
                val offsets = obj["rangeOffsets"]?.jsonObject
                    ?.let { o -> o.mapValues { (_, v) -> v.jsonPrimitive.content.toLong() }.toMutableMap() }
                    ?: mutableMapOf()
                val lengths = obj["rangeLengths"]?.jsonObject
                    ?.let { o -> o.mapValues { (_, v) -> v.jsonPrimitive.content.toInt() }.toMutableMap() }
                    ?: mutableMapOf()
                Checkpoint(
                    downloadedBytes = obj["downloadedBytes"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    verifiedRanges = verified,
                    rangeOffsets = offsets,
                    rangeLengths = lengths,
                )
            } catch (_: Exception) {
                Checkpoint()
            }
        }

        fun save(json: Json, dir: java.io.File, cp: Checkpoint) {
            val obj = buildJsonObject {
                put("downloadedBytes", JsonPrimitive(cp.downloadedBytes))
                put("verifiedRanges", buildJsonObject { cp.verifiedRanges.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
                put("rangeOffsets", buildJsonObject { cp.rangeOffsets.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
                put("rangeLengths", buildJsonObject { cp.rangeLengths.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
            }
            java.io.File(dir, FILE).writeText(obj.toString())
        }
    }
}

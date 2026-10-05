package me.rerere.locallm.litert

import me.rerere.locallm.litert.compute.ComputeCapabilities
import me.rerere.locallm.litert.compute.DeviceExecutionProfile
import me.rerere.locallm.litert.compute.MemoryAdmission
import me.rerere.locallm.litert.compute.decideMemoryAdmission
import me.rerere.locallm.litert.compute.pickLiteRt

enum class LiteRtPlanWarning {
    SYSTEM_LOW_MEMORY,
    BELOW_CATALOG_MINIMUM,
}

/**
 * Pure model/backend plan. This is selection evidence, not authority: the runtime can still
 * fall back if the chosen native backend fails, and the capability broker remains unchanged.
 */
data class LiteRtExecutionPlan(
    val modelFile: String,
    val modelFileBytes: Long,
    val accelerator: String,
    val memoryAdmission: MemoryAdmission,
    val warnings: Set<LiteRtPlanWarning>,
) {
    val allowed: Boolean get() = memoryAdmission is MemoryAdmission.Ok
}

object LiteRtExecutionPlanner {
    private const val GIB = 1024L * 1024L * 1024L

    fun plan(
        config: LiteRtModelConfig,
        modelFileBytes: Long,
        profile: DeviceExecutionProfile,
        cachedAccelerator: String? = null,
        forceCpu: Boolean = false,
    ): LiteRtExecutionPlan {
        val actualBytes = modelFileBytes.coerceAtLeast(0L)
        val admission = decideMemoryAdmission(actualBytes, profile.availableMemoryBytes)
        val warnings = buildSet {
            if (profile.lowMemory) add(LiteRtPlanWarning.SYSTEM_LOW_MEMORY)
            val publishedMinimum = config.minDeviceMemoryGb.toLong() * GIB
            if (profile.totalMemoryBytes in 1 until publishedMinimum) {
                add(LiteRtPlanWarning.BELOW_CATALOG_MINIMUM)
            }
        }
        return LiteRtExecutionPlan(
            modelFile = config.modelFile,
            modelFileBytes = actualBytes,
            accelerator = resolveAccelerator(
                config = config,
                capabilities = profile.capabilities,
                cachedAccelerator = cachedAccelerator,
                forceCpu = forceCpu,
            ),
            memoryAdmission = admission,
            warnings = warnings,
        )
    }

    /**
     * Pick a general-chat default from entries explicitly curated as recommended.
     *
     * Hard filter: the model must fit current free RAM. Published total-RAM minimums are
     * advisory: prefer entries whose band matches the device, but don't reject a model when
     * live memory evidence says it fits. If Android reports low-memory pressure, prefer the
     * smaller fitting model instead of greedily selecting the largest one.
     */
    fun recommend(
        entries: List<LiteRtCatalogEntry>,
        profile: DeviceExecutionProfile,
    ): LiteRtCatalogEntry? {
        val candidates = entries
            .asSequence()
            .filter { it.recommended }
            .map { entry ->
                val plan = plan(
                    config = entry.config(),
                    modelFileBytes = entry.sizeBytes,
                    profile = profile,
                )
                entry to plan
            }
            .filter { (_, plan) -> plan.allowed }
            .toList()
        if (candidates.isEmpty()) return null

        if (profile.lowMemory) {
            return candidates.minByOrNull { (entry, _) -> entry.sizeBytes }?.first
        }

        val inPublishedBand = candidates.filter { (_, plan) ->
            LiteRtPlanWarning.BELOW_CATALOG_MINIMUM !in plan.warnings
        }
        val pool = if (inPublishedBand.isNotEmpty()) inPublishedBand else candidates
        return pool.maxWithOrNull(
            compareBy<Pair<LiteRtCatalogEntry, LiteRtExecutionPlan>>(
                { it.first.minDeviceMemoryGb },
                { it.first.sizeBytes },
            )
        )?.first
    }

    internal fun resolveAccelerator(
        config: LiteRtModelConfig,
        capabilities: ComputeCapabilities,
        cachedAccelerator: String?,
        forceCpu: Boolean,
    ): String {
        if (forceCpu) return "CPU"

        normalize(cachedAccelerator)
            ?.takeIf { isSupported(it, capabilities) }
            ?.let { return it }

        val preferred = expandPreferences(config.preferredAccelerators)
        preferred.firstOrNull { isSupported(it, capabilities) }?.let { return it }

        return pickLiteRt(capabilities)
    }

    private fun expandPreferences(preferences: List<String>): List<String> {
        if (preferences.isEmpty()) return emptyList()
        return buildList {
            preferences.forEach { raw ->
                when (normalize(raw)) {
                    // Preserve RikkaHub's proven Qualcomm/QNN and NNAPI ladder around
                    // Gallery's generic "gpu" preference.
                    "GPU" -> {
                        add("QNN")
                        add("GPU")
                    }
                    "CPU" -> add("CPU")
                    "QNN" -> add("QNN")
                    "NNAPI" -> add("NNAPI")
                    "NPU" -> add("NPU")
                    else -> Unit
                }
            }
        }.distinct()
    }

    private fun isSupported(accelerator: String, caps: ComputeCapabilities): Boolean = when (accelerator) {
        "CPU" -> true
        "GPU" -> caps.gpuDelegateSupported
        "QNN" -> caps.isQualcomm && caps.qnnLibrarySupported
        "NPU" -> caps.npuSupported
        else -> false
    }

    private fun normalize(value: String?): String? = value
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.isNotEmpty() }
}

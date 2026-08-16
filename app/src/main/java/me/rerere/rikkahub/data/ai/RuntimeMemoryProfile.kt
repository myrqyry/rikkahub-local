package me.rerere.rikkahub.data.ai

/**
 * Phase: runtime memory admission (image-gen refinement Task 4).
 *
 * A conservative estimate of the resident memory a local diffusion run needs, and a budget
 * derived from what the device can actually spare — not from total RAM. Admission compares
 * the profile sum plus a safety margin against the budget.
 */
data class RuntimeMemoryProfile(
    val modelResidentEstimate: Long,
    val workspaceEstimate: Long,
    val outputEstimate: Long,
    val safetyMargin: Long,
) {
    val requiredBytes: Long
        get() = modelResidentEstimate + workspaceEstimate + outputEstimate + safetyMargin

    fun fitsIn(budget: Long): Boolean = requiredBytes <= budget
}

/** Bytes available to the model after Android reserves and the app's known working set. */
fun estimateRuntimeBudget(availMem: Long, androidReserve: Long, knownWorkingSet: Long): Long =
    (availMem - androidReserve - knownWorkingSet).coerceAtLeast(0L)

/**
 * Conservative workspace estimate: latents + intermediate buffers scale with pixel area and
 * steps. Doubling linear dimensions quadruples the estimate (guards against over-admission).
 */
fun workspaceEstimateBytes(width: Int, height: Int, steps: Int = 20, bytesPerPixel: Long = 16L): Long =
    width.toLong() * height.toLong() * bytesPerPixel * steps.coerceAtLeast(1)

/** Output buffer estimate: RGBA pixels for every generated image. */
fun outputEstimateBytes(width: Int, height: Int, batch: Int = 1, bytesPerPixel: Long = 4L): Long =
    width.toLong() * height.toLong() * bytesPerPixel * batch.coerceAtLeast(1)

package me.rerere.rikkahub.data.ai

/**
 * Phase: runtime memory admission (image-gen refinement Task 4).
 *
 * A conservative estimate of the resident memory a local diffusion run needs, and a budget
 * derived from what the device can actually spare — not from total RAM. Admission compares
 * the profile sum (estimates plus an explicit safety margin) against the budget.
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

/**
 * Modest extra headroom above the model/buffer estimates, so small estimator errors or
 * transient allocations during sampling do not push us over Android's low-memory line.
 * Kept here — visible and testable — instead of hidden as another reservation inside the
 * device budget.
 */
internal const val SD_SAFETY_MARGIN_BYTES = 256L * 1024L * 1024L

/**
 * Generation budget: what the device can spare without tripping Android's low-memory
 * threshold. [availMem] is what is free right now; [thresholdBytes] is the availMem value at
 * which Android begins reclaiming background processes, so anything above it is safe to hand
 * to native code. The Java heap ceiling is deliberately not subtracted here: `maxMemory()` is
 * a hypothetical maximum the JVM may attempt to use, not the app's current working set.
 */
fun estimateRuntimeBudget(availMem: Long, thresholdBytes: Long): Long =
    (availMem - thresholdBytes).coerceAtLeast(0L)

/**
 * Conservative workspace estimate: latents + intermediate buffers scale with pixel area and
 * steps. Doubling linear dimensions quadruples the estimate (guards against over-admission).
 */
fun workspaceEstimateBytes(width: Int, height: Int, steps: Int = 20, bytesPerPixel: Long = 16L): Long =
    width.toLong() * height.toLong() * bytesPerPixel * steps.coerceAtLeast(1)

/** Output buffer estimate: RGBA pixels for every generated image. */
fun outputEstimateBytes(width: Int, height: Int, batch: Int = 1, bytesPerPixel: Long = 4L): Long =
    width.toLong() * height.toLong() * bytesPerPixel * batch.coerceAtLeast(1)

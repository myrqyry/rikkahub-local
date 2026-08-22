package me.rerere.locallm

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import me.rerere.locallm.litert.compute.ComputeCapabilities
import me.rerere.locallm.litert.compute.pickLiteRt
import me.rerere.locallm.litert.compute.pickTaskAccelerator

/**
 * Android adapter that reads live device state and feeds the pure accelerator
 * decision functions in [me.rerere.locallm.litert.compute] — the compute seam
 * itself holds no Android types.
 *
 * The cached choice persists in [LocalRuntimePreferences]; the probe runs once
 * on first model load and again only when the user taps "Re-detect".
 */
object AcceleratorProbe {

    /**
     * Whether LiteRT should DEFAULT to forcing CPU (GPU opt-in) on a device, before the
     * user has expressed any preference in the "Try GPU acceleration" toggle.
     *
     * GPU is the fast path and works on the vast majority of devices - typically 3-10x
     * faster than CPU for these models. The one known-bad class is Google Tensor (Pixel
     * 6 and later): LiteRT-LM 0.11.0's GPU/NNAPI path has a native SIGSEGV there. For
     * that SoC family we keep CPU as the safe default; every other device defaults to
     * GPU and gets the speedup out of the box.
     *
     * This is only the *initial* default. The per-device crash sweep in RikkaHubApp still
     * backstops any device that crashes anyway by persisting forceCpu=true, and the
     * runtime's own GPU->CPU fallback handles a GPU that fails to initialise. So a wrong
     * guess here self-corrects; it is never load-bearing for safety.
     *
     * @param socManufacturer `Build.SOC_MANUFACTURER` (API 31+), or null on older devices.
     * @param socModel `Build.SOC_MODEL` (API 31+), or null on older devices.
     */
    fun defaultForceCpu(socManufacturer: String?, socModel: String?): Boolean {
        // Google Tensor SoCs report SOC_MANUFACTURER = "Google"; SOC_MODEL is checked as a
        // belt-and-braces signal ("Tensor G1".."Tensor G5"). Any positive match keeps the
        // conservative CPU default. Everything else - including pre-API-31 devices where
        // both args are null (no Google Tensor device runs an OS that old) - gets GPU.
        return socManufacturer?.equals("Google", ignoreCase = true) == true ||
            socModel?.contains("Tensor", ignoreCase = true) == true
    }

    /**
     * Read the live device capabilities for the LiteRT runtime. Production callers
     * use this; unit tests pass synthesised [ComputeCapabilities] to [pickLiteRt]
     * directly.
     *
     * @param forceCpu short-circuits to "CPU" without probing — set when the user has
     *   the "Try GPU acceleration" toggle off, OR when the auto-recovery sweep saw a
     *   prior native crash inside liblitertlm and flipped the flag for us.
     */
    fun probeLiteRt(context: Context, forceCpu: Boolean = false): String {
        if (forceCpu) return "CPU"
        val isQualcomm = Build.HARDWARE.contains("qcom", ignoreCase = true) ||
            Build.MANUFACTURER.equals("Qualcomm", ignoreCase = true)
        val qnnLibrarySupported = isQualcomm && runCatching {
            // The QNN delegate is bundled in the LiteRT-LM AAR (litertlm-android). Attempting to
            // load it eagerly fails fast on non-Qualcomm devices or where the right ABI is absent.
            // A failed load leaves the class loader in a partially-initialised state for that
            // library name, but Android's JNI loader is idempotent for subsequent real loads of the
            // same name by the actual runtime — the side effect is acceptable.
            System.loadLibrary("qnn_delegate_jni")
            true
        }.getOrDefault(false)
        // FEATURE_OPENGLES_EXTENSION_PACK is a reasonable proxy for GPU-delegate capability but
        // is only advisory — the LiteRT-LM runtime may still fail to initialise the GPU backend
        // at model-load time even if this returns true. The AcceleratorProbe is therefore
        // intentionally optimistic: prefer GPU when the feature flag suggests it's present, and
        // let the runtime's own error path trigger a re-probe if load fails.
        val gpuDelegateSupported = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_OPENGLES_EXTENSION_PACK,
        )
        val nnapiSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
        return pickLiteRt(
            ComputeCapabilities(
                isQualcomm = isQualcomm,
                qnnLibrarySupported = qnnLibrarySupported,
                gpuDelegateSupported = gpuDelegateSupported,
                nnapiSupported = nnapiSupported,
            )
        )
    }

    /** True when the device can run task models on the NPU via JIT: SDK >= 31 AND the
     *  built-in NPU accelerator provider reports both device support and that the vendor
     *  runtime libs are present. Never throws — any failure means "no NPU". */
    fun probeTaskNpu(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            // One-arg ctor uses the default compatibility checker internally.
            val provider = BuiltinNpuAcceleratorProvider(context)
            provider.isDeviceSupported() && provider.isLibraryReady()
        }.getOrDefault(false)
    }

    /** Production probe for the task-model accelerator label: NPU when libs present,
     *  else the classic GPU/NNAPI/CPU ladder. Cached by the caller (Task 3). */
    fun probeTaskAccelerator(context: Context): String {
        val npu = probeTaskNpu(context)
        val gpu = context.packageManager.hasSystemFeature(PackageManager.FEATURE_OPENGLES_EXTENSION_PACK)
        val nnapi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
        return pickTaskAccelerator(
            ComputeCapabilities(
                isQualcomm = false,
                qnnLibrarySupported = false,
                gpuDelegateSupported = gpu,
                nnapiSupported = nnapi,
                npuSupported = npu,
            )
        )
    }
}

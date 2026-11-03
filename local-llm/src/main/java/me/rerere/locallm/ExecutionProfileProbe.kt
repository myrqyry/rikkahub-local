package me.rerere.locallm

import android.app.ActivityManager
import android.content.Context
import me.rerere.locallm.litert.compute.DeviceExecutionProfile

/**
 * Android adapter for [DeviceExecutionProfile].
 *
 * Memory is read live for every capture. Accelerator capabilities are process-cached by
 * [AcceleratorProbe] because loading/probing delegate libraries on every chat turn is wasteful;
 * Settings -> Re-detect can request a fresh accelerator probe explicitly.
 */
object ExecutionProfileProbe {
    fun capture(
        context: Context,
        refreshAccelerators: Boolean = false,
    ): DeviceExecutionProfile {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        return DeviceExecutionProfile(
            totalMemoryBytes = memory.totalMem,
            availableMemoryBytes = memory.availMem,
            lowMemory = memory.lowMemory,
            capabilities = AcceleratorProbe.probeLiteRtCapabilities(
                context = context,
                refresh = refreshAccelerators,
            ),
        )
    }
}

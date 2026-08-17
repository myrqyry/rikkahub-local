package me.rerere.locallm.litert.compute

import me.rerere.locallm.AcceleratorProbe
import me.rerere.locallm.litert.CapabilityGrant

class ComputeSession private constructor(
    val ref: ComputeRef,
    private val gate: ComputeGate,
) {
    enum class State { IDLE, LOADED, BUSY, RELEASED, TERMINATED }

    @Volatile
    private var currentState = State.IDLE

    val state get() = currentState
    val isClosed get() = currentState == State.RELEASED || currentState == State.TERMINATED

    fun dispatch(
        command: ComputeCommand,
        granted: CapabilityGrant? = null,
        capabilities: AcceleratorProbe.LiteRtCapabilities? = null,
        availMemBytes: Long = 0L,
    ): List<ComputeObservation> {
        if (isClosed) return listOf(ComputeObservation.CommandRefused("session closed"))
        val decision = gate.evaluate(command, granted, capabilities, availMemBytes)
        if (!decision.allowed) {
            return listOf(ComputeObservation.CommandRefused(decision.reason ?: "command_denied"))
        }
        return when (command) {
            is ComputeCommand.Load -> if (currentState == State.IDLE) {
                currentState = State.LOADED
                listOf(ComputeObservation.Loaded(command.modelId))
            } else {
                listOf(ComputeObservation.CommandRefused("load not valid in ${currentState.name}"))
            }
            is ComputeCommand.Execute -> if (currentState == State.LOADED) {
                currentState = State.BUSY
                listOf(ComputeObservation.ExecutionStarted(command.modelId, command.operation))
            } else {
                listOf(ComputeObservation.CommandRefused("execute not valid in ${currentState.name}"))
            }
            is ComputeCommand.Release -> if (currentState == State.IDLE || currentState == State.LOADED) {
                currentState = State.RELEASED
                listOf(ComputeObservation.Released(command.modelId))
            } else {
                listOf(ComputeObservation.CommandRefused("release not valid in ${currentState.name}"))
            }
            ComputeCommand.Shutdown -> {
                currentState = State.TERMINATED
                listOf(ComputeObservation.ShutdownComplete)
            }
        }
    }

    fun observeExecutionCompleted(modelId: String, operation: String, outputBytes: Long): List<ComputeObservation> =
        if (currentState == State.BUSY) {
            currentState = State.LOADED
            listOf(ComputeObservation.ExecutionCompleted(modelId, operation, outputBytes))
        } else emptyList()

    fun observeExecutionFailed(modelId: String, operation: String, detail: String): List<ComputeObservation> =
        if (currentState == State.BUSY) {
            currentState = State.LOADED
            listOf(ComputeObservation.ExecutionFailed(modelId, operation, detail))
        } else emptyList()

    fun close(): List<ComputeObservation> = when {
        currentState == State.RELEASED || currentState == State.TERMINATED -> emptyList()
        else -> {
            currentState = State.TERMINATED
            listOf(ComputeObservation.Evicted("closed"))
        }
    }

    companion object {
        fun create(id: String, gate: ComputeGate = ComputeGate()): ComputeSession =
            ComputeSession(ComputeRef(id), gate)
    }
}

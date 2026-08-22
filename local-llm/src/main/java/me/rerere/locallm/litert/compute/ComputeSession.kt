package me.rerere.locallm.litert.compute

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

    internal val commandsLedger = mutableListOf<String>()
    internal val effectsLedger = mutableSetOf<ComputeEffect>()
    internal val refusalsLedger = mutableListOf<String>()

    fun dispatch(
        command: ComputeCommand,
        granted: CapabilityGrant? = null,
        capabilities: ComputeCapabilities? = null,
        availMemBytes: Long = 0L,
    ): List<ComputeObservation> {
        val commandName = command::class.simpleName ?: "command"
        if (isClosed) {
            commandsLedger.add(commandName)
            refusalsLedger.add("session closed")
            return listOf(ComputeObservation.CommandRefused("session closed"))
        }
        val decision = gate.evaluate(command, granted, capabilities, availMemBytes)
        if (!decision.allowed) {
            commandsLedger.add(commandName)
            refusalsLedger.add(decision.reason ?: "command_denied")
            return listOf(ComputeObservation.CommandRefused(decision.reason ?: "command_denied"))
        }
        fun commit(observation: ComputeObservation): List<ComputeObservation> {
            commandsLedger.add(commandName)
            decision.effect?.let { effectsLedger.add(it) }
            return listOf(observation)
        }
        fun refuse(reason: String): List<ComputeObservation> {
            commandsLedger.add(commandName)
            refusalsLedger.add(reason)
            return listOf(ComputeObservation.CommandRefused(reason))
        }
        return when (command) {
            is ComputeCommand.Load -> if (currentState == State.IDLE) {
                currentState = State.LOADED
                commit(ComputeObservation.Loaded(command.modelId))
            } else {
                refuse("load not valid in ${currentState.name}")
            }
            is ComputeCommand.Execute -> if (currentState == State.LOADED) {
                currentState = State.BUSY
                commit(ComputeObservation.ExecutionStarted(command.modelId, command.operation))
            } else {
                refuse("execute not valid in ${currentState.name}")
            }
            is ComputeCommand.Release -> if (currentState == State.IDLE || currentState == State.LOADED) {
                currentState = State.RELEASED
                commit(ComputeObservation.Released(command.modelId))
            } else {
                refuse("release not valid in ${currentState.name}")
            }
            ComputeCommand.Shutdown -> {
                currentState = State.TERMINATED
                commit(ComputeObservation.ShutdownComplete)
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

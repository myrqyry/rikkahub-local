package me.rerere.locallm.litert.mesh

/**
 * Canonical orchestration topics (roadmap D2). Micro-agents coordinate through these exact
 * wire values; roles never improvise English phrases. Later domains (evidence.*, artifact.*,
 * workspace.*, browser.*) may add their own namespaces, but not in the D1 slice.
 */
object MicroAgentTopics {
    const val PLAN_REQUESTED = "plan.requested"
    const val PLAN_PROPOSED = "plan.proposed"
    const val PLAN_REVIEWED = "plan.reviewed"

    const val EXECUTION_REQUESTED = "execution.requested"
    const val EXECUTION_STARTED = "execution.started"
    const val EXECUTION_COMPLETED = "execution.completed"
    const val EXECUTION_FAILED = "execution.failed"

    const val VERIFICATION_REQUESTED = "verification.requested"
    const val VERIFICATION_COMPLETED = "verification.completed"
    const val VERIFICATION_FAILED = "verification.failed"

    const val ORCHESTRATION_COMPLETED = "orchestration.completed"
    const val ORCHESTRATION_FAILED = "orchestration.failed"
    const val ORCHESTRATION_CANCELLED = "orchestration.cancelled"
}

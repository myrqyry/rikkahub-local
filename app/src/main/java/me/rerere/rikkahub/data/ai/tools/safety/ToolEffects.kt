package me.rerere.rikkahub.data.ai.tools.safety

import java.security.MessageDigest

@Suppress("EnumEntryName")
enum class ToolEffect {
    READ_LOCAL_DATA,
    WRITE_LOCAL_DATA,
    DELETE_LOCAL_DATA,
    ACCESS_SENSITIVE_DATA,
    SEND_NETWORK_REQUEST,
    UPLOAD_DATA,
    SHARE_EXTERNALLY,
    EXECUTE_CODE,
    INSTALL_COMPONENT,
    MODIFY_CONFIGURATION,
    SEND_MESSAGE,
}

data class DataEgress(
    val category: String,
    val destination: String,
    val scope: String,
)

data class ResourceMutation(
    val resource: String,
    val operation: String,
)

enum class PrivilegeLevel {
    NORMAL,
    ELEVATED,
    SENSITIVE,
}

data class ExecutionProvenance(
    val source: String,
    val assistantId: String? = null,
)

data class ToolExecutionPlan(
    val operationId: String,
    val toolName: String,
    val effects: Set<ToolEffect>,
    val dataEgress: List<DataEgress> = emptyList(),
    val resourceMutations: List<ResourceMutation> = emptyList(),
    val privilegeLevel: PrivilegeLevel = PrivilegeLevel.NORMAL,
    val inputSummary: String? = null,
    val provenance: ExecutionProvenance,
) {
    /**
     * The canonical representation of everything an approval actually grants.
     *
     * Covers every execution-relevant property so an approval binds to exactly the
     * operation that was reviewed: operation identity, the tool name, the full effect
     * set, external data egress, resource mutations, privilege level, the input
     * summary, and provenance. Fields are NUL-separated so no field value can
     * ambiguously merge with the next.
     *
     * Canonicalization rules (deterministic regardless of construction order):
     * - sets are sorted by stable wire name ([effects]);
     * - lists preserve their meaningful order ([dataEgress], [resourceMutations]);
     * - enums use stable wire names ([privilegeLevel]);
     * - nulls are represented explicitly.
     *
     * Note: [inputSummary] is a deterministic digest input even though callers may
     * compute it from a redacted summary; changing any security-relevant field (or the
     * reviewed input identity) produces a different digest and invalidates a prior
     * approval.
     */
    fun digest(): String {
        val canonical = buildString {
            append(operationId)
            append(SEP)
            append(toolName)
            append(SEP)
            append(effects.map { it.name }.sorted().joinToString(","))
            append(SEP)
            append(dataEgress.joinToString("|") { "${it.category}:${it.destination}:${it.scope}" })
            append(SEP)
            append(resourceMutations.joinToString("|") { "${it.resource}:${it.operation}" })
            append(SEP)
            append(privilegeLevel.name)
            append(SEP)
            append(inputSummary ?: "null")
            append(SEP)
            append(provenance.source)
            append(SEP)
            append(provenance.assistantId ?: "null")
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val SEP = '\u0000'
    }
}

enum class SafetyDecision {
    ALLOW,
    REQUIRE_APPROVAL,
    DENY,
}

enum class ApprovalRequirement {
    NONE,
    USER,
}

enum class DecisionSource {
    DETERMINISTIC_POLICY,
    MODEL_REVIEW,
    USER,
}

data class ToolSafetyDecision(
    val decision: SafetyDecision,
    val reasons: List<String>,
    val requiredApproval: ApprovalRequirement,
    val decidedBy: DecisionSource,
)

data class ApprovedToolOperation(
    val operationId: String,
    val planDigest: String,
    val approvedAtMs: Long,
    val approvalSource: String,
)

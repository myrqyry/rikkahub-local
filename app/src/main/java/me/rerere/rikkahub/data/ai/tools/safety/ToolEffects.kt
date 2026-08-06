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
    fun digest(): String {
        val normalized = buildString {
            append(operationId).append('|')
            append(toolName).append('|')
            effects.map(ToolEffect::name).sorted().joinTo(this, ",")
            append('|')
            dataEgress.sortedWith(compareBy(DataEgress::category, DataEgress::destination, DataEgress::scope))
                .joinTo(this, ",") { "${it.category}:${it.destination}:${it.scope}" }
            append('|').append(resourceMutations.sortedWith(compareBy(ResourceMutation::resource, ResourceMutation::operation)))
            append('|').append(privilegeLevel.name)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
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

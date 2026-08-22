package me.rerere.rikkahub.ui.pages.models

import me.rerere.rikkahub.data.modelregistry.ModelAssignments
import me.rerere.rikkahub.data.modelregistry.ModelDescriptor
import me.rerere.rikkahub.data.modelregistry.ModelRole

data class AssignmentSummaryRow(
    val role: ModelRole,
    val model: ModelDescriptor?,
)

fun defaultAssignmentsSummary(
    assignments: ModelAssignments,
    models: List<ModelDescriptor>,
): List<AssignmentSummaryRow> {
    val byId = models.associateBy { it.id }
    return ModelRole.entries.mapNotNull { role ->
        val modelId = assignments.defaults[role] ?: return@mapNotNull null
        byId[modelId]?.let { AssignmentSummaryRow(role, it) }
    }
}

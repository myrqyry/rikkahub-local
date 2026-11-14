package me.rerere.rikkahub.data.ai.tools.local

/**
 * Small, pure postcondition for an accessibility action. A tool dispatch result is
 * not evidence that the requested UI state was reached.
 */
internal data class UiPostcondition(
    val expectedText: String? = null,
    val absentText: String? = null,
    val expectedPackage: String? = null,
) {
    fun matches(state: UiObservedState?): Boolean =
        state != null &&
            (expectedText != null || absentText != null || expectedPackage != null) &&
            (expectedText == null || expectedText in state.visibleTexts) &&
            (absentText == null || absentText !in state.visibleTexts) &&
            (expectedPackage == null || expectedPackage == state.packageName)
}

internal data class UiObservedState(
    val packageName: String?,
    val visibleTexts: Set<String>,
)

internal enum class UiVerificationStatus(val wireValue: String) {
    PASSED("passed"),
    ALREADY_SATISFIED("already_satisfied"),
    NOT_OBSERVED("not_observed"),
    ACTION_REJECTED("action_rejected"),
}

internal fun assessUiPostcondition(
    actionAccepted: Boolean,
    initiallySatisfied: Boolean,
    observedSatisfied: Boolean,
): UiVerificationStatus = when {
    !actionAccepted -> UiVerificationStatus.ACTION_REJECTED
    !observedSatisfied -> UiVerificationStatus.NOT_OBSERVED
    initiallySatisfied -> UiVerificationStatus.ALREADY_SATISFIED
    else -> UiVerificationStatus.PASSED
}

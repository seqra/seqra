package org.opentaint.semgrep.pattern

import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Category
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Severity

object SemgrepLoadIssueMessages {
    private const val RULE_ISSUE_ACTION = "Fix this Semgrep rule and rerun rule loading."
    private const val UNSUPPORTED_FEATURE_ACTION = "Rule contains unsupported features."
    private const val INTERNAL_WARNING_ACTION = "Internal warning: no user action is required."

    private const val BLOCKING_IMPACT = "Processing of this rule stopped."
    private const val NON_BLOCKING_IMPACT = "Processing continues with best-effort fallback."

    private const val DETAILS_LABEL = "Details"
    private const val IMPACT_LABEL = "Impact"

    fun format(
        category: Category,
        severity: Severity,
        message: String,
    ): String {
        val action = when (category) {
            Category.RULE_ISSUE -> RULE_ISSUE_ACTION
            Category.UNSUPPORTED_FEATURE -> UNSUPPORTED_FEATURE_ACTION
            Category.INTERNAL_WARNING -> INTERNAL_WARNING_ACTION
        }

        val impact = when (severity) {
            Severity.BLOCKING -> BLOCKING_IMPACT
            Severity.NON_BLOCKING -> NON_BLOCKING_IMPACT
        }

        return "$action $IMPACT_LABEL: $impact $DETAILS_LABEL: $message."
    }
}

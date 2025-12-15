package org.opentaint.semgrep.pattern

object SemgrepRuleUtils {
    fun getRuleId(ruleSetName: String, id: String): String {
        return "$ruleSetName:$id"
    }
}

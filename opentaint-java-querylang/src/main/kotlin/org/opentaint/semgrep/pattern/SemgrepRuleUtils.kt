package org.opentaint.semgrep.pattern

object SemgrepRuleUtils {
    fun getRuleId(ruleSetName: String, id: String): String {
        return "$ruleSetName$SEPARATOR$id"
    }

    fun extractRuleSetNameAndId(ruleId: String): Pair<String, String>? {
        val separatorPos = ruleId.indexOf(SEPARATOR)
        if (separatorPos == -1) return null

        val ruleSetName = ruleId.substring(0, separatorPos)
        val rawRuleId = ruleId.substring(separatorPos + 1)
        return ruleSetName to rawRuleId
    }

    private const val SEPARATOR = ':'
}

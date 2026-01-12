package org.opentaint.org.opentaint.semgrep.pattern

import mu.KLogging

sealed interface Mark {
    data class StringMark(val mark: String) : Mark

    data object ArtificialMark : Mark

    data object StateMark : Mark

    data object TaintMark : Mark

    companion object {
        const val ArtificialMetavarName = "<ARTIFICIAL>"
        const val ArtificialStateName = "__<STATE>__"
        const val GeneralTaintName = "taint"
        const val GeneralTaintLabelPrefix = "taint_"
        const val MarkSeparator = '|'

        val logger = object : KLogging() {}.logger

        fun getMarkFromString(rawMark: String, ruleId: String): Mark {
            if (!rawMark.contains('#'))
            // running with config
                return StringMark(rawMark)
            val ruleLength = ruleId.length
            if (!(rawMark.length > ruleLength && rawMark[ruleLength] == '#')) {
                logger.error { "expected ruleId at the start of mark!" }
                return TaintMark
            }
            val noRuleId = rawMark.substring(ruleLength + 1)
            if (noRuleId.startsWith(GeneralTaintLabelPrefix))
                return StringMark(noRuleId.substringAfter(GeneralTaintLabelPrefix))
            if (noRuleId == GeneralTaintName)
                return TaintMark
            if (noRuleId.contains(ArtificialStateName))
                return StateMark
            if (noRuleId.contains(ArtificialMetavarName))
                return ArtificialMark
            val split = noRuleId.split(MarkSeparator)
            if (split.size < 2) {
                logger.error { "mark must contain at least two parts!" }
                return TaintMark
            }
            return StringMark(split[1])
        }
    }
}

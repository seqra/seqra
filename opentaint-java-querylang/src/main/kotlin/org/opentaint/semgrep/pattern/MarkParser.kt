package org.opentaint.org.opentaint.semgrep.pattern

import mu.KLogging
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

sealed interface Mark {
    data class StringMark(val mark: String) : Mark

    data object ArtificialMark : Mark

    data object StateMark : Mark

    data object TaintMark : Mark

    fun isRuleDefined() = when (this) {
        is StringMark,
            is TaintMark -> true
        is ArtificialMark,
            is StateMark -> false
    }

    fun isInternallyDefined() = !isRuleDefined()

    companion object {
        const val ArtificialMetavarName = "<ARTIFICIAL>"
        const val ArtificialStateName = "__<STATE>__"
        const val GeneralTaintName = "taint"
        const val GeneralTaintLabelPrefix = "taint_"
        const val MarkSeparator = '|'
        const val SquishedSeparator = '&'
        const val RuleIdSeparator = '#'

        val logger = object : KLogging() {}.logger

        fun markNamePrefix(shortRuleId: String, prefix: String) =
            "$shortRuleId${RuleIdSeparator}$prefix"

        fun getMarkFromString(rawMark: String, ruleId: String): Mark {
            val markRuleId = rawMark.substringBefore(RuleIdSeparator, missingDelimiterValue = "")
            if (markRuleId.isBlank()) {
                // running with config
                return StringMark(rawMark)
            }

            if (!ruleId.endsWith(markRuleId)) {
                logger.error { "expected ruleId at the start of mark!" }
                return TaintMark
            }

            val noRuleId = rawMark.substringAfter(RuleIdSeparator)
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
            return StringMark(split[1].split(SquishedSeparator).joinToString(" or "))
        }

        fun InitialFactAp.getMark(ruleId: String): Mark {
            val taintMarks = getAllAccessors().filterIsInstance<TaintMarkAccessor>()
            if (taintMarks.size != 1) {
                logger.error { "Expected exactly one taint mark but got ${taintMarks.size}!" }
            }
            if (taintMarks.isEmpty()) {
                return TaintMark
            }
            return getMarkFromString(taintMarks.first().mark, ruleId)
        }
    }
}

package org.opentaint.semgrep.pattern

import org.opentaint.dataflow.jvm.ap.ifds.taint.UserDefinedRuleInfo

data class UserRuleFromSemgrepInfo(
    val ruleId: String,
    override val relevantTaintMarks: Set<String>
) : UserDefinedRuleInfo

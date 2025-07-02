package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.taint.configuration.TaintConfigurationItem

fun interface TaintRuleFilter {
    fun ruleEnabled(rule: TaintConfigurationItem): Boolean
}

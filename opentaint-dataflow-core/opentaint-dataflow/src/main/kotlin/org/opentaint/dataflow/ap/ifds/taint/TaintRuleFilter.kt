package org.opentaint.dataflow.ap.ifds.taint

import org.opentaint.ir.taint.configuration.TaintConfigurationItem

fun interface TaintRuleFilter {
    fun ruleEnabled(rule: TaintConfigurationItem): Boolean
}

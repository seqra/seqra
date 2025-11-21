package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem


fun interface TaintRuleFilter {
    fun ruleEnabled(rule: TaintConfigurationItem): Boolean
}

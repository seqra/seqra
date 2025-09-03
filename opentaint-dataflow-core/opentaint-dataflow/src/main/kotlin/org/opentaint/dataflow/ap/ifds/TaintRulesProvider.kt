package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintMark

interface TaintRulesProvider {
    fun taintMarks(): Set<TaintMark>
    fun rulesForMethod(method: CommonMethod): Iterable<TaintConfigurationItem>
}

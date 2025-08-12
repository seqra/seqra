package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintMark

interface TaintRulesProvider {
    fun taintMarks(): Set<TaintMark>
    fun rulesForMethod(method: JIRMethod): Iterable<TaintConfigurationItem>
}

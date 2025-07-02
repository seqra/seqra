package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.taint.configuration.TaintConfigurationItem

interface TaintRulesProvider {
    fun rulesForMethod(method: JIRMethod): Iterable<TaintConfigurationItem>
}

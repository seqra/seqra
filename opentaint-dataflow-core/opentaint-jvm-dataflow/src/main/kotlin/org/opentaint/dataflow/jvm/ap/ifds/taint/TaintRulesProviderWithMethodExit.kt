package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink

class TaintRulesProviderWithMethodExit(
    private val entryPoints: Set<JIRMethod>,
    private val base: TaintRulesProvider
) : TaintRulesProvider by base {
    override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst): Iterable<TaintMethodExitSink> {
        if (method !in entryPoints) return emptyList()

        return base.sinkRulesForMethodExit(method, statement)
    }
}

fun TaintRulesProvider.applyExitSinksOnlyForEntryPoints(entryPoints: Set<JIRMethod>): TaintRulesProvider =
    TaintRulesProviderWithMethodExit(entryPoints, this)

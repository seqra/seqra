package org.opentaint.dataflow.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

interface MethodEntrypointResolver {
    fun resolveEntryPoints(method: CommonMethod, context: MethodContext): List<CommonInst>
}

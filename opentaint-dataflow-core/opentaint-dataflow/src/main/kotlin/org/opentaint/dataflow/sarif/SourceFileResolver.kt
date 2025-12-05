package org.opentaint.dataflow.sarif

import org.opentaint.ir.api.common.cfg.CommonInst

fun interface SourceFileResolver<in Statement : CommonInst> {
    fun resolve(inst: Statement): String?
}

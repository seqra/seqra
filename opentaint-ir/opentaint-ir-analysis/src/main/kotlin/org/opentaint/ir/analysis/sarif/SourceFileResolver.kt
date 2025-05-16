package org.opentaint.ir.analysis.sarif

import org.opentaint.ir.api.common.cfg.CommonInst

fun interface SourceFileResolver<Statement : CommonInst<*, Statement>> {
    fun resolve(inst: Statement): String?
}

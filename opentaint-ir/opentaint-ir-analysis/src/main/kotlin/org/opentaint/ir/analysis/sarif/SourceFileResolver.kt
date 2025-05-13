package org.opentaint.ir.analysis.sarif

import org.opentaint.ir.api.cfg.JIRInst

fun interface SourceFileResolver {
    fun resolve(inst: JIRInst): String?
}

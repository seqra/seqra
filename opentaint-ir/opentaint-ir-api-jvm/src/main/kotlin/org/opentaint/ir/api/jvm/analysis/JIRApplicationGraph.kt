package org.opentaint.ir.api.jvm.analysis

import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst

/**
 * Interface for [JIRApplicationGraph] built with opentaint-ir.
 */
interface JIRApplicationGraph : ApplicationGraph<JIRMethod, JIRInst> {
    override val project: JIRClasspath
}

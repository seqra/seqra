package org.opentaint.jvm.graph

import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.util.analysis.ApplicationGraph

interface JApplicationGraph : ApplicationGraph<JIRMethod, JIRInst> {
    val cp: JIRClasspath

    interface JMethodGraph : ApplicationGraph.MethodGraph<JIRMethod, JIRInst>
}

package org.opentaint.ir.api.jvm.analysis

import org.opentaint.ir.api.core.analysis.ApplicationGraph
import org.opentaint.ir.api.jvm.JIRProject
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst

/**
 * Interface for [ApplicationGraph] built with opentaint-ir.
 */
interface JIRApplicationGraph : ApplicationGraph<JIRMethod, JIRInst> {
    val classpath: JIRProject
}
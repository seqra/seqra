package org.opentaint.jvm.graph

import org.opentaint.ir.api.jvm.JcClasspath
import org.opentaint.ir.api.jvm.JcMethod
import org.opentaint.ir.api.jvm.cfg.JcInst
import org.opentaint.util.analysis.ApplicationGraph

interface JApplicationGraph : ApplicationGraph<JcMethod, JcInst> {
    val cp: JcClasspath
}

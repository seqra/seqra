package org.opentaint.jvm.sast.dataflow.rules

import org.opentaint.dataflow.jvm.ap.ifds.taint.JVMExecutableRuleCatalog

fun TaintConfiguration.originalExecutableRuleCatalog(): JVMExecutableRuleCatalog =
    JVMExecutableRuleCatalog(rawItems(), catalogGeneration)

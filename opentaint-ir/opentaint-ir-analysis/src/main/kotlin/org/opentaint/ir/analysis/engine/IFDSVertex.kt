package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.cfg.JIRInst

data class IFDSVertex<out T: DomainFact>(val statement: JIRInst, val domainFact: T)
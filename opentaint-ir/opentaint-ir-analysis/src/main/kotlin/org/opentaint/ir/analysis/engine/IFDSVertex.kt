package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.cfg.JIRInst

data class IFDSVertex(val statement: JIRInst, val domainFact: DomainFact)
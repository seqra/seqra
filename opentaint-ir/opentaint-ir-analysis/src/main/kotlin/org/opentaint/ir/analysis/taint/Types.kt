package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.Runner
import org.opentaint.ir.analysis.ifds.Vertex

typealias TaintVertex = Vertex<TaintDomainFact>
typealias TaintEdge = Edge<TaintDomainFact>
typealias TaintRunner = Runner<TaintDomainFact>

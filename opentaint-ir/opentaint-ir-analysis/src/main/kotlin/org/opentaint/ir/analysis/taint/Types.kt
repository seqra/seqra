package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.Runner
import org.opentaint.ir.analysis.ifds.Vertex

typealias TaintVertex<Statement> = Vertex<TaintDomainFact, Statement>
typealias TaintEdge<Statement> = Edge<TaintDomainFact, Statement>
typealias TaintRunner<Method, Statement> = Runner<TaintDomainFact, Method, Statement>

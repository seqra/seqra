package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.Runner
import org.opentaint.ir.analysis.ifds.Vertex

typealias TaintVertex<Method, Statement> = Vertex<TaintDomainFact, Method, Statement>
typealias TaintEdge<Method, Statement> = Edge<TaintDomainFact, Method, Statement>
typealias TaintRunner<Method, Statement> = Runner<TaintDomainFact, Method, Statement>

package org.opentaint.ir.analysis.ifds2.taint

import org.opentaint.ir.analysis.ifds2.Edge
import org.opentaint.ir.analysis.ifds2.Runner
import org.opentaint.ir.analysis.ifds2.Vertex

typealias TaintVertex = Vertex<TaintFact>
typealias TaintEdge = Edge<TaintFact>
typealias TaintRunner = Runner<TaintFact>

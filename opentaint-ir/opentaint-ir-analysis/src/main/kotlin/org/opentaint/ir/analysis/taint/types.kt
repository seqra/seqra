package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.Runner
import org.opentaint.ir.analysis.ifds.Vertex

typealias TaintVertex = Vertex<TaintFact>
typealias TaintEdge = Edge<TaintFact>
typealias TaintRunner = Runner<TaintFact>

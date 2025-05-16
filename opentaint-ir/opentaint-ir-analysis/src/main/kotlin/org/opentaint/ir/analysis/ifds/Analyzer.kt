package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

interface Analyzer<Fact, out Event, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    val flowFunctions: FlowFunctions<Fact, Method, Statement>

    fun handleNewEdge(
        edge: Edge<Fact, Method, Statement>,
    ): List<Event>

    fun handleCrossUnitCall(
        caller: Vertex<Fact, Method, Statement>,
        callee: Vertex<Fact, Method, Statement>,
    ): List<Event>
}

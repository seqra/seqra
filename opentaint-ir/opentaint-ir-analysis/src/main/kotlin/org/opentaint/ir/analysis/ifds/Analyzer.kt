package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

interface Analyzer<Fact, out Event, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    val flowFunctions: FlowFunctions<Fact, Method, Statement>

    fun handleNewEdge(
        edge: Edge<Fact, Statement>,
    ): List<Event>

    fun handleCrossUnitCall(
        caller: Vertex<Fact, Statement>,
        callee: Vertex<Fact, Statement>,
    ): List<Event>
}

package org.opentaint.dataflow.python.alias

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.graph.CompactGraph
import org.opentaint.dataflow.graph.MethodInstGraph
import org.opentaint.dataflow.graph.simulateGraph
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.python.PIRInstruction
import org.opentaint.util.analysis.ApplicationGraph

data class PIRInstGraph(
    val statements: List<PIRInstruction>,
    val graph: CompactGraph,
    val initialIdx: Int,
)

fun buildPirInstGraph(
    languageManager: LanguageManager,
    graph: ApplicationGraph<CommonMethod, CommonInst>,
    method: CommonMethod,
    entry: CommonInst,
): PIRInstGraph {
    val mig = MethodInstGraph.build(languageManager, graph, method)
    return PIRInstGraph(
        statements = mig.instructions.map { it as PIRInstruction },
        graph = mig.graph,
        initialIdx = languageManager.getInstIndex(entry),
    )
}

inline fun <reified State : Any> simulatePIG(
    pig: PIRInstGraph,
    initialState: State,
    statesBefore: Array<State?>,
    statesAfter: Array<State?>,
    eval: (PIRInstruction, State) -> State,
    merge: (Int2ObjectMap<State?>) -> State,
) = simulateGraph(
    statesAfter = statesAfter,
    graph = pig.graph,
    initialStmtIdx = pig.initialIdx,
    initialState = initialState,
    merge = { _, states -> merge(states) },
    eval = { idx, state ->
        statesBefore[idx] = state
        val inst = pig.statements[idx]
        eval(inst, state)
    },
)

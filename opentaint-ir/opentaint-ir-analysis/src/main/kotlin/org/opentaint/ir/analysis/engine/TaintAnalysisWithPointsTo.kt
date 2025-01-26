package org.opentaint.ir.analysis.engine

import org.opentaint.ir.analysis.AnalysisEngine
import org.opentaint.ir.analysis.DumpableAnalysisResult
import org.opentaint.ir.analysis.Points2Engine
import org.opentaint.ir.analysis.analyzers.TaintNode
import org.opentaint.ir.analysis.graph.reversed
import org.opentaint.ir.analysis.paths.startsWith
import org.opentaint.ir.analysis.paths.toPath
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.ApplicationGraph
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.ext.cfg.callExpr

class TaintAnalysisWithPointsTo(
    private val graph: ApplicationGraph<JIRMethod, JIRInst>,
    analyzer: Analyzer,
    points2Engine: Points2Engine,
): AnalysisEngine {

    private val forward: IFDSInstance = IFDSInstance(graph, analyzer, points2Engine.obtainDevirtualizer())

    private val backward: IFDSInstance = IFDSInstance(graph.reversed, analyzer.backward, points2Engine.obtainDevirtualizer())

    init {
        // In forward and backward analysis same function will have different entryPoints, so we have to change
        // `from` vertex of pathEdges properly at handover
        fun IFDSEdge<*>.handoverPathEdgeTo(
            instance: IFDSInstance,
            pred: JIRInst?,
            updateActivation: Boolean,
            propZero: Boolean
        ) {
            val (u, v) = this
            val fact = (v.domainFact as? TaintNode) ?: return
            val newFact = if (updateActivation && fact.activation == null) fact.updateActivation(pred) else fact // TODO: think between pred and v.statement
            val newStatement = pred ?: v.statement
            graph.entryPoint(u.statement.location.method).forEach {
                instance.addNewPathEdge(
                    IFDSEdge(
                        IFDSVertex(it, u.domainFact),
                        IFDSVertex(newStatement, newFact)
                    )
                )
                if (propZero) {
                    // Propagating zero fact
                    instance.addNewPathEdge(
                        IFDSEdge(
                            IFDSVertex(it, u.domainFact),
                            IFDSVertex(newStatement, ZEROFact)
                        )
                    )
                }
            }
        }

        // Forward initiates backward analysis and waits until it finishes
        // Backward analysis does not initiate forward one, because it will run with updated queue after the backward finishes
        forward.addListener(object: IFDSInstanceListener {
            override fun onPropagate(e: IFDSEdge<DomainFact>, pred: JIRInst?, factIsNew: Boolean) {
                val fact = e.v.domainFact as? TaintNode ?: return
                if (fact.variable.isOnHeap && factIsNew) {
                    e.handoverPathEdgeTo(backward, pred, updateActivation = true, propZero = true)
                    backward.run()
                }
            }
        })

        backward.addListener(object: IFDSInstanceListener {
            override fun onPropagate(e: IFDSEdge<DomainFact>, pred: JIRInst?, factIsNew: Boolean) {
                val v = e.v
                val curInst = v.statement
                val fact = (v.domainFact as? TaintNode) ?: return
                var canBeKilled = false

                if (!fact.variable.isOnHeap) {
                    return
                }

                if (curInst is JIRAssignInst && fact.variable.startsWith(curInst.lhv.toPath())) {
                    canBeKilled = true
                }

                curInst.callExpr?.let { callExpr ->
                    if (callExpr is JIRInstanceCallExpr && fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
                        canBeKilled = true
                    }
                    callExpr.args.forEach {
                        if (fact.variable.startsWith(it.toPathOrNull())) {
                            canBeKilled = true
                        }
                    }
                }
                if (canBeKilled) {
                    e.handoverPathEdgeTo(forward, pred, updateActivation = false, propZero = false)
                }
            }

            override fun onExitPoint(e: IFDSEdge<DomainFact>) {
                val fact = e.v.domainFact as? TaintNode ?: return
                if (fact.variable.isOnHeap) {
                    e.handoverPathEdgeTo(forward, pred = null, updateActivation = false, propZero = false)
                }
            }
        })
    }

    override fun addStart(method: JIRMethod) = forward.addStart(method)

    override fun analyze(): DumpableAnalysisResult = forward.analyze()
}
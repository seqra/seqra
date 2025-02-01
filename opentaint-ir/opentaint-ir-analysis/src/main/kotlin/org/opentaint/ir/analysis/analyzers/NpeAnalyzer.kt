package org.opentaint.ir.analysis.analyzers

import org.opentaint.ir.analysis.DumpableAnalysisResult
import org.opentaint.ir.analysis.VulnerabilityInstance
import org.opentaint.ir.analysis.engine.Analyzer
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.FlowFunctionsSpace
import org.opentaint.ir.analysis.engine.IFDSResult
import org.opentaint.ir.analysis.engine.IFDSVertex
import org.opentaint.ir.analysis.engine.SpaceId
import org.opentaint.ir.analysis.engine.ZEROFact
import org.opentaint.ir.analysis.paths.AccessPath
import org.opentaint.ir.analysis.paths.ElementAccessor
import org.opentaint.ir.analysis.paths.FieldAccessor
import org.opentaint.ir.analysis.paths.isDereferencedAt
import org.opentaint.ir.analysis.paths.startsWith
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.api.JIRArrayType
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRArgument
import org.opentaint.ir.api.cfg.JIRCallExpr
import org.opentaint.ir.api.cfg.JIRConstant
import org.opentaint.ir.api.cfg.JIREqExpr
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRIfInst
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRNeqExpr
import org.opentaint.ir.api.cfg.JIRNewArrayExpr
import org.opentaint.ir.api.cfg.JIRNewExpr
import org.opentaint.ir.api.cfg.JIRNullConstant
import org.opentaint.ir.api.cfg.JIRValue
import org.opentaint.ir.api.cfg.locals
import org.opentaint.ir.api.ext.fields
import org.opentaint.ir.api.ext.isNullable

class NpeAnalyzer(
    graph: JIRApplicationGraph,
    maxPathLength: Int = 5
) : Analyzer {
    override val flowFunctions: FlowFunctionsSpace = NPEForwardFunctions(graph, maxPathLength)
    override val backward: Analyzer = object : Analyzer {
        override val backward: Analyzer
            get() = this@NpeAnalyzer
        override val flowFunctions: FlowFunctionsSpace
            get() = this@NpeAnalyzer.flowFunctions.backward

        override fun calculateSources(ifdsResult: IFDSResult): DumpableAnalysisResult {
            error("Do not call sources for backward analyzer instance")
        }
    }

    companion object : SpaceId {
        override val value: String = "npe-analysis"
    }

    override fun calculateSources(ifdsResult: IFDSResult): DumpableAnalysisResult {
        val vulnerabilities = mutableListOf<VulnerabilityInstance>()
        ifdsResult.resultFacts.forEach { (inst, facts) ->
            facts.filterIsInstance<NPETaintNode>().forEach { fact ->
                if (fact.activation == null && fact.variable.isDereferencedAt(inst)) {
                    vulnerabilities.add(
                        ifdsResult.resolveTaintRealisationsGraph(IFDSVertex(inst, fact)).toVulnerability(value)
                    )
                }
            }
        }
        return DumpableAnalysisResult(vulnerabilities)
    }
}

private class NPEForwardFunctions(
    graph: JIRApplicationGraph,
    private val maxPathLength: Int
) : AbstractTaintForwardFunctions(graph) {

    override val inIds: List<SpaceId> get() = listOf(NpeAnalyzer, ZEROFact.id)

    private val JIRIfInst.pathComparedWithNull: AccessPath?
        get() {
            val expr = condition
            return if (expr.rhv is JIRNullConstant) {
                expr.lhv.toPathOrNull()?.limit(maxPathLength)
            } else if (expr.lhv is JIRNullConstant) {
                expr.rhv.toPathOrNull()?.limit(maxPathLength)
            } else {
                null
            }
        }

    override fun transmitDataFlow(from: JIRExpr, to: JIRValue, atInst: JIRInst, fact: DomainFact, dropFact: Boolean): List<DomainFact> {
        val default = if (dropFact) emptyList() else listOf(fact)
        val toPath = to.toPathOrNull()?.limit(maxPathLength) ?: return default
        val factPath = when (fact) {
            is NPETaintNode -> fact.variable
            ZEROFact -> null
            else -> return emptyList()
        }

        if (factPath.isDereferencedAt(atInst)) {
            return emptyList()
        }

        if (from is JIRNullConstant || (from is JIRCallExpr && from.method.method.treatAsNullable)) {
            return if (fact == ZEROFact) {
                listOf(ZEROFact, NPETaintNode(toPath)) // taint is generated here
            } else {
                if (factPath.startsWith(toPath)) {
                    emptyList()
                } else {
                    default
                }
            }
        }

        if (from is JIRNewArrayExpr && fact == ZEROFact) {
            val arrayType = from.type as JIRArrayType
            if (arrayType.elementType.nullable != false) {
                val arrayElemPath = AccessPath.fromOther(toPath, List(arrayType.dimensions) { ElementAccessor })
                return listOf(ZEROFact, NPETaintNode(arrayElemPath))
            }
        }

        if (from is JIRNewExpr || from is JIRNewArrayExpr || from is JIRConstant || (from is JIRCallExpr && !from.method.method.treatAsNullable)) {
            return if (factPath.startsWith(toPath)) {
                emptyList() // new kills the fact here
            } else {
                default
            }
        }

        if (fact == ZEROFact) {
            return listOf(ZEROFact)
        }

        fact as NPETaintNode

        // TODO: slightly differs from original paper, think what's correct
        val fromPath = from.toPathOrNull()?.limit(maxPathLength) ?: return default

        return normalFactFlow(fact, fromPath, toPath, dropFact, maxPathLength)
    }

    override fun transmitDataFlowAtNormalInst(inst: JIRInst, nextInst: JIRInst, fact: DomainFact): List<DomainFact> {
        val factPath = when (fact) {
            is NPETaintNode -> fact.variable
            ZEROFact -> null
            else -> return emptyList()
        }

        if (factPath.isDereferencedAt(inst)) {
            return emptyList()
        }

        if (inst !is JIRIfInst) {
            return listOf(fact)
        }

        // Following are some ad-hoc magic for if statements to change facts after instructions like if (x != null)
        val currentBranch = graph.methodOf(inst).flowGraph().ref(nextInst)
        if (fact == ZEROFact) {
            if (inst.pathComparedWithNull != null) {
                if ((inst.condition is JIREqExpr && currentBranch == inst.trueBranch) ||
                    (inst.condition is JIRNeqExpr && currentBranch == inst.falseBranch)) {
                    // This is a hack: instructions like `return null` in branch of next will be considered only if
                    //  the fact holds (otherwise we could not get there)
                    return listOf(NPETaintNode(inst.pathComparedWithNull!!))
                }
            }
            return listOf(ZEROFact)
        }

        fact as NPETaintNode

        // This handles cases like if (x != null) expr1 else expr2, where edges to expr1 and to expr2 should be different
        // (because x == null will be held at expr2 but won't be held at expr1)
        val expr = inst.condition
        val comparedPath = inst.pathComparedWithNull ?: return listOf(fact)

        if ((expr is JIREqExpr && currentBranch == inst.trueBranch) || (expr is JIRNeqExpr && currentBranch == inst.falseBranch)) {
            // comparedPath is null in this branch
            if (fact.variable.startsWith(comparedPath) && fact.activation == null) {
                if (fact.variable == comparedPath) {
                    return listOf(ZEROFact)
                }
                return emptyList()
            }
            return listOf(fact)
        } else {
            // comparedPath is not null in this branch
            if (fact.variable == comparedPath)
                return emptyList()
            return listOf(fact)
        }
    }

    override fun obtainStartFacts(startStatement: JIRInst): Collection<DomainFact> {
        val result = mutableListOf<DomainFact>(ZEROFact)

        val method = startStatement.location.method

        // Note that here and below we intentionally don't expand fields because this may cause
        //  an increase of false positives and significant performance drop

        // Possibly null arguments
        result += method.flowGraph().locals
            .filterIsInstance<JIRArgument>()
            .filter { method.parameters[it.index].isNullable != false }
            .map { NPETaintNode(AccessPath.fromLocal(it)) }

        // Possibly null statics
        // TODO: handle statics in a more general manner
        result += method.enclosingClass.fields
            .filter { it.isNullable != false && it.isStatic }
            .map { NPETaintNode(AccessPath.fromStaticField(it)) }

        val thisInstance = method.thisInstance

        // Possibly null fields
        result += method.enclosingClass.fields
            .filter { it.isNullable != false && !it.isStatic }
            .map {
                NPETaintNode(
                    AccessPath.fromOther(AccessPath.fromLocal(thisInstance), listOf(FieldAccessor(it)))
                )
            }

        return result
    }

    override val backward: FlowFunctionsSpace by lazy { NPEBackwardFunctions(graph, this, maxPathLength) }
}

private class NPEBackwardFunctions(
    graph: JIRApplicationGraph,
    backward: FlowFunctionsSpace,
    maxPathLength: Int,
) : AbstractTaintBackwardFunctions(graph, backward, maxPathLength) {
    override val inIds: List<SpaceId> = listOf(NpeAnalyzer, ZEROFact.id)
}

private val JIRMethod.treatAsNullable: Boolean
    get() {
        if (isNullable == true) {
            return true
        }
        return "${enclosingClass.name}.$name" in knownNullableMethods
    }

private val knownNullableMethods = listOf(
    "java.lang.System.getProperty",
    "java.util.Properties.getProperty"
)
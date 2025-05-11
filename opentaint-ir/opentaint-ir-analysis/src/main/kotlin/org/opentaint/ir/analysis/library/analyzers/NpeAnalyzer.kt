package org.opentaint.ir.analysis.library.analyzers

import org.opentaint.ir.analysis.engine.AbstractAnalyzer
import org.opentaint.ir.analysis.engine.AnalysisDependentEvent
import org.opentaint.ir.analysis.engine.AnalyzerFactory
import org.opentaint.ir.analysis.engine.CrossUnitCallFact
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.EdgeForOtherRunnerQuery
import org.opentaint.ir.analysis.engine.FlowFunctionsSpace
import org.opentaint.ir.analysis.engine.IfdsEdge
import org.opentaint.ir.analysis.engine.NewSummaryFact
import org.opentaint.ir.analysis.engine.VulnerabilityLocation
import org.opentaint.ir.analysis.engine.ZEROFact
import org.opentaint.ir.analysis.paths.AccessPath
import org.opentaint.ir.analysis.paths.ElementAccessor
import org.opentaint.ir.analysis.paths.FieldAccessor
import org.opentaint.ir.analysis.paths.isDereferencedAt
import org.opentaint.ir.analysis.paths.minus
import org.opentaint.ir.analysis.paths.startsWith
import org.opentaint.ir.analysis.paths.toPath
import org.opentaint.ir.analysis.paths.toPathOrNull
import org.opentaint.ir.analysis.sarif.SarifMessage
import org.opentaint.ir.analysis.sarif.VulnerabilityDescription
import org.opentaint.ir.api.JIRArrayType
import org.opentaint.ir.api.JIRClasspath
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
import org.opentaint.ir.api.cfg.values
import org.opentaint.ir.api.ext.fields
import org.opentaint.ir.api.ext.isNullable

fun NpeAnalyzerFactory(maxPathLength: Int) = AnalyzerFactory { graph ->
    NpeAnalyzer(graph, maxPathLength)
}

class NpeAnalyzer(
    graph: JIRApplicationGraph,
    maxPathLength: Int,
) : AbstractAnalyzer(graph) {
    override val flowFunctions: FlowFunctionsSpace = NpeForwardFunctions(graph.classpath, maxPathLength)

    override val isMainAnalyzer: Boolean
        get() = true

    companion object {
        const val ruleId: String = "npe-deref"
    }

    override fun handleNewEdge(edge: IfdsEdge): List<AnalysisDependentEvent> = buildList {
        val (inst, fact0) = edge.to

        if (fact0 is NpeTaintNode && fact0.activation == null && fact0.variable.isDereferencedAt(inst)) {
            val message = "Dereference of possibly-null ${fact0.variable}"
            val desc = VulnerabilityDescription(SarifMessage(message), ruleId)
            add(NewSummaryFact((VulnerabilityLocation(desc, edge.to, edge))))
            verticesWithTraceGraphNeeded.add(edge.to)
        }

        addAll(super.handleNewEdge(edge))
    }

    override fun handleNewCrossUnitCall(fact: CrossUnitCallFact): List<AnalysisDependentEvent> = buildList {
        add(EdgeForOtherRunnerQuery(IfdsEdge(fact.calleeVertex, fact.calleeVertex)))
        addAll(super.handleNewCrossUnitCall(fact))
    }
}

private class NpeForwardFunctions(
    cp: JIRClasspath,
    private val maxPathLength: Int,
) : AbstractTaintForwardFunctions(cp) {

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

    override fun transmitDataFlow(
        from: JIRExpr,
        to: JIRValue,
        atInst: JIRInst,
        fact: DomainFact,
        dropFact: Boolean,
    ): List<DomainFact> {
        val default = if (dropFact && fact != ZEROFact) emptyList() else listOf(fact)
        val toPath = to.toPathOrNull()?.limit(maxPathLength) ?: return default

        if (fact == ZEROFact) {
            return if (from is JIRNullConstant || (from is JIRCallExpr && from.method.method.treatAsNullable)) {
                listOf(ZEROFact, NpeTaintNode(toPath)) // taint is generated here
            } else if (from is JIRNewArrayExpr && (from.type as JIRArrayType).elementType.nullable != false) {
                val accessors = List((from.type as JIRArrayType).dimensions) {
                    ElementAccessor(null)
                }
                val path = (toPath / accessors).limit(maxPathLength)
                listOf(ZEROFact, NpeTaintNode(path))
            } else {
                listOf(ZEROFact)
            }
        }

        if (fact !is NpeTaintNode) {
            return emptyList()
        }

        val factPath = fact.variable
        if (factPath.isDereferencedAt(atInst)) {
            return emptyList()
        }

        if (
            from is JIRNewExpr ||
            from is JIRNewArrayExpr ||
            from is JIRConstant ||
            (from is JIRCallExpr && !from.method.method.treatAsNullable)
        ) {
            return if (factPath.startsWith(toPath)) {
                emptyList() // new kills the fact here
            } else {
                default
            }
        }

        // TODO: slightly differs from original paper, think what's correct
        val fromPath = from.toPathOrNull()?.limit(maxPathLength) ?: return default
        return normalFactFlow(fact, fromPath, toPath, dropFact, maxPathLength)
    }

    override fun transmitDataFlowAtNormalInst(
        inst: JIRInst,
        nextInst: JIRInst,
        fact: DomainFact,
    ): List<DomainFact> {
        val factPath = when (fact) {
            is NpeTaintNode -> fact.variable
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
        val nextInstIsTrueBranch = nextInst.location.index == inst.trueBranch.index
        if (fact == ZEROFact) {
            if (inst.pathComparedWithNull != null) {
                if ((inst.condition is JIREqExpr && nextInstIsTrueBranch) ||
                    (inst.condition is JIRNeqExpr && !nextInstIsTrueBranch)
                ) {
                    // This is a hack: instructions like `return null` in branch of next will be considered only if
                    //  the fact holds (otherwise we could not get there)
                    return listOf(NpeTaintNode(inst.pathComparedWithNull!!))
                }
            }
            return listOf(ZEROFact)
        }

        fact as NpeTaintNode

        // This handles cases like if (x != null) expr1 else expr2, where edges to expr1 and to expr2 should be different
        // (because x == null will be held at expr2 but won't be held at expr1)
        val expr = inst.condition
        if (inst.pathComparedWithNull != fact.variable) {
            return listOf(fact)
        }

        return if ((expr is JIREqExpr && nextInstIsTrueBranch) || (expr is JIRNeqExpr && !nextInstIsTrueBranch)) {
            // comparedPath is null in this branch
            listOf(ZEROFact)
        } else {
            emptyList()
        }
    }

    override fun obtainPossibleStartFacts(startStatement: JIRInst): List<DomainFact> = buildList {
        add(ZEROFact)

        val method = startStatement.location.method

        // Note that here and below we intentionally don't expand fields because this may cause
        //  an increase of false positives and significant performance drop

        // Possibly null arguments
        this += method.flowGraph().locals
            .filterIsInstance<JIRArgument>()
            .filter { method.parameters[it.index].isNullable != false }
            .map { NpeTaintNode(AccessPath.from(it)) }

        // Possibly null statics
        // TODO: handle statics in a more general manner
        this += method.enclosingClass.fields
            .filter { it.isNullable != false && it.isStatic }
            .map { NpeTaintNode(AccessPath.fromStaticField(it)) }

        // Possibly null public non-final fields
        this += method.enclosingClass.fields
            .filter { it.isNullable != false && !it.isStatic && it.isPublic && !it.isFinal }
            .map { NpeTaintNode(AccessPath.from(method.thisInstance) / FieldAccessor(it)) }
    }
}

fun NpePrecalcBackwardAnalyzerFactory(maxPathLength: Int) = AnalyzerFactory { graph ->
    NpePrecalcBackwardAnalyzer(graph, maxPathLength)
}

private class NpePrecalcBackwardAnalyzer(
    graph: JIRApplicationGraph,
    maxPathLength: Int,
) : AbstractAnalyzer(graph) {

    override val flowFunctions: FlowFunctionsSpace = NpePrecalcBackwardFunctions(graph, maxPathLength)

    override val isMainAnalyzer: Boolean
        get() = false

    override fun handleNewEdge(edge: IfdsEdge): List<AnalysisDependentEvent> = buildList {
        if (edge.to.statement in graph.exitPoints(edge.method)) {
            add(EdgeForOtherRunnerQuery((IfdsEdge(edge.to, edge.to))))
        }
    }
}

class NpePrecalcBackwardFunctions(
    graph: JIRApplicationGraph,
    maxPathLength: Int,
) : AbstractTaintBackwardFunctions(graph, maxPathLength) {

    override fun transmitBackDataFlow(
        from: JIRValue,
        to: JIRExpr,
        atInst: JIRInst,
        fact: DomainFact,
        dropFact: Boolean,
    ): List<DomainFact> {
        val thisInstance = atInst.location.method.thisInstance.toPath()
        if (fact == ZEROFact) {
            val derefs = atInst.values
                .mapNotNull { it.toPathOrNull() }
                .filter { it.isDereferencedAt(atInst) }
                .filterNot { it == thisInstance }
                .map { NpeTaintNode(it) }
            return listOf(ZEROFact) + derefs
        }

        if (fact !is TaintNode) {
            return emptyList()
        }

        val factPath = fact.variable
        val default = if (dropFact) emptyList() else listOf(fact)
        val toPath = to.toPathOrNull() ?: return default
        val fromPath = from.toPathOrNull() ?: return default

        val diff = factPath.minus(fromPath)
        if (diff != null) {
            val newPath = (toPath / diff).limit(maxPathLength)
            return listOf(fact.moveToOtherPath(newPath)).filterNot {
                it.variable == thisInstance
            }
        }
        return default
    }

    override fun transmitDataFlowAtNormalInst(
        inst: JIRInst,
        nextInst: JIRInst,
        fact: DomainFact,
    ): List<DomainFact> {
        return listOf(fact)
    }

    override fun obtainPossibleStartFacts(startStatement: JIRInst): List<DomainFact> {
        val values = startStatement.values
        return listOf(ZEROFact) + values
            .mapNotNull { it.toPathOrNull() }
            .filterNot { it == startStatement.location.method.thisInstance.toPath() }
            .map { NpeTaintNode(it) }
    }
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

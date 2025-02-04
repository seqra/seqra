package org.opentaint.ir.analysis.graph

import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstLocation
import org.opentaint.ir.api.cfg.JIRInstVisitor
import org.opentaint.ir.impl.cfg.JIRInstLocationImpl

/**
 * This is adopted specially for IFDS [JIRApplicationGraph] that
 *  1. Ignores method calls matching [bannedPackagePrefixes] (i.e., treats them as simple instructions with no callees)
 *  2. In [callers] returns only callsites that were visited before
 *  3. Adds a special [JIRNoopInst] instruction to the beginning of each method
 *    (because backward analysis may want for method to start with neutral instruction)
 */
class SimplifiedJIRApplicationGraph(
    private val impl: JIRApplicationGraphImpl,
    private val bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
) : JIRApplicationGraph by impl {

    private val visitedCallers: MutableMap<JIRMethod, MutableSet<JIRInst>> = mutableMapOf()

    // For backward analysis we may want for method to start with "neutral" operation =>
    //  we add noop to the beginning of every method
    private fun getStartInst(method: JIRMethod): JIRNoopInst {
        val methodEntryLineNumber = method.flowGraph().entries.firstOrNull()?.lineNumber
        return JIRNoopInst(JIRInstLocationImpl(method, -1, methodEntryLineNumber?.let { it - 1 } ?: -1))
    }

    override fun predecessors(node: JIRInst): Sequence<JIRInst> {
        val method = methodOf(node)
        return if (node == getStartInst(method)) {
            emptySequence()
        } else {
            if (node in impl.entryPoint(method)) {
                sequenceOf(getStartInst(method))
            } else {
                impl.predecessors(node)
            }
        }
    }

    override fun successors(node: JIRInst): Sequence<JIRInst> {
        val method = methodOf(node)
        return if (node == getStartInst(method)) {
            impl.entryPoint(method)
        } else {
            impl.successors(node)
        }
    }

    override fun callees(node: JIRInst): Sequence<JIRMethod> = impl.callees(node).filterNot { callee ->
        bannedPackagePrefixes.any { callee.enclosingClass.name.startsWith(it) }
    }.map {
        val curSet = visitedCallers.getOrPut(it) { mutableSetOf() }
        curSet.add(node)
        it
    }

    /**
     * This is IFDS-algorithm aware optimization.
     * In IFDS we don't need all method callers, we need only method callers which we visited earlier.
     */
    override fun callers(method: JIRMethod): Sequence<JIRInst> = visitedCallers.getOrDefault(method, mutableSetOf()).asSequence()

    override fun entryPoint(method: JIRMethod): Sequence<JIRInst> = sequenceOf(getStartInst(method))

    companion object {
        val defaultBannedPackagePrefixes: List<String> = listOf(
            "kotlin.",
            "java.",
            "jdk.internal.",
            "sun.",
//            "kotlin.jvm.internal.",
//            "java.security.",
//            "java.util.regex."
        )
    }
}

data class JIRNoopInst(override val location: JIRInstLocation): JIRInst {
    override val operands: List<JIRExpr>
        get() = emptyList()

    override fun <T> accept(visitor: JIRInstVisitor<T>): T {
        return visitor.visitExternalJIRInst(this)
    }

    override fun toString(): String = "noop"
}
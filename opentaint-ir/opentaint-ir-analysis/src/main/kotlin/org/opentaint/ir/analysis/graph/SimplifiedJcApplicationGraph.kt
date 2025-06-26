package org.opentaint.ir.analysis.graph

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.api.jvm.ext.isSubClassOf
import org.opentaint.ir.impl.cfg.JIRInstLocationImpl
import org.opentaint.ir.impl.features.hierarchyExt

/**
 * This is adopted specially for IFDS [JIRApplicationGraph] that
 *  1. Ignores method calls matching [bannedPackagePrefixes] (i.e., treats them as simple instructions with no callees)
 *  2. In [callers] returns only call sites that were visited before
 *  3. Adds a special [JIRNoopInst] instruction to the beginning of each method
 *    (because backward analysis may want for method to start with neutral instruction)
 */
internal class SimplifiedJIRApplicationGraph(
    private val graph: JIRApplicationGraph,
    private val bannedPackagePrefixes: List<String>,
) : JIRApplicationGraph by graph {
    private val hierarchyExtension = runBlocking {
        project.hierarchyExt()
    }

    private val visitedCallers: MutableMap<JIRMethod, MutableSet<JIRInst>> = mutableMapOf()

    private val cache: MutableMap<JIRMethod, List<JIRMethod>> = mutableMapOf()

    // For backward analysis we may want for method to start with "neutral" operation =>
    //  we add noop to the beginning of every method
    private fun getStartInst(method: JIRMethod): JIRNoopInst {
        val lineNumber = method.flowGraph().entries.firstOrNull()?.lineNumber?.let { it - 1 } ?: -1
        return JIRNoopInst(JIRInstLocationImpl(method, -1, lineNumber))
    }

    override fun predecessors(node: JIRInst): Sequence<JIRInst> {
        val method = methodOf(node)
        return when (node) {
            getStartInst(method) -> {
                emptySequence()
            }

            in graph.entryPoints(method) -> {
                sequenceOf(getStartInst(method))
            }

            else -> {
                graph.predecessors(node)
            }
        }
    }

    override fun successors(node: JIRInst): Sequence<JIRInst> {
        val method = methodOf(node)
        return when (node) {
            getStartInst(method) -> {
                graph.entryPoints(method)
            }

            else -> {
                graph.successors(node)
            }
        }
    }

    private fun getOverrides(method: JIRMethod): List<JIRMethod> {
        return if (cache.containsKey(method)) {
            cache[method]!!
        } else {
            val res = hierarchyExtension.findOverrides(method).toList()
            cache[method] = res
            res
        }
    }

    private fun calleesUnmarked(node: JIRInst): Sequence<JIRMethod> {
        val callees = graph.callees(node).filterNot { callee ->
            bannedPackagePrefixes.any { callee.enclosingClass.name.startsWith(it) }
        }

        val callExpr = node.callExpr as? JIRVirtualCallExpr ?: return callees
        val instanceClass = (callExpr.instance.type as? JIRClassType)?.jIRClass ?: return callees

        return callees
            .flatMap { callee ->
                val allOverrides = getOverrides(callee)
                    .filter {
                        it.enclosingClass isSubClassOf instanceClass ||
                            // TODO: use only down-most override here
                            instanceClass isSubClassOf it.enclosingClass
                    }

                // TODO: maybe filter inaccessible methods here?
                allOverrides + sequenceOf(callee)
            }
    }

    override fun callees(node: JIRInst): Sequence<JIRMethod> {
        return calleesUnmarked(node).also {
            it.forEach { method ->
                visitedCallers.getOrPut(method) { mutableSetOf() }.add(node)
            }
        }
    }

    /**
     * This is IFDS-algorithm aware optimization.
     * In IFDS we don't need all method callers, we need only method callers which we visited earlier.
     */
    // TODO: Think if this optimization is really needed
    override fun callers(method: JIRMethod): Sequence<JIRInst> =
        visitedCallers[method].orEmpty().asSequence()

    override fun entryPoints(method: JIRMethod): Sequence<JIRInst> = try {
        sequenceOf(getStartInst(method))
    } catch (e: Throwable) {
        // we couldn't find instructions list
        // TODO: maybe fix flowGraph()
        emptySequence()
    }

    override fun exitPoints(method: JIRMethod): Sequence<JIRInst> = try {
        graph.exitPoints(method)
    } catch (e: Throwable) {
        // we couldn't find instructions list
        // TODO: maybe fix flowGraph()
        emptySequence()
    }
}

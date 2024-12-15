package org.opentaint.opentaint-ir.impl.cfg.analysis

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import kotlinx.collections.immutable.toPersistentList
import org.opentaint.opentaint-ir.api.JIRMethod
import org.opentaint.opentaint-ir.api.analysis.JIRGraphTransformer
import org.opentaint.opentaint-ir.api.analysis.JIRInstIdentity
import org.opentaint.opentaint-ir.api.analysis.JIRInterProceduralTask
import org.opentaint.opentaint-ir.api.cfg.JIRGraph
import org.opentaint.opentaint-ir.api.cfg.JIRInst
import org.opentaint.opentaint-ir.api.ext.cfg.callExpr
import org.opentaint.opentaint-ir.impl.features.SyncUsagesExtension

abstract class AbstractJIRInterProceduralTask(val usages: SyncUsagesExtension) : JIRInterProceduralTask {

    protected open fun <KEY, VALUE> newCache(factory: (KEY) -> VALUE): LoadingCache<KEY, VALUE> {
        return CacheBuilder.newBuilder()
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .initialCapacity(10_000)
            .softValues()
            .build(object : CacheLoader<KEY, VALUE>() {
                override fun load(body: KEY): VALUE {
                    return factory(body)
                }
            })
    }

    protected val graphsStore by lazy(LazyThreadSafetyMode.NONE) {
        newCache<JIRMethod, JIRGraph> { flowOf(it) }
    }

    open val JIRMethod.actualFlowGraph: JIRGraph
        get() {
            return graphsStore.getUnchecked(this)
        }

    override fun groupedCallersOf(method: JIRMethod): Map<JIRMethod, Set<JIRInst>> {
        val callersMethod = usages.findUsages(method)
        return callersMethod.associateWith {
            it.actualFlowGraph.instructions.mapIndexedNotNull { index, inst ->
                val callExpr = inst.callExpr
                if (callExpr != null && callExpr.method.method == method) {
                    inst
                } else {
                    null
                }
            }.toSet()
        }
    }

    override fun callersOf(method: JIRMethod): Sequence<JIRInstIdentity> {
        val callersMethod = usages.findUsages(method)
        return callersMethod.map {
            it.actualFlowGraph.instructions.mapIndexedNotNull { index, inst ->
                val callExpr = inst.callExpr
                if (callExpr != null && callExpr.method.method == method) {
                    JIRInstIdentity(it, index)
                } else {
                    null
                }
            }
        }.flatten()
    }

    override fun callInstructionIdsOf(method: JIRMethod): Sequence<JIRInstIdentity> {
        return method.actualFlowGraph.instructions.mapIndexedNotNull { index, inst ->
            val callExpr = inst.callExpr
            if (callExpr != null && callExpr.method.method == method) {
                JIRInstIdentity(method, index)
            } else {
                null
            }
        }.asSequence()
    }

    override fun callInstructionsOf(method: JIRMethod): Sequence<JIRInst> {
        return method.actualFlowGraph.instructions
            .filter { inst -> inst.callExpr != null }
            .asSequence()
    }

    override fun heads(method: JIRMethod): List<JIRInstIdentity> {
        return listOf(method.actualFlowGraph.toRef { it.entry })
    }

    override fun isCall(instId: JIRInstIdentity): Boolean {
        val inst = toInstruction(instId)
        return inst.callExpr != null
    }

    override fun isExit(instId: JIRInstIdentity): Boolean {
        val graph = instId.method.actualFlowGraph
        return graph.exits.contains(graph.instructions[instId.index])
    }

    override fun isHead(instId: JIRInstIdentity): Boolean {
        val graph = instId.method.actualFlowGraph
        return graph.entry == graph.instructions[instId.index]
    }

    override fun toInstruction(instId: JIRInstIdentity): JIRInst {
        return instId.method.actualFlowGraph.instructions[instId.index]
    }

    private inline fun JIRGraph.toRef(inst: (JIRGraph) -> JIRInst): JIRInstIdentity {
        return JIRInstIdentity(this, indexOf(inst(this)))
    }
}

abstract class TransformingInterProcedureTask(
    usages: SyncUsagesExtension,
    _transformers: List<JIRGraphTransformer>
) : AbstractJIRInterProceduralTask(usages) {

    override val transformers = _transformers.toPersistentList()

}
@file:JvmName("InterProceduralPlatforms")
package org.opentaint.ir.impl.analysis

import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRAnalysisFeature
import org.opentaint.ir.api.analysis.JIRInstIdentity
import org.opentaint.ir.api.analysis.JIRInterProceduralPlatform
import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.ext.cfg.callExpr
import org.opentaint.ir.impl.analysis.features.JIRCacheGraphFeature
import org.opentaint.ir.impl.features.SyncUsagesExtension
import org.opentaint.ir.impl.features.usagesExt
import java.util.concurrent.Future

open class InterProceduralPlatform(
    classpath: JIRClasspath,
    val usages: SyncUsagesExtension,
    cacheSize: Long,
    feature: List<JIRAnalysisFeature>
) : JIRAnalysisPlatformImpl(
    classpath,
    feature.toPersistentList() + JIRCacheGraphFeature(cacheSize)
), JIRInterProceduralPlatform {

    protected open val JIRMethod.actualFlowGraph: JIRGraph
        get() {
            return flowGraph(this)
        }

    override fun groupedCallersOf(method: JIRMethod): Map<JIRMethod, Set<JIRInst>> {
        val callersMethod = usages.findUsages(method)
        return callersMethod.associateWith {
            it.actualFlowGraph.instructions.filter { inst ->
                inst.callExpr?.method?.method == method
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
        return listOf(method.actualFlowGraph.toIdentity { it.entry })
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

    private inline fun JIRGraph.toIdentity(inst: (JIRGraph) -> JIRInst): JIRInstIdentity {
        return JIRInstIdentity(this, indexOf(inst(this)))
    }
}

suspend fun JIRClasspath.interProcedure(
    features: List<JIRAnalysisFeature>,
    cacheSize: Long = 10_000
): InterProceduralPlatform {
    val usages = usagesExt()
    return InterProceduralPlatform(this, usages, cacheSize, features)
}

fun JIRClasspath.asyncInterProcedure(
    features: List<JIRAnalysisFeature>,
    cacheSize: Long = 10_000
): Future<InterProceduralPlatform> = GlobalScope.future { interProcedure(features, cacheSize) }

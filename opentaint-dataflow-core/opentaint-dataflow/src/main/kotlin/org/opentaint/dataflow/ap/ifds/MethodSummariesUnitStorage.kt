package org.opentaint.dataflow.ap.ifds

import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.serialization.MethodSummariesSerializer
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import java.util.concurrent.ConcurrentHashMap

open class MethodSummariesUnitStorage(
    private val apManager: ApManager,
    private val languageManager: LanguageManager,
) {
    private val methodSummaries = ConcurrentHashMap<MethodEntryPoint, SummaryEdgeStorageWithSubscribers>()

    fun storeSummaries(serializationContext: SummarySerializationContext) {
        val serializer = MethodSummariesSerializer(serializationContext, languageManager, apManager)

        methodSummaries.entries.groupBy({ it.key.method }) { it.value }.forEach { (method, storages) ->
            val methodSummaries = storages.map { it.getSummaries() }
            val serializedSummaries = serializer.serializeMethodSummaries(methodSummaries)
            serializationContext.storeSummaries(method, serializedSummaries)
        }

    }

    fun subscribeOnMethodEntryPointSummaries(
        methodEntryPoint: MethodEntryPoint,
        handler: SummaryEdgeStorageWithSubscribers.Subscriber
    ) {
        val methodStorage = methodSummaryEdges(methodEntryPoint)
        methodStorage.subscribeOnEdges(handler)
    }

    fun methodZeroSummaries(methodEntryPoint: MethodEntryPoint): List<Edge.ZeroInitialEdge> {
        val methodStorage = methodSummaryEdges(methodEntryPoint)
        return methodStorage.zeroEdges()
    }

    fun methodZeroToFactSummaries(
        methodEntryPoint: MethodEntryPoint,
        factBase: AccessPathBase
    ): List<Edge.ZeroToFact> {
        val methodStorage = methodSummaryEdges(methodEntryPoint)
        return methodStorage.zeroToFactEdges(factBase)
    }

    fun methodFactSummaries(
        methodEntryPoint: MethodEntryPoint,
        initialFactAp: FinalFactAp
    ): List<Edge.FactToFact> {
        val methodStorage = methodSummaryEdges(methodEntryPoint)
        return methodStorage.factEdges(initialFactAp)
    }

    fun methodFactNDSummaries(
        methodEntryPoint: MethodEntryPoint,
        initialFactAp: FinalFactAp
    ): List<Edge.NDFactToFact> {
        val methodStorage = methodSummaryEdges(methodEntryPoint)
        return methodStorage.factNDEdges(initialFactAp)
    }

    fun methodFactToFactSummaryEdges(
        methodEntryPoint: MethodEntryPoint,
        finalFactBase: AccessPathBase
    ): List<Edge.FactToFact> {
        val methodStorage = methodSummaryEdges(methodEntryPoint)
        return methodStorage.factToFactEdges(finalFactBase)
    }

    fun methodFactNDSummaries(
        methodEntryPoint: MethodEntryPoint,
        finalFactBase: AccessPathBase
    ): List<Edge.NDFactToFact> {
        val methodStorage = methodSummaryEdges(methodEntryPoint)
        return methodStorage.factNDEdges(finalFactBase)
    }

    fun addSummaryEdges(initialStatement: MethodEntryPoint, edges: List<Edge>) {
        val methodStorage = methodSummaryEdges(initialStatement)
        methodStorage.addEdges(edges)
    }

    fun methodSideEffectRequirements(
        initialStatement: MethodEntryPoint,
        initialFactAp: FinalFactAp
    ): List<InitialFactAp> {
        val methodStorage = methodSummaryEdges(initialStatement)
        return methodStorage.sideEffectRequirement(initialFactAp)
    }

    fun addSideEffectRequirement(initialStatement: MethodEntryPoint, requirements: List<InitialFactAp>) {
        val methodStorage = methodSummaryEdges(initialStatement)
        methodStorage.sideEffectRequirement(requirements)
    }

    private fun methodSummaryEdges(methodEntryPoint: MethodEntryPoint) =
        methodSummaries.computeIfAbsent(methodEntryPoint) {
            SummaryEdgeStorageWithSubscribers(apManager, methodEntryPoint)
        }

    fun collectMethodStats(stats: MethodStats) {
        methodSummaries.elements().iterator().forEach { it.collectStats(stats) }
    }
}

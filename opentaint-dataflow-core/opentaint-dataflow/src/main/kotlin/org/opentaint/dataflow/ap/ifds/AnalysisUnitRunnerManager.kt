package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType

interface AnalysisUnitRunnerManager {
    val unitResolver: UnitResolver<CommonMethod>

    fun getOrCreateUnitStorage(unit: UnitType): MethodSummariesUnitStorage?
    fun getOrCreateUnitRunner(unit: UnitType): AnalysisRunner?
    fun registerMethodCallFromUnit(method: CommonMethod, unit: UnitType)

    fun handleCrossUnitZeroCall(callerUnit: UnitType, methodEntryPoint: MethodEntryPoint) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val runner = getOrCreateUnitRunner(unit) ?: return

        registerMethodCallFromUnit(methodEntryPoint.method, callerUnit)

        runner.submitExternalInitialZeroFact(methodEntryPoint)
    }

    fun handleCrossUnitFactCall(callerUnit: UnitType, methodEntryPoint: MethodEntryPoint, methodFactAp: FinalFactAp) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val runner = getOrCreateUnitRunner(unit) ?: return

        registerMethodCallFromUnit(methodEntryPoint.method, callerUnit)

        runner.submitExternalInitialFact(methodEntryPoint, methodFactAp)
    }

    fun newSummaryEdges(methodEntryPoint: MethodEntryPoint, edges: List<Edge>) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return
        storage.addSummaryEdges(methodEntryPoint, edges)
    }

    fun newSideEffectRequirement(methodEntryPoint: MethodEntryPoint, requirements: List<InitialFactAp>) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return
        storage.addSideEffectRequirement(methodEntryPoint, requirements)
    }

    fun subscribeOnMethodEntryPointSummaries(
        methodEntryPoint: MethodEntryPoint,
        handler: SummaryEdgeStorageWithSubscribers.Subscriber
    ) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return
        storage.subscribeOnMethodEntryPointSummaries(methodEntryPoint, handler)
    }

    fun findZeroSummaryEdges(methodEntryPoint: MethodEntryPoint): List<Edge.ZeroInitialEdge> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return emptyList()
        return storage.methodZeroSummaries(methodEntryPoint)
    }

    fun findZeroToFactSummaryEdges(
        methodEntryPoint: MethodEntryPoint,
        factBase: AccessPathBase
    ): List<Edge.ZeroToFact> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return emptyList()
        return storage.methodZeroToFactSummaries(methodEntryPoint, factBase)
    }

    fun findFactSummaryEdges(methodEntryPoint: MethodEntryPoint, initialFactAp: FinalFactAp): List<Edge.FactToFact> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return emptyList()
        return storage.methodFactSummaries(methodEntryPoint, initialFactAp)
    }

    fun findFactToFactSummaryEdges(
        methodEntryPoint: MethodEntryPoint,
        initialFactAp: FinalFactAp,
        finalFactBase: AccessPathBase
    ): List<Edge.FactToFact> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return emptyList()
        return storage.methodFactToFactSummaryEdges(methodEntryPoint, initialFactAp, finalFactBase)
    }

    fun findSideEffectRequirements(
        methodEntryPoint: MethodEntryPoint,
        initialFactAp: FinalFactAp
    ): List<InitialFactAp> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return emptyList()
        return storage.methodSideEffectRequirements(methodEntryPoint, initialFactAp)
    }
}

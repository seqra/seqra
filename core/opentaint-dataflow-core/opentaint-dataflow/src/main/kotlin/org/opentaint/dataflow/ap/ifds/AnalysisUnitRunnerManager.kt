package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.util.Cancellation

interface AnalysisUnitRunnerManager {
    val unitResolver: UnitResolver<CommonMethod>
    val cancellation: Cancellation

    fun getOrCreateUnitStorage(unit: UnitType): MethodSummariesUnitStorage?
    fun getOrCreateUnitRunner(unit: UnitType): AnalysisRunner?
    fun registerMethodCallFromUnit(method: CommonMethod, unit: UnitType)

    fun handleCrossUnitZeroCall(callerUnit: UnitType, methodEntryPoint: MethodEntryPoint) {
        handleCrossUnitAction(callerUnit, methodEntryPoint) {
            submitExternalInitialZeroFact(methodEntryPoint)
        }
    }

    fun handleCrossUnitFactCall(callerUnit: UnitType, methodEntryPoint: MethodEntryPoint, methodFactAp: FinalFactAp) {
        handleCrossUnitAction(callerUnit, methodEntryPoint) {
            submitExternalInitialFact(methodEntryPoint, methodFactAp)
        }
    }

    fun handleCrossUnitSideEffectReq(methodEntryPoint: MethodEntryPoint, sideEffectReq: InitialFactAp) {
        handleCrossUnitAction(callerUnit = null, methodEntryPoint) {
            triggerSideEffectRequirement(methodEntryPoint, sideEffectReq)
        }
    }

    private inline fun handleCrossUnitAction(
        callerUnit: UnitType?,
        methodEntryPoint: MethodEntryPoint,
        body: AnalysisRunner.() -> Unit
    ) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val runner = getOrCreateUnitRunner(unit) ?: return

        callerUnit?.let { registerMethodCallFromUnit(methodEntryPoint.method, it) }

        runner.body()
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

    fun newSideEffectSummaries(methodEntryPoint: MethodEntryPoint, sideEffects: List<SideEffectSummary>) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return
        storage.addSideEffectSummaries(methodEntryPoint, sideEffects)
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

    fun findFactNDSummaryEdges(methodEntryPoint: MethodEntryPoint, initialFactAp: FinalFactAp): List<Edge.NDFactToFact> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return emptyList()
        return storage.methodFactNDSummaries(methodEntryPoint, initialFactAp)
    }

    fun findFactToFactSummaryEdges(
        methodEntryPoint: MethodEntryPoint,
        finalFactBase: AccessPathBase
    ): List<Edge.FactToFact> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return emptyList()
        return storage.methodFactToFactSummaryEdges(methodEntryPoint, finalFactBase)
    }

    fun findFactNDSummaryEdges(
        methodEntryPoint: MethodEntryPoint,
        finalFactBase: AccessPathBase
    ): List<Edge.NDFactToFact> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return emptyList()
        return storage.methodFactNDSummaries(methodEntryPoint, finalFactBase)
    }

    fun findSideEffectRequirements(
        methodEntryPoint: MethodEntryPoint,
        initialFactAp: FinalFactAp
    ): List<InitialFactAp> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return emptyList()
        return storage.methodSideEffectRequirements(methodEntryPoint, initialFactAp)
    }

    fun findZeroSideEffectSummaries(
        methodEntryPoint: MethodEntryPoint,
    ): List<SideEffectSummary.ZeroSideEffectSummary> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return emptyList()
        return storage.methodZeroSideEffectSummaries(methodEntryPoint)
    }

    fun findFactSideEffectSummaries(
        methodEntryPoint: MethodEntryPoint,
        initialFactAp: FinalFactAp
    ): List<SideEffectSummary.FactSideEffectSummary> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val storage = getOrCreateUnitStorage(unit) ?: return emptyList()
        return storage.methodFactSideEffectSummaries(methodEntryPoint, initialFactAp)
    }
}

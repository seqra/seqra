package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer.MethodCallHandler
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunner
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunner.LambdaResolvedEvent
import org.opentaint.dataflow.ap.ifds.TypeInfoAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoGroupAccessor
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallResolver
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallResolver.MethodCallResolutionResult
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRLambdaTracker
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.jIRDowncast
import org.opentaint.dataflow.util.getOrCreate
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRNewExpr
import org.opentaint.ir.api.jvm.ext.findMethodOrNull

class JIRMethodCallResolver(
    private val lambdaTracker: JIRLambdaTracker,
    val callResolver: JIRCallResolver,
    val runner: TaintAnalysisUnitRunner,
    private val params: Params,
) : MethodCallResolver {
    data class Params(
        val skipUnresolvedLambda: Boolean = true,
    )

    override fun resolveMethodCall(
        callerContext: MethodAnalysisContext,
        callExpr: CommonCallExpr,
        location: CommonInst,
        handler: MethodCallHandler,
        failureHandler: MethodAnalyzer.MethodCallResolutionFailureHandler
    ) {
        jIRDowncast<JIRCallExpr>(callExpr)
        jIRDowncast<JIRInst>(location)
        jIRDowncast<JIRMethodAnalysisContext>(callerContext)
        resolveJirMethodCall(callerContext, callExpr, location, handler, failureHandler)
    }

    override fun resolvedMethodCalls(
        callerContext: MethodAnalysisContext,
        callExpr: CommonCallExpr,
        location: CommonInst
    ): List<MethodCallResolutionResult> {
        jIRDowncast<JIRCallExpr>(callExpr)
        jIRDowncast<JIRInst>(location)
        jIRDowncast<JIRMethodAnalysisContext>(callerContext)
        return resolvedJirMethodCalls(callerContext, callExpr, location)
    }

    private val lambdaFeature by lazy {
        callResolver.cp.features?.filterIsInstance<LambdaAnonymousClassFeature>()?.firstOrNull()
            ?: error("No lambda feature found")
    }

    private fun resolveJirMethodCall(
        callerContext: JIRMethodAnalysisContext,
        callExpr: JIRCallExpr,
        location: JIRInst,
        handler: MethodCallHandler,
        failureHandler: MethodAnalyzer.MethodCallResolutionFailureHandler
    ) {
        val callees = callResolver.resolve(callExpr, location, callerContext)

        val analyzer = runner.getMethodAnalyzer(callerContext.methodEntryPoint)
        for (resolvedCallee in callees) {
            resolveJirMethodCall(callerContext, resolvedCallee, analyzer, callExpr, location, failureHandler, handler)
        }
    }

    private fun resolveJirMethodCall(
        callerContext: JIRMethodAnalysisContext,
        resolvedCallee: JIRCallResolver.MethodResolutionResult,
        analyzer: MethodAnalyzer,
        callExpr: JIRCallExpr,
        location: JIRInst,
        failureHandler: MethodAnalyzer.MethodCallResolutionFailureHandler,
        handler: MethodCallHandler
    ) {
        when (resolvedCallee) {
            JIRCallResolver.MethodResolutionResult.MethodResolutionFailed -> {
                analyzer.handleMethodCallResolutionFailure(callExpr, failureHandler)
            }

            is JIRCallResolver.MethodResolutionResult.ConcreteMethod -> {
                analyzer.handleResolvedMethodCall(resolvedCallee.method, handler)
            }

            is JIRCallResolver.MethodResolutionResult.Lambda -> {
                val locationIdx = location.location.index
                val lambdaResolver = callerContext.lambdaCallResolution.getOrCreate(locationIdx) {
                    JIRLambdaTracker.LambdaTracker(resolvedCallee.method)
                }

                val subscription = LambdaSubscription(runner, callerContext.methodEntryPoint, handler)
                lambdaResolver.addSubscriber(subscription)

                tryExtractLambdaType(lambdaResolver, handler, analyzer)
            }
        }
    }

    private fun tryExtractLambdaType(
        lambdaResolver: JIRLambdaTracker.LambdaTracker,
        handler: MethodCallHandler,
        analyzer: MethodAnalyzer,
    ) {
        val (start, fact) = when (handler) {
            is MethodCallHandler.ZeroToZeroHandler,
            is MethodCallHandler.NDFactToFactHandler -> return

            is MethodCallHandler.FactToFactHandler -> {
                handler.startFactBase to handler.currentEdge.factAp
            }

            is MethodCallHandler.ZeroToFactHandler -> {
                handler.startFactBase to handler.currentEdge.factAp
            }
        }

        if (start != AccessPathBase.This) return

        val typeInfoGroup = fact.readAccessor(TypeInfoGroupAccessor)
        if (typeInfoGroup == null) {
            if (handler is MethodCallHandler.FactToFactHandler) {
                val edge = handler.currentEdge
                val refinedInitial = edge.initialFactAp.exclude(TypeInfoGroupAccessor)
                analyzer.triggerSideEffectRequirement(refinedInitial)
            }
            return
        }

        val typeInfos = typeInfoGroup.getStartAccessors().filterIsInstance<TypeInfoAccessor>()
        typeInfos.forEach { typeInfo ->
            val cls = callResolver.cp.findClassOrNull(typeInfo.typeName)
            check(cls is LambdaAnonymousClassFeature.JIRLambdaClass) {
                "Unexpected type info: $cls"
            }

            lambdaResolver.addLambda(cls)
        }
    }

    data object TypeInfoSequentFlowFunction {
        fun handle(inst: JIRInst, body: (List<Accessor>) -> Unit) {
            if (inst !is JIRAssignInst) return
            val allocation = inst.rhv as? JIRNewExpr ?: return
            val allocatedType = allocation.type as? JIRClassType ?: return
            val allocatedClass = allocatedType.jIRClass
            if (allocatedClass !is LambdaAnonymousClassFeature.JIRLambdaClass) return

            body(listOf(TypeInfoGroupAccessor, TypeInfoAccessor(allocatedClass.name)))
        }
    }

    private fun resolvedJirMethodCalls(
        callerContext: JIRMethodAnalysisContext,
        callExpr: JIRCallExpr,
        location: JIRInst
    ): List<MethodCallResolutionResult> {
        val callees = callResolver.resolve(callExpr, location, callerContext)
        return callees.flatMap { resolvedCallee ->
            resolvedJirMethodCalls(callerContext, resolvedCallee)
        }
    }

    private fun resolvedJirMethodCalls(
        callerContext: JIRMethodAnalysisContext,
        resolvedCallee: JIRCallResolver.MethodResolutionResult
    ): List<MethodCallResolutionResult> =
        when (resolvedCallee) {
            JIRCallResolver.MethodResolutionResult.MethodResolutionFailed -> {
                listOf(MethodCallResolutionResult.ResolutionFailure)
            }

            is JIRCallResolver.MethodResolutionResult.ConcreteMethod -> {
                listOf(MethodCallResolutionResult.ResolvedMethod(resolvedCallee.method))
            }

            is JIRCallResolver.MethodResolutionResult.Lambda -> {
                resolvedCallee.withLambdaProxy(callerContext, { resolvedJirMethodCalls(callerContext, it) }) {
                    val resolvedLambdas = mutableListOf<MethodCallResolutionResult>()
                    lambdaTracker.forEachRegisteredLambda(
                        resolvedCallee.method,
                        object : JIRLambdaTracker.LambdaSubscriber {
                            override fun newLambda(
                                method: JIRMethod,
                                lambdaClass: LambdaAnonymousClassFeature.JIRLambdaClass
                            ) {
                                val methodImpl = lambdaClass.findMethodOrNull(method.name, method.description)
                                    ?: error("Lambda class $lambdaClass has no lambda method $method")

                                resolvedLambdas += MethodCallResolutionResult.ResolvedMethod(MethodWithContext(methodImpl, EmptyMethodContext))
                            }
                        }
                    )

                    resolvedLambdas.ifEmpty { listOf(MethodCallResolutionResult.ResolutionFailure) }
                }
            }
        }

    private data class LambdaSubscription(
        private val runner: TaintAnalysisUnitRunner,
        private val callerEntryPoint: MethodEntryPoint,
        private val handler: MethodCallHandler
    ) : JIRLambdaTracker.LambdaSubscriber {
        override fun newLambda(method: JIRMethod, lambdaClass: LambdaAnonymousClassFeature.JIRLambdaClass) {
            val methodImpl = lambdaClass.findMethodOrNull(method.name, method.description)
                ?: error("Lambda class $lambdaClass has no lambda method $method")

            val lambdaMethodWithContext = MethodWithContext(methodImpl, EmptyMethodContext)
            runner.addResolvedLambdaEvent(LambdaResolvedEvent(callerEntryPoint, handler, lambdaMethodWithContext))
        }
    }

    private inline fun <T> JIRCallResolver.MethodResolutionResult.Lambda.withLambdaProxy(
        callerContext: JIRMethodAnalysisContext,
        delegate: (JIRCallResolver.MethodResolutionResult) -> T,
        handleLambda: () -> T
    ): T {
        if (params.skipUnresolvedLambda) {
            return delegate(JIRCallResolver.MethodResolutionResult.MethodResolutionFailed)
        }

        val caller = callerContext.methodEntryPoint.method as JIRMethod

        if (caller is LambdaAnonymousClassFeature.OpentaintLambdaProxyMethod) {
            return handleLambda()
        }

        val callerLocation = caller.enclosingClass.declaration.location

        val proxy = lambdaFeature.getOrCreateLambdaProxy(method, callResolver.cp, callerLocation)
        val proxyMethod = proxy.declaredMethods.first()
        val proxyWithCtx = MethodWithContext(proxyMethod, EmptyMethodContext)
        val concreteCall = JIRCallResolver.MethodResolutionResult.ConcreteMethod(proxyWithCtx)
        return delegate(concreteCall)
    }
}

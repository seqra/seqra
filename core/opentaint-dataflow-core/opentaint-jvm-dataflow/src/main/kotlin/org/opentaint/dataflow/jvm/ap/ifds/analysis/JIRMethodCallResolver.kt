package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.findMethodOrNull
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallResolver
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunner
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunner.LambdaResolvedEvent
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRLambdaTracker
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.jIRDowncast

class JIRMethodCallResolver(
    private val lambdaTracker: JIRLambdaTracker,
    val callResolver: JIRCallResolver,
    private val runner: TaintAnalysisUnitRunner
) : MethodCallResolver {
    override fun resolveMethodCall(
        callerContext: MethodAnalysisContext,
        callExpr: CommonCallExpr,
        location: CommonInst,
        handler: MethodAnalyzer.MethodCallHandler,
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
    ): List<MethodWithContext> {
        jIRDowncast<JIRCallExpr>(callExpr)
        jIRDowncast<JIRInst>(location)
        jIRDowncast<JIRMethodAnalysisContext>(callerContext)
        return resolvedJirMethodCalls(callerContext, callExpr, location)
    }

    private fun resolveJirMethodCall(
        callerContext: JIRMethodAnalysisContext,
        callExpr: JIRCallExpr,
        location: JIRInst,
        handler: MethodAnalyzer.MethodCallHandler,
        failureHandler: MethodAnalyzer.MethodCallResolutionFailureHandler
    ) {
        val callees = callResolver.resolve(callExpr, location, callerContext)

        val analyzer = runner.getMethodAnalyzer(callerContext.methodEntryPoint)
        for (resolvedCallee in callees) {
            when (resolvedCallee) {
                JIRCallResolver.MethodResolutionResult.MethodResolutionFailed -> {
                    analyzer.handleMethodCallResolutionFailure(callExpr, failureHandler)
                }

                is JIRCallResolver.MethodResolutionResult.ConcreteMethod -> {
                    analyzer.handleResolvedMethodCall(resolvedCallee.method, handler)
                }

                is JIRCallResolver.MethodResolutionResult.Lambda -> {
                    val subscription = LambdaSubscription(runner, callerContext.methodEntryPoint, handler)
                    lambdaTracker.subscribeOnLambda(resolvedCallee.method, subscription)
                }
            }
        }
    }

    private fun resolvedJirMethodCalls(
        callerContext: JIRMethodAnalysisContext,
        callExpr: JIRCallExpr,
        location: JIRInst
    ): List<MethodWithContext> {
        val callees = callResolver.resolve(callExpr, location, callerContext)
        return callees.flatMap { resolvedCallee ->
            when (resolvedCallee) {
                JIRCallResolver.MethodResolutionResult.MethodResolutionFailed -> {
                    emptyList()
                }

                is JIRCallResolver.MethodResolutionResult.ConcreteMethod -> {
                    listOf(resolvedCallee.method)
                }

                is JIRCallResolver.MethodResolutionResult.Lambda -> {
                    val resolvedLambdas = mutableListOf<MethodWithContext>()
                    lambdaTracker.forEachRegisteredLambda(
                        resolvedCallee.method,
                        object : JIRLambdaTracker.LambdaSubscriber {
                            override fun newLambda(
                                method: JIRMethod,
                                lambdaClass: LambdaAnonymousClassFeature.JIRLambdaClass
                            ) {
                                val methodImpl = lambdaClass.findMethodOrNull(method.name, method.description)
                                    ?: error("Lambda class $lambdaClass has no lambda method $method")

                                resolvedLambdas += MethodWithContext(methodImpl, EmptyMethodContext)
                            }
                        }
                    )
                    resolvedLambdas
                }
            }
        }
    }

    private data class LambdaSubscription(
        private val runner: TaintAnalysisUnitRunner,
        private val callerEntryPoint: MethodEntryPoint,
        private val handler: MethodAnalyzer.MethodCallHandler
    ) : JIRLambdaTracker.LambdaSubscriber {
        override fun newLambda(method: JIRMethod, lambdaClass: LambdaAnonymousClassFeature.JIRLambdaClass) {
            val methodImpl = lambdaClass.findMethodOrNull(method.name, method.description)
                ?: error("Lambda class $lambdaClass has no lambda method $method")

            val lambdaMethodWithContext = MethodWithContext(methodImpl, EmptyMethodContext)
            runner.addResolvedLambdaEvent(LambdaResolvedEvent(callerEntryPoint, handler, lambdaMethodWithContext))
        }
    }
}
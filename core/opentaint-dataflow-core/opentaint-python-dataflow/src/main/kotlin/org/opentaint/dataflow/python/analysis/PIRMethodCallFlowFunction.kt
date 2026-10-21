package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnFFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnNonDistributiveFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnZFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnZeroFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToStartZeroFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.TraceInfo
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.ZeroCallFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.ZeroCallSuccessFact
import org.opentaint.dataflow.configuration.python.TaintConfigurationItem
import org.opentaint.dataflow.configuration.python.serialized.PIRUserDefinedRuleInfo
import org.opentaint.dataflow.python.PIRCallAnyArgumentResolver
import org.opentaint.dataflow.python.PIRCallAtomEvaluator
import org.opentaint.dataflow.python.PIRConditionRewriter
import org.opentaint.dataflow.python.PIRFlowFunctionUtils
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.resolveAp
import org.opentaint.dataflow.python.PIRSimpleFactAwareConditionEvaluator
import org.opentaint.dataflow.python.rulesWithConditions
import org.opentaint.dataflow.python.adapter.callExpr
import org.opentaint.dataflow.python.alias.forEachAliasBeforeCallStatement
import org.opentaint.dataflow.python.graph.PIRUnknownFunction
import org.opentaint.dataflow.taint.DefaultFactWithMarkAfterAnyFieldResolver.Companion.createMarkAfterAccessorResolver
import org.opentaint.dataflow.taint.EvaluatedCleanAction
import org.opentaint.dataflow.taint.FinalFactReader
import org.opentaint.dataflow.taint.TaintFactAwareConditionEvaluator
import org.opentaint.dataflow.taint.TaintPassActionEvaluator
import org.opentaint.dataflow.taint.applyCleanerActions
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.util.Maybe
import org.opentaint.util.maybeFlatMap
import org.opentaint.util.onSome

class PIRMethodCallFlowFunction(
    private val callInst: PIRCall,
    private val ctx: PIRMethodAnalysisContext,
    private val apManager: ApManager,
) : MethodCallFlowFunction.Default {
    private val rulesProvider get() = ctx.taint.taintConfig

    private val callExpr = callInst.callExpr ?: error("Unexpected null call expr")

    private val factMapper get() = ctx.methodCallFactMapper

    private val returnValue get() = callInst.target

    override fun propagateZeroToZero(): Set<ZeroCallFact> =
        setOf(CallToReturnZeroFact, CallToStartZeroFact)

    override fun propagateZeroToZeroResolutionSuccess(method: MethodWithContext): Set<ZeroCallSuccessFact> {
        val callee = method.method as PIRFunction
        val result = mutableSetOf<ZeroCallSuccessFact>()

        val conditionRewriter = callConditionRewriter(callInst)
        applySourceRules(callee, emptySet(), null, ExclusionSet.Universe,
            conditionRewriter,
            createFinalFact = { it, trace ->
                result += CallToReturnZFact(factAp = it, trace)
            },
            createEdge = { initial, it, trace ->
                result += CallToReturnFFact(initial, it, trace)
            },
            createNDEdge = { initial, it, trace ->
                result += CallToReturnNonDistributiveFact(initial, it, trace)
            }
        )

        applySinkRules(callee, initialFacts = emptySet(), factReader = null, conditionRewriter) {
            check(it is ZeroCallSuccessFact)
            result += it
        }.forEach { (fact, trace) ->
            fact.forEachSourceFactWithAliases { result += CallToReturnZFact(it, trace) }
        }

        if (callee !is PIRUnknownFunction) {
            result.add(CallToStartZeroFact)
        }

        return result
    }

    override fun propagateFact(
        initialFacts: Set<InitialFactAp>,
        exclusion: ExclusionSet,
        factAp: FinalFactAp,
        skipCall: () -> Unit,
        addSideEffectRequirement: (FinalFactReader) -> Unit,
        addCallToReturn: (FinalFactReader, FinalFactAp, TraceInfo) -> Unit,
        addCallToStart: (factReader: FinalFactReader, callerFact: FinalFactAp, startFactBase: AccessPathBase, TraceInfo) -> Unit,
        addUnchecked: (MethodCallFlowFunction.CallFact) -> Unit
    ) {
        if (!factMapper.factIsRelevantToMethodCall(callInst, returnValue, callExpr, factAp)) {
            skipCall()
            return
        }

        val reader = FinalFactReader(factAp, apManager)
        factMapper.mapMethodCallToStartFlowFact(
            callInst,
            callInst.location.method,
            callExpr,
            returnValue = null,
            factAp = factAp,
            checker = FactTypeChecker.Dummy,
        ) { callerFact, startFactBase ->
            addCallToStart(reader, callerFact, startFactBase, TraceInfo.Flow)
        }
    }

    override fun propagateSuccessCallFact(
        initialFacts: Set<InitialFactAp>,
        exclusion: ExclusionSet,
        factAp: FinalFactAp,
        startFactBase: AccessPathBase,
        method: MethodWithContext,
        addSideEffectRequirement: (FinalFactReader) -> Unit,
        addCallToReturn: (FinalFactReader, FinalFactAp, TraceInfo?) -> Unit,
        addCallToStart: (factReader: FinalFactReader, callerFact: FinalFactAp, startFactBase: AccessPathBase, TraceInfo?) -> Unit,
        addUnchecked: (MethodCallFlowFunction.CallFact) -> Unit,
    ) {
        val callee = method.method as PIRFunction
        val conditionRewriter = callConditionRewriter(callInst)
        val startFactReader = FinalFactReader(factAp.rebase(startFactBase), apManager)

        applySinkRules(callee, initialFacts, startFactReader, conditionRewriter, addUnchecked)
            .forEach { (fact, trace) ->
                fact.forEachSourceFactWithAliases { addUnchecked(CallToReturnZFact(it, trace)) }
            }

        applySourceRules(
            callee, initialFacts, startFactReader, exclusion, conditionRewriter,
            createFinalFact = { it, trace ->
                addCallToReturn(startFactReader, it, trace)
            },
            createEdge = { initial, it, trace ->
                addUnchecked(CallToReturnFFact(initial, it, trace))
            },
            createNDEdge = { initial, it, trace ->
                addUnchecked(CallToReturnNonDistributiveFact(initial, it, trace))
            }
        )

        val cleanedStartFacts = applyCleaners(callee, conditionRewriter, startFactReader, addUnchecked)
        for (cleanedStartFact in cleanedStartFacts) {
            val cleanedFact = cleanedStartFact.rebase(factAp.base)

            if (callee is PIRUnknownFunction) {
                propagateUnknownCallFact(callee, startFactReader, cleanedFact, cleanedStartFact, startFactBase, addCallToReturn)
                continue
            }

            val calleeFrameBase = factMapper.toCalleeFrame(callInst, callee, startFactBase) ?: continue
            addCallToStart(startFactReader, cleanedFact, calleeFrameBase, null)
        }

        if (startFactReader.hasRefinement) {
            addSideEffectRequirement(startFactReader)
        }
    }

    private fun propagateUnknownCallFact(
        callee: PIRFunction,
        originalFactReader: FinalFactReader,
        factAp: FinalFactAp,
        startFact: FinalFactAp,
        startFactBase: AccessPathBase,
        addCallToReturn: (FinalFactReader, FinalFactAp, TraceInfo?) -> Unit,
    ) {
        val summaryRewriter = PIRCallRuleBasedSummaryRewriter(callInst, ctx, apManager, callee)

        summaryRewriter.rewriteSummaryFact(startFact).forEach { (fact, reader) ->
            originalFactReader.updateRefinement(reader)
            factMapper.mapMethodExitToReturnFlowFact(callInst, fact, FactTypeChecker.Dummy).forEach {
                addCallToReturn(originalFactReader, it, null)
            }
        }

        applyPassRules(
            callee, summaryRewriter, factAp, originalFactReader, startFact, startFactBase, addCallToReturn
        )
    }

    private fun applyCleaners(
        callee: PIRFunction,
        conditionRewriter: PIRConditionRewriter,
        conditionFactReader: FinalFactReader,
        addUnchecked: (MethodCallFlowFunction.CallFact) -> Unit,
    ): List<FinalFactAp> {
        val conditionEvaluator = TaintFactAwareConditionEvaluator(
            listOf(conditionFactReader),
            markAfterAnyAccessorResolver = null
        )

        val simpleConditionEvaluator = PIRSimpleFactAwareConditionEvaluator(conditionRewriter, conditionEvaluator)
        val cleaner = PIRTaintCleanActionEvaluator(callInst)

        val factReaderBeforeCleaner = FinalFactReader(conditionFactReader.factAp, apManager)
        val cleanerResults = applyCleaner(callee, factReaderBeforeCleaner, simpleConditionEvaluator, cleaner)

        return cleanerResults.mapNotNull { cleanerResult ->
            val factReaderAfterCleaner = cleanerResult.fact
            if (factReaderAfterCleaner == null) {
                val trace = cleanerResult.action
                    ?.takeIf { (it.rule as? TaintConfigurationItem)?.info is PIRUserDefinedRuleInfo }
                    ?.let { TraceInfo.Rule(it.rule, it.action) }
                addUnchecked(MethodCallFlowFunction.Drop(trace))
                return@mapNotNull null
            }

            conditionFactReader.updateRefinement(factReaderAfterCleaner)
            factReaderAfterCleaner.factAp
        }
    }

    private fun applyCleaner(
        callee: PIRFunction,
        initialFact: FinalFactReader,
        conditionEvaluator: PIRSimpleFactAwareConditionEvaluator,
        cleanEvaluator: PIRTaintCleanActionEvaluator,
    ): List<EvaluatedCleanAction> {
        val rules = rulesProvider.cleanersForMethod(callee)
            .filter { conditionEvaluator.eval(it.condition) }

        return rules.applyCleanerActions(
            evalAction = { fact, rule, action -> cleanEvaluator.evaluate(fact, rule, action) },
            itemRule = { it },
            itemActions = { it.cleans },
            initial = EvaluatedCleanAction.initial(initialFact),
        )
    }

    override fun propagateUnresolvedCallFact(
        factAp: FinalFactAp,
        startFactBase: AccessPathBase,
        addCallToReturn: (FinalFactReader, FinalFactAp, TraceInfo?) -> Unit,
        addSideEffectRequirement: (FinalFactReader) -> Unit
    ) {
        addCallToReturn(FinalFactReader(factAp, apManager), factAp, null)
        trackExternalMethod(startFactBase, rulesApplied = false)
    }

    private fun trackExternalMethod(startFactBase: AccessPathBase, rulesApplied: Boolean) {
        if (startFactBase is AccessPathBase.ClassStatic) return

        ctx.taint.externalMethodTracker?.trackExternalMethod(
            method = callInst.resolvedCallee ?: callInst.callee.toString(),
            signature = "args:${callInst.args.size}",
            factPosition = startFactBase.toString(),
            rulesApplied = rulesApplied,
        )
    }

    private fun applySourceRules(
        callee: PIRFunction,
        initialFacts: Set<InitialFactAp>,
        factReader: FinalFactReader?,
        exclusionSet: ExclusionSet,
        conditionRewriter: PIRConditionRewriter,
        createFinalFact: (FinalFactAp, TraceInfo) -> Unit,
        createEdge: (InitialFactAp, FinalFactAp, TraceInfo) -> Unit,
        createNDEdge: (Set<InitialFactAp>, FinalFactAp, TraceInfo) -> Unit,
    ) {
        val sourceRules = rulesProvider.sourcesForMethod(callee)

        val taintUtil = PIRMethodCallTaintUtil(ctx, callInst, apManager)

        taintUtil.applySourceRules(
            sourceRules = conditionRewriter.rulesWithConditions(sourceRules),
            initialFacts = initialFacts,
            factReader = factReader,
            exclusion = exclusionSet,
            createFinalFact = { srcF, trace ->
                srcF.forEachSourceFactWithAliases {
                    createFinalFact(it, trace)
                }
            },
            createEdge = { initial, srcF, trace ->
                srcF.forEachSourceFactWithAliases {
                    createEdge(initial, it, trace)
                }
            },
            createNDEdge = { initial, srcF, trace ->
                srcF.forEachSourceFactWithAliases {
                    createNDEdge(initial, it, trace)
                }
            },
        )
    }

    private fun applySinkRules(
        callee: PIRFunction,
        initialFacts: Set<InitialFactAp>,
        factReader: FinalFactReader?,
        conditionRewriter: PIRConditionRewriter,
        addUnchecked: (MethodCallFlowFunction.CallFact) -> Unit
    ): List<Pair<FinalFactAp, TraceInfo>> {
        val sinkRules = rulesProvider.sinksForMethod(callee)

        val taintUtil = PIRMethodCallTaintUtil(ctx, callInst, apManager)

        val markAfterAnyAccessorResolver = createMarkAfterAccessorResolver(
            ctx.methodEntryPoint, initialFacts
        ) { i, k ->
            addUnchecked(MethodCallFlowFunction.FactSideEffect(i, k))
        }

        taintUtil.applySinkRules(
            conditionRewriter.rulesWithConditions(sinkRules), factReader, markAfterAnyAccessorResolver
        )

        return taintUtil.factsAfterSink
    }

    private fun applyPassRules(
        callee: PIRFunction,
        summaryRewriter: PIRCallRuleBasedSummaryRewriter,
        originalFact: FinalFactAp,
        originalFactReader: FinalFactReader,
        mappedFact: FinalFactAp,
        startFactBase: AccessPathBase,
        propagateFact: (FinalFactReader, FinalFactAp, TraceInfo) -> Unit,
    ) {
        val typeChecker = FactTypeChecker.Dummy
        val passRules = rulesProvider.passThroughForMethod(callee)
            .ifEmpty { rulesProvider.passThroughForMethod(callee, bySimpleName = true) }

        val reader = FinalFactReader(mappedFact, apManager)
        val evaluator = TaintPassActionEvaluator(
            apManager, typeChecker, reader,
            PIRFlowFunctionUtils.DummyPositionTypeResolver
        )

        val conditionRewriter = callConditionRewriter(callInst)
        val simpleConditionEvaluator = PIRSimpleFactAwareConditionEvaluator(conditionRewriter, null)

        val passThroughFacts = passRules.maybeFlatMap { rule ->
            if (!simpleConditionEvaluator.eval(rule.condition)) return@maybeFlatMap Maybe.none()

            rule.copy.maybeFlatMap { action ->
                val from = action.from.resolveAp(callInst) ?: return@maybeFlatMap Maybe.none()
                val to = action.to.resolveAp(callInst) ?: return@maybeFlatMap Maybe.none()

                evaluator.propagateData(rule, action, from, to)
            }
        }

        trackExternalMethod(startFactBase, passThroughFacts.isSome)

        passThroughFacts.onSome { facts ->
            facts.forEach { evp ->
                val traceInfo = TraceInfo.Rule(evp.rule, evp.action)
                val rewrittenFacts = summaryRewriter.rewriteSummaryFact(evp.fact)
                for ((unrefinedFact, factRefinement) in rewrittenFacts) {
                    val fact = factRefinement.refineFact(unrefinedFact)
                    reader.updateRefinement(factRefinement)
                    originalFactReader.updateRefinement(reader)

                    factMapper.mapMethodExitToReturnFlowFact(callInst, fact, typeChecker).forEach { mappedFact ->
                        mappedFact.forEachFactWithAliases(originalFact) { propagateFact(originalFactReader, it, traceInfo) }
                    }
                }
            }
        }

        originalFactReader.updateRefinement(reader)
    }

    private inline fun FinalFactAp.forEachSourceFactWithAliases(crossinline body: (FinalFactAp) -> Unit) =
        forEachFactWithAliases(originalFact = null, body)

    private inline fun FinalFactAp.forEachFactWithAliases(originalFact: FinalFactAp?,  crossinline body: (FinalFactAp) -> Unit) {
        body(this)

        if (originalFact != null && originalFact == this) {
            return
        }

        ctx.aliasAnalysis?.forEachAliasBeforeCallStatement(callInst, this) { aliased ->
            body(aliased)
        }
    }

    private fun callConditionRewriter(call: PIRCall) = PIRConditionRewriter(
        PIRCallAnyArgumentResolver(call), PIRCallAtomEvaluator(call), call
    )
}

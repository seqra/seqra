package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
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
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.serialized.UserDefinedRuleInfo
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper.factIsRelevantToMethodCall
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodPositionBaseTypeResolver
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyCleaner
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyPassThrough
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRMethodCallTaintUtil
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRTaintCleanActionEvaluator
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.dataflow.taint.DefaultFactWithMarkAfterAnyFieldResolver.Companion.createMarkAfterAccessorResolver
import org.opentaint.dataflow.taint.FactWithMarkAfterAnyAccessorResolver
import org.opentaint.dataflow.taint.FinalFactReader
import org.opentaint.dataflow.taint.TaintFactAwareConditionEvaluator
import org.opentaint.dataflow.taint.TaintPassActionEvaluator
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.util.merge
import org.opentaint.util.onSome

class JIRMethodCallFlowFunction(
    private val apManager: ApManager,
    private val analysisContext: JIRMethodAnalysisContext,
    private val returnValue: JIRImmediate?,
    private val callExpr: JIRCallExpr,
    private val statement: JIRInst,
    private val generateTrace: Boolean,
): MethodCallFlowFunction.Default {
    private val taintCtx get() = analysisContext.taint

    private val summaryRewriter by lazy {
        JIRMethodCallRuleBasedSummaryRewriter(statement, analysisContext, apManager)
    }

    val typeResolver by lazy {
        JIRMethodPositionBaseTypeResolver(callExpr.method.method)
    }

    override fun propagateZeroToZero() = buildSet {
        applySinkRules(
            factReader = null,
            markAfterAnyFieldResolver = null
        ).forEach { (fact, trace) ->
            fact.forEachSourceFactWithAliases { this += CallToReturnZFact(factAp = it, trace) }
        }

        applySourceRules(
            initialFacts = emptySet(), factReader = null, exclusion = ExclusionSet.Universe,
            createFinalFact = { fact, trace ->
                fact.forEachSourceFactWithAliases { this += CallToReturnZFact(factAp = it, trace) }
            },
            createEdge = { initial, final, trace ->
                final.forEachSourceFactWithAliases { this += CallToReturnFFact(initial, it, trace) }
            },
            createNDEdge = { initial, final, trace ->
                final.forEachSourceFactWithAliases { this += CallToReturnNonDistributiveFact(initial, it, trace) }
            }
        )

        this += CallToReturnZeroFact
        this += CallToStartZeroFact
    }

    override fun propagateFact(
        initialFacts: Set<InitialFactAp>,
        exclusion: ExclusionSet,
        factAp: FinalFactAp,
        skipCall: () -> Unit,
        addSideEffectRequirement: (FinalFactReader) -> Unit,
        addCallToReturn: (FinalFactReader, FinalFactAp, TraceInfo) -> Unit,
        addCallToStart: (factReader: FinalFactReader, callerFact: FinalFactAp, startFactBase: AccessPathBase, TraceInfo) -> Unit,
        addUnchecked: (MethodCallFlowFunction.CallFact) -> Unit,
    ) {
        if (!factIsRelevantToMethodCall(statement, returnValue, callExpr, factAp)) {
            skipCall()
            return
        }

        val factReader = FinalFactReader(factAp, apManager)

        val markAfterAnyFieldResolver = createMarkAfterAccessorResolver(
            analysisContext.methodEntryPoint, initialFacts
        ) { i, k ->
            addUnchecked(MethodCallFlowFunction.FactSideEffect(i, k))
        }

        applySinkRules(factReader, markAfterAnyFieldResolver).forEach { (fact, trace) ->
            fact.forEachSourceFactWithAliases {
                addUnchecked(CallToReturnZFact(it, trace))
            }
        }

        applySourceRules(
            initialFacts, factReader, exclusion,
            createFinalFact = { fact, trace ->
                fact.forEachSourceFactWithAliases { addCallToReturn(factReader, it, trace) }
            },
            createEdge = { initial, final, trace ->
                final.forEachSourceFactWithAliases {
                    addUnchecked(CallToReturnFFact(initial, it, trace))
                }
            },
            createNDEdge = { initial, final, trace ->
                final.forEachSourceFactWithAliases {
                    addUnchecked(CallToReturnNonDistributiveFact(initial, it, trace))
                }
            },
            markAfterAnyFieldResolver = markAfterAnyFieldResolver,
        )

        JIRMethodCallFactMapper.mapMethodCallToStartFlowFact(
            statement,
            callee = callExpr.callee,
            callExpr = callExpr,
            returnValue = null,
            factAp = factAp,
            checker = analysisContext.factTypeChecker,
        ) { callerFact, startFactBase ->
            applyCleanersOrCallToStart(
                factReader, callerFact, startFactBase,
                addCallToReturn, addCallToStart, addUnchecked
            )
        }

        if (factReader.hasRefinement) {
            addSideEffectRequirement(factReader)
        }
    }

    private fun applyCleanersOrCallToStart(
        originalFactReader: FinalFactReader,
        unmappedCallerFactAp: FinalFactAp,
        startFactBase: AccessPathBase,
        addCallToReturn: (FinalFactReader, FinalFactAp, TraceInfo) -> Unit,
        addCallToStart: (factReader: FinalFactReader, callerFactAp: FinalFactAp, startFactBase: AccessPathBase, TraceInfo) -> Unit,
        addCallToReturnUnchecked: (MethodCallFlowFunction.CallFact) -> Unit,
    ) {
        val method = callExpr.callee

        val callerFact = unmappedCallerFactAp.rebase(startFactBase)
        val conditionFactReader = FinalFactReader(callerFact, apManager)

        val conditionEvaluator = TaintFactAwareConditionEvaluator(
            listOf(conditionFactReader),
            markAfterAnyAccessorResolver = null // we don't expect such marks in pass rules
        )

        val cleaner = JIRTaintCleanActionEvaluator()

        val factReaderBeforeCleaner = FinalFactReader(callerFact, apManager)
        val cleanRules = taintCtx.cleanRulesForCallStatement(statement, callExpr, returnValue, callerFact)
        val cleanerResults = applyCleaner(
            cleanRules,
            factReaderBeforeCleaner,
            conditionEvaluator,
            cleaner
        )

        originalFactReader.updateRefinement(listOf(conditionFactReader))

        for (cleanerResult in cleanerResults) {
            val factReaderAfterCleaner = cleanerResult.fact
            if (factReaderAfterCleaner == null) {
                val trace = cleanerResult.action
                    ?.takeIf { (it.rule as? TaintConfigurationItem)?.info is UserDefinedRuleInfo }
                    ?.let { TraceInfo.Rule(it.rule, it.action) }
                addCallToReturnUnchecked(MethodCallFlowFunction.Drop(trace))
                continue
            }

            propagateCleanedFact(
                method,
                factReaderAfterCleaner,
                originalFactReader,
                addCallToReturn,
                startFactBase,
                addCallToStart
            )
        }
    }

    private fun propagateCleanedFact(
        method: JIRMethod,
        factReaderAfterCleaner: FinalFactReader,
        originalFactReader: FinalFactReader,
        addCallToReturn: (FinalFactReader, FinalFactAp, TraceInfo) -> Unit,
        startFactBase: AccessPathBase,
        addCallToStart: (factReader: FinalFactReader, callerFactAp: FinalFactAp, startFactBase: AccessPathBase, TraceInfo) -> Unit
    ) {
        originalFactReader.updateRefinement(listOf(factReaderAfterCleaner))

        val cleanedFact = factReaderAfterCleaner.factAp
        check(cleanedFact.base == startFactBase)

        val unmappedFact = cleanedFact.rebase(originalFactReader.factAp.base)

        // FIXME: adhoc for constructors:
        if (method.isConstructor) {
            addCallToReturn(originalFactReader, unmappedFact, TraceInfo.Flow)
        }

        addCallToStart(originalFactReader, unmappedFact, startFactBase, TraceInfo.Flow)
    }

    private fun applySinkRules(
        factReader: FinalFactReader?,
        markAfterAnyFieldResolver: FactWithMarkAfterAnyAccessorResolver?,
    ): List<Pair<FinalFactAp, TraceInfo>> {
        val sinkRules = taintCtx.sinkRulesForCallStatement(statement, callExpr, returnValue, factReader?.factAp)
        if (sinkRules.isEmpty()) return emptyList()

        val taintUtil = JIRMethodCallTaintUtil(apManager, statement, callExpr, analysisContext, generateTrace)
        taintUtil.applySinkRules(
            sinkRules, factReader, markAfterAnyFieldResolver
        )
        return taintUtil.factsAfterSink
    }

    private fun applySourceRules(
        initialFacts: Set<InitialFactAp>,
        factReader: FinalFactReader?,
        exclusion: ExclusionSet,
        createFinalFact: (FinalFactAp, TraceInfo) -> Unit,
        createEdge: (InitialFactAp, FinalFactAp, TraceInfo) -> Unit,
        createNDEdge: (Set<InitialFactAp>, FinalFactAp, TraceInfo) -> Unit,
        markAfterAnyFieldResolver: FactWithMarkAfterAnyAccessorResolver? = null,
    ) {
        val sourceRules = taintCtx.sourceRulesForCallStatement(statement, callExpr, returnValue, factReader?.factAp)
        if (sourceRules.isEmpty()) return

        val taintUtil = JIRMethodCallTaintUtil(apManager, statement, callExpr, analysisContext, generateTrace)
        taintUtil.applySourceRules(
            sourceRules, initialFacts, factReader, exclusion,
            createFinalFact, createEdge, createNDEdge,
            markAfterAnyFieldResolver
        )
    }

    override fun propagateUnresolvedCallFact(
        factAp: FinalFactAp,
        addCallToReturn: (FinalFactReader, FinalFactAp, TraceInfo?) -> Unit,
        addSideEffectRequirement: (FinalFactReader) -> Unit
    ) {
        val factReader = FinalFactReader(factAp, apManager)

        unresolvedCallDefaultFactPropagation(factAp, addCallToReturn)

        val method = callExpr.callee
        JIRMethodCallFactMapper.mapMethodCallToStartFlowFact(
            statement,
            callee = method,
            callExpr = callExpr,
            returnValue = null,
            factAp = factAp,
            checker = analysisContext.factTypeChecker
        ) { callerFact, startFactBase ->
            val passFactReader = FinalFactReader(callerFact.rebase(startFactBase), apManager)

            val conditionEvaluator = TaintFactAwareConditionEvaluator(
                listOf(passFactReader),
                markAfterAnyAccessorResolver = null // we don't expect such marks in pass rules
            )

            val passEvaluator = TaintPassActionEvaluator(
                apManager, analysisContext.factTypeChecker, passFactReader, typeResolver
            )

            val passRules = taintCtx.passRulesForCallStatement(statement, callExpr, returnValue, passFactReader.factAp)
            var passThroughFacts = applyPassThrough(passRules, conditionEvaluator, passEvaluator)

            if (startFactBase !is AccessPathBase.ClassStatic) {
                analysisContext.taint.externalMethodTracker?.let { tracker ->
                    if (JIRCallResolver.alwaysIgnoreMethod(method)) return@let

                    val methodName = "${method.enclosingClass.name}#${method.name}"
                    val methodDesc = method.description
                    val factPosition = startFactBase.toString()
                    val ruleApplied = startFactBase in passEvaluator.relevantPositionBase
                    tracker.trackExternalMethod(methodName, methodDesc, factPosition, ruleApplied)
                }
            }

            if (passThroughFacts.isNone) {
                analysisContext.analysisManager.params.defaultGetModel?.run {
                    val defaultRules = defaultPropagationRules(method)
                    val defaultPass = applyPassThrough(defaultRules, conditionEvaluator, passEvaluator)
                    passThroughFacts = passThroughFacts.merge(defaultPass)
                }
            }

            passThroughFacts.onSome { evaluatedPass ->
                evaluatedPass.forEach { evp ->
                    val rewrittenFacts = summaryRewriter.rewriteSummaryFact(evp.fact)
                    for ((unrefinedFact, factRefinement) in rewrittenFacts) {
                        val fact = factRefinement.refineFact(unrefinedFact)
                        passFactReader.updateRefinement(factRefinement)

                        val mappedFact = fact.mapExitToReturnFact() ?: continue

                        val trace = TraceInfo.Rule(evp.rule, evp.action)

                        mappedFact.forEachFactWithAliases(factAp) {
                            addCallToReturn(passFactReader, it, trace)
                        }
                    }
                }
            }

            factReader.updateRefinement(passFactReader)
        }

        if (factReader.hasRefinement) {
            addSideEffectRequirement(factReader)
        }
    }

    private fun unresolvedCallDefaultFactPropagation(
        factAp: FinalFactAp,
        addCallToReturn: (FinalFactReader, FinalFactAp, TraceInfo?) -> Unit,
    ) {
        val rewrittenFacts = summaryRewriter.rewriteSummaryFact(factAp)
        for ((unrefinedFact, factRefinement) in rewrittenFacts) {
            val fact = factRefinement.refineFact(unrefinedFact)
            addCallToReturn(factRefinement, fact, null)
        }
    }

    private fun FinalFactAp.mapExitToReturnFact(): FinalFactAp? =
        JIRMethodCallFactMapper.mapMethodExitToReturnFlowFact(statement, this, analysisContext.factTypeChecker)
            .singleOrNull()


    private fun FinalFactReader.updateRefinement(conditionFactReaders: List<FinalFactReader>) {
        conditionFactReaders.forEach { updateRefinement(it) }
    }

    private inline fun FinalFactAp.forEachSourceFactWithAliases(crossinline body: (FinalFactAp) -> Unit) =
        forEachFactWithAliases(originalFact = null, body)

    private inline fun FinalFactAp.forEachFactWithAliases(originalFact: FinalFactAp?,  crossinline body: (FinalFactAp) -> Unit) {
        body(this)

        if (originalFact != null && originalFact == this) {
            return
        }

        analysisContext.aliasAnalysis?.forEachAliasAfterCallStatement(statement, this) { aliased ->
            body(aliased)
        }
    }
}

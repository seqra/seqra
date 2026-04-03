package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnFFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnNonDistributiveFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnZFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnZeroFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToStartFFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToStartZFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToStartZeroFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.SideEffectRequirement
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.TraceInfo
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.Unchanged
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintMethodSource
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodPositionBaseTypeResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRSimpleFactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyCleaner
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyPassThrough
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyRuleWithAssumptions
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.sinkRules
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.FinalFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.FinalFactReaderWithPrefix
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRFactWithMarkAfterAnyFieldResolver
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRFactWithMarkAfterAnyFieldResolver.Companion.createMarkAfterFieldsResolver
import org.opentaint.dataflow.jvm.ap.ifds.taint.PositionAccess
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintCleanActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintPassActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintSourceActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.UserDefinedRuleInfo
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.dataflow.util.cartesianProductMapTo
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.util.onSome

class JIRMethodCallFlowFunction(
    private val apManager: ApManager,
    private val analysisContext: JIRMethodAnalysisContext,
    private val returnValue: JIRImmediate?,
    private val callExpr: JIRCallExpr,
    private val statement: JIRInst,
    private val generateTrace: Boolean,
): MethodCallFlowFunction {
    private val config get() = analysisContext.taint.taintConfig as TaintRulesProvider
    private val sinkTracker get() = analysisContext.taint.taintSinkTracker

    private val summaryRewriter by lazy {
        JIRMethodCallRuleBasedSummaryRewriter(statement, analysisContext, apManager)
    }

    override fun propagateZeroToZero() = buildSet {
        val conditionRewriter = JIRMarkAwareConditionRewriter(
            CallPositionToJIRValueResolver(callExpr, returnValue),
            analysisContext, statement
        )

        applySinkRules(
            conditionRewriter, factReader = null,
            markAfterAnyFieldResolver = null
        ).forEach { (fact, trace) ->
            fact.forEachSourceFactWithAliases { this += CallToReturnZFact(factAp = it, trace) }
        }

        applySourceRules(
            initialFacts = emptySet(), conditionRewriter, factReader = null, exclusion = ExclusionSet.Universe,
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

    override fun propagateZeroToFact(currentFactAp: FinalFactAp) = buildSet {
        propagateFact(
            initialFacts = emptySet(),
            exclusion = ExclusionSet.Universe,
            factAp = currentFactAp,
            skipCall = { this += Unchanged },
            addSideEffectRequirement = { factReader ->
                check(!factReader.hasRefinement) { "Can't refine Zero fact" }
            },
            addCallToReturn = { factReader, factAp, trace ->
                check(!factReader.hasRefinement) { "Can't refine Zero fact" }
                this += CallToReturnZFact(factAp, trace)
            },
            addCallToStart = { factReader, callerFactAp, startFactBase, trace ->
                check(!factReader.hasRefinement) { "Can't refine Zero fact" }
                this += CallToStartZFact(callerFactAp, startFactBase, trace)
            },
            addUnchecked = {
                check(it is MethodCallFlowFunction.ZeroCallFact) { "unexpected" }
                this += it
            },
        )
    }

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp
    ) = buildSet {
        propagateFact(
            initialFacts = setOf(initialFactAp),
            exclusion = initialFactAp.exclusions,
            factAp = currentFactAp,
            skipCall = { this += Unchanged },
            addSideEffectRequirement = { factReader ->
                this += SideEffectRequirement(factReader.refineFact(initialFactAp.replaceExclusions(ExclusionSet.Empty)))
            },
            addCallToReturn = { factReader, factAp, trace ->
                this += CallToReturnFFact(
                    factReader.refineFact(initialFactAp),
                    factReader.refineFact(factAp),
                    trace
                )
            },
            addCallToStart = { factReader, callerFactAp, startFactBase, trace ->
                this += CallToStartFFact(
                    factReader.refineFact(initialFactAp),
                    factReader.refineFact(callerFactAp),
                    startFactBase, trace
                )
            },
            addUnchecked = {
                check(it is MethodCallFlowFunction.FactCallFact) { "unexpected" }
                this += it
            },
        )
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp
    ): Set<MethodCallFlowFunction.NDFactCallFact> = buildSet {
        propagateFact(
            initialFacts = initialFacts,
            exclusion = ExclusionSet.Universe,
            factAp = currentFactAp,
            skipCall = { this += Unchanged },
            addSideEffectRequirement = { factReader ->
                check(!factReader.hasRefinement) { "Can't refine NDF2F edge" }
            },
            addCallToReturn = { factReader, factAp, trace ->
                check(!factReader.hasRefinement) { "Can't refine NDF2F edge" }
                this += CallToReturnNonDistributiveFact(initialFacts, factAp, trace)
            },
            addCallToStart = { factReader, callerFactAp, startFactBase, trace ->
                check(!factReader.hasRefinement) { "Can't refine NDF2F edge" }
                this += MethodCallFlowFunction.CallToStartNDFFact(
                    initialFacts, callerFactAp,
                    startFactBase, trace
                )
            },
            addUnchecked = {
                check(it is MethodCallFlowFunction.NDFactCallFact) { "unexpected" }
                this += it
            },
        )
    }

    override fun propagateZeroToZeroResolutionFailure(): Set<MethodCallFlowFunction.ZeroCallFailureFact> = setOf(CallToReturnZeroFact)

    override fun propagateZeroToFactResolutionFailure(currentFactAp: FinalFactAp, startFactBase: AccessPathBase) = buildSet {
        applyPassRulesOrCallSkip(
            factAp = currentFactAp,
            addSideEffectRequirement = { factReader ->
                check(!factReader.hasRefinement) { "Can't refine Zero fact" }
            },
            addCallToReturn = { factReader, factAp, trace ->
                check(!factReader.hasRefinement) { "Can't refine Zero fact" }
                this += CallToReturnZFact(factAp, trace)
            },
        )
    }

    override fun propagateFactToFactResolutionFailure(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
        startFactBase: AccessPathBase
    ): Set<MethodCallFlowFunction.FactCallFailureFact> = buildSet {
        applyPassRulesOrCallSkip(
            factAp = currentFactAp,
            addSideEffectRequirement = { factReader ->
                this += SideEffectRequirement(factReader.refineFact(initialFactAp.replaceExclusions(ExclusionSet.Empty)))
            },
            addCallToReturn = { factReader, factAp, trace ->
                this += CallToReturnFFact(
                    factReader.refineFact(initialFactAp),
                    factReader.refineFact(factAp),
                    trace
                )
            },
        )
    }

    override fun propagateNDFactToFactResolutionFailure(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
        startFactBase: AccessPathBase
    ) = buildSet {
        applyPassRulesOrCallSkip(
            factAp = currentFactAp,
            addSideEffectRequirement = { factReader ->
                check(!factReader.hasRefinement) { "Can't refine NDF2F edge" }
            },
            addCallToReturn = { factReader, factAp, trace ->
                check(!factReader.hasRefinement) { "Can't refine NDF2F edge" }
                this += CallToReturnNonDistributiveFact(initialFacts, factAp, trace)
            },
        )
    }

    private fun propagateFact(
        initialFacts: Set<InitialFactAp>,
        exclusion: ExclusionSet,
        factAp: FinalFactAp,
        skipCall: () -> Unit,
        addSideEffectRequirement: (FinalFactReader) -> Unit,
        addCallToReturn: (FinalFactReader, FinalFactAp, TraceInfo) -> Unit,
        addCallToStart: (factReader: FinalFactReader, callerFact: FinalFactAp, startFactBase: AccessPathBase, TraceInfo) -> Unit,
        addUnchecked: (MethodCallFlowFunction.CallFact) -> Unit,
    ) {
        val relevantBases = callExpr.operands.mapNotNull { MethodFlowFunctionUtils.accessPathBase(it) }

        val (aliasedFacts, irrelevantFacts) =
            FactUtils.splitFactMultipleBases(analysisContext.aliasAnalysis, statement, relevantBases, factAp, true)

        var fixedFactAp: FinalFactAp? = null

        aliasedFacts.forEach { (fact, _) ->
            if (JIRMethodCallFactMapper.factIsRelevantToMethodCall(returnValue, callExpr, fact))
                fixedFactAp = fact
        }

        if (fixedFactAp == null) {
            skipCall()
            return
        }

        irrelevantFacts.forEach { fact ->
            val reader = FinalFactReader(fact, apManager)
            addCallToReturn(reader, fact, TraceInfo.Flow)
        }

        val conditionRewriter = JIRMarkAwareConditionRewriter(
            CallPositionToJIRValueResolver(callExpr, returnValue),
            analysisContext, statement
        )

        val factReader = FinalFactReader(fixedFactAp, apManager)

        val markAfterAnyFieldResolver = createMarkAfterFieldsResolver(
            analysisContext.methodEntryPoint, initialFacts
        ) { i, k ->
            addUnchecked(MethodCallFlowFunction.FactSideEffect(i, k))
        }

        applySinkRules(conditionRewriter, factReader, markAfterAnyFieldResolver).forEach { (fact, trace) ->
            fact.forEachSourceFactWithAliases {
                addUnchecked(CallToReturnZFact(it, trace))
            }
        }

        applySourceRules(
            initialFacts, conditionRewriter, factReader, exclusion,
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
            }
        )

        JIRMethodCallFactMapper.mapMethodCallToStartFlowFact(
            callee = callExpr.callee,
            callExpr = callExpr,
            returnValue = null,
            factAp = fixedFactAp,
            checker = analysisContext.factTypeChecker,
        ) { callerFact, startFactBase ->
            applyCleanersOrCallToStart(
                conditionRewriter,
                factReader, callerFact, startFactBase,
                addCallToReturn, addCallToStart, addUnchecked
            )
        }

        if (factReader.hasRefinement) {
            addSideEffectRequirement(factReader)
        }
    }

    private fun applyCleanersOrCallToStart(
        conditionRewriter: JIRMarkAwareConditionRewriter,
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

        val conditionEvaluator = JIRFactAwareConditionEvaluator(
            listOf(conditionFactReader),
            markAfterAnyFieldResolver = null // we don't expect such marks in pass rules
        )

        val simpleConditionEvaluator = JIRSimpleFactAwareConditionEvaluator(conditionRewriter, conditionEvaluator)

        val typeResolver = JIRMethodPositionBaseTypeResolver(method)
        val cleaner = TaintCleanActionEvaluator(typeResolver)

        val factReaderBeforeCleaner = FinalFactReader(callerFact, apManager)
        val cleanerResults = applyCleaner(
            config,
            method,
            statement,
            factReaderBeforeCleaner,
            simpleConditionEvaluator,
            cleaner
        )

        originalFactReader.updateRefinement(listOf(conditionFactReader))

        for (cleanerResult in cleanerResults) {
            val factReaderAfterCleaner = cleanerResult.fact
            if (factReaderAfterCleaner == null) {
                val trace = cleanerResult.action
                    ?.takeIf { it.rule.info is UserDefinedRuleInfo }
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
        conditionRewriter: JIRMarkAwareConditionRewriter,
        factReader: FinalFactReader?,
        markAfterAnyFieldResolver: JIRFactWithMarkAfterAnyFieldResolver?,
    ): List<Pair<FinalFactAp, TraceInfo>> {
        val sinkRules = sinkRules(config, callExpr.callee, statement, factReader?.factAp).toList()
        if (sinkRules.isEmpty()) return emptyList()

        val normalConditionFactReaders = factReader?.toConditionFactReaders().orEmpty()

        val arrayElementFactReaders = normalConditionFactReaders.arrayElementConditionReaders(callExpr)

        val conditionFactReaders = normalConditionFactReaders + arrayElementFactReaders

        val factsAfterSink = mutableListOf<Pair<FinalFactAp, TraceInfo>>()
        val factAfterSinkEvaluator by lazy {
            TaintSourceActionEvaluator(
                apManager,
                exclusion = ExclusionSet.Universe,
            )
        }

        sinkRules.applyRuleWithAssumptions(
            apManager,
            conditionRewriter,
            conditionFactReaders,
            markAfterAnyFieldResolver,
            condition = { condition },
            storeAssumptions = { rule, facts ->
                storeInfo {
                    sinkTracker.addSinkRuleAssumptions(rule, statement, facts)
                }
            },
            currentAssumptions = { rule -> sinkTracker.currentSinkRuleAssumptions(rule, statement) }
        ) { rule, evaluatedFacts ->
            if (evaluatedFacts.isEmpty()) {
                // unconditional sinks handled with zero fact
                if (factReader != null) return@applyRuleWithAssumptions

                if (rule.trackFactsReachAnalysisEnd.isEmpty()) {
                    storeInfo {
                        sinkTracker.addUnconditionalVulnerability(
                            analysisContext.methodEntryPoint, statement, rule
                        )
                    }

                    return@applyRuleWithAssumptions
                }

                val requiredEndFacts = hashSetOf<FinalFactAp>()
                applySourceAction(rule, rule.trackFactsReachAnalysisEnd, factAfterSinkEvaluator) { f, action ->
                    requiredEndFacts += f

                    val trace = TraceInfo.Rule(rule, action)
                    factsAfterSink += f to trace
                }

                storeInfo {
                    sinkTracker.addUnconditionalVulnerabilityWithEndFactRequirement(
                        analysisContext.methodEntryPoint, statement, rule, requiredEndFacts
                    )
                }

                return@applyRuleWithAssumptions
            }

            val mappedFacts = evaluatedFacts.mapTo(hashSetOf()) {
                it.mapExitToReturnFact() ?: error("Fact mapping failure")
            }

            if (rule.trackFactsReachAnalysisEnd.isEmpty()) {
                storeInfo {
                    sinkTracker.addVulnerability(
                        analysisContext.methodEntryPoint, mappedFacts, statement, rule
                    )
                }

                return@applyRuleWithAssumptions
            }

            val requiredEndFacts = hashSetOf<FinalFactAp>()
            applySourceAction(rule, rule.trackFactsReachAnalysisEnd, factAfterSinkEvaluator) { f, action ->
                requiredEndFacts += f

                val trace = TraceInfo.Rule(rule, action)
                factsAfterSink += f to trace
            }

            storeInfo {
                sinkTracker.addVulnerabilityWithEndFactRequirement(
                    analysisContext.methodEntryPoint, mappedFacts, statement, rule, requiredEndFacts
                )
            }

            return@applyRuleWithAssumptions
        }

        factReader?.updateRefinement(normalConditionFactReaders)
        return factsAfterSink
    }

    private fun applySourceRules(
        initialFacts: Set<InitialFactAp>,
        conditionRewriter: JIRMarkAwareConditionRewriter,
        factReader: FinalFactReader?,
        exclusion: ExclusionSet,
        createFinalFact: (FinalFactAp, TraceInfo) -> Unit,
        createEdge: (InitialFactAp, FinalFactAp, TraceInfo) -> Unit,
        createNDEdge: (Set<InitialFactAp>, FinalFactAp, TraceInfo) -> Unit,
    ) {
        val method = callExpr.method.method
        val sourceRules = config.sourceRulesForMethod(method, statement, factReader?.factAp).toList()

        if (sourceRules.isEmpty()) return

        val conditionFactReaders = factReader?.toConditionFactReaders().orEmpty()

        val sourceEvaluator = TaintSourceActionEvaluator(
            apManager, exclusion,
        )

        sourceRules.applyRuleWithAssumptions(
            apManager,
            conditionRewriter,
            initialFacts,
            conditionFactReaders,
            markAfterAnyFieldResolver = null, // we don't expect such marks in source rules
            condition = { condition },
            storeAssumptions = { rule, facts ->
                storeInfo { sinkTracker.addSourceRuleAssumptions(rule, statement, facts) }
            },
            currentAssumptions = { rule -> sinkTracker.currentSourceRuleAssumptions(rule, statement) },
            currentAssumptionPreconditions = { rule, facts ->
                sinkTracker.currentSourceRuleAssumptionsPreconditions(rule, statement, facts)
            },
            applyRule = { rule, evaluatedFacts ->
                // unconditional sources handled with zero fact
                if (evaluatedFacts.isEmpty() && factReader != null) return@applyRuleWithAssumptions

                applySourceAction(rule, sourceEvaluator, createFinalFact)
            },
            applyRuleWithAssumptions = { rule, factsWithPreconditions ->
                val factPreconditions = factsWithPreconditions.map { it.preconditions }
                factPreconditions.cartesianProductMapTo { preconditions ->
                    val nonZeroPreconditions = hashSetOf<InitialFactAp>()
                    for (precondition in preconditions) {
                        if (precondition.isEmpty()) continue

                        nonZeroPreconditions.addAll(precondition)
                    }

                    if (nonZeroPreconditions.isEmpty()) {
                        check(initialFacts.isEmpty()) {
                            "Unexpected zero precondition"
                        }

                        applySourceAction(rule, sourceEvaluator, createFinalFact)
                        return@cartesianProductMapTo
                    }

                    if (nonZeroPreconditions.size == 1) {
                        val precondition = nonZeroPreconditions.first()

                        if (initialFacts.isEmpty()) {
                            // Here initial fact ends with taint mark and exclusion can be ignored
                            val newInitial = precondition.replaceExclusions(ExclusionSet.Empty)
                            applySourceAction(rule, sourceEvaluator) { fact, trace ->
                                createEdge(newInitial, fact.replaceExclusions(ExclusionSet.Empty), trace)
                            }

                            return@cartesianProductMapTo
                        }

                        if (initialFacts.size == 1) {
                            val initialFact = initialFacts.first()

                            check(precondition == initialFact.replaceExclusions(ExclusionSet.Universe)) {
                                "Unexpected fact precondition"
                            }

                            applySourceAction(rule, sourceEvaluator, createFinalFact)
                            return@cartesianProductMapTo
                        }

                        error("Multiple initial facts not expected here")
                    }

                    applySourceAction(rule, sourceEvaluator) { fact, trace ->
                        createNDEdge(
                            nonZeroPreconditions,
                            fact.replaceExclusions(ExclusionSet.Universe),
                            trace
                        )
                    }
                }
            }
        )

        factReader?.updateRefinement(conditionFactReaders)
    }

    private inline fun applySourceAction(
        rule: TaintMethodSource,
        sourceEvaluator: TaintSourceActionEvaluator,
        createFinalFact: (FinalFactAp, TraceInfo) -> Unit,
    ) = applySourceAction(rule, rule.actionsAfter, sourceEvaluator) { f, action ->
        val trace = TraceInfo.Rule(rule, action)
        createFinalFact(f, trace)
    }

    private inline fun applySourceAction(
        rule: TaintConfigurationItem,
        actions: List<AssignMark>,
        sourceEvaluator: TaintSourceActionEvaluator,
        createFinalFact: (FinalFactAp, AssignMark) -> Unit,
    ) {
        for (action in actions) {
            sourceEvaluator.evaluate(rule, action).onSome { facts ->
                facts.forEach { f -> f.mapExitToReturnFact()?.also { createFinalFact(it, action) } }
            }
        }
    }

    private fun applyPassRulesOrCallSkip(
        factAp: FinalFactAp,
        addCallToReturn: (FinalFactReader, FinalFactAp, TraceInfo?) -> Unit,
        addSideEffectRequirement: (FinalFactReader) -> Unit,
    ) {
        analysisContext.aliasAnalysis?.forEachMustAlias(statement, factAp) { fact ->
            val aliasReader = FinalFactReader(fact, apManager)
            unresolvedCallDefaultFactPropagation(aliasReader, fact, addCallToReturn)
        }

        val factReader = FinalFactReader(factAp, apManager)
        unresolvedCallDefaultFactPropagation(factReader, factAp, addCallToReturn)

        val method = callExpr.callee
        val conditionRewriter = JIRMarkAwareConditionRewriter(
            CallPositionToJIRValueResolver(callExpr, returnValue),
            analysisContext, statement
        )

        JIRMethodCallFactMapper.mapMethodCallToStartFlowFact(
            callee = method,
            callExpr = callExpr,
            returnValue = null,
            factAp = factAp,
            checker = analysisContext.factTypeChecker
        ) { callerFact, startFactBase ->
            val passFactReader = FinalFactReader(callerFact.rebase(startFactBase), apManager)

            val conditionEvaluator = JIRFactAwareConditionEvaluator(
                listOf(passFactReader),
                markAfterAnyFieldResolver = null // we don't expect such marks in pass rules
            )
            val simpleConditionEvaluator = JIRSimpleFactAwareConditionEvaluator(conditionRewriter, conditionEvaluator)
            val typeResolver = JIRMethodPositionBaseTypeResolver(method)
            val passEvaluator = TaintPassActionEvaluator(
                apManager, analysisContext.factTypeChecker, passFactReader, typeResolver
            )

            val passThroughFacts = applyPassThrough(
                config,
                method,
                statement,
                fact = passFactReader.factAp,
                simpleConditionEvaluator,
                passEvaluator
            )

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
        factReader: FinalFactReader,
        factAp: FinalFactAp,
        addCallToReturn: (FinalFactReader, FinalFactAp, TraceInfo?) -> Unit,
    ) {
        addCallToReturn(factReader, factAp, null)
    }

    private fun FinalFactAp.mapExitToReturnFact(): FinalFactAp? =
        JIRMethodCallFactMapper.mapMethodExitToReturnFlowFact(statement, this, analysisContext.factTypeChecker)
            .singleOrNull()

    private fun InitialFactAp.mapExitToReturnFact(): InitialFactAp? =
        JIRMethodCallFactMapper.mapMethodExitToReturnFlowFact(statement, this)
            .singleOrNull()

    private fun FinalFactReader.toConditionFactReaders(): List<FinalFactReader> {
        val conditionFactReaders = mutableListOf<FinalFactReader>()
        JIRMethodCallFactMapper.mapMethodCallToStartFlowFact(
            callee = callExpr.callee,
            callExpr = callExpr,
            returnValue = null,
            factAp = factAp,
            checker = analysisContext.factTypeChecker
        ) { callerFact, startFactBase ->
            conditionFactReaders += FinalFactReader(callerFact.rebase(startFactBase), apManager)
        }
        return conditionFactReaders
    }

    private fun FinalFactReader.updateRefinement(conditionFactReaders: List<FinalFactReader>) {
        conditionFactReaders.forEach { updateRefinement(it) }
    }

    private fun List<FinalFactReader>.arrayElementConditionReaders(callExpr: JIRCallExpr): List<FactReader> =
        mapNotNull {
            val base = it.factAp.base as? AccessPathBase.Argument ?: return@mapNotNull null

            if (!analysisContext.factTypeChecker.callArgumentMayBeArray(callExpr, base)) {
                return@mapNotNull null
            }

            val arrayElementPosition = PositionAccess.Complex(PositionAccess.Simple(base), ElementAccessor)
            if (!it.containsPosition(arrayElementPosition)) return@mapNotNull null

            FinalFactReaderWithPrefix(it, ElementAccessor)
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

    private inline fun storeInfo(body: () -> Unit) {
        if (generateTrace) return
        body()
    }
}

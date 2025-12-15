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
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.Unchanged
import org.opentaint.dataflow.configuration.jvm.TaintMethodSource
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodPositionBaseTypeResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRSimpleFactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyCleaner
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyPassThrough
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyRuleWithAssumptions
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.sinkRules
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.FinalFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.FinalFactReaderWithPrefix
import org.opentaint.dataflow.jvm.ap.ifds.taint.PositionAccess
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintCleanActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintPassActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintSourceActionEvaluator
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.dataflow.util.cartesianProductMapTo
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
): MethodCallFlowFunction {
    private val config get() = analysisContext.taint.taintConfig as TaintRulesProvider
    private val sinkTracker get() = analysisContext.taint.taintSinkTracker

    override fun propagateZeroToZero() = buildSet {
        val conditionRewriter = JIRMarkAwareConditionRewriter(
            CallPositionToJIRValueResolver(callExpr, returnValue),
            analysisContext.factTypeChecker
        )

        applySinkRules(conditionRewriter, factReader = null)

        applySourceRules(
            initialFacts = emptySet(), conditionRewriter, factReader = null, exclusion = ExclusionSet.Universe,
            createFinalFact = { fact ->
                fact.forEachFactWithAliases { this += CallToReturnZFact(factAp = it) }
            },
            createEdge = { initial, final ->
                final.forEachFactWithAliases { this += CallToReturnFFact(initial, it) }
            },
            createNDEdge = { initial, final ->
                final.forEachFactWithAliases { this += CallToReturnNonDistributiveFact(initial, it) }
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
            addCallToReturn = { factReader, factAp ->
                check(!factReader.hasRefinement) { "Can't refine Zero fact" }
                this += CallToReturnZFact(factAp)
            },
            addCallToStart = { factReader, callerFactAp, startFactBase ->
                check(!factReader.hasRefinement) { "Can't refine Zero fact" }
                this += CallToStartZFact(callerFactAp, startFactBase)
            },
            addCallToReturnF2F = { this += it },
            addCallToReturnND = { this += it }
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
            addCallToReturn = { factReader, factAp ->
                this += CallToReturnFFact(factReader.refineFact(initialFactAp), factReader.refineFact(factAp))
            },
            addCallToStart = { factReader, callerFactAp, startFactBase ->
                this += CallToStartFFact(
                    factReader.refineFact(initialFactAp),
                    factReader.refineFact(callerFactAp),
                    startFactBase
                )
            },
            addCallToReturnF2F = { this += it },
            addCallToReturnND = { this += it }
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
            addCallToReturn = { factReader, factAp ->
                check(!factReader.hasRefinement) { "Can't refine NDF2F edge" }
                this += CallToReturnNonDistributiveFact(initialFacts, factAp)
            },
            addCallToStart = { factReader, callerFactAp, startFactBase ->
                check(!factReader.hasRefinement) { "Can't refine NDF2F edge" }
                this += MethodCallFlowFunction.CallToStartNDFFact(initialFacts, callerFactAp, startFactBase)
            },
            addCallToReturnF2F = { error("Unexpected") },
            addCallToReturnND = { this += it }
        )
    }

    private fun propagateFact(
        initialFacts: Set<InitialFactAp>,
        exclusion: ExclusionSet,
        factAp: FinalFactAp,
        skipCall: () -> Unit,
        addSideEffectRequirement: (FinalFactReader) -> Unit,
        addCallToReturn: (FinalFactReader, FinalFactAp) -> Unit,
        addCallToStart: (factReader: FinalFactReader, callerFact: FinalFactAp, startFactBase: AccessPathBase) -> Unit,
        addCallToReturnF2F: (CallToReturnFFact) -> Unit,
        addCallToReturnND: (CallToReturnNonDistributiveFact) -> Unit
    ) {
        if (!JIRMethodCallFactMapper.factIsRelevantToMethodCall(returnValue, callExpr, factAp)) {
            skipCall()
            return
        }

        val conditionRewriter = JIRMarkAwareConditionRewriter(
            CallPositionToJIRValueResolver(callExpr, returnValue),
            analysisContext.factTypeChecker
        )

        val factReader = FinalFactReader(factAp, apManager)

        applySinkRules(conditionRewriter, factReader)

        applySourceRules(
            initialFacts, conditionRewriter, factReader, exclusion,
            createFinalFact = { fact ->
                fact.forEachFactWithAliases { addCallToReturn(factReader, it) }
            },
            createEdge = { initial, final ->
                final.forEachFactWithAliases {
                    addCallToReturnF2F(CallToReturnFFact(initial, it))
                }
            },
            createNDEdge = { initial, final ->
                final.forEachFactWithAliases {
                    addCallToReturnND(CallToReturnNonDistributiveFact(initial, it))
                }
            }
        )

        JIRMethodCallFactMapper.mapMethodCallToStartFlowFact(
            callExpr.callee,
            callExpr,
            factAp,
            analysisContext.factTypeChecker
        ) { callerFact, startFactBase ->
            applyPassRulesOrCallToStart(
                conditionRewriter,
                factReader, callerFact, startFactBase, addCallToReturn, addCallToStart
            )
        }

        if (factReader.hasRefinement) {
            addSideEffectRequirement(factReader)
        }
    }

    private fun applyPassRulesOrCallToStart(
        conditionRewriter: JIRMarkAwareConditionRewriter,
        originalFactReader: FinalFactReader,
        unmappedCallerFactAp: FinalFactAp,
        startFactBase: AccessPathBase,
        addCallToReturn: (FinalFactReader, FinalFactAp) -> Unit,
        addCallToStart: (factReader: FinalFactReader, callerFactAp: FinalFactAp, startFactBase: AccessPathBase) -> Unit,
    ) {
        val method = callExpr.callee

        val callerFact = unmappedCallerFactAp.rebase(startFactBase)
        val conditionFactReader = FinalFactReader(callerFact, apManager)

        val conditionEvaluator = JIRFactAwareConditionEvaluator(
            listOf(conditionFactReader)
        )

        val simpleConditionEvaluator = JIRSimpleFactAwareConditionEvaluator(conditionRewriter, conditionEvaluator)

        val cleaner = TaintCleanActionEvaluator()

        val factReaderBeforeCleaner = FinalFactReader(callerFact, apManager)
        val factReaderAfterCleaner = applyCleaner(
            config,
            method,
            statement,
            factReaderBeforeCleaner,
            simpleConditionEvaluator,
            cleaner
        ) ?: return

        val typeResolver = JIRMethodPositionBaseTypeResolver(method)
        val passEvaluator = TaintPassActionEvaluator(
            apManager, analysisContext.factTypeChecker, factReaderAfterCleaner, typeResolver
        )

        val passThroughFacts = applyPassThrough(
            config,
            method,
            statement,
            simpleConditionEvaluator,
            passEvaluator
        )

        originalFactReader.updateRefinement(listOf(conditionFactReader))
        originalFactReader.updateRefinement(listOf(factReaderAfterCleaner))

        passThroughFacts.onSome { facts ->
            facts.forEach { fact ->
                val mappedFact = fact.mapExitToReturnFact() ?: return@forEach

                addCallToReturn(factReaderAfterCleaner, mappedFact)

                analysisContext.aliasAnalysis?.forEachAliasAtStatement(statement, mappedFact) { aliased ->
                    addCallToReturn(factReaderAfterCleaner, aliased)
                }
            }

            // Skip method invocation
            return
        }

        val cleanedFact = factReaderAfterCleaner.factAp
        check(cleanedFact.base == startFactBase)

        val unmappedFact = cleanedFact.rebase(originalFactReader.factAp.base)

        // FIXME: adhoc for constructors:
        if (method.isConstructor) {
            addCallToReturn(originalFactReader, unmappedFact)
        }

        addCallToStart(originalFactReader, unmappedFact, startFactBase)
    }

    private fun applySinkRules(
        conditionRewriter: JIRMarkAwareConditionRewriter,
        factReader: FinalFactReader?,
    ) {
        val sinkRules = sinkRules(config, callExpr.callee, statement).toList()
        if (sinkRules.isEmpty()) return

        val normalConditionFactReaders = factReader?.toConditionFactReaders().orEmpty()

        val arrayElementFactReaders = normalConditionFactReaders.arrayElementConditionReaders(callExpr)

        val conditionFactReaders = normalConditionFactReaders + arrayElementFactReaders

        sinkRules.applyRuleWithAssumptions(
            apManager,
            conditionRewriter,
            conditionFactReaders,
            condition = { condition },
            storeAssumptions = { rule, facts -> sinkTracker.addSinkRuleAssumptions(rule, statement, facts) },
            currentAssumptions = { rule -> sinkTracker.currentSinkRuleAssumptions(rule, statement) }
        ) { rule, evaluatedFacts ->
            if (evaluatedFacts.isEmpty()) {
                // unconditional sinks handled with zero fact
                if (factReader != null) return@applyRuleWithAssumptions

                sinkTracker.addUnconditionalVulnerability(
                    analysisContext.methodEntryPoint, statement, rule
                )

                return@applyRuleWithAssumptions
            }

            val mappedFacts = evaluatedFacts.mapTo(hashSetOf()) {
                it.mapExitToReturnFact() ?: error("Fact mapping failure")
            }

            sinkTracker.addVulnerability(
                analysisContext.methodEntryPoint, mappedFacts, statement, rule
            )
        }

        factReader?.updateRefinement(normalConditionFactReaders)
    }

    private fun applySourceRules(
        initialFacts: Set<InitialFactAp>,
        conditionRewriter: JIRMarkAwareConditionRewriter,
        factReader: FinalFactReader?,
        exclusion: ExclusionSet,
        createFinalFact: (FinalFactAp) -> Unit,
        createEdge: (InitialFactAp, FinalFactAp) -> Unit,
        createNDEdge: (Set<InitialFactAp>, FinalFactAp) -> Unit,
    ) {
        val method = callExpr.method.method
        val sourceRules = config.sourceRulesForMethod(method, statement).toList()

        if (sourceRules.isEmpty()) return

        val conditionFactReaders = factReader?.toConditionFactReaders().orEmpty()

        val sourceEvaluator = TaintSourceActionEvaluator(
            apManager, exclusion, analysisContext.factTypeChecker, returnValueType = callExpr.method.returnType,
        )

        sourceRules.applyRuleWithAssumptions(
            apManager,
            conditionRewriter,
            initialFacts,
            conditionFactReaders,
            condition = { condition },
            storeAssumptions = { rule, facts -> sinkTracker.addSourceRuleAssumptions(rule, statement, facts) },
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
                            applySourceAction(rule, sourceEvaluator) { fact ->
                                createEdge(newInitial, fact.replaceExclusions(ExclusionSet.Empty))
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

                    applySourceAction(rule, sourceEvaluator) { fact ->
                        createNDEdge(
                            nonZeroPreconditions,
                            fact.replaceExclusions(ExclusionSet.Universe)
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
        createFinalFact: (FinalFactAp) -> Unit,
    ) {
        val actions = rule.actionsAfter
        check(actions.size == rule.actionsAfter.size) { "Unexpected source action: ${rule.actionsAfter}" }

        for (action in actions) {
            sourceEvaluator.evaluate(rule, action).onSome { facts ->
                facts.forEach { it.mapExitToReturnFact()?.let(createFinalFact) }
            }
        }
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
            callExpr.callee,
            callExpr,
            factAp,
            analysisContext.factTypeChecker
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

    private inline fun FinalFactAp.forEachFactWithAliases(crossinline body: (FinalFactAp) -> Unit) {
        body(this)

        analysisContext.aliasAnalysis?.forEachAliasAtStatement(statement, this) { aliased ->
            body(aliased)
        }
    }
}

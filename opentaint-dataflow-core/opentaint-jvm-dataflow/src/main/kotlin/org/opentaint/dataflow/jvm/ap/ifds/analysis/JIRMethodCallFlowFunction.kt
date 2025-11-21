package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnFFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnZFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToReturnZeroFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToStartFFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToStartZFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.CallToStartZeroFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.SideEffectRequirement
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.Unchanged
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.CalleePositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallPositionToAccessPathResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodPositionBaseTypeResolver
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyCleaner
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyEntryPointConfig
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyPassThrough
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyRuleWithAssumptions
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.sinkRules
import org.opentaint.dataflow.jvm.ap.ifds.taint.CalleePositionToAccessPath
import org.opentaint.dataflow.jvm.ap.ifds.taint.FinalFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRBasicConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintCleanActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintPassActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintSourceActionEvaluator
import org.opentaint.dataflow.jvm.util.callee
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
        applySinkRules(factReader = null)

        applySourceRules(factReader = null, exclusion = ExclusionSet.Universe).forEach {
            this += CallToReturnZFact(factAp = it)
        }

        this += CallToReturnZeroFact
        this += CallToStartZeroFact
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp) = buildSet {
        propagateFact(
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
            }
        )
    }

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp
    ) = buildSet {
        propagateFact(
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
            }
        )
    }

    private fun propagateFact(
        exclusion: ExclusionSet,
        factAp: FinalFactAp,
        skipCall: () -> Unit,
        addSideEffectRequirement: (FinalFactReader) -> Unit,
        addCallToReturn: (FinalFactReader, FinalFactAp) -> Unit,
        addCallToStart: (factReader: FinalFactReader, callerFact: FinalFactAp, startFactBase: AccessPathBase) -> Unit,
    ) {
        val factReaderBeforeCleaner = FinalFactReader(factAp, apManager)

        if (!JIRMethodCallFactMapper.factIsRelevantToMethodCall(returnValue, callExpr, factAp)) {
            skipCall()
            return
        }

        applySinkRules(factReaderBeforeCleaner)

        applySourceRules(factReaderBeforeCleaner, exclusion).forEach {
            addCallToReturn(factReaderBeforeCleaner, it)
        }

        if (factReaderBeforeCleaner.hasRefinement) {
            addSideEffectRequirement(factReaderBeforeCleaner)
        }

        val apResolver = JIRCallPositionToAccessPathResolver(callExpr, returnValue)

        val conditionEvaluator = JIRFactAwareConditionEvaluator(
            listOf(factReaderBeforeCleaner),
            apResolver,
            CallPositionToJIRValueResolver(callExpr, returnValue)
        )

        val cleaner = TaintCleanActionEvaluator(apResolver)

        val factReaderAfterCleaner = applyCleaner(
            config,
            callExpr.callee,
            statement,
            factReaderBeforeCleaner,
            conditionEvaluator,
            cleaner
        ) ?: return

        val typeResolver = JIRMethodPositionBaseTypeResolver(callExpr.callee)

        val passEvaluator = TaintPassActionEvaluator(
            apManager, apResolver, analysisContext.factTypeChecker, factReaderAfterCleaner, typeResolver
        )

        val passThroughFacts = applyPassThrough(
            config,
            callExpr.callee,
            statement,
            conditionEvaluator,
            passEvaluator
        )

        if (factReaderAfterCleaner.hasRefinement) {
            addSideEffectRequirement(factReaderAfterCleaner)
        }

        passThroughFacts.onSome { facts ->
            facts.forEach {
                addCallToReturn(factReaderAfterCleaner, it)
            }

            // Skip method invocation
            return
        }

        propagateFact(factReaderAfterCleaner, factAp, addCallToReturn, addCallToStart)
    }

    private fun propagateFact(
        factReader: FinalFactReader,
        factAp: FinalFactAp,
        addCallToReturn: (FinalFactReader, FinalFactAp) -> Unit,
        addCallToStart: (factReader: FinalFactReader, callerFactAp: FinalFactAp, startFactBase: AccessPathBase) -> Unit,
    ) {
        val method = callExpr.callee

        // FIXME: adhoc for constructors:
        if (method.isConstructor) {
            addCallToReturn(factReader, factAp)
        }

        JIRMethodCallFactMapper.mapMethodCallToStartFlowFact(
            method,
            callExpr,
            factAp,
            analysisContext.factTypeChecker
        ) { callerFact, startFactBase ->
            addCallToStart(factReader, callerFact, startFactBase)
        }
    }

    private fun applySinkRules(factReader: FinalFactReader?) {
        val sinkRules = sinkRules(config, callExpr.callee, statement).toList()
        if (sinkRules.isEmpty()) return

        val apResolver = JIRCallPositionToAccessPathResolver(
            callExpr,
            returnValue = null,
            readArgElementsIfArray = true
        )
        val valueResolver = CallPositionToJIRValueResolver(callExpr, returnValue = null)

        sinkRules.applyRuleWithAssumptions(
            apManager, apResolver,
            valueResolver, factReader, condition = { condition },
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

            val fact = evaluatedFacts.first() // todo: better fact selection?
            sinkTracker.addVulnerability(
                analysisContext.methodEntryPoint, fact, statement, rule
            )
        }
    }

    private fun applySourceRules(factReader: FinalFactReader?, exclusion: ExclusionSet): List<FinalFactAp> {
        val method = callExpr.method.method
        val sourceRules = config.sourceRulesForMethod(method, statement).toList()

        if (sourceRules.isEmpty()) return emptyList()

        val apResolver = JIRCallPositionToAccessPathResolver(callExpr, returnValue)
        val valueResolver = CallPositionToJIRValueResolver(callExpr, returnValue)

        val result = mutableListOf<FinalFactAp>()
        val sourceEvaluator = TaintSourceActionEvaluator(apManager, exclusion, apResolver)

        sourceRules.applyRuleWithAssumptions(
            apManager,
            apResolver, valueResolver, factReader,
            condition = { condition },
            storeAssumptions = { rule, facts -> sinkTracker.addSourceRuleAssumptions(rule, statement, facts) },
            currentAssumptions = { rule -> sinkTracker.currentSourceRuleAssumptions(rule, statement) }
        ) { rule, evaluatedFacts ->
            // unconditional sources handled with zero fact
            if (evaluatedFacts.isEmpty() && factReader != null) return@applyRuleWithAssumptions

            val actions = rule.actionsAfter.filterIsInstance<AssignMark>()
            check(actions.size == rule.actionsAfter.size) { "Unexpected source action: ${rule.actionsAfter}" }

            for (action in actions) {
                sourceEvaluator.evaluate(rule, action).onSome { facts ->
                    result += facts
                }
            }
        }

        return result
    }

    companion object {
        fun applyEntryPointConfigDefault(
            apManager: ApManager,
            config: TaintRulesProvider,
            method: JIRMethod
        ) = applyEntryPointConfig(
            config,
            method = method,
            conditionEvaluator = JIRBasicConditionEvaluator(
                CalleePositionToJIRValueResolver(method)
            ),
            taintActionEvaluator = TaintSourceActionEvaluator(
                apManager,
                exclusion = ExclusionSet.Universe,
                CalleePositionToAccessPath(resultAp = null)
            )
        )
    }
}

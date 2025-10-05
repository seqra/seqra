package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.ConditionVisitor
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.CalleePositionToAccessPath
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FinalFactReader
import org.opentaint.dataflow.ap.ifds.MethodCallFlowFunction
import org.opentaint.dataflow.ap.ifds.MethodCallFlowFunction.CallToStartZeroFact
import org.opentaint.dataflow.ap.ifds.MethodCallFlowFunction.CallToReturnZeroFact
import org.opentaint.dataflow.ap.ifds.MethodCallFlowFunction.CallToStartFFact
import org.opentaint.dataflow.ap.ifds.MethodCallFlowFunction.CallToReturnFFact
import org.opentaint.dataflow.ap.ifds.MethodCallFlowFunction.CallToReturnZFact
import org.opentaint.dataflow.ap.ifds.MethodCallFlowFunction.CallToStartZFact
import org.opentaint.dataflow.ap.ifds.MethodCallFlowFunction.SideEffectRequirement
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.TaintPassActionEvaluator
import org.opentaint.dataflow.ap.ifds.TaintRulesProvider
import org.opentaint.dataflow.ap.ifds.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.TaintSourceActionEvaluator
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.config.JIRBasicConditionEvaluator
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.dataflow.jvm.util.callee
import org.opentaint.util.Maybe
import org.opentaint.util.merge
import org.opentaint.util.onSome

class JIRMethodCallFlowFunction(
    private val apManager: ApManager,
    private val config: TaintRulesProvider,
    private val returnValue: JIRImmediate?,
    private val callExpr: JIRCallExpr,
    private val factTypeChecker: FactTypeChecker,
    private val statement: JIRInst,
    private val sinkTracker: TaintSinkTracker,
    private val methodEntryPoint: MethodEntryPoint,
    private val traits: JIRTraits
): MethodCallFlowFunction {

    override fun propagateZeroToZero() = buildSet {
        applySinkRules(factReader = null)

        this += CallToReturnZeroFact

        val callPositionToJIRValueResolver = CallPositionToJIRValueResolver(callExpr, returnValue)
        val callPositionToAccessPathResolver = JIRCallPositionToAccessPathResolver(callExpr, returnValue)

        applySourceConfig(
            config,
            method = callExpr.callee,
            statement = statement,
            conditionEvaluator = JIRBasicConditionEvaluator(traits, callPositionToJIRValueResolver),
            taintActionEvaluator = TaintSourceActionEvaluator(apManager, callPositionToAccessPathResolver)
        ).onSome { facts ->
            facts.mapTo(this) { CallToReturnZFact(factAp = it) }

            // Skip method invocation
            return@buildSet
        }

        this += CallToStartZeroFact
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp) = buildSet {
        propagateFact(
            factAp = currentFactAp,
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
            factAp = currentFactAp,
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
        factAp: FinalFactAp,
        addSideEffectRequirement: (FinalFactReader) -> Unit,
        addCallToReturn: (FinalFactReader, FinalFactAp) -> Unit,
        addCallToStart: (factReader: FinalFactReader, callerFact: FinalFactAp, startFactBase: AccessPathBase) -> Unit,
    ) {
        val factReader = FinalFactReader(factAp, apManager)
        applySinkRules(factReader)

        val apResolver = JIRCallPositionToAccessPathResolver(callExpr, returnValue)

        val conditionEvaluator = JIRFactAwareConditionEvaluator(
            traits,
            listOf(factReader),
            apResolver,
            CallPositionToJIRValueResolver(callExpr, returnValue)
        )

        val taintActionEvaluator = TaintPassActionEvaluator(
            apManager,
            apResolver,
            factTypeChecker,
            factReader,
            JIRMethodPositionBaseTypeResolver(callExpr.callee)
        )

        val passThroughFacts = applyPassThrough(
            config,
            callExpr.callee,
            statement,
            conditionEvaluator,
            taintActionEvaluator
        )

        val cleanerFacts = applyCleaner(
            config,
            callExpr.callee,
            statement,
            conditionEvaluator,
            taintActionEvaluator
        )

        if (factReader.hasRefinement) {
            addSideEffectRequirement(factReader)
        }

        passThroughFacts.merge(cleanerFacts).onSome { facts ->
            facts.forEach {
                addCallToReturn(factReader, it)
            }

            // Skip method invocation
            return
        }

        propagateFact(factReader, factAp, addCallToReturn, addCallToStart)
    }

    private fun propagateFact(
        factReader: FinalFactReader,
        factAp: FinalFactAp,
        addCallToReturn: (FinalFactReader, FinalFactAp) -> Unit,
        addCallToStart: (factReader: FinalFactReader, callerFactAp: FinalFactAp, startFactBase: AccessPathBase) -> Unit,
    ) {
        if (!JIRMethodCallFactMapper.factCanBeModifiedByMethodCall(returnValue, callExpr, factAp)) {
            addCallToReturn(factReader, factAp)
            return
        }

        val method = callExpr.callee

        // FIXME: adhoc for constructors:
        if (method.isConstructor) {
            addCallToReturn(factReader, factAp)
        }

        JIRMethodCallFactMapper.mapMethodCallToStartFlowFact(method, callExpr, factAp, factTypeChecker) { callerFact, startFactBase ->
            addCallToStart(factReader, callerFact, startFactBase)
        }
    }

    private fun applySinkRules(factReader: FinalFactReader?) {
        val apResolver = JIRCallPositionToAccessPathResolver(
            callExpr,
            returnValue = null,
            readArgElementsIfArray = true
        )
        val valueResolver = CallPositionToJIRValueResolver(callExpr, returnValue = null)

        val sinkRules = sinkRules(config, callExpr.callee, statement)
        val remainingSinkRules = mutableListOf<TaintMethodSink>()

        val noFactConditionEvaluator = JIRFactIgnoreConditionEvaluator(traits, valueResolver)
        for (rule in sinkRules) {
            if (rule.condition.accept(noFactConditionEvaluator)) {
                sinkTracker.addUnconditionalVulnerability(methodEntryPoint, statement, rule)
                continue
            }

            remainingSinkRules += rule
        }

        if (factReader == null) return

        val conditionEvaluator = JIRFactAwareConditionEvaluator(
            traits,
            listOf(factReader),
            apResolver,
            valueResolver
        )

        for (rule in remainingSinkRules) {
            if (conditionEvaluator.evalWithAssumptionsCheck(rule.condition)) {
                for (fact in conditionEvaluator.facts()) {
                    sinkTracker.addVulnerability(methodEntryPoint, fact, statement, rule)
                }
                continue
            }

            if (!conditionEvaluator.assumptionsPossible()) {
                continue
            }

            val conditionEvaluatorWithAssumptions = JIRFactAwareConditionEvaluatorWithAssumptions(
                traits,
                factReader,
                apResolver,
                valueResolver
            )

            for (resultWithAssumption in conditionEvaluatorWithAssumptions.evalWithAssumptions(rule.condition)) {
                if (!resultWithAssumption.result) continue

                for (fact in conditionEvaluator.facts()) {
                    sinkTracker.addVulnerabilityWithAssumption(
                        methodEntryPoint, fact, statement, rule, resultWithAssumption.assumptions
                    )
                }
            }
        }
    }

    companion object {
        fun applyEntryPointConfigDefault(
            apManager: ApManager,
            config: TaintRulesProvider,
            method: JIRMethod,
            traits: JIRTraits
        ) = applyEntryPointConfig(
            config,
            method = method,
            conditionEvaluator = JIRBasicConditionEvaluator(
                traits,
                CalleePositionToJIRValueResolver(method)
            ),
            taintActionEvaluator = TaintSourceActionEvaluator(apManager, CalleePositionToAccessPath())
        )

        private fun sinkRules(config: TaintRulesProvider, method: JIRMethod, statement: JIRInst) =
            TaintConfigUtils.sinkRules(config, method, statement)

        private fun applySourceConfig(
            config: TaintRulesProvider,
            method: JIRMethod,
            statement: JIRInst,
            conditionEvaluator: ConditionVisitor<Boolean>,
            taintActionEvaluator: TaintSourceActionEvaluator
        ) = TaintConfigUtils.applySourceConfig(config, method, statement, conditionEvaluator, taintActionEvaluator)

        private fun applyEntryPointConfig(
            config: TaintRulesProvider,
            method: JIRMethod,
            conditionEvaluator: ConditionVisitor<Boolean>,
            taintActionEvaluator: TaintSourceActionEvaluator
        ) = TaintConfigUtils.applyEntryPointConfig(config, method, conditionEvaluator, taintActionEvaluator)

        private fun applyPassThrough(
            config: TaintRulesProvider,
            method: JIRMethod,
            inst: CommonInst,
            conditionEvaluator: ConditionVisitor<Boolean>,
            taintActionEvaluator: TaintPassActionEvaluator
        ): Maybe<List<FinalFactAp>> =
            TaintConfigUtils.applyPassThrough(config, method, inst, conditionEvaluator, taintActionEvaluator)

        private fun applyCleaner(
            config: TaintRulesProvider,
            method: JIRMethod,
            statement: JIRInst,
            conditionEvaluator: ConditionVisitor<Boolean>,
            taintActionEvaluator: TaintPassActionEvaluator
        ): Maybe<List<FinalFactAp>> =
            TaintConfigUtils.applyCleaner(config, method, statement, conditionEvaluator, taintActionEvaluator)
    }
}

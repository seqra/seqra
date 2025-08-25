package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.ConditionVisitor
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.dataflow.config.BasicConditionEvaluator
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.ifds.merge
import org.opentaint.dataflow.ifds.onSome
import org.opentaint.dataflow.jvm.ap.ifds.access.ApManager
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.util.JIRTraits

class MethodCallFlowFunction(
    private val apManager: ApManager,
    private val config: TaintRulesProvider,
    private val returnValue: JIRImmediate?,
    private val callExpr: JIRCallExpr,
    private val factTypeChecker: FactTypeChecker,
    private val statement: JIRInst,
    private val sinkTracker: TaintSinkTracker,
    private val methodEntryPoint: MethodEntryPoint,
) {
    sealed interface ZeroCallFact

    sealed interface FactCallFact

    object CallToReturnZeroFact: ZeroCallFact

    object CallToStartZeroFact : ZeroCallFact

    data class CallToReturnFFact(val initialFactAp: InitialFactAp, val factAp: FinalFactAp) : FactCallFact

    data class CallToStartFFact(
        val initialFactAp: InitialFactAp,
        val callerFactAp: FinalFactAp,
        val startFactBase: AccessPathBase
    ) : FactCallFact

    data class CallToReturnZFact(val factAp: FinalFactAp) : ZeroCallFact

    data class CallToStartZFact(val callerFactAp: FinalFactAp, val startFactBase: AccessPathBase) : ZeroCallFact

    data class SinkRequirement(val initialFactAp: InitialFactAp) : FactCallFact

    private val traits by lazy {
        JIRTraits(statement.method.enclosingClass.classpath)
    }

    fun propagateZeroToZero(): Set<ZeroCallFact> = buildSet {
        applySinkRules(factReader = null)

        this += CallToReturnZeroFact

        applySourceConfig(
            config,
            method = callExpr.method.method,
            conditionEvaluator = BasicConditionEvaluator(CallPositionToJIRValueResolver(callExpr, returnValue), traits),
            taintActionEvaluator = TaintSourceActionEvaluator(
                apManager,
                CallPositionToAccessPathResolver(callExpr, returnValue)
            )
        ).onSome { facts ->
            facts.mapTo(this) { CallToReturnZFact(factAp = it) }

            // Skip method invocation
            return@buildSet
        }

        this += CallToStartZeroFact
    }

    fun propagateZeroToFact(currentFactAp: FinalFactAp) = buildSet<ZeroCallFact> {
        propagateFact(
            factAp = currentFactAp,
            addSinkRequirement = { factReader ->
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

    fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp
    ) = buildSet<FactCallFact> {
        propagateFact(
            factAp = currentFactAp,
            addSinkRequirement = { factReader ->
                this += SinkRequirement(factReader.refineFact(initialFactAp))
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
        addSinkRequirement: (FinalFactReader) -> Unit,
        addCallToReturn: (FinalFactReader, FinalFactAp) -> Unit,
        addCallToStart: (factReader: FinalFactReader, callerFact: FinalFactAp, startFactBase: AccessPathBase) -> Unit,
    ) {
        val factReader = FinalFactReader(factAp, apManager)
        applySinkRules(factReader)

        val apResolver = CallPositionToAccessPathResolver(callExpr, returnValue)

        val conditionEvaluator = FactAwareConditionEvaluator(
            traits,
            factReader,
            apResolver,
            CallPositionToJIRValueResolver(callExpr, returnValue)
        )

        val taintActionEvaluator = TaintPassActionEvaluator(
            apManager,
            callExpr.method.method, apResolver, factTypeChecker, factReader
        )

        val passThroughFacts = applyPassThrough(config, callExpr.method.method, conditionEvaluator, taintActionEvaluator)
        val cleanerFacts = applyCleaner(config, callExpr.method.method, conditionEvaluator, taintActionEvaluator)

        if (factReader.hasRefinement) {
            addSinkRequirement(factReader)
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
        if (!factCanBeModifiedByMethodCall(returnValue, callExpr, factAp)) {
            addCallToReturn(factReader, factAp)
            return
        }

        val method = callExpr.method.method

        // FIXME: adhoc for constructors:
        if (method.isConstructor) {
            addCallToReturn(factReader, factAp)
        }

        mapMethodCallToStartFlowFact(method, callExpr, factAp, factTypeChecker) { callerFact, startFactBase ->
            addCallToStart(factReader, callerFact, startFactBase)
        }
    }

    private fun applySinkRules(factReader: FinalFactReader?) {
        val apResolver = CallPositionToAccessPathResolver(callExpr, returnValue = null, readArgElementsIfArray = true)
        val valueResolver = CallPositionToJIRValueResolver(callExpr, returnValue = null)

        val sinkRules = sinkRules(config, callExpr.method.method)
        val remainingSinkRules = mutableListOf<TaintMethodSink>()

        val noFactConditionEvaluator = FactIgnoreConditionEvaluator(traits, valueResolver)
        for (rule in sinkRules) {
            if (rule.condition.accept(noFactConditionEvaluator)) {
                sinkTracker.addUnconditionalVulnerability(methodEntryPoint, statement, rule)
                continue
            }

            remainingSinkRules += rule
        }

        if (factReader == null) return

        val conditionEvaluator = FactAwareConditionEvaluator(
            traits, factReader, apResolver, valueResolver
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

            val conditionEvaluatorWithAssumptions = FactAwareConditionEvaluatorWithAssumptions(
                traits, factReader, apResolver, valueResolver
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
            method: JIRMethod
        ) = applyEntryPointConfig(
            config,
            method = method,
            conditionEvaluator = BasicConditionEvaluator(
                CalleePositionToJIRValueResolver(method),
                JIRTraits(method.enclosingClass.classpath)
            ),
            taintActionEvaluator = TaintSourceActionEvaluator(apManager, CalleePositionToAccessPath())
        )

        private fun sinkRules(config: TaintRulesProvider, method: JIRMethod) =
            TaintConfigUtils.sinkRules(config, method)

        private fun applySourceConfig(
            config: TaintRulesProvider,
            method: JIRMethod,
            conditionEvaluator: ConditionVisitor<Boolean>,
            taintActionEvaluator: TaintSourceActionEvaluator
        ) = TaintConfigUtils.applySourceConfig(config, method, conditionEvaluator, taintActionEvaluator)

        private fun applyEntryPointConfig(
            config: TaintRulesProvider,
            method: JIRMethod,
            conditionEvaluator: ConditionVisitor<Boolean>,
            taintActionEvaluator: TaintSourceActionEvaluator
        ) = TaintConfigUtils.applyEntryPointConfig(config, method, conditionEvaluator, taintActionEvaluator)

        private fun applyPassThrough(
            config: TaintRulesProvider,
            method: JIRMethod,
            conditionEvaluator: ConditionVisitor<Boolean>,
            taintActionEvaluator: TaintPassActionEvaluator
        ): Maybe<List<FinalFactAp>> =
            TaintConfigUtils.applyPassThrough(config, method, conditionEvaluator, taintActionEvaluator)

        private fun applyCleaner(
            config: TaintRulesProvider,
            method: JIRMethod,
            conditionEvaluator: ConditionVisitor<Boolean>,
            taintActionEvaluator: TaintPassActionEvaluator
        ): Maybe<List<FinalFactAp>> =
            TaintConfigUtils.applyCleaner(config, method, conditionEvaluator, taintActionEvaluator)
    }
}

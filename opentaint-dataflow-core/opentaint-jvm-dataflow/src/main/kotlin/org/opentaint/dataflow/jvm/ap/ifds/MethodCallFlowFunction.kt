package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.Action
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.Condition
import org.opentaint.ir.taint.configuration.ConditionVisitor
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.CopyMark
import org.opentaint.ir.taint.configuration.RemoveAllMarks
import org.opentaint.ir.taint.configuration.RemoveMark
import org.opentaint.ir.taint.configuration.TaintCleaner
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintEntryPointSource
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.ir.taint.configuration.TaintMethodSource
import org.opentaint.ir.taint.configuration.TaintPassThrough
import org.opentaint.dataflow.config.BasicConditionEvaluator
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.ifds.maybeFlatMap
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
    private val sinkTracker: TaintSinkTracker
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
        applyZeroFactSinkRules()

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

    private fun applyZeroFactSinkRules() {
        val conditionEvaluator = FactIgnoreConditionEvaluator(
            traits,
            CallPositionToJIRValueResolver(callExpr, returnValue = null)
        )

        sinkRules(config, callExpr.method.method)
            .filter { it.condition.accept(conditionEvaluator) }
            .forEach { sinkTracker.addUnconditionalVulnerability(statement, it) }
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
        addSinkRequirement: (FactReader) -> Unit,
        addCallToReturn: (FactReader, FinalFactAp) -> Unit,
        addCallToStart: (factReader: FactReader, callerFact: FinalFactAp, startFactBase: AccessPathBase) -> Unit,
    ) {
        val factReader = FactReader(factAp)
        applyTaintedFactSinkRules(factReader)

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
        factReader: FactReader,
        factAp: FinalFactAp,
        addCallToReturn: (FactReader, FinalFactAp) -> Unit,
        addCallToStart: (factReader: FactReader, callerFactAp: FinalFactAp, startFactBase: AccessPathBase) -> Unit,
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

    private fun applyTaintedFactSinkRules(factReader: FactReader) {
        val apResolver = CallPositionToAccessPathResolver(callExpr, returnValue = null, readArgElementsIfArray = true)
        val valueResolver = CallPositionToJIRValueResolver(callExpr, returnValue = null)

        val conditionEvaluator = FactAwareConditionEvaluator(
            traits, factReader, apResolver, valueResolver
        )

        for (rule in sinkRules(config, callExpr.method.method)) {
            if (conditionEvaluator.evalWithAssumptionsCheck(rule.condition)) {
                sinkTracker.addNormalVulnerability(factReader.factAp, statement, rule)
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

                sinkTracker.addVulnerabilityWithAssumption(
                    factReader.factAp, statement, rule, resultWithAssumption.assumptions
                )
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
            config.rulesForMethod(method).filterIsInstance<TaintMethodSink>()

        private fun applySourceConfig(
            config: TaintRulesProvider,
            method: JIRMethod,
            conditionEvaluator: ConditionVisitor<Boolean>,
            taintActionEvaluator: TaintSourceActionEvaluator
        ) = applyAssignMark<TaintMethodSource>(
            config, method, conditionEvaluator, taintActionEvaluator,
            TaintMethodSource::condition, TaintMethodSource::actionsAfter
        )

        private fun applyEntryPointConfig(
            config: TaintRulesProvider,
            method: JIRMethod,
            conditionEvaluator: ConditionVisitor<Boolean>,
            taintActionEvaluator: TaintSourceActionEvaluator
        ) = applyAssignMark<TaintEntryPointSource>(
            config, method, conditionEvaluator, taintActionEvaluator,
            TaintEntryPointSource::condition, TaintEntryPointSource::actionsAfter
        )

        private inline fun <reified T : TaintConfigurationItem> applyAssignMark(
            config: TaintRulesProvider,
            method: JIRMethod,
            conditionEvaluator: ConditionVisitor<Boolean>,
            taintActionEvaluator: TaintSourceActionEvaluator,
            condition: (T) -> Condition,
            actionsAfter: (T) -> List<Action>
        ): Maybe<List<FinalFactAp>> =
            config.rulesForMethod(method)
                .filterIsInstance<T>()
                .filter { condition(it).accept(conditionEvaluator) }
                .flatMap { actionsAfter(it) }
                .filterIsInstance<AssignMark>()
                .maybeFlatMap { taintActionEvaluator.evaluate(it) }

        private fun applyPassThrough(
            config: TaintRulesProvider,
            method: JIRMethod,
            conditionEvaluator: ConditionVisitor<Boolean>,
            taintActionEvaluator: TaintPassActionEvaluator
        ): Maybe<List<FinalFactAp>> =
            config.rulesForMethod(method)
                .filterIsInstance<TaintPassThrough>()
                .filter { it.condition.accept(conditionEvaluator) }
                .flatMap { it.actionsAfter }
                .maybeFlatMap {
                    when (it) {
                        is CopyMark -> taintActionEvaluator.evaluate(it)
                        is CopyAllMarks -> taintActionEvaluator.evaluate(it)
                        is RemoveMark -> taintActionEvaluator.evaluate(it)
                        is RemoveAllMarks -> taintActionEvaluator.evaluate(it)
                        else -> Maybe.none()
                    }
                }

        private fun applyCleaner(
            config: TaintRulesProvider,
            method: JIRMethod,
            conditionEvaluator: ConditionVisitor<Boolean>,
            taintActionEvaluator: TaintPassActionEvaluator
        ): Maybe<List<FinalFactAp>> =
            config.rulesForMethod(method)
                .filterIsInstance<TaintCleaner>()
                .filter { it.condition.accept(conditionEvaluator) }
                .flatMap { it.actionsAfter }
                .maybeFlatMap {
                    when (it) {
                        is RemoveMark -> taintActionEvaluator.evaluate(it)
                        is RemoveAllMarks -> taintActionEvaluator.evaluate(it)
                        else -> Maybe.none()
                    }
                }
    }
}

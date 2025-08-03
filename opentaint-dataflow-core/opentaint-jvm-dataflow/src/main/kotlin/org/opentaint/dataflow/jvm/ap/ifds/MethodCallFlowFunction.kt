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

    data class CallToReturnFFact(val initialFact: Fact.InitialFact, val fact: Fact.FinalFact) : FactCallFact

    data class CallToStartFFact(
        val initialFact: Fact.InitialFact,
        val callerFact: Fact.FinalFact,
        val startFactBase: AccessPathBase
    ) : FactCallFact

    data class CallToReturnZFact(val fact: Fact.FinalFact) : ZeroCallFact

    data class CallToStartZFact(val callerFact: Fact.FinalFact, val startFactBase: AccessPathBase) : ZeroCallFact

    data class SinkRequirement(val initialFact: Fact.InitialFact) : FactCallFact

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
            facts.mapTo(this) { CallToReturnZFact(fact = it) }

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
            .forEach { sinkTracker.addVulnerability(Fact.Zero, statement, it) }
    }

    fun propagateZeroToFact(currentFact: Fact.FinalFact) = buildSet<ZeroCallFact> {
        propagateFact(
            fact = currentFact,
            addSinkRequirement = { factReader ->
                check(!factReader.hasRefinement) { "Can't refine Zero fact" }
            },
            addCallToReturn = { factReader, fact ->
                check(!factReader.hasRefinement) { "Can't refine Zero fact" }
                this += CallToReturnZFact(fact)
            },
            addCallToStart = { factReader, callerFact, startFactBase ->
                check(!factReader.hasRefinement) { "Can't refine Zero fact" }
                this += CallToStartZFact(callerFact, startFactBase)
            }
        )
    }

    fun propagateFactToFact(
        initialFact: Fact.InitialFact,
        currentFact: Fact.FinalFact
    ) = buildSet<FactCallFact> {
        propagateFact(
            fact = currentFact,
            addSinkRequirement = { factReader ->
                this += SinkRequirement(factReader.refineFact(initialFact))
            },
            addCallToReturn = { factReader, fact ->
                this += CallToReturnFFact(factReader.refineFact(initialFact), factReader.refineFact(fact))
            },
            addCallToStart = { factReader, callerFact, startFactBase ->
                this += CallToStartFFact(
                    factReader.refineFact(initialFact),
                    factReader.refineFact(callerFact),
                    startFactBase
                )
            }
        )
    }

    private fun propagateFact(
        fact: Fact.FinalFact,
        addSinkRequirement: (FactReader) -> Unit,
        addCallToReturn: (FactReader, Fact.FinalFact) -> Unit,
        addCallToStart: (factReader: FactReader, callerFact: Fact.FinalFact, startFactBase: AccessPathBase) -> Unit,
    ) {
        run {
            val factReader = FactReader(fact)
            applyTaintedFactSinkRules(factReader)
            if (factReader.hasRefinement) {
                addSinkRequirement(factReader)
            }
        }

        val factReader = FactReader(fact)
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

        passThroughFacts.merge(cleanerFacts).onSome { facts ->
            facts.forEach {
                addCallToReturn(factReader, it)
            }

            // Skip method invocation
            return
        }

        propagateFact(factReader, fact, addCallToReturn, addCallToStart)
    }

    private fun propagateFact(
        factReader: FactReader,
        fact: Fact.FinalFact,
        addCallToReturn: (FactReader, Fact.FinalFact) -> Unit,
        addCallToStart: (factReader: FactReader, callerFact: Fact.FinalFact, startFactBase: AccessPathBase) -> Unit,
    ) {
        if (!factCanBeModifiedByMethodCall(returnValue, callExpr, fact)) {
            addCallToReturn(factReader, fact)
            return
        }

        val method = callExpr.method.method

        // FIXME: adhoc for constructors:
        if (method.isConstructor) {
            addCallToReturn(factReader, fact)
        }

        mapMethodCallToStartFlowFact(method, callExpr, fact, factTypeChecker) { callerFact, startFactBase ->
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
                sinkTracker.addVulnerability(factReader.fact, statement, rule)
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
                    factReader.fact, statement, rule, resultWithAssumption.assumptions
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
        ): Maybe<List<Fact.FinalFact>> =
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
        ): Maybe<List<Fact.FinalFact>> =
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
        ): Maybe<List<Fact.FinalFact>> =
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

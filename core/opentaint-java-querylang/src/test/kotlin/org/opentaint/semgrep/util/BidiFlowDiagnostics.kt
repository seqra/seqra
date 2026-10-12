package org.opentaint.semgrep.util

import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.Cleaner
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.ap.PathEdge
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.bidi.ap.members
import org.opentaint.dataflow.bidi.driver.IterationDriver
import org.opentaint.dataflow.bidi.driver.IterationPolicy
import org.opentaint.dataflow.bidi.driver.Next
import org.opentaint.dataflow.bidi.engine.EdgeDelta
import org.opentaint.dataflow.bidi.engine.MethodCallServicesFactory
import org.opentaint.dataflow.bidi.engine.MethodLocalServicesFactory
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.engine.RunManager
import org.opentaint.dataflow.bidi.engine.RunMethodAnalyzer
import org.opentaint.dataflow.bidi.engine.RunMethodCallServices
import org.opentaint.dataflow.bidi.engine.RunMethodHandlers
import org.opentaint.dataflow.bidi.engine.RunMethodLocalServices
import org.opentaint.dataflow.bidi.engine.RunResult
import org.opentaint.dataflow.bidi.engine.RunnerHandlerFactory
import org.opentaint.dataflow.bidi.engine.RunnerPort
import org.opentaint.dataflow.bidi.engine.SharedObjects
import org.opentaint.dataflow.bidi.interp.CallStage
import org.opentaint.dataflow.bidi.interp.CleanStep
import org.opentaint.dataflow.bidi.interp.StatementSummary
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.JIRBidiAnalysis
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.jvm.graph.JApplicationGraph
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/** Test-only observation of real facts and cached forms. It retains no run actor or result. */
class BidiFlowDiagnostics {
    private val printedPlans = ConcurrentHashMap.newKeySet<Triple<Int, MethodKey, CommonInst>>()

    fun createEntry(
        cp: JIRClasspath,
        graph: JApplicationGraph,
        unitResolver: JIRUnitResolver,
        rules: TaintRulesProvider,
        params: JIRAnalysisManager.Params,
        policy: IterationPolicy,
        refManager: RefManager,
    ): JIRBidiAnalysis {
        val ctor = JIRBidiAnalysis::class.java.declaredConstructors.single { it.parameterCount == 8 }
        ctor.isAccessible = true
        val factory: (IterationPolicy, SharedObjects, Duration) -> IterationDriver = ::driver
        return unwrap { ctor.newInstance(cp, graph, unitResolver, rules, params, policy, refManager, factory) }
            as JIRBidiAnalysis
    }

    private fun driver(policy: IterationPolicy, shared: SharedObjects, budget: Duration): IterationDriver {
        val invoke: (RunConfig, SharedObjects, Duration) -> RunResult<RunMethodAnalyzer> = ::runEngine
        val guard: (SharedObjects, () -> Next) -> Next = ::actualBarrier
        val rawBudget = Duration::class.java.getDeclaredMethod("unbox-impl").invoke(budget)
        val ctor = IterationDriver::class.java.declaredConstructors.single { it.parameterCount == 5 }
        ctor.isAccessible = true
        return unwrap { ctor.newInstance(policy, shared, rawBudget, invoke, guard) } as IterationDriver
    }

    private fun runEngine(config: RunConfig, shared: SharedObjects, timeout: Duration): RunResult<RunMethodAnalyzer> {
        val actual = RunMethodHandlers(MethodLocalServicesFactory(::RunMethodLocalServices),
            MethodCallServicesFactory(::RunMethodCallServices))
        val factory = RunnerHandlerFactory<RunMethodAnalyzer> { port ->
            val observed = object : RunnerPort<RunMethodAnalyzer> by port {
                override val onProcess: ((MethodKey, EdgeDelta) -> Unit) = { key, delta ->
                    if (key.method.toString().contains("RuleReturnWithNotInsideSignature")) {
                        val premise = delta.premise.members.joinToString { path(shared.ap, it.toPattern()) }
                        val leaves = shared.ops.leaves(delta.facts).joinToString { path(shared.ap, it) }
                        println("BIDI_FLOW run=${config.index} direction=${config.direction} method=$key " +
                            "statement=${delta.node} premise=[$premise] layer=${delta.facts.layer} facts=[$leaves]")
                        printPlan(config, shared, key, delta.node)
                    }
                    port.onProcess?.invoke(key, delta)
                }
            }
            // Preserve the actual SubscriptionHandler and all actual engine statuses.
            actual.create(observed)
        }
        return RunManager(config, shared, factory).run(timeout)
    }

    private fun printPlan(config: RunConfig, shared: SharedObjects, key: MethodKey, node: CommonInst) {
        val call = shared.language.getCallExpr(node) ?: return
        if (!printedPlans.add(Triple(config.index, key, node))) return
        val plan = shared.contexts.forms(key).call(config.direction, node, call)
        val stages = plan.stages.map { stage -> when (stage) {
            is CallStage.Edges -> "${stage.from}->${stage.to} ${stage.kind} ${summary(shared.ap, stage.summary)}"
            is CallStage.Callees -> "${stage.from}->${stage.to} CALLEES ${stage.callees}"
            is CallStage.Actions -> "${stage.from}->${stage.to} ACTIONS ${stage.steps.map { action(shared.ap, it) }}"
        } }
        println("BIDI_PLAN run=${config.index} direction=${config.direction} method=$key statement=$node " +
            "touched=${plan.touched} stages=$stages")
    }

    private fun summary(ap: ApManager, s: StatementSummary): String =
        "touched=${s.touched} edges=${s.edges.map { edge(ap, it.edge) }} " +
            "conjunctions=${s.conjunctions.map { c -> c.literals.map { path(ap, it) } to path(ap, c.target) }} " +
            "operandFilters=${s.typeFilters.keys} resultFilters=${s.resultFilters.keys}"

    private fun action(ap: ApManager, step: CleanStep): String = when (step) {
        is CleanStep.Clean -> "CLEAN ${cleaner(ap, step.cleaner)}"
        is CleanStep.Kill -> "KILL ${summary(ap, step.keepEdges)}"
        is CleanStep.Field -> "FIELD temporary=${step.temporary} read=[${summary(ap, step.read)}] " +
            "clean=[${cleaner(ap, step.cleaner)}] write=[${summary(ap, step.write)}]"
    }

    private fun cleaner(ap: ApManager, c: Cleaner): String =
        "base=${c.base} path=${c.path?.toList().orEmpty().map { ap.accessors.accessor(it) }} " +
            "reach=${c.reach} mark=${c.mark?.let(ap.marks::name) ?: "all"}"

    private fun edge(ap: ApManager, e: PathEdge): String =
        "${path(ap, Pattern(e.from, e.fromExclusion))}->${path(ap, Pattern(e.to, e.toExclusion))} " +
            "edgeE=${exclusion(ap, e.exclusion)}"

    private fun path(ap: ApManager, p: Pattern): String =
        "${path(ap, p.fact)} E=${exclusion(ap, p.exclusion)}"

    private fun path(ap: ApManager, p: PathFact): String {
        val mark = when (val m = p.mark) {
            is MarkSlot.Concrete -> ap.marks.name(m.mark)
            is MarkSlot.Star -> "* excluded=${m.excluded.ids.map { ap.marks.name(TaintMark(it)) }}"
        }
        return "base=${p.base} path=${p.path.map { ap.accessors.accessor(it) }} tail=${p.tail} mark=$mark"
    }

    private fun exclusion(ap: ApManager, e: ExclusionSet): String = when (e) {
        ExclusionSet.Empty -> "Empty"
        is ExclusionSet.Concrete -> "Concrete${e.ids.map { ap.accessors.accessor(it) }}"
    }

    private fun actualBarrier(shared: SharedObjects, body: () -> Next): Next {
        val owner = Class.forName("org.opentaint.dataflow.bidi.driver.IterationDriverKt")
        val guard = owner.declaredMethods.single { it.name == "withBarrierGuard" }
        guard.isAccessible = true
        return unwrap { guard.invoke(null, shared, body) } as Next
    }

    private inline fun <T> unwrap(body: () -> T): T = try { body() }
    catch (error: InvocationTargetException) { throw (error.cause ?: error) }
}

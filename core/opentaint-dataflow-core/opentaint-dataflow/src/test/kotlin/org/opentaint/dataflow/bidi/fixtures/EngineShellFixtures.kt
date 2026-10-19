package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.MethodContextCache
import org.opentaint.dataflow.bidi.engine.MethodContextSource
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.engine.SharedObjects
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.ConcurrentVulnerabilityStore
import org.opentaint.dataflow.bidi.store.DemandStore
import org.opentaint.dataflow.bidi.store.PersistentRecordStore
import org.opentaint.dataflow.graph.CompactGraph
import org.opentaint.dataflow.graph.MethodInstGraph
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.common.*
import org.opentaint.ir.api.common.cfg.*
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Real shell services over a small, fixed common IR graph. */
class EngineShellFixtures(val cancellation: Cancellation = Cancellation(),
                          markDeclarations: MarkDeclarations = COMMON_FIXTURE_DECLARATIONS) {
    data class Context(val name: String) : MethodContext
    data class Unit(val name: String) : UnitType

    private class Method(override val name: String) : CommonMethod {
        override val parameters: List<CommonMethodParameter> = emptyList()
        override val returnType = object : CommonTypeName { override val typeName = "java.lang.Object" }
        override fun flowGraph(): ControlFlowGraph<CommonInst> = error("The fixture supplies its compact graph")
        override fun toString(): String = name
    }

    val methodA: CommonMethod = Method("methodA")
    val methodB: CommonMethod = Method("methodB")
    val methodUnknown: CommonMethod = Method("methodUnknown")
    val unitA: UnitType = Unit("A")
    val unitB: UnitType = Unit("B")
    val unitResolver = UnitResolver<CommonMethod> { method -> when (method) {
        methodA -> unitA
        methodB -> unitB
        else -> UnknownUnit
    } }

    val refManager = RefManager()
    val manager = testApManager(cancellation, refManager, markDeclarations)
    val ops = ApOps(manager)
    val records = PersistentRecordStore(manager)
    val vulnerabilities = ConcurrentVulnerabilityStore(manager)
    private val instructions = ConcurrentHashMap<Pair<CommonMethod, Int>, CommonInst>()
    private val graphs = ConcurrentHashMap<Pair<CommonMethod, Direction>, MethodInstGraph>()
    private val forms = ConcurrentHashMap<MethodKey, MethodForms>()
    val callExpr = object : CommonCallExpr {
        override val typeName = "java.lang.Object"
        override val args: List<CommonValue> = emptyList()
    }

    fun inst(method: CommonMethod, index: Int): CommonInst {
        require(index in 0..4)
        return instructions.computeIfAbsent(method to index) { object : CommonInst {
            override val location = object : CommonInstLocation {
                override val method = method
                override val index = index
            }
            override fun toString(): String = "${method.name}:$index"
        } }
    }

    fun key(method: CommonMethod = methodA, context: String = "context", entry: Int = 0): MethodKey =
        MethodKey(Context(context), inst(method, entry))

    val a: MethodKey = key(methodA)
    val b: MethodKey = key(methodB)
    val unknown: MethodKey = key(methodUnknown)

    val language = object : LanguageManager {
        override fun getInstIndex(inst: CommonInst): Int = inst.location.index
        override fun getMaxInstIndex(method: CommonMethod): Int = 4
        override fun getInstByIndex(method: CommonMethod, index: Int): CommonInst = inst(method, index)
        override fun isEmpty(method: CommonMethod): Boolean = false
        override fun getCallExpr(inst: CommonInst): CommonCallExpr? = callExpr.takeIf { inst.location.index == 2 }
        override fun producesExceptionalControlFlow(inst: CommonInst): Boolean = inst.location.index == 2
        override fun getCalleeMethod(callExpr: CommonCallExpr): CommonMethod = methodB
        override val methodContextSerializer: MethodContextSerializer get() = error("Unused by shell tests")
    }

    fun graph(method: CommonMethod, direction: Direction): MethodInstGraph = graphs.computeIfAbsent(method to direction) {
        val successors = if (direction == Direction.FORWARD)
            listOf(listOf(1), listOf(2), listOf(3, 4), emptyList(), emptyList())
        else listOf(emptyList(), listOf(0), listOf(1), listOf(2), listOf(2))
        val compact = CompactGraph.build(5) { index -> BitSet().apply { successors[index].forEach(::set) } }
        val exits = BitSet().apply {
            if (direction == Direction.FORWARD) { set(3); set(4) } else set(0)
        }
        MethodInstGraph(Array(5) { inst(method, it) }, compact, exits)
    }

    fun methodForms(key: MethodKey): MethodForms = forms.computeIfAbsent(key) {
        val statement = StatementSummary(setOf(AccessPathBase.Argument(0)),
            listOf(MicroEdge.of(starEdge(AccessPathBase.Argument(0), emptyList(), AccessPathBase.Return, emptyList()))),
            emptyList(), emptyMap())
        val backwardStatement = statement.reversed()
        val entry = RuleStatement(StatementSummary.identities(listOf(AccessPathBase.Zero, AccessPathBase.Argument(0))),
            StatementSummary.EMPTY, emptyList())
        val backwardEntry = entry.reversed()
        val forwardExit = ExitRules(entry, globalStateDrop = true, entryMarks = setOf(manager.marks.mark("entry")))
        val backwardExit = ExitRules(forwardExit.reversed(), false, emptySet())
        val forwardCall = CallPlan(setOf(AccessPathBase.Argument(0), AccessPathBase.Return),
            listOf(CallStage.Edges(CallPoint.BEFORE, CallPoint.AFTER, StageKind.PASS_OVER, statement)),
            emptyList(), CallPoint.BEFORE, CallPoint.AFTER)
        val backwardCall = forwardCall.reversed()
        object : MethodForms {
            override fun statement(direction: Direction, s: CommonInst): StatementSummary =
                if (direction == Direction.FORWARD) statement else backwardStatement
            override fun call(direction: Direction, s: CommonInst, call: CommonCallExpr): CallPlan =
                if (direction == Direction.FORWARD) forwardCall else backwardCall
            override fun entryRules(direction: Direction): RuleStatement =
                if (direction == Direction.FORWARD) entry else backwardEntry
            override fun exitRules(direction: Direction, exit: CommonInst): ExitRules =
                if (direction == Direction.FORWARD) forwardExit else backwardExit
        }
    }

    val interpreter = object : Interpreter {
        override fun entryNode(method: MethodKey): CommonInst = method.statement
        override fun exitNodes(method: MethodKey): List<ExitNode> =
            listOf(ExitNode(inst(method.method, 3), false), ExitNode(inst(method.method, 4), true))
        override fun entryRules(method: MethodKey): RuleStatement = methodForms(method).entryRules(Direction.FORWARD)
        override fun exitRules(method: MethodKey, exit: CommonInst): ExitRules =
            methodForms(method).exitRules(Direction.FORWARD, exit)
        override fun statementSummary(method: MethodKey, statement: CommonInst): StatementSummary =
            methodForms(method).statement(Direction.FORWARD, statement)
        override fun callPlan(caller: MethodKey, statement: CommonInst, call: CommonCallExpr): CallPlan =
            methodForms(caller).call(Direction.FORWARD, statement, call)
        override fun isSummaryBase(base: AccessPathBase): Boolean =
            base !is AccessPathBase.LocalVar && base !is AccessPathBase.CleanerTemporary
    }
    val contexts = MethodContextCache(interpreter, object : MethodContextSource {
        override fun forms(key: MethodKey): MethodForms = methodForms(key)
        override fun graph(key: MethodKey, direction: Direction): MethodInstGraph = graph(key.method, direction)
    })

    fun shared(threads: Int = 1, unitResolver: UnitResolver<CommonMethod> = this.unitResolver,
               cancellationTimeout: Duration = 1.seconds, diagnostics: Boolean = false): SharedObjects =
        SharedObjects(manager, ops, interpreter, language, contexts, records, vulnerabilities, unitResolver,
            refManager, cancellation, threads, cancellationTimeout, diagnostics)

    fun config(index: Int = 1, L: Int = maxOf(1, index - 1), roots: List<MethodKey> = listOf(a),
               demand: DemandStore? = if (index == 1) null else DemandStore.Builder(manager).build(),
               seeds: SeedIndex = SeedIndex.EMPTY): RunConfig =
        RunConfig(index, L, demand, records.view(), seeds, roots)

    fun field(name: String): Int = manager.accessors.index(FieldAccessor("Box", name, "Box"))
    fun pattern(base: AccessPathBase = AccessPathBase.Argument(0), path: List<Int> = emptyList(),
                tail: Tail = Tail.EXACT, mark: MarkSlot = MarkSlot.Concrete(manager.marks.mark("T")),
                exclusion: ExclusionSet = ExclusionSet.Empty): Pattern = Pattern(PathFact(base, path, tail, mark), exclusion)
    fun fact(base: AccessPathBase = AccessPathBase.Argument(0), path: List<Int> = emptyList(),
             tail: Tail = Tail.EXACT, mark: MarkSlot = MarkSlot.Concrete(manager.marks.mark("T")),
             exclusion: ExclusionSet = ExclusionSet.Empty, layer: Layer = Layer.NORMAL): Facts =
        manager.factsOf(PathFact(base, path, tail, mark), exclusion, layer)
}

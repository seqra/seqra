package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.*
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
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
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

data class StatementInput(val key: MethodKey, val index: Int, val summary: StatementSummary)
data class EntryInput(val key: MethodKey, val rules: RuleStatement)
data class ExitInput(val key: MethodKey, val index: Int, val rules: ExitRules)
data class CallInput(val key: MethodKey, val index: Int, val plan: CallPlan, val expression: CommonCallExpr)
data class RawRecord(val method: MethodKey, val direction: Direction, val premise: Pattern, val conclusion: Conclusion)
data class ProgramRunInputs(val roots: List<MethodKey>, val demand: List<Pair<MethodKey, DemandPattern>>,
                            val records: List<RawRecord>, val seeds: List<Seed>)

private fun <T> ownedList(values: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
private fun ownPath(fact: PathFact): PathFact = fact.copy(path = ownedList(fact.path))
private fun ownPattern(pattern: Pattern): Pattern = pattern.copy(fact = ownPath(pattern.fact))

/** Method identity and source graph are fixture inputs, not a production graph observation. */
class ProgramMethod internal constructor(val method: CommonMethod, successors: List<List<Int>>,
                                         val normalExit: Int, val exceptionalExit: Int?) {
    data class Context(val name: String) : MethodContext {
        override fun toString(): String = name
    }
    val successors: List<List<Int>> = ownedList(successors.map(::ownedList))
    private val instructions: Array<CommonInst> = Array(successors.size) { index ->
        object : CommonInst {
            override val location = object : CommonInstLocation {
                override val method = this@ProgramMethod.method
                override val index = index
            }
            override fun toString(): String = "${method.name}:$index"
        }
    }
    private val keys = ConcurrentHashMap<Pair<String, Int>, MethodKey>()
    fun inst(index: Int): CommonInst = instructions[index]
    fun key(context: String = "context", entry: Int = 0): MethodKey =
        keys.computeIfAbsent(context to entry) { MethodKey(Context(context), inst(entry)) }
    override fun toString(): String = method.name
}

/** Complete finite input program. Construction lists are separate from engine indexes and tree stores. */
class FiniteProgramFixtures(val cancellation: Cancellation = Cancellation(), threads: Int = 1) : AutoCloseable {
    private data class ProgramUnit(val name: String) : UnitType
    val refManager = RefManager()
    val ap = ApManager(cancellation, refManager)
    val manager: ApManager get() = ap
    val ops = ApOps(ap)
    val records = PersistentRecordStore(ap)
    val vulnerabilities = ConcurrentVulnerabilityStore(ap)
    private val methodData = ArrayList<ProgramMethod>()
    val methods: List<ProgramMethod> = Collections.unmodifiableList(methodData)
    private val statements = ArrayList<StatementInput>()
    private val entries = ArrayList<EntryInput>()
    private val exits = ArrayList<ExitInput>()
    private val calls = ArrayList<CallInput>()
    val statementInputs: List<StatementInput> = Collections.unmodifiableList(statements)
    val entryInputs: List<EntryInput> = Collections.unmodifiableList(entries)
    val exitInputs: List<ExitInput> = Collections.unmodifiableList(exits)
    val callInputs: List<CallInput> = Collections.unmodifiableList(calls)
    private val formCache = ConcurrentHashMap<MethodKey, MethodForms>()
    private val graphCache = ConcurrentHashMap<Pair<MethodKey, Direction>, MethodInstGraph>()
    private val callExpressions = ConcurrentHashMap<CommonInst, CommonCallExpr>()
    private val runInputs = IdentityHashMap<RunConfig, ProgramRunInputs>()
    private val rawFacts = IdentityHashMap<Facts, List<Conclusion>>()
    private val recordInputs = IdentityHashMap<Record, List<Conclusion>>()
    val defaultCallExpression = object : CommonCallExpr {
        override val typeName = "Box"
        override val args: List<CommonValue> = emptyList()
    }

    fun method(name: String, successors: List<List<Int>>, normalExit: Int, exceptionalExit: Int? = null): ProgramMethod {
        require(successors.isNotEmpty())
        require(normalExit in successors.indices && (exceptionalExit == null || exceptionalExit in successors.indices))
        require(successors.all { next -> next.all { it in successors.indices } })
        require(methodData.none { it.method.name == name }) { "Finite methods have unique diagnostic names" }
        val common = object : CommonMethod {
            override val name = name
            override val parameters: List<CommonMethodParameter> = emptyList()
            override val returnType = object : CommonTypeName { override val typeName = "Box" }
            override fun flowGraph(): ControlFlowGraph<CommonInst> = error("Use the explicit finite compact graph")
            override fun toString(): String = name
        }
        return ProgramMethod(common, successors, normalExit, exceptionalExit).also(methodData::add)
    }
    fun key(method: ProgramMethod, context: String = "context", entry: Int = 0): MethodKey = method.key(context, entry)
    fun programMethod(key: MethodKey): ProgramMethod = methodData.single { it.method === key.method }
    fun isKnown(key: MethodKey): Boolean = methodData.any { it.method === key.method }
    fun inst(key: MethodKey, index: Int): CommonInst = programMethod(key).inst(index)
    private fun mutable(key: MethodKey) {
        check(isKnown(key)) { "The method key belongs to this finite program" }
        check(!formCache.containsKey(key)) { "Cached forms are immutable; configure the full method key before reading it" }
    }
    fun statement(key: MethodKey, index: Int, summary: StatementSummary) {
        mutable(key); inst(key, index)
        statements.removeAll { it.key == key && it.index == index }
        statements += StatementInput(key, index, summary)
    }
    fun entry(key: MethodKey, rules: RuleStatement) {
        mutable(key)
        entries.removeAll { it.key == key }
        entries += EntryInput(key, rules)
    }
    fun exit(key: MethodKey, index: Int, rules: ExitRules) {
        mutable(key); inst(key, index)
        exits.removeAll { it.key == key && it.index == index }
        exits += ExitInput(key, index, rules)
    }
    fun call(key: MethodKey, index: Int, plan: CallPlan, expression: CommonCallExpr = defaultCallExpression) {
        mutable(key)
        val node = inst(key, index)
        val old = callExpressions.putIfAbsent(node, expression)
        check(old == null || old === expression) { "One context-free instruction has one call expression" }
        calls.removeAll { it.key == key && it.index == index }
        calls += CallInput(key, index, plan, expression)
    }
    fun callExpression(key: MethodKey, index: Int): CommonCallExpr? = callExpressions[inst(key, index)]

    fun forms(key: MethodKey): MethodForms = formCache.computeIfAbsent(key) {
        val statementRows = statements.filter { it.key == key }.associate { it.index to it.summary }
        val exitRows = exits.filter { it.key == key }.associate { it.index to it.rules }
        val callRows = calls.filter { it.key == key }.associate { it.index to it.plan }
        val entryRules = entries.singleOrNull { it.key == key }?.rules ?: RuleStatement.EMPTY
        val statementCache = FormsCache(programMethod(key).successors.size, StatementSummary::reversed)
        val exitCache = FormsCache(programMethod(key).successors.size) { e: ExitRules -> ExitRules(e.reversed(), false, emptySet()) }
        val callCache = FormsCache(programMethod(key).successors.size, CallPlan::reversed)
        val entryCache = FormsCache(1, RuleStatement::reversed)
        object : MethodForms {
            override fun statement(direction: Direction, s: CommonInst): StatementSummary =
                statementCache.get(s.location.index, direction) { statementRows[s.location.index] ?: StatementSummary.EMPTY }
            override fun entryRules(direction: Direction): RuleStatement = entryCache.get(0, direction) { entryRules }
            override fun exitRules(direction: Direction, exit: CommonInst): ExitRules =
                exitCache.get(exit.location.index, direction) { exitRows[exit.location.index] ?: ExitRules.EMPTY }
            override fun call(direction: Direction, s: CommonInst, call: CommonCallExpr): CallPlan =
                callCache.get(s.location.index, direction) { checkNotNull(callRows[s.location.index]) { "A known call needs a full-key plan" } }
        }
    }
    fun graph(key: MethodKey, direction: Direction): MethodInstGraph = graphCache.computeIfAbsent(key to direction) {
        val method = programMethod(key)
        val next = if (direction == Direction.FORWARD) method.successors else
            method.successors.indices.map { target -> method.successors.indices.filter { target in method.successors[it] } }
        MethodInstGraph(Array(next.size, method::inst),
            CompactGraph.build(next.size) { index -> BitSet().apply { next[index].forEach(::set) } },
            BitSet().apply {
                if (direction == Direction.FORWARD) { set(method.normalExit); method.exceptionalExit?.let(::set) }
                else set(key.statement.location.index)
            })
    }
    val interpreter = object : Interpreter {
        override fun entryNode(method: MethodKey): CommonInst = method.statement
        override fun exitNodes(method: MethodKey): List<ExitNode> = programMethod(method).let { m ->
            listOf(ExitNode(m.inst(m.normalExit), false)) + listOfNotNull(m.exceptionalExit?.let { ExitNode(m.inst(it), true) })
        }
        override fun entryRules(method: MethodKey): RuleStatement = forms(method).entryRules(Direction.FORWARD)
        override fun exitRules(method: MethodKey, exit: CommonInst): ExitRules = forms(method).exitRules(Direction.FORWARD, exit)
        override fun statementSummary(method: MethodKey, statement: CommonInst): StatementSummary = forms(method).statement(Direction.FORWARD, statement)
        override fun callPlan(caller: MethodKey, statement: CommonInst, call: CommonCallExpr): CallPlan = forms(caller).call(Direction.FORWARD, statement, call)
        override fun isSummaryBase(base: AccessPathBase): Boolean = base !is AccessPathBase.LocalVar && base !is AccessPathBase.CleanerTemporary
    }
    val language = object : LanguageManager {
        private fun method(method: CommonMethod): ProgramMethod = methodData.single { it.method === method }
        override fun getInstIndex(inst: CommonInst): Int = inst.location.index
        override fun getMaxInstIndex(method: CommonMethod): Int = method(method).successors.lastIndex
        override fun getInstByIndex(method: CommonMethod, index: Int): CommonInst = method(method).inst(index)
        override fun isEmpty(method: CommonMethod): Boolean = methodData.none { it.method === method }
        override fun getCallExpr(inst: CommonInst): CommonCallExpr? = callExpressions[inst]
        override fun producesExceptionalControlFlow(inst: CommonInst): Boolean = method(inst.location.method).let { m ->
            m.exceptionalExit != null && m.exceptionalExit in m.successors[inst.location.index]
        }
        override fun getCalleeMethod(callExpr: CommonCallExpr): CommonMethod = calls.filter { it.expression === callExpr }
            .flatMap { row -> row.plan.stages.filterIsInstance<CallStage.Callees>().flatMap { it.callees } }
            .map { it.method }.distinct().single()
        override val methodContextSerializer: MethodContextSerializer get() = error("Serialization is outside the finite run fixture")
    }
    val contexts = MethodContextCache(interpreter, object : MethodContextSource {
        override fun forms(key: MethodKey): MethodForms = this@FiniteProgramFixtures.forms(key)
        override fun graph(key: MethodKey, direction: Direction): MethodInstGraph = this@FiniteProgramFixtures.graph(key, direction)
    })
    fun directed(direction: Direction): DirectedForms = contexts.directed(direction)
    private val unitResolver = UnitResolver<CommonMethod> { method ->
        if (methodData.any { it.method === method }) ProgramUnit(method.name) else UnknownUnit
    }
    val shared = SharedObjects(ap, ops, interpreter, language, contexts, records, vulnerabilities, unitResolver,
        refManager, cancellation, threads)
    fun freshShared(sameUnit: Boolean = false, freshVulnerabilities: Boolean = true, threads: Int = 1): SharedObjects =
        SharedObjects(ap, ops, interpreter, language, contexts, records,
            if (freshVulnerabilities) ConcurrentVulnerabilityStore(ap) else vulnerabilities,
            if (sameUnit) UnitResolver<CommonMethod> { if (methodData.any { m -> m.method === it }) ProgramUnit("all") else UnknownUnit }
            else unitResolver, refManager, cancellation, threads)

    fun config(index: Int = 1, L: Int = 1, roots: List<MethodKey>,
               demand: List<Pair<MethodKey, DemandPattern>> = emptyList(),
               records: List<Pair<MethodKey, Record>> = emptyList(), seeds: List<Seed> = emptyList()): RunConfig {
        val rawDemand = ownedList(demand.map { (method, d) -> method to DemandPattern(ownPattern(d.entry), d.exit?.let(::ownPattern)) })
        val recordStore = PersistentRecordStore(ap)
        val rawRecords = ArrayList<RawRecord>()
        for ((method, record) in records) {
            check(record.method == method)
            val conclusions = recordInputs[record] ?: rawFacts[record.conclusion]
                ?: error("Use fixture.fact or fixture.record for native inputs; expected leaves must precede production tree merging")
            recordStore.add(record)
            for (c in conclusions) rawRecords += RawRecord(method, record.direction, ownPattern(record.premise.toPattern()),
                c.copy(fact = ownPath(c.fact)))
        }
        val ownedSeeds = ownedList(seeds.map { seed -> when (seed) {
            is Seed.Source -> seed.copy(edge = seed.edge.copy(from = ownPath(seed.edge.from), to = ownPath(seed.edge.to)))
            is Seed.Sink -> Seed.Sink(seed.rule, seed.method, seed.statement, ownPattern(seed.requirement))
            is Seed.PredicateSink -> seed.copy()
        } })
        val builder = if (index == 1) null else DemandStore.Builder(ap).also { b -> rawDemand.forEach { b.add(it.first, it.second) } }
        val config = RunConfig(index, L, builder?.build(), recordStore, SeedIndex.of(ownedSeeds), roots)
        runInputs[config] = ProgramRunInputs(ownedList(roots), rawDemand, ownedList(rawRecords), ownedSeeds)
        return config
    }
    fun inputs(config: RunConfig): ProgramRunInputs = checkNotNull(runInputs[config]) { "The config belongs to this fixture" }

    fun field(name: String): Int = ap.accessors.index(FieldAccessor("Box", name, "Box"))
    fun mark(name: String): TaintMark = ap.marks.mark(name)
    fun pattern(base: AccessPathBase = AccessPathBase.Argument(0), path: List<Int> = emptyList(), tail: Tail = Tail.EXACT,
                mark: MarkSlot = MarkSlot.Concrete(mark("T")), exclusion: ExclusionSet = ExclusionSet.Empty): Pattern =
        Pattern(PathFact(base, ownedList(path), tail, mark), exclusion)
    fun fact(base: AccessPathBase = AccessPathBase.Argument(0), path: List<Int> = emptyList(), tail: Tail = Tail.EXACT,
             mark: MarkSlot = MarkSlot.Concrete(mark("T")), exclusion: ExclusionSet = ExclusionSet.Empty,
             layer: Layer = Layer.NORMAL): Facts {
        val fact = PathFact(base, ownedList(path), tail, mark)
        val value = ap.factsOf(fact, exclusion, layer)
        val demand = layer == Layer.DEMAND || tail == Tail.ANY || (tail == Tail.STAR && mark !is MarkSlot.Star)
        val raw = if (base == AccessPathBase.Zero) Conclusion(fact, ExclusionSet.Empty, layer == Layer.DEMAND)
            else if (demand && (tail == Tail.STAR || tail == Tail.ANY_TAINT || tail == Tail.ANY))
                Conclusion(fact.copy(tail = Tail.ANY), ExclusionSet.Empty, true)
            else Conclusion(fact, exclusion, demand)
        rawFacts[value] = listOf(raw)
        return value
    }
    fun record(method: MethodKey, direction: Direction, premise: InitialAp, conclusions: List<Conclusion>): Record {
        require(conclusions.isNotEmpty())
        val owned = ownedList(conclusions.map { it.copy(fact = ownPath(it.fact)) })
        val values = owned.map { ap.factsOf(it.fact, it.exclusion, if (it.demand) Layer.DEMAND else Layer.NORMAL) }
        val combined = values.reduce { a, b -> ap.mergeAddDelta(a, b).first }
        return Record(method, direction, premise, combined).also { recordInputs[it] = owned }
    }

    fun describe(): String = buildString {
        for (m in methods) appendLine("method=${m.method.name} adjacency=${m.successors} normal=${m.normalExit} exceptional=${m.exceptionalExit}")
        val accessorIds = LinkedHashSet<Int>()
        val markIds = LinkedHashSet<Int>()
        fun key(k: MethodKey): String = "${k.method.name}/${k.context}/${k.statement.location.index}"
        fun exclusion(e: ExclusionSet) { if (e is ExclusionSet.Concrete) accessorIds.addAll(e.ids.toList()) }
        fun fact(f: PathFact): String {
            accessorIds.addAll(f.path)
            when (val mark = f.mark) {
                is MarkSlot.Concrete -> markIds += mark.mark.id
                is MarkSlot.Star -> markIds.addAll(mark.excluded.ids.toList())
            }
            return f.toString()
        }
        fun pattern(p: Pattern): String { fact(p.fact); exclusion(p.exclusion); return p.toString() }
        fun pathEdge(e: PathEdge): String {
            fact(e.from); fact(e.to); exclusion(e.exclusion); exclusion(e.fromExclusion); exclusion(e.toExclusion)
            return e.toString()
        }
        fun edge(e: MicroEdge): String = "edge=${pathEdge(e.edge)} forward=${pathEdge(e.forward)} conjunctive=${e.conjunctive}"
        fun summary(s: StatementSummary): String = "touched=${s.touched} edges=${s.edges.map(::edge)} " +
            "conjunctions=${s.conjunctions.map { "literals=${it.literals.map(::pattern)} target=${fact(it.target)}" }} " +
            "operandFilters=${s.typeFilters.map { (base, f) -> "$base:${f.may.javaClass.name}/markPolicy=${f.markPolicy != null}" }} " +
            "resultFilters=${s.resultFilters.map { (base, f) -> "$base:${f.may.javaClass.name}/markPolicy=${f.markPolicy != null}" }}"
        fun sink(s: SinkRule): String = "rule=${s.rule.id} alternative=${s.alternative} patterns=${s.patterns.map(::pattern)} " +
            "endFacts=${s.endFacts.map(::edge)}"
        fun rules(r: RuleStatement): String = "summary={${summary(r.summary)}} " +
            "endFacts={${summary(r.endFacts)}} sinks=${r.sinks.map(::sink)}"
        fun cleaner(c: Cleaner): String {
            val path = c.path?.toIntArray()?.toList()
            accessorIds.addAll(path.orEmpty())
            c.mark?.let { markIds += it.id }
            return "base=${c.base} path=$path reach=${c.reach} mark=${c.mark}"
        }
        fun action(a: CleanStep): String = when (a) {
            is CleanStep.Clean -> "Clean{${cleaner(a.cleaner)}}"
            is CleanStep.Kill -> "Kill{${summary(a.keepEdges)}}"
            is CleanStep.Field -> "Field{temporary=${a.temporary} read={${summary(a.read)}} " +
                "cleaner={${cleaner(a.cleaner)}} write={${summary(a.write)}}}"
        }
        fun stage(s: CallStage): String = "${s.from}->${s.to} " + when (s) {
            is CallStage.Edges -> "Edges(kind=${s.kind}, summary={${summary(s.summary)}}, guard=" +
                when (val g = s.guard) { null -> "none"; Guard.MemoryEffect -> "MemoryEffect"
                    is Guard.SinkTriggered -> "SinkTriggered{${sink(g.sink)}}" } +
                ", trigger=${s.trigger?.let(::sink)})"
            is CallStage.Actions -> "Actions(ordered=${s.steps.map(::action)})"
            is CallStage.Callees -> "Callees(keys=${s.callees.map(::key)})"
        }
        for (row in statementInputs) appendLine("statement ${key(row.key)}:${row.index} ${summary(row.summary)}")
        for (row in entryInputs) appendLine("entry ${key(row.key)} ${rules(row.rules)}")
        for (row in exitInputs) appendLine("exit ${key(row.key)}:${row.index} ${rules(row.rules.rules)} " +
            "globalStateDrop=${row.rules.globalStateDrop} entryMarks=${row.rules.entryMarks}")
        for (row in callInputs) appendLine("call ${key(row.key)}:${row.index} expressionType=${row.expression.typeName} " +
            "touched=${row.plan.touched} entry=${row.plan.entry} exit=${row.plan.exit} " +
            "passReads=${row.plan.passReads} stages=${row.plan.stages.map(::stage)} sinks=${row.plan.sinks.map(::sink)}")
        val inputs = runInputs.entries.sortedWith(compareBy({ it.key.index }, { it.key.fieldLimit },
            { it.value.roots.joinToString(transform = ::key) }))
        for ((config, input) in inputs) {
            appendLine("run ${config.index} L=${config.fieldLimit} roots=${input.roots.map(::key)}")
            input.demand.forEach { (method, demand) -> appendLine("  demand ${key(method)} entry=${pattern(demand.entry)} exit=${demand.exit?.let(::pattern)}") }
            input.records.forEach { r ->
                fact(r.conclusion.fact); exclusion(r.conclusion.exclusion)
                appendLine("  record ${key(r.method)} ${r.direction} premise=${pattern(r.premise)} conclusion=${r.conclusion}")
            }
            for (seed in input.seeds) appendLine("  seed ${key(seed.method)}:${seed.statement.location.index} " + when (seed) {
                is Seed.Source -> "Source forward=${pathEdge(seed.edge)}"
                is Seed.Sink -> "Sink rule=${seed.rule.id} requirement=${pattern(seed.requirement)}"
                is Seed.PredicateSink -> "Sink predicate=${seed.occurrence}"
            })
        }
        appendLine("accessors=${accessorIds.sorted().associateWith { ap.accessors.accessor(it) }}")
        appendLine("marks=${markIds.sorted().associateWith { ap.marks.name(TaintMark(it)) }}")
    }
    override fun close(): Unit = shared.close()
}

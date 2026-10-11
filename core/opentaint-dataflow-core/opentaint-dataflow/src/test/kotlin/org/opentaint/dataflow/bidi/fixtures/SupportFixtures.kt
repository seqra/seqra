package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.ir.api.common.cfg.CommonInst

/** Support reads real frozen actors and AddedFactStore links; no method work is simulated here. */
class SupportFixtures(val index: Int = 1) : AutoCloseable {
    val program = FiniteProgramFixtures()
    val manager get() = program.manager
    val ops get() = program.ops
    val shared get() = program.shared
    val vulnerabilities get() = program.vulnerabilities
    val cancellation get() = program.cancellation
    val config = program.config(index = index, roots = emptyList())
    val mode get() = config.mode
    private val methods = LinkedHashMap<String, ProgramMethod>()
    private val actors = LinkedHashMap<MethodKey, RunMethodAnalyzer>()
    private var frozen = false

    fun key(method: String, context: String = "context", entry: Int = 0): MethodKey =
        methods.getOrPut(method) {
            program.method(method, listOf(listOf(1), listOf(2), listOf(3), listOf(4), emptyList()), 4)
        }.key(context, entry)

    fun inst(key: MethodKey, index: Int): CommonInst = program.inst(key, index)
    fun field(name: String): Int = program.field(name)
    fun mark(name: String): TaintMark = program.mark(name)
    fun pattern(base: AccessPathBase = AccessPathBase.Argument(0), path: List<Int> = emptyList(),
                tail: Tail = Tail.EXACT, mark: MarkSlot = MarkSlot.Concrete(mark("T")),
                exclusion: ExclusionSet = ExclusionSet.Empty): Pattern = program.pattern(base, path, tail, mark, exclusion)
    fun initial(base: AccessPathBase = AccessPathBase.Argument(0), path: List<Int> = emptyList(),
                tail: Tail = Tail.EXACT, mark: MarkSlot = MarkSlot.Concrete(mark("T")),
                exclusion: ExclusionSet = ExclusionSet.Empty): InitialAp = manager.initial(pattern(base, path, tail, mark, exclusion))
    fun fact(base: AccessPathBase = AccessPathBase.Argument(0), path: List<Int> = emptyList(),
             tail: Tail = Tail.EXACT, mark: MarkSlot = MarkSlot.Concrete(mark("T")),
             exclusion: ExclusionSet = ExclusionSet.Empty, layer: Layer = Layer.NORMAL): Facts =
        program.fact(base, path, tail, mark, exclusion, layer)
    fun premise(vararg members: InitialAp): PremiseKey = manager.premiseOf(members.toList())

    private val port = object : RunnerPort<RunMethodAnalyzer> {
        override val config get() = this@SupportFixtures.config
        override val shared get() = this@SupportFixtures.shared
        override val forms get() = shared.contexts.directed(config.direction)
        override val ops get() = this@SupportFixtures.ops
        override val steps = ProtocolSteps.Inline
        override fun send(event: RunEvent): Boolean = error("Support fixtures never route engine work")
        override fun post(event: RunEvent.Delivery): Boolean = error("Support fixtures never publish work")
        override fun enqueue(analyzer: RunMethodAnalyzer): Boolean = error("Support fixtures insert links without emission")
        override fun analyzer(key: MethodKey): RunMethodAnalyzer = actor(key)
        override fun summaryStorage(key: MethodKey): SummaryStorage = error("Support never reads summary storage")
        override fun subscribe(sub: Subscription): Unit = error("Support fixtures never subscribe")
        override fun link(callee: MethodKey, ref: CallerRef, linkLayer: Layer, added: Facts): Unit =
            error("Support fixtures insert actual added links directly")
    }

    fun actor(key: MethodKey): RunMethodAnalyzer {
        check(!frozen) { "A frozen support fixture cannot construct another actor" }
        return actors.getOrPut(key) {
            RunMethodAnalyzer(key, port, PublicationSink { _, _ -> error("Support fixtures never publish") },
                MethodLocalServicesFactory(::RunMethodLocalServices), MethodCallServicesFactory(::RunMethodCallServices))
        }
    }

    fun link(callee: MethodKey, caller: MethodKey, premise: PremiseKey, added: Facts,
             callerLayer: Layer = Layer.NORMAL, linkLayer: Layer = added.layer, call: Int = 1): Facts? {
        actor(caller)
        return actor(callee).links.add(CallerRef(caller, premise, callerLayer, inst(caller, call)), linkLayer, added)
    }

    fun result(status: RunStatus = RunStatus.COMPLETE, direction: Direction = config.direction,
               order: List<MethodKey>? = null): RunResult<RunMethodAnalyzer> {
        if (status != RunStatus.COMPLETE) return RunResult(status, emptyList(), index, direction, vulnerabilities)
        if (!frozen) {
            actors.values.forEach { it.freeze() }
            frozen = true
        }
        val keys = order ?: actors.keys.toList()
        check(keys.toSet() == actors.keys) { "A result permutation must contain every actor key" }
        return RunResult(status, keys.map(actors::getValue), index, direction, vulnerabilities)
    }

    fun rule(id: String = "sink"): RuleId = object : CommonTaintConfigurationSink {
        override val id = id
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = id
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    fun witness(method: MethodKey, premise: PremiseKey = manager.zero, layer: Layer = Layer.NORMAL,
                facts: Facts = Reach.NORMAL, alternative: Int = 0, run: Int = index,
                rule: RuleId = rule(), statement: Int = 2): SinkWitness {
        actor(method)
        return SinkWitness(alternative, method, listOf(SinkEdge(premise, layer, facts)), run).also {
            vulnerabilities.add(VulnerabilityKey(rule, method.method, inst(method, statement)), it)
        }
    }

    override fun close() {
        if (!frozen) {
            cancellation.activate()
            actors.values.forEach { it.freeze() }
            frozen = true
        }
        program.close()
    }
}

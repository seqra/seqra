package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.ReportBuilder
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.ir.api.common.cfg.CommonInst

/** Real frozen method actors with explicitly stated raw and selected barrier inputs. */
class HandOffFixtures(val index: Int = 1, val L: Int = 1) : AutoCloseable {
    val finite = MethodAnalysisFixtures()
    val manager get() = finite.manager
    val ops get() = finite.ops
    val shared get() = finite.shared
    val records get() = finite.records
    val vulnerabilities get() = finite.vulnerabilities
    val cancellation get() = finite.cancellation
    val config = finite.config(index, L)
    val report = ReportBuilder()
    private val actors = LinkedHashMap<MethodKey, RunMethodAnalyzer>()
    private var frozen = false

    fun key(context: String = "context", entry: Int = 0): MethodKey = finite.store.key(context, entry)
    fun inst(index: Int): CommonInst = finite.inst(index)
    fun field(name: String): Int = finite.field(name)
    fun mark(name: String): TaintMark = finite.mark(name)
    fun pattern(base: AccessPathBase = AccessPathBase.Argument(0), path: List<Int> = emptyList(),
                tail: Tail = Tail.EXACT, mark: MarkSlot = MarkSlot.Concrete(mark("T")),
                exclusion: ExclusionSet = ExclusionSet.Empty): Pattern = finite.pattern(base, path, tail, mark, exclusion)
    fun initial(base: AccessPathBase = AccessPathBase.Argument(0), path: List<Int> = emptyList(),
                tail: Tail = Tail.EXACT, mark: MarkSlot = MarkSlot.Concrete(mark("T")),
                exclusion: ExclusionSet = ExclusionSet.Empty): InitialAp =
        manager.initial(pattern(base, path, tail, mark, exclusion))
    fun premise(vararg members: InitialAp): PremiseKey = manager.premiseOf(members.toList())
    fun fact(base: AccessPathBase = AccessPathBase.Argument(0), path: List<Int> = emptyList(),
             tail: Tail = Tail.EXACT, mark: MarkSlot = MarkSlot.Concrete(mark("T")),
             exclusion: ExclusionSet = ExclusionSet.Empty, layer: Layer = Layer.NORMAL): Facts =
        finite.fact(base, path, tail, mark, exclusion, layer)

    fun core(key: MethodKey = key(), localFactory: MethodLocalServicesFactory? = null,
             callFactory: MethodCallServicesFactory? = null): RunMethodAnalyzer =
        actors[key] ?: run {
            check(!frozen) { "A frozen hand-off fixture cannot construct another actor" }
            finite.core(config, localFactory, callFactory, key).also { actors[key] = it }
        }

    fun admit(core: RunMethodAnalyzer, ref: org.opentaint.dataflow.bidi.store.CallerRef, layer: Layer, facts: Facts) =
        finite.admit(core, ref, layer, facts)
    fun links(core: RunMethodAnalyzer) = finite.links(core)

    fun drain(core: RunMethodAnalyzer, quantum: Int = 1000): Int = finite.drain(core, quantum)

    /** These two methods prescribe the stored inputs; neither computes demand selection. */
    fun raw(key: MethodKey, premise: PremiseKey, facts: Facts): Facts? {
        check(!frozen)
        return core(key).summaries.add(premise, facts)
    }
    fun selected(key: MethodKey, premise: PremiseKey, facts: Facts): Facts? {
        check(!frozen)
        return core(key).summaries.addDemand(premise, facts)
    }
    fun hit(key: MethodKey, statement: CommonInst, forward: PathEdge): Boolean {
        check(!frozen)
        return checkNotNull(core(key).sourceHits) { "Only a backward actor has a source-hit store" }
            .add(key, statement, forward)
    }

    /** Full method closure and actor freeze precede the retained result used by the barrier. */
    fun result(status: RunStatus = RunStatus.COMPLETE, order: List<MethodKey>? = null): RunResult<RunMethodAnalyzer> {
        if (status != RunStatus.COMPLETE) return RunResult(status, emptyList(), index, config.direction, vulnerabilities)
        if (!frozen) {
            actors.values.forEach { drain(it) }
            actors.values.forEach { it.freeze() }
            frozen = true
        }
        val keys = order ?: actors.keys.toList()
        check(keys.toSet() == actors.keys && keys.size == actors.size) { "A result order contains every actor exactly once" }
        return RunResult(status, keys.map(actors::getValue), index, config.direction, vulnerabilities)
    }

    override fun close(): Unit = finite.close()
}

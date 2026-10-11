package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

/** Report mechanics after confirmation, without an iteration driver or support cache. */
class ReportTest {
    private val fixture = EngineShellFixtures()
    private val manager = fixture.manager
    private val store = ConcurrentVulnerabilityStore(manager)
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)
    private val f = fixture.field("f")
    private val g = fixture.field("g")
    private val t = MarkSlot.Concrete(manager.marks.mark("T"))
    private val u = MarkSlot.Concrete(manager.marks.mark("U"))

    private fun rule(name: String): RuleId = object : CommonTaintConfigurationSink {
        override val id = name
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = name
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    private fun key(sinkRule: RuleId = rule("sink"), statement: Int = 3) =
        VulnerabilityKey(sinkRule, fixture.methodA, fixture.inst(fixture.methodA, statement))

    private fun edge(base: AccessPathBase = x, path: List<Int> = listOf(f),
                     tail: Tail = Tail.EXACT, mark: MarkSlot = t,
                     exclusion: ExclusionSet = ExclusionSet.Empty, layer: Layer = Layer.NORMAL,
                     premise: PremiseKey = manager.initial(fixture.pattern(mark = t))) =
        SinkEdge(premise, layer, fixture.fact(base, path, tail, mark, exclusion, layer))

    private fun witness(run: Int, edges: List<SinkEdge> = listOf(edge()), alternative: Int = 0,
                        method: MethodKey = fixture.a, endFacts: List<PathFact> = emptyList(),
                        confirmed: Boolean = false) =
        SinkWitness(alternative, method, edges, run, endFacts).also { it.confirmed = confirmed }

    private fun put(key: VulnerabilityKey, witness: SinkWitness) { store.add(key, witness) }

    private fun result(run: Int, status: RunStatus = RunStatus.COMPLETE,
                       witnesses: VulnerabilityStore = store): RunResult<ShellActor> =
        RunResult(status, emptyList(), run,
            if (run % 2 == 1) Direction.FORWARD else Direction.BACKWARD, witnesses)

    private fun add(builder: ReportBuilder, run: Int, status: RunStatus = RunStatus.COMPLETE,
                    witnesses: VulnerabilityStore = store) =
        builder.add(fixture.config(index = run, L = 1), result(run, status, witnesses))

    private fun end(run: Int = 1, status: RunStatus = RunStatus.COMPLETE,
                    reason: EndReason = EndReason.POLICY) =
        AnalysisEnd(status, run, if (run % 2 == 1) Direction.FORWARD else Direction.BACKWARD, reason)

    private fun entries(builder: ReportBuilder): Map<VulnerabilityKey, Report.Entry> =
        builder.build(end()).entries.associateBy { it.key }

    @Test
    fun `a confirmed key keeps its first confirmed run and complete witnesses through later demand absence and confirmation`(): Unit {
        val builder = ReportBuilder()
        val vulnerability = key()
        val first = witness(1, confirmed = true,
            endFacts = listOf(PathFact(y, listOf(f), Tail.EXACT, t)))
        put(vulnerability, first)
        add(builder, 1)
        val original = entries(builder).getValue(vulnerability)
        assertEquals(ReportState.CONFIRMED, original.state)
        assertEquals(1, original.run)
        assertSame(first, original.witnesses.single())

        put(vulnerability, witness(3, method = fixture.key(context = "later demand")))
        add(builder, 3)
        add(builder, 5)
        put(vulnerability, witness(7, alternative = 1, confirmed = true,
            method = fixture.key(context = "later confirmation")))
        add(builder, 7)

        assertSame(original, entries(builder).getValue(vulnerability))
        assertSame(first, entries(builder).getValue(vulnerability).witnesses.single())
        assertEquals(1, builder.confirmedCount)
        assertTrue(builder.demandEntries().isEmpty())
        assertFalse(builder.hasDemandVulnerability())
    }

    @Test
    fun `the latest complete forward run replaces same key demand witnesses and refutes absent unconfirmed keys`(): Unit {
        val builder = ReportBuilder()
        val retained = key(rule("retained"))
        val refuted = key(rule("refuted"))
        val added = key(rule("added"))
        val oldRetained = witness(1)
        val oldRefuted = witness(1, edges = listOf(edge(base = y, mark = u)))
        put(retained, oldRetained)
        put(refuted, oldRefuted)
        add(builder, 1)
        val first = builder.build(end())

        val nextRetained = witness(3, method = fixture.key(context = "new context"), alternative = 2,
            edges = listOf(edge(tail = Tail.ANY_TAINT, exclusion = ExclusionSet.of(g))),
            endFacts = listOf(PathFact(y, listOf(g), Tail.ANY_TAINT, t)))
        val nextAdded = witness(3, edges = listOf(edge(layer = Layer.DEMAND)))
        put(retained, nextRetained)
        put(added, nextAdded)
        add(builder, 3)

        val latest = entries(builder)
        assertEquals(setOf(retained, added), latest.keys)
        assertTrue(latest.values.all { it.state == ReportState.DEMAND && it.run == 3 })
        assertSame(nextRetained, latest.getValue(retained).witnesses.single())
        assertSame(nextAdded, latest.getValue(added).witnesses.single())
        assertEquals(setOf(retained, refuted), first.entries.map { it.key }.toSet())
        assertSame(oldRetained, first.entries.single { it.key == retained }.witnesses.single())
        assertEquals(0, builder.confirmedCount)
        assertTrue(builder.hasDemandVulnerability())
    }

    @Test
    fun `one confirmed context or alternative confirms the key while retaining every witness from that run`(): Unit {
        val builder = ReportBuilder()
        val vulnerability = key()
        val other = key(rule("still demand"))
        val demand = witness(1, edges = listOf(edge(layer = Layer.DEMAND)),
            method = fixture.key(context = "demand context"))
        val unsupportedNormal = witness(1, alternative = 1,
            method = fixture.key(context = "unsupported normal context"))
        val confirmed = witness(1, alternative = 2, confirmed = true,
            method = fixture.key(context = "confirmed context"))
        val otherDemand = witness(1, edges = listOf(edge(mark = u)))
        for (w in listOf(demand, unsupportedNormal, confirmed)) put(vulnerability, w)
        put(other, otherDemand)
        add(builder, 1)

        val report = builder.build(end())
        val confirmedEntry = report.entries.single { it.key == vulnerability }
        assertEquals(ReportState.CONFIRMED, confirmedEntry.state)
        assertEquals(1, confirmedEntry.run)
        assertEquals(setOf(demand, unsupportedNormal, confirmed), confirmedEntry.witnesses.toSet())
        assertEquals(listOf(other), builder.demandEntries().map { it.key })
        assertSame(otherDemand, builder.demandEntries().single().witnesses.single())
        assertEquals(1, builder.confirmedCount)
        assertTrue(builder.hasDemandVulnerability())
    }

    @Test
    fun `report witnesses retain contexts entries alternatives literal order layers premises groups and end facts`(): Unit {
        val builder = ReportBuilder()
        val vulnerability = key()
        val a = manager.initial(fixture.pattern(mark = t))
        val b = manager.initial(fixture.pattern(base = y, mark = u))
        val left = edge(premise = a)
        val right = edge(base = y, mark = u, premise = b)
        val variants = listOf(
            witness(3),
            witness(3, method = fixture.key(context = "other")),
            witness(3, method = fixture.key(entry = 1)),
            witness(3, alternative = 1),
            witness(3, edges = listOf(edge(base = y))),
            witness(3, edges = listOf(edge(tail = Tail.ANY_TAINT, exclusion = ExclusionSet.of(f)))),
            witness(3, edges = listOf(edge(tail = Tail.ANY_TAINT, exclusion = ExclusionSet.of(g)))),
            witness(3, edges = listOf(edge(layer = Layer.DEMAND))),
            witness(3, edges = listOf(edge(premise = manager.union(a, b)))),
            witness(3, edges = listOf(left, right),
                endFacts = listOf(PathFact(y, listOf(f, g), Tail.EXACT, t))),
            witness(3, edges = listOf(right, left),
                endFacts = listOf(PathFact(y, listOf(g, f), Tail.ANY_TAINT, u))),
        )
        variants.forEach { put(vulnerability, it) }
        add(builder, 3)
        val entry = entries(builder).getValue(vulnerability)
        assertEquals(ReportState.DEMAND, entry.state)
        assertEquals(3, entry.run)
        assertEquals(variants.toSet(), entry.witnesses.toSet())
        assertEquals(variants.size, entry.witnesses.size)
        for (original in variants) {
            val retained = entry.witnesses.single { it === original }
            assertEquals(original.edges.map { it.premise }, retained.edges.map { it.premise })
            assertEquals(original.edges.map { it.layer }, retained.edges.map { it.layer })
            assertEquals(original.edges.map { it.facts.groupKey }, retained.edges.map { it.facts.groupKey })
            original.edges.zip(retained.edges) { before, after -> assertSame(before.facts, after.facts) }
            assertEquals(original.endFacts, retained.endFacts)
        }
    }

    @Test
    fun `same shape store unions reach the report as one complete witness without losing literal facts or end facts`(): Unit {
        val builder = ReportBuilder()
        val vulnerability = key()
        val firstEnd = PathFact(y, listOf(f), Tail.EXACT, t)
        val secondEnd = PathFact(y, listOf(g), Tail.EXACT, t)
        put(vulnerability, witness(1, endFacts = listOf(firstEnd)))
        put(vulnerability, witness(1, edges = listOf(edge(path = listOf(g))), endFacts = listOf(secondEnd)))
        val complete = store.witnessesOf(1).single().second
        add(builder, 1)

        val retained = entries(builder).getValue(vulnerability).witnesses.single()
        assertSame(complete, retained)
        assertEquals(setOf(firstEnd, secondEnd), retained.endFacts.toSet())
        assertEquals(setOf(
            Pattern(PathFact(x, listOf(f), Tail.EXACT, t), ExclusionSet.Empty),
            Pattern(PathFact(x, listOf(g), Tail.EXACT, t), ExclusionSet.Empty),
        ), fixture.ops.leaves(retained.edges.single().facts).toSet())
    }

    @Test
    fun `every incomplete forward or backward status preserves both earlier report sets without adding evidence`(): Unit {
        for (status in listOf(RunStatus.TIMEOUT, RunStatus.OOM, RunStatus.FAILED)) for (run in listOf(2, 3)) {
            val builder = ReportBuilder()
            val rows = ConcurrentVulnerabilityStore(manager)
            val confirmedKey = key(rule("confirmed $status $run"))
            val demandKey = key(rule("demand $status $run"))
            val forbiddenKey = key(rule("incomplete evidence $status $run"))
            rows.add(confirmedKey, witness(1, confirmed = true))
            rows.add(demandKey, witness(1, edges = listOf(edge(layer = Layer.DEMAND))))
            rows.add(forbiddenKey, witness(run, confirmed = true))
            add(builder, 1, witnesses = rows)
            val before = entries(builder)

            add(builder, run, status, rows)
            val after = entries(builder)
            assertEquals(before.keys, after.keys, "run=$run status=$status")
            before.forEach { (key, entry) -> assertSame(entry, after.getValue(key)) }
            assertFalse(forbiddenKey in after)
            assertEquals(before.values.count { it.state == ReportState.CONFIRMED }, builder.confirmedCount)
            assertEquals(before.values.filter { it.state == ReportState.DEMAND }.map { it.key }.toSet(),
                builder.demandEntries().map { it.key }.toSet())
            assertTrue(builder.hasDemandVulnerability())
        }
    }

    @Test
    fun `a complete backward result adds no report evidence and refutes no earlier demand key`(): Unit {
        val builder = ReportBuilder()
        val confirmedKey = key(rule("forward confirmed"))
        val demandKey = key(rule("forward demand"))
        val backwardKey = key(rule("backward evidence"))
        put(confirmedKey, witness(1, confirmed = true))
        put(demandKey, witness(1))
        add(builder, 1)
        val before = entries(builder)
        put(backwardKey, witness(2, confirmed = true))
        add(builder, 2)
        val after = entries(builder)
        assertEquals(before.keys, after.keys)
        before.forEach { (key, entry) -> assertSame(entry, after.getValue(key)) }
        assertFalse(backwardKey in after)
        assertEquals(1, builder.confirmedCount)
        assertEquals(listOf(demandKey), builder.demandEntries().map { it.key })
    }

    @Test
    fun `an incomplete first run gives an empty report for timeout memory guard and failure`(): Unit {
        val vulnerability = key()
        put(vulnerability, witness(1, confirmed = true))
        put(key(rule("unconfirmed")), witness(1, edges = listOf(edge(layer = Layer.DEMAND))))
        for (status in listOf(RunStatus.TIMEOUT, RunStatus.OOM, RunStatus.FAILED)) {
            val builder = ReportBuilder()
            add(builder, 1, status)
            val end = end(1, status, EndReason.ABNORMAL)
            val report = builder.build(end)
            assertTrue(report.entries.isEmpty())
            assertTrue(builder.demandEntries().isEmpty())
            assertFalse(builder.hasDemandVulnerability())
            assertEquals(0, builder.confirmedCount)
            assertSame(end, report.end)
        }
    }

    @Test
    fun `an empty complete forward run refutes demand while previously confirmed keys stay final`(): Unit {
        val builder = ReportBuilder()
        val confirmedKey = key(rule("confirmed"))
        val demandKey = key(rule("demand"))
        val confirmed = witness(1, confirmed = true)
        put(confirmedKey, confirmed)
        put(demandKey, witness(1))
        add(builder, 1)
        add(builder, 3)
        assertEquals(setOf(confirmedKey), entries(builder).keys)
        assertSame(confirmed, entries(builder).getValue(confirmedKey).witnesses.single())
        assertEquals(1, builder.confirmedCount)
        assertTrue(builder.demandEntries().isEmpty())
        assertFalse(builder.hasDemandVulnerability())
    }

    @Test
    fun `build preserves every specified analysis end without changing retained report entries`(): Unit {
        val builder = ReportBuilder()
        val vulnerability = key()
        put(vulnerability, witness(1))
        add(builder, 1)
        val ends = listOf(
            end(1, RunStatus.COMPLETE, EndReason.NO_DEMAND_EDGE),
            end(3, RunStatus.COMPLETE, EndReason.POLICY),
        ) + listOf(2, 3).flatMap { run -> listOf(RunStatus.TIMEOUT, RunStatus.OOM, RunStatus.FAILED)
            .map { status -> end(run, status, EndReason.ABNORMAL) } }
        val original = entries(builder).getValue(vulnerability)
        for (end in ends) {
            val report = builder.build(end)
            assertSame(end, report.end)
            assertEquals(end.status, report.end.status)
            assertEquals(end.run, report.end.run)
            assertEquals(end.direction, report.end.direction)
            assertEquals(end.reason, report.end.reason)
            assertSame(original, report.entries.single())
        }
        val stopped = ReportBuilder()
        put(vulnerability, witness(5, confirmed = true))
        add(stopped, 5)
        val stopEnd = end(5, RunStatus.COMPLETE, EndReason.STOP_RULE)
        assertSame(stopEnd, stopped.build(stopEnd).end)
        assertEquals(ReportState.CONFIRMED, stopped.build(stopEnd).entries.single().state)
        assertFalse(stopped.hasDemandVulnerability())
    }

    @Test
    fun `report and entry constructors own unmodifiable list snapshots while retaining entry and witness identities`(): Unit {
        val vulnerability = key()
        val retainedWitness = witness(1)
        val inputWitnesses = mutableListOf(retainedWitness)
        val entry = Report.Entry(vulnerability, ReportState.DEMAND, 1, inputWitnesses)
        inputWitnesses.clear()
        assertSame(retainedWitness, entry.witnesses.single())
        assertFailsWith<UnsupportedOperationException> { (entry.witnesses as MutableList<SinkWitness>).clear() }

        val inputEntries = mutableListOf(entry)
        val end = end()
        val report = Report(inputEntries, end)
        inputEntries.clear()
        assertSame(entry, report.entries.single())
        assertSame(end, report.end)
        assertFailsWith<UnsupportedOperationException> { (report.entries as MutableList<Report.Entry>).clear() }
        assertSame(retainedWitness, report.entries.single().witnesses.single())
    }

    @Test
    fun `built reports and demand snapshots remain unchanged after later builder updates`(): Unit {
        val builder = ReportBuilder()
        val vulnerability = key()
        val first = witness(1)
        put(vulnerability, first)
        add(builder, 1)
        val demand = builder.demandEntries()
        val report = builder.build(end())
        assertFailsWith<UnsupportedOperationException> { (demand as MutableList<Report.Entry>).clear() }
        assertFailsWith<UnsupportedOperationException> { (report.entries as MutableList<Report.Entry>).clear() }
        assertFailsWith<UnsupportedOperationException> {
            (report.entries.single().witnesses as MutableList<SinkWitness>).clear()
        }

        val replacement = witness(3, alternative = 1, method = fixture.key(context = "replacement"))
        put(vulnerability, replacement)
        add(builder, 3)
        assertSame(first, demand.single().witnesses.single())
        assertSame(first, report.entries.single().witnesses.single())
        assertSame(replacement, builder.demandEntries().single().witnesses.single())
        assertEquals(1, report.entries.single().run)
        assertEquals(3, builder.demandEntries().single().run)
    }
}

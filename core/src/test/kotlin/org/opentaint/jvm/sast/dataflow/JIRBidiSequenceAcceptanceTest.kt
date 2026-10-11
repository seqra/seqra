package org.opentaint.jvm.sast.dataflow

import org.junit.jupiter.api.Test
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.configuration.TaintCleanReach
import org.opentaint.dataflow.configuration.jvm.serialized.*
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Result
import org.opentaint.dataflow.jvm.ap.ifds.taint.PrimitiveTaintExt
import kotlin.test.*

/** Actual JVM entry with independent current-spec vectors and joined-run observations. */
class JIRBidiSequenceAcceptanceTest : AnalysisTest() {
    override val sourceFileExtension = "java"
    private val cls = "test.samples.bidi.BidiAcceptanceSample"
    private val dto = "test.samples.bidi.BidiAcceptanceSample\$Dto"
    private val t = "J03-T"
    private val u = "J03-U"
    private val zero = JvmObservedPath(AccessPathBase.Zero, tail = Tail.EXACT, mark = "*")
    private fun at(base: PositionBase) = PositionBaseWithModifiers.BaseOnly(base)
    private fun any(base: PositionBase) = PositionBaseWithModifiers.WithModifiers(base, listOf(PositionModifier.AnyField))
    private fun field(base: PositionBase, name: String) = PositionBaseWithModifiers.WithModifiers(base,
        listOf(PositionModifier.Field(dto, name, "java.lang.Object")))
    private fun entry(name: String, whole: Boolean = true, mark: String = t, argument: Int = 0) =
        SerializedRule.EntryPoint(functionMatcher(cls, name), taint = listOf(
            SerializedTaintAssignAction(mark, pos = if (whole) any(Argument(argument)) else at(Argument(argument)))))
    private fun sink(name: String, id: String, mark: String = t, whole: Boolean = false) = SerializedRule.Sink(
        functionMatcher(cls, name), condition = SerializedCondition.ContainsMark(mark,
            if (whole) any(Argument(0)) else at(Argument(0))), id = id, meta = SinkMetaData(note = id))
    private fun source(name: String, mark: String, whole: Boolean) = SerializedRule.Source(
        functionMatcher(cls, name), taint = listOf(SerializedTaintAssignAction(mark,
            pos = if (whole) any(Result) else at(Result))))
    private fun mayPass() = SerializedRule.PassThrough(functionMatcher(cls, "mayPass"),
        condition = SerializedCondition.ContainsMark(t, at(Argument(0))),
        copy = listOf(SerializedTaintPassAction(t, from = at(Argument(0)), to = any(Result))))
    private fun run(config: SerializedTaintConfig, entry: String, limits: List<Int> = listOf(1, 2, 3)) =
        runBidiAcceptance(config, cls, entry, limits)
    private fun findings(r: BidiAcceptanceResult) = r.report.entries.associate { it.key.rule.id to it.state }
    private fun assertFinding(r: BidiAcceptanceResult, expected: Map<String, ReportState>) {
        assertEquals(expected, findings(r))
        assertEquals(expected.keys, r.output.mapTo(linkedSetOf()) { it.vulnerability.ruleId })
        assertEquals(RunStatus.COMPLETE, r.report.end.status)
        assertTrue(r.runs.isNotEmpty())
        assertTrue(r.runs.all { it.status == RunStatus.COMPLETE })
        assertEquals(r.frontiers.map { it.run }, r.runs.map { it.index })
        for ((run, frontier) in r.runs.zip(r.frontiers)) {
            assertEquals(run.recordCrossings, frontier.recordCrossings)
            assertEquals(run.nonZeroInitials, frontier.analysed)
            assertEquals(run.methodCounters.keys - run.nonZeroInitials, frontier.zeroOnly)
            assertEquals(run.methodCounters.filterKeys { it in frontier.zeroOnly }.values.sumOf { it.edgeDeltas },
                frontier.zeroOnlyEdges)
            assertEquals(run.methodCounters.values.sumOf { it.demandLayerEdges }, run.demandLayerEdges)
            assertTrue(run.steps > 0)
            assertTrue(run.edgeDeltas > 0)
            run.outgoingSeeds?.let { assertEquals(it.size, frontier.seeds) }
        }
        for (entry in r.report.entries) assertTrue(r.witnesses.any { witness ->
            witness.rule == entry.key.rule && witness.statement == entry.key.statement &&
                entry.witnesses.any { it.methodKey == witness.method && it.run == witness.run &&
                    it.alternative == witness.alternative && it.confirmed == witness.confirmed }
        }, "The report must retain an observed full rule/site/context witness")
    }
    private fun normal(base: AccessPathBase, path: List<String> = emptyList(), tail: Tail,
                       mark: String = t, exclusion: Set<String> = emptySet()) =
        JvmObservedConclusion(JvmObservedPath(base, path, tail, mark, exclusion), Layer.NORMAL)

    @Test
    fun `JVM getter refines its field read and crosses the normal backward record`(): Unit {
        val config = SerializedTaintConfig(entryPoint = listOf(entry("getterFlow")),
            sink = listOf(sink("sinkAnyT", "j03-getter", whole = true)))
        val r = run(config, "getterFlow")
        assertFinding(r, mapOf("j03-getter" to ReportState.CONFIRMED))
        assertEquals(listOf(1, 2, 3), r.runs.map { it.index })
        val getter = r.method("getter")
        val argument = AccessPathBase.Argument(0)
        val result = AccessPathBase.Return
        val forwardPr = JvmObservedPath(argument, tail = Tail.STAR, mark = "*")
        val backwardPr = JvmObservedPath(result, tail = Tail.STAR, mark = "*")
        assertEquals(setOf(JvmObservedConclusion(JvmObservedPath(result, tail = Tail.ANY, mark = "*"), Layer.DEMAND)),
            r.raw(1, getter, result))
        assertContains(assertNotNull(r.runs[0].outgoingDemand), JvmObservedDemand(getter,
            JvmObservedPath(result, tail = Tail.ANY, mark = "*"), forwardPr))
        assertContains(r.runs[1].records, JvmObservedRecord(getter, Direction.BACKWARD, backwardPr,
            normal(argument, listOf("email"), Tail.STAR, "*")))
        assertEquals(setOf(zero), r.runs[2].initials.filter { it.method == getter }.mapTo(linkedSetOf()) { it.value })
        assertTrue(r.runs.all { it.requests.isEmpty() })
        assertTrue(r.frontiers[2].recordCrossings > 0)
        assertEquals(EndReason.STOP_RULE, r.report.end.reason)
    }

    @Test
    fun `JVM callee sink confirms after same site normal demanded premises arrive`(): Unit {
        val config = SerializedTaintConfig(entryPoint = listOf(entry("calleeSinkFlow")),
            sink = listOf(sink("sinkT", "j03-callee")))
        val r = run(config, "calleeSinkFlow")
        assertFinding(r, mapOf("j03-callee" to ReportState.CONFIRMED))
        assertEquals(listOf(1, 2, 3), r.runs.map { it.index })
        val use = r.method("use")
        val root = r.method("calleeSinkFlow")
        assertContains(r.runs[0].requests, JvmObservedRequest(use,
            JvmObservedPath(AccessPathBase.Argument(0), tail = Tail.STAR, mark = "*"), mark = t))
        // Object-typed BaseOnly includes the ordinary value and its array-element alternative.
        assertEquals(setOf(zero,
            JvmObservedPath(AccessPathBase.Argument(0), listOf("email"), Tail.EXACT, t),
            JvmObservedPath(AccessPathBase.Argument(0), listOf("email", "[e]"), Tail.EXACT, t),
            JvmObservedPath(AccessPathBase.Argument(0), listOf("email", "[e]"), Tail.ANY_TAINT, t)),
            r.runs[2].initials.filter { it.method == use }.mapTo(linkedSetOf()) { it.value })
        assertTrue(r.runs[2].links.any { it.caller == root && it.callee == use &&
            it.callerLayer == Layer.NORMAL && it.linkLayer == Layer.NORMAL && it.premise == setOf(zero) &&
            it.added == JvmObservedPath(AccessPathBase.Argument(0), tail = Tail.ANY_TAINT, mark = t) })
        assertTrue(r.runs.drop(1).all { it.requests.isEmpty() })
    }

    @Test
    fun `JVM identity preserves normal whole object source and stops at the first positive limit`(): Unit {
        for (later in listOf(1, 2, 3)) {
            val r = run(SerializedTaintConfig(entryPoint = listOf(entry("identityFlow")),
                sink = listOf(sink("sinkAnyT", "j03-identity", whole = true))), "identityFlow", listOf(1, later, later))
            assertFinding(r, mapOf("j03-identity" to ReportState.CONFIRMED))
            assertEquals(listOf(1), r.runs.map { it.index })
            val identity = r.method("identity")
            assertContains(r.runs.single().records, JvmObservedRecord(identity, Direction.FORWARD,
                JvmObservedPath(AccessPathBase.Argument(0), tail = Tail.STAR, mark = "*"),
                normal(AccessPathBase.Return, tail = Tail.STAR, mark = "*")))
            assertTrue(r.runs.single().selected.isEmpty())
            assertEquals(0, r.frontiers.single().seeds)
            assertNull(r.runs.single().outgoingSeeds)
            assertTrue(r.runs.single().requests.isEmpty())
        }
    }

    @Test
    fun `JVM setter and one deeper wrapper retain admitted sibling and block the overwritten field`(): Unit {
        for (name in listOf("setterFlow", "deeperSetterFlow")) {
            val r = run(SerializedTaintConfig(entryPoint = listOf(entry(name)), sink = listOf(
                sink("sinkT", "j03-setter-admitted"), sink("sinkBlockedT", "j03-setter-blocked"))), name)
            assertFinding(r, mapOf("j03-setter-admitted" to ReportState.CONFIRMED))
            assertEquals(listOf(1), r.runs.map { it.index })
            assertContains(r.runs.single().records, JvmObservedRecord(r.method("setter"), Direction.FORWARD,
                JvmObservedPath(AccessPathBase.Argument(0), tail = Tail.STAR, mark = "*"),
                normal(AccessPathBase.Return, tail = Tail.STAR, mark = "*", exclusion = setOf("name"))))
            assertTrue(r.runs.single().requests.isEmpty())
        }
    }

    private fun factoryConfig(entryName: String, replay: Boolean = false) = SerializedTaintConfig(
        entryPoint = if (replay) listOf(entry(entryName, whole = false)) else emptyList(),
        source = listOf(source("wholeSource", t, true), source("exactUSource", u, false)),
        sink = listOf(sink("sinkT", "j03-factory-T"), sink("sinkU", "j03-factory-U", u),
            sink("sinkBlockedT", "j03-factory-blocked")) +
            if (replay) listOf(sink("sinkAnyT", "j03-factory-may", whole = true)) else emptyList(),
        passThrough = if (replay) listOf(mayPass()) else emptyList())

    @Test
    fun `JVM zero premise factory emits separate native must exclusion and exact U record`(): Unit {
        val r = run(factoryConfig("factoryFlow"), "factoryFlow")
        assertFinding(r, mapOf("j03-factory-T" to ReportState.CONFIRMED, "j03-factory-U" to ReportState.CONFIRMED))
        val factory = r.method("factory")
        assertEquals(setOf(normal(AccessPathBase.Return, tail = Tail.ANY_TAINT, exclusion = setOf("name", "k")),
            normal(AccessPathBase.Return, listOf("k"), Tail.EXACT, u),
            JvmObservedConclusion(JvmObservedPath(AccessPathBase.Return, listOf("k"), Tail.ANY, u), Layer.DEMAND)),
            r.raw(1, factory, AccessPathBase.Return))
        assertEquals(setOf(normal(AccessPathBase.Return, tail = Tail.ANY_TAINT, exclusion = setOf("name", "k")),
            normal(AccessPathBase.Return, listOf("k"), Tail.EXACT, u)),
            r.runs[0].records.filter { it.method == factory && it.conclusion.value.base == AccessPathBase.Return }
                .mapTo(linkedSetOf()) { it.conclusion })
        assertTrue(r.runs[0].rawSummaries.filter { it.method == factory && it.conclusion.value.base == AccessPathBase.Return }
            .all { it.premise == setOf(zero) })
    }

    @Test
    fun `JVM factory native return replay crosses records without executing source rules again`(): Unit {
        val r = run(factoryConfig("factoryReplayFlow", replay = true), "factoryReplayFlow")
        assertFinding(r, mapOf("j03-factory-T" to ReportState.CONFIRMED, "j03-factory-U" to ReportState.CONFIRMED,
            "j03-factory-may" to ReportState.DEMAND))
        assertEquals(listOf(1, 2, 3), r.runs.map { it.index })
        val factory = r.method("factory")
        assertEquals(setOf(zero), r.runs[1].initials.filter { it.method == factory }.mapTo(linkedSetOf()) { it.value })
        assertTrue(r.runs[1].sourceHits.none { it.method == factory })
        assertTrue(r.runs[2].incomingSeeds.filterIsInstance<Seed.Source>().none { it.method == factory })
        assertTrue(r.runs[2].rawSummaries.none { it.method == factory && it.conclusion.value.base == AccessPathBase.Return })
        // B2 is only-zero for the already confirmed factory rules and need not read a native return.
        assertTrue(r.frontiers[2].recordCrossings > 0)
    }

    @Test
    fun `JVM AnyField pass keeps DEMAND while the matching source produces normal must`(): Unit {
        val commonSink = sink("sinkAnyT", "j03-source-or-pass", whole = true)
        val pass = run(SerializedTaintConfig(entryPoint = listOf(entry("passFlow", whole = false)),
            passThrough = listOf(mayPass()), sink = listOf(commonSink)), "passFlow")
        assertFinding(pass, mapOf("j03-source-or-pass" to ReportState.DEMAND))
        assertEquals(listOf(1, 2, 3), pass.runs.map { it.index })
        assertEquals(EndReason.POLICY, pass.report.end.reason)
        val conditional = SerializedRule.Source(functionMatcher(cls, "conditionalSource"),
            condition = SerializedCondition.ContainsMark(t, at(Argument(0))),
            taint = listOf(SerializedTaintAssignAction(t, pos = any(Result))))
        val source = run(SerializedTaintConfig(entryPoint = listOf(entry("sourceFlow", whole = false)),
            source = listOf(conditional), sink = listOf(commonSink)), "sourceFlow")
        assertFinding(source, mapOf("j03-source-or-pass" to ReportState.CONFIRMED))
        assertEquals(listOf(1), source.runs.map { it.index })
    }

    @Test
    fun `JVM named subtree cleaner preserves email and excludes name with a no cleaner positive control`(): Unit {
        val sinks = listOf(sink("sinkT", "j03-cleaner-email"), sink("sinkBlockedT", "j03-cleaner-name"))
        val cleaner = SerializedRule.Cleaner(functionMatcher(cls, "cleanNamed"), cleans = listOf(
            SerializedTaintCleanAction(t, field(Argument(0), "name"), TaintCleanReach.ExactAndAnyField)))
        val cleaned = run(SerializedTaintConfig(entryPoint = listOf(entry("fieldCleanerFlow")),
            cleaner = listOf(cleaner), sink = sinks), "fieldCleanerFlow")
        assertFinding(cleaned, mapOf("j03-cleaner-email" to ReportState.CONFIRMED))
        assertTrue(cleaned.runs.single().processed.any { it.conclusion.layer == Layer.NORMAL &&
            it.conclusion.value.tail == Tail.ANY_TAINT && "name" in it.conclusion.value.exclusion })
        val control = run(SerializedTaintConfig(entryPoint = listOf(entry("fieldCleanerControl")), sink = sinks),
            "fieldCleanerControl")
        assertFinding(control, mapOf("j03-cleaner-email" to ReportState.CONFIRMED, "j03-cleaner-name" to ReportState.CONFIRMED))
    }

    @Test
    fun `JVM exact named cleaner retains the documented demand residual on a must field`(): Unit {
        val cleaner = SerializedRule.Cleaner(functionMatcher(cls, "cleanNamed"), cleans = listOf(
            SerializedTaintCleanAction(t, field(Argument(0), "name"))))
        val r = run(SerializedTaintConfig(entryPoint = listOf(entry("fieldCleanerFlow")), cleaner = listOf(cleaner),
            sink = listOf(sink("sinkT", "j03-cleaner-email"), sink("sinkBlockedT", "j03-cleaner-name"))),
            "fieldCleanerFlow")
        assertFinding(r, mapOf("j03-cleaner-email" to ReportState.CONFIRMED, "j03-cleaner-name" to ReportState.DEMAND))
        assertTrue(r.runs[0].processed.any { it.conclusion.layer == Layer.DEMAND &&
            it.conclusion.value.path.lastOrNull() == "name" && it.conclusion.value.tail == Tail.ANY })
    }

    @Test
    fun `JVM conjunctive source joins both literals at one call and never across two calls`(): Unit {
        val conditional = SerializedRule.Source(functionMatcher(cls, "joint"),
            condition = SerializedCondition.and(listOf(SerializedCondition.ContainsMark(t, at(Argument(0))),
                SerializedCondition.ContainsMark(u, at(Argument(1))))),
            taint = listOf(SerializedTaintAssignAction("J03-joint", pos = at(Result))))
        fun config(entryName: String) = SerializedTaintConfig(entryPoint = listOf(
            entry(entryName, false, t, 0), entry(entryName, false, u, 1)), source = listOf(conditional),
            sink = listOf(sink("sinkJoint", "j03-joint", "J03-joint")))
        val joined = run(config("jointFlow"), "jointFlow")
        assertFinding(joined, mapOf("j03-joint" to ReportState.CONFIRMED))
        assertTrue(joined.report.entries.single().witnesses.any { it.confirmed })
        val separated = run(config("jointSplitFlow"), "jointSplitFlow")
        assertFinding(separated, emptyMap())
    }

    @Test
    fun `JVM removes root and deep argument entry marks at exit while retaining returned taint`(): Unit {
        for (path in listOf(emptyList(), listOf("email"))) {
            val target = if (path.isEmpty()) any(Argument(0)) else PositionBaseWithModifiers.WithModifiers(
                Argument(0), listOf(PositionModifier.Field(dto, "email", "java.lang.Object"), PositionModifier.AnyField))
            val source = SerializedRule.EntryPoint(functionMatcher(cls, "entryRemoval"),
                taint = listOf(SerializedTaintAssignAction(t, pos = target)))
            val r = run(SerializedTaintConfig(entryPoint = listOf(source),
                sink = listOf(sink("sinkT", "j03-entry-removal"))), "entryRemoval")
            assertFinding(r, mapOf("j03-entry-removal" to ReportState.CONFIRMED))
            val root = r.method("entryRemoval")
            assertTrue(r.runs[0].processed.any { it.method == root &&
                it.conclusion == normal(AccessPathBase.Argument(0), path, Tail.ANY_TAINT) })
            val summaries = r.runs[0].rawSummaries.filter { it.method == root && it.premise == setOf(zero) }
            assertTrue(summaries.filter { it.conclusion.value.base is AccessPathBase.This ||
                it.conclusion.value.base is AccessPathBase.Argument }.none { it.conclusion.value.mark == t })
            assertContains(summaries.map { it.conclusion }, normal(AccessPathBase.Return, path, Tail.ANY_TAINT))
        }
    }

    @Test
    fun `JVM primitive tracking AnyField byte array source confirms in run one`(): Unit {
        val primitive = "J03-byte" + PrimitiveTaintExt.PRIMITIVE_TRACKING_ENABLED_MODE
        val r = run(SerializedTaintConfig(entryPoint = listOf(entry("primitiveFlow", true, primitive)),
            sink = listOf(sink("sinkByte", "j03-primitive", primitive))), "primitiveFlow")
        assertFinding(r, mapOf("j03-primitive" to ReportState.CONFIRMED))
        assertEquals(listOf(1), r.runs.map { it.index })
        assertTrue(r.runs[0].processed.any { it.conclusion.value.tail == Tail.ANY_TAINT &&
            it.conclusion.value.mark == primitive && it.conclusion.layer == Layer.NORMAL })
    }

    @Test
    fun `JVM ordinary mark is rejected by the primitive element filter with a tracking positive control`(): Unit {
        val r = run(SerializedTaintConfig(entryPoint = listOf(entry("primitiveFlow")),
            sink = listOf(sink("sinkByte", "j03-nonprimitive", t))), "primitiveFlow")
        assertFinding(r, emptyMap())
        assertTrue(r.runs[0].processed.any { it.conclusion.value.mark == t &&
            it.conclusion.value.base == AccessPathBase.Argument(0) && it.conclusion.value.tail == Tail.ANY_TAINT },
            "Root byte-array policy admits ordinary mark; only element policy removes it")
    }
}

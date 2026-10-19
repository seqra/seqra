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
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRStaticCallExpr
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
    private fun run(config: SerializedTaintConfig, entry: String, limits: List<Int> = listOf(1, 1, 2)) =
        runBidiAcceptance(config, cls, entry, limits)
    private fun findings(r: BidiAcceptanceResult) = r.report.entries.associate { it.key.rule.id to it.state }
    private fun assertFinding(r: BidiAcceptanceResult, expected: Map<String, ReportState>,
                              normalRules: Set<String> = expected.keys) {
        assertEquals(expected, findings(r))
        assertEquals(expected.keys, r.output.mapTo(linkedSetOf()) { it.vulnerability.ruleId })
        assertEquals(RunStatus.COMPLETE, r.report.end.status)
        assertTrue(r.runs.isNotEmpty())
        assertTrue(r.runs.all { it.status == RunStatus.COMPLETE })
        assertTrue(r.runs.first().records.all { it.conclusion.layer == Layer.NORMAL &&
            it.conclusion.value.mark == "*" }, "Run-1 persists normal FLOW/control records; grouped taint remains DEMAND")
        assertTrue(r.runs.first().processed.filter { it.conclusion.value.mark in r.runs.first().markGroups.values }
            .all { it.conclusion.layer == Layer.DEMAND }, "Every run-1 group-valued result is DEMAND")
        assertTrue(r.runs.first().processed.all { it.conclusion.value.exclusion.isEmpty() },
            "Run-1 surviving exclusion updates demote instead of retaining field E")
        assertTrue(r.runs.drop(1).flatMap { it.initials }.all { it.value.base == AccessPathBase.Zero || it.value.mark != "*" },
            "Strong phases start actual concrete queries and concrete guide intersections, never abstract FLOW guidance")
        for ((previous, current) in r.runs.zipWithNext()) if (previous.direction == Direction.BACKWARD) {
            val supplied = assertNotNull(previous.outgoingSeeds).filterIsInstance<Seed.Source>().toSet()
            assertEquals(supplied, current.incomingSeeds.filterIsInstance<Seed.Source>().toSet(),
                "The next forward phase consumes the exact full original source-hit identities")
            assertEquals(previous.sourceHits.size, supplied.size)
            assertEquals(previous.sourceHits.mapTo(linkedSetOf()) { Triple(it.method, it.statement, it.action) },
                supplied.mapTo(linkedSetOf()) { Triple(it.method, it.statement, it.action) })
        }
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
        assertTrue(r.witnesses.all { !it.confirmed },
            "The minimal policy retains observed witnesses as unproved candidates")
        for (rule in normalRules) assertTrue(r.witnesses.any { witness ->
            witness.rule.id == rule && witness.literals.isNotEmpty() &&
                witness.literals.all { it.layer == Layer.NORMAL }
        }, "Rule $rule must still have an observed NORMAL literal support; DEMAND is its proof status")
        for (entry in r.report.entries) assertTrue(r.witnesses.any { witness ->
            witness.rule == entry.key.rule && witness.statement == entry.key.statement &&
                entry.witnesses.any { it.methodKey == witness.method && it.run == witness.run &&
                    it.alternative == witness.alternative && it.confirmed == witness.confirmed }
        }, "The report must retain an observed full rule/site/context witness")
    }
    private fun group(r: BidiAcceptanceResult, full: String = t): String = r.runs.first().markGroups.getValue(full)
    private fun demand(base: AccessPathBase, path: List<String> = emptyList(), tail: Tail, mark: String) =
        JvmObservedConclusion(JvmObservedPath(base, path, tail, mark), Layer.DEMAND)
    private fun normal(base: AccessPathBase, path: List<String> = emptyList(), tail: Tail,
                       mark: String = t, exclusion: Set<String> = emptySet()) =
        JvmObservedConclusion(JvmObservedPath(base, path, tail, mark, exclusion), Layer.NORMAL)

    @Test
    fun `JVM getter refines its actual concrete query without an abstract backward record`(): Unit {
        val config = SerializedTaintConfig(entryPoint = listOf(entry("getterFlow")),
            sink = listOf(sink("sinkAnyT", "j03-getter", whole = true)))
        val r = run(config, "getterFlow")
        assertFinding(r, mapOf("j03-getter" to ReportState.DEMAND))
        assertEquals(listOf(1, 2, 3), r.runs.map { it.index })
        val getter = r.method("getter")
        val argument = AccessPathBase.Argument(0)
        val result = AccessPathBase.Return
        val forwardPr = JvmObservedPath(argument, tail = Tail.STAR, mark = "*")
        assertEquals(setOf(JvmObservedConclusion(JvmObservedPath(result, tail = Tail.ANY, mark = "*"), Layer.DEMAND)),
            r.raw(1, getter, result))
        assertEquals(true, r.runs[0].outgoingStaticGroupGuidance)
        assertContains(assertNotNull(r.runs[0].outgoingDemand), JvmObservedDemand(getter,
            JvmObservedPath(result, tail = Tail.ANY, mark = "*"), forwardPr))
        val backwardPr = JvmObservedPath(result, tail = Tail.ANY, mark = t)
        assertEquals(setOf(zero, backwardPr), r.runs[1].initials.filter { it.method == getter }.mapTo(linkedSetOf()) { it.value })
        assertContains(r.runs[1].rawSummaries, JvmObservedEdge(getter, setOf(backwardPr),
            JvmObservedConclusion(JvmObservedPath(argument, listOf("email"), Tail.ANY, t), Layer.DEMAND)))
        assertTrue(r.runs[1].records.none { it.method == getter && it.direction == Direction.BACKWARD },
            "The actual ANY query stays DEMAND; persisted forward FLOW does not become a backward result")
        assertEquals(setOf(zero, JvmObservedPath(argument, tail = Tail.ANY_TAINT, mark = t),
            JvmObservedPath(argument, listOf("email"), Tail.ANY_TAINT, t)),
            r.runs[2].initials.filter { it.method == getter }.mapTo(linkedSetOf()) { it.value })
        assertContains(r.runs[2].records, JvmObservedRecord(getter, Direction.FORWARD,
            JvmObservedPath(argument, listOf("email"), Tail.ANY_TAINT, t),
            normal(result, tail = Tail.ANY_TAINT)))
        assertTrue(r.runs.all { it.requests.isEmpty() })
        assertEquals(EndReason.NO_DEMAND_EDGE, r.report.end.reason)
        assertEquals(0L, r.runs.last().demandLayerEdges,
            "The NORMAL candidate remains unproved; absence of DEMAND work stops refinement")
    }

    @Test
    fun `JVM callee sink retains a candidate after same site normal demanded premises arrive`(): Unit {
        val config = SerializedTaintConfig(entryPoint = listOf(entry("calleeSinkFlow")),
            sink = listOf(sink("sinkT", "j03-callee")))
        val r = run(config, "calleeSinkFlow")
        assertFinding(r, mapOf("j03-callee" to ReportState.DEMAND))
        assertEquals(listOf(1, 2, 3), r.runs.map { it.index })
        val use = r.method("use")
        val root = r.method("calleeSinkFlow")
        assertContains(r.runs[0].requests, JvmObservedRequest(use,
            JvmObservedPath(AccessPathBase.Argument(0), tail = Tail.STAR, mark = "*"), mark = group(r)))
        // Backward run-2 has limit 1. The array alternative reaches that limit at email.
        // Forward run-3 keeps the exact value and its cut ANY_TAINT query at the same field.
        assertEquals(setOf(zero,
            JvmObservedPath(AccessPathBase.Argument(0), tail = Tail.ANY_TAINT, mark = t),
            JvmObservedPath(AccessPathBase.Argument(0), listOf("email"), Tail.EXACT, t),
            JvmObservedPath(AccessPathBase.Argument(0), listOf("email"), Tail.ANY_TAINT, t)),
            r.runs[2].initials.filter { it.method == use }.mapTo(linkedSetOf()) { it.value })
        assertTrue(r.runs[2].links.any { it.caller == root && it.callee == use &&
            it.callerLayer == Layer.NORMAL && it.linkLayer == Layer.NORMAL && it.premise == setOf(zero) &&
            it.added == JvmObservedPath(AccessPathBase.Argument(0), tail = Tail.ANY_TAINT, mark = t) })
        assertTrue(r.runs.drop(1).all { it.requests.isEmpty() })
    }

    @Test
    fun `JVM identity keeps F1 guidance separate and refines its normal candidate through run three`(): Unit {
        for (later in listOf(2)) {
            val r = run(SerializedTaintConfig(entryPoint = listOf(entry("identityFlow")),
                sink = listOf(sink("sinkAnyT", "j03-identity", whole = true))), "identityFlow", listOf(1, 1, later))
            assertFinding(r, mapOf("j03-identity" to ReportState.DEMAND))
            assertEquals(listOf(1, 2, 3), r.runs.map { it.index })
            val identity = r.method("identity")
            val guidance = JvmObservedEdge(identity,
                setOf(JvmObservedPath(AccessPathBase.Argument(0), tail = Tail.STAR, mark = "*")),
                normal(AccessPathBase.Return, tail = Tail.STAR, mark = "*"))
            assertContains(r.runs[0].rawSummaries, guidance)
            assertContains(r.runs[0].selected, guidance)
            assertContains(r.runs[2].records, JvmObservedRecord(identity, Direction.FORWARD,
                JvmObservedPath(AccessPathBase.Argument(0), tail = Tail.ANY_TAINT, mark = t),
                normal(AccessPathBase.Return, tail = Tail.ANY_TAINT)))
            assertEquals(1, r.frontiers.first().seeds,
                "The F1 NORMAL witness remains a candidate and seeds backward refinement")
            assertEquals(EndReason.NO_DEMAND_EDGE, r.report.end.reason)
            assertNull(r.runs.last().outgoingSeeds)
            assertTrue(r.runs.all { it.requests.isEmpty() })
        }
    }

    @Test
    fun `JVM setter and one deeper wrapper retain admitted sibling and block the overwritten field`(): Unit {
        for (name in listOf("setterFlow", "deeperSetterFlow")) {
            val r = run(SerializedTaintConfig(entryPoint = listOf(entry(name)), sink = listOf(
                sink("sinkT", "j03-setter-admitted"), sink("sinkBlockedT", "j03-setter-blocked"))), name)
            assertFinding(r, mapOf("j03-setter-admitted" to ReportState.DEMAND))
            assertEquals(listOf(1, 2, 3), r.runs.map { it.index })
            assertContains(r.raw(1, r.method("setter"), AccessPathBase.Return),
                JvmObservedConclusion(JvmObservedPath(AccessPathBase.Return, tail = Tail.STAR, mark = "*"), Layer.DEMAND))
            assertContains(r.runs[2].records, JvmObservedRecord(r.method("setter"), Direction.FORWARD,
                JvmObservedPath(AccessPathBase.Argument(0), tail = Tail.ANY_TAINT, mark = t),
                normal(AccessPathBase.Return, tail = Tail.ANY_TAINT, exclusion = setOf("name"))))
            assertTrue(r.witnesses.any { it.run == 1 && it.rule.id == "j03-setter-blocked" },
                "The F1 empty-E candidate is rejected by later strong field exclusion")
            assertTrue(r.runs.all { it.requests.isEmpty() })
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
    fun `JVM zero premise factory keeps F1 empty E guidance separate from strong T and U records`(): Unit {
        val r = run(factoryConfig("factoryFlow"), "factoryFlow")
        assertFinding(r, mapOf("j03-factory-T" to ReportState.DEMAND, "j03-factory-U" to ReportState.DEMAND))
        val factory = r.method("factory")
        assertEquals(group(r, t), group(r, u), "Undeclared model marks share the catalog's SharedModel group")
        assertEquals(setOf(demand(AccessPathBase.Return, tail = Tail.ANY_TAINT, mark = group(r, t)),
            demand(AccessPathBase.Return, listOf("k"), Tail.ANY, group(r, u))),
            r.raw(1, factory, AccessPathBase.Return),
            "Run-1 merges same-group exact output into its ANY output; full T/U remain distinct in run-3")
        assertEquals(setOf(normal(AccessPathBase.Return, tail = Tail.ANY_TAINT, exclusion = setOf("name", "k")),
            normal(AccessPathBase.Return, listOf("k"), Tail.EXACT, u),
            normal(AccessPathBase.Return, listOf("k", "[e]"), Tail.EXACT, u)),
            r.runs[2].records.filter { it.method == factory && it.direction == Direction.FORWARD &&
                it.conclusion.value.base == AccessPathBase.Return }
                .mapTo(linkedSetOf()) { it.conclusion })
        assertTrue(r.runs[0].rawSummaries.filter { it.method == factory && it.conclusion.value.base == AccessPathBase.Return }
            .all { it.premise == setOf(zero) })
    }

    @Test
    fun `JVM factory strong backward records coexist with newly seeded T and U source replay`(): Unit {
        val r = run(factoryConfig("factoryReplayFlow", replay = true), "factoryReplayFlow")
        assertFinding(r, mapOf("j03-factory-T" to ReportState.DEMAND, "j03-factory-U" to ReportState.DEMAND,
            "j03-factory-may" to ReportState.DEMAND), normalRules = setOf("j03-factory-T", "j03-factory-U"))
        assertEquals(listOf(1, 2, 3), r.runs.map { it.index })
        val factory = r.method("factory")
        val returnQueries = setOf(zero) + listOf("email", "name").flatMap { name -> listOf(
            JvmObservedPath(AccessPathBase.Return, listOf(name), Tail.EXACT, t),
            JvmObservedPath(AccessPathBase.Return, listOf(name), Tail.ANY, t)) } + listOf(
            JvmObservedPath(AccessPathBase.Return, listOf("k"), Tail.EXACT, u),
            JvmObservedPath(AccessPathBase.Return, listOf("k"), Tail.ANY, u))
        assertEquals(returnQueries,
            r.runs[1].initials.filter { it.method == factory }.mapTo(linkedSetOf()) { it.value },
            "Run-2 uses full T/U queries at field limit 1; array alternatives cut at the first field")
        val hits = r.runs[1].sourceHits.filter { it.method == factory }.toSet()
        assertTrue(hits.all { it.action != null }, "Each source hit retains its exact stable source action")
        val sourcePlace = hits.filter { it.forwardTo.mark == u }.map { it.statement }.distinct().single()
        val wholePlace = hits.filter { it.forwardTo.mark == t }.map { it.statement }.distinct().single()
        assertEquals(factory.method, sourcePlace.location.method)
        assertEquals(factory.method, wholePlace.location.method)
        assertEquals("exactUSource", assertIs<JIRStaticCallExpr>(assertIs<JIRAssignInst>(sourcePlace).rhv).method.name)
        assertEquals("wholeSource", assertIs<JIRStaticCallExpr>(assertIs<JIRAssignInst>(wholePlace).rhv).method.name)
        assertEquals(setOf(
            JvmObservedSourceHit(factory, sourcePlace, zero,
                JvmObservedPath(AccessPathBase.Return, tail = Tail.EXACT, mark = u)),
            JvmObservedSourceHit(factory, sourcePlace, zero,
                JvmObservedPath(AccessPathBase.Return, listOf("[e]"), Tail.EXACT, u)),
            JvmObservedSourceHit(factory, wholePlace, zero,
                JvmObservedPath(AccessPathBase.Return, tail = Tail.ANY_TAINT, mark = t))), hits.mapTo(linkedSetOf()) { it.copy(action = null) })
        val seeds = r.runs[2].incomingSeeds.filterIsInstance<Seed.Source>().filter { it.method == factory }.toSet()
        assertEquals(3, seeds.size)
        assertEquals(mapOf<CommonInst, Int>(sourcePlace to 2, wholePlace to 1), seeds.groupingBy { it.statement }.eachCount())
        assertEquals(assertNotNull(r.runs[1].outgoingSeeds).filterIsInstance<Seed.Source>()
            .filter { it.method == factory }.toSet(), seeds,
            "F3 consumes the same full original source edges and call/context identities selected in B2")
        assertEquals(setOf(
            JvmObservedEdge(factory, setOf(zero), normal(AccessPathBase.Return, tail = Tail.ANY_TAINT,
                exclusion = setOf("name", "k"))),
            JvmObservedEdge(factory, setOf(zero), normal(AccessPathBase.Return, listOf("k"), Tail.EXACT, u)),
            JvmObservedEdge(factory, setOf(zero), normal(AccessPathBase.Return, listOf("k", "[e]"), Tail.EXACT, u))),
            r.runs[2].rawSummaries.filter { it.method == factory &&
                it.conclusion.value.base == AccessPathBase.Return }.toSet())
        assertContains(r.runs[2].records, JvmObservedRecord(factory, Direction.FORWARD, zero,
            normal(AccessPathBase.Return, tail = Tail.ANY_TAINT, exclusion = setOf("name", "k"))))
        assertContains(r.runs[1].records, JvmObservedRecord(factory, Direction.BACKWARD,
            JvmObservedPath(AccessPathBase.Return, listOf("k"), Tail.EXACT, u),
            normal(AccessPathBase.Zero, tail = Tail.EXACT, mark = "*")))
        // Only strong B2 records may replay; both original T/U source hits permit fresh F3 GEN.
        assertTrue(r.frontiers[2].recordCrossings > 0)

        // An independent may-only sink leaves the factory's T/U rules unseeded. Its F1
        // factory output cannot leak through a native/opposite record into the strong run.
        val control = run(factoryConfig("factoryReplayFlow", replay = true).copy(
            sink = listOf(sink("sinkAnyT", "j03-factory-may", whole = true))), "factoryReplayFlow")
        assertFinding(control, mapOf("j03-factory-may" to ReportState.DEMAND), normalRules = emptySet())
        assertEquals(listOf(1, 2, 3), control.runs.map { it.index })
        val controlFactory = control.method("factory")
        assertEquals(setOf(zero), control.runs[1].initials.filter { it.method == controlFactory }
            .mapTo(linkedSetOf()) { it.value })
        assertTrue(control.runs[1].sourceHits.none { it.method == controlFactory })
        assertTrue(control.runs[2].incomingSeeds.filterIsInstance<Seed.Source>().none { it.method == controlFactory })
        assertTrue(control.runs[2].rawSummaries.none { it.method == controlFactory &&
            it.conclusion.value.base == AccessPathBase.Return })
        assertTrue(control.runs[2].records.none { it.method == controlFactory &&
            it.conclusion.value.base == AccessPathBase.Return })
    }

    @Test
    fun `JVM AnyField pass keeps DEMAND while the matching source produces normal must`(): Unit {
        val commonSink = sink("sinkAnyT", "j03-source-or-pass", whole = true)
        val pass = run(SerializedTaintConfig(entryPoint = listOf(entry("passFlow", whole = false)),
            passThrough = listOf(mayPass()), sink = listOf(commonSink)), "passFlow")
        assertFinding(pass, mapOf("j03-source-or-pass" to ReportState.DEMAND), normalRules = emptySet())
        assertEquals(listOf(1, 2, 3), pass.runs.map { it.index })
        assertEquals(EndReason.POLICY, pass.report.end.reason)
        val conditional = SerializedRule.Source(functionMatcher(cls, "conditionalSource"),
            condition = SerializedCondition.ContainsMark(t, at(Argument(0))),
            taint = listOf(SerializedTaintAssignAction(t, pos = any(Result))))
        val source = run(SerializedTaintConfig(entryPoint = listOf(entry("sourceFlow", whole = false)),
            source = listOf(conditional), sink = listOf(commonSink)), "sourceFlow")
        assertFinding(source, mapOf("j03-source-or-pass" to ReportState.DEMAND))
        assertEquals(listOf(1, 2, 3), source.runs.map { it.index })
        assertTrue(source.runs[0].processed.any { it.conclusion.value.tail == Tail.ANY_TAINT &&
            it.conclusion.value.mark == group(source) && it.conclusion.layer == Layer.DEMAND })
        assertTrue(source.runs[2].processed.any { it.conclusion.value.tail == Tail.ANY_TAINT &&
            it.conclusion.value.mark == t && it.conclusion.layer == Layer.NORMAL },
            "The full conditional source yields must taint; the AnyField pass retains DEMAND")
    }

    @Test
    fun `JVM named subtree cleaner preserves email and excludes name with a no cleaner positive control`(): Unit {
        val sinks = listOf(sink("sinkT", "j03-cleaner-email"), sink("sinkBlockedT", "j03-cleaner-name"))
        val cleaner = SerializedRule.Cleaner(functionMatcher(cls, "cleanNamed"), cleans = listOf(
            SerializedTaintCleanAction(t, field(Argument(0), "name"), TaintCleanReach.ExactAndAnyField)))
        val cleaned = run(SerializedTaintConfig(entryPoint = listOf(entry("fieldCleanerFlow")),
            cleaner = listOf(cleaner), sink = sinks), "fieldCleanerFlow")
        assertFinding(cleaned, mapOf("j03-cleaner-email" to ReportState.DEMAND))
        assertTrue(cleaned.runs[0].processed.any { it.conclusion.layer == Layer.DEMAND &&
            it.conclusion.value.tail == Tail.ANY_TAINT && it.conclusion.value.exclusion.isEmpty() })
        assertTrue(cleaned.runs[2].processed.any { it.conclusion.layer == Layer.NORMAL &&
            it.conclusion.value.tail == Tail.ANY_TAINT && "name" in it.conclusion.value.exclusion })
        val control = run(SerializedTaintConfig(entryPoint = listOf(entry("fieldCleanerControl")), sink = sinks),
            "fieldCleanerControl")
        assertFinding(control, mapOf("j03-cleaner-email" to ReportState.DEMAND, "j03-cleaner-name" to ReportState.DEMAND))
    }

    @Test
    fun `JVM exact named cleaner retains the documented demand residual on a must field`(): Unit {
        val cleaner = SerializedRule.Cleaner(functionMatcher(cls, "cleanNamed"), cleans = listOf(
            SerializedTaintCleanAction(t, field(Argument(0), "name"))))
        val r = run(SerializedTaintConfig(entryPoint = listOf(entry("fieldCleanerFlow")), cleaner = listOf(cleaner),
            sink = listOf(sink("sinkT", "j03-cleaner-email"), sink("sinkBlockedT", "j03-cleaner-name"))),
            "fieldCleanerFlow")
        assertFinding(r, mapOf("j03-cleaner-email" to ReportState.DEMAND, "j03-cleaner-name" to ReportState.DEMAND),
            normalRules = setOf("j03-cleaner-email"))
        assertTrue(r.runs[0].processed.any { it.conclusion.layer == Layer.DEMAND &&
            it.conclusion.value.path.lastOrNull() == "name" && it.conclusion.value.tail == Tail.ANY_TAINT &&
            it.conclusion.value.exclusion.isEmpty() })
        assertTrue(r.runs[2].processed.any { it.conclusion.layer == Layer.DEMAND &&
            it.conclusion.value.path.lastOrNull() == "name" && it.conclusion.value.tail == Tail.ANY_TAINT })
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
        assertFinding(joined, mapOf("j03-joint" to ReportState.DEMAND))
        assertTrue(joined.report.entries.single().witnesses.all { !it.confirmed })
        val separated = run(config("jointSplitFlow"), "jointSplitFlow")
        assertFinding(separated, emptyMap())
    }

    @Test
    fun `JVM demands entry mark removal in run one and removes full argument marks in run three`(): Unit {
        for (path in listOf(emptyList(), listOf("email"))) {
            val target = if (path.isEmpty()) any(Argument(0)) else PositionBaseWithModifiers.WithModifiers(
                Argument(0), listOf(PositionModifier.Field(dto, "email", "java.lang.Object"), PositionModifier.AnyField))
            val source = SerializedRule.EntryPoint(functionMatcher(cls, "entryRemoval"),
                taint = listOf(SerializedTaintAssignAction(t, pos = target)))
            val r = run(SerializedTaintConfig(entryPoint = listOf(source),
                sink = listOf(sink("sinkT", "j03-entry-removal"))), "entryRemoval")
            assertFinding(r, mapOf("j03-entry-removal" to ReportState.DEMAND))
            val root = r.method("entryRemoval")
            assertTrue(r.runs[0].processed.any { it.method == root &&
                it.conclusion == demand(AccessPathBase.Argument(0), path, Tail.ANY_TAINT, group(r)) })
            val summaries = r.runs[0].rawSummaries.filter { it.method == root && it.premise == setOf(zero) }
            assertContains(summaries.map { it.conclusion },
                demand(AccessPathBase.Argument(0), path, Tail.ANY_TAINT, group(r)),
                "Run-1 selected-mark removal requests refinement and keeps a DEMAND group residual")
            assertContains(summaries.map { it.conclusion }, demand(AccessPathBase.Return, path, Tail.ANY_TAINT, group(r)))
            val fullSummaries = r.runs[2].rawSummaries.filter { it.method == root && it.premise == setOf(zero) }
            assertTrue(fullSummaries.filter { it.conclusion.value.base is AccessPathBase.This ||
                it.conclusion.value.base is AccessPathBase.Argument }.none { it.conclusion.value.mark == t },
                "Full-mark exit removal in run-3 removes argument taint without removing returned taint")
            assertContains(fullSummaries.map { it.conclusion }, normal(AccessPathBase.Return, path, Tail.ANY_TAINT))
        }
    }

    @Test
    fun `JVM primitive tracking AnyField byte array source has a grouped demand candidate in run one`(): Unit {
        val primitive = "J03-byte" + PrimitiveTaintExt.PRIMITIVE_TRACKING_ENABLED_MODE
        val r = run(SerializedTaintConfig(entryPoint = listOf(entry("primitiveFlow", true, primitive)),
            sink = listOf(sink("sinkByte", "j03-primitive", primitive))), "primitiveFlow")
        assertFinding(r, mapOf("j03-primitive" to ReportState.DEMAND))
        assertEquals(listOf(1, 2, 3), r.runs.map { it.index })
        assertTrue(r.runs[0].processed.any { it.conclusion.value.tail == Tail.ANY_TAINT &&
            it.conclusion.value.mark == group(r, primitive) && it.conclusion.layer == Layer.DEMAND })
    }

    @Test
    fun `JVM ordinary mark is rejected by the primitive element filter with a tracking positive control`(): Unit {
        val r = run(SerializedTaintConfig(entryPoint = listOf(entry("primitiveFlow")),
            sink = listOf(sink("sinkByte", "j03-nonprimitive", t))), "primitiveFlow")
        assertFinding(r, emptyMap())
        assertTrue(r.runs[0].processed.any { it.conclusion.value.mark == group(r) &&
            it.conclusion.value.base == AccessPathBase.Argument(0) && it.conclusion.value.tail == Tail.ANY_TAINT },
            "Root byte-array policy admits ordinary mark; only element policy removes it")
    }
}

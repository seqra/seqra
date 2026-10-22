package org.opentaint.jvm.sast.dataflow

import org.junit.jupiter.api.Test
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.ReportState
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.configuration.jvm.serialized.*
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import kotlin.test.*

/** Real prescan, JVM forms, actors and three-run handoff. Writer counts and
 * controlled arrival orders are covered by the common actor tests. */
class JIRRun1LocalStaticWriterAcceptanceTest : AnalysisTest() {
    override val sourceFileExtension = "java"
    private val cls = "test.samples.bidi.Run1LocalStaticWriterSample"
    private val full = "local-static-writer-T"
    private val rule = "local-static-writer-sink"
    private val outputPath = listOf("<static>($cls)", "output")
    private val inputPath = listOf("<static>($cls)", "input")
    private val zero = JvmObservedPath(AccessPathBase.Zero, tail = Tail.EXACT, mark = "*")
    private fun at(base: PositionBase) = PositionBaseWithModifiers.BaseOnly(base)
    private fun staticOutput() = PositionBaseWithModifiers.WithModifiers(PositionBase.ClassStatic(cls),
        listOf(PositionModifier.Field(cls, "output", "java.lang.Object")))

    private fun config(withModel: Boolean = false): SerializedTaintConfig {
        val sources = listOf(SerializedRule.Source(functionMatcher(cls, "source"),
            taint = listOf(SerializedTaintAssignAction(full, pos = at(PositionBase.Result)))))
        val model = SerializedRule.Source(functionMatcher(cls, "modelStatic"),
            condition = SerializedCondition.ContainsMark(full, at(PositionBase.Argument(0))),
            taint = listOf(SerializedTaintAssignAction(full, pos = staticOutput())))
        return SerializedTaintConfig(source = sources + if (withModel) listOf(model) else emptyList(),
            sink = listOf(SerializedRule.Sink(functionMatcher(cls, "sink"),
                condition = SerializedCondition.ContainsMark(full, at(PositionBase.Argument(0))),
                id = rule, meta = SinkMetaData(note = rule))))
    }

    private fun run(entry: String, withModel: Boolean = false) =
        runBidiAcceptance(config(withModel), cls, entry, listOf(1, 1, 2))

    private fun isField(value: JvmObservedPath, path: List<String>) =
        value.base == AccessPathBase.ClassStatic && value.path.take(path.size) == path

    private fun assertComplete(r: BidiAcceptanceResult) {
        assertEquals(RunStatus.COMPLETE, r.report.end.status)
        assertTrue(r.runs.isNotEmpty())
        assertTrue(r.runs.all { it.status == RunStatus.COMPLETE })
    }

    private fun assertPositiveHandoff(r: BidiAcceptanceResult, entry: String,
                                      staticInput: List<String>? = null) {
        assertComplete(r)
        assertEquals(listOf(1, 2, 3), r.runs.map { it.index })
        assertEquals(listOf(1, 1, 2), r.runs.map { it.fieldLimit })
        assertEquals(mapOf(rule to ReportState.DEMAND), r.report.entries.associate { it.key.rule.id to it.state })
        assertEquals(setOf(rule), r.output.mapTo(linkedSetOf()) { it.vulnerability.ruleId })
        assertTrue(r.witnesses.any { it.run == 1 && it.rule.id == rule })
        assertTrue(r.witnesses.any { it.run == 3 && it.rule.id == rule },
            "The later FULL run must retain a real sink witness, not only the run-1 candidate")

        val f1 = r.runs[0]
        val group = f1.markGroups.getValue(full)
        val entryKey = r.method(entry)
        val supplies = f1.rawSummaries.filter { edge -> edge.method == entryKey &&
            (if (staticInput == null) edge.premise == setOf(zero) else
                edge.premise.singleOrNull()?.let { p -> p.base == AccessPathBase.ClassStatic &&
                    p.path == staticInput && p.mark == group } == true) &&
            isField(edge.conclusion.value, outputPath) && edge.conclusion.value.mark == group }
        assertTrue(supplies.isNotEmpty(),
            "Actual caller composition must retain ${if (staticInput == null) "ZERO" else "input/Group"} -> output/Group")
        assertTrue(supplies.all { it.conclusion.layer == Layer.DEMAND && it in f1.selected })
        if (staticInput != null) {
            assertTrue(f1.rawSummaries.any { it.method == entryKey && it.premise == setOf(zero) &&
                isField(it.conclusion.value, staticInput) && it.conclusion.value.mark == group &&
                it.conclusion.layer == Layer.DEMAND && it in f1.selected },
                "The conditional output must join an actual ZERO-rooted input/Group supply")
        }
        assertEquals(true, f1.outgoingStaticGroupGuidance)
        assertTrue(f1.initials.none { it.value.base == AccessPathBase.ClassStatic },
            "Run-1 static inputs resume at operations, not at method entry")
        assertTrue(f1.rawSummaries.none { edge -> edge.premise == setOf(zero) &&
            edge.conclusion.value.base == AccessPathBase.ClassStatic && edge.conclusion.value.mark == "*" },
            "ZERO/FLOW is not a legal bootstrap edge")

        val b2 = r.runs[1]
        assertTrue(b2.initials.any { isField(it.value, outputPath) && it.value.mark == full },
            "Run-2 must execute actual full-mark static queries through the wrapper calls")
        assertTrue(b2.sourceHits.any { hit -> hit.action != null && hit.forwardTo.mark == full &&
            (hit.statement as? JIRInst)?.callExpr?.method?.name == "source" },
            "The backward trace must reach the actual full source action")
        assertTrue(b2.sourceHits.all { it.action != null })
        val sources = assertNotNull(b2.outgoingSeeds).filterIsInstance<Seed.Source>().toSet()
        assertTrue(sources.isNotEmpty())
        assertEquals(sources, r.runs[2].incomingSeeds.filterIsInstance<Seed.Source>().toSet(),
            "F3 consumes the exact source edges, actions, statements and contexts selected by B2")
        assertEquals(b2.sourceHits.mapTo(linkedSetOf()) { Triple(it.method, it.statement, it.action) },
            sources.mapTo(linkedSetOf()) { Triple(it.method, it.statement, it.action) })
        assertTrue(r.runs.drop(1).all { it.requests.isEmpty() })
    }

    private fun assertSetterAndGetterGuidance(r: BidiAcceptanceResult) {
        val f1 = r.runs.first()
        for (name in listOf("set", "setWrap", "setDeep")) {
            val method = r.method(name)
            val edges = f1.rawSummaries.filter { it.method == method &&
                isField(it.conclusion.value, outputPath) && it.conclusion.value.mark == "*" &&
                it.premise.any { p -> p.base == AccessPathBase.Argument(0) && p.mark == "*" } }
            assertTrue(edges.isNotEmpty(), "$name must retain its ordinary early FLOW summary")
            assertTrue(edges.all { it.conclusion.layer == Layer.DEMAND && it in f1.selected })
        }
        for (name in listOf("get", "getWrap")) {
            val method = r.method(name)
            assertTrue(f1.rawSummaries.any { it.method == method &&
                it.premise.any { p -> isField(p, outputPath) && p.mark == "*" } &&
                it.conclusion.value.base == AccessPathBase.Return && it.conclusion.value.mark == "*" &&
                it.conclusion.layer == Layer.DEMAND }, "$name must keep conditional getter guidance")
        }
    }

    @Test
    fun `JVM source argument supplies a static setter through nested writer and getter wrappers`() {
        val r = run("sourceArgument")
        assertPositiveHandoff(r, "sourceArgument")
        assertSetterAndGetterGuidance(r)
    }

    @Test
    fun `JVM static argument preserves its original and joins actual supply after setter composition`() {
        val r = run("staticArgument")
        assertPositiveHandoff(r, "staticArgument", staticInput = inputPath)
        assertSetterAndGetterGuidance(r)
        val f1 = r.runs.first()
        val group = f1.markGroups.getValue(full)
        assertTrue(f1.rawSummaries.any { it.method == r.method("staticArgument") &&
            it.premise.any { p -> isField(p, inputPath) && p.mark == group } &&
            isField(it.conclusion.value, outputPath) && it.conclusion.value.mark == group },
            "The composed conditional static result must retain input/Group as its original")
    }

    @Test
    fun `JVM two actual caller methods reuse the setter without losing source handoff`() {
        val r = run("multipleCallers")
        assertPositiveHandoff(r, "multipleCallers")
        assertSetterAndGetterGuidance(r)
        val f1 = r.runs.first()
        val group = f1.markGroups.getValue(full)
        val target = r.method("setDeep")
        val callers = f1.links.filter { it.callee == target && it.added.mark == group }
            .mapTo(linkedSetOf()) { it.caller.method.name }
        assertEquals(setOf("firstCaller", "secondCaller"), callers,
            "Both real caller ports must retain their marked ordinary input")
    }

    @Test
    fun `JVM completed conditional model action writes static output without an instruction store`() {
        val r = run("conditionalAction", withModel = true)
        assertPositiveHandoff(r, "conditionalAction")
        assertTrue(r.runs[1].sourceHits.any { hit -> hit.action != null &&
            isField(hit.forwardTo, outputPath) &&
            (hit.statement as? JIRInst)?.callExpr?.method?.name == "modelStatic" },
            "The full reversed conditional action retains its actual call and source ActionId")
    }

    @Test
    fun `JVM absent source and incomplete model condition supply no static group`() {
        for ((entry, withModel) in listOf("noSource" to false, "incompleteCondition" to true)) {
            val r = run(entry, withModel)
            assertComplete(r)
            assertTrue(r.report.entries.isEmpty(), "$entry must not fabricate a pool group")
            assertTrue(r.output.isEmpty())
            val group = r.runs.first().markGroups.getValue(full)
            assertTrue(r.runs.first().rawSummaries.none { it.premise == setOf(zero) &&
                isField(it.conclusion.value, outputPath) && it.conclusion.value.mark == group })
        }
    }
}

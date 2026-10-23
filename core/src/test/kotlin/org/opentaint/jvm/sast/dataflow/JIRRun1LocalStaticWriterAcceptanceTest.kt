package org.opentaint.jvm.sast.dataflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources
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
@ResourceLock(Resources.SYSTEM_PROPERTIES)
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

    /** These established GROUP cases select their experiment explicitly. */
    private fun run(entry: String, withModel: Boolean = false): BidiAcceptanceResult {
        val property = "opentaint.bidi.run1MarkMode"
        val previous = System.getProperty(property)
        try {
            System.setProperty(property, "GROUP")
            return runBidiAcceptance(config(withModel), cls, entry, listOf(1, 1, 2))
        } finally {
            if (previous == null) System.clearProperty(property) else System.setProperty(property, previous)
        }
    }

    private fun isField(value: JvmObservedPath, path: List<String>) =
        value.base == AccessPathBase.ClassStatic && value.path.take(path.size) == path

    private fun assertComplete(r: BidiAcceptanceResult) {
        assertEquals(RunStatus.COMPLETE, r.report.end.status)
        assertTrue(r.runs.isNotEmpty())
        assertTrue(r.runs.all { it.status == RunStatus.COMPLETE })
    }

    private fun assertPositiveHandoff(r: BidiAcceptanceResult, entry: String,
                                      staticInput: List<String>? = null,
                                      run1MarkMode: MarkMode = MarkMode.GROUP) {
        assertComplete(r)
        assertEquals(listOf(1, 2, 3), r.runs.map { it.index })
        assertEquals(listOf(1, 1, 2), r.runs.map { it.fieldLimit })
        assertEquals(mapOf(rule to ReportState.DEMAND), r.report.entries.associate { it.key.rule.id to it.state })
        assertEquals(setOf(rule), r.output.mapTo(linkedSetOf()) { it.vulnerability.ruleId })
        assertTrue(r.witnesses.any { it.run == 1 && it.rule.id == rule })
        assertTrue(r.witnesses.any { it.run == 3 && it.rule.id == rule },
            "The later FULL run must retain a real sink witness, not only the run-1 candidate")

        val f1 = r.runs[0]
        val runtimeMark = if (run1MarkMode == MarkMode.FULL) full else f1.markGroups.getValue(full)
        val entryKey = r.method(entry)
        if (staticInput == null) {
            val supplies = f1.rawSummaries.filter { edge -> edge.method == entryKey &&
                edge.premise == setOf(zero) && isField(edge.conclusion.value, outputPath) &&
                edge.conclusion.value.mark == runtimeMark }
            assertTrue(supplies.isNotEmpty(), "Actual ZERO-derived composition must retain its output supply")
            assertTrue(supplies.all { it.conclusion.layer == Layer.DEMAND && it in f1.selected })
        } else {
            assertTrue(f1.rawSummaries.any { it.method == entryKey && it.premise == setOf(zero) &&
                isField(it.conclusion.value, staticInput) && it.conclusion.value.mark == runtimeMark &&
                it.conclusion.layer == Layer.DEMAND && it in f1.selected },
                "The conditional output must join an actual ZERO-rooted marked static input supply")
            assertTrue(f1.links.any { link -> link.caller == entryKey && link.callee == r.method("setDeep") &&
                link.premise.singleOrNull()?.let { p -> isField(p, staticInput) && p.mark == runtimeMark } == true &&
                link.added.base == AccessPathBase.Argument(0) && link.added.mark == runtimeMark },
                "The real first setter binding keeps the static original and its projected marked input")
            assertTrue(r.witnesses.any { witness -> witness.run == 1 && witness.rule.id == rule &&
                witness.literals.any { literal ->
                    literal.premise.singleOrNull()?.let { p -> isField(p, outputPath) && p.mark == runtimeMark } == true &&
                        literal.facts.any { it.value.mark == runtimeMark && it.layer == Layer.DEMAND }
                } }, "The computed output supply answers the sink's saved static-origin point")
        }
        for (rows in listOf(f1.rawSummaries, f1.selected)) assertTrue(rows.none { edge ->
            edge.conclusion.value.base == AccessPathBase.ClassStatic && edge.premise.isNotEmpty() &&
                edge.premise.all { it.base == AccessPathBase.ClassStatic }
        }, "Run-1 retains no static/static row after securing its producer effects")
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
    }

    @Test
    fun `JVM default FULL run1 static argument keeps actual supply and three-run source handoff`() {
        val property = "opentaint.bidi.run1MarkMode"
        val previous = System.getProperty(property)
        try {
            System.clearProperty(property)
            val r = runBidiAcceptance(config(), cls, "staticArgument", listOf(1, 1, 2))
            assertPositiveHandoff(r, "staticArgument", staticInput = inputPath, run1MarkMode = MarkMode.FULL)
            assertSetterAndGetterGuidance(r)
            assertEquals(MarkMode.FULL, r.report.end.run1MarkMode)
            val groupedNames = r.runs.first().markGroups.values.toSet()
            assertTrue(r.runs.first().rawSummaries.none { edge ->
                edge.conclusion.value.mark in groupedNames || edge.premise.any { it.mark in groupedNames }
            }, "FULL mode cannot project actual marked summaries to their owner group")
        } finally {
            if (previous == null) System.clearProperty(property) else System.setProperty(property, previous)
        }
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

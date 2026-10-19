package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.bidi.driver.FixedLimits
import org.opentaint.dataflow.bidi.driver.IterationDriver
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.interp.CallStage
import org.opentaint.dataflow.bidi.interp.StageKind
import org.opentaint.dataflow.bidi.engine.Run1Slot
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.jvm.bidi.interp.StaticMarkRole
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRPrescanFixtures
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRRuleTestFixtures
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import org.opentaint.dataflow.jvm.bidi.interp.JIRStaticMetadataCollector
import org.opentaint.dataflow.jvm.bidi.interp.JIRBoundaryForms
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Historical metadata laws plus the current production immutable-declaration seam. */
class JIRStaticMetadataFallbackTest : BidiJIRTestUtils() {
    private val owner = "sample.bidi.BidiStaticMetadataSample"
    private val fieldOwner = "sample.bidi.BidiStatementSample"
    private val staticPosition = PositionWithAccess(ClassStatic(fieldOwner),
        PositionAccessor.FieldAccessor(fieldOwner, "sField", "java.lang.Object"))

    private fun rules(): TaintRulesProvider {
        val m = method("aliasRule", owner)
        val target = assertNotNull(m.instList.instructions.single { it.callExpr != null }.callExpr).method.method
        val v = JIRRuleTestFixtures(cp)
        val unknown: Condition = CommonCondition.Atom(ConstantMatches(This, Regex("wanted")))
        fun marked(name: String): Condition = CommonCondition.Atom(ContainsMark(staticPosition, TaintMark(name)))
        fun condition(name: String, branch: Condition): Condition = CommonCondition.And(listOf(marked(name), branch))
        val branches: List<Pair<String, Condition>> = listOf("POS" to unknown, "NEG" to CommonCondition.Not(unknown),
            "CONTRADICTION" to CommonCondition.And(listOf(unknown, CommonCondition.Not(unknown))),
            "FALSE" to CommonCondition.Atom(IsConstant(This)))
        return object : TaintRulesProvider by NoTaintRules {
            override val markDeclarations = org.opentaint.dataflow.bidi.ap.RawMarkDeclarations(
                setOf("OUT") + branches.flatMap { (name, _) -> listOf("SOURCE_$name", "SINK_$name") },
                emptyMap(), emptyMap(),
            )
            override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) branches.map { (name, branch) ->
                    v.source(listOf(AssignMark(TaintMark("OUT"), Result)), condition("SOURCE_$name", branch), target)
                } else emptyList()
            override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == target) branches.map { (name, branch) -> v.sink(condition = condition("SINK_$name", branch), method = target) }
                else emptyList()
        }
    }

    @Test
    fun `absent alias owner with enabled runtime AA preserves both signed source and sink potentials`(): Unit {
        val runtime = interp(rules(), JIRLocalAliasAnalysis.Params(useAliasAnalysis = true))
        val m = method("aliasRule", owner)
        assertFalse(assertNotNull(runtime.entries[key(m)].aliasAnalysis).hasComputedAliasInfo())
        val metadata = JIRStaticMetadataCollector(runtime).collect(key(m))
        assertEquals(setOf("SOURCE_POS", "SOURCE_NEG", "SINK_POS", "SINK_NEG"), metadata.marks.map { it.mark }.toSet())
        assertFalse(assertNotNull(runtime.entries[key(m)].aliasAnalysis).hasComputedAliasInfo(),
            "Metadata must not initialize runtime AA either")
    }

    @Test
    fun `potential metadata effects do not make a runtime source or sink unconditional`(): Unit {
        val m = method("aliasRule", owner)
        val at = m.instList.instructions.single { it.callExpr != null }
        val call = assertNotNull(at.callExpr)
        val condition: Condition = CommonCondition.Or(listOf(
            CommonCondition.Atom(ConstantMatches(This, Regex("wanted"))),
            CommonCondition.Atom(ContainsMark(staticPosition, TaintMark("P")))))
        val v = JIRRuleTestFixtures(cp)
        val source = v.source(listOf(AssignMark(TaintMark("OUT"), ClassStatic("coverage.SourcePoint"))), condition, call.method.method)
        val sink = v.sink(listOf(AssignMark(TaintMark("END"), ClassStatic("coverage.SinkPoint"))), condition, call.method.method)
        val rules = object : TaintRulesProvider by NoTaintRules {
            override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == call.method.method) listOf(source) else emptyList()
            override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                if (method == call.method.method) listOf(sink) else emptyList()
        }
        val runtime = interp(rules, JIRLocalAliasAnalysis.Params(useAliasAnalysis = true))
        val metadata = JIRStaticMetadataCollector(runtime).collect(key(m))
        assertTrue(org.opentaint.dataflow.ap.ifds.ClassStaticAccessor("coverage.SourcePoint") in metadata.classPoints)
        assertFalse(org.opentaint.dataflow.ap.ifds.ClassStaticAccessor("coverage.SinkPoint") in metadata.classPoints,
            "Ignored sink end actions have no metadata effect")
        val checkedPath = listOf(org.opentaint.dataflow.ap.ifds.ClassStaticAccessor(fieldOwner),
            org.opentaint.dataflow.ap.ifds.FieldAccessor(fieldOwner, "sField", "java.lang.Object"))
        assertTrue(checkedPath in metadata.accesses)
        assertFalse(checkedPath in metadata.abstractions, "ContainsMark contributes B, not a read-field gate")
        assertEquals(setOf(StaticMarkRole.SOURCE, StaticMarkRole.SINK), metadata.marks
            .filter { it.path == checkedPath && it.tail == Tail.EXACT && it.mark == "P" }.map { it.role }.toSet())
        assertFalse(assertNotNull(runtime.entries[key(m)].aliasAnalysis).hasComputedAliasInfo())
        val plan = runtime.forms(key(m)).call(Direction.FORWARD, at, call)
        val sources = plan.stages.filterIsInstance<CallStage.Edges>().single { it.kind == StageKind.SOURCES }.summary
        assertTrue(sources.edges.none { it.isSource }, "False runtime atom leaves P, not an unconditional GEN")
        assertEquals(1, sources.sources.size)
        assertFalse(sources.sources.single().predicate.isTrue)
        assertFalse(plan.sinks.single().unconditional)
        assertEquals(setOf("P"), sources.sources.single().predicate.leaves.map {
            runtime.manager.marks.name((it.fact.mark as org.opentaint.dataflow.bidi.ap.MarkSlot.Concrete).mark)
        }.toSet())
    }

    @Test
    fun `symbolic alias alternatives retain only live positive source and sink mark needs`(): Unit {
        val m = method("aliasRule", owner)
        val target = assertNotNull(m.instList.instructions.single { it.callExpr != null }.callExpr).method.method
        val u: Condition = CommonCondition.Atom(ConstantMatches(This, Regex("wanted")))
        val p: Condition = CommonCondition.Atom(ContainsMark(staticPosition, TaintMark("P")))
        val q: Condition = CommonCondition.Atom(ContainsMark(staticPosition, TaintMark("Q")))
        val knownFalse: Condition = CommonCondition.Atom(IsConstant(This))
        val cases: List<Pair<Condition, Set<String>>> = listOf(
            CommonCondition.Or(listOf(u, p)) to setOf("P"),
            CommonCondition.Or(listOf(CommonCondition.Not(u), p)) to setOf("P"),
            CommonCondition.And(listOf(u, p)) to setOf("P"),
            CommonCondition.And(listOf(CommonCondition.Not(u), p)) to setOf("P"),
            CommonCondition.Or(listOf(CommonCondition.Not(knownFalse), p)) to emptySet(),
            CommonCondition.And(listOf(knownFalse, p)) to emptySet(),
            CommonCondition.Or(listOf(u, CommonCondition.And(listOf(p, CommonCondition.Not(p))))) to emptySet(),
            CommonCondition.And(listOf(u, CommonCondition.Not(u), p)) to emptySet(),
            CommonCondition.Or(listOf(u, CommonCondition.And(listOf(CommonCondition.Not(q), p)))) to setOf("P"),
            CommonCondition.Or(listOf(CommonCondition.Not(u), CommonCondition.And(listOf(CommonCondition.Not(p), q)))) to setOf("Q"))
        val checkedPath = listOf(org.opentaint.dataflow.ap.ifds.ClassStaticAccessor(fieldOwner),
            org.opentaint.dataflow.ap.ifds.FieldAccessor(fieldOwner, "sField", "java.lang.Object"))
        val v = JIRRuleTestFixtures(cp)
        for ((condition, expected) in cases) {
            val source = v.source(listOf(AssignMark(TaintMark("OUT"), ClassStatic("coverage.SymbolicSourcePoint"))), condition, target)
            val sink = v.sink(condition = condition, method = target)
            val rules = object : TaintRulesProvider by NoTaintRules {
                override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                    if (method == target) listOf(source) else emptyList()
                override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                    if (method == target) listOf(sink) else emptyList()
            }
            val runtime = interp(rules, JIRLocalAliasAnalysis.Params(useAliasAnalysis = true))
            val metadata = JIRStaticMetadataCollector(runtime).collect(key(m))
            for (role in listOf(StaticMarkRole.SOURCE, StaticMarkRole.SINK))
                assertEquals(expected, metadata.marks.filter { it.role == role && it.path == checkedPath && it.tail == Tail.EXACT }
                    .map { it.mark }.toSet(), "$condition $role")
            assertEquals(expected.isNotEmpty(), checkedPath in metadata.accesses, "$condition static ownership")
        }
    }

    @Test
    fun `empty-action call source adds no interest while an empty-end sink still reads its mark`(): Unit {
        val m = method("aliasRule", owner)
        val target = assertNotNull(m.instList.instructions.single { it.callExpr != null }.callExpr).method.method
        val condition: Condition = CommonCondition.Or(listOf(
            CommonCondition.Atom(ConstantMatches(This, Regex("wanted"))),
            CommonCondition.Atom(ContainsMark(staticPosition, TaintMark("P")))))
        val v = JIRRuleTestFixtures(cp)
        val source = v.source(emptyList(), condition, target)
        val sink = v.sink(condition = condition, method = target)
        val checkedPath = listOf(org.opentaint.dataflow.ap.ifds.ClassStaticAccessor(fieldOwner),
            org.opentaint.dataflow.ap.ifds.FieldAccessor(fieldOwner, "sField", "java.lang.Object"))
        for (includeSink in listOf(false, true)) {
            val rules = object : TaintRulesProvider by NoTaintRules {
                override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                    if (method == target) listOf(source) else emptyList()
                override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                    if (includeSink && method == target) listOf(sink) else emptyList()
            }
            val runtime = interp(rules, JIRLocalAliasAnalysis.Params(useAliasAnalysis = true))
            val metadata = JIRStaticMetadataCollector(runtime).collect(key(m))
            assertEquals(if (includeSink) setOf(StaticMarkRole.SINK) else emptySet(),
                metadata.marks.filter { it.path == checkedPath && it.mark == "P" }.map { it.role }.toSet())
            assertEquals(includeSink, checkedPath in metadata.accesses)
        }
    }

    @Test
    fun `actual entry false static atom leaves signed mark conditions and admits neither GEN nor sink`(): Unit {
        val m = method("copy")
        val falseAtom: Condition = CommonCondition.Atom(ConstantMatches(Argument(0), Regex("wanted")))
        val mark: Condition = CommonCondition.Atom(ContainsMark(staticPosition, TaintMark("ENTRY_P")))
        for (signedMark in listOf(mark, CommonCondition.Not(mark))) {
            val condition: Condition = CommonCondition.Or(listOf(falseAtom, signedMark))
            val source = TaintEntryPointSource(m, condition,
                listOf(AssignMark(TaintMark("OUT"), ClassStatic("coverage.EntrySourcePoint"))), null)
            val ordinarySink = JIRRuleTestFixtures(cp).sink(condition = condition, method = m)
            val sink = TaintMethodEntrySink(m, condition,
                listOf(AssignMark(TaintMark("END"), ClassStatic("coverage.EntrySinkPoint"))),
                ordinarySink.id, ordinarySink.meta, null)
            val rules = object : TaintRulesProvider by NoTaintRules {
                override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                    if (method == m) listOf(source) else emptyList()
                override fun sinkRulesForMethodEntry(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                    if (method == m) listOf(sink) else emptyList()
            }
            val runtime = interp(rules, JIRLocalAliasAnalysis.Params(useAliasAnalysis = true))
            val key = key(m)
            val metadata = JIRStaticMetadataCollector(runtime).collect(key)
            assertTrue(metadata.classPoints.none { it == org.opentaint.dataflow.ap.ifds.ClassStaticAccessor("coverage.EntrySourcePoint") ||
                it == org.opentaint.dataflow.ap.ifds.ClassStaticAccessor("coverage.EntrySinkPoint") })
            val actual = JIRBoundaryForms.entryRules(runtime, runtime.entries[key], key)
            assertTrue(actual.summary.edges.none { it.isSource })
            assertTrue(actual.sinks.isEmpty())
            assertTrue(actual.endFacts.edges.none { it.isSource })
            assertFalse(assertNotNull(runtime.entries[key].aliasAnalysis).hasComputedAliasInfo(),
                "Callee argument is a JIRArgument, not an alias-sensitive local")
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `actual gather uses declarations with no retained prescan context`(): Unit {
        val m = method("aliasRule", owner)
        val rules = rules()
        JIRPrescanFixtures(cp, graph, rules, aliases = true).use { f ->
            val roots = listOf(MethodWithContext(m, EmptyMethodContext))
            f.engine.runAnalysis(roots, 5.seconds, 5.seconds)
            assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, f.engine.status.get())
            f.engine.requirePrescanJoined()
            val resolved = f.engine.prescanResolvedGraph()
            assertTrue(m in resolved.methods)
            assertTrue(f.contexts().any { it.methodEntryPoint.method == m })
            // Complete provider declarations need no retained per-method context.
            val contexts = JIRAnalysisManager::class.java.getDeclaredField("contexts")
                .also { it.isAccessible = true }.get(f.manager) as MutableCollection<JIRMethodAnalysisContext>
            contexts.removeAll { it.methodEntryPoint.method == m }
            assertTrue(f.contexts().none { it.methodEntryPoint.method == m })
            var inspected = false
            val bidi = JIRBidiAnalysis(cp, graph, f.units, rules, f.params, FixedLimits(listOf(1, 1, 2)), f.refManager,
                driverFactory = { policy, shared, remaining ->
                    for (name in listOf("SOURCE_POS", "SOURCE_NEG", "SINK_POS", "SINK_NEG")) {
                        val full = shared.ap.marks.mark(name)
                        assertEquals(name, shared.ap.marks.name(full))
                    }
                    val staticOrigin = shared.ap.initial(org.opentaint.dataflow.bidi.ap.STATIC,
                        shared.ap.path(listOf(shared.ap.accessors.index(org.opentaint.dataflow.ap.ifds.ClassStaticAccessor(fieldOwner)),
                            shared.ap.accessors.index(org.opentaint.dataflow.ap.ifds.FieldAccessor(fieldOwner, "sField", "java.lang.Object")))),
                        Tail.STAR, org.opentaint.dataflow.bidi.ap.ExclusionSet.Empty,
                        org.opentaint.dataflow.bidi.ap.MarkSlot.STAR)
                    assertNull(Run1Slot.fromInitial(staticOrigin), "A static origin never enters ordinary request storage")
                    inspected = true
                    IterationDriver(policy, shared, remaining)
                })
            bidi.gather(f.manager, roots, resolved)
            f.manager.releasePrescan()
            f.engine.releasePrescan()
            assertEquals(RunStatus.TIMEOUT, bidi.run(Duration.ZERO).end.status)
            assertTrue(inspected, "The actual prepareShared path must consume the frozen declarations")
        }
    }
}

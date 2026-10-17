package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

class Run1AbstractionTest {
    private fun path(f: MethodAnalysisFixtures, field: String): List<Int> =
        listOf(f.manager.accessors.index(ClassStaticAccessor("Box")), f.field(field))
    private fun fixture(marks: Boolean = false, empty: Boolean = false, reads: Boolean = true): MethodAnalysisFixtures =
        MethodAnalysisFixtures(staticProfiles = { f ->
            val fp = path(f, "f")
            val gp = path(f, "g")
            val interest = f.pattern(STATIC, fp, Tail.ANY, MarkSlot.Concrete(f.mark("T")))
            Run1StaticProfiles.build(f.manager, mapOf(f.key.method to emptyList()), mapOf(f.key.method to
                if (empty) Run1StaticLocalProfile.EMPTY else Run1StaticLocalProfile(
                    accesses = listOf(fp, gp), abstractions = if (reads) listOf(fp, gp) else emptyList(),
                    marks = if (marks) listOf(StaticMarkInterest(interest, StaticMarkRole.SINK,
                        RequestMarkSet.of(f.mark("T")))) else emptyList())),
                declaredFieldUniverse = setOf(Run1Slot.StaticField(fp[0], fp[1]), Run1Slot.StaticField(gp[0], gp[1])))
        })
    private fun actor(f: MethodAnalysisFixtures): RunMethodAnalyzer =
        f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
    private fun abstract(f: MethodAnalysisFixtures, p: List<Int> = emptyList()): InitialAp =
        f.manager.initial(STATIC, f.manager.path(p), Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
    private fun caller(f: MethodAnalysisFixtures, p: List<Int>? = null, context: String = "supplier"): CallerRef =
        CallerRef(f.store.key(context), p?.let { abstract(f, it) } ?: f.manager.zero, Layer.NORMAL, f.inst(1))
    private fun barrier(f: MethodAnalysisFixtures, core: RunMethodAnalyzer) = f.flushRequests(core, 2)
    private fun answers(f: MethodAnalysisFixtures, core: RunMethodAnalyzer): Set<InitialAp> =
        initials(f, core).filter { it.mark is MarkSlot.Concrete && !it.isZero }.toSet()

    private fun initials(f: MethodAnalysisFixtures, core: RunMethodAnalyzer): Set<InitialAp> = buildSet {
        addAll(core.initials.all().toList())
        core.staticEntryFacts()?.let { tree -> f.ops.leaves(tree).forEach { add(f.manager.initial(it)) } }
        core.concreteStaticEntryFacts()?.let { tree -> f.ops.leaves(tree).forEach { leaf ->
            add(f.manager.initial(leaf.copy(fact = leaf.fact.copy(
                tail = if (leaf.fact.tail == Tail.EXACT) Tail.EXACT else Tail.STAR))))
        } }
    }

    @Test
    fun `empty reader metadata retains supplied entry and stores an ordinary DEMAND default`(): Unit {
        fixture(empty = true).use { f ->
            val core = actor(f)
            f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, path(f, "g")))
            barrier(f, core)
            val concrete = f.manager.initial(STATIC, f.manager.path(path(f, "g")), Tail.EXACT,
                ExclusionSet.Empty, MarkSlot.Concrete(f.mark("T")))
            assertEquals(setOf(abstract(f, path(f, "g")), concrete), initials(f, core))
            assertEquals(Layer.DEMAND, assertNotNull(core.concreteStaticEntryFacts()).layer)
            val default = core.summaries.all().single { (premise, _) ->
                premise is InitialAp && isSpecialStaticRoot(premise)
            }
            assertEquals(Layer.DEMAND, default.second.layer)
            assertEquals(Tail.STAR, f.ops.leaves(default.second).single().fact.tail)
            assertTrue(f.events.filterIsInstance<RunEvent.MarkRequestsIn>().isEmpty())
        }
    }

    @Test
    fun `broad supplier projects legal supplied fields and never starts an abstract class`(): Unit {
        fixture().use { f ->
            val core = actor(f)
            f.admit(core, caller(f, path(f, "f")), Layer.NORMAL,
                f.fact(STATIC, tail = Tail.STAR, mark = MarkSlot.STAR))
            val fp = path(f, "f")
            val gp = path(f, "g")
            assertEquals(setOf(abstract(f, fp), abstract(f, gp)), initials(f, core).toSet())
            f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, gp))
            barrier(f, core)
            val actualT = f.manager.initial(STATIC, f.manager.path(gp), Tail.EXACT,
                ExclusionSet.Empty, MarkSlot.Concrete(f.mark("T")))
            assertEquals(setOf(abstract(f, fp), abstract(f, gp), actualT), initials(f, core).toSet())
            assertEquals(setOf(actualT), answers(f, core))
            assertTrue(f.events.filterIsInstance<RunEvent.MarkRequestsIn>().isEmpty())
        }
    }

    @Test
    fun `actual static admission ignores reader placement and mark-interest metadata`(): Unit {
        for (read in listOf(false, true)) for (mark in listOf(false, true)) {
            fixture(marks = mark, reads = read).use { f ->
                val core = actor(f)
                val fp = path(f, "f")
                val t = f.mark("T")
                f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, fp, mark = MarkSlot.Concrete(t)))
                barrier(f, core)
                val initials = initials(f, core).toSet()
                assertContains(initials, abstract(f, fp), "A supplied field is admitted independently of local reads")
                assertTrue(initials.any { it.mark == MarkSlot.Concrete(t) })
                assertTrue(initials.none { it.base == STATIC && it.path == null })
                assertTrue(initials.all { it.exclusion == ExclusionSet.Empty })
            }
        }
    }

    @Test
    fun `actual unrequested marks start immediately and duplicate callers do not restart the same initial`(): Unit {
        fixture(marks = true).use { f ->
            val core = actor(f)
            val fp = path(f, "f")
            val t = f.mark("T")
            val u = f.mark("U")
            f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, fp, mark = MarkSlot.Concrete(u)))
            barrier(f, core)
            val suppliedU = f.manager.initial(STATIC, f.manager.path(fp), Tail.EXACT,
                ExclusionSet.Empty, MarkSlot.Concrete(u))
            assertEquals(setOf(suppliedU), answers(f, core))
            val abstracts = initials(f, core).toSet()
            f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, fp, mark = MarkSlot.Concrete(t)))
            val expected = f.manager.initial(STATIC, f.manager.path(fp), Tail.EXACT,
                ExclusionSet.Empty, MarkSlot.Concrete(t))
            assertEquals(setOf(suppliedU, expected), answers(f, core), "Every actual mark starts without static B/R membership")
            barrier(f, core)
            assertEquals(abstracts + expected, initials(f, core).toSet())
            val deltas = core.edgeDeltas
            f.admit(core, caller(f, context = "another caller"), Layer.NORMAL,
                f.fact(STATIC, fp, mark = MarkSlot.Concrete(t)))
            barrier(f, core)
            assertEquals(deltas, core.edgeDeltas, "An already admitted answer does not restart analysis")
            assertEquals(2, f.links(core).asSequence().count { it.addedFact.fact.mark == MarkSlot.Concrete(t) })
            assertTrue(f.events.filterIsInstance<RunEvent.MarkRequestsIn>().isEmpty())
        }
    }

    @Test
    fun `source needs and actual static suppliers are independent in both arrival orders`(): Unit {
        fixture().use { f ->
            val core = actor(f)
            val gp = path(f, "g")
            val origin = abstract(f, gp)
            val t = f.mark("T")
            val u = f.mark("U")
            f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, gp, mark = MarkSlot.Concrete(t)))
            barrier(f, core)
            core.addMarkRequests(origin, RequestMarkSet.fromIds(intArrayOf(t.id, u.id)))
            barrier(f, core)
            assertEquals(setOf(t), answers(f, core).map { (it.mark as MarkSlot.Concrete).mark }.toSet())
            assertContains(initials(f, core), origin)
            f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, gp, mark = MarkSlot.Concrete(u)))
            barrier(f, core)
            assertEquals(setOf(t, u), answers(f, core).map { (it.mark as MarkSlot.Concrete).mark }.toSet())
            assertTrue(answers(f, core).all { it.tail == Tail.EXACT })
            assertContains(initials(f, core), origin)
        }
    }

    @Test
    fun `mark templates create no static request wave and actual late facts start directly`(): Unit {
        fixture(marks = true).use { f ->
            val core = actor(f)
            val fp = path(f, "f")
            f.admit(core, caller(f, fp), Layer.NORMAL,
                f.fact(STATIC, fp, Tail.STAR, MarkSlot.STAR))
            barrier(f, core)
            assertTrue(f.events.filterIsInstance<RunEvent.MarkRequestsIn>().isEmpty(), "No local operation was configured to produce a need")
            assertTrue(answers(f, core).isEmpty())
            f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, fp))
            barrier(f, core)
            assertEquals(1, answers(f, core).size)
        }
    }

    @Test
    fun `copied static fact requests its original field and obtains a late supplied witness`(): Unit {
        fixture(marks = true).use { f ->
            val gp = path(f, "g")
            val fp = path(f, "f")
            val sink = SinkRule(object : CommonTaintConfigurationSink {
                override val id = "copied static"
                override val meta = object : CommonTaintConfigurationSinkMeta {
                    override val message = id
                    override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
                }
            }, 0, listOf(f.pattern(STATIC, fp)), emptyList())
            val copy = PathEdge(PathFact(STATIC, gp, Tail.STAR, MarkSlot.STAR),
                PathFact(STATIC, fp, Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
            f.statement(0, StatementSummary(setOf(STATIC), listOf(MicroEdge.of(copy)), emptyList(), emptyMap()))
            f.exitRules(2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
                listOf(sink)), false, emptySet()))
            val core = actor(f)
            val ref = caller(f, gp)
            f.admit(core, ref, Layer.NORMAL, f.fact(STATIC, gp, Tail.STAR, MarkSlot.STAR))
            f.drain(core)
            assertTrue(f.events.filterIsInstance<RunEvent.MarkRequestsIn>().isEmpty(),
                "Static-origin interest is not an ordinary static R/P delivery")
            assertEquals(RequestMarkSet.of(f.mark("T")),
                f.requestRow(core, Run1Slot.StaticField(gp[0], gp[1])).toRequestMarkSet {})
            assertTrue(f.vulnerabilities.witnessesOf(1).none(), "An abstract check supplies no predicate proof")
            f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, gp))
            f.drain(core)
            val witness = f.vulnerabilities.witnessesOf(1).single().second
            assertEquals(gp, (witness.edges.single().premise as InitialAp).path?.toList())
            assertEquals(MarkSlot.Concrete(f.mark("T")), (witness.edges.single().premise as InitialAp).mark)
            assertEquals(fp, f.ops.leaves(witness.edges.single().facts).single().fact.path)
            assertContains(initials(f, core), abstract(f, gp))
        }
    }

    @Test
    fun `emitter retains no answer memo and joined release detaches the emitter`(): Unit {
        fixture().use { f ->
            val core = actor(f)
            val gp = path(f, "g")
            val ref = caller(f)
            val input = f.fact(STATIC, gp)
            f.admit(core, ref, Layer.NORMAL, input)
            val emitter = assertNotNull(core.abstraction)
            assertEquals(0L, emitter.pendingAnswerShapeCount)
            assertEquals(0L, emitter.pendingAnswerMarkCount)
            core.addMarkRequests(abstract(f, gp), RequestMarkSet.of(f.mark("T")))
            barrier(f, core)
            assertEquals(1, answers(f, core).size)
            core.releaseAbstraction()
            assertNull(core.abstraction)
            assertFailsWith<IllegalStateException> { emitter.emit(ref, input) }
        }
    }
}

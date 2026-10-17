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
    private fun answers(core: RunMethodAnalyzer): Set<InitialAp> =
        core.initials.all().filter { it.mark is MarkSlot.Concrete }.toSet()

    private fun initials(f: MethodAnalysisFixtures, core: RunMethodAnalyzer): Set<InitialAp> = buildSet {
        addAll(core.initials.all().toList())
        core.staticEntryFacts()?.let { tree -> f.ops.leaves(tree).forEach { add(f.manager.initial(it)) } }
    }

    @Test
    fun `empty reader metadata retains supplied entry and stores an ordinary DEMAND default`(): Unit {
        fixture(empty = true).use { f ->
            val core = actor(f)
            f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, path(f, "g")))
            barrier(f, core)
            assertEquals(setOf(abstract(f, path(f, "g"))), initials(f, core))
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
            assertEquals(setOf(abstract(f, fp), abstract(f, gp)), initials(f, core).toSet())
            assertTrue(answers(core).isEmpty())
            assertTrue(f.events.filterIsInstance<RunEvent.MarkRequestsIn>().isEmpty())
        }
    }

    @Test
    fun `entry bindings ignore reader placement while concrete answers require a mark request`(): Unit {
        for (read in listOf(false, true)) for (mark in listOf(false, true)) {
            fixture(marks = mark, reads = read).use { f ->
                val core = actor(f)
                val fp = path(f, "f")
                val t = f.mark("T")
                f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, fp, mark = MarkSlot.Concrete(t)))
                barrier(f, core)
                val initials = initials(f, core).toSet()
                assertContains(initials, abstract(f, fp), "A supplied field is admitted independently of local reads")
                assertEquals(mark, initials.any { it.mark == MarkSlot.Concrete(t) })
                assertTrue(initials.none { it.base == STATIC && it.path == null })
                assertTrue(initials.all { it.exclusion == ExclusionSet.Empty })
            }
        }
    }

    @Test
    fun `precomputed interest adds only supplied requested marks and keeps each abstract initial`(): Unit {
        fixture(marks = true).use { f ->
            val core = actor(f)
            val fp = path(f, "f")
            val t = f.mark("T")
            val u = f.mark("U")
            f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, fp, mark = MarkSlot.Concrete(u)))
            barrier(f, core)
            assertTrue(answers(core).isEmpty())
            val abstracts = initials(f, core).toSet()
            f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, fp, mark = MarkSlot.Concrete(t)))
            val expected = f.manager.initial(STATIC, f.manager.path(fp), Tail.EXACT,
                ExclusionSet.Empty, MarkSlot.Concrete(t))
            assertEquals(setOf(expected), answers(core), "A new concrete supplier reads already installed B immediately")
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
    fun `dynamic marks are additive separate alternatives and process existing and late suppliers`(): Unit {
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
            assertEquals(setOf(t), answers(core).map { (it.mark as MarkSlot.Concrete).mark }.toSet())
            assertContains(initials(f, core), origin)
            f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, gp, mark = MarkSlot.Concrete(u)))
            barrier(f, core)
            assertEquals(setOf(t, u), answers(core).map { (it.mark as MarkSlot.Concrete).mark }.toSet())
            assertTrue(answers(core).all { it.tail == Tail.EXACT })
            assertContains(initials(f, core), origin)
        }
    }

    @Test
    fun `catalog covered targets produce no empty delivery and retain interest for late facts`(): Unit {
        fixture(marks = true).use { f ->
            val core = actor(f)
            val fp = path(f, "f")
            f.admit(core, caller(f, fp), Layer.NORMAL,
                f.fact(STATIC, fp, Tail.STAR, MarkSlot.STAR))
            barrier(f, core)
            assertTrue(f.events.filterIsInstance<RunEvent.MarkRequestsIn>().isEmpty(), "The exact original abstraction is covered by its installed catalog")
            assertTrue(answers(core).isEmpty())
            f.admit(core, caller(f), Layer.NORMAL, f.fact(STATIC, fp))
            barrier(f, core)
            assertEquals(1, answers(core).size)
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
            val request = f.events.filterIsInstance<RunEvent.MarkRequestsIn>().single()
            assertEquals(ref.caller, request.method)
            assertEquals(mapOf<Run1Slot, RequestMarkSet>(Run1Slot.StaticField(gp[0], gp[1]) to RequestMarkSet.of(f.mark("T"))), request.rows)
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
            assertEquals(1, answers(core).size)
            core.releaseAbstraction()
            assertNull(core.abstraction)
            assertFailsWith<IllegalStateException> { emitter.emit(ref, input) }
        }
    }
}

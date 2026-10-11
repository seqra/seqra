package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.RequestKind
import kotlin.test.*

class EngineAlgebraTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val z = AccessPathBase.Argument(1)

    @Test fun `all three modes use the real AP transfer while only statement passes an untouched fact`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val core = f.core()
            val field = f.field("f")
            val mt = MarkSlot.Concrete(f.mark("T"))
            val me = MicroEdge.of(PathEdge(PathFact(x, emptyList(), Tail.STAR, MarkSlot.STAR),
                PathFact(y, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty))
            val summary = StatementSummary(setOf(x), listOf(me), emptyList(), emptyMap())
            val input = f.fact(x, listOf(field), mark = mt)
            val premise = f.manager.initial(f.pattern(x, listOf(field), mark = mt))
            val expected = Conclusion(PathFact(y, listOf(field), Tail.EXACT, mt), ExclusionSet.Empty, false)
            for (mode in MethodFormMode.entries) {
                val output = applyMethodForm(core, mode, summary, premise, input, Place(f.inst(1), true, true))
                assertEquals(setOf(expected), methodLeaves(f.ops, output.map { it.second }))
                assertTrue(output.all { it.first === premise })
            }
            val untouched = f.fact(z, listOf(field), mark = mt)
            val otherPremise = f.manager.initial(f.pattern(z, listOf(field), mark = mt))
            for (mode in MethodFormMode.entries) {
                val output = applyMethodForm(core, mode, summary, otherPremise, untouched, Place(f.inst(1), true, true))
                if (mode == MethodFormMode.STATEMENT) {
                    assertSame(untouched, output.single().second)
                    assertSame(otherPremise, output.single().first)
                } else assertTrue(output.isEmpty())
            }
            val killed = StatementSummary(setOf(x), emptyList(), emptyList(), emptyMap())
            assertTrue(applyMethodForm(core, MethodFormMode.STATEMENT, killed, premise, input,
                Place(f.inst(1), true, true)).isEmpty())
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun `FLOW mark exclusions and normal must field exclusions survive ordinary mode application`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val core = f.core(index = 3)
            val g = f.field("g")
            val h = f.field("h")
            val t = f.mark("T")
            val u = f.mark("U")
            val me = MicroEdge.of(PathEdge(PathFact(x, emptyList(), Tail.STAR, MarkSlot.STAR),
                PathFact(y, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.of(intArrayOf(h))))
            val summary = StatementSummary(setOf(x), listOf(me), emptyList(), emptyMap())
            val mx = MarkSlot.Star(MarkSet(intArrayOf(u.id)))
            val inputs = listOf(
                f.fact(x, tail = Tail.STAR, mark = mx, exclusion = ExclusionSet.of(intArrayOf(g))) to
                    f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)),
                f.fact(x, tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t), exclusion = ExclusionSet.of(intArrayOf(g))) to
                    f.manager.initial(f.pattern(x, tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t),
                        exclusion = ExclusionSet.of(intArrayOf(g)))),
            )
            for ((input, premise) in inputs) for (mode in MethodFormMode.entries) {
                val output = applyMethodForm(core, mode, summary, premise, input, Place(f.inst(1), true, true))
                val mark = if (input is FlowTree) mx else MarkSlot.Concrete(t)
                val tail = if (input is FlowTree) Tail.STAR else Tail.ANY_TAINT
                assertEquals(setOf(Conclusion(PathFact(y, emptyList(), tail, mark),
                    ExclusionSet.of(intArrayOf(g, h)), false)), methodLeaves(f.ops, output.map { it.second }))
            }
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun `reversed may edge forces even exact results into DEMAND in every form mode`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val core = f.core(index = 2)
            val mt = MarkSlot.Concrete(f.mark("T"))
            val forward = PathEdge(PathFact(x, emptyList(), Tail.EXACT, mt),
                PathFact(y, emptyList(), Tail.ANY, mt), ExclusionSet.Empty)
            val me = MicroEdge(checkNotNull(revEdge(forward)), forward)
            assertTrue(me.may)
            val summary = StatementSummary(setOf(y), listOf(me), emptyList(), emptyMap())
            val input = f.fact(y, mark = mt)
            val premise = f.manager.initial(f.pattern(y, mark = mt))
            for (mode in MethodFormMode.entries) {
                val output = applyMethodForm(core, mode, summary, premise, input, Place(f.inst(1), true, true))
                assertEquals(setOf(Conclusion(PathFact(x, emptyList(), Tail.EXACT, mt), ExclusionSet.Empty, true)),
                    methodLeaves(f.ops, output.map { it.second }))
                assertTrue(output.all { it.first === premise })
            }
        }
    }

    @Test fun `run1 static position exception requires statement placement and names original FLOW premise`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val core = f.core()
            val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
            val field = f.field("f")
            val path = listOf(cls, field)
            val premise = f.manager.initial(f.pattern(AccessPathBase.ClassStatic, tail = Tail.STAR, mark = MarkSlot.STAR))
            val input = f.fact(AccessPathBase.ClassStatic, tail = Tail.STAR, mark = MarkSlot.STAR)
            val edge = MicroEdge.of(PathEdge(PathFact(AccessPathBase.ClassStatic, path, Tail.STAR, MarkSlot.STAR),
                PathFact(y, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty))
            val summary = StatementSummary(setOf(AccessPathBase.ClassStatic), listOf(edge), emptyList(), emptyMap())
            assertTrue(applyMethodForm(core, MethodFormMode.STATEMENT, summary, premise, input,
                Place(f.inst(1), true, true)).isEmpty())
            assertEquals(listOf<Pair<InitialAp, RequestKind>>(premise to RequestKind.Position(f.manager.path(path)!!)), f.requests)
            f.requests.clear()
            for (mode in listOf(MethodFormMode.STAGE, MethodFormMode.GEN)) {
                val result = applyMethodForm(core, mode, summary, premise, input, Place(f.inst(1), false, false))
                assertEquals(setOf(Conclusion(PathFact(y, emptyList(), Tail.ANY, MarkSlot.STAR), ExclusionSet.Empty, true)),
                    methodLeaves(f.ops, result.map { it.second }))
                assertTrue(result.all { it.first === premise })
            }
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun `restricted forward sources require their exact source seed at source places but GEN remains generative`(): Unit {
        for (seeded in listOf(false, true)) {
            val f = MethodAnalysisFixtures()
            f.shared.use {
                val mt = MarkSlot.Concrete(f.mark("T"))
                val source = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(y, emptyList(), Tail.EXACT, mt), ExclusionSet.Empty))
                val seeds = if (seeded) SeedIndex.of(listOf(Seed.Source(f.key, f.inst(1), source.forward))) else SeedIndex.EMPTY
                val core = f.core(index = 3, seeds = seeds)
                val summary = StatementSummary(setOf(AccessPathBase.Zero), listOf(source), emptyList(), emptyMap())
                val expected = setOf(Conclusion(PathFact(y, emptyList(), Tail.EXACT, mt), ExclusionSet.Empty, false))
                for (mode in listOf(MethodFormMode.STATEMENT, MethodFormMode.STAGE)) {
                    val output = applyMethodForm(core, mode, summary, f.manager.zero, Reach.NORMAL, Place(f.inst(1), true, true))
                    assertEquals(if (seeded) expected else emptySet(), methodLeaves(f.ops, output.map { it.second }))
                }
                val gen = applyMethodForm(core, MethodFormMode.GEN, summary, f.manager.zero, Reach.NORMAL,
                    Place(f.inst(1), false, false))
                assertEquals(expected, methodLeaves(f.ops, gen.map { it.second }))
                assertNull(core.sourceHits)
                assertTrue(f.requests.isEmpty())
            }
        }
    }

    @Test fun `backward source hit uses cached forward edge and occurs before duplicate zero storage`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val core = f.core(index = 2)
            val mt = MarkSlot.Concrete(f.mark("T"))
            val forward = PathEdge(ZERO_FACT, PathFact(y, emptyList(), Tail.ANY_TAINT, mt), ExclusionSet.Empty)
            val reversed = MicroEdge(checkNotNull(revEdge(forward)), forward)
            val summary = StatementSummary(setOf(y), listOf(reversed), emptyList(), emptyMap())
            val premise = f.manager.initial(f.pattern(y, mark = mt))
            repeat(2) {
                val result = applyMethodForm(core, MethodFormMode.STATEMENT, summary, premise, f.fact(y, mark = mt),
                    Place(f.inst(1), true, true))
                assertSame(Reach.NORMAL, result.single().second)
                core.emitAfter(premise, f.inst(1), result.single().second)
            }
            val hits = assertNotNull(core.sourceHits).entries().toList()
            assertEquals(listOf(Triple(f.key, f.inst(1), forward)), hits)
            assertSame(forward, hits.single().third)
            assertTrue(f.requests.isEmpty())
        }
    }
}

internal enum class MethodFormMode { STATEMENT, STAGE, GEN }

internal fun applyMethodForm(core: RunMethodAnalyzer, mode: MethodFormMode, summary: StatementSummary,
                             premise: PremiseKey, input: Facts, at: Place): List<Pair<PremiseKey, Facts>> = buildList {
    val emit: (PremiseKey, Facts) -> Unit = { pr, facts -> add(pr to facts) }
    when (mode) {
        MethodFormMode.STATEMENT -> core.applier.statement(summary, premise, input, at, emit)
        MethodFormMode.STAGE -> core.applier.stage(summary, premise, input, at) { pr, facts, _ -> emit(pr, facts) }
        MethodFormMode.GEN -> core.applier.gen(summary.edges, premise, input, emit)
    }
}

internal fun methodLeaves(ops: ApOps, values: Iterable<Facts>): Set<Conclusion> = values.flatMap { value ->
    if (value is Reach) listOf(Conclusion(ZERO_FACT, ExclusionSet.Empty, value.layer == Layer.DEMAND))
    else ops.leaves(value).map { Conclusion(it.fact, it.exclusion, value.layer == Layer.DEMAND) }.toList()
}.toSet()

internal fun methodEntries(ops: ApOps, values: Sequence<Pair<PremiseKey, Facts>>): Set<Pair<PremiseKey, Conclusion>> =
    values.flatMap { (premise, facts) -> methodLeaves(ops, listOf(facts)).asSequence().map { premise to it } }.toSet()

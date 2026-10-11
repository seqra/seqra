package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.HandOff
import org.opentaint.dataflow.bidi.fixtures.HandOffFixtures
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class NormalizedHandOffTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)
    private val resultBase = AccessPathBase.Return

    private fun stored(h: HandOff, method: MethodKey, vararg bases: AccessPathBase): Set<DemandPattern> =
        bases.flatMap { h.demand.near(method, it, null) }.toSet()

    @Test
    fun `forward selected abstract leaves normalize exactly once while distinct STAR exit field exclusions remain`(): Unit {
        HandOffFixtures(index = 1, L = 1).use { f ->
            val key = f.key()
            val a = f.field("a")
            val b = f.field("b")
            val c = f.field("c")
            val t = f.mark("T")
            val u = f.mark("U")
            val mxT = MarkSlot.Star(MarkSet(intArrayOf(t.id)))
            val mxU = MarkSlot.Star(MarkSet(intArrayOf(u.id)))
            val jA = f.initial(x, listOf(a), Tail.STAR, MarkSlot.STAR, ExclusionSet.of(a))
            val jB = f.initial(x, listOf(a), Tail.STAR, MarkSlot.STAR, ExclusionSet.of(b))
            f.raw(key, jA, f.fact(resultBase, listOf(c), Tail.STAR, mxT, ExclusionSet.of(b)))
            f.selected(key, jA, f.fact(resultBase, listOf(c), Tail.ANY, mxT, layer = Layer.DEMAND))
            f.selected(key, jA, f.fact(resultBase, listOf(c), Tail.ANY, mxU, layer = Layer.DEMAND))
            f.selected(key, jB, f.fact(resultBase, listOf(c), Tail.ANY, MarkSlot.STAR, layer = Layer.DEMAND))
            val result = f.result()
            val rawBefore = result.analyzers.single().summaries.all().toList()
            val selectedBefore = result.analyzers.single().summaries.demandEdges().toList()
            val h = HandOff.of(f.config, result, f.report, f.shared)
            val entry = f.pattern(resultBase, listOf(c), Tail.ANY, MarkSlot.STAR)
            val expected = setOf(DemandPattern(entry, f.pattern(x, listOf(a), Tail.STAR, MarkSlot.STAR, ExclusionSet.of(a))),
                DemandPattern(entry, f.pattern(x, listOf(a), Tail.STAR, MarkSlot.STAR, ExclusionSet.of(b))))
            assertEquals(expected, stored(h, key, resultBase))
            assertEquals(mapOf(key to 2), h.demandEdges, "Count accepted normalized patterns, not three raw leaves")
            assertEquals(rawBefore, result.analyzers.single().summaries.all().toList())
            assertEquals(selectedBefore, result.analyzers.single().summaries.demandEdges().toList())
            assertEquals(mxT, f.ops.leaves(rawBefore.single().second).single().fact.mark)
            assertEquals(ExclusionSet.of(b), f.ops.leaves(rawBefore.single().second).single().exclusion)
        }
    }

    @Test
    fun `forward normal multi premise must sides become may Empty E while exact and concrete marks stay distinct`(): Unit {
        HandOffFixtures(index = 3, L = 1).use { f ->
            val key = f.key()
            val native = f.key("native singleton")
            val a = f.field("a")
            val b = f.field("b")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val must = f.initial(x, listOf(a), Tail.ANY_TAINT, t, ExclusionSet.of(b))
            val exact = f.initial(y, listOf(b), Tail.EXACT, u)
            val joint = f.premise(must, exact)
            for (mark in listOf(t, v)) {
                val g = f.fact(resultBase, listOf(a), Tail.ANY_TAINT, mark, ExclusionSet.of(a))
                f.raw(key, joint, g)
                f.selected(key, joint, g)
            }
            f.raw(native, must, f.fact(resultBase, listOf(a), Tail.ANY_TAINT, t, ExclusionSet.of(a)))
            val result = f.result()
            val rawBefore = result.analyzers.associate { it.key to it.summaries.all().toList() }
            val h = HandOff.of(f.config, result, f.report, f.shared)
            val expected = listOf(t, v).flatMap { mark ->
                val entry = f.pattern(resultBase, listOf(a), Tail.ANY, mark)
                listOf(DemandPattern(entry, f.pattern(x, listOf(a), Tail.ANY, t)),
                    DemandPattern(entry, f.pattern(y, listOf(b), Tail.EXACT, u)))
            }.toSet()
            assertEquals(expected, stored(h, key, resultBase))
            assertEquals(mapOf(key to 4), h.demandEdges)
            assertTrue(stored(h, native, resultBase).isEmpty(), "An unselected native singleton supplies no callee demand")
            assertEquals(rawBefore, result.analyzers.associate { it.key to it.summaries.all().toList() })
            assertEquals(Tail.ANY_TAINT, must.tail)
            assertEquals(ExclusionSet.of(b), must.exclusion)
        }
    }

    @Test
    fun `concrete selected marks remain separate and raw normal singleton summaries never enter hand off counts`(): Unit {
        HandOffFixtures(index = 1, L = 1).use { f ->
            val key = f.key()
            val onlyRecord = f.key("record only")
            val a = f.field("a")
            val b = f.field("b")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            f.selected(key, f.manager.zero, f.fact(resultBase, listOf(a), Tail.ANY, t, layer = Layer.DEMAND))
            f.selected(key, f.manager.zero, f.fact(resultBase, listOf(a), Tail.ANY, u, layer = Layer.DEMAND))
            f.selected(key, f.manager.zero, f.fact(resultBase, listOf(b), Tail.EXACT, v, layer = Layer.DEMAND))
            val concrete = f.initial(x, listOf(a), Tail.EXACT, t)
            f.raw(onlyRecord, concrete, f.fact(resultBase, listOf(a), Tail.EXACT, u))
            val result = f.result()
            val h = HandOff.of(f.config, result, f.report, f.shared)
            val zero = f.manager.zero.toPattern()
            assertEquals(setOf(DemandPattern(f.pattern(resultBase, listOf(a), Tail.ANY, t), zero),
                DemandPattern(f.pattern(resultBase, listOf(a), Tail.ANY, u), zero),
                DemandPattern(f.pattern(resultBase, listOf(b), Tail.EXACT, v), zero)), stored(h, key, resultBase))
            assertEquals(mapOf(key to 3), h.demandEdges)
            assertTrue(stored(h, onlyRecord, resultBase).isEmpty())
            assertEquals(1, result.analyzers.single { it.key == onlyRecord }.summaries.all().count())
        }
    }

    @Test
    fun `backward selected abstract leaves normalize mark exclusions and retain STAR premise field E`(): Unit {
        HandOffFixtures(index = 2, L = 1).use { f ->
            val key = f.key()
            val a = f.field("a")
            val b = f.field("b")
            val t = f.mark("T")
            val u = f.mark("U")
            val mxT = MarkSlot.Star(MarkSet(intArrayOf(t.id)))
            val mxU = MarkSlot.Star(MarkSet(intArrayOf(u.id)))
            val j = f.initial(x, listOf(a), Tail.STAR, MarkSlot.STAR, ExclusionSet.of(b))
            f.raw(key, j, f.fact(y, listOf(b), Tail.STAR, mxT, ExclusionSet.of(a)))
            f.selected(key, j, f.fact(y, listOf(b), Tail.ANY, mxT, layer = Layer.DEMAND))
            f.selected(key, j, f.fact(y, listOf(b), Tail.ANY, mxU, layer = Layer.DEMAND))
            val result = f.result()
            val rawBefore = result.analyzers.single().summaries.all().toList()
            val h = HandOff.of(f.config, result, f.report, f.shared)
            assertEquals(setOf(DemandPattern(f.pattern(y, listOf(b), Tail.ANY, MarkSlot.STAR),
                f.pattern(x, listOf(a), Tail.STAR, MarkSlot.STAR, ExclusionSet.of(b)))), stored(h, key, y))
            assertEquals(mapOf(key to 1), h.demandEdges)
            assertEquals(rawBefore, result.analyzers.single().summaries.all().toList())
            assertEquals(mxT, f.ops.leaves(rawBefore.single().second).single().fact.mark)
            assertEquals(ExclusionSet.of(a), f.ops.leaves(rawBefore.single().second).single().exclusion)
        }
    }

    @Test
    fun `backward singleton and zero summaries count accepted patterns and never count implicit zero`(): Unit {
        HandOffFixtures(index = 2, L = 1).use { f ->
            val key = f.key()
            val recordOnly = f.key("record only")
            val a = f.field("a")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val j = f.initial(y, listOf(a), Tail.ANY, u)
            f.selected(key, f.manager.zero, Reach.NORMAL)
            f.selected(key, f.manager.zero, f.fact(x, listOf(a), Tail.EXACT, t))
            f.selected(key, f.manager.zero, f.fact(y, tail = Tail.ANY, mark = u, layer = Layer.DEMAND))
            f.selected(key, j, Reach.DEMAND)
            val normal = f.initial(y, listOf(a), Tail.EXACT, u)
            f.raw(recordOnly, normal, f.fact(x, listOf(a), Tail.EXACT, t))
            val h = HandOff.of(f.config, f.result(), f.report, f.shared)
            val zero = f.manager.zero.toPattern()
            assertEquals(setOf(DemandPattern(f.pattern(x, listOf(a), Tail.EXACT, t), null)), stored(h, key, x))
            assertEquals(setOf(DemandPattern(f.pattern(y, tail = Tail.ANY, mark = u), null)), stored(h, key, y))
            assertEquals(setOf(DemandPattern(zero, null), DemandPattern(zero, f.pattern(y, listOf(a), Tail.ANY, u))),
                stored(h, key, AccessPathBase.Zero))
            assertEquals(listOf(DemandPattern(zero, f.pattern(y, listOf(a), Tail.ANY, u))),
                h.demand.covering(key, AccessPathBase.Zero, null))
            assertEquals(mapOf(key to 3), h.demandEdges)
            assertTrue(stored(h, recordOnly, x).isEmpty())
            assertEquals(setOf(DemandPattern(zero, null)), stored(h, recordOnly, AccessPathBase.Zero))
            assertTrue(h.demand.covering(recordOnly, AccessPathBase.Zero, null).isEmpty())
        }
    }

    @Test
    fun `backward multi premise summaries are rejected rather than split into independent forward demand`(): Unit {
        HandOffFixtures(index = 2, L = 1).use { f ->
            val key = f.key()
            val a = f.initial()
            val b = f.initial(y, mark = MarkSlot.Concrete(f.mark("U")))
            val joint = f.premise(a, b)
            f.selected(key, joint, f.fact(resultBase, tail = Tail.ANY, layer = Layer.DEMAND))
            val result = f.result()
            val selectedBefore = result.analyzers.single().summaries.demandEdges().toList()
            assertFails { HandOff.of(f.config, result, f.report, f.shared) }
            assertEquals(selectedBefore, result.analyzers.single().summaries.demandEdges().toList())
        }
    }

    @Test
    fun `empty hand off construction checks cancellation before enumerating actors and remains usable after activation`(): Unit {
        for (index in listOf(1, 2, 3)) HandOffFixtures(index = index, L = 1).use { f ->
            val result = f.result()
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { HandOff.of(f.config, result, f.report, f.shared) }
            f.cancellation.activate()
            val h = HandOff.of(f.config, result, f.report, f.shared)
            assertTrue(h.demandEdges.isEmpty())
            assertEquals(0, h.seeds.size)
            assertEquals(setOf(DemandPattern(f.manager.zero.toPattern(), null)), stored(h, f.key(), AccessPathBase.Zero))
        }
    }

    @Test
    fun `cancelled nonempty leaf member and hit walks preserve the frozen inputs for later activation`(): Unit {
        HandOffFixtures(index = 3, L = 1).use { f ->
            val key = f.key()
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val a = f.initial(x, tail = Tail.ANY_TAINT, mark = t, exclusion = ExclusionSet.of(f.field("blocked")))
            val b = f.initial(y, mark = u)
            val joint = f.premise(a, b)
            for (mark in listOf(t, u)) f.selected(key, joint, f.fact(resultBase, tail = Tail.ANY_TAINT, mark = mark))
            val result = f.result()
            val selectedBefore = result.analyzers.single().summaries.demandEdges().toList()
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { HandOff.of(f.config, result, f.report, f.shared) }
            f.cancellation.activate()
            val h = HandOff.of(f.config, result, f.report, f.shared)
            assertEquals(mapOf(key to 4), h.demandEdges)
            assertEquals(listOf(t, u).flatMap { mark ->
                val entry = f.pattern(resultBase, tail = Tail.ANY, mark = mark)
                listOf(DemandPattern(entry, f.pattern(x, tail = Tail.ANY, mark = t)),
                    DemandPattern(entry, f.pattern(y, mark = u)))
            }.toSet(), stored(h, key, resultBase))
            assertEquals(selectedBefore, result.analyzers.single().summaries.demandEdges().toList())
        }
        HandOffFixtures(index = 2, L = 1).use { f ->
            val key = f.key()
            val t = MarkSlot.Concrete(f.mark("T"))
            val j = f.initial(x, tail = Tail.ANY, mark = t)
            f.selected(key, j, Reach.DEMAND)
            f.selected(key, f.manager.zero, f.fact(y, mark = t))
            val forward = PathEdge(f.manager.zero.toPattern().fact, f.pattern(x, mark = t).fact, ExclusionSet.Empty)
            f.hit(key, f.inst(1), forward)
            val result = f.result()
            val hitsBefore = result.analyzers.single().sourceHits!!.entries().toList()
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { HandOff.of(f.config, result, f.report, f.shared) }
            f.cancellation.activate()
            val h = HandOff.of(f.config, result, f.report, f.shared)
            assertEquals(mapOf(key to 2), h.demandEdges)
            assertEquals(setOf(DemandPattern(f.manager.zero.toPattern(), null),
                DemandPattern(f.manager.zero.toPattern(), f.pattern(x, tail = Tail.ANY, mark = t))),
                stored(h, key, AccessPathBase.Zero))
            assertEquals(setOf(DemandPattern(f.pattern(y, mark = t), null)), stored(h, key, y))
            assertEquals(1, h.seeds.size)
            assertTrue(h.seeds.allowsSource(key, f.inst(1), forward))
            assertEquals(hitsBefore, result.analyzers.single().sourceHits!!.entries().toList())
        }
    }
}

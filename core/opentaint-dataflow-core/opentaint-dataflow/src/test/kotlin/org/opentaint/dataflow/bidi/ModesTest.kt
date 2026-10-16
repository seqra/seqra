package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

class ModesTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return

    @Test fun `run1 policy emits one canonical FLOW initial from concrete and excluded abstract links`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val core = f.core()
            val field = f.field("f")
            val g = f.field("g")
            val t = f.mark("T")
            val inputs = listOf(
                f.fact(x, listOf(field), mark = MarkSlot.Concrete(t)),
                f.fact(x, tail = Tail.STAR, mark = MarkSlot.Star(MarkSet(intArrayOf(t.id))),
                    exclusion = ExclusionSet.of(intArrayOf(g))),
            )
            fun caller(input: Facts) = CallerRef(f.key,
                if (input is FlowTree) f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)) else f.manager.zero,
                Layer.NORMAL, f.inst(1))
            for (input in inputs) core.addLink(caller(input), input.layer, input)
            val initial = core.initials.all().single()
            assertSame(f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)), initial)
            assertEquals(setOf(Conclusion(PathFact(x, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty, false)),
                methodLeaves(f.ops, core.edges.edgesAt(f.inst(0), initial).map { it.second }.toList()))
            assertTrue(core.nonZeroInitial)
            assertEquals(2L, core.edgeDeltas, "One initial edge plus the shared default schema")
            for (input in inputs) core.addLink(caller(input), input.layer, input)
            assertEquals(2L, core.edgeDeltas, "One initial edge plus the shared default schema")
            assertEquals(listOf(initial), core.initials.all().toList())
        }
    }

    @Test fun `actual restricted queries retain every supplied mark independently of older guidance`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val field = f.field("f")
            val g = f.field("g")
            val mt = MarkSlot.Concrete(f.mark("T"))
            val mu = MarkSlot.Concrete(f.mark("U"))
            val demands = DemandStore.Builder(f.manager).apply {
                add(f.key, DemandPattern(f.pattern(x, listOf(field), Tail.ANY, mt), f.pattern(y, tail = Tail.ANY, mark = mt)))
                add(f.key, DemandPattern(f.pattern(x, tail = Tail.ANY, mark = MarkSlot.STAR),
                    f.pattern(y, tail = Tail.ANY, mark = MarkSlot.STAR)))
            }.build()
            val core = f.core(index = 3, demand = demands)
            val concreteT = f.fact(x, listOf(field), mark = mt)
            val concreteU = f.fact(x, listOf(g), mark = mu)
            val abstract = f.fact(x, tail = Tail.STAR, mark = MarkSlot.STAR)
            val caller = CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1))
            for (input in listOf(concreteT, concreteU, abstract)) core.addLink(
                if (input is FlowTree) caller.copy(premise = f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)))
                else caller, input.layer, input)
            val concrete = f.manager.initial(f.pattern(x, listOf(field), mark = mt))
            val flow = f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))
            val currentU = f.manager.initial(f.pattern(x, listOf(g), mark = mu))
            assertEquals(setOf(concrete, currentU, flow), core.initials.all().toSet())
            assertEquals(setOf(Conclusion(PathFact(x, listOf(field), Tail.EXACT, mt), ExclusionSet.Empty, false)),
                methodLeaves(f.ops, core.edges.edgesAt(f.inst(0), concrete).map { it.second }.toList()))
            assertEquals(setOf(Conclusion(PathFact(x, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty, false)),
                methodLeaves(f.ops, core.edges.edgesAt(f.inst(0), flow).map { it.second }.toList()))
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun `actual restricted must query preserves exclusion and admits only concrete guide intersections`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val field = f.field("f")
            val g = f.field("g")
            val mt = MarkSlot.Concrete(f.mark("T"))
            val d = DemandStore.Builder(f.manager).apply {
                add(f.key, DemandPattern(f.pattern(x, tail = Tail.ANY, mark = mt), null))
                add(f.key, DemandPattern(f.pattern(x, listOf(field), Tail.EXACT, mt), null))
                add(f.key, DemandPattern(f.pattern(x, listOf(g), Tail.EXACT, mt), null))
            }.build()
            val core = f.core(index = 3, demand = d)
            val e = ExclusionSet.of(intArrayOf(field))
            val input = f.fact(x, tail = Tail.ANY_TAINT, mark = mt, exclusion = e)
            core.addLink(CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1)), Layer.NORMAL, input)
            val must = f.manager.initial(f.pattern(x, tail = Tail.ANY_TAINT, mark = mt, exclusion = e))
            val exactG = f.manager.initial(f.pattern(x, listOf(g), Tail.EXACT, mt))
            assertEquals(setOf(must, exactG), core.initials.all().toSet())
            assertEquals(setOf(Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, mt), e, false)),
                methodLeaves(f.ops, core.edges.edgesAt(f.inst(0), must).map { it.second }.toList()))
            assertEquals(setOf(Conclusion(PathFact(x, listOf(g), Tail.EXACT, mt), ExclusionSet.Empty, false)),
                methodLeaves(f.ops, core.edges.edgesAt(f.inst(0), exactG).map { it.second }.toList()))
            assertFalse(core.initials.all().any { it.path?.toList() == listOf(field) })
            assertTrue(core.initials.all().all { it.mark == mt })
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun `must initials are accepted only in a restricted forward run`(): Unit {
        for (index in listOf(1, 2, 3)) {
            val f = MethodAnalysisFixtures()
            f.shared.use {
                val core = f.core(index = index)
                val mt = MarkSlot.Concrete(f.mark("T"))
                val initial = f.manager.initial(f.pattern(x, tail = Tail.ANY_TAINT, mark = mt,
                    exclusion = ExclusionSet.of(intArrayOf(f.field("f")))))
                if (index == 3) {
                    core.addInitial(initial)
                    assertEquals(listOf(initial), core.initials.all().toList())
                } else {
                    assertFails { core.addInitial(initial) }
                    assertTrue(core.initials.all().isEmpty())
                    assertTrue(core.edges.edgesAt(f.inst(0)).none())
                }
            }
        }
    }

    @Test fun `a concrete mark read on FLOW raises only run1 requests and keeps the incoming canonical premise`(): Unit {
        for (index in listOf(1, 2, 3)) for (excluded in listOf(false, true)) {
            val f = MethodAnalysisFixtures()
            f.shared.use {
                val core = f.core(index = index)
                val t = f.mark("T")
                val mt = MarkSlot.Concrete(t)
                val premise = f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))
                val input = f.fact(x, tail = Tail.STAR,
                    mark = if (excluded) MarkSlot.Star(MarkSet(intArrayOf(t.id))) else MarkSlot.STAR)
                val me = MicroEdge.of(PathEdge(PathFact(x, emptyList(), Tail.STAR, mt),
                    PathFact(y, emptyList(), Tail.EXACT, mt), ExclusionSet.Empty))
                val summary = StatementSummary(setOf(x), listOf(me), emptyList(), emptyMap())
                assertTrue(applyMethodForm(core, MethodFormMode.STATEMENT, summary, premise, input,
                    Place(f.inst(1), true, true)).isEmpty())
                assertEquals(if (index == 1 && !excluded) listOf(premise to RequestKind.Mark(t)) else emptyList(), f.requests)
                if (f.requests.isNotEmpty()) assertSame(premise, f.requests.single().first)
            }
        }
    }

    @Test fun `actual restricted queries start and publish even when prior guidance has no comparable entry`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val mt = MarkSlot.Concrete(f.mark("T"))
            val mu = MarkSlot.Concrete(f.mark("U"))
            val d = DemandStore.Builder(f.manager).apply {
                add(f.key, DemandPattern(f.pattern(x, listOf(f.field("f")), Tail.EXACT, mt),
                    f.pattern(y, tail = Tail.ANY, mark = mt)))
            }.build()
            val core = f.core(index = 3, demand = d)
            val caller = CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1))
            core.addLink(caller, Layer.NORMAL, f.fact(x, listOf(f.field("g")), mark = mt))
            core.addLink(caller, Layer.NORMAL, f.fact(x, listOf(f.field("f")), mark = mu))
            core.addLink(caller.copy(premise = f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))),
                Layer.NORMAL, f.fact(x, tail = Tail.STAR, mark = MarkSlot.STAR))
            f.drain(core)
            val expected = setOf(
                f.manager.initial(f.pattern(x, listOf(f.field("g")), mark = mt)),
                f.manager.initial(f.pattern(x, listOf(f.field("f")), mark = mu)),
                f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)))
            assertEquals(expected, core.initials.all().toSet())
            assertTrue(core.nonZeroInitial)
            assertEquals(expected, core.summaries.all().map { it.first }.toSet())
            assertEquals(expected, f.publications.flatten().map { it.premise }.toSet())
            assertTrue(f.requests.isEmpty())
            assertEquals(6L, core.edgeDeltas, "Three actual starts and three witnessed summaries")
            assertEquals(0L, core.demandLayerEdges)
        }
    }

    @Test fun `forward unchanged component drains without summarizing a detached normal exit`(): Unit {
        MethodAnalysisFixtures(listOf(listOf(1), listOf(1), emptyList())).use { f ->
            // A later forward run admits this generic non-root FLOW initial.
            val core = f.core(index = 3)
            val initial = f.manager.initial(f.pattern(x, listOf(f.field("f")), Tail.STAR, MarkSlot.STAR))
            core.addRootZero()
            core.addInitial(initial)
            f.drain(core, quantum = 1)
            assertEquals(setOf(f.manager.zero, initial), core.initials.all().toSet())
            assertEquals(setOf(0, 1), f.processed.map { it.node.location.index }.toSet())
            assertEquals(4L, core.steps)
            assertTrue(f.processed.none { it.node === f.inst(2) })
            assertTrue(core.summaries.all().none())
            assertTrue(core.summaries.demandEdges().none())
            assertTrue(f.publications.isEmpty())
            assertTrue(f.requests.isEmpty())
            assertTrue(f.cuts.none { it.first == Cut.STATEMENT || it.first == Cut.EXIT_RULES })
            assertEquals(2L, core.edgeDeltas)
            assertEquals(0L, core.demandLayerEdges)
            assertTrue(core.work(1))
        }
    }

    @Test fun `restricted request handlers reject mark and position events before calling local services`(): Unit {
        for (index in listOf(2, 3)) MethodAnalysisFixtures().use { f ->
            val core = f.core(index = index)
            val initial = f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))
            val static = f.manager.initial(f.pattern(AccessPathBase.ClassStatic, tail = Tail.STAR, mark = MarkSlot.STAR))
            val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
            assertFails { core.addMarkRequest(initial, f.mark("T")) }
            assertFails { core.addMarkRequest(static, f.mark("T")) }
            assertTrue(f.requests.isEmpty())
            assertTrue(core.initials.all().isEmpty())
            assertTrue(f.events.isEmpty())
        }
    }
}

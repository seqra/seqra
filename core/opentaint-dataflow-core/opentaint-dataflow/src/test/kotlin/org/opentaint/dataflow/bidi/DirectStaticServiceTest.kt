package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.FiniteProgramFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import kotlin.test.*

class DirectStaticServiceTest {
    private fun field(f: FiniteProgramFixtures, name: String): Pattern = Pattern(
        PathFact(STATIC, listOf(f.ap.accessors.index(ClassStaticAccessor("C")), f.field(name)), Tail.STAR, MarkSlot.STAR),
        ExclusionSet.Empty,
    )

    @Test fun `read and write join in either arrival order and duplicates do not replay`() {
        for (writeFirst in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val owner = f.method("reader", listOf(emptyList()), 0).key()
            val position = field(f, "f")
            val service = Run1StaticService(f.ap)
            val read = StaticBatch().apply { reads += StaticReadRegistration(owner, 1, position) }
            val write = StaticBatch().apply { writes += position }
            assertTrue(service.commit(if (writeFirst) write else read).isEmpty())
            val replies = service.commit(if (writeFirst) read else write)
            assertEquals(listOf(StaticOwnerEffect.ReadReady(owner, 1, position)), replies)
            assertTrue(service.commit(read).isEmpty())
            assertTrue(service.commit(write).isEmpty())
        }
    }

    @Test fun `an exact class point does not admit a field read`() {
        FiniteProgramFixtures().use { f ->
            val owner = f.method("reader", listOf(emptyList()), 0).key()
            val position = field(f, "f")
            val exactClass = position.copy(fact = position.fact.copy(path = position.fact.path.take(1), tail = Tail.EXACT))
            val service = Run1StaticService(f.ap)
            assertTrue(service.commit(StaticBatch().apply {
                reads += StaticReadRegistration(owner, 1, position)
                writes += exactClass
            }).isEmpty())
        }
    }

    @Test fun `group supplies create write presence first and answer only the requested group`() {
        FiniteProgramFixtures().use { f ->
            val owner = f.method("consumer", listOf(emptyList()), 0).key()
            val t = f.mark("T")
            val u = f.mark("U")
            f.ap.marks.finishFixtureOwnershipCensus()
            val g = f.ap.marks.groupOf(t)
            val h = f.ap.marks.groupOf(u)
            val position = field(f, "f")
            val service = Run1StaticService(f.ap)
            assertTrue(service.commit(StaticBatch().apply {
                reads += StaticReadRegistration(owner, 1, position)
                interests += StaticInterest(owner, 2, position, GroupSet.of(g))
            }).isEmpty())
            val unrelated = service.commit(StaticBatch().apply { supplies += RegionGroups(position, GroupSet.of(h)) })
            assertEquals(1, unrelated.filterIsInstance<StaticOwnerEffect.ReadReady>().size)
            assertTrue(unrelated.filterIsInstance<StaticOwnerEffect.PointReady>().isEmpty())
            val wanted = StaticBatch().apply { supplies += RegionGroups(position, GroupSet.of(g)) }
            val reply = service.commit(wanted).filterIsInstance<StaticOwnerEffect.PointReady>().single()
            assertEquals(GroupSet.of(g), reply.supply.groups)
            assertTrue(service.commit(wanted).isEmpty())
        }
    }

    @Test fun `unrelated regions and empty commits do not cause a global replay`() {
        FiniteProgramFixtures().use { f ->
            val owner = f.method("reader", listOf(emptyList()), 0).key()
            val service = Run1StaticService(f.ap)
            val readBatch = StaticBatch().apply {
                repeat(200) { reads += StaticReadRegistration(owner, it.toLong(), field(f, "read$it")) }
            }
            service.commit(readBatch)
            service.commit(StaticBatch().apply { repeat(200) { writes += field(f, "unrelated$it") } })
            assertEquals(0L, service.statistics().candidateVisits)
            service.commit(StaticBatch().apply { writes += field(f, "read17") })
            assertEquals(1L, service.statistics().candidateVisits)
            val before = service.statistics()
            repeat(20) { assertTrue(service.commit(StaticBatch()).isEmpty()) }
            assertEquals(before, service.statistics())
        }
    }

    @Test fun `ND support from different incoming calls cannot ground one writer`() {
        FiniteProgramFixtures().use { f ->
            val caller = f.method("caller", listOf(listOf(1), emptyList()), 1).key()
            val callee = f.method("writer", listOf(emptyList()), 0).key()
            val t = f.mark("T")
            val u = f.mark("U")
            f.ap.marks.finishFixtureOwnershipCensus()
            val g = f.ap.marks.groupOf(t)
            val h = f.ap.marks.groupOf(u)
            val p = Pattern(PathFact(AccessPathBase.Argument(0), emptyList(), Tail.EXACT,
                MarkSlot.Concrete(f.ap.marks.group(g))), ExclusionSet.Empty)
            val q = Pattern(PathFact(AccessPathBase.Argument(1), emptyList(), Tail.EXACT,
                MarkSlot.Concrete(f.ap.marks.group(h))), ExclusionSet.Empty)
            val premise = f.ap.premiseOf(listOf(f.ap.initial(p), f.ap.initial(q)))
            val first = N2InputRef(caller, callee, 0, CallerRef(caller, f.ap.zero, Layer.NORMAL, f.inst(caller, 0)))
            val second = N2InputRef(caller, callee, 1, CallerRef(caller, f.ap.zero, Layer.NORMAL, f.inst(caller, 1)))
            val service = Run1StaticService(f.ap)
            val initial = service.commit(StaticBatch().apply {
                writers += StaticWriterRegistration(callee, 1, premise)
                groundedInputs += GroundedInputGrowth(first, listOf(RegionGroups(p, GroupSet.of(g))))
                groundedInputs += GroundedInputGrowth(second, listOf(RegionGroups(q, GroupSet.of(h))))
            })
            assertTrue(initial.filterIsInstance<StaticOwnerEffect.WriterReady>().isEmpty())
            val complete = service.commit(StaticBatch().apply {
                groundedInputs += GroundedInputGrowth(first, listOf(RegionGroups(q, GroupSet.of(h))))
            })
            assertEquals(1, complete.filterIsInstance<StaticOwnerEffect.WriterReady>().size)
        }
    }
}

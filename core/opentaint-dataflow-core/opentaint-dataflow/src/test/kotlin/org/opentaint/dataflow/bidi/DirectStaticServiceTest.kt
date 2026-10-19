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

    @Test fun `all static ND writers require every actual group in either arrival order`() {
        for (supplyFirst in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val owner = f.method("writer", listOf(emptyList()), 0).key()
            val t = f.mark("T"); val u = f.mark("U")
            f.ap.marks.finishFixtureOwnershipCensus()
            val g = f.ap.marks.groupOf(t); val h = f.ap.marks.groupOf(u)
            val left = field(f, "left"); val right = field(f, "right")
            val premise = f.ap.premiseOf(listOf(
                f.ap.initial(left.copy(fact = left.fact.copy(mark = MarkSlot.Concrete(f.ap.marks.group(g))))),
                f.ap.initial(right.copy(fact = right.fact.copy(mark = MarkSlot.Concrete(f.ap.marks.group(h)))))))
            val writer = StaticBatch().apply { writers += StaticWriterRegistration(owner, 1, premise) }
            val service = Run1StaticService(f.ap)
            val first = StaticBatch().apply { supplies += RegionGroups(left, GroupSet.of(g)) }
            if (supplyFirst) assertTrue(service.commit(first).isEmpty())
            assertTrue(service.commit(writer).isEmpty())
            if (!supplyFirst) assertTrue(service.commit(first).isEmpty())
            assertTrue(service.commit(StaticBatch().apply {
                supplies += RegionGroups(right, GroupSet.of(g))
            }).filterIsInstance<StaticOwnerEffect.WriterReady>().isEmpty())
            val second = StaticBatch().apply { supplies += RegionGroups(right, GroupSet.of(h)) }
            val ready = service.commit(second).filterIsInstance<StaticOwnerEffect.WriterReady>().single()
            assertEquals(listOf(GroupSet.of(g), GroupSet.of(h)), ready.inputs.map { it.groups })
            assertEquals(listOf(left, right), ready.inputs.map { it.region })
            assertTrue(service.commit(second).isEmpty())
            assertTrue(service.commit(writer).isEmpty())
        }
    }

    @Test fun `a writer interest joins in both orders without creating group supply`() {
        for (writerFirst in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val owner = f.method("writer", listOf(emptyList()), 0).key()
            val t = f.mark("T"); f.ap.marks.finishFixtureOwnershipCensus()
            val g = f.ap.marks.groupOf(t)
            val output = field(f, "result")
            val writer = StaticBatch().apply { flowWriters += StaticFlowWriter(owner, 7, output) }
            val interest = StaticBatch().apply { interests += StaticInterest(owner, 8, output, GroupSet.of(g)) }
            val service = Run1StaticService(f.ap)
            assertTrue(service.commit(if (writerFirst) writer else interest).isEmpty())
            val reply = service.commit(if (writerFirst) interest else writer)
            assertEquals(listOf(StaticOwnerEffect.WriterInterest(owner, 7, GroupSet.of(g))), reply)
            assertTrue(reply.none { it is StaticOwnerEffect.PointReady || it is StaticOwnerEffect.WriterReady })
            assertTrue(service.commit(writer).isEmpty())
            assertTrue(service.commit(interest).isEmpty())
        }
    }

    @Test fun `a disjoint output does not receive a consumer interest`() {
        FiniteProgramFixtures().use { f ->
            val owner = f.method("writer", listOf(emptyList()), 0).key()
            val t = f.mark("T"); f.ap.marks.finishFixtureOwnershipCensus()
            val service = Run1StaticService(f.ap)
            assertTrue(service.commit(StaticBatch().apply {
                flowWriters += StaticFlowWriter(owner, 1, field(f, "left"))
                interests += StaticInterest(owner, 2, field(f, "right"), GroupSet.of(f.ap.marks.groupOf(t)))
            }).isEmpty())
        }
    }
}

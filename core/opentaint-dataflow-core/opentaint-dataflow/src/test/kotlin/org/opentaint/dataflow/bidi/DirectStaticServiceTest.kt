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
            val service = Run1StaticService(f.ap, MarkMode.GROUP)
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
            val service = Run1StaticService(f.ap, MarkMode.GROUP)
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
            val g = f.ap.marks.groupOf(t)
            val h = f.ap.marks.groupOf(u)
            val position = field(f, "f")
            val service = Run1StaticService(f.ap, MarkMode.GROUP)
            assertTrue(service.commit(StaticBatch().apply {
                reads += StaticReadRegistration(owner, 1, position)
                interests += StaticInterest(owner, 2, position, f.ap.marks.atomsFor(GroupSet.of(g)))
            }).isEmpty())
            val unrelated = service.commit(StaticBatch().apply { supplies += RegionGroups(position, f.ap.marks.atomsFor(GroupSet.of(h))) })
            assertEquals(1, unrelated.filterIsInstance<StaticOwnerEffect.ReadReady>().size)
            assertTrue(unrelated.filterIsInstance<StaticOwnerEffect.PointReady>().isEmpty())
            val wanted = StaticBatch().apply { supplies += RegionGroups(position, f.ap.marks.atomsFor(GroupSet.of(g))) }
            val reply = service.commit(wanted).filterIsInstance<StaticOwnerEffect.PointReady>().single()
            assertEquals(f.ap.marks.atomsFor(GroupSet.of(g)), reply.supply.groups)
            assertTrue(service.commit(wanted).isEmpty())
        }
    }

    @Test fun `unrelated regions and empty commits do not cause a global replay`() {
        FiniteProgramFixtures().use { f ->
            val owner = f.method("reader", listOf(emptyList()), 0).key()
            val service = Run1StaticService(f.ap, MarkMode.GROUP)
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

    @Test fun `static-origin FLOW writers are requested only after an output request`() {
        for (writeFirst in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val owner = f.method("writer", listOf(emptyList()), 0).key()
            val g = f.ap.marks.groupOf(f.mark("T"))
            val input = field(f, "input"); val output = field(f, "output")
            val writer = StaticBatch().apply {
                writers += StaticWriterRegistration(owner, 1, f.ap.initial(input), output)
            }
            val supply = StaticBatch().apply { supplies += RegionGroups(output, f.ap.marks.atomsFor(GroupSet.of(g))) }
            val service = Run1StaticService(f.ap, MarkMode.GROUP)
            assertTrue(service.commit(if (writeFirst) writer else supply).isEmpty())
            assertTrue(service.commit(if (writeFirst) supply else writer).isEmpty())
            assertEquals(1L, service.requestStatistics().reached)
            val ready = service.commit(StaticBatch().apply {
                interests += StaticInterest(owner, 2, output, f.ap.marks.atomsFor(GroupSet.of(g)))
            })
            assertEquals(1, ready.filterIsInstance<StaticOwnerEffect.WriterInterest>().size)
            assertEquals(1, ready.filterIsInstance<StaticOwnerEffect.PointReady>().size)
            assertEquals(0, service.statistics().pointSubscriptions)
            assertEquals(0L, service.requestStatistics().reached)
            assertEquals(1L, service.requestStatistics().processed)
            assertEquals(1L, service.requestStatistics().writerAdmissions)
        }
    }

    @Test fun `answered requests are released and canonical field answers merge geometry`() {
        FiniteProgramFixtures().use { f ->
            val owner = f.method("reader", listOf(emptyList()), 0).key()
            val g = f.ap.marks.groupOf(f.mark("T"))
            val h = f.ap.marks.groupOf(f.mark("U"))
            val position = field(f, "value")
            val service = Run1StaticService(f.ap, MarkMode.GROUP)
            service.commit(StaticBatch().apply {
                interests += StaticInterest(owner, 2, position, f.ap.marks.atomsFor(GroupSet.of(listOf(g, h))))
            })
            assertEquals(2L, service.requestStatistics().pendingMemberships)
            val first = service.commit(StaticBatch().apply {
                supplies += RegionGroups(position.copy(fact = position.fact.copy(tail = Tail.EXACT)), f.ap.marks.atomsFor(GroupSet.of(g)))
            }).filterIsInstance<StaticOwnerEffect.PointReady>().single()
            assertEquals(position, first.supply.region)
            assertEquals(1L, service.requestStatistics().pendingMemberships)
            assertEquals(1, service.statistics().pointSubscriptions)
            service.commit(StaticBatch().apply { supplies += RegionGroups(position, f.ap.marks.atomsFor(GroupSet.of(h))) })
            assertEquals(0L, service.requestStatistics().pendingMemberships)
            assertEquals(0, service.statistics().pointSubscriptions)
            assertEquals(2L, service.requestStatistics().processed)
            assertTrue(service.commit(StaticBatch()).isEmpty())
        }
    }

    @Test fun `a writer interest joins in both orders without creating group supply`() {
        for (writerFirst in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val owner = f.method("writer", listOf(emptyList()), 0).key()
            val t = f.mark("T")
            val g = f.ap.marks.groupOf(t)
            val output = field(f, "result")
            val writer = StaticBatch().apply { flowWriters += StaticFlowWriter(owner, 7, output) }
            val interest = StaticBatch().apply { interests += StaticInterest(owner, 8, output, f.ap.marks.atomsFor(GroupSet.of(g))) }
            val service = Run1StaticService(f.ap, MarkMode.GROUP)
            assertTrue(service.commit(if (writerFirst) writer else interest).isEmpty())
            val reply = service.commit(if (writerFirst) interest else writer)
            assertEquals(listOf(StaticOwnerEffect.WriterInterest(owner, 7, f.ap.marks.atomsFor(GroupSet.of(g)))), reply)
            assertTrue(reply.none { it is StaticOwnerEffect.PointReady })
            assertTrue(service.commit(writer).isEmpty())
            assertTrue(service.commit(interest).isEmpty())
        }
    }

    @Test fun `a disjoint output does not receive a consumer interest`() {
        FiniteProgramFixtures().use { f ->
            val owner = f.method("writer", listOf(emptyList()), 0).key()
            val t = f.mark("T")
            val service = Run1StaticService(f.ap, MarkMode.GROUP)
            assertTrue(service.commit(StaticBatch().apply {
                flowWriters += StaticFlowWriter(owner, 1, field(f, "left"))
                interests += StaticInterest(owner, 2, field(f, "right"), f.ap.marks.atomsFor(GroupSet.of(f.ap.marks.groupOf(t))))
            }).isEmpty())
        }
    }
    @Test fun `first request from reach asks old writers and later writers ask processed only`() {
        FiniteProgramFixtures().use { f ->
            val owner = f.method("writer", listOf(emptyList()), 0).key()
            val g = f.ap.marks.groupOf(f.mark("T")); val h = f.ap.marks.groupOf(f.mark("U"))
            val position = field(f, "f")
            val service = Run1StaticService(f.ap, MarkMode.GROUP)
            service.commit(StaticBatch().apply {
                flowWriters += StaticFlowWriter(owner, 1, position)
                supplies += RegionGroups(position, f.ap.marks.atomsFor(GroupSet.of(listOf(g, h))))
            })
            assertEquals(0L, service.requestStatistics().writerAdmissions)
            val first = service.commit(StaticBatch().apply {
                interests += StaticInterest(owner, 2, position, f.ap.marks.atomsFor(GroupSet.of(g)))
            })
            assertEquals(listOf(f.ap.marks.atomsFor(GroupSet.of(g))), first.filterIsInstance<StaticOwnerEffect.WriterInterest>().map { it.groups })
            val later = service.commit(StaticBatch().apply {
                flowWriters += StaticFlowWriter(owner, 3, position)
            })
            assertEquals(listOf(f.ap.marks.atomsFor(GroupSet.of(g))), later.filterIsInstance<StaticOwnerEffect.WriterInterest>().map { it.groups })
            assertEquals(1L, service.requestStatistics().reached)
            assertEquals(1L, service.requestStatistics().processed)
            assertEquals(2L, service.requestStatistics().writerAdmissions)
        }
    }

    @Test fun `a queued answer does not consume a newly added pending group`() {
        FiniteProgramFixtures().use { f ->
            val owner = f.method("reader", listOf(emptyList()), 0).key()
            val g = f.ap.marks.groupOf(f.mark("T")); val h = f.ap.marks.groupOf(f.mark("U"))
            val position = field(f, "f"); val service = Run1StaticService(f.ap, MarkMode.GROUP)
            service.commit(StaticBatch().apply { interests += StaticInterest(owner, 1, position, f.ap.marks.atomsFor(GroupSet.of(g))) })
            val queued = service.commit(StaticBatch().apply { supplies += RegionGroups(position, f.ap.marks.atomsFor(GroupSet.of(g))) })
                .filterIsInstance<StaticOwnerEffect.PointReady>().single()
            assertEquals(f.ap.marks.atomsFor(GroupSet.of(g)), queued.supply.groups)
            service.commit(StaticBatch().apply { interests += StaticInterest(owner, 1, position, f.ap.marks.atomsFor(GroupSet.of(h))) })
            assertEquals(1L, service.requestStatistics().pendingMemberships)
            val later = service.commit(StaticBatch().apply { supplies += RegionGroups(position, f.ap.marks.atomsFor(GroupSet.of(h))) })
                .filterIsInstance<StaticOwnerEffect.PointReady>().single()
            assertEquals(f.ap.marks.atomsFor(GroupSet.of(h)), later.supply.groups)
            assertEquals(0, service.statistics().pointSubscriptions)
        }
    }

    @Test fun `broad supplies preserve field exclusions and exact class points do not supply fields`() {
        for (supplyFirst in listOf(false, true)) for (blocked in listOf(false, true))
            FiniteProgramFixtures().use { f ->
                val owner = f.method("reader", listOf(emptyList()), 0).key()
                val g = f.ap.marks.groupOf(f.mark("T")); val position = field(f, "f")
                val broad = position.copy(fact = position.fact.copy(path = position.fact.path.take(1), tail = Tail.ANY_TAINT),
                    exclusion = if (blocked) ExclusionSet.of(position.fact.path[1]) else ExclusionSet.Empty)
                val service = Run1StaticService(f.ap, MarkMode.GROUP)
                val supply = StaticBatch().apply { supplies += RegionGroups(broad, f.ap.marks.atomsFor(GroupSet.of(g))) }
                val interest = StaticBatch().apply { interests += StaticInterest(owner, 1, position, f.ap.marks.atomsFor(GroupSet.of(g))) }
                assertTrue(service.commit(if (supplyFirst) supply else interest).isEmpty())
                val answer = service.commit(if (supplyFirst) interest else supply)
                    .filterIsInstance<StaticOwnerEffect.PointReady>()
                assertEquals(!blocked, answer.isNotEmpty())
                if (!blocked) assertEquals(position, answer.single().supply.region)
            }
    }

    @Test fun `an exact class GROUP supply never answers a descendant field request`() {
        for (supplyFirst in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val owner = f.method("reader", listOf(emptyList()), 0).key()
            val g = f.ap.marks.groupOf(f.mark("T")); val position = field(f, "f")
            val exactClass = position.copy(fact = position.fact.copy(path = position.fact.path.take(1), tail = Tail.EXACT))
            val service = Run1StaticService(f.ap, MarkMode.GROUP)
            val supply = StaticBatch().apply { supplies += RegionGroups(exactClass, f.ap.marks.atomsFor(GroupSet.of(g))) }
            val interest = StaticBatch().apply { interests += StaticInterest(owner, 1, position, f.ap.marks.atomsFor(GroupSet.of(g))) }
            assertTrue(service.commit(if (supplyFirst) supply else interest).isEmpty())
            assertTrue(service.commit(if (supplyFirst) interest else supply).isEmpty())
            assertEquals(1L, service.requestStatistics().pendingMemberships)
        }
    }

    @Test fun `canonical group answer does not broaden FLOW read admission`() {
        for (supplyFirst in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val owner = f.method("reader", listOf(emptyList()), 0).key()
            val g = f.ap.marks.groupOf(f.mark("T")); val position = field(f, "f")
            val descendant = position.copy(fact = position.fact.copy(path = position.fact.path + f.field("child")))
            val exact = position.copy(fact = position.fact.copy(tail = Tail.EXACT))
            val service = Run1StaticService(f.ap, MarkMode.GROUP)
            val supply = StaticBatch().apply { supplies += RegionGroups(exact, f.ap.marks.atomsFor(GroupSet.of(g))) }
            val consumers = StaticBatch().apply {
                reads += StaticReadRegistration(owner, 1, descendant)
                interests += StaticInterest(owner, 2, position, f.ap.marks.atomsFor(GroupSet.of(g)))
            }
            assertTrue(service.commit(if (supplyFirst) supply else consumers).isEmpty())
            val replies = service.commit(if (supplyFirst) consumers else supply)
            assertTrue(replies.filterIsInstance<StaticOwnerEffect.ReadReady>().isEmpty())
            assertEquals(position, replies.filterIsInstance<StaticOwnerEffect.PointReady>().single().supply.region)
        }
    }

    @Test fun `nonstatic supplies are rejected before recording write presence`() {
        FiniteProgramFixtures().use { f ->
            val position = field(f, "f")
            val g = f.ap.marks.groupOf(f.mark("T"))
            val service = Run1StaticService(f.ap, MarkMode.GROUP)
            assertFailsWith<IllegalArgumentException> {
                service.commit(StaticBatch().apply {
                    supplies += RegionGroups(position.copy(fact = position.fact.copy(base = AccessPathBase.Argument(0))),
                        f.ap.marks.atomsFor(GroupSet.of(g)))
                })
            }
            assertEquals(0, service.statistics().writeRegions)
            assertEquals(0L, service.statistics().suppliedGroups)
        }
    }

    @Test fun `the all-static writer route rejects an ordinary initial`() {
        FiniteProgramFixtures().use { f ->
            val owner = f.method("writer", listOf(emptyList()), 0).key()
            val position = field(f, "f")
            val ordinary = f.ap.initial(PathFact(AccessPathBase.Argument(0), emptyList(), Tail.STAR,
                MarkSlot.STAR).let { Pattern(it, ExclusionSet.Empty) })
            val service = Run1StaticService(f.ap, MarkMode.GROUP)
            assertFailsWith<IllegalStateException> {
                service.commit(StaticBatch().apply {
                    writers += StaticWriterRegistration(owner, 1, ordinary, position)
                })
            }
            assertEquals(0, service.statistics().writerRows)
        }
    }

    @Test fun `a nonstatic query is never rebased onto a static field`() {
        for (supplyFirst in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val owner = f.method("reader", listOf(emptyList()), 0).key()
            val position = field(f, "f")
            val g = f.ap.marks.groupOf(f.mark("T"))
            val nonstatic = position.copy(fact = position.fact.copy(base = AccessPathBase.Argument(0)))
            val service = Run1StaticService(f.ap, MarkMode.GROUP)
            val supply = StaticBatch().apply { supplies += RegionGroups(position, f.ap.marks.atomsFor(GroupSet.of(g))) }
            val interest = StaticBatch().apply { interests += StaticInterest(owner, 1, nonstatic, f.ap.marks.atomsFor(GroupSet.of(g))) }
            assertTrue(service.commit(if (supplyFirst) supply else interest).isEmpty())
            assertTrue(service.commit(if (supplyFirst) interest else supply).isEmpty())
            assertEquals(0L, service.requestStatistics().requested)
            assertEquals(0L, service.requestStatistics().processed)
        }
    }

}

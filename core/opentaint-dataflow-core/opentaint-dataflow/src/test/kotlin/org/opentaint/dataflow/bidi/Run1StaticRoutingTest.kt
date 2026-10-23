package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.engine.reference.UnoptimizedRun1StaticService
import org.opentaint.dataflow.bidi.fixtures.FiniteProgramFixtures
import kotlin.random.Random
import kotlin.test.*

/** Compare public effects and cumulative state with the frozen pre-optimization service. */
class Run1StaticRoutingTest {
    private class PairOfServices(f: FiniteProgramFixtures) {
        val optimized = Run1StaticService(f.ap, MarkMode.GROUP)
        private val reference = UnoptimizedRun1StaticService(f.ap)
        val eliminatedCandidateVisits: Long get() =
            reference.statistics().candidateVisits - optimized.statistics().candidateVisits

        fun commit(batch: StaticBatch) {
            assertEquals(reference.commit(batch), optimized.commit(batch), "Owner effects and their order")
            val old = reference.statistics()
            val now = optimized.statistics()
            assertEquals(listOf(old.writeRegions, old.suppliedRegions, old.suppliedGroups, old.readDescriptors,
                old.pointSubscriptions, old.writerRows, old.flowWriterRows, old.ownerReplies),
                listOf(now.writeRegions, now.suppliedRegions, now.suppliedGroups, now.readDescriptors,
                    now.pointSubscriptions, now.writerRows, now.flowWriterRows, now.ownerReplies),
                "All cumulative state observations except candidate visit diagnostics")
            val oldRequests = reference.requestStatistics()
            val nowRequests = optimized.requestStatistics()
            assertEquals(listOf(oldRequests.requested, oldRequests.reached, oldRequests.processed,
                oldRequests.pendingMemberships, oldRequests.writerAdmissions),
                listOf(nowRequests.requested, nowRequests.reached, nowRequests.processed,
                    nowRequests.pendingMemberships, nowRequests.writerAdmissions))
            assertTrue(now.candidateVisits <= old.candidateVisits)
        }

        fun clear() {
            reference.clear()
            optimized.clear()
            commit(StaticBatch())
        }
    }

    private class Vocabulary(val f: FiniteProgramFixtures) {
        val owner = f.method("routing", listOf(emptyList()), 0).key()
        val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
        val otherClass = f.ap.accessors.index(ClassStaticAccessor("D"))
        val field = f.field("f")
        val otherField = f.field("g")
        val child = f.field("child")
        val g = f.ap.marks.groupOf(f.mark("T"))
        val h = f.ap.marks.groupOf(f.mark("U"))
        val groups = f.ap.marks.atomsFor(GroupSet.of(listOf(g, h)))
        fun pattern(path: List<Int>, tail: Tail = Tail.STAR, exclusion: ExclusionSet = ExclusionSet.Empty) =
            Pattern(PathFact(STATIC, path, tail, MarkSlot.STAR), exclusion)
        val exactClass = pattern(listOf(cls), Tail.EXACT)
        val exactOtherClass = pattern(listOf(otherClass), Tail.EXACT)
        val fieldRegion = pattern(listOf(cls, field))
        val descendant = pattern(listOf(cls, field, child))
    }

    @Test fun `class requests and strict descendants remove only failed writer candidates in both orders`() {
        for (requestFirst in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val v = Vocabulary(f)
            val services = PairOfServices(f)
            val request = StaticBatch().apply {
                interests += StaticInterest(v.owner, 1, v.exactClass, v.groups)
            }
            val writers = StaticBatch().apply {
                repeat(64) { index ->
                    flowWriters += StaticFlowWriter(v.owner, 100L + index,
                        v.pattern(listOf(v.cls, f.field("field$index"))))
                }
                flowWriters += StaticFlowWriter(v.owner, 200, v.descendant)
            }
            services.commit(if (requestFirst) request else writers)
            services.commit(if (requestFirst) writers else request)
            assertEquals(0L, services.optimized.statistics().candidateVisits)
            assertEquals(65L, services.eliminatedCandidateVisits)
            assertEquals(2L, services.optimized.requestStatistics().pendingMemberships)
            assertEquals(0L, services.optimized.requestStatistics().writerAdmissions)
            services.commit(StaticBatch().apply {
                supplies += RegionGroups(v.fieldRegion, v.groups)
                supplies += RegionGroups(v.descendant, v.groups)
            })
            assertEquals(2L, services.optimized.requestStatistics().pendingMemberships,
                "Descendant group supplies cannot answer the exact class point")
            services.commit(StaticBatch().apply { supplies += RegionGroups(v.exactClass, v.groups) })
            assertEquals(0L, services.optimized.requestStatistics().pendingMemberships)
        }
    }

    @Test fun `root and same-path writers remain class candidates with complete matching`() {
        for (writerFirst in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val v = Vocabulary(f)
            val services = PairOfServices(f)
            val writers = StaticBatch().apply {
                flowWriters += StaticFlowWriter(v.owner, 1, v.pattern(emptyList()))
                flowWriters += StaticFlowWriter(v.owner, 2, v.exactClass)
                flowWriters += StaticFlowWriter(v.owner, 3, v.pattern(listOf(v.cls), Tail.ANY_TAINT))
                flowWriters += StaticFlowWriter(v.owner, 4, v.exactOtherClass)
                // Request matching ignores E. Prefix routing must keep that behavior.
                flowWriters += StaticFlowWriter(v.owner, 5,
                    v.pattern(emptyList(), Tail.STAR, ExclusionSet.of(v.cls)))
                flowWriters += StaticFlowWriter(v.owner, 6, v.pattern(emptyList(), Tail.EXACT))
            }
            val request = StaticBatch().apply {
                interests += StaticInterest(v.owner, 7, v.exactClass, f.ap.marks.atomsFor(GroupSet.of(v.g)))
            }
            services.commit(if (writerFirst) writers else request)
            services.commit(if (writerFirst) request else writers)
            assertEquals(4L, services.optimized.requestStatistics().writerAdmissions)
            services.commit(request)
            services.commit(writers)
            assertEquals(4L, services.optimized.requestStatistics().writerAdmissions,
                "Repeated registration/request retains askOnce admission")
        }
    }

    @Test fun `broad supplies retain exclusions partial group answers and late field initialization`() {
        for (supplyFirst in listOf(false, true)) for (blocked in listOf(false, true))
            for (tail in listOf(Tail.STAR, Tail.ANY, Tail.ANY_TAINT)) FiniteProgramFixtures().use { f ->
                val v = Vocabulary(f)
                val services = PairOfServices(f)
                val supply = StaticBatch().apply {
                    supplies += RegionGroups(v.pattern(listOf(v.cls), tail,
                        if (blocked) ExclusionSet.of(v.field) else ExclusionSet.Empty), f.ap.marks.atomsFor(GroupSet.of(v.g)))
                }
                val interests = StaticBatch().apply {
                    interests += StaticInterest(v.owner, 1, v.fieldRegion, v.groups)
                    interests += StaticInterest(v.owner, 2, v.exactClass, v.groups)
                    reads += StaticReadRegistration(v.owner, 3, v.fieldRegion)
                    reads += StaticReadRegistration(v.owner, 4, v.exactClass)
                }
                services.commit(if (supplyFirst) supply else interests)
                services.commit(if (supplyFirst) interests else supply)
                services.commit(supply)
                services.commit(StaticBatch().apply {
                    supplies += RegionGroups(v.exactClass, f.ap.marks.atomsFor(GroupSet.of(v.h)))
                    supplies += RegionGroups(v.fieldRegion, f.ap.marks.atomsFor(GroupSet.of(v.h)))
                })
                services.commit(StaticBatch().apply {
                    supplies += RegionGroups(v.pattern(emptyList()), f.ap.marks.atomsFor(GroupSet.of(v.g)))
                })
                assertEquals(0L, services.optimized.requestStatistics().pendingMemberships)
            }
    }

    @Test fun `mixed routing sequences preserve effects and states across batch boundaries and clear`() {
        for (seed in 0 until 8) for (chunkSize in listOf(1, 3, 9, 128)) FiniteProgramFixtures().use { f ->
            val v = Vocabulary(f)
            val services = PairOfServices(f)
            val regions = listOf(v.exactClass, v.exactOtherClass, v.fieldRegion,
                v.fieldRegion.copy(fact = v.fieldRegion.fact.copy(tail = Tail.EXACT)),
                v.descendant, v.pattern(listOf(v.cls, v.otherField)),
                v.pattern(listOf(v.cls), Tail.ANY_TAINT), v.pattern(emptyList()),
                v.pattern(listOf(v.cls), Tail.STAR, ExclusionSet.of(v.field)),
                v.pattern(emptyList(), Tail.STAR, ExclusionSet.of(v.cls)))
            val events = ArrayList<StaticBatch.() -> Unit>()
            regions.forEachIndexed { index, region ->
                events += { reads += StaticReadRegistration(v.owner, 100L + index, region) }
                events += { flowWriters += StaticFlowWriter(v.owner, 200L + index, region) }
                events += { writers += StaticWriterRegistration(v.owner, 300L + index,
                    f.ap.initial(v.fieldRegion), region) }
                events += { writes += region }
                events += { supplies += RegionGroups(region, f.ap.marks.atomsFor(GroupSet.of(v.g))) }
                events += { supplies += RegionGroups(region, f.ap.marks.atomsFor(GroupSet.of(v.h))) }
                if (region.fact.path.size >= 2 || region.fact.tail == Tail.EXACT && region.fact.path.size == 1) {
                    events += { interests += StaticInterest(v.owner, 400L + index, region, f.ap.marks.atomsFor(GroupSet.of(v.g))) }
                    events += { interests += StaticInterest(v.owner, 400L + index, region, v.groups) }
                }
            }
            val order = events.shuffled(Random(seed))
            fun replay() {
                for (chunk in order.chunked(chunkSize)) services.commit(StaticBatch().apply {
                    chunk.forEach { it() }
                })
                // Duplicate commits retain the reference service's pending/answered behavior.
                for (chunk in order.chunked(chunkSize)) services.commit(StaticBatch().apply {
                    chunk.forEach { it() }
                })
            }
            replay()
            services.clear()
            replay()
        }
    }
}

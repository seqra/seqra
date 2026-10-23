package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import kotlin.random.Random
import kotlin.test.*

/** Actual service and actor adapters for the checked finite supply model. */
class Run1TemporaryStaticSuppliesTest {
    private class Fixture : AutoCloseable {
        val f = MethodAnalysisFixtures()
        val ap get() = f.manager
        val cls = ap.accessors.index(ClassStaticAccessor("C"))
        val field = f.field("f")
        val other = f.field("g")
        val t = f.mark("T")
        val u = f.mark("U")
        val owner get() = f.key
        val fieldRegion get() = pattern(listOf(cls, field))
        val classRegion get() = pattern(listOf(cls), Tail.EXACT)
        fun pattern(path: List<Int>, tail: Tail = Tail.STAR,
                    exclusion: ExclusionSet = ExclusionSet.Empty) =
            Pattern(PathFact(STATIC, path, tail, MarkSlot.STAR), exclusion)
        fun batch() = StaticBatch(SupplyProfile.CANONICAL_RUN1_FULL, f.cancellation)
        override fun close() = f.close()
    }

    @Test fun `canonical temporary batch unions exact atoms and retains complete regions`() = Fixture().use { t ->
        val batch = t.batch()
        assertTrue(batch.isEmpty)
        batch.supplies += RegionGroups(t.fieldRegion, MarkSet.EMPTY)
        assertTrue(batch.isEmpty)
        repeat(1000) {
            batch.supplies += RegionGroups(t.fieldRegion, MarkSet.of(t.t))
            batch.supplies += RegionGroups(t.fieldRegion, MarkSet.of(t.t) + t.u)
        }
        val exact = t.pattern(listOf(t.cls, t.field), Tail.EXACT)
        val any = t.pattern(listOf(t.cls, t.field), Tail.ANY)
        val must = t.pattern(listOf(t.cls, t.field), Tail.ANY_TAINT)
        val other = t.pattern(listOf(t.cls, t.other))
        listOf(exact, any, must, other, t.classRegion).forEach {
            batch.supplies += RegionGroups(it, MarkSet.of(t.t))
        }
        assertFalse(batch.isEmpty)
        assertEquals(6, batch.supplies.size)
        assertEquals(MarkSet.of(t.t) + t.u, batch.supplies.single { it.region == t.fieldRegion }.groups)
        assertEquals(setOf(t.fieldRegion, exact, any, must, other, t.classRegion),
            batch.supplies.map { it.region }.toSet())
    }

    @Test fun `canonical keys own paths and reject broad or exclusion bearing supplies`() = Fixture().use { t ->
        val path = mutableListOf(t.cls, t.field)
        val batch = t.batch()
        batch.supplies += RegionGroups(t.pattern(path), MarkSet.of(t.t))
        path[1] = t.other
        batch.supplies += RegionGroups(t.fieldRegion, MarkSet.of(t.u))
        assertEquals(listOf(RegionGroups(t.fieldRegion, MarkSet.of(t.t) + t.u)), batch.supplies.toList())
        for (invalid in listOf(t.pattern(emptyList()), t.pattern(listOf(t.cls)),
                t.pattern(listOf(t.cls, t.field, t.other)),
                t.pattern(listOf(t.cls, t.field), exclusion = ExclusionSet.of(t.other)),
                Pattern(PathFact(AccessPathBase.Argument(0), emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty))) {
            assertFailsWith<IllegalArgumentException> { batch.supplies += RegionGroups(invalid, MarkSet.of(t.t)) }
        }
        assertEquals(1, batch.supplies.size, "Invalid values do not alter admitted geometry")
    }

    @Test fun `equal full names with different rule owners remain distinct memberships`() = Fixture().use { t ->
        var owner = "first-rule"
        val table = MarkTable(object : MarkDeclarations {
            override fun fullKey(name: String) = FullKey(encodeGroup(MarkGroupKey.Rule(owner), false), name)
        })
        val first = table.full("same-name")
        owner = "second-rule"
        val second = table.full("same-name")
        assertNotEquals(first, second)
        val batch = t.batch()
        batch.supplies += RegionGroups(t.fieldRegion, MarkSet.of(first))
        batch.supplies += RegionGroups(t.fieldRegion, MarkSet.of(second))
        assertEquals(listOf(RegionGroups(t.fieldRegion, MarkSet.of(first) + second)), batch.supplies.toList())
    }

    private fun atoms(effects: List<StaticOwnerEffect>): Set<Any> = buildSet {
        for (effect in effects) when (effect) {
            is StaticOwnerEffect.PointReady -> effect.supply.groups.forEach {
                add(listOf("point", effect.owner, effect.id, effect.supply.region, it))
            }
            is StaticOwnerEffect.WriterInterest -> effect.groups.forEach {
                add(listOf("writer", effect.owner, effect.id, it))
            }
            is StaticOwnerEffect.ReadReady -> add(effect)
        }
    }

    @Test fun `merged commits preserve atomic effects state and later requests in both arrival orders`() = Fixture().use { t ->
        for (seed in 0 until 8) for (supplyFirst in listOf(false, true)) {
            val ordered = Run1StaticService(t.ap)
            val merged = Run1StaticService(t.ap)
            val outputs = listOf(t.fieldRegion, t.pattern(listOf(t.cls, t.other), Tail.EXACT), t.classRegion)
            fun commit(oldBatch: StaticBatch, newBatch: StaticBatch) {
                assertEquals(atoms(ordered.commit(oldBatch)), atoms(merged.commit(newBatch)))
                val a = ordered.statistics(); val b = merged.statistics()
                assertEquals(a.copy(candidateVisits = 0, ownerReplies = 0), b.copy(candidateVisits = 0, ownerReplies = 0))
                assertEquals(ordered.requestStatistics(), merged.requestStatistics())
            }
            fun registrations(): StaticBatch = StaticBatch().apply {
                outputs.forEachIndexed { index, region ->
                    reads += StaticReadRegistration(t.owner, 10L + index, region)
                    interests += StaticInterest(t.owner, 20L + index, region, MarkSet.of(t.t) + t.u)
                    flowWriters += StaticFlowWriter(t.owner, 30L + index, region)
                }
            }
            val packets = (0 until 40).flatMap { outputs.map { p ->
                RegionGroups(p, if (it % 2 == 0) MarkSet.of(t.t) else MarkSet.of(t.u))
            } }.shuffled(Random(seed))
            fun supplies(profile: SupplyProfile) = StaticBatch(profile, t.f.cancellation).apply { supplies += packets }
            if (!supplyFirst) commit(registrations(), registrations())
            commit(supplies(SupplyProfile.ORDERED_REFERENCE), supplies(SupplyProfile.CANONICAL_RUN1_FULL))
            if (supplyFirst) commit(registrations(), registrations())
            commit(registrations(), registrations())
            commit(supplies(SupplyProfile.ORDERED_REFERENCE), supplies(SupplyProfile.CANONICAL_RUN1_FULL))
            val late = StaticBatch().apply {
                outputs.forEachIndexed { index, p ->
                    interests += StaticInterest(t.owner, 100L + index, p, MarkSet.of(t.t) + t.u)
                    flowWriters += StaticFlowWriter(t.owner, 200L + index, p)
                }
            }
            commit(late, late)
            assertEquals(0L, merged.requestStatistics().pendingMemberships)
        }
    }

    @Test fun `unrequested supplies do not wake existing writers and later writers see processed marks`() = Fixture().use { t ->
        val service = Run1StaticService(t.ap)
        service.commit(StaticBatch().apply { flowWriters += StaticFlowWriter(t.owner, 1, t.fieldRegion) })
        assertTrue(service.commit(t.batch().apply {
            supplies += RegionGroups(t.fieldRegion, MarkSet.of(t.t))
            supplies += RegionGroups(t.fieldRegion, MarkSet.of(t.t))
        }).isEmpty())
        assertEquals(1L, service.requestStatistics().reached)
        val requested = service.commit(StaticBatch().apply {
            interests += StaticInterest(t.owner, 2, t.fieldRegion, MarkSet.of(t.t))
        })
        assertEquals(1, requested.filterIsInstance<StaticOwnerEffect.PointReady>().size)
        assertEquals(1, requested.filterIsInstance<StaticOwnerEffect.WriterInterest>().size)
        assertEquals(0L, service.requestStatistics().pendingMemberships)
        assertEquals(1L, service.requestStatistics().processed)
        val late = service.commit(StaticBatch().apply { flowWriters += StaticFlowWriter(t.owner, 3, t.fieldRegion) })
        assertEquals(listOf(StaticOwnerEffect.WriterInterest(t.owner, 3, MarkSet.of(t.t))), late)
    }

    @Test fun `raw profile retains first hit geometry and exact original packet order`() = Fixture().use { t ->
        val p = t.pattern(listOf(t.cls), Tail.STAR, ExclusionSet.of(t.field))
        val q = t.classRegion
        val packets = listOf(RegionGroups(p, MarkSet.of(t.t)), RegionGroups(q, MarkSet.of(t.u)), RegionGroups(p, MarkSet.of(t.u)))
        val batch = StaticBatch().apply { supplies += packets }
        assertEquals(SupplyProfile.ORDERED_REFERENCE, batch.supplyProfile)
        assertEquals(packets, batch.supplies.toList())
        val service = Run1StaticService(t.ap)
        service.commit(StaticBatch().apply { interests += StaticInterest(t.owner, 1, q, MarkSet.of(t.u)) })
        val answer = service.commit(batch).filterIsInstance<StaticOwnerEffect.PointReady>().single()
        assertEquals(q, answer.supply.region)
        val reordered = Run1StaticService(t.ap)
        reordered.commit(StaticBatch().apply { interests += StaticInterest(t.owner, 1, q, MarkSet.of(t.u)) })
        val broadAnswer = reordered.commit(StaticBatch().apply {
            supplies += RegionGroups(p, MarkSet.of(t.t) + t.u)
            supplies += RegionGroups(q, MarkSet.of(t.u))
        }).filterIsInstance<StaticOwnerEffect.PointReady>().single()
        assertNotEquals(answer.supply.region, broadAnswer.supply.region,
            "Noncanonical broad inputs must not be regrouped")
    }

    @Test fun `actor selects FULL profile once and preserves it after queue empty commit`() = Fixture().use { t ->
        for (mode in MarkMode.entries) {
            val actor = t.f.core(RunConfig(1, 1, null, t.f.records,
                org.opentaint.dataflow.bidi.driver.SeedIndex.EMPTY, listOf(t.owner), run1MarkMode = mode))
            val expected = if (mode == MarkMode.FULL) SupplyProfile.CANONICAL_RUN1_FULL else SupplyProfile.ORDERED_REFERENCE
            assertEquals(expected, actor.staticBatch.supplyProfile)
            val old = actor.staticBatch
            actor.staticBatch.writes += t.fieldRegion
            t.f.drain(actor, quantum = 1)
            assertNotSame(old, actor.staticBatch)
            assertTrue(actor.staticBatch.isEmpty)
            assertEquals(expected, actor.staticBatch.supplyProfile)
        }
        val later = t.f.core(index = 2)
        assertEquals(SupplyProfile.ORDERED_REFERENCE, later.staticBatch.supplyProfile)
    }

    @Test fun `quantum yield retains canonical supply delta until the ordinary queue empty boundary`() = Fixture().use { t ->
        val actor = t.f.core(RunConfig(1, 1, null, t.f.records,
            org.opentaint.dataflow.bidi.driver.SeedIndex.EMPTY, listOf(t.owner), run1MarkMode = MarkMode.FULL))
        actor.addRootZero()
        val pending = actor.staticBatch
        pending.supplies += RegionGroups(t.fieldRegion, MarkSet.of(t.t))
        assertFalse(actor.work(1), "The remaining ordinary queue has not reached its barrier")
        assertSame(pending, actor.staticBatch)
        assertEquals(1, actor.staticBatch.supplies.size)
        assertEquals(0, checkNotNull(actor.runnerPort.staticService).statistics().writeRegions)
        t.f.drain(actor, quantum = 1)
        assertNotSame(pending, actor.staticBatch)
        assertTrue(actor.staticBatch.isEmpty)
        assertEquals(1, checkNotNull(actor.runnerPort.staticService).statistics().writeRegions)
    }

    @Test fun `aggregate counters describe offers and unique batch membership without retaining history`() = Fixture().use { t ->
        val statistics = N2Statistics()
        val batch = StaticBatch(SupplyProfile.CANONICAL_RUN1_FULL, t.f.cancellation, statistics)
        batch.supplies += RegionGroups(t.fieldRegion, MarkSet.EMPTY)
        batch.supplies += RegionGroups(t.fieldRegion, MarkSet.of(t.t))
        batch.supplies += RegionGroups(t.fieldRegion, MarkSet.of(t.t))
        batch.supplies += RegionGroups(t.fieldRegion, MarkSet.of(t.t) + t.u)
        val snapshot = statistics.snapshot()
        assertEquals(4L, snapshot.staticSupplyOffers)
        assertEquals(4L, snapshot.staticSupplyOfferedMembers)
        assertEquals(2L, snapshot.staticSupplyFreshMembers)
        assertEquals(1L, snapshot.staticSupplyRegionRows)
        Run1StaticService(t.ap).commit(batch)
        assertEquals(1L, statistics.snapshot().staticSupplyCommits)
        val replacement = StaticBatch(batch.supplyProfile, t.f.cancellation, statistics)
        assertTrue(replacement.isEmpty)
        replacement.supplies += RegionGroups(t.fieldRegion, MarkSet.of(t.t))
        assertEquals(3L, statistics.snapshot().staticSupplyFreshMembers,
            "Fresh membership is batch-local; no answered history or global admission gate is added")
    }

    @Test fun `canonical offers and iteration remain cancellable`() = Fixture().use { t ->
        val batch = t.batch()
        batch.supplies += RegionGroups(t.fieldRegion, MarkSet.of(t.t))
        t.f.cancellation.cancel()
        assertFailsWith<org.opentaint.dataflow.util.Cancellation.Cancelled> {
            batch.supplies += RegionGroups(t.fieldRegion, MarkSet.of(t.u))
        }
        assertFailsWith<org.opentaint.dataflow.util.Cancellation.Cancelled> { batch.supplies.toList() }
    }
}

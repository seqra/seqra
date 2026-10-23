package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.HandOff
import org.opentaint.dataflow.bidi.driver.ReportBuilder
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.RunSummaryStore
import kotlin.test.*

/** Compare the retained reference adapters and independently stated demand pairs. */
class Run1StaticGroupProtocolRemovalTest {
    private val returned = AccessPathBase.Return
    private val argument = AccessPathBase.Argument(0)
    private val alias = AccessPathBase.Argument(1)

    private class Fixture(val exactClass: Boolean = false, disabled: Boolean = false) : AutoCloseable {
        private val returned = AccessPathBase.Return
        private val argument = AccessPathBase.Argument(0)
        private val alias = AccessPathBase.Argument(1)
        val f = MethodAnalysisFixtures()
        val config = RunConfig(1, 1, null, f.records, SeedIndex.EMPTY, listOf(f.key),
            disableRun1Statics = disabled, run1MarkMode = MarkMode.GROUP)
        val actor = f.core(config, localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
            callFactory = MethodCallServicesFactory(::RunMethodCallServices))
        val service = checkNotNull(actor.runnerPort.staticService)
        val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
        val path = if (exactClass) listOf(cls) else listOf(cls, f.field("from"))
        val target = listOf(cls, f.field("target"))
        val group = f.manager.marks.groupOf(f.mark("T"))
        val atom = f.manager.marks.group(group)
        val tail = if (exactClass) Tail.EXACT else Tail.STAR
        val initial = f.manager.initial(f.pattern(STATIC, path, tail, MarkSlot.Concrete(atom)))
        val observer = f.store.key("external observer")
        val region = f.pattern(STATIC, path, tail, MarkSlot.STAR)

        /** An actual source supply and first request establish the production precondition. */
        fun ground(): RegionGroups {
            val result = service.commit(StaticBatch().apply {
                supplies += RegionGroups(region, f.manager.marks.atomsFor(GroupSet.of(group)))
                interests += StaticInterest(observer, 10, region, f.manager.marks.atomsFor(GroupSet.of(group)))
            }).filterIsInstance<StaticOwnerEffect.PointReady>().single()
            assertEquals(observer, result.owner)
            return result.supply
        }

        fun summary(vararg edges: PathEdge) = MicroEdgeBuilder().apply { edges.forEach(::edge) }.buildStage()

        fun callPlan() {
            f.call(1, CallPlan(setOf(AccessPathBase.Zero, returned, argument, alias, STATIC), listOf(
                CallStage.Callees(CallPoint.ADDED, CallPoint.RETURNED, listOf(f.store.key("callee"))),
                CallStage.Actions(CallPoint.RETURNED, CallPoint.REWRITTEN, emptyList()),
                CallStage.Edges(CallPoint.REWRITTEN, CallPoint.AFTER, StageKind.BIND_BACK,
                    summary(starEdge(returned, emptyList(), argument, emptyList()))),
                CallStage.Edges(CallPoint.REWRITTEN, CallPoint.AFTER, StageKind.ALIASES,
                    summary(starEdge(returned, emptyList(), alias, emptyList())), Guard.MemoryEffect),
            ), emptyList(), CallPoint.BEFORE, CallPoint.AFTER))
        }

        fun subscription() = Subscription(f.store.key("callee"),
            CallerRef(actor.key, f.manager.zero, Layer.NORMAL, f.inst(1), 0), Layer.NORMAL, Reach.NORMAL)

        fun decoded(rows: Sequence<Pair<PremiseKey, Facts>>) = rows.flatMap { (p, value) ->
            f.ops.leaves(value).map { p to Conclusion(it.fact, it.exclusion, value.layer == Layer.DEMAND) }
        }.toSet()

        override fun close() = f.close()
    }

    @Test fun `known field and exact class inputs equal actual first pool replies for every scalar target tail`() {
        for (exactClass in listOf(false, true)) Fixture(exactClass).use { t ->
            val supply = t.ground()
            val actual = t.service.facts(supply.region, supply.groups)
            val known = t.actor.knownStaticInput(t.initial)
            assertEquals(actual, known)
            val expected = t.f.pattern(STATIC, t.path, if (exactClass) Tail.EXACT else Tail.ANY,
                MarkSlot.Concrete(t.atom))
            assertEquals(listOf(expected), t.f.ops.leaves(known).toList())
            assertEquals(Layer.DEMAND, known.layer)
            assertTrue(t.actor.hasKnownStaticGroupInput(t.initial))

            val outputGroup = t.f.groupMark(t.f.mark("output"))
            for (tail in listOf(Tail.EXACT, Tail.ANY, Tail.ANY_TAINT)) {
                val value = t.f.fact(returned, tail = tail, mark = outputGroup, layer = Layer.DEMAND)
                val reference = CollectingOut(); val candidate = CollectingOut()
                t.f.ops.applySummary(actual, t.initial, value, t.actor.mode, reference)
                t.f.ops.applySummary(known, t.initial, value, t.actor.mode, candidate)
                assertEquals(reference.results, candidate.results)
                assertEquals(setOf(value), candidate.results.toSet(), "The complete target tail/group survives")
                assertEquals(reference.markRequests, candidate.markRequests)
            }
        }
    }

    @Test fun `direct calls retain reference scalar outputs raw alias behavior and full handoff pairs`() {
        for (exactClass in listOf(false, true)) Fixture(exactClass).use { t ->
            t.ground(); t.callPlan()
            val left = t.f.field("left"); val right = t.f.field("right")
            val other = t.f.groupMark(t.f.mark("output"))
            val exact = t.f.fact(returned, listOf(left), Tail.EXACT, t.f.groupMark(), layer = Layer.DEMAND)
            val may = t.f.fact(returned, listOf(right), Tail.ANY, other, layer = Layer.DEMAND)
            // Different tail headers are separate publications, as in the actual summary store.
            val publications = listOf(Publication(t.initial, exact), Publication(t.initial, may))
            val part = t.subscription()
            val before = t.service.statistics().ownerReplies
            publications.forEach { t.actor.staticCallInterest(part, it, 0) }
            assertEquals(2, t.actor.staticBatch.interests.size, "Exercise the retained reference protocol")
            t.f.drain(t.actor, quantum = 1)
            val referenceRaw = t.decoded(t.actor.summaries.all())
            val referenceSelected = t.decoded(t.actor.summaries.demandEdges())
            assertEquals(2, t.service.statistics().ownerReplies - before)

            // A fresh caller actor has no old suffix results to mask an omitted result.
            val candidate = t.f.core(t.config, localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
                callFactory = MethodCallServicesFactory(::RunMethodCallServices), key = t.f.store.key("new caller"))
            val candidatePart = part.copy(ref = part.ref.copy(caller = candidate.key))
            val priorReplies = t.service.statistics().ownerReplies
            publications.forEach { candidate.applyStaticSummary(candidatePart, it, 0) }
            assertTrue(candidate.staticBatch.interests.isEmpty())
            t.f.drain(candidate, quantum = 1)
            assertEquals(priorReplies, t.service.statistics().ownerReplies)
            assertEquals(referenceRaw, t.decoded(candidate.summaries.all()))
            assertEquals(referenceSelected, t.decoded(candidate.summaries.demandEdges()))
            val expected = setOf(argument, alias).flatMap { base -> listOf(
                t.initial to Conclusion(t.f.pattern(base, listOf(left), Tail.EXACT, t.f.groupMark()).fact,
                    ExclusionSet.Empty, true),
                t.initial to Conclusion(t.f.pattern(base, listOf(right), Tail.ANY, other).fact,
                    ExclusionSet.Empty, true)) }.toSet()
            assertEquals(expected, t.decoded(candidate.summaries.demandEdges()),
                "A genuine raw static-to-return effect still enables the alias arm")

            t.actor.freeze(); candidate.freeze()
            val result = RunResult(RunStatus.COMPLETE, listOf(t.actor, candidate), 1, Direction.FORWARD, t.f.vulnerabilities)
            val handOff = HandOff.of(t.config, result, ReportBuilder(), t.f.shared)
            for (actor in listOf(t.actor, candidate)) {
                val observed = expected.flatMap { (_, value) ->
                    handOff.demand.near(actor.key, Pattern(value.fact, value.exclusion))
                }.toSet()
                assertEquals(expected.map { (_, value) ->
                    DemandPattern(Pattern(value.fact, value.exclusion), t.initial.toPattern())
                }.toSet(), observed, "Every output retains this method's nonzero static exit")
            }
        }
    }

    @Test fun `duplicate direct call offers preserve semantic summaries and selected exits`() = Fixture().use { t ->
        t.ground(); t.callPlan()
        val pub = Publication(t.initial, t.f.run1Fact(returned))
        val part = t.subscription()
        t.actor.applyStaticSummary(part, pub, 0); t.f.drain(t.actor)
        val raw = t.decoded(t.actor.summaries.all())
        val demand = t.decoded(t.actor.summaries.demandEdges())
        repeat(4) { t.actor.applyStaticSummary(part, pub, 0) }
        assertTrue(t.actor.staticBatch.interests.isEmpty())
        t.f.drain(t.actor, quantum = 1)
        assertEquals(raw, t.decoded(t.actor.summaries.all()))
        assertEquals(demand, t.decoded(t.actor.summaries.demandEdges()))
    }

    @Test fun `computed output erases its method pair and only the output first request wakes writers`() {
        for (exactClass in listOf(false, true)) Fixture(exactClass).use { t ->
            t.ground()
            val outputFull = t.f.mark("output"); val output = t.f.manager.marks.groupOf(outputFull)
            val value = t.f.run1Fact(STATIC, t.target, Tail.ANY, outputFull)
            t.actor.promoteStaticSummary(t.initial, value)
            assertTrue(t.actor.staticBatch.interests.isEmpty(), "An answered original needs no unary input join")
            assertTrue(t.actor.staticBatch.supplies.isNotEmpty())
            val expected = emptySet<Pair<PremiseKey, Conclusion>>()
            assertEquals(expected, t.decoded(t.actor.summaries.demandEdges()))
            val before = t.service.statistics().ownerReplies
            t.f.drain(t.actor, quantum = 1)
            assertEquals(before, t.service.statistics().ownerReplies)
            val target = t.f.pattern(STATIC, t.target, Tail.STAR, MarkSlot.STAR)
            val writer = StaticFlowWriter(t.observer, 20, target)
            assertTrue(t.service.commit(StaticBatch().apply { flowWriters += writer }).isEmpty(),
                "An unrequested reached group must not wake a writer")
            val first = t.service.commit(StaticBatch().apply {
                interests += StaticInterest(t.observer, 21, target, t.f.manager.marks.atomsFor(GroupSet.of(output)))
            })
            assertEquals(listOf(t.f.manager.marks.atomsFor(GroupSet.of(output))), first.filterIsInstance<StaticOwnerEffect.PointReady>().map { it.supply.groups })
            assertEquals(listOf(20L), first.filterIsInstance<StaticOwnerEffect.WriterInterest>().map { it.id })
            val repeated = t.service.commit(StaticBatch().apply {
                interests += StaticInterest(t.observer, 22, target, t.f.manager.marks.atomsFor(GroupSet.of(output)))
            })
            assertEquals(1, repeated.filterIsInstance<StaticOwnerEffect.PointReady>().size)
            assertTrue(repeated.none { it is StaticOwnerEffect.WriterInterest })
            val late = t.service.commit(StaticBatch().apply {
                flowWriters += StaticFlowWriter(t.observer, 23, target)
            })
            assertEquals(listOf(23L), late.filterIsInstance<StaticOwnerEffect.WriterInterest>().map { it.id })
            assertEquals(expected, t.decoded(t.actor.summaries.demandEdges()))
        }
    }

    @Test fun `accepted exact payload remains selectable after same-path marks and broader tails arrive`() = Fixture().use { t ->
        val store = RunSummaryStore(t.f.manager)
        val exact = t.f.run1Fact(STATIC, t.target)
        val selected = t.f.ops.leaves(exact).single()
        assertNotNull(store.add(t.initial, exact))
        for (value in listOf(t.f.run1Fact(STATIC, t.target, full = t.f.mark("output")),
            t.f.run1Fact(STATIC, t.target, Tail.ANY_TAINT), t.f.run1Fact(STATIC, t.target, Tail.ANY))) {
            store.add(t.initial, value)
            assertTrue(store.borrow(t.initial).any { current -> t.f.ops.leaves(current).any { it == selected } },
                "Incoming subsumption and delta subtraction cannot remove the accepted exact leaf")
        }
    }

    @Test fun `exact field fallback still waits and static ablation precedes the direct branch`() {
        Fixture().use { t ->
            t.callPlan()
            val exactField = t.f.manager.initial(t.initial.toPattern().copy(fact = t.initial.toPattern().fact.copy(tail = Tail.EXACT)))
            assertFalse(t.actor.hasKnownStaticGroupInput(exactField))
            t.actor.applyStaticSummary(t.subscription(), Publication(exactField, t.f.run1Fact(returned)), 0)
            assertEquals(1, t.actor.staticBatch.interests.size)
            t.f.drain(t.actor)
            assertTrue(t.actor.summaries.all().none())
        }
        for (exactClass in listOf(false, true)) Fixture(exactClass, disabled = true).use { t ->
            t.ground(); t.callPlan()
            t.actor.applyStaticSummary(t.subscription(), Publication(t.initial, t.f.run1Fact(returned)), 0)
            t.f.drain(t.actor)
            assertEquals(exactClass, t.actor.summaries.demandEdges().any(),
                "The diagnostic drops field premises but keeps exact global markers")
            assertTrue(t.actor.staticBatch.interests.isEmpty())
        }
    }
}

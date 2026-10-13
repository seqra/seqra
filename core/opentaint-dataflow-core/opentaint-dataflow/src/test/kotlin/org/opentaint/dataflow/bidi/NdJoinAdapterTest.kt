package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.fixtures.QueuedProtocolSteps
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.ConjunctionStore
import org.opentaint.dataflow.bidi.store.NdBinding
import kotlin.test.*

class NdJoinAdapterTest {
    private val firstBase = AccessPathBase.Argument(0)
    private val secondBase = AccessPathBase.Argument(1)
    private val result = AccessPathBase.Return

    private fun plan(callee: MethodKey): CallPlan = CallPlan(setOf(firstBase, secondBase, result), listOf(
        CallStage.Callees(CallPoint.ADDED, CallPoint.RETURNED, listOf(callee)),
        CallStage.Edges(CallPoint.RETURNED, CallPoint.AFTER, StageKind.BIND_BACK,
            StatementSummary.identities(setOf(result))),
    ), emptyList(), CallPoint.BEFORE, CallPoint.AFTER)

    private fun core(f: MethodAnalysisFixtures, index: Int = 1, limit: Int = 3,
                     key: MethodKey = f.key): RunMethodAnalyzer =
        f.core(index = index, L = limit, key = key,
            localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
            callFactory = MethodCallServicesFactory(::RunMethodCallServices))

    private fun part(f: MethodAnalysisFixtures, callee: MethodKey, premise: PremiseKey, added: Facts,
                     call: Int = 1, caller: MethodKey = f.key, callerLayer: Layer = Layer.NORMAL): Subscription =
        Subscription(callee, CallerRef(caller, premise, callerLayer, f.inst(call)), added.layer, added)

    @Test fun `matched root growth reuses E6 bindings while new full references and conclusions still transfer`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val j1 = f.manager.initial(f.pattern(firstBase, mark = t))
            val j2 = f.manager.initial(f.pattern(secondBase, mark = t))
            val premise = assertIs<PremiseSet>(f.manager.union(j1, j2))
            val callee = f.store.key(context = "callee")
            f.call(1, plan(callee))
            val actor = core(f)
            val publication = Publication(premise, f.fact(result, mark = t))
            val first = part(f, callee, j1, f.fact(firstBase, mark = t), callerLayer = Layer.DEMAND)
            val second = part(f, callee, j2, f.fact(secondBase, mark = t))
            actor.applySummary(first, publication, premise.members.indexOf(j1))
            actor.applySummary(second, publication, premise.members.indexOf(j2))
            f.drain(actor)
            val join = assertNotNull(actor.localServices.conjunctions).ndJoin<NdBinding>(
                ConjunctionStore.NdKey(callee, premise, publication.layer, first.ref.call))
            assertEquals(2, join.memberCount)
            assertEquals(1, join.combinationCount)
            val cuts = f.cuts.count { it.first == Cut.CALL }
            // Both roots match in run-1, but neither root contributes to combination transfer.
            actor.applySummary(first.copy(added = f.fact(firstBase, listOf(f.field("later")), mark = t)),
                publication, premise.members.indexOf(j1))
            f.drain(actor)
            assertEquals(2, join.memberCount)
            assertEquals(1, join.combinationCount)
            assertEquals(cuts, f.cuts.count { it.first == Cut.CALL })
            val alternate = f.manager.initial(f.pattern(AccessPathBase.Argument(2), mark = t))
            actor.applySummary(first.copy(ref = first.ref.copy(premise = alternate)), publication,
                premise.members.indexOf(j1))
            f.drain(actor)
            assertEquals(3, join.memberCount)
            assertEquals(2, join.combinationCount)
            val grown = Publication(premise, f.fact(result, listOf(f.field("returned")), mark = t))
            actor.applySummary(first, grown, premise.members.indexOf(j1))
            f.drain(actor)
            assertEquals(3, join.memberCount)
            assertEquals(2, join.combinationCount)
            val expectedPremises = setOf(premise, f.manager.union(alternate, j2))
            assertEquals(expectedPremises, methodEntries(f.ops, actor.summaries.all()).map { it.first }.toSet())
            assertTrue(methodEntries(f.ops, actor.summaries.all()).all { !it.second.demand })
            assertEquals(cuts + 3, f.cuts.count { it.first == Cut.CALL })
        }
    }

    @Test fun `nonmatching direct E6 input cannot become a normalized member binding`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val j1 = f.manager.initial(f.pattern(firstBase))
            val j2 = f.manager.initial(f.pattern(secondBase))
            val premise = assertIs<PremiseSet>(f.manager.union(j1, j2))
            val callee = f.store.key(context = "callee")
            f.call(1, plan(callee))
            val actor = core(f)
            val publication = Publication(premise, f.fact(result))
            actor.applySummary(part(f, callee, j1, f.fact(AccessPathBase.Argument(9))), publication,
                premise.members.indexOf(j1))
            actor.applySummary(part(f, callee, j2, f.fact(secondBase)), publication,
                premise.members.indexOf(j2))
            f.drain(actor)
            assertTrue(actor.summaries.all().none())
            assertTrue(f.cuts.none { it.first == Cut.CALL })
        }
    }

    @Test fun `actual E6 adapter waits for every member in either order and uses the shared local conjunction store`(): Unit {
        for (reverse in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val j1 = f.manager.initial(f.pattern(firstBase, mark = t))
            val j2 = f.manager.initial(f.pattern(secondBase, mark = u))
            val calleePremise = assertIs<PremiseSet>(f.manager.union(j1, j2))
            val callee = f.store.key(context = "callee")
            f.call(1, plan(callee))
            val actor = core(f)
            val localJoin = assertNotNull((actor.localServices as RunMethodLocalServices).conjunctions)
            val publication = Publication(calleePremise, f.fact(result, mark = v))
            val inputs = listOf(j1 to part(f, callee, j1, f.fact(firstBase, mark = t)),
                j2 to part(f, callee, j2, f.fact(secondBase, mark = u)))
            val order = if (reverse) inputs.asReversed() else inputs
            val first = order.first()
            actor.applySummary(first.second, publication, calleePremise.members.indexOf(first.first))
            f.drain(actor, quantum = 1)
            assertTrue(actor.summaries.all().none())
            assertTrue(f.publications.isEmpty())
            assertTrue(f.cuts.none { it.first == Cut.CALL })
            val second = order.last()
            actor.applySummary(second.second, publication, calleePremise.members.indexOf(second.first))
            f.drain(actor, quantum = 1)
            assertEquals(setOf(calleePremise to Conclusion(PathFact(result, emptyList(), Tail.EXACT, v), ExclusionSet.Empty, false)),
                methodEntries(f.ops, actor.summaries.all()))
            assertSame(localJoin, (actor.localServices as RunMethodLocalServices).conjunctions)
            assertEquals(setOf(Conclusion(PathFact(result, emptyList(), Tail.EXACT, v), ExclusionSet.Empty, false)),
                methodLeaves(f.ops, f.cuts.filter { it.first == Cut.CALL }.map { it.second }))
            assertEquals(0L, actor.recordCrossings)
            assertEquals(0L, actor.demandLayerEdges)
            assertTrue(f.events.isEmpty())
        }
    }

    @Test fun `actual summary replay and delayed delivery complete E6 in either order without intermediate closure or duplicate cuts`(): Unit {
        for (publicationFirst in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val j1 = f.manager.initial(f.pattern(firstBase, mark = t))
            val j2 = f.manager.initial(f.pattern(secondBase, mark = u))
            val premise = assertIs<PremiseSet>(f.manager.union(j1, j2))
            val callee = f.store.key(context = "callee")
            f.call(1, plan(callee))
            val actor = core(f)
            val steps = QueuedProtocolSteps()
            val storage = SummaryStorage(callee, f.manager, f.ops, steps)
            val deliveries = ArrayList<RunEvent.Delivery>()
            val manager = SubscriptionManager(object : SubscriptionPort {
                override val config = actor.config
                override val ops = f.ops
                override val steps = steps
                override fun summaryStorage(key: MethodKey): SummaryStorage {
                    assertEquals(callee, key)
                    return storage
                }
                override fun applier(caller: MethodKey): SummaryApplier {
                    assertEquals(actor.key, caller)
                    return actor
                }
                override fun post(event: RunEvent.Delivery): Boolean { deliveries += event; return true }
            })
            val publication = Publication(premise, f.fact(result, mark = v))
            val first = part(f, callee, j1, f.fact(firstBase, mark = t))
            val second = part(f, callee, j2, f.fact(secondBase, mark = u))
            if (publicationFirst) storage.publish(listOf(publication))
            manager.subscribe(first)
            if (!publicationFirst) storage.publish(listOf(publication))
            steps.replayAll()
            steps.runNotifications()
            deliveries.forEach { manager.onDelivery(it.callee, it.publications) }
            deliveries.clear()
            f.drain(actor)
            assertTrue(actor.summaries.all().none())
            assertTrue(f.cuts.none { it.first == Cut.CALL })
            manager.subscribe(second)
            steps.replayAll()
            f.drain(actor)
            assertEquals(setOf(premise to Conclusion(PathFact(result, emptyList(), Tail.EXACT, v), ExclusionSet.Empty, false)),
                methodEntries(f.ops, actor.summaries.all()))
            val callCuts = f.cuts.count { it.first == Cut.CALL }
            manager.onDelivery(callee, listOf(publication, publication))
            manager.subscribe(first)
            manager.subscribe(second)
            steps.replayAll()
            f.drain(actor)
            assertEquals(callCuts, f.cuts.count { it.first == Cut.CALL })
            assertEquals(1, f.publications.flatten().size)
            manager.freeze()
        }
    }

    @Test fun `all E6 subscription combinations retain separate caller premise sets and join effective layers rather than caller layers`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val j1 = f.manager.initial(f.pattern(firstBase, mark = t))
            val j2 = f.manager.initial(f.pattern(secondBase, mark = u))
            val premise = assertIs<PremiseSet>(f.manager.union(j1, j2))
            val callerFirst = listOf(j1, f.manager.initial(f.pattern(AccessPathBase.Argument(2), mark = t)))
            val callerSecond = listOf(j2, f.manager.initial(f.pattern(AccessPathBase.Argument(3), mark = u)))
            val callee = f.store.key(context = "callee")
            f.call(1, plan(callee))
            val actor = core(f)
            val e = ExclusionSet.of(intArrayOf(f.field("blocked")))
            val target = PathFact(result, emptyList(), Tail.ANY_TAINT, v)
            val publication = Publication(premise, f.fact(result, tail = Tail.ANY_TAINT, mark = v, exclusion = e))
            for (k in 0..1) actor.applySummary(part(f, callee, callerFirst[k],
                f.fact(firstBase, mark = t, layer = Layer.entries[k]), callerLayer = Layer.DEMAND),
                publication, premise.members.indexOf(j1))
            f.drain(actor)
            assertTrue(actor.summaries.all().none())
            for (k in 0..1) actor.applySummary(part(f, callee, callerSecond[k],
                f.fact(secondBase, mark = u, layer = Layer.entries[k]), callerLayer = Layer.NORMAL),
                publication, premise.members.indexOf(j2))
            f.drain(actor, quantum = 1)
            val expected = buildSet {
                for (a in 0..1) for (b in 0..1) {
                    val demand = a == 1 || b == 1
                    add(f.manager.union(callerFirst[a], callerSecond[b]) to Conclusion(
                        target.copy(tail = if (demand) Tail.ANY else Tail.ANY_TAINT),
                        if (demand) ExclusionSet.Empty else e, demand))
                }
            }
            assertEquals(expected, methodEntries(f.ops, actor.summaries.all()))
            assertEquals(4, f.cuts.count { it.first == Cut.CALL })
            assertEquals(0L, actor.recordCrossings)
        }
    }

    @Test fun `E6 keys separate full callee contexts premise sets publication layers and call sites`(): Unit {
        for (dimension in listOf("context", "premise", "layer", "call"))
            MethodAnalysisFixtures(listOf(listOf(1, 2), listOf(3), listOf(3), emptyList()), normalExit = 3).use { f ->
                val t = MarkSlot.Concrete(f.mark("T"))
                val u = MarkSlot.Concrete(f.mark("U"))
                val v = MarkSlot.Concrete(f.mark("V"))
                val w = MarkSlot.Concrete(f.mark("W"))
                val j1 = f.manager.initial(f.pattern(firstBase, mark = t))
                val j2 = f.manager.initial(f.pattern(secondBase, mark = u))
                val other = f.manager.initial(f.pattern(secondBase, mark = w))
                val premiseA = assertIs<PremiseSet>(f.manager.union(j1, j2))
                val secondB = if (dimension == "premise") other else j2
                val premiseB = assertIs<PremiseSet>(f.manager.union(j1, secondB))
                val calleeA = f.store.key(context = "calleeA")
                val calleeB = if (dimension == "context") f.store.key(context = "calleeB") else calleeA
                val callB = if (dimension == "call") 2 else 1
                f.call(1, plan(calleeA))
                f.call(2, plan(calleeB))
                val actor = core(f)
                val field = f.field("a")
                val g = f.field("b")
                val layerB = if (dimension == "layer") Layer.DEMAND else Layer.NORMAL
                val publicationA = Publication(premiseA, f.fact(result, listOf(field), mark = v))
                val publicationB = Publication(premiseB, f.fact(result, listOf(g), mark = v, layer = layerB))
                actor.applySummary(part(f, calleeA, j1, f.fact(firstBase, mark = t)), publicationA, premiseA.members.indexOf(j1))
                actor.applySummary(part(f, calleeB, secondB, f.fact(secondBase, mark = secondB.mark), callB),
                    publicationB, premiseB.members.indexOf(secondB))
                f.drain(actor)
                assertTrue(actor.summaries.all().none(), dimension)
                assertTrue(f.cuts.none { it.first == Cut.CALL }, dimension)
                actor.applySummary(part(f, calleeA, j2, f.fact(secondBase, mark = u)), publicationA, premiseA.members.indexOf(j2))
                f.drain(actor)
                assertEquals(setOf(premiseA to Conclusion(PathFact(result, listOf(field), Tail.EXACT, v), ExclusionSet.Empty, false)),
                    methodEntries(f.ops, actor.summaries.all()), dimension)
                actor.applySummary(part(f, calleeB, j1, f.fact(firstBase, mark = t), callB), publicationB, premiseB.members.indexOf(j1))
                f.drain(actor)
                assertEquals(setOf(
                    premiseA to Conclusion(PathFact(result, listOf(field), Tail.EXACT, v), ExclusionSet.Empty, false),
                    premiseB to Conclusion(PathFact(result, listOf(g), Tail.EXACT, v), ExclusionSet.Empty, layerB == Layer.DEMAND)),
                    methodEntries(f.ops, actor.summaries.all()), dimension)
            }
    }

    @Test fun `zero subscriptions cannot fill a nonzero E6 slot and malformed direct zero contributions fail without completing it`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val j1 = f.manager.initial(f.pattern(firstBase))
            val j2 = f.manager.initial(f.pattern(secondBase))
            val premise = assertIs<PremiseSet>(f.manager.union(j1, j2))
            val callee = f.store.key(context = "callee")
            f.call(1, plan(callee))
            val actor = core(f)
            val publication = Publication(premise, f.fact(result))
            val zero = part(f, callee, f.manager.zero, Reach.NORMAL)
            assertNull(matches(zero, publication, premise.members.indexOf(j1), f.ops, actor.mode))
            assertNull(matches(zero.copy(zeroOnly = true), publication, premise.members.indexOf(j1), f.ops, actor.mode))
            assertFailsWith<IllegalStateException> { actor.applySummary(zero, publication, premise.members.indexOf(j1)) }
            assertFailsWith<IllegalStateException> { actor.applySummary(zero.copy(zeroOnly = true), publication, premise.members.indexOf(j1)) }
            actor.applySummary(part(f, callee, j2, f.fact(secondBase)), publication, premise.members.indexOf(j2))
            f.drain(actor)
            assertTrue(actor.summaries.all().none())
            assertTrue(f.cuts.none { it.first == Cut.CALL })
        }
    }

    @Test fun `a completed E6 result is cut after all members and a growing conclusion meets every standing combination`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val j1 = f.manager.initial(f.pattern(firstBase))
            val j2 = f.manager.initial(f.pattern(secondBase))
            val premise = assertIs<PremiseSet>(f.manager.union(j1, j2))
            val callee = f.store.key(context = "callee")
            f.call(1, plan(callee))
            val actor = core(f, limit = 1)
            val t = MarkSlot.Concrete(f.mark("T"))
            val path = listOf(f.field("f"), f.field("g"), f.field("h"))
            val e = ExclusionSet.of(intArrayOf(f.field("blocked")))
            val publication = Publication(premise, f.fact(result, path, Tail.ANY_TAINT, t, e))
            val first = part(f, callee, j1, f.fact(firstBase))
            val second = part(f, callee, j2, f.fact(secondBase))
            actor.applySummary(first, publication, premise.members.indexOf(j1))
            actor.applySummary(second, publication, premise.members.indexOf(j2))
            f.drain(actor)
            assertEquals(setOf(Conclusion(PathFact(result, path, Tail.ANY_TAINT, t), e, false)),
                methodLeaves(f.ops, f.cuts.filter { it.first == Cut.CALL }.map { it.second }))
            assertEquals(setOf(premise to Conclusion(PathFact(result, path.take(1), Tail.ANY, t), ExclusionSet.Empty, true)),
                methodEntries(f.ops, actor.summaries.all()))
            val next = Publication(premise, f.fact(result, mark = t))
            actor.applySummary(first, next, premise.members.indexOf(j1))
            f.drain(actor)
            assertEquals(setOf(
                premise to Conclusion(PathFact(result, path.take(1), Tail.ANY, t), ExclusionSet.Empty, true),
                premise to Conclusion(PathFact(result, emptyList(), Tail.EXACT, t), ExclusionSet.Empty, false)),
                methodEntries(f.ops, actor.summaries.all()))
            assertEquals(2, f.cuts.count { it.first == Cut.CALL })
        }
    }

    @Test fun `different caller method actors cannot complete E6 from each others partial inputs`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val j1 = f.manager.initial(f.pattern(firstBase))
            val j2 = f.manager.initial(f.pattern(secondBase))
            val premise = assertIs<PremiseSet>(f.manager.union(j1, j2))
            val callee = f.store.key(context = "callee")
            f.call(1, plan(callee))
            val left = core(f, key = f.store.key(context = "left"))
            val right = core(f, key = f.store.key(context = "right"))
            val publication = Publication(premise, f.fact(result))
            left.applySummary(part(f, callee, j1, f.fact(firstBase), caller = left.key), publication, premise.members.indexOf(j1))
            right.applySummary(part(f, callee, j2, f.fact(secondBase), caller = right.key), publication, premise.members.indexOf(j2))
            f.drain(left)
            f.drain(right)
            assertTrue(left.summaries.all().none())
            assertTrue(right.summaries.all().none())
            right.applySummary(part(f, callee, j1, f.fact(firstBase), caller = right.key), publication, premise.members.indexOf(j1))
            f.drain(right)
            assertTrue(left.summaries.all().none())
            assertEquals(setOf(premise to Conclusion(PathFact(result, emptyList(), Tail.EXACT, j1.mark), ExclusionSet.Empty, false)),
                methodEntries(f.ops, right.summaries.all()))
        }
    }
}

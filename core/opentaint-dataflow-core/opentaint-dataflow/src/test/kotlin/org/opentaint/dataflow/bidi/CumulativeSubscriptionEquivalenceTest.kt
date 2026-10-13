package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.RequestKind
import kotlin.test.*

/** Independent accepted-leaf history versus cumulative P6 delivery. No new storage/index is an oracle. */
class CumulativeSubscriptionEquivalenceTest {
    private val x = AccessPathBase.Argument(0)
    private val returned = AccessPathBase.Return

    private fun raw(base: AccessPathBase, path: List<Int>, tail: Tail, mark: MarkSlot,
                    e: ExclusionSet = ExclusionSet.Empty, demand: Boolean = false) =
        Conclusion(PathFact(base, path, tail, mark), e, demand)

    private fun conclusions(ops: ApOps, values: Collection<Facts>): List<Conclusion> = values.flatMap { facts ->
        ops.leaves(facts).map { Conclusion(it.fact, it.exclusion, facts.layer == Layer.DEMAND) }.toList()
    }

    @Test
    fun `cumulative matching and summary transfer equal independent raw history across current AP modes and annotations`(): Unit {
        val f = StoreFixtures()
        val a = f.field("a")
        val b = f.field("b")
        val t = f.mark("T")
        val u = f.mark("U")
        val r = f.mark("result")
        val mt = MarkSlot.Concrete(t)
        val mu = MarkSlot.Concrete(u)
        val e = ExclusionSet.of(b)
        val modes = listOf(ApMode(true, Direction.FORWARD, 3), ApMode(false, Direction.FORWARD, 3),
            ApMode(false, Direction.BACKWARD, 3))
        val universe = BoundedDenotation(listOf(x, returned, AccessPathBase.Zero),
            listOf(a, b, f.field("fresh")), listOf(t, u, r, f.mark("fresh"), TaintMark.ZERO), 3)
        val oracle = CumulativeSubscriptionReference(universe)
        val histories = listOf(
            listOf(raw(x, emptyList(), Tail.EXACT, mt), raw(x, listOf(a), Tail.EXACT, mt), raw(x, listOf(b), Tail.EXACT, mu)),
            listOf(raw(x, emptyList(), Tail.ANY_TAINT, mt, e), raw(x, listOf(a), Tail.EXACT, mu), raw(x, listOf(b), Tail.ANY_TAINT, mu, e)),
            listOf(raw(x, emptyList(), Tail.ANY, mt, demand = true), raw(x, listOf(a), Tail.EXACT, mu, demand = true), raw(x, listOf(b), Tail.ANY, mu, demand = true)),
            listOf(raw(x, emptyList(), Tail.STAR, MarkSlot.STAR, e), raw(x, listOf(a), Tail.STAR, MarkSlot.STAR, e), raw(x, listOf(b), Tail.STAR, MarkSlot.STAR, e)),
            listOf(raw(x, emptyList(), Tail.STAR, MarkSlot.Star(MarkSet(intArrayOf(t.id)))),
                raw(x, listOf(a), Tail.STAR, MarkSlot.Star(MarkSet(intArrayOf(t.id)))),
                raw(x, listOf(b), Tail.STAR, MarkSlot.Star(MarkSet(intArrayOf(t.id))))),
            listOf(raw(x, emptyList(), Tail.ANY, MarkSlot.STAR, demand = true),
                raw(x, listOf(a), Tail.ANY, MarkSlot.STAR, demand = true), raw(x, listOf(b), Tail.ANY, MarkSlot.STAR, demand = true)),
        )
        val patterns = buildList {
            for (path in listOf(emptyList(), listOf(a), listOf(b))) {
                add(f.pattern(x, path, Tail.STAR, MarkSlot.STAR))
                add(f.pattern(x, path, Tail.STAR, MarkSlot.STAR, e))
                for (mark in listOf(mt, mu)) for (tail in listOf(Tail.STAR, Tail.EXACT, Tail.ANY, Tail.ANY_TAINT))
                    add(f.pattern(x, path, tail, mark, if (tail == Tail.ANY_TAINT) e else ExclusionSet.Empty))
            }
        }
        for (mode in modes) for ((case, accepted) in histories.withIndex()) {
            // Must inputs cannot occur in a current backward run (W8d).
            if (mode.direction == Direction.BACKWARD && accepted.any { it.fact.tail == Tail.ANY_TAINT }) continue
            val callerPremise = if (accepted.first().fact.mark is MarkSlot.Star)
                f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)) else f.manager.zero
            val ref = CallerRef(f.key("caller-$case"), callerPremise, Layer.DEMAND, f.inst(1))
            val callee = f.key("callee-$case")
            for (pattern in patterns) {
                if (pattern.fact.tail == Tail.ANY_TAINT && !(mode.restricted && mode.direction == Direction.FORWARD)) continue
                val j = f.manager.initial(pattern)
                val targets = if (pattern.fact.mark is MarkSlot.Star) listOf(
                    raw(returned, listOf(a), Tail.STAR, MarkSlot.STAR, e),
                    raw(returned, listOf(a), Tail.ANY, MarkSlot.Star(MarkSet(intArrayOf(u.id))), demand = true),
                ) else listOf(raw(returned, listOf(a), Tail.EXACT, MarkSlot.Concrete(r)),
                    raw(returned, listOf(a), Tail.ANY_TAINT, MarkSlot.Concrete(r), e),
                    raw(returned, listOf(a), Tail.ANY, MarkSlot.Concrete(r), demand = true))
                for (target in targets) {
                    if (mode.direction == Direction.BACKWARD && target.fact.tail == Tail.ANY_TAINT) continue
                    val publication = Publication(j, f.manager.factsOf(target.fact, target.exclusion,
                        if (target.demand) Layer.DEMAND else Layer.NORMAL))
                    val storage = CalleeSubscriptions(f.ops)
                    val rawHistory = LinkedHashSet<Conclusion>()
                    for (size in 1..accepted.size) {
                        rawHistory += accepted[size - 1]
                        // Cumulative growth contains an old must leaf when a fresh exact delta has Empty E.
                        val input = cumulativeInput(f.manager, accepted.take(size))
                        val part = Subscription(callee, ref, input.layer, input)
                        assertNotNull(storage.add(part))
                        assertNull(storage.add(part))
                        val selected = storage.candidates(publication, 0, mode).toList().mapNotNull { candidate ->
                            matches(candidate, publication, 0, f.ops, mode)
                        }
                        val expectedSelected = rawHistory.filter { oracle.selects(pattern, it, mode.restricted) }
                        val header = setOf(callerPremise.toPattern())
                        assertEquals(oracle.denotation(header, expectedSelected),
                            oracle.denotation(header, conclusions(f.ops, selected)),
                            "selected case=$case size=$size mode=$mode j=$pattern")
                        val actual = ArrayList<Facts>()
                        for (value in selected) f.ops.applySummary(value, j, publication.conclusion, mode, object : ApOut {
                            override fun result(facts: Facts) { actual += facts }
                            override fun markRequest(mark: TaintMark) = error("A matched summary has no mark request")
                            override fun positionRequest(position: PathNode) = error("Summary application has no statement static exception")
                        })
                        val expected = expectedSelected.mapNotNull { leaf ->
                            when (val outcome = oracle.transfer(pattern, target, leaf, mode.restricted)) {
                                is EdgeOutcome.Fact -> outcome.conclusion
                                EdgeOutcome.None -> null
                                else -> error("A matched reference summary must transfer directly: $outcome")
                            }
                        }
                        assertEquals(oracle.denotation(header, expected), oracle.denotation(header, conclusions(f.ops, actual)),
                            "transfer case=$case size=$size mode=$mode j=$pattern target=$target")
                    }
                }
            }
        }
    }

    @Test
    fun `actual cumulative delivery followed by ordered cleaner alias and CALL cuts equals raw history at limits one two three`(): Unit {
        for (run in listOf(1, 3)) for (fieldLimit in listOf(1, 2, 3)) MethodAnalysisFixtures().use { f ->
            val a = f.field("a")
            val b = f.field("b")
            val t = f.mark("T")
            val u = f.mark("U")
            val star = MarkSlot.STAR
            val e = ExclusionSet.of(b)
            val j = f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = star))
            val callee = f.store.key("callee")
            val alias = AccessPathBase.Argument(2)
            val cleaner = Cleaner(returned, f.manager.path(listOf(a, b)), CleanReach.EXACT, t)
            val remap = StatementSummary(setOf(returned), listOf(
                MicroEdge.of(starEdge(returned, emptyList(), alias, listOf(a, b))),
                MicroEdge.of(keepEdge(returned))), emptyList(), emptyMap())
            val plan = CallPlan(setOf(x, returned, alias), listOf(
                CallStage.Callees(CallPoint.ADDED, CallPoint.RETURNED, listOf(callee)),
                CallStage.Actions(CallPoint.RETURNED, CallPoint.REWRITTEN, listOf(CleanStep.Clean(cleaner))),
                CallStage.Edges(CallPoint.REWRITTEN, CallPoint.AFTER, StageKind.BIND_BACK, remap),
            ), emptyList(), CallPoint.BEFORE, CallPoint.AFTER)
            f.call(1, plan)
            val actor = f.core(index = run, L = fieldLimit,
                localFactory = MethodLocalServicesFactory { core ->
                    val delegate = RunMethodLocalServices(core)
                    object : MethodLocalServices by delegate {
                        override fun addRequest(premise: InitialAp, kind: RequestKind) {
                            f.requests += premise to kind
                            delegate.addRequest(premise, kind)
                        }
                    }
                },
                callFactory = MethodCallServicesFactory(::RunMethodCallServices))
            val steps = QueuedProtocolSteps()
            val storage = SummaryStorage(callee, f.manager, f.ops, steps)
            val deliveries = ArrayList<RunEvent.Delivery>()
            val manager = SubscriptionManager(object : SubscriptionPort {
                override val config = actor.config
                override val ops = f.ops
                override val steps = steps
                override fun summaryStorage(key: MethodKey): SummaryStorage = storage
                override fun applier(caller: MethodKey): SummaryApplier = actor
                override fun post(event: RunEvent.Delivery): Boolean { deliveries += event; return true }
            })
            val accepted = listOf(raw(x, emptyList(), Tail.STAR, star, e),
                raw(x, listOf(a), Tail.STAR, star, e), raw(x, listOf(b), Tail.STAR, star, e))
            val callerPremise = j
            val ref = CallerRef(actor.key, callerPremise, Layer.NORMAL, f.inst(1))
            for (size in 1..accepted.size) manager.subscribe(Subscription(callee, ref, Layer.NORMAL,
                cumulativeInput(f.manager, accepted.take(size))))
            // The publication arrives after all growth. Notification uses current subscriptions (P6),
            // while delayed replays retain their own fresh deltas. Compare both schedules' effect union.
            val target = raw(returned, listOf(a), Tail.STAR, star, e)
            val pub = Publication(j, f.manager.factsOf(target.fact, target.exclusion, Layer.NORMAL))
            storage.publish(listOf(pub))
            steps.runNotifications()
            deliveries.toList().forEach { manager.onDelivery(it.callee, it.publications) }
            steps.replayAll()
            f.drain(actor, quantum = 1)
            val universe = BoundedDenotation(listOf(x, returned, alias), listOf(a, b, f.field("fresh")),
                listOf(t, u, f.mark("fresh")), 4)
            val oracle = CumulativeSubscriptionReference(universe)
            val header = setOf(j.toPattern())
            val expectedRequests = LinkedHashSet<Pair<Set<Pattern>, RequestKind>>()
            val reference = FormsReference(f.ops, ReferenceAlgebra(actor.mode, manager = f.manager))
            val expected = ArrayList<Conclusion>()
            for (leaf in accepted.filter { oracle.selects(j.toPattern(), it, actor.mode.restricted) }) {
                val outcome = oracle.transfer(j.toPattern(), target, leaf, actor.mode.restricted)
                if (outcome !is EdgeOutcome.Fact) continue
                val hooks = PlanHooks(clean = { cl, item ->
                    val out = cleanRes(cl, item.c)
                    if (actor.mode.run1 && out.request != null) expectedRequests += item.premise to RequestKind.Mark(out.request)
                    out.facts.map { item.copy(c = it) }
                }, exit = { it.copy(c = limit(it.c, fieldLimit)) })
                expected += reference.run(plan, listOf(PlanItem(header, outcome.conclusion, false)),
                    Place(f.inst(1), statementEdge = false, sources = false), hooks, from = CallPoint.RETURNED)
                    .filter { it.first == CallPoint.AFTER }.map { it.second.c }
            }
            val actual = actor.summaries.all().filter { it.first === callerPremise }.map { it.second }.toList()
            assertEquals(oracle.denotation(header, expected), oracle.denotation(header, conclusions(f.ops, actual)),
                "run=$run L=$fieldLimit")
            assertEquals(expectedRequests, f.requests.map { setOf(it.first.toPattern()) to it.second }.toSet())
            assertEquals(if (run == 1) setOf(t) else emptySet(), f.requests.map { (it.second as RequestKind.Mark).mark }.toSet())
            assertTrue(actual.isNotEmpty())
            manager.freeze()
        }
    }
}

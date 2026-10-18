package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class CallPlanTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val alias = AccessPathBase.Argument(7)

    private fun summary(vararg edges: PathEdge): StatementSummary = StatementSummary(
        edges.flatMap { listOf(it.from.base, it.to.base) }.toSet(), edges.map(MicroEdge::of),
        emptyList(), emptyMap())

    private fun straight(pass: StatementSummary = summary(keepEdge(x), ZERO_KEEP),
                         actions: List<CleanStep> = emptyList(),
                         returned: List<CleanStep> = emptyList(), aliases: Boolean = false): CallPlan {
        val stages = arrayListOf<CallStage>(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, actions),
            CallStage.Edges(ADDED, RETURNED, UNRESOLVED, pass),
            CallStage.Actions(RETURNED, REWRITTEN, returned),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(x), ZERO_KEEP)),
        )
        if (aliases) stages += CallStage.Edges(REWRITTEN, AFTER, ALIASES,
            summary(starEdge(x, emptyList(), alias, emptyList())), Guard.MemoryEffect)
        return CallPlan(setOf(x), stages, emptyList(), BEFORE, AFTER)
    }

    private fun service(f: MethodAnalysisFixtures, index: Int = 1, L: Int = 3): Pair<RunMethodAnalyzer, RunMethodCallServices> {
        lateinit var service: RunMethodCallServices
        val core = f.core(index = index, L = L, callFactory = MethodCallServicesFactory {
            RunMethodCallServices(it).also { result -> service = result }
        })
        return core to service
    }

    private fun stored(f: MethodAnalysisFixtures, core: RunMethodAnalyzer, index: Int = 1): Set<Pair<Set<Pattern>, Conclusion>> =
        methodEntries(f.ops, core.edges.edgesAt(f.inst(if (index % 2 == 1) 2 else 0))).map {
            it.first.members.map(InitialAp::toPattern).toSet() to it.second
        }.toSet()

    private fun expected(f: MethodAnalysisFixtures, plan: CallPlan, premise: InitialAp, input: Conclusion,
                         index: Int = 1, L: Int = 3, atBound: (List<PlanItem>) -> List<Pair<SinkRule, Layer>> = { emptyList() }):
            Set<Pair<Set<Pattern>, Conclusion>> {
        val directed = if (index % 2 == 1) plan else plan.reversed()
        val mode = ApMode(index == 1, if (index % 2 == 1) Direction.FORWARD else Direction.BACKWARD, L)
        val hooks = PlanHooks(guards = index % 2 == 1, atBound = atBound,
            clean = { cl, item -> cleanRes(cl, item.c, run1 = index == 1).facts.map { item.copy(c = it) } },
            statementCut = { it.copy(c = limit(it.c, L)) }, exit = { it.copy(c = limit(it.c, L)) })
        return FormsReference(f.ops, ReferenceAlgebra(mode, manager = f.manager))
            .run(directed, listOf(PlanItem(setOf(premise.toPattern()), input, null)),
                Place(f.inst(1), index == 1, false), hooks).filter { it.first == directed.exit }
            .map { it.second.premise to it.second.c }.toSet()
    }

    @Test fun `cached pure call graph walks both directions with the complete reference fact and premise`(): Unit {
        for (index in listOf(1, 2, 3)) MethodAnalysisFixtures().use { f ->
            val path = listOf(f.field("f"), f.field("g"))
            val mark = MarkSlot.Concrete(f.mark("T"))
            val pattern = Pattern(PathFact(x, path, Tail.EXACT, mark), ExclusionSet.Empty)
            val initial = f.manager.initial(pattern)
            val input = f.fact(x, path, mark = mark)
            val plan = straight()
            f.call(1, plan)
            val (core, calls) = service(f, index)
            assertSame(core.forms.call(f.key, f.inst(1), f.callExpression),
                core.forms.call(f.key, f.inst(1), f.callExpression))
            calls.process(EdgeDelta(initial, f.inst(1), input))
            assertEquals(expected(f, plan, initial, Conclusion(pattern.fact, pattern.exclusion, false), index),
                stored(f, core, index))
            assertEquals(listOf(Cut.CALL), f.cuts.map { it.first })
            assertTrue(f.subscriptions.isEmpty() && f.sentLinks.isEmpty())
        }
    }

    @Test fun `untouched bases pass unchanged without cleaners call cuts subscriptions or alias guards`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val other = AccessPathBase.Argument(9)
            val mark = MarkSlot.Concrete(f.mark("T"))
            val input = f.fact(other, mark = mark)
            val initial = f.manager.initial(f.pattern(other, mark = mark))
            f.call(1, straight(actions = listOf(CleanStep.Clean(Cleaner(other, null,
                CleanReach.AT_AND_BELOW, null))), aliases = true))
            val (core, calls) = service(f)
            calls.process(EdgeDelta(initial, f.inst(1), input))
            assertTrue(stored(f, core).isEmpty(), "Unchanged propagation stays in the worklist")
            assertTrue(f.cuts.isEmpty())
            assertTrue(f.subscriptions.isEmpty() && f.sentLinks.isEmpty())
            f.drain(core)
            assertEquals(setOf(initial to Conclusion(PathFact(other, emptyList(), Tail.EXACT, mark),
                ExclusionSet.Empty, false)), methodEntries(f.ops, core.summaries.all()))
            assertTrue(f.processed.all { it.premise === initial && it.facts === input })
        }
    }

    @Test fun `resolved stage admits caller owned inputs with complete caller callee and stage coordinates`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val first = f.store.key("callee-one")
            val second = f.store.key("callee-two")
            val bind = AccessPathBase.Argument(1)
            val plan = CallPlan(setOf(x), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(starEdge(x, emptyList(), bind, emptyList()))),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Callees(ADDED, RETURNED, listOf(first, second)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(starEdge(bind, emptyList(), x, emptyList()))),
            ), emptyList(), BEFORE, AFTER)
            f.call(1, plan)
            val order = arrayListOf<String>()
            f.onSubscribe = { order += "subscribe:${it.callee.context}" }
            f.onLink = { order += "link:${it.callee.context}" }
            val path = listOf(f.field("f"))
            val mark = MarkSlot.Concrete(f.mark("T"))
            val input = f.fact(x, path, layer = Layer.DEMAND, mark = mark)
            val initial = f.manager.initial(f.pattern(x, path, mark = mark))
            val (core, calls) = service(f)
            calls.process(EdgeDelta(initial, f.inst(1), input))
            assertEquals(listOf("subscribe:${first.context}", "subscribe:${second.context}"), order)
            assertEquals(listOf(first, second), f.subscriptions.map { it.callee })
            for (part in f.subscriptions) {
                assertEquals(f.key, part.ref.caller)
                assertSame(initial, part.ref.premise)
                assertSame(f.inst(1), part.ref.call)
                assertEquals(Layer.DEMAND, part.ref.callerLayer)
                assertEquals(Layer.DEMAND, part.linkLayer)
                assertEquals(setOf(Conclusion(PathFact(bind, path, Tail.EXACT, mark), ExclusionSet.Empty, true)),
                    methodLeaves(f.ops, listOf(part.added)))
                assertFalse(part.zeroOnly)
            }
            assertTrue(f.sentLinks.isEmpty(), "Run-1 entry descriptors are dispatched by caller admission")
            assertTrue(f.subscriptions.all { it.stageFrom == ADDED && it.stageTo == RETURNED })
            assertTrue(stored(f, core).isEmpty())
            assertTrue(f.cuts.isEmpty(), "The suspended callee stage has no call result to cut")
        }
    }

    @Test fun `forward alias guard distinguishes normal identities demand identities and normal effects`(): Unit {
        for (layer in Layer.entries) for (effect in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val path = listOf(f.field("f"))
            val pass = if (effect) summary(PathEdge(PathFact(x, emptyList(), Tail.STAR, t),
                PathFact(x, path, Tail.EXACT, u), ExclusionSet.Empty)) else summary(keepEdge(x))
            val plan = straight(pass = pass, aliases = true)
            f.call(1, plan)
            val initial = f.manager.initial(f.pattern(x, path, mark = t))
            val input = Conclusion(PathFact(x, path, Tail.EXACT, t), ExclusionSet.Empty, layer == Layer.DEMAND)
            val (core, calls) = service(f)
            calls.process(EdgeDelta(initial, f.inst(1), f.fact(x, path, mark = t, layer = layer)))
            assertEquals(expected(f, plan, initial, input), stored(f, core))
            assertEquals(effect || layer == Layer.DEMAND, stored(f, core).any { it.second.fact.base == alias })
        }
    }

    @Test fun `reversed aliases and their independent pass over apply without the forward identity guard`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val plan = straight(aliases = true)
            f.call(1, plan)
            val path = listOf(f.field("f"))
            val mark = MarkSlot.Concrete(f.mark("T"))
            val initial = f.manager.initial(f.pattern(alias, path, mark = mark))
            val input = Conclusion(PathFact(alias, path, Tail.EXACT, mark), ExclusionSet.Empty, false)
            val (core, calls) = service(f, index = 2)
            calls.process(EdgeDelta(initial, f.inst(1), f.fact(alias, path, mark = mark)))
            assertEquals(expected(f, plan, initial, input, index = 2), stored(f, core, index = 2))
            assertEquals(setOf(x, alias), stored(f, core, index = 2).map { it.second.fact.base }.toSet())
        }
    }

    @Test fun `forward operand and result filters run at their stage while reversal drops both`(): Unit {
        for (resultFilter in listOf(false, true)) for (index in listOf(1, 2)) MethodAnalysisFixtures().use { f ->
            val reject = TypeFilter(object : FactTypeChecker.FactApFilter {
                override fun check(accessor: Accessor) = FactTypeChecker.FilterResult.Reject
            })
            val plain = summary(keepEdge(x))
            val filtered = StatementSummary(plain.touched, plain.edges, emptyList(),
                if (resultFilter) emptyMap() else mapOf(x to reject),
                if (resultFilter) mapOf(x to reject) else emptyMap())
            val plan = straight(pass = filtered)
            f.call(1, plan)
            val path = listOf(f.field("f"))
            val mark = MarkSlot.Concrete(f.mark("T"))
            val initial = f.manager.initial(f.pattern(x, path, mark = mark))
            val input = Conclusion(PathFact(x, path, Tail.EXACT, mark), ExclusionSet.Empty, false)
            val (core, calls) = service(f, index)
            calls.process(EdgeDelta(initial, f.inst(1), f.fact(x, path, mark = mark)))
            assertEquals(expected(f, plan, initial, input, index), stored(f, core, index))
            assertEquals(index == 2, stored(f, core, index).isNotEmpty())
            assertEquals(if (index == 2) listOf(Cut.CALL) else emptyList(), f.cuts.map { it.first })
        }
    }

    @Test fun `cleaner chains preserve their order and full must exclusion before the one final call cut`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val mark = MarkSlot.Concrete(f.mark("T"))
            val field = f.field("f")
            val e = ExclusionSet.of(f.field("already-excluded"))
            val first = CleanStep.Clean(Cleaner(x, f.manager.path(listOf(field)), CleanReach.AT_AND_BELOW, mark.mark))
            val other = CleanStep.Clean(Cleaner(x, null, CleanReach.AT_AND_BELOW, f.mark("other")))
            val plan = straight(actions = listOf(first, other))
            f.call(1, plan)
            val initial = f.manager.initial(f.pattern(x, mark = mark))
            val input = Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, mark), e, false)
            val (core, calls) = service(f)
            calls.process(EdgeDelta(initial, f.inst(1), f.fact(x, tail = Tail.ANY_TAINT, mark = mark, exclusion = e)))
            assertEquals(expected(f, plan, initial, input), stored(f, core))
            assertEquals(setOf(setOf(initial.toPattern()) to input.copy(exclusion = ExclusionSet.Empty, demand = true)),
                stored(f, core))
            assertEquals(listOf(Cut.CALL), f.cuts.map { it.first })
        }
    }

    @Test fun `kill actions use their keep edge geometry and do not apply a statement cut`(): Unit {
        for (excluded in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val field = f.field("f")
            val other = f.field("other")
            val step = CleanStep.Kill(summary(keepEdge(x, emptyList(), ExclusionSet.of(field))))
            val plan = straight(actions = listOf(step))
            f.call(1, plan)
            val path = listOf(if (excluded) field else other)
            val mark = MarkSlot.Concrete(f.mark("T"))
            val initial = f.manager.initial(f.pattern(x, path, mark = mark))
            val input = Conclusion(PathFact(x, path, Tail.EXACT, mark), ExclusionSet.Empty, false)
            val (core, calls) = service(f)
            calls.process(EdgeDelta(initial, f.inst(1), f.fact(x, path, mark = mark)))
            assertEquals(expected(f, plan, initial, input), stored(f, core))
            assertEquals(!excluded, stored(f, core).isNotEmpty())
            assertEquals(if (excluded) emptyList() else listOf(Cut.CALL), f.cuts.map { it.first })
        }
    }

    @Test fun `field actions run read clean write statement cuts and remove the temporary before storage`(): Unit {
        for (index in listOf(1, 2)) for (L in listOf(1, 2, 3)) MethodAnalysisFixtures().use { f ->
            val field = f.field("f")
            val g = f.field("g")
            val h = f.field("h")
            val temporary = AccessPathBase.CleanerTemporary()
            val step = CleanStep.Field(temporary, summary(starEdge(x, listOf(field), temporary, emptyList())),
                Cleaner(temporary, null, CleanReach.AT_AND_BELOW, f.mark("unrelated")),
                summary(starEdge(temporary, emptyList(), x, listOf(field))))
            val plan = straight(actions = listOf(step))
            f.call(1, plan)
            val mark = MarkSlot.Concrete(f.mark("T"))
            val path = listOf(field, g, h)
            val initial = f.manager.initial(f.pattern(x, path, mark = mark))
            val input = Conclusion(PathFact(x, path, Tail.EXACT, mark), ExclusionSet.Empty, false)
            val (core, calls) = service(f, index, L)
            calls.process(EdgeDelta(initial, f.inst(1), f.fact(x, path, mark = mark)))
            assertEquals(expected(f, plan, initial, input, index, L), stored(f, core, index))
            assertTrue(stored(f, core, index).none { it.second.fact.base == temporary })
            assertEquals(listOf(Cut.STATEMENT, Cut.STATEMENT, Cut.CALL), f.cuts.map { it.first })
        }
    }

    private fun sink(f: MethodAnalysisFixtures, end: PathEdge): SinkRule {
        val rule = object : CommonTaintConfigurationSink {
            override val id = "call-sink"
            override val meta = object : CommonTaintConfigurationSinkMeta {
                override val message = "call sink"
                override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
            }
        }
        return SinkRule(rule, 0, listOf(f.pattern(x)), listOf(MicroEdge.of(end)))
    }

    @Test fun `bound sink guards generate end facts with their effective layer and preserve the ordinary branch`(): Unit {
        for (layer in Layer.entries) for (matching in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val mark = if (matching) t else MarkSlot.Concrete(f.mark("other"))
            val u = MarkSlot.Concrete(f.mark("end"))
            val end = PathEdge(ZERO_FACT, PathFact(y, emptyList(), Tail.EXACT, u), ExclusionSet.Empty)
            val sink = sink(f, end)
            val field = f.field("end-path")
            val plan = CallPlan(setOf(x), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x))),
                CallStage.Edges(BOUND, REWRITTEN, END_FACTS, summary(end), Guard.SinkTriggered(sink)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Edges(ADDED, RETURNED, UNRESOLVED, summary(keepEdge(x))),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK,
                    summary(keepEdge(x), starEdge(y, emptyList(), x, listOf(field)))),
            ), listOf(sink), BEFORE, AFTER)
            f.call(1, plan)
            val initial = f.manager.initial(f.pattern(x, mark = mark))
            val input = Conclusion(PathFact(x, emptyList(), Tail.EXACT, mark), ExclusionSet.Empty, layer == Layer.DEMAND)
            val (core, calls) = service(f)
            calls.process(EdgeDelta(initial, f.inst(1), f.fact(x, mark = mark, layer = layer)))
            assertEquals(expected(f, plan, initial, input,
                atBound = { if (matching) listOf(sink to layer) else emptyList() }), stored(f, core))
            assertEquals(if (matching) listOf(sink to layer) else emptyList(), f.sinkEvaluations)
            val endResults = stored(f, core).filter { it.second.fact.mark == u }
            assertEquals(if (matching) 1 else 0, endResults.size)
            assertTrue(endResults.all { it.first == setOf(f.manager.zero.toPattern()) && it.second.demand == (layer == Layer.DEMAND) })
        }
    }

    @Test fun `constructor pass bypasses returned actions and remains a normal identity for aliases`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val mark = MarkSlot.Concrete(f.mark("T"))
            val plan = CallPlan(setOf(x), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x))),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Edges(ADDED, REWRITTEN, CONSTRUCTOR, summary(keepEdge(x))),
                CallStage.Actions(RETURNED, REWRITTEN,
                    listOf(CleanStep.Clean(Cleaner(x, null, CleanReach.AT_AND_BELOW, mark.mark)))),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(x))),
                CallStage.Edges(REWRITTEN, AFTER, ALIASES,
                    summary(starEdge(x, emptyList(), alias, emptyList())), Guard.MemoryEffect),
            ), emptyList(), BEFORE, AFTER)
            f.call(1, plan)
            val initial = f.manager.initial(f.pattern(x, mark = mark))
            val input = Conclusion(PathFact(x, emptyList(), Tail.EXACT, mark), ExclusionSet.Empty, false)
            val (core, calls) = service(f)
            calls.process(EdgeDelta(initial, f.inst(1), f.fact(x, mark = mark)))
            assertEquals(expected(f, plan, initial, input), stored(f, core))
            assertEquals(setOf(x), stored(f, core).map { it.second.fact.base }.toSet())
            assertEquals(listOf(Cut.CALL), f.cuts.map { it.first })
        }
    }

    @Test fun `synchronous subscription replay resumes at the stored callee stage without a raw run1 link`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val callee = f.store.key("callee")
            val bound = AccessPathBase.Argument(2)
            val plan = CallPlan(setOf(x), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(starEdge(x, emptyList(), bound, emptyList()))),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Callees(ADDED, RETURNED, listOf(callee)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(starEdge(y, emptyList(), x, emptyList()))),
            ), emptyList(), BEFORE, AFTER)
            f.call(1, plan)
            val mark = MarkSlot.Concrete(f.mark("T"))
            val initial = f.manager.initial(f.pattern(x, mark = mark))
            val native = f.manager.initial(f.pattern(bound, mark = mark))
            val (core, calls) = service(f)
            f.onSubscribe = { part ->
                calls.applySummary(part, Publication(native, f.fact(y, mark = mark)), 0)
                assertTrue(f.sentLinks.isEmpty())
                assertEquals(setOf(setOf(initial.toPattern()) to Conclusion(PathFact(x, emptyList(), Tail.EXACT, mark),
                    ExclusionSet.Empty, false)), stored(f, core))
            }
            f.onLink = { assertEquals(1, f.subscriptions.size) }
            calls.process(EdgeDelta(initial, f.inst(1), f.fact(x, mark = mark)))
            assertTrue(f.sentLinks.isEmpty())
            assertEquals(listOf(Cut.CALL), f.cuts.map { it.first })
        }
    }

    @Test fun `pre-cancelled call processing makes no cut subscription link or result and can retry`(): Unit {
        MethodAnalysisFixtures().use { f ->
            f.call(1, straight())
            val initial = f.manager.initial(f.pattern())
            val input = f.fact()
            val (core, calls) = service(f)
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { calls.process(EdgeDelta(initial, f.inst(1), input)) }
            assertTrue(f.cuts.isEmpty() && f.subscriptions.isEmpty() && f.sentLinks.isEmpty())
            f.cancellation.activate()
            calls.process(EdgeDelta(initial, f.inst(1), input))
            assertEquals(setOf(setOf(initial.toPattern()) to Conclusion(initial.toPattern().fact, ExclusionSet.Empty, false)),
                stored(f, core))
        }
    }

    @Test fun `cancellation during the first call result stops the remaining sibling stages before they cut`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val plan = CallPlan(setOf(x), listOf(
                CallStage.Edges(BEFORE, AFTER, UNRESOLVED, summary(keepEdge(x))),
                CallStage.Edges(BEFORE, AFTER, UNRESOLVED, summary(starEdge(x, emptyList(), alias, emptyList()))),
            ), emptyList(), BEFORE, AFTER)
            f.call(1, plan)
            val initial = f.manager.initial(f.pattern())
            val input = f.fact()
            val (_, calls) = service(f)
            f.onCut = { point, _ -> assertEquals(Cut.CALL, point); f.cancellation.cancel() }
            assertFailsWith<Cancellation.Cancelled> { calls.process(EdgeDelta(initial, f.inst(1), input)) }
            assertEquals(1, f.cuts.size)
        }
    }

    @Test fun `call service freeze drops the actor owner and rejects every later transfer`(): Unit {
        MethodAnalysisFixtures().use { f ->
            f.call(1, straight())
            val (core, calls) = service(f)
            calls.freeze()
            val owner = calls.javaClass.declaredFields.single { it.type == RunMethodAnalyzer::class.java }
                .apply { isAccessible = true }
            assertNull(owner.get(calls))
            val initial = f.manager.initial(f.pattern())
            assertFails { calls.process(EdgeDelta(initial, f.inst(1), f.fact())) }
            val part = Subscription(f.store.key("callee"), org.opentaint.dataflow.bidi.store.CallerRef(
                f.key, initial, Layer.NORMAL, f.inst(1)), Layer.NORMAL, f.fact())
            assertFails { calls.applySummary(part, Publication(initial, f.fact()), 0) }
            assertSame(f.key, core.key)
        }
    }

    @Test fun `deep sparse paths pass through cached call stages and the final cut without recursion`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val field = f.field("f")
            val path = List(32768) { field }
            val initial = f.manager.initial(f.pattern(x, path))
            f.call(1, straight())
            val (core, calls) = service(f, L = Int.MAX_VALUE)
            calls.process(EdgeDelta(initial, f.inst(1), f.fact(x, path)))
            assertEquals(setOf(setOf(initial.toPattern()) to Conclusion(initial.toPattern().fact, ExclusionSet.Empty, false)),
                stored(f, core))
            assertEquals(1, f.cuts.size)
        }
    }

    @Test fun `unresolved observation sees only new run one taint deltas and never selects propagation`(): Unit {
        for (index in listOf(1, 2, 3)) for (kind in FactsKind.entries) MethodAnalysisFixtures().use { f ->
            val plan = straight()
            f.call(1, plan)
            val initial = when (kind) {
                FactsKind.REACH -> f.manager.zero
                FactsKind.FLOW -> f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))
                FactsKind.TAINT -> f.manager.initial(f.pattern())
            }
            val input = when (kind) {
                FactsKind.REACH -> Reach.NORMAL
                FactsKind.FLOW -> f.fact(x, tail = Tail.STAR, mark = MarkSlot.STAR)
                FactsKind.TAINT -> f.fact()
            }
            val (core, calls) = service(f, index)
            repeat(2) { calls.process(EdgeDelta(initial, f.inst(1), input)) }
            assertEquals(if (index == 1 && kind == FactsKind.TAINT) 1 else 0, f.unresolvedCalls.size)
            if (f.unresolvedCalls.isNotEmpty()) {
                val observed = f.unresolvedCalls.single()
                assertSame(f.callExpression, observed.first)
                assertEquals(x, observed.second)
                assertSame(plan, observed.third)
                assertTrue(plan.passReads.isEmpty(), "Metadata is not a transfer filter")
            }
            if (index == 2 && kind == FactsKind.REACH) {
                assertTrue(stored(f, core, index).isEmpty(), "Backward zero passes through the unchanged worklist")
                assertTrue(f.cuts.none { it.first == Cut.CALL })
            }
            f.drain(core)
            assertTrue(core.summaries.all().any())
        }
    }

    @Test fun `unresolved observation retains added growth premise and call keys after cleaners`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val plan = straight()
            f.call(0, plan)
            f.call(1, plan)
            val (core, calls) = service(f)
            val j = f.manager.initial(f.pattern())
            val other = f.manager.initial(f.pattern(y))
            val input = f.fact()
            repeat(2) { calls.process(EdgeDelta(j, f.inst(1), input)) }
            calls.process(EdgeDelta(j, f.inst(1), f.fact(x, listOf(f.field("growth")))))
            calls.process(EdgeDelta(other, f.inst(1), input))
            calls.process(EdgeDelta(j, f.inst(0), input))
            assertEquals(4, f.unresolvedCalls.size)
            assertTrue(f.unresolvedCalls.all { it.second == x && it.third === plan })
            assertTrue(core.steps == 0L, "Direct service tests do not process the actor worklist")
        }
        MethodAnalysisFixtures().use { f ->
            val mark = f.mark("T")
            val plan = straight(actions = listOf(CleanStep.Clean(Cleaner(x, null, CleanReach.EXACT, mark))))
            f.call(1, plan)
            val (_, calls) = service(f)
            val j = f.manager.initial(f.pattern())
            calls.process(EdgeDelta(j, f.inst(1), f.fact()))
            assertTrue(f.unresolvedCalls.isEmpty(), "A dropped cleaner input never reaches ADDED")
            repeat(2) { calls.process(EdgeDelta(j, f.inst(1), f.fact(tail = Tail.ANY_TAINT))) }
            calls.process(EdgeDelta(j, f.inst(1), f.fact(tail = Tail.ANY, layer = Layer.DEMAND)))
            assertEquals(2, f.unresolvedCalls.size,
                "The same added demand fact has two distinct original caller layers")
        }
    }

    @Test fun `resolved only plans make no unresolved observation before or during subscription`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val plan = CallPlan(setOf(x), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x))),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Callees(ADDED, RETURNED, listOf(f.store.key("callee"))),
            ), emptyList(), BEFORE, AFTER)
            f.call(1, plan)
            f.onSubscribe = { assertTrue(f.unresolvedCalls.isEmpty()) }
            f.onLink = { assertTrue(f.unresolvedCalls.isEmpty()) }
            val (_, calls) = service(f)
            calls.process(EdgeDelta(f.manager.initial(f.pattern()), f.inst(1), f.fact()))
            assertEquals(1, f.subscriptions.size)
            assertTrue(f.unresolvedCalls.isEmpty())
        }
    }
}

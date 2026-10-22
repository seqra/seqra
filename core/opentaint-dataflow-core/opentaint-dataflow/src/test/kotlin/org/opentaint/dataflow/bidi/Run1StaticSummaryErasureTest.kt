package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.DemandStore
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Producer effects must not depend on storing a static/static summary. */
class Run1StaticSummaryErasureTest {
    private class Fixture(val markMode: MarkMode) : AutoCloseable {
        val f = MethodAnalysisFixtures()
        val actor = f.core(RunConfig(1, 1, null, f.records, SeedIndex.EMPTY, listOf(f.key),
            run1MarkMode = markMode),
            localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
            callFactory = MethodCallServicesFactory(::RunMethodCallServices))
        val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
        val from = listOf(cls, f.field("from"))
        val target = listOf(cls, f.field("target"))
        val input = f.manager.marks.runtimeAtom(f.mark("T"), markMode)
        val output = f.manager.marks.runtimeAtom(f.mark("U"), markMode)
        val service = checkNotNull(actor.runnerPort.staticService)
        val observer = f.store.key("observer")

        fun initial(path: List<Int> = from, tail: Tail = Tail.STAR, atom: TaintMark = input) =
            f.manager.initial(f.pattern(STATIC, path, tail, MarkSlot.Concrete(atom)))

        fun value(path: List<Int> = target, tail: Tail = Tail.ANY, atom: TaintMark = output) =
            f.fact(STATIC, path, tail, MarkSlot.Concrete(atom), layer = Layer.DEMAND)

        fun assertNoRows() {
            assertTrue(actor.summaries.all().none())
            assertTrue(actor.summaries.demandEdges().none())
            assertTrue(f.publications.flatten().isEmpty())
        }

        override fun close() = f.close()
    }

    @Test fun `field and exact class scalar effects survive without any raw selected or published row`() {
        for (mode in MarkMode.entries) for (exactClass in listOf(false, true))
            for (tail in listOf(Tail.EXACT, Tail.ANY, Tail.ANY_TAINT)) Fixture(mode).use { t ->
                val premise = t.initial(if (exactClass) listOf(t.cls) else t.from,
                    if (exactClass) Tail.EXACT else Tail.STAR)
                t.actor.promoteStaticSummary(premise, t.value(tail = tail))
                t.assertNoRows()
                assertTrue(t.actor.staticBatch.supplies.isNotEmpty())
                val expected = t.actor.staticBatch.supplies.toList()
                assertTrue(expected.all { it.groups == MarkSet.of(t.output) })
                assertTrue(t.actor.staticBatch.writes.isEmpty())
                assertTrue(t.actor.staticBatch.writers.isEmpty())
                assertTrue(t.actor.staticBatch.flowWriters.isEmpty())
                t.f.drain(t.actor)
                t.assertNoRows()
            }
    }

    @Test fun `fixed exact-field joins own deduplicated unanswered scalars after erasure`() {
        for (mode in MarkMode.entries) for (supplyFirst in listOf(false, true)) Fixture(mode).use { t ->
            val premise = t.initial(tail = Tail.EXACT)
            val actual = RegionGroups(t.f.pattern(STATIC, t.from, Tail.STAR, MarkSlot.STAR), MarkSet.of(t.input))
            if (supplyFirst) t.service.commit(StaticBatch().apply { supplies += actual })
            repeat(3) { t.actor.promoteStaticSummary(premise, t.value()) }
            assertEquals(1, t.actor.staticBatch.interests.size,
                "Equal unanswered joins have one complete scalar payload and one interest")
            t.assertNoRows()
            t.f.drain(t.actor)
            t.assertNoRows()
            val query = StaticBatch().apply {
                interests += StaticInterest(t.observer, 100, t.f.pattern(STATIC, t.target, Tail.STAR, MarkSlot.STAR),
                    MarkSet.of(t.output))
            }
            var answers = t.service.commit(query).filterIsInstance<StaticOwnerEffect.PointReady>()
                .filter { it.owner == t.observer }
            if (!supplyFirst) {
                assertTrue(answers.isEmpty(), "The erased conditional row must still await its actual input")
                for (effect in t.service.commit(StaticBatch().apply { supplies += actual }))
                    if (effect.owner == t.actor.key) t.actor.onStaticEffect(effect)
                t.f.drain(t.actor)
                answers = t.f.events.filterIsInstance<RunEvent.StaticOwner>().map { it.effect }
                    .filterIsInstance<StaticOwnerEffect.PointReady>().filter { it.owner == t.observer }
            }
            assertEquals(listOf(MarkSet.of(t.output)), answers.map { it.supply.groups })
            t.assertNoRows()
            assertTrue(t.actor.work(1), "An answered input join has no retained work")
            t.actor.promoteStaticSummary(premise, t.value())
            assertEquals(1, t.actor.staticBatch.interests.size,
                "The answered join was removed; no completed scalar archive suppresses a new offer")
            t.f.drain(t.actor)
            t.assertNoRows()
        }
    }

    @Test fun `first ordinary binding supplies its static output then erases the caller relation`() {
        for (mode in MarkMode.entries) Fixture(mode).use { t ->
            val formal = t.f.manager.initial(t.f.pattern(AccessPathBase.Argument(0), tail = Tail.STAR,
                mark = MarkSlot.Concrete(t.input)))
            val callerInitial = t.initial()
            // The caller keeps C.from as the relation original. BIND_IN has
            // already mapped its current value to the callee's argument.
            val actual = t.f.fact(AccessPathBase.Argument(0), tail = Tail.ANY,
                mark = MarkSlot.Concrete(t.input), layer = Layer.DEMAND)
            val part = Subscription(t.f.store.key("set"),
                CallerRef(t.actor.key, callerInitial, Layer.DEMAND, t.f.inst(1)), Layer.DEMAND, actual)
            // set(p) { C.target = p; } remains an ordinary nonstatic-to-static callee row.
            t.actor.promoteStaticSummary(formal, t.value(atom = t.input))
            assertTrue(t.actor.summaries.all().any { it.first == formal })
            assertTrue(t.actor.staticBatch.supplies.isEmpty(), "A hypothetical formal is not a source")
            t.actor.applySummary(part, Publication(formal, t.value(atom = t.input)), 0)
            assertTrue(t.actor.staticBatch.supplies.any { it.groups == MarkSet.of(t.input) })
            assertTrue(t.actor.summaries.all().none { it.first == callerInitial })
            assertTrue(t.actor.summaries.demandEdges().none { it.first == callerInitial })
            assertTrue(t.actor.staticBatch.writers.isEmpty())
            assertTrue(t.actor.staticBatch.flowWriters.isEmpty())
            t.f.drain(t.actor)
            assertTrue(t.f.publications.flatten().none { it.premise == callerInitial })
        }
    }

    @Test fun `run1 call application never relays an already static summary`() {
        for (mode in MarkMode.entries) Fixture(mode).use { t ->
            val part = Subscription(t.f.store.key("callee"),
                CallerRef(t.actor.key, t.f.manager.zero, Layer.NORMAL, t.f.inst(1)), Layer.NORMAL, Reach.NORMAL)
            val publication = Publication(t.initial(), t.value())
            t.actor.applyStaticSummary(part, publication, 0)
            t.actor.applySummary(part, publication, 0)
            assertTrue(t.actor.staticBatch.isEmpty)
            t.f.drain(t.actor)
            t.assertNoRows()
        }
    }

    @Test fun `complete mixed guard retains its nonstatic origin and secures the erased static arm`() {
        for (mode in MarkMode.entries) Fixture(mode).use { t ->
            val staticInitial = t.initial()
            val localPattern = t.f.pattern(AccessPathBase.Argument(0), tail = Tail.EXACT,
                mark = MarkSlot.Concrete(t.output))
            val localInitial = t.f.manager.initial(localPattern.copy(fact = localPattern.fact.copy(tail = Tail.STAR)))
            val actual = RegionGroups(t.f.pattern(STATIC, t.from, Tail.STAR, MarkSlot.STAR), MarkSet.of(t.input))
            val priorAnswer = t.service.commit(StaticBatch().apply {
                supplies += actual
                interests += StaticInterest(t.observer, 100, actual.region, actual.groups)
            }).filterIsInstance<StaticOwnerEffect.PointReady>().single()
            assertEquals(actual, priorAnswer.supply)
            val value = t.value(tail = Tail.EXACT)
            val edge = ConjunctiveEdge(listOf(
                staticInitial.toPattern().copy(fact = staticInitial.toPattern().fact.copy(tail = Tail.EXACT)),
                localPattern), t.f.ops.leaves(value).single().fact)
            val operation = OperationId(FormKey(FormOwner(), t.actor.key.method, t.actor.key, t.f.inst(1)))
            val emitted = LinkedHashSet<PremiseKey>()
            val emit: ResultSink = { original, facts ->
                emitted += original
                t.actor.emitOperationResult(OperationInput(ResumeSite.StatementInput(operation), original,
                    t.f.fact(AccessPathBase.LocalVar(0), mark = MarkSlot.Concrete(t.output), layer = Layer.DEMAND)),
                    StaticOperation.Write(operation, t.f.ops.leaves(facts).single()), facts) { _, _ ->
                    fail("A static write must not enter successor work")
                }
            }
            t.actor.localServices.conjunction(edge, staticInitial,
                t.f.fact(STATIC, t.from, Tail.EXACT, MarkSlot.Concrete(t.input), layer = Layer.DEMAND), t.f.inst(1), emit)
            assertTrue(emitted.isEmpty(), "One arm cannot complete the conjunction")
            assertTrue(t.actor.staticBatch.isEmpty)
            t.actor.localServices.conjunction(edge, localInitial,
                t.f.fact(localPattern.fact.base, mark = localPattern.fact.mark, layer = Layer.DEMAND), t.f.inst(1), emit)
            assertEquals(setOf<PremiseKey>(staticInitial, localInitial), emitted)
            assertEquals(setOf(localInitial), t.actor.summaries.all().map { it.first }.toSet())
            assertEquals(setOf(localInitial), t.actor.summaries.demandEdges().map { it.first }.toSet())
            assertTrue(t.actor.staticBatch.supplies.any { it.groups == MarkSet.of(t.output) })
            assertTrue(t.actor.summaries.all().none { it.first.isZero || it.first is PremiseSet })
            t.f.drain(t.actor)
            assertEquals(setOf(localInitial), t.f.publications.flatten().map { it.premise }.toSet())
        }
    }

    @Test fun `later full analysis retains and persists a real static summary`() {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val from = listOf(cls, f.field("from")); val target = listOf(cls, f.field("target"))
            val mark = MarkSlot.Concrete(f.mark("T"))
            val initial = f.manager.initial(f.pattern(STATIC, from, Tail.EXACT, mark))
            val output = f.fact(STATIC, target, Tail.EXACT, mark)
            val config = RunConfig(2, 1, DemandStore.Builder(f.manager).build(), f.records,
                SeedIndex.EMPTY, listOf(f.key))
            val actor = f.core(config)
            actor.summaryDelta(initial, output)
            assertEquals(listOf(initial to output), actor.summaries.all().toList())
            f.records.persist(Direction.BACKWARD, sequenceOf(actor.key to actor.summaries))
            val record = f.records.byEntry(actor.key, initial.toPattern()).single()
            assertEquals(initial, record.premise)
            assertEquals(output, record.conclusion)
            assertEquals(Direction.BACKWARD, record.direction)
        }
    }

    @Test fun `a method with no retained summaries still delivers static work to a dead local sink`() {
        for (mode in MarkMode.entries) for (threads in listOf(1, 2)) DriverFixtures(threads).use { f ->
            val p = f.program
            val source = p.method("source", listOf(listOf(1), emptyList()), 1).key()
            val writer = p.method("looping copy", listOf(listOf(1), listOf(1), emptyList()), 2).key()
            val reader = p.method("dead local", listOf(listOf(1), emptyList()), 1).key()
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val from = listOf(cls, p.field("from")); val target = listOf(cls, p.field("target"))
            val mark = p.mark("T")
            val local = AccessPathBase.LocalVar(0)
            fun operation(owner: MethodKey, index: Int) =
                OperationId(FormKey(FormOwner(), owner.method, owner, p.inst(owner, index)))
            val gen = PathEdge(ZERO_FACT, p.pattern(STATIC, from, mark = MarkSlot.Concrete(mark)).fact,
                ExclusionSet.Empty)
            p.statement(source, 0, MicroEdgeBuilder().apply {
                keepZero(); edge(gen)
                staticOperation(StaticOperation.Write(operation(source, 0), Pattern(gen.to, ExclusionSet.Empty), gen))
            }.buildStage())
            val copy = starEdge(STATIC, from, STATIC, target)
            val copyOperation = operation(writer, 1)
            p.statement(writer, 1, MicroEdgeBuilder().apply {
                keepZero(); edge(copy)
                staticOperation(StaticOperation.Read(copyOperation, copy))
                staticOperation(StaticOperation.Write(copyOperation, Pattern(copy.to, ExclusionSet.Empty), copy))
            }.buildStage())
            val load = starEdge(STATIC, target, local, emptyList())
            p.statement(reader, 0, MicroEdgeBuilder().apply {
                keepZero(); edge(load)
                staticOperation(StaticOperation.Read(operation(reader, 0), load))
            }.buildStage())
            val sink = SinkRule(f.rule("dead local static value"), 0,
                listOf(p.pattern(local, mark = MarkSlot.Concrete(mark))), emptyList())
            p.exit(reader, 1, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
                listOf(sink)), false, emptySet()))
            val roots = listOf(reader, writer, source)
            val result = f.run(RunConfig(1, 1, null, f.records, SeedIndex.EMPTY, roots,
                run1MarkMode = mode), timeout = 20.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertTrue(result.witnessesByKey().any { it.key.rule == sink.rule },
                "Counted static replies must reach the local sink before COMPLETE")
            val copyActor = result.analyzers.single { it.key == writer }
            assertTrue(copyActor.summaries.all().none(),
                "The copy cannot return and every static/static result is erased")
            assertTrue(copyActor.summaries.demandEdges().none())
            assertTrue(result.analyzers.single { it.key == reader }.summaries.all().none { (original, _) ->
                original.members.any { it.base == STATIC }
            }, "The local value is not exported as a reader method summary")
            assertTrue(f.processed.none { it.delta.facts.base == STATIC })
        }
    }
}

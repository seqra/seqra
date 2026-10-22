package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

class Run1OperationStaticWriterTest {
    private class Fixture : AutoCloseable {
        val f = MethodAnalysisFixtures()
        val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
        val service = checkNotNull(actor.runnerPort.staticService)
        val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
        val from = listOf(cls, f.field("from"))
        val target = listOf(cls, f.field("target"))
        val full = f.mark("T")
        val group = f.manager.marks.groupOf(full)
        val other = f.manager.marks.groupOf(f.mark("U"))
        val observer = f.store.key("observer")
        val external = ArrayList<StaticOwnerEffect>()
        val operation = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1)))
        val initial = f.manager.initial(STATIC, f.manager.path(from), Tail.STAR,
            ExclusionSet.Empty, MarkSlot.STAR)
        val input = f.fact(STATIC, from, Tail.STAR, MarkSlot.STAR, layer = Layer.DEMAND)
        fun flow(mx: MarkExclusions = MarkExclusions.EMPTY): Facts =
            f.fact(STATIC, target, Tail.STAR, MarkSlot.Star(mx), layer = Layer.DEMAND)
        fun local(value: Facts = flow(), premise: PremiseKey = initial) {
            actor.emitOperationResult(OperationInput(ResumeSite.StatementInput(operation), premise, input),
                StaticOperation.Write(operation, f.ops.leaves(value).first()), value) { _, _ ->
                fail("A static result must not resume the CFG")
            }
        }
        fun commit(batch: StaticBatch) {
            for (effect in service.commit(batch)) {
                if (effect.owner == actor.key) actor.onStaticEffect(effect) else external += effect
            }
            f.drain(actor, quantum = 1)
        }
        fun request(group: GroupId = this.group) = commit(StaticBatch().apply {
            interests += StaticInterest(observer, group.value.toLong(),
                f.pattern(STATIC, target, Tail.STAR, MarkSlot.STAR), GroupSet.of(group))
        })
        fun supply(path: List<Int> = from, group: GroupId = this.group) = commit(StaticBatch().apply {
            supplies += RegionGroups(f.pattern(STATIC, path, Tail.STAR, MarkSlot.STAR), GroupSet.of(group))
        })
        fun answers(): List<StaticOwnerEffect.PointReady> =
            (external + f.events.filterIsInstance<RunEvent.StaticOwner>().map { it.effect })
                .filterIsInstance<StaticOwnerEffect.PointReady>().filter { it.owner == observer }
        override fun close() = f.close()
    }

    @Test fun `promotion retains summaries and demands without admitting write presence or FLOW writers`() = Fixture().use { t ->
        t.actor.promoteStaticSummary(t.initial, t.flow())
        assertTrue(t.actor.staticBatch.writes.isEmpty())
        assertTrue(t.actor.staticBatch.writers.isEmpty())
        assertTrue(t.actor.staticBatch.flowWriters.isEmpty())
        assertTrue(t.actor.summaries.all().any { (p, value) -> p === t.initial && value.base == STATIC })
        assertTrue(t.actor.summaries.demandEdges().any { (p, value) -> p === t.initial && value.base == STATIC })
        t.f.drain(t.actor)
        assertEquals(0, t.service.statistics().writerRows)
        assertEquals(0, t.service.statistics().flowWriterRows)
        assertTrue(t.f.publications.flatten().any { it.premise === t.initial })
    }

    @Test fun `a promoted summary cannot suppress the first equal local writer`() = Fixture().use { t ->
        val value = t.flow()
        t.actor.promoteStaticSummary(t.initial, value)
        t.local(value)
        t.local(value)
        assertEquals(1, t.actor.staticBatch.writers.size, "Local directory deduplicates independently of summary delta")
        t.f.drain(t.actor)
        assertEquals(1, t.service.statistics().writerRows)
    }

    @Test fun `local writer owns its bounded mx relation after a promoted merge removes the exact selector`() {
        for (promoteFirst in listOf(false, true)) for (supplyFirst in listOf(false, true)) Fixture().use { t ->
            val excluded = MarkExclusions.of(t.f.manager.marks, t.f.manager.marks.group(t.other))
            if (promoteFirst) t.actor.promoteStaticSummary(t.initial, t.flow())
            t.local(t.flow(excluded))
            if (!promoteFirst) t.actor.promoteStaticSummary(t.initial, t.flow())
            t.f.drain(t.actor)
            assertTrue(t.actor.summaries.all().filter { it.first === t.initial }.none { (_, value) ->
                value is FlowTree && value.markExclusion == excluded
            }, "The exact local selector is absent from the merged summary store")
            if (supplyFirst) t.supply()
            t.request()
            if (!supplyFirst) {
                assertTrue(t.answers().isEmpty())
                t.supply()
            }
            assertEquals(listOf(GroupSet.of(t.group)), t.answers().map { it.supply.groups })
            assertEquals(1, t.service.statistics().writerRows)
        }
    }

    @Test fun `promotion cannot relax the immutable local writer mark exclusions`() = Fixture().use { t ->
        val excluded = MarkExclusions.of(t.f.manager.marks, t.f.manager.marks.group(t.group))
        t.local(t.flow(excluded))
        t.actor.promoteStaticSummary(t.initial, t.flow())
        t.f.drain(t.actor)
        t.supply()
        t.request()
        assertTrue(t.answers().isEmpty(), "Only a real local relaxed relation can add the missing transfer")
        t.local()
        t.f.drain(t.actor)
        assertEquals(listOf(GroupSet.of(t.group)), t.answers().map { it.supply.groups })
        assertEquals(2, t.service.statistics().writerRows)
    }

    @Test fun `promoted ZERO group results keep computed supply without a write point`() = Fixture().use { t ->
        t.actor.promoteStaticSummary(t.f.manager.zero, t.f.run1Fact(STATIC, t.target))
        assertTrue(t.actor.staticBatch.writes.isEmpty())
        assertTrue(t.actor.staticBatch.writers.isEmpty())
        assertTrue(t.actor.staticBatch.flowWriters.isEmpty())
        assertTrue(t.actor.staticBatch.supplies.isNotEmpty())
        t.f.drain(t.actor)
        t.request()
        assertEquals(listOf(GroupSet.of(t.group)), t.answers().map { it.supply.groups })
    }

    @Test fun `noncanonical exact-field fixed-group result keeps the actual input fallback and declared output group`() {
        for (supplyFirst in listOf(false, true)) Fixture().use { t ->
            if (supplyFirst) t.supply()
            // Canonical STAR originals now require an earlier actual reply and no longer
            // repeat that request. An exact field is legal but outside the selected fast
            // path; retain this delayed-input regression on that explicit fallback.
            val premise = t.f.manager.initial(t.f.run1Pattern(STATIC, t.from, Tail.EXACT))
            val outFull = t.f.mark("output")
            val outGroup = t.f.manager.marks.groupOf(outFull)
            t.actor.promoteStaticSummary(premise, t.f.run1Fact(STATIC, t.target, full = outFull))
            assertTrue(t.actor.staticBatch.writes.isEmpty())
            assertTrue(t.actor.staticBatch.writers.isEmpty())
            assertTrue(t.actor.staticBatch.flowWriters.isEmpty())
            t.f.drain(t.actor)
            t.request(outGroup)
            if (!supplyFirst) {
                assertTrue(t.answers().isEmpty(), "A conditional summary is not an unconditional source")
                t.supply()
            }
            assertEquals(listOf(GroupSet.of(outGroup)), t.answers().map { it.supply.groups })
            assertEquals(0, t.service.statistics().writerRows)
            assertEquals(0, t.service.statistics().flowWriterRows)
        }
    }

    @Test fun `nonstatic hypothetical group promotion cannot supply the static pool`() = Fixture().use { t ->
        val initial = t.f.manager.initial(t.f.run1Pattern(AccessPathBase.Argument(0), tail = Tail.STAR))
        t.actor.promoteStaticSummary(initial, t.f.run1Fact(STATIC, t.target))
        t.f.drain(t.actor)
        t.request()
        assertTrue(t.answers().isEmpty())
        assertEquals(0, t.service.statistics().writerRows)
        assertEquals(0, t.service.statistics().flowWriterRows)
    }

    @Test fun `unseeded local FLOW copy cycle closes without supplying a group`() = Fixture().use { t ->
        t.local()
        val reverseInitial = t.f.manager.initial(STATIC, t.f.manager.path(t.target), Tail.STAR,
            ExclusionSet.Empty, MarkSlot.STAR)
        val reverse = t.f.fact(STATIC, t.from, Tail.STAR, MarkSlot.STAR, layer = Layer.DEMAND)
        t.local(reverse, reverseInitial)
        t.f.drain(t.actor, quantum = 1)
        t.request()
        assertTrue(t.answers().isEmpty())
        assertEquals(2, t.service.statistics().writerRows)
        assertEquals(0L, t.service.statistics().suppliedGroups)
        assertTrue(t.actor.work(1), "Missing group answers remain dormant")
    }

    @Test fun `completed source action identity and result filter gate local write admission`() {
        for (matchingAction in listOf(false, true)) for (filtered in listOf(false, true))
            MethodAnalysisFixtures().use { f ->
                val a = AccessPathBase.Argument(0); val b = AccessPathBase.Argument(1)
                val t = f.mark("T"); val u = f.mark("U"); val v = f.mark("V")
                val target = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("out"))
                val form = FormKey(FormOwner(), f.key.method, f.key, f.inst(1))
                val occurrence = RuleOccurrenceId(form, RuleKind.CALL_SOURCE, 0)
                val actionId = ActionId(occurrence, ActionRole.SOURCE, 0)
                val targetFact = f.run1Pattern(STATIC, target, full = v).fact
                val builder = RulePredicate.Builder(f.cancellation, f.manager.marks)
                val predicate = builder.build(builder.and(listOf(
                    builder.leaf(f.run1Pattern(a, full = t)), builder.leaf(f.run1Pattern(b, full = u)))))
                val source = SourceRuleOccurrence(occurrence, predicate, listOf(SourceAction(actionId, targetFact)))
                val reject = TypeFilter(object : FactTypeChecker.FactApFilter {
                    override fun check(accessor: Accessor): FactTypeChecker.FilterResult = FactTypeChecker.FilterResult.Reject
                })
                f.statement(1, StatementSummary(setOf(a, b), emptyList(), emptyList(), emptyMap(),
                    resultFilters = if (filtered) mapOf(STATIC to reject) else emptyMap(), sources = listOf(source),
                    staticOperations = listOf(StaticOperation.Write(OperationId(form), Pattern(targetFact, ExclusionSet.Empty),
                        action = if (matchingAction) actionId else actionId.copy(ordinal = 1)))))
                val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
                val service = checkNotNull(actor.runnerPort.staticService)
                actor.addInitial(f.manager.initial(f.run1Pattern(a, full = t)))
                f.drain(actor)
                assertEquals(0, service.statistics().writeRegions, "One literal cannot execute the action")
                actor.addInitial(f.manager.initial(f.run1Pattern(b, full = t)))
                f.drain(actor)
                assertEquals(0, service.statistics().writeRegions, "A wrong group cannot complete the action")
                actor.addInitial(f.manager.initial(f.run1Pattern(b, full = u)))
                f.drain(actor)
                assertEquals(if (matchingAction && !filtered) 1 else 0, service.statistics().writeRegions,
                    "Only the completed action's matching descriptor, after its result filter, admits the write")
                assertEquals(0, service.statistics().writerRows)
                assertEquals(0, service.statistics().flowWriterRows)
                assertEquals(0L, service.statistics().suppliedGroups,
                    "Nonstatic hypothetical origins are not unconditional group sources")
            }
    }

    @Test fun `executed conjunction cannot use another action or transfer descriptor with the same target`() {
        for (descriptorKind in listOf("conjunction", "other action", "other transfer"))
            MethodAnalysisFixtures().use { f ->
                val a = AccessPathBase.Argument(0); val b = AccessPathBase.Argument(1)
                val t = f.mark("T"); val u = f.mark("U"); val v = f.mark("V")
                val target = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("out"))
                val form = FormKey(FormOwner(), f.key.method, f.key, f.inst(1))
                val targetFact = f.run1Pattern(STATIC, target, full = v).fact
                val left = f.run1Pattern(a, full = t); val right = f.run1Pattern(b, full = u)
                val conjunction = ConjunctiveEdge(listOf(left, right), targetFact)
                val descriptor = StaticOperation.Write(OperationId(form), Pattern(targetFact, ExclusionSet.Empty),
                    transfer = if (descriptorKind == "other transfer")
                        PathEdge(left.fact, targetFact, ExclusionSet.Empty) else null,
                    action = if (descriptorKind == "other action")
                        ActionId(RuleOccurrenceId(form, RuleKind.CALL_SOURCE, 0), ActionRole.SOURCE, 0) else null)
                f.statement(1, StatementSummary(setOf(a, b), emptyList(), listOf(conjunction), emptyMap(),
                    staticOperations = listOf(descriptor)))
                val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
                val service = checkNotNull(actor.runnerPort.staticService)
                actor.addInitial(f.manager.initial(left))
                f.drain(actor)
                assertEquals(0, service.statistics().writeRegions, "A partial binding executes no conjunction result")
                actor.addInitial(f.manager.initial(right))
                f.drain(actor)
                assertEquals(if (descriptorKind == "conjunction") 1 else 0, service.statistics().writeRegions,
                    "Completed CJ identity must not borrow $descriptorKind metadata")
                assertEquals(0, service.statistics().writerRows)
                assertEquals(0, service.statistics().flowWriterRows)
            }
    }
}

package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

/** Complete local admission precedes each atomic static publication and pending group supply. */
class StaticConditionalRequestJoinTest {
    @Test fun `fixed static joins admit complete guards then either contributing origin can supply the result`() {
        for (nd in listOf(false, true)) for (supplyingMember in 0 until (if (nd) 2 else 1))
            for (supplyFirst in listOf(false, true))
            MethodAnalysisFixtures().use { f ->
                val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
                val left = listOf(cls, f.field("left")); val right = listOf(cls, f.field("right"))
                val output = listOf(cls, f.field("output"))
                val t = f.mark("T"); val u = f.mark("U"); val v = f.mark("V")
                val g = f.manager.marks.groupOf(t); val h = f.manager.marks.groupOf(u)
                val result = f.manager.marks.groupOf(v)
                val members = listOf(f.manager.initial(f.run1Pattern(STATIC, left, Tail.STAR, t))) +
                    if (nd) listOf(f.manager.initial(f.run1Pattern(STATIC, right, Tail.STAR, u))) else emptyList()
                val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
                val service = checkNotNull(actor.runnerPort.staticService)
                val observer = f.store.key("observer")
                val external = ArrayList<StaticOwnerEffect>()
                fun commit(batch: StaticBatch) {
                    for (effect in service.commit(batch)) {
                        if (effect.owner == actor.key) actor.onStaticEffect(effect) else external += effect
                    }
                    f.drain(actor)
                }
                fun supplies(path: List<Int>, group: GroupId) = StaticBatch().apply {
                    supplies += RegionGroups(f.pattern(STATIC, path, Tail.STAR, MarkSlot.STAR), GroupSet.of(group))
                }
                fun answers(): List<StaticOwnerEffect.PointReady> =
                    (external + f.events.filterIsInstance<RunEvent.StaticOwner>().map { it.effect })
                        .filterIsInstance<StaticOwnerEffect.PointReady>().filter { it.owner == observer }
                commit(StaticBatch().apply {
                    interests += StaticInterest(observer, 1, f.pattern(STATIC, output, Tail.STAR, MarkSlot.STAR), GroupSet.of(result))
                })
                val supplyingPath = if (supplyingMember == 0) left else right
                val supplyingGroup = if (supplyingMember == 0) g else h
                val wrongGroup = if (supplyingMember == 0) h else g
                if (supplyFirst) commit(supplies(supplyingPath, supplyingGroup))
                assertTrue(answers().isEmpty(), "An actual input group alone cannot bypass the complete local guard")
                val operation = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1)))
                val value = f.run1Fact(STATIC, output, Tail.EXACT, v)
                val published = LinkedHashSet<PremiseKey>()
                fun publish(premise: PremiseKey, value: Facts) {
                    assertTrue(members.any { it === premise }, "Each result arm keeps one contributing original")
                    assertTrue(published.add(premise), "The complete binding emits each original once")
                    actor.emitOperationResult(OperationInput(ResumeSite.StatementInput(operation), premise,
                        f.run1Fact(AccessPathBase.LocalVar(0), full = v)),
                        StaticOperation.Write(operation, Pattern(f.ops.leaves(value).single().fact, ExclusionSet.Empty)), value) { _, _ ->
                        fail("A static conditional result must not enter successor work")
                    }
                }
                if (nd) {
                    val edge = ConjunctiveEdge(members.map {
                        it.toPattern().copy(fact = it.toPattern().fact.copy(tail = Tail.EXACT))
                    }, f.ops.leaves(value).single().fact)
                    actor.localServices.conjunction(edge, members[0], f.run1Fact(STATIC, left, Tail.EXACT, t),
                        f.inst(1), ::publish)
                    assertTrue(actor.summaries.all().none(), "One admitted local literal cannot publish a write")
                    assertTrue(published.isEmpty())
                    assertTrue(answers().isEmpty())
                    actor.localServices.conjunction(edge, members[1], f.run1Fact(STATIC, right, Tail.EXACT, t),
                        f.inst(1), ::publish)
                    assertTrue(actor.summaries.all().none(), "A wrong group cannot complete the local guard")
                    assertTrue(published.isEmpty())
                    assertTrue(answers().isEmpty())
                    actor.localServices.conjunction(edge, members[1], f.run1Fact(STATIC, right, Tail.EXACT, u),
                        f.inst(1), ::publish)
                } else publish(members.single(), value)
                f.drain(actor)
                val expectedOrigins: Set<PremiseKey> = members.toSet()
                assertEquals(expectedOrigins, published, "Every admitted original has an atomic result arm")
                assertEquals(expectedOrigins, actor.summaries.all().map { it.first }.toSet())
                assertTrue(actor.summaries.all().all { (premise, _) -> premise is InitialAp && !premise.isZero },
                    "A local complete guard creates neither an executable ND premise nor an unconditional result")
                if (!supplyFirst) {
                    assertTrue(answers().isEmpty())
                    commit(supplies(supplyingPath, wrongGroup))
                    assertTrue(answers().isEmpty(), "A wrong group cannot supply either result arm")
                    commit(supplies(supplyingPath, supplyingGroup))
                }
                assertEquals(listOf(GroupSet.of(result)), answers().map { it.supply.groups })
                assertEquals(if (nd) 1L else 0L, service.requestStatistics().pendingMemberships,
                    "An unanswered origin remains pending after the other arm supplies the result")
                if (nd) {
                    val remainingPath = if (supplyingMember == 0) right else left
                    val remainingGroup = if (supplyingMember == 0) h else g
                    commit(supplies(remainingPath, remainingGroup))
                    assertEquals(listOf(GroupSet.of(result)), answers().map { it.supply.groups },
                        "The second satisfied arm does not answer an already released output request twice")
                }
                assertEquals(0, service.statistics().writerRows,
                    "Concrete conditional summaries must not retain a service writer row")
                assertEquals(0, service.statistics().flowWriterRows)
                assertEquals(0L, service.requestStatistics().pendingMemberships)
                assertEquals(0, service.statistics().pointSubscriptions)
            }
    }
}

package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

/** Complete local admission precedes singleton static publication and pending group supply. */
class StaticConditionalRequestJoinTest {
    @Test fun `fixed static joins admit complete local guards then wait for the selected group only`() {
        for (nd in listOf(false, true)) for (supplyFirst in listOf(false, true))
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
                if (supplyFirst) commit(supplies(left, g))
                val operation = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1)))
                val value = f.run1Fact(STATIC, output, Tail.EXACT, v)
                fun publish(premise: PremiseKey, value: Facts) {
                    assertSame(members.first(), premise, "The left field is the preferred original")
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
                    actor.localServices.conjunction(edge, members[1], f.run1Fact(STATIC, right, Tail.EXACT, t),
                        f.inst(1), ::publish)
                    assertTrue(actor.summaries.all().none(), "A wrong group cannot complete the local guard")
                    actor.localServices.conjunction(edge, members[1], f.run1Fact(STATIC, right, Tail.EXACT, u),
                        f.inst(1), ::publish)
                } else publish(members.single(), value)
                f.drain(actor)
                if (!supplyFirst) {
                    assertTrue(answers().isEmpty())
                    commit(supplies(left, h))
                    assertTrue(answers().isEmpty(), "A wrong group cannot supply the selected original")
                    commit(supplies(left, g))
                }
                assertEquals(listOf(GroupSet.of(result)), answers().map { it.supply.groups })
                assertTrue(actor.summaries.all().all { it.first === members.first() })
                assertEquals(0, service.statistics().writerRows,
                    "Concrete conditional summaries must not retain a service writer row")
                assertEquals(0, service.statistics().flowWriterRows)
                assertEquals(0L, service.requestStatistics().pendingMemberships)
                assertEquals(0, service.statistics().pointSubscriptions)
            }
    }
}

package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.HandOff
import org.opentaint.dataflow.bidi.driver.ReportBuilder
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

/** Complete local admission precedes each atomic static publication and pending group supply. */
class StaticConditionalRequestJoinTest {
    @Test fun `exact-field fallback joins admit complete guards then either contributing origin can supply the result`() {
        for (nd in listOf(false, true)) for (supplyingMember in 0 until (if (nd) 2 else 1))
            for (supplyFirst in listOf(false, true))
            MethodAnalysisFixtures().use { f ->
                val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
                val left = listOf(cls, f.field("left")); val right = listOf(cls, f.field("right"))
                val output = listOf(cls, f.field("output"))
                val t = f.mark("T"); val u = f.mark("U"); val v = f.mark("V")
                val g = f.manager.marks.groupOf(t); val h = f.manager.marks.groupOf(u)
                val result = f.manager.marks.groupOf(v)
                // This deliberately installs local input hypotheses before a pool answer.
                // Canonical STAR GROUP originals now require an earlier answer; legal exact
                // fields retain the delayed-input fallback which this scenario exercises.
                val members = listOf(f.manager.initial(f.run1Pattern(STATIC, left, Tail.EXACT, t))) +
                    if (nd) listOf(f.manager.initial(f.run1Pattern(STATIC, right, Tail.EXACT, u))) else emptyList()
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
                    supplies += RegionGroups(f.pattern(STATIC, path, Tail.STAR, MarkSlot.STAR), f.manager.marks.atomsFor(GroupSet.of(group)))
                }
                fun answers(): List<StaticOwnerEffect.PointReady> =
                    (external + f.events.filterIsInstance<RunEvent.StaticOwner>().map { it.effect })
                        .filterIsInstance<StaticOwnerEffect.PointReady>().filter { it.owner == observer }
                commit(StaticBatch().apply {
                    interests += StaticInterest(observer, 1, f.pattern(STATIC, output, Tail.STAR, MarkSlot.STAR), f.manager.marks.atomsFor(GroupSet.of(result)))
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
                assertTrue(actor.summaries.all().none(),
                    "Every static/static arm is erased after securing its delayed scalar input join")
                if (!supplyFirst) {
                    assertTrue(answers().isEmpty())
                    commit(supplies(supplyingPath, wrongGroup))
                    assertTrue(answers().isEmpty(), "A wrong group cannot supply either result arm")
                    commit(supplies(supplyingPath, supplyingGroup))
                }
                assertEquals(listOf(f.manager.marks.atomsFor(GroupSet.of(result))), answers().map { it.supply.groups })
                assertEquals(if (nd) 1L else 0L, service.requestStatistics().pendingMemberships,
                    "An unanswered origin remains pending after the other arm supplies the result")
                if (nd) {
                    val remainingPath = if (supplyingMember == 0) right else left
                    val remainingGroup = if (supplyingMember == 0) h else g
                    commit(supplies(remainingPath, remainingGroup))
                    assertEquals(listOf(f.manager.marks.atomsFor(GroupSet.of(result))), answers().map { it.supply.groups },
                        "The second satisfied arm does not answer an already released output request twice")
                }
                assertEquals(0, service.statistics().writerRows,
                    "Concrete conditional summaries must not retain a service writer row")
                assertEquals(0, service.statistics().flowWriterRows)
                assertEquals(0L, service.requestStatistics().pendingMemberships)
                assertEquals(0, service.statistics().pointSubscriptions)
            }
    }

    @Test fun `grounded canonical origins need the whole local guard then erase pairs without repeated input joins`() {
        for (reverse in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("Grounded"))
            val paths = listOf(listOf(cls, f.field("left")), listOf(cls, f.field("right")))
            val output = listOf(cls, f.field("output"))
            val full = listOf(f.mark("T"), f.mark("U"))
            val groups = full.map(f.manager.marks::groupOf)
            val result = f.manager.marks.groupOf(f.mark("V"))
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            val service = checkNotNull(actor.runnerPort.staticService)
            val observer = f.store.key("grounding observer")
            val grounded = service.commit(StaticBatch().apply {
                paths.forEachIndexed { index, path ->
                    val region = f.pattern(STATIC, path, Tail.STAR, MarkSlot.STAR)
                    supplies += RegionGroups(region, f.manager.marks.atomsFor(GroupSet.of(groups[index])))
                    interests += StaticInterest(observer, 10L + index, region, f.manager.marks.atomsFor(GroupSet.of(groups[index])))
                }
            }).filterIsInstance<StaticOwnerEffect.PointReady>().sortedBy { it.id }
            assertEquals(listOf(10L, 11L), grounded.map { it.id })
            assertEquals(2L, service.requestStatistics().processed,
                "Both canonical originals have actual prior supply/request answers")
            val members = grounded.map { reply ->
                val leaf = f.ops.leaves(service.facts(reply.supply.region, reply.supply.groups)).single()
                f.manager.initial(leaf.copy(fact = leaf.fact.copy(tail = Tail.STAR)))
            }
            val expectedOrigins: Set<PremiseKey> = members.toSet()
            assertTrue(members.all(actor::hasKnownStaticGroupInput))
            val external = service.commit(StaticBatch().apply {
                interests += StaticInterest(observer, 12,
                    f.pattern(STATIC, output, Tail.STAR, MarkSlot.STAR), f.manager.marks.atomsFor(GroupSet.of(result)))
            }).toMutableList()
            assertTrue(external.isEmpty(), "Input supply does not bypass the local guard")
            val value = f.run1Fact(STATIC, output, Tail.EXACT, f.mark("V"))
            val edge = ConjunctiveEdge(paths.indices.map { index ->
                f.run1Pattern(STATIC, paths[index], Tail.EXACT, full[index])
            }, f.ops.leaves(value).single().fact)
            val operation = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1)))
            val published = LinkedHashSet<PremiseKey>()
            fun publish(premise: PremiseKey, facts: Facts) {
                assertTrue(published.add(premise), "Emit every complete contributing original once")
                actor.emitOperationResult(OperationInput(ResumeSite.StatementInput(operation), premise,
                    f.run1Fact(AccessPathBase.LocalVar(0), full = f.mark("V"))),
                    StaticOperation.Write(operation, f.ops.leaves(facts).single()), facts) { _, _ ->
                    fail("The local static result must be published early")
                }
            }
            val order = if (reverse) listOf(1, 0) else listOf(0, 1)
            val first = order.first(); val last = order.last()
            actor.localServices.conjunction(edge, members[first],
                f.run1Fact(STATIC, paths[first], Tail.EXACT, full[first]), f.inst(1), ::publish)
            f.drain(actor, quantum = 1)
            assertTrue(published.isEmpty())
            assertTrue(actor.summaries.all().none())
            assertEquals(1L, service.requestStatistics().pendingMemberships,
                "The output request still waits for a complete local binding")
            actor.localServices.conjunction(edge, members[last],
                f.run1Fact(STATIC, paths[last], Tail.EXACT, full[first]), f.inst(1), ::publish)
            assertTrue(published.isEmpty(), "A wrong group cannot satisfy the remaining literal")
            actor.localServices.conjunction(edge, members[last],
                f.run1Fact(STATIC, paths[last], Tail.EXACT, full[last]), f.inst(1), ::publish)
            assertEquals(expectedOrigins, published)
            assertTrue(actor.staticBatch.interests.isEmpty(), "Neither complete origin repeats its pool request")
            assertEquals(2, actor.staticBatch.supplies.size)
            val beforeReplies = service.statistics().ownerReplies
            f.drain(actor, quantum = 1)
            external += f.events.filterIsInstance<RunEvent.StaticOwner>().map { it.effect }
                .filter { it.owner == observer }
            assertEquals(1L, service.statistics().ownerReplies - beforeReplies,
                "Only the real pending output consumer is answered, with no input-join replies")
            assertEquals(listOf(12L), external.filterIsInstance<StaticOwnerEffect.PointReady>().map { it.id })
            assertEquals(0, service.statistics().pointSubscriptions)
            assertEquals(0, service.statistics().writerRows)
            assertEquals(0, service.statistics().flowWriterRows)
            assertTrue(actor.summaries.all().none())
            assertTrue(actor.summaries.demandEdges().none())
            assertTrue(f.publications.flatten().isEmpty())
            val replies = service.statistics().ownerReplies
            for (index in order) actor.localServices.conjunction(edge, members[index],
                f.run1Fact(STATIC, paths[index], Tail.EXACT, full[index]), f.inst(1), ::publish)
            f.drain(actor, quantum = 1)
            assertEquals(replies, service.statistics().ownerReplies)
            actor.freeze()
            val handOff = HandOff.of(f.config(), RunResult(RunStatus.COMPLETE, listOf(actor), 1,
                Direction.FORWARD, f.vulnerabilities), ReportBuilder(), f.shared)
            assertTrue(handOff.demand.near(f.key, f.ops.leaves(value).single()).isEmpty(),
                "Erased group-valued static/static pairs cannot reappear in the actual handoff")
            assertEquals(org.opentaint.dataflow.bidi.store.StaticQueryPolicy.COMPLETE_RUN1_STATIC_FIELDS,
                handOff.demand.staticQueryPolicy)
        }
    }
}

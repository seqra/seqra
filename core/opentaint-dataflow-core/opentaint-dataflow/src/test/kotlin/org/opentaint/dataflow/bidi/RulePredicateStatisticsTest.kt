package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.ConjunctionStore
import org.opentaint.dataflow.bidi.store.RulePredicateStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class RulePredicateStatisticsTest {
    private val fixture = StoreFixtures()
    private val statement = fixture.inst(1)
    private val rule = RuleOccurrenceId(FormKey(FormOwner(), fixture.commonMethod, fixture.method, statement),
        RuleKind.CALL_SINK, 0)
    private val requirements = List(2) { fixture.pattern(base = AccessPathBase.Argument(it), tail = Tail.ANY) }
    private val premises = List(2) { fixture.manager.initial(fixture.pattern(base = AccessPathBase.Argument(it))) }

    private fun predicate(): RulePredicate {
        val builder = RulePredicate.Builder(fixture.cancellation)
        return builder.build(builder.and(requirements.map(builder::leaf)))
    }

    private fun checked(index: Int, path: List<Int> = emptyList()): ConjunctionStore.Input {
        val facts = fixture.fact(base = AccessPathBase.Argument(index), path = path)
        val holds = assertIs<MarkCheck.Holds>(fixture.ops.checkMark(facts, requirements[index],
            ApMode(run1 = true, direction = Direction.FORWARD, fieldLimit = 1)))
        return ConjunctionStore.Input(premises[index], holds.facts.layer, holds.facts)
    }

    private fun RulePredicateStore.accept(predicate: RulePredicate, index: Int, input: ConjunctionStore.Input):
        List<RulePredicateStore.Combination> = ArrayList<RulePredicateStore.Combination>().also {
            add(rule, statement, predicate, index, input, it::add)
        }

    @Test fun `compiled structure does not count unsupported branches or duplicate arrivals`(): Unit {
        val statistics = FormCacheStatistics()
        val predicate = predicate()
        statistics.compiledPredicate(predicate.nodeCount, predicate.leaves.size)
        val before = statistics.snapshot()
        assertEquals(3L, before.compiledNodes)
        assertEquals(2L, before.compiledLeaves)
        assertEquals(0L, before.observedProofRecords)
        assertEquals(0L, before.observedSupportShapes)
        assertEquals(0L, before.rootOutcomes)

        val store = RulePredicateStore(fixture.manager, statistics)
        assertTrue(store.accept(predicate, 0, checked(0)).isEmpty())
        val partial = statistics.snapshot()
        assertEquals(1L, partial.observedProofRecords)
        assertEquals(1L, partial.observedSupportShapes)
        assertEquals(0L, partial.rootOutcomes)
        assertTrue(store.accept(predicate, 0, checked(0)).isEmpty())
        assertEquals(partial, statistics.snapshot())

        val outcome = store.accept(predicate, 1, checked(1)).single()
        assertEquals(premises.toSet(), outcome.premise.members.toSet())
        assertEquals(Layer.NORMAL, outcome.layer)
        val complete = statistics.snapshot()
        assertEquals(3L, complete.observedProofRecords)
        assertEquals(3L, complete.observedSupportShapes)
        assertEquals(0L, complete.supportGrowthUpdates)
        assertEquals(1L, complete.newRootOutcomes)
        assertEquals(0L, complete.rootGrowthOutcomes)
        assertEquals(1L, complete.rootOutcomes)
        for (index in requirements.indices) assertTrue(store.accept(predicate(), index, checked(index)).isEmpty())
        assertEquals(complete, statistics.snapshot())
    }

    @Test fun `matched fact growth counts updates without another structural proof or root`(): Unit {
        val statistics = FormCacheStatistics()
        val predicate = predicate()
        val store = RulePredicateStore(fixture.manager, statistics)
        assertTrue(store.accept(predicate, 0, checked(0)).isEmpty())
        val initial = store.accept(predicate, 1, checked(1)).single()
        val child = fixture.field("child")
        val updated = store.accept(predicate(), 0, checked(0, listOf(child))).single()
        assertEquals(initial.proof, updated.proof)
        assertEquals(premises.toSet(), updated.premise.members.toSet())
        assertEquals(setOf(emptyList(), listOf(child)),
            fixture.ops.leaves(checkNotNull(updated.inputs[0].facts)).map { it.fact.path }.toSet())

        val counts = statistics.snapshot()
        assertEquals(3L, counts.observedProofRecords)
        assertEquals(3L, counts.observedSupportShapes)
        assertEquals(2L, counts.supportGrowthUpdates, "The existing leaf and AND-root supports both grew")
        assertEquals(1L, counts.newRootOutcomes)
        assertEquals(1L, counts.rootGrowthOutcomes)
        assertEquals(2L, counts.rootOutcomes)
        assertEquals(0L, counts.compiledNodes, "Runtime accounting does not invent compilation events")
        assertTrue(store.accept(predicate, 0, checked(0)).isEmpty())
        assertTrue(store.accept(predicate, 0, checked(0, listOf(child))).isEmpty())
        assertEquals(counts, statistics.snapshot())
    }

    @Test fun `statistics are optional and do not change complete predicate outputs`(): Unit {
        val predicate = predicate()
        val statistics = FormCacheStatistics()
        val measured = RulePredicateStore(fixture.manager, statistics)
        val ordinary = RulePredicateStore(fixture.manager)
        assertTrue(measured.accept(predicate, 0, checked(0)).isEmpty())
        assertTrue(ordinary.accept(predicate, 0, checked(0)).isEmpty())
        val a = measured.accept(predicate, 1, checked(1)).single()
        val b = ordinary.accept(predicate, 1, checked(1)).single()
        assertEquals(a.proof, b.proof)
        assertEquals(a.premise, b.premise)
        assertEquals(a.layer, b.layer)
        assertEquals(a.leaves, b.leaves)
        assertEquals(a.inputs, b.inputs)
        val field = ordinary.javaClass.getDeclaredField("statistics").apply { isAccessible = true }
        assertNull(field.get(ordinary), "The normal path retains no statistics object")
    }

    @Test fun `one opt in aggregate counts independent actor stores without retaining their state`(): Unit {
        val statistics = FormCacheStatistics()
        val predicate = predicate()
        val inputs = List(2) { checked(it) }
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val jobs = List(2) {
                workers.submit<Int> {
                    check(start.await(5, TimeUnit.SECONDS))
                    repeat(100) {
                        val store = RulePredicateStore(fixture.manager, statistics)
                        check(store.accept(predicate, 0, inputs[0]).isEmpty())
                        check(store.accept(predicate, 1, inputs[1]).size == 1)
                    }
                    100
                }
            }
            start.countDown()
            assertEquals(200, jobs.sumOf { it.get(10, TimeUnit.SECONDS) })
        } finally {
            workers.shutdownNow()
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
        }
        val counts = statistics.snapshot()
        assertEquals(600L, counts.observedProofRecords)
        assertEquals(600L, counts.observedSupportShapes)
        assertEquals(200L, counts.newRootOutcomes)
        assertEquals(200L, counts.rootOutcomes)
        assertEquals(0L, counts.rootGrowthOutcomes)
        assertEquals(0L, counts.supportGrowthUpdates)
        assertEquals(0L, counts.compiledPredicates)
        for (field in FormCacheStatistics::class.java.declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
            assertEquals(java.util.concurrent.atomic.AtomicLong::class.java, field.type,
                "Counters retain numbers, not predicates, actor stores, supports or callbacks")
        }
    }
}

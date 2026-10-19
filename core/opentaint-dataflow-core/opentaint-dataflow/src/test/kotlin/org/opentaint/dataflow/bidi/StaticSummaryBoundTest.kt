package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

class StaticSummaryBoundTest {
    private fun operation(f: MethodAnalysisFixtures, node: Int): OperationId =
        OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(node)))

    private fun summary(edge: PathEdge, operations: List<StaticOperation>): StatementSummary =
        MicroEdgeBuilder().apply {
            keepZero()
            edge(edge)
            operations.forEach(::staticOperation)
        }.buildStage()

    private fun pool(f: MethodAnalysisFixtures, core: RunMethodAnalyzer): List<Pattern> {
        val service = checkNotNull(core.runnerPort.staticService)
        val request = StaticBatch()
        request.interests += StaticInterest(f.key, Long.MAX_VALUE,
            Pattern(PathFact(STATIC, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty),
            GroupSet.of(f.manager.marks.groupOf(f.mark("T"))))
        return service.commit(request).filterIsInstance<StaticOwnerEffect.PointReady>()
            .filter { it.id == Long.MAX_VALUE }.map { it.supply.region }
    }

    @Test fun `suffix-producing static write is cut before raw publication and pool supply`() {
        MethodAnalysisFixtures().use { f ->
            val local = AccessPathBase.LocalVar(0)
            val h = f.field("h")
            val target = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("f"))
            val source = PathEdge(ZERO_FACT, f.run1Pattern(local, listOf(h)).fact, ExclusionSet.Empty)
            f.statement(0, summary(source, emptyList()))
            val store = starEdge(local, emptyList(), STATIC, target)
            f.statement(1, summary(store, listOf(StaticOperation.Write(operation(f, 1),
                Pattern(store.to, ExclusionSet.Empty), store))))
            val core = f.core()
            core.addRootZero()
            f.drain(core)
            val raw = core.summaries.all().filter { it.second.base == STATIC }.toList()
            assertTrue(raw.isNotEmpty())
            assertTrue(raw.all { it.first.isZero })
            val leaves = raw.flatMap { f.ops.leaves(it.second).toList() }
            assertEquals(setOf(target), leaves.map { it.fact.path }.toSet())
            assertEquals(setOf(Tail.ANY), leaves.map { it.fact.tail }.toSet())
            assertTrue(raw.all { it.second.layer == Layer.DEMAND })
            val supplied = pool(f, core)
            assertTrue(supplied.isNotEmpty())
            assertEquals(setOf(target), supplied.map { it.fact.path }.toSet())
            assertTrue(f.processed.none { it.facts.base == STATIC })
        }
    }

    @Test fun `rooted suffix-copy cycle closes with the existing field bound`() {
        MethodAnalysisFixtures().use { f ->
            val target = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("f"))
            val source = PathEdge(ZERO_FACT, f.run1Pattern(STATIC, target).fact, ExclusionSet.Empty)
            f.statement(0, summary(source, listOf(StaticOperation.Write(operation(f, 0),
                Pattern(source.to, ExclusionSet.Empty), source))))
            val copy = starEdge(STATIC, target, STATIC, target + f.field("h"))
            val op = operation(f, 1)
            f.statement(1, summary(copy, listOf(StaticOperation.Read(op, copy),
                StaticOperation.Write(op, Pattern(copy.to, ExclusionSet.Empty), copy))))
            val core = f.core()
            core.addRootZero()
            var complete = false
            for (turn in 0 until 32) {
                val drained = core.work(64)
                val delivered = f.pumpQuestions()
                if (drained && !delivered) { complete = true; break }
            }
            assertTrue(complete, "A fresh suffix cannot grow beyond the selected field bound")
            val raw = core.summaries.all().filter { it.second.base == STATIC }.toList()
            assertTrue(raw.any { it.first.isFlow })
            for ((_, facts) in raw) {
                val depth = when (facts) { is FlowTree -> facts.root.boundedDepth; is TaintTree -> facts.root.boundedDepth; is Reach -> 0 }
                assertTrue(depth <= 1)
                assertEquals(Layer.DEMAND, facts.layer)
            }
            assertTrue(pool(f, core).all { it.fact.path == target })
            assertTrue(checkNotNull(core.runnerPort.staticService).statistics().ownerReplies < 32)
        }
    }
}

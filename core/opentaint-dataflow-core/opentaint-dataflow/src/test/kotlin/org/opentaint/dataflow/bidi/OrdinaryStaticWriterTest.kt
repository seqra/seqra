package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

/** Real actor, static service, AP application and queued owner replies. */
class OrdinaryStaticWriterTest {
    private fun operation(f: MethodAnalysisFixtures, index: Int): OperationId =
        OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(index)))

    @Test fun `static FLOW mx blocks actual pool application until a relaxed summary arrives`() {
        MethodAnalysisFixtures().use { f ->
            val full = f.mark("T")
            val atom = (f.groupMark(full)).mark
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val from = listOf(cls, f.field("from")); val target = listOf(cls, f.field("target"))
            val source = PathEdge(ZERO_FACT, f.pattern(STATIC, from, mark = MarkSlot.Concrete(full)).fact,
                ExclusionSet.Empty)
            val sourceId = operation(f, 0)
            f.statement(0, StatementSummary(setOf(AccessPathBase.Zero),
                listOf(MicroEdge.of(ZERO_KEEP), MicroEdge.of(source)), emptyList(), emptyMap(),
                staticOperations = listOf(StaticOperation.Write(sourceId, Pattern(source.to, ExclusionSet.Empty), source))))
            val wanted = f.pattern(STATIC, target, mark = MarkSlot.Concrete(full))
            val sink = localSink(listOf(wanted))
            val checks = StatementSummary.EMPTY.withStaticOperations(listOf(
                StaticOperation.MarkInput(operation(f, 2), wanted)))
            f.exitRules(2, ExitRules(RuleStatement(checks, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            val initial = f.manager.initial(STATIC, f.manager.path(from), Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
            val input = f.fact(STATIC, from, Tail.STAR, MarkSlot.STAR, layer = Layer.DEMAND)
            val copyId = operation(f, 1)
            val at = OperationInput(ResumeSite.StatementInput(copyId), initial, input)
            fun publish(exclusions: MarkExclusions) {
                val edge = PathEdge(f.pattern(STATIC, from, Tail.STAR, MarkSlot.STAR).fact,
                    f.pattern(STATIC, target, Tail.STAR, MarkSlot.Star(exclusions)).fact, ExclusionSet.Empty)
                val result = f.fact(STATIC, target, Tail.STAR, MarkSlot.Star(exclusions), layer = Layer.DEMAND)
                actor.emitOperationResult(at, StaticOperation.Write(copyId, Pattern(edge.to, ExclusionSet.Empty), edge), result) {
                    _, _ -> fail("A static result must be published through the static protocol")
                }
            }
            publish(MarkExclusions.of(f.manager.marks, atom))
            actor.addRootZero()
            f.drain(actor, quantum = 1)
            assertTrue(f.vulnerabilities.witnessesOf(1).none(), "The actual group is excluded by the saved FLOW relation")
            assertTrue(f.events.filterIsInstance<RunEvent.StaticOwner>().any {
                it.effect is StaticOwnerEffect.WriterInterest
            }, "The all-static FLOW summary must receive its requested output group")
            publish(MarkExclusions.EMPTY)
            f.drain(actor, quantum = 1)
            assertTrue(f.vulnerabilities.witnessesOf(1).any { it.first.rule == sink.rule },
                "A fresh relaxed relation must reuse the old actual pool group")
            assertTrue(f.processed.none { it.facts.base == STATIC })
        }
    }

    @Test fun `an excluded ordinary FLOW writer still routes a Base interest without inventing taint`() {
        MethodAnalysisFixtures().use { f ->
            val full = f.mark("T"); val atom = f.groupMark(full).mark
            val path = listOf(f.manager.accessors.index(ClassStaticAccessor("C")), f.field("result"))
            val wanted = f.pattern(STATIC, path, mark = MarkSlot.Concrete(full))
            val checks = StatementSummary.EMPTY.withStaticOperations(listOf(
                StaticOperation.MarkInput(operation(f, 2), wanted)))
            f.exitRules(2, ExitRules(RuleStatement(checks, StatementSummary.EMPTY, listOf(localSink(listOf(wanted)))),
                false, emptySet()))
            val base = AccessPathBase.Argument(0)
            val initial = f.manager.initial(base, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
            val input = f.fact(base, tail = Tail.STAR, mark = MarkSlot.STAR)
            val edge = PathEdge(f.pattern(base, tail = Tail.STAR, mark = MarkSlot.STAR).fact,
                f.pattern(STATIC, path, Tail.STAR, MarkSlot.Star(MarkExclusions.of(f.manager.marks, atom))).fact,
                ExclusionSet.Empty)
            val id = operation(f, 0)
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            actor.emitOperationResult(OperationInput(ResumeSite.StatementInput(id), initial, input),
                StaticOperation.Write(id, Pattern(edge.to, ExclusionSet.Empty), edge),
                f.fact(STATIC, path, Tail.STAR, edge.to.mark, layer = Layer.DEMAND)) { _, _ ->
                fail("Static writer output must not enter ordinary successor work")
            }
            actor.addRootZero()
            f.drain(actor, quantum = 1)
            assertTrue(f.requestRow(actor, Run1Slot.Base(base)).containsId(atom.id))
            assertTrue(f.events.filterIsInstance<RunEvent.StaticOwner>().any {
                it.effect is StaticOwnerEffect.WriterInterest
            })
            assertTrue(f.events.filterIsInstance<RunEvent.StaticOwner>().none {
                it.effect is StaticOwnerEffect.PointReady
            }, "An unanswered interest is not an actual group supply")
            assertTrue(f.vulnerabilities.witnessesOf(1).none())
        }
    }
}

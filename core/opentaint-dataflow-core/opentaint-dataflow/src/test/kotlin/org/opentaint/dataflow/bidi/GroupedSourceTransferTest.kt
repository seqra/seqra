package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.MethodLocalServicesFactory
import org.opentaint.dataflow.bidi.engine.RunMethodLocalServices
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

/** Exact outputs distinguish existing unary AP transfer from fixed MULTI generation. */
class GroupedSourceTransferTest {
    private fun evaluate(unary: Boolean, multi: Boolean): Set<Tail> {
        MethodAnalysisFixtures().use { f ->
            val p = AccessPathBase.Argument(0)
            val q = AccessPathBase.Return
            val anchor = f.run1Pattern(p, listOf(f.field("f")))
            val builder = RulePredicate.Builder(f.cancellation, f.manager.marks)
            val guard = builder.build(builder.leaf(anchor))
            val owner = FormOwner()
            val id = RuleOccurrenceId(FormKey(owner, f.key.method, f.key, f.inst(1)), RuleKind.CALL_SOURCE, 0)
            val action = SourceAction(ActionId(id, ActionRole.SOURCE, 0),
                f.run1Pattern(q, tail = Tail.ANY_TAINT).fact)
            val source = SourceRuleOccurrence(id, guard, listOf(action), groupTransfers = true,
                unaryTransfers = if (unary) listOf(SourceUnaryTransfer(guard, anchor)) else emptyList(),
                multiPredicate = guard.takeIf { multi })
            val core = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            val original = f.manager.initial(f.run1Pattern(p, tail = Tail.STAR))
            val incoming = f.run1Fact(p, tail = Tail.ANY)
            val outputs = arrayListOf<Facts>()
            core.localServices.sourcePredicate(source, original, incoming, f.inst(1), statementEdge = false) { premise, value ->
                assertEquals(original, premise, "Conditional generation retains its actual ordinary original")
                assertEquals(Layer.DEMAND, value.layer)
                assertEquals(q, value.base)
                outputs += value
            }
            assertEquals(if (unary && multi) 2 else 1, outputs.size)
            return outputs.flatMap { f.ops.leaves(it).map { leaf -> leaf.fact.tail }.toList() }.toSet()
        }
    }

    @Test fun `collapsed one-leaf MULTI keeps ANY_TAINT target construction`() {
        assertEquals(setOf(Tail.ANY_TAINT), evaluate(unary = false, multi = true))
    }

    @Test fun `unary uses the original ANY input rather than its checked exact piece`() {
        assertEquals(setOf(Tail.ANY), evaluate(unary = true, multi = false))
    }

    @Test fun `a mixed original family retains both existing transfer outputs`() {
        assertEquals(setOf(Tail.ANY, Tail.ANY_TAINT), evaluate(unary = true, multi = true))
    }
}

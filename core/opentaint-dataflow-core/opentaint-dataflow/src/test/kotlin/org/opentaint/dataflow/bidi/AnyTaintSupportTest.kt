package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.HistoricalSupport
import org.opentaint.dataflow.bidi.fixtures.SupportFixtures
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

/** Historical support laws, not current automatic confirmation. */
class AnyTaintSupportTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)

    private data class SupplyCell(
        val name: String,
        val addedPath: List<Int> = emptyList(),
        val addedE: List<Int> = emptyList(),
        val queryPath: List<Int> = emptyList(),
        val queryTail: Tail = Tail.EXACT,
        val queryE: List<Int> = emptyList(),
        val queryBase: AccessPathBase = AccessPathBase.Argument(0),
        val queryMark: String = "T",
        val restricted: Boolean,
    )

    @Test
    fun `restricted must suppliers follow the independently specified location exclusion and mark table`(): Unit {
        for (index in listOf(1, 3)) SupportFixtures(index).use { f ->
            val root = f.key("root")
            val a = f.field("a")
            val b = f.field("b")
            val c = f.field("c")
            val rows = listOf(
                SupplyCell("exact at supplier root", addedE = listOf(b), restricted = true),
                SupplyCell("exact admitted first field", addedE = listOf(b), queryPath = listOf(a), restricted = true),
                SupplyCell("exact excluded first field", addedE = listOf(b), queryPath = listOf(b), restricted = false),
                SupplyCell("later excluded field is admitted", addedE = listOf(b), queryPath = listOf(a, b), restricted = true),
                SupplyCell("excluded first field remains excluded", addedE = listOf(b), queryPath = listOf(b, a), restricted = false),
                SupplyCell("must equal own E", addedE = listOf(b), queryTail = Tail.ANY_TAINT, queryE = listOf(b), restricted = true),
                SupplyCell("must narrower own E", addedE = listOf(b), queryTail = Tail.ANY_TAINT, queryE = listOf(a, b), restricted = true),
                SupplyCell("must broader own E", addedE = listOf(b), queryTail = Tail.ANY_TAINT, restricted = false),
                SupplyCell("must incomparable own E", addedE = listOf(b), queryTail = Tail.ANY_TAINT, queryE = listOf(a), restricted = false),
                SupplyCell("must below admitted branch", addedE = listOf(b), queryPath = listOf(a), queryTail = Tail.ANY_TAINT, restricted = true),
                SupplyCell("must below excluded branch", addedE = listOf(b), queryPath = listOf(b), queryTail = Tail.ANY_TAINT,
                    queryE = listOf(a), restricted = false),
                SupplyCell("exact at nested supplier root", addedPath = listOf(a), addedE = listOf(b), queryPath = listOf(a), restricted = true),
                SupplyCell("exact nested excluded continuation", addedPath = listOf(a), addedE = listOf(b), queryPath = listOf(a, b), restricted = false),
                SupplyCell("exact nested admitted continuation", addedPath = listOf(a), addedE = listOf(b), queryPath = listOf(a, c), restricted = true),
                SupplyCell("exact above supplier", addedPath = listOf(a), addedE = listOf(b), restricted = false),
                SupplyCell("must above supplier", addedPath = listOf(a), addedE = listOf(b), queryTail = Tail.ANY_TAINT,
                    queryE = listOf(a, b), restricted = false),
                SupplyCell("different concrete mark", addedE = listOf(b), queryMark = "U", restricted = false),
                SupplyCell("different base", addedE = listOf(b), queryBase = y, restricted = false),
                SupplyCell("may member is inadmissible", queryTail = Tail.ANY, restricted = false),
                SupplyCell("FLOW member is inadmissible", queryTail = Tail.STAR, queryMark = "*", restricted = false),
            )
            val questions = rows.map { row ->
                val callee = f.key(row.name)
                f.link(callee, root, f.manager.zero, f.fact(x, row.addedPath, Tail.ANY_TAINT,
                    exclusion = ExclusionSet.of(row.addedE.toIntArray())))
                val mark = if (row.queryMark == "*") MarkSlot.STAR else MarkSlot.Concrete(f.mark(row.queryMark))
                callee to f.initial(row.queryBase, row.queryPath, row.queryTail, mark, ExclusionSet.of(row.queryE.toIntArray()))
            }
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            rows.zip(questions).forEach { (row, question) ->
                assertEquals(index == 3 && row.restricted, support.isSupported(question.first, question.second.members),
                    "run=$index cell=${row.name}")
            }
        }
    }

    @Test
    fun `root nonzero must has no root privilege while identical must links are restricted suppliers`(): Unit {
        for (index in listOf(1, 3)) SupportFixtures(index).use { f ->
            val root = f.key("root")
            val callee = f.key("callee")
            val e = ExclusionSet.of(f.field("blocked"))
            val must = f.initial(tail = Tail.ANY_TAINT, exclusion = e)
            f.link(callee, root, f.manager.zero, f.fact(tail = Tail.ANY_TAINT, exclusion = e))
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            assertFalse(support.isSupported(root, must.members))
            assertEquals(index == 3, support.isSupported(callee, must.members))
            assertTrue(support.isSupported(root, f.manager.zero.members))
        }
    }

    @Test
    fun `normal must sink facts are confirmable with root zero independently of fact tail and E`(): Unit {
        for (index in listOf(1, 3)) SupportFixtures(index).use { f ->
            val root = f.key("root")
            val e = ExclusionSet.of(f.field("blocked"))
            val normal = f.witness(root, facts = f.fact(tail = Tail.ANY_TAINT, exclusion = e))
            val demand = f.witness(root, layer = Layer.DEMAND, facts = f.fact(tail = Tail.ANY, layer = Layer.DEMAND), alternative = 1)
            HistoricalSupport(f.result(), listOf(root), f.mode, f.shared).confirm()
            assertTrue(normal.confirmed, "A normal sink part need not have an exact tail")
            assertFalse(demand.confirmed)
            assertEquals(e, (normal.edges.single().facts as TaintTree).exclusion)
        }
    }

    @Test
    fun `restricted confirmation retains must premise exclusions and rejects excluded members`(): Unit {
        SupportFixtures(3).use { f ->
            val root = f.key("root")
            val callee = f.key("callee")
            val a = f.field("a")
            val b = f.field("b")
            val supplierE = ExclusionSet.of(b)
            f.link(callee, root, f.manager.zero, f.fact(tail = Tail.ANY_TAINT, exclusion = supplierE))
            val admitted = f.initial(tail = Tail.ANY_TAINT, exclusion = ExclusionSet.of(intArrayOf(a, b)))
            val excluded = f.initial(path = listOf(b), tail = Tail.ANY_TAINT)
            val admittedWitness = f.witness(callee, admitted, facts = f.fact(y, tail = Tail.ANY_TAINT,
                exclusion = ExclusionSet.of(a)), alternative = 0)
            val excludedWitness = f.witness(callee, excluded, facts = f.fact(y, tail = Tail.ANY_TAINT), alternative = 1)
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            support.confirm()
            assertTrue(admittedWitness.confirmed)
            assertFalse(excludedWitness.confirmed)
        }
    }

    @Test
    fun `transitive support checks the caller must own E rather than only its final exact supplier`(): Unit {
        for (narrow in listOf(false, true)) SupportFixtures(3).use { f ->
            val root = f.key("root")
            val caller = f.key("caller")
            val callee = f.key("callee")
            val a = f.field("a")
            val b = f.field("b")
            f.link(caller, root, f.manager.zero, f.fact(tail = Tail.ANY_TAINT, exclusion = ExclusionSet.of(b)))
            val callerPremise = f.initial(tail = Tail.ANY_TAINT,
                exclusion = if (narrow) ExclusionSet.of(intArrayOf(a, b)) else ExclusionSet.Empty)
            val exact = f.initial(y)
            f.link(callee, caller, callerPremise, f.fact(y))
            val witness = f.witness(callee, exact, facts = f.fact(y))
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            assertEquals(narrow, support.isSupported(caller, callerPremise.members))
            assertEquals(narrow, support.isSupported(callee, exact.members))
            support.confirm()
            assertEquals(narrow, witness.confirmed)
        }
    }
}

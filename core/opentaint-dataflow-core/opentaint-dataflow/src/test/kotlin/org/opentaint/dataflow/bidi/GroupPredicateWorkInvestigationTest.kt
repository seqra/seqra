package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.ConjunctionStore
import org.opentaint.dataflow.bidi.store.RulePredicateStore
import kotlin.test.*

/** The GROUP DAG here is explicitly constructed in projected coordinates.
 * Actual provider projection/normalization is checked by the JVM provider tests.
 * The FULL control retains real alternatives; it is not a GROUP runtime fallback. */
class GroupPredicateWorkInvestigationTest {
    @Test fun `normalized group conditions merge aliases but retain all argument positions`() {
        for (width in listOf(3, 4)) {
            val fixture = StoreFixtures()
            val manager = ApManager(fixture.cancellation)
            val owner = MarkGroupKey.Rule("group-predicate-work")
            fun mark(name: String): TaintMark {
                manager.marks.register(name, owner)
                return manager.marks.reserveFull(name).also(manager.marks::markEligible)
            }
            val first = List(width) { mark("T$it") }
            val second = List(width) { mark("U$it") }
            manager.marks.finishOwnershipCensus()
            val group = manager.marks.group(manager.marks.groupOf(first.first()))
            fun pattern(position: Int, mark: TaintMark) = Pattern(
                PathFact(AccessPathBase.Argument(position), emptyList(), Tail.EXACT, MarkSlot.Concrete(mark)),
                ExclusionSet.Empty)
            val ops = ApOps(manager)
            fun execute(mode: MarkMode, offerBoth: Boolean): List<RulePredicateStore.Combination> {
                val builder = RulePredicate.Builder(fixture.cancellation, manager.marks)
                val branches = List(width) { position ->
                    val left = if (mode == MarkMode.GROUP) group else first[position]
                    val right = if (mode == MarkMode.GROUP) group else second[position]
                    builder.or(listOf(builder.leaf(pattern(position, left)), builder.leaf(pattern(position, right))))
                }
                val predicate = builder.build(builder.and(branches))
                assertEquals(if (mode == MarkMode.GROUP) width else 2 * width, predicate.leaves.size)
                val occurrence = RuleOccurrenceId(FormKey(FormOwner(), fixture.commonMethod, fixture.method,
                    fixture.inst(1)), RuleKind.CALL_SOURCE, 0)
                val store = RulePredicateStore(manager)
                val apMode = ApMode(true, Direction.FORWARD, 1, mode)
                fun offer(): List<RulePredicateStore.Combination> {
                    val output = ArrayList<RulePredicateStore.Combination>()
                    repeat(width) { position ->
                        for (full in if (offerBoth) listOf(first[position], second[position]) else listOf(first[position])) {
                            val actual = manager.marks.runtimeAtom(full, mode)
                            val facts = ops.targetTree(pattern(position, full).fact, Layer.NORMAL, apMode)
                            val original = manager.initial(pattern(position, actual))
                            predicate.matchingLeaves(facts.base, actual) { ordinal, literal ->
                                val checked = assertIs<MarkCheck.Holds>(ops.checkMark(facts, literal, apMode))
                                store.add(occurrence, fixture.inst(1), predicate, ordinal,
                                    ConjunctionStore.Input(original, checked.facts.layer, checked.facts),
                                    output::add)
                            }
                        }
                    }
                    return output
                }
                val output = offer()
                assertTrue(offer().isEmpty(), "Identical actual input memberships must remain quiescent")
                for (combination in output) {
                    assertEquals(width, combination.inputs.size)
                    assertEquals(width, combination.premise.members.size)
                    assertEquals((0 until width).map { AccessPathBase.Argument(it) }.toSet(),
                        combination.leaves.map { predicate.leaves[it].fact.base }.toSet())
                }
                return output
            }
            assertEquals(1, execute(MarkMode.FULL, offerBoth = false).size)
            assertEquals(1 shl width, execute(MarkMode.FULL, offerBoth = true).size)
            assertEquals(1, execute(MarkMode.GROUP, offerBoth = false).size)
            assertEquals(1, execute(MarkMode.GROUP, offerBoth = true).size)
        }
    }
}

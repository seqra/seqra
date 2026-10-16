package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.ir.api.common.cfg.CommonInst
import kotlin.test.*

/** Expected raw conclusions are independent of the production predicate store and AP trie. */
class ReferenceSourcePredicateTest {
    private val owner = FormOwner()
    private val a = AccessPathBase.Argument(0)
    private val b = AccessPathBase.Argument(1)
    private val c = AccessPathBase.Argument(2)
    private val ret = AccessPathBase.Return

    private fun source(f: MethodAnalysisFixtures, leaves: List<Pattern>,
                       targets: List<PathFact> = listOf(f.pattern(ret).fact),
                       root: (RulePredicate.Builder, List<Int>) -> Int): SourceRuleOccurrence {
        val builder = RulePredicate.Builder(f.cancellation)
        val predicate = builder.build(root(builder, leaves.map(builder::leaf)))
        val id = RuleOccurrenceId(FormKey(owner, f.key.method, f.key, f.inst(2)), RuleKind.CALL_SOURCE, 0)
        return SourceRuleOccurrence(id, predicate, targets.mapIndexed { ordinal, target ->
            SourceAction(ActionId(id, ActionRole.SOURCE, ordinal), target)
        })
    }

    private data class Result(val premise: Set<Pattern>, val conclusion: Conclusion)
    private fun result(pr: Set<Pattern>, p: Pattern, demand: Boolean = false) =
        Result(pr, Conclusion(p.fact, p.exclusion, demand))

    /** Each harness owns one real actor and one reference algebra, as a method/run does. */
    private class Agreement(val f: MethodAnalysisFixtures, index: Int = 1,
                            val referencePositions: List<Pair<Set<Pattern>, RequestKind>> = emptyList()) {
        val raw = ArrayList<Result>()
        val tree = ArrayList<Result>()
        val rawRequests = ArrayList<Pair<Set<Pattern>, RequestKind>>()
        val treeRequests = ArrayList<Pair<Set<Pattern>, RequestKind>>()
        val core = f.core(index = index, localFactory = MethodLocalServicesFactory { actor ->
            val actual = RunMethodLocalServices(actor)
            object : MethodLocalServices by actual {
                override fun addMarkRequest(premise: InitialAp, mark: TaintMark) {
                            val kind = RequestKind.Mark(mark)
                    treeRequests += setOf(premise.toPattern()) to kind
                    actual.addMarkRequest(premise, mark)
                }
                override fun addMarkRequests(premise: InitialAp, marks: org.opentaint.dataflow.bidi.store.RequestMarkSet) {
                    marks.forEachMark { treeRequests += setOf(premise.toPattern()) to RequestKind.Mark(it) }
                    actual.addMarkRequests(premise, marks)
                }
            }
        })
        val algebra = ReferenceAlgebra(core.mode, request = { pr, kind -> rawRequests += pr to kind }, manager = f.manager,
            historicalRun1 = referencePositions.isNotEmpty())
        private val decoder = FormsReference(f.ops)

        fun give(source: SourceRuleOccurrence, premise: Set<Pattern>, input: Conclusion,
                 node: CommonInst = f.inst(2), statementEdge: Boolean = false) {
            algebra.sourcePredicate(source, premise, input, node, statementEdge) { pr, out -> raw += Result(pr, out) }
            val key = f.manager.premiseOf(premise.map(f.manager::initial))
            val facts = f.manager.factsOf(input.fact, input.exclusion, if (input.demand) Layer.DEMAND else Layer.NORMAL)
            core.localServices.sourcePredicate(source, key, facts, node, statementEdge) { pr, out ->
                val rawPremise = pr.members.map { it.toPattern() }.toSet()
                decoder.conclusions(out).forEach { tree += Result(rawPremise, it) }
            }
            assertEquals(raw.toSet(), tree.toSet(), "reference/tree outputs after $input")
            assertEquals(rawRequests.toSet(), (treeRequests + referencePositions).toSet(),
                "reference requests equal runtime marks plus the explicit removed Position control after $input")
        }
    }

    @Test
    fun `unary source changes its mark and drops the suffix while preserving the original premise`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val field = f.field("f")
            val u = MarkSlot.Concrete(f.mark("U"))
            val literal = f.pattern(a, tail = Tail.ANY)
            val target = f.pattern(ret, tail = Tail.ANY_TAINT, mark = u)
            val source = source(f, listOf(literal), listOf(target.fact)) { _, leaves -> leaves.single() }
            val original = setOf(f.pattern(b))
            val agreement = Agreement(f)
            agreement.give(source, original, Conclusion(f.pattern(a, listOf(field)).fact, ExclusionSet.Empty, false))
            assertEquals(listOf(result(original, target)), agreement.raw)
            agreement.give(source, original, Conclusion(f.pattern(a, listOf(field), mark = u).fact, ExclusionSet.Empty, false))
            assertEquals(1, agreement.raw.size)
            assertTrue(agreement.rawRequests.isEmpty())
        }
    }

    @Test
    fun `joined source unions full premises drops zero and propagates each effective layer to every action`(): Unit {
        for (order in listOf(listOf(a, b), listOf(b, a))) for (demand in listOf(false, true)) {
            MethodAnalysisFixtures().use { f ->
                val field = f.field("f")
                val pa = f.pattern(a, tail = Tail.ANY_TAINT, exclusion = ExclusionSet.of(field))
                val targets = listOf(f.pattern(ret, tail = Tail.ANY_TAINT), f.pattern(c))
                val source = source(f, listOf(f.pattern(a), f.pattern(b)), targets.map { it.fact }) { builder, leaves ->
                    builder.and(leaves)
                }
                val agreement = Agreement(f, index = 3)
                for ((position, base) in order.withIndex()) {
                    val pr = if (base == a) setOf(pa) else setOf(ZERO_PATTERN)
                    agreement.give(source, pr, Conclusion(f.pattern(base).fact, ExclusionSet.Empty, demand && base == b))
                    assertEquals(if (position == 0) 0 else 2, agreement.raw.size)
                }
                val expectedMust = targets[0]
                assertEquals(setOf(result(setOf(pa), expectedMust, demand), result(setOf(pa), targets[1], demand)),
                    agreement.raw.toSet())
                assertTrue(agreement.rawRequests.isEmpty())
            }
        }
    }

    @Test
    fun `must first accessor exclusion admits only the unblocked unary read`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val field = f.field("f")
            val other = f.field("g")
            val literal = f.pattern(a, listOf(field))
            val target = f.pattern(ret, tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(f.mark("U")))
            val source = source(f, listOf(literal), listOf(target.fact)) { _, leaves -> leaves.single() }
            val agreement = Agreement(f, index = 3)
            for (excluded in listOf(field, other)) {
                val initial = f.pattern(a, tail = Tail.ANY_TAINT, exclusion = ExclusionSet.of(excluded))
                agreement.give(source, setOf(initial), Conclusion(initial.fact, initial.exclusion, false))
            }
            val admitted = f.pattern(a, tail = Tail.ANY_TAINT, exclusion = ExclusionSet.of(other))
            assertEquals(listOf(result(setOf(admitted), target)), agreement.raw)
            assertTrue(agreement.rawRequests.isEmpty())
        }
    }

    @Test
    fun `FLOW requests keep the initial and excluded or restricted reads produce no source output`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val source = source(f, listOf(f.pattern(a), f.pattern(b))) { builder, leaves -> builder.and(leaves) }
            val initial = f.pattern(a, tail = Tail.STAR, mark = MarkSlot.STAR)
            val flow = Conclusion(initial.fact, ExclusionSet.Empty, false)
            val run1 = Agreement(f)
            run1.give(source, setOf(initial), flow)
            assertEquals(listOf<Pair<Set<Pattern>, RequestKind>>(setOf(initial) to RequestKind.Mark(f.mark("T"))), run1.rawRequests)
            assertTrue(run1.raw.isEmpty())
            val excluded = flow.copy(fact = flow.fact.copy(mark = MarkSlot.Star(MarkSet(intArrayOf(f.mark("T").id)))))
            run1.give(source, setOf(initial), excluded)
            assertEquals(1, run1.rawRequests.size)
            val restricted = Agreement(f, index = 3)
            restricted.give(source, setOf(initial), flow)
            assertTrue(restricted.raw.isEmpty())
            assertTrue(restricted.rawRequests.isEmpty())
            val noActions = SourceRuleOccurrence(source.id, source.predicate, emptyList())
            val emptySource = Agreement(f)
            emptySource.give(noActions, setOf(initial), flow)
            assertTrue(emptySource.raw.isEmpty())
            assertTrue(emptySource.rawRequests.isEmpty())
        }
    }

    @Test
    fun `historical root source position traffic has no selected runtime source output`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
            val field = f.field("f")
            val other = f.field("g")
            val source = source(f, listOf(f.pattern(STATIC, listOf(cls, field), Tail.ANY))) { _, leaves -> leaves.single() }
            val initial = f.pattern(STATIC, tail = Tail.STAR, mark = MarkSlot.STAR)
            val input = Conclusion(initial.fact, ExclusionSet.of(other), false)
            val removedPosition = setOf(initial) to RequestKind.Position(f.manager.path(listOf(cls, field))!!)
            val agreement = Agreement(f, referencePositions = listOf(removedPosition))
            agreement.give(source, setOf(initial), input, statementEdge = true)
            assertEquals(listOf<Pair<Set<Pattern>, RequestKind>>(setOf(initial) to RequestKind.Position(f.manager.path(listOf(cls, field))!!)),
                agreement.rawRequests)
            assertTrue(agreement.raw.isEmpty())
            agreement.give(source, setOf(initial), input.copy(exclusion = ExclusionSet.of(cls)), statementEdge = true)
            assertEquals(1, agreement.rawRequests.size)
        }
    }

    @Test
    fun `repeated canonical leaves keep one contribution and retain distinct original unary premises`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val target = f.pattern(ret)
            val source = source(f, listOf(f.pattern(a)), listOf(target.fact)) { builder, leaves ->
                builder.and(listOf(leaves.single(), leaves.single()))
            }
            val agreement = Agreement(f)
            for (pr in listOf(setOf(f.pattern(a)), setOf(f.pattern(b))))
                agreement.give(source, pr, Conclusion(f.pattern(a).fact, ExclusionSet.Empty, false))
            assertEquals(listOf(result(setOf(f.pattern(a)), target), result(setOf(f.pattern(b)), target)), agreement.raw)
            assertFalse(source.predicate.isMulti(0))
        }
    }

    @Test
    fun `standing source support belongs to one algebra and one statement occurrence`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val source = source(f, listOf(f.pattern(a), f.pattern(b))) { builder, leaves -> builder.and(leaves) }
            val first = Agreement(f)
            val second = Agreement(f)
            val inputA = Conclusion(f.pattern(a).fact, ExclusionSet.Empty, false)
            val inputB = Conclusion(f.pattern(b).fact, ExclusionSet.Empty, false)
            first.give(source, setOf(f.pattern(a)), inputA)
            second.give(source, setOf(f.pattern(b)), inputB)
            assertTrue(first.raw.isEmpty())
            assertTrue(second.raw.isEmpty())
            first.give(source, setOf(f.pattern(b)), inputB, node = f.inst(1))
            assertTrue(first.raw.isEmpty())
            first.give(source, setOf(f.pattern(b)), inputB)
            second.give(source, setOf(f.pattern(a)), inputA)
            val expected = setOf(result(setOf(f.pattern(a), f.pattern(b)), f.pattern(ret)))
            assertEquals(expected, first.raw.toSet())
            assertEquals(expected, second.raw.toSet())
        }
    }

    @Test
    fun `backward source keeps ordinary and forced demand branches in explicit and compatibility modes`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val u = MarkSlot.Concrete(f.mark("U"))
            val target = f.pattern(ret, mark = u)
            val source = source(f, listOf(f.pattern(a), f.pattern(b)), listOf(target.fact)) { builder, leaves ->
                builder.or(listOf(leaves[0], builder.and(leaves)))
            }.reversed()
            val input = Conclusion(target.fact, ExclusionSet.Empty, false)
            val premise = setOf(target)
            val agreement = Agreement(f, index = 2)
            agreement.give(source, premise, input)
            val expected = setOf(result(premise, f.pattern(a)), result(premise, f.pattern(a), true),
                result(premise, f.pattern(b), true))
            assertEquals(expected, agreement.raw.toSet())
            assertEquals(3, agreement.raw.size)
            assertTrue(agreement.rawRequests.isEmpty())
            val compatibility = ReferenceAlgebra(ApMode(true, Direction.FORWARD, 1), manager = f.manager,
                request = { _, _ -> error("A reversed source cannot request") })
            val results = ArrayList<Result>()
            compatibility.sourcePredicate(source, premise, input, f.inst(2), false) { pr, out -> results += Result(pr, out) }
            assertEquals(expected, results.toSet())
            val abstractInput = input.copy(fact = input.fact.copy(tail = Tail.STAR, mark = MarkSlot.STAR))
            compatibility.sourcePredicate(source, setOf(f.pattern(ret, tail = Tail.STAR, mark = MarkSlot.STAR)),
                abstractInput, f.inst(2), true) { _, _ -> fail("A restricted abstract source read cannot emit") }
        }
    }
}

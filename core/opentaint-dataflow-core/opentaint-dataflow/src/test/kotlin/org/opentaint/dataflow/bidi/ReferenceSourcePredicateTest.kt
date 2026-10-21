package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.fixtures.GroupRuleFixtureViews
import org.opentaint.dataflow.bidi.fixtures.GroupedScalarMarks
import org.opentaint.dataflow.bidi.fixtures.SelectedReferenceAlgebra
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
        val builder = RulePredicate.Builder(f.cancellation, f.manager.marks)
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
                            source: SourceRuleOccurrence? = null,
                            val referencePositions: List<Pair<Set<Pattern>, RequestKind>> = emptyList()) {
        val raw = ArrayList<Result>()
        val tree = ArrayList<Result>()
        val rawRequests = ArrayList<Pair<Set<Pattern>, RequestKind>>()
        val treeRequests = ArrayList<Pair<Set<Pattern>, RequestKind>>()
        private val seeds = SeedIndex.of(source?.let { occurrence ->
            occurrence.predicate.leaves.flatMap { literal -> occurrence.actions.map { action ->
                Seed.Source(f.key, f.inst(2), occurrence.transfer(literal, action).forward, action.id)
            } }
        }.orEmpty())
        val core = f.core(index = index, seeds = seeds, localFactory = MethodLocalServicesFactory { actor ->
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
        private val groups = if (core.config.run1) GroupedScalarMarks(f.manager.marks) else null
        val algebra: FactAlgebra<Set<Pattern>, Conclusion> = if (core.config.run1)
            SelectedReferenceAlgebra(core.mode, request = { pr, kind -> rawRequests += pr to kind },
                allows = { _, _ -> true }, hit = { _, _ -> }, manager = f.manager,
                join = { _, _, _, _, _ -> error("Source NNF owns its scalar joins") })
        else ReferenceAlgebra(core.mode, request = { pr, kind -> rawRequests += pr to kind }, manager = f.manager)
        fun expected(premise: Set<Pattern>, p: Pattern, demand: Boolean = false): Result =
            Result(premise.mapTo(LinkedHashSet()) { groups?.pattern(it) ?: it },
                groups?.conclusion(Conclusion(p.fact, p.exclusion, demand)) ?: Conclusion(p.fact, p.exclusion, demand))
        fun group(mark: TaintMark): TaintMark = groups?.atom(mark) ?: mark
        private val selectedViews = GroupRuleFixtureViews(f.manager.marks, f.cancellation)
        private val decoder = FormsReference(f.ops)

        fun give(source: SourceRuleOccurrence, premise: Set<Pattern>, input: Conclusion,
                 node: CommonInst = f.inst(2), statementEdge: Boolean = false) {
            val runtimePremise = premise.mapTo(LinkedHashSet()) { groups?.pattern(it) ?: it }
            val runtimeInput = groups?.conclusion(input) ?: input
            algebra.sourcePredicate(source, runtimePremise, runtimeInput, node, statementEdge) { pr, out -> raw += Result(pr, out) }
            val key = f.manager.premiseOf(runtimePremise.map(f.manager::initial))
            val facts = f.manager.factsOf(runtimeInput.fact, runtimeInput.exclusion,
                if (runtimeInput.demand) Layer.DEMAND else Layer.NORMAL)
            val selectedSource = if (core.config.run1) selectedViews.source(source) else source
            core.localServices.sourcePredicate(selectedSource, key, facts, node, statementEdge) { pr, out ->
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
            assertEquals(listOf(agreement.expected(original, target)), agreement.raw)
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
                val agreement = Agreement(f, index = 3, source = source)
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
            val agreement = Agreement(f, index = 3, source = source)
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
            assertEquals(listOf<Pair<Set<Pattern>, RequestKind>>(setOf(initial) to RequestKind.Mark(run1.group(f.mark("T")))), run1.rawRequests)
            assertTrue(run1.raw.isEmpty())
            val excluded = flow.copy(fact = flow.fact.copy(mark = MarkSlot.Star(MarkSet(intArrayOf(f.mark("T").id)))))
            run1.give(source, setOf(initial), excluded)
            assertEquals(2, run1.rawRequests.size, "An unchanged excluded Group still records its interest")
            val restricted = Agreement(f, index = 3, source = source)
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
    fun `historical root source Position remains a full scalar reference control`() {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
            val field = f.field("f")
            val other = f.field("g")
            val source = source(f, listOf(f.pattern(STATIC, listOf(cls, field), Tail.ANY))) { _, leaves -> leaves.single() }
            val initial = f.pattern(STATIC, tail = Tail.STAR, mark = MarkSlot.STAR)
            val input = Conclusion(initial.fact, ExclusionSet.of(other), false)
            val requests = arrayListOf<Pair<Set<Pattern>, RequestKind>>()
            val algebra = ReferenceAlgebra(ApMode(true, Direction.FORWARD, 1, MarkMode.FULL),
                request = { pr, kind -> requests += pr to kind }, manager = f.manager, historicalRun1 = true)
            algebra.sourcePredicate(source, setOf(initial), input, f.inst(2), true) { _, _ -> fail("Position is a request") }
            assertEquals(listOf<Pair<Set<Pattern>, RequestKind>>(setOf(initial) to
                RequestKind.Position(checkNotNull(f.manager.path(listOf(cls, field))))), requests)
            algebra.sourcePredicate(source, setOf(initial), input.copy(exclusion = ExclusionSet.of(cls)),
                f.inst(2), true) { _, _ -> fail("An excluded class cannot produce a source") }
            assertEquals(1, requests.size)
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
            assertEquals(listOf(agreement.expected(setOf(f.pattern(a)), target), agreement.expected(setOf(f.pattern(b)), target)), agreement.raw)
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
            // Complete admission remains local to one actor/statement. Each contributor then
            // gets its own atomic arm to the same fixed target; neither partial input emits ZERO.
            val expected = setOf(first.expected(setOf(f.pattern(a)), f.pattern(ret)),
                first.expected(setOf(f.pattern(b)), f.pattern(ret)))
            assertEquals(expected, first.raw.toSet())
            assertEquals(expected, second.raw.toSet())
            assertEquals(2, first.raw.size)
            assertEquals(2, second.raw.size)
            assertTrue(first.raw.all { it.premise.size == 1 && ZERO_PATTERN !in it.premise })
            first.give(source, setOf(f.pattern(a)), inputA)
            second.give(source, setOf(f.pattern(b)), inputB)
            assertEquals(2, first.raw.size, "Duplicate accepted input does not reemit a fixed result arm")
            assertEquals(2, second.raw.size)
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
            val compatibility = ReferenceAlgebra(ApMode(true, Direction.FORWARD, 1, markMode = MarkMode.FULL), manager = f.manager,
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

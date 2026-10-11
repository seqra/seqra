package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.RequestKind
import org.opentaint.dataflow.configuration.TaintCleanReach
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodPositionBaseTypeResolver
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveAp
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRRuleTestFixtures
import org.opentaint.dataflow.taint.RuleConditionRewriter
import org.opentaint.dataflow.taint.RuleConditionRewriter.ExprOrConstant
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.ContainsMarkLiteral
import org.opentaint.ir.api.common.cfg.CommonInst
import kotlin.test.*

/** Explicit facts test rule lowering; raw and tree walks share only the produced forms. */
class JIRLoweredCleanerEffectTest : BasicTestUtils() {
    private val fixture by lazy { JIRRuleTestFixtures(cp) }
    private val forms by lazy { fixture.forms() }
    private val m get() = fixture.manager
    private val ops get() = fixture.ops
    private val x = AccessPathBase.Argument(0)
    private val t get() = m.marks.mark("T")
    private val u get() = m.marks.mark("U")
    private val primitive get() = m.marks.mark("T%%primitive%%")
    private val mt get() = MarkSlot.Concrete(t)
    private val mu get() = MarkSlot.Concrete(u)
    private val mp get() = MarkSlot.Concrete(primitive)
    private val f get() = idx("f")
    private val g get() = idx("g")
    private val next get() = idx("next", CLS)
    private val element get() = m.accessors.index(ElementAccessor)
    private val premise get() = setOf(Pattern(PathFact(x, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty))
    private fun p(vararg ids: Int) = ids.toList()
    private fun ex(vararg ids: Int) = ExclusionSet.of(ids)
    private fun marks(vararg ids: Int) = MarkSlot.Star(MarkSet(ids))
    private fun idx(name: String, type: String = "java.lang.Object", owner: String = CLS) =
        m.accessors.index(FieldAccessor(owner, name, type))
    private fun at(base: Position, name: String, type: String = "java.lang.Object", owner: String = CLS) =
        PositionWithAccess(base, PositionAccessor.FieldAccessor(owner, name, type))
    private fun any(position: Position) = PositionWithAccess(position, PositionAccessor.AnyFieldAccessor)
    private fun c(base: AccessPathBase = x, path: List<Int> = emptyList(), tail: Tail = Tail.EXACT,
                  mark: MarkSlot = mt, exclusion: ExclusionSet = ExclusionSet.Empty,
                  demand: Boolean = tail == Tail.ANY) = Conclusion(PathFact(base, path, tail, mark), exclusion, demand)
    private fun flow(path: List<Int> = emptyList(), exclusion: ExclusionSet = ExclusionSet.Empty,
                     mark: MarkSlot = MarkSlot.STAR, base: AccessPathBase = x) = c(base, path, Tail.STAR, mark, exclusion)
    private fun must(path: List<Int> = emptyList(), exclusion: ExclusionSet = ExclusionSet.Empty,
                     mark: MarkSlot = mt, base: AccessPathBase = x) = c(base, path, Tail.ANY_TAINT, mark, exclusion)
    private fun steps(actions: List<Action>, method: String = "plain",
                      condition: ExprOrConstant = RuleConditionRewriter.trueExpr): List<CleanStep> {
        val owner = fixture.method(method)
        val rule = fixture.cleaner(actions, method = owner)
        assertTrue(forms.valid(rule))
        return forms.cleanSteps(rule, condition, JIRMethodPositionBaseTypeResolver(owner))
    }
    private fun selected(position: Position, mark: String = "T", reach: TaintCleanReach = TaintCleanReach.Exact) =
        RemoveMark(RuleMark(mark), position, reach)

    @Test fun `selected field action keeps outside FLOW marks and requests on the original incoming premise`(): Unit {
        val action = steps(listOf(selected(at(Argument(0), "f"))))
        val before = marks(u.id)
        val after = marks(t.id, u.id)
        val input = flow(exclusion = ex(g), mark = before)
        val originalPremise = premise
        val result = check(action, listOf(input), setOf(flow(exclusion = ex(f, g), mark = before),
            c(path = p(f), tail = Tail.ANY, mark = after)), run1 = true, inputPremise = originalPremise,
            expectedRequests = listOf(t))
        assertSame(originalPremise, result.requests.single().first)
        assertEquals(listOf(c(assertIs<CleanStep.Field>(action.single()).temporary, tail = Tail.ANY, mark = before)),
            result.temporaryInputs)
        assertEquals(before, result.facts.single { !it.demand }.fact.mark)
        val blocked = flow(exclusion = ex(f), mark = before)
        val noRead = check(action, listOf(blocked), setOf(blocked), run1 = true)
        assertTrue(noRead.temporaryInputs.isEmpty())
    }

    @Test fun `normal must ancestor follows exact below and subtree reach rows after field lowering`(): Unit {
        val input = must(exclusion = ex(g))
        val position = at(Argument(0), "f")
        val exact = steps(listOf(selected(position)))
        check(exact, listOf(input), setOf(must(exclusion = ex(f, g)), c(path = p(f), tail = Tail.ANY)))
        val below = steps(listOf(selected(any(position))))
        check(below, listOf(input), setOf(must(exclusion = ex(f, g)), c(path = p(f))))
        val subtree = steps(listOf(selected(position, reach = TaintCleanReach.ExactAndAnyField)))
        check(subtree, listOf(input), setOf(must(exclusion = ex(f, g))))
        val blocked = must(exclusion = ex(f, g))
        for (action in listOf(exact, below, subtree)) check(action, listOf(blocked), setOf(blocked))
    }

    @Test fun `must and may at the named field retain own E until the primitive root reach result`(): Unit {
        val position = at(Argument(0), "f")
        for (tail in listOf(Tail.ANY_TAINT, Tail.ANY)) {
            val demand = tail == Tail.ANY
            val exclusion = if (demand) ExclusionSet.Empty else ex(g)
            val input = c(path = p(f), tail = tail, exclusion = exclusion, demand = demand)
            check(steps(listOf(selected(position))), listOf(input), setOf(c(path = p(f), tail = Tail.ANY)))
            check(steps(listOf(selected(any(position)))), listOf(input), setOf(c(path = p(f), demand = demand)))
            check(steps(listOf(selected(position, reach = TaintCleanReach.ExactAndAnyField))), listOf(input), emptySet())
            check(steps(listOf(selected(position, mark = "U"))), listOf(input), setOf(input))
        }
    }

    @Test fun `concrete leaves distinguish exact below and all marks while preserving siblings and other marks`(): Unit {
        val position = at(Argument(0), "f")
        val atField = c(path = p(f))
        val below = c(path = p(f, next), demand = true)
        val sibling = c(path = p(g))
        val unrelatedMark = c(path = p(f), mark = mu)
        val inputs = listOf(atField, below, sibling, unrelatedMark)
        check(steps(listOf(selected(position))), inputs, setOf(below, sibling, unrelatedMark))
        check(steps(listOf(selected(any(position)))), inputs, setOf(atField, sibling, unrelatedMark))
        check(steps(listOf(selected(position, reach = TaintCleanReach.ExactAndAnyField))), inputs,
            setOf(sibling, unrelatedMark))
        check(steps(listOf(RemoveAllMarks(position))), inputs, setOf(sibling))
        check(steps(listOf(RemoveAllMarks(any(position)))), inputs, setOf(atField, sibling, unrelatedMark))
    }

    @Test fun `nested different owner resolves final temporary type without filtering root by the inner owner`(): Unit {
        val holder = idx("holder", HOLDER)
        val value = idx("value", owner = HOLDER)
        val position = at(at(Argument(0), "holder", HOLDER), "value", owner = HOLDER)
        val input = must(p(holder, value, next), ex(g), mu)
        val action = steps(listOf(selected(position)))
        check(action, listOf(input), setOf(input))
        val temp = assertIs<CleanStep.Field>(action.single()).temporary
        assertEquals(listOf(must(p(next), ex(g), mu, temp)), check(action, listOf(input), setOf(input)).temporaryInputs)
    }

    @Test fun `primitive field policy rejects ordinary T before cleaner and permits primitive tracking T`(): Unit {
        val field = idx("primitive", "int")
        val position = at(Argument(0), "primitive", "int")
        val action = steps(listOf(selected(position, mark = "U")))
        val rejected = check(action, listOf(c(path = p(field))), emptySet(), run1 = true)
        assertTrue(rejected.temporaryInputs.isEmpty())
        val tracked = c(path = p(field), mark = mp)
        check(action, listOf(tracked), setOf(tracked), run1 = true)
        val abstractAction = steps(listOf(selected(position)))
        check(abstractAction, listOf(flow()), setOf(flow(exclusion = ex(field)),
            c(path = p(field), tail = Tail.ANY, mark = marks(t.id))), run1 = true, expectedRequests = listOf(t))
    }

    @Test fun `result path filter precedes the positive read cut and removes invalid primitive element suffix`(): Unit {
        val bytes = idx("bytes", "byte[]")
        val action = steps(listOf(selected(at(Argument(0), "bytes", "byte[]"), mark = "U")))
        // byte[] admits [e], but its primitive byte element has no field f.
        val input = c(path = p(bytes, element, f), mark = mp)
        for (limit in listOf(1, 2, 3)) {
            val result = check(action, listOf(input), emptySet(), fieldLimit = limit)
            assertTrue(result.temporaryInputs.isEmpty(), "Value filtering precedes the read cut L=$limit")
            assertTrue(result.cuts.none { it.fact.base is AccessPathBase.CleanerTemporary })
        }
    }

    @Test fun `nested field actions cut each touched read and write output at positive limits one two and three`(): Unit {
        val position = at(at(Argument(0), "next", CLS), "f")
        val action = steps(listOf(selected(position)))
        val input = c(path = p(next, f, next), mark = mu)
        val untouched = must(p(next, next, f), ex(g), mu, AccessPathBase.This)
        for ((limit, output) in listOf(1 to c(path = p(next), tail = Tail.ANY, mark = mu),
            2 to c(path = p(next, f), tail = Tail.ANY, mark = mu), 3 to input)) {
            val result = check(action, listOf(input, untouched), setOf(output, untouched), fieldLimit = limit)
            assertTrue(result.cuts.any { it.fact.base is AccessPathBase.CleanerTemporary }, "The generated read reaches its cut")
            assertTrue(result.cuts.none { it.fact.base == AccessPathBase.This }, "Untouched input is not cut")
            assertTrue(result.facts.none { it.fact.base is AccessPathBase.CleanerTemporary })
        }
    }

    @Test fun `selected field action preserves distinct uncleaned prefix and cleaned target abstractions under cuts`(): Unit {
        val position = at(at(Argument(0), "next", CLS), "f")
        val action = steps(listOf(selected(position)))
        val before = marks(u.id)
        val after = marks(t.id, u.id)
        val input = flow(mark = before)
        for (limit in listOf(1, 2, 3)) {
            val target = if (limit == 1) p(next) else p(next, f)
            check(action, listOf(input), setOf(flow(exclusion = ex(next), mark = before),
                c(path = p(next), tail = Tail.ANY, mark = before),
                c(path = target, tail = Tail.ANY, mark = after)), fieldLimit = limit,
                run1 = true, expectedRequests = listOf(t))
        }
    }

    @Test fun `primitive F75 requests even for disjoint or already excluded abstract input and keeps original premise`(): Unit {
        val action = steps(listOf(selected(PositionWithAccess(Argument(0), PositionAccessor.ElementAccessor))), method = "arrayArg")
        assertIs<CleanStep.Clean>(action.single())
        val originalPremise = premise
        val inputs = listOf(flow(exclusion = ex(element), mark = marks(t.id)),
            flow(p(element, element), ex(g), marks(u.id)), c(path = p(element, element), tail = Tail.ANY, mark = marks(t.id)))
        for (input in inputs) {
            val expected = input.copy(fact = input.fact.copy(mark = marks(t.id, u.id).let {
                if (input.fact.mark == marks(t.id)) marks(t.id) else it
            }))
            val result = check(action, listOf(input), setOf(expected), run1 = true, inputPremise = originalPremise,
                expectedRequests = listOf(t))
            assertSame(originalPremise, result.requests.single().first)
            check(action, listOf(input), setOf(expected))
            check(action, listOf(input), setOf(expected), direction = Direction.BACKWARD)
        }
        val other = flow(p(next), mark = marks(u.id), base = AccessPathBase.This)
        check(action, listOf(other), setOf(other), run1 = true)
    }

    @Test fun `String companion is ordered uses separate temporary and requests again after T is excluded`(): Unit {
        val action = steps(listOf(selected(Argument(0))), method = "stringArg")
        assertEquals(2, action.size)
        val bytes = idx("<string-bytes>", "byte[]", "java.lang.String")
        val input = flow(mark = marks(u.id))
        val originalPremise = premise
        val result = check(action, listOf(input), setOf(flow(exclusion = ex(bytes), mark = marks(t.id, u.id)),
            c(path = p(bytes), tail = Tail.ANY, mark = marks(t.id, u.id))), run1 = true,
            inputPremise = originalPremise, expectedRequests = listOf(t, t))
        assertTrue(result.requests.all { it.first === originalPremise })
        assertEquals(1, result.temporaryInputs.size)
        assertEquals(marks(t.id, u.id), result.temporaryInputs.single().fact.mark)
        val already = flow(mark = marks(t.id, u.id))
        check(action, listOf(already), result.facts, run1 = true, expectedRequests = listOf(t, t))
    }

    @Test fun `multiple field actions keep rule order and project each temporary before the next action`(): Unit {
        val action = steps(listOf(selected(at(Argument(0), "f")), selected(at(Argument(0), "g"), mark = "U")))
        val result = check(action, listOf(flow()), setOf(flow(exclusion = ex(f, g)),
            c(path = p(f), tail = Tail.ANY, mark = marks(t.id)),
            c(path = p(g), tail = Tail.ANY, mark = marks(u.id))), run1 = true, expectedRequests = listOf(t, u))
        val first = assertIs<CleanStep.Field>(action[0]).temporary
        val second = assertIs<CleanStep.Field>(action[1]).temporary
        assertNotSame(first, second)
        assertEquals(listOf(first, second), result.temporaryInputs.map { it.fact.base })
        check(action, listOf(flow()), result.facts, run1 = true, expectedRequests = listOf(t, u))
    }

    @Test fun `conditional positive and negative cleaner actions neither mutate facts nor raise a request`(): Unit {
        for (negated in listOf(false, true)) {
            val condition = ExprOrConstant(ContainsMarkLiteral(Argument(0).resolveAp(), TaintMarkAccessor("T"), negated))
            val action = steps(listOf(selected(at(Argument(0), "f"))), condition = condition)
            assertTrue(action.isEmpty())
            val input = flow(mark = marks(u.id))
            check(action, listOf(input), setOf(input), run1 = true, fieldLimit = 1)
        }
    }

    @Test fun `static kills remove selected field subtree keep prefixes and leave unrelated bases uncut`(): Unit {
        val cls = m.accessors.index(ClassStaticAccessor(CLS))
        val field = idx("sField")
        val stat = AccessPathBase.ClassStatic
        val action = steps(listOf(RemoveAllMarks(at(ClassStatic(CLS), "sField"))))
        assertIs<CleanStep.Kill>(action.single())
        // This bare-static normal must is direct AP geometry outside JVM emission.
        val input = must(exclusion = ex(g), base = stat)
        val unrelated = must(p(next, next, f), ex(g), mu)
        check(action, listOf(input, unrelated), setOf(must(exclusion = ex(cls, g), base = stat),
            must(p(cls), ex(field), base = stat), unrelated), fieldLimit = 1)
        check(action, listOf(c(stat, p(cls, field, g))), emptySet())
        check(action, listOf(c(stat, p(cls, g))), setOf(c(stat, p(cls, g))))
    }

    @Test fun `run one static kill requests the class position on the original static FLOW premise`(): Unit {
        val cls = m.accessors.index(ClassStaticAccessor(CLS))
        val stat = AccessPathBase.ClassStatic
        val action = steps(listOf(RemoveAllMarks(at(ClassStatic(CLS), "sField"))))
        val originalPremise = setOf(Pattern(PathFact(stat, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty))
        check(action, listOf(flow(exclusion = ex(g), base = stat)),
            setOf(flow(exclusion = ex(cls, g), base = stat)), run1 = true, fieldLimit = 1,
            inputPremise = originalPremise, expectedPositions = listOf(checkNotNull(m.path(p(cls)))))
        val blocked = flow(exclusion = ex(cls), base = stat)
        check(action, listOf(blocked), setOf(blocked), run1 = true, fieldLimit = 1,
            inputPremise = originalPremise)
    }

    @Test fun `backward field action reverses write clean read shares cached temporary and omits forward type policy`(): Unit {
        val field = idx("primitive", "int")
        val action = steps(listOf(selected(at(Argument(0), "primitive", "int"), mark = "U")))
        val backward = action.asReversed().map { it.reversed() }
        assertSame(assertIs<CleanStep.Field>(action.single()).temporary, assertIs<CleanStep.Field>(backward.single()).temporary)
        // A forward primitive policy would remove ordinary T; the backward requirement is retained.
        val input = c(path = p(field), demand = true)
        check(backward, listOf(input), setOf(input), direction = Direction.BACKWARD)
        val selected = steps(listOf(selected(at(Argument(0), "f"))))
        val reverse = selected.asReversed().map { it.reversed() }
        check(reverse, listOf(c(path = p(f), demand = true)), emptySet(), direction = Direction.BACKWARD)
        val below = c(path = p(f, next), demand = true)
        check(reverse, listOf(below), setOf(below), direction = Direction.BACKWARD)
        val sibling = c(path = p(g), demand = true)
        check(reverse, listOf(sibling), setOf(sibling), direction = Direction.BACKWARD)
    }

    private data class WalkResult(val facts: Set<Conclusion>, val requests: List<Pair<Set<Pattern>, TaintMark>>,
                                  val temporaryInputs: List<Conclusion>, val cuts: List<Conclusion>)

    private fun check(steps: List<CleanStep>, inputs: List<Conclusion>, expected: Set<Conclusion>,
                      run1: Boolean = false, direction: Direction = Direction.FORWARD,
                      fieldLimit: Int = Int.MAX_VALUE, inputPremise: Set<Pattern>? = null,
                      expectedRequests: List<TaintMark> = emptyList(),
                      expectedPositions: List<PathNode> = emptyList()): WalkResult {
        require(inputs.isNotEmpty())
        val flowInput = inputs.first().fact.mark is MarkSlot.Star
        require(inputs.all { (it.fact.mark is MarkSlot.Star) == flowInput }) { "One caller edge has one conclusion kind (K6)" }
        val callerPremise = inputPremise ?: if (flowInput) premise else setOf(Pattern(
            PathFact(x, emptyList(), Tail.EXACT, inputs.first().fact.mark), ExclusionSet.Empty))
        val key = m.premiseOf(callerPremise.map(m::initial))
        require(key.isFlow == flowInput) { "The caller premise determines FLOW or TAINT (K6)" }
        val mode = ApMode(run1, direction, fieldLimit)
        val requests = ArrayList<Pair<Set<Pattern>, TaintMark>>()
        val positions = ArrayList<PathNode>()
        val temporaryInputs = ArrayList<Conclusion>()
        val cuts = ArrayList<Conclusion>()
        val oracle = FormsReference(ops, ReferenceAlgebra(mode, manager = m, request = { pr, kind ->
            when (kind) {
                is RequestKind.Mark -> requests += pr to kind.mark
                is RequestKind.Position -> positions += kind.path
            }
        }))
        val plan = CallPlan(inputs.map { it.fact.base }.toSet(), listOf(CallStage.Actions(CallPoint.BEFORE,
            CallPoint.AFTER, steps)), emptyList(), CallPoint.BEFORE, CallPoint.AFTER)
        val place = Place(fixture.method("plain").instList.instructions.first(), statementEdge = true, sources = false)
        val raw = oracle.run(plan, inputs.map { PlanItem(callerPremise, it, identityCandidate = true) }, place,
            PlanHooks(clean = { cleaner, item ->
                if (item.c.fact.base is AccessPathBase.CleanerTemporary) temporaryInputs += item.c
                val out = cleanRes(cleaner, item.c)
                if (run1) out.request?.let { requests += item.premise to it }
                out.facts.map { item.copy(c = it) }
            }, statementCut = { item -> cuts += item.c; item.copy(c = limit(item.c, fieldLimit)) }))
            .filter { it.first == CallPoint.AFTER }.map { it.second }
        assertTrue(raw.all { it.premise === callerPremise && it.identityCandidate == true })
        val actual = raw.map { it.c }.toSet()
        assertEquals(expected, actual, "raw inputs=$inputs direction=$direction L=$fieldLimit")
        assertEquals(expectedRequests, requests.map { it.second })
        assertEquals(expectedPositions, positions)
        assertTrue(actual.none { it.fact.base is AccessPathBase.CleanerTemporary })

        val treeRequests = ArrayList<TaintMark>()
        val treePositions = ArrayList<PathNode>()
        fun collecting(results: MutableList<Facts>) = object : ApOut {
            override fun result(f: Facts) { results += f }
            override fun markRequest(mark: TaintMark) { treeRequests += mark }
            override fun positionRequest(position: PathNode) { treePositions += position }
        }
        val applier = FormApplier(object : FactAlgebra<Set<Pattern>, Facts> {
            override fun base(c: Facts) = c.base
            override fun filter(c: Facts, filter: TypeFilter) = ops.filter(c, filter)
            override fun applyEdge(me: MicroEdge, c: Facts, premise: Set<Pattern>, statementEdge: Boolean,
                                   out: (Set<Pattern>, Facts) -> Unit) {
                ops.applyEdge(c, key, me.edge, statementEdge, mode, object : ApOut {
                    override fun result(f: Facts) = out(premise, f)
                    override fun markRequest(mark: TaintMark) { treeRequests += mark }
                    override fun positionRequest(position: PathNode) { treePositions += position }
                }, me.may)
            }
            override fun conjunction(cj: ConjunctiveEdge, premise: Set<Pattern>, c: Facts, node: CommonInst,
                                     out: (Set<Pattern>, Facts) -> Unit) = error("A cleaner has no conjunction")
            override fun allowsSource(node: CommonInst, me: MicroEdge) = error("A cleaner is not a source place")
            override fun sourceHit(node: CommonInst, me: MicroEdge) = error("A cleaner cannot record a source hit")
        })
        fun statement(summary: StatementSummary, input: Facts, cut: Boolean): List<Facts> = buildList {
            applier.statement(summary, callerPremise, input, place,
                sink = { _, result -> if (cut) ops.limit(result, fieldLimit, collecting(this)) else add(result) },
                untouched = { _, result -> add(result) })
        }
        fun clean(cleaner: Cleaner, input: Facts): List<Facts> = buildList { ops.clean(input, key, cleaner, mode, collecting(this)) }
        var trees = inputs.map { m.factsOf(it.fact, it.exclusion, if (it.demand) Layer.DEMAND else Layer.NORMAL) }
        for (step in steps) trees = trees.flatMap { input -> when (step) {
            is CleanStep.Clean -> clean(step.cleaner, input)
            is CleanStep.Kill -> statement(step.keepEdges, input, cut = false)
            is CleanStep.Field -> statement(step.read, input, cut = true).flatMap { clean(step.cleaner, it) }
                .flatMap { statement(step.write, it, cut = true) }.filter { it.base != step.temporary }
        } }
        assertEquals(expected, trees.flatMap(oracle::conclusions).toSet(), "tree inputs=$inputs direction=$direction L=$fieldLimit")
        assertEquals(expectedRequests, treeRequests)
        assertEquals(expectedPositions, treePositions)
        return WalkResult(actual, requests, temporaryInputs, cuts)
    }

    private companion object {
        const val CLS = "sample.bidi.BidiRuleSample"
        const val HOLDER = "sample.bidi.BidiRuleSample\$Holder"
    }
}

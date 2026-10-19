package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.ReferenceRequestAction as RequestAction

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonMethodParameter
import org.opentaint.ir.api.common.CommonTypeName
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonInstLocation
import org.opentaint.ir.api.common.cfg.ControlFlowGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Full-mark primitive/reference regression; selected GROUP behavior has separate differential controls. */
class RequestActionTest {
    private val manager = ApManager(Cancellation())
    private val ops = ApOps(manager)
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val cls = manager.accessors.index(ClassStaticAccessor("Box"))
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val run1 = ApMode(true, Direction.FORWARD, 3, markMode = MarkMode.FULL)
    private fun p(vararg accessors: Int) = accessors.toList()
    private fun ex(vararg accessors: Int) = ExclusionSet.of(accessors)
    private fun marks(vararg ids: Int) = MarkSet(ids)
    private fun pattern(path: List<Int>, tail: Tail, mark: MarkSlot = mt,
                        exclusion: ExclusionSet = ExclusionSet.Empty, base: AccessPathBase = x) =
        Pattern(PathFact(base, path, tail, mark), exclusion)
    private fun policy(path: List<Int> = p(), base: AccessPathBase = x): InitialAp =
        manager.initial(pattern(path, Tail.STAR, MarkSlot.STAR, base = base))
    private data class Context(val name: String) : MethodContext
    private val commonMethod = object : CommonMethod {
        override val name: String = "requestCaller"
        override val parameters: List<CommonMethodParameter> = emptyList()
        override val returnType: CommonTypeName = object : CommonTypeName {
            override val typeName: String = "void"
        }
        override fun flowGraph(): ControlFlowGraph<CommonInst> = error("unused")
    }
    private val entry = statement(0)
    private val call = statement(42)
    private val method = MethodKey(Context("caller-context"), entry)
    private val callerInitial = policy(p(g), y)
    private fun caller(initial: PremiseKey = callerInitial, layer: Layer = Layer.NORMAL) =
        CallerRef(method, initial, layer, call)

    @Test
    fun `concrete mark answers follow independent chain and exact rows and are canonical`() {
        data class Row(val request: InitialAp, val added: Pattern, val expected: Pattern)
        val root = policy()
        val atG = policy(p(g))
        val rows = listOf(
            Row(root, pattern(p(), Tail.EXACT), pattern(p(), Tail.EXACT)),
            Row(root, pattern(p(f, g), Tail.EXACT), pattern(p(), Tail.STAR)),
            Row(root, pattern(p(f), Tail.ANY), pattern(p(), Tail.STAR)),
            Row(root, pattern(p(f), Tail.ANY_TAINT, exclusion = ex(h)), pattern(p(), Tail.STAR)),
            Row(atG, pattern(p(g), Tail.EXACT), pattern(p(g), Tail.EXACT)),
            Row(atG, pattern(p(), Tail.ANY_TAINT, exclusion = ex(f)), pattern(p(g), Tail.STAR)),
            Row(atG, pattern(p(g, f), Tail.ANY_TAINT, exclusion = ex(h)), pattern(p(g), Tail.STAR)),
        )
        val kind = RequestKind.Mark(t)
        for (row in rows) {
            assertTrue(overlap(row.request.toPattern(), row.added))
            // The A02 raw answer is checked against explicit expected rows, rather than used to construct them.
            assertEquals(row.expected, answer(row.request.toPattern(), row.added, t))
            val result = assertIs<RequestAction.Answer>(ops.referenceRequestAction(row.request, kind, row.added, caller()))
            assertEquals(row.expected, result.initial.toPattern())
            assertSame(manager.initial(row.expected), result.initial)
        }
    }

    @Test
    fun `unexcluded abstract marks climb with the exact original caller premise and request`() {
        val kind = RequestKind.Mark(t)
        val request = policy()
        for (tail in listOf(Tail.STAR, Tail.ANY)) for (excluded in listOf(MarkSet.EMPTY, marks(u.id))) {
            val added = pattern(p(f), tail, MarkSlot.Star(excluded), if (tail == Tail.STAR) ex(h) else ExclusionSet.Empty)
            for (layer in Layer.entries) {
                val origin = caller(layer = layer)
                val result = assertIs<RequestAction.Climb>(ops.referenceRequestAction(request, kind, added, origin))
                assertSame(callerInitial, result.premise)
                assertSame(kind, result.request)
                assertSame(method, origin.caller)
                assertSame(call, origin.call)
                assertEquals(layer, origin.callerLayer)
                assertSame(callerInitial, origin.premise)
            }
        }
    }

    @Test
    fun `mismatched concrete marks and excluded abstract marks never answer or climb`() {
        val request = policy()
        val kind = RequestKind.Mark(t)
        val added = listOf(pattern(p(), Tail.EXACT, mu), pattern(p(f), Tail.ANY, mu),
            pattern(p(f), Tail.ANY_TAINT, mu, ex(h)),
            pattern(p(f), Tail.STAR, MarkSlot.Star(marks(t.id, u.id)), ex(h)),
            pattern(p(f), Tail.ANY, MarkSlot.Star(marks(t.id))))
        for (fact in added) {
            assertTrue(overlap(request.toPattern(), fact))
            assertSame(RequestAction.None, ops.referenceRequestAction(request, kind, fact, caller()))
        }
    }

    @Test
    fun `static mark answers keep at or below paths and convert added must to any with empty E`() {
        data class Row(val request: InitialAp, val added: Pattern, val expected: Pattern)
        val root = policy(base = STATIC)
        val atClass = policy(p(cls), STATIC)
        val atField = policy(p(cls, f), STATIC)
        val rows = listOf(
            Row(root, pattern(p(cls, f), Tail.EXACT, base = STATIC), pattern(p(cls, f), Tail.EXACT, base = STATIC)),
            Row(root, pattern(p(cls, f, g), Tail.ANY, base = STATIC), pattern(p(cls, f, g), Tail.ANY, base = STATIC)),
            Row(atClass, pattern(p(cls, f), Tail.ANY_TAINT, exclusion = ex(g, h), base = STATIC),
                pattern(p(cls, f), Tail.ANY, base = STATIC)),
            Row(atField, pattern(p(cls, f), Tail.ANY_TAINT, exclusion = ex(g), base = STATIC),
                pattern(p(cls, f), Tail.ANY, base = STATIC)),
            Row(atField, pattern(p(cls, f, g), Tail.ANY_TAINT, exclusion = ex(h), base = STATIC),
                pattern(p(cls, f, g), Tail.ANY, base = STATIC)),
            Row(atField, pattern(p(cls), Tail.ANY_TAINT, exclusion = ex(g), base = STATIC),
                pattern(p(cls, f), Tail.STAR, base = STATIC)),
        )
        for (row in rows) {
            assertTrue(overlap(row.request.toPattern(), row.added))
            assertEquals(row.expected, answer(row.request.toPattern(), row.added, t))
            val result = assertIs<RequestAction.Answer>(ops.referenceRequestAction(row.request, RequestKind.Mark(t), row.added, caller()))
            assertSame(manager.initial(row.expected), result.initial)
            assertEquals(row.expected, result.initial.toPattern())
            assertEquals(ExclusionSet.Empty, result.initial.exclusion)
            assertTrue(result.initial.tail != Tail.ANY_TAINT)
        }
    }

    @Test
    fun `position answers use the requested static prefix regardless of added tail mark or layer`() {
        val requestedPath = p(cls, f)
        val kind = RequestKind.Position(manager.path(requestedPath)!!)
        val request = policy(base = STATIC)
        val added = listOf(
            pattern(requestedPath, Tail.EXACT, base = STATIC),
            pattern(requestedPath, Tail.ANY, mu, base = STATIC),
            pattern(p(cls, f, g), Tail.ANY_TAINT, mt, ex(h), STATIC),
            pattern(p(cls, f, g), Tail.STAR, MarkSlot.Star(marks(t.id, u.id)), ex(h), STATIC),
            pattern(p(cls, f, g, h), Tail.ANY, MarkSlot.STAR, base = STATIC),
        )
        val expected = policy(requestedPath, STATIC)
        for (fact in added) for (layer in Layer.entries) {
            assertTrue(overlap(Pattern(PathFact(STATIC, requestedPath, Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty), fact))
            val result = assertIs<RequestAction.Answer>(ops.referenceRequestAction(request, kind, fact, caller(layer = layer)))
            assertSame(expected, result.initial)
            assertEquals(Tail.STAR, result.initial.tail)
            assertEquals(MarkSlot.STAR, result.initial.mark)
            assertEquals(ExclusionSet.Empty, result.initial.exclusion)
        }
    }

    @Test
    fun `position requests above their prefix climb only on a single static caller premise`() {
        val requestedPath = p(cls, f)
        val kind = RequestKind.Position(manager.path(requestedPath)!!)
        val request = policy(base = STATIC)
        val added = listOf(
            pattern(p(), Tail.ANY, MarkSlot.STAR, base = STATIC),
            pattern(p(cls), Tail.STAR, MarkSlot.Star(marks(u.id)), ex(g), STATIC),
            pattern(p(cls), Tail.ANY_TAINT, mt, ex(g), STATIC),
        )
        val staticCallers = listOf(policy(base = STATIC), policy(p(cls), STATIC),
            manager.initial(pattern(p(cls, h), Tail.EXACT, mu, base = STATIC)))
        val concrete = manager.initial(pattern(p(), Tail.EXACT, mu))
        val several = manager.premiseOf(listOf(concrete,
            manager.initial(pattern(p(cls, h), Tail.EXACT, mt, base = STATIC))))
        for (fact in added) {
            assertTrue(overlap(Pattern(PathFact(STATIC, requestedPath, Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty), fact))
            for (initial in staticCallers) {
                val result = assertIs<RequestAction.Climb>(ops.referenceRequestAction(request, kind, fact, caller(initial)))
                assertSame(initial, result.premise)
                assertSame(kind, result.request)
            }
            for (initial in listOf(callerInitial, concrete, several)) {
                assertSame(RequestAction.None, ops.referenceRequestAction(request, kind, fact, caller(initial)))
            }
        }
    }

    @Test
    fun `request premises must be unexcluded abstract star policy facts`() {
        val invalid = listOf(
            manager.initial(pattern(p(), Tail.EXACT)),
            manager.initial(pattern(p(), Tail.ANY)),
            manager.initial(pattern(p(), Tail.ANY, MarkSlot.STAR)),
            manager.initial(pattern(p(), Tail.ANY_TAINT)),
            manager.initial(pattern(p(), Tail.STAR)),
            manager.initial(pattern(p(), Tail.STAR, MarkSlot.STAR, ex(f))),
            manager.zero,
        )
        val kinds = listOf(RequestKind.Mark(t), RequestKind.Position(manager.path(p(cls, f))!!))
        for (request in invalid) for (kind in kinds) {
            val added = if (request.isZero) manager.zero.toPattern()
                else pattern(p(), Tail.EXACT, base = request.base)
            assertFails { ops.referenceRequestAction(request, kind, added, caller()) }
        }
    }

    @Test
    fun `caller values retain method context entry layer and call while a climb keeps the original key`() {
        val kind = RequestKind.Mark(t)
        val first = caller()
        val distinctContext = MethodKey(Context("other-context"), entry)
        val distinctEntry = MethodKey(method.context, statement(1))
        val distinctCall = statement(43)
        val origins = listOf(first, first.copy(caller = distinctContext), first.copy(caller = distinctEntry),
            first.copy(callerLayer = Layer.DEMAND), first.copy(call = distinctCall))
        assertEquals(origins.size, origins.toSet().size)
        assertNotEquals(first.caller, distinctContext)
        assertNotEquals(first.caller, distinctEntry)
        for (origin in origins) {
            val before = origin.copy()
            val result = assertIs<RequestAction.Climb>(ops.referenceRequestAction(policy(), kind,
                pattern(p(f), Tail.STAR, MarkSlot.STAR), origin))
            assertSame(origin.premise, result.premise)
            assertSame(kind, result.request)
            assertEquals(before, origin)
            assertSame(before.caller, origin.caller)
            assertSame(before.call, origin.call)
        }
    }

    @Test
    fun `mark requests answer and start as exact normal or chain demand without producing must premises`() {
        val request = policy()
        val abstractInput = manager.flowTree(x, Layer.NORMAL, ExclusionSet.Empty, MarkSet.EMPTY, FlowAlgebra.LEAF_NODE)
        val mark = assertIs<MarkCheck.Request>(ops.checkMark(abstractInput, pattern(p(), Tail.EXACT), run1)).mark
        assertEquals(t, mark)
        data class Row(val added: Pattern, val expected: Conclusion, val sink: Pattern)
        val rows = listOf(
            Row(pattern(p(), Tail.EXACT), Conclusion(PathFact(x, p(), Tail.EXACT, mt), ExclusionSet.Empty, false),
                pattern(p(), Tail.EXACT)),
            Row(pattern(p(f, g), Tail.EXACT), Conclusion(PathFact(x, p(), Tail.ANY, mt), ExclusionSet.Empty, true),
                pattern(p(f, g), Tail.EXACT)),
        )
        for (row in rows) {
            val action = assertIs<RequestAction.Answer>(ops.referenceRequestAction(request, RequestKind.Mark(mark), row.added, caller()))
            val started = ops.startFact(action.initial)
            assertEquals(row.expected, singleLeaf(started))
            val triggered = assertIs<MarkCheck.Holds>(ops.checkMark(started, row.sink, run1))
            assertEquals(started.layer, triggered.facts.layer)
            assertEquals(row.expected, singleLeaf(triggered.facts))
        }
        val staticMust = pattern(p(cls, f), Tail.ANY_TAINT, mt, ex(g), STATIC)
        val staticAnswer = assertIs<RequestAction.Answer>(ops.referenceRequestAction(policy(base = STATIC),
            RequestKind.Mark(t), staticMust, caller()))
        assertEquals(Conclusion(PathFact(STATIC, p(cls, f), Tail.ANY, mt), ExclusionSet.Empty, true),
            singleLeaf(ops.startFact(staticAnswer.initial)))
    }

    @Test
    fun `concrete summary input rejects an abstract caller while the standing callee request climbs`() {
        val request = policy()
        val literal = manager.initial(pattern(p(), Tail.ANY))
        val addedPattern = pattern(p(), Tail.STAR, MarkSlot.Star(marks(u.id)), ex(h))
        val added = manager.flowTree(x, Layer.NORMAL, ex(h), marks(u.id), FlowAlgebra.LEAF_NODE)
        assertFalse(satisfies(literal.toPattern(), addedPattern, restricted = false))
        assertNull(ops.satisfying(added, literal, run1))
        val kind = RequestKind.Mark(t)
        val result = assertIs<RequestAction.Climb>(ops.referenceRequestAction(request, kind, addedPattern, caller()))
        assertSame(callerInitial, result.premise)
        assertSame(kind, result.request)
    }

    /** Read the single emitted startup leaf directly; no production leaf-view helper supplies the expected shape. */
    private fun singleLeaf(facts: Facts): Conclusion {
        assertIs<TaintTree>(facts)
        val path = ArrayList<Int>()
        var at = facts.root
        while (at.leaf.isEmpty) {
            val accessors = at.accessors!!
            assertEquals(1, accessors.size)
            path += accessors.single()
            at = at.children!!.single()
        }
        assertNull(at.accessors)
        assertEquals(1, at.leaf.exact.ids.size + at.leaf.any.ids.size)
        val tail: Tail
        val mark: TaintMark
        if (!at.leaf.exact.isEmpty) {
            tail = Tail.EXACT
            mark = TaintMark(at.leaf.exact.ids.single())
        } else {
            tail = if (facts.layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY
            mark = TaintMark(at.leaf.any.ids.single())
        }
        return Conclusion(PathFact(facts.base, path, tail, MarkSlot.Concrete(mark)),
            if (tail == Tail.ANY_TAINT) facts.exclusion else ExclusionSet.Empty, facts.layer == Layer.DEMAND)
    }

    private fun statement(statementIndex: Int): CommonInst = object : CommonInst {
        override val location: CommonInstLocation = object : CommonInstLocation {
            override val method: CommonMethod = commonMethod
            override val index: Int = statementIndex
        }
    }
}

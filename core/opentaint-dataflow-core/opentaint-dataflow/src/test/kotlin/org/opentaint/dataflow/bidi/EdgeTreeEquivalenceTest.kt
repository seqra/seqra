package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Input leaves are stated independently. concat supplies the expected transfer, with requests kept separate. */
/** Full-mark primitive/reference regression; selected GROUP behavior has separate differential controls. */
class EdgeTreeEquivalenceTest {
    private val manager = testApManager(Cancellation())
    private val ops = ApOps(manager)
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val static = AccessPathBase.ClassStatic
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val k = manager.accessors.index(FieldAccessor("Box", "k", "Box"))
    private val c = manager.accessors.index(ClassStaticAccessor("Box"))
    private val element = manager.accessors.index(ElementAccessor)
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val v = manager.marks.mark("V")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val star = MarkSlot.STAR
    private val run1 = ApMode(true, Direction.FORWARD, Int.MAX_VALUE, markMode = MarkMode.FULL)
    private val forward = ApMode(false, Direction.FORWARD, Int.MAX_VALUE)
    private val backward = ApMode(false, Direction.BACKWARD, Int.MAX_VALUE)
    private val modes = listOf(run1, forward, backward)
    private data class Input(val facts: Facts, val leaves: List<Conclusion>)
    private data class Expected(val facts: Set<Conclusion>, val marks: Set<TaintMark>, val positions: Set<List<Int>>)
    private data class Payload(val exact: Set<Int> = emptySet(), val any: Set<Int> = emptySet())

    private fun p(vararg accessors: Int): List<Int> = accessors.toList()
    private fun ex(vararg accessors: Int): ExclusionSet = ExclusionSet.of(accessors)
    private fun marks(vararg ids: Int): MarkSet = MarkSet(ids)
    private fun value(path: List<Int>, tail: Tail, mark: MarkSlot = mt,
                      demand: Boolean = false, exclusion: ExclusionSet = ExclusionSet.Empty,
                      base: AccessPathBase = x): Conclusion = Conclusion(PathFact(base, path, tail, mark), exclusion, demand)

    /** Build the actual typed input from the stated leaves; no production leaf decoder supplies the oracle inputs. */
    private fun pack(leaves: List<Conclusion>): Input {
        require(leaves.isNotEmpty())
        val typed = leaves.map { manager.factsOf(it.fact, it.exclusion, if (it.demand) Layer.DEMAND else Layer.NORMAL) }
        val first = typed.first()
        require(typed.all { it.base == first.base && it.layer == first.layer })
        val facts = when (first) {
            is Reach -> first
            is FlowTree -> {
                require(typed.all { it is FlowTree && it.exclusion == first.exclusion && it.markExclusion == first.markExclusion })
                val root = typed.drop(1).fold(first.root) { root, next -> FlowAlgebra.mergeAdd(root, (next as FlowTree).root) }
                manager.flowTree(first.base, first.layer, first.exclusion, first.markExclusion, root, first.tail)
            }
            is TaintTree -> {
                require(typed.all { it is TaintTree })
                val root = typed.drop(1).fold(first.root) { root, next -> manager.taintAlg.mergeAdd(root, (next as TaintTree).root) }
                val exclusion = leaves.firstOrNull { it.fact.tail == Tail.ANY_TAINT }?.exclusion ?: ExclusionSet.Empty
                require(leaves.filter { it.fact.tail == Tail.ANY_TAINT }.all { it.exclusion == exclusion })
                manager.taintTree(first.base, first.layer, root, exclusion, (typed.firstOrNull { it is TaintTree && it.root.hasAny } as? TaintTree)?.anyTail ?: Tail.ANY_TAINT)
            }
        }
        return Input(facts, leaves)
    }

    private fun flow(paths: List<List<Int>>, demand: Boolean = false, mx: MarkSet = MarkSet.EMPTY,
                     exclusion: ExclusionSet = ExclusionSet.Empty, base: AccessPathBase = x): Input = pack(paths.map {
        value(it, if (demand) Tail.ANY else Tail.STAR, MarkSlot.Star(mx), demand, exclusion, base)
    })

    private fun taint(leaves: Map<List<Int>, Payload>, demand: Boolean = false,
                      exclusion: ExclusionSet = ExclusionSet.Empty, base: AccessPathBase = x): Input = pack(buildList {
        for ((path, payload) in leaves) {
            for (id in payload.exact) add(value(path, Tail.EXACT, MarkSlot.Concrete(TaintMark(id)), demand, base = base))
            for (id in payload.any) add(value(path, if (demand) Tail.ANY else Tail.ANY_TAINT,
                MarkSlot.Concrete(TaintMark(id)), demand, exclusion, base))
        }
    })

    private fun edge(fromTail: Tail, toTail: Tail, fromPath: List<Int> = p(f, g), toPath: List<Int> = p(k),
                     fromMark: MarkSlot = mt, toMark: MarkSlot = if (toTail == Tail.EXACT || toTail == Tail.ANY_TAINT) mu else star,
                     exclusion: ExclusionSet = ExclusionSet.Empty, fromE: ExclusionSet = ExclusionSet.Empty,
                     toE: ExclusionSet = ExclusionSet.Empty, fromBase: AccessPathBase = x, toBase: AccessPathBase = y): PathEdge =
        PathEdge(PathFact(fromBase, fromPath, fromTail, fromMark), PathFact(toBase, toPath, toTail, toMark), exclusion, fromE, toE)

    private fun premise(input: Input): InitialAp = when (input.facts) {
        is Reach -> manager.zero
        is FlowTree -> manager.initial(input.facts.base, null, Tail.STAR, ExclusionSet.Empty, star)
        is TaintTree -> manager.initial(input.facts.base, null, Tail.EXACT, ExclusionSet.Empty, mt)
    }

    private fun staticIdentity(leaf: Conclusion, premise: PremiseKey, statement: Boolean, mode: ApMode): Boolean {
        val initial = premise as? InitialAp ?: return false
        return statement && mode.run1 && !leaf.demand && leaf.fact.base == static && initial.base == static &&
            initial.tail == Tail.STAR && initial.mark == star && leaf.fact.tail == Tail.STAR &&
            leaf.fact.mark is MarkSlot.Star && leaf.fact.path == initial.pathArray.toList() && rootOrClass(leaf.fact.path)
    }

    private fun expected(input: Input, premise: PremiseKey, e: PathEdge, statement: Boolean, mode: ApMode, may: Boolean): Expected {
        val facts = linkedSetOf<Conclusion>()
        val marks = linkedSetOf<TaintMark>()
        val positions = linkedSetOf<List<Int>>()
        // F78 adds a creator obligation to ordinary concat. It does not change
        // concat's positional transfer or its concrete gate outcome.
        if (mode.run1 && input.facts is FlowTree && input.facts.base == e.from.base) {
            (e.to.mark as? MarkSlot.Star)?.excluded?.ids?.forEach { marks += TaintMark(it) }
        }
        for (leaf in input.leaves) when (val result = concat(leaf, e, may,
            staticIdentity(leaf, premise, statement, mode), mode.restricted, mode.run1)) {
            EdgeOutcome.None -> Unit
            is EdgeOutcome.Fact -> facts += result.conclusion
            is EdgeOutcome.Request -> marks += result.mark
            is EdgeOutcome.PositionRequest -> positions += result.path
        }
        return Expected(facts, marks, positions)
    }

    private fun decoded(results: List<Facts>): Set<Conclusion> = results.flatMap { facts ->
        ops.leaves(facts).map { pattern -> Conclusion(pattern.fact, pattern.exclusion, facts.layer == Layer.DEMAND) }.toList()
    }.toSet()

    private fun check(input: Input, e: PathEdge, mode: ApMode = run1, may: Boolean = false,
                      statement: Boolean = true, premise: PremiseKey = premise(input), name: String = ""): CollectingOut {
        val expected = expected(input, premise, e, statement, mode, may)
        val out = CollectingOut()
        ops.applyEdge(input.facts, premise, e, statement, mode, out, may)
        assertEquals(expected.facts, decoded(out.results), "$name facts")
        assertEquals(expected.marks, out.markRequests.toSet(), "$name mark requests")
        assertEquals(out.markRequests.toSet().size, out.markRequests.size, "$name duplicate mark requests")
        return out
    }

    @Test
    fun `tree transfer matches concat for every admitted generic tail cell geometry layer and mode`() {
        val geometries = listOf("above" to p(f), "at" to p(f, g), "below" to p(f, g, h, k), "apart" to p(f, h))
        for ((geometry, path) in geometries) {
            val inputs = listOf(
                pack(listOf(value(path, Tail.EXACT))), pack(listOf(value(path, Tail.EXACT, demand = true))),
                pack(listOf(value(path, Tail.ANY_TAINT, exclusion = ex(k)))),
                pack(listOf(value(path, Tail.ANY, demand = true))),
                flow(listOf(path), mx = marks(u.id), exclusion = ex(k)),
                flow(listOf(path), demand = true, mx = marks(u.id)),
            )
            for (fromTail in Tail.entries) for (toTail in Tail.entries) {
                if (fromTail == Tail.EXACT && toTail == Tail.STAR) continue
                val e = edge(fromTail, toTail,
                    exclusion = if (fromTail == Tail.STAR || toTail == Tail.STAR) ex(h) else ExclusionSet.Empty,
                    fromE = if (fromTail == Tail.ANY_TAINT) ex(k) else ExclusionSet.Empty,
                    toE = if (toTail == Tail.ANY_TAINT) ex(h) else ExclusionSet.Empty)
                for ((index, input) in inputs.withIndex()) for (mode in modes) for (may in listOf(false, true)) {
                    check(input, e, mode, may, name = "$geometry input $index $fromTail -> $toTail run1=${mode.run1} ${mode.direction} may=$may")
                }
            }
        }
    }

    @Test
    fun `abstract premise preserves marks and composes exclusions across multi-leaf inputs`() {
        val inputs = listOf(
            flow(listOf(p(), p(f), p(f, g), p(f, g, h), p(f, h), p(k)), mx = marks(u.id), exclusion = ex(k)),
            flow(listOf(p(), p(f, g), p(f, g, h), p(k)), demand = true, mx = marks(u.id)),
            taint(mapOf(p() to Payload(any = setOf(t.id)), p(f, g) to Payload(setOf(t.id, u.id), setOf(v.id)),
                p(f, g, h) to Payload(setOf(v.id)), p(k) to Payload(setOf(u.id))), exclusion = ex(k)),
            taint(mapOf(p() to Payload(any = setOf(t.id)), p(f, g) to Payload(setOf(t.id, u.id), setOf(v.id)),
                p(f, g, h) to Payload(setOf(v.id)), p(k) to Payload(setOf(u.id))), demand = true),
        )
        for (input in inputs) for (fromTail in listOf(Tail.STAR, Tail.ANY)) for (toTail in listOf(Tail.STAR, Tail.ANY)) {
            val e = edge(fromTail, toTail, fromMark = star, toMark = MarkSlot.Star(marks(v.id)),
                exclusion = if (fromTail == Tail.STAR || toTail == Tail.STAR) ex(h) else ExclusionSet.Empty)
            for (mode in modes) for (may in listOf(false, true)) check(input, e, mode, may)
        }
    }

    @Test
    fun `concrete gates rename marks compose exclusions and request only admitted uncleaned flow`() {
        val inputs = listOf(flow(listOf(p(f, g)), mx = marks(u.id)),
            flow(listOf(p(f, g)), mx = marks(t.id)),
            taint(mapOf(p(f, g) to Payload(setOf(t.id, u.id, v.id), setOf(t.id, v.id)))),
            taint(mapOf(p(f, g, k) to Payload(setOf(t.id, u.id), setOf(v.id))), demand = true))
        val edges = listOf(edge(Tail.STAR, Tail.EXACT), edge(Tail.STAR, Tail.STAR, toMark = mu),
            edge(Tail.STAR, Tail.STAR, toMark = MarkSlot.Star(marks(t.id))),
            edge(Tail.STAR, Tail.STAR, toMark = MarkSlot.Star(marks(u.id))),
            edge(Tail.STAR, Tail.ANY, fromMark = mu, toMark = mt))
        for (input in inputs) for (e in edges) for (mode in modes) for (may in listOf(false, true)) check(input, e, mode, may)
    }

    @Test
    fun `must own E and shared star target E both filter before flow requests`() {
        val e = edge(Tail.ANY_TAINT, Tail.STAR, fromPath = p(f), exclusion = ex(g), fromE = ex(h))
        val paths = listOf(p(f, g), p(f, h), p(f, k), p(f, k, g, h), p(f))
        for (path in paths) {
            val inputs = listOf(flow(listOf(path), mx = marks(u.id)),
                pack(listOf(value(path, Tail.EXACT))), pack(listOf(value(path, Tail.ANY_TAINT, exclusion = ex(h)))))
            for (input in inputs) for (mode in modes) for (may in listOf(false, true)) check(input, e, mode, may)
        }
        for (path in listOf(p(f, g), p(f, h))) {
            val out = check(flow(listOf(path), mx = marks(u.id)), e)
            assertTrue(out.results.isEmpty())
            assertTrue(out.markRequests.isEmpty())
        }
        assertEquals(listOf(t), check(flow(listOf(p(f, k, g, h)), mx = marks(u.id)), e).markRequests)
    }

    @Test
    fun `multi-leaf transfer includes prefix leaves and admitted descendants but omits off-spine leaves`() {
        val normal = taint(mapOf(p() to Payload(any = setOf(t.id)), p(f) to Payload(setOf(u.id), setOf(t.id)),
            p(f, g) to Payload(setOf(t.id, u.id), setOf(t.id)), p(f, g, h) to Payload(setOf(t.id)),
            p(f, g, k, element) to Payload(setOf(t.id, v.id), setOf(u.id)), p(f, h) to Payload(setOf(t.id)),
            p(g) to Payload(any = setOf(t.id))), exclusion = ex(k))
        val demand = taint(mapOf(p() to Payload(any = setOf(t.id)), p(f, g) to Payload(setOf(t.id, u.id), setOf(t.id)),
            p(f, g, k, element) to Payload(setOf(t.id, v.id)), p(f, h) to Payload(setOf(t.id))), demand = true)
        for (input in listOf(normal, demand)) for (toTail in Tail.entries) {
            val e = edge(Tail.STAR, toTail, exclusion = ex(h), toE = if (toTail == Tail.ANY_TAINT) ex(g) else ExclusionSet.Empty)
            for (mode in modes) for (may in listOf(false, true)) check(input, e, mode, may)
        }
    }

    @Test
    fun `reach sources preserve layer and must target E and zero bindings remain reach`() {
        for (layer in Layer.entries) {
            val input = pack(listOf(value(p(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO), layer == Layer.DEMAND,
                base = AccessPathBase.Zero)))
            for (toTail in listOf(Tail.EXACT, Tail.ANY, Tail.ANY_TAINT)) {
                val source = edge(Tail.EXACT, toTail, fromPath = p(), fromBase = AccessPathBase.Zero,
                    fromMark = MarkSlot.Concrete(TaintMark.ZERO), toMark = mt,
                    toE = if (toTail == Tail.ANY_TAINT) ex(h) else ExclusionSet.Empty)
                for (mode in modes) for (may in listOf(false, true)) check(input, source, mode, may)
            }
            val zeroKeep = edge(Tail.STAR, Tail.STAR, fromPath = p(), toPath = p(), fromBase = AccessPathBase.Zero,
                toBase = AccessPathBase.Zero, fromMark = star, toMark = star)
            for (mode in modes) check(input, zeroKeep, mode)
        }
        val concrete = taint(mapOf(p(f, g) to Payload(setOf(t.id))), demand = true)
        check(concrete, edge(Tail.EXACT, Tail.EXACT, toPath = p(), toBase = AccessPathBase.Zero,
            toMark = MarkSlot.Concrete(TaintMark.ZERO)))
        check(flow(listOf(p(f, g))), edge(Tail.STAR, Tail.EXACT, fromBase = AccessPathBase.Argument(1)))
        check(pack(listOf(value(p(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO), base = AccessPathBase.Zero))),
            edge(Tail.STAR, Tail.EXACT))
    }

    @Test
    fun `static identity parent remains no-result while the scalar oracle records a position`(): Unit {
        val readPath = p(c, f, g, h)
        for (q in listOf(p(), p(c))) {
            val input = flow(listOf(q), mx = marks(t.id), base = static)
            val initial = manager.initial(static, manager.path(q), Tail.STAR, ExclusionSet.Empty, star)
            for (fromTail in Tail.entries) for (toTail in Tail.entries) {
                if (fromTail == Tail.EXACT && toTail == Tail.STAR) continue
                val e = edge(fromTail, toTail, fromPath = readPath, fromBase = static)
                val out = check(input, e, premise = initial)
                assertEquals(setOf(p(c, f)), expected(input, initial, e, true, run1, false).positions)
                assertTrue(out.results.isEmpty())
                assertTrue(out.markRequests.isEmpty())
            }
        }
        val input = flow(listOf(p()), base = static)
        val root = manager.initial(static, null, Tail.STAR, ExclusionSet.Empty, star)
        check(input, edge(Tail.STAR, Tail.EXACT, fromPath = p(c), fromBase = static), premise = root)
        val blocked = flow(listOf(p()), exclusion = ex(c), base = static)
        assertTrue(check(blocked, edge(Tail.STAR, Tail.EXACT, fromPath = readPath, fromBase = static), premise = root)
            .results.isEmpty())
        val classInput = flow(listOf(p(c)), exclusion = ex(f), base = static)
        val classInitial = manager.initial(static, manager.path(p(c)), Tail.STAR, ExclusionSet.Empty, star)
        assertTrue(check(classInput, edge(Tail.STAR, Tail.EXACT, fromPath = readPath, fromBase = static),
            premise = classInitial).results.isEmpty())
    }

    @Test
    fun `concrete STAR static premise raises an ordinary mark request at root and class`() {
        val read = edge(Tail.STAR, Tail.EXACT, fromPath = p(c, f, g), fromBase = static)
        for (path in listOf(p(), p(c))) {
            val input = flow(listOf(path), base = static)
            val initial = manager.initial(static, manager.path(path), Tail.STAR, ExclusionSet.Empty, mt)
            val out = check(input, read, premise = initial)
            assertEquals(listOf(t), out.markRequests)
            assertTrue(out.results.isEmpty())
        }
    }

    @Test
    fun `binding summary restricted demand and deep static facts use ordinary concat`() {
        val rootInitial = manager.initial(static, null, Tail.STAR, ExclusionSet.Empty, star)
        val read = edge(Tail.STAR, Tail.EXACT, fromPath = p(c, f, g), fromBase = static)
        val root = flow(listOf(p()), base = static)
        check(root, read, statement = false, premise = rootInitial)
        check(root, read, mode = forward, premise = rootInitial)
        check(root, read, mode = backward, premise = rootInitial)
        check(flow(listOf(p()), demand = true, base = static), read, premise = rootInitial)
        val fieldInitial = manager.initial(static, manager.path(p(c, f)), Tail.STAR, ExclusionSet.Empty, star)
        check(flow(listOf(p(c, f)), base = static), read, premise = fieldInitial)
        check(flow(listOf(p(c)), base = static), read, premise = rootInitial)
        check(root, edge(Tail.STAR, Tail.STAR, fromPath = p(), toPath = p(), fromBase = static,
            toBase = static, fromMark = star), statement = false, premise = rootInitial)
        val multiple = flow(listOf(p(), p(c, f), p(k)), base = static)
        val out = check(multiple, read, premise = rootInitial)
        assertEquals(listOf(t), out.markRequests)
        assertEquals(setOf(p(c, f)), expected(multiple, rootInitial, read, true, run1, false).positions)
    }

    @Test
    fun `compiled edges reject every S7 S8 W1 and side-exclusion violation before base filtering`() {
        val malformed = listOf(
            edge(Tail.STAR, Tail.STAR, fromMark = MarkSlot.Star(marks(t.id))),
            edge(Tail.STAR, Tail.ANY, fromMark = star, toMark = mu),
            edge(Tail.EXACT, Tail.ANY, fromMark = star),
            edge(Tail.EXACT, Tail.STAR), edge(Tail.STAR, Tail.EXACT, fromMark = star, toMark = star),
            edge(Tail.STAR, Tail.ANY_TAINT, toMark = star), edge(Tail.ANY_TAINT, Tail.ANY, fromMark = star),
            edge(Tail.ANY, Tail.EXACT, exclusion = ex(h)),
            edge(Tail.STAR, Tail.STAR, fromE = ex(h)), edge(Tail.STAR, Tail.STAR, toE = ex(h)),
        )
        val input = pack(listOf(value(p(f, g), Tail.EXACT, base = AccessPathBase.Argument(1))))
        for ((index, e) in malformed.withIndex()) {
            assertFails("compile malformed edge $index") { manager.compile(e) }
            assertFails("apply malformed edge $index before base mismatch") {
                ops.applyEdge(input.facts, premise(input), e, true, run1, CollectingOut())
            }
        }
    }

    @Test
    fun `compiled edge owns its input path array and returns snapshots`() {
        val fromPath = intArrayOf(f, g)
        val compiled = CompiledEdge(x, fromPath, Tail.STAR, mt, y, manager.path(p(k)), Tail.EXACT, mu, ExclusionSet.Empty)
        fromPath[0] = h
        val snapshot = compiled.fromPath
        snapshot[1] = h
        assertContentEquals(intArrayOf(f, g), compiled.fromPath)
        assertNotSame(snapshot, compiled.fromPath)
    }

    @Test
    fun `unchanged edge cache is reused and recompiled for a different manager path owner`() {
        val e = edge(Tail.STAR, Tail.STAR, fromPath = p(f))
        val input = pack(listOf(value(p(f), Tail.EXACT)))
        assertNull(e.compiled)
        check(input, e)
        val first = assertNotNull(e.compiled)
        check(input, e)
        assertSame(first, e.compiled)
        val other = testApManager(Cancellation())
        for (accessor in listOf(FieldAccessor("Box", "f", "Box"), FieldAccessor("Box", "g", "Box"),
            FieldAccessor("Box", "h", "Box"), FieldAccessor("Box", "k", "Box"))) other.accessors.index(accessor)
        val otherT = other.marks.mark("T")
        val otherOps = ApOps(other)
        val otherFacts = other.factsOf(PathFact(x, p(f), Tail.EXACT, MarkSlot.Concrete(otherT)), ExclusionSet.Empty, Layer.NORMAL)
        val otherPremise = other.initial(x, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(otherT))
        val out = CollectingOut()
        otherOps.applyEdge(otherFacts, otherPremise, e, true, run1, out)
        assertEquals(setOf(value(p(k), Tail.EXACT, base = y)), decoded(out.results))
        val second = assertNotNull(e.compiled)
        assertNotSame(first, second)
        assertSame(other.path(p(k)), second.toPath)
        check(input, e)
        assertNotSame(second, e.compiled)
        assertSame(manager.path(p(k)), e.compiled?.toPath)
    }

    @Test
    fun `concurrent edge cache publication and transfer keep each call state local`() {
        val e = edge(Tail.STAR, Tail.STAR, exclusion = ex(h), toMark = MarkSlot.Star(marks(v.id)))
        val inputs = listOf(flow(listOf(p(), p(f, g), p(f, g, k)), mx = marks(u.id)),
            taint(mapOf(p() to Payload(any = setOf(t.id)), p(f, g) to Payload(setOf(t.id, u.id), setOf(t.id)),
                p(f, g, k) to Payload(setOf(t.id)), p(f, h) to Payload(setOf(t.id)))))
        val initial = inputs.map(::premise)
        val expected = inputs.mapIndexed { index, input -> expected(input, initial[index], e, true, run1, false) }
        val workers = 8
        val start = CyclicBarrier(workers)
        val pool = Executors.newFixedThreadPool(workers)
        try {
            val tasks = (0 until workers).map { worker -> Callable {
                start.await(30, TimeUnit.SECONDS)
                repeat(20) { iteration ->
                    val index = (worker + iteration) % inputs.size
                    val out = CollectingOut()
                    ops.applyEdge(inputs[index].facts, initial[index], e, true, run1, out)
                    assertEquals(expected[index].facts, decoded(out.results))
                    assertEquals(expected[index].marks, out.markRequests.toSet())
                    assertTrue(expected[index].positions.isEmpty()) // These reference inputs are nonstatic.
                }
                true
            } }
            for (future in pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
                assertFalse(future.isCancelled)
                assertTrue(future.get(30, TimeUnit.SECONDS))
            }
        } finally {
            pool.shutdownNow()
        }
        val compiled = assertNotNull(e.compiled)
        assertContentEquals(intArrayOf(f, g), compiled.fromPath)
        assertSame(manager.path(p(k)), compiled.toPath)
        check(inputs.first(), e)
        assertSame(compiled, e.compiled)
    }

    @Test
    fun `leaf views and nearby views preserve all marks tails layers and prefix admission`() {
        val inputs = listOf(
            flow(listOf(p(), p(f), p(f, g), p(f, k), p(h)), mx = marks(u.id), exclusion = ex(g)),
            flow(listOf(p(), p(f), p(f, g), p(f, k), p(h)), demand = true, mx = marks(u.id)),
            taint(mapOf(p() to Payload(setOf(u.id), setOf(t.id)), p(f) to Payload(setOf(t.id), setOf(u.id)),
                p(f, g) to Payload(setOf(t.id, v.id), setOf(u.id)), p(f, k) to Payload(setOf(u.id)),
                p(g, element) to Payload(any = setOf(v.id))), exclusion = ex(g)),
            taint(mapOf(p() to Payload(setOf(u.id), setOf(t.id)), p(f) to Payload(setOf(t.id), setOf(u.id)),
                p(f, g) to Payload(setOf(t.id, v.id), setOf(u.id)), p(g, element) to Payload(setOf(v.id))), demand = true),
            pack(listOf(value(p(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO), base = AccessPathBase.Zero))),
            pack(listOf(value(p(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO), demand = true, base = AccessPathBase.Zero))),
        )
        val queries = listOf(p(), p(f), p(f, g), p(f, g, h), p(f, k), p(h), p(g, element), p(k, h))
        for ((index, input) in inputs.withIndex()) {
            val leaves = input.leaves.map { Pattern(it.fact, it.exclusion) }.toSet()
            assertEquals(leaves, ops.leaves(input.facts).toSet(), "leaf views input $index")
            for (path in queries) {
                val query = Pattern(PathFact(input.facts.base, path, Tail.STAR, star), ExclusionSet.Empty)
                val near = leaves.filter { overlap(it, query) }.toSet()
                assertEquals(near, ops.leavesNear(input.facts, path.toIntArray()).toSet(), "near input $index at $path")
            }
        }
    }

    @Test
    fun `edge reversal delegates the reference tails marks and unsupported must guards`() {
        val edges = listOf(
            edge(Tail.STAR, Tail.STAR, fromMark = star, exclusion = ex(h)),
            edge(Tail.STAR, Tail.STAR, toMark = MarkSlot.Star(marks(u.id)), exclusion = ex(h)),
            edge(Tail.STAR, Tail.EXACT), edge(Tail.STAR, Tail.ANY),
            edge(Tail.ANY, Tail.STAR, exclusion = ex(h)), edge(Tail.ANY, Tail.EXACT), edge(Tail.ANY, Tail.ANY),
            edge(Tail.EXACT, Tail.EXACT), edge(Tail.EXACT, Tail.ANY),
            edge(Tail.EXACT, Tail.ANY_TAINT), edge(Tail.ANY, Tail.ANY_TAINT),
            edge(Tail.ANY_TAINT, Tail.EXACT, fromE = ex(h)),
            edge(Tail.EXACT, Tail.ANY_TAINT, toE = ex(h)),
        )
        for ((index, e) in edges.withIndex()) assertEquals(revEdge(e), ops.reverse(e), "reverse edge $index")
        assertNull(ops.reverse(edges[11]))
        assertNull(ops.reverse(edges[12]))
    }

    @Test
    fun `32768 depth exact must and flow transfers and sparse nearby views stay iterative`() {
        val path = List(32768) { f }
        val exact = pack(listOf(value(path, Tail.EXACT)))
        val must = pack(listOf(value(path, Tail.ANY_TAINT, exclusion = ex(g))))
        val demand = pack(listOf(value(path, Tail.ANY, demand = true)))
        val normalFlow = flow(listOf(path), mx = marks(u.id), exclusion = ex(g))
        val abstractCopy = edge(Tail.STAR, Tail.STAR, fromPath = p(), fromMark = star)
        for (input in listOf(exact, must, demand, normalFlow)) {
            check(input, edge(Tail.EXACT, Tail.EXACT, fromPath = path))
            check(input, abstractCopy)
        }
        val queryPaths = listOf(path, path.dropLast(1), path + g, path + h)
        for (input in listOf(exact, must, demand)) {
            val leaves = input.leaves.map { Pattern(it.fact, it.exclusion) }.toSet()
            for (queryPath in queryPaths) {
                val query = Pattern(PathFact(x, queryPath, Tail.STAR, star), ExclusionSet.Empty)
                assertEquals(leaves.filter { overlap(it, query) }.toSet(),
                    ops.leavesNear(input.facts, queryPath.toIntArray()).toSet())
            }
        }
        val root = flow(listOf(p()))
        assertEquals(listOf(t), check(root, edge(Tail.STAR, Tail.EXACT, fromPath = path)).markRequests)
        val staticRoot = flow(listOf(p()), base = static)
        val initial = manager.initial(static, null, Tail.STAR, ExclusionSet.Empty, star)
        val staticRead = edge(Tail.STAR, Tail.EXACT, fromPath = p(c) + path, fromBase = static)
        assertEquals(setOf(p(c, f)), expected(staticRoot, initial, staticRead, true, run1, false).positions)
        val parent = check(staticRoot, staticRead, premise = initial)
        assertTrue(parent.results.isEmpty())
        assertTrue(parent.markRequests.isEmpty())
    }

    @Test
    fun `transfer cancellation is checked during a sparse walk after its first request`() {
        val path = List(32768) { f }
        val input = flow(listOf(p(), path))
        val e = edge(Tail.STAR, Tail.EXACT, fromPath = path)
        var requests = 0
        val out = object : ApOut {
            override fun result(f: Facts) = Unit
            override fun markRequest(mark: TaintMark) {
                assertEquals(t, mark)
                requests++
                manager.cancellation.cancel()
            }
        }
        try {
            assertFailsWith<Cancellation.Cancelled> { ops.applyEdge(input.facts, premise(input), e, true, run1, out) }
            assertEquals(1, requests)
        } finally {
            manager.cancellation.activate()
        }
        assertEquals(listOf(t), check(input, e).markRequests)
        manager.cancellation.cancel()
        try {
            assertFailsWith<Cancellation.Cancelled> {
                ops.applyEdge(input.facts, premise(input), e, true, run1, CollectingOut())
            }
            assertFailsWith<Cancellation.Cancelled> { ops.leaves(input.facts).toList() }
            assertFailsWith<Cancellation.Cancelled> { ops.leavesNear(input.facts, path.toIntArray()).toList() }
        } finally {
            manager.cancellation.activate()
        }
    }
}

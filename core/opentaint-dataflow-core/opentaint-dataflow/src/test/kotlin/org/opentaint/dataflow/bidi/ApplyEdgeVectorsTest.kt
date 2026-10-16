package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.Conclusion
import org.opentaint.dataflow.bidi.ap.EdgeOutcome
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.MarkSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.PathEdge
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.bidi.ap.concat
import org.opentaint.dataflow.bidi.ap.normalize
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** Direct vectors from ap.md 4.1. The matrices cover generic concat, not native-summary reachability. */
class ApplyEdgeVectorsTest {
    private val manager = ApManager(Cancellation())
    private val source = AccessPathBase.Argument(0)
    private val target = AccessPathBase.Return
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val a = manager.accessors.index(FieldAccessor("Box", "a", "Box"))
    private val k = manager.accessors.index(FieldAccessor("Box", "k", "Box"))
    private val classC = manager.accessors.index(ClassStaticAccessor("Box"))
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val v = manager.marks.mark("V")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val star = MarkSlot.STAR
    private val none = EdgeOutcome.None
    private val requestT = EdgeOutcome.Request(t)
    private val tails = listOf(Tail.STAR, Tail.ANY, Tail.ANY_TAINT, Tail.EXACT)

    private fun ex(vararg accessors: Int): ExclusionSet = ExclusionSet.of(accessors)
    private fun mx(vararg marks: Int): MarkSlot.Star = MarkSlot.Star(MarkSet(marks))

    private fun input(path: List<Int>, tail: Tail, mark: MarkSlot = mt, demand: Boolean = false,
                      exclusion: ExclusionSet = ExclusionSet.Empty, base: AccessPathBase = source): Conclusion =
        Conclusion(PathFact(base, path, tail, mark), exclusion, demand)

    // Construct a stated expected value. No AP operation computes an expected result.
    private fun out(path: List<Int>, tail: Tail, mark: MarkSlot = mt, demand: Boolean = false,
                    exclusion: ExclusionSet = ExclusionSet.Empty, base: AccessPathBase = target): EdgeOutcome =
        EdgeOutcome.Fact(Conclusion(PathFact(base, path, tail, mark), exclusion, demand))

    private fun edge(fromTail: Tail, toTail: Tail, fromPath: List<Int> = listOf(f),
                     toPath: List<Int> = listOf(k), fromMark: MarkSlot = mt,
                     toMark: MarkSlot = if (toTail == Tail.ANY_TAINT || toTail == Tail.EXACT) mu else star,
                     exclusion: ExclusionSet = ExclusionSet.Empty,
                     fromExclusion: ExclusionSet = ExclusionSet.Empty,
                     toExclusion: ExclusionSet = ExclusionSet.Empty,
                     fromBase: AccessPathBase = source, toBase: AccessPathBase = target): PathEdge =
        PathEdge(PathFact(fromBase, fromPath, fromTail, fromMark),
            PathFact(toBase, toPath, toTail, toMark), exclusion, fromExclusion, toExclusion)

    private data class Row(val name: String, val input: Conclusion,
                           val normalEdge: List<EdgeOutcome>)

    private fun checkRows(rows: List<Row>, sourceTails: List<Tail>, fromPath: List<Int>) {
        for (row in rows) for (fromTail in sourceTails) for ((index, toTail) in tails.withIndex()) {
            // S8 excludes this one pair. The malformed-edge tests check its rejection.
            if (fromTail == Tail.EXACT && toTail == Tail.STAR) continue
            val e = edge(fromTail, toTail, fromPath = fromPath,
                fromExclusion = if (fromTail == Tail.ANY_TAINT) ex(a) else ExclusionSet.Empty,
                toExclusion = if (toTail == Tail.ANY_TAINT) ex(h) else ExclusionSet.Empty)
            assertEquals(row.normalEdge[index], concat(row.input, e),
                "${row.name}: $fromTail -> $toTail, NORMAL edge")
            assertEquals(when (val expected = row.normalEdge[index]) {
                is EdgeOutcome.Fact -> EdgeOutcome.Fact(expected.conclusion.copy(demand = true))
                else -> expected
            }, concat(row.input, e, edgeDemand = true),
                "${row.name}: $fromTail -> $toTail, DEMAND edge")
        }
    }

    @Test
    fun `at table covers all admitted source and target tails with both edge layers`() {
        val q = listOf(k)
        val rows = listOf(
            Row("exact normal", input(listOf(f), Tail.EXACT),
                listOf(out(q, Tail.EXACT), out(q, Tail.ANY, demand = true),
                    out(q, Tail.ANY_TAINT, mu, exclusion = ex(h)), out(q, Tail.EXACT, mu))),
            Row("exact demand", input(listOf(f), Tail.EXACT, demand = true),
                listOf(out(q, Tail.EXACT, demand = true), out(q, Tail.ANY, demand = true),
                    out(q, Tail.ANY_TAINT, mu, true, exclusion = ex(h)), out(q, Tail.EXACT, mu, true))),
            Row("may demand", input(listOf(f), Tail.ANY, demand = true),
                listOf(out(q, Tail.ANY, demand = true), out(q, Tail.ANY, demand = true),
                    out(q, Tail.ANY_TAINT, mu, true, exclusion = ex(h)), out(q, Tail.EXACT, mu, true))),
            Row("must normal", input(listOf(f), Tail.ANY_TAINT, exclusion = ex(g)),
                listOf(out(q, Tail.ANY_TAINT, exclusion = ex(g)), out(q, Tail.ANY, demand = true),
                    out(q, Tail.ANY_TAINT, mu, exclusion = ex(h)), out(q, Tail.EXACT, mu))),
            Row("abstract star normal", input(listOf(f), Tail.STAR, mx(u.id), exclusion = ex(g)),
                listOf(requestT, requestT, requestT, requestT)),
        )
        checkRows(rows, tails, listOf(f))
    }

    @Test
    fun `below table keeps suffix and Ec only for a star target`() {
        val p = listOf(f, g, h)
        val q = listOf(k)
        val qr = listOf(k, g, h)
        val rows = listOf(
            Row("exact normal", input(p, Tail.EXACT),
                listOf(out(qr, Tail.EXACT), out(q, Tail.ANY, demand = true),
                    out(q, Tail.ANY_TAINT, mu, exclusion = ex(h)), out(q, Tail.EXACT, mu))),
            Row("exact demand", input(p, Tail.EXACT, demand = true),
                listOf(out(qr, Tail.EXACT, demand = true), out(q, Tail.ANY, demand = true),
                    out(q, Tail.ANY_TAINT, mu, true, exclusion = ex(h)), out(q, Tail.EXACT, mu, true))),
            Row("may demand", input(p, Tail.ANY, demand = true),
                listOf(out(qr, Tail.ANY, demand = true), out(q, Tail.ANY, demand = true),
                    out(q, Tail.ANY_TAINT, mu, true, exclusion = ex(h)), out(q, Tail.EXACT, mu, true))),
            Row("must Ec applies after the input path", input(p, Tail.ANY_TAINT, exclusion = ex(g)),
                listOf(out(qr, Tail.ANY_TAINT, exclusion = ex(g)), out(q, Tail.ANY, demand = true),
                    out(q, Tail.ANY_TAINT, mu, exclusion = ex(h)), out(q, Tail.EXACT, mu))),
            Row("abstract star normal", input(p, Tail.STAR, mx(u.id), exclusion = ex(g)),
                listOf(requestT, requestT, requestT, requestT)),
        )
        checkRows(rows, listOf(Tail.STAR, Tail.ANY, Tail.ANY_TAINT), listOf(f))
    }

    @Test
    fun `an exact premise admits only the empty suffix before any mark request`() {
        val inputs = listOf(input(listOf(f, g), Tail.EXACT),
            input(listOf(f, g), Tail.EXACT, demand = true),
            input(listOf(f, g), Tail.ANY, demand = true),
            input(listOf(f, g), Tail.ANY_TAINT, exclusion = ex(a)),
            input(listOf(f, g), Tail.STAR, mx(u.id), exclusion = ex(a)))
        for (c in inputs) for (toTail in listOf(Tail.ANY, Tail.ANY_TAINT, Tail.EXACT)) {
            val e = edge(Tail.EXACT, toTail)
            assertEquals(none, concat(c, e), "below exact premise: ${c.fact.tail} -> $toTail")
            assertEquals(none, concat(c, e, edgeDemand = true))
        }
    }

    @Test
    fun `above table retains must precision and loses star correlation`() {
        val q = listOf(k)
        val rows = listOf(
            Row("exact normal cannot overlap from above", input(listOf(f), Tail.EXACT),
                listOf(none, none, none, none)),
            Row("exact demand cannot overlap from above", input(listOf(f), Tail.EXACT, demand = true),
                listOf(none, none, none, none)),
            Row("may demand", input(listOf(f), Tail.ANY, demand = true),
                listOf(out(q, Tail.ANY, demand = true), out(q, Tail.ANY, demand = true),
                    out(q, Tail.ANY, mu, true), out(q, Tail.EXACT, mu, true))),
            Row("must normal", input(listOf(f), Tail.ANY_TAINT, exclusion = ex(a)),
                listOf(out(q, Tail.ANY_TAINT), out(q, Tail.ANY, demand = true),
                    out(q, Tail.ANY_TAINT, mu, exclusion = ex(h)), out(q, Tail.EXACT, mu))),
            Row("abstract star normal", input(listOf(f), Tail.STAR, mx(u.id), exclusion = ex(a)),
                listOf(requestT, requestT, requestT, requestT)),
        )
        checkRows(rows, tails, listOf(f, g, h))
        val flow = input(listOf(f), Tail.STAR, mx(u.id), exclusion = ex(a))
        assertEquals(out(q, Tail.ANY, mx(u.id), true),
            concat(flow, edge(Tail.STAR, Tail.STAR, listOf(f, g), fromMark = star)))
        assertEquals(out(q, Tail.ANY, mx(u.id), true),
            concat(flow, edge(Tail.ANY, Tail.ANY, listOf(f, g), fromMark = star)))
    }

    @Test
    fun `shared exclusion unions at the path and only tests the first suffix accessor below it`() {
        val identity = edge(Tail.STAR, Tail.STAR, fromMark = star, exclusion = ex(h))
        assertEquals(out(listOf(k), Tail.STAR, mx(u.id), exclusion = ex(g, h)),
            concat(input(listOf(f), Tail.STAR, mx(u.id), exclusion = ex(g)), identity))
        assertEquals(out(listOf(k), Tail.ANY_TAINT, exclusion = ex(g, h)),
            concat(input(listOf(f), Tail.ANY_TAINT, exclusion = ex(g)), identity))
        assertEquals(out(listOf(k), Tail.EXACT), concat(input(listOf(f), Tail.EXACT), identity))
        assertEquals(out(listOf(k), Tail.ANY, demand = true),
            concat(input(listOf(f), Tail.ANY, demand = true), identity))
        assertEquals(out(listOf(k, a, h), Tail.EXACT),
            concat(input(listOf(f, a, h), Tail.EXACT), identity))
        assertEquals(none, concat(input(listOf(f, h), Tail.EXACT), identity))
        assertEquals(none, concat(input(listOf(f, h), Tail.STAR, star),
            edge(Tail.ANY, Tail.STAR, exclusion = ex(h))))
        assertEquals(out(listOf(k), Tail.STAR, mx(u.id), true, exclusion = ex(g, h)),
            concat(input(listOf(f), Tail.STAR, mx(u.id), exclusion = ex(g)), identity, edgeDemand = true))
    }

    @Test
    fun `must own premise and target exclusions are separate from current exclusion`() {
        val e = edge(Tail.ANY_TAINT, Tail.ANY_TAINT,
            fromExclusion = ex(g), toExclusion = ex(h))
        assertEquals(none, concat(input(listOf(f, g), Tail.EXACT), e))
        assertEquals(out(listOf(k), Tail.ANY_TAINT, mu, exclusion = ex(h)),
            concat(input(listOf(f, a), Tail.ANY_TAINT, exclusion = ex(a)), e))
        assertEquals(out(listOf(k), Tail.ANY_TAINT, mu, true, exclusion = ex(h)),
            concat(input(listOf(f, a), Tail.ANY_TAINT, exclusion = ex(a)), e, edgeDemand = true))
        assertEquals(out(listOf(k), Tail.EXACT, mu),
            concat(input(listOf(f, a), Tail.ANY_TAINT, exclusion = ex(a)),
                edge(Tail.ANY_TAINT, Tail.EXACT, fromExclusion = ex(g))))
        // In the above row, Ec tests the read and then the STAR target supplies the edge's E, not Ec.
        assertEquals(out(listOf(k), Tail.ANY_TAINT, exclusion = ex(h)),
            concat(input(listOf(f), Tail.ANY_TAINT, exclusion = ex(a)),
                edge(Tail.STAR, Tail.STAR, fromPath = listOf(f, g), exclusion = ex(h))))
        assertEquals(none, concat(input(listOf(f), Tail.ANY_TAINT, exclusion = ex(g)),
            edge(Tail.STAR, Tail.STAR, fromPath = listOf(f, g), exclusion = ex(h))))
    }

    @Test
    fun `generic must premise star target checks shared and own exclusions below`() {
        // Generic-only: no native must summary has a STAR conclusion, and no micro edge has a must premise.
        // The below table requires both the premise's own E and shared E to admit the first suffix accessor.
        val e = edge(Tail.ANY_TAINT, Tail.STAR, exclusion = ex(g), fromExclusion = ex(h))
        val rows = listOf(
            Triple("shared E rejects g", input(listOf(f, g), Tail.EXACT), none),
            Triple("own E rejects h", input(listOf(f, h), Tail.EXACT), none),
            Triple("both admit a", input(listOf(f, a), Tail.EXACT), out(listOf(k, a), Tail.EXACT)),
            Triple("later g and h do not reject", input(listOf(f, a, g, h), Tail.EXACT),
                out(listOf(k, a, g, h), Tail.EXACT)),
        )
        for ((name, c, expected) in rows) assertEquals(expected, concat(c, e), name)
        assertEquals(none, concat(input(listOf(f, g), Tail.EXACT), e, edgeDemand = true))
        assertEquals(none, concat(input(listOf(f, h), Tail.EXACT), e, edgeDemand = true))
        assertEquals(out(listOf(k, a, g, h), Tail.EXACT, demand = true),
            concat(input(listOf(f, a, g, h), Tail.EXACT), e, edgeDemand = true))
    }

    @Test
    fun `generic must premise star target applies shared E at and retains current E below`() {
        val e = edge(Tail.ANY_TAINT, Tail.STAR, exclusion = ex(g), fromExclusion = ex(h))
        val rows = listOf(
            Triple("at exact has no suffix to exclude", input(listOf(f), Tail.EXACT),
                out(listOf(k), Tail.EXACT)),
            Triple("at must unions current and shared E", input(listOf(f), Tail.ANY_TAINT, exclusion = ex(a)),
                out(listOf(k), Tail.ANY_TAINT, exclusion = ex(a, g))),
            Triple("below must retains only current E", input(listOf(f, a), Tail.ANY_TAINT, exclusion = ex(h)),
                out(listOf(k, a), Tail.ANY_TAINT, exclusion = ex(h))),
            Triple("current E does not exclude an accessor already in the input path",
                input(listOf(f, a), Tail.ANY_TAINT, exclusion = ex(a)),
                out(listOf(k, a), Tail.ANY_TAINT, exclusion = ex(a))),
        )
        for ((name, c, expected) in rows) assertEquals(expected, concat(c, e), name)
        assertEquals(out(listOf(k), Tail.ANY_TAINT, demand = true, exclusion = ex(a, g)),
            concat(input(listOf(f), Tail.ANY_TAINT, exclusion = ex(a)), e, edgeDemand = true))
        assertEquals(out(listOf(k, a), Tail.ANY_TAINT, demand = true, exclusion = ex(h)),
            concat(input(listOf(f, a), Tail.ANY_TAINT, exclusion = ex(h)), e, edgeDemand = true))
    }

    @Test
    fun `generic must premise star target excludes geometry before abstract mark requests`() {
        val e = edge(Tail.ANY_TAINT, Tail.STAR, exclusion = ex(g), fromExclusion = ex(h))
        val rows = listOf(
            Triple("shared E suppresses request", input(listOf(f, g), Tail.STAR, mx(u.id)), none),
            Triple("own E suppresses request", input(listOf(f, h), Tail.STAR, mx(u.id)), none),
            Triple("both admit first accessor before request", input(listOf(f, a, g, h), Tail.STAR, mx(u.id)),
                requestT),
            Triple("at has no suffix to exclude before request", input(listOf(f), Tail.STAR, mx(u.id)),
                requestT),
        )
        for ((name, c, expected) in rows) {
            assertEquals(expected, concat(c, e), name)
            assertEquals(expected, concat(c, e, edgeDemand = true), "$name with demand edge")
            assertEquals(none, concat(c, e, restricted = true), "$name in restricted run")
        }
    }

    @Test
    fun `mark gates preserve input marks rename output marks and compose mark exclusions`() {
        val c = input(listOf(f), Tail.EXACT)
        assertEquals(out(listOf(k), Tail.EXACT, mu), concat(c, edge(Tail.STAR, Tail.EXACT)))
        assertEquals(out(listOf(k), Tail.EXACT), concat(c, edge(Tail.STAR, Tail.EXACT, toMark = star)))
        assertEquals(out(listOf(k), Tail.EXACT, mu), concat(c, edge(Tail.STAR, Tail.STAR, toMark = mu)))
        assertEquals(out(listOf(k), Tail.ANY, mu, true), concat(c, edge(Tail.STAR, Tail.ANY, toMark = mu)))
        assertEquals(none, concat(input(listOf(f), Tail.EXACT, mu), edge(Tail.STAR, Tail.EXACT)))
        assertEquals(out(listOf(k), Tail.EXACT),
            concat(c, edge(Tail.STAR, Tail.STAR, fromMark = star, toMark = mx(u.id))))
        assertEquals(none, concat(c, edge(Tail.STAR, Tail.STAR, fromMark = star, toMark = mx(t.id))))
        assertEquals(out(listOf(k), Tail.STAR, mx(u.id, v.id), exclusion = ex(g, h)),
            concat(input(listOf(f), Tail.STAR, mx(v.id), exclusion = ex(g)),
                edge(Tail.STAR, Tail.STAR, fromMark = star, toMark = mx(u.id), exclusion = ex(h))))
        assertEquals(out(listOf(k), Tail.ANY, mx(u.id, v.id), true),
            concat(input(listOf(f), Tail.ANY, mx(v.id), true),
                edge(Tail.ANY, Tail.ANY, fromMark = star, toMark = mx(u.id))))
        assertEquals(requestT, concat(input(listOf(f), Tail.STAR, star), edge(Tail.STAR, Tail.EXACT)))
        assertEquals(requestT, concat(input(listOf(f), Tail.STAR, mx(u.id)), edge(Tail.STAR, Tail.EXACT)))
        assertEquals(requestT, concat(input(listOf(f), Tail.STAR, star),
            edge(Tail.STAR, Tail.STAR, toMark = mx(t.id))))
        assertEquals(none, concat(input(listOf(f), Tail.STAR, mx(t.id)), edge(Tail.STAR, Tail.EXACT)))
        assertEquals(requestT, concat(input(listOf(f), Tail.ANY, mx(u.id), true), edge(Tail.ANY, Tail.ANY_TAINT)))
    }

    @Test
    fun `geometry precedes mark requests and restricted runs return no request`() {
        val c = input(listOf(f), Tail.STAR, mx(u.id))
        val cases = listOf(
            c to edge(Tail.STAR, Tail.EXACT, fromPath = listOf(g)),
            c.copy(fact = c.fact.copy(base = AccessPathBase.Argument(1))) to edge(Tail.STAR, Tail.EXACT),
            input(listOf(f, g), Tail.STAR, mx(u.id)) to edge(Tail.STAR, Tail.EXACT, exclusion = ex(g)),
            input(listOf(f, g), Tail.STAR, mx(u.id)) to edge(Tail.EXACT, Tail.EXACT),
            input(listOf(f, g), Tail.STAR, mx(u.id)) to
                edge(Tail.ANY_TAINT, Tail.ANY_TAINT, fromExclusion = ex(g)),
            input(listOf(f), Tail.STAR, mx(u.id), exclusion = ex(g)) to
                edge(Tail.STAR, Tail.EXACT, fromPath = listOf(f, g)),
        )
        for ((fact, e) in cases) {
            assertEquals(none, concat(fact, e), "geometry rejects ${fact.fact} with ${e.from}")
            assertEquals(none, concat(fact, e, restricted = true))
        }
        for (fact in listOf(c, input(listOf(f), Tail.ANY, mx(u.id), true))) {
            for (toTail in listOf(Tail.STAR, Tail.ANY, Tail.ANY_TAINT, Tail.EXACT)) {
                val e = edge(Tail.STAR, toTail)
                assertEquals(requestT, concat(fact, e), "run 1 request before $toTail composition")
                assertEquals(none, concat(fact, e, restricted = true), "restricted $toTail")
            }
        }
    }

    @Test
    fun `generic abstract exact values survive transfer without becoming canonical initial facts`() {
        val c = input(listOf(f, g), Tail.EXACT, mx(u.id))
        assertEquals(out(listOf(k, g), Tail.EXACT, mx(u.id)),
            concat(c, edge(Tail.STAR, Tail.STAR, fromMark = star)))
        assertEquals(out(listOf(k, g), Tail.EXACT, mx(u.id), true),
            concat(c, edge(Tail.STAR, Tail.STAR, fromMark = star), edgeDemand = true))
        assertEquals(out(listOf(k), Tail.ANY, mx(u.id), true),
            concat(c, edge(Tail.STAR, Tail.ANY, fromMark = star)))
        assertEquals(requestT, concat(c, edge(Tail.STAR, Tail.EXACT)))
        assertEquals(none, concat(c, edge(Tail.STAR, Tail.EXACT), restricted = true))
    }

    @Test
    fun `static root and class identities request a two-accessor position before the mark gate`() {
        val static = AccessPathBase.ClassStatic
        val readPath = listOf(classC, f, g)
        val read = edge(Tail.STAR, Tail.EXACT, fromPath = readPath, fromBase = static)
        for (q in listOf(emptyList(), listOf(classC))) {
            val c = input(q, Tail.STAR, mx(t.id), base = static)
            assertEquals(EdgeOutcome.PositionRequest(listOf(classC, f)), concat(c, read, staticIdentity = true))
            assertEquals(none, concat(c, read), "mark T is excluded without the static exception")
            assertEquals(none, concat(c, read, restricted = true))
        }
        val root = input(emptyList(), Tail.STAR, star, base = static)
        assertEquals(EdgeOutcome.PositionRequest(listOf(classC)),
            concat(root, edge(Tail.STAR, Tail.EXACT, fromPath = listOf(classC), fromBase = static),
                staticIdentity = true))
        assertEquals(none, concat(root.copy(exclusion = ex(classC)), read, staticIdentity = true))
        val classFact = input(listOf(classC), Tail.STAR, star, exclusion = ex(f), base = static)
        assertEquals(none, concat(classFact, read, staticIdentity = true))
        val fieldFact = input(listOf(f), Tail.STAR, star, base = static)
        assertEquals(requestT, concat(fieldFact,
            edge(Tail.STAR, Tail.EXACT, fromPath = listOf(f, g), fromBase = static), staticIdentity = true))
        assertEquals(requestT, concat(input(emptyList(), Tail.STAR, star),
            edge(Tail.STAR, Tail.EXACT, fromPath = listOf(f, g)), staticIdentity = true))
        assertFails { concat(root, read, staticIdentity = true, restricted = true) }
    }

    @Test
    fun `normalize retains tail and E independently of demand and preserves generic abstract exact`() {
        data class NormalRow(val name: String, val fact: PathFact, val exclusion: ExclusionSet,
                             val demand: Boolean, val expected: Conclusion)
        val path = listOf(k)
        fun raw(tail: Tail, mark: MarkSlot) = PathFact(target, path, tail, mark)
        val rows = listOf(
            NormalRow("normal FLOW", raw(Tail.STAR, mx(u.id)), ex(g), false,
                Conclusion(raw(Tail.STAR, mx(u.id)), ex(g), false)),
            NormalRow("concrete STAR", raw(Tail.STAR, mt), ex(g), false,
                Conclusion(raw(Tail.ANY, mt), ExclusionSet.Empty, true)),
            NormalRow("demand FLOW", raw(Tail.STAR, mx(u.id)), ex(g), true,
                Conclusion(raw(Tail.STAR, mx(u.id)), ex(g), true)),
            NormalRow("demand concrete STAR", raw(Tail.STAR, mt), ex(g), true,
                Conclusion(raw(Tail.ANY, mt), ExclusionSet.Empty, true)),
            NormalRow("normal may", raw(Tail.ANY, mt), ex(g), false,
                Conclusion(raw(Tail.ANY, mt), ExclusionSet.Empty, true)),
            NormalRow("demand may", raw(Tail.ANY, mx(u.id)), ex(g), true,
                Conclusion(raw(Tail.ANY, mx(u.id)), ExclusionSet.Empty, true)),
            NormalRow("normal must", raw(Tail.ANY_TAINT, mt), ex(g), false,
                Conclusion(raw(Tail.ANY_TAINT, mt), ex(g), false)),
            NormalRow("demand must", raw(Tail.ANY_TAINT, mt), ex(g), true,
                Conclusion(raw(Tail.ANY_TAINT, mt), ex(g), true)),
            NormalRow("normal exact", raw(Tail.EXACT, mt), ExclusionSet.Empty, false,
                Conclusion(raw(Tail.EXACT, mt), ExclusionSet.Empty, false)),
            NormalRow("demand exact", raw(Tail.EXACT, mt), ExclusionSet.Empty, true,
                Conclusion(raw(Tail.EXACT, mt), ExclusionSet.Empty, true)),
            NormalRow("generic abstract exact", raw(Tail.EXACT, mx(u.id)), ExclusionSet.Empty, false,
                Conclusion(raw(Tail.EXACT, mx(u.id)), ExclusionSet.Empty, false)),
            NormalRow("generic demand abstract exact", raw(Tail.EXACT, mx(u.id)), ExclusionSet.Empty, true,
                Conclusion(raw(Tail.EXACT, mx(u.id)), ExclusionSet.Empty, true)),
        )
        for (row in rows) assertEquals(row.expected, normalize(row.fact, row.exclusion, row.demand), row.name)
        assertFails { normalize(raw(Tail.ANY_TAINT, star), ExclusionSet.Empty, false) }
        assertFails { normalize(raw(Tail.ANY_TAINT, star), ExclusionSet.Empty, true) }
    }

    @Test
    fun `concat rejects each malformed edge precondition before base or geometry filtering`() {
        val malformed = listOf(
            "S7 excluded premise mark" to edge(Tail.STAR, Tail.STAR, fromMark = mx(t.id)),
            "S7 concrete target abstract premise" to edge(Tail.STAR, Tail.ANY, fromMark = star, toMark = mu),
            "S8 exact abstract premise" to edge(Tail.EXACT, Tail.ANY, fromMark = star, toMark = star),
            "S8 exact premise STAR target" to edge(Tail.EXACT, Tail.STAR),
            "S8 exact target abstract premise" to edge(Tail.STAR, Tail.EXACT, fromMark = star, toMark = star),
            "W8 abstract must target" to edge(Tail.STAR, Tail.ANY_TAINT, toMark = star),
            "W8 abstract must premise" to edge(Tail.ANY_TAINT, Tail.ANY, fromMark = star, toMark = star),
        )
        for ((name, e) in malformed) {
            assertFails(name) { concat(input(listOf(f), Tail.EXACT), e) }
            assertFails("$name, base mismatch") {
                concat(input(listOf(g), Tail.EXACT, base = AccessPathBase.Argument(1)), e)
            }
        }
    }
}

package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Decode finite valid locations independently of the packed component codec. */
class Run1StaticComponentCodecTest {
    private val ap = ApManager(Cancellation())
    private val ops = ApOps(ap)
    private val c = ap.accessors.index(ClassStaticAccessor("C"))
    private val d = ap.accessors.index(ClassStaticAccessor("D"))
    private val f = ap.accessors.index(FieldAccessor("C", "f", "Box"))
    private val g = ap.accessors.index(FieldAccessor("C", "g", "Box"))
    private val h = ap.accessors.index(FieldAccessor("D", "h", "Box"))
    private val suffix = ap.accessors.index(FieldAccessor("Box", "suffix", "Box"))
    private val t = ap.marks.mark("T")
    private val u = ap.marks.mark("U")
    private val cf = Run1Slot.StaticField(c, f)
    private val cg = Run1Slot.StaticField(c, g)
    private val dh = Run1Slot.StaticField(d, h)
    private val universe = setOf(cf, cg, dh)
    private val locations = listOf(listOf(c), listOf(d), listOf(c, f), listOf(c, f, suffix),
        listOf(c, g), listOf(c, g, suffix), listOf(d, h), listOf(d, h, suffix))
    private data class Cell(val path: List<Int>, val mark: TaintMark, val layer: Layer)

    private fun decode(facts: List<Facts>): Set<Cell> = buildSet {
        for (fact in facts) for (leaf in ops.leaves(fact)) for (location in locations) {
            val path = leaf.fact.path
            if (location.take(path.size) != path) continue
            val rest = location.drop(path.size)
            if (leaf.fact.tail == Tail.EXACT && rest.isNotEmpty()) continue
            if (rest.isNotEmpty() && !leaf.exclusion.admits(rest.first())) continue
            for (mark in listOf(t, u)) {
                val holds = when (val declared = leaf.fact.mark) {
                    is MarkSlot.Concrete -> declared.mark == mark
                    is MarkSlot.Star -> mark !in declared.excluded
                }
                if (holds) add(Cell(location, mark, fact.layer))
            }
        }
    }
    private fun expected(input: Facts, footprint: Run1StaticFootprint): Set<Cell> = decode(listOf(input)).filterTo(linkedSetOf()) {
        if (it.path.size == 1) !footprint.ownsPoint(it.path[0])
        else !footprint.owns(it.path[0], it.path[1])
    }
    private fun flow(path: List<Int> = emptyList(), exclusion: ExclusionSet = ExclusionSet.Empty): FlowTree =
        ap.factsOf(PathFact(AccessPathBase.ClassStatic, path, Tail.STAR, MarkSlot.STAR), exclusion, Layer.NORMAL) as FlowTree

    @Test
    fun `normal root and class frames equal the scalar complement including class point ownership`() {
        for (points in listOf(emptySet(), setOf(c), setOf(d))) {
            val footprint = Run1StaticFootprint(setOf(cf), fieldUniverse = universe, classPoints = points,
                classUniverse = setOf(c, d))
            for (input in listOf(flow(), flow(listOf(c)), flow(listOf(c, g), ExclusionSet.of(suffix)))) {
                assertEquals(expected(input, footprint), decode(staticUntouchedInputs(ap, input, footprint)))
            }
        }
    }

    @Test
    fun `normal packed marks and mixed ancestor tip exclusions retain their distinct headers`() {
        val leaf = ap.taintAlg.leafNode(ap.taintLeaves(MarkSet.EMPTY, ap.markSetOf(t)))
        val explicit = ap.taintAlg.prepend(ap, ap.path(listOf(c, g)),
            ap.taintAlg.leafNode(ap.taintLeaves(MarkSet.EMPTY, ap.markSetOf(u))))
        val root = ap.taintAlg.mergeAdd(leaf, explicit)
        val input = ap.taintTree(AccessPathBase.ClassStatic, Layer.NORMAL, root, ExclusionSet.of(suffix))
        val footprint = Run1StaticFootprint(setOf(cf), fieldUniverse = universe, classUniverse = setOf(c, d))
        assertEquals(expected(input, footprint), decode(staticUntouchedInputs(ap, input, footprint)))
    }

    @Test
    fun `a whole static footprint has no root frame and an empty footprint preserves the input`() {
        val input = flow()
        val empty = Run1StaticFootprint(emptySet(), fieldUniverse = universe, classUniverse = setOf(c, d))
        assertEquals(decode(listOf(input)), decode(staticUntouchedInputs(ap, input, empty)))
        val all = Run1StaticFootprint(emptySet(), allStatic = true, fieldUniverse = universe,
            classUniverse = setOf(c, d))
        assertTrue(staticUntouchedInputs(ap, input, all).isEmpty())
    }

    @Test
    fun `legal universe is snapshotted once and the compact complement keeps full scalar payload`() {
        val source = universe.toMutableSet()
        var enumerations = 0
        var readable = true
        val suppliedUniverse = object : AbstractSet<Run1Slot.StaticField>() {
            override val size: Int get() = source.size
            override fun iterator(): Iterator<Run1Slot.StaticField> {
                check(readable) { "A frozen footprint must not retain its mutable supplier" }
                enumerations++
                return source.iterator()
            }
        }
        val footprint = Run1StaticFootprint(setOf(cf), fieldUniverse = suppliedUniverse,
            classUniverse = setOf(c, d))
        assertEquals(1, enumerations)
        source.clear()
        readable = false
        assertEquals(universe, footprint.fieldUniverse)
        val result = staticUntouchedInputs(ap, flow(), footprint)
        assertEquals(expected(flow(), footprint), decode(result))
        assertTrue(result.size <= 3)
        assertEquals(1, enumerations, "Later reads borrow the owned domain, not the supplier")
    }
}

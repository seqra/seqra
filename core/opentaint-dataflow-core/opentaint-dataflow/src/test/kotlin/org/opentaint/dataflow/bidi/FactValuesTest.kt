package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoGroupAccessor
import org.opentaint.dataflow.ap.ifds.ValueAccessor
import org.opentaint.dataflow.bidi.ap.AccessorTable
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.InitialAp
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.ap.MarkSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.MarkTable
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.bidi.ap.isAny
import org.opentaint.dataflow.bidi.ap.isClass
import org.opentaint.dataflow.bidi.ap.isCounted
import org.opentaint.dataflow.bidi.ap.or
import org.opentaint.dataflow.util.Cancellation
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FactValuesTest {
    @Test
    fun `accessor table keeps values and counts fields and elements but not classes`() {
        val table = AccessorTable()
        val field = FieldAccessor("Box", "f", "Box")
        val sameNameOtherOwner = field.copy(className = "OtherBox")
        val sameNameOtherType = field.copy(fieldType = "String")
        val classAccessor = ClassStaticAccessor("Box")
        val accessors = listOf(field, sameNameOtherOwner, sameNameOtherType, ElementAccessor, classAccessor)
        for (accessor in accessors) {
            val index = table.index(accessor)
            assertEquals(accessor, table.accessor(index))
            assertEquals(index, table.index(accessor))
            assertEquals(accessor is FieldAccessor || accessor == ElementAccessor, index.isCounted())
            assertEquals(accessor is ClassStaticAccessor, index.isClass())
        }
        assertEquals(accessors.size, accessors.map(table::index).toSet().size)
        assertEquals(table.index(field), table.index(field.copy()))
    }

    @Test
    fun `accessor table rejects old any tail mark value and type-info accessors`() {
        val table = AccessorTable()
        for (accessor in listOf(AnyAccessor, FinalAccessor, TaintMarkAccessor("T"),
            ValueAccessor, TypeInfoAccessor("Box"), TypeInfoGroupAccessor)) {
            assertFails { table.index(accessor) }
        }
    }

    @Test
    fun `mark names have canonical values and zero has a reserved id`() {
        val table = MarkTable(org.opentaint.dataflow.bidi.fixtures.COMMON_FIXTURE_DECLARATIONS)
        val t = table.mark("T")
        val u = table.mark("U")
        assertEquals(t, table.mark("T"))
        assertEquals("T", table.name(t))
        assertEquals("U", table.name(u))
        assertNotEquals(t, u)
        assertNotEquals(TaintMark.ZERO, t)
        assertNotEquals(TaintMark.ZERO, u)
        assertEquals(0, TaintMark.ZERO.id)
    }

    @Test
    fun `mark sets are sorted distinct values with canonical empty identity`() {
        val a = MarkSet(intArrayOf(3, 1, 3, 2))
        val b = MarkSet(intArrayOf(1, 2, 3))
        assertContentEquals(intArrayOf(1, 2, 3), a.ids)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(TaintMark(2) in a)
        assertFalse(TaintMark(4) in a)
        assertTrue(MarkSet.EMPTY.isEmpty)
        val manager = testApManager(Cancellation())
        assertSame(MarkSet.EMPTY, manager.intern(MarkSet(intArrayOf())))
        assertSame(manager.intern(a), manager.intern(b))
    }

    @Test
    fun `mark set operations retain exact union intersection difference and subset`() {
        val a = MarkSet(intArrayOf(1, 3, 5))
        val b = MarkSet(intArrayOf(3, 4))
        assertEquals(MarkSet(intArrayOf(1, 3, 4, 5)), a + b)
        assertEquals(MarkSet(intArrayOf(1, 3, 4, 5)), a + TaintMark(4))
        assertEquals(a, a + TaintMark(3))
        assertEquals(MarkSet(intArrayOf(3)), a.intersect(b))
        assertEquals(MarkSet(intArrayOf(1, 5)), a - b)
        assertEquals(MarkSet.EMPTY, a - a)
        assertTrue(a.isSubsetOf(a + b))
        assertFalse(a.isSubsetOf(b))
        assertTrue(MarkSet.EMPTY.isSubsetOf(a))
    }

    @Test
    fun `mark set owns input and returns array snapshots`() {
        val input = intArrayOf(1, 2, 3)
        val set = MarkSet(input)
        val hash = set.hashCode()
        input[0] = 99
        set.ids[0] = 88
        assertContentEquals(intArrayOf(1, 2, 3), set.ids)
        assertEquals(hash, set.hashCode())
        assertEquals(MarkSet(intArrayOf(1, 2, 3)), set)
    }

    @Test
    fun `field exclusions use sorted values and admit empty suffixes`() {
        val a = ExclusionSet.of(intArrayOf(3, 1, 3))
        val b = ExclusionSet.of(intArrayOf(2, 3))
        assertIs<ExclusionSet.Concrete>(a)
        assertContentEquals(intArrayOf(1, 3), a.ids)
        assertSame(ExclusionSet.Empty, ExclusionSet.of(intArrayOf()))
        assertFalse(a.admits(1))
        assertTrue(a.admits(2))
        assertTrue(a.admits(emptyList()))
        assertFalse(a.admits(listOf(1, 2)))
        assertTrue(a.admits(listOf(2, 1)))
        assertEquals(ExclusionSet.of(intArrayOf(1, 2, 3)), a.union(b))
        assertEquals(ExclusionSet.of(3), a.intersect(b))
        assertSame(ExclusionSet.Empty, a.intersect(ExclusionSet.Empty))
        assertSame(ExclusionSet.Empty, ExclusionSet.of(1).intersect(ExclusionSet.of(2)))
        assertTrue(a.isSubsetOf(a.union(b)))
        assertFalse(a.isSubsetOf(b))
        assertTrue(ExclusionSet.Empty.isSubsetOf(a))
    }

    @Test
    fun `field exclusions own input and return array snapshots before interning`() {
        val manager = testApManager(Cancellation())
        val input = intArrayOf(3, 1)
        val exclusion = assertIs<ExclusionSet.Concrete>(ExclusionSet.of(input))
        val interned = manager.intern(exclusion)
        val hash = exclusion.hashCode()
        input[0] = 99
        exclusion.ids[0] = 88
        assertContentEquals(intArrayOf(1, 3), exclusion.ids)
        assertEquals(hash, exclusion.hashCode())
        assertSame(interned, manager.intern(ExclusionSet.of(intArrayOf(1, 3))))
    }

    @Test
    fun `path cells preserve order share suffixes and cache exact counted depth`() {
        val manager = testApManager(Cancellation())
        val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
        val e = manager.accessors.index(ElementAccessor)
        val c = manager.accessors.index(ClassStaticAccessor("Box"))
        val input = intArrayOf(c, f, e, c)
        val path = requireNotNull(manager.path(input))
        assertEquals(4, path.size)
        assertEquals(2, path.counted)
        assertContentEquals(input, path.toIntArray())
        assertEquals(input.asList(), path.toList())
        assertSame(path, manager.path(input.asList()))
        assertSame(path.next, manager.path(input.drop(1)))
        input[1] = c
        path.toIntArray()[1] = c
        assertEquals(listOf(c, f, e, c), path.toList())
        assertEquals(null, manager.path(emptyList()))
        assertEquals(null, manager.path(intArrayOf()))
    }

    @Test
    fun `path construction and conversion support 32768 accessors without recursion limits`() {
        val manager = testApManager(Cancellation())
        val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
        val c = manager.accessors.index(ClassStaticAccessor("Box"))
        val input = IntArray(32768) { if (it % 4 == 0) c else f }
        val path = requireNotNull(manager.path(input))
        assertEquals(input.size, path.size)
        assertEquals(24576, path.counted)
        assertContentEquals(input, path.toIntArray())
        assertSame(path, manager.path(input))
    }

    @Test
    fun `tail and layer values distinguish may must and monotone demand`() {
        assertTrue(Tail.ANY.isAny)
        assertTrue(Tail.ANY_TAINT.isAny)
        assertFalse(Tail.EXACT.isAny)
        assertFalse(Tail.STAR.isAny)
        assertEquals(Layer.NORMAL, Layer.NORMAL or false)
        assertEquals(Layer.DEMAND, Layer.NORMAL or true)
        assertEquals(Layer.DEMAND, Layer.DEMAND or false)
        assertEquals(Layer.DEMAND, Layer.DEMAND or true)
    }

    @Test
    fun `concurrent accessor and mark publication keeps canonical manager keys`() {
        val manager = testApManager(Cancellation())
        val workers = 8
        val barrier = CyclicBarrier(workers)
        val executor = Executors.newFixedThreadPool(workers)
        try {
            val futures = List(workers) {
                executor.submit(Callable<List<InitialAp>> {
                    barrier.await(10, TimeUnit.SECONDS)
                    List(128) { i ->
                        val field = FieldAccessor("Box", "field-$i", "Box")
                        val accessor = manager.accessors.index(field)
                        check(manager.accessors.accessor(accessor) == field)
                        val mark = manager.marks.mark("mark-$i")
                        check(manager.marks.name(mark) == "mark-$i")
                        manager.initial(AccessPathBase.Argument(i), manager.path(intArrayOf(accessor)), Tail.EXACT,
                            ExclusionSet.Empty, MarkSlot.Concrete(mark))
                    }
                })
            }
            val results = futures.map { it.get(20, TimeUnit.SECONDS) }
            val first = results.first()
            for (result in results.drop(1)) for (index in first.indices) {
                assertSame(first[index], result[index])
                assertSame(first[index].path, result[index].path)
                assertEquals(first[index].mark, result[index].mark)
            }
            assertEquals(128, first.map { it.id }.toSet().size)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
}

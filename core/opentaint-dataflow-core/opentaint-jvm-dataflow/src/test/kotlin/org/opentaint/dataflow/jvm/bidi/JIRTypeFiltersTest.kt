package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactTypeChecker
import org.opentaint.dataflow.jvm.bidi.interp.JIRTypeFilters
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import org.opentaint.ir.api.jvm.JIRType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JIRTypeFiltersTest : BasicTestUtils() {
    private val manager = ApManager(Cancellation())
    private val ops = ApOps(manager)
    private val checker by lazy { JIRFactTypeChecker(cp) }
    private val filters by lazy { JIRTypeFilters(checker, manager) }
    private val ordinary = manager.marks.mark("T")
    private val tracked = manager.marks.mark("T%%primitive%%")
    private val x = AccessPathBase.Argument(0)
    private val sample = "sample.bidi.BidiStatementSample"
    private val sampleField = FieldAccessor(sample, "f", "java.lang.Object")
    private val stringField = FieldAccessor("java.lang.String", "value", "byte[]")
    private val boxedField = FieldAccessor("java.lang.Integer", "value", "int")
    private val cls = ClassStaticAccessor(sample)
    private val excluded = manager.accessors.index(FieldAccessor(sample, "excluded", "java.lang.Object"))
    private val element = manager.accessors.index(ElementAccessor)

    private fun type(name: String): JIRType = cp.findTypeOrNull(name) ?: error("Missing test type $name")
    private fun filter(name: String): TypeFilter = requireNotNull(filters.of(type(name)))
    private fun indexes(path: List<Accessor>) = path.map(manager.accessors::index)
    private fun facts(path: List<Accessor> = emptyList(), tail: Tail = Tail.EXACT,
                      mark: MarkSlot = MarkSlot.Concrete(ordinary), layer: Layer = Layer.NORMAL,
                      exclusion: ExclusionSet = ExclusionSet.Empty) =
        manager.factsOf(PathFact(x, indexes(path), tail, mark), exclusion, layer)
    private fun admitted(name: String, path: List<Accessor>, mark: TaintMark = tracked): Boolean =
        ops.filter(facts(path, mark = MarkSlot.Concrete(mark)), filter(name)) != null

    @Test
    fun `nullable type filters cache each type by identity across callers and concurrent construction`(): Unit {
        assertNull(filters.of(null))
        for (name in listOf("int", "java.lang.Integer", "byte[][]", sample, "java.lang.Object")) {
            val first = filter(name)
            repeat(5) { assertSame(first, filters.of(type(name))) }
        }
        assertNotSame(filter("int"), filter("java.lang.Integer"))
        assertNotSame(filter("byte[]"), filter("byte[][]"))
        val concurrent = JIRTypeFilters(checker, manager)
        val array = type("java.lang.Byte[][][]")
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val values = List(24) { pool.submit<TypeFilter> {
                start.await()
                requireNotNull(concurrent.of(array))
            } }
            start.countDown()
            val first = values.first().get(30, TimeUnit.SECONDS)
            for (value in values) assertSame(first, value.get(30, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `local path checks follow first field array element object and static accessor rows`(): Unit {
        data class Row(val type: String, val path: List<Accessor>, val accepts: Boolean)
        val rows = listOf(
            Row("int", emptyList(), true), Row("int", listOf(ElementAccessor), false),
            Row("int", listOf(sampleField), false), Row("int", listOf(cls, sampleField), true),
            Row("java.lang.String", listOf(stringField), true),
            Row("java.lang.String", listOf(sampleField), false),
            Row("java.lang.String", listOf(ElementAccessor), false),
            Row("java.lang.String", listOf(stringField, ElementAccessor, sampleField), true),
            Row("java.lang.Object", listOf(sampleField), true),
            Row("java.lang.Object", listOf(ElementAccessor, ElementAccessor, sampleField), true),
            Row("java.lang.Cloneable", listOf(ElementAccessor), false),
            Row("java.io.Serializable", listOf(ElementAccessor), false),
            Row("int[]", listOf(ElementAccessor), true),
            Row("int[]", listOf(ElementAccessor, ElementAccessor), false),
            Row("int[]", listOf(ElementAccessor, sampleField), false),
            Row("int[]", listOf(ElementAccessor, cls), true),
            Row("java.lang.String[][]", listOf(ElementAccessor, ElementAccessor), true),
            Row("java.lang.String[][]", listOf(ElementAccessor, ElementAccessor, ElementAccessor), false),
            Row("java.lang.String[][]", listOf(ElementAccessor, ElementAccessor, stringField), true),
            Row("java.lang.String[][]", listOf(ElementAccessor, ElementAccessor, sampleField), false),
        )
        for (row in rows) assertEquals(row.accepts, admitted(row.type, row.path), "${row.type}/${row.path}")
        assertEquals(FactTypeChecker.FilterResult.Accept, checker.localFilter(type(sample)).check(sampleField))
        assertEquals(FactTypeChecker.FilterResult.Reject, checker.localFilter(type("int")).check(ElementAccessor))
        val next = assertIs<FactTypeChecker.FilterResult.FilterNext>(checker.localFilter(type("int[]")).check(ElementAccessor))
        assertEquals(checker.localFilter(type("int")), next.filter)
    }

    @Test
    fun `primitive tracking recognizes only the exact name suffix`(): Unit {
        for (name in listOf("T%%primitive%%", "%%primitive%%", "many%%primitive%%%%primitive%%"))
            assertTrue(filters.isPrimitiveTracking(manager.marks.mark(name)), name)
        for (name in listOf("T", "T%%primitive%%suffix", "T%%PRIMITIVE%%", "primitive"))
            assertFalse(filters.isPrimitiveTracking(manager.marks.mark(name)), name)
        assertFalse(filters.isPrimitiveTracking(TaintMark.ZERO))
    }

    @Test
    fun `all primitive and boxed roots keep primitive tracking marks and drop ordinary concrete marks`(): Unit {
        val names = listOf("boolean", "byte", "short", "int", "long", "float", "double", "char",
            "java.lang.Boolean", "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
            "java.lang.Float", "java.lang.Double", "java.lang.Character")
        for (name in names) {
            val filter = filter(name)
            val policy = requireNotNull(filter.markPolicy)
            assertFalse(policy.keeps(ordinary, 0), "An ordinary mark on a primitive or boxed root is removed: $name")
            assertTrue(policy.keeps(tracked, 0), name)
            assertTrue(policy.keeps(ordinary, 1), "An unknown level keeps every mark: $name")
            assertNull(ops.filter(facts(), filter), name)
            val enabled = facts(mark = MarkSlot.Concrete(tracked))
            assertSame(enabled, ops.filter(enabled, filter), name)
        }
        for (name in listOf("java.lang.Object", "java.lang.String", sample, "java.lang.Object[][]", "java.lang.String[][]")) {
            assertNull(filter(name).markPolicy, name)
            val ordinaryFact = facts()
            assertSame(ordinaryFact, ops.filter(ordinaryFact, filter(name)), name)
        }
    }

    @Test
    fun `primitive and boxed array policies read every element level and keep unknown levels`(): Unit {
        for (leaf in listOf("byte", "java.lang.Byte", "java.lang.Character")) for (dimensions in 1..3) {
            val name = leaf + "[]".repeat(dimensions)
            val filter = filter(name)
            val policy = requireNotNull(filter.markPolicy)
            for (level in 0..dimensions + 2) {
                assertEquals(level != dimensions, policy.keeps(ordinary, level), "$name level=$level")
                assertTrue(policy.keeps(tracked, level), "$name level=$level")
                val path = List(level) { ElementAccessor }
                assertEquals(level < dimensions, admitted(name, path, ordinary), "$name ordinary level=$level")
                assertEquals(level <= dimensions, admitted(name, path, tracked), "$name tracked level=$level")
            }
        }
    }

    @Test
    fun `field and class accessors stop the primitive policy while field value types do not propagate into a fact`(): Unit {
        val integer = filter("java.lang.Integer")
        val underField = facts(listOf(boxedField))
        assertSame(underField, ops.filter(underField, integer))
        val fieldThenElement = facts(listOf(boxedField, ElementAccessor))
        assertSame(fieldThenElement, ops.filter(fieldThenElement, integer), "A first field accepts the whole suffix")
        val classFact = facts(listOf(cls))
        assertSame(classFact, ops.filter(classFact, filter("int")))
        val afterElements = facts(listOf(ElementAccessor, ElementAccessor, cls, sampleField))
        assertSame(afterElements, ops.filter(afterElements, filter("int[][]")))
        val arrayField = facts(listOf(ElementAccessor, boxedField))
        assertSame(arrayField, ops.filter(arrayField, filter("java.lang.Integer[]")))
        assertNull(ops.filter(facts(listOf(ElementAccessor, ElementAccessor, sampleField)), filter("int[][]")),
            "A path rejection happens before any off-chain mark policy")
    }

    @Test
    fun `filters retain all admitted tails exclusions layers and abstract mark exclusions`(): Unit {
        for (path in listOf(emptyList(), listOf(ElementAccessor))) {
            val filter = filter(if (path.isEmpty()) "int" else "int[]")
            for (tail in Tail.entries) for (layer in Layer.entries) {
                if (tail == Tail.STAR && layer == Layer.DEMAND) continue
                if (tail == Tail.ANY_TAINT && layer == Layer.DEMAND) continue
                if (tail == Tail.ANY && layer == Layer.NORMAL) continue
                val mark = if (tail == Tail.STAR) MarkSlot.Star(MarkSet(intArrayOf(ordinary.id))) else MarkSlot.Concrete(tracked)
                val exclusion = if (tail == Tail.STAR || tail == Tail.ANY_TAINT) ExclusionSet.of(excluded) else ExclusionSet.Empty
                val input = facts(path, tail, mark, layer, exclusion)
                assertSame(input, ops.filter(input, filter), "$path/$tail/$layer")
                assertEquals(setOf(Pattern(PathFact(x, indexes(path), tail, mark), exclusion)), ops.leaves(input).toSet())
            }
            val abstractMay = facts(path, Tail.ANY, MarkSlot.Star(MarkSet(intArrayOf(tracked.id))), Layer.DEMAND)
            assertSame(abstractMay, ops.filter(abstractMay, filter), "The policy never removes abstract marks")
        }
    }

    @Test
    fun `value type follows fields elements and static prefixes including an unknown root`(): Unit {
        val next = manager.accessors.index(FieldAccessor(sample, "next", "java.lang.String"))
        val bytes = manager.accessors.index(stringField)
        val static = manager.accessors.index(cls)
        val array = manager.accessors.index(FieldAccessor(sample, "sField", "int[][]"))
        val integer = manager.accessors.index(boxedField)
        assertEquals(type(sample), filters.valueType(type(sample), emptyList()))
        assertNull(filters.valueType(null, emptyList()))
        assertEquals(type("java.lang.String"), filters.valueType(type(sample), listOf(next)))
        assertEquals(type("byte[]"), filters.valueType(type(sample), listOf(next, bytes)))
        assertEquals(type("byte"), filters.valueType(type(sample), listOf(next, bytes, element)))
        assertEquals(type("int"), filters.valueType(null, listOf(static, array, element, element)))
        assertNull(filters.valueType(type("java.lang.Object"), listOf(element)))
        assertNull(filters.valueType(type(sample), listOf(static)))
        assertNull(filters.valueType(type("byte[]"), listOf(element, element)))
        assertEquals(type("int"), filters.valueType(null, listOf(integer)), "A field supplies its own declared value type")
        assertEquals(type("int"), filters.valueType(type("java.lang.Object"), listOf(element, integer)),
            "A field value type can be decoded after an unknown element level")
        assertEquals(type("byte[]"), checker.fieldAccessorType(stringField))
        assertEquals<JIRType?>(type("java.lang.String"), checker.fieldClassType(stringField))
        assertNull(checker.fieldClassType(FieldAccessor("int", "unknown", "int")))
    }

    @Test
    fun `field filters combine only root and first declaring type and use the final temporary value type`(): Unit {
        val first = FieldAccessor(sample, "next", "java.lang.String")
        val path = indexes(listOf(first, stringField, ElementAccessor))
        val (base, value) = filters.forField(type(sample), path)
        val baseFilter = requireNotNull(base)
        assertSame(filter("byte"), value)
        assertSame(filter(sample), filters.forField(null, listOf(manager.accessors.index(first))).first)
        assertSame(filter(sample), filters.forField(type(sample), emptyList()).first)
        assertSame(filter(sample), filters.forField(type(sample), emptyList()).second)
        assertEquals(null to null, filters.forField(null, emptyList()))
        assertSame(filter("byte[][]"), filters.forField(type("byte[][]"), listOf(element)).first)
        assertSame(filter("byte[]"), filters.forField(type("byte[][]"), listOf(element)).second)
        assertEquals(FactTypeChecker.FilterResult.Accept, baseFilter.may.check(first))
        assertEquals(FactTypeChecker.FilterResult.Reject, baseFilter.may.check(stringField))
        val objectRoot = requireNotNull(filters.forField(type("java.lang.Object"), path).first)
        assertEquals(FactTypeChecker.FilterResult.Accept, filter("java.lang.Object").may.check(stringField))
        assertEquals(FactTypeChecker.FilterResult.Reject, objectRoot.may.check(stringField),
            "The first field declaring type is combined with a broader root type")
        val nested = facts(listOf(first, stringField, ElementAccessor))
        assertSame(nested, ops.filter(nested, baseFilter), "Deeper declaring classes are not root constraints")
        assertNull(ops.filter(facts(), requireNotNull(value)), "The temporary uses the final primitive mark policy")
        val enabled = facts(mark = MarkSlot.Concrete(tracked))
        assertSame(enabled, ops.filter(enabled, requireNotNull(value)))
        val staticPath = indexes(listOf(cls, FieldAccessor(sample, "sField", "java.lang.Integer")))
        val staticFilters = filters.forField(null, staticPath)
        assertNull(staticFilters.first)
        assertSame(filter("java.lang.Integer"), staticFilters.second)
    }

    @Test
    fun `filter conjunction meets path checks and element mark policies without changing retained annotations`(): Unit {
        val combined = filter("byte[][]").and(filter("java.lang.Byte[]"))
        assertSame(FactTypeChecker.FilterResult.Reject,
            assertIs<FactTypeChecker.FilterResult.FilterNext>(combined.may.check(ElementAccessor)).filter.check(ElementAccessor))
        for (path in listOf(emptyList(), listOf(ElementAccessor), listOf(ElementAccessor, ElementAccessor)))
            for (mark in listOf(ordinary, tracked)) {
                val input = facts(path, Tail.ANY_TAINT, MarkSlot.Concrete(mark), exclusion = ExclusionSet.of(excluded))
                val expected = path.isEmpty() || (path.size == 1 && mark == tracked)
                assertEquals(expected, ops.filter(input, combined) != null, "$path/$mark")
                if (expected) assertSame(input, ops.filter(input, combined))
            }
        val objectAndString = filter("java.lang.Object").and(filter("java.lang.String"))
        assertFalse(admitted(sample, listOf(stringField)))
        assertNull(ops.filter(facts(listOf(sampleField)), objectAndString))
        val valid = facts(listOf(stringField), mark = MarkSlot.Concrete(ordinary))
        assertSame(valid, ops.filter(valid, objectAndString))
    }

    @Test
    fun `the public local filter retains the existing prescan mark and array checks`(): Unit {
        val oldManager = TreeApManager(object : AnyAccessorUnrollStrategy {
            override fun unrollAccessor(accessor: Accessor) = false
        }, RefManager(), Cancellation())
        fun oldFact(name: String, element: Boolean) = oldManager.createFinalAp(x,
            org.opentaint.dataflow.ap.ifds.ExclusionSet.Empty).prependAccessor(TaintMarkAccessor(name))
            .let { if (element) it.prependAccessor(ElementAccessor) else it }
        for (name in listOf("T", "T%%primitive%%")) for (element in listOf(false, true)) {
            val type = type(if (element) "int[]" else "int")
            val input = oldFact(name, element)
            val old = checker.filterFactByLocalType(type, input)
            val exposed = input.filterFact(checker.localFilter(type))
            assertEquals(old, exposed)
            assertEquals(name.endsWith("%%primitive%%"), old != null)
            assertSame(input, checker.filterFactByLocalType(null, input))
        }
        assertEquals(FactTypeChecker.FilterResult.Reject, checker.localFilter(type("int")).check(TaintMarkAccessor("T")))
        assertEquals(FactTypeChecker.FilterResult.Accept,
            checker.localFilter(type("int")).check(TaintMarkAccessor("T%%primitive%%")))
    }
}

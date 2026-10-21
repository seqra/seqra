package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.ZERO_PATTERN
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.fixtures.TestMark
import org.opentaint.dataflow.bidi.fixtures.RawRun1Representative
import org.opentaint.dataflow.bidi.fixtures.testDeclarations
import kotlin.test.*

class PremiseCombinationTest {
    private val declarations = testDeclarations(
        TestMark("alpha", MarkGroupKey.Rule("AA")),
        TestMark("omega", MarkGroupKey.Rule("AA")),
        TestMark("other", MarkGroupKey.Rule("ZZ")),
    )
    private val fixture = StoreFixtures(markDeclarations = declarations)
    private val m = fixture.manager

    private fun initial(base: AccessPathBase, path: List<Int> = emptyList(), tail: Tail = Tail.EXACT,
                        mark: TaintMark = m.marks.full("alpha")): InitialAp =
        m.initial(base, m.path(path), tail, ExclusionSet.Empty, MarkSlot.Concrete(mark))

    private fun static(name: String, field: String? = null, type: String = "Box",
                       mark: TaintMark = m.marks.full("alpha"), tail: Tail = Tail.EXACT): InitialAp {
        val path = listOf(m.accessors.index(ClassStaticAccessor(name))) +
            if (field == null) emptyList() else listOf(m.accessors.index(FieldAccessor(name, field, type)))
        return initial(STATIC, path, tail, mark)
    }

    @Test fun `exact policy keeps canonical unions and the manager remains exact`() {
        val a = initial(AccessPathBase.Argument(0))
        val b = initial(AccessPathBase.Argument(1))
        val exact = ExactPremises(m)
        val expected = m.union(a, b)
        assertIs<PremiseSet>(expected)
        assertSame(expected, exact.combine(listOf(b, m.zero, a, b)))
        assertSame(expected, m.union(b, a))
        assertSame(m.zero, exact.combine(listOf(m.zero, m.zero)))
    }

    @Test fun `run1 preference uses original bases before allocation order`() {
        val classPoint = static("Box")
        val field = static("Box", "value")
        val other = initial(AccessPathBase.Return)
        val receiver = initial(AccessPathBase.This)
        val argument = initial(AccessPathBase.Argument(7))
        val ranked = listOf(argument, receiver, other, field, classPoint)
        val policy = Run1Premises(m)
        for (i in ranked.indices) for (j in i until ranked.size) {
            assertSame(ranked[i], policy.combine(listOf(ranked[j], ranked[i])))
            assertSame(ranked[i], policy.combine(listOf(ranked[i], ranked[j])))
        }
        assertTrue(classPoint.id < argument.id, "The best origin was allocated last")
    }

    @Test fun `numeric base keys and full accessor descriptors break ties`() {
        val policy = Run1Premises(m)
        val arg10 = initial(AccessPathBase.Argument(10))
        val arg2 = initial(AccessPathBase.Argument(2))
        assertSame(arg2, policy.combine(listOf(arg10, arg2)))
        val local10 = initial(AccessPathBase.LocalVar(10))
        val local2 = initial(AccessPathBase.LocalVar(2))
        assertSame(local2, policy.combine(listOf(local10, local2)))
        val z = static("z.Box", "same", "Z")
        val a = static("a.Box", "same", "Z")
        assertSame(a, policy.combine(listOf(z, a)))
        val typeA = static("a.Box", "same", "A")
        assertSame(typeA, policy.combine(listOf(a, typeA)))
        val fieldA = static("a.Box", "aaa", "Z")
        assertSame(fieldA, policy.combine(listOf(a, fieldA)))
    }

    @Test fun `tail and semantic full and group names break ties without atom IDs`() {
        val policy = Run1Premises(m)
        val omega = m.marks.full("omega")
        val alpha = m.marks.full("alpha")
        assertTrue(omega.id < alpha.id)
        val broad = initial(AccessPathBase.Argument(0), tail = Tail.STAR, mark = alpha)
        val exact = initial(AccessPathBase.Argument(0), mark = alpha)
        assertSame(exact, policy.combine(listOf(broad, exact)))
        val fullOmega = initial(AccessPathBase.Argument(0), mark = omega)
        assertSame(exact, policy.combine(listOf(fullOmega, exact)))
        val groupZ = m.marks.group(m.marks.groupOf(m.marks.full("other")))
        val groupA = m.marks.group(m.marks.groupOf(alpha))
        assertTrue(groupZ.id < groupA.id)
        val z = initial(AccessPathBase.Argument(1), mark = groupZ)
        val a = initial(AccessPathBase.Argument(1), mark = groupA)
        assertSame(a, policy.combine(listOf(z, a)))
    }

    @Test fun `zero is neutral and empty or foreign or ND input is rejected`() {
        val policy = Run1Premises(m)
        val a = initial(AccessPathBase.Argument(0))
        val b = initial(AccessPathBase.Argument(1))
        assertSame(m.zero, policy.combine(listOf(m.zero, m.zero)))
        assertSame(a, policy.combine(listOf(m.zero, a, m.zero, a)))
        assertFails { policy.combine(emptyList()) }
        assertFails { ExactPremises(m).combine(emptyList()) }
        val foreign = StoreFixtures(markDeclarations = declarations).manager
        assertFails { policy.combine(listOf(m.zero, foreign.zero)) }
        assertFails { policy.combine(listOf(m.union(a, b))) }
    }

    @Test fun `permutations duplicates and nested selections keep membership without interning ND`() {
        val policy = Run1Premises(m)
        val a = initial(AccessPathBase.Argument(0))
        val b = initial(AccessPathBase.Argument(1))
        val c = initial(AccessPathBase.This)
        val values = listOf(m.zero, a, b, c)
        for (x in values) for (y in values) for (z in values) {
            val inputs = listOf(x, y, z)
            val expected = when {
                a in inputs -> a
                b in inputs -> b
                c in inputs -> c
                else -> m.zero
            }
            assertSame(expected, policy.combine(inputs))
            assertSame(expected, policy.combine(inputs.reversed() + inputs))
            assertSame(expected, policy.combine(listOf(policy.combine(listOf(x, y)), z)))
            assertTrue(expected in inputs)
        }
        val sets = m.javaClass.getDeclaredField("premiseSets").apply { isAccessible = true }.get(m) as Map<*, *>
        assertTrue(sets.isEmpty(), "Run-1 selection must never union then select")
    }

    @Test fun `logical selection survives reversed initial accessor and mark allocation`() {
        fun selected(reverse: Boolean): List<String> {
            val manager = StoreFixtures(markDeclarations = declarations).manager
            val names = listOf("alpha", "omega", "other").let { if (reverse) it.reversed() else it }
            names.forEach { manager.marks.full(it) }
            val descriptors = listOf(FieldAccessor("a.Box", "x", "Box"), FieldAccessor("z.Box", "x", "Box"))
            (if (reverse) descriptors.reversed() else descriptors).forEach(manager.accessors::index)
            val cls = manager.accessors.index(ClassStaticAccessor("Box"))
            val origins = (if (reverse) descriptors.reversed() else descriptors).map {
                manager.initial(STATIC, manager.path(listOf(cls, manager.accessors.index(it))), Tail.EXACT,
                    ExclusionSet.Empty, MarkSlot.Concrete(manager.marks.full("alpha")))
            }
            val chosen = Run1Premises(manager).combine(origins)
            return chosen.pathArray.map { when (val accessor = manager.accessors.accessor(it)) {
                is ClassStaticAccessor -> "class:${accessor.typeName}"
                is FieldAccessor -> "field:${accessor.className}:${accessor.fieldName}:${accessor.fieldType}"
                else -> error("Unexpected path")
            } } +
                manager.marks.name((chosen.mark as MarkSlot.Concrete).mark)
        }
        assertEquals(selected(false), selected(true))
    }

    @Test fun `independent scalar sort agrees with explicit category and zero expectations`() {
        val arg = initial(AccessPathBase.Argument(3))
        val receiver = initial(AccessPathBase.This)
        val field = static("Box", "value")
        val cls = static("Box")
        val policy = Run1Premises(m)
        val oracle = RawRun1Representative(m)
        val ranked = listOf(arg, receiver, field, cls)
        for (i in ranked.indices) for (j in i until ranked.size) {
            val binding = listOf(m.zero, ranked[j], ranked[i], ranked[j])
            val expected = ranked[i].toPattern()
            assertEquals(setOf(expected), oracle.choose(binding.map { setOf(it.toPattern()) }))
            assertEquals(expected, policy.combine(binding).toPattern())
        }
        assertEquals(setOf(ZERO_PATTERN), oracle.choose(listOf(setOf(ZERO_PATTERN), setOf(ZERO_PATTERN))))
    }
}

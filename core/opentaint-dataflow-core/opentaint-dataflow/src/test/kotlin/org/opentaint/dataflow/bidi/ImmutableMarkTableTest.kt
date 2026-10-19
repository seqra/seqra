package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.TestMark
import org.opentaint.dataflow.bidi.fixtures.testApManager
import org.opentaint.dataflow.bidi.fixtures.testDeclarations
import org.opentaint.dataflow.util.Cancellation
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ImmutableMarkTableTest {
    @Test fun `frozen declarations detach input and reject unknown executable names`() {
        val name = "T"
        val key = FullKey(encodeGroup(MarkGroupKey.Rule("outer"), false), name)
        val input = linkedMapOf(name to key)
        val declarations = FrozenMarkDeclarations(input)
        input.clear()
        input["later"] = FullKey(encodeGroup(MarkGroupKey.Model("later"), false), "later")
        assertEquals(key, declarations.fullKey(name))
        assertFailsWith<IllegalArgumentException> { declarations.fullKey("later") }
        assertFailsWith<IllegalArgumentException> {
            FrozenMarkDeclarations(mapOf("wrong lookup" to key))
        }
        val table = MarkTable(declarations)
        assertIs<MarkAtomKey.ControlZero>(table.key(TaintMark.ZERO))
        assertFailsWith<IllegalArgumentException> { table.full("undeclared") }
        assertEquals(1, table.full(name).id, "Only control ZERO was interned before actual use")
    }

    @Test fun `group encoding separates owner kinds flags and delimiter text`() {
        val owners = listOf(MarkGroupKey.Rule("id"), MarkGroupKey.Model("id"), MarkGroupKey.SharedModel,
            MarkGroupKey.Rule("id;%%primitive%%"), MarkGroupKey.Rule("@group:2:S:;%%ordinary%%"))
        val encoded = owners.flatMap { owner -> listOf(false, true).map { encodeGroup(owner, it).encoded } }
        assertEquals(owners.size * 2, encoded.toSet().size)
        owners.forEach { owner ->
            assertTrue(encodeGroup(owner, true).encoded.endsWith("%%primitive%%"))
            assertFalse(encodeGroup(owner, false).encoded.endsWith("%%primitive%%"))
        }
    }

    @Test fun `concurrent first use keeps full group and atom handles canonical`() {
        val declarations = testDeclarations(*(0 until 128).map {
            TestMark("mark-$it", MarkGroupKey.Rule("same owner"))
        }.toTypedArray())
        val table = MarkTable(declarations)
        val pool = Executors.newFixedThreadPool(8)
        val barrier = CyclicBarrier(8)
        try {
            val results = (0 until 8).map {
                pool.submit<List<Pair<TaintMark, TaintMark>>> {
                    barrier.await(10, TimeUnit.SECONDS)
                    (0 until 128).map { i ->
                        val full = table.full("mark-$i")
                        full to table.group(table.groupOf(full))
                    }
                }
            }.map { it.get(20, TimeUnit.SECONDS) }
            results.forEach { assertEquals(results.first(), it) }
            assertEquals(128, results.first().map { it.first }.toSet().size)
            val group = results.first().map { it.second }.toSet().single()
            assertTrue(table.isGroup(group))
            assertFalse(table.isFull(group))
            results.first().forEach { (full, sameGroup) ->
                assertTrue(table.isFull(full))
                assertEquals(group, sameGroup)
                assertNotEquals(group, full)
                assertNotEquals(TaintMark.ZERO, full)
                assertEquals(table.groupOf(full), table.owner(assertNotNull(table.fullId(full))))
            }
        } finally { pool.shutdownNow() }
    }

    @Test fun `explicit group sets convert their IDs without discovering other groups`() {
        val table = MarkTable(testDeclarations(TestMark("T"), TestMark("U"), TestMark("unused")))
        val t = table.full("T")
        val u = table.full("U")
        val groups = GroupSet.of(listOf(table.groupOf(u), table.groupOf(t)))
        assertEquals(setOf(table.group(table.groupOf(t)), table.group(table.groupOf(u))),
            table.atomsFor(groups).ids.map(::TaintMark).toSet())
        assertTrue(table.atomsFor(GroupSet.EMPTY).isEmpty)
        assertFails { table.group(GroupId(2)) }
    }

    @Test fun `declaration validation rejects mixed flags and conflicting owners`() {
        assertFailsWith<IllegalArgumentException> {
            testDeclarations(TestMark("T", MarkGroupKey.SharedModel),
                TestMark("U%%primitive%%", MarkGroupKey.SharedModel))
        }
        assertFailsWith<IllegalArgumentException> {
            testDeclarations(TestMark("T", MarkGroupKey.Rule("A")), TestMark("T", MarkGroupKey.Rule("B")))
        }
    }

    @Test fun `twenty group primitive Facts checks do not scale with declared name population`() {
        val accepts = object : FactTypeChecker.FactApFilter {
            override fun check(accessor: org.opentaint.dataflow.ap.ifds.Accessor) = FactTypeChecker.FilterResult.Accept
        }
        for (population in listOf(51, 1172)) {
            val declarations = testDeclarations(TestMark("P%%primitive%%", MarkGroupKey.Rule("primitive")),
                *(0 until population - 1).map { TestMark("M$it", MarkGroupKey.Rule("ordinary")) }.toTypedArray())
            val manager = testApManager(Cancellation(), declarations = declarations)
            val ops = ApOps(manager)
            val primitive = manager.marks.runtimeAtom(manager.marks.full("P%%primitive%%"), MarkMode.GROUP)
            val ordinary = manager.marks.runtimeAtom(manager.marks.full("M0"), MarkMode.GROUP)
            var calls = 0
            val filter = TypeFilter(accepts, MarkPolicy { mark, level ->
                calls++
                assertEquals(0, level)
                manager.marks.name(mark).endsWith("%%primitive%%")
            })
            repeat(20) { i ->
                val atom = if (i % 2 == 0) primitive else ordinary
                val value = manager.factsOf(PathFact(AccessPathBase.Argument(0), emptyList(),
                    Tail.EXACT, MarkSlot.Concrete(atom)), ExclusionSet.Empty, Layer.DEMAND)
                assertEquals(i % 2 == 0, ops.filter(value, filter) != null)
            }
            assertEquals(20, calls, "One existing policy call per checked leaf, population=$population")
        }
    }
}

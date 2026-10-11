package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

class SeedIndexTest {
    private val f = EngineShellFixtures()
    private val field = f.field("f")
    private val other = f.field("g")
    private val statement get() = f.inst(f.methodA, 1)
    private val rule = object : CommonTaintConfigurationSink {
        override val id = "sink"
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = "sink"
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Error
        }
    }
    private fun edge(path: List<Int> = listOf(field), tail: Tail = Tail.ANY_TAINT,
                     mark: MarkSlot = MarkSlot.Concrete(f.manager.marks.mark("T")),
                     exclusion: ExclusionSet = ExclusionSet.Empty): PathEdge =
        PathEdge(f.manager.zero.toPattern().fact,
            PathFact(AccessPathBase.Return, path, tail, mark), exclusion)

    @Test fun `sink requirements own paths and retain complete structural equality and hash`(): Unit {
        val path = arrayListOf(field, other)
        val requirement = f.pattern(path = path, tail = Tail.ANY)
        val seed = Seed.Sink(rule, f.a, statement, requirement)
        val expected = Seed.Sink(rule, f.a, statement, f.pattern(path = listOf(field, other), tail = Tail.ANY))
        val hash = seed.hashCode()
        path[0] = other
        assertEquals(listOf(field, other), seed.requirement.fact.path)
        assertEquals(expected, seed)
        assertEquals(expected.hashCode(), seed.hashCode())
        assertEquals(hash, seed.hashCode())
        assertSame(rule, seed.rule)
        assertSame(f.a, seed.method)
        assertSame(statement, seed.statement)
        assertFailsWith<UnsupportedOperationException> { (seed.requirement.fact.path as MutableList).clear() }
        assertNotEquals(seed, Seed.Sink(rule, f.a, statement, f.pattern(path = listOf(field), tail = Tail.ANY)))
        assertNotEquals(seed, Seed.Sink(rule, f.b, statement, expected.requirement))
    }

    @Test fun `source retains the supplied forward edge while lookup uses complete structural metadata`(): Unit {
        val forward = edge()
        val seed = Seed.Source(f.a, statement, forward)
        val index = SeedIndex.of(listOf(seed))
        assertSame(forward, seed.edge)
        assertSame(seed, index.at(f.a, statement).single())
        assertTrue(index.allowsSource(f.a, statement, forward))
        assertTrue(index.allowsSource(f.a, statement, edge()))
        assertFalse(index.allowsSource(f.a, statement, edge(path = listOf(other))))
        assertFalse(index.allowsSource(f.a, statement, edge(tail = Tail.EXACT)))
        assertFalse(index.allowsSource(f.a, statement, edge(mark = MarkSlot.Concrete(f.manager.marks.mark("U")))))
        assertFalse(index.allowsSource(f.a, statement, edge(exclusion = ExclusionSet.of(other))))
    }

    @Test fun `method context and statement remain part of every seed lookup`(): Unit {
        val forward = edge()
        val differentContext = f.key(f.methodA, "other")
        val differentStatement = f.inst(f.methodA, 2)
        val source = Seed.Source(f.a, statement, forward)
        val sink = Seed.Sink(rule, differentContext, differentStatement, f.pattern())
        val index = SeedIndex.of(listOf(source, sink))
        assertEquals(listOf(source), index.at(f.a, statement))
        assertEquals(listOf(sink), index.at(differentContext, differentStatement))
        for ((method, at) in listOf(f.b to statement, differentContext to statement, f.a to differentStatement)) {
            assertTrue(index.at(method, at).isEmpty())
            assertFalse(index.allowsSource(method, at, forward))
        }
    }

    @Test fun `seed order and multiplicity survive grouping and owned snapshots`(): Unit {
        val source = Seed.Source(f.a, statement, edge())
        val sink = Seed.Sink(rule, f.a, statement, f.pattern())
        val seeds = arrayListOf<Seed>(source, sink, source, sink)
        val index = SeedIndex.of(seeds)
        seeds.clear()
        assertEquals(4, index.size)
        assertEquals(listOf(source, sink, source, sink), index.at(f.a, statement))
        assertFailsWith<UnsupportedOperationException> { (index.at(f.a, statement) as MutableList).clear() }
        assertEquals(setOf(f.a), index.sinkMethods())
    }

    @Test fun `direct index construction owns the outer map and each seed list`(): Unit {
        val source = Seed.Source(f.a, statement, edge())
        val values = arrayListOf<Seed>(source)
        val byPlace = linkedMapOf((f.a to statement) to values)
        val index = SeedIndex(byPlace)
        values.clear()
        byPlace.clear()
        assertEquals(1, index.size)
        assertSame(source, index.at(f.a, statement).single())
        assertTrue(index.allowsSource(f.a, statement, source.edge))
    }

    @Test fun `sink method enumeration includes contexts and excludes source only places`(): Unit {
        val context = f.key(f.methodA, "second")
        val index = SeedIndex.of(listOf(Seed.Source(f.b, f.inst(f.methodB, 1), edge()),
            Seed.Sink(rule, f.a, statement, f.pattern()), Seed.Sink(rule, context, statement, f.pattern())))
        assertEquals(setOf(f.a, context), index.sinkMethods())
        assertEquals(setOf(f.a, context), index.sinkMethods())
        assertFalse(index.sinkMethods().contains(f.b))
    }

    @Test fun `empty seed index has no source admission or sink methods`(): Unit {
        assertEquals(0, SeedIndex.EMPTY.size)
        assertTrue(SeedIndex.EMPTY.at(f.a, statement).isEmpty())
        assertTrue(SeedIndex.EMPTY.sinkMethods().isEmpty())
        assertFalse(SeedIndex.EMPTY.allowsSource(f.a, statement, edge()))
    }

    @Test fun `a deep sink requirement owns its full path without recursive construction`(): Unit {
        val path = MutableList(32768) { if (it % 2 == 0) field else other }
        val seed = Seed.Sink(rule, f.a, statement, f.pattern(path = path))
        path.clear()
        assertEquals(32768, seed.requirement.fact.path.size)
        assertEquals(field, seed.requirement.fact.path.first())
        assertEquals(other, seed.requirement.fact.path.last())
        assertEquals(seed, Seed.Sink(rule, f.a, statement,
            f.pattern(path = List(32768) { if (it % 2 == 0) field else other })))
    }

    @Test fun `field action temporary bases cannot enter persistent seed requirements or source sides`(): Unit {
        val temporary = AccessPathBase.CleanerTemporary()
        assertFailsWith<IllegalStateException> { Seed.Sink(rule, f.a, statement, f.pattern(base = temporary)) }
        val forward = edge()
        assertFailsWith<IllegalStateException> { Seed.Source(f.a, statement, forward.copy(from = forward.from.copy(base = temporary))) }
        assertFailsWith<IllegalStateException> { Seed.Source(f.a, statement, forward.copy(to = forward.to.copy(base = temporary))) }
    }
}

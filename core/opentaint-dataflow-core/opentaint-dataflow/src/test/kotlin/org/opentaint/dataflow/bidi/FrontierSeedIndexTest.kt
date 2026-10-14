package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.fixtures.HandOffFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

/** D02 additions; the existing E01 SeedIndexTest stays intact. */
class FrontierSeedIndexTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)

    private fun rule(id: String): CommonTaintConfigurationSink = object : CommonTaintConfigurationSink {
        override val id = id
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = id
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    @Test fun `source seed lookup retains full entry context statement and every forward edge field independently of compilation`(): Unit {
        HandOffFixtures().use { f ->
            val key = f.key("source context", entry = 0)
            val otherEntry = f.key("source context", entry = 1)
            val otherContext = f.key("other context", entry = 0)
            val at = f.inst(1)
            val a = f.field("source field")
            val b = f.field("source excluded field")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val forward = PathEdge(ZERO_FACT, PathFact(x, listOf(a), Tail.ANY_TAINT, t),
                ExclusionSet.Empty, toExclusion = ExclusionSet.of(b))
            val equal = forward.copy()
            val seed = Seed.Source(key, at, forward)
            val index = SeedIndex.of(listOf(seed))
            val hash = seed.hashCode()
            assertNull(forward.compiled)
            f.ops.applyEdge(Reach.NORMAL, f.manager.zero, forward, true, f.config.mode, object : ApOut {
                override fun result(facts: Facts) = Unit
                override fun markRequest(mark: TaintMark) = error("An unconditional source does not request a mark")
            })
            assertNotNull(forward.compiled)
            assertNull(equal.compiled)
            assertEquals(hash, seed.hashCode())
            assertEquals(seed, Seed.Source(key, at, equal))
            assertSame(forward, index.at(key, at).single().let { (it as Seed.Source).edge })
            assertTrue(index.allowsSource(key, at, equal))
            assertFalse(index.allowsSource(otherEntry, at, equal))
            assertFalse(index.allowsSource(otherContext, at, equal))
            assertFalse(index.allowsSource(key, f.inst(2), equal))
            val changes = listOf(
                forward.copy(from = forward.from.copy(base = y)),
                forward.copy(from = forward.from.copy(path = listOf(b))),
                forward.copy(from = forward.from.copy(tail = Tail.ANY)),
                forward.copy(from = forward.from.copy(mark = u)),
                forward.copy(to = forward.to.copy(base = y)),
                forward.copy(to = forward.to.copy(path = listOf(b))),
                forward.copy(to = forward.to.copy(tail = Tail.EXACT)),
                forward.copy(to = forward.to.copy(mark = u)),
                forward.copy(exclusion = ExclusionSet.of(a)),
                forward.copy(fromExclusion = ExclusionSet.of(a)),
                forward.copy(toExclusion = ExclusionSet.Empty),
            )
            for (changed in changes) {
                assertFalse(index.allowsSource(key, at, changed), "Complete forward identity differs: $changed")
                assertNotEquals(seed, Seed.Source(key, at, changed))
            }
        }
    }

    @Test fun `implicit zero builder insertion never increases explicit demand while a zero entry with an exit is retained`(): Unit {
        HandOffFixtures().use { f ->
            val keys = listOf(f.key("left"), f.key("right"))
            val builder = DemandStore.Builder(f.manager)
            val implicit = DemandPattern(ZERO_PATTERN, null)
            val explicit = DemandPattern(ZERO_PATTERN, f.pattern(x))
            var accepted = 0
            for (key in keys) {
                assertFalse(builder.add(key, implicit))
                assertFalse(builder.add(key, implicit))
                if (builder.add(key, explicit)) accepted++
                assertFalse(builder.add(key, explicit))
            }
            assertEquals(2, accepted)
            val snapshot = builder.build()
            for (key in keys) {
                assertEquals(listOf(explicit), snapshot.covering(key, AccessPathBase.Zero, null))
                assertEquals(setOf(explicit, implicit), snapshot.near(key, AccessPathBase.Zero, null).toSet())
            }
            builder.add(keys.first(), DemandPattern(f.pattern(y), f.pattern(x)))
            assertTrue(snapshot.near(keys.first(), y, null).isEmpty(), "A demand snapshot owns its builder contents")
        }
    }

    @Test fun `derived backward sink seeds deduplicate exact requirements while the direct index retains literal multiplicity`(): Unit {
        HandOffFixtures().use { f ->
            val id = rule("repeated seed literals")
            val px = f.pattern(x)
            val py = f.pattern(y, mark = MarkSlot.Concrete(f.mark("U")))
            val keys = listOf(f.key("left sink context"), f.key("right sink context"))
            val sinks = listOf(SinkRule(id, 0, listOf(px, px), emptyList()),
                SinkRule(id, 1, listOf(px, py), emptyList()), SinkRule(id, 2, listOf(ZERO_PATTERN), emptyList()))
            f.finite.exitRules(2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, sinks), false, emptySet()))
            for (key in keys) {
                f.core(key)
                val edge = SinkEdge(f.manager.zero, Layer.DEMAND, f.fact(x, layer = Layer.DEMAND))
                val witness = SinkWitness(0, key, listOf(edge, edge), f.index)
                f.vulnerabilities.add(VulnerabilityKey(id, key.method, f.inst(2)), witness)
                f.vulnerabilities.add(VulnerabilityKey(id, key.method, f.inst(2)),
                    SinkWitness(1, key, listOf(edge,
                        SinkEdge(f.manager.zero, Layer.DEMAND, f.fact(y, mark = py.fact.mark, layer = Layer.DEMAND))), f.index))
            }
            val result = f.result()
            f.report.add(f.config, result)
            assertEquals(1, f.report.demandEntries().size)
            assertEquals(4, f.report.demandEntries().single().witnesses.size)
            val rawInputs = keys.flatMap { key -> listOf(px, px, px, py).map { Seed.Sink(id, key, f.inst(2), it) } }
            val direct = SeedIndex.of(rawInputs)
            assertEquals(8, direct.size)
            for (key in keys) assertEquals(listOf(px, px, px, py),
                direct.at(key, f.inst(2)).map { (it as Seed.Sink).requirement })
            val handOff = HandOff.of(f.config, result, f.report, f.shared)
            val expected = keys.flatMap { key -> listOf(px, py).map { Seed.Sink(id, key, f.inst(2), it) } }.toSet()
            val actual = keys.flatMap { handOff.seeds.at(it, f.inst(2)) }.toSet()
            assertEquals(expected, actual)
            assertEquals(4, handOff.seeds.size, "Contexts remain distinct; alternatives and repeated literals give no duplicate exact seed")
            assertEquals(keys.toSet(), handOff.seeds.sinkMethods())
            assertTrue(actual.all { (it as Seed.Sink).rule === id })
            assertTrue(actual.none { (it as Seed.Sink).requirement == ZERO_PATTERN }, "An unconditional alternative has no positive seed")
            assertEquals(4, frontierOf(f.config, result, handOff, f.report).seeds)
        }
    }

    @Test fun `source and sink seed snapshots own place lists while preserving supplied forward identities and order`(): Unit {
        HandOffFixtures().use { f ->
            val left = f.key("left", entry = 0)
            val right = f.key("left", entry = 1)
            val a = f.field("a")
            val id = rule("snapshot")
            val mutablePath = arrayListOf(a)
            val sink = Seed.Sink(id, right, f.inst(2), f.pattern(x, mutablePath, Tail.ANY))
            val forward = PathEdge(ZERO_FACT, f.pattern(y).fact, ExclusionSet.Empty)
            val source = Seed.Source(left, f.inst(1), forward)
            val inputs = arrayListOf<Seed>(source, sink, source, sink)
            val index = SeedIndex.of(inputs)
            inputs.clear()
            mutablePath.clear()
            assertEquals(4, index.size)
            assertEquals(listOf(source, source), index.at(left, f.inst(1)))
            assertEquals(listOf(sink, sink), index.at(right, f.inst(2)))
            assertSame(forward, (index.at(left, f.inst(1)).first() as Seed.Source).edge)
            assertEquals(listOf(a), sink.requirement.fact.path)
            assertFalse(index.allowsSource(right, f.inst(1), forward))
            assertEquals(setOf(right), index.sinkMethods())
            assertFailsWith<UnsupportedOperationException> { (index.at(right, f.inst(2)) as MutableList).clear() }
            assertFailsWith<UnsupportedOperationException> { (sink.requirement.fact.path as MutableList).clear() }
        }
    }
}

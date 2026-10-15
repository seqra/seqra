package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.HistoricalSupport
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.bidi.fixtures.SupportFixtures
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.common.cfg.CommonInst
import kotlin.test.*

/** Historical support laws, not the current candidate-report policy. */
class SupportTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)

    private data class InputLink(val callee: MethodKey, val caller: MethodKey, val premise: PremiseKey,
                                 val added: Pattern, val callerLayer: Layer, val linkLayer: Layer, val call: CommonInst)
    private fun link(f: SupportFixtures, inputs: MutableList<InputLink>, callee: MethodKey, caller: MethodKey,
                     premise: PremiseKey, added: InitialAp, call: Int = 1,
                     callerLayer: Layer = Layer.NORMAL, linkLayer: Layer = Layer.NORMAL) {
        val p = added.toPattern()
        inputs += InputLink(callee, caller, premise, p, callerLayer, linkLayer, f.inst(caller, call))
        f.link(callee, caller, premise, f.fact(p.fact.base, p.fact.path, p.fact.tail, p.fact.mark, p.exclusion, linkLayer),
            callerLayer, linkLayer, call)
    }

    /** Independent finite list fixed point for zero/exact inputs, with no AP support predicate or index. */
    private fun exactFixedPoint(roots: List<MethodKey>, inputs: List<InputLink>,
                                queries: Set<Pair<MethodKey, PremiseKey>>, zero: InitialAp): Set<Pair<MethodKey, PremiseKey>> {
        val questions = queries + inputs.map { it.caller to it.premise }
        val supported = roots.mapTo(LinkedHashSet()) { it to (zero as PremiseKey) }
        var changed: Boolean
        do {
            changed = false
            for ((method, premise) in questions) {
                if ((method to premise) in supported) continue
                val normal = inputs.filter { it.callee == method && it.callerLayer == Layer.NORMAL && it.linkLayer == Layer.NORMAL }
                val sites = normal.map { it.caller to it.call }.distinct()
                val holds = sites.any { site -> premise.members.all { member ->
                    val admissible = member.isZero || (member.tail == Tail.EXACT && member.mark is MarkSlot.Concrete)
                    admissible && normal.any { edge -> (edge.caller to edge.call) == site &&
                        edge.added == member.toPattern() && (edge.caller to edge.premise) in supported }
                } }
                if (holds) { supported += method to premise; changed = true }
            }
        } while (changed)
        return supported
    }

    @Test
    fun `root support starts only with canonical zero and normal zero links extend it`(): Unit {
        SupportFixtures().use { f ->
            val root = f.key("root")
            val callee = f.key("callee")
            val unrelated = f.key("unrelated")
            val exact = f.initial()
            f.link(callee, root, f.manager.zero, Reach.NORMAL)
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            assertTrue(support.isSupported(root, f.manager.zero.members))
            assertFalse(support.isSupported(root, exact.members))
            assertTrue(support.isSupported(callee, f.manager.zero.members))
            assertFalse(support.isSupported(unrelated, f.manager.zero.members))
        }
    }

    @Test
    fun `transitive exact support is the least fixed point independent of link and actor order`(): Unit {
        for (reverse in listOf(false, true)) SupportFixtures().use { f ->
            val root = f.key("root")
            val first = f.key("first")
            val second = f.key("second")
            val sink = f.key("sink")
            val a = f.initial(mark = MarkSlot.Concrete(f.mark("A")))
            val b = f.initial(y, mark = MarkSlot.Concrete(f.mark("B")))
            val inputs = ArrayList<InputLink>()
            val actions = listOf<() -> Unit>(
                { link(f, inputs, first, root, f.manager.zero, a) },
                { link(f, inputs, second, first, a, b) },
                { link(f, inputs, sink, second, b, a) },
                { link(f, inputs, sink, second, b, b) })
            (if (reverse) actions.reversed() else actions).forEach { it() }
            val both = f.premise(a, b)
            val queries = setOf(root to f.manager.zero, root to a, first to a, second to b, sink to both)
            val expected = exactFixedPoint(listOf(root), inputs, queries, f.manager.zero)
            for (order in listOf(listOf(root, first, second, sink), listOf(sink, second, first, root),
                listOf(second, root, sink, first))) {
                val support = HistoricalSupport(f.result(order = order), listOf(root), f.mode, f.shared)
                for ((method, premise) in queries) assertEquals((method to premise) in expected,
                    support.isSupported(method, premise.members), "reverse=$reverse order=$order question=$method/$premise")
                assertTrue(support.isSupported(sink, both.members))
                assertFalse(support.isSupported(root, a.members))
            }
        }
    }

    @Test
    fun `cycles without a supported zero supplier never bootstrap exact support`(): Unit {
        SupportFixtures().use { f ->
            val root = f.key("unrelated root")
            val left = f.key("left")
            val right = f.key("right")
            f.actor(root)
            val a = f.initial()
            val b = f.initial(y, mark = MarkSlot.Concrete(f.mark("B")))
            val rows = ArrayList<InputLink>()
            link(f, rows, left, right, b, a)
            link(f, rows, right, left, a, b)
            val questions = setOf(left to a, right to b)
            val expected = exactFixedPoint(listOf(root), rows, questions, f.manager.zero)
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            for ((method, premise) in questions) {
                assertFalse((method to premise) in expected)
                assertFalse(support.isSupported(method, premise.members))
            }
        }
    }

    @Test
    fun `all joint members need one call site although each member can use a different caller edge`(): Unit {
        for (sameSite in listOf(false, true)) SupportFixtures().use { f ->
            val root = f.key("root")
            val callee = f.key("callee")
            val a = f.initial()
            val b = f.initial(y, mark = MarkSlot.Concrete(f.mark("B")))
            val rows = ArrayList<InputLink>()
            link(f, rows, callee, root, f.manager.zero, a, call = 1)
            link(f, rows, callee, root, f.manager.zero, b, call = if (sameSite) 1 else 2)
            val joint = f.premise(a, b)
            val queries = setOf(callee to a, callee to b, callee to joint)
            val expected = exactFixedPoint(listOf(root), rows, queries, f.manager.zero)
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            assertTrue(support.isSupported(callee, a.members))
            assertTrue(support.isSupported(callee, b.members))
            assertEquals(sameSite, support.isSupported(callee, joint.members))
            assertEquals(sameSite, (callee to joint) in expected)
        }
    }

    @Test
    fun `same instruction in separate caller contexts or entries is not a joint supplier site`(): Unit {
        for (differentEntry in listOf(false, true)) SupportFixtures().use { f ->
            val callerA = f.key("caller", "left", entry = 0)
            val callerB = f.key("caller", if (differentEntry) "left" else "right", entry = if (differentEntry) 3 else 0)
            val callee = f.key("callee")
            val a = f.initial()
            val b = f.initial(y)
            assertSame(f.inst(callerA, 1), f.inst(callerB, 1))
            f.link(callee, callerA, f.manager.zero, f.fact(mark = a.mark))
            f.link(callee, callerB, f.manager.zero, f.fact(y, mark = b.mark))
            val support = HistoricalSupport(f.result(), listOf(callerA, callerB), f.mode, f.shared)
            assertTrue(support.isSupported(callee, a.members))
            assertTrue(support.isSupported(callee, b.members))
            assertFalse(support.isSupported(callee, f.premise(a, b).members))
        }
    }

    @Test
    fun `callee context and entry keys remain separate during transitive support`(): Unit {
        SupportFixtures().use { f ->
            val root = f.key("root")
            val left = f.key("callee", "left", 0)
            val right = f.key("callee", "right", 0)
            val otherEntry = f.key("callee", "left", 3)
            val sink = f.key("sink")
            val a = f.initial()
            f.link(left, root, f.manager.zero, f.fact())
            f.link(sink, right, a, f.fact())
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            assertTrue(support.isSupported(left, a.members))
            assertFalse(support.isSupported(right, a.members))
            assertFalse(support.isSupported(otherEntry, a.members))
            assertFalse(support.isSupported(sink, a.members))
        }
    }

    @Test
    fun `different supported caller premise sets can supply joint members at their one outgoing call`(): Unit {
        SupportFixtures().use { f ->
            val root = f.key("root")
            val caller = f.key("caller")
            val callee = f.key("callee")
            val a = f.initial()
            val b = f.initial(y, mark = MarkSlot.Concrete(f.mark("B")))
            f.link(caller, root, f.manager.zero, f.fact(mark = a.mark), call = 1)
            f.link(caller, root, f.manager.zero, f.fact(y, mark = b.mark), call = 2)
            f.link(callee, caller, a, f.fact(mark = a.mark), call = 1)
            f.link(callee, caller, b, f.fact(y, mark = b.mark), call = 1)
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            assertFalse(support.isSupported(caller, f.premise(a, b).members))
            assertTrue(support.isSupported(callee, f.premise(a, b).members), "The support tree may branch below one call")
        }
    }

    @Test
    fun `both the added link layer and the caller edge layer must be normal`(): Unit {
        for (callerLayer in Layer.entries) for (linkLayer in Layer.entries) SupportFixtures().use { f ->
            val root = f.key("root")
            val callee = f.key("callee")
            val exact = f.initial()
            f.link(callee, root, f.manager.zero, f.fact(layer = linkLayer), callerLayer, linkLayer)
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            assertEquals(callerLayer == Layer.NORMAL && linkLayer == Layer.NORMAL,
                support.isSupported(callee, exact.members), "caller=$callerLayer added=$linkLayer")
        }
    }

    @Test
    fun `exactness alone does not support a caller premise and cannot supply a callee`(): Unit {
        SupportFixtures().use { f ->
            val root = f.key("root")
            val caller = f.key("unsupported caller")
            val callee = f.key("callee")
            val exact = f.initial()
            f.link(callee, caller, exact, f.fact())
            val witness = f.witness(callee, exact, facts = f.fact())
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            assertFalse(support.isSupported(callee, exact.members))
            support.confirm()
            assertFalse(witness.confirmed)
        }
    }

    @Test
    fun `FLOW and may initial members cannot confirm even with otherwise matching supplier shapes`(): Unit {
        for (index in listOf(1, 3)) SupportFixtures(index).use { f ->
            val root = f.key("root")
            val callee = f.key("callee")
            val flow = f.initial(tail = Tail.STAR, mark = MarkSlot.STAR)
            val may = f.initial(y, tail = Tail.ANY)
            f.link(callee, root, f.manager.zero, f.fact(tail = Tail.STAR, mark = MarkSlot.STAR))
            f.link(callee, root, f.manager.zero, f.fact(y, tail = Tail.ANY_TAINT))
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            assertFalse(support.isSupported(callee, flow.members))
            assertFalse(support.isSupported(callee, may.members))
        }
    }

    @Test
    fun `witness confirmation joins premises without zero and reads every edge layer`(): Unit {
        for (layer in Layer.entries) SupportFixtures().use { f ->
            val root = f.key("root")
            val callee = f.key("callee")
            val exact = f.initial()
            f.link(callee, root, f.manager.zero, f.fact())
            val witness = SinkWitness(0, callee, listOf(SinkEdge(f.manager.zero, Layer.NORMAL, Reach.NORMAL),
                SinkEdge(exact, layer, f.fact(layer = layer))), f.index)
            f.vulnerabilities.add(VulnerabilityKey(f.rule(), callee.method, f.inst(callee, 2)), witness)
            assertSame(exact, witness.supportPremise(f.manager))
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            support.confirm()
            assertEquals(layer == Layer.NORMAL, witness.confirmed)
        }
    }

    @Test
    fun `joint witness confirmation cannot substitute separate single member support`(): Unit {
        for (sameSite in listOf(false, true)) SupportFixtures().use { f ->
            val root = f.key("root")
            val callee = f.key("callee")
            val a = f.initial()
            val b = f.initial(y)
            f.link(callee, root, f.manager.zero, f.fact(), call = 1)
            f.link(callee, root, f.manager.zero, f.fact(y), call = if (sameSite) 1 else 2)
            val witness = SinkWitness(1, callee, listOf(SinkEdge(a, Layer.NORMAL, f.fact()),
                SinkEdge(b, Layer.NORMAL, f.fact(y))), f.index)
            f.vulnerabilities.add(VulnerabilityKey(f.rule(), callee.method, f.inst(callee, 2)), witness)
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            support.confirm()
            assertEquals(sameSite, witness.confirmed)
        }
    }

    @Test
    fun `all zero witness premises retain the canonical zero root base case`(): Unit {
        SupportFixtures().use { f ->
            val root = f.key("root")
            f.actor(root)
            val witness = SinkWitness(0, root, listOf(SinkEdge(f.manager.zero, Layer.NORMAL, Reach.NORMAL),
                SinkEdge(f.manager.zero, Layer.NORMAL, Reach.NORMAL)), f.index)
            f.vulnerabilities.add(VulnerabilityKey(f.rule(), root.method, f.inst(root, 2)), witness)
            assertSame(f.manager.zero, witness.supportPremise(f.manager))
            HistoricalSupport(f.result(), listOf(root), f.mode, f.shared).confirm()
            assertTrue(witness.confirmed)
        }
    }

    @Test
    fun `empty member collections are rejected at public support queries`(): Unit {
        SupportFixtures().use { f ->
            val root = f.key("root")
            val callee = f.key("callee")
            f.link(callee, root, f.manager.zero, Reach.NORMAL)
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            assertFails { support.isSupported(root, emptyList()) }
            assertFails { support.isSupported(callee, emptyList()) }
            assertTrue(support.isSupported(root, f.manager.zero.members))
        }
    }

    @Test
    fun `support rejects incomplete results and backward results before reading any run stores`(): Unit {
        for (status in listOf(RunStatus.TIMEOUT, RunStatus.OOM, RunStatus.FAILED)) SupportFixtures().use { f ->
            val root = f.key("root")
            val witness = f.witness(root)
            assertFails { HistoricalSupport(f.result(status), listOf(root), f.mode, f.shared) }
            assertFalse(witness.confirmed)
        }
        SupportFixtures(2).use { f ->
            val root = f.key("backward root")
            f.actor(root)
            assertFails { HistoricalSupport(f.result(), listOf(root), f.mode, f.shared) }
        }
    }

    @Test
    fun `support construction checks cancellation even with no roots or actors`(): Unit {
        SupportFixtures().use { f ->
            val unreached = f.key("unreached")
            val result = f.result()
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { HistoricalSupport(result, emptyList(), f.mode, f.shared) }
            f.cancellation.activate()
            val support = HistoricalSupport(result, emptyList(), f.mode, f.shared)
            assertFalse(support.isSupported(unreached, f.manager.zero.members))
        }
    }

    @Test
    fun `support queries check cancellation before the root shortcut and preserve results after activation`(): Unit {
        SupportFixtures().use { f ->
            val root = f.key("root")
            f.actor(root)
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { support.isSupported(root, f.manager.zero.members) }
            f.cancellation.activate()
            assertTrue(support.isSupported(root, f.manager.zero.members))
        }
    }

    @Test
    fun `confirmation checks cancellation before changing witnesses and remains usable after activation`(): Unit {
        SupportFixtures().use { f ->
            val root = f.key("root")
            val witness = f.witness(root)
            val support = HistoricalSupport(f.result(), listOf(root), f.mode, f.shared)
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { support.confirm() }
            assertFalse(witness.confirmed)
            f.cancellation.activate()
            support.confirm()
            assertTrue(witness.confirmed)
        }
    }
}

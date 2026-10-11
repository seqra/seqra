package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.Record
import kotlin.test.*

class ProtocolContractsTest {
    private val f = EngineShellFixtures()

    @Test fun `edge delta zero classification follows premise and REACH kind in either layer`(): Unit {
        val node = f.inst(f.methodA, 1)
        for (layer in Layer.entries) {
            val reach = f.ops.zero(layer)
            val delta = EdgeDelta(f.manager.zero, node, reach)
            assertTrue(delta.zeroToZero)
            assertSame(f.manager.zero, delta.premise)
            assertSame(node, delta.node)
            assertSame(reach, delta.facts)
            assertFalse(EdgeDelta(f.manager.initial(f.pattern()), node, reach).zeroToZero)
            assertFalse(EdgeDelta(f.manager.zero, node, f.fact(layer = layer)).zeroToZero)
        }
        val flow = f.fact(tail = Tail.STAR, mark = MarkSlot.STAR)
        assertFalse(EdgeDelta(f.manager.initial(f.pattern(tail = Tail.STAR, mark = MarkSlot.STAR)), node, flow).zeroToZero)
    }

    @Test fun `publication preserves exact canonical premise facts and layer`(): Unit {
        val premise = f.manager.premiseOf(listOf(f.manager.initial(f.pattern()),
            f.manager.initial(f.pattern(AccessPathBase.Return))))
        for (layer in Layer.entries) {
            val facts = f.fact(layer = layer)
            val publication = Publication(premise, facts)
            assertSame(premise, publication.premise)
            assertSame(facts, publication.conclusion)
            assertEquals(layer, publication.layer)
            assertEquals(publication, Publication(premise, facts))
            assertEquals(publication.hashCode(), Publication(premise, facts).hashCode())
        }
        assertNotEquals(Publication(premise, f.fact()), Publication(premise, f.fact(layer = Layer.DEMAND)))
    }

    @Test fun `subscription keeps caller layer independent of the common added fact and link layer`(): Unit {
        val premise = f.manager.initial(f.pattern())
        for (callerLayer in Layer.entries) for (linkLayer in Layer.entries) for (zeroOnly in listOf(false, true)) {
            val ref = CallerRef(f.a, premise, callerLayer, f.inst(f.methodA, 2))
            val added = if (zeroOnly) f.ops.zero(linkLayer) else f.fact(layer = linkLayer)
            val subscription = Subscription(f.b, ref, linkLayer, added, zeroOnly)
            assertSame(f.b, subscription.callee)
            assertSame(ref, subscription.ref)
            assertSame(f.a, subscription.caller)
            assertSame(added, subscription.added)
            assertEquals(callerLayer, subscription.ref.callerLayer)
            assertEquals(linkLayer, subscription.linkLayer)
            assertEquals(linkLayer, subscription.added.layer)
            assertEquals(zeroOnly, subscription.zeroOnly)
            if (zeroOnly) assertIs<Reach>(subscription.added) else assertIs<TaintTree>(subscription.added)
        }
        val ref = CallerRef(f.a, premise, Layer.NORMAL, f.inst(f.methodA, 2))
        assertFalse(Subscription(f.b, ref, Layer.NORMAL, f.fact()).zeroOnly)
    }

    @Test fun `inline replay and notification execute exactly once at the call site`(): Unit {
        val events = arrayListOf<String>()
        events += "before"
        ProtocolSteps.Inline.replay {
            events += "replay"
            ProtocolSteps.Inline.notify { events += "nested-notify" }
            events += "replay-end"
        }
        ProtocolSteps.Inline.notify { events += "notify" }
        events += "after"
        assertEquals(listOf("before", "replay", "nested-notify", "replay-end", "notify", "after"), events)
        assertFailsWith<IllegalStateException> { ProtocolSteps.Inline.replay { error("replay failure") } }
        assertFailsWith<IllegalStateException> { ProtocolSteps.Inline.notify { error("notify failure") } }
    }

    @Test fun `summary applier keeps publication member native record and transient view separate`(): Unit {
        val ref = CallerRef(f.a, f.manager.initial(f.pattern()), Layer.NORMAL, f.inst(f.methodA, 2))
        val part = Subscription(f.b, ref, Layer.NORMAL, f.fact())
        val pub = Publication(ref.premise, f.fact(AccessPathBase.Return))
        val record = Record(f.b, Direction.FORWARD, assertIs<InitialAp>(ref.premise), pub.conclusion)
        val view = RecordLeafView(f.pattern(AccessPathBase.Return),
            Conclusion(f.pattern().fact, ExclusionSet.Empty, false))
        val calls = arrayListOf<List<Any>>()
        val applier = object : SummaryApplier {
            override fun applySummary(part: Subscription, pub: Publication, member: Int) { calls += listOf(part, pub, member) }
            override fun applyRecord(part: Subscription, record: Record) { calls += listOf(part, record) }
            override fun applyRecordView(part: Subscription, view: RecordLeafView) { calls += listOf(part, view) }
        }
        applier.applySummary(part, pub, 2)
        applier.applyRecord(part, record)
        applier.applyRecordView(part, view)
        assertEquals(listOf(listOf(part, pub, 2), listOf(part, record), listOf(part, view)), calls)
        assertSame(pub, calls[0][1])
        assertSame(record, calls[1][1])
        assertSame(view, calls[2][1])
    }

    @Test fun `cut points retain all statement call boundary and seed cases`(): Unit {
        assertEquals(listOf("STATEMENT", "CALL", "ENTRY_RULES", "EXIT_RULES", "SEED"), Cut.entries.map { it.name })
    }

    @Test fun `delivery owns a pending list while preserving publication fact identities order and multiplicity`(): Unit {
        val premise = f.manager.initial(f.pattern())
        val normal = f.fact(AccessPathBase.Return)
        val demand = f.fact(layer = Layer.DEMAND)
        val first = Publication(premise, normal)
        val second = Publication(premise, demand)
        val replacement = Publication(premise, f.ops.zero(Layer.NORMAL))
        val pending = arrayListOf(first, second, first)
        val delivery = RunEvent.Delivery(f.b, pending)
        pending[0] = replacement
        assertEquals(listOf(first, second, first), delivery.publications)
        pending.clear()
        pending += replacement
        assertEquals(listOf(first, second, first), delivery.publications)
        assertSame(f.b, delivery.callee)
        assertSame(first, delivery.publications[0])
        assertSame(second, delivery.publications[1])
        assertSame(first, delivery.publications[2])
        assertSame(normal, delivery.publications[0].conclusion)
        assertSame(demand, delivery.publications[1].conclusion)
        assertSame(premise, delivery.publications[0].premise)
        assertFailsWith<UnsupportedOperationException> { (delivery.publications as MutableList).clear() }
        assertEquals(listOf(first, second, first), delivery.publications)
    }
}

package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.fixtures.Run1RequestPipelineFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.ir.api.common.CommonMethod
import kotlin.test.*

/** Current shared-view batching controls. The class name preserves historical
 * test selectors; no dynamic graph, wave, or full-source replay is used. */
class StaticNeedReplayBatchingTest {
    @Test fun `duplicate subscription leaves no pending callback or source work`() {
        ViewBatchNetwork().use { h ->
            val source=h.source("source"); val view=h.view("entry")
            assertTrue(h.subscribe(view,"site"))
            h.offer(source,4);h.commit(source);h.drain()
            val before=h.deliveries.size
            assertFalse(h.subscribe(view,"site"))
            h.commit(source);h.drain()
            assertEquals(before,h.deliveries.size)
            assertEquals(setOf(4),h.received("site"))
        }
    }

    @Test fun `equal content reader sources share one view before fanout`() {
        ViewBatchNetwork().use { h ->
            val first=h.source("first");val second=h.source("second")
            val view=h.view("entry")
            h.subscribe(view,"one");h.subscribe(view,"two")
            h.offer(first,3,7);h.offer(second,7,3)
            assertTrue(h.deliveries.isEmpty(),"Method temporary requests wait for commit")
            h.commit(first);h.commit(second);h.drain()
            assertEquals(2,h.router.snapshot().sources)
            assertEquals(1,h.router.snapshot().views)
            assertEquals(2L,h.router.snapshot().viewMarkMembers)
            assertEquals(2,h.deliveries.size,"Each callsite sees one shared union")
            assertEquals(setOf(3,7),h.received("one"));assertEquals(setOf(3,7),h.received("two"))
        }
    }

    @Test fun `unequal colliding mark hashes do not cross disconnected entries`() {
        ViewBatchNetwork().use { h ->
            val left=h.source("left");val right=h.source("right",h.f.methodUnknown)
            val a=RequestMarkSet.fromIds(intArrayOf(0,33));val b=RequestMarkSet.fromIds(intArrayOf(1,2))
            assertEquals(a.hashCode(),b.hashCode());assertNotEquals(a,b)
            h.subscribe(h.view("left",h.f.methodB),"left")
            h.subscribe(h.view("right",h.f.methodUnknown),"right")
            h.offer(left,0,33);h.offer(right,1,2);h.commit(left);h.commit(right);h.drain()
            assertEquals(setOf(0,33),h.received("left"));assertEquals(setOf(1,2),h.received("right"))
        }
    }

    @Test fun `one method temporary batch merges deltas and rejects later authoritative duplicates`() {
        ViewBatchNetwork().use { h ->
            val source=h.source("source");h.subscribe(h.view("entry"),"site")
            h.offer(source,4);h.offer(source,9);h.offer(source,4)
            assertTrue(h.deliveries.isEmpty())
            h.commit(source);h.drain()
            assertEquals(1,h.deliveries.size)
            assertEquals(setOf(4,9),h.received("site"))
            h.offer(source,4,9);h.commit(source);h.drain()
            assertEquals(1,h.deliveries.size)
        }
    }

    @Test fun `overlapping sources union only at reachable shared views`() {
        ViewBatchNetwork().use { h ->
            val left=h.source("left");val right=h.source("right",h.f.methodUnknown)
            h.subscribe(h.view("left",h.f.methodB),"left")
            h.subscribe(h.view("right",h.f.methodUnknown),"right")
            h.subscribe(h.view("shared"),"shared")
            h.offer(left,4,9);h.offer(right,9,12);h.commit(left);h.commit(right);h.drain()
            assertEquals(setOf(4,9),h.received("left"));assertEquals(setOf(9,12),h.received("right"))
            assertEquals(setOf(4,9,12),h.received("shared"))
        }
    }

    @Test fun `late view registration reads cumulative source while fresh marks still propagate`() {
        ViewBatchNetwork().use { h ->
            val source=h.source("source");h.offer(source,4);h.commit(source)
            h.subscribe(h.view("late"),"late")
            h.offer(source,9);h.commit(source);h.drain()
            assertEquals(setOf(4,9),h.received("late"))
            assertEquals(listOf(setOf(4),setOf(9)),h.deliveries.filter { it.site=="late" }.map { it.marks.ids() })
            assertEquals(1,h.router.snapshot().sources)
        }
    }

    @Test fun `fixed recursive SCC serves late views and late marks without traversal replay`() {
        for(sourceFirst in listOf(false,true)) ViewBatchNetwork(recursive=true).use { h ->
            val source=h.source("reader",h.f.methodA)
            if(sourceFirst) { h.offer(source,4);h.commit(source) }
            h.subscribe(h.view("entry",h.f.methodB),"site")
            if(!sourceFirst) { h.offer(source,4);h.commit(source) }
            h.drain();h.subscribe(h.view("late",h.f.methodB),"late")
            h.offer(source,9);h.commit(source);h.drain()
            assertEquals(setOf(4,9),h.received("site"));assertEquals(setOf(4,9),h.received("late"))
            assertEquals(1,h.router.snapshot().sources)
        }
    }

    @Test fun `every callsite owner and late subscriber receives the shared union`() {
        ViewBatchNetwork().use { h ->
            val source=h.source("source");val view=h.view("entry")
            h.subscribe(view,"first owner");h.subscribe(view,"second owner")
            h.offer(source,4);h.commit(source);h.drain()
            h.subscribe(view,"late owner");h.drain()
            for(site in listOf("first owner","second owner","late owner"))
                assertEquals(setOf(4),h.received(site))
            assertEquals(3,h.router.snapshot().subscribers)
        }
    }

    @Test fun `late subscriber replays old union while older callbacks remain queued`() {
        ViewBatchNetwork().use { h ->
            val source=h.source("source");val view=h.view("entry")
            h.subscribe(view,"old");h.offer(source,4);h.commit(source)
            assertTrue(h.deliveries.isEmpty())
            h.subscribe(view,"new")
            h.offer(source,9);h.commit(source);h.drain()
            assertEquals(setOf(4,9),h.received("old"));assertEquals(setOf(4,9),h.received("new"))
            assertEquals(4,h.deliveries.size)
        }
    }

    @Test fun `joined teardown releases source view and subscriber reference caches`() {
        ViewBatchNetwork().use { h ->
            val source=h.source("source");h.subscribe(h.view("entry"),"site")
            h.offer(source,4);h.commit(source);h.drain()
            assertEquals(setOf(4),h.received("site"))
            h.router.clear()
            assertEquals(CallerOwnedStaticRequests.Snapshot(0,0,0,0),h.router.snapshot())
        }
    }

    @Test fun `committing one method cannot flush another methods temporary requests`() {
        ViewBatchNetwork().use { h ->
            val first=h.source("first");val second=h.source("second",h.f.methodUnknown)
            h.subscribe(h.view("left",h.f.methodB),"left")
            h.subscribe(h.view("right",h.f.methodUnknown),"right")
            h.offer(first,4);h.offer(second,9);h.commit(first);h.drain()
            assertEquals(setOf(4),h.received("left"));assertTrue(h.received("right").isEmpty())
            h.commit(second);h.drain();assertEquals(setOf(9),h.received("right"))
        }
    }

    @Test fun `a source committed during another receiver callback keeps its own marks`() {
        ViewBatchNetwork().use { h ->
            val first=h.source("first");val second=h.source("second",h.f.methodUnknown)
            h.subscribe(h.view("left",h.f.methodB),"left")
            h.subscribe(h.view("right",h.f.methodUnknown),"right")
            h.onDelivery={ if(it.site=="left") { h.offer(second,9);h.commit(second) } }
            h.offer(first,4);h.commit(first);h.drain()
            assertEquals(setOf(4),h.received("left"));assertEquals(setOf(9),h.received("right"))
            assertEquals(2,h.router.snapshot().sources)
        }
    }

    @Test fun `committed Base request filters new view delivery without losing the other mark`() {
        Run1RequestPipelineFixtures().use { h ->
            val f=h.f;val field=Run1Slot.StaticField(f.ap.accessors.index(ClassStaticAccessor("C")),f.store.field("f"))
            val base=Run1Slot.Base(AccessPathBase.Argument(0));val t=f.store.mark("T");val u=f.store.mark("U")
            h.declareIdentityStaticDomain(field)
            h.publish(base,t.id,method=f.caller)
            val input=f.store.fact(STATIC,field.pathData.toList(),Tail.STAR,MarkSlot.STAR)
            h.submit(f.subscription(input,base.initial(f.ap)));h.drain()
            h.publish(field,t.id,u.id);h.drain()
            assertEquals(setOf(Triple(f.caller,base as Run1Slot,u.id)),h.climbs)
            h.commit(f.caller)
            assertEquals(RequestMarkSet.fromIds(intArrayOf(t.id,u.id)),h.storage(f.caller).borrowRequest(base).dynamic)
        }
    }

    @Test fun `packed diagonal and changed field inputs preserve both productive Base endpoints`() {
        Run1RequestPipelineFixtures().use { h ->
            val f=h.f;val cls=f.ap.accessors.index(ClassStaticAccessor("C"))
            val field=Run1Slot.StaticField(cls,f.store.field("f"));val g=Run1Slot.StaticField(cls,f.store.field("g"))
            val p=Run1Slot.Base(AccessPathBase.Argument(0));val q=Run1Slot.Base(AccessPathBase.Argument(1));val t=f.store.mark("T")
            val upstream=f.store.key("upstream");val changed=f.store.key("changed")
            h.declareIdentityStaticDomain(field,g)
            val atF=f.store.fact(STATIC,field.pathData.toList(),Tail.STAR,MarkSlot.STAR)
            val atG=f.store.fact(STATIC,g.pathData.toList(),Tail.STAR,MarkSlot.STAR)
            h.submitStatic(StaticSubscription(f.callee,f.caller,f.store.inst(1),
                StaticIdentityEntries(f.ops).single(field.initial(f.ap))))
            h.submit(f.subscription(atF,p.initial(f.ap),caller=upstream,callee=f.caller))
            h.submit(f.subscription(atF,g.initial(f.ap),caller=changed,callee=f.callee))
            h.submit(f.subscription(atG,q.initial(f.ap),caller=upstream,callee=changed,call=2))
            h.drain();h.publish(field,t.id);h.drain()
            h.commit(f.caller);h.commit(changed);h.drain()
            assertTrue(Triple(upstream,p as Run1Slot,t.id) in h.climbs)
            assertTrue(Triple(upstream,q as Run1Slot,t.id) in h.climbs)
        }
    }
}

private fun RequestMarkSet.ids(): Set<Int> = buildSet { forEachId { add(it) } }

/** Test scheduling only. The stores, source references, SCC gate and view
 * unions are production classes. Candidate inputs/summary semantics are tested
 * by the two pipeline controls and the runtime lifecycle controls. */
private class ViewBatchNetwork(recursive:Boolean=false) : AutoCloseable {
    val f=EngineShellFixtures()
    private val field=Run1Slot.StaticField(f.manager.accessors.index(ClassStaticAccessor("C")),f.field("f"))
    private val edges=mapOf(f.methodA to listOf(f.methodB,f.methodUnknown),
        f.methodB to if(recursive) listOf(f.methodA) else emptyList(),f.methodUnknown to emptyList())
    private val profiles=Run1StaticProfiles.build(f.manager,edges,
        listOf(f.methodA,f.methodB,f.methodUnknown).associateWith { Run1StaticLocalProfile.EMPTY })
    val router=CallerOwnedStaticRequests(f.manager,profiles.reachability)
    private val stores=LinkedHashMap<MethodKey,SummaryStorage>()
    private val temporary=LinkedHashMap<MethodKey,RequestStorage>()
    private val queued=ArrayDeque<Delivery>()
    val deliveries=ArrayList<Delivery>()
    var onDelivery:((Delivery)->Unit)?=null
    data class Delivery(val site:String,val marks:RequestMarkSet)
    fun source(name:String,method:CommonMethod=f.methodB)=StaticNeedKey(f.key(method,name),field)
    fun view(name:String,method:CommonMethod=f.methodA)=StaticDemandViewKey(f.key(method,name),field)
    private fun storage(method:MethodKey)=stores.getOrPut(method) {
        SummaryStorage(method,f.manager,f.ops,ProtocolSteps.Inline,staticRequests=router)
    }
    fun subscribe(view:StaticDemandViewKey,site:String)=router.subscribeView(view,site) { queued+=Delivery(site,it) }
    fun offer(source:StaticNeedKey,vararg ids:Int) {
        temporary.getOrPut(source.method,::RequestStorage).addAll(source.slot,RequestMarkSet.fromIds(ids))
    }
    fun commit(source:StaticNeedKey) { temporary.remove(source.method)?.let { storage(source.method).commit(it) } }
    fun drain() {
        var count=0
        while(queued.isNotEmpty()) {
            check(++count<100_000)
            val next=queued.removeFirst();deliveries+=next;onDelivery?.invoke(next)
        }
    }
    fun received(site:String):Set<Int> = deliveries.filter { it.site==site }.flatMap { it.marks.ids() }.toSet()
    override fun close() { queued.clear();stores.clear();temporary.clear();router.clear() }
}

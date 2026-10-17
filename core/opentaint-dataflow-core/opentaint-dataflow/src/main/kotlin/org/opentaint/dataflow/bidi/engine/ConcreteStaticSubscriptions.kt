package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.CallerRef

/** Runner-owned diagonal inputs, with no ordinary per-mark caller directory. */
internal class ConcreteStaticSubscriptions(private val port: SubscriptionPort, private val subscriber: SubscriptionManager) {
    private data class Port(val caller: MethodKey, val call: org.opentaint.ir.api.common.cfg.CommonInst,
        val from: org.opentaint.dataflow.bidi.interp.CallPoint, val to: org.opentaint.dataflow.bidi.interp.CallPoint,
        val identityCandidate: Boolean?)
    private val byCallee = LinkedHashMap<MethodKey, LinkedHashMap<Port, ConcreteStaticSubscription>>()
    private val codec = ConcreteStaticEntries(port.ops)

    fun subscribe(sub: ConcreteStaticSubscription) {
        check(port.config.run1)
        codec.validate(sub.entries)
        val storage = port.summaryStorage(sub.callee)
        val relations = byCallee.getOrPut(sub.callee) {
            LinkedHashMap<Port, ConcreteStaticSubscription>().also { storage.addSubscriber(subscriber) }
        }
        val key = Port(sub.caller, sub.call, sub.stageFrom, sub.stageTo, sub.identityCandidate)
        val (stored, delta) = codec.merge(relations[key]?.entries, sub.entries)
        if (delta == null) return
        relations[key] = sub.copy(entries = stored) // before both replay reads
        val fresh = sub.copy(entries = delta)
        port.steps.replay {
            val probe = codec.originals(delta).first()
            val input = Subscription(sub.callee, CallerRef(sub.caller, probe, Layer.DEMAND, sub.call), Layer.DEMAND, delta)
            for ((pub, member) in storage.candidates(input, port.config.mode)) apply(fresh, pub, member)
        }
        check(port.send(RunEvent.ConcreteStaticEntriesIn(sub.callee, delta))) { "A run rejected concrete static entry delivery" }
    }

    private fun apply(part: ConcreteStaticSubscription, pub: Publication, member: Int) {
        port.ops.manager.cancellation.checkpoint()
        val premise = pub.premise.member(member)
        if (pub.premise is InitialAp && isSpecialStaticRoot(premise)) {
            check(pub.conclusion == port.ops.startFact(premise).withLayer(Layer.DEMAND))
            port.applier(part.caller).applyConcreteStaticDefault(part, part.entries)
            return
        }
        if (premise.base != STATIC) return
        for (initial in codec.originals(part.entries)) {
            port.ops.manager.cancellation.checkpoint()
            val input = Subscription(part.callee, CallerRef(part.caller, initial, Layer.DEMAND, part.call),
                Layer.DEMAND, codec.current(initial))
            val matched = matches(input, pub, member, port.ops, port.config.mode) ?: continue
            port.applier(part.caller).applyConcreteStaticSummary(part, input.copy(added = matched), pub, member)
        }
    }

    fun onDelivery(callee: MethodKey, pubs: List<Publication>) {
        val inputs = byCallee[callee]?.values?.toList() ?: return
        for (pub in pubs) for (member in 0 until pub.premise.size) for (input in inputs) apply(input, pub, member)
    }
    fun clear() { byCallee.clear() }
}

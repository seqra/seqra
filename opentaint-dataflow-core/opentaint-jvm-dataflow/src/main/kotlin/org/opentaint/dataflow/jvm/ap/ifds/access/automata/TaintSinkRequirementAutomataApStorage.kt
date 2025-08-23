package org.opentaint.dataflow.jvm.ap.ifds.access.automata

import org.opentaint.dataflow.jvm.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.TaintSinkRequirementApStorage
import java.util.concurrent.ConcurrentHashMap

class TaintSinkRequirementAutomataApStorage : TaintSinkRequirementApStorage {
    private val based = ConcurrentHashMap<AccessPathBase, MutableSet<InitialFactAp>>()

    override fun add(ap: InitialFactAp): InitialFactAp? {
        val basedFacts = based.computeIfAbsent(ap.base) { ConcurrentHashMap.newKeySet() }
        if (!basedFacts.add(ap)) return null
        return ap
    }

    override fun find(fact: FinalFactAp): Sequence<InitialFactAp>? =
        based[fact.base]?.asSequence()
}

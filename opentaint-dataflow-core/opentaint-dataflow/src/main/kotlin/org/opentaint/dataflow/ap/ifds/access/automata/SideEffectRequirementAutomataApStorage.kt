package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.SideEffectRequirementApStorage
import java.util.concurrent.ConcurrentHashMap

class SideEffectRequirementAutomataApStorage : SideEffectRequirementApStorage {
    private val based = ConcurrentHashMap<AccessPathBase, MutableSet<InitialFactAp>>()

    override fun add(ap: InitialFactAp): InitialFactAp? {
        val basedFacts = based.computeIfAbsent(ap.base) { ConcurrentHashMap.newKeySet() }
        if (!basedFacts.add(ap)) return null
        return ap
    }

    override fun find(fact: FinalFactAp): Sequence<InitialFactAp>? =
        based[fact.base]?.asSequence()
}

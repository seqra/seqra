package org.opentaint.dataflow.jvm.ap.ifds.access.cactus

import org.opentaint.dataflow.jvm.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.TaintSinkRequirementApStorage
import java.util.concurrent.ConcurrentHashMap

class TaintSinkRequirementCactusApStorage : TaintSinkRequirementApStorage {
    private val based = ConcurrentHashMap<AccessPathBase, TaintSinkRequirementStorage>()

    override fun add(ap: InitialFactAp): InitialFactAp? {
        val baseRequirements = based.computeIfAbsent(ap.base) {
            TaintSinkRequirementStorage()
        }

        return baseRequirements.mergeAdd(ap as AccessPathWithCycles)
    }

    override fun find(fact: FinalFactAp): Sequence<InitialFactAp>? =
        based[fact.base]?.findRequirements()
}

private class TaintSinkRequirementStorage() {
    private val requirements: MutableMap<AccessPathWithCycles.AccessNode?, AccessPathWithCycles?> =
        mutableMapOf()

    fun mergeAdd(requirement: AccessPathWithCycles): AccessPathWithCycles? {
        val oldValue = requirements.getOrDefault(requirement.access, null)
        val newValue = oldValue.mergeAdd(requirement)

        requirements[requirement.access] = newValue ?: oldValue
        return newValue
    }

    fun findRequirements(): Sequence<AccessPathWithCycles> =
        requirements.values.filterNotNull().asSequence()
}


private fun AccessPathWithCycles?.mergeAdd(requirement: AccessPathWithCycles): AccessPathWithCycles? {
    if (this == null) {
        return requirement
    }

    val currentExclusion = exclusions
    val mergedExclusion = currentExclusion.union(requirement.exclusions)

    if (mergedExclusion === currentExclusion) return null

    val mergedAp = with(requirement) {
        AccessPathWithCycles(base, access, mergedExclusion)
    }

    return mergedAp
}

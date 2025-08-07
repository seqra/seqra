package org.opentaint.dataflow.jvm.ap.ifds.access.cactus

import kotlinx.collections.immutable.persistentHashMapOf
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

private class TaintSinkRequirementStorage {
    private var requirements = persistentHashMapOf<AccessPathWithCycles.AccessNode?, AccessPathWithCycles?>()

    fun mergeAdd(requirement: AccessPathWithCycles): AccessPathWithCycles? {
        val oldValue = requirements[requirement.access]
        val newValue = oldValue.mergeAdd(requirement) ?: return null

        requirements = requirements.put(requirement.access, newValue)
        return newValue
    }

    fun findRequirements(): Sequence<AccessPathWithCycles> =
        requirements.values.asSequence().filterNotNull()
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

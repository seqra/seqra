package org.opentaint.dataflow.ap.ifds.access.cactus

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.SideEffectRequirementApStorage
import java.util.concurrent.ConcurrentHashMap

class SideEffectRequirementCactusApStorage : SideEffectRequirementApStorage {
    private val based = ConcurrentHashMap<AccessPathBase, SideEffectRequirementStorage>()

    override fun add(requirements: List<InitialFactAp>): List<InitialFactAp> {
        val modifiedStorages = mutableListOf<SideEffectRequirementStorage>()

        for (requirement in requirements) {
            requirement as AccessPathWithCycles

            val baseRequirements = based.computeIfAbsent(requirement.base) {
                SideEffectRequirementStorage()
            }

            baseRequirements.mergeAdd(requirement) ?: continue

            modifiedStorages.add(baseRequirements)
        }

        val result = mutableListOf<AccessPathWithCycles>()
        modifiedStorages.forEach { it.getAndResetDelta(result) }
        return result
    }

    override fun filterTo(dst: MutableList<InitialFactAp>, fact: FinalFactAp) {
        val storage = based[fact.base] ?: return
        dst.addAll(storage.requirements.values)
    }
}

private class SideEffectRequirementStorage {
    var requirements = persistentHashMapOf<AccessPathWithCycles.AccessNode?, AccessPathWithCycles>()
    private var delta: PersistentMap<AccessPathWithCycles.AccessNode?, AccessPathWithCycles>? = null

    fun mergeAdd(requirement: AccessPathWithCycles): AccessPathWithCycles? {
        val oldValue = requirements[requirement.access]
        val newValue = oldValue.mergeAdd(requirement) ?: return null

        requirements = requirements.put(requirement.access, newValue)
        delta = (delta ?: persistentHashMapOf()).put(requirement.access, newValue)

        return newValue
    }

    fun getAndResetDelta(dst: MutableCollection<AccessPathWithCycles>) {
        delta?.values?.let { dst.addAll(it) }
        delta = null
    }
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

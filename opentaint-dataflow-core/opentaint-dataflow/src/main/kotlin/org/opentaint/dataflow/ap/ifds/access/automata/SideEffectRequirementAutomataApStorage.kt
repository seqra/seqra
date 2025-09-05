package org.opentaint.dataflow.ap.ifds.access.automata

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.SideEffectRequirementApStorage
import java.util.concurrent.ConcurrentHashMap

class SideEffectRequirementAutomataApStorage : SideEffectRequirementApStorage {
    private val based = ConcurrentHashMap<AccessPathBase, Storage>()

    override fun add(requirements: List<InitialFactAp>): List<InitialFactAp> {
        val modifiedStorages = mutableListOf<Storage>()

        for (requirement in requirements) {
            requirement as AccessGraphInitialFactAp

            val storage = based.computeIfAbsent(requirement.base) { Storage() }
            storage.mergeAdd(requirement) ?: continue
            modifiedStorages.add(storage)
        }

        val result = mutableListOf<AccessGraphInitialFactAp>()
        modifiedStorages.forEach { it.getAndResetDelta(result) }
        return result
    }

    override fun filterTo(dst: MutableList<InitialFactAp>, fact: FinalFactAp) {
        val storage = based[fact.base] ?: return
        dst.addAll(storage.requirements.values)
    }

    private class Storage {
        var requirements = persistentHashMapOf<AccessGraph, AccessGraphInitialFactAp>()
        private var delta: PersistentMap<AccessGraph, AccessGraphInitialFactAp>? = null

        fun mergeAdd(requirement: AccessGraphInitialFactAp): AccessGraphInitialFactAp? {
            val oldValue = requirements[requirement.access]
            val newValue = oldValue.mergeAdd(requirement) ?: return null

            requirements = requirements.put(requirement.access, newValue)
            delta = (delta ?: persistentHashMapOf()).put(requirement.access, newValue)

            return newValue
        }

        fun getAndResetDelta(dst: MutableCollection<AccessGraphInitialFactAp>) {
            delta?.values?.let { dst.addAll(it) }
            delta = null
        }

        private fun AccessGraphInitialFactAp?.mergeAdd(requirement: AccessGraphInitialFactAp): AccessGraphInitialFactAp? {
            if (this == null) {
                return requirement
            }

            val currentExclusion = exclusions
            val mergedExclusion = currentExclusion.union(requirement.exclusions)

            if (mergedExclusion === currentExclusion) return null

            val mergedAp = with(requirement) {
                AccessGraphInitialFactAp(base, access, mergedExclusion)
            }

            return mergedAp
        }
    }
}

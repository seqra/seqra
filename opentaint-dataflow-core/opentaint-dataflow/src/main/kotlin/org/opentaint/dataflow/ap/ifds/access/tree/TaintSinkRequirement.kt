package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.SideEffectRequirementApStorage
import java.util.concurrent.ConcurrentHashMap

class SideEffectRequirementTreeApStorage : SideEffectRequirementApStorage {
    private val based = ConcurrentHashMap<AccessPathBase, SideEffectRequirementStorage>()

    override fun add(ap: InitialFactAp): InitialFactAp? {
        val baseRequirements = based.computeIfAbsent(ap.base) {
            SideEffectRequirementStorage()
        }

        return baseRequirements.mergeAdd(ap as AccessPath)
    }

    override fun find(fact: FinalFactAp): Sequence<InitialFactAp>? =
        based[fact.base]?.findRequirements((fact as AccessTree).access)
}

private class SideEffectRequirementStorage : AccessBasedStorage<SideEffectRequirementStorage>() {
    private var requirement: AccessPath? = null

    override fun createStorage() = SideEffectRequirementStorage()

    fun mergeAdd(requirement: AccessPath): AccessPath? =
        getOrCreateNode(requirement.access).mergeAddCurrent(requirement)

    fun findRequirements(access: AccessTree.AccessNode): Sequence<AccessPath> =
        filterContains(access).mapNotNull { it.requirement }

    private fun mergeAddCurrent(requirement: AccessPath): AccessPath? {
        val current = this.requirement
        if (current == null) {
            this.requirement = requirement
            return requirement
        }

        val currentExclusion = current.exclusions
        val mergedExclusion = currentExclusion.union(requirement.exclusions)

        if (mergedExclusion === currentExclusion) return null

        val mergedAp = with(requirement) {
            AccessPath(base, access, mergedExclusion)
        }

        this.requirement = mergedAp
        return mergedAp
    }
}

package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.SideEffectRequirementApStorage
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

class SideEffectRequirementTreeApStorage(
    val apManager: TreeApManager
) : SideEffectRequirementApStorage {
    private val based = ConcurrentHashMap<AccessPathBase, SideEffectRequirementStorage>()

    override fun add(requirements: List<InitialFactAp>): List<InitialFactAp> {
        val addedNodes = IdentityHashMap<SideEffectRequirementStorage, Unit>()
        val modifiedStorageNodes = mutableListOf<SideEffectRequirementStorage>()

        for (requirement in requirements) {
            requirement as AccessPath

            val baseRequirements = based.computeIfAbsent(requirement.base) {
                SideEffectRequirementStorage(apManager)
            }

            val node = baseRequirements.mergeAdd(requirement) ?: continue

            if (addedNodes.put(node, Unit) == null) {
                modifiedStorageNodes.add(node)
            }
        }

        return modifiedStorageNodes.mapNotNull { it.requirement }
    }

    override fun filterTo(dst: MutableList<InitialFactAp>, fact: FinalFactAp) {
        val storage = based[fact.base] ?: return
        dst.addAll(storage.findRequirements((fact as AccessTree).access))
    }

    override fun collectAllRequirementsTo(dst: MutableList<InitialFactAp>) {
        based.values.forEach { storage ->
            storage.allNodes().mapNotNullTo(dst) { it.requirement }
        }
    }
}

private class SideEffectRequirementStorage(
    val apManager: TreeApManager,
) : AccessBasedStorage<SideEffectRequirementStorage>() {
    var requirement: AccessPath? = null

    override fun createStorage() = SideEffectRequirementStorage(apManager)

    fun mergeAdd(requirement: AccessPath): SideEffectRequirementStorage? =
        getOrCreateNode(requirement.access).mergeAddCurrent(requirement)

    fun findRequirements(access: AccessTree.AccessNode): Sequence<AccessPath> =
        filterContains(access).mapNotNull { it.requirement }

    private fun mergeAddCurrent(requirement: AccessPath): SideEffectRequirementStorage? {
        val current = this.requirement
        if (current == null) {
            this.requirement = requirement
            return this
        }

        val currentExclusion = current.exclusions
        val mergedExclusion = currentExclusion.union(requirement.exclusions)

        if (mergedExclusion === currentExclusion) return null

        val mergedAp = with(requirement) {
            AccessPath(apManager, base, access, mergedExclusion)
        }

        this.requirement = mergedAp
        return this
    }
}

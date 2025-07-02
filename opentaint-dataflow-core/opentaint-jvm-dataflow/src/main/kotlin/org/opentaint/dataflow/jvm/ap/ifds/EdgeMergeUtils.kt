package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.jvm.ap.ifds.AccessTree.AccessNode

object EdgeMergeUtils {

    inline fun mergeAdd(
        access: AccessNode,
        exclusion: ExclusionSet,
        allStorages: () -> Iterator<Storage>,
        saveStorage: (ExclusionSet, Storage) -> Unit,
        removeStorage: (ExclusionSet) -> Unit,
        enqueue: (ExclusionSet, AccessNode) -> Unit,
    ): Boolean {
        var accessToAdd = access
        val strictStorages = mutableListOf<Pair<ExclusionSet, Storage>>()
        var sameExStorage: Storage? = null

        for (edgeStorage in allStorages()) {
            val storedExclusion = edgeStorage.exclusion
            val exclusionUnion = storedExclusion.union(exclusion)
            if (exclusionUnion === storedExclusion) {
                if (exclusionUnion == exclusion) {
                    sameExStorage = edgeStorage
                    continue
                }

                val (remainAccessToAdd, _) = accessToAdd.subtract(edgeStorage.edges)
                accessToAdd = remainAccessToAdd ?: return false
            } else {
                strictStorages += exclusionUnion to edgeStorage
            }
        }

        if (sameExStorage != null) {
            accessToAdd = sameExStorage.addToThis(accessToAdd) ?: return false
        } else {
            val storage = Storage(exclusion, accessToAdd, accessToAdd)
            saveStorage(exclusion, storage)
        }

        for ((exclusionUnion, edgeStorage) in strictStorages) {
            val (storageIsValid, remainAccess) = edgeStorage.removeFromThis(accessToAdd)

            if (!storageIsValid) {
                removeStorage(edgeStorage.exclusion)
            }

            if (remainAccess != null) {
                enqueue(exclusionUnion, remainAccess)
            }
        }

        return true
    }

    data class Storage(
        val exclusion: ExclusionSet,
        var edges: AccessNode,
        private var currentEdgesDelta: AccessNode?
    ) {
        fun addToThis(access: AccessNode): AccessNode? {
            val (modifiedEdges, modificationDelta) = edges.mergeAddDelta(access)
            if (modificationDelta == null) return null

            edges = modifiedEdges
            currentEdgesDelta = currentEdgesDelta?.mergeAdd(modificationDelta) ?: modificationDelta
            return modificationDelta
        }

        fun removeFromThis(access: AccessNode): Pair<Boolean, AccessNode?> {
            val (modifiedEdges, remainder) = edges.subtract(access)
            if (modifiedEdges == null) {
                return false to remainder
            }

            edges = modifiedEdges
            currentEdgesDelta = currentEdgesDelta?.intersect(modifiedEdges)
            return true to remainder
        }

        fun replaceEdgesAfterRemoval(newEdges: AccessNode) {
            edges = newEdges
            currentEdgesDelta = currentEdgesDelta?.intersect(newEdges)
        }

        fun getAndResetDelta(): AccessNode? = currentEdgesDelta.also { this.currentEdgesDelta = null }
    }
}

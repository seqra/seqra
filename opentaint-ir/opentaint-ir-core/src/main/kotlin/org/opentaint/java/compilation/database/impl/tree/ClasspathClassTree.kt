package org.opentaint.java.compilation.database.impl.tree

import org.opentaint.java.compilation.database.impl.LocationsRegistrySnapshot

/**
 * ClassTree view limited for by number of `locations`
 */
class ClasspathClassTree(
    private val classTree: ClassTree,
    locationsRegistrySnapshot: LocationsRegistrySnapshot
) {

    private val locationHashes = locationsRegistrySnapshot.locations.map { it.id }.toHashSet()
    fun firstClassOrNull(fullName: String): ClassNode? {
        return classTree.firstClassNodeOrNull(fullName) {
            locationHashes.contains(it)
        }
    }

    fun findSubTypesOf(fullName: String): List<ClassNode> {
        return firstClassOrNull(fullName)?.subTypes.orEmpty().filter {
            locationHashes.contains(it.location.id)
        }
    }
}
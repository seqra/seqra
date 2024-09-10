package org.opentaint.java.compilation.database.impl.tree

import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.impl.LocationsRegistrySnapshot

/**
 * ClassTree view limited by number of `locations`
 */
class ClasspathClassTree(
    private val classTree: ClassTree,
    locations: List<ByteCodeLocation>
) {

    constructor(classTree: ClassTree, locationsRegistrySnapshot: LocationsRegistrySnapshot) : this(
        classTree,
        locationsRegistrySnapshot.locations
    )

    private val locationIds: Set<String> = locations.map { it.id }.toHashSet()


    fun firstClassOrNull(fullName: String): ClassNode? {
        return classTree.firstClassNodeOrNull(fullName) {
            locationIds.contains(it)
        }
    }

    fun findSubTypesOf(fullName: String): List<ClassNode> {
        return firstClassOrNull(fullName)?.subTypes.orEmpty().filter {
            locationIds.contains(it.location.id)
        }
    }
}
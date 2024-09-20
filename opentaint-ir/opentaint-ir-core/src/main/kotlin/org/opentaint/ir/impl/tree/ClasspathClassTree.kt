package org.opentaint.ir.impl.tree

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.impl.LocationsRegistrySnapshot

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
}
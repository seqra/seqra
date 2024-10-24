package org.opentaint.ir.impl.vfs

import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.impl.LocationsRegistrySnapshot

/**
 * ClassTree view limited by number of `locations`
 */
class ClasspathClassTree(
    private val globalClassVFS: GlobalClassesVfs,
    locations: List<JIRByteCodeLocation>
) {

    constructor(globalClassVFS: GlobalClassesVfs, locationsRegistrySnapshot: LocationsRegistrySnapshot) : this(
        globalClassVFS,
        locationsRegistrySnapshot.locations
    )

    private val locationIds: Set<String> = locations.map { it.path }.toHashSet()

    fun firstClassOrNull(fullName: String): ClassVfsItem? {
        return globalClassVFS.firstClassNodeOrNull(fullName) {
            locationIds.contains(it)
        }
    }
}
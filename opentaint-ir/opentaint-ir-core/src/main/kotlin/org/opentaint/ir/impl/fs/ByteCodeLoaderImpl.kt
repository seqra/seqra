package org.opentaint.ir.impl.fs

import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.LocationType
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.vfs.LibraryClassVfs

/**
 * load sync part into the tree and returns lambda that will do async part
 */
fun RegisteredLocation.load(): LibraryClassVfs {
    val libraryTree = LibraryClassVfs(this)
    jirLocation.classes?.forEach {
        val source = ClassSourceImpl(this, it.key, it.value)
        libraryTree.addClass(source)
    }
    return libraryTree
}

/**
 * limits scope for search base on location. That means that sometimes there is no need to search for subclasses of
 * library class inside java runtime.
 *
 * @param location target location
 */
fun Collection<JIRByteCodeLocation>.relevantLocations(location: JIRByteCodeLocation?): Collection<JIRByteCodeLocation> {
    if (location?.type != LocationType.APP) {
        return this
    }
    return filter { it.type == LocationType.APP }
}
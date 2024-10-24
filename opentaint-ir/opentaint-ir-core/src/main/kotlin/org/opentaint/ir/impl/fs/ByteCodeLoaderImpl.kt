package org.opentaint.ir.impl.fs

import org.opentaint.ir.api.ClassLoadingContainer
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.LocationType
import org.opentaint.ir.impl.vfs.LibraryClassVfs
import java.io.InputStream

class ClassLoadingContainerImpl(
    override val classes: Map<String, InputStream>,
    val onClose: () -> Unit = {}
) : ClassLoadingContainer {

    override fun close() {
        onClose()
    }
}

/**
 * load sync part into the tree and returns lambda that will do async part
 */
suspend fun JIRByteCodeLocation.load(): LibraryClassVfs {
    val libraryTree = LibraryClassVfs(this)
    val container = classes()
    container?.classes?.forEach {
        val source = ClassByteCodeSource(this, it.key, it.value.readBytes())
        libraryTree.addClass(source)
    }
    container?.close()
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
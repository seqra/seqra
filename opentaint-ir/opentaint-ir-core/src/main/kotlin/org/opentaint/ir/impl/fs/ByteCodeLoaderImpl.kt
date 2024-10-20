package org.opentaint.ir.impl.fs

import org.opentaint.ir.api.ByteCodeLoader
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClassLoadingContainer
import org.opentaint.ir.api.LocationScope
import org.opentaint.ir.impl.tree.LibraryClassTree
import java.io.InputStream

class ByteCodeLoaderImpl(
    override val location: ByteCodeLocation,
    private val sync: ClassLoadingContainer
) : ByteCodeLoader {

    constructor(location: ByteCodeLocation, sync: Map<String, InputStream>) :
            this(location, ClassLoadingContainerImpl(sync))

    override suspend fun allResources() = sync

    override suspend fun asyncResources(): suspend () -> ClassLoadingContainer? = { null }

}

class ClassLoadingContainerImpl(
    override val classesToLoad: Map<String, InputStream>,
    val onClose: () -> Unit = {}
) : ClassLoadingContainer {

    override fun close() {
        onClose()
    }
}


/**
 * load sync part into the tree and returns lambda that will do async part
 */
suspend fun ByteCodeLoader.load(): LibraryClassTree {
    val libraryTree = LibraryClassTree(location)
    val sync = allResources()
    sync.classesToLoad.forEach {
        val source = ClassByteCodeSource(location, it.key, it.value.readBytes())
        libraryTree.addClass(source)
    }
    sync.close()
    return libraryTree
}

/**
 * limits scope for search base on location. That means that sometimes there is no need to search for subclasses of
 * library class inside java runtime.
 *
 * @param location target location
 */
fun Collection<ByteCodeLocation>.relevantLocations(location: ByteCodeLocation?): Collection<ByteCodeLocation> {
    if (location?.scope != LocationScope.APP) {
        return this
    }
    return filter { it.scope == LocationScope.APP }
}
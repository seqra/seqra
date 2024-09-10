package org.opentaint.java.compilation.database.impl.fs

import org.opentaint.java.compilation.database.api.ByteCodeLoader
import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.api.LoadingContainer
import org.opentaint.java.compilation.database.impl.tree.ClassTree
import java.io.InputStream

class ByteCodeLoaderImpl(
    override val location: ByteCodeLocation,
    private val sync: LoadingContainer,
    private val async: suspend () -> LoadingContainer?
) : ByteCodeLoader {

    constructor(
        location: ByteCodeLocation,
        sync: Map<String, InputStream?>,
        async: suspend () -> Map<String, InputStream?>
    ) : this(location, LoadingContainerImpl(sync), {
        LoadingContainerImpl(async())
    })

    override suspend fun allResources() = sync

    override suspend fun asyncResources() = async

}

class LoadingContainerImpl(
    override val classes: Map<String, InputStream?>,
    val onClose: () -> Unit = {}
) : LoadingContainer {

    override fun close() {
        onClose()
    }
}


suspend fun ByteCodeLoader.load(classTree: ClassTree): suspend () -> Unit {
    val sync = allResources()
    sync.classes.forEach {
        ClassByteCodeSource(location = location, it.key).also { source ->
            val node = classTree.addClass(source)
            it.value?.let {
                source.preLoad(it)
                classTree.notifyOnMetaLoaded(node)
            }
        }
    }
    sync.close()
    return {
        val async = asyncResources()()
        async?.classes?.forEach { entry ->
            val node = classTree.firstClassNodeOrNull(entry.key)
            val stream = entry.value
            if (stream != null && node != null) {
                node.source.preLoad(stream)
                classTree.notifyOnMetaLoaded(node)
            }
        }
        async?.close()
    }
}

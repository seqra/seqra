package org.opentaint.java.compilation.database.impl.fs

import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.impl.tree.ClassTree
import java.io.InputStream

interface ByteCodeLoader {
    suspend fun load(classTree: ClassTree): suspend () -> Unit
}

class ByteCodeLoaderImpl(
    private val location: ByteCodeLocation,
    private val sync: LoadingPortion,
    private val async: suspend () -> LoadingPortion?
) : ByteCodeLoader {

    constructor(
        location: ByteCodeLocation,
        sync: Map<String, InputStream?>,
        async: suspend () -> Map<String, InputStream?>
    ) : this(location, LoadingPortion(sync), {
        LoadingPortion(async())
    })


    override suspend fun load(classTree: ClassTree): suspend () -> Unit {
        sync.classes.forEach {
            ClassByteCodeSource(location.apiLevel, location = location, it.key).also { source ->
                val node = classTree.addClass(source)
                it.value?.let {
                    source.preLoad(it)
                    classTree.notifyOnMetaLoaded(node)
                }
            }
        }
        sync.onPortionFinish()
        return {
            val portion = async()
            portion?.classes?.forEach { entry ->
                val node = classTree.firstClassNodeOrNull(entry.key)
                val stream = entry.value
                if (stream != null && node != null) {
                    node.source.preLoad(stream)
                    classTree.notifyOnMetaLoaded(node)
                }
            }
            portion?.onPortionFinish?.invoke()
        }
    }

}

class LoadingPortion(
    val classes: Map<String, InputStream?>,
    val onPortionFinish: () -> Unit = {}
)
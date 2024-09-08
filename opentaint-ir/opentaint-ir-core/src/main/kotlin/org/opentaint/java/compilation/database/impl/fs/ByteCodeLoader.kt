package org.opentaint.java.compilation.database.impl.fs

import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.impl.tree.ClassTree
import java.io.InputStream

class ByteCodeLoader(
    private val location: ByteCodeLocation,
    private val loadSync: List<Pair<String, InputStream>>,
    private val loadAsync: List<Pair<String, () -> InputStream>>
) {

    suspend fun load(classTree: ClassTree): suspend () -> Unit {
        loadSync.forEach {
            ClassByteCodeSource(location.apiLevel, location = location, it.first).also { source ->
                source.preLoad(it.second)
                val node = classTree.addClass(source)
                classTree.notifyOnMetaLoaded(node)
            }
        }
        val asyncSources = loadAsync.map {
            val source = ClassByteCodeSource(location.apiLevel, location = location, it.first)
            val node = classTree.addClass(source)
            node to it.second
        }
        return {
            asyncSources.forEach {
                it.first.source.preLoad(it.second())
                classTree.notifyOnMetaLoaded(it.first)
            }
        }
    }

}
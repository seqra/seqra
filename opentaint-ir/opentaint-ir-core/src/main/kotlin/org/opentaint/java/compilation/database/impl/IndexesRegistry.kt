package org.opentaint.java.compilation.database.impl

import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.api.ByteCodeLocationIndex
import org.opentaint.java.compilation.database.api.IndexInstaller
import org.opentaint.java.compilation.database.impl.index.SubClassesIndex
import org.opentaint.java.compilation.database.impl.index.index
import org.opentaint.java.compilation.database.impl.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap

class IndexesRegistry(private val installers: List<IndexInstaller<*, *>>) {

    private val indexes = ConcurrentHashMap<ByteCodeLocation, ConcurrentHashMap<String, ByteCodeLocationIndex<*>>>()

    suspend fun index(location: ByteCodeLocation, classes: Collection<ClassNode>) {
        installers.forEach { installer ->
            val index = location.index(classes) {
                installer.indexBuilderOf(it)
            }
            val existedIndexes = indexes.getOrPut(location) {
                ConcurrentHashMap()
            }
            existedIndexes[index.key] = index
        }
    }

    fun removeIndexes(location: ByteCodeLocation) {
        indexes.remove(location)
    }

    fun subClassesIndex(location: ByteCodeLocation): ByteCodeLocationIndex<String>? {
        return indexes[location]?.get(SubClassesIndex.KEY) as? ByteCodeLocationIndex<String>
    }

    fun <T> findIndex(key: String, location: ByteCodeLocation): ByteCodeLocationIndex<T>? {
        return indexes[location]?.get(key) as? ByteCodeLocationIndex<T>
    }

}

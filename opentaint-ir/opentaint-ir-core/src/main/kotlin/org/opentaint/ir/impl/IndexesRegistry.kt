package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ByteCodeLocationIndex
import org.opentaint.ir.api.IndexInstaller
import org.opentaint.ir.impl.index.index
import org.opentaint.ir.impl.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap

class IndexesRegistry(private val installers: List<IndexInstaller<*, *>>) {

    private val indexes = ConcurrentHashMap<ByteCodeLocation, ConcurrentHashMap<String, ByteCodeLocationIndex<*>>>()

    suspend fun index(location: ByteCodeLocation, classes: Collection<ClassNode>) {
        installers.forEach { installer ->
            val index = location.index(classes) {
                installer.newBuilder()
            }
            val existedIndexes = indexes.getOrPut(location) {
                ConcurrentHashMap()
            }
            existedIndexes[installer.key] = index
        }
    }

    fun removeIndexes(location: ByteCodeLocation) {
        indexes.remove(location)
    }

    fun <T> findIndex(key: String, location: ByteCodeLocation): ByteCodeLocationIndex<T>? {
        return indexes[location]?.get(key) as? ByteCodeLocationIndex<T>
    }

}

package org.opentaint.java.compilation.database.impl

import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.impl.index.*
import org.opentaint.java.compilation.database.impl.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap

typealias IndexBuilder<T> = (ByteCodeLocation) -> ByteCodeLocationIndexBuilder<T>

class IndexesRegistry {
    private val indexes = ConcurrentHashMap<ByteCodeLocation, ConcurrentHashMap<String, ByteCodeLocationIndex<*>>>()
    private val subtypesIndexBuilder: IndexBuilder<String> = { SubClassesIndexBuilder(it) }

    suspend fun index(location: ByteCodeLocation, classes: Collection<ClassNode>) {
        val subtypesIndex = location.index(classes, subtypesIndexBuilder)
        val existedIndexes = indexes.getOrPut(location) {
            ConcurrentHashMap()
        }
        existedIndexes[subtypesIndex.key] = subtypesIndex
    }

    fun removeIndexes(location: ByteCodeLocation) {
        indexes.remove(location)
    }

//    fun snapshot(locations: List<ByteCodeLocation>): Map<ByteCodeLocation, Map<String, ByteCodeLocationIndex<*>>> {
//        return indexes.filterKeys {
//            locations.contains(it)
//        }
//    }

    fun subClassesIndex(location: ByteCodeLocation): ByteCodeLocationIndex<String>? {
        return indexes[location]?.get(SubClassesIndex.KEY) as? ByteCodeLocationIndex<String>
    }


}

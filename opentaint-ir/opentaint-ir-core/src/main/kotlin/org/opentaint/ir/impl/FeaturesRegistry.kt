package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ByteCodeLocationIndex
import org.opentaint.ir.api.Feature
import org.opentaint.ir.impl.index.index
import org.opentaint.ir.impl.tree.ClassNode
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class FeaturesRegistry(private val features: List<Feature<*, *>>) : Closeable {

    private val indexes = ConcurrentHashMap<ByteCodeLocation, ConcurrentHashMap<String, ByteCodeLocationIndex<*>>>()

    fun <T, INDEX : ByteCodeLocationIndex<T>> append(
        location: ByteCodeLocation,
        feature: Feature<T, INDEX>,
        index: INDEX
    ) {
        location.indexes.put(feature.key, index)
    }

    suspend fun index(location: ByteCodeLocation, classes: Collection<ClassNode>) {
        val existedIndexes = location.indexes
        val builders = features.associate { it.key to it.newBuilder() }
        classes.forEach { node ->
            features.forEach { installer ->
                val builder = builders[installer.key]
                if (builder != null) {
                    index(node, builder)
                }
            }
            node.onAfterIndexing()
        }
        features.forEach { installer ->
            val index = builders[installer.key]?.build(location)
            if (index != null) {
                existedIndexes.put(installer.key, index)
            }
        }
    }

    private val ByteCodeLocation.indexes: ConcurrentHashMap<String, ByteCodeLocationIndex<*>>
        get() {
            return this@FeaturesRegistry.indexes.getOrPut(this) {
                ConcurrentHashMap()
            }
        }

    fun removeIndexes(location: ByteCodeLocation) {
        indexes.remove(location)
    }

    fun <T> findIndex(key: String, location: ByteCodeLocation): ByteCodeLocationIndex<T>? {
        return indexes[location]?.get(key) as? ByteCodeLocationIndex<T>
    }

    override fun close() {
        indexes.clear()
    }
}

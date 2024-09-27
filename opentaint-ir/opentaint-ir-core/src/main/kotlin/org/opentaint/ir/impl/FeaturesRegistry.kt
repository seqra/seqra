package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ByteCodeLocationIndex
import org.opentaint.ir.api.Feature
import org.opentaint.ir.impl.index.index
import org.opentaint.ir.impl.storage.PersistentEnvironment
import org.opentaint.ir.impl.tree.ClassNode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class FeaturesRegistry(
    private val persistence: PersistentEnvironment? = null,
    private val features: List<Feature<*, *>>,
    private val indexes: ConcurrentHashMap<ByteCodeLocation, ConcurrentHashMap<String, ByteCodeLocationIndex<*>>> = ConcurrentHashMap()
) : Closeable {

    fun <T, INDEX : ByteCodeLocationIndex<T>> append(
        location: ByteCodeLocation,
        feature: Feature<T, INDEX>,
        index: INDEX
    ) {
        location.indexes[feature.key] = index
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
        features.forEach { feature ->
            feature.index(location, existedIndexes)
        }
    }

    private fun <T, INDEX: ByteCodeLocationIndex<T>> Feature<T, INDEX>.index(
        location: ByteCodeLocation,
        existedIndexes: ConcurrentHashMap<String, ByteCodeLocationIndex<*>>
    ) {
        val index = newBuilder().build(location)
        existedIndexes[key] = index
        val entity = persistence?.locationStore?.findOrNew(location)
        if (entity != null) {
            val out = ByteArrayOutputStream()
            serialize(index, out)
            persistence?.transactional {
                entity.index(key, ByteArrayInputStream(out.toByteArray()))
            }
            persistence?.globalIds?.sync()
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

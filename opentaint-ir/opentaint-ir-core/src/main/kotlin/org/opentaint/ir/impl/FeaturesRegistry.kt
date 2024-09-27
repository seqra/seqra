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
        features.forEach { feature ->
            feature.index(location, classes)
        }
        classes.forEach { node ->
            node.onAfterIndexing()
        }
    }

    private suspend fun <T, INDEX : ByteCodeLocationIndex<T>> Feature<T, INDEX>.index(
        location: ByteCodeLocation,
        classes: Collection<ClassNode>
    ) {
        val builder = newBuilder()
        classes.forEach { node ->
            index(node, builder)
            node.onAfterIndexing()
        }
        val index = builder.build(location)
        location.indexes[key] = index
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

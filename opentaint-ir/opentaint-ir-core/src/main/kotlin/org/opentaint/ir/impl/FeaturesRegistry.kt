package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.Feature
import org.opentaint.ir.api.Index
import org.opentaint.ir.api.IndexRequest
import org.opentaint.ir.impl.index.index
import org.opentaint.ir.impl.storage.PersistentEnvironment
import org.opentaint.ir.impl.tree.ClassNode
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class FeaturesRegistry(
    private val persistence: PersistentEnvironment? = null,
    val features: List<Feature<*, *>>
) : Closeable {

    private val indexes = ConcurrentHashMap<String, Index<*, *>>()

    fun <T, INDEX : Index<T, *>> append(feature: Feature<T, INDEX>, index: INDEX) {
        indexes[feature.key] = index
    }

    suspend fun index(location: ByteCodeLocation, classes: Collection<ClassNode>) {
        features.forEach { feature ->
            feature.index(location, classes)
        }
    }

    private suspend fun <T, INDEX : Index<T, *>> Feature<T, INDEX>.index(
        location: ByteCodeLocation,
        classes: Collection<ClassNode>
    ) {
        val builder = newBuilder(location)
        classes.forEach { node ->
            index(node, builder)
        }
        val index = builder.build()
        indexes[key] = index

        val store = persistence?.locationStore
        if (store != null) {
            persistentOperation {
                persist(index)
            }
        }
    }

    fun <T, REQ: IndexRequest> findIndex(key: String): Index<T, REQ>? {
        return indexes[key] as? Index<T, REQ>?
    }

    override fun close() {
        indexes.clear()
    }
}

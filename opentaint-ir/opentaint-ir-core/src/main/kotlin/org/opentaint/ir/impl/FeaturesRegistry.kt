package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.Feature
import org.opentaint.ir.api.GlobalIdsStore
import org.opentaint.ir.api.Index
import org.opentaint.ir.impl.index.index
import org.opentaint.ir.impl.storage.PersistentEnvironment
import org.opentaint.ir.impl.tree.ClassNode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class FeaturesRegistry(
    private val persistence: PersistentEnvironment? = null,
    private val globalIdsStore: GlobalIdsStore,
    val features: List<Feature<*, *>>
) : Closeable {

    private val indexes: ConcurrentHashMap<String, Index<*>> = ConcurrentHashMap()

    fun <T, INDEX : Index<T>> append(feature: Feature<T, INDEX>, index: INDEX) {
        indexes[feature.key] = index
    }

    suspend fun index(location: ByteCodeLocation, classes: Collection<ClassNode>) {
        features.forEach { feature ->
            feature.index(location, classes)
        }
        classes.forEach { node ->
            node.onAfterIndexing()
        }
    }

    private suspend fun <T, INDEX : Index<T>> Feature<T, INDEX>.index(
        location: ByteCodeLocation,
        classes: Collection<ClassNode>
    ) {
        val builder = newBuilder(globalIdsStore)
        classes.forEach { node ->
            index(node, builder)
        }
        val index = builder.build(location)
        indexes[key] = index

        val entity = sql {
            persistence?.locationStore?.findOrNewTx(location)
        }
        if (entity != null) {
            val out = ByteArrayOutputStream()
            serialize(index, out)
            sql {
                entity.index(key, ByteArrayInputStream(out.toByteArray()))
            }
        }
    }

    fun <T> findIndex(key: String): Index<T>? {
        return indexes[key] as? Index<T>?
    }

    override fun close() {
        indexes.clear()
    }
}

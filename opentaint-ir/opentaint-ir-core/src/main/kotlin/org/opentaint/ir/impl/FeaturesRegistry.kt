package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.Feature
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRDBFeature
import org.opentaint.ir.impl.index.index
import org.opentaint.ir.impl.tree.ClassNode
import java.io.Closeable

class FeaturesRegistry(val features: List<Feature<*, *>>) : Closeable {

    lateinit var jirdbFeatures: List<JIRDBFeature<*, *>>

    fun bind(jirdb: JIRDB) {
        jirdbFeatures = features.map { it.featureOf(jirdb) }
    }

    suspend fun index(location: ByteCodeLocation, classes: Collection<ClassNode>) {
        jirdbFeatures.forEach { feature ->
            feature.index(location, classes)
        }
    }

    private suspend fun <REQ, RES> JIRDBFeature<RES, REQ>.index(
        location: ByteCodeLocation,
        classes: Collection<ClassNode>
    ) {
        val indexer = newIndexer(location)
        classes.forEach { node ->
            index(node, indexer)
        }
        persistence?.jirdbPersistence?.write {
            indexer.flush()
        }
    }

    fun <REQ, RES> findIndex(key: String): JIRDBFeature<RES, REQ>? {
        return jirdbFeatures.firstOrNull { it.key == key } as? JIRDBFeature<RES, REQ>?
    }

    fun onLocationRemove(location: ByteCodeLocation) {
        jirdbFeatures.forEach {
            it.onLocationRemoved(location)
        }
    }

    override fun close() {
    }
}

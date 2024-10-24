package org.opentaint.ir.impl

import org.opentaint.ir.api.Feature
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRDBFeature
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.index.index
import org.opentaint.ir.impl.vfs.ClassVfsItem
import java.io.Closeable

class FeaturesRegistry(private val features: List<Feature<*, *>>) : Closeable {

    lateinit var jirdbFeatures: List<JIRDBFeature<*, *>>

    fun bind(jirdb: JIRDB) {
        jirdbFeatures = features.map { it.featureOf(jirdb) }
    }

    suspend fun index(location: RegisteredLocation, classes: Collection<ClassVfsItem>) {
        jirdbFeatures.forEach { feature ->
            feature.index(location, classes)
        }
    }

    private suspend fun <REQ, RES> JIRDBFeature<RES, REQ>.index(
        location: RegisteredLocation,
        classes: Collection<ClassVfsItem>
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

    fun onLocationRemove(location: RegisteredLocation) {
        jirdbFeatures.forEach {
            it.onLocationRemoved(location)
        }
    }

    override fun close() {
    }
}

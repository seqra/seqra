package org.opentaint.ir.impl

import org.opentaint.ir.api.Feature
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.index.index
import org.opentaint.ir.impl.vfs.ClassVfsItem
import java.io.Closeable

class FeaturesRegistry(private val features: List<Feature<*, *>>) : Closeable {

    private lateinit var jirdb: JIRDB

    fun bind(jirdb: JIRDB) {
        this.jirdb = jirdb
    }

    suspend fun index(location: RegisteredLocation, classes: Collection<ClassVfsItem>) {
        features.forEach { feature ->
            feature.index(location, classes)
        }
    }

    private suspend fun <REQ, RES> Feature<RES, REQ>.index(
        location: RegisteredLocation,
        classes: Collection<ClassVfsItem>
    ) {
        val indexer = newIndexer(jirdb, location)
        classes.forEach { index(it, indexer) }
        jirdb.persistence.write {
            indexer.flush()
        }
    }

    fun onLocationRemove(location: RegisteredLocation) {
        features.forEach {
            it.onRemoved(jirdb, location)
        }
    }

    fun forEach(action: (JIRDB, Feature<*, *>) -> Unit) {
        features.forEach { action(jirdb, it) }
    }

    override fun close() {
    }

}

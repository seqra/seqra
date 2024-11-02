package org.opentaint.ir.impl

import org.opentaint.ir.api.Feature
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRSignal
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

    fun broadcast(signal: JIRInternalSignal) {
        features.forEach { it.onSignal(signal.asJcSignal(jirdb)) }
    }

    fun forEach(action: (JIRDB, Feature<*, *>) -> Unit) {
        features.forEach { action(jirdb, it) }
    }

    override fun close() {
    }

}


sealed class JIRInternalSignal {

    class BeforeIndexing(val clearOnStart: Boolean) : JIRInternalSignal()
    object AfterIndexing : JIRInternalSignal()
    class LocationRemoved(val location: RegisteredLocation) : JIRInternalSignal()

    fun asJcSignal(jirdb: JIRDB): JIRSignal {
        return when (this) {
            is BeforeIndexing -> JIRSignal.BeforeIndexing(jirdb, clearOnStart)
            is AfterIndexing -> JIRSignal.AfterIndexing(jirdb)
            is LocationRemoved -> JIRSignal.LocationRemoved(jirdb, location)
        }
    }

}

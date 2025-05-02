package org.opentaint.ir.impl

import kotlinx.collections.immutable.toPersistentList
import org.opentaint.ir.api.jvm.ByteCodeIndexer
import org.opentaint.ir.api.jvm.ClassSource
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRFeature
import org.opentaint.ir.api.jvm.JIRSignal
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.impl.fs.fullAsmNode
import java.io.Closeable

class FeaturesRegistry(features: List<JIRFeature<*, *>>) : Closeable {

    val features = features.toPersistentList()

    private lateinit var jIRdb: JIRDatabase

    fun bind(jIRdb: JIRDatabase) {
        this.jIRdb = jIRdb
    }

    fun index(location: RegisteredLocation, classes: List<ClassSource>) {
        features.forEach { feature ->
            feature.index(location, classes)
        }
    }

    private fun <REQ, RES> JIRFeature<RES, REQ>.index(
        location: RegisteredLocation,
        classes: List<ClassSource>
    ) {
        val indexer = newIndexer(jIRdb, location)
        classes.forEach { index(it, indexer) }
        jIRdb.persistence.write {
            indexer.flush(it)
        }
    }

    fun broadcast(signal: JIRInternalSignal) {
        features.forEach { it.onSignal(signal.asJIRSignal(jIRdb)) }
    }

    override fun close() {
    }

    private fun index(source: ClassSource, builder: ByteCodeIndexer) {
        builder.index(source.fullAsmNode)
    }
}

sealed class JIRInternalSignal {

    class BeforeIndexing(val clearOnStart: Boolean) : JIRInternalSignal()
    object AfterIndexing : JIRInternalSignal()
    object Drop : JIRInternalSignal()
    object Closed : JIRInternalSignal()
    class LocationRemoved(val location: RegisteredLocation) : JIRInternalSignal()

    fun asJIRSignal(jIRdb: JIRDatabase): JIRSignal {
        return when (this) {
            is BeforeIndexing -> JIRSignal.BeforeIndexing(jIRdb, clearOnStart)
            is AfterIndexing -> JIRSignal.AfterIndexing(jIRdb)
            is LocationRemoved -> JIRSignal.LocationRemoved(jIRdb, location)
            is Drop -> JIRSignal.Drop(jIRdb)
            is Closed -> JIRSignal.Closed(jIRdb)
        }
    }

}

package org.opentaint.ir.impl

import org.opentaint.ir.api.ByteCodeIndexer
import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRFeature
import org.opentaint.ir.api.JIRSignal
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.fs.fullAsmNode
import java.io.Closeable

class FeaturesRegistry(private val features: List<JIRFeature<*, *>>) : Closeable {

    private lateinit var jirdb: JIRDB

    fun bind(jirdb: JIRDB) {
        this.jirdb = jirdb
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
        val indexer = newIndexer(jirdb, location)
        classes.forEach { index(it, indexer) }
        jirdb.persistence.write {
            indexer.flush(it)
        }
    }

    fun broadcast(signal: JIRInternalSignal) {
        features.forEach { it.onSignal(signal.asJcSignal(jirdb)) }
    }

    fun forEach(action: (JIRDB, JIRFeature<*, *>) -> Unit) {
        features.forEach { action(jirdb, it) }
    }

    override fun close() {
    }

    private fun index(source: ClassSource, builder: ByteCodeIndexer) {
        val asmNode = source.fullAsmNode
        builder.index(asmNode)
        asmNode.methods.forEach {
            builder.index(asmNode, it)
        }
    }
}

sealed class JIRInternalSignal {

    class BeforeIndexing(val clearOnStart: Boolean) : JIRInternalSignal()
    object AfterIndexing : JIRInternalSignal()
    object Drop : JIRInternalSignal()
    class LocationRemoved(val location: RegisteredLocation) : JIRInternalSignal()

    fun asJcSignal(jirdb: JIRDB): JIRSignal {
        return when (this) {
            is BeforeIndexing -> JIRSignal.BeforeIndexing(jirdb, clearOnStart)
            is AfterIndexing -> JIRSignal.AfterIndexing(jirdb)
            is LocationRemoved -> JIRSignal.LocationRemoved(jirdb, location)
            is Drop -> JIRSignal.Drop(jirdb)
        }
    }

}

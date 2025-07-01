package org.opentaint.ir.impl

import kotlinx.collections.immutable.toPersistentList
import org.opentaint.ir.api.jvm.*
import org.opentaint.ir.impl.fs.fullAsmNode
import org.objectweb.asm.tree.ClassNode
import java.io.Closeable

class FeaturesRegistry(features: List<JIRFeature<*, *>>) : Closeable {

    val features = features.toPersistentList()

    private lateinit var jIRdb: JIRDatabase

    fun bind(jIRdb: JIRDatabase) {
        this.jIRdb = jIRdb
    }

    fun index(location: RegisteredLocation, classes: List<ClassSource>) {
        val classNodes = hashMapOf<ClassSource, ClassNode>()
        features.forEach { feature ->
            feature.index(location, classes) { source ->
                classNodes.getOrPut(source) {
                    source.fullAsmNode
                }
            }
        }
    }

    private fun <REQ, RES> JIRFeature<RES, REQ>.index(
        location: RegisteredLocation,
        classes: List<ClassSource>,
        classNodeProvider: (ClassSource) -> ClassNode
    ) {
        val indexer = newIndexer(jIRdb, location)
        classes.forEach { index(it, indexer, classNodeProvider) }
        jIRdb.persistence.write {
            indexer.flush(it)
        }
    }

    fun broadcast(signal: JIRInternalSignal) {
        features.forEach { it.onSignal(signal.asJIRSignal(jIRdb)) }
    }

    override fun close() {
    }

    private fun index(source: ClassSource, builder: ByteCodeIndexer, classNodeProvider: (ClassSource) -> ClassNode) {
        builder.index(classNodeProvider(source))
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

package org.opentaint.ir.api.jvm

import org.jooq.DSLContext
import org.objectweb.asm.tree.ClassNode

/** index builder */
interface ByteCodeIndexer {

    fun index(classNode: ClassNode)

    fun flush(jooq: DSLContext)
}

interface JIRFeature<REQ, RES> {

    suspend fun query(classpath: JIRProject, req: REQ): Sequence<RES>

    fun newIndexer(jIRdb: JIRDatabase, location: RegisteredLocation): ByteCodeIndexer

    fun onSignal(signal: JIRSignal)

}

sealed class JIRSignal(val jIRdb: JIRDatabase) {

    /** can be used for creating persistence scheme */
    class BeforeIndexing(jIRdb: JIRDatabase, val clearOnStart: Boolean) : JIRSignal(jIRdb)

    /** can be used to create persistence indexes after data batch upload */
    class AfterIndexing(jIRdb: JIRDatabase) : JIRSignal(jIRdb)

    /** can be used for cleanup index data when location is removed */
    class LocationRemoved(jIRdb: JIRDatabase, val location: RegisteredLocation) : JIRSignal(jIRdb)

    /**
     * rebuild all
     */
    class Drop(jIRdb: JIRDatabase) : JIRSignal(jIRdb)

    /**
     * database is closed
     */
    class Closed(jIRdb: JIRDatabase) : JIRSignal(jIRdb)

}

suspend fun <REQ, RES> JIRProject.query(feature: JIRFeature<REQ, RES>, req: REQ): Sequence<RES> {
    return feature.query(this, req)
}
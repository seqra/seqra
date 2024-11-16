package org.opentaint.ir.impl.features

import org.jooq.DSLContext
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.opentaint.ir.api.ByteCodeIndexer
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRDBPersistence
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRFeature
import org.opentaint.ir.api.JIRSignal
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.fs.ClassSourceImpl
import org.opentaint.ir.impl.fs.className
import org.opentaint.ir.impl.storage.BatchedSequence
import org.opentaint.ir.impl.storage.eqOrNull
import org.opentaint.ir.impl.storage.executeQueries
import org.opentaint.ir.impl.storage.jooq.tables.references.CALLS
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.opentaint.ir.impl.storage.longHash
import org.opentaint.ir.impl.storage.runBatch
import org.opentaint.ir.impl.storage.setNullableLong
import org.opentaint.ir.impl.vfs.LazyPersistentByteCodeLocation


class UsagesIndexer(private val persistence: JIRDBPersistence, private val location: RegisteredLocation) :
    ByteCodeIndexer {

    // callee_class -> (callee_name, callee_desc, opcode) -> caller
    private val usages = hashMapOf<String, HashMap<Triple<String, String?, Int>, HashMap<String, HashSet<Byte>>>>()
    private val interner = persistence.newSymbolInterner()

    override fun index(classNode: ClassNode) {
        val callerClass = Type.getObjectType(classNode.name).className
        var callerMethodOffset: Byte = 0
        classNode.methods.forEach { methodNode ->
            methodNode.instructions.forEach {
                var key: Triple<String, String?, Int>? = null
                var callee: String? = null
                when (it) {
                    is FieldInsnNode -> {
                        callee = it.owner
                        key = Triple(it.name, null, it.opcode)
                    }

                    is MethodInsnNode -> {
                        callee = it.owner
                        key = Triple(it.name, it.desc, it.opcode)
                    }
                }
                if (key != null && callee != null) {
                    callee.symbolId
                    key.first.symbolId
                    usages.getOrPut(callee) { hashMapOf() }
                        .getOrPut(key) { hashMapOf() }
                        .getOrPut(callerClass) { hashSetOf() }
                        .add(callerMethodOffset)
                }
            }
            callerMethodOffset++
        }
    }

    override fun flush(jooq: DSLContext) {
        jooq.connection { conn ->
            usages.keys.forEach { it.className.symbolId }
            interner.flush(conn)
            conn.runBatch(CALLS) {
                usages.forEach { (calleeClass, calleeEntry) ->
                    calleeEntry.forEach { (info, callers) ->
                        val (calleeName, calleeDesc, opcode) = info
                        callers.forEach { (caller, offsets) ->
                            setLong(1, calleeClass.className.existedSymbolId)
                            setLong(2, calleeName.existedSymbolId)
                            setNullableLong(3, calleeDesc?.longHash)
                            setInt(4, opcode)
                            setLong(5, caller.existedSymbolId)
                            setBytes(6, offsets.toByteArray())
                            setLong(7, location.id)
                            addBatch()
                        }
                    }
                }
            }
        }
    }

    private inline val String.symbolId get() = interner.findOrNew(this)
    private inline val String.existedSymbolId get() = persistence.findSymbolId(this)!!
}


object Usages : JIRFeature<UsageFeatureRequest, UsageFeatureResponse> {

    private val createScheme = """
        CREATE TABLE IF NOT EXISTS "Calls"(
            "callee_class_symbol_id"      BIGINT NOT NULL,
            "callee_name_symbol_id"       BIGINT NOT NULL,
            "callee_desc_hash"            BIGINT,
            "opcode"                      INTEGER,
            "caller_class_symbol_id"      BIGINT NOT NULL,
            "caller_method_offsets"       BLOB,
            "location_id"                 BIGINT NOT NULL,
            CONSTRAINT "fk_callee_class_symbol_id" FOREIGN KEY ("callee_class_symbol_id") REFERENCES "Symbols" ("id") ON DELETE CASCADE,
            CONSTRAINT "fk_location_id" FOREIGN KEY ("caller_class_symbol_id") REFERENCES "BytecodeLocations" ("id") ON DELETE CASCADE ON UPDATE RESTRICT
        );
    """.trimIndent()

    private val createIndex = """
        CREATE INDEX IF NOT EXISTS 'Calls search' ON Calls(opcode, location_id, callee_class_symbol_id, callee_name_symbol_id, callee_desc_hash)
    """.trimIndent()

    private val dropScheme = """
        DROP TABLE IF EXISTS "Calls";
        DROP INDEX IF EXISTS "Calls search";
    """.trimIndent()

    override fun onSignal(signal: JIRSignal) {
        when (signal) {
            is JIRSignal.BeforeIndexing -> {
                signal.jirdb.persistence.write {
                    if (signal.clearOnStart) {
                        it.executeQueries(dropScheme)
                    }
                    it.executeQueries(createScheme)
                }
            }

            is JIRSignal.LocationRemoved -> {
                signal.jirdb.persistence.write {
                    it.delete(CALLS).where(CALLS.LOCATION_ID.eq(signal.location.id)).execute()
                }
            }

            is JIRSignal.AfterIndexing -> {
                signal.jirdb.persistence.write {
                    it.execute(createIndex)
                }
            }

            is JIRSignal.Drop -> {
                signal.jirdb.persistence.write {
                    it.delete(CALLS).execute()
                }
            }

            else -> Unit
        }
    }

    override suspend fun query(classpath: JIRClasspath, req: UsageFeatureRequest): Sequence<UsageFeatureResponse> {
        return syncQuery(classpath, req)
    }

    fun syncQuery(classpath: JIRClasspath, req: UsageFeatureRequest): Sequence<UsageFeatureResponse> {
        val locationIds = classpath.registeredLocations.map { it.id }
        val persistence = classpath.db.persistence
        val name = (req.methodName ?: req.field).let { persistence.findSymbolId(it!!) }
        val desc = req.description?.longHash
        val className = persistence.findSymbolId(req.className)
        return BatchedSequence(50) { offset, batchSize ->
            persistence.read { jooq ->
                var position = offset ?: 0
                jooq.select(CALLS.CALLER_METHOD_OFFSETS, SYMBOLS.NAME, CLASSES.BYTECODE, CLASSES.LOCATION_ID)
                    .from(CALLS)
                    .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                    .join(CLASSES).on(CLASSES.NAME.eq(CALLS.CALLER_CLASS_SYMBOL_ID))
                    .where(
                        CALLS.CALLEE_CLASS_SYMBOL_ID.eq(className)
                            .and(CALLS.CALLEE_NAME_SYMBOL_ID.eq(name))
                            .and(CALLS.CALLEE_DESC_HASH.eqOrNull(desc))
                            .and(CALLS.OPCODE.`in`(req.opcodes))
                            .and(CALLS.LOCATION_ID.`in`(locationIds))
                    )
                    .limit(batchSize).offset(offset ?: 0)
                    .fetch()
                    .mapNotNull { (offset, className, byteCode, locationId) ->
                        position++ to
                                UsageFeatureResponse(
                                    source = ClassSourceImpl(
                                        LazyPersistentByteCodeLocation(persistence, locationId!!),
                                        className!!,
                                        byteCode!!
                                    ),
                                    offsets = offset!!
                                )
                    }
            }
        }

    }

    override fun newIndexer(jirdb: JIRDB, location: RegisteredLocation) = UsagesIndexer(jirdb.persistence, location)

}
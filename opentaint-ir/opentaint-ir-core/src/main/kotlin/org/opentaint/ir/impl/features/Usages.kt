
package org.opentaint.ir.impl.features

import org.jooq.DSLContext
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.opentaint.ir.api.ByteCodeIndexer
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRDatabase
import org.opentaint.ir.api.JIRDatabasePersistence
import org.opentaint.ir.api.JIRFeature
import org.opentaint.ir.api.JIRSignal
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.fs.PersistenceClassSource
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


private class MethodMap(size: Int) {

    private val ticks = BooleanArray(size)
    private val array = ShortArray(size)
    private var position = 0

    fun tick(index: Int) {
        if (!ticks[index]) {
            array[position] = index.toShort()
            ticks[index] = true
            position++
        }
    }

    fun result(): ByteArray {
        return array.sliceArray(0 until position).toByteArray()
    }

    private fun ShortArray.toByteArray(): ByteArray {
        var short_index: Int
        val iterations = size
        val buffer = ByteArray(size * 2)
        var byte_index: Int = 0
        short_index = byte_index
        while ( /*NOP*/short_index != iterations /*NOP*/) {
            buffer[byte_index] = (this[short_index].toInt() and 0x00FF).toByte()
            buffer[byte_index + 1] = (this[short_index].toInt() and 0xFF00 shr 8).toByte()
            ++short_index
            byte_index += 2
        }
        return buffer
    }
}

class UsagesIndexer(persistence: JIRDatabasePersistence, private val location: RegisteredLocation) :
    ByteCodeIndexer {

    // callee_class -> (callee_name, callee_desc, opcode) -> caller
    private val usages = hashMapOf<String, HashMap<Triple<String, String?, Int>, HashMap<String, MethodMap>>>()
    private val interner = persistence.newSymbolInterner()

    override fun index(classNode: ClassNode) {
        val callerClass = Type.getObjectType(classNode.name).className
        val size = classNode.methods.size
        classNode.methods.forEachIndexed { index, methodNode ->
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
                    usages.getOrPut(callee) { hashMapOf() }
                        .getOrPut(key) { hashMapOf() }
                        .getOrPut(callerClass) { MethodMap(size) }.tick(index)
                }
            }
        }
    }

    override fun flush(jooq: DSLContext) {
        jooq.connection { conn ->
            conn.runBatch(CALLS) {
                usages.forEach { (calleeClass, calleeEntry) ->
                    val calleeId = calleeClass.className.symbolId
                    calleeEntry.forEach { (info, callers) ->
                        val (calleeName, calleeDesc, opcode) = info
                        callers.forEach { (caller, offsets) ->
                            val callerId = if (calleeClass == caller) calleeId else caller.symbolId
                            setLong(1, calleeId)
                            setLong(2, calleeName.symbolId)
                            setNullableLong(3, calleeDesc?.longHash)
                            setInt(4, opcode)
                            setLong(5, callerId)
                            setBytes(6, offsets.result())
                            setLong(7, location.id)
                            addBatch()
                        }
                    }
                }
            }
            interner.flush(conn)
        }
    }

    private inline val String.symbolId get() = interner.findOrNew(this)
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
            CONSTRAINT "fk_location_id" FOREIGN KEY ("location_id") REFERENCES "BytecodeLocations" ("id") ON DELETE CASCADE ON UPDATE RESTRICT
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
                    it.deleteFrom(CALLS).where(CALLS.LOCATION_ID.eq(signal.location.id)).execute()
                }
            }

            is JIRSignal.AfterIndexing -> {
                signal.jirdb.persistence.write {
                    it.executeQueries(createIndex)
                }
            }

            is JIRSignal.Drop -> {
                signal.jirdb.persistence.write {
                    it.deleteFrom(CALLS).execute()
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
        val className = req.className.map { persistence.findSymbolId(it) }

        val calls = persistence.read { jooq ->
            jooq.select(CLASSES.ID, CALLS.CALLER_METHOD_OFFSETS, SYMBOLS.NAME, CLASSES.LOCATION_ID)
                .from(CALLS)
                .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                .join(CLASSES).on(CLASSES.NAME.eq(CALLS.CALLER_CLASS_SYMBOL_ID))
                .where(
                    CALLS.CALLEE_CLASS_SYMBOL_ID.`in`(className)
                        .and(CALLS.CALLEE_NAME_SYMBOL_ID.eq(name))
                        .and(CALLS.CALLEE_DESC_HASH.eqOrNull(desc))
                        .and(CALLS.OPCODE.`in`(req.opcodes))
                        .and(CALLS.LOCATION_ID.`in`(locationIds))
                ).fetch().mapNotNull { (classId, offset, className, locationId) ->
                    PersistenceClassSource(
                        classpath,
                        className!!,
                        classId = classId!!,
                        locationId = locationId!!
                    ) to offset!!.toShortArray()
                }
        }

        return BatchedSequence(50) { offset, batchSize ->
            var position = offset ?: 0
            val classes = calls.drop(position.toInt()).take(batchSize)
            val classIds = classes.map { it.first.classId }.toSet()
            val byteCodes = persistence.read { jooq ->
                jooq.select(CLASSES.ID, CLASSES.BYTECODE).from(CLASSES)
                    .where(CLASSES.ID.`in`(classIds))
                    .fetch()
                    .map { (classId, byteArray) ->
                        classId!! to byteArray!!
                    }.toMap()
            }
            classes.map { (source, offsets) ->
                position++ to UsageFeatureResponse(
                    source = source.bind(byteCodes[source.classId]),
                    offsets = offsets
                )
            }
        }
    }

    override fun newIndexer(jirdb: JIRDatabase, location: RegisteredLocation) = UsagesIndexer(jirdb.persistence, location)


    private fun ByteArray.toShortArray(): ShortArray {
        val byteArray = this
        val shortArray = ShortArray(byteArray.size / 2) {
            (byteArray[it * 2].toUByte().toInt() + (byteArray[(it * 2) + 1].toInt() shl 8)).toShort()
        }
        return shortArray // [211, 24]
    }
}
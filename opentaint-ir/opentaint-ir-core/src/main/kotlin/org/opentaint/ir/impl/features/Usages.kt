package org.opentaint.ir.impl.features

import org.opentaint.ir.api.*
import org.opentaint.ir.impl.fs.PersistenceClassSource
import org.opentaint.ir.impl.fs.className
import org.opentaint.ir.impl.storage.*
import org.opentaint.ir.impl.storage.jooq.tables.references.CALLS
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.jooq.DSLContext
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode

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

class UsagesIndexer(private val location: RegisteredLocation) :
    ByteCodeIndexer {

    // callee_class -> (callee_name, callee_desc, opcode) -> caller
    private val usages = hashMapOf<String, HashMap<Triple<String, String?, Int>, HashMap<String, MethodMap>>>()

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
        val names = HashSet<String>()
        usages.forEach { (calleeClass, calleeEntry) ->
            names.add(calleeClass.className)
            calleeEntry.forEach { (info, callers) ->
                names.add(info.first)
                callers.forEach { (caller, _) ->
                    names.add(caller)
                }
            }
        }

        jooq.withoutAutoCommit { conn ->
            conn.runBatch(CALLS) {
                usages.forEach { (calleeClass, calleeEntry) ->
                    val calleeId = calleeClass.className
                    calleeEntry.forEach { (info, callers) ->
                        val (calleeName, calleeDesc, opcode) = info
                        callers.forEach { (caller, offsets) ->
                            setString(1, calleeId)
                            setString(2, calleeName)
                            setNullableLong(3, calleeDesc?.longHash)
                            setInt(4, opcode)
                            setString(5, caller)
                            setBytes(6, offsets.result())
                            setLong(7, location.id)
                            addBatch()
                        }
                    }
                }
            }
        }
    }
}

object Usages : JIRFeature<UsageFeatureRequest, UsageFeatureResponse> {

    fun create(jooq: DSLContext, drop: Boolean) {
        if (drop) {
            jooq.executeQueries("usages/drop-schema.sql".sqlScript())
        }
        jooq.executeQueries("usages/create-schema.sql".sqlScript())
    }

    override fun onSignal(signal: JIRSignal) {
        val jIRdb = signal.jIRdb
        when (signal) {
            is JIRSignal.BeforeIndexing -> jIRdb.persistence.write {
                if (signal.clearOnStart) {
                    it.executeQueries("usages/drop-schema.sql".sqlScript())
                }
                it.executeQueries("usages/create-schema.sql".sqlScript())
            }

            is JIRSignal.LocationRemoved -> jIRdb.persistence.write {
                it.deleteFrom(CALLS).where(CALLS.LOCATION_ID.eq(signal.location.id)).execute()
            }

            is JIRSignal.AfterIndexing -> jIRdb.persistence.write {
                it.executeQueries("usages/add-indexes.sql".sqlScript())
            }

            is JIRSignal.Drop -> jIRdb.persistence.write {
                it.deleteFrom(CALLS).execute()
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
        val name = req.methodName ?: req.field
        val desc = req.description?.longHash
        val className = req.className

        val calls = persistence.read { jooq ->
            jooq.select(CLASSES.ID, CALLS.CALLER_METHOD_OFFSFrontend, SYMBOLS.NAME, CLASSES.LOCATION_ID)
                .from(CALLS)
                .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                .join(CLASSES)
                .on(SYMBOLS.NAME.eq(CALLS.CALLER_CLASS_NAME).and(CLASSES.LOCATION_ID.eq(CALLS.LOCATION_ID)))
                .where(
                    CALLS.CALLEE_CLASS_NAME.`in`(className)
                        .and(CALLS.CALLEE_NAME.eq(name))
                        .and(CALLS.CALLEE_DESC_HASH.eqOrNull(desc))
                        .and(CALLS.OPCODE.`in`(req.opcodes))
                        .and(CALLS.LOCATION_ID.`in`(locationIds))
                ).fetch().mapNotNull { (classId, offset, className, locationId) ->
                    PersistenceClassSource(
                        classpath.db,
                        className!!,
                        classId = classId!!,
                        locationId = locationId!!
                    ) to offset!!.toShortArray()
                }
        }
        if (calls.isEmpty()) {
            return emptySequence()
        }

        return BatchedSequence(defaultBatchSize) { offset, batchSize ->
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

    override fun newIndexer(jIRdb: JIRDatabase, location: RegisteredLocation) = UsagesIndexer(location)

    private fun ByteArray.toShortArray(): ShortArray {
        val byteArray = this
        val shortArray = ShortArray(byteArray.size / 2) {
            (byteArray[it * 2].toUByte().toInt() + (byteArray[(it * 2) + 1].toInt() shl 8)).toShort()
        }
        return shortArray // [211, 24]
    }
}
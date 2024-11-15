package org.opentaint.ir.impl.features

import org.jooq.DSLContext
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.api.ByteCodeIndexer
import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRFeature
import org.opentaint.ir.api.JIRSignal
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.fs.ClassSourceImpl
import org.opentaint.ir.impl.storage.eqOrNull
import org.opentaint.ir.impl.storage.executeQueries
import org.opentaint.ir.impl.storage.jooq.tables.references.CALLS
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.opentaint.ir.impl.storage.longHash
import org.opentaint.ir.impl.storage.runBatch
import org.opentaint.ir.impl.storage.setNullableLong
import org.opentaint.ir.impl.vfs.LazyPersistentByteCodeLocation
import kotlin.streams.asSequence


class UsagesIndexer(private val location: RegisteredLocation) : ByteCodeIndexer {

    // callee_class -> (callee_name, callee_desc, opcode) -> caller
    private val usages = hashMapOf<String, HashMap<Triple<String, String?, Int>, HashSet<String>>>()

    override fun index(classNode: ClassNode) {
        val callerClass = Type.getObjectType(classNode.name).className
        classNode.methods.forEach { methodNode ->
            methodNode.instructions.forEach {
                var key: Triple<String, String?, Int>? = null
                var callee: String? = null
                when (it) {
                    is FieldInsnNode -> {
                        callee = Type.getObjectType(it.owner).className
                        key = Triple(it.name, null, it.opcode)
                    }

                    is MethodInsnNode -> {
                        callee = Type.getObjectType(it.owner).className
                        key = Triple(it.name, it.desc, it.opcode)
                    }
                }
                if (key != null && callee != null) {
                    usages.getOrPut(callee) { hashMapOf() }.getOrPut(key) { hashSetOf() }.add(callerClass)
                }
            }
        }
    }

    override fun index(classNode: ClassNode, methodNode: MethodNode) {
    }

    override fun flush(jooq: DSLContext) {
        jooq.connection { conn ->
            conn.runBatch(CALLS) {
                usages.forEach { (calleeClass, calleeEntry) ->
                    calleeEntry.forEach { (info, callers) ->
                        val (calleeName, calleeDesc, opcode) = info
                        callers.forEach { caller ->
                            setLong(1, calleeClass.longHash)
                            setString(2, calleeName)
                            setNullableLong(3, calleeDesc?.longHash)
                            setInt(4, opcode)
                            setLong(5, caller.longHash)
                            setLong(6, location.id)
                            addBatch()
                        }
                    }
                }
            }
        }
    }
}


object Usages : JIRFeature<UsageFeatureRequest, ClassSource> {

    private val createScheme = """
        CREATE TABLE IF NOT EXISTS "Calls"(
            "callee_class_hash"  BIGINT NOT NULL,
            "callee_name"        VARCHAR(256),
            "callee_desc_hash"   BIGINT,
            "opcode"             INTEGER,
            "caller_class_hash"  BIGINT NOT NULL,
            "location_id"        BIGINT NOT NULL
        );
        
        CREATE INDEX IF NOT EXISTS 'Calls search' ON Calls(location_id, opcode, callee_class_hash, callee_name, callee_desc_hash)
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
                    it.execute(createScheme)
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

    override suspend fun query(jirdb: JIRDB, req: UsageFeatureRequest): Sequence<ClassSource> {
        val name = req.methodName ?: req.field
        val desc = req.methodDesc
        val className = req.className
        return jirdb.persistence.read { jooq ->
            val classHashes: List<Long> = jooq.select(CALLS.CALLER_CLASS_HASH).from(CALLS)
                .where(
                    CALLS.CALLEE_CLASS_HASH.eq(className.longHash)
                        .and(CALLS.CALLEE_NAME.eq(name))
                        .and(CALLS.CALLEE_DESC_HASH.eqOrNull(desc?.longHash))
                        .and(CALLS.OPCODE.`in`(req.opcodes))
                ).fetch().mapNotNull { it.component1() }
            jooq.select(SYMBOLS.NAME, CLASSES.BYTECODE, CLASSES.LOCATION_ID)
                .from(CLASSES)
                .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                .where(SYMBOLS.HASH.`in`(classHashes))
                .fetchStream().asSequence()
                .mapNotNull {
                    ClassSourceImpl(
                        LazyPersistentByteCodeLocation(jirdb.persistence, it.component3()!!),
                        it.component1()!!, it.component2()!!
                    )
                }
        }
    }

    override fun newIndexer(jirdb: JIRDB, location: RegisteredLocation) = UsagesIndexer(location)

}
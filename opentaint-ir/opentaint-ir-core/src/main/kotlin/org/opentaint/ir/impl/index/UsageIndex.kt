package org.opentaint.ir.impl.index

import kotlinx.serialization.Serializable
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.api.ByteCodeIndexer
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRFeature
import org.opentaint.ir.api.JIRSignal
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.storage.SQLitePersistenceImpl
import org.opentaint.ir.impl.storage.insertElements
import org.opentaint.ir.impl.storage.jooq.tables.references.CALLS
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.opentaint.ir.impl.storage.longHash
import java.sql.Types


class UsagesIndexer(private val location: RegisteredLocation, private val jirdb: JIRDB) : ByteCodeIndexer {

    // class method -> usages of methods|fields
    private val fieldsUsages = hashMapOf<Pair<String, String>, HashSet<String>>()
    private val methodsUsages = hashMapOf<Pair<String, String>, HashSet<String>>()

    override fun index(classNode: ClassNode) {
    }

    override fun index(classNode: ClassNode, methodNode: MethodNode) {
        val callerClass = Type.getObjectType(classNode.name).className
        methodNode.instructions.forEach {
            when (it) {
                is FieldInsnNode -> {
                    val owner = Type.getObjectType(it.owner).className
                    val key = owner to it.name
                    fieldsUsages.getOrPut(key) { hashSetOf() }.add(callerClass)
                }

                is MethodInsnNode -> {
                    val owner = Type.getObjectType(it.owner).className
                    val key = owner to it.name
                    methodsUsages.getOrPut(key) { hashSetOf() }.add(callerClass)
                }
            }
        }
    }

    override fun flush() {
        (jirdb.persistence as SQLitePersistenceImpl).jooq.connection { conn ->
            conn.insertElements(CALLS, fieldsUsages.flatme()) {
                val (calleeClass, calleeField, caller) = it
                setLong(1, calleeClass.longHash)
                setLong(2, calleeField.longHash)
                setNull(3, Types.BIGINT)
                setLong(4, caller.longHash)
                setLong(5, location.id)
            }
            conn.insertElements(CALLS, methodsUsages.flatme()) {
                val (calleeClass, calleeMethod, caller) = it
                setLong(1, calleeClass.longHash)
                setNull(2, Types.BIGINT)
                setLong(3, calleeMethod.longHash)
                setLong(4, caller.longHash)
                setLong(5, location.id)
            }
        }
    }

    private fun Map<Pair<String, String>, HashSet<String>>.flatme(): List<Triple<String, String, String>> {
        return flatMap { entry ->
            entry.value.map { Triple(entry.key.first, entry.key.second, it) }
        }
    }
}

@Serializable
data class UsageIndexRequest(
    val method: String?,
    val field: String?,
    val className: String
) : java.io.Serializable


object Usages : JIRFeature<UsageIndexRequest, String> {

    private val createIndexes = """
        CREATE INDEX IF NOT EXISTS 'Calls methods' ON Calls(callee_class_hash, callee_method_hash, location_id)
        WHERE callee_field_hash IS NULL;

        CREATE INDEX IF NOT EXISTS 'Calls fields' ON Calls(callee_class_hash, callee_field_hash, location_id)
        WHERE callee_method_hash IS NULL;
    """.trimIndent()

    override fun onSignal(signal: JIRSignal) {
        when (signal) {
            is JIRSignal.BeforeIndexing -> {
//                if (signal.clearOnStart) {
//                    SchemaUtils.drop(Calls)
//                }
//                SchemaUtils.create(Calls)
            }

            is JIRSignal.LocationRemoved -> {
                signal.jirdb.persistence.write {
                    val create = (signal.jirdb.persistence as SQLitePersistenceImpl).jooq
                    create.delete(CALLS).where(CALLS.LOCATION_ID.eq(signal.location.id)).execute()
                }
            }

            is JIRSignal.AfterIndexing -> {
                signal.jirdb.persistence.write {
                    val create = (signal.jirdb.persistence as SQLitePersistenceImpl).jooq
                    create.execute(createIndexes)

                }
            }

            is JIRSignal.Drop -> {
                signal.jirdb.persistence.write {
                    val create = (signal.jirdb.persistence as SQLitePersistenceImpl).jooq
                    create.delete(CALLS).execute()
                }
            }

            else -> Unit
        }
    }

    override suspend fun query(jirdb: JIRDB, req: UsageIndexRequest): Sequence<String> {
        val (method, field, className) = req
        return jirdb.persistence.read {
            val create = (jirdb.persistence as SQLitePersistenceImpl).jooq

            val classHashes: List<Long> = if (method != null) {
                create.select(CALLS.CALLER_CLASS_HASH).from(CALLS)
                    .where(
                        CALLS.CALLEE_CLASS_HASH.eq(className.longHash).and(CALLS.CALLEE_METHOD_HASH.eq(method.longHash))
                    ).fetch().mapNotNull { it.component1() }
            } else if (field != null) {
                create.select(CALLS.CALLER_CLASS_HASH).from(CALLS)
                    .where(
                        CALLS.CALLEE_CLASS_HASH.eq(className.longHash).and(CALLS.CALLEE_FIELD_HASH.eq(field.longHash))
                    ).fetch().mapNotNull { it.component1() }
            } else {
                emptyList()
            }
            create.select(SYMBOLS.NAME).from(SYMBOLS)
                .where(SYMBOLS.HASH.`in`(classHashes))
                .fetch()
                .mapNotNull { it.component1() }.asSequence()
        }
    }

    override fun newIndexer(jirdb: JIRDB, location: RegisteredLocation) = UsagesIndexer(location, jirdb)

}

//
//object Calls : Table() {
//
//    val calleeClassHash = long("callee_class_hash")
//    val calleeFieldHash = long("callee_field_hash").nullable()
//    val calleeMethodHash = long("callee_method_hash").nullable()
//    val callerClassHash = long("caller_class_hash")
//    val locationId = long("location_id")
//
//}

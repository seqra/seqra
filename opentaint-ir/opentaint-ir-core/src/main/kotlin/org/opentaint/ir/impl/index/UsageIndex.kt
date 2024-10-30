package org.opentaint.ir.impl.index

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.api.ByteCodeIndexer
import org.opentaint.ir.api.Feature
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.storage.Symbols
import org.opentaint.ir.impl.storage.longHash


class UsagesIndexer(private val location: RegisteredLocation) : ByteCodeIndexer {

    // class method -> usages of methods|fields
    private val fieldsUsages = hashMapOf<Pair<String, String>, HashSet<String>>()
    private val methodsUsages = hashMapOf<Pair<String, String>, HashSet<String>>()

    override suspend fun index(classNode: ClassNode) {
    }

    override suspend fun index(classNode: ClassNode, methodNode: MethodNode) {
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
        Calls.batchInsert(
            fieldsUsages.entries.flatMap { entry ->
                entry.value.map {
                    Triple(
                        entry.key.first,
                        entry.key.second,
                        it
                    )
                }
            },
            shouldReturnGeneratedValues = false
        ) {
            this[Calls.calleeClassHash] = it.first.longHash
            this[Calls.calleeFieldHash] = it.second.longHash
            this[Calls.callerClassHash] = it.third.longHash
            this[Calls.locationId] = location.id
        }
        Calls.batchInsert(
            methodsUsages.entries.flatMap { entry ->
                entry.value.map {
                    Triple(
                        entry.key.first,
                        entry.key.second,
                        it
                    )
                }
            },
            shouldReturnGeneratedValues = false
        ) {
            this[Calls.calleeClassHash] = it.first.longHash
            this[Calls.calleeMethodHash] = it.second.longHash
            this[Calls.callerClassHash] = it.third.longHash
            this[Calls.locationId] = location.id
        }
    }

}

@Serializable
data class UsageIndexRequest(
    val method: String?,
    val field: String?,
    val className: String
) : java.io.Serializable


object Usages : Feature<UsageIndexRequest, String> {

    override val key = "usages"

    override fun beforeIndexing(jirdb: JIRDB, clearOnStart: Boolean) {
        if (clearOnStart) {
            SchemaUtils.drop(Calls)
        }
        SchemaUtils.create(Calls)
    }

    override fun afterIndexing(jirdb: JIRDB) {
        TODO("Not yet implemented")
    }

    override suspend fun query(jirdb: JIRDB, req: UsageIndexRequest): Sequence<String> {
        val (method, field, className) = req
        return jirdb.persistence.read {
            val classHashes: List<Long> = if (method != null) {
                Calls.select {
                    (Calls.calleeClassHash eq className.longHash) and (Calls.calleeMethodHash eq method.longHash)
                }.map { it[Calls.callerClassHash] }
            } else if (field != null) {
                Calls.select {
                    (Calls.calleeClassHash eq className.longHash) and (Calls.calleeFieldHash eq field.longHash)
                }.map { it[Calls.callerClassHash] }
            } else {
                emptyList()
            }
            Symbols.select { Symbols.hash inList classHashes }.map { it[Symbols.name] }.asSequence()
        }

    }

    override fun newIndexer(jirdb: JIRDB, location: RegisteredLocation) = UsagesIndexer(location)

    override fun onRemoved(jirdb: JIRDB, location: RegisteredLocation) {
        jirdb.persistence.write {
            Calls.deleteWhere { Calls.locationId eq location.id }
        }
    }
}


object Calls : Table() {

    val calleeClassHash = long("callee_class_hash")
    val calleeFieldHash = long("callee_field_hash").nullable()
    val calleeMethodHash = long("callee_method_hash").nullable()
    val callerClassHash = long("caller_class_hash")
    val locationId = long("location_id")

}

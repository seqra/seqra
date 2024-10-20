package org.opentaint.ir.impl.index

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.api.ByteCodeIndexBuilder
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.Feature
import org.opentaint.ir.api.Index
import org.opentaint.ir.api.IndexRequest
import org.opentaint.ir.impl.storage.BytecodeLocationEntity.Companion.findOrNew
import org.opentaint.ir.impl.storage.Symbols
import org.opentaint.ir.impl.storage.longHash


class ReversedUsageIndexBuilder(private val location: ByteCodeLocation) :
    ByteCodeIndexBuilder<String, UsageIndex> {

    // class method -> usages of methods|fields
    private val fieldsUsages = hashMapOf<Pair<Long, Long>, HashSet<Long>>()
    private val methodsUsages = hashMapOf<Pair<Long, Long>, HashSet<Long>>()

    override suspend fun index(classNode: ClassNode) {
    }

    override suspend fun index(classNode: ClassNode, methodNode: MethodNode) {
        if (methodNode.access and Opcodes.ACC_PRIVATE != 0) {
            return
        }
        val pureName = Type.getObjectType(classNode.name).className
        val id = pureName.longHash
        methodNode.instructions.forEach {
            when (it) {
                is FieldInsnNode -> {
                    val owner = Type.getObjectType(it.owner).className
                    val key = owner.longHash to it.name.longHash
                    fieldsUsages.getOrPut(key) { hashSetOf() }.add(id)
                }
                is MethodInsnNode -> {
                    val owner = Type.getObjectType(it.owner).className
                    val key = owner.longHash to it.name.longHash
                    methodsUsages.getOrPut(key) { hashSetOf() }.add(id)
                }
            }
        }
    }

    override fun build(): UsageIndex {
        return UsageIndex(
            location = location,
            fieldsUsages = fieldsUsages.toImmutableMap(),
            methodsUsages = methodsUsages.toImmutableMap()
        )
    }

}

data class UsageIndexRequest(
    val method: String?,
    val field: String?,
    val className: String
) : IndexRequest


class UsageIndex(
    val location: ByteCodeLocation,
    internal val fieldsUsages: ImmutableMap<Pair<Long, Long>, Set<Long>>,
    internal val methodsUsages: ImmutableMap<Pair<Long, Long>, Set<Long>>,
) : Index<String, UsageIndexRequest> {

    override suspend fun query(req: UsageIndexRequest): Sequence<String> {
        val (method, field, className) = req
        return transaction {
            val classHashes: List<Long> = if (method != null) {
                Calls.select {
                    (Calls.callerClassHash eq className.longHash) and (Calls.callerMethodHash eq method.longHash)
                }.map { it[Calls.ownerClassHash] }
            } else if (field != null) {
                Calls.select {
                    (Calls.callerClassHash eq className.longHash) and (Calls.callerFieldHash eq field.longHash)
                }.map { it[Calls.ownerClassHash] }
            } else {
                emptyList()
            }
            Symbols.select { Symbols.hash inList classHashes }.map { it[Symbols.name] }.asSequence()
        }
    }
}


object ReversedUsages : Feature<String, UsageIndex> {

    override val key = "reversed-usages"

    override fun newBuilder(location: ByteCodeLocation) = ReversedUsageIndexBuilder(location)

    override fun onRestore(): UsageIndex? = null

    override fun persist(index: UsageIndex) {
        val locationEntity = index.location.findOrNew()
        Calls.batchInsert(
            index.fieldsUsages.entries.flatMap { entry -> entry.value.map { Pair(entry.key, it) } },
            shouldReturnGeneratedValues = false
        ) {
            this[Calls.callerClassHash] = it.first.first
            this[Calls.callerFieldHash] = it.first.second
            this[Calls.ownerClassHash] = it.second
            this[Calls.locationId] = locationEntity.id.value
        }
        Calls.batchInsert(
            index.methodsUsages.entries.flatMap { entry -> entry.value.map { Pair(entry.key, it) } },
            shouldReturnGeneratedValues = false
        ) {
            this[Calls.callerClassHash] = it.first.first
            this[Calls.callerMethodHash] = it.first.second
            this[Calls.ownerClassHash] = it.second
            this[Calls.locationId] = locationEntity.id.value
        }
    }
}

object Calls : Table() {

    val callerClassHash = long("caller_class_hash")
    val callerFieldHash = long("caller_field_hash").nullable()
    val callerMethodHash = long("caller_method_hash").nullable()
    val ownerClassHash = long("owner_class_hash")
    val locationId = integer("location_id")

}



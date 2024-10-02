package org.opentaint.ir.impl.index

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.api.*
import java.io.InputStream
import java.io.OutputStream


class ReversedUsageIndexBuilder(private val globalIdsStore: GlobalIdsStore) : ByteCodeLocationIndexBuilder<String, ReversedUsageIndex> {

    // class method -> usages of methods|fields
    private val fieldsUsages = hashMapOf<Int, HashSet<Int>>()
    private val methodsUsages = hashMapOf<Int, HashSet<Int>>()

    override suspend fun index(classNode: ClassNode) {
    }

    override suspend fun index(classNode: ClassNode, methodNode: MethodNode) {
        val pureName = Type.getObjectType(classNode.name).className
        val id = globalIdsStore.getId(pureName)
        methodNode.instructions.forEach {
            when (it) {
                is FieldInsnNode -> {
                    val owner = Type.getObjectType(it.owner).className
                    val key = globalIdsStore.getId(owner + "#" + it.name)
                    fieldsUsages.getOrPut(key) { hashSetOf() }.add(id)
                }
                is MethodInsnNode -> {
                    val owner = Type.getObjectType(it.owner).className
                    val key = globalIdsStore.getId(owner + "#" + it.name)
                    methodsUsages.getOrPut(key) { hashSetOf() }.add(id)
                }
            }
        }

    }

    override fun build(location: ByteCodeLocation): ReversedUsageIndex {
        return ReversedUsageIndex(
            location = location,
            globalIdsStore = globalIdsStore,
            fieldsUsages = fieldsUsages.toImmutableMap(),
            methodsUsages = methodsUsages.toImmutableMap()
        )
    }

}


class ReversedUsageIndex(
    override val location: ByteCodeLocation,
    internal val globalIdsStore: GlobalIdsStore,
    internal val fieldsUsages: ImmutableMap<Int, Set<Int>>,
    internal val methodsUsages: ImmutableMap<Int, Set<Int>>,
) : ByteCodeLocationIndex<String> {

    override suspend fun query(term: String): Sequence<String> {
        val usages = fieldsUsages[globalIdsStore.getId(term)].orEmpty() +
                methodsUsages[globalIdsStore.getId(term)].orEmpty()
        return usages.map { globalIdsStore.getName(it) }.asSequence().filterNotNull()
    }

}


object ReversedUsages : Feature<String, ReversedUsageIndex> {

    override val key = "reversed-usages"

    override fun newBuilder(globalIdsStore: GlobalIdsStore) = ReversedUsageIndexBuilder(globalIdsStore)

    override fun deserialize(globalIdsStore: GlobalIdsStore, location: ByteCodeLocation, stream: InputStream): ReversedUsageIndex {
        val reader = stream.reader()

        val fields = hashMapOf<Int, Set<Int>>()
        val methods = hashMapOf<Int, Set<Int>>()
        var result = fields
        reader.use {
            reader.forEachLine {
                if (it == "-") {
                    result = methods
                } else {
                    val (key, value) = it.asEntry()
                    result[key] = value
                }
            }
        }
        return ReversedUsageIndex(location, globalIdsStore, fields.toPersistentMap(), methods.toPersistentMap())

    }

    override fun serialize(index: ReversedUsageIndex, out: OutputStream) {
        out.writer().use { writer ->
            index.fieldsUsages.forEach {
                writer.write(it.asString())
            }
            writer.write("-\n")
            index.methodsUsages.forEach {
                writer.write(it.asString())
            }
        }

    }

}


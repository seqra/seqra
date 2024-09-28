package org.opentaint.ir.impl.index

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ByteCodeLocationIndex
import org.opentaint.ir.api.ByteCodeLocationIndexBuilder
import org.opentaint.ir.api.Feature
import java.io.InputStream
import java.io.OutputStream


class ReversedUsageIndexBuilder : ByteCodeLocationIndexBuilder<String, ReversedUsageIndex> {

    // class method -> usages of methods|fields
    private val fieldsUsages = hashMapOf<Int, HashSet<Int>>()
    private val methodsUsages = hashMapOf<Int, HashSet<Int>>()

    override fun index(classNode: ClassNode) {
    }

    override fun index(classNode: ClassNode, methodNode: MethodNode) {
        val pureName = Type.getObjectType(classNode.name).className
        val id = GlobalIds.getId(pureName)
        methodNode.instructions.forEach {
            when (it) {
                is FieldInsnNode -> {
                    val owner = Type.getObjectType(it.owner).className
                    val key = GlobalIds.getId(owner + "#" + it.name)
                    fieldsUsages.getOrPut(key) { hashSetOf() }.add(id)
                }
                is MethodInsnNode -> {
                    val owner = Type.getObjectType(it.owner).className
                    val key = GlobalIds.getId(owner + "#" + it.name)
                    methodsUsages.getOrPut(key) { hashSetOf() }.add(id)
                }
            }
        }

    }

    override fun build(location: ByteCodeLocation): ReversedUsageIndex {
        return ReversedUsageIndex(
            location = location,
            fieldsUsages = fieldsUsages.toImmutableMap(),
            methodsUsages = methodsUsages.toImmutableMap()
        )
    }

}


class ReversedUsageIndex(
    override val location: ByteCodeLocation,
    internal val fieldsUsages: ImmutableMap<Int, Set<Int>>,
    internal val methodsUsages: ImmutableMap<Int, Set<Int>>,
) : ByteCodeLocationIndex<String> {

    override fun query(term: String): Sequence<String> {
        val usages = fieldsUsages[GlobalIds.getId(term)].orEmpty() +
                methodsUsages[GlobalIds.getId(term)].orEmpty()
        return usages.map { GlobalIds.getName(it) }.asSequence().filterNotNull()
    }

}


object ReversedUsages : Feature<String, ReversedUsageIndex> {

    override val key = "reversed-usages"

    override fun newBuilder() = ReversedUsageIndexBuilder()

    override fun deserialize(location: ByteCodeLocation, stream: InputStream): ReversedUsageIndex {
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
        return ReversedUsageIndex(location, fields.toPersistentMap(), methods.toPersistentMap())

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


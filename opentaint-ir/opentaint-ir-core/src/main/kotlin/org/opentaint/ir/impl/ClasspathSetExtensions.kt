package org.opentaint.ir.impl.index

import kotlinx.collections.immutable.toImmutableList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.opentaint.ir.api.*

/**
 * find all methods used in bytecode of specified `method`
 * @param method method to analyze
 */
suspend fun ClasspathSet.findMethodsUsedIn(method: MethodId): List<MethodId> {
    val methodNode = method.readBody() ?: return emptyList()
    val result = LinkedHashSet<MethodId>()
    methodNode.instructions.forEach { instruction ->
        when (instruction) {
            is MethodInsnNode -> {
                val owner = Type.getObjectType(instruction.owner).className
                val clazz = findClassOrNull(owner)
                if (clazz != null) {
                    clazz.findMethodOrNull(instruction.name, instruction.desc)?.also {
                        result.add(it)
                    }
                }
            }
        }
    }
    return result.toImmutableList()
}

class FieldUsagesResult(
    val reads: List<FieldId>,
    val writes: List<FieldId>
) {
    companion object {
        val EMPTY = FieldUsagesResult(emptyList(), emptyList())
    }
}

/**
 * find all methods used in bytecode of specified `method`
 * @param method method to analyze
 */
suspend fun ClasspathSet.findFieldsUsedIn(method: MethodId): FieldUsagesResult {
    val methodNode = method.readBody() ?: return FieldUsagesResult.EMPTY
    val reads = LinkedHashSet<FieldId>()
    val writes = LinkedHashSet<FieldId>()
    methodNode.instructions.forEach { instruction ->
        when (instruction) {
            is FieldInsnNode -> {
                val owner = Type.getObjectType(instruction.owner).className
                val clazz = findClassOrNull(owner)
                if (clazz != null) {
                    clazz.findFieldOrNull(instruction.name)?.also {
                        when (instruction.opcode) {
                            Opcodes.GETFIELD -> reads.add(it)
                            Opcodes.GETSTATIC -> reads.add(it)
                            Opcodes.PUTFIELD -> writes.add(it)
                            Opcodes.PUTSTATIC -> writes.add(it)
                        }
                    }
                }
            }
        }
    }
    return FieldUsagesResult(
        reads = reads.toImmutableList(),
        writes = writes.toImmutableList()
    )

}

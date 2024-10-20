package org.opentaint.ir.impl.index

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.opentaint.ir.api.*
import org.opentaint.ir.impl.storage.ClassEntity

class ReversedUsagesExtension(
    private val db: JIRDB,
    private val cp: Classpath
) {
    /**
     * find all methods that directly modifies field
     *
     * @param fieldId field
     * @param mode mode of search
     */
    suspend fun findUsages(fieldId: FieldId, mode: FieldUsageMode): List<MethodId> {
        val name = fieldId.name
        val className = fieldId.classId.name

        val maybeHierarchy = when {
            fieldId.isPrivate() -> hashSetOf(fieldId.classId)
            else -> cp.findSubClasses(className, true).toHashSet()
        }

        val potentialCandidates = maybeHierarchy.findPotentialCandidates(fieldId.name)

        val isStatic = fieldId.isStatic()
        val opcode = when {
            isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTSTATIC
            !isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTFIELD
            isStatic && mode == FieldUsageMode.READ -> Opcodes.GETSTATIC
            !isStatic && mode == FieldUsageMode.READ -> Opcodes.GETFIELD
            else -> return emptyList()
        }
        return potentialCandidates.findUsages(maybeHierarchy) { inst, hierarchyNames ->
            inst is FieldInsnNode
                    && inst.name == name
                    && inst.opcode == opcode
                    && hierarchyNames.contains(Type.getObjectType(inst.owner).className)
        }
    }

    /**
     * find all methods that call this method
     *
     * @param methodId method
     * @param mode mode of search
     */
    suspend fun findUsages(methodId: MethodId): List<MethodId> {
        val name = methodId.name
        val className = methodId.classId.name

        val hierarchy = cp.findSubClasses(className, true).toHashSet() + methodId.classId

        val potentialCandidates = hierarchy.findPotentialCandidates(methodId.name)
        val opcodes = when (methodId.isStatic()) {
            true -> setOf(Opcodes.INVOKESTATIC)
            else -> setOf(Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL)
        }
        return potentialCandidates.findUsages(hierarchy) { inst, hierarchyNames ->
            inst is MethodInsnNode
                    && inst.name == name
                    && opcodes.contains(inst.opcode)
                    && hierarchyNames.contains(Type.getObjectType(inst.owner).className)
        }
    }

    private suspend fun Set<ClassEntity>.findUsages(
        hierarchy: Set<ClassId>,
        matcher: (AbstractInsnNode, Set<String>) -> Boolean
    ): List<MethodId> {
        val result = hashSetOf<MethodId>()
        val hierarchyNames = hierarchy.map { it.name }.toSet()
        forEach {
            val classId = cp.findClassOrNull(it.name.name)
            val byteCode = classId?.byteCode()
            byteCode?.methods?.forEach { method ->
                for (inst in method.instructions) {
                    val matches = matcher(inst, hierarchyNames)
                    if (matches) {
                        val methodId = classId.methods().firstOrNull {
                            it.name == method.name && it.description() == method.desc
                        }
                        if (methodId != null) {
                            result.add(methodId)
                        }
                        break
                    }
                }
            }
        }
        return result.toList()
    }


    private suspend fun Set<ClassId>.findPotentialCandidates(method: String?, field: String?): Set<ClassEntity> {
        db.awaitBackgroundJobs()

        return flatMap { classId ->
            val classNames = cp.query<String, UsageIndexRequest>(
                ReversedUsages.key, UsageIndexRequest(
                    method = method,
                    field = field,
                    className = classId.name
                )
            )
        }
    }
}


val Classpath.reversedUsagesExt: ReversedUsagesExtension
    get() {
        return ReversedUsagesExtension(db, this)
    }
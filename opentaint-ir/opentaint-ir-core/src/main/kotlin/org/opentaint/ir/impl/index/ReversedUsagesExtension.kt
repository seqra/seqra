package org.opentaint.ir.impl.index

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.opentaint.ir.api.*
import org.opentaint.ir.impl.fs.relevantLocations

class ReversedUsagesExtension(private val db: JIRDB, private val cp: ClasspathSet) {

    /**
     * find all methods that directly modifies field
     *
     * @param fieldId field
     * @param mode mode of search
     */
    suspend fun findUsages(fieldId: FieldId, mode: FieldUsageMode): List<MethodId> {
        val name = fieldId.name
        val className = fieldId.classId.name

        val hierarchy = cp.findSubClasses(className, true).toHashSet() + fieldId.classId

        val potentialCandidates = hierarchy.findPotentialCandidates(fieldId.name)

        val isStatic = fieldId.isStatic()
        val opcode = when {
            isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTSTATIC
            !isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTFIELD
            isStatic && mode == FieldUsageMode.READ -> Opcodes.GETSTATIC
            !isStatic && mode == FieldUsageMode.READ -> Opcodes.GETFIELD
            else -> return emptyList()
        }
        return potentialCandidates.findUsages(hierarchy) { inst, hierarchyNames ->
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

    private suspend fun Set<String>.findUsages(
        hierarchy: Set<ClassId>,
        matcher: (AbstractInsnNode, Set<String>) -> Boolean
    ): List<MethodId> {
        val result = hashSetOf<MethodId>()
        val hierarchyNames = hierarchy.map { it.name }.toSet()
        forEach {
            val classId = cp.findClassOrNull(it)
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


    private suspend fun Set<ClassId>.findPotentialCandidates(methodOrField: String): Set<String> {
        db.awaitBackgroundJobs()

        return flatMap { classId ->
            val location = classId.location
            val potentialCandidates = cp.locations.relevantLocations(location).flatMap {
                cp.query<String>(ReversedUsages.key, "${classId.name}#$methodOrField")
            }.toHashSet()
            potentialCandidates
        }.toSet()
    }

}


val ClasspathSet.reversedUsagesExt: ReversedUsagesExtension
    get() {
        return ReversedUsagesExtension(db, this)
    }
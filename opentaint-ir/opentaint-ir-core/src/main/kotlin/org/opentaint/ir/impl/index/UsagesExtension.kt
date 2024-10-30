package org.opentaint.ir.impl.index

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.opentaint.ir.api.*

/**
 * find all methods that directly modifies field
 *
 * @param field field
 * @param mode mode of search
 */
suspend fun JIRClasspath.findUsages(field: JIRField, mode: FieldUsageMode): List<JIRMethod> {
    val name = field.name
    val className = field.enclosingClass.name

    val maybeHierarchy = when {
        field.isPrivate -> hashSetOf(field.enclosingClass)
        else -> findSubClasses(className, true).toHashSet() + field.enclosingClass
    }

    val potentialCandidates = findPotentialCandidates(maybeHierarchy, field = field.name) + field.enclosingClass

    val isStatic = field.isStatic
    val opcode = when {
        isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTSTATIC
        !isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTFIELD
        isStatic && mode == FieldUsageMode.READ -> Opcodes.GETSTATIC
        !isStatic && mode == FieldUsageMode.READ -> Opcodes.GETFIELD
        else -> return emptyList()
    }
    return findUsages(potentialCandidates) { inst, hierarchyNames ->
        inst is FieldInsnNode
                && inst.name == name
                && inst.opcode == opcode
                && hierarchyNames.contains(Type.getObjectType(inst.owner).className)
    }
}

/**
 * find all methods that call this method
 *
 * @param method method
 * @param mode mode of search
 */
suspend fun JIRClasspath.findUsages(method: JIRMethod): List<JIRMethod> {
    val name = method.name
    val className = method.enclosingClass.name
    val maybeHierarchy = when {
        method.isPrivate -> hashSetOf(method.enclosingClass)
        else -> findSubClasses(className, true).toHashSet() + method.enclosingClass
    }

    val potentialCandidates = findPotentialCandidates(maybeHierarchy, method = method.name) + method.enclosingClass
    val opcodes = when (method.isStatic) {
        true -> setOf(Opcodes.INVOKESTATIC)
        else -> setOf(Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL)
    }
    return findUsages(potentialCandidates) { inst, hierarchyNames ->
        inst is MethodInsnNode
                && inst.name == name
                && opcodes.contains(inst.opcode)
                && hierarchyNames.contains(Type.getObjectType(inst.owner).className)
    }
}

private suspend fun JIRClasspath.findUsages(
    hierarchy: Set<JIRClassOrInterface>,
    matcher: (AbstractInsnNode, Set<String>) -> Boolean
): List<JIRMethod> {
    val result = hashSetOf<JIRMethod>()
    val hierarchyNames = hierarchy.map { it.name }.toSet()
    hierarchy.forEach {
        val jirClass = findClassOrNull(it.name)
        val asm = jirClass?.bytecode()
        asm?.methods?.forEach { method ->
            for (inst in method.instructions) {
                val matches = matcher(inst, hierarchyNames)
                if (matches) {
                    val methodId = jirClass.methods.firstOrNull {
                        it.name == method.name && it.description == method.desc
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


private suspend fun JIRClasspath.findPotentialCandidates(
    hierarchy: Set<JIRClassOrInterface>,
    method: String? = null,
    field: String? = null
): Set<JIRClassOrInterface> {
    db.awaitBackgroundJobs()

    return hierarchy.flatMap { jirClass ->
        val classNames = query<String, UsageIndexRequest>(
            Usages.key, UsageIndexRequest(
                method = method,
                field = field,
                className = jirClass.name
            )
        ).toList()
        classNames.mapNotNull { findClassOrNull(it) }
    }.toSet()
}



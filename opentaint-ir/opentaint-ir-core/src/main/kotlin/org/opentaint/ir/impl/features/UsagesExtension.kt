package org.opentaint.ir.impl.features

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.opentaint.ir.api.*
import org.opentaint.ir.impl.bytecode.JIRClassOrInterfaceImpl

/**
 * find all methods that directly modifies field
 *
 * @param field field
 * @param mode mode of search
 */
suspend fun JIRClasspath.findUsages(field: JIRField, mode: FieldUsageMode): List<JIRMethod> {
    val maybeHierarchy = maybeHierarchy(field.enclosingClass, field.isPrivate)
    val isStatic = field.isStatic
    val opcode = when {
        isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTSTATIC
        !isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTFIELD
        isStatic && mode == FieldUsageMode.READ -> Opcodes.GETSTATIC
        !isStatic && mode == FieldUsageMode.READ -> Opcodes.GETFIELD
        else -> return emptyList()
    }

    val candidates = findMatches(
        maybeHierarchy, field = field, opcodes = listOf(opcode)
    ) + field.enclosingClass
    val name = field.name
    return findUsages(candidates) { inst, hierarchyNames ->
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
    val maybeHierarchy = maybeHierarchy(method.enclosingClass, method.isPrivate)

    val opcodes = when (method.isStatic) {
        true -> setOf(Opcodes.INVOKESTATIC)
        else -> setOf(Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL)
    }
    val candidates = findMatches(maybeHierarchy, method = method, opcodes = opcodes) + method.enclosingClass
    val name = method.name
    val desc = method.description
    return findUsages(candidates) { inst, hierarchyNames ->
        inst is MethodInsnNode
                && inst.name == name
                && inst.desc == desc
                && opcodes.contains(inst.opcode)
                && hierarchyNames.contains(Type.getObjectType(inst.owner).className)
    }
}

private suspend fun JIRClasspath.maybeHierarchy(
    enclosingClass: JIRClassOrInterface,
    private: Boolean
): Set<JIRClassOrInterface> {
    return when {
        private -> hashSetOf(enclosingClass)
        else -> hierarchyExt().findSubClasses(enclosingClass.name, true).toHashSet() + enclosingClass
    }
}

private fun findUsages(
    hierarchy: Set<JIRClassOrInterface>,
    matcher: (AbstractInsnNode, Set<String>) -> Boolean
): List<JIRMethod> {
    val result = hashSetOf<JIRMethod>()
    val hierarchyNames = hierarchy.map { it.name }.toSet()
    hierarchy.forEach { jirClass ->
        val asm = jirClass.bytecode()
        asm.methods?.forEach { method ->
            for (inst in method.instructions) {
                val matches = matcher(inst, hierarchyNames)
                if (matches) {
                    val methodId = jirClass.declaredMethods.firstOrNull {
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

private suspend fun JIRClasspath.findMatches(
    hierarchy: Set<JIRClassOrInterface>,
    method: JIRMethod? = null,
    field: JIRField? = null,
    opcodes: Collection<Int>
): Set<JIRClassOrInterface> {
    db.awaitBackgroundJobs()

    return hierarchy.flatMap { jirClass ->
        val classNames = query(
            Usages, UsageFeatureRequest(
                methodName = method?.name,
                methodDesc = method?.description,
                field = field?.name,
                opcodes = opcodes,
                className = jirClass.name
            )
        ).toList()
        classNames.map { JIRClassOrInterfaceImpl(this, it) }
    }.toSet()
}



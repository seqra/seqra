package org.opentaint.ir.impl.features

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.objectweb.asm.Opcodes
import org.opentaint.ir.api.*
import org.opentaint.ir.impl.bytecode.JIRClassOrInterfaceImpl
import kotlin.streams.asStream

/**
 * find all methods that directly modifies field
 *
 * @param field field
 * @param mode mode of search
 */
suspend fun JIRClasspath.findUsages(field: JIRField, mode: FieldUsageMode): Sequence<JIRMethod> {
    val maybeHierarchy = maybeHierarchy(field.enclosingClass, field.isPrivate) {
        it.findFieldOrNull(field.name).let {
            it == null || !it.isOverriddenBy(field)
        } // no overrides
    }
    val isStatic = field.isStatic
    val opcode = when {
        isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTSTATIC
        !isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTFIELD
        isStatic && mode == FieldUsageMode.READ -> Opcodes.GETSTATIC
        !isStatic && mode == FieldUsageMode.READ -> Opcodes.GETFIELD
        else -> return emptySequence()
    }

    return findMatches(maybeHierarchy, field = field, opcodes = listOf(opcode))
}

fun JIRClasspath.asyncFindUsage(field: JIRField, mode: FieldUsageMode) =
    GlobalScope.launch { findUsages(field, mode).asStream() }

fun JIRClasspath.asyncFindUsage(method: JIRMethod) = GlobalScope.launch { findUsages(method).asStream() }

/**
 * find all methods that call this method
 *
 * @param method method
 * @param mode mode of search
 */
suspend fun JIRClasspath.findUsages(method: JIRMethod): Sequence<JIRMethod> {
    val maybeHierarchy = maybeHierarchy(method.enclosingClass, method.isPrivate) {
        it.findMethodOrNull(method.name, method.description).let {
            it == null || !it.isOverriddenBy(method)
        } // no overrides// no override
    }

    val opcodes = when (method.isStatic) {
        true -> setOf(Opcodes.INVOKESTATIC)
        else -> setOf(Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL)
    }
    return findMatches(maybeHierarchy, method = method, opcodes = opcodes)
}

private suspend fun JIRClasspath.maybeHierarchy(
    enclosingClass: JIRClassOrInterface,
    private: Boolean,
    matcher: (JIRClassOrInterface) -> Boolean
): Set<JIRClassOrInterface> {
    return when {
        private -> hashSetOf(enclosingClass)
        else -> hierarchyExt().findSubClasses(enclosingClass.name, true).filter(matcher).toHashSet() + enclosingClass
    }
}

private suspend fun JIRClasspath.findMatches(
    hierarchy: Set<JIRClassOrInterface>,
    method: JIRMethod? = null,
    field: JIRField? = null,
    opcodes: Collection<Int>
): Sequence<JIRMethod> {
    db.awaitBackgroundJobs()
    val list = hierarchy.map {
        query(
            Usages, UsageFeatureRequest(
                methodName = method?.name,
                methodDesc = method?.description,
                field = field?.name,
                opcodes = opcodes,
                className = it.name
            )
        ).flatMap {
            JIRClassOrInterfaceImpl(
                this,
                it.source
            ).declaredMethods.filterIndexed { index, jirMethod -> it.offsets.contains(index) }
        }
    }

    return sequence {
        list.forEach {
            yieldAll(it)
        }
    }
}


private fun JIRField.isOverriddenBy(field: JIRField): Boolean {
    if (name == field.name) {
        return when {
            isPrivate -> false
            isPackagePrivate -> enclosingClass.packageName == field.enclosingClass.packageName
            else -> true
        }
    }
    return false
}

private fun JIRMethod.isOverriddenBy(method: JIRMethod): Boolean {
    if (name == method.name && description == method.description) {
        return when {
            isPrivate -> false
            isPackagePrivate -> enclosingClass.packageName == method.enclosingClass.packageName
            else -> true
        }
    }
    return false
}

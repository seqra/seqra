package org.opentaint.ir.api

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode

/**
 * is item has `public` modifier
 */
suspend fun Accessible.isPublic(): Boolean {
    return access() and Opcodes.ACC_PUBLIC != 0
}

/**
 * is item has `private` modifier
 */
suspend fun Accessible.isPrivate(): Boolean {
    return access() and Opcodes.ACC_PRIVATE != 0
}

/**
 * is item has `protected` modifier
 */
suspend fun Accessible.isProtected(): Boolean {
    return access() and Opcodes.ACC_PROTECTED != 0
}

/**
 * is item has `static` modifier
 */
suspend fun Accessible.isStatic(): Boolean {
    return access() and Opcodes.ACC_STATIC != 0
}

/**
 * is item has `final` modifier
 */
suspend fun Accessible.isFinal(): Boolean {
    return access() and Opcodes.ACC_FINAL != 0
}

/**
 * is item has `synchronized` modifier
 */
suspend fun MethodId.isSynchronized(): Boolean {
    return access() and Opcodes.ACC_SYNCHRONIZED != 0
}

/**
 * is item has `volatile` modifier
 */
suspend fun FieldId.isVolatile(): Boolean {
    return access() and Opcodes.ACC_VOLATILE != 0
}

/**
 * is field has `transient` modifier
 */
suspend fun FieldId.isTransient(): Boolean {
    return access() and Opcodes.ACC_TRANSIENT != 0
}

/**
 * is method has `native` modifier
 */
suspend fun MethodId.isNative(): Boolean {
    return access() and Opcodes.ACC_NATIVE != 0
}

/**
 * is class is interface
 */
suspend fun ClassId.isInterface(): Boolean {
    return access() and Opcodes.ACC_INTERFACE != 0
}

/**
 * is item has `abstract` modifier
 */
suspend fun Accessible.isAbstract(): Boolean {
    return access() and Opcodes.ACC_ABSTRACT != 0
}

/**
 * is method has `strictfp` modifier
 */
suspend fun MethodId.isStrict(): Boolean {
    return access() and Opcodes.ACC_STRICT != 0
}

/**
 * return true if method is constructor
 */
fun MethodId.isConstructor(): Boolean {
    return name == "<init>"
}

suspend fun ClassId.isSynthetic(): Boolean {
    return access() and Opcodes.ACC_SYNTHETIC != 0
}

suspend fun ClassId.isLocalOrAnonymous(): Boolean {
    return outerMethod() != null
}

suspend fun ClassId.isLocal(): Boolean {
    return outerClass() != null && !isAnonymous()
}

suspend fun ClassId.isMemberClass(): Boolean {
    return simpleBinaryName() != null && !isLocalOrAnonymous()
}

private suspend fun ClassId.simpleBinaryName(): String? {
    // top level class
    val enclosingClass = outerClass() ?: return null
    // Otherwise, strip the enclosing class' name
    return try {
        name.substring(enclosingClass.name.length)
    } catch (ex: IndexOutOfBoundsException) {
        throw InternalError("Malformed class name", ex)
    }
}

/**
 * find field by name
 */
suspend fun ClassId.findFieldOrNull(name: String): FieldId? = fields().firstOrNull { it.name == name }

/**
 * find method by name and description
 */
suspend fun ClassId.findMethodOrNull(name: String, desc: String): MethodId? =
    methods().firstOrNull { it.name == name && it.description() == desc }

/**
 * find method by ASM node
 */
suspend fun ClassId.findMethodOrNull(methodNode: MethodNode): MethodId? =
    methods().firstOrNull { it.name == methodNode.name && it.description() == methodNode.desc }


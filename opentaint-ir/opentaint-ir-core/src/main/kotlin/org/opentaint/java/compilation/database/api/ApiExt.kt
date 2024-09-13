package org.opentaint.java.compilation.database.api

import org.objectweb.asm.Opcodes
import java.security.MessageDigest

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


fun String.md5(): String {
    return MessageDigest
        .getInstance("MD5")
        .digest(toByteArray())
        .fold("") { str, it -> str + "%02x".format(it) }
}


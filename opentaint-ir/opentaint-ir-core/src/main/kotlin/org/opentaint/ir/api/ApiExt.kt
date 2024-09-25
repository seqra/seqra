package org.opentaint.ir.api

import kotlinx.collections.immutable.toPersistentList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.impl.index.findClassOrNull
import org.opentaint.ir.impl.types.*

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
 * is item has `package` modifier
 */
suspend fun Accessible.isPackagePrivate(): Boolean {
    return !isPublic() && !isProtected() && !isPrivate()
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

suspend fun Accessible.isSynthetic(): Boolean {
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
 * @return element class in case of `this` is ArrayClass
 */
fun ClassId.ifArrayGetElementClass(): ClassId? {
    return when (this) {
        is ArrayClassId -> elementClass
        else -> null
    }
}

/**
 * unboxes `this` class. That means that for 'java.lang.Integet' will be returned `PredefinedPrimitive.int`
 * and for `java.lang.String` will be returned `java.lang.String`
 */
fun ClassId.unboxIfNeeded(): ClassId {
    return when (name) {
        "java.lang.Boolean" -> classpath.boolean
        "java.lang.Byte" -> classpath.byte
        "java.lang.Char" -> classpath.char
        "java.lang.Short" -> classpath.short
        "java.lang.Integer" -> classpath.int
        "java.lang.Long" -> classpath.long
        "java.lang.Float" -> classpath.float
        "java.lang.Double" -> classpath.double
        else -> this
    }
}

/**
 * unboxes `this` class. That means that for 'java.lang.Integet' will be returned `PredefinedPrimitive.int`
 * and for `java.lang.String` will be returned `java.lang.String`
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
suspend fun ClassId.autoboxIfNeeded(): ClassId {
    return when (this) {
        classpath.boolean -> classpath.findClassOrNull<java.lang.Boolean>() ?: throwClassNotFound<java.lang.Boolean>()
        classpath.byte -> classpath.findClassOrNull<java.lang.Byte>() ?: throwClassNotFound<java.lang.Byte>()
        classpath.char -> classpath.findClassOrNull<Character>() ?: throwClassNotFound<Character>()
        classpath.short -> classpath.findClassOrNull<java.lang.Short>() ?: throwClassNotFound<java.lang.Short>()
        classpath.int -> classpath.findClassOrNull<Integer>() ?: throwClassNotFound<Integer>()
        classpath.long -> classpath.findClassOrNull<java.lang.Long>() ?: throwClassNotFound<java.lang.Long>()
        classpath.float -> classpath.findClassOrNull<java.lang.Float>() ?: throwClassNotFound<java.lang.Float>()
        classpath.double -> classpath.findClassOrNull<java.lang.Double>() ?: throwClassNotFound<java.lang.Double>()
        else -> this
    }
}

val ArrayClassId.isPrimitiveArray: Boolean
    get() {
        return PredefinedPrimitives.matches(elementClass.name)
    }

/**
 * @return all interfaces and classes retrieved recursively from this ClassId
 */
suspend fun ClassId.allSuperClasses(): List<ClassId> {
    val parents = (interfaces() + superclass()).filterNotNull()
    val result = parents.toMutableSet()
    parents.forEach {
        it.allSuperClasses().forEach {
            result.add(it)
        }
    }
    return result.toPersistentList()
}

suspend infix fun ClassId.isSubtypeOf(another: ClassId): Boolean {
    if (this is ArrayClassId && another is ArrayClassId) {
        if ((isPrimitiveArray || another.isPrimitiveArray) && another != this) {
            return false
        }
        return elementClass isSubtypeOf another.elementClass
    }
    if (another == classpath.findClassOrNull<Any>()) {
        return true
    }
    // unbox primitive types
    val left = unboxIfNeeded()
    val right = another.unboxIfNeeded()
    if (left == right) {
        return true
    }
    return right in allSuperClasses()
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


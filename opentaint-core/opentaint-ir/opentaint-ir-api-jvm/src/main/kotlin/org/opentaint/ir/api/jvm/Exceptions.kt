package org.opentaint.ir.api.jvm

/**
 * This exception should be thrown when classpath is incomplete
 */
class NoClassInClasspathException(val className: String) : Exception("Class $className not found in classpath")

/**
 * This exception should be thrown when classpath is incomplete
 */
class TypeNotFoundException(val typeName: String) : Exception("Type $typeName not found in classpath")

class MethodNotFoundException(msg: String) : Exception(msg)

fun String.throwClassNotFound(): Nothing {
    throw NoClassInClasspathException(this)
}

inline fun <reified T> throwClassNotFound(): Nothing {
    T::class.java.name.throwClassNotFound()
}

package org.opentaint.ir.api

/**
 * This exception should be thrown when classpath is incomplete
 */
class NoClassInClasspathException(val className: String) : Exception("Class $className not found in classpath")


fun String.throwClassNotFound(): Nothing {
    throw NoClassInClasspathException(this)
}

inline fun <reified T> throwClassNotFound(): Nothing {
    throw NoClassInClasspathException(T::class.java.name)
}
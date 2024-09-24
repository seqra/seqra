package org.opentaint.ir.api

/**
 * This exception should be thrown when classpath is incomplete
 */
class NoClassInClasspathException(val className: String) : Exception("Class $className not found in classpath")


fun classNotFound(className: String) : Nothing {
    throw NoClassInClasspathException(className)
}
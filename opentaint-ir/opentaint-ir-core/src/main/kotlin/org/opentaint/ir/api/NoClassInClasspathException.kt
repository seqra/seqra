package org.opentaint.ir.api

/**
 * This exception should be thrown when classpath is incomplete
 */
class NoClassInClasspathException(val className: String) : Exception()


fun classNotFound(className: String) : Nothing {
    throw NoClassInClasspathException(className)
}
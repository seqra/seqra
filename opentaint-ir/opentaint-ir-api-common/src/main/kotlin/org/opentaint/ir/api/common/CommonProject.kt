package org.opentaint.ir.api.common

interface Project : AutoCloseable {
    fun findTypeOrNull(name: String): CommonType?

    fun typeOf(clazz: CommonClass): CommonClassType
}

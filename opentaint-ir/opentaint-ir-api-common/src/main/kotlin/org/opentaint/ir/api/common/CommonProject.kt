package org.opentaint.ir.api.common

interface CommonProject : AutoCloseable {
    fun findTypeOrNull(name: String): CommonType?
}

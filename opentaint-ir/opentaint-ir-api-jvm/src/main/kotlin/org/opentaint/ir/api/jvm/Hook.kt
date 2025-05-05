package org.opentaint.ir.api.jvm

interface Hook {

    suspend fun afterStart()

    fun afterStop() {}
}
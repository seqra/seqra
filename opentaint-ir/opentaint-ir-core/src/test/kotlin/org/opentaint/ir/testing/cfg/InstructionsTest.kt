package org.opentaint.ir.testing.cfg

import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.junit.jupiter.api.Test

class InstructionsTest : BaseTest() {

    companion object : WithDB()

    @Test
    fun `assign inst`() {
        val clazz = cp.findClass<SimpleAlias1>()
        val method = clazz.declaredMethods.first { it.name == "main" }
        println(method.instList)

    }
}
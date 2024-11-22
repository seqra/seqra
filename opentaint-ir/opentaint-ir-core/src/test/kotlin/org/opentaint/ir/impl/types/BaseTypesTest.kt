
package org.opentaint.ir.impl.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.impl.BaseTest
import org.opentaint.ir.impl.WithDB

abstract class BaseTypesTest : BaseTest() {

    companion object : WithDB()

    protected inline fun <reified T> findType(): JIRClassType {
        val found = cp.findTypeOrNull(T::class.java.name)
        assertNotNull(found)
        return found!!.assertIs()
    }

    protected fun JIRType?.assertIsClass(): JIRClassType {
        assertNotNull(this)
        return this!!.assertIs()
    }

    protected inline fun <reified T> JIRType?.assertClassType(): JIRClassType {
        val expected = findType<T>()
        assertEquals(
            expected.jirClass.name,
            (this as? JIRClassType)?.jirClass?.name,
            "Expected ${expected.jirClass.name} but got ${this?.typeName}"
        )
        return this as JIRClassType
    }


    protected inline fun <reified T> Any.assertIs(): T {
        return assertInstanceOf(T::class.java, this)
    }
}
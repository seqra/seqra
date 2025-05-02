package org.opentaint.ir.testing.types

import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithGlobalDB
import org.junit.jupiter.api.Assertions.*

abstract class BaseTypesTest : BaseTest() {

    companion object : WithGlobalDB()

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
            expected.jIRClass.name,
            (this as? JIRClassType)?.jIRClass?.name,
            "Expected ${expected.jIRClass.name} but got ${this?.typeName}"
        )
        return this as JIRClassType
    }

    protected inline fun <reified T> Any.assertIs(): T {
        return assertInstanceOf(T::class.java, this)
    }
}
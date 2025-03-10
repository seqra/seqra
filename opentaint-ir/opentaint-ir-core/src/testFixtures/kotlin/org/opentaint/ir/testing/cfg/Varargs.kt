package org.opentaint.ir.testing.cfg

import org.junit.jupiter.api.Assertions.assertEquals

class Varargs {

    fun <T> foo(vararg a: T) = a.size

    inline fun <reified T> bar(block: () -> T): Array<T> {
        assertEquals(2, foo(block(), block()))

        return arrayOf(block(), block(), block())
    }

    inline fun <reified T> empty() = arrayOf<T>()

    fun box(): String {
        var i = 0
        val a: Array<String> = bar() { i++; i.toString() }
        assertEquals("345", a.joinToString(""))
        return "OK"
    }

}
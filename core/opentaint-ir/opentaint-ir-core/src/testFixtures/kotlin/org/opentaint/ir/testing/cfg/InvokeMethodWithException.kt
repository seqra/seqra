package org.opentaint.ir.testing.cfg

class InvokeMethodWithException {

    class A {
        fun lol(a: Int): Int {
            return 888/a
        }
    }

    fun box():String {
        val method = A::class.java.getMethod("lol", Int::class.java)
        var failed = false
        try {
            method.invoke(null, 0)
        }
        catch(e: Exception) {
            failed = true
        }

        return if (!failed) "fail" else "OK"
    }

}

fun main() {
    println(InvokeMethodWithException().box())
}
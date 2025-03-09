package org.opentaint.ir.testing.ir

object DoubleComparison {

    @JvmStatic
    fun box(): String {
        if ((-0.0 as Comparable<Double>) >= 0.0) return "fail"
        return "OK"
    }
}

object WhenExpr {

    val x = 1

    @JvmStatic
    fun box() =
        when (val y = x) {
            in 0..2 -> "OK"
            else -> "Fail: $y"
        }

}

object InvokeMethodWithException {

    class A {
        fun lol(a: Int): Int {
            return 888/a
        }
    }

    @JvmStatic
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
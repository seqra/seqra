package org.opentaint.ir.testing.cfg

class KotlinSequence {

    val xs = listOf("a", "b", "c", "d")

    fun box(): String {
        var s = ""

        for ((i, _) in xs.withIndex()) {
            s += "$i;"
        }

        return if (s == "0;1;2;3;") "OK" else "fail: '$s'"
    }

}
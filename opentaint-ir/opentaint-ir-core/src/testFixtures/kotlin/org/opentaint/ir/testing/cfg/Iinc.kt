package org.opentaint.ir.testing.cfg

class Iinc {

    fun box(): String {
        while (false);
        var x = 0
        while (x++ < 5);
        return if (x != 6) "Fail: $x" else "OK"
    }
}
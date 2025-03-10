package org.opentaint.ir.testing.cfg

class DoubleComparison {
    fun box(): String {
        if ((-0.0 as Comparable<Double>) >= 0.0) return "fail"
        return "OK"
    }
}
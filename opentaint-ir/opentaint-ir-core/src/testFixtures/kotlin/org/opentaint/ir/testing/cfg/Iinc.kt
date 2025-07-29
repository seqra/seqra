package org.opentaint.ir.testing.cfg

class Iinc {

    fun box(): String {
        while (false);
        var x = 0
        while (x++ < 5);
        return if (x != 6) "Fail: $x" else "OK"
    }
}

class Iinc2 {
    class A(var b: Byte) {
        fun c(d: Short) = (b + d.toByte()).toChar()
    }

    fun box(): String {
        if (A(10.toByte()).c(20.toShort()) != 30.toByte().toChar()) return "plus failed"

        var x = 20.toByte()
        var y = 20.toByte()
        val foo = {
            x++
            ++x
        }

        if (++x != 21.toByte() || x++ != 21.toByte() || foo() != 24.toByte() || x != 24.toByte()) return "shared byte fail"
        if (++y != 21.toByte() || y++ != 21.toByte() || y != 22.toByte()) return "byte fail"

        var xs = 20.toShort()
        var ys = 20.toShort()
        val foos = {
            xs++
            ++xs
        }

        if (++xs != 21.toShort() || xs++ != 21.toShort() || foos() != 24.toShort() || xs != 24.toShort()) return "shared short fail"
        if (++ys != 21.toShort() || ys++ != 21.toShort() || ys != 22.toShort()) return "short fail"

        var xc = 20.toChar()
        var yc = 20.toChar()
        val fooc = {
            xc++
            ++xc
        }

        if (++xc != 21.toChar() || xc++ != 21.toChar() || fooc() != 24.toChar() || xc != 24.toChar()) return "shared char fail"
        if (++yc != 21.toChar() || yc++ != 21.toChar() || yc != 22.toChar()) return "char fail"

        return "OK"
    }
}

class Iinc3 {
    fun box(): String {
        val aByte = arrayListOf<Byte>(1)
        var bByte: Byte = 1

        val aShort = arrayListOf<Short>(1)
        var bShort: Short = 1

        val aInt = arrayListOf<Int>(1)
        var bInt: Int = 1

        val aLong = arrayListOf<Long>(1)
        var bLong: Long = 1

        val aFloat = arrayListOf<Float>(1.0f)
        var bFloat: Float = 1.0f

        val aDouble = arrayListOf<Double>(1.0)
        var bDouble: Double = 1.0

        aByte[0]--
        bByte--

        if (aByte[0] != bByte) return "Failed post-decrement Byte: ${aByte[0]} != $bByte"

        aByte[0]++
        bByte++

        if (aByte[0] != bByte) return "Failed post-increment Byte: ${aByte[0]} != $bByte"

        aShort[0]--
        bShort--

        if (aShort[0] != bShort) return "Failed post-decrement Short: ${aShort[0]} != $bShort"

        aShort[0]++
        bShort++

        if (aShort[0] != bShort) return "Failed post-increment Short: ${aShort[0]} != $bShort"

        aInt[0]--
        bInt--

        if (aInt[0] != bInt) return "Failed post-decrement Int: ${aInt[0]} != $bInt"

        aInt[0]++
        bInt++

        if (aInt[0] != bInt) return "Failed post-increment Int: ${aInt[0]} != $bInt"

        aLong[0]--
        bLong--

        if (aLong[0] != bLong) return "Failed post-decrement Long: ${aLong[0]} != $bLong"

        aLong[0]++
        bLong++

        if (aLong[0] != bLong) return "Failed post-increment Long: ${aLong[0]} != $bLong"

        aFloat[0]--
        bFloat--

        if (aFloat[0] != bFloat) return "Failed post-decrement Float: ${aFloat[0]} != $bFloat"

        aFloat[0]++
        bFloat++

        if (aFloat[0] != bFloat) return "Failed post-increment Float: ${aFloat[0]} != $bFloat"

        aDouble[0]--
        bDouble--

        if (aDouble[0] != bDouble) return "Failed post-decrement Double: ${aDouble[0]} != $bDouble"

        aDouble[0]++
        bDouble++

        if (aDouble[0] != bDouble) return "Failed post-increment Double: ${aDouble[0]} != $bDouble"

        return "OK"
    }

}

class Iinc4 {

    fun box(): String {
        val aInt = arrayListOf<Int>(1)
        var bInt: Int = 1

        aInt[0]--
        bInt--

        if (aInt[0] != bInt) return "Failed post-decrement Int: ${aInt[0]} != $bInt"

        return "OK"
    }

}

class Iinc5 {

    fun box(): String {
        val result = intArrayOf(0)
        var i = 0

        repeat(10) {
            try {
                result[i++]++
            } catch (_: ArrayIndexOutOfBoundsException) {
                result[0] += 2
                i = 0
            }
        }

        return if (result[0] == 15) "OK" else "Catch failed: ${result[0]}"
    }
}

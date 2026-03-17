package org.opentaint.ir.testing.cfg

class DefaultArgs {
    class A(
        val c1: Boolean,
        val c2: Boolean,
        val c3: Boolean,
        val c4: String
    ) {
        override fun equals(o: Any?): Boolean {
            if (o !is A) return false;
            return c1 == o.c1 &&
                    c2 == o.c2 &&
                    c3 == o.c3 &&
                    c4 == o.c4
        }
    }

    fun reformat(
        str : String,
        normalizeCase : Boolean = true,
        uppercaseFirstLetter : Boolean = true,
        divideByCamelHumps : Boolean = true,
        wordSeparator : String = " "
    ) =
        A(normalizeCase, uppercaseFirstLetter, divideByCamelHumps, wordSeparator)


    fun box() : String {
        val expected = A(true, true, true, " ")
        if(reformat("", true, true) != expected) return "fail"
        return "OK"
    }

}
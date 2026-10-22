package org.opentaint.ir.approximations.target

class KotlinNames {
    @JvmField
    val `field-with-dash`: Int = -1

    fun `method-with-dash`(value: Int): Int = -value
}

class `Kotlin-Class` {
    fun evaluate(value: Int): Int = -value
}

class LegacyTarget {
    fun identity(value: Int): Int = -value
}

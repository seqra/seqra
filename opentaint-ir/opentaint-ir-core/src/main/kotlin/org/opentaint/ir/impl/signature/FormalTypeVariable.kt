package org.opentaint.ir.impl.signature

interface FormalTypeVariable {
    val symbol: String
    val boundTypeTokens: List<SType>?
}

internal class Formal(override val symbol: String, override val boundTypeTokens: List<SType>? = null) : FormalTypeVariable {
    override fun toString(): String {
        return "$symbol : ${boundTypeTokens?.joinToString()}"
    }
}
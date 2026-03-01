package org.opentaint.ir.impl.types.signature

import org.opentaint.ir.api.jvm.JIRAccessible
import org.opentaint.ir.api.jvm.JvmType
import org.opentaint.ir.api.jvm.JvmTypeParameterDeclaration

class JvmTypeParameterDeclarationImpl(
    override val symbol: String,
    override val owner: JIRAccessible,
    override val bounds: List<JvmType>? = null
) : JvmTypeParameterDeclaration {


    override fun toString(): String {
        return "$symbol : ${bounds?.joinToString { it.displayName }}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JvmTypeParameterDeclarationImpl

        if (symbol != other.symbol) return false
        if (owner != other.owner) return false

        return true
    }

    override fun hashCode(): Int {
        var result = symbol.hashCode()
        result = 31 * result + owner.hashCode()
        return result
    }

}

package org.opentaint.ir.impl.types.signature

import org.opentaint.ir.api.PredefinedPrimitives

sealed class JvmType {

    abstract val displayName: String

}

internal sealed class JvmRefType : JvmType()

internal class JvmArrayType(val elementType: JvmType) : JvmRefType() {

    override val displayName: String
        get() = elementType.displayName + "[]"

}

internal class JvmParameterizedType(
    val name: String,
    val parameterTypes: List<JvmType>
) : JvmRefType() {

    override val displayName: String
        get() = name + "<${parameterTypes.joinToString { it.displayName }}>"

    class JvmNestedType(
        val name: String,
        val parameterTypes: List<JvmType>,
        val ownerType: JvmType
    ) : JvmRefType() {

        override val displayName: String
            get() = name + "<${parameterTypes.joinToString { it.displayName }}>"

    }

}

internal class JvmClassRefType(val name: String) : JvmRefType() {

    override val displayName: String
        get() = name

}

open class JvmTypeVariable(val symbol: String) : JvmType() {

    constructor(declaration: JvmTypeParameterDeclaration) : this(declaration.symbol) {
        this.declaration = declaration
    }

    lateinit var declaration: JvmTypeParameterDeclaration

    override val displayName: String
        get() = symbol

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JvmTypeVariable

        if (symbol != other.symbol) return false
        if (declaration != other.declaration) return false

        return true
    }

    override fun hashCode(): Int {
        var result = symbol.hashCode()
        result = 31 * result + (declaration.hashCode())
        return result
    }


}

internal sealed class JvmBoundWildcard(val bound: JvmType) : JvmType() {
    internal class JvmUpperBoundWildcard(boundType: JvmType) : JvmBoundWildcard(boundType) {
        override val displayName: String
            get() = "? extends ${bound.displayName}"

    }

    internal class JvmLowerBoundWildcard(boundType: JvmType) : JvmBoundWildcard(boundType) {
        override val displayName: String
            get() = "? super ${bound.displayName}"

    }
}

internal object JvmUnboundWildcard : JvmType() {

    override val displayName: String
        get() = "*"
}

internal class JvmPrimitiveType(val ref: String) : JvmRefType() {

    companion object {
        fun of(descriptor: Char): JvmType {
            return when (descriptor) {
                'V' -> JvmPrimitiveType(PredefinedPrimitives.void)
                'Z' -> JvmPrimitiveType(PredefinedPrimitives.boolean)
                'B' -> JvmPrimitiveType(PredefinedPrimitives.byte)
                'S' -> JvmPrimitiveType(PredefinedPrimitives.short)
                'C' -> JvmPrimitiveType(PredefinedPrimitives.char)
                'I' -> JvmPrimitiveType(PredefinedPrimitives.int)
                'J' -> JvmPrimitiveType(PredefinedPrimitives.long)
                'F' -> JvmPrimitiveType(PredefinedPrimitives.float)
                'D' -> JvmPrimitiveType(PredefinedPrimitives.double)
                else -> throw IllegalArgumentException("Not a valid primitive type descriptor: $descriptor")
            }
        }
    }

    override val displayName: String
        get() = ref

}
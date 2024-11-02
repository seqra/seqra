package org.opentaint.ir.impl.signature

import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.impl.types.JIRTypeBindings

sealed class SType {
    abstract val displayName: String

    abstract fun applyTypeDeclarations(bindings: JIRTypeBindings, currentSymbol: String?): SType

    abstract fun applyKnownBindings(bindings: JIRTypeBindings): SType

    fun apply(bindings: JIRTypeBindings, currentSymbol: String?): SType {
        return applyTypeDeclarations(bindings, currentSymbol).applyKnownBindings(bindings)
    }

}

internal abstract class SRefType : SType()

internal class SArrayType(val elementType: SType) : SRefType() {

    override val displayName: String
        get() = elementType.displayName + "[]"

    override fun applyTypeDeclarations(bindings: JIRTypeBindings, currentSymbol: String?): SType {
        return SArrayType(elementType.applyTypeDeclarations(bindings, currentSymbol))
    }

    override fun applyKnownBindings(bindings: JIRTypeBindings): SType {
        return SArrayType(elementType.applyKnownBindings(bindings))
    }
}

internal class SParameterizedType(
    val name: String,
    val parameterTypes: List<SType>
) : SRefType() {

    override val displayName: String
        get() = name + "<${parameterTypes.joinToString { it.displayName }}>"

    class SNestedType(
        val name: String,
        val parameterTypes: List<SType>,
        val ownerType: SType
    ) : SRefType() {

        override val displayName: String
            get() = name + "<${parameterTypes.joinToString { it.displayName }}>"

        override fun applyTypeDeclarations(bindings: JIRTypeBindings, currentSymbol: String?): SType {
            return SNestedType(
                name,
                parameterTypes.map { it.applyTypeDeclarations(bindings, currentSymbol) },
                ownerType.applyTypeDeclarations(bindings, currentSymbol)
            )
        }

        override fun applyKnownBindings(bindings: JIRTypeBindings): SType {
            return SNestedType(
                name,
                parameterTypes.map { it.applyKnownBindings(bindings) },
                ownerType.applyKnownBindings(bindings)
            )
        }
    }

    override fun applyTypeDeclarations(bindings: JIRTypeBindings, currentSymbol: String?): SType {
        return SParameterizedType(name, parameterTypes.map { it.applyTypeDeclarations(bindings, currentSymbol) })
    }

    override fun applyKnownBindings(bindings: JIRTypeBindings): SType {
        return SParameterizedType(name, parameterTypes.map { it.applyKnownBindings(bindings) })
    }

}

internal class SClassRefType(val name: String) : SRefType() {

    override val displayName: String
        get() = name

    override fun applyTypeDeclarations(bindings: JIRTypeBindings, currentSymbol: String?): SType {
        return this
    }

    override fun applyKnownBindings(bindings: JIRTypeBindings): SType {
        return this
    }
}

open class STypeVariable(val symbol: String) : SType() {
    override val displayName: String
        get() = symbol

    override fun applyTypeDeclarations(bindings: JIRTypeBindings, currentSymbol: String?): SType {
        if (currentSymbol == symbol) {
            return this
        }
        return bindings.resolve(symbol)
    }

    override fun applyKnownBindings(bindings: JIRTypeBindings): SType {
        return bindings.findTypeBinding(symbol) ?: this
    }
}

open class SResolvedTypeVariable(symbol: String, val boundaries: List<SType>) : STypeVariable(symbol) {

    override fun applyTypeDeclarations(bindings: JIRTypeBindings, currentSymbol: String?): SType {
        return this
    }

    override fun applyKnownBindings(bindings: JIRTypeBindings): SType {
        return bindings.findTypeBinding(symbol) ?: SResolvedTypeVariable(
            symbol,
            boundaries.map { it.applyKnownBindings(bindings) })
    }
}

internal sealed class SBoundWildcard(val bound: SType) : SType() {
    internal class SUpperBoundWildcard(boundType: SType) : SBoundWildcard(boundType) {
        override val displayName: String
            get() = "? extends ${bound.displayName}"


        override fun applyTypeDeclarations(bindings: JIRTypeBindings, currentSymbol: String?): SType {
            return SUpperBoundWildcard(bound.applyTypeDeclarations(bindings, currentSymbol))
        }

        override fun applyKnownBindings(bindings: JIRTypeBindings): SType {
            return SUpperBoundWildcard(bound.applyKnownBindings(bindings))
        }
    }

    internal class SLowerBoundWildcard(boundType: SType) : SBoundWildcard(boundType) {
        override val displayName: String
            get() = "? super ${bound.displayName}"

        override fun applyTypeDeclarations(bindings: JIRTypeBindings, currentSymbol: String?): SType {
            return SLowerBoundWildcard(bound.applyTypeDeclarations(bindings, currentSymbol))
        }

        override fun applyKnownBindings(bindings: JIRTypeBindings): SType {
            return SLowerBoundWildcard(bound.applyKnownBindings(bindings))
        }
    }
}

internal object SUnboundWildcard : SType() {

    override val displayName: String
        get() = "*"

    override fun applyTypeDeclarations(bindings: JIRTypeBindings, currentSymbol: String?): SType {
        return this
    }

    override fun applyKnownBindings(bindings: JIRTypeBindings): SType {
        return this
    }

}

internal class SPrimitiveType(val ref: String) : SRefType() {

    companion object {
        fun of(descriptor: Char): SType {
            return when (descriptor) {
                'V' -> SPrimitiveType(PredefinedPrimitives.void)
                'Z' -> SPrimitiveType(PredefinedPrimitives.boolean)
                'B' -> SPrimitiveType(PredefinedPrimitives.byte)
                'S' -> SPrimitiveType(PredefinedPrimitives.short)
                'C' -> SPrimitiveType(PredefinedPrimitives.char)
                'I' -> SPrimitiveType(PredefinedPrimitives.int)
                'J' -> SPrimitiveType(PredefinedPrimitives.long)
                'F' -> SPrimitiveType(PredefinedPrimitives.float)
                'D' -> SPrimitiveType(PredefinedPrimitives.double)
                else -> throw IllegalArgumentException("Not a valid primitive type descriptor: $descriptor")
            }
        }
    }

    override val displayName: String
        get() = ref

    override fun applyTypeDeclarations(bindings: JIRTypeBindings, currentSymbol: String?): SType {
        return this
    }

    override fun applyKnownBindings(bindings: JIRTypeBindings): SType {
        return this
    }
}
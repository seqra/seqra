package org.opentaint.ir.impl.signature

import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.impl.types.JIRTypeBindings

sealed class SType {
    abstract fun apply(bindings: JIRTypeBindings): SType

}

internal abstract class SRefType : SType()

internal class SArrayType(val elementType: SType) : SRefType() {

    override fun apply(bindings: JIRTypeBindings): SType {
        return SArrayType(elementType.apply(bindings))
    }
}

internal class SParameterizedType(
    val name: String,
    val parameterTypes: List<SType>
) : SRefType() {

    class SNestedType(
        val name: String,
        val parameterTypes: List<SType>,
        val ownerType: SType
    ) : SRefType() {

        override fun apply(bindings: JIRTypeBindings): SType {
            return SNestedType(name, parameterTypes.map { it.apply(bindings) }, ownerType.apply(bindings))
        }
    }

    override fun apply(bindings: JIRTypeBindings): SType {
        return SParameterizedType(name, parameterTypes.map { it.apply(bindings) })
    }
}

internal class SClassRefType(val name: String) : SRefType() {
    override fun apply(bindings: JIRTypeBindings): SType {
        return this
    }
}

internal class STypeVariable(val symbol: String) : SType() {
    override fun apply(bindings: JIRTypeBindings): SType {
        return bindings.bindings[symbol] ?: this
    }
}

internal sealed class SBoundWildcard(val boundType: SType) : SType() {
    internal class SUpperBoundWildcard(boundType: SType) : SBoundWildcard(boundType) {

        override fun apply(bindings: JIRTypeBindings): SType {
            return SUpperBoundWildcard(boundType.apply(bindings))
        }
    }

    internal class SLowerBoundWildcard(boundType: SType) : SBoundWildcard(boundType) {

        override fun apply(bindings: JIRTypeBindings): SType {
            return SUpperBoundWildcard(boundType.apply(bindings))
        }
    }
}

internal object SUnboundWildcard : SType() {

    override fun apply(bindings: JIRTypeBindings): SType {
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

    override fun apply(bindings: JIRTypeBindings): SType {
        return this
    }

}
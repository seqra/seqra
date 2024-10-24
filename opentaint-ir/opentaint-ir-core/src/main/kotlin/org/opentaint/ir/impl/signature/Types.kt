package org.opentaint.ir.impl.signature

import org.opentaint.ir.api.PredefinedPrimitives


internal abstract class SType

internal abstract class SRefType : SType()

internal class SArrayType(val elementType: SType) : SRefType()

internal class SParameterizedType(
    val name: String,
    val parameterTypes: List<SType>
) : SRefType() {

    class SNestedType(
        val name: String,
        val parameterTypes: List<SType>,
        val ownerType: SType
    ) : SRefType()
}

internal class SClassRefType(val name: String) : SRefType()

internal class STypeVariable(val symbol: String) : SType()

internal sealed class SBoundWildcard(val boundType: SType) : SType() {
    internal class UpperSBoundWildcard(boundType: SType) : SBoundWildcard(boundType)
    internal class LowerSBoundWildcard(boundType: SType) : SBoundWildcard(boundType)
}

internal class SUnboundWildcard : SType()

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
}
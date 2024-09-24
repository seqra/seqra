package org.opentaint.ir.impl.signature


interface GenericType

class GenericArray(val componentType: GenericType) : GenericType

class ParameterizedType(
    val name: String,
    val parameterTypes: List<GenericType>
) : GenericType {

    class Nested(
        val name: String,
        val parameterTypes: List<GenericType>,
        val ownerType: GenericType
    ) : GenericType
}

class RawType(val name: String) : GenericType
class TypeVariable(val symbol: String) : GenericType

sealed class BoundWildcard(val boundType: GenericType) : GenericType {
    class UpperBoundWildcard(boundType: GenericType) : BoundWildcard(boundType)
    class LowerBoundWildcard(boundType: GenericType) : BoundWildcard(boundType)
}

object UnboundWildcard : GenericType

enum class PrimitiveType(val type: Class<*>?) : GenericType {
    BOOLEAN(Boolean::class.javaPrimitiveType),
    BYTE(Byte::class.javaPrimitiveType),
    SHORT(Short::class.javaPrimitiveType),
    CHAR(Char::class.javaPrimitiveType),
    INTEGER(Int::class.javaPrimitiveType),
    LONG(Long::class.javaPrimitiveType),
    FLOAT(Float::class.javaPrimitiveType),
    DOUBLE(Double::class.javaPrimitiveType),
    VOID(Void.TYPE);

    companion object {
        fun of(descriptor: Char): GenericType {
            return when (descriptor) {
                'V' -> VOID
                'Z' -> BOOLEAN
                'B' -> BYTE
                'S' -> SHORT
                'C' -> CHAR
                'I' -> INTEGER
                'J' -> LONG
                'F' -> FLOAT
                'D' -> DOUBLE
                else -> throw IllegalArgumentException("Not a valid primitive type descriptor: $descriptor")
            }
        }
    }
}
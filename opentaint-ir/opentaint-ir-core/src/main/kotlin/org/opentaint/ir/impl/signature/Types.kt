package org.opentaint.ir.impl.signature

import org.opentaint.ir.api.*


abstract class GenericType(val classpath: ClasspathSet) {

    suspend fun findClass(name: String): ClassId {
        return classpath.findClassOrNull(name) ?: name.throwClassNotFound()
    }

}

class GenericArray(cp: ClasspathSet, val elementType: GenericType) : GenericType(cp)

class ParameterizedType(
    cp: ClasspathSet,
    val name: String,
    val parameterTypes: List<GenericType>
) : GenericType(cp) {

    class Nested(
        cp: ClasspathSet,
        val name: String,
        val parameterTypes: List<GenericType>,
        val ownerType: GenericType
    ) : GenericType(cp) {
        suspend fun findClass(): ClassId {
            return findClass(name)
        }
    }

    suspend fun findClass(): ClassId {
        return findClass(name)
    }
}

class RawType(cp: ClasspathSet, val name: String) : GenericType(cp) {
    suspend fun findClass(): ClassId {
        return findClass(name)
    }
}

class TypeVariable(cp: ClasspathSet, val symbol: String) : GenericType(cp)

sealed class BoundWildcard(cp: ClasspathSet, val boundType: GenericType) : GenericType(cp) {
    class UpperBoundWildcard(cp: ClasspathSet, boundType: GenericType) : BoundWildcard(cp, boundType)
    class LowerBoundWildcard(cp: ClasspathSet, boundType: GenericType) : BoundWildcard(cp, boundType)
}

class UnboundWildcard(cp: ClasspathSet) : GenericType(cp)

class PrimitiveType(cp: ClasspathSet, val ref: PredefinedPrimitive) : GenericType(cp) {

    companion object {
        fun of(descriptor: Char, cp: ClasspathSet): GenericType {
            return when (descriptor) {
                'V' -> PrimitiveType(cp, cp.void)
                'Z' -> PrimitiveType(cp, cp.boolean)
                'B' -> PrimitiveType(cp, cp.byte)
                'S' -> PrimitiveType(cp, cp.short)
                'C' -> PrimitiveType(cp, cp.char)
                'I' -> PrimitiveType(cp, cp.int)
                'J' -> PrimitiveType(cp, cp.long)
                'F' -> PrimitiveType(cp, cp.float)
                'D' -> PrimitiveType(cp, cp.double)
                else -> throw IllegalArgumentException("Not a valid primitive type descriptor: $descriptor")
            }
        }
    }
}
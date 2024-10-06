package org.opentaint.ir.impl.signature

import org.opentaint.ir.api.*


abstract class GenericType(val classpath: ClasspathSet) {

    suspend fun findClass(name: String): ClassId {
        return classpath.findClassOrNull(name) ?: name.throwClassNotFound()
    }

}

abstract class GenericClassType(classpath: ClasspathSet) : GenericType(classpath) {

    abstract suspend fun findClass(): ClassId

}

class GenericArray(cp: ClasspathSet, val elementType: GenericType) : GenericClassType(cp) {

    override suspend fun findClass(): ClassId {
        if (elementType is GenericClassType) {
            return findClass(elementType.findClass().name + "[]")
        }
        return findClass("java.lang.Object")
    }
}

class ParameterizedType(
    cp: ClasspathSet,
    val name: String,
    val parameterTypes: List<GenericType>
) : GenericClassType(cp) {

    class Nested(
        cp: ClasspathSet,
        val name: String,
        val parameterTypes: List<GenericType>,
        val ownerType: GenericType
    ) : GenericClassType(cp) {
        override suspend fun findClass(): ClassId {
            return findClass(name)
        }
    }

    override suspend fun findClass(): ClassId {
        return findClass(name)
    }
}

class RawType(cp: ClasspathSet, val name: String) : GenericClassType(cp) {
    override suspend fun findClass(): ClassId {
        return findClass(name)
    }
}

class TypeVariable(cp: ClasspathSet, val symbol: String) : GenericType(cp)

sealed class BoundWildcard(cp: ClasspathSet, val boundType: GenericType) : GenericType(cp) {
    class UpperBoundWildcard(cp: ClasspathSet, boundType: GenericType) : BoundWildcard(cp, boundType)
    class LowerBoundWildcard(cp: ClasspathSet, boundType: GenericType) : BoundWildcard(cp, boundType)
}

class UnboundWildcard(cp: ClasspathSet) : GenericType(cp)

class PrimitiveType(cp: ClasspathSet, val ref: PredefinedPrimitive) : GenericClassType(cp) {

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

    override suspend fun findClass() = ref
}
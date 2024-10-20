package org.opentaint.ir.impl.signature

import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.Classpath
import org.opentaint.ir.api.PredefinedPrimitive
import org.opentaint.ir.api.boolean
import org.opentaint.ir.api.byte
import org.opentaint.ir.api.char
import org.opentaint.ir.api.double
import org.opentaint.ir.api.float
import org.opentaint.ir.api.int
import org.opentaint.ir.api.long
import org.opentaint.ir.api.short
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.api.void


abstract class GenericType(val classpath: Classpath) {

    suspend fun findClass(name: String): ClassId {
        return classpath.findClassOrNull(name) ?: name.throwClassNotFound()
    }

}

abstract class GenericClassType(classpath: Classpath) : GenericType(classpath) {

    abstract suspend fun findClass(): ClassId

}

class GenericArray(cp: Classpath, val elementType: GenericType) : GenericClassType(cp) {

    override suspend fun findClass(): ClassId {
        if (elementType is GenericClassType) {
            return findClass(elementType.findClass().name + "[]")
        }
        return findClass("java.lang.Object")
    }
}

class ParameterizedType(
    cp: Classpath,
    val name: String,
    val parameterTypes: List<GenericType>
) : GenericClassType(cp) {

    class Nested(
        cp: Classpath,
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

class RawType(cp: Classpath, val name: String) : GenericClassType(cp) {
    override suspend fun findClass(): ClassId {
        return findClass(name)
    }
}

class TypeVariable(cp: Classpath, val symbol: String) : GenericType(cp)

sealed class BoundWildcard(cp: Classpath, val boundType: GenericType) : GenericType(cp) {
    class UpperBoundWildcard(cp: Classpath, boundType: GenericType) : BoundWildcard(cp, boundType)
    class LowerBoundWildcard(cp: Classpath, boundType: GenericType) : BoundWildcard(cp, boundType)
}

class UnboundWildcard(cp: Classpath) : GenericType(cp)

class PrimitiveType(cp: Classpath, val ref: PredefinedPrimitive) : GenericClassType(cp) {

    companion object {
        fun of(descriptor: Char, cp: Classpath): GenericType {
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
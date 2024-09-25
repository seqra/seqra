package org.opentaint.ir.impl.signature

import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.types.PredefinedPrimitive


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

class RawType(cp: ClasspathSet, val name: String) : GenericType(cp){
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
                'V' -> PrimitiveType(cp, PredefinedPrimitive.void)
                'Z' -> PrimitiveType(cp, PredefinedPrimitive.boolean)
                'B' -> PrimitiveType(cp, PredefinedPrimitive.byte)
                'S' -> PrimitiveType(cp, PredefinedPrimitive.short)
                'C' -> PrimitiveType(cp, PredefinedPrimitive.char)
                'I' -> PrimitiveType(cp, PredefinedPrimitive.int)
                'J' -> PrimitiveType(cp, PredefinedPrimitive.long)
                'F' -> PrimitiveType(cp, PredefinedPrimitive.float)
                'D' -> PrimitiveType(cp, PredefinedPrimitive.double)
                else -> throw IllegalArgumentException("Not a valid primitive type descriptor: $descriptor")
            }
        }
    }
}
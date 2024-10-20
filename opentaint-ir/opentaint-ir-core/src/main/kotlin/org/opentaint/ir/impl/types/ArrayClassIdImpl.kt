package org.opentaint.ir.impl.types

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.opentaint.ir.api.AnnotationId
import org.opentaint.ir.api.ArrayClassId
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.Classpath
import org.opentaint.ir.api.FieldId
import org.opentaint.ir.api.MethodId
import org.opentaint.ir.api.Raw
import org.opentaint.ir.api.boolean
import org.opentaint.ir.api.byte
import org.opentaint.ir.api.char
import org.opentaint.ir.api.double
import org.opentaint.ir.api.ext.findClassOrNull
import org.opentaint.ir.api.float
import org.opentaint.ir.api.int
import org.opentaint.ir.api.long
import org.opentaint.ir.api.short
import org.opentaint.ir.api.throwClassNotFound

class ArrayClassIdImpl(override val elementClass: ClassId) : ArrayClassId {

    override val name = elementClass.name + "[]"
    override val simpleName = elementClass.simpleName + "[]"

    override val location: ByteCodeLocation?
        get() = elementClass.location

    override val classpath: Classpath
        get() = elementClass.classpath

    override suspend fun byteCode(): ClassNode? {
        return null
    }

    override suspend fun innerClasses() = emptyList<ClassId>()

    override suspend fun outerClass() = null

    override suspend fun isAnonymous() = false

    override suspend fun resolution() = Raw

    override suspend fun outerMethod() = null

    override suspend fun methods() = emptyList<MethodId>()

    override suspend fun superclass(): ClassId {
        return classpath.findClassOrNull<Any>() ?: throwClassNotFound<Any>()
    }

    override suspend fun interfaces() = emptyList<ClassId>()

    override suspend fun annotations() = emptyList<AnnotationId>()

    override suspend fun fields() = emptyList<FieldId>()

    override suspend fun access() = Opcodes.ACC_PUBLIC

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArrayClassIdImpl

        if (elementClass != other.elementClass) return false

        return true
    }

    override fun hashCode(): Int {
        return elementClass.hashCode()
    }

}

/**
 * Predefined arrays of primitive types
 */
val Classpath.booleanArray get() = ArrayClassIdImpl(boolean)
val Classpath.shortArray get() = ArrayClassIdImpl(short)
val Classpath.intArray get() = ArrayClassIdImpl(int)
val Classpath.longArray get() = ArrayClassIdImpl(long)
val Classpath.floatArray get() = ArrayClassIdImpl(float)
val Classpath.doubleArray get() = ArrayClassIdImpl(double)
val Classpath.byteArray get() = ArrayClassIdImpl(byte)
val Classpath.charArray get() = ArrayClassIdImpl(char)
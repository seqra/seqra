package org.opentaint.ir.impl.types

import kotlinx.collections.immutable.persistentListOf
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.opentaint.ir.api.*
import org.opentaint.ir.impl.index.findClassOrNull
import org.opentaint.ir.impl.signature.Raw

object PredefinedArrays {

    val boolean = ArrayClassIdImpl(PredefinedPrimitive.boolean)
    val byte = ArrayClassIdImpl(PredefinedPrimitive.byte)
    val char = ArrayClassIdImpl(PredefinedPrimitive.char)
    val short = ArrayClassIdImpl(PredefinedPrimitive.short)
    val int = ArrayClassIdImpl(PredefinedPrimitive.int)
    val long = ArrayClassIdImpl(PredefinedPrimitive.long)
    val float = ArrayClassIdImpl(PredefinedPrimitive.float)
    val double = ArrayClassIdImpl(PredefinedPrimitive.double)

    val values = persistentListOf(boolean, byte, char, short, int, long, float, double)

}

class ArrayClassIdImpl(val classId: ClassId) : ClassId {

    override val name = classId.simpleName + "[]"
    override val simpleName = classId.simpleName + "[]"

    override val location: ByteCodeLocation?
        get() = classId.location

    override val classpath: ClasspathSet
        get() = classId.classpath

    override suspend fun byteCode(): ClassNode? {
        return null
    }

    override suspend fun innerClasses() = emptyList<ClassId>()

    override suspend fun outerClass() = null

    override suspend fun isAnonymous() = false

    override suspend fun signature() = Raw

    override suspend fun outerMethod() = null

    override suspend fun methods() = emptyList<MethodId>()

    override suspend fun superclass(): ClassId? {
        return classId.classpath.findClassOrNull<Any>()
    }

    override suspend fun interfaces() = emptyList<ClassId>()

    override suspend fun annotations() = emptyList<ClassId>()

    override suspend fun fields() = emptyList<FieldId>()

    override suspend fun access() = Opcodes.ACC_PUBLIC

}
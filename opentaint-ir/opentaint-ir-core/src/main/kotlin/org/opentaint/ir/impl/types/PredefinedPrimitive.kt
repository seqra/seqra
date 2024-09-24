package org.opentaint.ir.impl.types

import kotlinx.collections.immutable.persistentListOf
import org.objectweb.asm.Opcodes
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.FieldId
import org.opentaint.ir.api.MethodId
import org.opentaint.ir.impl.signature.Raw

/**
 * Predefined primitive types
 */
class PredefinedPrimitive(override val simpleName: String) : ClassId {

    companion object {
        val boolean = PredefinedPrimitive("boolean")
        val byte = PredefinedPrimitive("byte")
        val char = PredefinedPrimitive("char")
        val short = PredefinedPrimitive("short")
        val int = PredefinedPrimitive("int")
        val long = PredefinedPrimitive("long")
        val float = PredefinedPrimitive("float")
        val double = PredefinedPrimitive("double")
        val void = PredefinedPrimitive("void")

        val values = persistentListOf(boolean, byte, char, short, int, long, float, double, void)

    }


    override val name: String get() = simpleName
    override val location = null

    override val classpath: ClasspathSet get() = throw UnsupportedOperationException()

    override suspend fun signature() = Raw

    override suspend fun outerClass() = null

    override suspend fun outerMethod() = null

    override suspend fun isAnonymous() = false

    override suspend fun innerClasses() = emptyList<ClassId>()

    override suspend fun byteCode() = null

    override suspend fun methods() = emptyList<MethodId>()

    override suspend fun superclass() = null

    override suspend fun interfaces() = emptyList<ClassId>()

    override suspend fun annotations() = emptyList<ClassId>()

    override suspend fun fields() = emptyList<FieldId>()

    override suspend fun access() = Opcodes.ACC_PUBLIC
}
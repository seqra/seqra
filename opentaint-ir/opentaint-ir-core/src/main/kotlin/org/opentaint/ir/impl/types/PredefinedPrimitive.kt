package org.opentaint.ir.impl.types

import kotlinx.collections.immutable.persistentListOf
import org.objectweb.asm.Opcodes
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.ClasspathSet
import org.opentaint.ir.api.FieldId
import org.opentaint.ir.api.MethodId
import org.opentaint.ir.impl.signature.Raw

object PredefinedPrimitives {

    val boolean = "boolean"
    val byte = "byte"
    val char = "char"
    val short = "short"
    val int = "int"
    val long = "long"
    val float = "float"
    val double = "double"
    val void = "void"

    private val values = persistentListOf(boolean, byte, char, short, int, long, float, double, void)
    private val valueSet = values.toHashSet()

    fun of(name: String, cp: ClasspathSet): ClassId? {
        if (valueSet.contains(name)) {
            return PredefinedPrimitive(cp, name)
        }
        return null
    }

    fun matches(name: String): Boolean {
        return valueSet.contains(name)
    }
}

/**
 * Predefined primitive types
 */
class PredefinedPrimitive(override val classpath: ClasspathSet, override val simpleName: String) : ClassId {

    override val name: String get() = simpleName
    override val location = null

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PredefinedPrimitive

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }


}

val ClasspathSet.void get() = PredefinedPrimitive(this, PredefinedPrimitives.void)
val ClasspathSet.boolean get() = PredefinedPrimitive(this, PredefinedPrimitives.boolean)
val ClasspathSet.short get() = PredefinedPrimitive(this, PredefinedPrimitives.short)
val ClasspathSet.int get() = PredefinedPrimitive(this, PredefinedPrimitives.int)
val ClasspathSet.long get() = PredefinedPrimitive(this, PredefinedPrimitives.long)
val ClasspathSet.float get() = PredefinedPrimitive(this, PredefinedPrimitives.float)
val ClasspathSet.double get() = PredefinedPrimitive(this, PredefinedPrimitives.double)
val ClasspathSet.byte get() = PredefinedPrimitive(this, PredefinedPrimitives.byte)
val ClasspathSet.char get() = PredefinedPrimitive(this, PredefinedPrimitives.char)
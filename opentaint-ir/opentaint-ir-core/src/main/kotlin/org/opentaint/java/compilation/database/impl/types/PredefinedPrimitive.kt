package org.opentaint.java.compilation.database.impl.types

import kotlinx.collections.immutable.persistentListOf
import org.objectweb.asm.Opcodes
import org.opentaint.java.compilation.database.api.ClassId
import org.opentaint.java.compilation.database.api.FieldId
import org.opentaint.java.compilation.database.api.MethodId

/**
 * Predefined primitive types
 */
class PredefinedPrimitive(override val simpleName: String) : ClassId {

    companion object {
        val values = persistentListOf(
            PredefinedPrimitive("boolean"),
            PredefinedPrimitive("char"),
            PredefinedPrimitive("short"),
            PredefinedPrimitive("int"),
            PredefinedPrimitive("long"),
            PredefinedPrimitive("float"),
            PredefinedPrimitive("double")
        )
    }


    override val name: String get() = simpleName
    override val location = null

    override suspend fun methods() = emptyList<MethodId>()

    override suspend fun superclass() = null

    override suspend fun interfaces() = emptyList<ClassId>()

    override suspend fun annotations() = emptyList<ClassId>()

    override suspend fun fields() = emptyList<FieldId>()

    override suspend fun access() = Opcodes.ACC_PUBLIC
}
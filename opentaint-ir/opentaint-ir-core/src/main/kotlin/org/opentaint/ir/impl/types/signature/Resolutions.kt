package org.opentaint.ir.impl.types.signature

import org.opentaint.ir.api.jvm.FieldResolution
import org.opentaint.ir.api.jvm.MethodResolution
import org.opentaint.ir.api.jvm.RecordComponentResolution
import org.opentaint.ir.api.jvm.TypeResolution
import org.opentaint.ir.api.jvm.JvmType
import org.opentaint.ir.api.jvm.JvmTypeParameterDeclaration

internal class FieldResolutionImpl(val fieldType: JvmType) : FieldResolution

internal class RecordComponentResolutionImpl(val recordComponentType: JvmType) : RecordComponentResolution

internal class MethodResolutionImpl(
    val returnType: JvmType,
    val parameterTypes: List<JvmType>,
    val exceptionTypes: List<JvmRefType>,
    val typeVariables: List<JvmTypeParameterDeclaration>
) : MethodResolution

internal class TypeResolutionImpl(
    val superClass: JvmType,
    val interfaceType: List<JvmType>,
    val typeVariables: List<JvmTypeParameterDeclaration>
) : TypeResolution


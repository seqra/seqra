package org.opentaint.opentaint-ir.impl.types.signature

import org.opentaint.opentaint-ir.api.FieldResolution
import org.opentaint.opentaint-ir.api.MethodResolution
import org.opentaint.opentaint-ir.api.RecordComponentResolution
import org.opentaint.opentaint-ir.api.TypeResolution

internal class FieldResolutionImpl(val fieldType: JvmType) : FieldResolution

internal class RecordComponentResolutionImpl(val recordComponentType: JvmType) : RecordComponentResolution

internal class MethodResolutionImpl(
    val returnType: JvmType,
    val parameterTypes: List<JvmType>,
    val exceptionTypes: List<JvmClassRefType>,
    val typeVariables: List<JvmTypeParameterDeclaration>
) : MethodResolution

internal class TypeResolutionImpl(
    val superClass: JvmType,
    val interfaceType: List<JvmType>,
    val typeVariables: List<JvmTypeParameterDeclaration>
) : TypeResolution


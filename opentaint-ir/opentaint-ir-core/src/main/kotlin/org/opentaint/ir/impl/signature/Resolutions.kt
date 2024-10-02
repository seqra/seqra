package org.opentaint.ir.impl.signature

import org.opentaint.ir.api.FieldResolution
import org.opentaint.ir.api.MethodResolution
import org.opentaint.ir.api.RecordComponentResolution
import org.opentaint.ir.api.TypeResolution

class FieldResolutionImpl(val fieldType: GenericType) : FieldResolution

class RecordComponentResolutionImpl(val recordComponentType: GenericType) : RecordComponentResolution

class MethodResolutionImpl(
    val returnType: GenericType,
    val parameterTypes: List<GenericType>,
    val exceptionTypes: List<GenericType>,
    val typeVariables: List<FormalTypeVariable>
) : MethodResolution

class TypeResolutionImpl(
    val superClass: GenericType,
    val interfaceType: List<GenericType>,
    val typeVariable: List<FormalTypeVariable>
) : TypeResolution


package org.opentaint.ir.impl.signature

import org.opentaint.ir.api.FieldResolution
import org.opentaint.ir.api.MethodResolution
import org.opentaint.ir.api.RecordComponentResolution
import org.opentaint.ir.api.TypeResolution

internal class FieldResolutionImpl(val fieldType: SType) : FieldResolution

internal class RecordComponentResolutionImpl(val recordComponentType: SType) : RecordComponentResolution

internal class MethodResolutionImpl(
    val returnType: SType,
    val parameterTypes: List<SType>,
    val exceptionTypes: List<SClassRefType>,
    val typeVariables: List<FormalTypeVariable>
) : MethodResolution

internal class TypeResolutionImpl(
    val superClass: SType,
    val interfaceType: List<SType>,
    val typeVariable: List<FormalTypeVariable>
) : TypeResolution


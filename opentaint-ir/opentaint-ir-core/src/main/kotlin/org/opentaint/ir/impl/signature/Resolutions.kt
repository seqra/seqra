package org.opentaint.ir.impl.signature


interface Resolution
interface MethodResolution : Resolution
interface RecordComponentResolution : Resolution
interface FieldResolution : Resolution
interface TypeResolution : Resolution

object Malformed : TypeResolution, FieldResolution, MethodResolution, RecordComponentResolution
object Raw : TypeResolution, FieldResolution, MethodResolution, RecordComponentResolution

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


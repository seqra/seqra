package org.opentaint.ir.impl.python.flat

/** Static type carried by a [FlatValue] or annotated on a [FlatInst]/[FlatParameter]. */
sealed interface FlatType

data object FlatAnyType : FlatType
data object FlatNeverType : FlatType
data object FlatNoneType : FlatType
data class FlatClassType(
    val qualifiedName: String,
    val typeArgs: List<FlatType> = emptyList(),
    val isOptional: Boolean = false,
) : FlatType
data class FlatFunctionType(val paramTypes: List<FlatType>, val returnType: FlatType) : FlatType
data class FlatUnionType(val members: List<FlatType>) : FlatType
data class FlatTupleType(val elementTypes: List<FlatType>, val isVarLength: Boolean) : FlatType
data class FlatLiteralType(val value: String, val baseType: FlatType) : FlatType
data class FlatTypeVarType(val name: String, val bounds: List<FlatType>) : FlatType

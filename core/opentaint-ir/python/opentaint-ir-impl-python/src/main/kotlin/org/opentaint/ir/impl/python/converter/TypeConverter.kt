package org.opentaint.ir.impl.python.converter

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.builder.*

object TypeConverter {
    fun convert(flat: FlatType): PIRType = when (flat) {
        is FlatAnyType -> PIRAnyType
        is FlatNeverType -> PIRNeverType
        is FlatNoneType -> PIRNoneType
        is FlatClassType -> PIRClassType(
            qualifiedName = flat.qualifiedName,
            typeArgs = flat.typeArgs.map { convert(it) },
            isOptional = flat.isOptional,
        )
        is FlatFunctionType -> PIRFunctionType(
            paramTypes = flat.paramTypes.map { convert(it) },
            returnType = convert(flat.returnType),
        )
        is FlatUnionType -> PIRUnionType(members = flat.members.map { convert(it) })
        is FlatTupleType -> PIRTupleType(
            elementTypes = flat.elementTypes.map { convert(it) },
            isVarLength = flat.isVarLength,
        )
        is FlatLiteralType -> PIRLiteralType(
            value = flat.value,
            baseType = convert(flat.baseType),
        )
        is FlatTypeVarType -> PIRTypeVarType(
            name = flat.name,
            bounds = flat.bounds.map { convert(it) },
        )
    }
}

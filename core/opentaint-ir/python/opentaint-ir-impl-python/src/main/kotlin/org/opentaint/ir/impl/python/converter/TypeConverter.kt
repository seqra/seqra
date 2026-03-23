package org.opentaint.ir.impl.python.converter

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.proto.PIRTypeProto

class TypeConverter {

    fun convert(proto: PIRTypeProto): PIRType {
        return when (proto.kindCase) {
            PIRTypeProto.KindCase.CLASS_TYPE -> {
                val ct = proto.classType
                PIRClassType(
                    qualifiedName = ct.qualifiedName,
                    typeArgs = ct.typeArgsList.map { convert(it) },
                    isOptional = ct.isOptional,
                )
            }
            PIRTypeProto.KindCase.FUNCTION_TYPE -> {
                val ft = proto.functionType
                PIRFunctionType(
                    paramTypes = ft.paramTypesList.map { convert(it) },
                    returnType = convert(ft.returnType),
                )
            }
            PIRTypeProto.KindCase.UNION_TYPE -> {
                PIRUnionType(members = proto.unionType.membersList.map { convert(it) })
            }
            PIRTypeProto.KindCase.TUPLE_TYPE -> {
                PIRTupleType(
                    elementTypes = proto.tupleType.elementTypesList.map { convert(it) },
                    isVarLength = proto.tupleType.isVarLength,
                )
            }
            PIRTypeProto.KindCase.LITERAL_TYPE -> {
                PIRLiteralType(
                    value = proto.literalType.value,
                    baseType = convert(proto.literalType.baseType),
                )
            }
            PIRTypeProto.KindCase.ANY_TYPE -> PIRAnyType
            PIRTypeProto.KindCase.NEVER_TYPE -> PIRNeverType
            PIRTypeProto.KindCase.NONE_TYPE -> PIRNoneType
            PIRTypeProto.KindCase.TYPE_VAR_TYPE -> {
                PIRTypeVarType(
                    name = proto.typeVarType.name,
                    bounds = proto.typeVarType.boundsList.map { convert(it) },
                )
            }
            PIRTypeProto.KindCase.KIND_NOT_SET, null -> PIRAnyType
        }
    }
}

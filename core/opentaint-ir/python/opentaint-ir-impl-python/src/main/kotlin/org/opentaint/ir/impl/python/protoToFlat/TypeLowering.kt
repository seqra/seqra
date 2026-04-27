package org.opentaint.ir.impl.python.protoToFlat

import org.opentaint.ir.impl.python.flat.*
import org.opentaint.ir.impl.python.proto.MypyArgumentProto
import org.opentaint.ir.impl.python.proto.MypyExprProto
import org.opentaint.ir.impl.python.proto.PIRTypeProto

/**
 * Stateless conversions of mypy proto types and constants into Flat IR.
 * No state, no side effects — safe to call from anywhere in the pipeline.
 */
internal object TypeLowering {

    fun convertType(proto: PIRTypeProto): FlatType = when (proto.kindCase) {
        PIRTypeProto.KindCase.CLASS_TYPE -> {
            val ct = proto.classType
            FlatClassType(
                qualifiedName = ct.qualifiedName,
                typeArgs = ct.typeArgsList.map { convertType(it) },
                isOptional = ct.isOptional,
            )
        }
        PIRTypeProto.KindCase.FUNCTION_TYPE -> FlatFunctionType(
            paramTypes = proto.functionType.paramTypesList.map { convertType(it) },
            returnType = convertType(proto.functionType.returnType),
        )
        PIRTypeProto.KindCase.UNION_TYPE -> FlatUnionType(
            members = proto.unionType.membersList.map { convertType(it) },
        )
        PIRTypeProto.KindCase.TUPLE_TYPE -> FlatTupleType(
            elementTypes = proto.tupleType.elementTypesList.map { convertType(it) },
            isVarLength = proto.tupleType.isVarLength,
        )
        PIRTypeProto.KindCase.LITERAL_TYPE -> FlatLiteralType(
            value = proto.literalType.value,
            baseType = convertType(proto.literalType.baseType),
        )
        PIRTypeProto.KindCase.ANY_TYPE -> FlatAnyType
        PIRTypeProto.KindCase.NEVER_TYPE -> FlatNeverType
        PIRTypeProto.KindCase.NONE_TYPE -> FlatNoneType
        PIRTypeProto.KindCase.TYPE_VAR_TYPE -> FlatTypeVarType(
            name = proto.typeVarType.name,
            bounds = proto.typeVarType.boundsList.map { convertType(it) },
        )
        PIRTypeProto.KindCase.KIND_NOT_SET, null -> FlatAnyType
    }

    fun convertParameters(args: List<MypyArgumentProto>): List<FlatParameter> =
        args.map { arg ->
            FlatParameter(
                name = arg.name,
                type = if (arg.hasType()) convertType(arg.type) else FlatAnyType,
                kind = paramKind(arg.kind),
                hasDefault = arg.hasDefault,
                defaultValue = if (arg.hasDefault && arg.hasDefaultValue()) constFromExpr(arg.defaultValue) else null,
            )
        }

    /** Map mypy's integer ARG_KIND enum into [FlatParamKind]. */
    private fun paramKind(kind: Int): FlatParamKind = when (kind) {
        0, 1 -> FlatParamKind.POSITIONAL_OR_KEYWORD     // ARG_POS, ARG_OPT
        2 -> FlatParamKind.VAR_POSITIONAL               // ARG_STAR
        4 -> FlatParamKind.VAR_KEYWORD                  // ARG_STAR2
        3, 5 -> FlatParamKind.KEYWORD_ONLY              // ARG_NAMED, ARG_NAMED_OPT
        else -> FlatParamKind.POSITIONAL_OR_KEYWORD
    }

    /**
     * Evaluate a literal expression as a [FlatConst]. Returns null for anything
     * that isn't a compile-time constant.
     */
    fun constFromExpr(expr: MypyExprProto): FlatConst? = when (expr.kindCase) {
        MypyExprProto.KindCase.INT_EXPR -> FlatIntConst(expr.intExpr.value)
        MypyExprProto.KindCase.FLOAT_EXPR -> FlatFloatConst(expr.floatExpr.value)
        MypyExprProto.KindCase.STR_EXPR -> FlatStrConst(expr.strExpr.value)
        MypyExprProto.KindCase.BYTES_EXPR -> FlatBytesConst(expr.bytesExpr.value.toByteArray())
        MypyExprProto.KindCase.COMPLEX_EXPR -> FlatComplexConst(expr.complexExpr.real, expr.complexExpr.imag)
        MypyExprProto.KindCase.ELLIPSIS_EXPR -> FlatEllipsisConst
        MypyExprProto.KindCase.NAME_EXPR -> when (expr.nameExpr.name) {
            "True" -> FlatBoolConst(true)
            "False" -> FlatBoolConst(false)
            "None" -> FlatNoneConst
            else -> null
        }
        else -> null
    }
}

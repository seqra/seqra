package org.opentaint.ir.impl.python.converter

import org.opentaint.ir.api.python.*
import org.opentaint.ir.impl.python.proto.PIRValueProto
import org.opentaint.ir.impl.python.proto.PIRConstProto

class ValueConverter(private val typeConverter: TypeConverter) {

    fun convert(proto: PIRValueProto): PIRValue {
        return when (proto.kindCase) {
            PIRValueProto.KindCase.LOCAL -> {
                PIRLocal(proto.local.name, typeConverter.convert(proto.local.type))
            }
            PIRValueProto.KindCase.PARAMETER -> {
                PIRParameterRef(proto.parameter.name, PIRAnyType)
            }
            PIRValueProto.KindCase.CONST_VAL -> {
                convertConst(proto.constVal)
            }
            PIRValueProto.KindCase.GLOBAL_REF -> {
                PIRGlobalRef(proto.globalRef.name, proto.globalRef.module, PIRAnyType)
            }
            PIRValueProto.KindCase.KIND_NOT_SET, null -> PIRNoneConst
        }
    }

    private fun convertConst(proto: PIRConstProto): PIRConst {
        val type = if (proto.hasType()) typeConverter.convert(proto.type) else PIRAnyType
        return when (proto.valueCase) {
            PIRConstProto.ValueCase.INT_VALUE -> PIRIntConst(proto.intValue, type)
            PIRConstProto.ValueCase.FLOAT_VALUE -> PIRFloatConst(proto.floatValue, type)
            PIRConstProto.ValueCase.STRING_VALUE -> PIRStrConst(proto.stringValue, type)
            PIRConstProto.ValueCase.BOOL_VALUE -> PIRBoolConst(proto.boolValue, type)
            PIRConstProto.ValueCase.NONE_VALUE -> PIRNoneConst
            PIRConstProto.ValueCase.ELLIPSIS_VALUE -> PIREllipsisConst
            PIRConstProto.ValueCase.BYTES_VALUE -> PIRBytesConst(proto.bytesValue.toByteArray(), type)
            PIRConstProto.ValueCase.COMPLEX_REAL -> PIRComplexConst(proto.complexReal, proto.complexImag, type)
            PIRConstProto.ValueCase.VALUE_NOT_SET, null -> PIRNoneConst
        }
    }
}

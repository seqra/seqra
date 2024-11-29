package org.opentaint.opentaint-ir.impl.cfg

import org.opentaint.opentaint-ir.api.PredefinedPrimitives
import org.opentaint.opentaint-ir.api.TypeName
import org.opentaint.opentaint-ir.api.cfg.JIRRawNullConstant
import org.opentaint.opentaint-ir.api.cfg.JIRRawStringConstant
import org.opentaint.opentaint-ir.impl.cfg.util.NULL
import org.opentaint.opentaint-ir.impl.cfg.util.STRING_CLASS
import org.opentaint.opentaint-ir.impl.cfg.util.typeName

fun JIRRawNull() = JIRRawNullConstant(NULL)
fun JIRRawBool(value: Boolean) = org.opentaint.opentaint-ir.api.cfg.JIRRawBool(value, PredefinedPrimitives.boolean.typeName())
fun JIRRawByte(value: Byte) = org.opentaint.opentaint-ir.api.cfg.JIRRawByte(value, PredefinedPrimitives.byte.typeName())
fun JIRRawShort(value: Short) = org.opentaint.opentaint-ir.api.cfg.JIRRawShort(value, PredefinedPrimitives.short.typeName())
fun JIRRawChar(value: Char) = org.opentaint.opentaint-ir.api.cfg.JIRRawChar(value, PredefinedPrimitives.char.typeName())
fun JIRRawInt(value: Int) = org.opentaint.opentaint-ir.api.cfg.JIRRawInt(value, PredefinedPrimitives.int.typeName())
fun JIRRawLong(value: Long) = org.opentaint.opentaint-ir.api.cfg.JIRRawLong(value, PredefinedPrimitives.long.typeName())
fun JIRRawFloat(value: Float) = org.opentaint.opentaint-ir.api.cfg.JIRRawFloat(value, PredefinedPrimitives.float.typeName())
fun JIRRawDouble(value: Double) = org.opentaint.opentaint-ir.api.cfg.JIRRawDouble(value, PredefinedPrimitives.double.typeName())

fun JIRRawZero(typeName: TypeName) = when (typeName.typeName) {
    PredefinedPrimitives.boolean -> JIRRawBool(false)
    PredefinedPrimitives.byte -> JIRRawByte(0)
    PredefinedPrimitives.char -> JIRRawChar(0.toChar())
    PredefinedPrimitives.short -> JIRRawShort(0)
    PredefinedPrimitives.int -> JIRRawInt(0)
    PredefinedPrimitives.long -> JIRRawLong(0)
    PredefinedPrimitives.float -> JIRRawFloat(0.0f)
    PredefinedPrimitives.double -> JIRRawDouble(0.0)
    else -> error("Unknown primitive type: $typeName")
}

fun JIRRawNumber(number: Number) = when (number) {
    is Int -> JIRRawInt(number)
    is Float -> JIRRawFloat(number)
    is Long -> JIRRawLong(number)
    is Double -> JIRRawDouble(number)
    else -> error("Unknown number: $number")
}

fun JIRRawString(value: String) = JIRRawStringConstant(value, STRING_CLASS.typeName())

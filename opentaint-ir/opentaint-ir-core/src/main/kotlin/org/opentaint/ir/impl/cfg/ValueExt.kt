package org.opentaint.opentaint-ir.impl.cfg

import org.opentaint.opentaint-ir.api.PredefinedPrimitives
import org.opentaint.opentaint-ir.api.TypeName
import org.opentaint.opentaint-ir.api.cfg.JIRRawNullConstant
import org.opentaint.opentaint-ir.api.cfg.JIRRawStringConstant
import org.opentaint.opentaint-ir.impl.cfg.util.NULL
import org.opentaint.opentaint-ir.impl.cfg.util.STRING_CLASS
import org.opentaint.opentaint-ir.impl.cfg.util.typeName

fun JIRRawNull() = JIRRawNullConstant(NULL)
fun JIRRawBool(value: Boolean) = org.opentaint.opentaint-ir.api.cfg.JIRRawBool(value, PredefinedPrimitives.Boolean.typeName())
fun JIRRawByte(value: Byte) = org.opentaint.opentaint-ir.api.cfg.JIRRawByte(value, PredefinedPrimitives.Byte.typeName())
fun JIRRawShort(value: Short) = org.opentaint.opentaint-ir.api.cfg.JIRRawShort(value, PredefinedPrimitives.Short.typeName())
fun JIRRawChar(value: Char) = org.opentaint.opentaint-ir.api.cfg.JIRRawChar(value, PredefinedPrimitives.Char.typeName())
fun JIRRawInt(value: Int) = org.opentaint.opentaint-ir.api.cfg.JIRRawInt(value, PredefinedPrimitives.Int.typeName())
fun JIRRawLong(value: Long) = org.opentaint.opentaint-ir.api.cfg.JIRRawLong(value, PredefinedPrimitives.Long.typeName())
fun JIRRawFloat(value: Float) = org.opentaint.opentaint-ir.api.cfg.JIRRawFloat(value, PredefinedPrimitives.Float.typeName())
fun JIRRawDouble(value: Double) = org.opentaint.opentaint-ir.api.cfg.JIRRawDouble(value, PredefinedPrimitives.Double.typeName())

fun JIRRawZero(typeName: TypeName) = when (typeName.typeName) {
    PredefinedPrimitives.Boolean -> JIRRawBool(false)
    PredefinedPrimitives.Byte -> JIRRawByte(0)
    PredefinedPrimitives.Char -> JIRRawChar(0.toChar())
    PredefinedPrimitives.Short -> JIRRawShort(0)
    PredefinedPrimitives.Int -> JIRRawInt(0)
    PredefinedPrimitives.Long -> JIRRawLong(0)
    PredefinedPrimitives.Float -> JIRRawFloat(0.0f)
    PredefinedPrimitives.Double -> JIRRawDouble(0.0)
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

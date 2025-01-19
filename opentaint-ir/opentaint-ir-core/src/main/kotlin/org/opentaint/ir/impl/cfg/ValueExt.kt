@file:JvmName("JIRValues")

package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.PredefinedPrimitives
import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.cfg.JIRBool
import org.opentaint.ir.api.cfg.JIRByte
import org.opentaint.ir.api.cfg.JIRDouble
import org.opentaint.ir.api.cfg.JIRFloat
import org.opentaint.ir.api.cfg.JIRInt
import org.opentaint.ir.api.cfg.JIRLong
import org.opentaint.ir.api.cfg.JIRRawNullConstant
import org.opentaint.ir.api.cfg.JIRRawStringConstant
import org.opentaint.ir.api.cfg.JIRShort
import org.opentaint.ir.api.ext.boolean
import org.opentaint.ir.api.ext.byte
import org.opentaint.ir.api.ext.double
import org.opentaint.ir.api.ext.float
import org.opentaint.ir.api.ext.int
import org.opentaint.ir.api.ext.long
import org.opentaint.ir.api.ext.short
import org.opentaint.ir.impl.cfg.util.NULL
import org.opentaint.ir.impl.cfg.util.STRING_CLASS
import org.opentaint.ir.impl.cfg.util.typeName

@JvmName("rawNull")
fun JIRRawNull() = JIRRawNullConstant(NULL)

@JvmName("rawBool")
fun JIRRawBool(value: Boolean) = org.opentaint.ir.api.cfg.JIRRawBool(value, PredefinedPrimitives.Boolean.typeName())

@JvmName("rawByte")
fun JIRRawByte(value: Byte) = org.opentaint.ir.api.cfg.JIRRawByte(value, PredefinedPrimitives.Byte.typeName())

@JvmName("rawShort")
fun JIRRawShort(value: Short) = org.opentaint.ir.api.cfg.JIRRawShort(value, PredefinedPrimitives.Short.typeName())

@JvmName("rawChar")
fun JIRRawChar(value: Char) = org.opentaint.ir.api.cfg.JIRRawChar(value, PredefinedPrimitives.Char.typeName())

@JvmName("rawInt")
fun JIRRawInt(value: Int) = org.opentaint.ir.api.cfg.JIRRawInt(value, PredefinedPrimitives.Int.typeName())

@JvmName("rawLong")
fun JIRRawLong(value: Long) = org.opentaint.ir.api.cfg.JIRRawLong(value, PredefinedPrimitives.Long.typeName())

@JvmName("rawFloat")
fun JIRRawFloat(value: Float) = org.opentaint.ir.api.cfg.JIRRawFloat(value, PredefinedPrimitives.Float.typeName())

@JvmName("rawDouble")
fun JIRRawDouble(value: Double) = org.opentaint.ir.api.cfg.JIRRawDouble(value, PredefinedPrimitives.Double.typeName())

@JvmName("rawZero")
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

@JvmName("rawNumber")
fun JIRRawNumber(number: Number) = when (number) {
    is Int -> JIRRawInt(number)
    is Float -> JIRRawFloat(number)
    is Long -> JIRRawLong(number)
    is Double -> JIRRawDouble(number)
    else -> error("Unknown number: $number")
}

@JvmName("rawString")
fun JIRRawString(value: String) = JIRRawStringConstant(value, STRING_CLASS.typeName())

fun JIRClasspath.int(value: Int): JIRInt = JIRInt(value, int)
fun JIRClasspath.byte(value: Byte): JIRByte = JIRByte(value, byte)
fun JIRClasspath.short(value: Short): JIRShort = JIRShort(value, short)
fun JIRClasspath.long(value: Long): JIRLong = JIRLong(value, long)
fun JIRClasspath.boolean(value: Boolean): JIRBool = JIRBool(value, boolean)
fun JIRClasspath.double(value: Double): JIRDouble = JIRDouble(value, double)
fun JIRClasspath.float(value: Float): JIRFloat = JIRFloat(value, float)

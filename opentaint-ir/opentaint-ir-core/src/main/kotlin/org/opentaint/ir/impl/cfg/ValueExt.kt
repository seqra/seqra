@file:JvmName("JIRValues")

package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.core.TypeName
import org.opentaint.ir.api.jvm.JIRProject
import org.opentaint.ir.api.jvm.PredefinedJIRPrimitives
import org.opentaint.ir.api.jvm.cfg.JIRBool
import org.opentaint.ir.api.jvm.cfg.JIRByte
import org.opentaint.ir.api.jvm.cfg.JIRDouble
import org.opentaint.ir.api.jvm.cfg.JIRFloat
import org.opentaint.ir.api.jvm.cfg.JIRInt
import org.opentaint.ir.api.jvm.cfg.JIRLong
import org.opentaint.ir.api.jvm.cfg.JIRRawNullConstant
import org.opentaint.ir.api.jvm.cfg.JIRRawStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRShort
import org.opentaint.ir.api.jvm.ext.boolean
import org.opentaint.ir.api.jvm.ext.byte
import org.opentaint.ir.api.jvm.ext.double
import org.opentaint.ir.api.jvm.ext.float
import org.opentaint.ir.api.jvm.ext.int
import org.opentaint.ir.api.jvm.ext.long
import org.opentaint.ir.api.jvm.ext.short
import org.opentaint.ir.impl.cfg.util.NULL
import org.opentaint.ir.impl.cfg.util.STRING_CLASS
import org.opentaint.ir.impl.cfg.util.typeName

@JvmName("rawNull")
fun JIRRawNull() = JIRRawNullConstant(NULL)

@JvmName("rawBool")
fun JIRRawBool(value: Boolean) =
    org.opentaint.ir.api.jvm.cfg.JIRRawBool(value, PredefinedJIRPrimitives.Boolean.typeName())

@JvmName("rawByte")
fun JIRRawByte(value: Byte) = org.opentaint.ir.api.jvm.cfg.JIRRawByte(value, PredefinedJIRPrimitives.Byte.typeName())

@JvmName("rawShort")
fun JIRRawShort(value: Short) =
    org.opentaint.ir.api.jvm.cfg.JIRRawShort(value, PredefinedJIRPrimitives.Short.typeName())

@JvmName("rawChar")
fun JIRRawChar(value: Char) = org.opentaint.ir.api.jvm.cfg.JIRRawChar(value, PredefinedJIRPrimitives.Char.typeName())

@JvmName("rawInt")
fun JIRRawInt(value: Int) = org.opentaint.ir.api.jvm.cfg.JIRRawInt(value, PredefinedJIRPrimitives.Int.typeName())

@JvmName("rawLong")
fun JIRRawLong(value: Long) = org.opentaint.ir.api.jvm.cfg.JIRRawLong(value, PredefinedJIRPrimitives.Long.typeName())

@JvmName("rawFloat")
fun JIRRawFloat(value: Float) =
    org.opentaint.ir.api.jvm.cfg.JIRRawFloat(value, PredefinedJIRPrimitives.Float.typeName())

@JvmName("rawDouble")
fun JIRRawDouble(value: Double) =
    org.opentaint.ir.api.jvm.cfg.JIRRawDouble(value, PredefinedJIRPrimitives.Double.typeName())

@JvmName("rawZero")
fun JIRRawZero(typeName: TypeName) = when (typeName.typeName) {
    PredefinedJIRPrimitives.Boolean -> JIRRawBool(false)
    PredefinedJIRPrimitives.Byte -> JIRRawByte(0)
    PredefinedJIRPrimitives.Char -> JIRRawChar(0.toChar())
    PredefinedJIRPrimitives.Short -> JIRRawShort(0)
    PredefinedJIRPrimitives.Int -> JIRRawInt(0)
    PredefinedJIRPrimitives.Long -> JIRRawLong(0)
    PredefinedJIRPrimitives.Float -> JIRRawFloat(0.0f)
    PredefinedJIRPrimitives.Double -> JIRRawDouble(0.0)
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

fun JIRProject.int(value: Int): JIRInt = JIRInt(value, int)
fun JIRProject.byte(value: Byte): JIRByte = JIRByte(value, byte)
fun JIRProject.short(value: Short): JIRShort = JIRShort(value, short)
fun JIRProject.long(value: Long): JIRLong = JIRLong(value, long)
fun JIRProject.boolean(value: Boolean): JIRBool = JIRBool(value, boolean)
fun JIRProject.double(value: Double): JIRDouble = JIRDouble(value, double)
fun JIRProject.float(value: Float): JIRFloat = JIRFloat(value, float)

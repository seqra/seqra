@file:JvmName("JIRValues")
package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.cfg.JIRBool
import org.opentaint.ir.api.jvm.cfg.JIRByte
import org.opentaint.ir.api.jvm.cfg.JIRDouble
import org.opentaint.ir.api.jvm.cfg.JIRFloat
import org.opentaint.ir.api.jvm.cfg.JIRInt
import org.opentaint.ir.api.jvm.cfg.JIRLong
import org.opentaint.ir.api.jvm.cfg.JIRRawBool
import org.opentaint.ir.api.jvm.cfg.JIRRawByte
import org.opentaint.ir.api.jvm.cfg.JIRRawChar
import org.opentaint.ir.api.jvm.cfg.JIRRawDouble
import org.opentaint.ir.api.jvm.cfg.JIRRawFloat
import org.opentaint.ir.api.jvm.cfg.JIRRawInt
import org.opentaint.ir.api.jvm.cfg.JIRRawLong
import org.opentaint.ir.api.jvm.cfg.JIRRawNullConstant
import org.opentaint.ir.api.jvm.cfg.JIRRawShort
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
import org.opentaint.ir.impl.cfg.util.typeNameFromJvmName

@JvmName("rawNull")
fun JIRRawNull() = JIRRawNullConstant(NULL)

@JvmName("rawBool")
fun JIRRawBool(value: Boolean) =
    JIRRawBool(value, PredefinedPrimitives.Boolean.typeName())

@JvmName("rawByte")
fun JIRRawByte(value: Byte) =
    JIRRawByte(value, PredefinedPrimitives.Byte.typeName())

@JvmName("rawShort")
fun JIRRawShort(value: Short) =
    JIRRawShort(value, PredefinedPrimitives.Short.typeName())

@JvmName("rawChar")
fun JIRRawChar(value: Char) =
    JIRRawChar(value, PredefinedPrimitives.Char.typeName())

@JvmName("rawInt")
fun JIRRawInt(value: Int) =
    JIRRawInt(value, PredefinedPrimitives.Int.typeName())

@JvmName("rawLong")
fun JIRRawLong(value: Long) =
    JIRRawLong(value, PredefinedPrimitives.Long.typeName())

@JvmName("rawFloat")
fun JIRRawFloat(value: Float) =
    JIRRawFloat(value, PredefinedPrimitives.Float.typeName())

@JvmName("rawDouble")
fun JIRRawDouble(value: Double) =
    JIRRawDouble(value, PredefinedPrimitives.Double.typeName())

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
fun JIRRawString(value: String) =
    JIRRawStringConstant(value, STRING_CLASS.typeNameFromJvmName())

fun JIRClasspath.int(value: Int): JIRInt = JIRInt(value, int)
fun JIRClasspath.byte(value: Byte): JIRByte = JIRByte(value, byte)
fun JIRClasspath.short(value: Short): JIRShort = JIRShort(value, short)
fun JIRClasspath.long(value: Long): JIRLong = JIRLong(value, long)
fun JIRClasspath.boolean(value: Boolean): JIRBool = JIRBool(value, boolean)
fun JIRClasspath.double(value: Double): JIRDouble = JIRDouble(value, double)
fun JIRClasspath.float(value: Float): JIRFloat = JIRFloat(value, float)

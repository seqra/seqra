@file:JvmName("GoIRFunctions")
package org.opentaint.ir.go.ext

import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.inst.GoIRValueInst

/** All instructions of a specific type in this function */
inline fun <reified T : GoIRInst> GoIRFunction.findInstructions(): List<T> =
    body?.instructions?.filterIsInstance<T>() ?: emptyList()

/** Count instructions of a specific type */
inline fun <reified T : GoIRInst> GoIRFunction.countInstructions(): Int =
    body?.instructions?.count { it is T } ?: 0

/** All values (value-producing instructions) in this function */
val GoIRFunction.allValues: List<GoIRValueInst>
    get() = body?.instructions?.filterIsInstance<GoIRValueInst>() ?: emptyList()

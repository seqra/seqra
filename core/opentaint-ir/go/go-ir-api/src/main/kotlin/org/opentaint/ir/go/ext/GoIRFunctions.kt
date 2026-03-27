@file:JvmName("GoIRFunctions")
package org.opentaint.ir.go.ext

import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.expr.GoIRExpr
import org.opentaint.ir.go.inst.GoIRAssignInst
import org.opentaint.ir.go.inst.GoIRDefInst
import org.opentaint.ir.go.inst.GoIRInst

/** All instructions of a specific type in this function */
inline fun <reified T : GoIRInst> GoIRFunction.findInstructions(): List<T> =
    body?.instructions?.filterIsInstance<T>() ?: emptyList()

/** Count instructions of a specific type */
inline fun <reified T : GoIRInst> GoIRFunction.countInstructions(): Int =
    body?.instructions?.count { it is T } ?: 0

/** All value-defining instructions in this function */
val GoIRFunction.allDefInsts: List<GoIRDefInst>
    get() = body?.instructions?.filterIsInstance<GoIRDefInst>() ?: emptyList()

/**
 * Find all assign instructions whose expression is of a specific type.
 * Usage: `fn.findExpressions<GoIRBinOpExpr>()`
 */
inline fun <reified T : GoIRExpr> GoIRFunction.findExpressions(): List<T> =
    body?.instructions
        ?.filterIsInstance<GoIRAssignInst>()
        ?.mapNotNull { it.expr as? T }
        ?: emptyList()

/**
 * Find all assign instructions whose expression is of a specific type, returning pairs of (inst, expr).
 * Usage: `fn.findAssignments<GoIRBinOpExpr>()`
 */
inline fun <reified T : GoIRExpr> GoIRFunction.findAssignments(): List<Pair<GoIRAssignInst, T>> =
    body?.instructions
        ?.filterIsInstance<GoIRAssignInst>()
        ?.mapNotNull { inst -> (inst.expr as? T)?.let { inst to it } }
        ?: emptyList()

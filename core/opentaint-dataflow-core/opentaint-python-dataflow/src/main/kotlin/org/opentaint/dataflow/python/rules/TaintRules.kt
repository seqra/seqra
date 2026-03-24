package org.opentaint.dataflow.python.rules

import org.opentaint.dataflow.configuration.CommonTaintRulesProvider
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers

sealed interface TaintRules {
    data class Source(val function: String, val mark: String, val pos: PositionBase) : TaintRules
    data class Sink(val function: String, val mark: String, val pos: PositionBase, val id: String) : TaintRules
    data class Pass(val function: String, val from: PositionBaseWithModifiers, val to: PositionBaseWithModifiers) : TaintRules
}

data class PIRTaintConfig(
    val sources: List<TaintRules.Source>,
    val sinks: List<TaintRules.Sink>,
    val propagators: List<TaintRules.Pass>,
): CommonTaintRulesProvider

/**
 * Default pass-through rules for common Python builtins.
 *
 * These rules propagate taint from the method receiver (PositionBase.This)
 * to the method result (PositionBase.Result) for methods that transform strings
 * without sanitizing them.
 *
 * In PIR, `data.upper()` is lowered to:
 *   PIRAssign(target=$t0, expr=PIRAttrExpr(obj=data, attr="upper"))
 *   PIRCall(target=$t1, callee=$t0, args=[], resolvedCallee="builtins.str.upper")
 *
 * The PositionBase.This position resolves to `data` (the receiver object)
 * via PIRFlowFunctionUtils.findMethodCallReceiver().
 */
object PythonBuiltinPassRules {
    private val THIS = PositionBaseWithModifiers.BaseOnly(PositionBase.This)
    private val RESULT = PositionBaseWithModifiers.BaseOnly(PositionBase.Result)
    private val ARG0 = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0))

    private fun receiverToResult(function: String) = TaintRules.Pass(function, THIS, RESULT)
    private fun arg0ToResult(function: String) = TaintRules.Pass(function, ARG0, RESULT)

    /** String methods: receiver → result */
    val stringMethods: List<TaintRules.Pass> = listOf(
        // Case conversion
        receiverToResult("builtins.str.upper"),
        receiverToResult("builtins.str.lower"),
        receiverToResult("builtins.str.capitalize"),
        receiverToResult("builtins.str.casefold"),
        receiverToResult("builtins.str.swapcase"),
        receiverToResult("builtins.str.title"),
        // Stripping/padding
        receiverToResult("builtins.str.strip"),
        receiverToResult("builtins.str.lstrip"),
        receiverToResult("builtins.str.rstrip"),
        receiverToResult("builtins.str.center"),
        receiverToResult("builtins.str.ljust"),
        receiverToResult("builtins.str.rjust"),
        receiverToResult("builtins.str.zfill"),
        // Search/replace
        receiverToResult("builtins.str.replace"),
        // Encoding
        receiverToResult("builtins.str.encode"),
        receiverToResult("builtins.bytes.decode"),
        // Join: result taint comes from the iterable elements (arg0), not the separator (this)
        // For simplicity, propagate from both this and arg0
        receiverToResult("builtins.str.join"),
        arg0ToResult("builtins.str.join"),
        // Format: receiver (format string) and arguments can be tainted
        receiverToResult("builtins.str.format"),
    )

    /** String format: arguments → result */
    val stringFormatArgs: List<TaintRules.Pass> = (0..9).map { i ->
        TaintRules.Pass(
            "builtins.str.format",
            PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(i)),
            RESULT,
        )
    }

    /** Constructor pass-throughs: arg(0) → result */
    val constructors: List<TaintRules.Pass> = listOf(
        arg0ToResult("builtins.str"),
        arg0ToResult("builtins.bytes"),
        arg0ToResult("builtins.list"),
        arg0ToResult("builtins.tuple"),
        arg0ToResult("builtins.set"),
        arg0ToResult("builtins.frozenset"),
        arg0ToResult("builtins.dict"),
    )

    /** All default pass-through rules combined */
    val all: List<TaintRules.Pass> = stringMethods + stringFormatArgs + constructors
}

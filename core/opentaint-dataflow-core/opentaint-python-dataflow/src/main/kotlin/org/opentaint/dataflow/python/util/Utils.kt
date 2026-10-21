package org.opentaint.dataflow.python.util

import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRCallArgKind
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRParameterKind

fun PIRFunction.indexOfKeywordParam(name: String) = parameters.firstOrNull { param ->
    val isKeywordKind = param.kind == PIRParameterKind.POSITIONAL_OR_KEYWORD || param.kind == PIRParameterKind.KEYWORD_ONLY
    param.name == name && isKeywordKind
}?.index

fun PIRCall.indexOfKeywordArg(name: String) = args.indexOfFirst {
    it.kind == PIRCallArgKind.KEYWORD && it.keyword == name
}.takeIf { it >= 0 }

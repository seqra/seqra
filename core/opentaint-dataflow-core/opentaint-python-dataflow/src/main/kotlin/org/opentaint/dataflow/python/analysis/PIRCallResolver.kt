package org.opentaint.dataflow.python.analysis

import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRClasspath
import org.opentaint.ir.api.python.PIRFunction

/**
 * Resolves PIRCall instructions to concrete PIRFunction callees.
 * Uses mypy's static resolution (PIRCall.resolvedCallee).
 */
class PIRCallResolver(private val cp: PIRClasspath) {

    fun resolve(call: PIRCall): PIRFunction? {
        val qualifiedName = call.resolvedCallee ?: return null
        return cp.findFunctionOrNull(qualifiedName)
    }
}

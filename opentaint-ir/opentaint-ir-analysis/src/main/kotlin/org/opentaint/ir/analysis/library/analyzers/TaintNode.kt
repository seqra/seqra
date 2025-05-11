package org.opentaint.ir.analysis.library.analyzers

import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.ifds2.taint.Tainted
import org.opentaint.ir.analysis.paths.AccessPath
import org.opentaint.ir.api.cfg.JIRInst

/**
 * Abstract implementation for [DomainFact] that can be used for analysis where dataflow facts correlate with
 * variables/values
 *
 * @property activation is the activation point, as described in ARF14. Null value means that activation point was
 * passed (so, for analyses that do not use backward runner to taint aliases, [activation] will always be null).
 */
abstract class TaintNode(
    val variable: AccessPath,
    val activation: JIRInst? = null,
) : DomainFact {
    internal abstract val nodeType: String

    abstract fun updateActivation(newActivation: JIRInst?): TaintNode

    abstract fun moveToOtherPath(newPath: AccessPath): TaintNode

    val activatedCopy: TaintNode
        get() = updateActivation(null)

    override fun toString(): String {
        return if (activation != null) {
            "[$nodeType]: $variable, activation=$activation"
        } else {
            "[$nodeType]: $variable"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TaintNode

        if (variable != other.variable) return false
        if (activation != other.activation) return false

        return true
    }

    override fun hashCode(): Int {
        var result = variable.hashCode()
        result = 31 * result + (activation?.hashCode() ?: 0)
        return result
    }
}

class NpeTaintNode(
    variable: AccessPath,
    activation: JIRInst? = null,
) : TaintNode(variable, activation) {
    override val nodeType: String
        get() = "NPE"

    override fun updateActivation(newActivation: JIRInst?): NpeTaintNode {
        return NpeTaintNode(variable, newActivation)
    }

    override fun moveToOtherPath(newPath: AccessPath): NpeTaintNode {
        return NpeTaintNode(newPath, activation)
    }
}

data class UnusedVariableNode(
    val variable: AccessPath,
    val initStatement: JIRInst,
) : DomainFact

class TaintAnalysisNode(
    variable: AccessPath,
    activation: JIRInst? = null,
    override val nodeType: String, // = "Taint analysis"
) : TaintNode(variable, activation) {

    constructor(fact: Tainted) : this(fact.variable, nodeType = fact.mark.name)

    override fun updateActivation(newActivation: JIRInst?): TaintAnalysisNode {
        return TaintAnalysisNode(variable, newActivation, nodeType)
    }

    override fun moveToOtherPath(newPath: AccessPath): TaintAnalysisNode {
        return TaintAnalysisNode(newPath, activation, nodeType)
    }
}

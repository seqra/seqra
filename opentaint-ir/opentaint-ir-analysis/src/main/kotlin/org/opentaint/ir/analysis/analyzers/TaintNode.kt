package org.opentaint.ir.analysis.analyzers

import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.SpaceId
import org.opentaint.ir.analysis.paths.AccessPath
import org.opentaint.ir.api.cfg.JIRInst

/**
 * activation == null <=> activation point is passed
 */
abstract class TaintNode(val variable: AccessPath, val activation: JIRInst? = null): DomainFact {
    abstract fun updateActivation(newActivation: JIRInst?): TaintNode

    abstract fun moveToOtherPath(newPath: AccessPath): TaintNode

    val activatedCopy: TaintNode
        get() = updateActivation(null)

    override fun toString(): String {
        return "[${id.value}]: $variable, activation point=$activation"
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

class NPETaintNode(variable: AccessPath, activation: JIRInst? = null): TaintNode(variable, activation) {
    override fun updateActivation(newActivation: JIRInst?): NPETaintNode {
        return NPETaintNode(variable, newActivation)
    }

    override fun moveToOtherPath(newPath: AccessPath): TaintNode {
        return NPETaintNode(newPath, activation)
    }

    override val id: SpaceId
        get() = NpeAnalyzer
}

data class UnusedVariableNode(val variable: AccessPath, val initStatement: JIRInst): DomainFact {
    override val id: SpaceId
        get() = UnusedVariableAnalyzer
}

class TaintAnalysisNode(variable: AccessPath, activation: JIRInst? = null): TaintNode(variable, activation) {
    override fun updateActivation(newActivation: JIRInst?): TaintAnalysisNode {
        return TaintAnalysisNode(variable, newActivation)
    }

    override fun moveToOtherPath(newPath: AccessPath): TaintNode {
        return TaintAnalysisNode(newPath, activation)
    }

    override val id: SpaceId
        get() = TaintAnalyzer
}
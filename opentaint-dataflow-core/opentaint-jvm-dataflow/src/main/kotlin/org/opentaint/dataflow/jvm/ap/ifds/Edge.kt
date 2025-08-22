package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp

sealed interface Edge {
    val methodEntryPoint: MethodEntryPoint
    val statement: JIRInst

    sealed interface ZeroInitialEdge: Edge

    class ZeroToZero(
        override val methodEntryPoint: MethodEntryPoint,
        override val statement: JIRInst
    ) : ZeroInitialEdge {
        override fun toString(): String = "(Z -> Z)[$methodEntryPoint -> $statement]]"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ZeroToZero

            if (methodEntryPoint != other.methodEntryPoint) return false
            if (statement != other.statement) return false

            return true
        }

        override fun hashCode(): Int {
            var result = methodEntryPoint.hashCode()
            result = 31 * result + statement.hashCode()
            return result
        }
    }

    class ZeroToFact(
        override val methodEntryPoint: MethodEntryPoint,
        override val statement: JIRInst,
        val factAp: FinalFactAp
    ) : ZeroInitialEdge {

        init {
            check(factAp.exclusions is ExclusionSet.Universe) {
                "Incorrect ZeroToFact edge exclusion: $factAp"
            }
        }

        override fun toString(): String = "(Z -> $factAp)[$methodEntryPoint -> $statement]"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ZeroToFact

            if (methodEntryPoint != other.methodEntryPoint) return false
            if (statement != other.statement) return false
            if (factAp != other.factAp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = methodEntryPoint.hashCode()
            result = 31 * result + statement.hashCode()
            result = 31 * result + factAp.hashCode()
            return result
        }
    }

    class FactToFact(
        override val methodEntryPoint: MethodEntryPoint,
        val initialFactAp: InitialFactAp,
        override val statement: JIRInst,
        val factAp: FinalFactAp
    ) : Edge {

        init {
            check(factAp.exclusions !is ExclusionSet.Universe) {
                "Incorrect FactToFact edge exclusion: $factAp"
            }
        }

        override fun toString(): String = "($initialFactAp -> $factAp)[$methodEntryPoint -> $statement]"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FactToFact

            if (methodEntryPoint != other.methodEntryPoint) return false
            if (initialFactAp != other.initialFactAp) return false
            if (statement != other.statement) return false
            if (factAp != other.factAp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = methodEntryPoint.hashCode()
            result = 31 * result + initialFactAp.hashCode()
            result = 31 * result + statement.hashCode()
            result = 31 * result + factAp.hashCode()
            return result
        }
    }
}

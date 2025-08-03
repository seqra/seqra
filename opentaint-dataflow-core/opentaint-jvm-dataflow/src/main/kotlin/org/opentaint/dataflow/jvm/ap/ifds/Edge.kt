package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.TaintMark
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

    class ZeroToFact private constructor(
        override val methodEntryPoint: MethodEntryPoint,
        override val statement: JIRInst,
        private val factMark: TaintMark,
        private val factAp: FinalFactAp
    ) : ZeroInitialEdge {
        constructor(methodEntryPoint: MethodEntryPoint, statement: JIRInst, fact: Fact.FinalFact) :
                this(methodEntryPoint, statement, fact.mark, fact.ap)

        init {
            check(fact.ap.exclusions is ExclusionSet.Universe) {
                "Incorrect ZeroToFact edge exclusion: $fact"
            }
        }

        val fact: Fact.FinalFact get() = Fact.FinalFact(factMark, factAp)

        override fun toString(): String = "(Z -> $fact)[$methodEntryPoint -> $statement]"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ZeroToFact

            if (methodEntryPoint != other.methodEntryPoint) return false
            if (statement != other.statement) return false
            if (factMark != other.factMark) return false
            if (factAp != other.factAp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = methodEntryPoint.hashCode()
            result = 31 * result + statement.hashCode()
            result = 31 * result + factMark.hashCode()
            result = 31 * result + factAp.hashCode()
            return result
        }
    }

    class FactToFact private constructor(
        override val methodEntryPoint: MethodEntryPoint,
        private val initialFactMark: TaintMark,
        private val initialFactAp: InitialFactAp,
        override val statement: JIRInst,
        private val factMark: TaintMark,
        private val factAp: FinalFactAp
    ) : Edge {
        constructor(methodEntryPoint: MethodEntryPoint, initialFact: Fact.InitialFact, statement: JIRInst, fact: Fact.FinalFact) :
                this(methodEntryPoint, initialFact.mark, initialFact.ap, statement, fact.mark, fact.ap)

        init {
            check(fact.ap.exclusions !is ExclusionSet.Universe) {
                "Incorrect FactToFact edge exclusion: $fact"
            }
        }

        val initialFact: Fact.InitialFact get() = Fact.InitialFact(initialFactMark, initialFactAp)

        val fact: Fact.FinalFact get() = Fact.FinalFact(factMark, factAp)

        override fun toString(): String = "($initialFact -> $fact)[$methodEntryPoint -> $statement]"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FactToFact

            if (methodEntryPoint != other.methodEntryPoint) return false
            if (initialFactMark != other.initialFactMark) return false
            if (initialFactAp != other.initialFactAp) return false
            if (statement != other.statement) return false
            if (factMark != other.factMark) return false
            if (factAp != other.factAp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = methodEntryPoint.hashCode()
            result = 31 * result + initialFactMark.hashCode()
            result = 31 * result + initialFactAp.hashCode()
            result = 31 * result + statement.hashCode()
            result = 31 * result + factMark.hashCode()
            result = 31 * result + factAp.hashCode()
            return result
        }
    }
}

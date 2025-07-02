package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.TaintMark

sealed interface Edge {
    val initialStatement: JIRInst
    val statement: JIRInst

    sealed interface ZeroInitialEdge: Edge

    class ZeroToZero(override val initialStatement: JIRInst, override val statement: JIRInst) : ZeroInitialEdge {
        override fun toString(): String = "(Z -> Z)[$initialStatement -> $statement]]"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ZeroToZero

            if (initialStatement != other.initialStatement) return false
            if (statement != other.statement) return false

            return true
        }

        override fun hashCode(): Int {
            var result = initialStatement.hashCode()
            result = 31 * result + statement.hashCode()
            return result
        }
    }

    class ZeroToFact private constructor(
        override val initialStatement: JIRInst,
        override val statement: JIRInst,
        private val factMark: TaintMark,
        private val factAp: AccessTree
    ) : ZeroInitialEdge {
        constructor(initialStatement: JIRInst, statement: JIRInst, fact: Fact.TaintedTree) :
                this(initialStatement, statement, fact.mark, fact.ap)

        init {
            check(fact.ap.exclusions is ExclusionSet.Universe) {
                "Incorrect ZeroToFact edge exclusion: $fact"
            }
        }

        val fact: Fact.TaintedTree get() = Fact.TaintedTree(factMark, factAp)

        override fun toString(): String = "(Z -> $fact)[$initialStatement -> $statement]"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ZeroToFact

            if (initialStatement != other.initialStatement) return false
            if (statement != other.statement) return false
            if (factMark != other.factMark) return false
            if (factAp != other.factAp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = initialStatement.hashCode()
            result = 31 * result + statement.hashCode()
            result = 31 * result + factMark.hashCode()
            result = 31 * result + factAp.hashCode()
            return result
        }
    }

    class FactToFact private constructor(
        override val initialStatement: JIRInst,
        private val initialFactMark: TaintMark,
        private val initialFactAp: AccessPath,
        override val statement: JIRInst,
        private val factMark: TaintMark,
        private val factAp: AccessTree
    ) : Edge {
        constructor(initialStatement: JIRInst, initialFact: Fact.TaintedPath, statement: JIRInst, fact: Fact.TaintedTree) :
                this(initialStatement, initialFact.mark, initialFact.ap, statement, fact.mark, fact.ap)

        init {
            check(fact.ap.exclusions !is ExclusionSet.Universe) {
                "Incorrect FactToFact edge exclusion: $fact"
            }
        }

        val initialFact: Fact.TaintedPath get() = Fact.TaintedPath(initialFactMark, initialFactAp)

        val fact: Fact.TaintedTree get() = Fact.TaintedTree(factMark, factAp)

        override fun toString(): String = "($initialFact -> $fact)[$initialStatement -> $statement]"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FactToFact

            if (initialStatement != other.initialStatement) return false
            if (initialFactMark != other.initialFactMark) return false
            if (initialFactAp != other.initialFactAp) return false
            if (statement != other.statement) return false
            if (factMark != other.factMark) return false
            if (factAp != other.factAp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = initialStatement.hashCode()
            result = 31 * result + initialFactMark.hashCode()
            result = 31 * result + initialFactAp.hashCode()
            result = 31 * result + statement.hashCode()
            result = 31 * result + factMark.hashCode()
            result = 31 * result + factAp.hashCode()
            return result
        }
    }
}

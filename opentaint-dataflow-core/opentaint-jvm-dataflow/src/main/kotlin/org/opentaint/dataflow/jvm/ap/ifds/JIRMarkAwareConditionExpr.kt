package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.configuration.jvm.ContainsMark

sealed interface JIRMarkAwareConditionExpr {
    class And(val args: Array<JIRMarkAwareConditionExpr>) : JIRMarkAwareConditionExpr {
        override fun toString(): String = "And(${args.contentToString()})"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is And) return false
            return args.contentEquals(other.args)
        }

        override fun hashCode(): Int = args.contentHashCode()
    }

    class Or(val args: Array<JIRMarkAwareConditionExpr>) : JIRMarkAwareConditionExpr {
        override fun toString(): String = "Or(${args.contentToString()})"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Or) return false
            return args.contentEquals(other.args)
        }

        override fun hashCode(): Int = args.contentHashCode()
    }

    data class Literal(val condition: ContainsMark, val negated: Boolean) : JIRMarkAwareConditionExpr
}

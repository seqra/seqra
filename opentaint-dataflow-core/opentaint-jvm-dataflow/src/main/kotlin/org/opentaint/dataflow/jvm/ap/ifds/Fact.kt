package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.taint.configuration.TaintMark

sealed interface Fact {
    object Zero : Fact {
        override fun toString(): String = "ZERO"
    }

    data class TaintedPath(val mark: TaintMark, val ap: AccessPath) : Fact {
        fun changeAP(newAp: AccessPath): TaintedPath =
            if (this.ap == newAp) this else TaintedPath(this.mark, newAp)

        override fun toString(): String = "[$mark]($ap)"
    }

    data class TaintedTree(val mark: TaintMark, val ap: AccessTree) : Fact {
        fun changeAP(newAp: AccessTree): TaintedTree =
            if (this.ap === newAp) this else TaintedTree(this.mark, newAp)

        override fun toString(): String = "[$mark]($ap)"
    }
}

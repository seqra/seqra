package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp

sealed interface Fact {
    object Zero : Fact {
        override fun toString(): String = "ZERO"
    }

    data class InitialFact(val mark: TaintMark, val ap: InitialFactAp) : Fact {
        fun changeAP(newAp: InitialFactAp): InitialFact =
            if (this.ap == newAp) this else InitialFact(this.mark, newAp)

        override fun toString(): String = "[$mark]($ap)"
    }

    data class FinalFact(val mark: TaintMark, val ap: FinalFactAp) : Fact {
        fun changeAP(newAp: FinalFactAp): FinalFact =
            if (this.ap === newAp) this else FinalFact(this.mark, newAp)

        override fun toString(): String = "[$mark]($ap)"
    }
}

package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor

sealed interface PositionAccess {
    data class Simple(val base: AccessPathBase) : PositionAccess
    data class Complex(val base: PositionAccess, val accessor: Accessor) : PositionAccess
}
